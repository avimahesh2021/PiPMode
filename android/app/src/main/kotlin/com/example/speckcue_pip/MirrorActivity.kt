package com.example.speckcue_pip

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity

class MirrorActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private var screenCaptureResultCode: Int = 0
    private var screenCaptureData: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve the screen capture data from the intent
        screenCaptureResultCode = intent.getIntExtra("EXTRA_RESULT_CODE", 0)
        screenCaptureData = intent.getParcelableExtra("EXTRA_RESULT_INTENT")

        if (screenCaptureData == null) {
            Log.e("MirrorActivity", "Missing screen capture data")
            finish()
            return
        }

        surfaceView = SurfaceView(this)
        setContentView(surfaceView)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d("MirrorActivity", "Surface created, attaching to service")
                ScreenCaptureService.previewSurface = holder.surface

                val svc = Intent(this@MirrorActivity, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START_SERVICE
                    putExtra("EXTRA_RESULT_CODE", screenCaptureResultCode)
                    putExtra("EXTRA_RESULT_INTENT", screenCaptureData)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc)
                } else {
                    startService(svc)
                }

                enterPiP()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                ScreenCaptureService.previewSurface = null
            }
        })
    }

    private fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}
