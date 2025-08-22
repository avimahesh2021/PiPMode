package com.example.speckcue_pip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val CHANNEL = "pip_channel"
        private const val SCREEN_CAPTURE_REQUEST_CODE = 1001

        var pendingScreenCaptureResultCode: Int = -1
        var pendingScreenCaptureData: Intent? = null
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isPiPSupported" -> {
                    result.success(true) // assume API >= 26 for now
                }

                "startLivePiP" -> {
                    mediaProjectionManager =
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
                    if (captureIntent != null) {
                        pendingResult = result
                        startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST_CODE)
                    } else {
                        result.error("CAPTURE_ERROR", "Failed to create screen capture intent", null)
                    }
                }

                "stopLivePiP" -> {
                    val stop = Intent(this, ScreenCaptureService::class.java)
                    stopService(stop)
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            // Save globally
            pendingScreenCaptureResultCode = resultCode
            pendingScreenCaptureData = data

            // Launch MirrorActivity which will go PiP
            val intent = Intent(this, MirrorActivity::class.java)
            startActivity(intent)

            pendingResult?.success(true)
        } else {
            pendingResult?.success(false)
        }
    }
}
