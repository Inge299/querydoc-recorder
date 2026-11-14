package com.querydoc.lecturerecorder.study

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class StudyPlan(
    val examAtMillis: Long,
    val reminderDays: Int = 3,
)

object StudyPlanStore {
    private const val PREFS = "study_plan"
    private const val KEY_EXAM = "exam_at_millis"
    private const val KEY_REMINDER_DAYS = "reminder_days"

    fun load(context: Context): StudyPlan? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val exam = prefs.getLong(KEY_EXAM, 0L)
        if (exam <= 0L) return null
        return StudyPlan(exam, prefs.getInt(KEY_REMINDER_DAYS, 3).coerceIn(1, 14))
    }

    fun save(context: Context, examAtMillis: Long, reminderDays: Int = 3) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_EXAM, examAtMillis)
            .putInt(KEY_REMINDER_DAYS, reminderDays.coerceIn(1, 14))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun daysUntil(context: Context): Long? {
        val plan = load(context) ?: return null
        val zone = ZoneId.systemDefault()
        val examDate = Instant.ofEpochMilli(plan.examAtMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(LocalDate.now(zone), examDate)
    }
}
