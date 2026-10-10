package ian.dev.zaizai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

/** 桌面小组件：大时钟 + 大字显示下一件该做的事，可以代替系统时钟。内容来自本机缓存，每次看板状态变化时刷新；点击进入 App（会顺便同步）。 */
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
            badge(context, views, "还没同步", done = false)
            views.setTextViewText(R.id.widget_sub, if (state.config.isComplete) "点这里打开" else "点这里设置")
            views.setTextViewText(R.id.widget_footer, context.getString(R.string.app_name))
            return views
        }

        val content = BoardLogic.widgetContent(board)
        badge(context, views, content.big, content.done)
        views.setTextViewText(R.id.widget_sub, content.sub)
        val synced = state.syncedAt?.let { BizDate.hhmm(it + state.clockOffset) } ?: "--:--"
        views.setTextViewText(R.id.widget_footer, if (state.offline) "${board.nickname} · 离线 · 最后同步 $synced" else "${board.nickname} · 最后同步 $synced")
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
