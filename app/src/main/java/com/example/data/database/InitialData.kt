package com.example.data.database

import com.example.data.model.Inventory
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.Purchase
import com.example.data.model.Todo
import com.example.data.model.TodoStatus
import com.example.data.model.TrackingMode
import com.example.data.model.UsageCycle

object InitialData {
    val initialProducts = listOf(
        Product(
            id = 1,
            name = "花王妙而舒纸尿裤 L码",
            category = ProductCategory.BABY,
            barcode = "4901301230882",
            trackingMode = TrackingMode.COUNT,
            safetyDays = 3,
            leadTimeDays = 2,
            defaultDailyBurnRate = 5.0,
            unit = "片",
            iconName = "child_care",
            note = "宝宝日常消耗大，低于25片需及时补货"
        ),
        Product(
            id = 2,
            name = "贝亲婴儿柔湿巾 (80抽)",
            category = ProductCategory.BABY,
            barcode = "6927902800115",
            trackingMode = TrackingMode.PACKAGE,
            safetyDays = 4,
            leadTimeDays = 2,
            defaultDailyBurnRate = 0.14, // 约7天一包
            defaultCycleDays = 7,
            unit = "包",
            iconName = "clean_hands",
            note = "常备3包以上最安心"
        ),
        Product(
            id = 3,
            name = "狮王儿童防蛀牙膏 葡萄味",
            category = ProductCategory.BABY,
            barcode = "4903301282082",
            trackingMode = TrackingMode.CYCLE,
            safetyDays = 5,
            leadTimeDays = 3,
            defaultCycleDays = 67,
            unit = "支",
            iconName = "brush",
            note = "早晚各一次，一支大约使用2个月"
        ),
        Product(
            id = 4,
            name = "丝塔芙宝宝舒润大白罐",
            category = ProductCategory.BABY,
            barcode = "3499320008549",
            trackingMode = TrackingMode.CYCLE,
            safetyDays = 5,
            leadTimeDays = 3,
            defaultCycleDays = 65,
            unit = "罐",
            iconName = "spa",
            note = "洗澡后全身抚触涂抹"
        ),
        Product(
            id = 5,
            name = "蓝臻智利新鲜蓝莓 (125g)",
            category = ProductCategory.FOOD,
            barcode = "8410293810023",
            trackingMode = TrackingMode.EXPIRY,
            safetyDays = 1,
            leadTimeDays = 1,
            unit = "盒",
            iconName = "nutrition",
            note = "开封冷藏，尽快给宝宝食用"
        ),
        Product(
            id = 6,
            name = "南美白虾仁 (冷冻 500g)",
            category = ProductCategory.FOOD,
            barcode = "6901827364510",
            trackingMode = TrackingMode.EXPIRY,
            safetyDays = 1,
            leadTimeDays = 1,
            unit = "袋",
            iconName = "restaurant",
            note = "宝宝辅食清蒸做虾泥"
        ),
        Product(
            id = 7,
            name = "蓝月亮深层洁净洗衣液 (3kg)",
            category = ProductCategory.HOUSEHOLD,
            barcode = "6917878018201",
            trackingMode = TrackingMode.LEVEL,
            safetyDays = 5,
            leadTimeDays = 2,
            unit = "瓶",
            iconName = "local_laundry_service",
            note = "家庭常备洗涤液"
        ),
        Product(
            id = 8,
            name = "立白除菌去油洗洁精 (1kg)",
            category = ProductCategory.HOUSEHOLD,
            barcode = "6902233445566",
            trackingMode = TrackingMode.LEVEL,
            safetyDays = 4,
            leadTimeDays = 2,
            unit = "瓶",
            iconName = "sanitizer",
            note = "洗碗清洗餐具"
        ),
        Product(
            id = 9,
            name = "3M净水器复合活性炭滤芯",
            category = ProductCategory.MAINTENANCE,
            barcode = "051131012345",
            trackingMode = TrackingMode.CYCLE,
            safetyDays = 7,
            leadTimeDays = 3,
            defaultCycleDays = 180,
            unit = "根",
            iconName = "water_drop",
            note = "建议每半年更换一次保证饮水水质"
        )
    )

    fun getInitialInventories(now: Long = System.currentTimeMillis()): List<Inventory> {
        val oneDay = 86_400_000L
        return listOf(
            // 纸尿裤：42片
            Inventory(
                productId = 1,
                quantity = 42.0,
                purchasedAt = now - 6 * oneDay,
                openedAt = now - 6 * oneDay,
                batchNote = "主卧尿布台"
            ),
            // 湿巾：3包
            Inventory(
                productId = 2,
                quantity = 3.0,
                purchasedAt = now - 10 * oneDay,
                openedAt = now - 2 * oneDay,
                batchNote = "客厅茶几抽屉"
            ),
            // 牙膏：开封使用中 (第53天，预估67天用完，还剩约14天)
            Inventory(
                productId = 3,
                quantity = 1.0,
                purchasedAt = now - 60 * oneDay,
                openedAt = now - 53 * oneDay,
                batchNote = "卫生间洗漱台"
            ),
            // 面霜：开封使用中 (第53天，预估65天用完，还剩约12天)
            Inventory(
                productId = 4,
                quantity = 1.0,
                purchasedAt = now - 55 * oneDay,
                openedAt = now - 53 * oneDay,
                batchNote = "儿童房床头柜"
            ),
            // 蓝莓：明天到期！今天优先吃！
            Inventory(
                productId = 5,
                quantity = 1.0,
                purchasedAt = now - 3 * oneDay,
                openedAt = now - 1 * oneDay,
                expiresAt = now + 18 * 3600_000L, // 18小时后（今天/明天到期）
                batchNote = "冰箱冷藏室保鲜盒"
            ),
            // 鲜虾：后天到期
            Inventory(
                productId = 6,
                quantity = 1.0,
                purchasedAt = now - 2 * oneDay,
                expiresAt = now + 48 * 3600_000L, // 2天后到期
                batchNote = "冰箱冷冻室上层"
            ),
            // 洗衣液：剩余 25% (触发建议购买)
            Inventory(
                productId = 7,
                quantity = 1.0,
                levelPercent = 25,
                purchasedAt = now - 45 * oneDay,
                openedAt = now - 40 * oneDay,
                batchNote = "阳台洗衣柜"
            ),
            // 洗洁精：剩余 75%
            Inventory(
                productId = 8,
                quantity = 1.0,
                levelPercent = 75,
                purchasedAt = now - 20 * oneDay,
                openedAt = now - 18 * oneDay,
                batchNote = "厨房水槽边"
            ),
            // 净水器滤芯：已使用 165天，还剩 15 天到期
            Inventory(
                productId = 9,
                quantity = 1.0,
                purchasedAt = now - 165 * oneDay,
                openedAt = now - 165 * oneDay,
                batchNote = "厨房水槽下方"
            )
        )
    }

    fun getInitialUsageCycles(now: Long = System.currentTimeMillis()): List<UsageCycle> {
        val oneDay = 86_400_000L
        return listOf(
            UsageCycle(
                productId = 3, // 牙膏历史周期
                openedAt = now - 200 * oneDay,
                usedUpAt = now - 137 * oneDay,
                durationDays = 63
            ),
            UsageCycle(
                productId = 3,
                openedAt = now - 137 * oneDay,
                usedUpAt = now - 66 * oneDay,
                durationDays = 71
            ),
            UsageCycle(
                productId = 4, // 面霜历史周期
                openedAt = now - 180 * oneDay,
                usedUpAt = now - 116 * oneDay,
                durationDays = 64
            )
        )
    }

    fun getInitialTodos(now: Long = System.currentTimeMillis()): List<Todo> {
        val oneDay = 86_400_000L
        return listOf(
            Todo(
                id = 1,
                title = "给宝宝买夏季透气凉鞋 (13.5码)",
                category = "宝宝供应链",
                dueAt = now + 3 * oneDay,
                status = TodoStatus.PENDING,
                note = "老婆提示：前包头防踢撞，鞋底要软"
            ),
            Todo(
                id = 2,
                title = "更换厨房净水器复合滤芯",
                category = "家庭维护",
                dueAt = now + 7 * oneDay,
                status = TodoStatus.PENDING,
                note = "滤芯已使用接近半年，换完需放水冲洗10分钟"
            ),
            Todo(
                id = 3,
                title = "联系物业报修客厅筒灯闪烁",
                category = "物业维修",
                dueAt = now + 2 * oneDay,
                status = TodoStatus.PENDING,
                note = "下班前致电管家上门"
            ),
            Todo(
                id = 4,
                title = "挑选2岁宝宝情绪管理绘本",
                category = "宝宝供应链",
                dueAt = now + 5 * oneDay,
                status = TodoStatus.IN_PROGRESS,
                note = "待对比《我的情绪小怪兽》和《小鸽子系列》"
            )
        )
    }
}
