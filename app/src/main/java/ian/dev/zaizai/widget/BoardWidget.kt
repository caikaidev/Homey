package ian.dev.zaizai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import ian.dev.zaizai.MainActivity
import ian.dev.zaizai.R
import ian.dev.zaizai.ZaizaiApp
import ian.dev.zaizai.data.BizDate
import ian.dev.zaizai.data.BoardLogic
import ian.dev.zaizai.data.BoardState

/** 桌面小组件。内容来自本机缓存，每次看板状态变化时刷新；点击进入 App（会顺便同步）。 */
class BoardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val state = (context.applicationContext as ZaizaiApp).repository.state.value
        manager.updateAppWidget(ids, BoardWidget.views(context, state))
    }
}

object BoardWidget {
    private val ITEM_IDS = intArrayOf(R.id.widget_item1, R.id.widget_item2, R.id.widget_item3)

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
            views.setTextViewText(R.id.widget_count, "还没同步")
            ITEM_IDS.forEach { views.setViewVisibility(it, View.GONE) }
            views.setTextViewText(R.id.widget_footer, if (state.config.isComplete) "点这里打开 App 同步" else "点这里打开 App 完成设置")
            return views
        }

        views.setTextViewText(R.id.widget_title, "${board.nickname}今天的事 · ${BizDate.monthDay(board.date)} ${board.weekday}")
        views.setTextViewText(
            R.id.widget_count,
            when {
                board.today.isEmpty() -> "今天没有要做的事"
                board.pendingCount == 0 -> "都做完了"
                else -> "还剩 ${board.pendingCount} 件"
            },
        )
        val lines = BoardLogic.summaryLines(board)
        ITEM_IDS.forEachIndexed { i, id ->
            val line = lines.getOrNull(i)
            views.setViewVisibility(id, if (line == null) View.GONE else View.VISIBLE)
            views.setTextViewText(id, line.orEmpty())
        }
        val synced = state.syncedAt?.let { BizDate.hhmm(it + state.clockOffset) } ?: "--:--"
        views.setTextViewText(R.id.widget_footer, if (state.offline) "离线 · 最后同步 $synced" else "最后同步 $synced")
        return views
    }
}
