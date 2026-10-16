package ian.dev.zaizai.data

import android.content.Context
import ian.dev.zaizai.sync.Sync
import ian.dev.zaizai.widget.BoardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class BoardState(
    val config: Config,
    /** 服务器快照叠上待上传的操作；从没同步过为 null。 */
    val board: Board?,
    /** 还没传上去的实例键。 */
    val queued: Set<String>,
    val syncedAt: Long?,
    /** 服务器时间 − 本机时间。 */
    val clockOffset: Long,
    val offline: Boolean,
    val syncing: Boolean,
    /** 需要家长处理的问题（口令错、地址错），正常为 null。 */
    val problem: String?,
    /** 首页大卡片，见 [BoardLogic.homeCards]。 */
    val cards: List<HomeCard> = emptyList(),
)

enum class SyncResult { OK, OFFLINE, FAILED, NOT_CONFIGURED }

/**
 * 看板的唯一入口：App 界面、小组件、后台同步共用一份状态。
 * 登记先写本机队列并立刻显示（乐观更新），再尽快上传；上传失败留在队列里，联网后由 WorkManager 补传。
 */
class BoardRepository(private val app: Context, private val store: LocalStore = LocalStore(app)) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncLock = Mutex()
    private val queueLock = Mutex()

    @Volatile private var snapshot: Snapshot? = store.readSnapshot()
    @Volatile private var queue: List<PendingOp> = store.readQueue()
    @Volatile private var home: Home? = store.readHome()
    private val imageDir = File(app.filesDir, "images")

    private val _state = MutableStateFlow(
        build(store.readConfig(), offline = false, syncing = false, problem = null),
    )
    val state: StateFlow<BoardState> = _state.asStateFlow()

    fun serverNow(): Long = System.currentTimeMillis() + (snapshot?.clockOffset ?: 0)

    fun saveConfig(config: Config) {
        val old = _state.value.config
        store.writeConfig(config)
        // 换了服务器，旧缓存和没传完的登记都不属于新服务器了
        if (ApiClient.normalizeServer(old.server) != ApiClient.normalizeServer(config.server)) {
            snapshot = null
            home = null
            store.clearSnapshot()
            queue = emptyList()
            store.writeQueue(queue)
        }
        publish { build(config, offline = false, syncing = it.syncing, problem = null) }
        Sync.schedulePeriodic(app)
    }

    /** 先补传队列，再拉最新看板。 */
    suspend fun sync(): SyncResult = syncLock.withLock {
        val config = _state.value.config
        val server = ApiClient.normalizeServer(config.server)
        if (!config.isComplete || server == null) return SyncResult.NOT_CONFIGURED
        publish { it.copy(syncing = true) }
        val api = ApiClient(server, config.familyKey, config.recorder)
        try {
            upload(api)
            val fetched = api.board()
            val offset = if (fetched.board.serverTime > 0) fetched.board.serverTime - fetched.receivedAt else 0L
            store.writeSnapshot(fetched.raw, fetched.receivedAt, offset)
            snapshot = Snapshot(fetched.board, fetched.receivedAt, offset)
            val homeProblem = refreshHome(api)
            publish { it.copy(offline = false, syncing = false, problem = homeProblem) }
            SyncResult.OK
        } catch (e: IOException) {
            publish { it.copy(offline = true, syncing = false) }
            SyncResult.OFFLINE
        } catch (e: ApiException) {
            val message = if (e.status == 401) "家庭口令不对，请让家长在设置里重新填写" else e.message
            publish { it.copy(offline = false, syncing = false, problem = message) }
            SyncResult.FAILED
        } finally {
            // 被取消（App 切到后台）或出了没料到的错时，别让“同步中”一直挂着，刷新键会一直转圈
            if (_state.value.syncing) publish { it.copy(syncing = false) }
        }
    }

    /** 本机缓存的图片文件；还没下载好返回 null。 */
    fun imageFile(image: DayImage): File? = File(imageDir, image.id).takeIf { it.isFile }

    /**
     * 拉首页卡片并缓存图片。服务器出错时沿用上次的首页，不影响看板，但把原因返回给状态栏显示，
     * 免得卡片没更新却看不出为什么；断网照常抛出。
     */
    private suspend fun refreshHome(api: ApiClient): String? {
        val fetched = try {
            api.home()
        } catch (e: ApiException) {
            return "首页卡片没更新：${e.message}"
        }
        home = fetched?.first
        store.writeHome(fetched?.second)
        publish { it } // 先把新卡片显示出来，图片接着下载
        cacheImages(api, fetched?.first)
        return null
    }

    /** 下载首页要用的图片，删掉用不上的（过期或被替换的）。下载失败不影响看板，下次同步再试。 */
    private suspend fun cacheImages(api: ApiClient, current: Home?) {
        val wanted = current?.cards.orEmpty().mapNotNull { it.image }
        withContext(Dispatchers.IO) {
            imageDir.listFiles()?.filter { f -> wanted.none { it.id == f.name } }?.forEach { it.delete() }
        }
        for (image in wanted) {
            if (imageFile(image) != null) continue
            try {
                val bytes = api.download(image.url)
                withContext(Dispatchers.IO) {
                    imageDir.mkdirs()
                    val tmp = File(imageDir, "${image.id}.part")
                    tmp.writeBytes(bytes)
                    tmp.renameTo(File(imageDir, image.id))
                }
            } catch (e: ApiException) {
                // 图片被删了或替换了，等下次同步拿新的
            } catch (e: IOException) {
                // 网断了，等下次同步
            }
        }
    }

    private suspend fun upload(api: ApiClient) {
        while (true) {
            val op = queueLock.withLock { queue.firstOrNull() } ?: return
            try {
                api.send(op)
            } catch (e: ApiException) {
                // 4xx（事项已暂停、超过撤销时间等）重发也不会成功，丢掉；口令错和服务器错留着下次再传
                if (e.status == 401 || e.status !in 400..499) throw e
            }
            queueLock.withLock {
                queue = queue.filterNot { it.id == op.id }
                store.writeQueue(queue)
            }
        }
    }

    fun checkin(item: BoardItem, source: String = "app") = mutate(item, OpType.CHECKIN, source, BoardLogic::enqueueCheckin)

    fun undo(item: BoardItem) = mutate(item, OpType.UNDO, "app", BoardLogic::enqueueUndo)

    private fun mutate(
        item: BoardItem,
        type: OpType,
        source: String,
        enqueue: (List<PendingOp>, PendingOp) -> List<PendingOp>,
    ) {
        val date = _state.value.board?.date ?: return
        val op = PendingOp(UUID.randomUUID().toString(), type, item.taskId, date, item.slot, source, serverNow())
        scope.launch {
            queueLock.withLock {
                queue = enqueue(queue, op)
                store.writeQueue(queue)
            }
            publish { it }
            if (sync() != SyncResult.OK) Sync.requestUpload(app)
        }
    }

    /** 重新叠加快照和队列，发布新状态并刷新小组件。 */
    @Synchronized
    private fun publish(change: (BoardState) -> BoardState) {
        val next = change(_state.value).let {
            build(it.config, it.offline, it.syncing, it.problem)
        }
        _state.value = next
        BoardWidget.render(app, next)
    }

    private fun build(config: Config, offline: Boolean, syncing: Boolean, problem: String?): BoardState {
        val snap = snapshot
        val ops = queue
        val board = snap?.board?.let { BoardLogic.applyPending(it, ops, config.recorder) }
        return BoardState(
            config = config,
            board = board,
            queued = ops.map { it.key }.toSet(),
            syncedAt = snap?.syncedAt,
            clockOffset = snap?.clockOffset ?: 0,
            offline = offline,
            syncing = syncing,
            problem = problem,
            cards = BoardLogic.homeCards(home, board),
        )
    }
}
