package com.aimassist.vision

import com.aimassist.model.Point2
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc

class TableTransformer {
    var ready = false; private var forward: Mat? = null; private var inverse: Mat? = null
    fun setCorners(corners: List<Point2>, width: Float = 2000f, height: Float = 1000f) {
        require(corners.size == 4)
        val src = MatOfPoint2f(*corners.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val dst = MatOfPoint2f(Point(0.0,0.0), Point(width.toDouble(),0.0), Point(width.toDouble(),height.toDouble()), Point(0.0,height.toDouble()))
        forward?.release(); inverse?.release(); forward = Calib3d.findHomography(src, dst); inverse = Calib3d.findHomography(dst, src)
        src.release(); dst.release(); ready = true
    }
    fun cameraToTable(p: Point2): Point2 = map(p, forward!!)
    fun tableToCamera(p: Point2): Point2 = map(p, inverse!!)
    private fun map(p: Point2, h: Mat): Point2 {
        val input = MatOfPoint2f(Point(p.x.toDouble(), p.y.toDouble())); val output = MatOfPoint2f(); CorePerspective.transform(input, output, h)
        val q = output.toArray()[0]; input.release(); output.release(); return Point2(q.x.toFloat(), q.y.toFloat())
    }
    fun warp(input: Mat, width: Int = 1000, height: Int = 500): Mat { val out = Mat(); Imgproc.warpPerspective(input, out, forward!!, org.opencv.core.Size(width.toDouble(),height.toDouble())); return out }
}

private object CorePerspective { fun transform(src: Mat, dst: Mat, h: Mat) = org.opencv.core.Core.perspectiveTransform(src, dst, h) }
