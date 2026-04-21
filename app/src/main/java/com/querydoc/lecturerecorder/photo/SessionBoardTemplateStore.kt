package com.querydoc.lecturerecorder.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.querydoc.lecturerecorder.ui.CropSessionUi
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object SessionBoardTemplateStore {
    private const val TEMPLATE_WIDTH = 260
    private const val TEMPLATE_HEIGHT = 170
    private const val CONTEXT_EXPANSION_FACTOR = 1.10f

    @Volatile
    private var template: BoardTemplate? = null

    fun reset() {
        template = null
    }

    fun updateFromSession(session: CropSessionUi) {
        // Keep the first manually confirmed board as the session anchor template.
        if (template != null) return
        val bitmap = ImageOrientationUtils.decodeOrientedBitmap(session.sourceImagePath) ?: return
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        val boardQuad = session.toCropRectangle(bitmap.width, bitmap.height)
        val expandedQuad = scaleQuadAroundCenter(
            quad = boardQuad,
            factor = CONTEXT_EXPANSION_FACTOR,
            width = srcWidth,
            height = srcHeight
        )
        val warped = warp(bitmap, expandedQuad, TEMPLATE_WIDTH, TEMPLATE_HEIGHT)
        bitmap.recycle()
        val gray = grayscale(warped)
        val edge = gradientMagnitude(gray, TEMPLATE_WIDTH, TEMPLATE_HEIGHT)
        val tileSignature = computeTileSignature(gray, TEMPLATE_WIDTH, TEMPLATE_HEIGHT)
        warped.recycle()
        template = BoardTemplate(
            normalizedExpandedQuad = expandedQuad.toNormalized(bitmapWidth = srcWidth, bitmapHeight = srcHeight),
            gray = gray,
            edge = edge,
            tileSignature = tileSignature
        )
    }

    fun hasTemplate(): Boolean = template != null

    fun refineFromCurrent(imagePath: String, seed: CropRectangle): CropRectangle? {
        val bitmap = ImageOrientationUtils.decodeOrientedBitmap(imagePath) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        if (!seed.isInside(width, height)) {
            bitmap.recycle()
            return null
        }
        val luminance = grayscale(bitmap)
        val gradient = gradientMagnitude(luminance, width, height)
        bitmap.recycle()

        var current = seed
        repeat(2) {
            current = refineEdge(current, Edge.TOP, gradient, width, height)
            current = refineEdge(current, Edge.RIGHT, gradient, width, height)
            current = refineEdge(current, Edge.BOTTOM, gradient, width, height)
            current = refineEdge(current, Edge.LEFT, gradient, width, height)
        }
        if (!current.isInside(width, height)) return null
        if (polygonArea(current) < polygonArea(seed) * 0.35) return null
        return current
    }

    fun proposeByTap(
        imagePath: String,
        normalizedX: Float,
        normalizedY: Float,
        sensitivity: Float = 1.0f
    ): MatchResult? {
        val original = ImageOrientationUtils.decodeOrientedBitmap(imagePath) ?: return null
        val (bitmap, scaleToOriginal) = downscaleForFastDetection(original, maxSide = 960)
        if (bitmap !== original) original.recycle()

        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        val tapX = (normalizedX.coerceIn(0f, 1f) * imageWidth).coerceIn(0f, imageWidth - 1f)
        val tapY = (normalizedY.coerceIn(0f, 1f) * imageHeight).coerceIn(0f, imageHeight - 1f)
        val luminance = grayscale(bitmap)
        val gradient = gradientMagnitude(luminance, imageWidth, imageHeight)

        val expectedAspect = template?.normalizedExpandedQuad?.aspectRatio
            ?.let { it / CONTEXT_EXPANSION_FACTOR }
            ?.coerceIn(0.9f, 2.8f)
            ?: 1.6f
        val safeSensitivity = sensitivity.coerceIn(0.6f, 1.8f)
        val minAcceptedScore = (0.11f / safeSensitivity).coerceIn(0.05f, 0.18f)

        val angles = listOf(-28f, -20f, -14f, -8f, -4f, 0f, 4f, 8f, 14f, 20f, 28f)
        var bestRect: CropRectangle? = null
        var bestScore = Float.MIN_VALUE

        for (angle in angles) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val ux = cos(rad)
            val uy = sin(rad)
            val vx = -sin(rad)
            val vy = cos(rad)

            val minHalf = (min(imageWidth, imageHeight) * 0.08f).coerceAtLeast(24f)
            val maxHalf = (min(imageWidth, imageHeight) * 0.45f).coerceAtLeast(92f)

            val right = estimateEdgeDistanceOnRay(
                startX = tapX, startY = tapY, dirX = ux, dirY = uy,
                normalX = vx, normalY = vy, width = imageWidth, height = imageHeight,
                luminance = luminance, gradient = gradient, minDistance = minHalf, maxDistance = maxHalf,
                minAcceptedScore = minAcceptedScore
            ) ?: continue
            val left = estimateEdgeDistanceOnRay(
                startX = tapX, startY = tapY, dirX = -ux, dirY = -uy,
                normalX = vx, normalY = vy, width = imageWidth, height = imageHeight,
                luminance = luminance, gradient = gradient, minDistance = minHalf, maxDistance = maxHalf,
                minAcceptedScore = minAcceptedScore
            ) ?: continue
            val down = estimateEdgeDistanceOnRay(
                startX = tapX, startY = tapY, dirX = vx, dirY = vy,
                normalX = ux, normalY = uy, width = imageWidth, height = imageHeight,
                luminance = luminance, gradient = gradient, minDistance = minHalf * 0.8f, maxDistance = maxHalf,
                minAcceptedScore = minAcceptedScore
            ) ?: continue
            val up = estimateEdgeDistanceOnRay(
                startX = tapX, startY = tapY, dirX = -vx, dirY = -vy,
                normalX = ux, normalY = uy, width = imageWidth, height = imageHeight,
                luminance = luminance, gradient = gradient, minDistance = minHalf * 0.8f, maxDistance = maxHalf,
                minAcceptedScore = minAcceptedScore
            ) ?: continue

            val w = right + left
            val h = down + up
            if (w < 50f || h < 40f) continue

            val cx = tapX + ux * (right - left) * 0.5f + vx * (down - up) * 0.5f
            val cy = tapY + uy * (right - left) * 0.5f + vy * (down - up) * 0.5f
            val candidate = rotatedRectangle(cx, cy, w, h, angle)
            if (!candidate.isInside(imageWidth, imageHeight)) continue

            val score = 0.50f * edgeAlignmentScore(gradient, imageWidth, imageHeight, candidate) +
                0.30f * borderContrastScore(luminance, imageWidth, imageHeight, candidate) +
                0.20f * geometricScore(candidate, imageWidth, imageHeight, expectedAspect)
            if (score > bestScore) {
                bestScore = score
                bestRect = candidate
            }
        }

        bitmap.recycle()
        val scaledBest = bestRect ?: return null
        val refined = refineFromCurrentScaled(
            rectangle = scaledBest,
            scaleToOriginal = scaleToOriginal,
            originalWidth = (imageWidth * scaleToOriginal).toInt().coerceAtLeast(imageWidth),
            originalHeight = (imageHeight * scaleToOriginal).toInt().coerceAtLeast(imageHeight)
        )
        return MatchResult(rectangle = refined, score = bestScore)
    }

    private fun downscaleForFastDetection(source: Bitmap, maxSide: Int): Pair<Bitmap, Float> {
        val srcW = source.width
        val srcH = source.height
        val maxSrc = max(srcW, srcH)
        if (maxSrc <= maxSide) return source to 1f
        val scale = maxSide.toFloat() / maxSrc.toFloat()
        val dstW = (srcW * scale).toInt().coerceAtLeast(1)
        val dstH = (srcH * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, dstW, dstH, true)
        val scaleToOriginal = srcW.toFloat() / dstW.toFloat()
        return scaled to scaleToOriginal
    }

    private fun refineFromCurrentScaled(
        rectangle: CropRectangle,
        scaleToOriginal: Float,
        originalWidth: Int,
        originalHeight: Int
    ): CropRectangle {
        if (scaleToOriginal <= 1.001f) return rectangle
        fun up(p: PointFInt): PointFInt {
            return PointFInt(
                x = (p.x * scaleToOriginal).toInt().coerceIn(0, originalWidth - 1),
                y = (p.y * scaleToOriginal).toInt().coerceIn(0, originalHeight - 1)
            )
        }
        return CropRectangle(
            topLeft = up(rectangle.topLeft),
            topRight = up(rectangle.topRight),
            bottomRight = up(rectangle.bottomRight),
            bottomLeft = up(rectangle.bottomLeft)
        )
    }

    fun proposeByMatching(imagePath: String): MatchResult? {
        val tpl = template ?: return null
        val bitmap = ImageOrientationUtils.decodeOrientedBitmap(imagePath) ?: return null
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        val luminance = grayscale(bitmap)
        val gradient = gradientMagnitude(luminance, imageWidth, imageHeight)

        val candidates = buildTemplateTrackingCandidates(
            width = imageWidth,
            height = imageHeight,
            template = tpl
        ) + buildTemplateSearchCandidates(
            width = imageWidth,
            height = imageHeight,
            template = tpl
        )

        val result = findBestCandidate(
            bitmap = bitmap,
            luminance = luminance,
            gradient = gradient,
            candidates = candidates,
            tpl = tpl,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            minCombinedThreshold = 0.68f,
            requireTemplateThresholds = true
        )
        bitmap.recycle()
        return result
    }

    private fun findBestCandidate(
        bitmap: Bitmap,
        luminance: FloatArray,
        gradient: FloatArray,
        candidates: List<CropRectangle>,
        tpl: BoardTemplate?,
        imageWidth: Int,
        imageHeight: Int,
        minCombinedThreshold: Float,
        requireTemplateThresholds: Boolean
    ): MatchResult? {
        val scored = ArrayList<RawCandidateScore>(candidates.size)
        var maxEdge = 1e-4f
        var maxContrast = 1e-4f
        val expectedAspect = tpl?.normalizedExpandedQuad?.aspectRatio?.let { it / CONTEXT_EXPANSION_FACTOR } ?: 1.6f
        for (candidate in candidates) {
            if (!candidate.isInside(imageWidth, imageHeight)) continue
            val edge = edgeAlignmentScore(gradient, imageWidth, imageHeight, candidate)
            val contrast = borderContrastScore(luminance, imageWidth, imageHeight, candidate)
            val shape = geometricScore(candidate, imageWidth, imageHeight, expectedAspect)
            if (shape <= 0f) continue
            maxEdge = max(maxEdge, edge)
            maxContrast = max(maxContrast, contrast)
            scored.add(RawCandidateScore(candidate, edge, contrast, shape))
        }
        if (scored.isEmpty()) return null

        val top = scored
            .map {
                val edgeNorm = it.edge / maxEdge
                val contrastNorm = it.contrast / maxContrast
                val cvScore = 0.52f * edgeNorm + 0.30f * contrastNorm + 0.18f * it.shape
                CandidateScore(it.rectangle, cvScore)
            }
            .sortedByDescending { it.score }
            .take(18)

        var bestBoardRect: CropRectangle? = null
        var bestCombined = Float.MIN_VALUE
        var bestGraySimilarity = -1f
        var bestEdgeSimilarity = -1f

        for ((boardCandidate, cvScore) in top) {
            if (tpl == null) {
                if (cvScore > bestCombined) {
                    bestCombined = cvScore
                    bestBoardRect = boardCandidate
                    bestGraySimilarity = 1f
                    bestEdgeSimilarity = 1f
                }
                continue
            }

            val expandedCandidate = scaleQuadAroundCenter(
                quad = boardCandidate,
                factor = CONTEXT_EXPANSION_FACTOR,
                width = imageWidth,
                height = imageHeight
            )
            if (!expandedCandidate.isInside(imageWidth, imageHeight)) continue
            val warped = warp(bitmap, expandedCandidate, TEMPLATE_WIDTH, TEMPLATE_HEIGHT)
            val gray = grayscale(warped)
            val edge = gradientMagnitude(gray, TEMPLATE_WIDTH, TEMPLATE_HEIGHT)
            val tile = computeTileSignature(gray, TEMPLATE_WIDTH, TEMPLATE_HEIGHT)
            warped.recycle()

            val graySimilarity = (normalizedCrossCorrelation(gray, tpl.gray).coerceIn(-1f, 1f) + 1f) * 0.5f
            val edgeSimilarity = (normalizedCrossCorrelation(edge, tpl.edge).coerceIn(-1f, 1f) + 1f) * 0.5f
            val tileSimilarity = tileSignatureSimilarity(tile, tpl.tileSignature)
            val combined = 0.40f * graySimilarity + 0.28f * edgeSimilarity + 0.20f * tileSimilarity + 0.12f * cvScore
            if (combined > bestCombined) {
                bestCombined = combined
                bestBoardRect = boardCandidate
                bestGraySimilarity = graySimilarity
                bestEdgeSimilarity = edgeSimilarity
            }
        }

        val rect = bestBoardRect ?: return null
        if (bestCombined < minCombinedThreshold) return null
        if (requireTemplateThresholds && (bestGraySimilarity < 0.44f || bestEdgeSimilarity < 0.36f)) return null
        return MatchResult(rectangle = rect, score = bestCombined)
    }

    private fun buildGlobalCandidates(width: Int, height: Int, templateAspectRatio: Float): List<CropRectangle> {
        val candidates = ArrayList<CropRectangle>(6_000)
        val centerXs = listOf(0.18f, 0.34f, 0.50f, 0.66f, 0.82f).map { it * width }
        val centerYs = listOf(0.20f, 0.36f, 0.52f, 0.68f, 0.84f).map { it * height }
        val scales = listOf(0.34f, 0.46f, 0.58f, 0.70f, 0.82f)
        val angles = listOf(-24f, -14f, -7f, 0f, 7f, 14f, 24f)
        val baseAspect = templateAspectRatio.coerceIn(1.0f, 2.5f)
        val aspects = listOf(baseAspect * 0.86f, baseAspect, baseAspect * 1.16f)
            .map { it.coerceIn(0.95f, 2.8f) }
            .distinct()

        for (cy in centerYs) {
            for (cx in centerXs) {
                for (scale in scales) {
                    val h = (height * scale).coerceAtLeast(48f)
                    for (aspect in aspects) {
                        val w = (h * aspect).coerceAtLeast(64f)
                        if (w > width * 0.96f || h > height * 0.96f) continue
                        for (angle in angles) {
                            candidates.add(rotatedRectangle(cx, cy, w, h, angle))
                        }
                    }
                }
            }
        }
        return candidates
    }

    private fun buildTemplateTrackingCandidates(width: Int, height: Int, template: BoardTemplate): List<CropRectangle> {
        val baseExpanded = template.normalizedExpandedQuad.toAbsolute(width, height)
        val boardBase = scaleQuadAroundCenter(
            quad = baseExpanded,
            factor = 1f / CONTEXT_EXPANSION_FACTOR,
            width = width,
            height = height
        )
        return perturbWide(boardBase, width, height)
    }

    private fun buildTemplateSearchCandidates(width: Int, height: Int, template: BoardTemplate): List<CropRectangle> {
        val candidates = ArrayList<CropRectangle>(900)
        val baseExpanded = template.normalizedExpandedQuad.toAbsolute(width, height)
        val boardBase = scaleQuadAroundCenter(
            quad = baseExpanded,
            factor = 1f / CONTEXT_EXPANSION_FACTOR,
            width = width,
            height = height
        )
        val baseWidth = (distance(boardBase.topLeft, boardBase.topRight) + distance(boardBase.bottomLeft, boardBase.bottomRight)) * 0.5f
        val baseHeight = (distance(boardBase.topLeft, boardBase.bottomLeft) + distance(boardBase.topRight, boardBase.bottomRight)) * 0.5f
        val baseAngle = dominantAngle(boardBase)
        val centerXs = listOf(0.14f, 0.30f, 0.46f, 0.62f, 0.78f, 0.90f).map { it * width }
        val centerYs = listOf(0.14f, 0.30f, 0.46f, 0.62f, 0.78f, 0.90f).map { it * height }
        val scales = listOf(0.82f, 0.94f, 1.0f, 1.10f, 1.20f)
        val angleOffsets = listOf(-12f, -6f, 0f, 6f, 12f)

        for (cy in centerYs) {
            for (cx in centerXs) {
                for (s in scales) {
                    val w = (baseWidth * s).coerceAtLeast(70f)
                    val h = (baseHeight * s).coerceAtLeast(52f)
                    if (w > width * 0.98f || h > height * 0.98f) continue
                    for (a in angleOffsets) {
                        val base = rotatedRectangle(cx, cy, w, h, baseAngle + a)
                        candidates.add(base)
                        candidates.addAll(perspectiveVariants(base, width, height))
                    }
                }
            }
        }
        return candidates
    }

    private fun buildTapSeedCandidates(
        tapX: Float,
        tapY: Float,
        width: Int,
        height: Int,
        boardAspectRatio: Float,
        luminance: FloatArray,
        gradient: FloatArray
    ): List<CropRectangle> {
        val candidates = ArrayList<CropRectangle>(1000)
        val angles = listOf(-28f, -18f, -10f, -6f, 0f, 6f, 10f, 18f, 28f)
        val minHalf = (min(width, height) * 0.10f).coerceAtLeast(36f)
        val maxHalf = (min(width, height) * 0.48f).coerceAtLeast(110f)

        for (angle in angles) {
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val ux = cos(rad)
            val uy = sin(rad)
            val vx = -sin(rad)
            val vy = cos(rad)

            val right = estimateEdgeDistanceOnRay(
                startX = tapX, startY = tapY, dirX = ux, dirY = uy,
                normalX = vx, normalY = vy, width = width, height = height,
                luminance = luminance, gradient = gradient, minDistance = minHalf, maxDistance = maxHalf
            )
            val left = estimateEdgeDistanceOnRay(
                startX = tapX, startY = tapY, dirX = -ux, dirY = -uy,
                normalX = vx, normalY = vy, width = width, height = height,
                luminance = luminance, gradient = gradient, minDistance = minHalf, maxDistance = maxHalf
            )
            val down = estimateEdgeDistanceOnRay(
                startX = tapX, startY = tapY, dirX = vx, dirY = vy,
                normalX = ux, normalY = uy, width = width, height = height,
                luminance = luminance, gradient = gradient, minDistance = minHalf * 0.7f, maxDistance = maxHalf
            )
            val up = estimateEdgeDistanceOnRay(
                startX = tapX, startY = tapY, dirX = -vx, dirY = -vy,
                normalX = ux, normalY = uy, width = width, height = height,
                luminance = luminance, gradient = gradient, minDistance = minHalf * 0.7f, maxDistance = maxHalf
            )
            if (right == null || left == null || down == null || up == null) continue

            val widthCandidate = right + left
            val heightCandidate = down + up
            if (widthCandidate < 70f || heightCandidate < 50f) continue
            val measuredAspect = (widthCandidate / heightCandidate).coerceIn(0.5f, 3.5f)
            val aspectPenalty = abs(measuredAspect - boardAspectRatio)
            if (aspectPenalty > 1.15f) continue

            val centerX = tapX + ux * (right - left) * 0.5f + vx * (down - up) * 0.5f
            val centerY = tapY + uy * (right - left) * 0.5f + vy * (down - up) * 0.5f

            val base = rotatedRectangle(centerX, centerY, widthCandidate, heightCandidate, angle)
            candidates.add(base)

            val localScales = listOf(0.95f, 1.0f, 1.05f)
            val localAngles = listOf(-3f, 0f, 3f)
            for (s in localScales) {
                for (da in localAngles) {
                    val rw = (widthCandidate * s).coerceAtLeast(64f)
                    val rh = (heightCandidate * s).coerceAtLeast(48f)
                    candidates.add(rotatedRectangle(centerX, centerY, rw, rh, angle + da))
                }
            }
        }

        if (candidates.isEmpty()) {
            val fallbackScales = listOf(0.28f, 0.40f, 0.52f, 0.64f)
            for (scale in fallbackScales) {
                val h = (height * scale).coerceAtLeast(48f)
                val w = (h * boardAspectRatio).coerceAtLeast(64f)
                if (w > width * 0.95f || h > height * 0.95f) continue
                for (angle in angles) {
                    val rect = rotatedRectangle(tapX, tapY, w, h, angle)
                    if (rect.containsPoint(tapX.toInt(), tapY.toInt())) {
                        candidates.add(rect)
                    }
                }
            }
        }
        return candidates
    }

    private fun estimateEdgeDistanceOnRay(
        startX: Float,
        startY: Float,
        dirX: Float,
        dirY: Float,
        normalX: Float,
        normalY: Float,
        width: Int,
        height: Int,
        luminance: FloatArray,
        gradient: FloatArray,
        minDistance: Float,
        maxDistance: Float,
        minAcceptedScore: Float = 0.09f
    ): Float? {
        val maxD = maxDistance.toInt().coerceAtMost(max(width, height))
        val minD = minDistance.toInt().coerceAtLeast(12)
        var bestDistance = -1f
        var bestScore = 0f
        val edgeSampleOffset = (min(width, height) * 0.010f).coerceAtLeast(2f)

        var d = minD
        while (d <= maxD) {
            val px = startX + dirX * d
            val py = startY + dirY * d
            if (px < 2f || py < 2f || px >= width - 2f || py >= height - 2f) break
            val xi = px.toInt().coerceIn(0, width - 1)
            val yi = py.toInt().coerceIn(0, height - 1)
            val g = gradient[yi * width + xi]

            val xOut = (px + normalX * edgeSampleOffset).toInt().coerceIn(0, width - 1)
            val yOut = (py + normalY * edgeSampleOffset).toInt().coerceIn(0, height - 1)
            val xIn = (px - normalX * edgeSampleOffset).toInt().coerceIn(0, width - 1)
            val yIn = (py - normalY * edgeSampleOffset).toInt().coerceIn(0, height - 1)
            val c = abs(luminance[yOut * width + xOut] - luminance[yIn * width + xIn])

            val score = 0.68f * g + 0.32f * c
            if (score > bestScore) {
                bestScore = score
                bestDistance = d.toFloat()
            }
            d += 2
        }

        if (bestDistance < 0f) return null
        return if (bestScore > minAcceptedScore) bestDistance else null
    }

    private fun gradientMagnitude(luma: FloatArray, width: Int, height: Int): FloatArray {
        val grad = FloatArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val gx = abs(luma[idx + 1] - luma[idx - 1])
                val gy = abs(luma[idx + width] - luma[idx - width])
                grad[idx] = sqrt(gx * gx + gy * gy)
            }
        }
        return grad
    }

    private fun edgeAlignmentScore(
        gradient: FloatArray,
        width: Int,
        height: Int,
        quad: CropRectangle
    ): Float {
        fun sampleEdge(a: PointFInt, b: PointFInt): Float {
            val steps = 32
            var sum = 0f
            for (i in 0..steps) {
                val t = i.toFloat() / steps.toFloat()
                val x = (a.x + (b.x - a.x) * t).toInt().coerceIn(0, width - 1)
                val y = (a.y + (b.y - a.y) * t).toInt().coerceIn(0, height - 1)
                sum += gradient[y * width + x]
            }
            return sum / (steps + 1)
        }

        val top = sampleEdge(quad.topLeft, quad.topRight)
        val right = sampleEdge(quad.topRight, quad.bottomRight)
        val bottom = sampleEdge(quad.bottomLeft, quad.bottomRight)
        val left = sampleEdge(quad.topLeft, quad.bottomLeft)
        return (top + right + bottom + left) / 4f
    }

    private fun refineEdge(
        quad: CropRectangle,
        edge: Edge,
        gradient: FloatArray,
        width: Int,
        height: Int
    ): CropRectangle {
        val baseW = (distance(quad.topLeft, quad.topRight) + distance(quad.bottomLeft, quad.bottomRight)) * 0.5f
        val baseH = (distance(quad.topLeft, quad.bottomLeft) + distance(quad.topRight, quad.bottomRight)) * 0.5f
        val maxShift = (min(baseW, baseH) * 0.18f).coerceAtLeast(6f)
        val shifts = listOf(-1f, -0.75f, -0.5f, -0.25f, 0f, 0.25f, 0.5f, 0.75f, 1f).map { it * maxShift }

        var best = quad
        var bestScore = edgeAlignmentScore(gradient, width, height, quad)
        for (shift in shifts) {
            val candidate = moveEdgeParallel(quad, edge, shift, width, height) ?: continue
            if (!candidate.isInside(width, height)) continue
            val area = polygonArea(candidate)
            if (area < 2000.0) continue
            val score = edgeAlignmentScore(gradient, width, height, candidate)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun moveEdgeParallel(
        quad: CropRectangle,
        edge: Edge,
        shift: Float,
        width: Int,
        height: Int
    ): CropRectangle? {
        fun shiftPair(a: PointFInt, b: PointFInt): Pair<PointFInt, PointFInt> {
            val ex = (b.x - a.x).toFloat()
            val ey = (b.y - a.y).toFloat()
            val len = sqrt(ex * ex + ey * ey).coerceAtLeast(1f)
            val nx = -ey / len
            val ny = ex / len
            val na = PointFInt(
                (a.x + nx * shift).toInt().coerceIn(0, width - 1),
                (a.y + ny * shift).toInt().coerceIn(0, height - 1)
            )
            val nb = PointFInt(
                (b.x + nx * shift).toInt().coerceIn(0, width - 1),
                (b.y + ny * shift).toInt().coerceIn(0, height - 1)
            )
            return na to nb
        }

        return when (edge) {
            Edge.TOP -> {
                val (na, nb) = shiftPair(quad.topLeft, quad.topRight)
                quad.copy(topLeft = na, topRight = nb)
            }
            Edge.RIGHT -> {
                val (na, nb) = shiftPair(quad.topRight, quad.bottomRight)
                quad.copy(topRight = na, bottomRight = nb)
            }
            Edge.BOTTOM -> {
                val (na, nb) = shiftPair(quad.bottomLeft, quad.bottomRight)
                quad.copy(bottomLeft = na, bottomRight = nb)
            }
            Edge.LEFT -> {
                val (na, nb) = shiftPair(quad.topLeft, quad.bottomLeft)
                quad.copy(topLeft = na, bottomLeft = nb)
            }
        }
    }

    private fun borderContrastScore(
        luma: FloatArray,
        width: Int,
        height: Int,
        quad: CropRectangle
    ): Float {
        fun samplePair(a: PointFInt, b: PointFInt): Float {
            val steps = 30
            val dx = (b.x - a.x).toFloat()
            val dy = (b.y - a.y).toFloat()
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val nx = -dy / length
            val ny = dx / length
            val offset = (min(width, height) * 0.012f).coerceAtLeast(2f)
            var sum = 0f
            for (i in 0..steps) {
                val t = i.toFloat() / steps.toFloat()
                val px = a.x + dx * t
                val py = a.y + dy * t
                val x1 = (px + nx * offset).toInt().coerceIn(0, width - 1)
                val y1 = (py + ny * offset).toInt().coerceIn(0, height - 1)
                val x2 = (px - nx * offset).toInt().coerceIn(0, width - 1)
                val y2 = (py - ny * offset).toInt().coerceIn(0, height - 1)
                sum += abs(luma[y1 * width + x1] - luma[y2 * width + x2])
            }
            return sum / (steps + 1)
        }

        val top = samplePair(quad.topLeft, quad.topRight)
        val right = samplePair(quad.topRight, quad.bottomRight)
        val bottom = samplePair(quad.bottomLeft, quad.bottomRight)
        val left = samplePair(quad.topLeft, quad.bottomLeft)
        return (top + right + bottom + left) / 4f
    }

    private fun geometricScore(
        quad: CropRectangle,
        imageWidth: Int,
        imageHeight: Int,
        expectedAspectRatio: Float
    ): Float {
        val area = polygonArea(quad).toFloat().coerceAtLeast(1f)
        val minArea = (imageWidth * imageHeight * 0.07f).coerceAtLeast(5_000f)
        if (area < minArea) return 0f

        val angleScore = perspectiveFriendlyAngleScore(quad)
        val balanceScore = oppositeEdgeBalanceScore(quad)
        val avgWidth = (distance(quad.topLeft, quad.topRight) + distance(quad.bottomLeft, quad.bottomRight)) * 0.5f
        val avgHeight = (distance(quad.topLeft, quad.bottomLeft) + distance(quad.topRight, quad.bottomRight)) * 0.5f
        if (avgHeight < 1f || avgWidth < 1f) return 0f
        val ratio = (avgWidth / avgHeight).coerceAtLeast(0.3f)
        val ratioDiff = abs(ratio - expectedAspectRatio.coerceIn(0.8f, 3.2f))
        val ratioScore = exp((-ratioDiff * 1.05f).toDouble()).toFloat()
        val areaScore = (area / (imageWidth * imageHeight * 0.52f)).coerceIn(0f, 1f)
        return (0.26f * angleScore + 0.30f * ratioScore + 0.24f * balanceScore + 0.20f * areaScore).coerceIn(0f, 1f)
    }

    private fun polygonArea(quad: CropRectangle): Double {
        val pts = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        var sum = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            sum += (a.x * b.y - b.x * a.y).toDouble()
        }
        return abs(sum) * 0.5
    }

    private fun perspectiveFriendlyAngleScore(quad: CropRectangle): Float {
        val pts = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        var acc = 0f
        for (i in pts.indices) {
            val prev = pts[(i + pts.size - 1) % pts.size]
            val cur = pts[i]
            val next = pts[(i + 1) % pts.size]
            val ax = prev.x - cur.x.toFloat()
            val ay = prev.y - cur.y.toFloat()
            val bx = next.x - cur.x.toFloat()
            val by = next.y - cur.y.toFloat()
            val dot = ax * bx + ay * by
            val na = sqrt(ax * ax + ay * ay).coerceAtLeast(1f)
            val nb = sqrt(bx * bx + by * by).coerceAtLeast(1f)
            val cosA = (dot / (na * nb)).coerceIn(-1f, 1f)
            val angle = abs(Math.toDegrees(atan2(sqrt((1f - cosA * cosA).coerceAtLeast(0f).toDouble()), cosA.toDouble())).toFloat())
            val score = 1f - (abs(angle - 90f) / 70f).coerceIn(0f, 1f)
            acc += score
        }
        return (acc / 4f).coerceIn(0f, 1f)
    }

    private fun oppositeEdgeBalanceScore(quad: CropRectangle): Float {
        val top = distance(quad.topLeft, quad.topRight).coerceAtLeast(1f)
        val bottom = distance(quad.bottomLeft, quad.bottomRight).coerceAtLeast(1f)
        val left = distance(quad.topLeft, quad.bottomLeft).coerceAtLeast(1f)
        val right = distance(quad.topRight, quad.bottomRight).coerceAtLeast(1f)
        val horizRatio = min(top, bottom) / max(top, bottom)
        val vertRatio = min(left, right) / max(left, right)
        return ((horizRatio + vertRatio) * 0.5f).coerceIn(0f, 1f)
    }

    private fun rotatedRectangle(centerX: Float, centerY: Float, width: Float, height: Float, angleDeg: Float): CropRectangle {
        val hw = width * 0.5f
        val hh = height * 0.5f
        val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        fun rotateLocal(x: Float, y: Float): PointFInt {
            val rx = centerX + x * c - y * s
            val ry = centerY + x * s + y * c
            return PointFInt(rx.toInt(), ry.toInt())
        }
        return CropRectangle(
            topLeft = rotateLocal(-hw, -hh),
            topRight = rotateLocal(hw, -hh),
            bottomRight = rotateLocal(hw, hh),
            bottomLeft = rotateLocal(-hw, hh)
        )
    }

    private fun perspectiveVariants(base: CropRectangle, width: Int, height: Int): List<CropRectangle> {
        val out = ArrayList<CropRectangle>(4)
        val cx = (base.topLeft.x + base.topRight.x + base.bottomRight.x + base.bottomLeft.x) / 4f
        val cy = (base.topLeft.y + base.topRight.y + base.bottomRight.y + base.bottomLeft.y) / 4f
        val baseW = (distance(base.topLeft, base.topRight) + distance(base.bottomLeft, base.bottomRight)) * 0.5f
        val baseH = (distance(base.topLeft, base.bottomLeft) + distance(base.topRight, base.bottomRight)) * 0.5f
        val hw = (baseW * 0.5f).coerceAtLeast(1f)
        val hh = (baseH * 0.5f).coerceAtLeast(1f)
        val strengths = listOf(-0.12f, 0f, 0.12f)

        fun warpPoint(p: PointFInt, kx: Float, ky: Float, sx: Float, sy: Float): PointFInt {
            val nx = ((p.x - cx) / hw).coerceIn(-2.2f, 2.2f)
            val ny = ((p.y - cy) / hh).coerceIn(-2.2f, 2.2f)
            val dx = (nx * ny * kx + ny * sx) * hw * 0.55f
            val dy = (nx * ny * ky + nx * sy) * hh * 0.55f
            return PointFInt(
                x = (p.x + dx).toInt().coerceIn(0, width - 1),
                y = (p.y + dy).toInt().coerceIn(0, height - 1)
            )
        }

        for (kx in strengths) {
            for (ky in listOf(-0.08f, 0f, 0.08f)) {
                val q = CropRectangle(
                    topLeft = warpPoint(base.topLeft, kx, ky, sx = 0f, sy = 0f),
                    topRight = warpPoint(base.topRight, kx, ky, sx = 0f, sy = 0f),
                    bottomRight = warpPoint(base.bottomRight, kx, ky, sx = 0f, sy = 0f),
                    bottomLeft = warpPoint(base.bottomLeft, kx, ky, sx = 0f, sy = 0f)
                )
                out.add(q)
                if (out.size >= 4) return out
            }
        }
        return out
    }

    private fun distance(a: PointFInt, b: PointFInt): Float {
        val dx = (b.x - a.x).toFloat()
        val dy = (b.y - a.y).toFloat()
        return sqrt(dx * dx + dy * dy)
    }

    private fun dominantAngle(quad: CropRectangle): Float {
        val topDx = (quad.topRight.x - quad.topLeft.x).toFloat()
        val topDy = (quad.topRight.y - quad.topLeft.y).toFloat()
        val bottomDx = (quad.bottomRight.x - quad.bottomLeft.x).toFloat()
        val bottomDy = (quad.bottomRight.y - quad.bottomLeft.y).toFloat()
        val ax = (topDx + bottomDx) * 0.5f
        val ay = (topDy + bottomDy) * 0.5f
        return Math.toDegrees(atan2(ay.toDouble(), ax.toDouble())).toFloat()
    }

    private fun scaleQuadAroundCenter(quad: CropRectangle, factor: Float, width: Int, height: Int): CropRectangle {
        val cx = (quad.topLeft.x + quad.topRight.x + quad.bottomRight.x + quad.bottomLeft.x) / 4f
        val cy = (quad.topLeft.y + quad.topRight.y + quad.bottomRight.y + quad.bottomLeft.y) / 4f
        fun scalePoint(p: PointFInt): PointFInt {
            val nx = cx + (p.x - cx) * factor
            val ny = cy + (p.y - cy) * factor
            return PointFInt(
                x = nx.toInt().coerceIn(0, width - 1),
                y = ny.toInt().coerceIn(0, height - 1)
            )
        }
        return CropRectangle(
            topLeft = scalePoint(quad.topLeft),
            topRight = scalePoint(quad.topRight),
            bottomRight = scalePoint(quad.bottomRight),
            bottomLeft = scalePoint(quad.bottomLeft)
        )
    }

    private fun perturbWide(base: CropRectangle, width: Int, height: Int): List<CropRectangle> {
        val centerX = (base.topLeft.x + base.topRight.x + base.bottomRight.x + base.bottomLeft.x) / 4f
        val centerY = (base.topLeft.y + base.topRight.y + base.bottomRight.y + base.bottomLeft.y) / 4f
        val points = listOf(base.topLeft, base.topRight, base.bottomRight, base.bottomLeft).map {
            Pair(it.x - centerX, it.y - centerY)
        }

        val scales = listOf(0.78f, 0.90f, 1.0f, 1.12f, 1.24f)
        val shiftsX = listOf(-0.24f, -0.12f, 0f, 0.12f, 0.24f).map { it * width }
        val shiftsY = listOf(-0.24f, -0.12f, 0f, 0.12f, 0.24f).map { it * height }
        val angles = listOf(-18f, -10f, -4f, 0f, 4f, 10f, 18f)

        val out = ArrayList<CropRectangle>(scales.size * shiftsX.size * shiftsY.size * angles.size)
        for (scale in scales) {
            for (dx in shiftsX) {
                for (dy in shiftsY) {
                    for (angle in angles) {
                        val rad = Math.toRadians(angle.toDouble()).toFloat()
                        val transformed = points.map { (x, y) ->
                            val sx = x * scale
                            val sy = y * scale
                            val rx = sx * cos(rad) - sy * sin(rad)
                            val ry = sx * sin(rad) + sy * cos(rad)
                            PointFInt((centerX + dx + rx).toInt(), (centerY + dy + ry).toInt())
                        }
                        out.add(
                            CropRectangle(
                                topLeft = transformed[0],
                                topRight = transformed[1],
                                bottomRight = transformed[2],
                                bottomLeft = transformed[3]
                            )
                        )
                    }
                }
            }
        }
        return out
    }

    private fun warp(source: Bitmap, quad: CropRectangle, targetWidth: Int, targetHeight: Int): Bitmap {
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val src = floatArrayOf(
            quad.topLeft.x.toFloat(), quad.topLeft.y.toFloat(),
            quad.topRight.x.toFloat(), quad.topRight.y.toFloat(),
            quad.bottomRight.x.toFloat(), quad.bottomRight.y.toFloat(),
            quad.bottomLeft.x.toFloat(), quad.bottomLeft.y.toFloat()
        )
        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )
        val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
        Canvas(output).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun grayscale(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return FloatArray(pixels.size) { idx ->
            val c = pixels[idx]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }
    }

    private fun normalizedCrossCorrelation(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return -1f
        var meanA = 0f
        var meanB = 0f
        for (i in a.indices) {
            meanA += a[i]
            meanB += b[i]
        }
        meanA /= a.size
        meanB /= b.size

        var num = 0f
        var denA = 0f
        var denB = 0f
        for (i in a.indices) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            num += da * db
            denA += da * da
            denB += db * db
        }
        val denom = sqrt(denA * denB).coerceAtLeast(1e-6f)
        return (num / denom).coerceIn(-1f, 1f)
    }

    private fun computeTileSignature(values: FloatArray, width: Int, height: Int): FloatArray {
        val gridX = 6
        val gridY = 4
        val out = FloatArray(gridX * gridY * 2)
        var idxOut = 0
        for (gy in 0 until gridY) {
            val y0 = (gy * height) / gridY
            val y1 = ((gy + 1) * height) / gridY
            for (gx in 0 until gridX) {
                val x0 = (gx * width) / gridX
                val x1 = ((gx + 1) * width) / gridX
                var sum = 0f
                var sq = 0f
                var count = 0
                for (y in y0 until y1) {
                    var base = y * width + x0
                    for (x in x0 until x1) {
                        val v = values[base++]
                        sum += v
                        sq += v * v
                        count++
                    }
                }
                val n = count.coerceAtLeast(1)
                val mean = sum / n
                val variance = (sq / n - mean * mean).coerceAtLeast(0f)
                val std = sqrt(variance)
                out[idxOut++] = mean
                out[idxOut++] = std
            }
        }
        return out
    }

    private fun tileSignatureSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var diff = 0f
        for (i in a.indices) {
            diff += abs(a[i] - b[i])
        }
        val avgDiff = diff / a.size
        return (1f - avgDiff * 1.6f).coerceIn(0f, 1f)
    }

    private fun CropRectangle.isInside(width: Int, height: Int): Boolean {
        val pts = listOf(topLeft, topRight, bottomRight, bottomLeft)
        return pts.all { p -> p.x in 0 until width && p.y in 0 until height }
    }

    private fun CropRectangle.containsPoint(x: Int, y: Int): Boolean {
        val p = PointFInt(x, y)
        val pts = listOf(topLeft, topRight, bottomRight, bottomLeft)
        var sign = 0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            if (cross == 0) continue
            val current = if (cross > 0) 1 else -1
            if (sign == 0) sign = current
            if (sign != current) return false
        }
        return true
    }

    private fun CropSessionUi.toCropRectangle(width: Int, height: Int): CropRectangle {
        fun denorm(x: Float, y: Float): PointFInt {
            return PointFInt(
                x = (x.coerceIn(0f, 1f) * width).toInt().coerceIn(0, width - 1),
                y = (y.coerceIn(0f, 1f) * height).toInt().coerceIn(0, height - 1)
            )
        }
        return CropRectangle(
            topLeft = denorm(topLeft.x, topLeft.y),
            topRight = denorm(topRight.x, topRight.y),
            bottomRight = denorm(bottomRight.x, bottomRight.y),
            bottomLeft = denorm(bottomLeft.x, bottomLeft.y)
        )
    }

    private fun CropRectangle.toNormalized(bitmapWidth: Int, bitmapHeight: Int): NormalizedQuad {
        fun norm(p: PointFInt): Pair<Float, Float> = Pair(
            (p.x.toFloat() / bitmapWidth).coerceIn(0f, 1f),
            (p.y.toFloat() / bitmapHeight).coerceIn(0f, 1f)
        )
        val tl = norm(topLeft)
        val tr = norm(topRight)
        val br = norm(bottomRight)
        val bl = norm(bottomLeft)
        return NormalizedQuad(tl.first, tl.second, tr.first, tr.second, br.first, br.second, bl.first, bl.second)
    }

    private fun NormalizedQuad.toAbsolute(width: Int, height: Int): CropRectangle {
        fun denorm(x: Float, y: Float): PointFInt = PointFInt(
            x = (x * width).toInt().coerceIn(0, width - 1),
            y = (y * height).toInt().coerceIn(0, height - 1)
        )
        return CropRectangle(
            topLeft = denorm(topLeftX, topLeftY),
            topRight = denorm(topRightX, topRightY),
            bottomRight = denorm(bottomRightX, bottomRightY),
            bottomLeft = denorm(bottomLeftX, bottomLeftY)
        )
    }

    data class MatchResult(
        val rectangle: CropRectangle,
        val score: Float
    )

    private data class RawCandidateScore(
        val rectangle: CropRectangle,
        val edge: Float,
        val contrast: Float,
        val shape: Float
    )

    private data class CandidateScore(
        val rectangle: CropRectangle,
        val score: Float
    )

    private enum class Edge {
        TOP, RIGHT, BOTTOM, LEFT
    }

    private data class BoardTemplate(
        val normalizedExpandedQuad: NormalizedQuad,
        val gray: FloatArray,
        val edge: FloatArray,
        val tileSignature: FloatArray
    )

    private data class NormalizedQuad(
        val topLeftX: Float,
        val topLeftY: Float,
        val topRightX: Float,
        val topRightY: Float,
        val bottomRightX: Float,
        val bottomRightY: Float,
        val bottomLeftX: Float,
        val bottomLeftY: Float
    ) {
        val aspectRatio: Float
            get() {
                val widthTop = sqrt((topRightX - topLeftX).pow(2) + (topRightY - topLeftY).pow(2))
                val widthBottom = sqrt((bottomRightX - bottomLeftX).pow(2) + (bottomRightY - bottomLeftY).pow(2))
                val heightLeft = sqrt((bottomLeftX - topLeftX).pow(2) + (bottomLeftY - topLeftY).pow(2))
                val heightRight = sqrt((bottomRightX - topRightX).pow(2) + (bottomRightY - topRightY).pow(2))
                val avgWidth = (widthTop + widthBottom) * 0.5f
                val avgHeight = (heightLeft + heightRight) * 0.5f
                return if (avgHeight <= 1e-6f) 1.6f else (avgWidth / avgHeight).coerceIn(0.8f, 3.2f)
            }
    }
}
