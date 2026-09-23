package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.model.Inventory
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.StockLog
import com.example.data.model.StockLogType
import com.example.data.model.TrackingMode
import com.example.data.model.mode
import com.example.domain.prediction.ItemStatus
import com.example.domain.prediction.PredictionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 新建/编辑物品时界面提交的数据。 */
data class ItemDraft(
    val name: String,
    val category: ProductCategory = ProductCategory.OTHER,
    val mode: TrackingMode = TrackingMode.COUNT,
    val unit: String = "件",
    val quantity: Double = 1.0,
    val dailyUsage: Double = 1.0,
    val levelPercent: Int = 100,
    val lifeDays: Int = 30,
    val expiresInDays: Int = 7,
    val remindDaysAhead: Int = 3,
    val restockAmount: Double = 1.0,
    val location: String = "",
    val note: String = ""
)

/**
 * 所有写操作都在事务里完成，并且：
 * - 每次库存变化都写一条 stock_logs 流水；
 * - 每次变化都刷新 product.updatedAt（合并备份时以它判断哪边更新）。
 */
class HomeyRepository(private val db: AppDatabase) {

    private val products = db.productDao()
    private val inventories = db.inventoryDao()
    private val logs = db.stockLogDao()

    fun observeItems(nowProvider: () -> Long = { System.currentTimeMillis() }): Flow<List<ItemStatus>> =
        combine(products.observeActive(), inventories.observeAll()) { ps, invs ->
            val byProduct = invs.groupBy { it.productId }
            val now = nowProvider()
            ps.map { PredictionEngine.evaluate(it, byProduct[it.id].orEmpty(), now) }
        }

    fun observeProductCount(): Flow<Int> = products.observeActiveCount()
    fun observeSampleCount(): Flow<Int> = products.observeSampleCount()
    fun observeLogCount(): Flow<Int> = logs.observeCount()

    suspend fun getProduct(id: String): Product? = products.getById(id)

    suspend fun addItem(draft: ItemDraft, isSample: Boolean = false, now: Long = System.currentTimeMillis()): String =
        db.withTransaction {
            val product = Product(
                name = draft.name.trim(),
                category = draft.category.name,
                trackingMode = draft.mode.name,
                unit = draft.unit.ifBlank { "件" }.trim(),
                dailyUsage = draft.dailyUsage.coerceAtLeast(0.0),
                lifeDays = draft.lifeDays.coerceAtLeast(1),
                remindDaysAhead = draft.remindDaysAhead.coerceAtLeast(0),
                restockAmount = draft.restockAmount.coerceAtLeast(0.0),
                location = draft.location.trim(),
                note = draft.note.trim(),
                isSample = isSample,
                createdAt = now,
                updatedAt = now
            )
            products.upsert(product)
            val inventory = Inventory(
                productId = product.id,
                quantity = if (draft.mode == TrackingMode.LEVEL) 1.0 else draft.quantity.coerceAtLeast(0.0),
                levelPercent = if (draft.mode == TrackingMode.LEVEL) draft.levelPercent.coerceIn(0, 100) else 100,
                expiresAt = if (draft.mode == TrackingMode.EXPIRY) expiryFromNow(now, draft.expiresInDays) else null,
                createdAt = now,
                updatedAt = now
            )
            inventories.upsert(inventory)
            logs.insert(
                StockLog(
                    productId = product.id,
                    type = StockLogType.CREATE.name,
                    delta = inventory.quantity,
                    levelPercent = if (draft.mode == TrackingMode.LEVEL) inventory.levelPercent else null,
                    createdAt = now
                )
            )
            product.id
        }

    /** 编辑物品的设置项（名称、分类、用量等），不改库存。 */
    suspend fun updateSettings(id: String, draft: ItemDraft, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val old = products.getById(id) ?: return@withTransaction
            products.upsert(
                old.copy(
                    name = draft.name.trim(),
                    category = draft.category.name,
                    trackingMode = draft.mode.name,
                    unit = draft.unit.ifBlank { "件" }.trim(),
                    dailyUsage = draft.dailyUsage.coerceAtLeast(0.0),
                    lifeDays = draft.lifeDays.coerceAtLeast(1),
                    remindDaysAhead = draft.remindDaysAhead.coerceAtLeast(0),
                    restockAmount = draft.restockAmount.coerceAtLeast(0.0),
                    location = draft.location.trim(),
                    note = draft.note.trim(),
                    updatedAt = now
                )
            )
        }
    }

    /** 手动加减数量。看保质期的物品减少时，优先从最早到期的那批扣。 */
    suspend fun adjustQuantity(id: String, delta: Double, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val product = products.getById(id) ?: return@withTransaction
            val batches = inventories.getByProduct(id)
            var applied = 0.0
            if (product.mode == TrackingMode.EXPIRY && delta < 0) {
                var remaining = -delta
                val ordered = batches.filter { it.quantity > 0 }.sortedBy { it.expiresAt ?: Long.MAX_VALUE }
                for (batch in ordered) {
                    if (remaining <= 0) break
                    val take = minOf(batch.quantity, remaining)
                    inventories.upsert(batch.copy(quantity = batch.quantity - take, updatedAt = now))
                    remaining -= take
                    applied -= take
                }
            } else {
                val primary = primaryBatch(product, batches, now)
                val newQty = (primary.quantity + delta).coerceAtLeast(0.0)
                applied = newQty - primary.quantity
                inventories.upsert(primary.copy(quantity = newQty, updatedAt = now))
            }
            if (applied != 0.0) {
                logs.insert(StockLog(productId = id, type = StockLogType.ADJUST.name, delta = applied, createdAt = now))
                products.upsert(product.copy(updatedAt = now))
            }
        }
    }

    suspend fun setLevel(id: String, level: Int, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val product = products.getById(id) ?: return@withTransaction
            val primary = primaryBatch(product, inventories.getByProduct(id), now)
            val value = level.coerceIn(0, 100)
            inventories.upsert(primary.copy(levelPercent = value, updatedAt = now))
            logs.insert(StockLog(productId = id, type = StockLogType.LEVEL.name, levelPercent = value, createdAt = now))
            products.upsert(product.copy(updatedAt = now))
        }
    }

    /** 买到了：按物品的"默认补货量"入库。 */
    suspend fun restock(id: String, amount: Double? = null, now: Long = System.currentTimeMillis()) {
        db.withTransaction { restockInternal(id, amount, now) }
    }

    suspend fun restockMany(ids: Collection<String>, now: Long = System.currentTimeMillis()) {
        db.withTransaction { ids.forEach { restockInternal(it, null, now) } }
    }

    private suspend fun restockInternal(id: String, amount: Double?, now: Long) {
        val product = products.getById(id) ?: return
        val batches = inventories.getByProduct(id)
        val add = (amount ?: product.restockAmount).coerceAtLeast(0.0)
        when (product.mode) {
            TrackingMode.COUNT -> {
                val primary = primaryBatch(product, batches, now)
                inventories.upsert(primary.copy(quantity = primary.quantity + add, updatedAt = now))
                logs.insert(StockLog(productId = id, type = StockLogType.RESTOCK.name, delta = add, createdAt = now))
            }
            TrackingMode.LEVEL -> {
                val primary = primaryBatch(product, batches, now)
                inventories.upsert(primary.copy(levelPercent = 100, openedAt = null, updatedAt = now))
                logs.insert(StockLog(productId = id, type = StockLogType.RESTOCK.name, delta = 1.0, levelPercent = 100, createdAt = now))
            }
            TrackingMode.EXPIRY -> {
                // 吃完的批次清掉，新买的作为新一批，带自己的到期日
                batches.filter { it.quantity <= 0 }.forEach { inventories.deleteById(it.id) }
                inventories.upsert(
                    Inventory(
                        productId = id,
                        quantity = add,
                        expiresAt = expiryFromNow(now, product.lifeDays),
                        createdAt = now,
                        updatedAt = now
                    )
                )
                logs.insert(StockLog(productId = id, type = StockLogType.RESTOCK.name, delta = add, createdAt = now))
            }
        }
        products.upsert(product.copy(updatedAt = now))
    }

    /** 软删除：界面上消失，但数据还在，备份合并时也不会被"复活"。 */
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val product = products.getById(id) ?: return@withTransaction
            products.upsert(product.copy(deletedAt = now, updatedAt = now))
        }
    }

    suspend fun undoDelete(id: String, now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            val product = products.getById(id) ?: return@withTransaction
            products.upsert(product.copy(deletedAt = null, updatedAt = now))
        }
    }

    suspend fun addSamples(now: Long = System.currentTimeMillis()) {
        db.withTransaction {
            SampleData.drafts.forEachIndexed { index, draft ->
                // 让示例按列表顺序排列（最新的在前）
                addItem(draft, isSample = true, now = now - index)
            }
        }
    }

    suspend fun clearSamples() {
        db.withTransaction { products.deleteSamples() }
    }

    private suspend fun primaryBatch(product: Product, batches: List<Inventory>, now: Long): Inventory {
        val existing = when (product.mode) {
            TrackingMode.EXPIRY -> batches.filter { it.quantity > 0 }.minByOrNull { it.expiresAt ?: Long.MAX_VALUE }
                ?: batches.minByOrNull { it.createdAt }
            else -> batches.minByOrNull { it.createdAt }
        }
        if (existing != null) return existing
        val created = Inventory(
            productId = product.id,
            quantity = 0.0,
            levelPercent = if (product.mode == TrackingMode.LEVEL) 0 else 100,
            expiresAt = if (product.mode == TrackingMode.EXPIRY) expiryFromNow(now, product.lifeDays) else null,
            createdAt = now,
            updatedAt = now
        )
        inventories.upsert(created)
        return created
    }

    private fun expiryFromNow(now: Long, days: Int): Long =
        PredictionEngine.startOfDay(now) + days.coerceAtLeast(0) * PredictionEngine.DAY_MS
}

object SampleData {
    val drafts = listOf(
        ItemDraft(
            name = "花王妙而舒纸尿裤 L码", category = ProductCategory.BABY, mode = TrackingMode.COUNT,
            unit = "片", quantity = 12.0, dailyUsage = 8.0, remindDaysAhead = 3, restockAmount = 64.0, location = "主卧衣柜"
        ),
        ItemDraft(
            name = "贝亲婴儿柔湿巾", category = ProductCategory.BABY, mode = TrackingMode.COUNT,
            unit = "包", quantity = 1.0, dailyUsage = 0.5, remindDaysAhead = 3, restockAmount = 3.0
        ),
        ItemDraft(
            name = "鲜牛奶 950ml", category = ProductCategory.FOOD, mode = TrackingMode.EXPIRY,
            unit = "盒", quantity = 1.0, lifeDays = 7, expiresInDays = 1, restockAmount = 2.0, location = "冷藏室"
        ),
        ItemDraft(
            name = "洗洁精", category = ProductCategory.HOUSEHOLD, mode = TrackingMode.LEVEL,
            unit = "瓶", levelPercent = 25, lifeDays = 40, remindDaysAhead = 3
        ),
        ItemDraft(
            name = "蓝莓 125g", category = ProductCategory.FOOD, mode = TrackingMode.EXPIRY,
            unit = "盒", quantity = 2.0, lifeDays = 5, expiresInDays = 2, restockAmount = 2.0, location = "冷藏室"
        ),
        ItemDraft(
            name = "狮王儿童防蛀牙膏", category = ProductCategory.BABY, mode = TrackingMode.LEVEL,
            unit = "支", levelPercent = 25, lifeDays = 60, remindDaysAhead = 3
        ),
        ItemDraft(
            name = "抽纸", category = ProductCategory.HOUSEHOLD, mode = TrackingMode.COUNT,
            unit = "包", quantity = 9.0, dailyUsage = 0.5, remindDaysAhead = 3, restockAmount = 12.0
        )
    )
}
