package com.querydoc.lecturerecorder.ui

data class PhotoListItem(
    val id: String,
    val title: String,
    val filePath: String,
    val createdAtLabel: String,
    val statusLabel: String,
    val deleteAfterLabel: String
)
