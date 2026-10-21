package space.jot.android.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import space.jot.android.Fixtures

/** Mirrors VocabularyFileTests in StoneHub/jot so both apps read and write the same file. */
class VocabularyFileTest {
    @get:Rule val folder = TemporaryFolder()
    private val sample = listOf(VocabularyEntry("SwiftUI"), VocabularyEntry("C++", "see plus plus"), VocabularyEntry("Monroe", "mon row", enabled = false))

    private fun refusal(json: String) = assertThrows(ContractFileException::class.java) { VocabularyFile.decode(json.encodeToByteArray()) }.message

    @Test fun sharedSampleReadsAsItsListAndEncodesToTheSameBytes() {
        val bytes = Fixtures.bytes("contract/vocabulary-v1.json")
        assertEquals(sample, VocabularyFile.decode(bytes))
        assertEquals(bytes.decodeToString(), VocabularyFile.encode(sample).decodeToString())
        assertEquals(emptyList<VocabularyEntry>(), VocabularyFile.decode(VocabularyFile.encode(emptyList())))
    }

    @Test fun handWrittenFileMayLeaveOutHeardEnabledAndLaterFields() {
        val entries = VocabularyFile.decode("""{"format": "jot.vocabulary", "version": 1, "note": "from my phone", "entries": [{"preferred": "Jot", "source": "android"}]}""".encodeToByteArray())
        assertEquals(listOf(VocabularyEntry("Jot", "", true)), entries)
    }

    @Test fun refusesOtherFilesNewerVersionsAndBrokenEntries() {
        val notOurs = "That file is not a Jot vocabulary export."
        assertEquals(notOurs, refusal("[]"))
        assertEquals(notOurs, refusal("not json"))
        assertEquals(notOurs, refusal("""{"format": "jot.settings", "version": 1, "entries": []}"""))
        assertEquals(notOurs, refusal("""{"format": "jot.vocabulary", "entries": []}"""))
        assertEquals(notOurs, refusal("""{"format": "jot.vocabulary", "version": "1", "entries": []}"""))
        assertEquals("That file uses vocabulary version 2, which this Jot cannot read. Update Jot to import it.",
            refusal("""{"format": "jot.vocabulary", "version": 2, "entries": []}"""))
        val unreadable = "The entries in that file could not be read."
        assertEquals(unreadable, refusal("""{"format": "jot.vocabulary", "version": 1, "entries": [{"heard": "jot"}]}"""))
        assertEquals(unreadable, refusal("""{"format": "jot.vocabulary", "version": 1, "entries": [{"preferred": "Jot", "enabled": "yes"}]}"""))
        assertEquals(unreadable, refusal("""{"format": "jot.vocabulary", "version": 1, "entries": [{"preferred": "Jot", "heard": null}]}"""))
        assertEquals("That file is too large to be a vocabulary export.", refusal(" ".repeat(VocabularyFile.MAX_BYTES + 1)))
    }

    @Test fun importAddsOnlyNewPhrasesAndNeverChangesAnEntryTheListHas() {
        val vocabulary = PersonalVocabulary(listOf(VocabularyEntry("Monroe", "Mon  Row"), VocabularyEntry("SwiftUI")))
        val file = sample + listOf(VocabularyEntry("  Kotlin  ", " cot lin "), VocabularyEntry("Cotlin", "COT LIN"))
        val plan = VocabularyFile.additions(file, vocabulary)
        assertEquals(listOf(VocabularyEntry("C++", "see plus plus"), VocabularyEntry("Kotlin", "cot lin")), plan.added)
        assertEquals(3, plan.skipped)
    }

    @Test fun importChecksEveryEntryAndTheLimitBeforeAnythingIsSaved() {
        val blank = assertThrows(ContractFileException::class.java) { VocabularyFile.additions(listOf(VocabularyEntry("Jot"), VocabularyEntry(" ")), PersonalVocabulary()) }
        assertEquals("Entry 2: Enter a preferred spelling. Nothing was imported.", blank.message)
        assertThrows(ContractFileException::class.java) { VocabularyFile.additions(listOf(VocabularyEntry("Jot", "line\nbreak")), PersonalVocabulary()) }
        val full = PersonalVocabulary((0 until PersonalVocabulary.MAX_ENTRIES).map { VocabularyEntry("word$it") })
        assertEquals(1, VocabularyFile.additions(listOf(VocabularyEntry("word0")), full).skipped)
        assertThrows(ContractFileException::class.java) { VocabularyFile.additions(listOf(VocabularyEntry("new")), full) }
    }

    @Test fun fieldLimitCountsCharactersAsPeopleSeeThem() {
        val accented = "é".repeat(PersonalVocabulary.MAX_FIELD)
        assertEquals(1, VocabularyFile.additions(listOf(VocabularyEntry(accented)), PersonalVocabulary()).added.size)
        assertThrows(ContractFileException::class.java) { VocabularyFile.additions(listOf(VocabularyEntry("a".repeat(PersonalVocabulary.MAX_FIELD + 1))), PersonalVocabulary()) }
    }

    @Test fun storeImportsOnceAndKeepsTheListOnABadFile() {
        val store = VocabularyStore(folder.root.resolve("vocabulary.json"))
        val sampleBytes = Fixtures.bytes("contract/vocabulary-v1.json")
        assertEquals(3, store.import(sampleBytes).added.size)
        assertEquals(VocabularyFile.Import(emptyList(), 3), store.import(sampleBytes))
        assertThrows(ContractFileException::class.java) {
            store.import("""{"format": "jot.vocabulary", "version": 1, "entries": [{"preferred": "Jot"}, {"preferred": ""}]}""".encodeToByteArray())
        }
        assertEquals(sample, store.load().entries)
        assertFalse(folder.root.resolve("vocabulary.json.partial").exists())
    }
}
