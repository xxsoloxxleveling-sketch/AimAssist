package com.aimassist.vision

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.provider.Settings
import android.view.*
import android.os.IBinder

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_READY = "com.aimassist.SCREEN_CAPTURE_SERVICE_READY"
        const val ACTION_STOP = "com.aimassist.SCREEN_CAPTURE_SERVICE_STOP"
        private const val CHANNEL = "screen_capture"
    private const val NOTIFICATION_ID = 7102
    private var overlay: View? = null
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("AimAssist is running")
            .setContentText("Analyzing the 2D pool app screen")
            .setSmallIcon(com.aimassist.R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        showOverlay()
        sendBroadcast(Intent(ACTION_READY).setPackage(packageName))
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val view = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                paint.color = 0xe61a2633.toInt()
                canvas.drawRoundRect(24f, 24f, 430f, 88f, 18f, 18f, paint)
                paint.color = Color.WHITE
                paint.textSize = 26f
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("AIMASSIST  •  CAPTURING", 48f, 65f, paint)
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            112,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        getSystemService(WindowManager::class.java).addView(view, params)
        overlay = view
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
