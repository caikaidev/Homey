package ian.dev.zaizai.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

data class Config(
    val server: String = "",
    val familyKey: String = "",
    /** 我是谁：登记时的记录人。 */
    val recorder: String = "",
    /** 打开 App 时语音播报。 */
    val speak: Boolean = true,
) {
    val isComplete get() = server.isNotBlank() && familyKey.isNotBlank() && recorder.isNotBlank()
}

/** 最近一次同步到的看板。服务端是唯一真相，这里只是离线时能看的缓存。 */
data class Snapshot(val board: Board, val syncedAt: Long, val clockOffset: Long)

/**
 * App 私有存储（SharedPreferences）：设置、看板快照、待上传队列、已提醒过的实例。
 * 队列用 commit() 同步写盘，点了确认马上杀进程也不会丢。
 */
class LocalStore(context: Context) {
    private val config = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    private val cache = context.getSharedPreferences("cache", Context.MODE_PRIVATE)

    fun readConfig() = Config(
        server = config.getString("server", "").orEmpty(),
        familyKey = config.getString("family_key", "").orEmpty(),
        recorder = config.getString("recorder", "").orEmpty(),
        speak = config.getBoolean("speak", true),
    )

    fun writeConfig(c: Config) = config.edit(commit = true) {
        putString("server", c.server)
        putString("family_key", c.familyKey)
        putString("recorder", c.recorder)
        putBoolean("speak", c.speak)
    }

    fun readSnapshot(): Snapshot? {
        val raw = cache.getString("board", null) ?: return null
        val board = runCatching { Board.parse(JSONObject(raw)) }.getOrNull() ?: return null
        return Snapshot(board, cache.getLong("synced_at", 0), cache.getLong("clock_offset", 0))
    }

    fun writeSnapshot(raw: String, syncedAt: Long, clockOffset: Long) = cache.edit(commit = true) {
        putString("board", raw)
        putLong("synced_at", syncedAt)
        putLong("clock_offset", clockOffset)
    }

    fun clearSnapshot() = cache.edit(commit = true) { remove("board"); remove("synced_at") }

    fun readQueue(): List<PendingOp> = PendingOp.parseList(cache.getString("queue", null))

    fun writeQueue(ops: List<PendingOp>) = cache.edit(commit = true) { putString("queue", PendingOp.toJson(ops)) }

    fun readNotified(): Set<String> = cache.getStringSet("notified", emptySet()).orEmpty().toSet()

    fun writeNotified(keys: Set<String>) = cache.edit { putStringSet("notified", keys) }
}
