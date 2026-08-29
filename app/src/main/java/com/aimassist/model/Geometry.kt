package com.aimassist.model

data class Point2(val x: Float, val y: Float)

data class DetectedBall(
    val x: Float,
    val y: Float,
    val radius: Float,
    val confidence: Float = 1f
)

data class GuideLine(val from: Point2, val to: Point2, val kind: Kind) {
    enum class Kind { AIM, RAIL, OBJECT }
}
