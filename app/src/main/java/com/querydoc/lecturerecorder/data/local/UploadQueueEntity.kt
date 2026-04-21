package com.querydoc.lecturerecorder.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "upload_queue",
    indices = [Index(value = ["status", "createdAtMs"])]
)
data class UploadQueueEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val mediaType: MediaType,
    val filePath: String,
    val mimeType: String,
    val status: UploadStatus,
    val retryCount: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val uploadedAtMs: Long? = null,
    val deleteAfterMs: Long? = null,
    val metadataJson: String? = null
)
