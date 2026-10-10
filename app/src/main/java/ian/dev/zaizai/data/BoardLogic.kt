package ian.dev.zaizai.data

/** 不依赖 Android 的看板逻辑：离线排队、乐观更新、撤销窗口、播报文案。 */
object BoardLogic {
    const val UNDO_WINDOW_MS = 120_000L

    /** 与服务端一致：未登记在前，同组按时间点，再按优先级降序。 */
    fun sorted(items: List<BoardItem>): List<BoardItem> = items.sortedWith(
        compareBy<BoardItem> { it.status != ItemStatus.PENDING }.thenBy { it.slot }.thenByDescending { it.priority },
    )

    /** 把还没传上去的操作叠到服务器快照上，界面上立刻看到结果。 */
    fun applyPending(board: Board, ops: List<PendingOp>, me: String): Board {
        if (ops.isEmpty()) return board
        val byKey = board.today.associateBy { board.key(it) }.toMutableMap()
        for (op in ops) {
            val item = byKey[op.key] ?: continue
            byKey[op.key] = when (op.type) {
                OpType.CHECKIN ->
                    if (item.status != ItemStatus.PENDING) item
                    else item.copy(status = ItemStatus.DONE, completion = Completion("done", "", me, op.at, op.source))
                OpType.UNDO ->
                    if (item.completion?.recordedBy != me) item
                    else item.copy(status = ItemStatus.PENDING, completion = null)
            }
        }
        return board.copy(today = sorted(board.today.map { byKey.getValue(board.key(it)) }))
    }

    /** 登记入队。同一实例已经排着登记就不重复；排着撤销（之前已传上去的登记）就把撤销抵掉。 */
    fun enqueueCheckin(queue: List<PendingOp>, op: PendingOp): List<PendingOp> {
        val last = queue.lastOrNull { it.key == op.key } ?: return queue + op
        return if (last.type == OpType.CHECKIN) queue else queue - last
    }

    /** 撤销入队。登记还没传上去就直接从队列里拿掉，不必再告诉服务器。 */
    fun enqueueUndo(queue: List<PendingOp>, op: PendingOp): List<PendingOp> {
        val last = queue.lastOrNull { it.key == op.key } ?: return queue + op
        return if (last.type == OpType.UNDO) queue else queue - last
    }

    /** 还能撤销多久（毫秒）；不能撤销返回 0。只能撤销自己的登记。 */
    fun undoRemainingMs(item: BoardItem, me: String, serverNow: Long): Long {
        val c = item.completion ?: return 0
        if (item.status == ItemStatus.PENDING || c.recordedBy != me) return 0
        return (c.recordedAt + UNDO_WINDOW_MS - serverNow).coerceIn(0, UNDO_WINDOW_MS)
    }

    /** 打开 App 时的语音播报，最多念 3 项。 */
    fun speech(board: Board, max: Int = 3): String {
        val pending = board.today.filter { it.status == ItemStatus.PENDING }
        if (pending.isEmpty()) {
            return if (board.today.isEmpty()) "${board.nickname}今天没有要做的事" else "${board.nickname}今天的事都做完了"
        }
        val list = pending.take(max).joinToString("；") { "${spokenTime(it.slot)}，${it.title}" }
        val more = if (pending.size > max) "；还有${pending.size - max}件" else ""
        return "${board.nickname}今天还有${pending.size}件事：$list$more"
    }

    /** 刚点完“确认已喂”时念一句，让长辈知道登记上了；最后一件念得开心一点。 */
    fun checkinSpeech(board: Board, item: BoardItem): String {
        val left = board.today.count { it.status == ItemStatus.PENDING && board.key(it) != board.key(item) }
        return if (left == 0) "${item.title}喂好了，今天的都做完啦，真棒！"
        else "${item.title}喂好了，还剩${left}件"
    }

    /** 刚登记完卡片上的夸奖，按实例固定挑一句，免得每秒刷新都换。 */
    fun praise(key: String): String = PRAISES[Math.floorMod(key.hashCode(), PRAISES.size)]

    private val PRAISES = listOf("真棒！", "辛苦啦！", "崽崽谢谢你！", "又完成一件！", "做得好！")

    /** 页头问候：`07:30` → `早上好`。 */
    fun greeting(hhmm: String): String = when (hhmm.substringBefore(':').toIntOrNull() ?: 12) {
        in 5..8 -> "早上好"
        in 9..10 -> "上午好"
        in 11..12 -> "中午好"
        in 13..17 -> "下午好"
        in 18..21 -> "晚上好"
        else -> "夜深了，早点休息"
    }

    /** `08:00` → `早上8点`，`19:30` → `晚上7点半`。 */
    fun spokenTime(slot: String): String {
        val h = slot.substringBefore(':').toIntOrNull() ?: return slot
        val m = slot.substringAfter(':', "").toIntOrNull() ?: 0
        val (period, hour) = when (h) {
            in 0..5 -> "凌晨" to h
            in 6..8 -> "早上" to h
            in 9..11 -> "上午" to h
            12 -> "中午" to 12
            in 13..17 -> "下午" to h - 12
            else -> "晚上" to h - 12
        }
        val minute = when (m) {
            0 -> "点"
            30 -> "点半"
            else -> "点${m}分"
        }
        return "$period$hour$minute"
    }

    /** 到点了还没登记、且还没提醒过的项。 */
    fun dueForNotice(board: Board, today: String, nowHhmm: String, notified: Set<String>): List<BoardItem> {
        if (board.date != today) return emptyList()
        return board.today.filter {
            it.status == ItemStatus.PENDING && it.slot <= nowHhmm && board.key(it) !in notified
        }
    }

    /**
     * 小组件名称牌：只放一件最该做的事，大字显示名称（如“AD”），右边两行写时间和剩几件。
     * 都做完了小组件换成小牛崽动画，右边写“都喂好了”。
     */
    fun widgetContent(board: Board): WidgetContent {
        val pending = board.today.filter { it.status == ItemStatus.PENDING }
        val next = pending.minByOrNull { it.slot }
            ?: return if (board.today.isEmpty()) WidgetContent("今天没事", "今天没事\n好好休息", done = true)
            else WidgetContent("都喂好了", "都喂好了\n今天 ${board.today.size} 件", done = true)
        val rest = if (pending.size > 1) "\n还剩 ${pending.size} 件" else "\n该喂了"
        return WidgetContent(next.title, "${next.slot}$rest", done = false)
    }
}

data class WidgetContent(val big: String, val sub: String, val done: Boolean)
