package com.karelisio.miroir.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import com.karelisio.miroir.R
import com.karelisio.miroir.data.local.DayEntity
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.prefs.AppPrefs
import com.karelisio.miroir.ui.today.bestStreakOf
import com.karelisio.miroir.ui.today.display
import com.karelisio.miroir.ui.today.myAnswerPayload
import com.karelisio.miroir.ui.today.myReactionPayload
import com.karelisio.miroir.ui.today.partnerAnswerPayload
import com.karelisio.miroir.ui.today.partnerReactionPayload
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Livre souvenir : couverture puis chaque question révélée, avec vos deux réponses et vos petits mots. */
object MemoriesPdf {
    private const val PAGE_W = 595 // A4 en points
    private const val PAGE_H = 842
    private const val MARGIN = 56f
    private const val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()

    private const val INK = 0xFF2A1E23.toInt()
    private const val MUTED = 0xFF7A6A70.toInt()
    private const val ROSE = 0xFFA23B67.toInt()
    private const val VIOLET = 0xFF6B4FA0.toInt()
    private const val PAPER = 0xFFFFF8F9.toInt()
    private const val LINE = 0xFFEBD9DF.toInt()

    private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
    private val shortDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

    data class Entry(val day: DayEntity, val question: Question?)

    /** Souvenirs retenus : les questions révélées, de la plus ancienne à la plus récente. */
    fun revealed(entries: List<Entry>): List<Entry> =
        entries.filter { it.day.myAnswerPayload() != null && it.day.partnerAnswerPayload() != null }.sortedBy { it.day.day }

    /** Écrit le PDF et renvoie le nombre de souvenirs. */
    fun write(context: Context, out: OutputStream, prefs: AppPrefs, entries: List<Entry>, today: LocalDate): Int {
        val doc = PdfDocument()
        pages(context, prefs, entries, today).forEachIndexed { i, draw ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, i + 1).create())
            draw(page.canvas)
            doc.finishPage(page)
        }
        doc.writeTo(out)
        doc.close()
        return revealed(entries).size
    }

    /** Mise en page, une fonction de dessin par page (aussi utilisée pour les aperçus de test). */
    fun pages(context: Context, prefs: AppPrefs, entries: List<Entry>, today: LocalDate): List<(Canvas) -> Unit> {
        val serif = ResourcesCompat.getFont(context, R.font.fraunces) ?: Typeface.SERIF
        val me = prefs.myName.ifBlank { "Moi" }
        val partner = prefs.partnerDisplayName
        val nameOf: (String) -> String = { if (it == prefs.member.key) me else partner }
        val revealed = revealed(entries)
        val divider = Paint().apply { color = LINE; strokeWidth = 1f }

        val pages = mutableListOf<(Canvas) -> Unit>()
        pages += { c -> c.drawColor(PAPER); cover(c, serif, me, partner, revealed, today) }

        // Répartition des souvenirs : (position verticale, blocs) par page.
        var current = mutableListOf<Pair<Float, List<Pair<Float, StaticLayout>>>>()
        val laidOut = mutableListOf(current)
        var y = MARGIN + 6f
        for (e in revealed) {
            val block = entryLayouts(e, serif, me, partner, nameOf)
            val h = block.sumOf { it.second.height + it.first.toDouble() }.toFloat() + 28f
            if (y + h > PAGE_H - MARGIN - 20f && current.isNotEmpty()) {
                current = mutableListOf()
                laidOut += current
                y = MARGIN + 6f
            }
            current += y to block
            y += h
        }
        laidOut.forEachIndexed { i, blocks ->
            val number = i + 2
            pages += { c ->
                c.drawColor(PAPER)
                header(c, me, partner)
                for ((top, block) in blocks) {
                    var by = top
                    for ((gap, layout) in block) {
                        by += gap
                        c.withTranslation(MARGIN, by) { layout.draw(this) }
                        by += layout.height
                    }
                    by += 14f
                    c.drawLine(MARGIN, by, PAGE_W - MARGIN, by, divider)
                }
                if (revealed.isEmpty()) {
                    text("Vos premiers souvenirs s'écriront ici. 💜", 12f, MUTED, italic = true).let { c.withTranslation(MARGIN, MARGIN + 6f) { it.draw(this) } }
                }
                footer(c, number)
            }
        }
        return pages
    }

    private fun cover(canvas: Canvas, serif: Typeface, me: String, partner: String, revealed: List<Entry>, today: LocalDate) {
        val cx = PAGE_W / 2f
        // Deux bulles qui se reflètent, comme l'icône.
        val bubble = RectF(cx - 70f, 190f, cx + 10f, 270f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 190f, 0f, 350f, ROSE, VIOLET, Shader.TileMode.CLAMP) }
        canvas.drawRoundRect(bubble, 28f, 28f, paint)
        canvas.drawRoundRect(RectF(cx - 10f, 290f, cx + 70f, 370f), 28f, 28f, paint.apply { alpha = 120 })
        canvas.drawLine(MARGIN + 60f, 280f, PAGE_W - MARGIN - 60f, 280f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROSE; alpha = 90; strokeWidth = 1.5f })

        var y = 420f
        fun centered(layout: StaticLayout, gap: Float) {
            y += gap
            canvas.withTranslation(MARGIN, y) { layout.draw(this) }
            y += layout.height
        }
        centered(text("Miroir", 46f, ROSE, serif, center = true), 0f)
        centered(text("$me & $partner", 24f, INK, serif, center = true), 10f)
        val first = revealed.firstOrNull()?.day?.day?.let { LocalDate.parse(it) }
        val last = revealed.lastOrNull()?.day?.day?.let { LocalDate.parse(it) }
        val span = if (first != null && last != null) "du ${first.format(shortDate)} au ${last.format(shortDate)}" else "votre histoire commence"
        centered(text("${revealed.size} question${if (revealed.size > 1) "s" else ""} partagée${if (revealed.size > 1) "s" else ""}\n$span", 13f, MUTED, center = true), 28f)
        val best = bestStreakOf(revealed.map { it.day })
        if (best > 1) centered(text("Meilleure série : $best jours d'affilée", 12f, VIOLET, center = true), 10f)
        y = PAGE_H - MARGIN - 20f
        centered(text("Exporté le ${today.format(shortDate)}", 10f, MUTED, center = true), 0f)
    }

    private fun header(canvas: Canvas, me: String, partner: String) {
        text("Miroir · $me & $partner", 9f, MUTED).let { canvas.withTranslation(MARGIN, MARGIN - 24f) { it.draw(this) } }
    }

    private fun footer(canvas: Canvas, page: Int) {
        text("$page", 9f, MUTED, center = true).let { canvas.withTranslation(MARGIN, PAGE_H - MARGIN + 12f) { it.draw(this) } }
    }

    /** (espace avant, bloc de texte) pour un souvenir. */
    private fun entryLayouts(e: Entry, serif: Typeface, me: String, partner: String, nameOf: (String) -> String): List<Pair<Float, StaticLayout>> {
        val d = e.day
        val mine = d.myAnswerPayload()!!
        val theirs = d.partnerAnswerPayload()!!
        val date = LocalDate.parse(d.day).format(longDate).replaceFirstChar { it.titlecase(Locale.FRENCH) }
        val out = mutableListOf<Pair<Float, StaticLayout>>()
        out += 0f to text("$date · ${Category.of(d.category).label}" + if (d.favorite) "  ★" else "", 9.5f, ROSE, bold = true)
        out += 4f to text(d.questionText, 14.5f, INK, serif)
        out += 8f to answer(me, mine.display(e.question, nameOf), ROSE)
        out += 4f to answer(partner, theirs.display(e.question, nameOf), VIOLET)
        listOfNotNull(
            d.myReactionPayload()?.let { me to it },
            d.partnerReactionPayload()?.let { partner to it },
        ).forEach { (name, r) ->
            val words = listOfNotNull(r.emoji, r.comment?.takeIf { it.isNotBlank() }?.let { "« $it »" }).joinToString("  ")
            if (words.isNotBlank()) out += 4f to text("$name : $words", 10f, MUTED, italic = true)
        }
        return out
    }

    private fun answer(name: String, value: String, color: Int): StaticLayout {
        val s = SpannableStringBuilder("$name  ").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(value)
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f; this.color = INK }
        return StaticLayout.Builder.obtain(s, 0, s.length, paint, CONTENT_W).setLineSpacing(2f, 1f).build()
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        typeface: Typeface = Typeface.DEFAULT,
        bold: Boolean = false,
        italic: Boolean = false,
        center: Boolean = false,
    ): StaticLayout {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.typeface = Typeface.create(typeface, style)
        }
        return StaticLayout.Builder.obtain(value, 0, value.length, paint, CONTENT_W)
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .build()
    }
}
