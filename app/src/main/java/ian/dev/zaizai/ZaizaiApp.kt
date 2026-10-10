package ian.dev.zaizai

import android.app.Application
import ian.dev.zaizai.data.BoardRepository
import ian.dev.zaizai.sync.Notifier
import ian.dev.zaizai.sync.Sync

class ZaizaiApp : Application() {
    val repository by lazy { BoardRepository(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        if (repository.state.value.config.isComplete) Sync.schedulePeriodic(this)
    }
}
