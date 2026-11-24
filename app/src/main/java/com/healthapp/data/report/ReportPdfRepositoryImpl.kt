package com.healthapp.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.healthapp.R
import com.healthapp.core.AppLocale
import com.healthapp.domain.model.PeriodReport
import com.healthapp.domain.repository.ReportExportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * Draws the period report on an A4 page with the platform [PdfDocument] (no extra library).
 * Same sections and numbers as the Reports screen, in the app's current language, so it can be
 * printed or sent to a doctor. The file stays in the app cache until it is shared.
 */
class ReportPdfRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ReportExportRepository {

    private data class Section(val accent: Int, val title: String, val subtitle: String, val rows: List<Pair<String, String>>)

    override suspend fun exportPdf(report: PeriodReport): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val res = AppLocale.localized(context).resources
            val locale = res.configuration.locales[0]
            fun grouped(n: Int) = String.format(locale, "%,d", n)
            fun avgDays(n: Int) = res.getQuantityString(R.plurals.avg_over_days, n, n)

            val sections = listOf(
                Section(ORANGE, res.getString(R.string.section_nutrition),
                    if (report.nutritionDays == 0) res.getString(R.string.no_meals_period) else avgDays(report.nutritionDays),
                    if (report.nutritionDays == 0) emptyList() else listOf(
                        res.getString(R.string.energy) to res.getString(R.string.kcal_per_day_v, report.avgCalories),
                        res.getString(R.string.macro_protein) to res.getString(R.string.g_per_day_v, report.avgProtein.toString()),
                        res.getString(R.string.macro_carbs) to res.getString(R.string.g_per_day_v, report.avgCarbs.toString()),
                        res.getString(R.string.macro_fat) to res.getString(R.string.g_per_day_v, report.avgFat.toString())
                    )),
                Section(CORAL, res.getString(R.string.area_activity),
                    if (report.activityDays == 0) res.getString(R.string.no_activity_period) else avgDays(report.activityDays),
                    if (report.activityDays == 0) emptyList() else listOf(
                        res.getString(R.string.stat_steps) to res.getString(R.string.steps_per_day_v, grouped(report.avgSteps)),
                        res.getString(R.string.total_time) to res.getString(R.string.minutes_v, report.totalExerciseMinutes),
                        res.getString(R.string.energy_recorded) to res.getString(R.string.kcal_v, report.totalCaloriesBurned)
                    )),
                Section(SKY, res.getString(R.string.stat_water),
                    if (report.waterDays == 0) res.getString(R.string.no_water_period) else avgDays(report.waterDays),
                    if (report.waterDays == 0) emptyList() else listOf(
                        res.getString(R.string.water_amount) to res.getString(R.string.ml_per_day_v, report.avgWaterMl)
                    )),
                Section(VIOLET, res.getString(R.string.section_habits),
                    if (report.activeHabits == 0) res.getString(R.string.no_habits_created)
                    else res.getQuantityString(R.plurals.tracking_habits, report.activeHabits, report.activeHabits),
                    if (report.activeHabits == 0) emptyList() else listOf(
                        res.getString(R.string.completions) to report.completedHabits.toString()
                    ))
            )

            val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            val range = if (report.start == report.end) report.start.format(dateFmt)
                else "${report.start.format(dateFmt)} – ${report.end.format(dateFmt)}"
            val generated = res.getString(R.string.pdf_generated,
                LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)))

            val doc = PdfDocument()
            try {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
                draw(page.canvas, res.getString(R.string.link_reports), range, generated, sections, res.getString(R.string.pdf_disclaimer))
                doc.finishPage(page)

                val dir = File(context.cacheDir, "reports").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() } // keep only the latest report on disk
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                File(dir, "health-report-${report.start}_${report.end}-$stamp.pdf").also { file ->
                    file.outputStream().use { doc.writeTo(it) }
                }
            } finally {
                doc.close()
            }
        }
    }

    private fun draw(c: Canvas, title: String, range: String, generated: String, sections: List<Section>, disclaimer: String) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        // Header band
        fill.color = INDIGO
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 112f, fill)
        c.drawText(title, MARGIN, 52f, text(24f, Color.WHITE, bold = true))
        c.drawText(range, MARGIN, 78f, text(13f, Color.WHITE))
        c.drawText(generated, MARGIN, 97f, text(10f, 0xFFD6DAFF.toInt()))

        var y = 140f
        val label = text(12f, MUTED)
        val value = text(12f, INK, bold = true)
        val line = Paint().apply { color = LINE; strokeWidth = 0.8f }
        for (s in sections) {
            val height = 52f + s.rows.size * ROW_H + 12f
            fill.color = SURFACE
            c.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + height), 10f, 10f, fill)
            fill.color = s.accent
            c.drawRoundRect(RectF(MARGIN, y, MARGIN + 6f, y + height), 3f, 3f, fill)
            c.drawText(s.title, MARGIN + 20f, y + 24f, text(15f, INK, bold = true))
            c.drawText(s.subtitle, MARGIN + 20f, y + 42f, text(10.5f, MUTED))
            var ry = y + 52f
            for ((l, v) in s.rows) {
                c.drawLine(MARGIN + 20f, ry, PAGE_W - MARGIN - 16f, ry, line)
                c.drawText(l, MARGIN + 20f, ry + 18f, label)
                c.drawText(v, PAGE_W - MARGIN - 16f - value.measureText(v), ry + 18f, value)
                ry += ROW_H
            }
            y += height + 14f
        }

        // Disclaimer, wrapped to the page width, pinned to the bottom.
        val small = text(9.5f, MUTED)
        val width = (PAGE_W - 2 * MARGIN).toInt()
        val layout = StaticLayout.Builder.obtain(disclaimer, 0, disclaimer.length, small, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        c.save()
        c.translate(MARGIN, PAGE_H - 40f - layout.height)
        layout.draw(c)
        c.restore()
    }

    private companion object {
        // A4 in PostScript points.
        const val PAGE_W = 595
        const val PAGE_H = 842
        const val MARGIN = 40f
        const val ROW_H = 28f
        // Print colours (always light), matching the app's HealthPalette accents.
        val INDIGO = 0xFF4F46E5.toInt()
        val ORANGE = 0xFFF97316.toInt()
        val CORAL = 0xFFF43F5E.toInt()
        val SKY = 0xFF0EA5E9.toInt()
        val VIOLET = 0xFF8B5CF6.toInt()
        val INK = 0xFF111827.toInt()
        val MUTED = 0xFF6B7280.toInt()
        val LINE = 0xFFE5E7EB.toInt()
        val SURFACE = 0xFFF5F6FA.toInt()
    }
}
