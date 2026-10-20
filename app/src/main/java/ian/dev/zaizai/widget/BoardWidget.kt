package ian.dev.zaizai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ian.dev.zaizai.MainActivity
import ian.dev.zaizai.R
import ian.dev.zaizai.ZaizaiApp
import ian.dev.zaizai.data.BizDate
import ian.dev.zaizai.data.BoardLogic
import ian.dev.zaizai.data.BoardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 桌面小组件：大时钟 + 大字显示下一件该做的事，可以代替系统时钟；喇叭按钮念出今天还剩的事和手机电量。内容来自本机缓存，每次看板状态变化时刷新；点击进入 App（会顺便同步）。 */
class BoardWidgetProvider : AppWidgetProvider() {
    /** 旧版小组件按钮发的广播（更新后第一次刷新小组件前可能还会收到），转给播报服务。 */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BoardWidget.ACTION_SPEAK) return super.onReceive(context, intent)
        SpeakService.start(context)
    }

    /**
     * 系统每 30 分钟（updatePeriodMillis）叫一次。以前这里只把本机缓存重画一遍，不联网，
     * 后台同步又常被小米等系统省电策略拦掉，小组件就一直停在上次打开 App 时的样子。
     * 现在顺便同步一次，同步完 publish 会再刷新小组件。
     */
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as ZaizaiApp
        manager.updateAppWidget(ids, BoardWidget.views(context, app.repository.state.value))
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(SYNC_TIMEOUT_MS) { app.repository.sync() }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        /** 后台广播最多给 60 秒，留足余量。 */
        const val SYNC_TIMEOUT_MS = 25_000L
    }
}

object BoardWidget {
    const val ACTION_SPEAK = "ian.dev.zaizai.widget.SPEAK"

    /** 喇叭按钮念的整段：日期时间、今天还剩的事，再加手机电量（奶奶看不清状态栏的电池图标）。 */
    fun speechText(context: Context): String {
        val app = context.applicationContext as ZaizaiApp
        val board = app.repository.state.value.board
        val now = app.repository.serverNow()
        val body = when {
            board == null -> "${BoardLogic.spokenNow(now)}。还没同步，请打开 App 看看"
            board.date != BizDate.date(now) -> "${BoardLogic.spokenNow(now)}。看板还没换到今天，请打开 App 看看"
            else -> BoardLogic.announce(board, now)
        }
        return battery(context)?.let { (percent, charging) -> "$body。${BoardLogic.spokenBattery(percent, charging)}" } ?: body
    }

    /** 当前电量（0–100）和是否在充电；读不到返回 null。 */
    private fun battery(context: Context): Pair<Int, Boolean>? {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return null
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent !in 0..100) return null
        return percent to manager.isCharging
    }

    fun render(context: Context, state: BoardState) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, BoardWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, views(context, state))
    }

    fun views(context: Context, state: BoardState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_board)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        // 直接启动前台服务来念：广播只给 10 秒左右，念到电量就被系统掐断
        val speakIntent = Intent(context, SpeakService::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val speak = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, 1, speakIntent, flags)
        else PendingIntent.getService(context, 1, speakIntent, flags)
        views.setOnClickPendingIntent(R.id.widget_speak, speak)

        val board = state.board
        if (board == null) {
            badge(context, views, "还没同步", done = false)
            views.setTextViewText(R.id.widget_sub, if (state.config.isComplete) "点这里打开" else "点这里设置")
            return views
        }

        // 过了零点还没同步上：别把昨天的事当成今天的显示
        if (board.date != BizDate.date(System.currentTimeMillis() + state.clockOffset)) {
            badge(context, views, "点开看看", done = false)
            views.setTextViewText(R.id.widget_sub, "还没换到今天")
            return views
        }
        val content = BoardLogic.widgetContent(board)
        badge(context, views, content.big, content.done)
        views.setTextViewText(R.id.widget_sub, content.sub)
        return views
    }

    /** 待做：大字事项名。做完：大字换成蹦跳的小牛崽，右边的字放大一点。 */
    private fun badge(context: Context, views: RemoteViews, text: String, done: Boolean) {
        views.setTextViewText(R.id.widget_big, text)
        views.setViewVisibility(R.id.widget_big, if (done) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_calf, if (done) View.VISIBLE else View.GONE)
        views.setContentDescription(R.id.widget_badge, text)
        views.setTextViewTextSize(R.id.widget_sub, TypedValue.COMPLEX_UNIT_SP, if (done) 26f else 20f)
        views.setInt(R.id.widget_badge, "setBackgroundResource", if (done) R.drawable.widget_badge_done else R.drawable.widget_badge_pending)
        views.setTextColor(R.id.widget_big, ContextCompat.getColor(context, if (done) R.color.widget_done else R.color.widget_accent))
    }
}
