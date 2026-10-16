package ian.dev.zaizai.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTest {
    private fun item(id: String, kind: String, status: ItemStatus = ItemStatus.PENDING) = BoardItem(
        taskId = id, title = id, kind = kind, note = "", priority = 0, slot = "08:00", status = status,
        completion = if (status == ItemStatus.PENDING) null else Completion(status.wire, "", "奶奶", 0, "app"),
    )

    private fun board(vararg items: BoardItem, tomorrow: List<TomorrowItem> = emptyList()) =
        Board("2026-10-16", "周五", "崽崽", items.toList(), "2026-10-17", tomorrow, 0)

    private val image = DayImage("img1", "2026-10-16", "/api/images/img1")

    @Test
    fun `事项卡片的数字按本机看板现算，图片卡只在当天显示`() {
        val home = Home(
            "2026-10-16",
            listOf(
                HomeCard("supplement", CardType.TASKS, "补剂", total = 9, pending = 9),
                HomeCard("medicine", CardType.TASKS, "药品"),
                HomeCard("babyroom", CardType.IMAGE, "宝宝屋", image = image),
            ),
        )
        val b = board(item("AD", "supplement", ItemStatus.DONE), item("钙", "supplement"))
        val cards = BoardLogic.homeCards(home, b)
        assertEquals(listOf("supplement", "medicine", "babyroom"), cards.map { it.key })
        assertEquals(2 to 1, cards[0].total to cards[0].pending)
        assertEquals(0 to 0, cards[1].total to cards[1].pending)

        // 跨过零点、看板已经是新的一天但首页还是旧的：昨天的图不显示
        val next = b.copy(date = "2026-10-17")
        assertEquals(listOf("supplement", "medicine"), BoardLogic.homeCards(home, next).map { it.key })
    }

    @Test
    fun `没有首页数据时按看板里的类别显示`() {
        val b = board(item("感冒药", "medicine"), tomorrow = listOf(TomorrowItem("AD", "AD", "08:00", "supplement")))
        val cards = BoardLogic.homeCards(null, b)
        assertEquals(listOf("补剂", "药品"), cards.map { it.title })
        assertEquals(1, cards[1].pending)
        assertEquals(emptyList<HomeCard>(), BoardLogic.homeCards(null, null))
    }

    @Test
    fun `只看某一类`() {
        val b = board(item("AD", "supplement"), item("感冒药", "medicine"), tomorrow = listOf(TomorrowItem("AD", "AD", "08:00", "supplement")))
        val med = b.ofKind("medicine")
        assertEquals(listOf("感冒药"), med.today.map { it.taskId })
        assertEquals(emptyList<TomorrowItem>(), med.tomorrow)
    }

    @Test
    fun `首页接口按契约解析；不认识的卡片类型跳过；旧服务器 404 返回 null；图片原样下载`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val api = ApiClient(server.url("/").toString().trimEnd('/'), "family-key", "奶奶")
            server.enqueue(
                MockResponse().setBody(
                    """{"date":"2026-10-16","serverTime":0,"cards":[
                      {"key":"supplement","type":"tasks","title":"补剂","total":2,"pending":1},
                      {"key":"future","type":"video","title":"新东西"},
                      {"key":"babyroom","type":"image","title":"宝宝屋","image":{"id":"img1","category":"babyroom","use_date":"2026-10-16","mime":"image/png","size":3,"url":"/api/images/img1"}}
                    ]}""",
                ),
            )
            val (home, _) = api.home()!!
            assertEquals("/api/home", server.takeRequest().path)
            assertEquals(listOf("supplement", "babyroom"), home.cards.map { it.key })
            assertEquals(image, home.cards[1].image)

            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":"not_found","message":"没有这个接口"}}"""))
            assertNull(api.home())
            server.takeRequest()

            server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1, 2, 3))))
            assertArrayEquals(byteArrayOf(1, 2, 3), api.download("/api/images/img1"))
            assertEquals("Bearer family-key", server.takeRequest().getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}
