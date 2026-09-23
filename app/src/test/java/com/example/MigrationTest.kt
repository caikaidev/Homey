package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.Migrations
import com.example.data.model.TrackingMode
import com.example.data.model.mode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 用 v1 的表结构造一个旧数据库，跑迁移后用 Room 打开。
 * Room 打开时会逐表校验结构，结构不一致会直接抛异常，所以这个测试同时验证了"迁移 SQL 与 @Entity 一致"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V1_SCHEMA.forEach { sql -> db.execSQL(sql) }
            db.execSQL(
                "INSERT INTO products (id,name,category,barcode,trackingMode,safetyDays,leadTimeDays," +
                    "defaultDailyBurnRate,defaultCycleDays,unit,iconName,note,createdAt) VALUES " +
                    "(1,'纸尿裤','BABY','','COUNT',3,2,5.0,60,'片','','',1000)," +
                    "(2,'湿巾','BABY','','PACKAGE',4,2,0.14,7,'包','','',1000)," +
                    "(3,'牙膏','BABY','','CYCLE',5,3,1.0,67,'支','','',1000)," +
                    "(4,'牛奶','FOOD','','EXPIRY',1,1,1.0,7,'盒','','',1000)," +
                    "(5,'剪刀','MAINTENANCE','','NONE',0,0,1.0,60,'把','','',1000)"
            )
            db.execSQL(
                "INSERT INTO inventories (id,productId,quantity,levelPercent,purchasedAt,openedAt,expiresAt,batchNote) VALUES " +
                    "(1,1,12,100,1000,NULL,NULL,'主卧')," +
                    "(2,2,3,100,1000,NULL,NULL,'')," +
                    "(3,3,1,50,1000,1500,NULL,'')," +
                    "(4,4,1,100,1000,NULL,999999,'冷藏室')," +
                    "(5,5,1,100,1000,NULL,NULL,'')"
            )
            db.execSQL("INSERT INTO purchases (id,productId,quantity,purchasedAt,price,note) VALUES (1,1,64,900,0,'大促')")
            db.execSQL("INSERT INTO usage_cycles (id,productId,openedAt,usedUpAt,durationDays) VALUES (1,3,0,5000,60)")
            db.execSQL(
                "INSERT INTO todos (id,title,category,dueAt,completedAt,reminderEnabled,calendarEventId,status,note,createdAt) " +
                    "VALUES (1,'买凉鞋','宝宝',NULL,NULL,1,NULL,'PENDING','',1000)"
            )
            db.execSQL(
                "INSERT INTO reminders (id,type,targetId,scheduledAt,calendarEventId,status,title,description,createdAt) " +
                    "VALUES (1,'REORDER',1,0,NULL,'ACTIVE','t','d',0)"
            )
            db.version = 1
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun migrate1To2_keepsAllUserData() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val products = db.productDao().getAll().associateBy { it.name }
            assertEquals(5, products.size)
            assertEquals(TrackingMode.COUNT, products.getValue("纸尿裤").mode)
            assertEquals(TrackingMode.COUNT, products.getValue("湿巾").mode)
            assertEquals(TrackingMode.LEVEL, products.getValue("牙膏").mode)
            assertEquals(TrackingMode.EXPIRY, products.getValue("牛奶").mode)
            assertEquals(TrackingMode.COUNT, products.getValue("剪刀").mode)

            val diaper = products.getValue("纸尿裤")
            assertEquals(5.0, diaper.dailyUsage, 0.001)
            assertEquals(5, diaper.remindDaysAhead)
            assertEquals(70.0, diaper.restockAmount, 0.001)
            assertEquals("主卧", diaper.location)
            assertEquals(1.0 / 7, products.getValue("湿巾").dailyUsage, 0.001)
            assertEquals(0.0, products.getValue("剪刀").dailyUsage, 0.001)
            assertTrue(products.values.map { it.id }.toSet().size == 5)

            val inventories = db.inventoryDao().getAll()
            assertEquals(5, inventories.size)
            assertEquals(12.0, inventories.first { it.productId == diaper.id }.quantity, 0.001)
            assertEquals(50, inventories.first { it.productId == products.getValue("牙膏").id }.levelPercent)

            val logs = db.stockLogDao().getAll()
            assertEquals(2, logs.size)
            assertNotNull(logs.firstOrNull { it.type == "RESTOCK" && it.productId == diaper.id && it.note == "大促" })
            assertNotNull(logs.firstOrNull { it.type == "USED_UP" })

            val todos = db.todoDao().getAll()
            assertEquals(1, todos.size)
            assertEquals("买凉鞋", todos.first().title)
        } finally {
            db.close()
        }
    }

    private companion object {
        /** 与 v1（AppDatabase version = 1）Room 生成的建表语句一致。 */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `barcode` TEXT NOT NULL, `trackingMode` TEXT NOT NULL, `safetyDays` INTEGER NOT NULL, `leadTimeDays` INTEGER NOT NULL, `defaultDailyBurnRate` REAL NOT NULL, `defaultCycleDays` INTEGER NOT NULL, `unit` TEXT NOT NULL, `iconName` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `inventories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `quantity` REAL NOT NULL, `levelPercent` INTEGER NOT NULL, `purchasedAt` INTEGER NOT NULL, `openedAt` INTEGER, `expiresAt` INTEGER, `batchNote` TEXT NOT NULL, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_inventories_productId` ON `inventories` (`productId`)",
            "CREATE TABLE IF NOT EXISTS `usage_cycles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `openedAt` INTEGER NOT NULL, `usedUpAt` INTEGER NOT NULL, `durationDays` INTEGER NOT NULL, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_usage_cycles_productId` ON `usage_cycles` (`productId`)",
            "CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `quantity` REAL NOT NULL, `purchasedAt` INTEGER NOT NULL, `price` REAL NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_purchases_productId` ON `purchases` (`productId`)",
            "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `dueAt` INTEGER, `completedAt` INTEGER, `reminderEnabled` INTEGER NOT NULL, `calendarEventId` INTEGER, `status` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `targetId` INTEGER NOT NULL, `scheduledAt` INTEGER NOT NULL, `calendarEventId` INTEGER, `status` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
    }
}
