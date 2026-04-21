package com.querydoc.lecturerecorder.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object ImageOrientationUtils {
    fun normalizeFileToUpright(path: String): String {
        val exif = runCatching { ExifInterface(path) }.getOrNull() ?: return path
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return path
        }
        val rotated = decodeOrientedBitmap(path) ?: return path
        val original = File(path)
        val outFile = File(original.parentFile, "upright_${original.name}")
        FileOutputStream(outFile).use { stream ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 97, stream)
        }
        rotated.recycle()
        runCatching {
            val outExif = ExifInterface(outFile.absolutePath)
            outExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            outExif.saveAttributes()
        }
        return outFile.absolutePath
    }

    fun decodeOrientedBitmap(path: String): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        val exif = runCatching { ExifInterface(path) }.getOrNull()
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL
        return rotateByOrientation(bitmap, orientation)
    }

    private fun rotateByOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}
