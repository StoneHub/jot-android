package com.example.jotmodellab

import android.content.Context
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

data class SpeechModel(val id: String, val label: String, val url: String, val sha256: String = "")

object ModelCatalog {
    private const val ROOT = "https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models"
    val speech = listOf(
        SpeechModel("base", "Whisper Base · NPU", "$ROOT/whisper_base/releases/v0.63.0/whisper_base-precompiled_qnn_onnx-float-qualcomm_snapdragon_8_elite_gen5_for_galaxy.zip", "38f341d0334c6119ac4d5fe6205d2e75bb45bb7b0b4d91f553fd6d69f284bbf2"),
        SpeechModel("small", "Whisper Small · NPU", "$ROOT/whisper_small/releases/v0.63.0/whisper_small-precompiled_qnn_onnx-float-qualcomm_snapdragon_8_elite_gen5_for_galaxy.zip", "c02d8e86b541f5b259b3b0f2b300b400a6a2e2828ceff10b668f3b3909e9f074")
    )
    val cleanup = SpeechModel("cleanup", "Qwen3 0.6B · NPU", "$ROOT/qwen3_0_6b/releases/v0.63.0/qwen3_0_6b-genie-w4a16-qualcomm_snapdragon_8_elite_gen5_for_galaxy.zip", "dd891556f17b3e0029bd1cc846ac17738c6d50db7d9e2546abd880ecab1c458d")
}

class ModelFiles(private val context: Context) {
    val root = File(context.filesDir, "models").apply { mkdirs() }
    val cleanup get() = File(root, "cleanup")
    fun speech(model: SpeechModel) = File(root, model.id)
    fun ready(model: SpeechModel) = if (model.id == "cleanup") listOf("genie_config.json", "tokenizer.json", "part1_of_2.bin", "part2_of_2.bin").all { File(cleanup, it).isFile } else speech(model).let { File(it, "encoder.onnx").isFile && File(it, "decoder.onnx").isFile && File(it, "metadata.json").isFile && File(it, "encoder_qairt_context.bin").isFile && File(it, "decoder_qairt_context.bin").isFile }

    fun download(model: SpeechModel, progress: (String) -> Unit) {
        if (ready(model)) return
        val archive = File(context.cacheDir, "${model.id}.zip.part")
        val stage = File(root, "${model.id}.partial").apply { deleteRecursively(); mkdirs() }
        try {
            val connection = URL(model.url).openConnection().apply { connectTimeout = 20_000; readTimeout = 30_000 }
            val total = connection.contentLengthLong
            val hash = java.security.MessageDigest.getInstance("SHA-256")
            connection.getInputStream().use { input -> archive.outputStream().use { out ->
                val buffer = ByteArray(256 * 1024); var count = 0L; var previous = -1
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    count += n; check(count <= 1_500_000_000) { "Model archive exceeds size limit" }
                    out.write(buffer, 0, n); hash.update(buffer, 0, n)
                    val percent = if (total > 0) (100 * count / total).toInt() else 0
                    if (percent != previous) { progress("Downloading ${model.label}: $percent%"); previous = percent }
                }
                check(total <= 0 || count == total) { "Incomplete download" }
            } }
            val digest = hash.digest().joinToString("") { "%02x".format(it) }
            check(model.sha256.isNotEmpty() && digest == model.sha256) { "Model archive checksum mismatch" }
            var extracted = 0L
            ZipInputStream(archive.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    check(name in setOf("encoder.onnx", "decoder.onnx", "encoder_qairt_context.bin", "decoder_qairt_context.bin", "metadata.json", "genie_config.json", "tokenizer.json", "tokenizer_config.json", "htp_backend_ext_config.json", "part1_of_2.bin", "part2_of_2.bin", "sample_prompt.txt", "config.json")) { "Unexpected model file: $name" }
                    File(stage, name).outputStream().use { out ->
                        val b = ByteArray(256 * 1024)
                        while (true) { val n = zip.read(b); if (n < 0) break; extracted += n; check(extracted < 2_000_000_000); out.write(b, 0, n) }
                    }
                }
            }
            File(stage, "archive.sha256").writeText(digest)
            val target = speech(model)
            check(!target.exists() || target.deleteRecursively())
            check(stage.renameTo(target)) { "Cannot finish model setup" }
            check(ready(model)) { "Model archive is missing required files" }
        } finally { archive.delete(); stage.deleteRecursively() }
    }
}
