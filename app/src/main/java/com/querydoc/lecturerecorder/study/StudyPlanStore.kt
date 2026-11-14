package com.querydoc.lecturerecorder.study

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class SubjectDeadline(
    val subjectId: String,
    val subjectName: String,
    val examAtMillis: Long,
    val reminderDays: Int = 3,
) {
    val key: String
        get() = if (subjectId.isNotBlank()) "id:$subjectId" else "name:${subjectName.trim().lowercase()}"

    fun daysUntil(zone: ZoneId = ZoneId.systemDefault()): Long {
        val examDate = Instant.ofEpochMilli(examAtMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(LocalDate.now(zone), examDate)
    }
}

object StudyPlanStore {
    private const val PREFS = "study_plan"
    private const val KEY_SUBJECTS = "subject_deadlines"

    fun all(context: Context): List<SubjectDeadline> {
        val raw = prefs(context).getString(KEY_SUBJECTS, "") ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            root.keys().asSequence().mapNotNull { key ->
                val item = root.optJSONObject(key) ?: return@mapNotNull null
                val name = item.optString("subjectName").trim()
                val exam = item.optLong("examAtMillis", 0L)
                if (name.isBlank() || exam <= 0L) return@mapNotNull null
                SubjectDeadline(
                    subjectId = item.optString("subjectId"),
                    subjectName = name,
                    examAtMillis = exam,
                    reminderDays = item.optInt("reminderDays", 3).coerceIn(1, 14),
                )
            }.toList()
        }.getOrDefault(emptyList())
    }

    fun forSubject(context: Context, subjectId: String, subjectName: String): SubjectDeadline? {
        val wantedId = subjectId.trim()
        val wantedName = subjectName.trim().lowercase()
        return all(context).firstOrNull { deadline ->
            (wantedId.isNotBlank() && deadline.subjectId == wantedId) ||
                (deadline.subjectName.trim().lowercase() == wantedName)
        }
    }

    fun set(context: Context, subjectId: String, subjectName: String, examAtMillis: Long, reminderDays: Int = 3) {
        if (examAtMillis <= 0L || subjectName.trim().isBlank()) return
        val root = runCatching { JSONObject(prefs(context).getString(KEY_SUBJECTS, "") ?: "{}") }.getOrDefault(JSONObject())
        val deadline = SubjectDeadline(subjectId.trim(), subjectName.trim(), examAtMillis, reminderDays.coerceIn(1, 14))
        root.put(deadline.key, JSONObject().apply {
            put("subjectId", deadline.subjectId)
            put("subjectName", deadline.subjectName)
            put("examAtMillis", deadline.examAtMillis)
            put("reminderDays", deadline.reminderDays)
        })
        prefs(context).edit().putString(KEY_SUBJECTS, root.toString()).apply()
    }

    fun remove(context: Context, subjectId: String, subjectName: String) {
        val deadline = forSubject(context, subjectId, subjectName) ?: return
        val root = runCatching { JSONObject(prefs(context).getString(KEY_SUBJECTS, "") ?: "{}") }.getOrDefault(JSONObject())
        root.remove(deadline.key)
        prefs(context).edit().putString(KEY_SUBJECTS, root.toString()).apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_SUBJECTS).apply()
    }

    fun nearest(context: Context): SubjectDeadline? =
        all(context).minByOrNull { it.examAtMillis }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
