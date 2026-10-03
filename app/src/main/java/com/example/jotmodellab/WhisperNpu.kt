package com.example.jotmodellab

import ai.onnxruntime.*
import android.content.Context
import android.os.SystemClock
import android.util.Half
import org.json.JSONObject
import java.io.File
import java.nio.*

data class SpeechRun(val text: String, val loadMs: Long, val featureMs: Long, val inferenceMs: Long, val tokens: Int, val truncated: Boolean, val evidence: String)

/** Qualcomm's SHA Whisper graph contract, with CPU execution explicitly prohibited. */
class WhisperNpu(private val context: Context, private val directory: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val encoder: OrtSession
    private val decoder: OrtSession
    val loadMs: Long
    private val vocabulary = JSONObject(context.assets.open("whisper-vocab.json").bufferedReader().readText())
    private val inverseBytes: Map<Int, Int> = run {
        val kept = ((33..126) + (161..172) + (174..255)).toMutableList()
        val chars = kept.toMutableList(); var next = 256
        for (b in 0..255) if (b !in kept) { kept += b; chars += next++ }
        chars.zip(kept).toMap()
    }
    private val tokens = buildMap<Int, String> { vocabulary.keys().forEach { token -> put(vocabulary.getInt(token), token) } }
    init {
        val start = SystemClock.elapsedRealtime()
        synchronized(WhisperNpu::class.java) {
            if (!registered) { env.registerExecutionProviderLibrary("QNNExecutionProvider", "${context.applicationInfo.nativeLibraryDir}/libonnxruntime_providers_qnn.so"); registered = true }
        }
        val devices = env.epDevices.filter { it.epName == "QNNExecutionProvider" }
        check(devices.isNotEmpty()) { "Qualcomm HTP provider unavailable; CPU fallback is disabled." }
        fun open(name: String): OrtSession = OrtSession.SessionOptions().use { options ->
            options.addExecutionProvider(devices, mapOf("backend_path" to "${context.applicationInfo.nativeLibraryDir}/libQnnHtp.so", "profiling_level" to "basic", "profiling_file_path" to File(context.filesDir, "${directory.name}-$name-qnn.csv").path, "htp_performance_mode" to "balanced"))
            options.addConfigEntry("session.disable_cpu_ep_fallback", "1")
            options.enableProfiling(File(context.filesDir, "${directory.name}-$name-profile").path)
            env.createSession(File(directory, "$name.onnx").path, options)
        }
        encoder = open("encoder")
        try { decoder = open("decoder") } catch (e: Throwable) { encoder.close(); throw e }
        loadMs = SystemClock.elapsedRealtime() - start
    }

    fun transcribe(audio: FloatArray, cancelled: () -> Boolean = { false }): SpeechRun {
        val featureStart = SystemClock.elapsedRealtime()
        val features = MelFeatures.extract(audio)
        val featureMs = SystemClock.elapsedRealtime() - featureStart
        val start = SystemClock.elapsedRealtime()
        half(features, longArrayOf(1, 80, 3000)).use { input -> encoder.run(mapOf("input_features" to input)).use { cross ->
            val cache = mutableMapOf<String, OnnxTensor>()
            decoder.inputInfo.filterKeys { it.contains("cache_self") }.forEach { (name, info) ->
                val shape = (info.info as TensorInfo).shape
                cache[name] = half(FloatArray(shape.fold(1L) { a, b -> a * b }.toInt()), shape)
            }
            var previous: OrtSession.Result? = null
            val generated = mutableListOf<Int>()
            var token = 50258; var ended = false
            try {
                for (position in 0 until 199) {
                    check(!cancelled()) { "Cancelled" }
                    val inputs = mutableMapOf<String, OnnxTensor>()
                    cross.forEach { entry -> inputs[entry.key] = entry.value as OnnxTensor }
                    inputs.putAll(cache)
                    val ids = OnnxTensor.createTensor(env, IntBuffer.wrap(intArrayOf(token)), longArrayOf(1, 1))
                    val pos = OnnxTensor.createTensor(env, IntBuffer.wrap(intArrayOf(position)), longArrayOf(1))
                    val mask = half(FloatArray(200) { if (it >= 199 - position) 0f else -100f }, longArrayOf(1, 1, 1, 200))
                    inputs["input_ids"] = ids; inputs["position_ids"] = pos; inputs["attention_mask"] = mask
                    val result = try { decoder.run(inputs) } finally { ids.close(); pos.close(); mask.close() }
                    val logits = (result.get("logits").get() as OnnxTensor).floatBuffer
                    var best = Float.NEGATIVE_INFINITY; var bestId = 0
                    for (i in 0 until logits.remaining()) {
                        val value = logits.get(i)
                        // Force English transcription without timestamps, then suppress non-text tokens.
                        if (position >= 3 && i >= 50257 && i != 50257) continue
                        if (value > best) { best = value; bestId = i }
                    }
                    token = when (position) { 0 -> 50259; 1 -> 50359; 2 -> 50363; else -> bestId }
                    if (previous == null) cache.values.forEach { it.close() }
                    cache.clear()
                    result.forEach { entry -> if (entry.key.endsWith("_out")) cache[entry.key.removeSuffix("_out") + "_in"] = entry.value as OnnxTensor }
                    previous?.close(); previous = result
                    if (token == 50257) { ended = true; break }
                    if (token < 50257) generated += token
                }
                val bytes = java.io.ByteArrayOutputStream()
                generated.forEach { id -> tokens[id]?.codePoints()?.forEach { c -> bytes.write(inverseBytes[c] ?: error("Invalid tokenizer byte")) } }
                return SpeechRun(bytes.toByteArray().toString(Charsets.UTF_8).trim(), loadMs, featureMs, SystemClock.elapsedRealtime() - start, generated.size, !ended,
                    "QNN HTP encoder + decoder completed; CPU fallback disabled. ORT profiles saved locally.")
            } finally { if (previous == null) cache.values.forEach { it.close() }; previous?.close() }
        } }
    }
    // ORT's FLOAT16 API takes raw half bits in a ShortBuffer, without widening.
    @android.annotation.SuppressLint("HalfFloat")
    private fun half(values: FloatArray, shape: LongArray): OnnxTensor {
        val buffer = ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        values.forEach { buffer.put(Half.toHalf(it)) }; buffer.flip()
        return OnnxTensor.createTensor(env, buffer, shape, OnnxJavaType.FLOAT16)
    }
    override fun close() { encoder.close(); decoder.close() }
    companion object { private var registered = false }
}
