package com.aimassist.vision

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_READY = "com.aimassist.SCREEN_CAPTURE_SERVICE_READY"
        const val ACTION_STOP = "com.aimassist.SCREEN_CAPTURE_SERVICE_STOP"
        private const val CHANNEL = "screen_capture"
        private const val NOTIFICATION_ID = 7102
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
        sendBroadcast(Intent(ACTION_READY).setPackage(packageName))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
