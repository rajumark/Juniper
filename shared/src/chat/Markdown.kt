package raju.shingadiya.juniper.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import raju.shingadiya.juniper.ui.JuniperSpacing

/** One block of an assistant reply. Inline markup (bold, code) stays in the text until rendering. */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock

    /** A list entry. [depth] is the nesting level (0 = top level). [marker] is "•" or "1.". */
    data class ListItem(val marker: String, val depth: Int, val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
}

private val HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val ORDERED = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")

/**
 * Splits [source] into blocks. Supports headings, paragraphs, bullet and numbered lists (nested by
 * indentation) and fenced code. An unfinished code fence, as during streaming, is kept as code.
 */
fun parseMarkdown(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val code = StringBuilder()
    var inCode = false

    for (raw in source.lines()) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MarkdownBlock.Code(code.toString().trimEnd('\n'))
                code.clear()
            }
            inCode = !inCode
            continue
        }
        if (inCode) {
            code.append(raw).append('\n')
            continue
        }
        if (line.isBlank()) continue
        blocks += parseLine(line)
    }
    if (inCode) blocks += MarkdownBlock.Code(code.toString().trimEnd('\n'))
    return blocks
}

private fun parseLine(line: String): MarkdownBlock {
    HEADING.matchEntire(line)?.let { match ->
        return MarkdownBlock.Heading(level = match.groupValues[1].length, text = match.groupValues[2])
    }
    BULLET.matchEntire(line)?.let { match ->
        return MarkdownBlock.ListItem(marker = "•", depth = match.groupValues[1].length / 2, text = match.groupValues[2])
    }
    ORDERED.matchEntire(line)?.let { match ->
        return MarkdownBlock.ListItem(
            marker = "${match.groupValues[2]}.",
            depth = match.groupValues[1].length / 2,
            text = match.groupValues[3],
        )
    }
    return MarkdownBlock.Paragraph(line.trim())
}

/**
 * Turns inline markup into styled text: `**bold**`, `*italic*` (or `_italic_`) and `` `code` ``.
 * Unmatched markers are kept as plain characters.
 */
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val marker = when {
            text.startsWith("**", i) -> "**"
            text[i] == '`' -> "`"
            (text[i] == '*' || text[i] == '_') && i + 1 < text.length && !text[i + 1].isWhitespace() -> text[i].toString()
            else -> null
        }
        val end = if (marker != null) text.indexOf(marker, startIndex = i + marker.length) else -1
        if (marker != null && end > i + marker.length) {
            val style = when (marker) {
                "**" -> SpanStyle(fontWeight = FontWeight.Bold)
                "`" -> SpanStyle(fontFamily = FontFamily.Monospace)
                else -> SpanStyle(fontStyle = FontStyle.Italic)
            }
            withStyle(style) { append(text.substring(i + marker.length, end)) }
            i = end + marker.length
        } else {
            append(text[i])
            i++
        }
    }
}

/** Renders an assistant reply with headings, lists and code formatted through the app theme. */
@Composable
fun MarkdownContent(
    source: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(source) { parseMarkdown(source) }
    val typography = MaterialTheme.typography

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(JuniperSpacing.sm)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = inlineMarkdown(block.text),
                    style = when (block.level) {
                        1 -> typography.headlineSmall
                        2 -> typography.titleLarge
                        else -> typography.titleMedium
                    },
                    color = color,
                )
                is MarkdownBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text),
                    style = typography.bodyLarge,
                    color = color,
                )
                is MarkdownBlock.ListItem -> Row {
                    Text(
                        text = block.marker,
                        style = typography.bodyLarge,
                        color = color,
                        modifier = Modifier
                            .padding(start = JuniperSpacing.listIndent * block.depth.toFloat())
                            .width(JuniperSpacing.listMarkerWidth),
                    )
                    Text(
                        text = inlineMarkdown(block.text),
                        style = typography.bodyLarge,
                        color = color,
                    )
                }
                is MarkdownBlock.Code -> Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(JuniperSpacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = block.text,
                        style = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(JuniperSpacing.md),
                    )
                }
            }
        }
    }
}
