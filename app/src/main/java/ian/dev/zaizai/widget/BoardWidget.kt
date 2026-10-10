package ian.dev.zaizai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ian.dev.zaizai.MainActivity
import ian.dev.zaizai.R
import ian.dev.zaizai.ZaizaiApp
import ian.dev.zaizai.data.BizDate
import ian.dev.zaizai.data.BoardLogic
import ian.dev.zaizai.data.BoardState

/** 桌面小组件：大字显示下一件该做的事。内容来自本机缓存，每次看板状态变化时刷新；点击进入 App（会顺便同步）。 */
class BoardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val state = (context.applicationContext as ZaizaiApp).repository.state.value
        manager.updateAppWidget(ids, BoardWidget.views(context, state))
    }
}

object BoardWidget {
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

        val board = state.board
        if (board == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            badge(context, views, "还没同步", done = false)
            views.setTextViewText(R.id.widget_sub, if (state.config.isComplete) "点这里打开" else "点这里设置")
            views.setTextViewText(R.id.widget_footer, "")
            return views
        }

        views.setTextViewText(R.id.widget_title, "${board.nickname} · ${BizDate.monthDay(board.date)} ${board.weekday}")
        val content = BoardLogic.widgetContent(board)
        badge(context, views, content.big, content.done)
        views.setTextViewText(R.id.widget_sub, content.sub)
        val synced = state.syncedAt?.let { BizDate.hhmm(it + state.clockOffset) } ?: "--:--"
        views.setTextViewText(R.id.widget_footer, if (state.offline) "离线 · 最后同步 $synced" else "最后同步 $synced")
        return views
    }

    private fun badge(context: Context, views: RemoteViews, text: String, done: Boolean) {
        views.setTextViewText(R.id.widget_big, text)
        views.setInt(R.id.widget_big, "setBackgroundResource", if (done) R.drawable.widget_badge_done else R.drawable.widget_badge_pending)
        views.setTextColor(R.id.widget_big, ContextCompat.getColor(context, if (done) R.color.widget_done else R.color.widget_accent))
    }
}
