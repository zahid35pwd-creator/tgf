package com.omrscanner.app

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
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.omrscanner.app.ui.Icon
import com.omrscanner.app.ui.ZoomImageView
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
class SheetSetupActivity : BaseActivity() {
    private lateinit var store: Store
    private var learned: LearnedSheet? = null
    private var idGroup = -1
    private var setGroup = -1
    private var acrossRows = false
    private var bangla = false
    private var name = ""
    private lateinit var preview: ZoomImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        showIntro()
    }

    /** 1 Choose file → 2 Check → 3 Save. */
    private fun stepper(step: Int): View {
        val row = horizontal().apply { setPadding(dp(4), dp(4), dp(4), dp(4)) }
        listOf("Choose", "Check", "Save").forEachIndexed { i, label ->
            val done = i < step
            val on = i == step
            val dot = text(if (done) "✓" else "${i + 1}", 13f, if (done || on) Ui.ON_PRIMARY else Ui.MUTED, true).apply {
                gravity = Gravity.CENTER
                background = rounded(if (done) Ui.GREEN else if (on) Ui.PRIMARY else Ui.CARD_ALT, dp(13).toFloat())
            }
            row.add(dot, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(8) })
            row.add(text(label, 14f, if (on) Ui.TEXT else Ui.MUTED, on), wrap())
            if (i < 2) row.add(View(this).apply { setBackgroundColor(if (done) Ui.GREEN else Ui.DIVIDER) },
                LinearLayout.LayoutParams(0, dp(2), 1f).apply { marginStart = dp(10); marginEnd = dp(10) })
        }
        return row
    }

    private fun showIntro() {
        val page = scrollingPage("Add OMR sheet")
        page.add(monoText("STEP 1 / 3  //  CHOOSE FILE", 11.5f, Ui.PRIMARY).apply { setPadding(dp(4), 0, 0, dp(8)) }, wrap())
        page.add(stepper(0), block(18))
        val hero = vertical().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(4), 0, dp(18)) }
        hero.add(iconBadge(Icon.SPARK, Ui.PRIMARY, Ui.PRIMARY_LIGHT, 80, 36), LinearLayout.LayoutParams(dp(80), dp(80)).apply { bottomMargin = dp(14) })
        hero.add(text("Teach the app a new OMR sheet", TextStyle.TITLE).apply { gravity = Gravity.CENTER }, wrap())
        hero.add(text("Give it a BLANK copy: the PDF, a flat scan or a clear photo of the whole page. It finds every bubble, then you check and name the fields.",
            TextStyle.BODY, Ui.MUTED).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(6), dp(8), 0) }, wrap())
        page.add(hero, block(0))

        val choose = card(6)
        choose.add(listRow(Icon.PDF, "PDF of the blank sheet", "Best quality", Ui.PURPLE, Ui.PURPLE_BG) { pick("application/pdf", REQUEST_PDF) }, block(0))
        choose.add(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(68) })
        choose.add(listRow(Icon.GALLERY, "Picture or scan", "From the gallery or files") { ImageSources.openGallery(this) }, block(0))
        choose.add(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(68) })
        choose.add(listRow(Icon.CAMERA, "Take a photo", "Flat, whole page in view", Ui.GREEN, Ui.GREEN_BG) { ImageSources.openCamera(this) }, block(0))
        page.add(choose, block(14))
        page.add(banner("Sheets with black squares in the corners work best. For sheets without them, photograph the filled sheets on a darker surface with the whole page in view."), block(0))
    }

    private fun banner(message: String): View {
        val row = horizontal().apply { setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Ui.PRIMARY_LIGHT, dp(16).toFloat()) }
        row.add(iconBadge(Icon.INFO, Ui.ON_PRIMARY_LIGHT, android.graphics.Color.TRANSPARENT, 28, 20), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
        row.add(text(message, 13.5f, Ui.ON_PRIMARY_LIGHT), weighted())
        return row
    }

    private fun pick(type: String, request: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { this.type = type; addCategory(Intent.CATEGORY_OPENABLE) }
        try { startActivityForResult(intent, request) } catch (e: ActivityNotFoundException) { toast("No file picker found.") }
    }

    private fun learn(uri: Uri, pdf: Boolean) {
        processingScreen("Studying the sheet", listOf("Opening the file", "Finding the page", "Finding the bubbles", "Grouping the areas")).first.autoAdvance(900)
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
        // Keep the user where they were (tap on a block in the middle of the list, etc.) across rebuilds.
        val savedY = findScroll(window.decorView)?.scrollY ?: 0
        val page = scrollingPage("Check the sheet", footer = footerBar(
            button("Something's missing", ButtonStyle.SECONDARY) { showIntro() },
            button("Save sheet", ButtonStyle.PRIMARY, Icon.CHECK) { save() },
        ))
        page.add(monoText("STEP 2 / 3  //  CHECK & NAME", 11.5f, Ui.PRIMARY).apply { setPadding(dp(4), 0, 0, dp(8)) }, wrap())
        page.add(stepper(1), block(16))

        val questions = l.groups.filter { it.kind == GroupKind.ANSWERS }.sumOf { it.rows.size }
        val numbers = l.groups.count { it.isField }
        val tiles = horizontal()
        tiles.add(statTile("$questions", "Questions", Ui.BLUE, Ui.PRIMARY_LIGHT), weighted().apply { marginEnd = dp(8) })
        tiles.add(statTile("$numbers", "ID fields", Ui.GREEN, Ui.GREEN_BG), weighted().apply { marginEnd = dp(8) })
        tiles.add(statTile(if (l.alignment == Alignment.MARKERS) "4" else "—", "Corner marks", Ui.PURPLE, Ui.PURPLE_BG), weighted())
        page.add(tiles, block(12))
        page.add(statusStrip("$questions QUESTIONS  //  $numbers ID FIELDS  //  CORNER MARKS: ${if (l.alignment == Alignment.MARKERS) "4" else "—"}",
            if (l.alignment == Alignment.MARKERS) DotColor.OK else DotColor.BUSY), block(12))
        val notes = listOfNotNull(
            if (l.alignment == Alignment.MARKERS) null else "No corner squares: filled sheets are lined up by the page edges, so photograph them on a darker surface.",
            if (l.inkMode != InkMode.LUMINANCE) "The print is coloured, so it is ignored when reading marks." else null,
        )
        if (notes.isNotEmpty()) page.add(banner(notes.joinToString(" ")), block(12))
        page.add(banner("Check every question row and every ID column is coloured below. If some are missing, tap \"Something's missing\" and retake with better light, less tilt and all four corner squares in the frame."), block(12))

        val (nameBox, nameField) = inputField("Name of this sheet type", name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        nameField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { name = s.toString() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        page.add(nameBox, block(16))

        // The sheet with an overlay of rotate buttons.
        val holder = card(6)
        val frame = FrameLayout(this)
        preview = ZoomImageView(this).apply { maxHeightPx = (resources.displayMetrics.heightPixels * 0.7).toInt() }
        frame.addView(preview)
        val turn = horizontal().apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(if (Ui.dark) 0xCC1A1B2E.toInt() else 0xE6FFFFFF.toInt(), dp(26).toFloat())
            elevation = dp(3).toFloat()
        }
        turn.add(iconButton(Icon.ROTATE, Ui.PRIMARY, sizeDp = 44) { rotate(1) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        turn.add(iconButton(Icon.FLIP, Ui.PRIMARY, sizeDp = 44) { rotate(2) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        frame.addView(turn, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
        holder.add(frame, block(0))
        page.add(holder, block(6))
        page.add(text("Not the right way up? Use ⟳ to rotate 90° or ⇅ to turn it upside down. Pinch to zoom.", 12.5f, Ui.MUTED).apply { setPadding(dp(4), 0, dp(4), 0) }, block(8))
        val legend = horizontal()
        for (k in listOf(GroupKind.ANSWERS, GroupKind.NUMBER, GroupKind.LETTERS, GroupKind.NAME)) {
            legend.add(View(this).apply { background = rounded(colour(k), dp(5).toFloat()) }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(5) })
            legend.add(text(kindName(k), 12f, Ui.MUTED), wrap().apply { marginEnd = dp(12) })
        }
        page.add(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(legend); setPadding(dp(4), 0, 0, 0) }, block(16))

        page.add(sectionHeader("Reading options"), block(4))
        val opts = card(6)
        val lettersRow = horizontal().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        lettersRow.add(text("Option letters", 15f, Ui.TEXT, true), weighted())
        lettersRow.add(segmented(listOf("A B C", "ক খ গ"), if (bangla) 1 else 0) { i -> if ((i == 1) != bangla) { bangla = i == 1; showReview() } },
            LinearLayout.LayoutParams(dp(150), -2))
        opts.add(lettersRow, block(0))
        opts.add(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(14) })
        val acrossRow = horizontal().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val acrossText = vertical()
        acrossText.add(text("Number across the rows", 15f, Ui.TEXT, true), wrap())
        acrossText.add(text("1, 2, 3… left to right instead of down each column", 12.5f, Ui.MUTED), wrap())
        acrossRow.add(acrossText, weighted())
        acrossRow.add(android.widget.Switch(this).apply {
            isChecked = acrossRows
            thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Ui.PRIMARY, Ui.CARD))
            trackTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Ui.PRIMARY_LIGHT, Ui.DIVIDER))
            setOnCheckedChangeListener { _, on -> acrossRows = on; redraw() }
        }, wrap())
        opts.add(acrossRow, block(0))
        page.add(opts, block(16))

        page.add(sectionHeader("Detected areas"), block(4))
        page.add(text("Tap the type to change it. Mark which field is the roll / ID and which is the set code.", 13f, Ui.MUTED).apply { setPadding(dp(4), 0, dp(4), 0) }, block(8))
        l.groups.forEachIndexed { i, _ -> page.add(groupRow(i), block(10)) }
        window.decorView.post { findScroll(window.decorView)?.scrollTo(0, savedY) }
        redraw()
    }

    /** Turns the flattened sheet clockwise by [quarterTurns] × 90° and studies it again. */
    private fun rotate(quarterTurns: Int) {
        val l = learned ?: return
        processingScreen("Turning the sheet", listOf("Rotating", "Finding the bubbles", "Grouping the areas")).first.autoAdvance(700)
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
        val c = card(14)
        val head = horizontal()
        head.add(text("${i + 1}", 14f, Ui.ON_PRIMARY, true).apply {
            gravity = Gravity.CENTER
            background = rounded(colour(g.kind), dp(15).toFloat())
        }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
        head.add(text(g.describe(), 14f, Ui.TEXT), weighted())
        val type = text(kindName(g.kind) + "  ▾", 13f, Ui.ON_PRIMARY_LIGHT, true).apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            val r = dp(14).toFloat()
            background = ripple(rounded(Ui.PRIMARY_LIGHT, r), r)
            setOnClickListener { chooseKind(i) }
        }
        head.add(type, wrap())
        c.add(head, block(0))
        if (g.isField) {
            val line = horizontal().apply { setPadding(0, dp(12), 0, 0) }
            val (box, nameEdit) = inputField("Field name", g.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
            (box.getChildAt(0)).visibility = View.GONE
            nameEdit.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { g.name = s.toString().trim() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            line.add(box, weighted())
            line.add(choiceChip("Roll/ID", idGroup == i) { idGroup = if (idGroup == i) -1 else i; showReview() }, wrap().apply { marginStart = dp(8) })
            line.add(choiceChip("Set", setGroup == i) { setGroup = if (setGroup == i) -1 else i; showReview() }, wrap().apply { marginStart = dp(6) })
            c.add(line, block(0))
        }
        return c
    }

    private fun chooseKind(i: Int) = bottomSheet("Area ${i + 1} is…") { col, d ->
        val g = learned!!.groups[i]
        for (k in GroupKind.entries) {
            val on = k == g.kind
            col.add(listRow(Icon.SHEET, kindName(k), kindHelp(k), colour(k), Ui.CARD_ALT,
                trailing = if (on) android.widget.ImageView(this).apply { setImageDrawable(com.omrscanner.app.ui.IconDrawable(Icon.CHECK, Ui.PRIMARY, dp(22))) } else null,
                chevron = false) {
                d.dismiss()
                g.kind = k
                if (g.isField && g.name.isBlank()) g.name = "Code ${i + 1}"
                showReview()
            }, block(0))
        }
    }

    private fun kindHelp(k: GroupKind) = when (k) {
        GroupKind.ANSWERS -> "Question bubbles"
        GroupKind.NUMBER -> "Digits 0–9, e.g. roll or registration"
        GroupKind.LETTERS -> "A short code, e.g. set ক খ গ ঘ"
        GroupKind.NAME -> "Letters A–Z for a name"
        GroupKind.IGNORE -> "Not read"
    }

    private fun kindName(k: GroupKind) = when (k) {
        GroupKind.ANSWERS -> "Answers"
        GroupKind.NUMBER -> "Number"
        GroupKind.LETTERS -> "Letters"
        GroupKind.NAME -> "Name A–Z"
        GroupKind.IGNORE -> "Ignore"
    }

    private fun colour(k: GroupKind) = when (k) {
        GroupKind.ANSWERS -> 0xFF2563EB.toInt()
        GroupKind.NUMBER -> 0xFF16A34A.toInt()
        GroupKind.LETTERS -> 0xFF9333EA.toInt()
        GroupKind.NAME -> 0xFFEA7C0C.toInt()
        GroupKind.IGNORE -> 0xFF8A8CA3.toInt()
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
        preview.setImage(bmp)
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
