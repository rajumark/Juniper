package raju.shingadiya.juniper.chat

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTest {

    @Test
    fun `headings, numbered and nested bullet items are parsed`() {
        val blocks = parseMarkdown(
            """
            ## Animals
            1. **Tiger**
               - **Status:** Endangered
            2. Leopard
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MarkdownBlock.Heading(level = 2, text = "Animals"),
                MarkdownBlock.ListItem(marker = "1.", depth = 0, text = "**Tiger**"),
                MarkdownBlock.ListItem(marker = "•", depth = 1, text = "**Status:** Endangered"),
                MarkdownBlock.ListItem(marker = "2.", depth = 0, text = "Leopard"),
            ),
            blocks,
        )
    }

    @Test
    fun `bold line is a paragraph, not a bullet`() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph("**Bold start**")),
            parseMarkdown("**Bold start**"),
        )
    }

    @Test
    fun `fenced code is kept verbatim`() {
        val blocks = parseMarkdown("```\nval x = 1\n  - not a list\n```")

        assertEquals(listOf(MarkdownBlock.Code("val x = 1\n  - not a list")), blocks)
    }

    @Test
    fun `unfinished code fence while streaming is kept as code`() {
        assertEquals(listOf(MarkdownBlock.Code("partial")), parseMarkdown("```\npartial"))
    }

    @Test
    fun `inline bold and italic and code get styles without the markers`() {
        val text = inlineMarkdown("a **bold** and *ital* and `code`")

        assertEquals("a bold and ital and code", text.text)
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold && text.text.substring(it.start, it.end) == "bold" })
        assertTrue(text.spanStyles.any { it.item.fontStyle == FontStyle.Italic && text.text.substring(it.start, it.end) == "ital" })
    }

    @Test
    fun `unmatched markers stay as plain text`() {
        assertEquals("price **5", inlineMarkdown("price **5").text)
    }
}
