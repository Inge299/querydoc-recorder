package com.querydoc.lecturerecorder.ui

import com.querydoc.lecturerecorder.data.local.UploadStatus

data class RecordingListItem(
    val id: String,
    val title: String,
    val filePath: String,
    val chunkPaths: List<String>,
    val chunkDurationsMs: List<Long>,
    val durationMs: Long,
    val createdAtLabel: String,
    val durationLabel: String,
    val status: UploadStatus,
    val stage: RecordingStage,
    val statusLabel: String,
    val deleteAfterLabel: String
)
