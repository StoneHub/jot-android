package space.jot.android

/**
 * Decides whether a cleanup result is safe to use. Rules 1 to 3 are the shared
 * Mac/Android contract (`jot.cleanup-checks` version 1, docs/SHARED-CONTRACT.md in
 * StoneHub/jot); the control text check is Android's own addition.
 * This is a conservative fallback that rejects known dangerous edits, not a proof
 * that the meaning is unchanged.
 */
object CleanupChecks {
    /** Returns why [candidate] should not replace [source], or null when it may. */
    fun warning(source: String, candidate: String): String? {
        val output = candidate.trim()
        if (output.isEmpty()) return "Empty output; keep the original."
        if (output.contains("<think>") || output.contains("<|")) return "Model returned control text; keep the original."
        if (output.utf8Size() > maxOf(120, source.utf8Size() * 2)) return "Output grew too long; keep the original."
        if (protectedWords(source) != protectedWords(output)) return "Numbers, negations or hedges changed; keep the original."
        return null
    }

    fun accepts(source: String, candidate: String) = warning(source, candidate) == null

    private val numbers = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen " +
        "fifteen sixteen seventeen eighteen nineteen twenty thirty forty fifty sixty seventy eighty ninety hundred " +
        "thousand million billion trillion first second third half quarter percent point").split(" ").toSet()
    private val qualifiers = ("no not never cannot can't don't doesn't didn't won't wouldn't shouldn't isn't aren't " +
        "wasn't weren't haven't hasn't hadn't maybe probably possibly might unless").split(" ").toSet()
    private val separators = Regex("[^\\p{L}\\p{M}\\p{N}'.,]+")
    private val digit = Regex("\\p{N}")

    /** Number words, digit pieces, negations and hedges in order, with immediate repeats counted once. */
    internal fun protectedWords(text: String): List<String> {
        val result = mutableListOf<String>()
        text.lowercase().replace('’', '\'').split(separators).map { it.trim('.', ',') }.forEach { word ->
            if (word.isEmpty()) return@forEach
            if ((word in numbers || word in qualifiers || digit.containsMatchIn(word)) && result.lastOrNull() != word) result += word
        }
        return result
    }

    private fun String.utf8Size() = toByteArray(Charsets.UTF_8).size
}
