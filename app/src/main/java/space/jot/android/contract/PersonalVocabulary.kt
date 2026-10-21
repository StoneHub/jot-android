package space.jot.android.contract

import java.text.BreakIterator

/** One personal vocabulary entry. An empty [heard] only fixes the capitalization of [preferred]. */
data class VocabularyEntry(val preferred: String, val heard: String = "", val enabled: Boolean = true) {
    val matchPhrase: String get() = heard.ifEmpty { preferred }
}

/**
 * The entry rules and matching from docs/SHARED-CONTRACT.md (StoneHub/jot), kept the same as the Mac's
 * PersonalVocabulary so a list behaves alike on both. Entries are applied to text an app inserts, not to listening rows.
 */
class PersonalVocabulary(entries: List<VocabularyEntry> = emptyList()) {
    var entries: List<VocabularyEntry> = emptyList()
        private set

    init { entries.forEach(::add) }

    /** Adds a trimmed copy of [draft], or throws with the reason it breaks an entry rule. */
    fun add(draft: VocabularyEntry): VocabularyEntry {
        val entry = VocabularyEntry(draft.preferred.trim(), draft.heard.trim(), draft.enabled)
        if (entry.preferred.isEmpty()) throw ContractFileException("Enter a preferred spelling.")
        if (listOf(entry.preferred, entry.heard).any { characters(it) > MAX_FIELD || it.any(::isLineBreak) }) {
            throw ContractFileException("Use a single word or phrase, up to $MAX_FIELD characters per field.")
        }
        if (has(entry.matchPhrase)) throw ContractFileException("That matching phrase already has an entry. Edit the existing entry instead.")
        if (entries.size >= MAX_ENTRIES) throw ContractFileException("Vocabulary holds at most $MAX_ENTRIES entries.")
        entries = entries + entry
        return entry
    }

    fun has(phrase: String) = matchKey(phrase).let { key -> entries.any { matchKey(it.matchPhrase) == key } }

    /** Matches the original text once: leftmost first, longest at the same place, then list order. Replacements are literal. */
    fun apply(text: String): String {
        data class Hit(val start: Int, val end: Int, val replacement: String, val order: Int)
        val hits = entries.withIndex().filter { it.value.enabled }.flatMap { (order, entry) ->
            val regex = pattern(entry.matchPhrase) ?: return@flatMap emptyList()
            regex.findAll(text).map { Hit(it.range.first, it.range.last + 1, entry.preferred, order) }.toList()
        }.sortedWith(compareBy<Hit> { it.start }.thenByDescending { it.end - it.start }.thenBy { it.order })
        val result = StringBuilder()
        var cursor = 0
        for (hit in hits) {
            if (hit.start < cursor) continue
            result.append(text, cursor, hit.start).append(hit.replacement)
            cursor = hit.end
        }
        return result.append(text, cursor, text.length).toString()
    }

    companion object {
        const val MAX_ENTRIES = 1000
        const val MAX_FIELD = 200

        // Swift's whitespace and newline sets, spelled out so the JVM and Android regex engines agree.
        private const val SPACE = "[\\p{Z}\\t\\n\\u000B\\f\\r\\u0085]+"
        private const val WORD = "\\p{L}\\p{M}\\p{N}_"

        /** How two phrases compare: case and runs of whitespace do not count. */
        fun matchKey(value: String) = value.split(Regex(SPACE)).filter { it.isNotEmpty() }.joinToString(" ").lowercase()

        private fun pattern(phrase: String): Regex? {
            val words = phrase.split(Regex(SPACE)).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            return Regex("(?<![$WORD])(?:${words.joinToString(SPACE) { Regex.escape(it) }})(?![$WORD])", RegexOption.IGNORE_CASE)
        }

        private fun isLineBreak(c: Char) = c in '\n'..'\r' || c == '\u0085' || c == ' ' || c == ' '

        /** Characters as a person counts them (grapheme clusters), as Swift's String.count does. */
        private fun characters(text: String): Int {
            val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
            var count = 0
            while (iterator.next() != BreakIterator.DONE) count++
            return count
        }
    }
}
