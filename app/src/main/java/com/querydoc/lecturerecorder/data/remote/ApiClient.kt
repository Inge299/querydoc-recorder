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
import java.io.FileOutputStream
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

data class SubjectSummary(
    val id: String,
    val name: String,
    val count: Int = 0,
)

data class ConspectSummary(
    val sessionId: String,
    val title: String,
    val subject: String = "",
    val subjectId: String = "",
    val preview: String,
    val completedAt: String,
    val length: Int,
    val markdown: String? = null,
)

data class AudioSourceChunk(
    val url: String,
    val durationMs: Long,
    val mimeType: String,
)

data class LectureCitation(
    val index: Int,
    val chunkIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val time: String,
    val text: String,
)

data class StudyQuestion(
    val question: String,
    val answer: String,
)

data class StudyKit(
    val questions: List<StudyQuestion>,
    val checklist: List<String>,
    val cached: Boolean = false,
)

data class LectureAnswer(
    val answer: String,
    val citations: List<LectureCitation>,
)

data class QaHistoryTurn(
    val role: String,
    val text: String,
)

data class AudioSessionSource(
    val sessionId: String,
    val chunks: List<AudioSourceChunk>,
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
    val conspectStatus: String,
    val conspectCreatedAt: String,
    val conspectUpdatedAt: String,
    val conspectEtaSeconds: Long,
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
        language: String?,
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
        if (!language.isNullOrBlank()) payload.put("language", language)
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
                    conspectStatus = item.optString("conspectStatus"),
                    conspectCreatedAt = item.optString("conspectCreatedAt"),
                    conspectUpdatedAt = item.optString("conspectUpdatedAt"),
                    conspectEtaSeconds = item.optLong("conspectEtaSeconds"),
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

    suspend fun listSubjects(): ApiResult<List<SubjectSummary>> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/subjects")
            .get()
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(response.code, "", "Не удалось получить предметы", response.code >= 500)
            }
            val array = json.optJSONArray("subjects") ?: JSONArray()
            val values = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                SubjectSummary(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    count = item.optInt("count"),
                )
            }
            ApiResult.Ok(values)
        }
    }

    suspend fun createSubject(name: String): ApiResult<SubjectSummary> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/subjects")
            .post(JSONObject().put("name", name).toString().toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            val item = json?.optJSONObject("subject")
            if (!response.isSuccessful || item == null) {
                return@use ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось создать предмет" }, false)
            }
            ApiResult.Ok(SubjectSummary(item.optString("id"), item.optString("name"), item.optInt("count")))
        }
    }

    suspend fun renameSubject(id: String, name: String): ApiResult<SubjectSummary> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/subjects")
            .patch(JSONObject().put("id", id).put("name", name).toString().toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            val item = json?.optJSONObject("subject")
            if (!response.isSuccessful || item == null) {
                return@use ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось переименовать предмет" }, false)
            }
            ApiResult.Ok(SubjectSummary(item.optString("id"), item.optString("name"), item.optInt("count")))
        }
    }

    suspend fun deleteSubject(id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/subjects")
            .delete(JSONObject().put("id", id).toString().toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, "", "Не удалось удалить предмет", false)
        }
    }

    suspend fun assignSubject(sessionId: String, subjectId: String?, name: String?): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject().put("sessionId", sessionId)
        if (!subjectId.isNullOrBlank()) payload.put("subjectId", subjectId)
        if (!name.isNullOrBlank()) payload.put("name", name)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/subject")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, "", "Не удалось назначить предмет", false)
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
                    subject = item.optString("subject"),
                    subjectId = item.optString("subjectId"),
                    preview = item.optString("preview"),
                    completedAt = item.optString("completedAt"),
                    length = item.optInt("length"),
                )
            }
            ApiResult.Ok(values)
        }
    }

    suspend fun deleteConspect(sessionId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject().put("sessionId", sessionId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/conspects")
            .delete(payload.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                return@use ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось удалить конспект" },
                    retryable = response.code >= 500,
                )
            }
            ApiResult.Ok(Unit)
        }
    }

    suspend fun downloadAudio(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                body.byteStream().use { input ->
                    FileOutputStream(destination).use { output -> input.copyTo(output) }
                }
                destination.length() > 0L
            }
        }.getOrDefault(false)
    }

    suspend fun studyKit(sessionId: String, refresh: Boolean = false): ApiResult<StudyKit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject().put("sessionId", sessionId).put("refresh", refresh).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/study-kit")
            .post(payload.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось подготовить вопросы" },
                    retryable = response.code >= 500,
                )
            }
            val qArray = json.optJSONArray("questions") ?: JSONArray()
            val questions = (0 until qArray.length()).mapNotNull { index ->
                val item = qArray.optJSONObject(index) ?: return@mapNotNull null
                val question = item.optString("question").trim()
                val answer = item.optString("answer").trim()
                if (question.isBlank() || answer.isBlank()) null else StudyQuestion(question, answer)
            }
            val cArray = json.optJSONArray("checklist") ?: JSONArray()
            val checklist = (0 until cArray.length()).mapNotNull { index ->
                cArray.optString(index).trim().takeIf { it.isNotBlank() }
            }
            ApiResult.Ok(StudyKit(questions, checklist, json.optBoolean("cached")))
        }
    }

    suspend fun askLecture(
        sessionId: String,
        question: String,
        history: List<QaHistoryTurn>,
    ): ApiResult<LectureAnswer> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val historyArray = JSONArray()
        history.takeLast(6).forEach { turn ->
            historyArray.put(JSONObject().put("role", turn.role).put("text", turn.text))
        }
        val payload = JSONObject()
            .put("sessionId", sessionId)
            .put("question", question)
            .put("history", historyArray)
            .toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/ask")
            .post(payload.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось получить ответ" },
                    retryable = response.code >= 500,
                )
            }
            val array = json.optJSONArray("citations") ?: JSONArray()
            val citations = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                LectureCitation(
                    index = item.optInt("index"),
                    chunkIndex = item.optInt("chunkIndex"),
                    startMs = item.optLong("startMs"),
                    endMs = item.optLong("endMs"),
                    time = item.optString("time"),
                    text = item.optString("text"),
                )
            }
            ApiResult.Ok(
                LectureAnswer(
                    answer = json.optString("answer"),
                    citations = citations,
                ),
            )
        }
    }

    suspend fun audioSession(sessionId: String): ApiResult<AudioSessionSource> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val encoded = java.net.URLEncoder.encode(sessionId, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/audio-sessions/audio?sessionId=$encoded")
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
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось получить аудио" },
                    retryable = response.code >= 500,
                )
            }
            val array = json.optJSONArray("chunks") ?: JSONArray()
            val chunks = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AudioSourceChunk(
                    url = item.optString("url"),
                    durationMs = item.optLong("durationMs"),
                    mimeType = item.optString("mimeType"),
                )
            }.filter { it.url.isNotBlank() }
            ApiResult.Ok(AudioSessionSource(sessionId = json.optString("sessionId"), chunks = chunks))
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
