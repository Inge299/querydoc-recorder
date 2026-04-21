package com.querydoc.lecturerecorder.photo

import android.graphics.BitmapFactory
import com.querydoc.lecturerecorder.config.AppConfig
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class HeuristicRectangleDetector : RectangleDetector {
    override fun detectRectangle(imagePath: String): CropRectangle? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = computeInSampleSize(
            srcWidth = bounds.outWidth,
            srcHeight = bounds.outHeight,
            maxSide = AppConfig.Photo.detectorMaxSidePx
        )
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return null

        val width = bitmap.width
        val height = bitmap.height
        if (width < 50 || height < 50) {
            bitmap.recycle()
            return null
        }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        val luminance = FloatArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luminance[i] = (0.299f * r + 0.587f * g + 0.114f * b)
        }

        val rowEnergy = FloatArray(height)
        val colEnergy = FloatArray(width)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val gx = abs(luminance[idx + 1] - luminance[idx - 1])
                val gy = abs(luminance[idx + width] - luminance[idx - width])
                val energy = gx + gy
                rowEnergy[y] += energy
                colEnergy[x] += energy
            }
        }

        val edgePoints = collectStrongEdgePoints(width, height, luminance)
        if (edgePoints.size < 40) return null
        val angle = dominantAngle(edgePoints)

        val rotated = edgePoints.map { p ->
            rotate(p.x.toFloat(), p.y.toFloat(), angle, width / 2f, height / 2f)
        }
        val xValues = rotated.map { it.first }.sorted()
        val yValues = rotated.map { it.second }.sorted()
        val leftQ = quantile(xValues, 0.08f)
        val rightQ = quantile(xValues, 0.92f)
        val topQ = quantile(yValues, 0.08f)
        val bottomQ = quantile(yValues, 0.92f)

        if (bottomQ - topQ < height / 6f || rightQ - leftQ < width / 6f) return null

        val smoothRows = smooth(rowEnergy, window = max(3, height / 80))
        val smoothCols = smooth(colEnergy, window = max(3, width / 80))
        val topIdx = findPeakInRange(smoothRows, start = height / 20, end = height / 2) ?: return null
        val bottomIdx = findPeakInRange(smoothRows, start = height / 2, end = height - height / 20) ?: return null
        val leftIdx = findPeakInRange(smoothCols, start = width / 20, end = width / 2) ?: return null
        val rightIdx = findPeakInRange(smoothCols, start = width / 2, end = width - width / 20) ?: return null

        val rowMedian = median(smoothRows)
        val colMedian = median(smoothCols)
        val confidence = min(
            (smoothRows[topIdx] + smoothRows[bottomIdx]) / (2f * max(1f, rowMedian)),
            (smoothCols[leftIdx] + smoothCols[rightIdx]) / (2f * max(1f, colMedian))
        )
        if (confidence < 0.75f) return null

        val adjustedRotated = if (confidence < AppConfig.Photo.detectorMinConfidenceRatio) {
            expandRectF(
                left = leftQ,
                top = topQ,
                right = rightQ,
                bottom = bottomQ,
                width = width,
                height = height,
                expandBy = max(width, height) / 35f
            )
        } else {
            RectF(leftQ, topQ, rightQ, bottomQ)
        }

        val scale = sampleSize.toFloat()
        val corners = listOf(
            adjustedRotated.left to adjustedRotated.top,
            adjustedRotated.right to adjustedRotated.top,
            adjustedRotated.right to adjustedRotated.bottom,
            adjustedRotated.left to adjustedRotated.bottom
        ).map { (x, y) ->
            val unrot = rotate(x, y, -angle, width / 2f, height / 2f)
            PointFInt(
                x = (unrot.first * scale).toInt().coerceIn(0, bounds.outWidth - 1),
                y = (unrot.second * scale).toInt().coerceIn(0, bounds.outHeight - 1)
            )
        }

        return CropRectangle(
            topLeft = corners[0],
            topRight = corners[1],
            bottomRight = corners[2],
            bottomLeft = corners[3]
        )
    }

    private fun expandRectF(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        width: Int,
        height: Int,
        expandBy: Float
    ): RectF {
        return RectF(
            left = (left - expandBy).coerceAtLeast(0f),
            top = (top - expandBy).coerceAtLeast(0f),
            right = (right + expandBy).coerceAtMost(width - 1f),
            bottom = (bottom + expandBy).coerceAtMost(height - 1f)
        )
    }

    private fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxSide: Int): Int {
        var inSampleSize = 1
        while (srcWidth / inSampleSize > maxSide || srcHeight / inSampleSize > maxSide) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun smooth(values: FloatArray, window: Int): FloatArray {
        val result = FloatArray(values.size)
        for (i in values.indices) {
            var sum = 0f
            var count = 0
            val start = max(0, i - window)
            val end = min(values.lastIndex, i + window)
            for (j in start..end) {
                sum += values[j]
                count++
            }
            result[i] = if (count == 0) values[i] else sum / count
        }
        return result
    }

    private fun findPeakInRange(values: FloatArray, start: Int, end: Int): Int? {
        if (start >= end || start < 0 || end > values.size) return null
        var bestIndex: Int? = null
        var bestValue = Float.MIN_VALUE
        for (i in start until end) {
            if (values[i] > bestValue) {
                bestValue = values[i]
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun median(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.copyOf().sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }

    private fun collectStrongEdgePoints(width: Int, height: Int, luminance: FloatArray): List<PointFInt> {
        val points = ArrayList<PointFInt>(width * height / 10)
        val threshold = 24f
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val gx = abs(luminance[idx + 1] - luminance[idx - 1])
                val gy = abs(luminance[idx + width] - luminance[idx - width])
                if (gx + gy > threshold) {
                    points.add(PointFInt(x, y))
                }
            }
        }
        return points
    }

    private fun dominantAngle(points: List<PointFInt>): Float {
        var meanX = 0.0
        var meanY = 0.0
        points.forEach {
            meanX += it.x
            meanY += it.y
        }
        meanX /= points.size
        meanY /= points.size

        var covXX = 0.0
        var covYY = 0.0
        var covXY = 0.0
        points.forEach {
            val dx = it.x - meanX
            val dy = it.y - meanY
            covXX += dx * dx
            covYY += dy * dy
            covXY += dx * dy
        }
        return (0.5 * atan2(2.0 * covXY, covXX - covYY)).toFloat()
    }

    private fun rotate(x: Float, y: Float, angleRad: Float, centerX: Float, centerY: Float): Pair<Float, Float> {
        val tx = x - centerX
        val ty = y - centerY
        val rx = tx * cos(angleRad) - ty * sin(angleRad)
        val ry = tx * sin(angleRad) + ty * cos(angleRad)
        return Pair(rx + centerX, ry + centerY)
    }

    private fun quantile(sortedValues: List<Float>, q: Float): Float {
        if (sortedValues.isEmpty()) return 0f
        val clampedQ = q.coerceIn(0f, 1f)
        val idx = (clampedQ * (sortedValues.size - 1)).toInt().coerceIn(0, sortedValues.lastIndex)
        return sortedValues[idx]
    }

    private data class RectF(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}
