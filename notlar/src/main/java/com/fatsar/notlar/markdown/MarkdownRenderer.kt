package com.fatsar.notlar.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import com.fatsar.notlar.R

/** Markdown kaynağını, önizleme bölmesinde gösterilen biçimli metne çevirir. */
class MarkdownRenderer(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val accent = color(R.color.md_accent)
    private val muted = color(R.color.md_muted)
    private val codeBackground = color(R.color.md_code_background)
    private val highlight = color(R.color.md_highlight)
    private val rule = color(R.color.md_rule)

    fun render(source: String): CharSequence = render(MarkdownParser.parse(source))

    fun render(blocks: List<MdBlock>): CharSequence {
        val out = SpannableStringBuilder()
        for ((index, block) in blocks.withIndex()) {
            if (index > 0) out.append("\n\n")
            when (block) {
                is MdBlock.Heading -> heading(out, block)
                is MdBlock.Paragraph -> inline(out, block.text)
                is MdBlock.Bullet -> bullet(out, block)
                is MdBlock.Numbered -> numbered(out, block)
                is MdBlock.Task -> task(out, block)
                is MdBlock.Quote -> quote(out, block)
                is MdBlock.Code -> code(out, block)
                MdBlock.Rule -> horizontalRule(out)
            }
        }
        return out
    }

    private fun heading(out: SpannableStringBuilder, block: MdBlock.Heading) {
        val start = out.length
        inline(out, block.text)
        val scale = when (block.level) {
            1 -> 1.55f
            2 -> 1.32f
            3 -> 1.16f
            else -> 1.04f
        }
        out.setSpan(RelativeSizeSpan(scale), start, out.length, SPAN)
        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, SPAN)
        if (block.level <= 2) out.setSpan(ForegroundColorSpan(accent), start, out.length, SPAN)
    }

    private fun bullet(out: SpannableStringBuilder, block: MdBlock.Bullet) {
        val start = out.length
        inline(out, block.text)
        val indent = dp(16f * (block.indent + 1))
        out.setSpan(BulletSpan(dp(8f), accent), start, out.length, SPAN)
        out.setSpan(LeadingMarginSpan.Standard(indent), start, out.length, SPAN)
    }

    private fun numbered(out: SpannableStringBuilder, block: MdBlock.Numbered) {
        val start = out.length
        val marker = "${block.number}. "
        out.append(marker)
        out.setSpan(ForegroundColorSpan(accent), start, out.length, SPAN)
        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, SPAN)
        inline(out, block.text)
        val indent = dp(16f * (block.indent + 1))
        out.setSpan(LeadingMarginSpan.Standard(indent, indent + dp(16f)), start, out.length, SPAN)
    }

    private fun task(out: SpannableStringBuilder, block: MdBlock.Task) {
        val start = out.length
        out.append(if (block.done) "☑  " else "☐  ")
        out.setSpan(ForegroundColorSpan(if (block.done) muted else accent), start, out.length, SPAN)
        val textStart = out.length
        inline(out, block.text)
        if (block.done) {
            out.setSpan(StrikethroughSpan(), textStart, out.length, SPAN)
            out.setSpan(ForegroundColorSpan(muted), textStart, out.length, SPAN)
        }
        val indent = dp(16f * (block.indent + 1))
        out.setSpan(LeadingMarginSpan.Standard(indent, indent + dp(20f)), start, out.length, SPAN)
    }

    private fun quote(out: SpannableStringBuilder, block: MdBlock.Quote) {
        val start = out.length
        inline(out, block.text)
        val span = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            QuoteSpan(accent, dp(3f), dp(10f))
        } else {
            QuoteSpan(accent)
        }
        out.setSpan(span, start, out.length, SPAN)
        out.setSpan(ForegroundColorSpan(muted), start, out.length, SPAN)
        out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, SPAN)
    }

    private fun code(out: SpannableStringBuilder, block: MdBlock.Code) {
        val start = out.length
        out.append(block.code.ifEmpty { " " })
        out.setSpan(TypefaceSpan("monospace"), start, out.length, SPAN)
        out.setSpan(RelativeSizeSpan(0.92f), start, out.length, SPAN)
        out.setSpan(BackgroundColorSpan(codeBackground), start, out.length, SPAN)
        out.setSpan(LeadingMarginSpan.Standard(dp(12f)), start, out.length, SPAN)
    }

    private fun horizontalRule(out: SpannableStringBuilder) {
        val start = out.length
        out.append(" ")
        out.setSpan(RuleSpan(rule, dp(1f).coerceAtLeast(1)), start, out.length, SPAN)
    }

    private fun inline(out: SpannableStringBuilder, text: MdText) {
        val offset = out.length
        out.append(text.text)
        for (span in text.spans) {
            val start = offset + span.start
            val end = offset + span.end
            if (start >= end || end > out.length) continue
            when (span.style) {
                MdStyle.BOLD -> out.setSpan(StyleSpan(Typeface.BOLD), start, end, SPAN)
                MdStyle.ITALIC -> out.setSpan(StyleSpan(Typeface.ITALIC), start, end, SPAN)
                MdStyle.STRIKE -> out.setSpan(StrikethroughSpan(), start, end, SPAN)
                MdStyle.HIGHLIGHT -> out.setSpan(BackgroundColorSpan(highlight), start, end, SPAN)
                MdStyle.CODE -> {
                    out.setSpan(TypefaceSpan("monospace"), start, end, SPAN)
                    out.setSpan(BackgroundColorSpan(codeBackground), start, end, SPAN)
                }
                MdStyle.LINK -> {
                    val href = span.href
                    if (!href.isNullOrBlank()) out.setSpan(URLSpan(href), start, end, SPAN)
                    out.setSpan(ForegroundColorSpan(accent), start, end, SPAN)
                }
            }
        }
    }

    private fun dp(value: Float): Int = (value * density + 0.5f).toInt()

    private fun color(id: Int): Int = androidx.core.content.ContextCompat.getColor(context, id)

    /** Yatay çizgi (`---`) için tek satırlık ayraç. */
    private class RuleSpan(private val color: Int, private val thickness: Int) : LineBackgroundSpan {
        override fun drawBackground(
            canvas: Canvas,
            paint: Paint,
            left: Int,
            right: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence,
            start: Int,
            end: Int,
            lineNumber: Int
        ) {
            val original = paint.color
            paint.color = color
            val y = (top + bottom) / 2f
            canvas.drawRect(left.toFloat(), y, right.toFloat(), y + thickness, paint)
            paint.color = original
        }
    }

    private companion object {
        const val SPAN = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    }
}
