package space.jot.android.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Mirrors PersonalVocabularyTests in StoneHub/jot: the matching rules in docs/SHARED-CONTRACT.md. */
class PersonalVocabularyTest {
    @Test fun wholeWordsUnicodePunctuationAndCapitalization() {
        val vocabulary = PersonalVocabulary(listOf(VocabularyEntry("Jot"), VocabularyEntry("Monroe", "mon row")))
        assertEquals("👋 Jot, Monroe! jotting jot_2 éjot joté Jot's.", vocabulary.apply("👋 JOT, mon  row! jotting jot_2 éjot joté jot's."))
        vocabulary.add(VocabularyEntry("C++", "see plus plus"))
        assertEquals("Use C++.", vocabulary.apply("Use see plus plus."))
    }

    @Test fun longestPhraseWinsWithoutCascadingOrTemplateExpansion() {
        val vocabulary = PersonalVocabulary(listOf(VocabularyEntry("swift", "quick"), VocabularyEntry("SwiftUI", "swift you eye"),
            VocabularyEntry("replacement", "swift"), VocabularyEntry("$1\\folder", "dollar")))
        assertEquals("swift SwiftUI $1\\folder", vocabulary.apply("quick swift you eye dollar"))
        vocabulary.add(VocabularyEntry("dot", "a.b"))
        assertEquals("dot axb", vocabulary.apply("a.b axb"))
    }

    @Test fun disabledEntriesAreKeptButNotApplied() {
        val vocabulary = PersonalVocabulary(listOf(VocabularyEntry("Monroe", "mon row", enabled = false)))
        assertEquals("mon row", vocabulary.apply("mon row"))
        assertEquals(1, vocabulary.entries.size)
    }

    @Test fun sameMatchingPhraseIsRefusedIgnoringCaseAndSpacing() {
        val vocabulary = PersonalVocabulary(listOf(VocabularyEntry("Monroe", "mon row")))
        assertThrows(ContractFileException::class.java) { vocabulary.add(VocabularyEntry("Munro", " MON  ROW ")) }
        assertThrows(ContractFileException::class.java) { vocabulary.add(VocabularyEntry("mon row")) }
    }
}
