package ian.dev.homey.data.backup

import ian.dev.homey.data.model.Inventory
import ian.dev.homey.data.model.Product
import ian.dev.homey.data.model.ProductCategory
import ian.dev.homey.data.model.StockLog
import ian.dev.homey.data.model.StockLogType
import ian.dev.homey.data.model.Todo
import ian.dev.homey.data.model.TodoStatus
import ian.dev.homey.data.model.TrackingMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class BackupData(
    val formatVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val products: List<Product>,
    val inventories: List<Inventory>,
    val stockLogs: List<StockLog>,
    val todos: List<Todo>
)

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 备份文件格式（JSON）。约定：
 * - `format` 固定为 "homey-backup"，用来识别文件；
 * - `formatVersion` 只在格式有不兼容变化时 +1，并在 [upgrade] 里补上旧版本 → 新版本的转换；
 * - 读取时忽略不认识的字段、给缺失字段默认值，这样新旧版本 App 都能尽量读懂对方的文件；
 * - 枚举一律存 name，读取时经过 fromCode 做兼容映射。
 */
object BackupCodec {
    const val FORMAT = "homey-backup"
    const val FORMAT_VERSION = 1

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", data.exportedAt)
        root.put("appVersion", data.appVersion)
        root.put("products", JSONArray().apply { data.products.forEach { put(productToJson(it)) } })
        root.put("inventories", JSONArray().apply { data.inventories.forEach { put(inventoryToJson(it)) } })
        root.put("stockLogs", JSONArray().apply { data.stockLogs.forEach { put(logToJson(it)) } })
        root.put("todos", JSONArray().apply { data.todos.forEach { put(todoToJson(it)) } })
        return root.toString(2)
    }

    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException("这不是有效的备份文件", e)
        }
        if (root.optString("format") != FORMAT) throw BackupException("这不是 Homey 的备份文件")
        val version = root.optInt("formatVersion", 1)
        if (version > FORMAT_VERSION) throw BackupException("备份来自更新版本的 App，请先升级再恢复")
        val upgraded = upgrade(root, version)
        return try {
            BackupData(
                formatVersion = FORMAT_VERSION,
                exportedAt = upgraded.optLong("exportedAt", 0L),
                appVersion = upgraded.optString("appVersion", ""),
                products = upgraded.objects("products").map(::productFromJson),
                inventories = upgraded.objects("inventories").map(::inventoryFromJson),
                stockLogs = upgraded.objects("stockLogs").map(::logFromJson),
                todos = upgraded.objects("todos").map(::todoFromJson)
            )
        } catch (e: JSONException) {
            throw BackupException("备份文件内容不完整", e)
        }
    }

    /** 以后格式升级时，在这里逐级把旧结构转换成当前结构。 */
    private fun upgrade(root: JSONObject, fromVersion: Int): JSONObject {
        var current = root
        var v = fromVersion
        while (v < FORMAT_VERSION) {
            current = when (v) {
                // 1 -> { ...转换成 2 的结构...; }
                else -> current
            }
            v++
        }
        return current
    }

    // ---- Product ----
    private fun productToJson(p: Product) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("category", p.category); put("trackingMode", p.trackingMode)
        put("unit", p.unit); put("dailyUsage", p.dailyUsage); put("lifeDays", p.lifeDays)
        put("remindDaysAhead", p.remindDaysAhead); put("restockAmount", p.restockAmount)
        put("location", p.location); put("note", p.note); put("barcode", p.barcode); put("isSample", p.isSample)
        put("createdAt", p.createdAt); put("updatedAt", p.updatedAt); putNullable("deletedAt", p.deletedAt)
    }

    private fun productFromJson(o: JSONObject) = Product(
        id = o.getString("id"),
        name = o.getString("name"),
        category = ProductCategory.fromCode(o.optString("category")).name,
        trackingMode = TrackingMode.fromCode(o.optString("trackingMode")).name,
        unit = o.optString("unit", "件"),
        dailyUsage = o.optDouble("dailyUsage", 1.0).finiteOr(1.0),
        lifeDays = o.optInt("lifeDays", 30),
        remindDaysAhead = o.optInt("remindDaysAhead", 3),
        restockAmount = o.optDouble("restockAmount", 1.0).finiteOr(1.0),
        location = o.optString("location", ""),
        note = o.optString("note", ""),
        barcode = o.optString("barcode", ""),
        isSample = o.optBoolean("isSample", false),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", o.optLong("createdAt", 0L)),
        deletedAt = o.nullableLong("deletedAt")
    )

    // ---- Inventory ----
    private fun inventoryToJson(i: Inventory) = JSONObject().apply {
        put("id", i.id); put("productId", i.productId); put("quantity", i.quantity); put("levelPercent", i.levelPercent)
        putNullable("expiresAt", i.expiresAt); putNullable("openedAt", i.openedAt)
        put("createdAt", i.createdAt); put("updatedAt", i.updatedAt)
    }

    private fun inventoryFromJson(o: JSONObject) = Inventory(
        id = o.getString("id"),
        productId = o.getString("productId"),
        quantity = o.optDouble("quantity", 0.0).finiteOr(0.0),
        levelPercent = o.optInt("levelPercent", 100),
        expiresAt = o.nullableLong("expiresAt"),
        openedAt = o.nullableLong("openedAt"),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", o.optLong("createdAt", 0L))
    )

    // ---- StockLog ----
    private fun logToJson(l: StockLog) = JSONObject().apply {
        put("id", l.id); put("productId", l.productId); put("type", l.type); put("delta", l.delta)
        putNullable("levelPercent", l.levelPercent?.toLong()); put("note", l.note); put("createdAt", l.createdAt)
    }

    private fun logFromJson(o: JSONObject) = StockLog(
        id = o.getString("id"),
        productId = o.getString("productId"),
        type = StockLogType.fromCode(o.optString("type")).name,
        delta = o.optDouble("delta", 0.0).finiteOr(0.0),
        levelPercent = o.nullableLong("levelPercent")?.toInt(),
        note = o.optString("note", ""),
        createdAt = o.optLong("createdAt", 0L)
    )

    // ---- Todo ----
    private fun todoToJson(t: Todo) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("category", t.category)
        putNullable("dueAt", t.dueAt); putNullable("completedAt", t.completedAt)
        put("status", t.status); put("note", t.note); put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
        putNullable("deletedAt", t.deletedAt)
    }

    private fun todoFromJson(o: JSONObject) = Todo(
        id = o.getString("id"),
        title = o.getString("title"),
        category = o.optString("category", "家庭事务"),
        dueAt = o.nullableLong("dueAt"),
        completedAt = o.nullableLong("completedAt"),
        status = TodoStatus.fromCode(o.optString("status")).name,
        note = o.optString("note", ""),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", o.optLong("createdAt", 0L)),
        deletedAt = o.nullableLong("deletedAt")
    )

    // ---- helpers ----
    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.putNullable(key: String, value: Long?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun Double.finiteOr(fallback: Double): Double = if (isNaN() || isInfinite()) fallback else this
}
