package com.example.jotmodellab

import android.content.Context
import android.os.SystemClock
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.bean.*
import org.json.JSONObject
import java.io.File

data class CleanupRun(val text: String, val loadMs: Long, val inferenceMs: Long, val tokens: Long, val evidence: String, val warning: String?)

object CleanupChecks {
    fun warning(source: String, candidate: String): String? {
        if (candidate.isBlank()) return "Empty output; keep the original."
        if (candidate.contains("<think>") || candidate.contains("<|")) return "Model returned control text; keep the original."
        val numbers = Regex("\\d+(?:[.,]\\d+)*")
        if (numbers.findAll(source).map { it.value }.toList() != numbers.findAll(candidate).map { it.value }.toList()) return "Numbers changed; keep the original."
        val negations = Regex("\\b(?:not|never|no|cannot|can't|don't|won't|isn't|wasn't|shouldn't)\\b", RegexOption.IGNORE_CASE)
        if (negations.findAll(source).count() != negations.findAll(candidate).count()) return "Negation changed; keep the original."
        if (candidate.length > maxOf(80, source.length * 2)) return "Output expanded substantially; review before using."
        return null
    }
}

class CleanupNpu(private val context: Context, private val directory: File) {
    private var wrapper: LlmWrapper? = null
    suspend fun clean(source: String): CleanupRun {
        require(source.isNotBlank() && source.length <= 1200) { "Use a short dictation (up to 1,200 characters)" }
        val loadStart = SystemClock.elapsedRealtime(); var loadMs = 0L
        if (wrapper == null) {
            val sdk = GenieXSdk.getInstance()
            synchronized(CleanupNpu::class.java) {
                if (!registered) {
                    android.system.Os.setenv("ADSP_LIBRARY_PATH", context.applicationInfo.nativeLibraryDir, true)
                    check(sdk.registerPlugin("${context.applicationInfo.nativeLibraryDir}/libgeniex_plugin_qairt.so") == 0) { "Qualcomm QAIRT plugin could not load" }
                    registered = true
                }
            }
            val original = JSONObject(File(directory, "genie_config.json").readText())
            val dialog = original.getJSONObject("dialog")
            val engine = dialog.getJSONObject("engine")
            check(engine.getJSONObject("backend").getString("type") == "QnnHtp") { "Cleanup model must use HTP" }
            dialog.getJSONObject("tokenizer").put("path", File(directory, "tokenizer.json").path)
            engine.getJSONObject("backend").put("extensions", File(directory, "htp_backend_ext_config.json").path)
            val binaries = engine.getJSONObject("model").getJSONObject("binary").getJSONArray("ctx-bins")
            for (i in 0 until binaries.length()) binaries.put(i, File(directory, binaries.getString(i)).path)
            val config = File(directory, "jot-genie-config.json").apply { writeText(original.toString()) }
            wrapper = LlmWrapper.builder().llmCreateInput(LlmCreateInput(
                model_path = config.path, tokenizer_path = File(directory, "tokenizer.json").path,
                config = ModelConfig(nCtx = 0, nGpuLayers = 0, nThreads = 2, power_mode = "balanced",
                    chat_template_content = JSONObject(File(directory, "tokenizer_config.json").readText()).getString("chat_template")),
                runtime_id = "qairt", compute_unit = "NPU"
            )).build().getOrThrow()
            loadMs = SystemClock.elapsedRealtime() - loadStart
        }
        val model = checkNotNull(wrapper)
        check(model.reset() == 0) { "Cannot reset cleanup context" }
        val instruction = "You edit dictation. Remove filler words such as um and uh, and fix punctuation. Keep every sentence and every detail, including names, numbers and negatives. Never summarize or shorten. Leave short fragments as fragments without adding sentence punctuation. Output only the edited text."
        val prompt = model.applyChatTemplate(arrayOf(
            ChatMessage("system", instruction),
            ChatMessage("user", "Um please send the red folder on Tuesday. Do not change 17."),
            ChatMessage("assistant", "Please send the red folder on Tuesday. Do not change 17."),
            ChatMessage("user", "uh blue folder"), ChatMessage("assistant", "blue folder"),
            ChatMessage("user", source)
        ), null, false).getOrThrow().formattedText
        check(prompt.contains("<|im_start|>") && prompt.contains(source)) { "Qwen chat template did not preserve the input" }
        val start = SystemClock.elapsedRealtime()
        val output = StringBuilder(); var count = 0L; var reason = ""
        model.generateStreamFlow(prompt, GenerationConfig(maxTokens = 256, samplerConfig = SamplerConfig(temperature = 0f, topK = 1))).collect { chunk ->
            when (chunk) {
                is LlmStreamResult.Token -> output.append(chunk.text)
                is LlmStreamResult.Completed -> { count = chunk.profile.generatedTokens; reason = chunk.profile.stopReason }
                is LlmStreamResult.Error -> throw chunk.throwable
            }
        }
        val text = output.toString().trim()
        return CleanupRun(text, loadMs, SystemClock.elapsedRealtime() - start, count,
            "QAIRT runtime pinned to NPU; compiled QnnHtp model completed. Stop: $reason.",
            if (count >= 256 || reason.contains("limit", true) || reason.contains("max", true)) "Token limit reached; output may be incomplete. Keep the original."
            else CleanupChecks.warning(source, text))
    }
    fun close() { wrapper?.close(); wrapper = null }
    companion object { private var registered = false }
}
