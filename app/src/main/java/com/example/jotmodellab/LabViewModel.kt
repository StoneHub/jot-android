package com.example.jotmodellab

import android.app.Application
import android.os.*
import android.content.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

data class ResultCard(val label: String, val text: String, val details: String, val evidence: String)
data class LabState(
    val busy: Boolean = false, val recording: Boolean = false, val status: String = "Load the example or record up to 30 seconds.",
    val sampleLabel: String = "No recording yet", val seconds: Float = 0f, val ready: Set<String> = emptySet(),
    val results: List<ResultCard> = emptyList(), val cleanupInput: String = "", val cleanupResult: ResultCard? = null,
    val repeat: Boolean = false, val expected: String = "", val environment: String = ""
)

object WordScore {
    fun errorRate(expected: String, actual: String): String {
        fun words(s: String) = Regex("[\\p{L}\\p{N}']+").findAll(s.lowercase()).map { it.value }.toList()
        val a = words(expected); val b = words(actual)
        if (a.isEmpty()) return ""
        var prior = IntArray(b.size + 1) { it }
        for (i in a.indices) {
            val next = IntArray(b.size + 1); next[0] = i + 1
            for (j in b.indices) next[j + 1] = minOf(next[j] + 1, prior[j + 1] + 1, prior[j] + if (a[i] == b[j]) 0 else 1)
            prior = next
        }
        return "Word error rate: %.1f%%".format(100.0 * prior.last() / a.size)
    }
}

class LabViewModel(app: Application) : AndroidViewModel(app) {
    private val files = ModelFiles(app)
    private val mutable = MutableStateFlow(LabState())
    val state = mutable.asStateFlow()
    private var sample: FloatArray? = null
    private val recorder = SampleRecorder(app)
    private var cleaner: CleanupNpu? = null
    init { refreshReady(); environment() }
    private fun update(change: (LabState) -> LabState) = mutable.update(change)
    fun refreshReady() { update { it.copy(ready = (ModelCatalog.speech + ModelCatalog.cleanup).filter(files::ready).map { m -> m.id }.toSet()) } }
    fun editCleanup(value: String) { update { it.copy(cleanupInput = value, cleanupResult = null) } }
    fun editExpected(value: String) { update { it.copy(expected = value) } }
    fun repeat(value: Boolean) { update { it.copy(repeat = value) } }
    private fun operation(action: suspend () -> Unit) {
        if (state.value.busy || state.value.recording) return
        update { it.copy(busy = true) }
        viewModelScope.launch {
            try { action() } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { update { it.copy(status = "Could not complete: ${e.message ?: e.javaClass.simpleName}") } }
            finally { refreshReady(); environment(); update { it.copy(busy = false) } }
        }
    }
    fun prepare() = operation {
        require(Build.SOC_MODEL.uppercase().startsWith("SM8850")) { "These compiled models target SM8850; found ${Build.SOC_MODEL}." }
        withContext(Dispatchers.IO) { (ModelCatalog.speech + ModelCatalog.cleanup).forEach { model -> files.download(model) { message -> update { it.copy(status = message) } } } }
        update { it.copy(status = "All three models are ready. Inference works offline.") }
    }
    fun example() = operation {
        sample = withContext(Dispatchers.IO) { PcmWave.decode(getApplication<Application>().assets.open("reference.wav").use { it.readBytes() }) }
        update { it.copy(sampleLabel = "Example recording", seconds = sample!!.size / 16000f, results = emptyList(), cleanupResult = null,
            expected = "Please send the blue folder tomorrow morning. The meeting starts at nine thirty. Do not change the number forty two.", status = "Example loaded. Run a model or compare both.") }
    }
    fun record() {
        if (state.value.recording) { recorder.stop(); return }
        if (state.value.busy) return
        update { it.copy(recording = true, status = "Recording… stops automatically at 30 seconds.") }
        try {
            recorder.start { result ->
                result.fold(onSuccess = { audio ->
                    sample = audio
                    update { it.copy(recording = false, sampleLabel = "Your recording", seconds = audio.size / 16000f, expected = "", results = emptyList(), cleanupResult = null, status = "Recording ready. Run a model or compare both.") }
                }, onFailure = { error -> update { it.copy(recording = false, status = "Recording failed: ${error.message}") } })
            }
        } catch (e: Throwable) { update { it.copy(recording = false, status = "Microphone unavailable: ${e.message}") } }
    }
    fun stopRecording() { if (state.value.recording) recorder.stop() }
    fun speech(models: List<SpeechModel>) = operation {
        val audio = checkNotNull(sample) { "Load the example or make a recording first" }
        val expected = state.value.expected; val repeats = if (state.value.repeat) 3 else 1
        update { it.copy(results = emptyList()) }
        withContext(Dispatchers.IO) {
            for (model in models) {
                check(files.ready(model)) { "Prepare models first" }
                update { it.copy(status = "Loading ${model.label}…") }
                WhisperNpu(getApplication(), files.speech(model)).use { engine ->
                    repeat(repeats) { index ->
                        update { it.copy(status = "Running ${model.label} ${index + 1}/$repeats…") }
                        val result = engine.transcribe(audio) { !viewModelScope.isActive }
                        val details = "${result.inferenceMs} ms inference · ${result.featureMs} ms audio prep · ${if (index == 0) result.loadMs else 0} ms load · ${result.tokens} tokens\n${WordScore.errorRate(expected, result.text)}${if (result.truncated) "\nToken limit reached; transcript may be incomplete." else ""}"
                        update { it.copy(results = it.results + ResultCard(model.label + if (repeats > 1) " · run ${index + 1}" else "", result.text, details, result.evidence), cleanupInput = if (it.cleanupInput.isBlank()) result.text else it.cleanupInput) }
                    }
                }
            }
        }
        update { it.copy(status = "Finished. Compare the words, then choose text for cleanup.") }
    }
    fun system() = operation {
        val audio = checkNotNull(sample) { "Load the example or make a recording first" }
        update { it.copy(status = "Running the on-device system recognizer…") }
        val start = SystemClock.elapsedRealtime(); val text = recognizeSystemSample(getApplication(), audio)
        update { it.copy(results = it.results + ResultCard("System · local baseline", text, "${SystemClock.elapsedRealtime() - start} ms · ${WordScore.errorRate(it.expected, text)}", "On-device API selected. Accelerator use is unverified; no cloud fallback."), status = "System baseline finished.") }
    }
    fun cleanup() = operation {
        val source = state.value.cleanupInput
        check(files.ready(ModelCatalog.cleanup)) { "Prepare models first" }
        update { it.copy(status = "Running Qwen3 cleanup on HTP…") }
        val result = withContext(Dispatchers.IO) {
            val engine = cleaner ?: CleanupNpu(getApplication(), files.cleanup).also { cleaner = it }
            engine.clean(source)
        }
        update { it.copy(cleanupResult = ResultCard("Qwen3 0.6B · cleanup", result.text, "${result.inferenceMs} ms inference · ${result.loadMs} ms load · ${result.tokens} tokens${result.warning?.let { w -> "\n$w" }.orEmpty()}", result.evidence), status = "Cleanup finished. Review it alongside the original.") }
    }
    private fun environment() {
        val app = getApplication<Application>()
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val thermal = (app.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        update { it.copy(environment = "App PSS: ${Debug.getPss() / 1024} MB · Battery: ${level * 100 / maxOf(1, scale)}% / $temp°C · Thermal status: $thermal\nWhole-phone battery readings; not a measurement of this app's energy use.") }
    }
    fun report(): String = org.json.JSONObject().apply {
        put("device", "${Build.MODEL} / ${Build.SOC_MODEL} / Android ${Build.VERSION.RELEASE}")
        put("sample", state.value.sampleLabel); put("seconds", state.value.seconds)
        put("environment", state.value.environment)
        put("results", org.json.JSONArray().apply { state.value.results.forEach { r -> put(org.json.JSONObject().put("model", r.label).put("text", r.text).put("timings", r.details).put("hardware", r.evidence)) } })
        state.value.cleanupResult?.let { put("cleanup", org.json.JSONObject().put("source", state.value.cleanupInput).put("text", it.text).put("timings", it.details).put("hardware", it.evidence)) }
    }.toString(2)
    override fun onCleared() { recorder.stop(); cleaner?.close(); super.onCleared() }
}
