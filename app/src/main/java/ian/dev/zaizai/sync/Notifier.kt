package ian.dev.zaizai.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ian.dev.zaizai.MainActivity
import ian.dev.zaizai.R
import ian.dev.zaizai.data.BizDate
import ian.dev.zaizai.data.BoardLogic
import ian.dev.zaizai.data.BoardState
import ian.dev.zaizai.data.LocalStore

/** 到点提醒：后台同步时发现“到点还没登记”的项，每项只提醒一次。不承诺精确到分钟。 */
object Notifier {
    private const val CHANNEL = "reminders"
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, "到点提醒", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "到时间还没登记的事项" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notifyDue(context: Context, state: BoardState) {
        val board = state.board ?: return
        if (!canNotify(context)) return
        val now = System.currentTimeMillis() + state.clockOffset
        val today = BizDate.date(now)
        val store = LocalStore(context)
        // 只留今天的记录，旧的自然清掉
        val notified = store.readNotified().filter { it.contains("|$today|") }.toSet()
        val due = BoardLogic.dueForNotice(board, today, BizDate.hhmm(now), notified)
        if (due.isEmpty()) {
            store.writeNotified(notified)
            return
        }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pending = board.pendingCount
        val text = due.joinToString("、") { "${it.slot} ${it.title}" }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${board.nickname}还有 $pending 件事没登记")
            .setContentText("到点了：$text")
            .setStyle(NotificationCompat.BigTextStyle().bigText("到点了：$text"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            return
        }
        store.writeNotified(notified + due.map { board.key(it) })
    }
}
