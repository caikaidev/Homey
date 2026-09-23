package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.InventoryDao
import com.example.data.dao.ProductDao
import com.example.data.dao.PurchaseDao
import com.example.data.dao.ReminderDao
import com.example.data.dao.TodoDao
import com.example.data.dao.UsageCycleDao
import com.example.data.model.Inventory
import com.example.data.model.Product
import com.example.data.model.Purchase
import com.example.data.model.Reminder
import com.example.data.model.Todo
import com.example.data.model.UsageCycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Product::class,
        Inventory::class,
        UsageCycle::class,
        Purchase::class,
        Todo::class,
        Reminder::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun usageCycleDao(): UsageCycleDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun todoDao(): TodoDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "family_restock_master.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database)
                }
            }
        }

        private suspend fun populateInitialData(database: AppDatabase) {
            val productDao = database.productDao()
            val inventoryDao = database.inventoryDao()
            val usageCycleDao = database.usageCycleDao()
            val todoDao = database.todoDao()

            for (product in InitialData.initialProducts) {
                productDao.insertProduct(product)
            }
            for (inventory in InitialData.getInitialInventories()) {
                inventoryDao.insertInventory(inventory)
            }
            for (cycle in InitialData.getInitialUsageCycles()) {
                usageCycleDao.insertUsageCycle(cycle)
            }
            for (todo in InitialData.getInitialTodos()) {
                todoDao.insertTodo(todo)
            }
        }
    }
}
