package space.jot.android.contract

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The `jot.vocabulary` file (docs/SHARED-CONTRACT.md in StoneHub/jot). It carries no ids; entries match by phrase. */
object VocabularyFile {
    const val FORMAT = "jot.vocabulary"
    const val VERSION = 1
    const val MAX_BYTES = 2_000_000

    data class Import(val added: List<VocabularyEntry>, val skipped: Int)

    /** Reads a file of this version or an earlier one, refusing a newer one rather than reading it in part. */
    fun decode(bytes: ByteArray): List<VocabularyEntry> {
        if (bytes.size > MAX_BYTES) throw ContractFileException("That file is too large to be a vocabulary export.")
        val root = ContractJson.open(bytes.decodeToString(), FORMAT, VERSION, "That file is not a Jot vocabulary export.") { version ->
            "That file uses vocabulary version $version, which this Jot cannot read. Update Jot to import it."
        }
        val unreadable = ContractFileException("The entries in that file could not be read.")
        val entries = root["entries"] as? JsonArray ?: throw unreadable
        return entries.map { element ->
            val entry = element as? JsonObject ?: throw unreadable
            VocabularyEntry(
                preferred = entry["preferred"]?.let(ContractJson::string) ?: throw unreadable,
                heard = entry["heard"]?.let { ContractJson.string(it) ?: throw unreadable } ?: "",
                enabled = entry["enabled"]?.let { ContractJson.boolean(it) ?: throw unreadable } ?: true,
            )
        }
    }

    /** Every field, keys sorted, two-space indents and a trailing newline, as the Mac writes it. */
    fun encode(entries: List<VocabularyEntry>): ByteArray {
        val lines = entries.joinToString(",\n") { entry ->
            val fields = buildJsonObject { put("enabled", entry.enabled); put("heard", entry.heard); put("preferred", entry.preferred) }
            "    {\n" + fields.entries.joinToString(",\n") { (key, value) -> "      \"$key\" : $value" } + "\n    }"
        }
        val list = if (entries.isEmpty()) "[]" else "[\n$lines\n  ]"
        return "{\n  \"entries\" : $list,\n  \"format\" : ${JsonPrimitive(FORMAT)},\n  \"version\" : $VERSION\n}\n".encodeToByteArray()
    }

    /**
     * What importing [file] into [vocabulary] adds, in file order. An entry whose phrase the list already has, or that
     * an earlier entry in the file added, is skipped, so an import never changes or removes an entry. Every entry and
     * the entry limit are checked first, so a bad file throws before anything is saved.
     */
    fun additions(file: List<VocabularyEntry>, vocabulary: PersonalVocabulary): Import {
        val trial = PersonalVocabulary(vocabulary.entries)
        val added = mutableListOf<VocabularyEntry>()
        var skipped = 0
        file.forEachIndexed { index, entry ->
            val phrase = entry.heard.trim().ifEmpty { entry.preferred.trim() }
            if (PersonalVocabulary.matchKey(phrase).isNotEmpty() && trial.has(phrase)) { skipped++; return@forEachIndexed }
            if (trial.entries.size >= PersonalVocabulary.MAX_ENTRIES) {
                throw ContractFileException("Importing would pass ${PersonalVocabulary.MAX_ENTRIES} vocabulary entries. Nothing was imported.")
            }
            try { added += trial.add(entry) } catch (e: ContractFileException) {
                throw ContractFileException("Entry ${index + 1}: ${e.message} Nothing was imported.")
            }
        }
        return Import(added, skipped)
    }
}
