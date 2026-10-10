package ian.dev.zaizai

import android.app.Application
import ian.dev.zaizai.data.BoardRepository
import ian.dev.zaizai.sync.Notifier
import ian.dev.zaizai.sync.Sync
import ian.dev.zaizai.ui.Speaker

class ZaizaiApp : Application() {
    val repository by lazy { BoardRepository(this) }

    /** 小组件喇叭按钮用的语音，App 进程活着就一直留着，免得每次点都重新连 TTS 引擎。 */
    val voice by lazy { Speaker(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        if (repository.state.value.config.isComplete) Sync.schedulePeriodic(this)
    }
}
