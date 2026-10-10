package ian.dev.zaizai.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 系统 TTS 的薄封装：引擎还没准备好时先记下要说的话，准备好再说。 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var waiting: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.CHINESE)
        tts.setSpeechRate(0.9f)
        ready = true
        waiting?.let { speak(it) }
    }

    fun speak(text: String) {
        if (!ready) {
            waiting = text
            return
        }
        waiting = null
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "board")
    }

    fun stop() {
        waiting = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}
