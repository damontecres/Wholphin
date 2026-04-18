package com.github.damontecres.wholphin.util

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertSame
import junit.framework.TestCase.assertTrue
import org.junit.Test

class TextUtilsKtTest {
    @Test
    fun `Empty string input`() {
        assertEquals("", "".stripMarkdown())
    }

    @Test
    fun `String without markdown characters`() {
        val plain = "Hello, world! This is plain text."
        val result = plain.stripMarkdown()
        assertSame(plain, result) // fast path must return the exact same reference
    }

    @Test
    fun `Headers level 1 to 6 removal`() {
        assertEquals("Heading", "# Heading".stripMarkdown())
        assertEquals("Heading", "## Heading".stripMarkdown())
        assertEquals("Heading", "### Heading".stripMarkdown())
        assertEquals("Heading", "#### Heading".stripMarkdown())
        assertEquals("Heading", "##### Heading".stripMarkdown())
        assertEquals("Heading", "###### Heading".stripMarkdown())
    }

    @Test
    fun `Triple emphasis Bold and Italic stripping`() {
        assertEquals("text", "***text***".stripMarkdown())
        assertEquals("text", "___text___".stripMarkdown())
    }

    @Test
    fun `Double emphasis Bold stripping`() {
        assertEquals("text", "**text**".stripMarkdown())
        assertEquals("text", "__text__".stripMarkdown())
    }

    @Test
    fun `Single emphasis Italic stripping`() {
        assertEquals("text", "*text*".stripMarkdown())
        assertEquals("text", "_text_".stripMarkdown())
    }

    @Test
    fun `Strikethrough stripping`() {
        assertEquals("text", "~~text~~".stripMarkdown())
    }

    @Test
    fun `Inline code stripping`() {
        assertEquals("code", "`code`".stripMarkdown())
        assertEquals("Use code here", "Use `code` here".stripMarkdown())
    }

    @Test
    fun `Multi line code block removal`() {
        val input =
            """
            Before
            ```
            fun foo() = 42
            ```
            After
            """.trimIndent()
        val result = input.stripMarkdown()
        assertFalse(result.contains("fun foo"))
        assertTrue(result.contains("Before"))
        assertTrue(result.contains("After"))
    }

    @Test
    fun `Markdown link conversion`() {
        assertEquals("Click here", "[Click here](https://example.com)".stripMarkdown())
        assertFalse("[text](https://example.com)".stripMarkdown().contains("https"))
    }

    @Test
    fun `Blockquote prefix removal`() {
        assertEquals("A quote", "> A quote".stripMarkdown())
        assertEquals("Line one\nLine two", "> Line one\n> Line two".stripMarkdown())
    }

    @Test
    fun `Unordered list marker removal`() {
        assertEquals("Item", "- Item".stripMarkdown())
        assertEquals("Item", "* Item".stripMarkdown())
        assertEquals("Item", "+ Item".stripMarkdown())
    }

    @Test
    fun `Ordered list marker removal`() {
        assertEquals("First", "1. First".stripMarkdown())
        assertEquals("Second", "2. Second".stripMarkdown())
        assertEquals("Tenth", "10. Tenth".stripMarkdown())
    }

    @Test
    fun `Horizontal rule removal`() {
        assertEquals("", "---".stripMarkdown())
        assertEquals("", "***".stripMarkdown())
        assertEquals("", "___".stripMarkdown())
        assertEquals("", "------".stripMarkdown())
    }

    @Test
    fun `HTML tag stripping`() {
        assertEquals("text", "<b>text</b>".stripMarkdown())
        assertEquals("Hello world", "Hello <br/> world".stripMarkdown())
    }

    @Test
    fun `Nested emphasis handling`() {
        // ***text*** should collapse to just "text"
        assertEquals("text", "***text***".stripMarkdown())
        // **bold *italic*** — inner markers stripped, text preserved
        val result = "**bold *italic***".stripMarkdown()
        assertTrue(result.contains("bold"))
        assertTrue(result.contains("italic"))
    }

    @Test
    fun `Escaped markdown character behavior`() {
        // Current impl does NOT handle escapes — document the actual behaviour
        val result = """\*not italic\*""".stripMarkdown()
        // Backslashes are left intact; no italic stripping occurs on escaped markers
        assertTrue(result.contains("not italic"))
    }

    @Test
    fun `Non matching markdown characters`() {
        // '#' mid-word must not be stripped (regex is anchored to line start)
        assertEquals("colour #FF0000", "colour #FF0000".stripMarkdown())
        // '>' inside a sentence must not be stripped
        assertEquals("a > b", "a > b".stripMarkdown())
    }

    @Test
    fun `Incomplete markdown syntax`() {
        // Unmatched markers — no crash, text is preserved as-is
        val result = "**bold without close".stripMarkdown()
        assertTrue(result.contains("bold without close"))

        val result2 = "[link(url)".stripMarkdown()
        assertTrue(result2.isNotEmpty())
    }

    @Test
    fun `Large text performance`() {
        val chunk = "# Title\n\n**bold** and *italic* with [link](url)\n\n"
        val large = chunk.repeat(10_000)
        val start = System.currentTimeMillis()
        val result = large.stripMarkdown()
        val elapsed = System.currentTimeMillis() - start
        assertTrue(result.isNotEmpty())
        assertTrue("Took ${elapsed}ms, expected < 2000ms", elapsed < 2000)
    }
}
