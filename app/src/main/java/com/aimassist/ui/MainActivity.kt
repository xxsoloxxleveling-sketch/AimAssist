package com.aimassist.ui

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
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
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(20,16,20,16);setBackgroundColor(0xcc101820)}
        val captureButton=Button(this).apply{text="Start screen capture";setOnClickListener{capture.request(this@MainActivity)}}
        val recalibrate=Button(this).apply{text="Calibrate (4 taps)";setOnClickListener{corners.clear();transformer.ready=false;canvas.invalidate();updateStatus()}}
        status=TextView(this).apply{textColor=Color.WHITE;textSize=14f;text="Ready — capture the 2D app screen";setPadding(18,8,8,8)}
        bar.addView(captureButton);bar.addView(recalibrate);bar.addView(status,LinearLayout.LayoutParams(0,-1,1f));root.addView(bar,FrameLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT,Gravity.TOP));setContentView(root)
    }
    override fun onActivityResult(request:Int,result:Int,data:android.content.Intent?){super.onActivityResult(request,result,data);if(request==ScreenCaptureController.REQUEST_CODE&&result==RESULT_OK&&data!=null)capture.start(result,data){bmp->lastBitmap=bmp;process(bmp)}}
    private fun process(bitmap:Bitmap){frameCount++;if(corners.size<4){canvas.invalidate();updateStatus();return};if(!transformer.ready){transformer.setCorners(corners);updateStatus()};val source=Mat();Utils.bitmapToMat(bitmap,source);val warped=transformer.warp(source);balls=detector.detect(warped);warped.release();source.release();canvas.invalidate();updateStatus()}
    private fun updateStatus(){status.text=when{corners.size<4->"Tap table corners ${corners.size}/4";!transformer.ready->"Calibration ready";else->"LIVE  balls=${balls.size}  frames=$frameCount  screen capture"}}
    override fun onDestroy(){capture.stop();super.onDestroy()}

    private inner class CaptureCanvas:View(this@MainActivity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private var cueStart:PointF?=null;private var cueEnd:PointF?=null
        override fun onDraw(c:Canvas){val b=lastBitmap?:return;val scale=minOf(width.toFloat()/b.width,height.toFloat()/b.height);val ox=(width-b.width*scale)/2;val oy=(height-b.height*scale)/2;c.drawBitmap(b,null,RectF(ox,oy,ox+b.width*scale,oy+b.height*scale),paint);fun screen(p:Point2)=PointF(ox+p.x*scale,oy+p.y*scale);paint.style=Paint.Style.STROKE;paint.strokeWidth=4f;paint.color=Color.YELLOW;corners.forEach{val q=screen(it);c.drawCircle(q.x,q.y,14f,paint)};if(!transformer.ready)return;paint.color=Color.CYAN;corners.forEachIndexed{i,p->val q=screen(corners[(i+1)%4]);val a=screen(p);c.drawLine(a.x,a.y,q.x,q.y,paint)};balls.forEach{val q=screen(transformer.tableToCamera(Point2(it.x,it.y)));paint.color=Color.GREEN;c.drawCircle(q.x,q.y,it.radius/2f*scale,paint);paint.style=Paint.Style.FILL;c.drawCircle(q.x,q.y,4f,paint);paint.style=Paint.Style.STROKE)};val angle=cueStart?.let{a->cueEnd?.let{d->atan2(d.y-a.y,d.x-a.x)}};engine.calculate(balls,angle).forEach{g->val a=screen(transformer.tableToCamera(g.from));val z=screen(transformer.tableToCamera(g.to));paint.strokeWidth=5f;paint.color=if(g.kind==GuideLine.Kind.AIM)Color.WHITE else Color.MAGENTA;c.drawLine(a.x,a.y,z.x,z.y,paint)}}
        override fun onTouchEvent(e:MotionEvent):Boolean{if(e.action==MotionEvent.ACTION_DOWN){if(corners.size<4){val b=lastBitmap?:return true;val s=minOf(width.toFloat()/b.width,height.toFloat()/b.height);corners+=Point2((e.x-(width-b.width*s)/2)/s,(e.y-(height-b.height*s)/2)/s);invalidate();updateStatus()}else cueStart=PointF(e.x,e.y);return true};if(e.action==MotionEvent.ACTION_MOVE&&cueStart!=null){cueEnd=PointF(e.x,e.y);invalidate();return true};if(e.action==MotionEvent.ACTION_UP&&cueStart!=null){cueEnd=PointF(e.x,e.y);invalidate();return true};return true}
    }
}
