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
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    companion object {
        var previewSurface: Surface? = null
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "screen_capture"
        private const val TAG = "ScreenCaptureService"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projectionManager: MediaProjectionManager? = null
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Start as foreground service (required for MediaProjection on Android 14+)
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Service started as foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "Received action: $action")

        if (action == ACTION_START_SERVICE && intent != null) {
            val resultCode = intent.getIntExtra("EXTRA_RESULT_CODE", -1)
            val data: Intent? = intent.getParcelableExtra("EXTRA_RESULT_INTENT")

            if (data != null) {
                Log.d(TAG, "Processing ACTION_START_SERVICE")
                setupMediaProjection(resultCode, data)
            } else {
                Log.e(TAG, "Screen capture data not provided in intent")
            }
        }
        return START_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        try {
            Log.d(TAG, "Setting up MediaProjection - resultCode: $resultCode, data: ${data != null}")

            mediaProjection = projectionManager?.getMediaProjection(resultCode, data)
            if (mediaProjection != null) {
                Log.d(TAG, "MediaProjection created successfully")
                createVirtualDisplay()
            } else {
                Log.e(TAG, "Failed to create MediaProjection")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup MediaProjection", e)
        }
    }

    private fun createVirtualDisplay() {
        if (mediaProjection == null || previewSurface == null) {
            Log.w(TAG, "Cannot create VirtualDisplay - MediaProjection: ${mediaProjection != null}, Surface: ${previewSurface != null}")
            return
        }

        try {
            // Get display metrics
            val dm = resources.displayMetrics
            val width = dm.widthPixels
            val height = dm.heightPixels
            val density = dm.densityDpi
            
            Log.d(TAG, "Creating VirtualDisplay: ${width}x${height}, density: $density")
            
            // Release existing VirtualDisplay if any
            virtualDisplay?.release()
            
            // Create new VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenMirror",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                previewSurface,
                null,
                null
            )
            
            if (virtualDisplay != null) {
                Log.d(TAG, "VirtualDisplay created successfully: ${width}x${height}")
            } else {
                Log.e(TAG, "Failed to create VirtualDisplay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating VirtualDisplay", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Mirroring Active")
            .setContentText("Tap to return to the app")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroying")
        
        try {
            virtualDisplay?.release()
            mediaProjection?.stop()
            Log.d(TAG, "Service destroyed; resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
