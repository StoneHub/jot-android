package com.example.jotmodellab

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelLogicTest {
    @Test fun wordScoreCountsSubstitutionAndInsertion() {
        assertEquals("Word error rate: 0.0%", WordScore.errorRate("Blue folder.", "blue folder"))
        assertEquals("Word error rate: 50.0%", WordScore.errorRate("blue folder", "red folder"))
        assertEquals("Word error rate: 50.0%", WordScore.errorRate("blue folder", "the blue folder"))
    }
    @Test fun cleanupFlagsNumberAndNegationChanges() {
        assertNotNull(CleanupChecks.warning("Do not send 42", "Send 42"))
        assertNotNull(CleanupChecks.warning("Send 42", "Send 24"))
        assertNull(CleanupChecks.warning("um send 42 tomorrow", "Send 42 tomorrow."))
    }
    @Test fun melSilenceAndToneHaveWhisperShape() {
        val silence = MelFeatures.extract(FloatArray(16000))
        assertEquals(240000, silence.size)
        assertTrue(silence.all { it == -1.5f })
        val tone = MelFeatures.extract(FloatArray(16000) { kotlin.math.sin(it * 2 * Math.PI * 440 / 16000).toFloat() * 0.1f })
        assertTrue(tone.all { it.isFinite() })
        assertTrue(tone.maxOrNull()!! > silence.maxOrNull()!!)
    }
    @Test fun waveDecoderRejectsUnexpectedRateAndTruncation() {
        val wave = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(40); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(4); putShort(16384); putShort(-16384)
        }.array()
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), PcmWave.decode(wave), 0f)
        assertThrows(IllegalArgumentException::class.java) { PcmWave.decode(wave.copyOf(45)) }
        ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN).putInt(24, 48000)
        assertThrows(IllegalArgumentException::class.java) { PcmWave.decode(wave) }
    }
}
