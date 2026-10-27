package com.contextswitcher.android.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresPermission
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import kotlin.concurrent.thread

/// One dictated sentence through Android's [SpeechRecognizer]: from the
/// phone's microphone, or — with a [carMic] — from the car's, which a head-unit
/// app hears only through [CarAudioRecord], piped into the recognizer as an
/// external audio source (Android 13 and later).
/// `onDone(text, error)` runs on the main thread with exactly one of the two.
///
/// The car path is not verifiable without a head unit — try on the car or the
/// Desktop Head Unit.
// [impl->dsn~android-auto-message~1]
// [impl->dsn~android-voice-tour~1]
class Dictation(private val context: Context, private val carMic: CarContext? = null, private val onDone: (String?, String?) -> Unit) {

    @Volatile private var stopped = false
    private var recognizer: SpeechRecognizer? = null

    /// Call on the main thread (a [SpeechRecognizer] requirement).
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onDone(null, "No speech recognition on this phone.")
            return
        }
        if (carMic != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onDone(null, "Dictation needs Android 13 or later.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    finish(text, if (text.isNullOrBlank()) "Nothing understood." else null)
                }

                override fun onError(error: Int) = finish(null, "Speech recognition failed (error $error).")
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            if (carMic != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pipeCarMic(carMic, intent)
            }
            it.startListening(intent)
        }
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private fun pipeCarMic(carMic: CarContext, intent: Intent) {
        val (source, sink) = ParcelFileDescriptor.createPipe()
        val record = CarAudioRecord.create(carMic)
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE)
        thread(name = "car-dictation") {
            // The recognizer closing its end ends the write with an IOException: done either way.
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(sink).use { out ->
                    record.startRecording()
                    val buffer = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
                    while (!stopped) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            runCatching { record.stopRecording() }
        }
    }

    /// Ends the recording and the recognizer without a result; main thread.
    fun stop() {
        stopped = true
        recognizer?.destroy()
        recognizer = null
    }

    private fun finish(text: String?, error: String?) {
        stop()
        onDone(text?.takeIf { error == null }, error)
    }
}
