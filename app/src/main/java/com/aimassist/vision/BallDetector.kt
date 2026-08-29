package com.aimassist.vision

import com.aimassist.model.DetectedBall
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class BallDetector {
    data class AimObservation(val cueBall: DetectedBall, val angle: Float)

    fun detect(table: Mat): List<DetectedBall> {
        val hsv = Mat(); Imgproc.cvtColor(table, hsv, Imgproc.COLOR_RGB2HSV)
        val result = mutableListOf<DetectedBall>()
        val combinedColorMask = Mat.zeros(hsv.rows(), hsv.cols(), org.opencv.core.CvType.CV_8UC1)
        val ranges = listOf(
            Scalar(0.0, 95.0, 35.0) to Scalar(12.0, 255.0, 255.0),    // red / brown
            Scalar(8.0, 95.0, 55.0) to Scalar(38.0, 255.0, 255.0),    // orange / yellow
            Scalar(38.0, 90.0, 40.0) to Scalar(78.0, 255.0, 255.0),   // green
            Scalar(105.0, 85.0, 40.0) to Scalar(132.0, 255.0, 255.0), // blue
            Scalar(132.0, 80.0, 35.0) to Scalar(169.0, 255.0, 255.0), // purple
            Scalar(170.0, 95.0, 35.0) to Scalar(179.0, 255.0, 255.0), // wrapped red
            Scalar(0.0, 0.0, 0.0) to Scalar(179.0, 255.0, 95.0)       // black ball and its highlights
        )
        ranges.forEach { (low, high) ->
            val mask = Mat()
            Core.inRange(hsv, low, high, mask)
            Core.bitwise_or(combinedColorMask, mask, combinedColorMask)
            appendColorCandidates(mask, result)
            mask.release()
        }
        appendDarkBallCandidates(hsv, result)
        val gray = Mat(); Imgproc.cvtColor(table, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(7.0, 7.0), 1.5)
        val circles = Mat()
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.2, 15.0, 100.0, 15.0, 8, 20)
        for (index in 0 until circles.cols()) {
            val circle = circles.get(0, index) ?: continue
            if (circle.size < 3) continue
            val coloredFraction = maskFraction(combinedColorMask, circle[0], circle[1], circle[2] * 0.82)
            // The game's ghost/target ring reached ~0.28 in testing; real rendered
            // balls remain above 0.40 even when striped.
            if (coloredFraction >= 0.36f) {
                result += DetectedBall(circle[0].toFloat() * 2f, circle[1].toFloat() * 2f, circle[2].toFloat() * 2f, coloredFraction)
            }
        }
        circles.release(); gray.release(); combinedColorMask.release()
        hsv.release()
        return result.sortedByDescending { it.radius }.fold(mutableListOf<DetectedBall>()) { unique, candidate ->
            val overlaps = unique.any { existing ->
                hypot(existing.x - candidate.x, existing.y - candidate.y) <
                    maxOf(existing.radius, candidate.radius) * 1.2f
            }
            if (!overlaps) unique += candidate
            unique
        }.filterNot(::isPocketCandidate)
    }

    private fun appendColorCandidates(mask: Mat, output: MutableList<DetectedBall>) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(3.0, 3.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
        val contours = mutableListOf<MatOfPoint>(); val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            val bounds = Imgproc.boundingRect(contour)
            val points = MatOfPoint2f(*contour.toArray()); val center = Point(); val radius = FloatArray(1)
            Imgproc.minEnclosingCircle(points, center, radius)
            val r = radius[0]
            val fill = if (r <= 0f) 0.0 else area / (Math.PI * r * r)
            val aspect = if (bounds.height == 0) 0.0 else bounds.width.toDouble() / bounds.height
            if (area in 35.0..1000.0 && r in 6.0f..20.0f && fill > 0.35 && aspect in 0.55..1.80) {
                output += DetectedBall(center.x.toFloat() * 2f, center.y.toFloat() * 2f, (r * 2.15f).coerceAtLeast(18f), fill.toFloat().coerceAtMost(1f))
            }
            points.release(); contour.release()
        }
        hierarchy.release(); kernel.release()
    }

    private fun isPocketCandidate(ball: DetectedBall): Boolean {
        val pocketCenters = arrayOf(
            0f to 0f, 1000f to 0f, 2000f to 0f,
            0f to 1000f, 1000f to 1000f, 2000f to 1000f
        )
        return pocketCenters.any { (x, y) -> hypot(ball.x - x, ball.y - y) < 55f }
    }

    private fun appendDarkBallCandidates(hsv: Mat, output: MutableList<DetectedBall>) {
        val darkMask = Mat()
        Core.inRange(hsv, Scalar(0.0, 0.0, 0.0), Scalar(179.0, 255.0, 115.0), darkMask)
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(darkMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val clothHue = dominantClothHue(hsv)
        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            val bounds = Imgproc.boundingRect(contour)
            val points = MatOfPoint2f(*contour.toArray())
            val center = Point()
            val radius = FloatArray(1)
            Imgproc.minEnclosingCircle(points, center, radius)
            val r = radius[0]
            val fill = if (r <= 0f) 0.0 else area / (Math.PI * r * r)
            val aspect = if (bounds.height == 0) 0.0 else bounds.width.toDouble() / bounds.height
            val clothRing = clothRingFraction(hsv, center.x, center.y, r.toDouble(), clothHue)
            if (
                area in 35.0..1100.0 && r in 6.0f..22.0f &&
                fill > 0.28 && aspect in 0.55..1.80 && clothRing > 0.32f
            ) {
                output += DetectedBall(
                    center.x.toFloat() * 2f,
                    center.y.toFloat() * 2f,
                    (r * 2.1f).coerceAtLeast(18f),
                    (fill.toFloat() * clothRing).coerceIn(0f, 1f)
                )
            }
            points.release()
            contour.release()
        }
        hierarchy.release()
        darkMask.release()
    }

    private fun clothRingFraction(hsv: Mat, cx: Double, cy: Double, radius: Double, clothHue: Double): Float {
        val inner = radius * 1.25
        val outer = radius * 1.85
        val minX = (cx - outer).toInt().coerceAtLeast(0)
        val maxX = (cx + outer).toInt().coerceAtMost(hsv.cols() - 1)
        val minY = (cy - outer).toInt().coerceAtLeast(0)
        val maxY = (cy + outer).toInt().coerceAtMost(hsv.rows() - 1)
        var cloth = 0
        var total = 0
        for (y in minY..maxY step 2) for (x in minX..maxX step 2) {
            val dx = x - cx
            val dy = y - cy
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared < inner * inner || distanceSquared > outer * outer) continue
            val pixel = hsv.get(y, x) ?: continue
            val rawHueDistance = abs(pixel[0] - clothHue)
            val hueDistance = minOf(rawHueDistance, 180.0 - rawHueDistance)
            if (hueDistance < 15.0 && pixel[1] > 70.0 && pixel[2] > 65.0) cloth++
            total++
        }
        return if (total == 0) 0f else cloth.toFloat() / total
    }

    private fun maskFraction(mask: Mat, cx: Double, cy: Double, radius: Double): Float {
        val minX = (cx - radius).toInt().coerceAtLeast(0); val maxX = (cx + radius).toInt().coerceAtMost(mask.cols() - 1)
        val minY = (cy - radius).toInt().coerceAtLeast(0); val maxY = (cy + radius).toInt().coerceAtMost(mask.rows() - 1)
        var active = 0; var total = 0
        for (y in minY..maxY step 2) for (x in minX..maxX step 2) {
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy > radius * radius) continue
            if ((mask.get(y, x)?.get(0) ?: 0.0) > 0.0) active++
            total++
        }
        return if (total == 0) 0f else active.toFloat() / total
    }

    private fun dominantClothHue(hsv: Mat): Double {
        val histogram = IntArray(180)
        for (y in 0 until hsv.rows() step 5) for (x in 0 until hsv.cols() step 5) {
            val pixel = hsv.get(y, x) ?: continue
            if (pixel[1] > 80.0 && pixel[2] > 70.0) {
                histogram[pixel[0].toInt().coerceIn(0, 179)]++
            }
        }
        return histogram.indices.maxByOrNull { histogram[it] }?.toDouble() ?: 90.0
    }

    /** Finds the filled near-white circle; guide rings and line art score much lower. */
    fun findCueBall(table: Mat, balls: List<DetectedBall>): DetectedBall? {
        val hsv = Mat()
        Imgproc.cvtColor(table, hsv, Imgproc.COLOR_RGB2HSV)
        val whiteMask = Mat()
        Core.inRange(hsv, Scalar(0.0, 0.0, 155.0), Scalar(180.0, 75.0, 255.0), whiteMask)

        val gray = Mat()
        Imgproc.cvtColor(table, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(7.0, 7.0), 1.5)
        val circles = Mat()
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.2, 18.0, 100.0, 14.0, 8, 20)
        var cueCandidate: DetectedBall? = null
        var cueScore = 0f
        for (index in 0 until circles.cols()) {
            val circle = circles.get(0, index) ?: continue
            if (circle.size < 3) continue
            val whiteness = whiteFraction(whiteMask, circle[0], circle[1], circle[2] * 0.72)
            val score = whiteness * 100f + circle[2].toFloat()
            if (whiteness >= 0.55f && score > cueScore) {
                cueScore = score
                cueCandidate = DetectedBall(
                    circle[0].toFloat() * 2f,
                    circle[1].toFloat() * 2f,
                    circle[2].toFloat() * 2f,
                    whiteness
                )
            }
        }
        circles.release()
        gray.release()
        if (cueCandidate != null) {
            whiteMask.release()
            hsv.release()
            return cueCandidate
        }

        // Fallback for frames where the circle edge is partly hidden by the guide line.
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(whiteMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val whiteCandidate = contours.mapNotNull { contour ->
            val area = Imgproc.contourArea(contour)
            val points = MatOfPoint2f(*contour.toArray())
            val center = Point()
            val radius = FloatArray(1)
            Imgproc.minEnclosingCircle(points, center, radius)
            points.release()
            contour.release()
            val r = radius[0]
            if (area in 120.0..1800.0 && r in 8f..28f && area / (Math.PI * r * r) > 0.55) {
                DetectedBall(center.x.toFloat() * 2f, center.y.toFloat() * 2f, r * 2f, 0.9f)
            } else null
        }.maxByOrNull { it.radius }
        hierarchy.release(); whiteMask.release()

        if (whiteCandidate != null) {
            hsv.release()
            return whiteCandidate
        }
        val cue = balls.maxByOrNull { ball ->
            val x = (ball.x / 2f).toInt().coerceIn(0, hsv.cols() - 1)
            val y = (ball.y / 2f).toInt().coerceIn(0, hsv.rows() - 1)
            val value = hsv.get(y, x) ?: doubleArrayOf(0.0, 255.0, 0.0)
            (value[2] - value[1] * 0.70).toFloat()
        }
        hsv.release()
        return cue
    }

    /** Finds the cue endpoint and direction from the bright in-game aiming ray. */
    fun findAimObservation(table: Mat): AimObservation? {
        val cueBall = findCueBall(table, emptyList()) ?: return null
        val cueX = cueBall.x / 2.0
        val cueY = cueBall.y / 2.0
        val hsv = Mat()
        Imgproc.cvtColor(table, hsv, Imgproc.COLOR_RGB2HSV)
        val whiteMask = Mat()
        Core.inRange(hsv, Scalar(0.0, 0.0, 155.0), Scalar(180.0, 85.0, 255.0), whiteMask)
        val lines = Mat()
        Imgproc.HoughLinesP(whiteMask, lines, 1.0, Math.PI / 180.0, 45, 70.0, 24.0)
        var bestObservation: AimObservation? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (row in 0 until lines.rows()) {
            val line = lines.get(row, 0) ?: continue
            if (line.size < 4) continue
            val x1 = line[0]; val y1 = line[1]; val x2 = line[2]; val y2 = line[3]
            val length = hypot(x2 - x1, y2 - y1)
            if (length < 70.0) continue
            val distanceFromCue = lineDistance(cueX, cueY, x1, y1, x2, y2)
            if (distanceFromCue > 22.0) continue
            val distanceToFirst = hypot(x1 - cueX, y1 - cueY)
            val distanceToSecond = hypot(x2 - cueX, y2 - cueY)
            val farX = if (distanceToFirst >= distanceToSecond) x1 else x2
            val farY = if (distanceToFirst >= distanceToSecond) y1 else y2
            val score = length - distanceFromCue * 4.0
            if (score > bestScore) {
                bestScore = score
                bestObservation = AimObservation(
                    cueBall,
                    atan2((farY - cueY).toFloat(), (farX - cueX).toFloat())
                )
            }
        }
        lines.release(); whiteMask.release(); hsv.release()
        return bestObservation
    }

    private fun lineDistance(
        px: Double,
        py: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double
    ): Double {
        val length = hypot(x2 - x1, y2 - y1)
        if (length == 0.0) return Double.MAX_VALUE
        return abs((y2 - y1) * px - (x2 - x1) * py + x2 * y1 - y2 * x1) / length
    }

    private fun whiteFraction(mask: Mat, cx: Double, cy: Double, radius: Double): Float {
        var white = 0; var total = 0
        val minX = (cx - radius).toInt().coerceAtLeast(0); val maxX = (cx + radius).toInt().coerceAtMost(mask.cols() - 1)
        val minY = (cy - radius).toInt().coerceAtLeast(0); val maxY = (cy + radius).toInt().coerceAtMost(mask.rows() - 1)
        for (y in minY..maxY step 2) for (x in minX..maxX step 2) {
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy > radius * radius) continue
            if ((mask.get(y, x)?.get(0) ?: 0.0) > 0.0) white++
            total++
        }
        return if (total == 0) 0f else white.toFloat() / total
    }
}
