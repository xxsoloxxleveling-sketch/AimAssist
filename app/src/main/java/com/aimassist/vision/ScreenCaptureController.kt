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
import android.util.DisplayMetrics
import java.nio.ByteBuffer

class ScreenCaptureController(private val context: Context) {
    companion object { const val REQUEST_CODE = 7101 }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var bitmap: Bitmap? = null
    private var onFrame: ((Bitmap) -> Unit)? = null

    fun request(activity: Activity) {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        activity.startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    fun start(resultCode: Int, data: Intent, callback: (Bitmap) -> Unit) {
        stop(); onFrame = callback
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, data)
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

    fun stop() { display?.release(); display = null; reader?.close(); reader = null; projection?.stop(); projection = null }
}
