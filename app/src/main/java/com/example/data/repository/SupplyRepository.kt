package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.Inventory
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.ProductWithDetails
import com.example.data.model.Purchase
import com.example.data.model.Reminder
import com.example.data.model.Todo
import com.example.data.model.TodoStatus
import com.example.data.model.TrackingMode
import com.example.domain.calendar.CalendarHelper
import com.example.domain.prediction.PredictionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SupplyRepository(private val database: AppDatabase) {

    private val productDao = database.productDao()
    private val inventoryDao = database.inventoryDao()
    private val usageCycleDao = database.usageCycleDao()
    private val purchaseDao = database.purchaseDao()
    private val todoDao = database.todoDao()
    private val reminderDao = database.reminderDao()

    val allProductsWithDetails: Flow<List<ProductWithDetails>> = productDao.getAllProductsWithDetails()
    val allTodos: Flow<List<Todo>> = todoDao.getAllTodos()
    val activeReminders: Flow<List<Reminder>> = reminderDao.getActiveReminders()

    fun getProductWithDetails(id: Long): Flow<ProductWithDetails?> = productDao.getProductWithDetailsById(id)

    suspend fun addProductWithInventory(
        product: Product,
        initialQuantity: Double = 1.0,
        levelPercent: Int = 100,
        openedNow: Boolean = false,
        expiresAt: Long? = null,
        batchNote: String = ""
    ): Long {
        val productId = productDao.insertProduct(product)
        val now = System.currentTimeMillis()
        val inventory = Inventory(
            productId = productId,
            quantity = initialQuantity,
            levelPercent = levelPercent,
            purchasedAt = now,
            openedAt = if (openedNow) now else null,
            expiresAt = expiresAt,
            batchNote = batchNote
        )
        inventoryDao.insertInventory(inventory)
        return productId
    }

    suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product)
    }

    suspend fun deleteProduct(productId: Long) {
        productDao.deleteProductById(productId)
    }

    suspend fun adjustInventoryQuantity(inventoryId: Long, delta: Double) {
        val item = inventoryDao.getInventoryById(inventoryId) ?: return
        val newQty = (item.quantity + delta).coerceAtLeast(0.0)
        inventoryDao.updateInventory(item.copy(quantity = newQty))
    }

    suspend fun updateInventoryLevel(inventoryId: Long, newLevel: Int) {
        val item = inventoryDao.getInventoryById(inventoryId) ?: return
        inventoryDao.updateInventory(item.copy(levelPercent = newLevel))
    }

    suspend fun restockProduct(productId: Long, quantity: Double, price: Double = 0.0, note: String = "") {
        val now = System.currentTimeMillis()
        purchaseDao.insertPurchase(
            Purchase(
                productId = productId,
                quantity = quantity,
                purchasedAt = now,
                price = price,
                note = note
            )
        )
        val existingInventories = inventoryDao.getInventoriesByProductSync(productId)
        if (existingInventories.isNotEmpty()) {
            val primary = existingInventories.first()
            inventoryDao.updateInventory(
                primary.copy(
                    quantity = primary.quantity + quantity,
                    levelPercent = 100
                )
            )
        } else {
            inventoryDao.insertInventory(
                Inventory(
                    productId = productId,
                    quantity = quantity,
                    levelPercent = 100,
                    purchasedAt = now
                )
            )
        }
    }

    suspend fun markOpened(inventoryId: Long) {
        val item = inventoryDao.getInventoryById(inventoryId) ?: return
        inventoryDao.updateInventory(item.copy(openedAt = System.currentTimeMillis()))
    }

    suspend fun markUsedUp(productId: Long, inventoryId: Long) {
        val now = System.currentTimeMillis()
        val item = inventoryDao.getInventoryById(inventoryId)
        if (item?.openedAt != null) {
            val days = (((now - item.openedAt) / 86_400_000L).toInt()).coerceAtLeast(1)
            usageCycleDao.insertUsageCycle(
                com.example.data.model.UsageCycle(
                    productId = productId,
                    openedAt = item.openedAt,
                    usedUpAt = now,
                    durationDays = days
                )
            )
        }
        if (item != null) {
            if (item.quantity > 1.0) {
                // Decrement count by 1 and reset openedAt for next package
                inventoryDao.updateInventory(item.copy(quantity = item.quantity - 1.0, openedAt = now))
            } else {
                inventoryDao.updateInventory(item.copy(quantity = 0.0, levelPercent = 0, openedAt = null))
            }
        }
    }

    suspend fun addTodo(title: String, category: String = "家庭事务", dueAt: Long? = null, note: String = ""): Long {
        return todoDao.insertTodo(
            Todo(
                title = title,
                category = category,
                dueAt = dueAt,
                status = TodoStatus.PENDING,
                note = note
            )
        )
    }

    suspend fun toggleTodoStatus(todo: Todo) {
        val nextStatus = when (todo.status) {
            TodoStatus.PENDING -> TodoStatus.IN_PROGRESS
            TodoStatus.IN_PROGRESS -> TodoStatus.COMPLETED
            TodoStatus.COMPLETED -> TodoStatus.PENDING
        }
        val completedTime = if (nextStatus == TodoStatus.COMPLETED) System.currentTimeMillis() else null
        todoDao.updateTodo(todo.copy(status = nextStatus, completedAt = completedTime))
    }

    suspend fun deleteTodo(id: Long) {
        todoDao.deleteTodoById(id)
    }

    suspend fun syncShoppingToCalendar(context: Context): Long? {
        val products = allProductsWithDetails.first()
        val urgent = products
            .map { PredictionEngine.calculate(it) }
            .filter { it.isUrgentReorder || it.statusTone == com.example.data.model.StatusTone.URGENT_RED }

        return CalendarHelper.syncConsolidatedShoppingEvent(context, urgent)
    }

    suspend fun syncTodoToCalendar(context: Context, todo: Todo): Long? {
        val eventId = CalendarHelper.syncTodoCalendarEvent(context, todo, todo.calendarEventId)
        if (eventId != null && eventId != todo.calendarEventId) {
            todoDao.updateTodo(todo.copy(calendarEventId = eventId))
        }
        return eventId
    }
}
