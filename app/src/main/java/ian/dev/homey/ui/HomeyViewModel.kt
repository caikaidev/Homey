package ian.dev.homey.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import ian.dev.homey.data.backup.BackupException
import ian.dev.homey.data.backup.BackupManager
import ian.dev.homey.data.backup.BackupPrefs
import ian.dev.homey.data.backup.BackupPreview
import ian.dev.homey.data.backup.RestoreMode
import ian.dev.homey.data.database.AppDatabase
import ian.dev.homey.data.model.ProductCategory
import ian.dev.homey.data.model.categoryEnum
import ian.dev.homey.data.repository.HomeyRepository
import ian.dev.homey.data.repository.ItemDraft
import ian.dev.homey.domain.notification.NotificationHelper
import ian.dev.homey.domain.prediction.ItemStatus
import ian.dev.homey.domain.prediction.PredictionEngine
import ian.dev.homey.domain.worker.AutoBackupWorker
import ian.dev.homey.domain.worker.DailyReminderWorker
import ian.dev.homey.domain.worker.ReminderPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Inventory : Screen
    data class Detail(val id: String) : Screen
    data class Edit(val id: String?) : Screen
    data object Backup : Screen
}

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val key: Long = System.nanoTime()
)

data class HomeState(
    val today: Long = 0L,
    val loaded: Boolean = false,
    val hasAnyItem: Boolean = false,
    val shopping: List<ItemStatus> = emptyList(),
    val expiring: List<ItemStatus> = emptyList(),
    val soon: List<ItemStatus> = emptyList()
) {
    val total: Int get() = shopping.size + expiring.size
}

data class ReminderUiState(
    val enabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0
) {
    val timeText: String get() = "%d:%02d".format(hour, minute)
}

data class BackupUiState(
    val autoEnabled: Boolean = true,
    val folderLabel: String? = null,
    val lastBackupAt: Long = 0L,
    val productCount: Int = 0,
    val logCount: Int = 0,
    val sampleCount: Int = 0,
    val busy: Boolean = false,
    val canUndoRestore: Boolean = false
)

private data class PrefSnapshot(
    val autoEnabled: Boolean,
    val folderLabel: String?,
    val lastBackupAt: Long,
    val canUndo: Boolean
)

class HomeyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)
    private val repository = HomeyRepository(db)
    private val prefs = BackupPrefs(application)
    private val backup = BackupManager(application, db, prefs)

    init {
        NotificationHelper.createNotificationChannels(application)
        DailyReminderWorker.schedule(application)
        AutoBackupWorker.schedule(application)
    }

    // ---------- 时间 ----------

    /** 当天 0 点。只在日期变化时才会变，避免无意义的重算。 */
    private val today = MutableStateFlow(PredictionEngine.startOfDay(System.currentTimeMillis()))

    /** 回到 App（onResume）时调用：如果已经跨天，首页会立即刷新。 */
    fun refreshClock() {
        today.value = PredictionEngine.startOfDay(System.currentTimeMillis())
    }

    init {
        // 跨过零点时自动刷新"还剩几天/几天后到期"
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val nextMidnight = PredictionEngine.startOfDay(now) + PredictionEngine.DAY_MS
                delay(nextMidnight - now + 1_000)
                refreshClock()
            }
        }
    }

    // ---------- 数据 ----------

    /** null 表示还在加载。 */
    val items: StateFlow<List<ItemStatus>?> = repository.observeItems(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val home: StateFlow<HomeState> = combine(items, today) { list, day ->
        if (list == null) {
            HomeState(today = day)
        } else {
            HomeState(
                today = day,
                loaded = true,
                hasAnyItem = list.isNotEmpty(),
                shopping = list.filter { it.needsBuy }.sortedBy(PredictionEngine::urgencyKey),
                expiring = list.filter { it.expiringSoon && !it.needsBuy }.sortedBy { it.expiresInDays ?: 0 },
                soon = list.filter { it.runsOutSoon }.sortedBy { it.daysLeft ?: 0 }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    // ---------- 导航 ----------

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    fun open(screen: Screen) {
        _stack.value = _stack.value + screen
    }

    /**
     * 只有 [screen] 仍在栈顶时才返回。页面退出动画期间旧页面还在屏幕上，
     * 用它可以避免连点「返回/保存」时多退一级。
     */
    fun backFrom(screen: Screen) {
        if (_stack.value.lastOrNull() == screen) back()
    }

    fun switchTab(tab: Screen) {
        _stack.value = listOf(tab)
    }

    /** 返回 true 表示处理了返回键。 */
    fun back(): Boolean {
        val current = _stack.value
        if (current.size <= 1) {
            if (current.firstOrNull() != Screen.Home) {
                _stack.value = listOf(Screen.Home)
                return true
            }
            return false
        }
        _stack.value = current.dropLast(1)
        return true
    }

    // ---------- 提示 ----------

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun consumeMessage(key: Long) {
        if (_message.value?.key == key) _message.value = null
    }

    private fun say(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _message.value = UiMessage(text, actionLabel, action)
    }

    // ---------- 今天 ----------

    private val _checked = MutableStateFlow<Set<String>>(emptySet())
    val checked: StateFlow<Set<String>> = _checked.asStateFlow()

    fun toggleChecked(id: String) {
        val now = _checked.value
        _checked.value = if (id in now) now - id else now + id
    }

    fun stockInChecked() {
        val visible = home.value.shopping.map { it.id }.toSet()
        val ids = _checked.value intersect visible
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.restockMany(ids)
            _checked.value = emptySet()
            say("已入库 ${ids.size} 件，库存和提醒已更新")
        }
    }

    // ---------- 物品列表 ----------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _category = MutableStateFlow<ProductCategory?>(null)
    val category: StateFlow<ProductCategory?> = _category.asStateFlow()

    fun setQuery(value: String) { _query.value = value }
    fun setCategory(value: ProductCategory?) { _category.value = value }

    val inventory: StateFlow<List<ItemStatus>> = combine(items, _query, _category) { list, q, cat ->
        list.orEmpty()
            .filter { cat == null || it.product.categoryEnum == cat }
            .filter { q.isBlank() || it.product.name.contains(q.trim(), ignoreCase = true) }
            .sortedBy(PredictionEngine::urgencyKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 只显示已有物品用到的分类。 */
    val usedCategories: StateFlow<List<ProductCategory>> = items.map { list ->
        val used = list.orEmpty().map { it.product.categoryEnum }.toSet()
        ProductCategory.entries.filter { it in used }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- 物品操作 ----------

    fun adjust(id: String, delta: Double) {
        viewModelScope.launch { repository.adjustQuantity(id, delta) }
    }

    fun setQuantity(id: String, quantity: Int) {
        viewModelScope.launch { repository.setQuantity(id, quantity.toDouble()) }
    }

    fun setLevel(id: String, level: Int) {
        viewModelScope.launch { repository.setLevel(id, level) }
    }

    fun restock(id: String) {
        viewModelScope.launch {
            repository.restock(id)
            say("已补货入库")
        }
    }

    fun saveItem(id: String?, draft: ItemDraft) {
        if (draft.name.isBlank()) {
            say("先填一下名称")
            return
        }
        // 先关闭页面再保存；页面已经不在栈顶（重复点击、退出动画中）就忽略
        val screen = Screen.Edit(id)
        if (_stack.value.lastOrNull() != screen) return
        back()
        viewModelScope.launch {
            if (id == null) {
                repository.addItem(draft)
                say("已添加「${draft.name.trim()}」")
            } else {
                repository.updateSettings(id, draft)
                say("已保存")
            }
        }
    }

    fun delete(id: String, name: String) {
        viewModelScope.launch {
            repository.softDelete(id)
            // 详情页发现物品不在列表里后会自动返回
            say("已不再记录「$name」", "撤销") {
                viewModelScope.launch { repository.undoDelete(id) }
            }
        }
    }

    fun addSamples() {
        viewModelScope.launch { repository.addSamples() }
    }

    fun clearSamples() {
        viewModelScope.launch {
            repository.clearSamples()
            say("已清除示例物品")
        }
    }

    suspend fun loadProduct(id: String) = repository.getProduct(id)

    // ---------- 每日提醒 ----------

    private val reminderPrefs = ReminderPrefs(application)
    private val _reminder = MutableStateFlow(readReminder())
    val reminder: StateFlow<ReminderUiState> = _reminder.asStateFlow()

    private fun readReminder() = ReminderUiState(reminderPrefs.enabled, reminderPrefs.hour, reminderPrefs.minute)

    fun setReminderEnabled(enabled: Boolean) {
        reminderPrefs.enabled = enabled
        _reminder.value = readReminder()
        DailyReminderWorker.schedule(getApplication(), ExistingWorkPolicy.REPLACE)
    }

    fun setReminderTime(hour: Int, minute: Int) {
        reminderPrefs.hour = hour
        reminderPrefs.minute = minute
        _reminder.value = readReminder()
        DailyReminderWorker.schedule(getApplication(), ExistingWorkPolicy.REPLACE)
        say("每天 ${_reminder.value.timeText} 提醒你")
    }

    // ---------- 数据与备份 ----------

    private val prefState = MutableStateFlow(readPrefs())
    private val busy = MutableStateFlow(false)

    val backupState: StateFlow<BackupUiState> = combine(
        prefState,
        repository.observeProductCount(),
        repository.observeLogCount(),
        repository.observeSampleCount(),
        busy
    ) { p, products, logs, samples, isBusy ->
        BackupUiState(
            autoEnabled = p.autoEnabled,
            folderLabel = p.folderLabel,
            lastBackupAt = p.lastBackupAt,
            productCount = products,
            logCount = logs,
            sampleCount = samples,
            busy = isBusy,
            canUndoRestore = p.canUndo
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    private val _pendingRestore = MutableStateFlow<BackupPreview?>(null)
    val pendingRestore: StateFlow<BackupPreview?> = _pendingRestore.asStateFlow()

    private fun readPrefs() = PrefSnapshot(
        autoEnabled = prefs.autoBackupEnabled,
        folderLabel = prefs.folderLabel,
        lastBackupAt = prefs.lastBackupAt,
        canUndo = prefs.lastRestoreSnapshot != null
    )

    private fun refreshPrefs() { prefState.value = readPrefs() }

    fun suggestedBackupName() = backup.suggestedFileName()
    fun suggestedCsvName() = backup.suggestedCsvName()

    fun setAutoBackup(enabled: Boolean) {
        prefs.autoBackupEnabled = enabled
        refreshPrefs()
    }

    fun setBackupFolder(uri: Uri) {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.folderUri = uri
        prefs.folderLabel = runCatching {
            DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifBlank { "已选择的文件夹" }
        }.getOrDefault("已选择的文件夹")
        refreshPrefs()
        backupNow()
    }

    fun backupNow() = runBusy {
        val ok = backup.autoBackup()
        say(if (ok) "已备份" else "备份失败，请检查备份位置")
    }

    fun exportTo(uri: Uri) = runBusy {
        backup.exportTo(uri)
        say("备份文件已保存")
    }

    fun exportCsvTo(uri: Uri) = runBusy {
        backup.exportCsvTo(uri)
        say("已导出表格")
    }

    fun pickRestoreFile(uri: Uri) = runBusy {
        _pendingRestore.value = backup.readPreview(uri)
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    fun confirmRestore(mode: RestoreMode) {
        val preview = _pendingRestore.value ?: return
        _pendingRestore.value = null
        runBusy {
            val result = backup.restore(preview.data, mode)
            val text = when (mode) {
                RestoreMode.REPLACE -> "已用备份覆盖当前数据"
                RestoreMode.MERGE -> "已恢复：新增 ${result.added} 件，更新 ${result.updated} 件"
            }
            say(text, "撤回") { undoRestore() }
        }
    }

    fun undoRestore() = runBusy {
        val path = prefs.lastRestoreSnapshot ?: throw BackupException("没有可以撤回的恢复")
        val snapshot = backup.readSnapshot(path)
        backup.restore(snapshot.data, RestoreMode.REPLACE, takeSnapshot = false)
        prefs.lastRestoreSnapshot = null
        say("已撤回，数据回到恢复之前")
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } catch (e: BackupException) {
                say(e.message ?: "操作失败")
            } catch (e: Exception) {
                say("操作失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy.value = false
                refreshPrefs()
            }
        }
    }
}
