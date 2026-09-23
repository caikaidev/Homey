package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.UsageCycle
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageCycleDao {
    @Query("SELECT * FROM usage_cycles WHERE productId = :productId ORDER BY usedUpAt DESC")
    fun getUsageCyclesByProduct(productId: Long): Flow<List<UsageCycle>>

    @Query("SELECT * FROM usage_cycles WHERE productId = :productId ORDER BY usedUpAt DESC")
    suspend fun getUsageCyclesByProductSync(productId: Long): List<UsageCycle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageCycle(cycle: UsageCycle): Long

    @Update
    suspend fun updateUsageCycle(cycle: UsageCycle)

    @Delete
    suspend fun deleteUsageCycle(cycle: UsageCycle)
}
