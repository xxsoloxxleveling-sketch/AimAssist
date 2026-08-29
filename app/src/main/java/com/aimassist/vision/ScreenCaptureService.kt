package com.aimassist.vision

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.provider.Settings
import android.view.*
import android.os.IBinder
import com.aimassist.model.DetectedBall
import com.aimassist.model.GuideLine
import com.aimassist.model.Point2

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_READY = "com.aimassist.SCREEN_CAPTURE_SERVICE_READY"
        const val ACTION_STOP = "com.aimassist.SCREEN_CAPTURE_SERVICE_STOP"
        private const val CHANNEL = "screen_capture"
        private const val NOTIFICATION_ID = 7102
        @Volatile private var instance: ScreenCaptureService? = null
        @Volatile private var calibrationListener: ((List<Point2>) -> Unit)? = null

        fun setCalibrationListener(listener: ((List<Point2>) -> Unit)?) {
            calibrationListener = listener
        }

        fun beginCalibration() {
            instance?.guideOverlay?.post { instance?.resetCalibration() }
        }

        fun showPrediction(
            corners: List<Point2>,
            balls: List<DetectedBall>,
            guides: List<GuideLine>,
            message: String
        ) {
            instance?.guideOverlay?.post {
                instance?.guideOverlay?.update(corners, balls, guides, message)
            }
        }
    }

    private var overlay: View? = null
    private var guideOverlay: GuideOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
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
        val view = GuideOverlayView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        getSystemService(WindowManager::class.java).addView(view, params)
        overlay = view
        guideOverlay = view
        overlayParams = params
        resetCalibration()
    }

    private fun resetCalibration() {
        guideOverlay?.resetCalibration()
        setOverlayTouchEnabled(true)
    }

    private fun setOverlayTouchEnabled(enabled: Boolean) {
        val params = overlayParams ?: return
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            if (enabled) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        overlay?.let { getSystemService(WindowManager::class.java).updateViewLayout(it, params) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
        guideOverlay = null
        overlayParams = null
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class GuideOverlayView : View(this@ScreenCaptureService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var corners = emptyList<Point2>()
        private var balls = emptyList<DetectedBall>()
        private var guides = emptyList<GuideLine>()
        private var message = "Waiting for calibration"
        private val selectedCorners = mutableListOf<Point2>()

        fun resetCalibration() {
            selectedCorners.clear()
            corners = emptyList()
            balls = emptyList()
            guides = emptyList()
            message = "TAP TABLE CORNER 1 / 4"
            invalidate()
        }

        fun update(
            newCorners: List<Point2>,
            newBalls: List<DetectedBall>,
            newGuides: List<GuideLine>,
            newMessage: String
        ) {
            if (newCorners.isNotEmpty()) corners = newCorners.toList()
            balls = newBalls.toList()
            guides = newGuides.toList()
            message = newMessage
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP || selectedCorners.size >= 4) return true
            selectedCorners += Point2(event.x, event.y)
            corners = selectedCorners.toList()
            message = if (selectedCorners.size == 4) {
                "CALIBRATED • ANALYZING"
            } else {
                "TAP TABLE CORNER ${selectedCorners.size + 1} / 4"
            }
            invalidate()
            if (selectedCorners.size == 4) {
                calibrationListener?.invoke(corners)
                setOverlayTouchEnabled(false)
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            paint.style = Paint.Style.FILL
            paint.color = 0xe61a2633.toInt()
            canvas.drawRoundRect(24f, 24f, 570f, 92f, 18f, 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = 26f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("AIMASSIST  •  $message", 48f, 67f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.CYAN
            if (corners.size == 4) {
                corners.indices.forEach { index ->
                    val start = corners[index]
                    val end = corners[(index + 1) % corners.size]
                    canvas.drawLine(start.x, start.y, end.x, end.y, paint)
                }
            }

            balls.forEach { ball ->
                paint.color = Color.rgb(92, 255, 138)
                paint.strokeWidth = 4f
                canvas.drawCircle(ball.x, ball.y, ball.radius.coerceAtLeast(12f), paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(ball.x, ball.y, 5f, paint)
                paint.style = Paint.Style.STROKE
            }

            guides.forEach { guide ->
                paint.strokeWidth = if (guide.kind == GuideLine.Kind.AIM) 7f else 5f
                paint.color = when (guide.kind) {
                    GuideLine.Kind.AIM -> Color.WHITE
                    GuideLine.Kind.OBJECT -> Color.MAGENTA
                    GuideLine.Kind.RAIL -> Color.YELLOW
                }
                canvas.drawLine(guide.from.x, guide.from.y, guide.to.x, guide.to.y, paint)
            }
        }
    }
}
