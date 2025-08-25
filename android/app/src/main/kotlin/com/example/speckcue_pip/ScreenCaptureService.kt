package com.example.speckcue_pip

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    companion object {
        var previewSurface: Surface? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var mediaProjection: MediaProjection? = null
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "screen_capture"
        private const val TAG = "ScreenCaptureService"

        fun updateVirtualDisplay(width: Int, height: Int) {
            virtualDisplay?.release()
            createVirtualDisplay(width, height)
        }

        private fun createVirtualDisplay(width: Int, height: Int) {
            previewSurface?.let { surface ->
                val metrics = DisplayMetrics()
                // Use provided dimensions or fallback to default
                val displayWidth = if (width > 0) width else 1280
                val displayHeight = if (height > 0) height else 720
                val density = metrics.densityDpi

                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenMirror",
                    displayWidth, displayHeight, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surface,
                    object : VirtualDisplay.Callback() {
                        override fun onPaused() {
                            Log.d(TAG, "VirtualDisplay paused")
                        }

                        override fun onResumed() {
                            Log.d(TAG, "VirtualDisplay resumed")
                        }

                        override fun onStopped() {
                            Log.d(TAG, "VirtualDisplay stopped")
                        }
                    },
                    null
                )
                Log.d(TAG, "VirtualDisplay created ${displayWidth}x${displayHeight}")
            }
        }
    }

    private var projectionManager: MediaProjectionManager? = null

    override fun onCreate() {
        super.onCreate()
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "Service created (foreground)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                val code = intent.getIntExtra("EXTRA_RESULT_CODE", -1)
                val data : Intent? = intent.getParcelableExtra("EXTRA_RESULT_INTENT")
                val width = intent.getIntExtra("surface_width", 0)
                val height = intent.getIntExtra("surface_height", 0)

                if (code != -1 && data != null) {
                    try {
                        mediaProjection = projectionManager?.getMediaProjection(code, data)
                        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                            override fun onStop() {
                                Log.d(TAG, "MediaProjection stopped")
                                stopSelf()
                            }
                        }, null)

                        Log.d(TAG, "MediaProjection ready")

                        // Create virtual display with proper dimensions
                        createVirtualDisplay(width, height)

                    } catch (e: Exception) {
                        Log.e(TAG, "MediaProjection init failed", e)
                        stopSelf()
                    }
                } else {
                    Log.e(TAG, "Missing screen capture data from Intent")
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen Capture", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Screen mirroring notification"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Mirroring Active")
            .setContentText("Tap to return to the app")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            virtualDisplay?.release()
            mediaProjection?.stop()
            previewSurface = null
            Log.d(TAG, "Service destroyed; resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}