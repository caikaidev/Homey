package ian.dev.homey

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ian.dev.homey.data.database.AppDatabase
import ian.dev.homey.data.model.Inventory
import ian.dev.homey.data.model.StockLogType
import ian.dev.homey.data.model.TrackingMode
import ian.dev.homey.data.repository.HomeyRepository
import ian.dev.homey.data.repository.ItemDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: HomeyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = HomeyRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun total(id: String) = runBlocking { db.inventoryDao().getByProduct(id).sumOf { it.quantity } }

    @Test
    fun setQuantity_isIdempotent_andLogsTheDifference() = runBlocking {
        val id = repo.addItem(ItemDraft(name = "纸尿裤", mode = TrackingMode.COUNT, quantity = 12.0))
        repo.setQuantity(id, 200.0)
        repo.setQuantity(id, 200.0) // 重复提交（比如失焦又按了完成）不应再加一次
        assertEquals(200.0, total(id), 0.001)
        val adjusts = db.stockLogDao().getAll().filter { it.type == StockLogType.ADJUST.name }
        assertEquals(1, adjusts.size)
        assertEquals(188.0, adjusts.single().delta, 0.001)
    }

    @Test
    fun setQuantity_lowerTarget_consumesAcrossBatches() = runBlocking {
        val id = repo.addItem(ItemDraft(name = "纸尿裤", mode = TrackingMode.COUNT, quantity = 10.0), now = 1000)
        // 模拟 v1 迁移过来的第二个批次
        db.inventoryDao().upsert(Inventory(productId = id, quantity = 5.0, createdAt = 2000, updatedAt = 2000))
        repo.setQuantity(id, 3.0)
        assertEquals(3.0, total(id), 0.001)
    }

    @Test
    fun stepAfterTypedValue_appliesOnLatestStock() = runBlocking {
        val id = repo.addItem(ItemDraft(name = "湿巾", mode = TrackingMode.COUNT, quantity = 1.0))
        repo.setQuantity(id, 30.0)
        repo.adjustQuantity(id, 1.0)
        assertEquals(31.0, total(id), 0.001)
    }
}
