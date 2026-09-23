package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Inventory
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.ProductWithDetails
import com.example.data.model.TrackingMode
import com.example.domain.prediction.PredictionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("家庭补给管家", appName)
    }

    @Test
    fun `test prediction engine reorder urgency`() {
        val product = Product(
            id = 1L,
            name = "花王纸尿裤",
            category = ProductCategory.BABY,
            trackingMode = TrackingMode.COUNT,
            unit = "片",
            safetyDays = 3,
            leadTimeDays = 2,
            defaultDailyBurnRate = 5.0
        )
        // reorder point = 5 * (2 + 3) = 25片.
        // If stock is 10片, it must be urgent!
        val item = ProductWithDetails(
            product = product,
            inventories = listOf(Inventory(id = 1L, productId = 1L, quantity = 10.0))
        )
        val prediction = PredictionEngine.calculate(item)
        assertTrue(prediction.isUrgentReorder)
    }
}
