package coredevices.pebble.ui.health

import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.Point

/**
 * Applies Catmull-Rom spline smoothing to create a smooth curve through points.
 * This produces a more visually appealing chart line.
 *
 * @param points The original data points
 * @param segments Number of interpolation segments between each pair of points
 * @return Smoothed list of points
 */
fun catmullRomSmooth(
    points: List<Point<Float, Float>>,
    segments: Int = 8
): List<Point<Float, Float>> {
    if (points.size < 3 || segments <= 0) return points

    val smoothed = mutableListOf<Point<Float, Float>>()
    for (i in 0 until points.lastIndex) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]

        if (i == 0) smoothed += p1

        for (j in 1..segments) {
            val t = j / segments.toFloat()
            val t2 = t * t
            val t3 = t2 * t

            fun blend(v0: Float, v1: Float, v2: Float, v3: Float): Float {
                return 0.5f * (
                    (2f * v1) +
                        (-v0 + v2) * t +
                        (2f * v0 - 5f * v1 + 4f * v2 - v3) * t2 +
                        (-v0 + 3f * v1 - 3f * v2 + v3) * t3
                    )
            }

            val x = blend(p0.x, p1.x, p2.x, p3.x)
            val y = blend(p0.y, p1.y, p2.y, p3.y)
            // Clamp to avoid spline undershooting below origin or below the segment bounds
            val lowerBound = maxOf(0f, minOf(p1.y, p2.y))
            val upperBound = maxOf(p1.y, p2.y)
            smoothed += DefaultPoint(x, y.coerceIn(lowerBound, upperBound))
        }
    }

    return smoothed
}
