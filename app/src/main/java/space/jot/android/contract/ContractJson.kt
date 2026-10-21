package space.jot.android.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** A shared file this app cannot read; the message is written for the person who chose the file. */
class ContractFileException(message: String) : Exception(message)

/**
 * Reading helpers for the files Jot for Mac and Jot for Android share (docs/SHARED-CONTRACT.md in StoneHub/jot).
 * Every file names its `format` and an integer `version`; a reader refuses another format and any version newer
 * than it reads, and ignores fields it does not know.
 */
internal object ContractJson {
    /** The file's top-level object when it is [format] at a version from 1 to [current]. */
    fun open(text: String, format: String, current: Int, notThisFormat: String, newer: (Int) -> String): JsonObject {
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: throw ContractFileException(notThisFormat)
        val version = root["version"]?.let(::integer)
        if (root["format"]?.let(::string) != format || version == null || version < 1) throw ContractFileException(notThisFormat)
        if (version > current) throw ContractFileException(newer(version))
        return root
    }

    fun string(element: JsonElement): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun integer(element: JsonElement): Int? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    fun number(element: JsonElement): Double? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    fun boolean(element: JsonElement): Boolean? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}
