package com.omrscanner.core

/** Reads and grades one sheet of a batch (e.g. one page of a scanned PDF) into a report row. */
object Batch {
    /**
     * [keyFor] gives the answer key for a set code (null = no or unreadable set); it may return null
     * when no key is known, and the sheet is then reported without a score.
     */
    fun check(
        source: String,
        image: ArgbImage,
        options: ReaderOptions,
        keyFor: (Int?) -> List<Int?>?,
        marking: Marking,
        labels: List<String>,
    ): Pair<ReportEntry, OmrResult?> = try {
        val r = OmrReader.read(image, options.copy(rectifiedPxPerMm = 0.0))
        val t = r.template
        val key = keyFor(r.set)?.takeIf { k -> k.any { it != null } }
        val grade = key?.let { Grader.grade(r.answers, it, marking) }
        val details = r.fields.filter { it.name != t.idField }.map { it.name to it.text }
        val answers = r.answers.map { a -> if (a.marked.isEmpty()) "—" else a.marked.sorted().joinToString(",") { labels[it] } }
        ReportEntry(source, r.field(t.idField)?.ascii?.takeIf { it.any { c -> c != '?' } } ?: "", details, grade, answers) to r
    } catch (e: OmrException) {
        ReportEntry(source, "", emptyList(), null, emptyList(), e.message ?: "unreadable") to null
    }
}
