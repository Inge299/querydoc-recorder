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

data class DocumentUploadTarget(
    val upload: PresignedUpload,
)

data class ConspectState(
    val sessionId: String,
    val status: String,
    val markdown: String,
    val error: String,
    val attempts: Int,
)

data class SupportMessageItem(
    val id: String,
    val role: String,
    val text: String,
    val createdAt: String?,
)

data class SupportPayload(
    val threadId: String,
    val messages: List<SupportMessageItem>,
)

data class AssignedSubject(
    val subjectId: String,
    val subject: String,
)

data class SubjectSummary(
    val id: String,
    val name: String,
    val count: Int = 0,
)

data class ConspectSummary(
    val sessionId: String,
    val title: String,
    val sourceTitle: String = "",
    val sourceType: String = "",
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

data class AuthChallenge(
    val login: String,
    val channel: String,
    val callPhone: String,
    val callCheckId: String,
    val message: String,
)

data class UsageSummary(
    val recordingUsedMinutes: Int,
    val recordingLimitMinutes: Int,
    val recordingRemainingMinutes: Int,
    val qaUsed: Int,
    val qaLimit: Int,
    val qaRemaining: Int,
    val studyKitUsed: Int,
    val studyKitLimit: Int,
    val studyKitRemaining: Int,
    val planId: String = "",
    val planName: String = "",
    val storageDays: Int = 14,
)

data class PlanInfo(
    val id: String,
    val name: String,
    val priceMonthly: Int,
    val priceAnnual: Int,
    val monthlyRecordingMinutes: Int,
    val maxSessionMinutes: Int,
    val dailyQa: Int,
    val dailyStudyKit: Int,
    val monthlyDocuments: Int,
    val storageDays: Int,
)

data class TopUpInfo(
    val id: String,
    val name: String,
    val priceRub: Int,
    val minutes: Int,
)

data class PlansPayload(
    val currentPlanId: String,
    val currentPlanName: String,
    val testBilling: Boolean,
    val billingReady: Boolean,
    val plans: List<PlanInfo>,
    val topUp: TopUpInfo? = null,
)

data class InboxNotification(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val action: String,
    val actionUrl: String,
    val priority: String,
    val paymentId: String,
    val readAt: String?,
    val createdAt: String?,
)

data class InboxPayload(
    val unread: Int,
    val notifications: List<InboxNotification>,
)

data class AdminAlert(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val paymentId: String,
    val readAt: String?,
    val createdAt: String?,
)

data class AdminAlertsPayload(
    val unread: Int,
    val alerts: List<AdminAlert>,
)

data class PaymentHistoryItem(
    val id: String,
    val planId: String,
    val amountRub: Int,
    val status: String,
    val paidAt: String?,
    val receiptStatus: String,
    val receiptUrl: String,
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

    suspend fun trackEvent(name: String, props: Map<String, String> = emptyMap()): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val event = JSONObject().put("name", name)
        if (props.isNotEmpty()) {
            val propsJson = JSONObject()
            props.forEach { (key, value) -> propsJson.put(key, value) }
            event.put("props", propsJson)
        }
        val body = JSONObject().put("events", JSONArray().put(event)).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/events")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, "", "Не удалось сохранить событие", false)
        }
    }

    suspend fun deleteAccount(password: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/account/delete")
            .post(JSONObject().put("password", password).toString().toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, json?.optString("code").orEmpty(), json?.optString("error").orEmpty().ifBlank { "Не удалось удалить аккаунт" }, false)
        }
    }

    suspend fun register(phone: String, password: String): ApiResult<AuthChallenge> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("login", phone.trim()).put("password", password).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/auth/register")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(
                    status = response.code,
                    code = json?.optString("code").orEmpty(),
                    message = json?.optString("error").orEmpty().ifBlank { "Не удалось зарегистрироваться" },
                    retryable = response.code >= 500,
                )
            }
            ApiResult.Ok(
                AuthChallenge(
                    login = json.optString("login", phone.trim()),
                    channel = json.optString("channel"),
                    callPhone = json.optString("callPhone"),
                    callCheckId = json.optString("callCheckId"),
                    message = json.optString("message"),
                ),
            )
        }
    }

    suspend fun confirmRegistrationCall(phone: String, checkId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("phone", phone.trim()).put("checkId", checkId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/auth/phone-verification/call/confirm")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, json?.optString("code").orEmpty(), json?.optString("error").orEmpty().ifBlank { "Звонок ещё не подтверждён" }, false)
        }
    }

    suspend fun requestPasswordReset(phone: String): ApiResult<AuthChallenge> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("login", phone.trim()).put("channel", "call").toString()
        val request = Request.Builder()
            .url("$baseUrl/api/auth/password-reset/request")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(response.code, json?.optString("code").orEmpty(), json?.optString("error").orEmpty().ifBlank { "Не удалось начать восстановление" }, false)
            }
            ApiResult.Ok(
                AuthChallenge(
                    login = phone.trim(),
                    channel = json.optString("channel"),
                    callPhone = json.optString("callPhone"),
                    callCheckId = json.optString("callCheckId"),
                    message = json.optString("message"),
                ),
            )
        }
    }

    suspend fun confirmPasswordResetCall(phone: String, checkId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("login", phone.trim()).put("checkId", checkId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/auth/password-reset/call/confirm")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, json?.optString("code").orEmpty(), json?.optString("error").orEmpty().ifBlank { "Звонок ещё не подтверждён" }, false)
        }
    }

    suspend fun resetPassword(phone: String, newPassword: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("login", phone.trim()).put("newPassword", newPassword).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/auth/password-reset/confirm")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, json?.optString("code").orEmpty(), json?.optString("error").orEmpty().ifBlank { "Не удалось изменить пароль" }, false)
        }
    }

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

    suspend fun putObject(upload: PresignedUpload, file: File, mimeType: String? = null): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(upload.uploadUrl)
        upload.uploadHeaders.forEach { (key, value) -> builder.header(key, value) }
        val mediaType = mimeType?.toMediaTypeOrNull() ?: FILE_MEDIA_TYPE
        builder.method(upload.uploadMethod, file.asRequestBody(mediaType))
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

    suspend fun initiateDocumentUpload(fileName: String, mimeType: String, sizeBytes: Long): ApiResult<DocumentUploadTarget> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject()
            .put("fileName", fileName)
            .put("mimeType", mimeType)
            .put("sizeBytes", sizeBytes)
            .put("ingestKind", "file")
        val request = Request.Builder()
            .url("$baseUrl/api/materials/direct-uploads/initiate")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось подготовить загрузку документа" }, response.code >= 500)
            }
            if (json.optBoolean("duplicate") && json.optString("materialFileId").isNotBlank()) {
                return@use ApiResult.Ok(DocumentUploadTarget(
                    upload = PresignedUpload("", "", emptyMap(), "", true, json.optString("materialFileId")),
                ))
            }
            val session = json.optJSONObject("uploadSession")
            val target = json.optJSONObject("uploadTarget")
            if (session == null || target == null) return@use ApiResult.Failure(500, "", "Сервер не вернул параметры загрузки", true)
            val headersJson = target.optJSONObject("headers")
            val headers = mutableMapOf<String, String>()
            headersJson?.keys()?.forEach { key -> headers[key] = headersJson.optString(key) }
            ApiResult.Ok(DocumentUploadTarget(
                upload = PresignedUpload(
                    uploadUrl = target.optString("url"),
                    uploadMethod = target.optString("method"),
                    uploadHeaders = headers,
                    uploadSessionId = session.optString("id"),
                    duplicate = false,
                    materialFileId = null,
                ),
            ))
        }
    }

    suspend fun completeDocumentUpload(uploadSessionId: String): ApiResult<String> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject().put("uploadSessionId", uploadSessionId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/materials/direct-uploads/complete")
            .post(payload.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            val id = json?.optJSONObject("file")?.optString("id").orEmpty()
            if (response.isSuccessful && id.isNotBlank()) ApiResult.Ok(id)
            else ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось завершить загрузку документа" }, response.code >= 500)
        }
    }

    suspend fun createDocumentConspect(materialFileId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val payload = JSONObject().put("materialFileId", materialFileId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/documents/conspect")
            .post(payload.toRequestBody(JSON))
            .build()
        val longClient = client.newBuilder().readTimeout(180, TimeUnit.SECONDS).build()
        val response = try { longClient.newCall(request).execute() } catch (error: Exception) {
            Diagnostics.warn(appContext, "document conspect network error: ${error.message}")
            null
        } ?: return@withContext networkFailure()
        response.use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (response.isSuccessful && json?.optBoolean("accepted") == true) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, json?.optString("code").orEmpty(), json?.optString("error").orEmpty().ifBlank { "Не удалось подготовить конспект" }, response.code >= 500)
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

    suspend fun usage(): ApiResult<UsageSummary> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/usage")
            .get()
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(response.code, "", "Не удалось получить лимиты", response.code >= 500)
            }
            val recording = json.optJSONObject("recording")
            val qa = json.optJSONObject("qa")
            val study = json.optJSONObject("studyKit")
            val plan = json.optJSONObject("plan")
            ApiResult.Ok(
                UsageSummary(
                    recordingUsedMinutes = recording?.optInt("usedMinutes") ?: 0,
                    recordingLimitMinutes = recording?.optInt("limitMinutes") ?: 180,
                    recordingRemainingMinutes = recording?.optInt("remainingMinutes") ?: 0,
                    qaUsed = qa?.optInt("used") ?: 0,
                    qaLimit = qa?.optInt("limit") ?: 10,
                    qaRemaining = qa?.optInt("remaining") ?: 0,
                    studyKitUsed = study?.optInt("used") ?: 0,
                    studyKitLimit = study?.optInt("limit") ?: 5,
                    studyKitRemaining = study?.optInt("remaining") ?: 0,
                    planId = plan?.optString("id").orEmpty(),
                    planName = plan?.optString("name").orEmpty(),
                    storageDays = json.optInt("storageDays", 14).coerceAtLeast(3),
                ),
            )
        }
    }

    suspend fun notifications(): ApiResult<InboxPayload> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder().url("$baseUrl/api/v1/mobile/notifications").get().build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось получить уведомления" }, response.code >= 500)
            }
            val array = json.optJSONArray("notifications") ?: JSONArray()
            val items = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                InboxNotification(
                    id = item.optString("id"),
                    kind = item.optString("kind"),
                    title = item.optString("title"),
                    body = item.optString("body"),
                    action = item.optString("action"),
                    actionUrl = item.optString("actionUrl"),
                    priority = item.optString("priority"),
                    paymentId = item.optString("paymentId"),
                    readAt = item.optString("readAt").takeIf { it.isNotBlank() },
                    createdAt = item.optString("createdAt").takeIf { it.isNotBlank() },
                )
            }
            ApiResult.Ok(InboxPayload(json.optInt("unread"), items))
        }
    }

    suspend fun markNotificationsRead(ids: List<String>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        // Mark all unread via GET: avoids request-body/Content-Type differences on some devices.
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/notifications/read")
            .get()
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, "", "Не удалось отметить уведомления", response.code >= 500)
        }
    }

    suspend fun adminAlerts(): ApiResult<AdminAlertsPayload> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder().url("$baseUrl/api/v1/mobile/admin/alerts").get().build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось получить уведомления" }, response.code >= 500)
            }
            val array = json.optJSONArray("alerts") ?: JSONArray()
            val alerts = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AdminAlert(
                    id = item.optString("id"),
                    type = item.optString("type"),
                    title = item.optString("title"),
                    body = item.optString("body"),
                    paymentId = item.optString("paymentId"),
                    readAt = item.optString("readAt").takeIf { it.isNotBlank() },
                    createdAt = item.optString("createdAt").takeIf { it.isNotBlank() },
                )
            }
            ApiResult.Ok(AdminAlertsPayload(json.optInt("unread"), alerts))
        }
    }

    suspend fun markAdminAlertsRead(ids: List<String>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext ApiResult.Ok(Unit)
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val body = JSONObject().put("ids", org.json.JSONArray(ids)).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/admin/alerts/read")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, "", "Не удалось отметить уведомления", response.code >= 500)
        }
    }

    suspend fun payments(): ApiResult<List<PaymentHistoryItem>> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder().url("$baseUrl/api/v1/mobile/billing/payments").get().build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(response.code, "", "Не удалось получить историю платежей", response.code >= 500)
            }
            val array = json.optJSONArray("payments") ?: JSONArray()
            val values = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                PaymentHistoryItem(
                    id = item.optString("id"),
                    planId = item.optString("planId"),
                    amountRub = item.optInt("amountRub"),
                    status = item.optString("status"),
                    paidAt = item.optString("paidAt").takeIf { it.isNotBlank() },
                    receiptStatus = item.optString("receiptStatus"),
                    receiptUrl = item.optString("receiptUrl"),
                )
            }
            ApiResult.Ok(values)
        }
    }

    suspend fun plans(): ApiResult<PlansPayload> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder().url("$baseUrl/api/v1/mobile/plans").get().build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(response.code, "", "Не удалось получить тарифы", response.code >= 500)
            }
            val current = json.optJSONObject("current")
            val array = json.optJSONArray("plans") ?: JSONArray()
            val plans = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val limits = item.optJSONObject("limits")
                PlanInfo(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    priceMonthly = item.optInt("priceMonthly"),
                    priceAnnual = item.optInt("priceAnnual"),
                    monthlyRecordingMinutes = limits?.optInt("monthlyRecordingMinutes") ?: 0,
                    maxSessionMinutes = limits?.optInt("maxSessionMinutes") ?: 0,
                    dailyQa = limits?.optInt("dailyQa") ?: 0,
                    dailyStudyKit = limits?.optInt("dailyStudyKit") ?: 0,
                    monthlyDocuments = limits?.optInt("monthlyDocuments") ?: 0,
                    storageDays = limits?.optInt("storageDays") ?: 0,
                )
            }
            val topUpJson = json.optJSONObject("topUp")
            ApiResult.Ok(PlansPayload(
                currentPlanId = current?.optString("planId").orEmpty(),
                currentPlanName = current?.optString("planName").orEmpty(),
                testBilling = json.optBoolean("testBilling", false),
                billingReady = json.optBoolean("billingReady", false),
                plans = plans,
                topUp = topUpJson?.let {
                    TopUpInfo(
                        id = it.optString("id"),
                        name = it.optString("name"),
                        priceRub = it.optInt("priceRub"),
                        minutes = it.optInt("minutes"),
                    )
                },
            ))
        }
    }

    suspend fun createTopUp(): ApiResult<String> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/billing/topup")
            .post("{}".toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            val url = json?.optString("confirmationUrl").orEmpty()
            if (response.isSuccessful && url.isNotBlank()) ApiResult.Ok(url)
            else ApiResult.Failure(response.code, json?.optString("code").orEmpty(), json?.optString("error").orEmpty().ifBlank { "Не удалось создать платёж" }, response.code >= 500)
        }
    }

    suspend fun createPayment(planId: String): ApiResult<String> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val body = JSONObject().put("planId", planId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/billing/create")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            val url = json?.optString("confirmationUrl").orEmpty()
            if (response.isSuccessful && url.isNotBlank()) ApiResult.Ok(url)
            else ApiResult.Failure(response.code, json?.optString("code").orEmpty(), json?.optString("error").orEmpty().ifBlank { "Не удалось создать платёж" }, response.code >= 500)
        }
    }

    suspend fun testPurchase(planId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val body = JSONObject().put("planId", planId).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/billing/test")
            .post(body.toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, json?.optString("code").orEmpty(), json?.optString("error").orEmpty().ifBlank { "Не удалось выполнить тестовый платёж" }, response.code >= 500)
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

    suspend fun supportMessages(): ApiResult<SupportPayload> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder().url("$baseUrl/api/v1/mobile/support").get().build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (!response.isSuccessful || json == null) {
                return@use ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось загрузить поддержку" }, response.code >= 500)
            }
            val thread = json.optJSONObject("thread")
            val array = json.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                SupportMessageItem(
                    id = item.optString("id"),
                    role = item.optString("role"),
                    text = item.optString("text"),
                    createdAt = item.optString("createdAt").takeIf { it.isNotBlank() },
                )
            }
            ApiResult.Ok(SupportPayload(thread?.optString("id").orEmpty(), messages))
        }
    }

    suspend fun sendSupportMessage(text: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val request = Request.Builder()
            .url("$baseUrl/api/v1/mobile/support")
            .post(JSONObject().put("text", text).toString().toRequestBody(JSON))
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось отправить сообщение" }, response.code >= 500)
        }
    }

    suspend fun assignSubject(sessionId: String, subjectId: String?, name: String?): ApiResult<AssignedSubject> = withContext(Dispatchers.IO) {
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
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (response.isSuccessful && json != null) {
                ApiResult.Ok(AssignedSubject(json.optString("subjectId"), json.optString("subject")))
            } else {
                ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось назначить предмет" }, false)
            }
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
                    sourceTitle = item.optString("sourceTitle"),
                    sourceType = item.optString("sourceType"),
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

    suspend fun deleteAudioSession(sessionId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (!ensureSession()) return@withContext ApiResult.Failure(401, "auth_required", "Войдите снова", false)
        val encoded = java.net.URLEncoder.encode(sessionId, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/api/materials/audio-sessions/$encoded")
            .delete()
            .build()
        val response = execute(request) ?: return@withContext networkFailure()
        response.use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (response.isSuccessful) ApiResult.Ok(Unit)
            else ApiResult.Failure(response.code, "", json?.optString("error").orEmpty().ifBlank { "Не удалось удалить пакет" }, false)
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
