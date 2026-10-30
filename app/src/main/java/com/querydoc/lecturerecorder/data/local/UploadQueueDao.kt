package com.querydoc.lecturerecorder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UploadQueueEntity)

    @Query("SELECT COUNT(*) FROM upload_queue WHERE status = 'PENDING' OR status = 'FAILED' OR status = 'UPLOADING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM upload_queue WHERE mediaType = 'AUDIO_CHUNK' ORDER BY createdAtMs DESC")
    fun observeAudioItems(): Flow<List<UploadQueueEntity>>

    @Query("SELECT * FROM upload_queue WHERE mediaType = 'CROPPED_PHOTO' ORDER BY createdAtMs DESC")
    fun observePhotoItems(): Flow<List<UploadQueueEntity>>

    @Query("SELECT * FROM upload_queue WHERE mediaType = 'AUDIO_CHUNK' ORDER BY createdAtMs ASC")
    suspend fun allAudioItems(): List<UploadQueueEntity>

    @Query("SELECT * FROM upload_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAtMs ASC LIMIT 1")
    suspend fun nextPending(): UploadQueueEntity?

    @Query(
        "SELECT * FROM upload_queue " +
            "WHERE status = 'UPLOADED' AND deleteAfterMs IS NOT NULL AND deleteAfterMs <= :nowMs " +
            "ORDER BY deleteAfterMs ASC LIMIT :limit"
    )
    suspend fun findCleanupCandidates(nowMs: Long, limit: Int): List<UploadQueueEntity>

    @Query("UPDATE upload_queue SET status = :status, retryCount = :retryCount, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateStatus(id: String, status: UploadStatus, retryCount: Int, updatedAtMs: Long)

    @Query("UPDATE upload_queue SET status = 'UPLOADED', uploadedAtMs = :uploadedAtMs, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun markUploaded(id: String, uploadedAtMs: Long, updatedAtMs: Long)

    @Query("SELECT * FROM upload_queue WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): UploadQueueEntity?

    @Query("SELECT * FROM upload_queue WHERE filePath = :filePath LIMIT 1")
    suspend fun findByFilePath(filePath: String): UploadQueueEntity?

    @Query("DELETE FROM upload_queue")
    suspend fun deleteAll()

    @Query("DELETE FROM upload_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM upload_queue WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)
}
