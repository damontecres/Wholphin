package com.github.damontecres.wholphin.util

private val MARKDOWN_CHARS = charArrayOf(
    '#', '*', '_', '~', '`', '[', '!', '>', '<', '-', '+', '.', '(', ')',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
)

/**
 * Strips common Markdown and HTML tags from a string for plain-text display.
 */
fun String.stripMarkdown(): String {
    // Early return if no markdown-like characters are present and no trimming is needed.
    if (this.none { it in MARKDOWN_CHARS } && this.trim() == this) return this

    return this
        // Code blocks (``` ... ```) - Remove first to avoid matching contents
        .replace(Regex("(?s)```.*?```"), "")
        // Horizontal rules (--- or ***) - MUST be before bold/italic
        .replace(Regex("(?m)^([-*_]){3,}\\s*$"), "")
        // Headers (# Heading)
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        // Bold + italic (***text*** or ___text___)
        .replace(Regex("\\*{3}(.+?)\\*{3}"), "$1")
        .replace(Regex("_{3}(.+?)_{3}"), "$1")
        // Bold (**text** or __text__)
        .replace(Regex("\\*{2}(.+?)\\*{2}"), "$1")
        .replace(Regex("_{2}(.+?)_{2}"), "$1")
        // Italic (*text* or _text_)
        .replace(Regex("\\*(.+?)\\*"), "$1")
        .replace(Regex("_(.+?)_"), "$1")
        // Strikethrough (~~text~~)
        .replace(Regex("~~(.+?)~~"), "$1")
        // Inline code (`code`)
        .replace(Regex("`(.+?)`"), "$1")
        // Images (![alt](url))
        .replace(Regex("!\\[.*?]\\(.*?\\)"), "")
        // Links ([text](url)) → keep text
        .replace(Regex("\\[(.+?)]\\(.*?\\)"), "$1")
        // Blockquotes (> text)
        .replace(Regex("(?m)^>\\s+"), "")
        // Unordered lists (- item, * item, + item)
        .replace(Regex("(?m)^[\\-*+]\\s+"), "")
        // Ordered lists (1. item)
        .replace(Regex("(?m)^\\d+\\.\\s+"), "")
        // HTML tags
        .replace(Regex("<[^>]+>"), "")
        // Clean up extra blank lines (more than 2)
        .replace(Regex("(\\r?\\n){3,}"), "\n\n")
        // Collapse multiple spaces to a single space
        .replace(Regex(" {2,}"), " ")
        .trim()
}
