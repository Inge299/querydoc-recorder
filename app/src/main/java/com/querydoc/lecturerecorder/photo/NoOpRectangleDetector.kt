package com.querydoc.lecturerecorder.photo

class NoOpRectangleDetector : RectangleDetector {
    override fun detectRectangle(imagePath: String): CropRectangle? = null
}
