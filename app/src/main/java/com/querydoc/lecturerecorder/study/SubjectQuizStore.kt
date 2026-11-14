package com.querydoc.lecturerecorder.study

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SubjectQuizStats(
    val attempts: Int,
    val correct: Int,
    val distinctQuestions: Int,
    val bankSize: Int,
    val weakTopics: List<String> = emptyList(),
) {
    val accuracy: Int
        get() = if (attempts > 0) (correct * 100 / attempts) else 0

    val coverage: Int
        get() = if (bankSize > 0) (distinctQuestions * 100 / bankSize).coerceIn(0, 100) else 0
}

object SubjectQuizStore {
    private const val PREFS = "subject_quiz_progress"

    fun stats(context: Context, subjectId: String): SubjectQuizStats {
        val item = read(context).optJSONObject(subjectId)
        return SubjectQuizStats(
            attempts = item?.optInt("attempts", 0) ?: 0,
            correct = item?.optInt("correct", 0) ?: 0,
            distinctQuestions = item?.optJSONArray("questions")?.length() ?: 0,
            bankSize = item?.optInt("bankSize", 0) ?: 0,
            weakTopics = item?.optJSONObject("weak")?.let { weak ->
                weak.keys().asSequence().sortedByDescending { weak.optInt(it, 0) }.take(3).toList()
            } ?: emptyList(),
        )
    }

    fun setBankSize(context: Context, subjectId: String, bankSize: Int) {
        if (subjectId.isBlank() || bankSize <= 0) return
        val root = read(context)
        val item = root.optJSONObject(subjectId) ?: JSONObject()
        item.put("bankSize", bankSize)
        root.put(subjectId, item)
        write(context, root)
    }

    fun record(context: Context, subjectId: String, questionKey: String, correct: Boolean, bankSize: Int, conspectTitle: String = "") {
        if (subjectId.isBlank() || questionKey.isBlank()) return
        val root = read(context)
        val item = root.optJSONObject(subjectId) ?: JSONObject()
        item.put("attempts", item.optInt("attempts", 0) + 1)
        item.put("correct", item.optInt("correct", 0) + if (correct) 1 else 0)
        item.put("bankSize", bankSize.coerceAtLeast(item.optInt("bankSize", 0)))
        val questions = item.optJSONArray("questions") ?: JSONArray()
        val known = (0 until questions.length()).map { questions.optString(it) }.toMutableSet()
        if (known.add(questionKey)) {
            val updated = JSONArray()
            known.sorted().forEach { updated.put(it) }
            item.put("questions", updated)
        }
        if (!correct && conspectTitle.isNotBlank()) {
            val weak = item.optJSONObject("weak") ?: JSONObject()
            weak.put(conspectTitle, weak.optInt(conspectTitle, 0) + 1)
            item.put("weak", weak)
        }
        root.put(subjectId, item)
        write(context, root)
    }

    fun readiness(stats: SubjectQuizStats): String {
        if (stats.attempts < 8 || stats.distinctQuestions < 5) return "мало данных"
        val score = (stats.accuracy * stats.coverage) / 100
        return when {
            score >= 65 -> "высокая"
            score >= 35 -> "средняя"
            else -> "низкая"
        }
    }

    private fun read(context: Context): JSONObject =
        runCatching {
            JSONObject(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("stats", "{}") ?: "{}",
            )
        }.getOrDefault(JSONObject())

    private fun write(context: Context, value: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("stats", value.toString()).apply()
    }
}
