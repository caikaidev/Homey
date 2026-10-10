package ian.dev.zaizai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 服务器给的错误（`{error:{code,message}}`），或者返回了看不懂的内容。网络不通抛 IOException。 */
class ApiException(val status: Int, val code: String, message: String) : Exception(message)

data class RemoteSettings(val nickname: String, val caregivers: List<String>)

/** 看板和收到它的本机时间，用来估计服务器时钟（撤销倒计时按服务器时间算）。 */
data class FetchedBoard(val board: Board, val raw: String, val receivedAt: Long)

/**
 * 照护人端只用到的几个接口，见 docs/API.md。用家庭口令鉴权，记录人放在 `X-Recorder`。
 * 不跟随重定向：Access 没给 `/api/*` 放行时会被重定向到登录页，这里直接报错说清楚。
 */
class ApiClient(
    private val base: String,
    private val familyKey: String,
    private val recorder: String,
    private val http: OkHttpClient = shared,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun ping(): String = call("GET", "/api/ping").optJSONObject("actor")?.optString("name").orEmpty()

    suspend fun settings(): RemoteSettings {
        val json = call("GET", "/api/settings")
        val caregivers = json.optJSONObject("members")?.optJSONArray("caregivers")
        return RemoteSettings(
            nickname = json.optString("child_nickname").ifBlank { "崽崽" },
            caregivers = (0 until (caregivers?.length() ?: 0)).mapNotNull { i ->
                caregivers?.optString(i)?.trim()?.takeIf { it.isNotEmpty() }
            },
        )
    }

    suspend fun board(): FetchedBoard {
        val json = call("GET", "/api/board")
        val board = try {
            Board.parse(json)
        } catch (e: JSONException) {
            throw ApiException(200, "bad_response", "服务器返回的看板看不懂，请检查服务器地址")
        }
        return FetchedBoard(board, json.toString(), clock())
    }

    suspend fun send(op: PendingOp) {
        val body = JSONObject().put("task_id", op.taskId).put("date", op.date).put("slot", op.slot)
        when (op.type) {
            OpType.CHECKIN -> call("POST", "/api/checkin", body.put("source", op.source))
            OpType.UNDO -> call("POST", "/api/undo", body)
        }
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(base + path)
                .header("Authorization", "Bearer $familyKey")
                .header("X-Recorder", encodeRecorder(recorder))
                .header("Accept", "application/json")
                .method(method, body?.toString()?.toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val json = try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    null
                }
                if (res.isRedirect) {
                    throw ApiException(res.code, "redirect", "服务器要求网页登录，请让家长检查服务器地址和 Access 放行设置")
                }
                if (!res.isSuccessful) {
                    val err = json?.optJSONObject("error")
                    throw ApiException(
                        res.code,
                        err?.optString("code").orEmpty().ifBlank { "http_${res.code}" },
                        err?.optString("message").orEmpty().ifBlank { "服务器出错了（${res.code}）" },
                    )
                }
                json ?: throw ApiException(res.code, "bad_response", "服务器返回的不是看板数据，请检查服务器地址")
            }
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        val shared: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        /** 用户填的地址 → `https://host[:port][/path]`，去掉结尾的 `/` 和 `/api`。不合法返回 null。 */
        fun normalizeServer(raw: String): String? {
            var s = raw.trim()
            if (s.isEmpty()) return null
            if (!s.contains("://")) s = "https://$s"
            s = s.trimEnd('/').removeSuffix("/api").trimEnd('/')
            val url = s.toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" && url.scheme != "http") return null
            return s
        }

        /** 与服务端 decodeURIComponent 对应；URLEncoder 把空格编成 `+`，这里改回 `%20`。 */
        fun encodeRecorder(name: String): String = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    }
}
