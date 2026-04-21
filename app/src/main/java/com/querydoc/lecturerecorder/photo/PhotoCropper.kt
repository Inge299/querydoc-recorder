package com.querydoc.lecturerecorder.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.querydoc.lecturerecorder.config.AppConfig
import com.querydoc.lecturerecorder.ui.CropSessionUi
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class PhotoCropper {
    fun cropAndSave(
        sourcePath: String,
        session: CropSessionUi,
        outputDir: File
    ): File? {
        val sourceBitmap = ImageOrientationUtils.decodeOrientedBitmap(sourcePath) ?: return null

        val tl = PointF(
            session.topLeft.x.coerceIn(0f, 1f) * sourceBitmap.width,
            session.topLeft.y.coerceIn(0f, 1f) * sourceBitmap.height
        )
        val tr = PointF(
            session.topRight.x.coerceIn(0f, 1f) * sourceBitmap.width,
            session.topRight.y.coerceIn(0f, 1f) * sourceBitmap.height
        )
        val br = PointF(
            session.bottomRight.x.coerceIn(0f, 1f) * sourceBitmap.width,
            session.bottomRight.y.coerceIn(0f, 1f) * sourceBitmap.height
        )
        val bl = PointF(
            session.bottomLeft.x.coerceIn(0f, 1f) * sourceBitmap.width,
            session.bottomLeft.y.coerceIn(0f, 1f) * sourceBitmap.height
        )

        val topWidth = distance(tl, tr)
        val bottomWidth = distance(bl, br)
        val leftHeight = distance(tl, bl)
        val rightHeight = distance(tr, br)

        val targetWidth = max(topWidth, bottomWidth).toInt().coerceAtLeast(1)
        val targetHeight = max(leftHeight, rightHeight).toInt().coerceAtLeast(1)

        var cropped = perspectiveTransform(
            source = sourceBitmap,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            topLeft = tl,
            topRight = tr,
            bottomRight = br,
            bottomLeft = bl
        )
        sourceBitmap.recycle()

        cropped = downscaleIfNeeded(cropped, AppConfig.Photo.maxLongSidePx)

        outputDir.mkdirs()
        val outFile = File(outputDir, "photo_crop_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { stream ->
            cropped.compress(Bitmap.CompressFormat.JPEG, AppConfig.Photo.jpegQualityDefault, stream)
        }
        cropped.recycle()
        return outFile
    }

    private fun perspectiveTransform(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        topLeft: PointF,
        topRight: PointF,
        bottomRight: PointF,
        bottomLeft: PointF
    ): Bitmap {
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val src = floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix().apply {
            setPolyToPoly(src, 0, dst, 0, 4)
        }
        val canvas = Canvas(output)
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun distance(a: PointF, b: PointF): Float {
        return sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxLongSide: Int): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxLongSide) return bitmap
        val scale = maxLongSide.toFloat() / longSide.toFloat()
        val targetWidth = ((bitmap.width * scale).toInt()).coerceAtLeast(1)
        val targetHeight = ((bitmap.height * scale).toInt()).coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        bitmap.recycle()
        return resized
    }
}
