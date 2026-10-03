package com.example.jotmodellab

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jotmodellab.theme.JotModelLabTheme

class MainActivity : ComponentActivity() {
    private val lab: LabViewModel by viewModels()
    private val mic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lab.record()
        else android.widget.Toast.makeText(this, "Microphone permission is needed only to record your sample.", android.widget.Toast.LENGTH_LONG).show()
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(lab.report()) } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            JotModelLabTheme {
                val state by lab.state.collectAsStateWithLifecycle()
                Surface(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.safeDrawingPadding()) {
                        if (maxWidth >= 700.dp) {
                            Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Controls(state) }
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Results(state) }
                            }
                        } else {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Controls(state); Results(state) }
                        }
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); lab.refreshReady() }
    override fun onStop() { lab.stopRecording(); super.onStop() }
    @Composable private fun Controls(state: LabState) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Jot Model Lab", style = MaterialTheme.typography.headlineLarge)
            Text("Find the right local dictation pipeline.", style = MaterialTheme.typography.bodyLarge)
            Text("${Build.MODEL} · ${Build.SOC_MODEL}\nAndroid ${Build.VERSION.RELEASE} · Hexagon HTP", style = MaterialTheme.typography.labelLarge)
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.status)
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Models: ${state.ready.size}/3 ready · Audio and text stay on this phone.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.ready.size < 3) {
                Button(lab::prepare, enabled = !state.busy && !state.recording, modifier = Modifier.fillMaxWidth()) { Text("Prepare models") }
                Text("One-time download: about 1.4 GB. Use Wi-Fi. Once ready, inference works offline.", style = MaterialTheme.typography.bodySmall)
            }
            Text("1 · Choose a recording", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (state.recording || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) lab.record()
                    else mic.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = !state.busy) { Text(if (state.recording) "Stop recording" else "Record") }
                OutlinedButton(lab::example, enabled = !state.busy && !state.recording) { Text("Load example") }
            }
            Text("${state.sampleLabel}${if (state.seconds > 0) " · %.1f seconds".format(state.seconds) else ""}")
            OutlinedTextField(state.expected, lab::editExpected, label = { Text("Expected words (optional)") }, supportingText = { Text("Enter what you said to compare word error rates.") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !state.recording, minLines = 2)
            Text("2 · Compare speech models", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelCatalog.speech.forEach { model ->
                    OutlinedButton({ lab.speech(listOf(model)) }, enabled = !state.busy && !state.recording && state.seconds > 0 && model.id in state.ready) { Text(if (model.id == "base") "Run Base" else "Run Small") }
                }
            }
            Button({ lab.speech(ModelCatalog.speech) }, enabled = !state.busy && !state.recording && state.seconds > 0 && ModelCatalog.speech.all { it.id in state.ready }, modifier = Modifier.fillMaxWidth()) { Text("Compare both on the same recording") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(state.repeat, lab::repeat, enabled = !state.busy)
                Text("Three runs each · cold then warm", modifier = Modifier.padding(top = 12.dp))
            }
            OutlinedButton(lab::system, enabled = !state.busy && !state.recording && state.seconds > 0 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { Text("Try system baseline") }
            Text("System recognition is on-device; its accelerator use is unverified. Some providers do not accept recorded audio.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("3 · Test cleanup", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(state.cleanupInput, lab::editCleanup, label = { Text("Original dictation") }, minLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !state.recording)
            Button(lab::cleanup, enabled = !state.busy && !state.recording && state.cleanupInput.isNotBlank() && "cleanup" in state.ready) { Text("Clean up with Qwen3 · NPU") }
        }
    }
    @Composable private fun Results(state: LabState) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Results", style = MaterialTheme.typography.headlineSmall)
            if (state.results.isEmpty() && state.cleanupResult == null) Text("Run the example first, then try your own voice. Each model sees exactly the same recording.")
            state.results.forEach { result -> ResultPanel(result) {
                OutlinedButton({ lab.editCleanup(result.text) }, enabled = !state.busy && !state.recording) { Text("Use for cleanup") }
            } }
            state.cleanupResult?.let { ResultPanel(it) {} }
            HorizontalDivider()
            Text(state.environment, style = MaterialTheme.typography.bodySmall)
            Text("NPU results require the Qualcomm backend to complete. CPU fallback is disabled for speech; cleanup is pinned to QAIRT / NPU. Audio preprocessing and token handling use the CPU.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ export.launch("jot-model-lab-results.json") }, enabled = !state.busy && (state.results.isNotEmpty() || state.cleanupResult != null)) { Text("Save results") }
            Text("Saved results include text and timings, but no audio. Nothing is uploaded automatically.", style = MaterialTheme.typography.bodySmall)
        }
    }
    @Composable private fun ResultPanel(result: ResultCard, action: @Composable () -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(result.label, style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(result.text.ifBlank { "No words returned" }, style = MaterialTheme.typography.bodyLarge) }
                Text(result.details, style = MaterialTheme.typography.bodySmall)
                Text(result.evidence, style = MaterialTheme.typography.labelSmall)
                action()
            }
        }
    }
}
