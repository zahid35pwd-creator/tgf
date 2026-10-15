package com.omrscanner.core

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One checked sheet in a batch report. */
class ReportEntry(
    /** Where the sheet came from, e.g. "Page 3". */
    val source: String,
    /** Roll / registration number, or empty if it couldn't be read. */
    val id: String,
    /** Other ID fields, e.g. "Set" to "খ". */
    val details: List<Pair<String, String>>,
    val grade: GradeResult?,
    /** Marked answer per question as shown to people ("ক", "—", "ক,খ"). */
    val answers: List<String>,
    /** Set when the sheet could not be read at all. */
    val error: String? = null,
)

/**
 * Writes a Word (.docx) report: a summary table with one row per candidate, sorted by ID, followed by
 * each candidate's answers. Built by hand (a .docx is a zip of XML parts) so it needs no libraries.
 */
object DocxReport {

    fun write(out: OutputStream, title: String, subtitle: String, entries: List<ReportEntry>, optionLabels: List<String>) {
        val sorted = entries.sortedWith(compareBy({ it.error != null }, { it.id.isEmpty() }, { it.id.padStart(12, '0') }, { it.source }))
        val body = StringBuilder()
        body.append(paragraph(title, bold = true, size = 32))
        if (subtitle.isNotBlank()) body.append(paragraph(subtitle, size = 20, color = "555555"))
        val graded = sorted.filter { it.grade != null }
        if (graded.isNotEmpty()) {
            val avg = graded.sumOf { it.grade!!.score } / graded.size
            body.append(paragraph("${sorted.size} sheets • average score ${fmt(avg)} / ${fmt(graded.first().grade!!.maxScore)}", size = 20))
        }

        val detailNames = sorted.flatMap { e -> e.details.map { it.first } }.distinct()
        val header = listOf("#", "ID") + detailNames + listOf("Score", "%", "Right", "Wrong", "Blank", "Multi", "Source")
        val rows = sorted.mapIndexed { i, e ->
            val g = e.grade
            listOf((i + 1).toString(), e.id.ifEmpty { "?" }) + detailNames.map { n -> e.details.firstOrNull { it.first == n }?.second ?: "" } +
                if (e.error != null) listOf("Not read", "", "", "", "", "", e.source)
                else if (g == null) listOf("—", "", "", "", "", "", e.source)
                else listOf("${fmt(g.score)}/${fmt(g.maxScore)}", "%.1f".format(java.util.Locale.US, g.percent), "${g.correct}", "${g.wrong}", "${g.blank}", "${g.multiple}", e.source)
        }
        // Column widths in twips; the page has about 10,100 to spare.
        val fixed = listOf(450, 1500) + detailNames.map { 800 } + listOf(1150, 700, 750, 700, 650, 750, 950)
        val scale = minOf(1.0, 10100.0 / fixed.sum())
        body.append(table(header, rows, widths = fixed.map { (it * scale).toInt() }))

        for (e in sorted) {
            body.append(paragraph("ID ${e.id.ifEmpty { "?" }}" + e.details.joinToString("") { "   ${it.first}: ${it.second}" } + "   (${e.source})", bold = true, size = 24, spaceBefore = 240))
            if (e.error != null) { body.append(paragraph("Could not read this sheet: ${e.error}", color = "C62828")); continue }
            e.grade?.let { g ->
                body.append(paragraph("Score ${fmt(g.score)} / ${fmt(g.maxScore)} (${"%.1f".format(java.util.Locale.US, g.percent)}%) — correct ${g.correct}, wrong ${g.wrong}, blank ${g.blank}, multiple ${g.multiple}", size = 20))
            }
            // Answers in a grid: question, mark, and the key where it differs.
            val cells = e.answers.mapIndexed { q, a ->
                val qg = e.grade?.questions?.getOrNull(q)
                val mark = when (qg?.verdict) {
                    Verdict.CORRECT -> " ✓"
                    Verdict.WRONG, Verdict.MULTIPLE -> " ✗ (${qg.key?.let { optionLabels[it] } ?: ""})"
                    Verdict.BLANK -> " (${qg.key?.let { optionLabels[it] } ?: ""})"
                    else -> ""
                }
                "${q + 1}. $a$mark"
            }
            val perRow = 5
            body.append(table(null, cells.chunked(perRow).map { it + List(perRow - it.size) { "" } }, size = 18))
        }

        ZipOutputStream(out).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", RELS)
            put("word/document.xml", document(body.toString()))
        }
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun run(text: String, bold: Boolean = false, size: Int = 20, color: String? = null) =
        "<w:r><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Nirmala UI\"/>" +
            (if (bold) "<w:b/>" else "") + (color?.let { "<w:color w:val=\"$it\"/>" } ?: "") +
            "<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r>"

    private fun paragraph(text: String, bold: Boolean = false, size: Int = 20, color: String? = null, spaceBefore: Int = 0) =
        "<w:p><w:pPr><w:spacing w:before=\"$spaceBefore\" w:after=\"80\"/></w:pPr>${run(text, bold, size, color)}</w:p>"

    private fun table(header: List<String>?, rows: List<List<String>>, size: Int = 18, widths: List<Int>? = null): String {
        val sb = StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/>" +
            (if (widths != null) "<w:tblLayout w:type=\"fixed\"/>" else "") +
            "<w:tblBorders>" + listOf("top", "left", "bottom", "right", "insideH", "insideV").joinToString("") {
                "<w:$it w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BBBBBB\"/>"
            } + "</w:tblBorders></w:tblPr>" +
            (widths?.joinToString("", "<w:tblGrid>", "</w:tblGrid>") { "<w:gridCol w:w=\"$it\"/>" } ?: ""))
        fun row(cells: List<String>, head: Boolean) {
            sb.append(if (head) "<w:tr><w:trPr><w:tblHeader/></w:trPr>" else "<w:tr>")
            for ((i, c) in cells.withIndex()) {
                sb.append("<w:tc><w:tcPr>")
                widths?.getOrNull(i)?.let { sb.append("<w:tcW w:w=\"$it\" w:type=\"dxa\"/>") }
                if (head) sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E8EAF6\"/>")
                sb.append("</w:tcPr><w:p><w:pPr><w:spacing w:before=\"20\" w:after=\"20\"/></w:pPr>${run(c, head, size)}</w:p></w:tc>")
            }
            sb.append("</w:tr>")
        }
        header?.let { row(it, true) }
        rows.forEach { row(it, false) }
        return sb.append("</w:tbl><w:p/>").toString()
    }

    private fun document(body: String) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body" +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"900\" w:right=\"900\" w:bottom=\"900\" w:left=\"900\" w:header=\"500\" w:footer=\"500\" w:gutter=\"0\"/></w:sectPr>" +
            "</w:body></w:document>"

    private const val CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "</Types>"

    private const val RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "</Relationships>"
}
