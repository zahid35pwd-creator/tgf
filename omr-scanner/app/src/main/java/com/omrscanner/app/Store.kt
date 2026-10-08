package com.omrscanner.app

import android.content.Context
import com.omrscanner.core.AnswerKeyCodec
import com.omrscanner.core.Marking
import com.omrscanner.core.SheetSpec
import org.json.JSONArray
import org.json.JSONObject

data class ExamSettings(
    val examName: String = "",
    val questionCount: Int = 50,
    val optionCount: Int = 4,
    val banglaLabels: Boolean = false,
    val perCorrect: Double = 1.0,
    val perWrong: Double = 0.0,
) {
    val labels: List<String> get() = if (banglaLabels) SheetSpec.BANGLA_LABELS else SheetSpec.LATIN_LABELS
    val sheetSpec get() = SheetSpec(questionCount, optionCount, labels, examName = examName)
    val marking get() = Marking(perCorrect, perWrong)

    fun label(option: Int) = labels[option]

    /** e.g. "A–D" or "ক–ঘ". */
    val labelRange get() = "${labels.first()}–${labels[optionCount - 1]}"
}

class SavedResult(
    val time: Long,
    val exam: String,
    val roll: String,
    val score: Double,
    val maxScore: Double,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val multiple: Int,
    /** One character per question: A–E, '-' for blank, '*' for several marks. */
    val answers: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("time", time).put("exam", exam).put("roll", roll)
        .put("score", score).put("max", maxScore)
        .put("correct", correct).put("wrong", wrong).put("blank", blank).put("multiple", multiple)
        .put("answers", answers)

    companion object {
        fun fromJson(o: JSONObject) = SavedResult(
            o.getLong("time"), o.optString("exam"), o.optString("roll"),
            o.getDouble("score"), o.getDouble("max"),
            o.optInt("correct"), o.optInt("wrong"), o.optInt("blank"), o.optInt("multiple"),
            o.optString("answers"),
        )
    }
}

/** Settings, answer key and saved results, kept in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("omr", Context.MODE_PRIVATE)

    var settings: ExamSettings
        get() = ExamSettings(
            examName = prefs.getString("exam", "") ?: "",
            questionCount = prefs.getInt("questions", 50),
            optionCount = prefs.getInt("options", 4),
            banglaLabels = prefs.getBoolean("bangla", false),
            perCorrect = prefs.getFloat("perCorrect", 1f).toDouble(),
            perWrong = prefs.getFloat("perWrong", 0f).toDouble(),
        )
        set(s) {
            prefs.edit()
                .putString("exam", s.examName)
                .putInt("questions", s.questionCount)
                .putInt("options", s.optionCount)
                .putBoolean("bangla", s.banglaLabels)
                .putFloat("perCorrect", s.perCorrect.toFloat())
                .putFloat("perWrong", s.perWrong.toFloat())
                .apply()
        }

    /** The answer key sized to the current question count; null entries are not graded. */
    var key: List<Int?>
        get() {
            val s = settings
            val stored = AnswerKeyCodec.decode(prefs.getString("key", "") ?: "", s.optionCount)
            return List(s.questionCount) { stored.getOrNull(it) }
        }
        set(value) { prefs.edit().putString("key", AnswerKeyCodec.encode(value)).apply() }

    fun history(): List<SavedResult> {
        val arr = try { JSONArray(prefs.getString("history", "[]")) } catch (e: Exception) { JSONArray() }
        return List(arr.length()) { SavedResult.fromJson(arr.getJSONObject(it)) }
    }

    fun addResult(r: SavedResult) {
        val arr = JSONArray()
        for (old in history()) arr.put(old.toJson())
        arr.put(r.toJson())
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory() = prefs.edit().remove("history").apply()
}
