package com.omrscanner.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.omrscanner.core.Alignment
import com.omrscanner.core.ArgbImage
import com.omrscanner.core.GroupKind
import com.omrscanner.core.InkMode
import com.omrscanner.core.LearnedSheet
import com.omrscanner.core.OmrException
import com.omrscanner.core.SheetSpec
import com.omrscanner.core.TemplateLearner
import kotlin.math.max

/**
 * Teaches the app a new OMR sheet design from a blank copy: the learner finds the bubbles, and the
 * teacher checks the result, names the number fields and saves it as a sheet type.
 */
class SheetSetupActivity : Activity() {
    private lateinit var store: Store
    private var learned: LearnedSheet? = null
    private var idGroup = -1
    private var setGroup = -1
    private var acrossRows = false
    private var bangla = false
    private var name = ""
    private lateinit var preview: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        title = "Add a sheet type"
        showIntro()
    }

    private fun showIntro() {
        val page = scrollingPage()
        val c = card()
        c.add(text("Teach the app a new OMR sheet", 20f, bold = true), block(8))
        c.add(text(
            "Give it a BLANK copy of the sheet: the PDF, a flat scan, or a clear photo of the whole page. " +
                "The app finds every bubble, then you check what it found and name the number fields (roll, registration, set…).",
            15f, Ui.MUTED,
        ), block(8))
        c.add(text(
            "Sheets with black squares in the corners work best. For sheets without them, photograph the filled sheets " +
                "on a darker surface with the whole page in view.",
            14f, Ui.MUTED,
        ), block(12))
        c.add(button("Choose PDF of the blank sheet") { pick("application/pdf", REQUEST_PDF) }, block(8))
        c.add(button("Choose picture", ButtonStyle.SECONDARY) { ImageSources.openGallery(this) }, block(8))
        c.add(button("Take photo", ButtonStyle.SECONDARY) { ImageSources.openCamera(this) }, block(0))
        page.add(c)
    }

    private fun pick(type: String, request: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { this.type = type; addCategory(Intent.CATEGORY_OPENABLE) }
        try { startActivityForResult(intent, request) } catch (e: ActivityNotFoundException) { toast("No file picker found.") }
    }

    private fun learn(uri: Uri, pdf: Boolean) {
        val box = vertical(32).apply { gravity = Gravity.CENTER; setBackgroundColor(Ui.BACKGROUND) }
        box.add(ProgressBar(this), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(16) })
        box.add(text("Finding the bubbles…", 17f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2))
        setContentView(box)
        Thread {
            var result: LearnedSheet? = null
            var error: String? = null
            try {
                val image = (if (pdf) renderFirstPage(uri) else ImageSources.load(this, uri)) ?: throw OmrException("Could not open this file.")
                result = TemplateLearner.learn(image)
                if (result.groups.none { it.kind == GroupKind.ANSWERS }) {
                    throw OmrException("No answer bubbles were found. Use a sharp, flat, blank copy of the sheet.")
                }
            } catch (e: OmrException) {
                error = e.message
            } catch (e: OutOfMemoryError) {
                error = "Not enough memory for this file."
            } catch (e: Exception) {
                error = "Could not read this file (${e.message ?: e.javaClass.simpleName})."
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val r = result
                if (r == null) { toast(error ?: "Failed"); showIntro() } else start(r)
            }
        }.start()
    }

    private fun renderFirstPage(uri: Uri): ArgbImage? {
        val fd = contentResolver.openFileDescriptor(uri, "r") ?: return null
        return PdfRenderer(fd).use { pdf ->
            pdf.openPage(0).use { p ->
                val scale = 2600.0 / max(p.width, p.height)
                val bmp = Bitmap.createBitmap((p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(0xFFFFFFFF.toInt())
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                ArgbImage(bmp.width, bmp.height, px).also { bmp.recycle() }
            }
        }
    }

    private fun start(l: LearnedSheet) {
        learned = l
        idGroup = l.groups.indexOfFirst { it.name == "Roll" }
        setGroup = l.groups.indexOfFirst { it.kind == GroupKind.LETTERS }
        bangla = l.inkMode == InkMode.RED // pink-printed sheets are usually Bangladeshi board sheets
        name = "My OMR sheet ${store.templates().count { it.custom } + 1}"
        showReview()
    }

    // ---- Review ------------------------------------------------------------------------------

    private fun showReview() {
        val l = learned ?: return
        val page = scrollingPage()

        val top = card()
        top.add(text("Check what was found", 20f, bold = true), block(6))
        val questions = l.groups.filter { it.kind == GroupKind.ANSWERS }.sumOf { it.rows.size }
        val numbers = l.groups.count { it.isField }
        top.add(text(
            "$questions questions and $numbers number/letter fields. " +
                (if (l.alignment == Alignment.MARKERS) "Lined up by the 4 corner squares." else "No corner squares: filled sheets are lined up by the page edges, so photograph them on a darker surface.") +
                (if (l.inkMode != InkMode.LUMINANCE) " The print is coloured, so it is ignored when reading marks." else ""),
            14f, Ui.MUTED,
        ), block(10))
        top.add(text("Name of this sheet type", 13f, Ui.MUTED), block(2))
        val nameField = EditText(this).apply {
            setText(name)
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { name = s.toString() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        top.add(nameField, block(8))
        top.add(text("Is the sheet the right way up in the picture below? If not, turn it:", 13f, Ui.MUTED), block(4))
        val turn = horizontal()
        turn.add(button("Rotate 90°", ButtonStyle.SECONDARY) { rotate(1) }, weighted().apply { marginEnd = dp(8) })
        turn.add(button("Turn upside down", ButtonStyle.SECONDARY) { rotate(2) }, weighted())
        top.add(turn, block(10))
        val letters = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL }
        letters.addView(RadioButton(this).apply { text = "A B C D"; id = 1 })
        letters.addView(RadioButton(this).apply { text = "ক খ গ ঘ"; id = 2 })
        letters.check(if (bangla) 2 else 1)
        letters.setOnCheckedChangeListener { _, id -> bangla = id == 2; redraw() }
        top.add(letters, block(4))
        val across = CheckBox(this).apply {
            text = "Number questions across the rows (1, 2, 3… left to right) instead of down each column"
            isChecked = acrossRows
            setOnCheckedChangeListener { _, on -> acrossRows = on; redraw() }
        }
        top.add(across, block(0))
        page.add(top, block(12))

        val holder = card().apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
        preview = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        holder.add(preview, block(0))
        page.add(holder, block(6))
        page.add(text("Blue: answers (with question numbers) • Green: number fields • Purple: letter codes • Orange: name (A–Z) • Grey: ignored", 13f, Ui.MUTED), block(12))

        val list = card()
        list.add(text("Detected areas", 16f, bold = true), block(4))
        list.add(text("Tap the type to change it. Mark which number field is the roll / ID and which is the set code.", 13f, Ui.MUTED), block(8))
        l.groups.forEachIndexed { i, g -> list.add(groupRow(i), block(10)) }
        page.add(list, block(12))

        page.add(button("Save sheet type") { save() })
        page.add(button("Start again with another file", ButtonStyle.SECONDARY) { showIntro() })
        redraw()
    }

    /** Turns the flattened sheet clockwise by [quarterTurns] × 90° and studies it again. */
    private fun rotate(quarterTurns: Int) {
        val l = learned ?: return
        val box = vertical(32).apply { gravity = Gravity.CENTER; setBackgroundColor(Ui.BACKGROUND) }
        box.add(ProgressBar(this), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(16) })
        box.add(text("Turning the sheet…", 17f, Ui.MUTED), LinearLayout.LayoutParams(-2, -2))
        setContentView(box)
        Thread {
            val result = try { TemplateLearner.learn(l.page.rotated(quarterTurns), keepOrientation = true) } catch (e: Exception) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result == null) { toast("Could not turn the sheet."); showReview(); return@runOnUiThread }
                val keepName = name
                val keepBangla = bangla
                start(result)
                name = keepName
                bangla = keepBangla
                showReview()
            }
        }.start()
    }

    private fun groupRow(i: Int): View {
        val l = learned!!
        val g = l.groups[i]
        val row = vertical()
        val head = horizontal()
        head.add(text("#${i + 1}", 15f, colour(g.kind), bold = true), LinearLayout.LayoutParams(dp(40), -2))
        head.add(text(g.describe(), 14f), weighted())
        val type = text(kindName(g.kind), 14f, Ui.PRIMARY, bold = true).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(Ui.PRIMARY_LIGHT, dp(14).toFloat())
            setOnClickListener {
                g.kind = GroupKind.entries[(g.kind.ordinal + 1) % GroupKind.entries.size]
                if (g.isField) { if (g.name.isBlank()) g.name = "Code ${i + 1}" }
                showReview()
            }
        }
        head.add(type)
        row.add(head, block(4))
        if (g.isField) {
            val line = horizontal()
            val nameEdit = EditText(this).apply {
                setText(g.name)
                setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                hint = "Field name"
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { g.name = s.toString().trim() }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
            }
            line.add(nameEdit, weighted())
            line.add(chip("Roll/ID", idGroup == i) { idGroup = if (idGroup == i) -1 else i; showReview() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
            line.add(chip("Set", setGroup == i) { setGroup = if (setGroup == i) -1 else i; showReview() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
            row.add(line, block(0))
        }
        return row
    }

    private fun chip(label: String, on: Boolean, onClick: () -> Unit) = text(label, 14f, if (on) 0xFFFFFFFF.toInt() else Ui.PRIMARY, bold = true).apply {
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = rounded(if (on) Ui.PRIMARY else Ui.CARD, dp(14).toFloat(), Ui.PRIMARY, dp(1.5f))
        setOnClickListener { onClick() }
    }

    private fun kindName(k: GroupKind) = when (k) {
        GroupKind.ANSWERS -> "Answers"
        GroupKind.NUMBER -> "Number"
        GroupKind.LETTERS -> "Letters"
        GroupKind.NAME -> "Name A–Z"
        GroupKind.IGNORE -> "Ignore"
    }

    private fun colour(k: GroupKind) = when (k) {
        GroupKind.ANSWERS -> Ui.BLUE
        GroupKind.NUMBER -> Ui.GREEN
        GroupKind.LETTERS -> Ui.PURPLE
        GroupKind.NAME -> Ui.ORANGE
        GroupKind.IGNORE -> Ui.MUTED
    }

    private fun labels() = if (bangla) SheetSpec.BANGLA_LABELS else SheetSpec.LATIN_LABELS

    /** The blank sheet with every detected area outlined, and question numbers as they will be counted. */
    private fun redraw() {
        val l = learned ?: return
        val img = l.page
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(img.pixels, 0, img.width, 0, 0, img.width, img.height)
        val c = Canvas(bmp)
        val s = l.pxPerMm.toFloat()
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.45f * s }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        l.groups.forEachIndexed { i, g ->
            ring.color = colour(g.kind)
            for (row in g.rows) for (p in row) c.drawCircle(p.x.toFloat() * s, p.y.toFloat() * s, (g.radius * 1.3).toFloat() * s, ring)
            label.color = colour(g.kind)
            label.textSize = 4f * s
            c.drawText("#${i + 1}", (g.left - g.radius * 2).toFloat() * s, (g.top - g.radius * 1.8).toFloat() * s, label)
        }
        // Question numbers exactly as the saved template will count them.
        try {
            val t = l.toTemplate("preview", name, null, null, labels(), acrossRows)
            label.color = 0xFFD84315.toInt()
            label.textSize = (t.answerRadius * 1.5).toFloat() * s
            label.textAlign = Paint.Align.RIGHT
            t.questions.forEachIndexed { q, opts ->
                val p = opts.first()
                c.drawText("${q + 1}", (p.x - t.answerRadius * 1.5).toFloat() * s, (p.y + t.answerRadius * 0.5).toFloat() * s, label)
            }
        } catch (e: IllegalArgumentException) {
            // No answer area selected yet.
        }
        preview.setImageBitmap(bmp)
    }

    private fun save() {
        val l = learned ?: return
        val fields = l.groups.filter { it.isField }
        when {
            name.isBlank() -> { toast("Give the sheet type a name"); return }
            l.groups.none { it.kind == GroupKind.ANSWERS } -> { toast("Mark at least one area as Answers"); return }
            fields.any { it.name.isBlank() } -> { toast("Give every number field a name"); return }
            fields.map { it.name }.distinct().size != fields.size -> { toast("Field names must be different"); return }
        }
        val idName = l.groups.getOrNull(idGroup)?.takeIf { it.kind != GroupKind.ANSWERS && it.kind != GroupKind.IGNORE }?.name
        val setName = l.groups.getOrNull(setGroup)?.takeIf { it.kind != GroupKind.ANSWERS && it.kind != GroupKind.IGNORE }?.name
        val t = l.toTemplate("custom-${System.currentTimeMillis()}", name.trim(), idName, setName, labels(), acrossRows)
        store.addTemplate(t)
        store.settings = store.settings.copy(
            template = t,
            questionCount = t.maxQuestions,
            optionCount = t.questions.map { it.size }.sorted()[t.questions.size / 2].coerceIn(2, 5),
            banglaLabels = bangla,
        )
        toast("Saved. \"${t.name}\" is now the sheet type in use.")
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PDF) {
            if (resultCode == RESULT_OK) data?.data?.let { learn(it, pdf = true) }
            return
        }
        ImageSources.resultUri(this, requestCode, resultCode, data)?.let { learn(it, pdf = false) }
    }

    companion object {
        private const val REQUEST_PDF = 301
    }
}
