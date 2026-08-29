package com.aimassist.vision

import com.aimassist.model.Point2
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc

class TableTransformer {
    companion object {
        const val TABLE_WIDTH = 2000f
        const val TABLE_HEIGHT = 1000f
        private const val VISION_WIDTH = 1000
        private const val VISION_HEIGHT = 500
    }

    var ready = false
    private var forward: Mat? = null
    private var inverse: Mat? = null
    private var visionForward: Mat? = null

    fun setCorners(corners: List<Point2>, width: Float = 2000f, height: Float = 1000f) {
        require(corners.size == 4)
        val src = MatOfPoint2f(*corners.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val dst = MatOfPoint2f(Point(0.0,0.0), Point(width.toDouble(),0.0), Point(width.toDouble(),height.toDouble()), Point(0.0,height.toDouble()))
        val visionDst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(VISION_WIDTH.toDouble(), 0.0),
            Point(VISION_WIDTH.toDouble(), VISION_HEIGHT.toDouble()),
            Point(0.0, VISION_HEIGHT.toDouble())
        )
        forward?.release()
        inverse?.release()
        visionForward?.release()
        forward = Calib3d.findHomography(src, dst)
        inverse = Calib3d.findHomography(dst, src)
        // Vision runs at half resolution, but this homography must still cover the
        // complete table. Using the canonical homography here crops to one quarter.
        visionForward = Calib3d.findHomography(src, visionDst)
        src.release()
        dst.release()
        visionDst.release()
        ready = true
    }
    fun cameraToTable(p: Point2): Point2 = map(p, forward!!)
    fun tableToCamera(p: Point2): Point2 = map(p, inverse!!)
    private fun map(p: Point2, h: Mat): Point2 {
        val input = MatOfPoint2f(Point(p.x.toDouble(), p.y.toDouble())); val output = MatOfPoint2f(); CorePerspective.transform(input, output, h)
        val q = output.toArray()[0]; input.release(); output.release(); return Point2(q.x.toFloat(), q.y.toFloat())
    }
    fun warp(input: Mat): Mat {
        check(ready) { "Table calibration is not ready" }
        val out = Mat()
        Imgproc.warpPerspective(
            input,
            out,
            visionForward!!,
            org.opencv.core.Size(VISION_WIDTH.toDouble(), VISION_HEIGHT.toDouble())
        )
        return out
    }
}

private object CorePerspective { fun transform(src: Mat, dst: Mat, h: Mat) = org.opencv.core.Core.perspectiveTransform(src, dst, h) }
