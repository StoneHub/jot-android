package space.jot.android

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.jot.android.contract.ContractFileException
import space.jot.android.contract.VocabularyEntry
import space.jot.android.contract.VocabularyFile
import space.jot.android.contract.VocabularyStore
import java.io.File

data class SharedFilesState(val vocabulary: List<VocabularyEntry> = emptyList(), val status: String = "")

/** Files shared with Jot for Mac (docs/SHARED-CONTRACT.md in StoneHub/jot). Nothing here leaves the phone. */
class SharedFilesViewModel(app: Application) : AndroidViewModel(app) {
    private val vocabulary = VocabularyStore(File(app.filesDir, "vocabulary.json"))
    private val mutable = MutableStateFlow(SharedFilesState())
    // One file operation at a time, so two imports cannot both read the list before either saves it.
    private val files = Dispatchers.IO.limitedParallelism(1)
    val state = mutable.asStateFlow()

    init { inBackground { mutable.update { it.copy(vocabulary = vocabulary.load().entries) } } }

    fun importVocabulary(uri: Uri) = inBackground {
        val result = vocabulary.import(read(uri, VocabularyFile.MAX_BYTES))
        val entries = vocabulary.load().entries
        mutable.update { it.copy(vocabulary = entries, status = "Added ${result.added.size} vocabulary entries; skipped ${result.skipped} phrases already in the list.") }
    }

    private fun inBackground(action: () -> Unit) {
        viewModelScope.launch {
            val failure = withContext(files) { runCatching(action).exceptionOrNull() }
            if (failure != null) mutable.update {
                it.copy(status = if (failure is ContractFileException) failure.message.orEmpty() else "Could not read that file: ${failure.message ?: failure.javaClass.simpleName}")
            }
        }
    }

    /** Reads at most one byte past [limit], so an oversized file is refused without loading all of it. */
    private fun read(uri: Uri, limit: Int): ByteArray =
        checkNotNull(getApplication<Application>().contentResolver.openInputStream(uri)) { "The file could not be opened" }
            .use { it.readNBytes(limit + 1) }
}
