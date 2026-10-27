package com.contextswitcher.android.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/// Reads text aloud with the phone's text-to-speech engine (on device, no
/// service account): the car plays it over its speakers while the phone is
/// connected. Text spoken before the engine is ready waits for it. Each text
/// is read in the voice of the language it looks like, English or German.
/// `voiceCall` plays through the call's audio route (`TourCall`).
// [impl->dsn~android-voice-tour~1]
class Speaker(context: Context, voiceCall: Boolean = false) {

    private var ready = false
    private val pending = ArrayList<String>()
    private var lastId: String? = null

    /// Called (on the engine's thread) when the last queued text has been read.
    var onIdle: (() -> Unit)? = null

    private val tts = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            pending.forEach { say(it) }
        }
        pending.clear()
    }.also {
        if (voiceCall) {
            it.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        }
        it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
        })
    }

    private fun finished(id: String?) {
        if (id != null && id == lastId) onIdle?.invoke()
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
        tts.setLanguage(localeOf(text))
        // Chunked: an engine refuses one utterance above getMaxSpeechInputLength (4000 chars).
        text.chunked(TextToSpeech.getMaxSpeechInputLength()).forEachIndexed { i, chunk ->
            val id = "cs-$i-${chunk.hashCode()}"
            lastId = id
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    companion object {
        private val GERMAN = Regex("""\b(und|nicht|ich|ist|das|die|der|eine|auch|mit|für|dass|wird|werden|habe|sind)\b""", RegexOption.IGNORE_CASE)
        private val ENGLISH = Regex("""\b(the|and|is|not|you|with|for|that|this|are|have|it)\b""", RegexOption.IGNORE_CASE)

        /// German when the text has more German than English function words;
        /// the card's English frame ("Task 1 of 3 … Claude said:") does not
        /// outweigh a German answer of a few sentences.
        // ponytail: word counting; an engine's language detector when a mixed text reads wrong.
        fun localeOf(text: String): Locale =
            if (GERMAN.findAll(text).count() > ENGLISH.findAll(text).count()) Locale.GERMAN else Locale.ENGLISH
    }
}
