package com.example.speckcue_pip

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MirrorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MirrorActivity"
    }

    private lateinit var surfaceView: SurfaceView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MirrorActivity onCreate")

        // Pre-validation to ensure we have permission data before creating the window
        if (MainActivity.pendingScreenCaptureResultCode != RESULT_OK || MainActivity.pendingScreenCaptureData == null) {
            Log.e(TAG, "Critical: Missing screen capture permission. Aborting.")
            Toast.makeText(this, "Screen capture permission missing", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        surfaceView = SurfaceView(this)
        // Ensure the SurfaceView fills the entire screen
        surfaceView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(surfaceView)

        // Set holder buffer size to device real resolution to avoid top-left rendering
        val metrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        Log.d(TAG, "📐 Setting holder fixed size to ${metrics.widthPixels}x${metrics.heightPixels}")
        surfaceView.holder.setFixedSize(metrics.widthPixels, metrics.heightPixels)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "🎯 Surface created - SYNCHRONIZED APPROACH")
                Log.d(TAG, "   - Surface valid: ${holder.surface.isValid}")
                
                if (holder.surface.isValid) {
                    // Ensure fixed size matches current metrics on creation
                    val createMetrics = android.util.DisplayMetrics()
                    windowManager.defaultDisplay.getRealMetrics(createMetrics)
                    holder.setFixedSize(createMetrics.widthPixels, createMetrics.heightPixels)

                    // Use synchronized surface management
                    ScreenCaptureService.setSurface(holder.surface)
                    
                    // Start the service with proper synchronization
                    startAndConfigureServiceSynchronized()
                    
                    // Enter PiP after service and surface are properly coordinated
                    handler.postDelayed({ enterPiP() }, 500) // Increased delay for coordination
                } else {
                    Log.e(TAG, "❌ Surface is invalid upon creation")
                    finish()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d(TAG, "🔄 Surface changed: ${width}x$height")
                // Update the surface and buffer size in case dimensions changed
                val changedMetrics = android.util.DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(changedMetrics)
                Log.d(TAG, "📐 Updating holder fixed size to ${changedMetrics.widthPixels}x${changedMetrics.heightPixels}")
                holder.setFixedSize(changedMetrics.widthPixels, changedMetrics.heightPixels)

                if (holder.surface.isValid) {
                    ScreenCaptureService.setSurface(holder.surface)
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(TAG, "🧹 Surface destroyed - cleaning up synchronized references")
                // Use synchronized surface clearing
                ScreenCaptureService.clearSurface()
            }
        })
    }

    private fun startAndConfigureServiceSynchronized() {
        Log.d(TAG, "🚀 Starting service with synchronized architecture")
        
        val resultCode = MainActivity.pendingScreenCaptureResultCode
        val resultData = MainActivity.pendingScreenCaptureData
        
        if (resultCode == null || resultData == null) {
            Log.e(TAG, "❌ Missing permission data - resultCode: $resultCode, data: $resultData")
            finish()
            return
        }
        
        Log.d(TAG, "✅ Permission data validated - starting service")

        // Start the service with the permission data
        val startIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
            Log.d(TAG, "✅ Service started with projection data")
            
            // The service will now coordinate with the surface automatically
            // No need to send separate CREATE_VIRTUAL_DISPLAY command
            // The synchronized architecture handles this automatically
            Log.d(TAG, "🎯 Synchronized service will coordinate VirtualDisplay creation automatically")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start service: ${e.message}")
            finish()
        }
    }
    
    @Deprecated("Use startAndConfigureServiceSynchronized() instead")
    private fun startAndConfigureService() {
        Log.d(TAG, "⚠️ Old method called - redirecting to synchronized version")
        startAndConfigureServiceSynchronized()
    }

    private fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Attempting to enter PiP mode...")
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16)) // Portrait aspect ratio
                .build()
            if (enterPictureInPictureMode(params)) {
                Log.d(TAG, "PiP mode entered successfully.")
            } else {
                Log.e(TAG, "Failed to enter PiP mode.")
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            // User has exited PiP mode. Stop the service and finish this activity.
            Log.d(TAG, "Exited PiP mode. Stopping service and finishing activity.")
            val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
            }
            startService(stopIntent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // Clear static data when the mirror activity is destroyed
        // This is important if the user backs out before PiP starts
        if (!isInPictureInPictureMode) {
            MainActivity.pendingScreenCaptureResultCode = null
            MainActivity.pendingScreenCaptureData = null
        }
        Log.d(TAG, "MirrorActivity destroyed.")
    }
}