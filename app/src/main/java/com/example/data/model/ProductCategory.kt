package com.example.data.model

enum class ProductCategory(val label: String, val icon: String) {
    BABY("宝宝供应链", "👶"),
    HOUSEHOLD("家庭耗材", "🏠"),
    MAINTENANCE("家庭维护", "🔧"),
    FOOD("生鲜食品", "🍎"),
    PERSONAL_CARE("个人洗护", "🧴"),
    OTHER("其他用品", "📦");

    companion object {
        fun fromString(value: String): ProductCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.label == value } ?: OTHER
        }
    }
}
