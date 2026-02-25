package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale

object SearchRelevance {
    private val REGEX_PATTERN = Regex("^/(.+)/(i)?$")

    fun score(
        item: BaseItem,
        query: String,
    ): Int {
        val name = item.name?.lowercase(Locale.getDefault()) ?: return Int.MAX_VALUE
        val q = query.lowercase(Locale.getDefault())

        REGEX_PATTERN.find(query)?.let { match ->
            val pattern = match.groupValues[1]
            val caseInsensitive = match.groupValues[2] == "i"
            return regexScore(item.name ?: "", pattern, caseInsensitive) + typeBonus(item.type)
        }

        return when {
            name == q -> 0
            name.startsWith(q) -> 100
            name.contains(" $q") -> 200
            name.contains(q) -> 300
            else -> fuzzyScore(name, q)
        } + typeBonus(item.type)
    }

    private fun regexScore(
        name: String,
        pattern: String,
        caseInsensitive: Boolean,
    ): Int =
        try {
            val options = if (caseInsensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
            if (Regex(pattern, options).containsMatchIn(name)) 250 else 400
        } catch (e: Exception) {
            400
        }

    private fun fuzzyScore(
        name: String,
        query: String,
    ): Int {
        val distance = levenshteinDistance(name, query)
        val maxLen = maxOf(name.length, query.length)
        val similarity = 1.0 - (distance.toDouble() / maxLen)

        return when {
            similarity >= 0.8 -> 350
            similarity >= 0.6 -> 375
            else -> 400
        }
    }

    private fun levenshteinDistance(
        s1: String,
        s2: String,
    ): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] =
                    if (s1[i - 1] == s2[j - 1]) {
                        dp[i - 1][j - 1]
                    } else {
                        1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                    }
            }
        }
        return dp[m][n]
    }

    private fun typeBonus(type: BaseItemKind): Int =
        when (type) {
            BaseItemKind.SERIES -> 0
            BaseItemKind.MOVIE -> 1
            BaseItemKind.BOX_SET -> 2
            BaseItemKind.EPISODE -> 10
            else -> 5
        }
}
