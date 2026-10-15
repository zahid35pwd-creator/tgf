package com.omrscanner.core

/**
 * Saves learned sheet types as JSON (written and parsed here, as the core has no dependencies).
 * Coordinates are stored in millimetres with 0.01 mm precision.
 */
object TemplateCodec {

    fun encode(t: SheetTemplate): String {
        fun pt(p: Pt) = "[${n(p.x)},${n(p.y)}]"
        fun pts(ps: List<Pt>) = ps.joinToString(",", "[", "]") { pt(it) }
        fun str(s: String?) = if (s == null) "null" else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val fields = t.fields.joinToString(",", "[", "]") { f ->
            "{\"name\":${str(f.name)},\"radius\":${n(f.radius)},\"labels\":${f.labels.joinToString(",", "[", "]") { str(it) }}," +
                "\"columns\":${f.columns.joinToString(",", "[", "]") { pts(it) }}}"
        }
        return "{\"version\":1,\"id\":${str(t.id)},\"name\":${str(t.name)},\"alignment\":${str(t.alignment.name)}," +
            "\"page\":[${n(t.pageWidth)},${n(t.pageHeight)}],\"markers\":${pts(t.markerCenters)},\"markerSize\":${n(t.markerSize)}," +
            "\"answerRadius\":${n(t.answerRadius)},\"questions\":${t.questions.joinToString(",", "[", "]") { pts(it) }}," +
            "\"blocks\":${t.answerBlocks.joinToString(",", "[", "]") { b -> b.joinToString(",", "[", "]") }}," +
            "\"fields\":$fields,\"idField\":${str(t.idField)},\"setField\":${str(t.setField)}," +
            "\"orientationMarks\":${pts(t.orientationMarks)},\"ink\":${str(t.inkMode.name)}," +
            "\"labels\":${t.optionLabels.joinToString(",", "[", "]") { str(it) }}}"
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(json: String): SheetTemplate {
        val o = Json(json).value() as Map<String, Any?>
        fun num(v: Any?) = (v as Number).toDouble()
        fun pt(v: Any?) = (v as List<Any?>).let { Pt(num(it[0]), num(it[1])) }
        fun pts(v: Any?) = (v as List<Any?>).map { pt(it) }
        val page = o["page"] as List<Any?>
        return SheetTemplate(
            id = o["id"] as String,
            name = o["name"] as String,
            markerCenters = pts(o["markers"]),
            markerSize = num(o["markerSize"]),
            questions = (o["questions"] as List<Any?>).map { pts(it) },
            answerRadius = num(o["answerRadius"]),
            answerBlocks = (o["blocks"] as List<Any?>).map { b -> (b as List<Any?>).map { (it as Number).toInt() } },
            fields = (o["fields"] as List<Any?>).map { f ->
                f as Map<String, Any?>
                DigitField(f["name"] as String, (f["columns"] as List<Any?>).map { pts(it) }, num(f["radius"]), (f["labels"] as List<Any?>).map { it as String })
            },
            idField = o["idField"] as String?,
            setField = o["setField"] as String?,
            orientationMarks = pts(o["orientationMarks"]),
            inkMode = InkMode.valueOf(o["ink"] as String),
            printable = false,
            optionLabels = (o["labels"] as List<Any?>).map { it as String },
            alignment = Alignment.valueOf(o["alignment"] as String),
            pageWidth = num(page[0]),
            pageHeight = num(page[1]),
            custom = true,
        )
    }

    private fun n(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    }

    /** Minimal JSON reader: objects, arrays, strings, numbers, true/false/null. */
    private class Json(private val s: String) {
        private var i = 0

        fun value(): Any? {
            skip()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Bad JSON at $i")
            }
        }

        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++; skip()
            if (s[i] == '}') { i++; return m }
            while (true) {
                skip(); val k = str(); skip(); i++ // ':'
                m[k] = value(); skip()
                if (s[i++] == '}') return m
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++; skip()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(value()); skip()
                if (s[i++] == ']') return l
            }
        }

        private fun str(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (s[i]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t')
                        'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> sb.append(s[i])
                    }
                } else sb.append(s[i])
                i++
            }
            i++
            return sb.toString()
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
    }
}
