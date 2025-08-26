package com.example.speckcue_pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val CHANNEL = "pip_channel"
        private const val SCREEN_CAPTURE_REQUEST_CODE = 1001
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1002
        private const val TAG = "MainActivity"

        // These hold the permission result from onActivityResult to be passed to the service.
        var pendingScreenCaptureResultCode: Int? = null
        var pendingScreenCaptureData: Intent? = null
        var isServiceRunning = false
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "isPiPSupported" -> {
                    val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                    Log.d(TAG, "PiP support check: $isSupported")
                    result.success(isSupported)
                }

                "checkOverlayPermission" -> {
                    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(this)
                    } else {
                        true
                    }
                    Log.d(TAG, "Overlay permission check: $hasPermission")
                    result.success(hasPermission)
                }

                "requestOverlayPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        Log.d(TAG, "Requesting overlay permission")
                        pendingResult = result
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                    } else {
                        Log.d(TAG, "Overlay permission already granted")
                        result.success(true)
                    }
                }

                "startLivePiP" -> {
                    Log.d(TAG, "startLivePiP called")
                    if (isServiceRunning) {
                        Log.w(TAG, "Service is already running.")
                        result.success(true)
                        return@setMethodCallHandler
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        Log.e(TAG, "Overlay permission missing")
                        result.error("PERMISSION_ERROR", "Overlay permission required", null)
                        return@setMethodCallHandler
                    }

                    // Request screen capture permission
                    try {
                        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
                        if (captureIntent != null) {
                            pendingResult = result
                            Log.d(TAG, "Launching screen capture permission activity")
                            Toast.makeText(this, "Select 'Start now' to begin screen sharing", Toast.LENGTH_LONG).show()
                            @Suppress("DEPRECATION")
                            startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST_CODE)
                        } else {
                            Log.e(TAG, "Failed to create screen capture intent")
                            result.error("CAPTURE_ERROR", "Failed to create screen capture intent", null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up MediaProjection", e)
                        result.error("SETUP_ERROR", "Error setting up screen capture: ${e.message}", null)
                    }
                }
                
                "enterActivityPiP" -> {
                    val ok = enterActivityPiP()
                    result.success(ok)
                }

                "stopLivePiP" -> {
                    Log.d(TAG, "stopLivePiP called")
                    try {
                        stopService(Intent(this, ScreenCaptureService::class.java))
                        isServiceRunning = false
                        // Clear static references on explicit stop
                        pendingScreenCaptureResultCode = null
                        pendingScreenCaptureData = null
                        ScreenCaptureService.previewSurface = null
                        Log.d(TAG, "Service stopped and state reset")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping service", e)
                        result.success(false)
                    }
                }

                "startCountdownTest" -> {
                    val secs = (call.argument<Int>("seconds") ?: 10).coerceAtLeast(1)
                    try {
                        val intent = Intent(this, ScreenCaptureService::class.java).apply {
                            action = ScreenCaptureService.ACTION_START_COUNTDOWN_TEST
                            putExtra(ScreenCaptureService.EXTRA_COUNTDOWN_SECS, secs)
                        }
                        startService(intent)
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting countdown test", e)
                        result.error("COUNTDOWN_ERROR", "Failed to start countdown: ${e.message}", null)
                    }
                }

                else -> result.notImplemented()
            }
        }
    }

    // Enter Activity-based PiP (app-only, live UI)
    private fun enterActivityPiP(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Build PiP params with current aspect ratio
                val metrics = resources.displayMetrics
                val width = metrics.widthPixels
                val height = metrics.heightPixels
                val ratio = if (height > 0) android.util.Rational(width, height) else android.util.Rational(9, 16)

                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)

                // Optional: hint the visible area
                val rootView = window?.decorView
                val loc = IntArray(2)
                rootView?.getLocationOnScreen(loc)
                val rect = android.graphics.Rect(loc[0], loc[1], loc[0] + rootView!!.width, loc[1] + rootView.height)
                builder.setSourceRectHint(rect)

                enterPictureInPictureMode(builder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enter Activity PiP", e)
                false
            }
        } else {
            false
        }
    }

    @Deprecated("This method is required for screen capture permission.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: request=$requestCode, result=$resultCode")

        when (requestCode) {
            SCREEN_CAPTURE_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Log.d(TAG, "Screen capture permission granted")
                    // Store the result data to be used by MirrorActivity to start the service
                    pendingScreenCaptureResultCode = resultCode
                    pendingScreenCaptureData = data

                    // Start the invisible MirrorActivity which will host the SurfaceView for PiP
                    startActivity(Intent(this, MirrorActivity::class.java))
                    isServiceRunning = true
                    pendingResult?.success(true)
                } else {
                    Log.w(TAG, "Screen capture permission denied")
                    Toast.makeText(this, "Screen Capture permission is required for PiP", Toast.LENGTH_SHORT).show()
                    pendingResult?.error("PERMISSION_DENIED", "Screen capture permission denied", null)
                }
                pendingResult = null
            }

            OVERLAY_PERMISSION_REQUEST_CODE -> {
                val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(this)
                } else {
                    true
                }
                Log.d(TAG, "Overlay permission result: granted=$granted")
                pendingResult?.success(granted)
                pendingResult = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure service stops if the main activity is destroyed
        if (!isServiceRunning && isFinishing) {
            stopService(Intent(this, ScreenCaptureService::class.java))
        }
    }
}