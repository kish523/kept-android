package app.kept.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kept.AppContainer
import app.kept.data.db.CaptureSource
import app.kept.data.settings.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val query: String = "",
    val filter: HomeFilter = HomeFilter.ALL,
    val content: HomeContent = HomeContent(null, emptyList(), 0, 0, 0, emptyList()),
) {
    val isEmpty: Boolean
        get() = loaded && query.isBlank() && content.unfinished.isEmpty() && content.allCount == 0
}

/** A message for the snackbar; [undo] is offered as the "Undo" action when present. */
data class HomeMessage(val text: String, val undo: (suspend () -> Unit)? = null)

private data class Inputs(val query: String, val filter: HomeFilter)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HomeFilter.ALL)
    private val matches: Flow<Set<String>?> =
        query.mapLatest { q -> if (q.isBlank()) null else container.repository.search(q).toSet() }

    private val _messages = Channel<HomeMessage>(Channel.BUFFERED)
    val messages: Flow<HomeMessage> = _messages.receiveAsFlow()

    val themeMode: StateFlow<ThemeMode> = container.themeSettings.mode

    val state: StateFlow<HomeUiState> = combine(
        container.database.receiptDao().observeUnfinished(),
        container.database.itemDao().observeDeadlines(),
        combine(query, filter, ::Inputs),
        matches,
    ) { unfinished, rows, inputs, ids ->
        val today = LocalDate.now()
        HomeUiState(
            loaded = true,
            today = today,
            query = inputs.query,
            filter = inputs.filter,
            content = buildHome(rows, unfinished, today, inputs.filter, ids),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(q: String) {
        query.value = q
    }

    fun onFilter(f: HomeFilter) {
        filter.value = f
    }

    fun setTheme(mode: ThemeMode) = container.themeSettings.set(mode)

    /** "Keeping it" on the Next up card. */
    fun keep(itemId: String) = viewModelScope.launch {
        container.repository.markKeeping(itemId)
        _messages.send(HomeMessage("Marked as kept. No more return reminders for it.") { container.repository.undoKeeping(itemId) })
    }

    /** "Returned it" on the Next up card. */
    fun returned(itemId: String) = viewModelScope.launch {
        val before = container.repository.markReturned(itemId)
        _messages.send(HomeMessage("Marked as returned. Kept stopped tracking it.") { container.repository.undoReturned(itemId, before) })
    }

    fun undo(message: HomeMessage) {
        val action = message.undo ?: return
        container.appScope.launch { action() }
    }

    /** Saves the scan as a draft (nothing can lose it after this), then reads it in the background. */
    suspend fun createDraftFromScan(pages: List<Uri>): String {
        val id = container.repository.createDraft(pages, CaptureSource.CAMERA)
        container.appScope.launch { container.extraction.run(id) }
        return id
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { HomeViewModel(container) } }
    }
}
