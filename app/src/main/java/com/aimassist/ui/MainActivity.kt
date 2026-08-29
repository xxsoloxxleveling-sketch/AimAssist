package com.aimassist.ui

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import android.graphics.drawable.GradientDrawable
import com.aimassist.model.*
import com.aimassist.physics.PoolGuideEngine
import com.aimassist.vision.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import kotlin.math.atan2

class MainActivity : Activity() {
    private lateinit var canvas: CaptureCanvas
    private val capture by lazy { ScreenCaptureController(this) }
    private val transformer = TableTransformer()
    private val detector = BallDetector(); private val engine = PoolGuideEngine()
    private val corners = mutableListOf<Point2>(); private var balls = emptyList<DetectedBall>(); private var frameCount=0
    private var lastBitmap: Bitmap? = null; private lateinit var status: TextView

    override fun onCreate(state: Bundle?) { super.onCreate(state); OpenCVLoader.initLocal(); buildUi() }
    private fun buildUi() {
        val root=FrameLayout(this); canvas=CaptureCanvas(); root.addView(canvas,FrameLayout.LayoutParams(-1,-1))
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(22, 14, 22, 14)
            background = GradientDrawable().apply {
                setColor(0xf216202b.toInt())
                cornerRadius = 18f
            }
        }
        val title = TextView(this).apply {
            text = "AIMASSIST\n2D SCREEN OVERLAY"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 24, 0)
        }
        val captureButton = Button(this).apply {
            text = "Start screen capture"
            isAllCaps = false
            setOnClickListener { capture.request(this@MainActivity) }
        }
        val recalibrate = Button(this).apply {
            text = "Calibrate (4 taps)"
            isAllCaps = false
            setOnClickListener {
                corners.clear()
                transformer.ready = false
                canvas.invalidate()
                updateStatus()
            }
        }
        val stopButton = Button(this).apply {
            text = "Stop"
            isAllCaps = false
            setOnClickListener {
                capture.stop()
                lastBitmap = null
                balls = emptyList()
                canvas.invalidate()
                status.text = "Capture stopped"
            }
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            text = "Ready · capture the 2D app screen"
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 8, 8, 8)
        }
        bar.addView(title, LinearLayout.LayoutParams(150, -1))
        bar.addView(captureButton)
        bar.addView(recalibrate)
        bar.addView(stopButton)
        bar.addView(status, LinearLayout.LayoutParams(0, -1, 1f))
        val barParams = FrameLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        barParams.setMargins(16, 16, 16, 0)
        root.addView(bar, barParams)
        setContentView(root)
    }
    override fun onActivityResult(request:Int,result:Int,data:android.content.Intent?){super.onActivityResult(request,result,data);if(request==ScreenCaptureController.REQUEST_CODE&&result==RESULT_OK&&data!=null)capture.start(result,data){bmp->lastBitmap=bmp;process(bmp)}}
    private fun process(bitmap:Bitmap){frameCount++;if(corners.size<4){canvas.invalidate();updateStatus();return};if(!transformer.ready){transformer.setCorners(corners);updateStatus()};val source=Mat();Utils.bitmapToMat(bitmap,source);val warped=transformer.warp(source);balls=detector.detect(warped);warped.release();source.release();canvas.invalidate();updateStatus()}
    private fun updateStatus(){status.text=when{corners.size<4->"Tap table corners ${corners.size}/4";!transformer.ready->"Calibration ready";else->"LIVE  balls=${balls.size}  frames=$frameCount  screen capture"}}
    override fun onDestroy(){capture.stop();super.onDestroy()}

    private inner class CaptureCanvas:View(this@MainActivity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private var cueStart:PointF?=null;private var cueEnd:PointF?=null
        override fun onDraw(c: Canvas) {
            val bitmap = lastBitmap ?: return
            val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val offsetX = (width - bitmap.width * scale) / 2f
            val offsetY = (height - bitmap.height * scale) / 2f
            c.drawBitmap(bitmap, null, RectF(offsetX, offsetY,
                offsetX + bitmap.width * scale, offsetY + bitmap.height * scale), paint)

            fun screen(point: Point2) = PointF(offsetX + point.x * scale, offsetY + point.y * scale)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.YELLOW
            corners.forEach { point ->
                val screenPoint = screen(point)
                c.drawCircle(screenPoint.x, screenPoint.y, 14f, paint)
            }
            if (!transformer.ready) return

            paint.color = Color.CYAN
            corners.forEachIndexed { index, point ->
                val start = screen(point)
                val end = screen(corners[(index + 1) % 4])
                c.drawLine(start.x, start.y, end.x, end.y, paint)
            }
            balls.forEach { ball ->
                val screenPoint = screen(transformer.tableToCamera(Point2(ball.x, ball.y)))
                paint.color = Color.GREEN
                c.drawCircle(screenPoint.x, screenPoint.y, ball.radius / 2f * scale, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(screenPoint.x, screenPoint.y, 4f, paint)
                paint.style = Paint.Style.STROKE
            }

            val angle = cueStart?.let { start ->
                cueEnd?.let { end -> atan2(end.y - start.y, end.x - start.x) }
            }
            engine.calculate(balls, angle).forEach { guide ->
                val start = screen(transformer.tableToCamera(guide.from))
                val end = screen(transformer.tableToCamera(guide.to))
                paint.strokeWidth = 5f
                paint.color = if (guide.kind == GuideLine.Kind.AIM) Color.WHITE else Color.MAGENTA
                c.drawLine(start.x, start.y, end.x, end.y, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (corners.size < 4) {
                        val bitmap = lastBitmap ?: return true
                        val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                        corners += Point2(
                            (event.x - (width - bitmap.width * scale) / 2f) / scale,
                            (event.y - (height - bitmap.height * scale) / 2f) / scale
                        )
                        invalidate()
                        updateStatus()
                    } else {
                        cueStart = PointF(event.x, event.y)
                    }
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    if (cueStart != null) {
                        cueEnd = PointF(event.x, event.y)
                        invalidate()
                    }
                }
            }
            return true
        }
    }
}
