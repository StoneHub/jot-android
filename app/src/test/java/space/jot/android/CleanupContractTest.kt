package space.jot.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the shared corpus from StoneHub/jot docs/contract/ (see src/test/resources/contract/README.md). */
class CleanupContractTest {
    @Test fun sharedCorpusVersion1() {
        val corpus = Json.parseToJsonElement(Fixtures.text("contract/cleanup-checks-v1.json")).jsonObject
        assertEquals("jot.cleanup-checks", corpus.getValue("format").jsonPrimitive.content)
        assertEquals(1, corpus.getValue("version").jsonPrimitive.int)
        val cases = corpus.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty())
        val failures = cases.filter { case ->
            val source = case.getValue("source").jsonPrimitive.content
            val candidate = case.getValue("candidate").jsonPrimitive.content
            CleanupChecks.accepts(source, candidate) != case.getValue("accept").jsonPrimitive.boolean
        }.map { it.getValue("name").jsonPrimitive.content }
        assertEquals(emptyList<String>(), failures)
    }

    @Test fun controlTextIsRejected() {
        assertFalse(CleanupChecks.accepts("send it", "<think>ok</think> Send it."))
        assertFalse(CleanupChecks.accepts("send it", "Send it.<|im_end|>"))
    }

    @Test fun warningNamesTheRule() {
        assertNull(CleanupChecks.warning("um send 42 tomorrow", "Send 42 tomorrow."))
        assertEquals("Numbers, negations or hedges changed; keep the original.", CleanupChecks.warning("Do not send 42", "Send 42"))
        assertEquals("Output grew too long; keep the original.", CleanupChecks.warning("Yes.", "Yes. ".repeat(40)))
    }

    @Test fun protectedWordsFollowTheContractSplit() {
        assertEquals(listOf("don't", "2.5", "three"), CleanupChecks.protectedWords("I don’t want 2.5, three, three."))
        assertEquals(listOf("4.50"), CleanupChecks.protectedWords("It costs \$4.50."))
    }
}
