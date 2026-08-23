package com.github.damontecres.wholphin.util.media

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

/** Returns whether this is an HEVC-based Dolby Vision decoder input format. */
internal fun Format.isHevcDolbyVision(): Boolean =
    sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION &&
        codecs?.containsHevcDolbyVisionCodecToken() == true

/** Finds a complete dvhe/dvh1 token without allocating split or normalized strings. */
private fun String.containsHevcDolbyVisionCodecToken(): Boolean {
    var tokenStart = 0
    while (tokenStart <= length) {
        val nextComma = indexOf(',', startIndex = tokenStart)
        var tokenEnd = if (nextComma < 0) length else nextComma

        while (tokenStart < tokenEnd && this[tokenStart].isWhitespace()) tokenStart++
        while (tokenEnd > tokenStart && this[tokenEnd - 1].isWhitespace()) tokenEnd--

        if (
            isCodecTokenWithPrefix(tokenStart, tokenEnd, "dvhe") ||
            isCodecTokenWithPrefix(tokenStart, tokenEnd, "dvh1")
        ) {
            return true
        }

        if (nextComma < 0) return false
        tokenStart = nextComma + 1
    }
    return false
}

private fun String.isCodecTokenWithPrefix(
    start: Int,
    end: Int,
    prefix: String,
): Boolean {
    if (
        end - start < prefix.length ||
        !regionMatches(start, prefix, 0, prefix.length, ignoreCase = true)
    ) {
        return false
    }
    if (end - start == prefix.length) return true
    if (this[start + prefix.length] != '.' || end - start == prefix.length + 1) return false

    // Reject empty codec components and characters that cannot occur in an RFC 6381 token.
    var componentHasCharacters = false
    for (index in start + prefix.length + 1 until end) {
        val character = this[index]
        if (character == '.') {
            if (!componentHasCharacters) return false
            componentHasCharacters = false
        } else {
            val isAsciiLetterOrDigit =
                character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9'
            if (!isAsciiLetterOrDigit) return false
            componentHasCharacters = true
        }
    }
    return componentHasCharacters
}
