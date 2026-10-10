package ian.dev.zaizai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardLogicTest {
    private val me = "奶奶"

    private fun item(id: String, slot: String, status: ItemStatus = ItemStatus.PENDING, by: String = me, at: Long = 0, priority: Int = 0) =
        BoardItem(
            taskId = id, title = id, kind = "supplement", note = "", priority = priority, slot = slot, status = status,
            completion = if (status == ItemStatus.PENDING) null else Completion(status.wire, "", by, at, "app"),
        )

    private fun board(vararg items: BoardItem) =
        Board("2026-10-10", "周六", "崽崽", items.toList(), "2026-10-11", emptyList(), 0)

    private fun op(type: OpType, id: String, slot: String, date: String = "2026-10-10", at: Long = 1_000) =
        PendingOp("$type-$id-$slot-$at", type, id, date, slot, "app", at)

    @Test
    fun `离线登记立刻显示为已喂，并排到后面`() {
        val b = board(item("AD", "08:00"), item("钙", "19:00"))
        val shown = BoardLogic.applyPending(b, listOf(op(OpType.CHECKIN, "AD", "08:00", at = 5_000)), me)
        assertEquals(listOf("钙", "AD"), shown.today.map { it.taskId })
        val ad = shown.today.last()
        assertEquals(ItemStatus.DONE, ad.status)
        assertEquals(me, ad.completion!!.recordedBy)
        assertEquals(5_000L, ad.completion!!.recordedAt)
        assertEquals(1, shown.pendingCount)
    }

    @Test
    fun `别的日子的排队操作不影响今天的看板`() {
        val b = board(item("AD", "08:00"))
        val shown = BoardLogic.applyPending(b, listOf(op(OpType.CHECKIN, "AD", "08:00", date = "2026-10-09")), me)
        assertEquals(ItemStatus.PENDING, shown.today.single().status)
    }

    @Test
    fun `排队的撤销只撤自己的登记`() {
        val b = board(item("AD", "08:00", ItemStatus.DONE, by = "妈妈"), item("钙", "19:00", ItemStatus.DONE))
        val ops = listOf(op(OpType.UNDO, "AD", "08:00"), op(OpType.UNDO, "钙", "19:00"))
        val shown = BoardLogic.applyPending(b, ops, me)
        assertEquals(ItemStatus.DONE, shown.today.first { it.taskId == "AD" }.status)
        val ca = shown.today.first { it.taskId == "钙" }
        assertEquals(ItemStatus.PENDING, ca.status)
        assertNull(ca.completion)
    }

    @Test
    fun `重复点确认只排一条`() {
        val first = op(OpType.CHECKIN, "AD", "08:00", at = 1)
        val q = BoardLogic.enqueueCheckin(BoardLogic.enqueueCheckin(emptyList(), first), op(OpType.CHECKIN, "AD", "08:00", at = 2))
        assertEquals(listOf(first), q)
    }

    @Test
    fun `还没传上去就撤销，直接从队列拿掉`() {
        val checkin = op(OpType.CHECKIN, "AD", "08:00")
        val other = op(OpType.CHECKIN, "钙", "19:00")
        val q = BoardLogic.enqueueUndo(listOf(checkin, other), op(OpType.UNDO, "AD", "08:00"))
        assertEquals(listOf(other), q)
    }

    @Test
    fun `已传上去的登记撤销要排队，再点确认则抵掉撤销`() {
        val undo = op(OpType.UNDO, "AD", "08:00")
        val q = BoardLogic.enqueueUndo(emptyList(), undo)
        assertEquals(listOf(undo), q)
        assertEquals(q, BoardLogic.enqueueUndo(q, op(OpType.UNDO, "AD", "08:00", at = 9)))
        assertTrue(BoardLogic.enqueueCheckin(q, op(OpType.CHECKIN, "AD", "08:00")).isEmpty())
    }

    @Test
    fun `撤销窗口2分钟，只能撤自己的`() {
        val mine = item("AD", "08:00", ItemStatus.DONE, at = 10_000)
        assertEquals(120_000L, BoardLogic.undoRemainingMs(mine, me, 10_000))
        assertEquals(1_000L, BoardLogic.undoRemainingMs(mine, me, 129_000))
        assertEquals(0L, BoardLogic.undoRemainingMs(mine, me, 130_000))
        assertEquals(0L, BoardLogic.undoRemainingMs(item("AD", "08:00", ItemStatus.DONE, by = "妈妈", at = 10_000), me, 10_000))
        assertEquals(0L, BoardLogic.undoRemainingMs(item("AD", "08:00"), me, 10_000))
    }

    @Test
    fun `排序与服务端一致`() {
        val sorted = BoardLogic.sorted(
            listOf(
                item("done", "07:00", ItemStatus.DONE),
                item("low", "08:00"),
                item("high", "08:00", priority = 5),
                item("early", "06:00"),
            ),
        )
        assertEquals(listOf("early", "high", "low", "done"), sorted.map { it.taskId })
    }

    @Test
    fun `播报最多念3项`() {
        val b = board(item("AD 一粒", "08:00"), item("益生菌", "12:00"), item("钙", "19:30"), item("鱼油", "21:15"), item("早饭", "07:00", ItemStatus.DONE))
        assertEquals("崽崽今天还有4件事：早上8点，AD 一粒；中午12点，益生菌；晚上7点半，钙；还有1件", BoardLogic.speech(b))
        assertEquals("崽崽今天的事都做完了", BoardLogic.speech(board(item("AD", "08:00", ItemStatus.DONE))))
        assertEquals("崽崽今天没有要做的事", BoardLogic.speech(board()))
    }

    @Test
    fun spokenTime() {
        assertEquals("凌晨5点", BoardLogic.spokenTime("05:00"))
        assertEquals("上午10点5分", BoardLogic.spokenTime("10:05"))
        assertEquals("下午3点", BoardLogic.spokenTime("15:00"))
        assertEquals("晚上11点", BoardLogic.spokenTime("23:00"))
    }

    @Test
    fun `到点还没登记的才提醒，每项一次`() {
        val b = board(item("AD", "08:00"), item("钙", "19:00"), item("早", "07:00", ItemStatus.DONE))
        assertEquals(listOf("AD"), BoardLogic.dueForNotice(b, "2026-10-10", "08:00", emptySet()).map { it.taskId })
        assertTrue(BoardLogic.dueForNotice(b, "2026-10-10", "09:00", setOf("AD|2026-10-10|08:00")).isEmpty())
        assertTrue(BoardLogic.dueForNotice(b, "2026-10-11", "20:00", emptySet()).isEmpty())
        // 过了 2 小时就不再提醒（晚上九点不弹上午九点的事）
        assertEquals(listOf("AD"), BoardLogic.dueForNotice(b, "2026-10-10", "09:59", emptySet()).map { it.taskId })
        assertTrue(BoardLogic.dueForNotice(b, "2026-10-10", "10:00", emptySet()).isEmpty())
        assertTrue(BoardLogic.dueForNotice(board(item("AD", "09:00")), "2026-10-10", "21:00", emptySet()).isEmpty())
    }

    @Test
    fun `小组件只放最早一件没做的，大字名称`() {
        val b = board(item("钙", "19:00"), item("AD", "08:00"), item("早", "07:00", ItemStatus.DONE))
        assertEquals(WidgetContent("AD", "08:00\n还剩 2 件", done = false), BoardLogic.widgetContent(b))
        assertEquals(WidgetContent("钙", "19:00\n该喂了", done = false), BoardLogic.widgetContent(board(item("钙", "19:00"))))
        assertEquals(WidgetContent("都喂好了", "都喂好了\n今天 1 件", done = true), BoardLogic.widgetContent(board(item("早", "07:00", ItemStatus.DONE))))
        assertTrue(BoardLogic.widgetContent(board()).done)
    }

    @Test
    fun checkinSpeechAndGreeting() {
        val ad = item("AD", "08:00")
        val ca = item("钙", "19:00")
        assertEquals("AD喂好了，还剩1件", BoardLogic.checkinSpeech(board(ad, ca), ad))
        assertEquals("钙喂好了，今天的都做完啦，真棒！", BoardLogic.checkinSpeech(board(ca), ca))
        assertEquals("早上好", BoardLogic.greeting("07:30"))
        assertEquals("下午好", BoardLogic.greeting("15:00"))
        assertEquals("夜深了，早点休息", BoardLogic.greeting("23:10"))
        assertEquals(BoardLogic.praise("a|b|c"), BoardLogic.praise("a|b|c"))
    }
}
