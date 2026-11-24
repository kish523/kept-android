package app.kept.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kept.AppContainer
import app.kept.data.db.CaptureSource
import app.kept.data.db.DeadlineRow
import app.kept.data.db.ReceiptEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val query: String = "",
    /** Receipts still being read, or read but not yet confirmed. */
    val unfinished: List<ReceiptEntity> = emptyList(),
    val returnsClosing: List<DeadlineRow> = emptyList(),
    val warrantiesEnding: List<DeadlineRow> = emptyList(),
    val everythingElse: List<DeadlineRow> = emptyList(),
) {
    val isEmpty: Boolean
        get() = loaded && query.isBlank() && unfinished.isEmpty() && returnsClosing.isEmpty() &&
            warrantiesEnding.isEmpty() && everythingElse.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val matches = query.mapLatest { q -> if (q.isBlank()) null else container.repository.search(q).toSet() }

    val state: StateFlow<HomeUiState> = combine(
        container.database.receiptDao().observeUnfinished(),
        container.database.itemDao().observeDeadlines(),
        query,
        matches,
    ) { unfinished, rows, q, ids ->
        val today = LocalDate.now()
        val visible = if (ids == null) rows else rows.filter { it.receiptId in ids }
        val returns = visible
            .filter { r -> r.returnBy?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(30)) } == true }
            .sortedBy { it.returnBy }
        val warranties = (visible - returns.toSet())
            .filter { r -> r.warrantyEnds?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(90)) } == true }
            .sortedBy { it.warrantyEnds }
        HomeUiState(
            loaded = true,
            today = today,
            query = q,
            unfinished = if (ids == null) unfinished else unfinished.filter { it.id in ids },
            returnsClosing = returns,
            warrantiesEnding = warranties,
            everythingElse = visible - returns.toSet() - warranties.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(q: String) {
        query.value = q
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
