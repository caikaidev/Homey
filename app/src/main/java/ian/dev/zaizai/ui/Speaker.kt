package ian.dev.zaizai.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** 系统 TTS 的薄封装：引擎还没准备好时先记下要说的话，准备好再说。说完（或说不了）回调 onDone。 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var waiting: Pair<String, (() -> Unit)?>? = null
    private var onDone: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            waiting?.second?.invoke()
            waiting = null
            return
        }
        val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.CHINESE)
        tts.setSpeechRate(0.9f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish()
        })
        ready = true
        waiting?.let { (text, done) -> speak(text, done) }
    }

    @Synchronized
    private fun finish() {
        val done = onDone
        onDone = null
        done?.invoke()
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ready) {
            waiting?.second?.invoke()
            waiting = text to onDone
            return
        }
        waiting = null
        finish() // 被新的一句打断，上一句算说完
        synchronized(this) { this.onDone = onDone }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "board")
    }

    fun stop() {
        waiting?.second?.invoke()
        waiting = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        stop()
        finish()
        tts.shutdown()
    }
}
