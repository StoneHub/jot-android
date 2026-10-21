package space.jot.android.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import space.jot.android.Fixtures
import java.time.Instant

/** Mirrors SessionFileTests in StoneHub/jot: the shared sample reads as the session it was made from. */
class SessionFileTest {
    private fun refusal(json: String) = assertThrows(ContractFileException::class.java) { SessionFile.decode(json.encodeToByteArray()) }.message

    @Test fun sharedSampleReadsAsTheSessionItWasMadeFrom() {
        val contents = SessionFile.decode(Fixtures.bytes("contract/session-v1.json"))
        assertEquals("Design review", contents.title)
        assertEquals(Instant.parse("2027-01-15T08:00:00Z"), contents.startedAt)
        assertEquals(listOf(
            SessionFile.Row(0.9, 2.4, "Let's start with the launch date.", "ambient", "speaker-1", "Ana"),
            SessionFile.Row(5.4, 6.4, "Sounds good.", "ambient", "speaker-2"),
            SessionFile.Row(10.4, 11.6, "Send the notes to the team.", "dictation"),
        ), contents.rows)
    }

    @Test fun optionalFieldsMayBeAbsentOrNullAndUnknownOnesAreIgnored() {
        val contents = SessionFile.decode("""{"format": "jot.session", "version": 1, "title": null, "device": "phone", "startedAt": "2027-01-15T09:00:00+01:00",
            "rows": [{"start": 0, "end": 1, "text": "Hi.", "mode": "note", "speaker": null, "confidence": 0.9}]}""".encodeToByteArray())
        assertNull(contents.title)
        assertEquals(Instant.parse("2027-01-15T08:00:00Z"), contents.startedAt)
        assertEquals(listOf(SessionFile.Row(0.0, 1.0, "Hi.", "note")), contents.rows)
    }

    @Test fun refusesOtherFilesNewerVersionsAndBrokenRows() {
        assertEquals("That file is not a Jot session file.", refusal("[]"))
        assertEquals("That file is not a Jot session file.", refusal("""{"format": "jot.vocabulary", "version": 1, "entries": []}"""))
        assertEquals("That file uses session version 2, which this Jot cannot read. Update Jot to read it.",
            refusal("""{"format": "jot.session", "version": 2, "startedAt": "2027-01-15T08:00:00Z", "rows": []}"""))
        val unreadable = "The rows in that file could not be read."
        assertEquals(unreadable, refusal("""{"format": "jot.session", "version": 1, "startedAt": "2027-01-15T08:00:00Z", "rows": [{"start": 0}]}"""))
        assertEquals(unreadable, refusal("""{"format": "jot.session", "version": 1, "startedAt": "yesterday", "rows": []}"""))
        assertEquals(unreadable, refusal("""{"format": "jot.session", "version": 1, "rows": []}"""))
        assertEquals(unreadable, refusal("""{"format": "jot.session", "version": 1, "startedAt": "2027-01-15T08:00:00Z", "rows": [{"start": "0", "end": 1, "text": "Hi.", "mode": "ambient"}]}"""))
    }
}
