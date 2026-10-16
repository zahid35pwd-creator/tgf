package com.omrscanner.app

import android.content.Context
import com.omrscanner.core.AnswerKeyCodec
import com.omrscanner.core.Marking
import com.omrscanner.core.ReaderOptions
import com.omrscanner.core.SheetSpec
import com.omrscanner.core.SheetTemplate
import com.omrscanner.core.TemplateCodec
import org.json.JSONArray
import org.json.JSONObject

data class ExamSettings(
    val template: SheetTemplate = SheetTemplate.OMR_SCANNER,
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
    val templateId get() = template.id
    val readerOptions get() = ReaderOptions(template = template, questionCount = questionCount, optionCount = optionCount)

    /** Name of the set code's value, e.g. "খ", or "All sets" for null. */
    fun setName(set: Int?) = set?.let { template.field(template.setField)?.labels?.getOrNull(it) } ?: "All sets"

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
    /** The key used, same format ('-' = not graded); empty for results saved before it was kept. */
    val key: String = "",
    /** The exam this sheet belongs to (results are grouped by it). */
    val examName: String = "",
) {
    /** Exam group name; older results without [examName] fall back to the label's first part. */
    val examGroup: String get() = examName.ifBlank {
        exam.substringBefore(" • ").takeUnless { it.startsWith("Page ") || it.startsWith("Set ") } ?: ""
    }.ifBlank { "Untitled exam" }

    fun toJson(): JSONObject = JSONObject()
        .put("time", time).put("exam", exam).put("roll", roll)
        .put("score", score).put("max", maxScore)
        .put("correct", correct).put("wrong", wrong).put("blank", blank).put("multiple", multiple)
        .put("answers", answers).put("key", key).put("examName", examName)

    companion object {
        fun fromJson(o: JSONObject) = SavedResult(
            o.getLong("time"), o.optString("exam"), o.optString("roll"),
            o.getDouble("score"), o.getDouble("max"),
            o.optInt("correct"), o.optInt("wrong"), o.optInt("blank"), o.optInt("multiple"),
            o.optString("answers"), o.optString("key"), o.optString("examName"),
        )
    }
}

/** Settings, answer key and saved results, kept in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("omr", Context.MODE_PRIVATE)

    var settings: ExamSettings
        get() {
            val t = template(prefs.getString("template", null))
            return ExamSettings(
                template = t,
                examName = prefs.getString("exam", "") ?: "",
                questionCount = prefs.getInt("questions", 50).coerceIn(1, t.maxQuestions),
                optionCount = prefs.getInt("options", 4).coerceIn(minOf(4, t.maxOptions), t.maxOptions),
                banglaLabels = prefs.getBoolean("bangla", false),
                perCorrect = prefs.getFloat("perCorrect", 1f).toDouble(),
                perWrong = prefs.getFloat("perWrong", 0f).toDouble(),
            )
        }
        set(s) {
            prefs.edit()
                .putString("template", s.templateId)
                .putString("exam", s.examName)
                .putInt("questions", s.questionCount)
                .putInt("options", s.optionCount)
                .putBoolean("bangla", s.banglaLabels)
                .putFloat("perCorrect", s.perCorrect.toFloat())
                .putFloat("perWrong", s.perWrong.toFloat())
                .apply()
        }

    /** Built-in sheet types followed by the ones learned from blank sheets. */
    fun templates(): List<SheetTemplate> = SheetTemplate.ALL + customTemplates()

    fun template(id: String?): SheetTemplate = templates().firstOrNull { it.id == id } ?: SheetTemplate.OMR_SCANNER

    private fun customTemplates(): List<SheetTemplate> {
        val arr = try { JSONArray(prefs.getString("templates", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i -> try { TemplateCodec.decode(arr.getString(i)) } catch (e: Exception) { null } }
    }

    fun addTemplate(t: SheetTemplate) {
        val arr = JSONArray()
        for (old in customTemplates()) if (old.id != t.id) arr.put(TemplateCodec.encode(old))
        arr.put(TemplateCodec.encode(t))
        prefs.edit().putString("templates", arr.toString()).apply()
    }

    fun deleteTemplate(id: String) {
        val arr = JSONArray()
        for (old in customTemplates()) if (old.id != id) arr.put(TemplateCodec.encode(old))
        prefs.edit().putString("templates", arr.toString()).apply()
        if (prefs.getString("template", null) == id) prefs.edit().remove("template").apply()
    }

    private fun keyPref(set: Int?) = "key:${settings.templateId}:${set ?: "all"}"

    /**
     * The answer key stored for [set] (null = the key used for all sets), sized to the current question
     * count; null entries are not graded.
     */
    fun storedKey(set: Int?): List<Int?> {
        val s = settings
        var text = prefs.getString(keyPref(set), null)
        // Keys saved before sheet types existed belong to this app's own sheet.
        if (text == null && set == null && s.templateId == SheetTemplate.OMR_SCANNER.id) text = prefs.getString("key", null)
        val stored = AnswerKeyCodec.decode(text ?: "", s.optionCount)
        return List(s.questionCount) { stored.getOrNull(it) }
    }

    fun saveKey(set: Int?, key: List<Int?>) { prefs.edit().putString(keyPref(set), AnswerKeyCodec.encode(key)).apply() }

    /** The key to grade a sheet with: its own set's key if one was entered, else the all-sets key. */
    fun keyFor(set: Int?): List<Int?> {
        if (set != null) storedKey(set).takeIf { k -> k.any { it != null } }?.let { return it }
        return storedKey(null)
    }

    /** Set values that have their own key. */
    fun setsWithKeys(): List<Int> {
        val f = settings.template.let { it.field(it.setField) } ?: return emptyList()
        return f.labels.indices.filter { v -> storedKey(v).any { it != null } }
    }

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

    /** Replaces all saved results (used to delete one and to undo). */
    fun setHistory(results: List<SavedResult>) {
        val arr = JSONArray()
        for (r in results) arr.put(r.toJson())
        prefs.edit().putString("history", arr.toString()).apply()
    }

    /** True once the teacher has picked an OMR sheet type (or used the app before sheet types existed). */
    val hasTemplate get() = prefs.contains("template")

    var themeMode: String
        get() = prefs.getString("theme", "dark") ?: "dark"
        set(v) = prefs.edit().putString("theme", v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()
}
