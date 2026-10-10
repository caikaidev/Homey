package ian.dev.zaizai

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ian.dev.zaizai.data.BoardLogic
import ian.dev.zaizai.data.SyncResult
import ian.dev.zaizai.sync.Notifier
import ian.dev.zaizai.ui.BoardScreen
import ian.dev.zaizai.ui.SetupScreen
import ian.dev.zaizai.ui.Speaker
import ian.dev.zaizai.ui.theme.ZaiZaiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository get() = (application as ZaizaiApp).repository
    private var speaker: Speaker? = null
    private var lastSpokenAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lastSpokenAt = savedInstanceState?.getLong(KEY_SPOKEN_AT) ?: 0L

        setContent {
            ZaiZaiTheme {
                val state by repository.state.collectAsState()
                var editing by rememberSaveable { mutableStateOf(false) }

                if (!state.config.isComplete || editing) {
                    BackHandler(enabled = editing) { editing = false }
                    SetupScreen(
                        initial = state.config,
                        canCancel = state.config.isComplete,
                        onSave = { config ->
                            repository.saveConfig(config)
                            editing = false
                            lifecycleScope.launch { repository.sync() }
                        },
                        onCancel = { editing = false },
                    )
                } else {
                    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(this@MainActivity)) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    BoardScreen(
                        state = state,
                        serverNow = { repository.serverNow() },
                        onCheckin = { repository.checkin(it) },
                        onUndo = { repository.undo(it) },
                        onRefresh = { lifecycleScope.launch { repository.sync() } },
                        onSettings = { editing = true },
                    )
                }
            }
        }

        // 在前台时：打开就同步并播报；之后每分钟同步一次，跨零点也会自动换到新的一天
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val result = repository.sync()
                if (result != SyncResult.NOT_CONFIGURED) speakIfDue()
                while (true) {
                    delay(60_000)
                    repository.sync()
                }
            }
        }
    }

    /** 打开 App 时播报一次；10 分钟内重复打开不再念。设置里可以关掉。 */
    private fun speakIfDue() {
        val state = repository.state.value
        val board = state.board ?: return
        if (!state.config.speak) return
        val now = System.currentTimeMillis()
        if (now - lastSpokenAt < SPEAK_INTERVAL_MS) return
        lastSpokenAt = now
        val s = speaker ?: Speaker(this).also { speaker = it }
        s.speak(BoardLogic.speech(board))
    }

    override fun onStop() {
        super.onStop()
        speaker?.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_SPOKEN_AT, lastSpokenAt)
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }

    private companion object {
        const val KEY_SPOKEN_AT = "spoken_at"
        const val SPEAK_INTERVAL_MS = 10 * 60_000L
    }
}
