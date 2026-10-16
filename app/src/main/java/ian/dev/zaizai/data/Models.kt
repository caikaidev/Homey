package ian.dev.zaizai.data

import org.json.JSONArray
import org.json.JSONObject

/** 一个实例（某事项某天某时间点）的唯一键，也是登记的幂等键。 */
fun instanceKey(taskId: String, date: String, slot: String) = "$taskId|$date|$slot"

enum class ItemStatus(val wire: String) {
    PENDING("pending"), DONE("done"), SKIPPED("skipped");

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire } ?: PENDING
    }
}

data class Completion(
    val result: String,
    val reason: String,
    val recordedBy: String,
    /** 服务器时间，毫秒。 */
    val recordedAt: Long,
    val source: String,
)

data class BoardItem(
    val taskId: String,
    val title: String,
    val kind: String,
    val note: String,
    val priority: Int,
    val slot: String,
    val status: ItemStatus,
    val completion: Completion?,
)

data class TomorrowItem(val taskId: String, val title: String, val slot: String, val kind: String = "supplement")

/** `GET /api/board` 的内容，字段见 docs/API.md。 */
data class Board(
    val date: String,
    val weekday: String,
    val nickname: String,
    val today: List<BoardItem>,
    val tomorrowDate: String,
    val tomorrow: List<TomorrowItem>,
    val serverTime: Long,
) {
    val pendingCount get() = today.count { it.status == ItemStatus.PENDING }

    fun key(item: BoardItem) = instanceKey(item.taskId, date, item.slot)

    companion object {
        fun parse(json: JSONObject): Board {
            val tomorrow = json.optJSONObject("tomorrow")
            return Board(
                date = json.getString("date"),
                weekday = json.optString("weekday"),
                nickname = json.optString("nickname").ifBlank { "崽崽" },
                today = json.optJSONArray("today").objects().map(::parseItem),
                tomorrowDate = tomorrow?.optString("date").orEmpty(),
                tomorrow = tomorrow?.optJSONArray("items").objects().map {
                    TomorrowItem(it.getString("task_id"), it.getString("title"), it.getString("slot"), it.optString("kind", "supplement"))
                },
                serverTime = json.optLong("serverTime"),
            )
        }

        private fun parseItem(o: JSONObject): BoardItem {
            val c = o.optJSONObject("completion")
            return BoardItem(
                taskId = o.getString("task_id"),
                title = o.getString("title"),
                kind = o.optString("kind", "supplement"),
                note = o.optString("note"),
                priority = o.optInt("priority"),
                slot = o.getString("slot"),
                status = ItemStatus.of(o.optString("status")),
                completion = c?.let {
                    Completion(
                        result = it.optString("result"),
                        reason = it.optString("reason"),
                        recordedBy = it.optString("recorded_by"),
                        recordedAt = it.optLong("recorded_at"),
                        source = it.optString("source"),
                    )
                },
            )
        }
    }
}

/** 只看某一类（补剂、药品）的看板：首页点进某张卡片时用。 */
fun Board.ofKind(kind: String): Board =
    copy(today = today.filter { it.kind == kind }, tomorrow = tomorrow.filter { it.kind == kind })

/** 按日期使用的图片（宝宝屋），`url` 是相对服务器的路径。 */
data class DayImage(val id: String, val useDate: String, val url: String)

enum class CardType { TASKS, IMAGE }

/** App 首页的一张大卡片。事项类卡片的 total / pending 由看板现算，离线登记也立刻反映。 */
data class HomeCard(
    val key: String,
    val type: CardType,
    val title: String,
    val total: Int = 0,
    val pending: Int = 0,
    val image: DayImage? = null,
)

/** `GET /api/home`：服务器说今天有哪些类别要显示。 */
data class Home(val date: String, val cards: List<HomeCard>) {
    companion object {
        fun parse(json: JSONObject) = Home(
            date = json.getString("date"),
            cards = json.optJSONArray("cards").objects().mapNotNull { o ->
                val type = when (o.optString("type")) {
                    "tasks" -> CardType.TASKS
                    "image" -> CardType.IMAGE
                    else -> return@mapNotNull null // 新版服务器加的类别，旧 App 先不显示
                }
                val img = o.optJSONObject("image")
                HomeCard(
                    key = o.getString("key"),
                    type = type,
                    title = o.optString("title").ifBlank { o.getString("key") },
                    total = o.optInt("total"),
                    pending = o.optInt("pending"),
                    image = img?.let { DayImage(it.getString("id"), it.getString("use_date"), it.getString("url")) },
                )
            }.filter { it.type != CardType.IMAGE || it.image != null },
        )
    }
}

enum class OpType { CHECKIN, UNDO }

/** 待上传的登记或撤销。离线时排队，联网后按顺序补传；服务端幂等，重发不会多记。 */
data class PendingOp(
    val id: String,
    val type: OpType,
    val taskId: String,
    val date: String,
    val slot: String,
    val source: String,
    /** 点按时的服务器时间估计，用于本地显示。 */
    val at: Long,
) {
    val key get() = instanceKey(taskId, date, slot)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type.name).put("task_id", taskId)
        .put("date", date).put("slot", slot).put("source", source).put("at", at)

    companion object {
        fun parse(o: JSONObject) = PendingOp(
            id = o.getString("id"),
            type = OpType.valueOf(o.getString("type")),
            taskId = o.getString("task_id"),
            date = o.getString("date"),
            slot = o.getString("slot"),
            source = o.optString("source", "app"),
            at = o.optLong("at"),
        )

        fun parseList(raw: String?): List<PendingOp> =
            if (raw.isNullOrBlank()) emptyList()
            else runCatching { JSONArray(raw).objects().map(::parse) }.getOrDefault(emptyList())

        fun toJson(ops: List<PendingOp>): String = JSONArray().apply { ops.forEach { put(it.toJson()) } }.toString()
    }
}

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
