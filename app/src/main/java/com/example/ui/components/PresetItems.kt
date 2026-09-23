package com.example.ui.components

import com.example.data.model.ProductCategory
import com.example.data.model.TrackingMode

data class QuickTemplateItem(
    val name: String,
    val category: ProductCategory,
    val trackingMode: TrackingMode,
    val unit: String,
    val defaultQty: Double,
    val defaultLevel: Int = 100,
    val safetyDays: Int,
    val leadTimeDays: Int,
    val dailyBurnRate: Double,
    val cycleDays: Int,
    val barcode: String,
    val note: String
)

object PresetLibrary {
    val items = listOf(
        QuickTemplateItem(
            name = "花王纸尿裤 L",
            category = ProductCategory.BABY,
            trackingMode = TrackingMode.COUNT,
            unit = "片",
            defaultQty = 44.0,
            safetyDays = 3,
            leadTimeDays = 2,
            dailyBurnRate = 5.0,
            cycleDays = 0,
            barcode = "4901301230882",
            note = "宝宝日常主力纸尿裤"
        ),
        QuickTemplateItem(
            name = "贝亲婴儿柔湿巾",
            category = ProductCategory.BABY,
            trackingMode = TrackingMode.PACKAGE,
            unit = "包",
            defaultQty = 3.0,
            safetyDays = 4,
            leadTimeDays = 2,
            dailyBurnRate = 0.14,
            cycleDays = 7,
            barcode = "6927902800115",
            note = "80抽温和纯水湿巾"
        ),
        QuickTemplateItem(
            name = "爱他美卓萃奶粉 3段",
            category = ProductCategory.BABY,
            trackingMode = TrackingMode.COUNT,
            unit = "罐",
            defaultQty = 2.0,
            safetyDays = 4,
            leadTimeDays = 2,
            dailyBurnRate = 0.1, // 约10天一罐
            cycleDays = 10,
            barcode = "9316562002345",
            note = "每罐800g"
        ),
        QuickTemplateItem(
            name = "丝塔芙宝宝面霜",
            category = ProductCategory.BABY,
            trackingMode = TrackingMode.CYCLE,
            unit = "罐",
            defaultQty = 1.0,
            safetyDays = 5,
            leadTimeDays = 3,
            dailyBurnRate = 0.0,
            cycleDays = 65,
            barcode = "3499320008549",
            note = "大白罐滋润保湿"
        ),
        QuickTemplateItem(
            name = "狮王儿童防蛀牙膏",
            category = ProductCategory.BABY,
            trackingMode = TrackingMode.CYCLE,
            unit = "支",
            defaultQty = 1.0,
            safetyDays = 5,
            leadTimeDays = 3,
            dailyBurnRate = 0.0,
            cycleDays = 67,
            barcode = "4903301282082",
            note = "早晚刷牙，按周期消耗"
        ),
        QuickTemplateItem(
            name = "新鲜智利蓝莓",
            category = ProductCategory.FOOD,
            trackingMode = TrackingMode.EXPIRY,
            unit = "盒",
            defaultQty = 1.0,
            safetyDays = 1,
            leadTimeDays = 1,
            dailyBurnRate = 0.0,
            cycleDays = 3,
            barcode = "8410293810023",
            note = "开封后需冷藏，优先吃"
        ),
        QuickTemplateItem(
            name = "冷冻南美白虾",
            category = ProductCategory.FOOD,
            trackingMode = TrackingMode.EXPIRY,
            unit = "袋",
            defaultQty = 1.0,
            safetyDays = 2,
            leadTimeDays = 1,
            dailyBurnRate = 0.0,
            cycleDays = 7,
            barcode = "6901827364510",
            note = "辅食做虾泥"
        ),
        QuickTemplateItem(
            name = "蓝月亮洗衣液",
            category = ProductCategory.HOUSEHOLD,
            trackingMode = TrackingMode.LEVEL,
            unit = "瓶",
            defaultQty = 1.0,
            defaultLevel = 75,
            safetyDays = 5,
            leadTimeDays = 2,
            dailyBurnRate = 0.0,
            cycleDays = 45,
            barcode = "6917878018201",
            note = "按余量标记"
        ),
        QuickTemplateItem(
            name = "加厚抽绳垃圾袋",
            category = ProductCategory.HOUSEHOLD,
            trackingMode = TrackingMode.PACKAGE,
            unit = "卷",
            defaultQty = 3.0,
            safetyDays = 3,
            leadTimeDays = 2,
            dailyBurnRate = 0.07,
            cycleDays = 15,
            barcode = "6945892301923",
            note = "每卷50只"
        ),
        QuickTemplateItem(
            name = "净水器活性炭滤芯",
            category = ProductCategory.MAINTENANCE,
            trackingMode = TrackingMode.CYCLE,
            unit = "根",
            defaultQty = 1.0,
            safetyDays = 7,
            leadTimeDays = 3,
            dailyBurnRate = 0.0,
            cycleDays = 180,
            barcode = "051131012345",
            note = "建议半年更换一次"
        )
    )
}
