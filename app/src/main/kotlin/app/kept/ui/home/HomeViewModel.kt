package app.kept.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kept.AppContainer
import app.kept.BuildConfig
import app.kept.update.NoteBlock
import app.kept.update.UpdateState
import app.kept.update.changelogSection
import app.kept.update.changelogTitle
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.flowOn
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

/**
 * A message for the snackbar. [undo] is offered as the "Undo" action when present; [onExpire] runs if the
 * message goes away without Undo (used to erase deleted receipts for good).
 */
data class HomeMessage(
    val text: String,
    val undo: (suspend () -> Unit)? = null,
    val onExpire: (suspend () -> Unit)? = null,
)

/** The "What's new" sheet shown once after an update. */
data class WhatsNew(val version: String, val title: String?, val blocks: List<NoteBlock>)

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
    val update: StateFlow<UpdateState> = container.updater.state
    val autoUpdate: StateFlow<Boolean> = container.prefs.autoUpdate

    private val _whatsNew = MutableStateFlow<WhatsNew?>(null)
    val whatsNew: StateFlow<WhatsNew?> = _whatsNew

    init {
        viewModelScope.launch(Dispatchers.IO) { _whatsNew.value = pendingWhatsNew() }
    }

    /** Shown once per version, and only after an update (not on a fresh install). */
    private fun pendingWhatsNew(): WhatsNew? {
        val prefs = container.prefs
        val current = BuildConfig.VERSION_CODE
        if (prefs.lastSeenVersionCode >= current) return null
        val ctx = container.appContext
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val updated = prefs.lastSeenVersionCode > 0 || info.firstInstallTime != info.lastUpdateTime
        if (!updated) { prefs.lastSeenVersionCode = current; return null }
        return loadWhatsNew()
    }

    /** The bundled notes for this version (also used by "What's new" in Settings). */
    fun loadWhatsNew(): WhatsNew? {
        val text = runCatching { container.appContext.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }.getOrNull() ?: return null
        val blocks = changelogSection(text, BuildConfig.VERSION_NAME).ifEmpty { return null }
        return WhatsNew(BuildConfig.VERSION_NAME, changelogTitle(text, BuildConfig.VERSION_NAME), blocks)
    }

    fun showWhatsNew() { _whatsNew.value = loadWhatsNew() }

    fun dismissWhatsNew() {
        container.prefs.lastSeenVersionCode = BuildConfig.VERSION_CODE
        _whatsNew.value = null
    }

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
    }
        // Sorting and grouping happen off the main thread, so drawing never waits for them.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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

    fun expire(message: HomeMessage) {
        val action = message.onExpire ?: return
        container.appScope.launch { action() }
    }

    /** Swipe or Delete: gone at once, erased for good when the Undo message goes. */
    fun delete(receiptId: String) = viewModelScope.launch {
        val repo = container.repository
        val before = repo.trash(receiptId) ?: return@launch
        _messages.send(
            HomeMessage(
                "Receipt deleted.",
                undo = { repo.restore(receiptId, before) },
                onExpire = { repo.purge(listOf(receiptId)) },
            ),
        )
    }

    suspend fun finishedCount(): Int = container.repository.finishedReceiptIds().size

    /** Settings > Clean up: deletes every receipt with nothing left to track, with one Undo. */
    fun cleanUpFinished() = viewModelScope.launch {
        val repo = container.repository
        val trashed = repo.finishedReceiptIds().mapNotNull { id -> repo.trash(id)?.let { id to it } }
        if (trashed.isEmpty()) return@launch
        val n = trashed.size
        _messages.send(
            HomeMessage(
                if (n == 1) "Deleted 1 finished receipt." else "Deleted $n finished receipts.",
                undo = { trashed.forEach { (id, s) -> repo.restore(id, s) } },
                onExpire = { repo.purge(trashed.map { it.first }) },
            ),
        )
    }

    // ---- updates and notifications, for Settings and the update card

    val reduceEffects: StateFlow<Boolean?> = container.prefs.reduceEffects
    val lowEndDevice: Boolean get() = container.lowEndDevice
    fun setReduceEffects(on: Boolean) = container.prefs.setReduceEffects(if (on == container.lowEndDevice) null else on)

    fun setAutoUpdate(on: Boolean) = container.prefs.setAutoUpdate(on)
    fun checkForUpdate() = container.appScope.launch { container.updater.checkAndDownload() }
    fun installUpdate() = container.updater.install()
    fun canInstallUpdates() = container.updater.canRequestInstalls()
    /** The "Allow" button: opens Android's switch, and the install continues when the user comes back. */
    fun installPermissionIntent() = container.updater.allowInstallsThenInstall()

    fun notificationsAllowed() = container.reminderNotifications.canPost()
    var askedForNotifications: Boolean
        get() = container.prefs.askedForNotifications
        set(v) { container.prefs.askedForNotifications = v }

    /** After the user allows notifications, deliver anything that was waiting. */
    fun onNotificationsAllowed() = container.appScope.launch { container.scheduler.sync() }

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
