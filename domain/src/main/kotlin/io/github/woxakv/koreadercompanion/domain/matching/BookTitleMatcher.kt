package io.github.woxakv.koreadercompanion.domain.matching

import java.text.Normalizer

/**
 * Fail-closed title matching between an epub's own title and a statistics-DB
 * title: only an exact match after normalization pairs the two, so a near
 * miss shows stats without a cover rather than risking the wrong pairing.
 */
object BookTitleMatcher {

    private val leadingArticles = listOf("the ", "a ", "le ", "la ", "les ")
    private val diacriticals = Regex("\\p{Mn}+")
    private val elidedArticle = Regex("^l['’]")
    private val nonWordChars = Regex("[^\\p{L}\\p{Nd}\\s]")
    private val extraWhitespace = Regex("\\s+")

    fun matches(epubTitle: String?, statsTitle: String): Boolean {
        if (epubTitle == null) return false
        val normalizedEpub = normalize(epubTitle)
        if (normalizedEpub.isBlank()) return false
        return normalizedEpub == normalize(statsTitle)
    }

    fun normalize(title: String): String {
        var result = Normalizer.normalize(title.trim(), Normalizer.Form.NFKD)
            .replace(diacriticals, "")
            .lowercase()
        result = result.replace(elidedArticle, "")
        result = result.replace(nonWordChars, " ")
        result = result.trim().replace(extraWhitespace, " ")
        for (article in leadingArticles) {
            if (result.startsWith(article)) {
                result = result.removePrefix(article)
                break
            }
        }
        return result.trim()
    }
}
