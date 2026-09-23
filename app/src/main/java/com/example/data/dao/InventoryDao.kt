package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Inventory
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventories WHERE productId = :productId ORDER BY id ASC")
    fun getInventoriesByProduct(productId: Long): Flow<List<Inventory>>

    @Query("SELECT * FROM inventories WHERE productId = :productId ORDER BY id ASC")
    suspend fun getInventoriesByProductSync(productId: Long): List<Inventory>

    @Query("SELECT * FROM inventories WHERE id = :id LIMIT 1")
    suspend fun getInventoryById(id: Long): Inventory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventory(inventory: Inventory): Long

    @Update
    suspend fun updateInventory(inventory: Inventory)

    @Delete
    suspend fun deleteInventory(inventory: Inventory)

    @Query("DELETE FROM inventories WHERE id = :id")
    suspend fun deleteInventoryById(id: Long)

    @Query("DELETE FROM inventories WHERE productId = :productId")
    suspend fun deleteInventoriesByProduct(productId: Long)
}
