package com.aimassist.vision

import com.aimassist.model.DetectedBall
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint3f
import org.opencv.imgproc.Imgproc

class BallDetector {
    fun detect(table: Mat): List<DetectedBall> {
        val gray = Mat(); Imgproc.cvtColor(table, gray, Imgproc.COLOR_RGBA2GRAY); Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(9.0,9.0), 2.0)
        val circles = Mat(); Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.2, 18.0, 100.0, 22.0, 8, 45)
        val result = mutableListOf<DetectedBall>()
        for (i in 0 until circles.cols()) { val c = circles.get(0,i) ?: continue; if (c.size >= 3) result += DetectedBall(c[0].toFloat()*2f, c[1].toFloat()*2f, c[2].toFloat()*2f, .75f) }
        circles.release(); gray.release(); return result
    }
}
