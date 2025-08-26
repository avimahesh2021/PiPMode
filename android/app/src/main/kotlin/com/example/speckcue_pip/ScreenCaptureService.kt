package com.example.speckcue_pip

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo // 👈 Make sure this import is present
import android.graphics.Color
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "screen_capture_pip"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_CREATE_VIRTUAL_DISPLAY = "ACTION_CREATE_VIRTUAL_DISPLAY"
        const val ACTION_START_COUNTDOWN_TEST = "ACTION_START_COUNTDOWN_TEST"
        const val ACTION_SURFACE_READY = "ACTION_SURFACE_READY"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_COUNTDOWN_SECS = "EXTRA_COUNTDOWN_SECS"

        @Volatile
        var previewSurface: Surface? = null
        
        // Synchronization and state management
        private var serviceInstance: ScreenCaptureService? = null
        private val stateLock = Object()
        
        fun notifyVirtualDisplayReady(context: Context) {
            Log.d(TAG, "🎉 VirtualDisplay ready notification")
            try {
                val intent = Intent("PIP_VIRTUAL_DISPLAY_READY")
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to send ready notification: ${e.message}")
            }
        }
        
        fun setSurface(surface: Surface) {
            synchronized(stateLock) {
                previewSurface = surface
                Log.d(TAG, "🎯 Surface synchronized: ${surface.isValid}")
                
                // If service is ready and surface is set, trigger VirtualDisplay creation
                serviceInstance?.let { service ->
                    if (service.isReadyForVirtualDisplay()) {
                        Log.d(TAG, "🚀 Service ready, triggering VirtualDisplay creation")
                        service.createVirtualDisplaySafely()
                    }
                }
            }
        }
        
        fun clearSurface() {
            synchronized(stateLock) {
                previewSurface = null
                Log.d(TAG, "🧹 Surface cleared")
            }
        }
    }

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Enhanced state management
    private var serviceReady = false
    private var mediaProjectionReady = false
    private var permissionResultCode: Int? = null
    private var permissionData: Intent? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 ScreenCaptureService onCreate - ANDROID 15 COMPATIBLE")
        Log.d(TAG, "📱 Device: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Log.d(TAG, "🎯 Target SDK: Checking Android 15 compatibility...")
        
        synchronized(stateLock) {
            serviceInstance = this
        }
        
        try {
            projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            createNotificationChannel()
            val notification = buildNotification("🔄 Service Initializing...")

            // Android 15 (API 35) requires CAPTURE_VIDEO_OUTPUT permission for MediaProjection foreground services
            Log.d(TAG, "🔐 Starting foreground service with MediaProjection type...")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            serviceReady = true
            Log.d(TAG, "✅ Service onCreate completed successfully!")
            Log.d(TAG, "✅ MediaProjection foreground service started (Android 15 compatible)")
            
            // Check if surface is already available and trigger VirtualDisplay creation
            checkAndTriggerVirtualDisplayCreation()
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ CRITICAL: SecurityException starting foreground service!")
            Log.e(TAG, "   This usually means missing Android 15 permissions:")
            Log.e(TAG, "   - FOREGROUND_SERVICE_MEDIA_PROJECTION ✓")
            Log.e(TAG, "   - CAPTURE_VIDEO_OUTPUT (newly added for Android 15)")
            Log.e(TAG, "   Error: ${e.message}")
            
            // Try to continue without foreground service type
            try {
                Log.d(TAG, "🔄 Attempting fallback service start...")
                val fallbackNotification = buildNotification("⚠️ Limited Service Mode")
                startForeground(NOTIFICATION_ID, fallbackNotification)
                serviceReady = true
                Log.d(TAG, "⚠️ Service started in fallback mode")
            } catch (fallbackException: Exception) {
                Log.e(TAG, "💥 TOTAL FAILURE: Cannot start service at all")
                Log.e(TAG, "   Fallback error: ${fallbackException.message}")
                stopSelf()
                throw e // Re-throw original exception
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error in service onCreate: ${e.message}")
            stopSelf()
            throw e
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📨 onStartCommand action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "🔄 ACTION_START: Initializing MediaProjection")
                
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                synchronized(stateLock) {
                    if (resultCode != Activity.RESULT_OK || resultData == null) {
                        Log.e(TAG, "❌ Invalid projection data - resultCode: $resultCode, data: $resultData")
                        updateNotification("❌ Invalid permissions")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    
                    // Store permission data
                    permissionResultCode = resultCode
                    permissionData = resultData
                    
                    // Create MediaProjection
                    createMediaProjectionSafely(resultCode, resultData)
                }
            }
            
            ACTION_CREATE_VIRTUAL_DISPLAY -> {
                Log.d(TAG, "📺 ACTION_CREATE_VIRTUAL_DISPLAY: Direct VirtualDisplay request")
                createVirtualDisplaySafely()
            }
            
            ACTION_SURFACE_READY -> {
                Log.d(TAG, "🎯 ACTION_SURFACE_READY: Surface is ready for VirtualDisplay")
                checkAndTriggerVirtualDisplayCreation()
            }
            
            ACTION_START_COUNTDOWN_TEST -> {
                val secs = intent.getIntExtra(EXTRA_COUNTDOWN_SECS, 10)
                Log.d(TAG, "⏱️ ACTION_START_COUNTDOWN_TEST: seconds=$secs")
                startCountdownTest(secs)
            }
            
            ACTION_STOP -> {
                Log.d(TAG, "🛑 ACTION_STOP: Stopping service")
                stopSelf()
            }
            
            else -> {
                Log.d(TAG, "⚠️ Unknown action: ${intent?.action}")
            }
        }
        
        return START_STICKY
    }

    // Countdown test rendering
    private var countdownHandler: Handler? = null
    private var countdownRunnable: Runnable? = null
    private var countdownRemaining: Int = 0

    private fun startCountdownTest(seconds: Int) {
        val surface = previewSurface
        if (surface == null || !surface.isValid) {
            Log.e(TAG, "❌ Cannot start countdown - surface not ready")
            return
        }

        // Release VirtualDisplay so we can draw directly
        virtualDisplay?.release()
        virtualDisplay = null
        updateNotification("⏱️ Countdown Test Running")

        countdownRemaining = seconds.coerceAtLeast(1)
        if (countdownHandler == null) {
            countdownHandler = Handler(Looper.getMainLooper())
        }

        countdownRunnable?.let { countdownHandler?.removeCallbacks(it) }

        countdownRunnable = object : Runnable {
            override fun run() {
                try {
                    drawCountdown(surface, countdownRemaining)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error drawing countdown: ${e.message}")
                }

                countdownRemaining -= 1
                if (countdownRemaining > 0) {
                    countdownHandler?.postDelayed(this, 1000)
                } else {
                    Log.d(TAG, "✅ Countdown finished")
                    // Clear and show DONE
                    try { drawCountdown(surface, 0) } catch (_: Exception) {}
                    // Attempt to restore VirtualDisplay if possible
                    checkAndTriggerVirtualDisplayCreation()
                }
            }
        }

        countdownHandler?.post(countdownRunnable!!)
    }

    private fun drawCountdown(surface: Surface, value: Int) {
        val canvas = surface.lockCanvas(null)
        try {
            val paint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            // Background
            canvas.drawColor(Color.parseColor("#111827")) // slate-900

            // Big number
            paint.color = Color.WHITE
            paint.textSize = 220f
            val centerX = canvas.width / 2f
            val centerY = canvas.height / 2f
            val text = if (value > 0) value.toString() else "DONE"
            canvas.drawText(text, centerX, centerY, paint)

            // Subtitle
            paint.textSize = 36f
            paint.color = Color.parseColor("#93C5FD") // blue-300
            canvas.drawText("Countdown Test", centerX, centerY + 80f, paint)

        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }
    
    // SYNCHRONIZED ARCHITECTURE METHODS
    
    private fun createMediaProjectionSafely(resultCode: Int, resultData: Intent) {
        try {
            Log.d(TAG, "🔄 Creating MediaProjection safely...")
            
            mediaProjection?.stop() // Clean up any existing projection
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            
            if (mediaProjection == null) {
                Log.e(TAG, "❌ MediaProjection creation failed")
                updateNotification("❌ MediaProjection failed")
                stopSelf()
                return
            }
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.e(TAG, "⚠️ MediaProjection stopped unexpectedly")
                    mediaProjectionReady = false
                    mainHandler.post { 
                        updateNotification("❌ MediaProjection stopped")
                        stopSelf() 
                    }
                }
            }, mainHandler)
            
            mediaProjectionReady = true
            Log.d(TAG, "✅ MediaProjection created and ready")
            updateNotification("📹 MediaProjection ready")
            
            // Now check if we can create VirtualDisplay
            checkAndTriggerVirtualDisplayCreation()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception creating MediaProjection: ${e.message}")
            updateNotification("❌ MediaProjection error")
            stopSelf()
        }
    }
    
    fun isReadyForVirtualDisplay(): Boolean {
        synchronized(stateLock) {
            val ready = serviceReady && 
                       mediaProjectionReady && 
                       mediaProjection != null && 
                       previewSurface != null && 
                       previewSurface!!.isValid
            
            Log.d(TAG, "🔍 VirtualDisplay readiness check:")
            Log.d(TAG, "   - Service ready: $serviceReady")
            Log.d(TAG, "   - MediaProjection ready: $mediaProjectionReady")
            Log.d(TAG, "   - MediaProjection exists: ${mediaProjection != null}")
            Log.d(TAG, "   - Surface exists: ${previewSurface != null}")
            Log.d(TAG, "   - Surface valid: ${previewSurface?.isValid}")
            Log.d(TAG, "   - Overall ready: $ready")
            
            return ready
        }
    }
    
    private fun checkAndTriggerVirtualDisplayCreation() {
        Log.d(TAG, "🔍 Checking if VirtualDisplay can be created...")
        
        if (isReadyForVirtualDisplay()) {
            Log.d(TAG, "✅ All components ready - creating VirtualDisplay")
            createVirtualDisplaySafely()
        } else {
            Log.d(TAG, "⏳ Not ready yet - will wait for all components")
        }
    }
    
    fun createVirtualDisplaySafely() {
        synchronized(stateLock) {
            Log.d(TAG, "🚀 Creating VirtualDisplay with synchronized architecture")
            
            if (!isReadyForVirtualDisplay()) {
                Log.e(TAG, "❌ Cannot create VirtualDisplay - not ready")
                updateNotification("⏳ Waiting for components")
                return
            }
            
            val surface = previewSurface!!
            val projection = mediaProjection!!
            
            Log.d(TAG, "✅ Prerequisites validated - starting VirtualDisplay creation")
            updateNotification("🔄 Creating VirtualDisplay...")
            
            // Use our aggressive VirtualDisplay creation instead of simple approach
            createVirtualDisplayAggressive(surface, projection)
        }
    }
    
    private fun createVirtualDisplayAggressive(surface: Surface, projection: MediaProjection) {
        Log.d(TAG, "🚨 SYNCHRONIZED VirtualDisplay Creation Starting...")
        
        try {
            // Release any existing VirtualDisplay
            virtualDisplay?.release()
            virtualDisplay = null
            
            // Get screen metrics
            val metrics = DisplayMetrics()
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay.getRealMetrics(metrics)
            
            Log.d(TAG, "📱 Device screen: ${metrics.widthPixels}x${metrics.heightPixels}@${metrics.densityDpi}dpi")
            
            // Define multiple size and flag combinations to try
            data class VirtualDisplayConfig(val width: Int, val height: Int, val density: Int, val flags: Int)
            
            val attempts = listOf(
                // Attempt 1: Use device dimensions with no flags (most compatible)
                VirtualDisplayConfig(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, 0),
                // Attempt 2: Use smaller resolution with auto-mirror
                VirtualDisplayConfig(480, 800, 160, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR),
                // Attempt 3: Use even smaller resolution with public flag
                VirtualDisplayConfig(360, 640, 160, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC),
                // Attempt 4: Minimal size with no flags
                VirtualDisplayConfig(320, 480, 120, 0)
            )
            
            for ((index, config) in attempts.withIndex()) {
                val width = config.width
                val height = config.height
                val density = config.density
                val flags = config.flags
                
                Log.d(TAG, "🔥 ATTEMPT ${index + 1}: ${width}x${height}@${density}dpi, flags=$flags")
                
                try {
                    virtualDisplay = projection.createVirtualDisplay(
                        "SynchronizedMirror_${index + 1}",
                        width, height, density,
                        flags,
                        surface,
                        object : VirtualDisplay.Callback() {
                            override fun onPaused() { 
                                Log.d(TAG, "⏸️ VirtualDisplay paused") 
                            }
                            override fun onResumed() { 
                                Log.d(TAG, "▶️ VirtualDisplay resumed") 
                            }
                            override fun onStopped() {
                                Log.d(TAG, "🛑 VirtualDisplay stopped")
                                virtualDisplay = null
                                updateNotification("❌ VirtualDisplay Stopped")
                            }
                        },
                        mainHandler
                    )
                    
                    if (virtualDisplay != null) {
                        Log.d(TAG, "🎉 SUCCESS! VirtualDisplay created on attempt ${index + 1}!")
                        Log.d(TAG, "✅ Working config: ${width}x${height}@${density}dpi, flags=$flags")
                        
                        // Validate the VirtualDisplay
                        if (virtualDisplay?.display?.isValid == true) {
                            Log.d(TAG, "✅ VirtualDisplay validation passed")
                            updateNotification("📺 Screen Broadcasting")
                            
                            // Notify success
                            Handler(Looper.getMainLooper()).postDelayed({
                                notifyVirtualDisplayReady(this@ScreenCaptureService)
                            }, 500)
                            
                            return // SUCCESS - exit function
                        } else {
                            Log.w(TAG, "⚠️ VirtualDisplay created but display is invalid - trying next config")
                            virtualDisplay?.release()
                            virtualDisplay = null
                        }
                    } else {
                        Log.w(TAG, "❌ VirtualDisplay creation returned null for attempt ${index + 1}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception on attempt ${index + 1}: ${e.javaClass.simpleName} - ${e.message}")
                }
            }
            
            // If all attempts failed
            Log.e(TAG, "💥 ALL VirtualDisplay attempts failed!")
            updateNotification("❌ VirtualDisplay Failed")
            showTestPattern(surface)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Critical exception in VirtualDisplay creation: ${e.message}")
            updateNotification("❌ Critical VirtualDisplay Error")
            showTestPattern(surface)
        }
    }
    
    private fun showTestPattern(surface: Surface) {
        Log.d(TAG, "🎨 Showing test pattern (VirtualDisplay failed)")
        
        try {
            val canvas = surface.lockCanvas(null) ?: return
            val paint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }
            
            // Clear canvas with blue background
            canvas.drawColor(Color.parseColor("#2196F3"))
            
            // Draw title
            paint.textSize = 32f
            paint.color = Color.WHITE
            canvas.drawText("❌ VirtualDisplay Failed", 50f, 100f, paint)
            
            // Draw device info
            paint.textSize = 18f
            canvas.drawText("Device: ${Build.MANUFACTURER} ${Build.MODEL}", 50f, 150f, paint)
            canvas.drawText("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", 50f, 180f, paint)
            
            // Draw troubleshooting info
            paint.textSize = 16f
            paint.color = Color.YELLOW
            canvas.drawText("💡 Troubleshooting:", 50f, 230f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 14f
            canvas.drawText("1. Restart your device", 50f, 260f, paint)
            canvas.drawText("2. Enable Developer Options", 50f, 285f, paint)
            canvas.drawText("3. Grant all app permissions", 50f, 310f, paint)
            canvas.drawText("4. Try 'Open PiP Settings' button", 50f, 335f, paint)
            canvas.drawText("5. This device may not support VirtualDisplay", 50f, 360f, paint)
            
            surface.unlockCanvasAndPost(canvas)
            
            Log.d(TAG, "✅ Test pattern displayed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing test pattern: ${e.message}")
        }
    }

    private fun createVirtualDisplay() {
        Log.d(TAG, "⚠️ Old createVirtualDisplay called - redirecting to safe version")
        createVirtualDisplaySafely()
    }

    private fun showFallbackContent(surface: Surface) {
        try {
            val canvas = surface.lockCanvas(null) ?: return
            val paint = Paint().apply {
                color = Color.parseColor("#1E3A8A") // Dark Blue
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
            paint.color = Color.WHITE
            paint.textSize = 40f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                "VirtualDisplay Failed",
                canvas.width / 2f,
                canvas.height / 2f - 40,
                paint
            )
            paint.textSize = 24f
            canvas.drawText(
                "This device may not support screen capture.",
                canvas.width / 2f,
                canvas.height / 2f + 20,
                paint
            )
            surface.unlockCanvasAndPost(canvas)
            Log.d(TAG, "Fallback content drawn on surface.")
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing fallback content", e)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PiP Screen Broadcasting")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "🛑 Service onDestroy - SYNCHRONIZED CLEANUP")
        
        synchronized(stateLock) {
            // Clear service instance
            serviceInstance = null
            serviceReady = false
            mediaProjectionReady = false
        }
        
        // Clean up VirtualDisplay
        virtualDisplay?.let { vd ->
            try {
                vd.release()
                Log.d(TAG, "✅ VirtualDisplay released")
            } catch (e: Exception) {
                Log.w(TAG, "Warning releasing VirtualDisplay: ${e.message}")
            }
        }
        virtualDisplay = null
        
        // Clean up MediaProjection
        mediaProjection?.let { mp ->
            try {
                mp.stop()
                Log.d(TAG, "✅ MediaProjection stopped")
            } catch (e: Exception) {
                Log.w(TAG, "Warning stopping MediaProjection: ${e.message}")
            }
        }
        mediaProjection = null
        
        // Clean up surface reference  
        clearSurface()
        
        // Update MainActivity state
        MainActivity.isServiceRunning = false
        
        Log.d(TAG, "🧹 Synchronized service cleanup completed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}