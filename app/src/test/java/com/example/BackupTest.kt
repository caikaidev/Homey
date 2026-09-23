package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupCodec
import com.example.data.backup.BackupException
import com.example.data.backup.BackupManager
import com.example.data.backup.RestoreMode
import com.example.data.database.AppDatabase
import com.example.data.model.ProductCategory
import com.example.data.model.TrackingMode
import com.example.data.repository.HomeyRepository
import com.example.data.repository.ItemDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: HomeyRepository
    private lateinit var backup: BackupManager

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = HomeyRepository(db)
        backup = BackupManager(context, db)
    }

    @After
    fun tearDown() = db.close()

    private fun draft(name: String) = ItemDraft(name = name, category = ProductCategory.BABY, mode = TrackingMode.COUNT, unit = "片", quantity = 10.0, dailyUsage = 2.0)

    @Test
    fun encodeDecode_roundTrip() = runBlocking {
        repo.addItem(draft("纸尿裤"), now = 1000)
        val original = backup.snapshot(now = 5000)
        val decoded = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original.products, decoded.products)
        assertEquals(original.inventories, decoded.inventories)
        assertEquals(original.stockLogs, decoded.stockLogs)
        assertEquals(5000L, decoded.exportedAt)
    }

    @Test
    fun decode_rejectsForeignAndFutureFiles() {
        assertThrows { BackupCodec.decode("{\"hello\":1}") }
        assertThrows { BackupCodec.decode("{\"format\":\"homey-backup\",\"formatVersion\":99}") }
        assertThrows { BackupCodec.decode("not json") }
    }

    @Test
    fun decode_mapsLegacyValuesAndFillsDefaults() {
        val json = """
            {"format":"homey-backup","formatVersion":1,
             "products":[{"id":"a","name":"湿巾","trackingMode":"PACKAGE","category":"???"}],
             "futureField":{"x":1}}
        """.trimIndent()
        val data = BackupCodec.decode(json)
        val p = data.products.single()
        assertEquals("COUNT", p.trackingMode)
        assertEquals("OTHER", p.category)
        assertNull(p.deletedAt)
    }

    @Test
    fun merge_newerWins_andDeletedStaysDeleted() = runBlocking {
        val keepId = repo.addItem(draft("纸尿裤"), now = 1000)
        val deletedId = repo.addItem(draft("湿巾"), now = 1000)
        val exported = BackupCodec.decode(backup.exportJson())

        // 备份之后：本地改了纸尿裤数量、删了湿巾、新增了奶粉
        repo.adjustQuantity(keepId, -3.0, now = 2000)
        repo.softDelete(deletedId, now = 2000)
        repo.addItem(draft("奶粉"), now = 2000)

        val result = backup.restore(exported, RestoreMode.MERGE)
        assertEquals(0, result.added)
        assertEquals(0, result.updated)
        assertNotNull(result.snapshotPath)

        val products = db.productDao().getAll().associateBy { it.name }
        assertEquals(3, products.size)
        assertNotNull(products.getValue("湿巾").deletedAt)
        val qty = db.inventoryDao().getByProduct(keepId).sumOf { it.quantity }
        assertEquals(7.0, qty, 0.001)
    }

    @Test
    fun merge_intoEmptyPhone_restoresEverything() = runBlocking {
        repo.addItem(draft("纸尿裤"), now = 1000)
        repo.addItem(draft("湿巾"), now = 1000)
        val exported = BackupCodec.decode(backup.exportJson())

        val phone2 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val result = BackupManager(context, phone2).restore(exported, RestoreMode.MERGE)
            assertEquals(2, result.added)
            assertEquals(2, phone2.productDao().getAll().size)
            assertEquals(2, phone2.inventoryDao().getAll().size)
            assertEquals(2, phone2.stockLogDao().getAll().size)
        } finally {
            phone2.close()
        }
    }

    @Test
    fun replace_thenUndoFromSnapshot() = runBlocking {
        repo.addItem(draft("纸尿裤"), now = 1000)
        val before = backup.snapshot()
        val empty = before.copy(products = emptyList(), inventories = emptyList(), stockLogs = emptyList(), todos = emptyList())

        val result = backup.restore(empty, RestoreMode.REPLACE)
        assertTrue(db.productDao().getAll().isEmpty())

        val snapshot = backup.readSnapshot(result.snapshotPath!!)
        backup.restore(snapshot.data, RestoreMode.REPLACE, takeSnapshot = false)
        assertEquals(before.products, db.productDao().getAll())
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (_: BackupException) {
            return
        }
        throw AssertionError("expected BackupException")
    }
}
