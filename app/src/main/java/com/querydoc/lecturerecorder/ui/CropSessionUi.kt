package com.querydoc.lecturerecorder.ui

data class CropPointUi(
    val x: Float,
    val y: Float
)

data class CropSessionUi(
    val sourceImagePath: String,
    val topLeft: CropPointUi,
    val topRight: CropPointUi,
    val bottomRight: CropPointUi,
    val bottomLeft: CropPointUi,
    val overlayVisible: Boolean = true
)
