package space.jot.android.contract

import java.io.File

/** This phone's vocabulary list, kept as a `jot.vocabulary` file so the stored form and the shared form are the same. */
class VocabularyStore(private val file: File) {
    fun load(): PersonalVocabulary =
        if (file.isFile) PersonalVocabulary(VocabularyFile.decode(file.readBytes())) else PersonalVocabulary()

    /** Adds the new entries from an exported file and saves the list; a file that breaks a rule changes nothing. */
    fun import(bytes: ByteArray): VocabularyFile.Import {
        val vocabulary = load()
        val result = VocabularyFile.additions(VocabularyFile.decode(bytes), vocabulary)
        if (result.added.isNotEmpty()) save(vocabulary.entries + result.added)
        return result
    }

    private fun save(entries: List<VocabularyEntry>) {
        file.parentFile?.mkdirs()
        val partial = File(file.path + ".partial")
        partial.writeBytes(VocabularyFile.encode(entries))
        check(partial.renameTo(file)) { "Could not save the vocabulary list" }
    }
}
