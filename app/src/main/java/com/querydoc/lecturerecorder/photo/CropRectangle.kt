package com.querydoc.lecturerecorder.photo

data class PointFInt(val x: Int, val y: Int)

data class CropRectangle(
    val topLeft: PointFInt,
    val topRight: PointFInt,
    val bottomRight: PointFInt,
    val bottomLeft: PointFInt
)
