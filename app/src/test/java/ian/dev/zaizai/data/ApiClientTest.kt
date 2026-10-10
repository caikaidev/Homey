package ian.dev.zaizai.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    private fun client(recorder: String = "奶奶") =
        ApiClient(server.url("/").toString().trimEnd('/'), "family-key", recorder, clock = { 1_000L })

    private val boardJson = """
        {"date":"2026-10-10","weekday":"周六","nickname":"牛牛","pending_count":1,
         "today":[
           {"task_id":"t1","title":"钙","kind":"supplement","note":"","priority":0,"slot":"19:00","status":"pending","completion":null},
           {"task_id":"t2","title":"AD","kind":"supplement","note":"滴嘴里","priority":1,"slot":"08:00","status":"done",
            "completion":{"id":"c1","result":"done","reason":"","recorded_by":"奶奶","recorded_at":1791590400000,"source":"app","can_undo":false}}
         ],
         "tomorrow":{"date":"2026-10-11","items":[{"task_id":"t1","title":"钙","kind":"supplement","slot":"19:00"}]},
         "serverTime":1791600000000}
    """.trimIndent()

    @Test
    fun `看板按契约解析，带口令和记录人`() = runTest {
        server.enqueue(MockResponse().setBody(boardJson))
        val fetched = client("奶 奶").board()
        val req = server.takeRequest()
        assertEquals("/api/board", req.path)
        assertEquals("Bearer family-key", req.getHeader("Authorization"))
        assertEquals("%E5%A5%B6%20%E5%A5%B6", req.getHeader("X-Recorder"))

        val b = fetched.board
        assertEquals("牛牛", b.nickname)
        assertEquals(1, b.pendingCount)
        assertEquals(listOf("t1", "t2"), b.today.map { it.taskId })
        assertNull(b.today[0].completion)
        assertEquals("滴嘴里", b.today[1].note)
        assertEquals(1_791_590_400_000L, b.today[1].completion!!.recordedAt)
        assertEquals("2026-10-11", b.tomorrowDate)
        assertEquals("钙", b.tomorrow.single().title)
        assertEquals(1_000L, fetched.receivedAt)
        // 缓存的原文能还原出同一个看板
        assertEquals(b, Board.parse(JSONObject(fetched.raw)))
    }

    @Test
    fun `登记带 source，撤销不带`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"created":true}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val api = client()
        api.send(PendingOp("1", OpType.CHECKIN, "t1", "2026-10-10", "19:00", "widget", 0))
        api.send(PendingOp("2", OpType.UNDO, "t1", "2026-10-10", "19:00", "app", 0))

        val checkin = server.takeRequest()
        assertEquals("POST", checkin.method)
        assertEquals("/api/checkin", checkin.path)
        val body = JSONObject(checkin.body.readUtf8())
        assertEquals("t1", body.getString("task_id"))
        assertEquals("2026-10-10", body.getString("date"))
        assertEquals("19:00", body.getString("slot"))
        assertEquals("widget", body.getString("source"))

        val undo = server.takeRequest()
        assertEquals("/api/undo", undo.path)
        assertEquals(false, JSONObject(undo.body.readUtf8()).has("source"))
    }

    @Test
    fun `错误按服务端的 code 和 message 抛出`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"error":{"code":"undo_expired","message":"超过 2 分钟，不能撤销了"}}"""),
        )
        try {
            client().send(PendingOp("1", OpType.UNDO, "t1", "2026-10-10", "19:00", "app", 0))
            fail("应当抛错")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
            assertEquals("undo_expired", e.code)
            assertEquals("超过 2 分钟，不能撤销了", e.message)
        }
    }

    @Test
    fun `被重定向到登录页时不跟随，直接报错`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/login"))
        try {
            client().board()
            fail("应当抛错")
        } catch (e: ApiException) {
            assertEquals("redirect", e.code)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `设置里取照护人名单`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"child_nickname":"","members":{"parents":[{"email":"a@b.c","name":"爸爸"}],"caregivers":["奶奶"," ","外婆"]}}""",
            ),
        )
        val s = client().settings()
        assertEquals("崽崽", s.nickname)
        assertEquals(listOf("奶奶", "外婆"), s.caregivers)
    }

    @Test
    fun normalizeServer() {
        assertEquals("https://zaizai.example.workers.dev", ApiClient.normalizeServer(" zaizai.example.workers.dev/ "))
        assertEquals("https://a.example.com", ApiClient.normalizeServer("https://a.example.com/api/"))
        assertEquals("http://192.168.1.2:8787", ApiClient.normalizeServer("http://192.168.1.2:8787"))
        assertNull(ApiClient.normalizeServer(""))
        assertNull(ApiClient.normalizeServer("ftp://a.example.com"))
        assertNull(ApiClient.normalizeServer("not a url"))
    }

    @Test
    fun `待上传队列能存能取`() {
        val ops = listOf(
            PendingOp("1", OpType.CHECKIN, "t1", "2026-10-10", "19:00", "app", 5),
            PendingOp("2", OpType.UNDO, "t2", "2026-10-09", "08:00", "app", 6),
        )
        assertEquals(ops, PendingOp.parseList(PendingOp.toJson(ops)))
        assertEquals(emptyList<PendingOp>(), PendingOp.parseList("not json"))
        assertEquals(emptyList<PendingOp>(), PendingOp.parseList(null))
    }
}
