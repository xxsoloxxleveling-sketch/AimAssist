package com.aimassist.vision

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.util.DisplayMetrics
import java.nio.ByteBuffer

class ScreenCaptureController(private val context: Context) {
    companion object { const val REQUEST_CODE = 7101 }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var bitmap: Bitmap? = null
    private var onFrame: ((Bitmap) -> Unit)? = null
    private var pendingResultCode: Int = 0
    private var pendingData: Intent? = null
    private var readyReceiver: BroadcastReceiver? = null

    fun request(activity: Activity) {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        activity.startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    fun start(resultCode: Int, data: Intent, callback: (Bitmap) -> Unit) {
        stop(); onFrame = callback; pendingResultCode = resultCode; pendingData = data
        readyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ScreenCaptureService.ACTION_READY) {
                    context.unregisterReceiver(this)
                    readyReceiver = null
                    startProjection(pendingResultCode, pendingData!!, callback)
                }
            }
        }
        ContextCompat.registerReceiver(context, readyReceiver, IntentFilter(ScreenCaptureService.ACTION_READY), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.startForegroundService(context, Intent(context, ScreenCaptureService::class.java))
    }

    private fun startProjection(resultCode: Int, data: Intent, callback: (Bitmap) -> Unit) {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, data)
        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                display?.release()
                display = null
                reader?.close()
                reader = null
                projection = null
            }
        }
        projection!!.registerCallback(projectionCallback!!, main)
        val metrics = DisplayMetrics(); @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels; val height = metrics.heightPixels
        reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]; val buffer: ByteBuffer = plane.buffer
                val stride = plane.pixelStride; val rowPadding = plane.rowStride - stride * width
                val paddedWidth = width + rowPadding / stride
                val source = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                source.copyPixelsFromBuffer(buffer)
                val out = Bitmap.createBitmap(source, 0, 0, width, height)
                source.recycle()
                main.post { bitmap = out; callback(out) }
            } finally { image.close() }
        }, main)
        display = projection!!.createVirtualDisplay("AimAssistCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, main)
    }

    fun stop() {
        readyReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        readyReceiver = null
        display?.release(); display = null; reader?.close(); reader = null
        projectionCallback?.let { callback -> projection?.unregisterCallback(callback) }
        projectionCallback = null
        projection?.stop(); projection = null
        context.stopService(Intent(context, ScreenCaptureService::class.java))
    }
}
