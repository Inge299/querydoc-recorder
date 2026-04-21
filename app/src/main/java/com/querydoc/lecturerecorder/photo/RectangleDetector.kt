package com.querydoc.lecturerecorder.photo

interface RectangleDetector {
    fun detectRectangle(imagePath: String): CropRectangle?
}
