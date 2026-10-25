package com.contextswitcher.android.voice

import android.content.Context
import android.speech.tts.TextToSpeech

/// Reads text aloud with the phone's text-to-speech engine (on device, no
/// service account): the car plays it over its speakers while the phone is
/// connected. Text spoken before the engine is ready waits for it.
// [impl->dsn~android-voice-tour~1]
class Speaker(context: Context) {

    private var ready = false
    private val pending = ArrayList<String>()
    // ponytail: the engine's default voice reads English and German alike;
    // detect the language per text when that grates.
    private val tts = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            pending.forEach { say(it) }
        }
        pending.clear()
    }

    /// Speaks `text` after whatever is being said.
    fun speak(text: String) {
        if (ready) say(text) else pending += text
    }

    /// Stops speaking and speaks `text`.
    fun speakNow(text: String) {
        tts.stop()
        speak(text)
    }

    fun stop() = tts.stop()

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun say(text: String) {
        // Chunked: an engine refuses one utterance above getMaxSpeechInputLength (4000 chars).
        text.chunked(TextToSpeech.getMaxSpeechInputLength()).forEachIndexed { i, chunk ->
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, "cs-$i-${chunk.hashCode()}")
        }
    }
}
