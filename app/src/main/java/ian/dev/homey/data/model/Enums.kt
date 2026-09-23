package ian.dev.homey.data.model

/**
 * 所有枚举在数据库和备份文件里都以 [name] 字符串保存。
 * 规则：已经上线的 name 永远不改名、不删除；要废弃就在 [fromCode] 里做显式映射。
 */
enum class TrackingMode(val label: String, val example: String) {
    COUNT("数数量", "纸尿裤 · 鸡蛋"),
    LEVEL("看余量", "洗洁精 · 面霜"),
    EXPIRY("看保质期", "牛奶 · 水果");

    companion object {
        /** 兼容 v1 的 6 种模式：PACKAGE/NONE → COUNT，CYCLE → LEVEL。 */
        fun fromCode(code: String?): TrackingMode = when (code) {
            "COUNT", "PACKAGE", "NONE" -> COUNT
            "LEVEL", "CYCLE" -> LEVEL
            "EXPIRY" -> EXPIRY
            else -> COUNT
        }
    }
}

enum class ProductCategory(val label: String) {
    BABY("宝宝"),
    HOUSEHOLD("清洁"),
    FOOD("食物"),
    PERSONAL_CARE("洗护"),
    MAINTENANCE("维护"),
    OTHER("其他");

    companion object {
        fun fromCode(code: String?): ProductCategory =
            entries.firstOrNull { it.name == code } ?: OTHER
    }
}

enum class StockLogType {
    CREATE,   // 新建物品时的初始库存
    ADJUST,   // 手动加减
    LEVEL,    // 更新余量
    RESTOCK,  // 买到了/补货
    USED_UP;  // v1 使用周期记录迁移而来

    companion object {
        fun fromCode(code: String?): StockLogType = entries.firstOrNull { it.name == code } ?: ADJUST
    }
}

enum class TodoStatus {
    PENDING, IN_PROGRESS, COMPLETED;

    companion object {
        fun fromCode(code: String?): TodoStatus = entries.firstOrNull { it.name == code } ?: PENDING
    }
}
