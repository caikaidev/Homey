package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.PredictionResult
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.ProductWithDetails
import com.example.data.model.StatusTone
import com.example.data.model.Todo
import com.example.data.model.TodoStatus
import com.example.data.model.TrackingMode
import com.example.data.repository.SupplyRepository
import com.example.domain.calendar.CalendarHelper
import com.example.domain.notification.NotificationHelper
import com.example.domain.prediction.PredictionEngine
import com.example.domain.worker.RestockDailyWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeSummary(
    val urgentCount: Int = 0,
    val urgentShoppingItems: List<PredictionResult> = emptyList(),
    val expiringItems: List<PredictionResult> = emptyList(),
    val lowStockItems: List<PredictionResult> = emptyList(),
    val pendingTodos: List<Todo> = emptyList()
)

class SupplyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = SupplyRepository(database)

    init {
        NotificationHelper.createNotificationChannels(application)
        RestockDailyWorker.schedule(application)
    }

    val allProductsWithDetails: StateFlow<List<ProductWithDetails>> = repository.allProductsWithDetails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPredictions: StateFlow<List<PredictionResult>> = allProductsWithDetails
        .map { list -> list.map { PredictionEngine.calculate(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTodos: StateFlow<List<Todo>> = repository.allTodos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Navigation & Filters
    private val _currentScreen = MutableStateFlow<String>("home") // "home", "inventory", "todo", "add", "detail"
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _selectedProductId = MutableStateFlow<Long?>(null)
    val selectedProductId: StateFlow<Long?> = _selectedProductId.asStateFlow()

    private val _inventoryCategoryFilter = MutableStateFlow<ProductCategory?>(null)
    val inventoryCategoryFilter: StateFlow<ProductCategory?> = _inventoryCategoryFilter.asStateFlow()

    private val _inventorySearchQuery = MutableStateFlow("")
    val inventorySearchQuery: StateFlow<String> = _inventorySearchQuery.asStateFlow()

    private val _todoStatusFilter = MutableStateFlow<TodoStatus?>(null)
    val todoStatusFilter: StateFlow<TodoStatus?> = _todoStatusFilter.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Filtered predictions for Inventory Screen
    val filteredPredictions: StateFlow<List<PredictionResult>> = combine(
        allPredictions,
        _inventoryCategoryFilter,
        _inventorySearchQuery
    ) { predictions, category, query ->
        predictions.filter { item ->
            val matchCategory = category == null || item.product.category == category
            val matchQuery = query.isBlank() ||
                    item.product.name.contains(query, ignoreCase = true) ||
                    item.product.barcode.contains(query, ignoreCase = true)
            matchCategory && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Home Screen Summary Aggregator
    val homeSummary: StateFlow<HomeSummary> = combine(allPredictions, allTodos) { predictions, todos ->
        val urgentShopping = predictions.filter { it.isUrgentReorder || (it.product.trackingMode != TrackingMode.EXPIRY && it.statusTone == StatusTone.URGENT_RED) }
        val expiring = predictions.filter { it.isExpiringSoon || it.isExpired }
        val lowStock = predictions.filter { it.statusTone == StatusTone.WARNING_YELLOW }
        val pending = todos.filter { it.status != TodoStatus.COMPLETED }

        val totalUrgent = urgentShopping.size + expiring.size + pending.count { it.dueAt != null && it.dueAt - System.currentTimeMillis() <= 86400000L }

        HomeSummary(
            urgentCount = totalUrgent,
            urgentShoppingItems = urgentShopping,
            expiringItems = expiring,
            lowStockItems = lowStock,
            pendingTodos = pending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeSummary())

    fun navigateTo(screen: String, productId: Long? = null) {
        _currentScreen.value = screen
        if (productId != null) {
            _selectedProductId.value = productId
        }
    }

    fun setCategoryFilter(category: ProductCategory?) {
        _inventoryCategoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _inventorySearchQuery.value = query
    }

    fun setTodoStatusFilter(status: TodoStatus?) {
        _todoStatusFilter.value = status
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun quickAdjustStock(inventoryId: Long, delta: Double) {
        viewModelScope.launch {
            repository.adjustInventoryQuantity(inventoryId, delta)
            _userMessage.value = if (delta > 0) "已增加库存" else "已扣减库存"
        }
    }

    fun quickUpdateLevel(inventoryId: Long, level: Int) {
        viewModelScope.launch {
            repository.updateInventoryLevel(inventoryId, level)
            _userMessage.value = "已更新余量为 $level%"
        }
    }

    fun markOpened(inventoryId: Long) {
        viewModelScope.launch {
            repository.markOpened(inventoryId)
            _userMessage.value = "已记录开封，系统将持续追踪使用周期"
        }
    }

    fun markUsedUp(productId: Long, inventoryId: Long) {
        viewModelScope.launch {
            repository.markUsedUp(productId, inventoryId)
            _userMessage.value = "已记录用完，周期历史已更新"
        }
    }

    fun restock(productId: Long, quantity: Double, price: Double = 0.0, note: String = "") {
        viewModelScope.launch {
            repository.restockProduct(productId, quantity, price, note)
            _userMessage.value = "补货入库成功！"
        }
    }

    fun addProduct(
        name: String,
        category: ProductCategory,
        trackingMode: TrackingMode,
        unit: String,
        initialQty: Double,
        level: Int = 100,
        safetyDays: Int = 3,
        leadTimeDays: Int = 2,
        dailyBurnRate: Double = 1.0,
        cycleDays: Int = 60,
        expiresAt: Long? = null,
        barcode: String = "",
        batchNote: String = "",
        note: String = "",
        openedNow: Boolean = false
    ) {
        viewModelScope.launch {
            val product = Product(
                name = name.trim(),
                category = category,
                barcode = barcode.trim(),
                trackingMode = trackingMode,
                unit = unit.ifBlank { "件" },
                safetyDays = safetyDays,
                leadTimeDays = leadTimeDays,
                defaultDailyBurnRate = dailyBurnRate,
                defaultCycleDays = cycleDays,
                note = note.trim()
            )
            repository.addProductWithInventory(
                product = product,
                initialQuantity = initialQty,
                levelPercent = level,
                openedNow = openedNow,
                expiresAt = expiresAt,
                batchNote = batchNote
            )
            _userMessage.value = "成功添加用品「$name」"
            _currentScreen.value = "inventory"
        }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
            _selectedProductId.value = null
            _currentScreen.value = "inventory"
            _userMessage.value = "已移除该用品"
        }
    }

    fun addTodo(title: String, category: String, dueAt: Long?, note: String = "") {
        viewModelScope.launch {
            repository.addTodo(title.trim(), category, dueAt, note)
            _userMessage.value = "待办已添加"
        }
    }

    fun toggleTodo(todo: Todo) {
        viewModelScope.launch {
            repository.toggleTodoStatus(todo)
        }
    }

    fun deleteTodo(id: Long) {
        viewModelScope.launch {
            repository.deleteTodo(id)
            _userMessage.value = "待办已删除"
        }
    }

    fun syncShoppingToCalendar(context: Context) {
        viewModelScope.launch {
            val eventId = repository.syncShoppingToCalendar(context)
            if (eventId != null) {
                _userMessage.value = "已同步「🛒 家庭采购」至系统日历"
            } else {
                // If direct calendar provider permission not granted, open Calendar Intent
                val urgent = homeSummary.value.urgentShoppingItems
                if (urgent.isNotEmpty()) {
                    val intent = CalendarHelper.createShoppingCalendarIntent(urgent)
                    context.startActivity(intent)
                    _userMessage.value = "已打开系统日历应用"
                } else {
                    _userMessage.value = "目前没有需要紧急采购的物品"
                }
            }
        }
    }

    fun syncTodoToCalendar(context: Context, todo: Todo) {
        viewModelScope.launch {
            val eventId = repository.syncTodoToCalendar(context, todo)
            if (eventId != null) {
                _userMessage.value = "已同步待办到系统日历"
            } else {
                _userMessage.value = "请授权日历权限或查看待办"
            }
        }
    }
}
