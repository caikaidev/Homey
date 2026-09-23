package com.example.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移。规则：
 * 1. 每次改 @Entity 都要把 AppDatabase.version +1，并在这里写对应的 Migration；
 * 2. 已发布的迁移永远不修改，只追加；
 * 3. app/schemas/ 下 Room 导出的 JSON 要提交进仓库，用来对比和测试。
 */
object Migrations {

    // ---- v2 的建表语句（与 Room 根据 @Entity 生成的一致，Room 打开时会逐列校验）----

    const val CREATE_PRODUCTS_V2 =
        "CREATE TABLE IF NOT EXISTS `%s` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`category` TEXT NOT NULL, `trackingMode` TEXT NOT NULL, `unit` TEXT NOT NULL, " +
            "`dailyUsage` REAL NOT NULL, `lifeDays` INTEGER NOT NULL, `remindDaysAhead` INTEGER NOT NULL, " +
            "`restockAmount` REAL NOT NULL, `location` TEXT NOT NULL, `note` TEXT NOT NULL, " +
            "`barcode` TEXT NOT NULL, `isSample` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))"

    const val CREATE_INVENTORIES_V2 =
        "CREATE TABLE IF NOT EXISTS `inventories` (`id` TEXT NOT NULL, `productId` TEXT NOT NULL, " +
            "`quantity` REAL NOT NULL, `levelPercent` INTEGER NOT NULL, `expiresAt` INTEGER, " +
            "`openedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`productId`) REFERENCES `products`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"

    const val CREATE_STOCK_LOGS_V2 =
        "CREATE TABLE IF NOT EXISTS `stock_logs` (`id` TEXT NOT NULL, `productId` TEXT NOT NULL, " +
            "`type` TEXT NOT NULL, `delta` REAL NOT NULL, `levelPercent` INTEGER, `note` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`productId`) " +
            "REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    const val CREATE_TODOS_V2 =
        "CREATE TABLE IF NOT EXISTS `todos` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`category` TEXT NOT NULL, `dueAt` INTEGER, `completedAt` INTEGER, `status` TEXT NOT NULL, " +
            "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "`deletedAt` INTEGER, PRIMARY KEY(`id`))"

    /** 每行各自生成一个随机 ID（不能用子查询，SQLite 可能只算一次）。 */
    private const val RANDOM_ID = "lower(hex(randomblob(16)))"

    /**
     * v1 → v2：
     * - 自增整数主键 → 全局唯一字符串 ID（为合并备份做准备）
     * - 6 种记录方式合并为 3 种：PACKAGE/NONE → COUNT，CYCLE → LEVEL
     * - 送达天数 + 安全天数 → 提前几天提醒
     * - 批次上的"存放位置"移到物品上
     * - purchases / usage_cycles 转成 stock_logs 流水，reminders（可重新计算）丢弃
     * - todos 保留（V1 暂不显示）
     *
     * 做法：先把旧数据复制到无外键的临时表 → 从子表到父表依次删除旧表 → 建新表 → 写回。
     * 这样无论 foreign_keys 是否开启，都不会因为级联删除丢数据。
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE `_id_map` (`oldId` INTEGER PRIMARY KEY NOT NULL, `uuid` TEXT NOT NULL)")
            db.execSQL("INSERT INTO `_id_map` (`oldId`, `uuid`) SELECT `id`, $RANDOM_ID FROM `products`")

            db.execSQL(CREATE_PRODUCTS_V2.format("products_new"))
            db.execSQL(
                """
                INSERT INTO `products_new` (`id`, `name`, `category`, `trackingMode`, `unit`, `dailyUsage`,
                    `lifeDays`, `remindDaysAhead`, `restockAmount`, `location`, `note`, `barcode`, `isSample`,
                    `createdAt`, `updatedAt`, `deletedAt`)
                SELECT m.`uuid`,
                    p.`name`,
                    COALESCE(p.`category`, 'OTHER'),
                    CASE p.`trackingMode` WHEN 'CYCLE' THEN 'LEVEL' WHEN 'LEVEL' THEN 'LEVEL'
                        WHEN 'EXPIRY' THEN 'EXPIRY' ELSE 'COUNT' END,
                    COALESCE(p.`unit`, '件'),
                    CASE p.`trackingMode`
                        WHEN 'COUNT' THEN p.`defaultDailyBurnRate`
                        WHEN 'PACKAGE' THEN (CASE WHEN p.`defaultCycleDays` > 0 THEN 1.0 / p.`defaultCycleDays` ELSE 1.0 END)
                        ELSE 0.0 END,
                    CASE WHEN p.`trackingMode` = 'EXPIRY' THEN 7
                        WHEN p.`defaultCycleDays` > 0 THEN p.`defaultCycleDays` ELSE 30 END,
                    p.`leadTimeDays` + p.`safetyDays`,
                    CASE p.`trackingMode` WHEN 'COUNT' THEN max(1.0, round(p.`defaultDailyBurnRate` * 14)) ELSE 1.0 END,
                    COALESCE((SELECT i.`batchNote` FROM `inventories` i
                        WHERE i.`productId` = p.`id` AND i.`batchNote` != '' ORDER BY i.`id` LIMIT 1), ''),
                    COALESCE(p.`note`, ''),
                    COALESCE(p.`barcode`, ''),
                    0,
                    p.`createdAt`,
                    p.`createdAt`,
                    NULL
                FROM `products` p JOIN `_id_map` m ON m.`oldId` = p.`id`
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE `_inv_tmp` AS
                SELECT $RANDOM_ID AS `id`, m.`uuid` AS `productId`, i.`quantity` AS `quantity`,
                    i.`levelPercent` AS `levelPercent`, i.`expiresAt` AS `expiresAt`, i.`openedAt` AS `openedAt`,
                    i.`purchasedAt` AS `createdAt`
                FROM `inventories` i JOIN `_id_map` m ON m.`oldId` = i.`productId`
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE `_log_tmp` AS
                SELECT $RANDOM_ID AS `id`, m.`uuid` AS `productId`, 'RESTOCK' AS `type`, pu.`quantity` AS `delta`,
                    NULL AS `levelPercent`, COALESCE(pu.`note`, '') AS `note`, pu.`purchasedAt` AS `createdAt`
                FROM `purchases` pu JOIN `_id_map` m ON m.`oldId` = pu.`productId`
                UNION ALL
                SELECT $RANDOM_ID, m.`uuid`, 'USED_UP', 0.0, NULL,
                    '开封后 ' || u.`durationDays` || ' 天用完', u.`usedUpAt`
                FROM `usage_cycles` u JOIN `_id_map` m ON m.`oldId` = u.`productId`
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE `_todo_tmp` AS
                SELECT $RANDOM_ID AS `id`, `title`, COALESCE(`category`, '家庭事务') AS `category`, `dueAt`,
                    `completedAt`, COALESCE(`status`, 'PENDING') AS `status`, COALESCE(`note`, '') AS `note`,
                    `createdAt`
                FROM `todos`
                """.trimIndent()
            )

            // 先删子表，再删父表
            db.execSQL("DROP TABLE IF EXISTS `reminders`")
            db.execSQL("DROP TABLE IF EXISTS `usage_cycles`")
            db.execSQL("DROP TABLE IF EXISTS `purchases`")
            db.execSQL("DROP TABLE IF EXISTS `inventories`")
            db.execSQL("DROP TABLE IF EXISTS `products`")
            db.execSQL("DROP TABLE IF EXISTS `todos`")

            db.execSQL("ALTER TABLE `products_new` RENAME TO `products`")

            db.execSQL(CREATE_INVENTORIES_V2)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventories_productId` ON `inventories` (`productId`)")
            db.execSQL(
                "INSERT INTO `inventories` (`id`, `productId`, `quantity`, `levelPercent`, `expiresAt`, " +
                    "`openedAt`, `createdAt`, `updatedAt`) SELECT `id`, `productId`, COALESCE(`quantity`, 0), " +
                    "COALESCE(`levelPercent`, 100), `expiresAt`, `openedAt`, `createdAt`, `createdAt` FROM `_inv_tmp`"
            )

            db.execSQL(CREATE_STOCK_LOGS_V2)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_logs_productId` ON `stock_logs` (`productId`)")
            db.execSQL(
                "INSERT INTO `stock_logs` (`id`, `productId`, `type`, `delta`, `levelPercent`, `note`, `createdAt`) " +
                    "SELECT `id`, `productId`, `type`, COALESCE(`delta`, 0), `levelPercent`, `note`, `createdAt` FROM `_log_tmp`"
            )

            db.execSQL(CREATE_TODOS_V2)
            db.execSQL(
                "INSERT INTO `todos` (`id`, `title`, `category`, `dueAt`, `completedAt`, `status`, `note`, " +
                    "`createdAt`, `updatedAt`, `deletedAt`) SELECT `id`, `title`, `category`, `dueAt`, " +
                    "`completedAt`, `status`, `note`, `createdAt`, `createdAt`, NULL FROM `_todo_tmp`"
            )

            db.execSQL("DROP TABLE `_inv_tmp`")
            db.execSQL("DROP TABLE `_log_tmp`")
            db.execSQL("DROP TABLE `_todo_tmp`")
            db.execSQL("DROP TABLE `_id_map`")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
