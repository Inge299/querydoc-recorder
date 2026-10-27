package com.querydoc.lecturerecorder

import android.content.Context
import androidx.room.Room
import com.querydoc.lecturerecorder.data.local.AppDatabase
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.repository.QueryDocUploadRepository
import com.querydoc.lecturerecorder.data.repository.UploadRepository
import com.querydoc.lecturerecorder.photo.HeuristicRectangleDetector
import com.querydoc.lecturerecorder.photo.PhotoCropper
import com.querydoc.lecturerecorder.photo.RectangleDetector

object AppGraph {
    @Volatile
    private var dbInstance: AppDatabase? = null

    @Volatile
    private var uploadRepositoryInstance: UploadRepository? = null
    @Volatile
    private var apiClientInstance: ApiClient? = null
    @Volatile
    private var photoCropperInstance: PhotoCropper? = null
    @Volatile
    private var rectangleDetectorInstance: RectangleDetector? = null

    fun db(context: Context): AppDatabase {
        return dbInstance ?: synchronized(this) {
            dbInstance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "lecture-recorder.db"
            ).fallbackToDestructiveMigration().build().also { dbInstance = it }
        }
    }

    fun apiClient(context: Context): ApiClient {
        return apiClientInstance ?: synchronized(this) {
            apiClientInstance ?: ApiClient(context.applicationContext).also { apiClientInstance = it }
        }
    }

    fun uploadRepository(context: Context): UploadRepository {
        return uploadRepositoryInstance ?: synchronized(this) {
            uploadRepositoryInstance ?: QueryDocUploadRepository(apiClient(context)).also { uploadRepositoryInstance = it }
        }
    }

    fun photoCropper(): PhotoCropper {
        return photoCropperInstance ?: synchronized(this) {
            photoCropperInstance ?: PhotoCropper().also { photoCropperInstance = it }
        }
    }

    fun rectangleDetector(): RectangleDetector {
        return rectangleDetectorInstance ?: synchronized(this) {
            rectangleDetectorInstance ?: HeuristicRectangleDetector().also { rectangleDetectorInstance = it }
        }
    }
}
