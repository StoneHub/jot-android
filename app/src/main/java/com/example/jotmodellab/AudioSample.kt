package com.example.jotmodellab

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

object PcmWave {
    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size >= 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Choose a PCM WAV file" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var cursor = 12; var validFormat = false
        while (cursor + 8 <= bytes.size) {
            val name = String(bytes, cursor, 4); val size = b.getInt(cursor + 4)
            require(size >= 0 && size.toLong() + cursor + 8 <= bytes.size) { "Invalid WAV chunk" }
            val start = cursor + 8
            if (name == "fmt ") {
                require(size >= 16)
                validFormat = b.getShort(start).toInt() == 1 && b.getShort(start + 2).toInt() == 1 && b.getInt(start + 4) == 16000 && b.getShort(start + 14).toInt() == 16
            }
            if (name == "data") {
                require(validFormat) { "WAV must be mono, 16 kHz, PCM16" }
                require(size > 0 && size % 2 == 0 && size / 2 <= MelFeatures.maxSamples) { "Use a recording of at most 30 seconds" }
                return FloatArray(size / 2) { b.getShort(start + it * 2) / 32768f }
            }
            cursor = start + size + size % 2
        }
        error("WAV has no audio")
    }
    fun pcm(audio: FloatArray): ByteArray = ByteBuffer.allocate(audio.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        audio.forEach { putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
    }.array()
}

class SampleRecorder(private val context: android.content.Context) {
    private val recording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    fun start(finished: (Result<FloatArray>) -> Unit) {
        check(!recording.get())
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission is required")
        }
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "This microphone cannot record 16 kHz audio" }
        val input = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 8192))
        try {
            check(input.state == AudioRecord.STATE_INITIALIZED) { "Microphone is unavailable" }
            input.startRecording()
        } catch (error: Throwable) { input.release(); throw error }
        recorder = input; recording.set(true)
        thread = Thread({
            val outcome = runCatching {
                val audio = FloatArray(MelFeatures.maxSamples); val block = ShortArray(2048); var count = 0
                while (recording.get() && count < audio.size) {
                    val n = input.read(block, 0, minOf(block.size, audio.size - count))
                    if (!recording.get()) break
                    check(n >= 0) { "Microphone read failed: $n" }
                    for (i in 0 until n) audio[count++] = block[i] / 32768f
                }
                check(count > 0) { "No audio recorded" }; audio.copyOf(count)
            }
            recording.set(false)
            runCatching { input.stop() }; input.release(); recorder = null
            finished(outcome)
        }, "JotSampleRecorder").apply { start() }
    }
    fun stop() { recording.set(false); runCatching { recorder?.stop() } }
}
