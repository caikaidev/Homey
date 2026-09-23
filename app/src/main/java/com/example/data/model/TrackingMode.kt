package com.example.data.model

enum class TrackingMode(val label: String, val description: String) {
    COUNT("按数量", "纸尿裤、鸡蛋、奶粉罐等按个/片消耗"),
    PACKAGE("按包装", "湿巾、抽纸、垃圾袋等整包使用"),
    CYCLE("按周期", "牙膏、面霜、防晒等，记录开封到用完"),
    LEVEL("按余量", "洗发水、洗衣液、洗洁精等百分比余量"),
    EXPIRY("按保质期", "蓝莓、牛奶、生鲜食材等优先处理"),
    NONE("仅记录", "长期耐用品、绘本、玩具")
}
