package com.querydoc.lecturerecorder.data.remote

import android.content.Context
import com.querydoc.lecturerecorder.config.AppConfig
import com.querydoc.lecturerecorder.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failure(val status: Int, val code: String, val message: String, val retryable: Boolean) : ApiResult<Nothing>
}

data class PresignedUpload(
    val uploadUrl: String,
    val uploadMethod: String,
    val uploadHeaders: Map<String, String>,
    val uploadSessionId: String,
    val duplicate: Boolean,
    val materialFileId: String?,
)

data class ConspectState(
    val sessionId: String,
    val status: String,
    val markdown: String,
    val error: String,
    val attempts: Int,
)

data class ConspectSummary(
    val sessionId: String,
    val title: String,
    val preview: String,
    val completedAt: String,
    val length: Int,
    val markdown: String? = null,
)

data class TranscriptState(
    val sessionId: String,
    val title: String,
    val transcript: String,
    val turns: List<String>,
)

data class SessionProgress(
    val sessionId: String,
    val uploadedChunks: Int,
    val totalChunks: Int,
    val uploadPct: Int,
    val missingChunks: Int,
    val aggregateStatus: String?,
)

class ApiClient(context: Context) {
    private val appContext = context.applicationContext
    private val baseUrl = AppConfig.Api.baseUrl
    private val cookieJar = PersistentCookieJar(context)
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun execute(request: Request): Response? = try {
        client.newCall(request).execute()
    } catch (error: Exception) {
        Diagnostics.warn(appContext, "network error: ${error.message}")
        null
    }

    private fun networkFailure(): ApiResult.Failure =
        ApiResult.Failure(0, "network_error", "Нет подключения к интернету", true)

    fun hasSession(): Boolean = cookieJar.hasSessionCookie()

    suspend fun login(phone: String, password: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("login", phone.trim()).put("password", password).put("rememberDevice", true).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/auth/login")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (response.isSuccessful && json?.optBoolean("success") == true) {
                ApiResult.Ok(Unit)
            } else {
                ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось войти (${response.code})" },
                    retryable = response.code >= 500,
                )
            }
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url("$baseUrl/api/auth/logout").post(EMPTY).build()).execute().close()
        }
        cookieJar.clear()
    }

    suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
        if (cookieJar.hasSessionCookie()) return@withContext true
        val deviceId = cookieJar.cookieValue("deviceId") ?: return@withContext false
        val body = JSONObject().put("deviceId", deviceId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/auth/verify-device")
            .post(body.toRequestBody(JSON))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                json?.optString("token")?.isNotBlank() == true
            }
        }.getOrDefault(false)
    }

    suspend fun ensureSession(): Boolean = hasSession() || restoreSession()

    suspend fun presign(
        chunkId: String,
        sessionId: String,
        chunkIndex: Int,
        chunkCount: Int?,
        sizeBytes: Long,
        mimeType: String,
        fileName: String,
        durationMs: Long?,
        capturedAtMs: Long?,
    ): ApiResult<PresignedUpload> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject()
            .put("chunkId", chunkId)
            .put("sessionId", sessionId)
            .put("chunkIndex", chunkIndex)
            .put("mimeType", mimeType)
            .put("sizeBytes", sizeBytes)
            .put("fileName", fileName)
        if (chunkCount != null && chunkCount > 0) payload.put("chunkCount", chunkCount)
        if (durationMs != null) payload.put("durationSec", durationMs / 1000.0)
        if (capturedAtMs != null) payload.put("capturedAt", java.time.Instant.ofEpochMilli(capturedAtMs).toString())
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/uploads/audio-chunks/presign")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                Diagnostics.warn(appContext, "presign failed status=${response.code} code=${json?.optString("code").orEmpty()}")
                return@use ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось получить upload URL" },
                    retryable = response.code >= 500 || response.code == 409,
                )
            }
            if (json.optBoolean("duplicate", false) && json.optBoolean("accepted", false)) {
                return@use ApiResult.Ok(
                    PresignedUpload(
                        uploadUrl = "",
                        uploadMethod = "PUT",
                        uploadHeaders = emptyMap(),
                        uploadSessionId = "",
                        duplicate = true,
                        materialFileId = json.optString("materialFileId").takeIf { it.isNotBlank() },
                    ),
                )
            }
            val uploadUrl = json.optString("uploadUrl")
            if (uploadUrl.isBlank()) {
                return@use ApiResult.Failure(
                    status = response.code,
                    code = "duplicate_or_expired",
                    message = "Сервер не вернул upload URL",
                    retryable = false,
                )
            }
            val headers = json.optJSONObject("headers")?.let { obj ->
                obj.keys().asSequence().associateWith { key -> obj.optString(key) }
            }.orEmpty()
            ApiResult.Ok(
                PresignedUpload(
                    uploadUrl = uploadUrl,
                    uploadMethod = json.optString("method", "PUT"),
                    uploadHeaders = headers,
                    uploadSessionId = json.optString("uploadSessionId"),
                    duplicate = json.optBoolean("duplicate", false),
                    materialFileId = json.optString("materialFileId").takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    suspend fun putObject(upload: PresignedUpload, file: File): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(upload.uploadUrl)
        upload.uploadHeaders.forEach { (key, value) -> builder.header(key, value) }
        builder.method(upload.uploadMethod, file.asRequestBody(FILE_MEDIA_TYPE))
        val request = builder.build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else {
                Diagnostics.warn(appContext, "s3 put failed status=${response.code}")
                ApiResult.Failure(response.code, "s3_put_failed", "S3 отклонил загрузку (${response.code})", response.code >= 500)
            }
        }
    }

    suspend fun complete(uploadSessionId: String, chunkId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject().put("uploadSessionId", uploadSessionId).put("chunkId", chunkId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/uploads/audio-chunks/complete")
            .post(payload.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (response.isSuccessful && json?.optBoolean("accepted") != false) ApiResult.Ok(Unit)
            else {
                Diagnostics.warn(appContext, "complete failed status=${response.code} code=${json?.optString("code").orEmpty()}")
                ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось подтвердить загрузку" },
                    retryable = response.code >= 500 || response.code == 409,
                )
            }
        }
    }

    suspend fun status(sessionIds: List<String>): ApiResult<List<SessionProgress>> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject().put("sessionIds", JSONArray(sessionIds)).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/status")
            .post(payload.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                Diagnostics.warn(appContext, "presign failed status=${response.code} code=${json?.optString("code").orEmpty()}")
                return@use ApiResult.Failure(response.code, "", "Не удалось получить статус сессии", response.code >= 500)
            }
            val sessions = json.optJSONArray("sessions") ?: return@use ApiResult.Ok(emptyList())
            val values = (0 until sessions.length()).mapNotNull { index ->
                val item = sessions.optJSONObject(index) ?: return@mapNotNull null
                SessionProgress(
                    sessionId = item.optString("sessionId"),
                    uploadedChunks = item.optInt("uploadedChunks"),
                    totalChunks = item.optInt("totalChunks"),
                    uploadPct = item.optInt("uploadPct"),
                    missingChunks = item.optInt("missingChunks"),
                    aggregateStatus = item.optString("aggregateStatus").takeIf { it.isNotBlank() },
                )
            }
            ApiResult.Ok(values)
        }
    }

    suspend fun finalizeConspect(sessionId: String): ApiResult<ConspectState> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject().put("sessionId", sessionId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/finalize")
            .post(payload.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (response.code == 202 && json?.optBoolean("accepted") == false) {
                return@use ApiResult.Ok(
                    ConspectState(
                        sessionId = sessionId,
                        status = "processing",
                        markdown = "",
                        error = json.optString("error"),
                        attempts = 0,
                    ),
                )
            }
            val item = json?.optJSONObject("conspect")
            if (!response.isSuccessful || item == null) {
                return@use ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось запустить конспект" },
                    retryable = response.code >= 500,
                )
            }
            ApiResult.Ok(parseConspect(item))
        }
    }

    suspend fun conspect(sessionId: String): ApiResult<ConspectState> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject().put("sessionId", sessionId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/conspect")
            .post(payload.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            val item = json?.optJSONObject("conspect")
            if (!response.isSuccessful || item == null) {
                return@use ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Конспект ещё не готов" },
                    retryable = response.code >= 500,
                )
            }
            ApiResult.Ok(parseConspect(item))
        }
    }

    suspend fun listConspects(): ApiResult<List<ConspectSummary>> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/conspects")
            .get()
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось получить конспекты" },
                    retryable = response.code >= 500,
                )
            }
            val items = json.optJSONArray("conspects") ?: JSONArray()
            val values = (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                ConspectSummary(
                    sessionId = item.optString("sessionId"),
                    title = item.optString("title").ifBlank { "Конспект лекции" },
                    preview = item.optString("preview"),
                    completedAt = item.optString("completedAt"),
                    length = item.optInt("length"),
                )
            }
            ApiResult.Ok(values)
        }
    }

    suspend fun transcript(sessionId: String): ApiResult<TranscriptState> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val encoded = java.net.URLEncoder.encode(sessionId, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/transcript?sessionId=$encoded")
            .get()
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось получить транскрипт" },
                    retryable = response.code >= 500,
                )
            }
            val turns = json.optJSONArray("turns")?.let { array ->
                (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.optString("text") }
            }.orEmpty()
            ApiResult.Ok(
                TranscriptState(
                    sessionId = json.optString("sessionId"),
                    title = json.optString("title"),
                    transcript = json.optString("transcript"),
                    turns = turns,
                ),
            )
        }
    }

    private fun parseConspect(item: JSONObject): ConspectState = ConspectState(
        sessionId = item.optString("sessionId"),
        status = item.optString("status"),
        markdown = item.optString("markdown"),
        error = item.optString("error"),
        attempts = item.optInt("attempts"),
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
        val FILE_MEDIA_TYPE = "audio/mp4".toMediaTypeOrNull()
        val EMPTY = ByteArray(0).toRequestBody(null)
    }
}
