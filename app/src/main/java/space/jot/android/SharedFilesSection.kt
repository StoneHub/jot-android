package space.jot.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Files from Jot for Mac: a vocabulary export to import. */
@Composable fun SharedFilesSection(state: SharedFilesState, importVocabulary: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("4 · Files from Jot for Mac", style = MaterialTheme.typography.titleMedium)
        Text("Make the file on the Mac with jot vocabulary export > vocabulary.json, then move it to this phone.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(importVocabulary) { Text("Import vocabulary") }
        if (state.status.isNotEmpty()) Text(state.status)
        val enabled = state.vocabulary.count { it.enabled }
        Text("Vocabulary: ${state.vocabulary.size} entries, $enabled on. Import only adds phrases the list does not have.", style = MaterialTheme.typography.bodySmall)
        state.vocabulary.take(SHOWN).forEach { entry ->
            Text("${entry.heard.ifEmpty { entry.preferred }} → ${entry.preferred}${if (entry.enabled) "" else " (off)"}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.vocabulary.size > SHOWN) Text("and ${state.vocabulary.size - SHOWN} more", style = MaterialTheme.typography.bodySmall)
    }
}

private const val SHOWN = 20
