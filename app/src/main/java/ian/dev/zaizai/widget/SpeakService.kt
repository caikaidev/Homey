package ian.dev.zaizai.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ian.dev.zaizai.R
import ian.dev.zaizai.ZaizaiApp

/**
 * 小组件喇叭按钮的播报。用短暂的前台服务念完整段再停下：
 * 放在广播里念，系统只给 10 秒左右，长一点的播报（念到电量时）会被掐断。
 * 念的时候通知栏有一条“正在播报”，念完自动消失。
 */
class SpeakService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stop = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel(this)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在播报")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: RuntimeException) {
            // 系统不让开前台服务时照样念，只是可能念不完
        }
        val app = application as ZaizaiApp
        // 念完（或被下一次点击打断）就停；最多留 60 秒，防止引擎没回调时一直挂着
        handler.removeCallbacks(stop)
        handler.postDelayed(stop, MAX_MS)
        // 被下一次点击打断时，上一次的回调用旧的 startId，stopSelf(startId) 不会停掉新的这次
        app.voice.speak(BoardWidget.speechText(this)) { handler.post { stopSelf(startId) } }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stop)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "speak"
        private const val NOTIFICATION_ID = 2
        private const val MAX_MS = 60_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, SpeakService::class.java))
            } catch (e: RuntimeException) {
                // 后台不让启动前台服务（旧按钮的广播）：直接念，小组件刷新后就会换成新按钮
                (context.applicationContext as ZaizaiApp).voice.speak(BoardWidget.speechText(context))
            }
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL, "小组件播报", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "点小组件喇叭时，念完之前显示" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
