package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.data.model.Inventory
import com.example.data.model.Product
import com.example.data.model.StockLog
import com.example.data.model.Todo
import kotlinx.coroutines.flow.Flow

// 注意：写入一律用 @Upsert，不用 @Insert(REPLACE)。
// REPLACE 在 SQLite 里是"先删再插"，会触发外键 CASCADE，把这件物品的库存和流水一起删掉。

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Product?

    @Query("SELECT * FROM products")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM products WHERE deletedAt IS NULL")
    suspend fun getActive(): List<Product>

    @Query("SELECT COUNT(*) FROM products WHERE deletedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE isSample = 1 AND deletedAt IS NULL")
    fun observeSampleCount(): Flow<Int>

    @Upsert
    suspend fun upsert(product: Product)

    @Upsert
    suspend fun upsertAll(products: List<Product>)

    @Query("DELETE FROM products WHERE isSample = 1")
    suspend fun deleteSamples()

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventories")
    fun observeAll(): Flow<List<Inventory>>

    @Query("SELECT * FROM inventories")
    suspend fun getAll(): List<Inventory>

    @Query("SELECT * FROM inventories WHERE productId = :productId ORDER BY createdAt ASC")
    suspend fun getByProduct(productId: String): List<Inventory>

    @Upsert
    suspend fun upsert(inventory: Inventory)

    @Upsert
    suspend fun upsertAll(inventories: List<Inventory>)

    @Query("DELETE FROM inventories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM inventories WHERE productId = :productId")
    suspend fun deleteByProduct(productId: String)

    @Query("DELETE FROM inventories")
    suspend fun deleteAll()
}

@Dao
interface StockLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: StockLog)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<StockLog>)

    @Query("SELECT * FROM stock_logs ORDER BY createdAt ASC")
    suspend fun getAll(): List<StockLog>

    @Query("SELECT COUNT(*) FROM stock_logs")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM stock_logs")
    suspend fun deleteAll()
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos")
    suspend fun getAll(): List<Todo>

    @Upsert
    suspend fun upsertAll(todos: List<Todo>)

    @Query("DELETE FROM todos")
    suspend fun deleteAll()
}
