package space.jot.android.contract

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * The `jot.session` file `jot export <session-id> --file` writes on the Mac (docs/SHARED-CONTRACT.md in StoneHub/jot):
 * one session's spoken rows on the session's own clock, with no session or row ids.
 */
object SessionFile {
    const val FORMAT = "jot.session"
    const val VERSION = 1
    /** Not in the contract: a cap so a picked file cannot exhaust memory. Hours of meeting rows fit well below it. */
    const val MAX_BYTES = 20_000_000

    /** [start] and [end] are seconds from the session's start. [mode] is `ambient` or `dictation`, or a value a later Jot adds. */
    data class Row(val start: Double, val end: Double, val text: String, val mode: String, val speaker: String? = null, val speakerName: String? = null)

    data class Contents(val title: String?, val startedAt: Instant, val rows: List<Row>)

    /** Reads a file of this version or an earlier one, refusing a newer one rather than reading it in part. */
    fun decode(bytes: ByteArray): Contents {
        if (bytes.size > MAX_BYTES) throw ContractFileException("That file is too large to open here.")
        val root = ContractJson.open(bytes.decodeToString(), FORMAT, VERSION, "That file is not a Jot session file.") { version ->
            "That file uses session version $version, which this Jot cannot read. Update Jot to read it."
        }
        val unreadable = ContractFileException("The rows in that file could not be read.")
        fun optional(row: JsonObject, key: String) = row[key]?.takeIf { it !is JsonNull }?.let { ContractJson.string(it) ?: throw unreadable }
        val startedAt = root["startedAt"]?.let(ContractJson::string)?.let { text ->
            runCatching { OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
        } ?: throw unreadable
        val rows = (root["rows"] as? JsonArray ?: throw unreadable).map { element ->
            val row = element as? JsonObject ?: throw unreadable
            Row(
                start = row["start"]?.let(ContractJson::number) ?: throw unreadable,
                end = row["end"]?.let(ContractJson::number) ?: throw unreadable,
                text = row["text"]?.let(ContractJson::string) ?: throw unreadable,
                mode = row["mode"]?.let(ContractJson::string) ?: throw unreadable,
                speaker = optional(row, "speaker"),
                speakerName = optional(row, "speakerName"),
            )
        }
        return Contents(optional(root, "title"), startedAt, rows)
    }
}
