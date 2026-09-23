package com.example.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.InventoryDao
import com.example.data.dao.ProductDao
import com.example.data.dao.StockLogDao
import com.example.data.dao.TodoDao
import com.example.data.model.Inventory
import com.example.data.model.Product
import com.example.data.model.StockLog
import com.example.data.model.Todo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [Product::class, Inventory::class, StockLog::class, Todo::class],
    version = AppDatabase.VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun stockLogDao(): StockLogDao
    abstract fun todoDao(): TodoDao

    companion object {
        const val VERSION = 2
        const val NAME = "family_restock_master.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        /**
         * 故意不使用 fallbackToDestructiveMigration()：
         * 缺少迁移时宁可崩溃（开发阶段就会发现），也不能静默清空用户数据。
         */
        private fun build(context: Context): AppDatabase {
            PreMigrationBackup.run(context, NAME, VERSION)
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
        }
    }
}

/**
 * 数据库升级前，先把原始 .db 文件原样复制一份到 files/backups/pre-migration/。
 * 万一迁移逻辑有 bug，还能从这份文件里把数据救回来。
 */
object PreMigrationBackup {
    fun run(context: Context, dbName: String, targetVersion: Int) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return
        val currentVersion = try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        } catch (_: Exception) {
            return
        }
        if (currentVersion >= targetVersion) return

        val dir = File(context.filesDir, "backups/pre-migration").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        for (suffix in listOf("", "-wal", "-shm")) {
            val source = File(dbFile.path + suffix)
            if (source.exists()) {
                runCatching { source.copyTo(File(dir, "$dbName.v$currentVersion.$stamp$suffix"), overwrite = true) }
            }
        }
    }
}
