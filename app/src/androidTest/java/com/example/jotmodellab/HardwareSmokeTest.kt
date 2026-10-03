package com.example.jotmodellab

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.ViewModelStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HardwareSmokeTest {
    @Test fun leavingDuringCleanupFinishesBeforeDisposal() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var lab: LabViewModel
        val store = ViewModelStore()
        instrumentation.runOnMainSync {
            lab = LabViewModel(instrumentation.targetContext.applicationContext as android.app.Application)
            store.put("lab", lab)
            lab.editCleanup("Um please send the blue folder tomorrow. Do not change 42.")
            lab.cleanup()
        }
        assertTrue(lab.state.value.busy)
        delay(50)
        instrumentation.runOnMainSync { store.clear() }
        withTimeout(20_000) { while (lab.state.value.busy) delay(100) }
    }

    @Test fun compiledModelsRunOnFoldNpu() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val files = ModelFiles(app)
        val audio = PcmWave.decode(app.assets.open("reference.wav").use { it.readBytes() })
        assertTrue(audio.size > 16000)
        val results = JSONArray()
        for (model in ModelCatalog.speech) {
            assertTrue("Model must be seeded or downloaded: ${model.id}", files.ready(model))
            WhisperNpu(app, files.speech(model)).use { engine ->
                val run = engine.transcribe(audio)
                results.put(JSONObject().put("model", model.id).put("text", run.text).put("loadMs", run.loadMs).put("featureMs", run.featureMs).put("inferenceMs", run.inferenceMs).put("hardware", run.evidence))
                File(app.filesDir, "npu-smoke.json").writeText(results.toString(2))
                assertTrue("Unexpected speech output: ${run.text}", run.text.lowercase().contains("blue folder"))
                assertFalse(run.truncated)
            }
        }
        val cleaner = CleanupNpu(app, files.cleanup)
        try {
            val run = cleaner.clean("Um please send the blue folder tomorrow morning. Do not change 42.")
            results.put(JSONObject().put("model", "qwen3-0.6b").put("text", run.text).put("loadMs", run.loadMs).put("inferenceMs", run.inferenceMs).put("hardware", run.evidence))
            File(app.filesDir, "npu-smoke.json").writeText(results.toString(2))
            assertTrue(run.text.isNotBlank()); assertTrue(run.text.contains("42")); assertNull(run.warning)
        } finally { cleaner.close() }
    }
}
