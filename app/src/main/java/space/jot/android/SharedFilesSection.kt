package space.jot.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import space.jot.android.contract.SessionFile
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Files from Jot for Mac: a vocabulary export to import and a session file to read. */
@Composable fun SharedFilesSection(state: SharedFilesState, importVocabulary: () -> Unit, openSession: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("4 · Files from Jot for Mac", style = MaterialTheme.typography.titleMedium)
        Text("On the Mac, run jot vocabulary export > vocabulary.json or jot export <session-id> --file, then move the file to this phone.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(importVocabulary) { Text("Import vocabulary") }
            OutlinedButton(openSession) { Text("Open session") }
        }
        if (state.status.isNotEmpty()) Text(state.status)
        val enabled = state.vocabulary.count { it.enabled }
        Text("Vocabulary: ${state.vocabulary.size} entries, $enabled on. Import only adds phrases the list does not have.", style = MaterialTheme.typography.bodySmall)
        state.vocabulary.take(SHOWN).forEach { entry ->
            Text("${entry.heard.ifEmpty { entry.preferred }} → ${entry.preferred}${if (entry.enabled) "" else " (off)"}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.vocabulary.size > SHOWN) Text("and ${state.vocabulary.size - SHOWN} more", style = MaterialTheme.typography.bodySmall)
        state.session?.let { SessionCard(it) }
    }
}

@Composable private fun SessionCard(session: SessionFile.Contents) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(session.title ?: "Session", style = MaterialTheme.typography.titleMedium)
            Text("Started ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(session.startedAt)}", style = MaterialTheme.typography.bodySmall)
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    session.rows.take(SHOWN_ROWS).forEach { row -> Text("[${clock(row.start)}] ${speaker(row)}${row.text}") }
                }
            }
            if (session.rows.size > SHOWN_ROWS) Text("and ${session.rows.size - SHOWN_ROWS} more rows", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The person's name for the speaker, else the session's grouping as the Mac's Markdown export writes it. */
private fun speaker(row: SessionFile.Row): String = when {
    row.speakerName != null -> "${row.speakerName}: "
    row.speaker == "overlap" -> "Overlap: "
    row.speaker != null -> "${row.speaker.replaceFirst("speaker-", "Speaker ")}: "
    row.mode == "dictation" -> "Dictation: "
    else -> ""
}

private fun clock(seconds: Double): String {
    val whole = seconds.toLong()
    return "%d:%02d:%02d".format(whole / 3600, whole / 60 % 60, whole % 60)
}

private const val SHOWN_ROWS = 200

private const val SHOWN = 20
