package com.omrscanner.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.ProgressRing
import com.omrscanner.app.ui.ZoomImageView
import com.omrscanner.app.ui.countUp
import com.omrscanner.core.GradeResult
import com.omrscanner.core.Grader
import com.omrscanner.core.MarkStatus
import com.omrscanner.core.OmrException
import com.omrscanner.core.OmrReader
import com.omrscanner.core.OmrResult
import com.omrscanner.core.QuestionReading
import com.omrscanner.core.Verdict

/**
 * Reads one picture of a sheet and shows the result. Started from the home screen, from another app's
 * "share"/"open with", or (with [EXTRA_KEY_MODE]) to read a filled-in sheet as the answer key.
 */
class ScanActivity : BaseActivity() {
    private lateinit var store: Store
    private lateinit var settings: ExamSettings
    private var keyMode = false
    private var saved = false
    private var roll = ""
    private var tab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        settings = store.settings
        keyMode = intent.getBooleanExtra(EXTRA_KEY_MODE, false)

        val uri = ImageSources.sharedUri(intent)
        if (uri == null) {
            showError("No picture was received.")
            return
        }
        process(uri)
    }

    private fun process(uri: Uri) {
        val (steps, _) = processingScreen(
            if (keyMode) "Reading the answer key" else "Reading the sheet",
            listOf("Opening the picture", "Finding the sheet", "Reading the bubbles", if (keyMode) "Collecting answers" else "Grading"),
            settings.template.name,
        )
        steps.autoAdvance()
        val options = settings.readerOptions
        Thread {
            var result: OmrResult? = null
            var image: Bitmap? = null
            var error: String? = null
            try {
                val picture = ImageSources.load(this, uri) ?: throw OmrException("Could not open this picture.")
                result = OmrReader.read(picture, options)
                image = ResultImage.render(result, if (keyMode) null else gradeOf(result))
            } catch (e: OmrException) {
                error = e.message
            } catch (e: OutOfMemoryError) {
                error = "Not enough memory to read this picture. Try a smaller photo."
            } catch (e: Exception) {
                error = "Could not read this picture (${e.javaClass.simpleName})."
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val r = result
                if (r == null) showError(error ?: "Unknown error") else { roll = r.roll; showResult(r, image) }
            }
        }.start()
    }

    private fun gradeOf(result: OmrResult): GradeResult? {
        val key = store.keyFor(result.set)
        if (key.all { it == null }) return null
        return Grader.grade(result.answers, key, settings.marking)
    }

    // ---- Error -------------------------------------------------------------------------------

    private fun showError(message: String) {
        val footer = footerBar(
            button("Choose another", ButtonStyle.SECONDARY, Icon.GALLERY) { ImageSources.openGallery(this) },
            button("Retake", ButtonStyle.PRIMARY, Icon.CAMERA) { ImageSources.openCamera(this) },
        )
        val page = scrollingPage("Couldn't read the sheet", footer = footer)
        page.add(statusStrip("READ FAILED  //  ${settings.template.name}", DotColor.ERR), block(8))
        val top = vertical().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(16), 0, dp(20)) }
        top.add(iconBadge(Icon.WARNING, Ui.RED, Ui.RED_BG, 88, 40), LinearLayout.LayoutParams(dp(88), dp(88)).apply { bottomMargin = dp(16) })
        top.add(text(message, TextStyle.HEADING).apply { gravity = Gravity.CENTER }, wrap())
        page.add(top, block(8))
        page.add(sectionHeader("Try this"), block(4))
        val tips = card(6)
        tips.add(listRow(Icon.SHEET, "Right sheet type", "This picture is read as \"${settings.template.name}\". Change it on the Home tab if needed.", chevron = false), block(0))
        tips.add(listRow(Icon.SCAN, "Whole sheet in view", "All 4 black corner squares visible, sheet filling most of the frame", Ui.GREEN, Ui.GREEN_BG, chevron = false), block(0))
        tips.add(listRow(Icon.INFO, "Even light", "No heavy shadow or glare on the bubbles", Ui.ORANGE, Ui.ORANGE_BG, chevron = false), block(0))
        page.add(tips, block(0))
    }

    // ---- Result ------------------------------------------------------------------------------

    private fun showResult(result: OmrResult, image: Bitmap?) {
        val grade = if (keyMode) null else gradeOf(result)
        val footer = when {
            keyMode -> footerBar(
                button("Retake", ButtonStyle.SECONDARY, Icon.CAMERA) { ImageSources.openCamera(this) },
                button("Save as key", ButtonStyle.PRIMARY, Icon.CHECK) { saveAsKey(result) },
            )
            grade != null -> footerBar(
                saveButton(result, grade),
                button("Next sheet", ButtonStyle.PRIMARY, Icon.CAMERA) { ImageSources.openCamera(this) },
            )
            else -> footerBar(
                button("Use as key", ButtonStyle.SECONDARY, Icon.KEY) { saveAsKey(result) },
                button("Next sheet", ButtonStyle.PRIMARY, Icon.CAMERA) { ImageSources.openCamera(this) },
            )
        }
        val gallery = iconButton(Icon.GALLERY) { ImageSources.openGallery(this) }
        val page = scrollingPage(if (keyMode) "Answer key" else "Result", footer = footer, actions = *arrayOf(gallery))
        page.add(when {
            keyMode -> statusStrip("KEY SHEET READ  //  ${settings.template.name}", DotColor.OK)
            grade != null -> statusStrip("SHEET READ  //  ${settings.template.name}", DotColor.OK)
            else -> statusStrip("NO ANSWER KEY  //  ${settings.template.name}", DotColor.BUSY)
        }, block(12))

        when {
            keyMode -> page.add(keyHero(result), block(12))
            grade != null -> page.add(scoreHero(result, grade), block(12))
            else -> page.add(noKeyHero(result), block(12))
        }

        if (grade != null) {
            val tiles = horizontal()
            tiles.add(statTile("${grade.correct}", "Correct", Ui.GREEN, Ui.GREEN_BG), weighted().apply { marginEnd = dp(8) })
            tiles.add(statTile("${grade.wrong}", "Wrong", Ui.RED, Ui.RED_BG), weighted().apply { marginEnd = dp(8) })
            tiles.add(statTile("${grade.blank}", "Blank", Ui.ORANGE, Ui.ORANGE_BG), weighted().apply { marginEnd = dp(8) })
            tiles.add(statTile("${grade.multiple}", "Multiple", Ui.PURPLE, Ui.PURPLE_BG), weighted())
            page.add(tiles, block(12))
        }
        val review = result.answers.count { it.needsReview }
        if (review > 0) page.add(banner(Icon.WARNING, "$review question${if (review > 1) "s" else ""} had faint or erased marks — check them on the sheet.", Ui.ORANGE, Ui.ORANGE_BG), block(12))

        val body = FrameLayout(this)
        val tabs = FrameLayout(this)
        fun fill() {
            body.removeAllViews()
            body.addView(if (tab == 0 && image != null) sheetView(image, grade != null) else answersView(result, grade))
        }
        fun tabBar() {
            tabs.removeAllViews()
            tabs.addView(segmented(listOf("Sheet", "Answers"), tab) { i -> if (i != tab) { tab = i; tabBar(); fill() } })
        }
        if (image != null) {
            tabBar()
            page.add(tabs, block(12))
        }
        page.add(body, block(0))
        fill()
    }

    private fun banner(icon: Icon, message: String, fg: Int, bg: Int): View {
        val row = horizontal().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(bg, dp(16).toFloat())
        }
        row.add(iconBadge(icon, fg, android.graphics.Color.TRANSPARENT, 28, 20), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
        row.add(text(message, 14f, fg), weighted())
        return row
    }

    private fun scoreHero(result: OmrResult, grade: GradeResult): View {
        val c = card(18)
        val top = horizontal()
        val ring = ProgressRing(this, Ui.CARD_ALT, Ui.bandColor(grade.percent), Ui.TEXT).apply {
            strokeDp = 9f
            label = pct(grade.percent)
            setProgress((grade.percent / 100).toFloat())
        }
        top.add(ring, LinearLayout.LayoutParams(dp(104), dp(104)).apply { marginEnd = dp(18) })
        val t = vertical()
        t.add(text(settings.examName.ifBlank { "SCORE" }.uppercase(), TextStyle.OVERLINE, Ui.MUTED).apply { maxLines = 1 }, block(2))
        val score = text("0", 38f, Ui.TEXT, true).apply { letterSpacing = -0.02f }
        val scoreRow = horizontal().apply { gravity = Gravity.BOTTOM }
        scoreRow.add(score, wrap())
        scoreRow.add(text(" / ${fmt(grade.maxScore)}", 18f, Ui.MUTED, true).apply { setPadding(0, 0, 0, dp(6)) }, wrap())
        t.add(scoreRow, block(4))
        score.countUp(grade.score) { v -> if (grade.score == Math.floor(grade.score)) Math.round(v).toString() else fmt(Math.round(v * 100) / 100.0) }
        t.add(text(verdictLine(grade.percent), 13f, Ui.bandColor(grade.percent), true), wrap())
        top.add(t, weighted())
        c.add(top, block(16))
        c.add(identityRow(result), block(0))
        return c
    }

    private fun verdictLine(p: Double) = when {
        p >= 80 -> "Excellent"
        p >= 60 -> "Good"
        p >= 40 -> "Needs work"
        else -> "Low score"
    }

    /** Roll / ID (tap to correct) with the set and other fields as chips. */
    private fun identityRow(result: OmrResult): View {
        val box = vertical().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val r = dp(16).toFloat()
            background = ripple(rounded(Ui.CARD_ALT, r), r)
        }
        val line = horizontal()
        val label = vertical()
        label.add(text(result.template.idField ?: "Roll / ID", TextStyle.CAPTION, Ui.MUTED), wrap())
        val value = text(roll.ifBlank { "Not read" }, 20f, Ui.TEXT, true)
        label.add(value, wrap())
        line.add(label, weighted())
        result.set?.let { line.add(chip("Set ${settings.setName(it)}"), wrap().apply { marginEnd = dp(8) }) }
        line.add(iconBadge(Icon.EDIT, Ui.PRIMARY, Ui.CARD, 36, 18), LinearLayout.LayoutParams(dp(36), dp(36)))
        box.add(line, block(0))
        val others = result.fields.filter { it.name != result.template.idField && it.name != result.template.setField }
        if (others.isNotEmpty()) box.add(text(others.joinToString("   ") { "${it.name}: ${it.text}" }, 13f, Ui.MUTED).apply { setPadding(0, dp(6), 0, 0) }, block(0))
        if (result.rollDigits.any { it == null }) box.add(text("Some digits were not filled clearly (?). Tap to correct.", 12.5f, Ui.ORANGE).apply { setPadding(0, dp(6), 0, 0) }, block(0))
        box.setOnClickListener {
            bottomSheet("Correct the roll / ID") { col, d ->
                val (field, edit) = inputField("Roll / ID number", roll, InputType.TYPE_CLASS_TEXT)
                col.add(field, block(16))
                col.add(button("Done") { roll = edit.text.toString().trim(); value.text = roll.ifBlank { "Not read" }; d.dismiss() }, block(0))
                edit.requestFocus()
            }
        }
        return box
    }

    private fun noKeyHero(result: OmrResult): View {
        val c = card(18)
        val row = horizontal()
        row.add(iconBadge(Icon.KEY, Ui.ORANGE, Ui.ORANGE_BG, 52, 26), LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(14) })
        val t = vertical()
        t.add(text("No answer key yet", TextStyle.HEADING), wrap())
        t.add(text("Showing the marked answers only. Set a key to get scores — or use this sheet as the key.", 13.5f, Ui.MUTED), wrap())
        row.add(t, weighted())
        c.add(row, block(14))
        c.add(identityRow(result), block(0))
        return c
    }

    private fun keyHero(result: OmrResult): View {
        val c = card(18)
        val filled = result.answers.count { it.status == MarkStatus.SINGLE }
        val problems = result.answers.size - filled
        val row = horizontal()
        val ring = ProgressRing(this, Ui.CARD_ALT, if (problems == 0) Ui.GREEN else Ui.PRIMARY, Ui.TEXT).apply {
            strokeDp = 7f
            label = "$filled"
            setProgress(filled.toFloat() / result.answers.size.coerceAtLeast(1))
        }
        row.add(ring, LinearLayout.LayoutParams(dp(76), dp(76)).apply { marginEnd = dp(16) })
        val t = vertical()
        t.add(text("Answer key detected", TextStyle.HEADING), wrap())
        t.add(text("$filled of ${result.answers.size} questions have one answer marked.", 13.5f, Ui.MUTED), wrap())
        if (settings.template.setField != null) t.add(chip("For ${settings.setName(result.set)}"), wrap().apply { topMargin = dp(8) })
        row.add(t, weighted())
        c.add(row, block(if (problems > 0) 12 else 0))
        if (problems > 0) c.add(banner(Icon.INFO, "$problems blank or multiple — these won't be graded. You can fix them on the Answer key tab.", Ui.ORANGE, Ui.ORANGE_BG), block(0))
        return c
    }

    private fun sheetView(image: Bitmap, graded: Boolean): View {
        val box = vertical()
        val holder = card(6)
        val zoom = ZoomImageView(this).apply {
            setImage(image)
            maxHeightPx = (resources.displayMetrics.heightPixels * 0.72).toInt()
        }
        holder.add(zoom, block(0))
        box.add(holder, block(8))
        val legend = horizontal().apply { gravity = Gravity.CENTER }
        val items = if (graded) listOf("Correct" to Ui.GREEN, "Wrong" to Ui.RED, "Missed" to Ui.ORANGE, "Multiple" to Ui.PURPLE)
        else listOf("Marked" to Ui.BLUE)
        for ((name, color) in items) {
            legend.add(View(this).apply { background = rounded(color, dp(5).toFloat()) }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(6) })
            legend.add(text(name, 12.5f, Ui.MUTED), wrap().apply { marginEnd = dp(14) })
        }
        box.add(legend, block(4))
        box.add(text("Pinch or double-tap to zoom", 12f, Ui.MUTED).apply { gravity = Gravity.CENTER }, block(0))
        return box
    }

    private fun answersView(result: OmrResult, grade: GradeResult?): View {
        val c = card(12)
        val perRow = 5
        result.answers.chunked(perRow).forEach { chunk ->
            val row = horizontal()
            chunk.forEachIndexed { i, a ->
                row.add(pill(a, grade), weighted().apply { if (i < perRow - 1) marginEnd = dp(6) })
            }
            repeat(perRow - chunk.size) { i -> row.add(View(this), weighted().apply { if (chunk.size + i < perRow - 1) marginEnd = dp(6) }) }
            c.add(row, block(6))
        }
        return c
    }

    private fun colours(v: Verdict?) = when (v) {
        Verdict.CORRECT -> Ui.GREEN to Ui.GREEN_BG
        Verdict.WRONG -> Ui.RED to Ui.RED_BG
        Verdict.BLANK -> Ui.ORANGE to Ui.ORANGE_BG
        Verdict.MULTIPLE -> Ui.PURPLE to Ui.PURPLE_BG
        else -> Ui.ON_PRIMARY_LIGHT to Ui.CARD_ALT
    }

    private fun marks(a: QuestionReading) = when (a.status) {
        MarkStatus.BLANK -> "—"
        else -> a.marked.sorted().joinToString(",") { settings.label(it) }
    }

    private fun pill(a: QuestionReading, grade: GradeResult?): View {
        val g = grade?.questions?.getOrNull(a.question)
        val (fg, bg) = colours(g?.verdict)
        val box = vertical().apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(7))
            val r = dp(12).toFloat()
            background = ripple(rounded(bg, r, if (a.needsReview) Ui.ORANGE else Ui.DIVIDER, if (a.needsReview) dp(1.5f) else dp(1)), r)
            setOnClickListener { questionSheet(a, grade) }
        }
        box.add(text("${a.question + 1}", 11f, Ui.MUTED).apply { gravity = Gravity.CENTER }, wrap().apply { gravity = Gravity.CENTER_HORIZONTAL })
        box.add(text(marks(a), 15f, fg, true).apply { gravity = Gravity.CENTER; maxLines = 1 }, wrap().apply { gravity = Gravity.CENTER_HORIZONTAL })
        return box
    }

    private fun questionSheet(a: QuestionReading, grade: GradeResult?) = bottomSheet("Question ${a.question + 1}") { col, _ ->
        val g = grade?.questions?.getOrNull(a.question)
        val (fg, bg) = colours(g?.verdict)
        val verdict = when (g?.verdict) {
            Verdict.CORRECT -> "Correct"
            Verdict.WRONG -> "Wrong"
            Verdict.BLANK -> "Not answered"
            Verdict.MULTIPLE -> "Several marks"
            Verdict.UNKEYED -> "No key for this question"
            null -> if (a.status == MarkStatus.SINGLE) "Marked" else if (a.status == MarkStatus.BLANK) "Not answered" else "Several marks"
        }
        col.add(chip(verdict, fg, bg), wrap().apply { bottomMargin = dp(14) })
        val tiles = horizontal()
        tiles.add(statTile(marks(a), "Marked", Ui.TEXT, Ui.CARD_ALT), weighted().apply { marginEnd = dp(8) })
        if (grade != null) tiles.add(statTile(g?.key?.let { settings.label(it) } ?: "—", "Key", Ui.GREEN, Ui.GREEN_BG), weighted())
        col.add(tiles, block(14))
        col.add(text("How dark each bubble is", TextStyle.LABEL), block(6))
        a.scores.forEachIndexed { o, v ->
            val row = horizontal()
            row.add(text(settings.label(o), 14f, Ui.MUTED, true), LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.add(progressBar(100, (v * 100).toInt().coerceIn(0, 100), if (o in a.marked) Ui.PRIMARY else Ui.MUTED), weighted())
            col.add(row, block(8))
        }
        if (a.needsReview) col.add(banner(Icon.WARNING, "A faint or erased mark — worth checking on the sheet.", Ui.ORANGE, Ui.ORANGE_BG), block(0))
    }

    private fun saveButton(result: OmrResult, grade: GradeResult): TextView {
        lateinit var b: android.widget.Button
        b = button(if (saved) "Saved" else "Save", ButtonStyle.SECONDARY, if (saved) Icon.CHECK else Icon.SAVE) {
            if (saved) return@button
            store.addResult(
                SavedResult(
                    time = System.currentTimeMillis(), exam = examLabel(result), roll = roll,
                    score = grade.score, maxScore = grade.maxScore, correct = grade.correct, wrong = grade.wrong,
                    blank = grade.blank, multiple = grade.multiple, answers = answerString(result),
                    key = keyString(grade), examName = settings.examName,
                ),
            )
            saved = true
            b.text = "Saved"
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(com.omrscanner.app.ui.IconDrawable(Icon.CHECK, Ui.ON_PRIMARY_LIGHT, dp(20)), null, null, null)
            snackbar("Result saved")
        }
        return b
    }

    private fun saveAsKey(result: OmrResult) {
        store.saveKey(if (keyMode) result.set else null, result.answers.map { it.answer })
        if (keyMode) {
            toast("Answer key saved")
            setResult(RESULT_OK)
            finish()
        } else {
            // Show this same sheet graded against its own key (a useful sanity check).
            showResult(result, ResultImage.render(result, gradeOf(result)))
            snackbar("Answer key saved")
        }
    }

    private fun examLabel(result: OmrResult) =
        listOfNotNull(settings.examName.takeIf { it.isNotBlank() }, result.set?.let { "Set ${settings.setName(it)}" }).joinToString(" • ")

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = ImageSources.resultUri(this, requestCode, resultCode, data) ?: return
        startActivity(
            Intent(this, ScanActivity::class.java).setData(uri)
                .putExtra(EXTRA_KEY_MODE, keyMode)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        finish()
    }

    companion object {
        const val EXTRA_KEY_MODE = "keyMode"

        /** One character per question for saved results: A–E, '-' blank, '*' several marks. */
        fun answerString(result: OmrResult) = result.answers.joinToString("") { a ->
            when (a.status) {
                MarkStatus.BLANK -> "-"
                MarkStatus.MULTIPLE -> "*"
                MarkStatus.SINGLE -> ('A' + a.marked[0]).toString()
            }
        }

        /** The key used for grading in the same format ('-' = not graded). */
        fun keyString(grade: GradeResult) = grade.questions.joinToString("") { q -> q.key?.let { ('A' + it).toString() } ?: "-" }
    }
}
