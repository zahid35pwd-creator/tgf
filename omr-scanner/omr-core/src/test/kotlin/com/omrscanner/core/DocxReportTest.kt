package com.omrscanner.core

import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocxReportTest {
    @Test
    fun writesWordReportForABatch() {
        val t = SheetTemplate.BCS_200
        val src = ImageIO.read(javaClass.getResourceAsStream("/sheets/bcs-200.png")!!)
        val key = List(200) { it % 4 }
        val entries = ArrayList<ReportEntry>()
        val expectedIds = ArrayList<String>()
        for (page in 0 until 3) {
            val rng = Random(40 + page)
            val img = java.awt.image.BufferedImage(src.width, src.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.drawImage(src, 0, 0, null)
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            g.scale(img.width / 210.0, img.width / 210.0)
            val id = List(8) { rng.nextInt(10) }
            expectedIds.add(id.joinToString(""))
            t.field("Registration")!!.columns.forEachIndexed { c, col -> SyntheticSheets.scribble(g, col[id[c]], 1.6, Ink.BLACK_PEN, rng) }
            for (q in 0 until 200) if (rng.nextInt(10) > 0) SyntheticSheets.scribble(g, t.questions[q][if (rng.nextInt(4) == 0) (q + 1) % 4 else q % 4], 1.6, Ink.BLACK_PEN, rng)
            g.dispose()
            val photo = SyntheticSheets.photograph(img, img.width / 210.0, SyntheticSheets.PhotoParams(width = 1654, height = 2339, coverage = 0.97, perspective = 0.005, noise = 2.0, blurRadius = 0, background = 0xFFFFFF), rng)
            val (entry, _) = Batch.check("Page ${page + 1}", SyntheticSheets.toArgb(photo), ReaderOptions(template = t), { key }, Marking(1.0, 0.5), t.optionLabels)
            entries.add(entry)
        }
        entries.add(ReportEntry("Page 4", "", emptyList(), null, emptyList(), "Could not find the 4 black corner squares."))
        assertEquals(expectedIds.sorted(), entries.filter { it.error == null }.map { it.id }.sorted())

        val file = File(System.getProperty("sheetOut") ?: System.getProperty("java.io.tmpdir"), "omr-report.docx")
        file.outputStream().use { DocxReport.write(it, "51st BCS preliminary", "Checked with OMR Scanner", entries, t.optionLabels) }
        ZipFile(file).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("word/document.xml")).readBytes().toString(Charsets.UTF_8)
            for (id in expectedIds) assertTrue(id in xml, "report lists $id")
            assertTrue("Could not read this sheet" in xml)
        }
    }
}
