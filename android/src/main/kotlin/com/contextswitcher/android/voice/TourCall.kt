package com.contextswitcher.android.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.contextswitcher.android.ui.TaskGroupUi
import com.contextswitcher.android.watch.TaskWatchService
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.ssh.SshCommandRunner
import java.util.concurrent.Executors

/// The tour as a phone call: a self-managed Telecom call, so Android Auto
/// shows it like any call (mute, hang up) and routes the car's microphone
/// and speakers to it; the phone's screen may stay off. The call speaks the
/// card, then listens: "next", "previous", "repeat" and "hang up" (or the
/// German words) steer, anything else is sent as the reply, and a finished
/// turn is read out as on the screen.
// [impl->dsn~android-voice-tour~1]
object TourCall {

    enum class Command { NEXT, PREVIOUS, REPEAT, HANG_UP }

    private val COMMANDS = mapOf(
        Command.NEXT to setOf("next", "weiter", "nächster", "nächste", "next task"),
        Command.PREVIOUS to setOf("previous", "back", "zurück", "vorheriger", "vorherige"),
        Command.REPEAT to setOf("repeat", "again", "wiederhole", "wiederholen", "nochmal", "noch mal"),
        Command.HANG_UP to setOf("hang up", "stop", "auflegen", "ende", "end call", "bye"),
    )

    /// The command a recognized text is, or null for a reply to send.
    fun command(text: String): Command? {
        val words = text.lowercase().replace(Regex("[^\\p{L} ]"), "").trim()
        return COMMANDS.entries.firstOrNull { words in it.value }?.key
    }

    class Setup(val tour: VoiceTour, val groups: List<TaskGroupUi>, val ssh: SshCommandRunner, val messageSender: MessageSender)

    /// True while a call runs; read by the screen's button.
    val isActive = mutableStateOf(false)

    @Volatile internal var pending: Setup? = null
    @Volatile internal var active: TourConnection? = null

    /// Places the call; null on success, else why not.
    fun start(context: Context, setup: Setup): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return "Allow the microphone first (Dictate asks for it)."
        }
        val telecom = context.getSystemService(TelecomManager::class.java)
        val handle = PhoneAccountHandle(ComponentName(context, TourCallService::class.java), "tour")
        telecom.registerPhoneAccount(PhoneAccount.builder(handle, "ContextSwitcher tour")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED).build())
        pending = setup
        val extras = Bundle().apply { putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle) }
        return runCatching {
            telecom.placeCall(Uri.fromParts("tour", setup.tour.category, null), extras)
            null
        }.getOrElse { pending = null; it.message ?: it.toString() }
    }

    fun hangUp() = active?.hangUp()
}

class TourCallService : ConnectionService() {

    override fun onCreateOutgoingConnection(from: PhoneAccountHandle?, request: ConnectionRequest): Connection {
        val setup = TourCall.pending ?: return Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
        TourCall.pending = null
        return TourConnection(applicationContext, setup).also { it.begin() }
    }

    override fun onCreateOutgoingConnectionFailed(from: PhoneAccountHandle?, request: ConnectionRequest?) {
        TourCall.pending = null
    }
}

class TourConnection(private val context: Context, private val setup: TourCall.Setup) : Connection() {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val speaker = Speaker(context, voiceCall = true)
    private var dictation: Dictation? = null
    @Volatile private var ended = false

    private val refresh = object : Runnable {
        override fun run() {
            io.execute {
                val changed = runCatching { setup.tour.refreshStatuses(setup.groups) }.getOrDefault(false)
                if (changed && setup.tour.current?.status?.lowercase() != "working") announce()
            }
            main.postDelayed(this, 10_000)
        }
    }

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
        setCallerDisplayName("ContextSwitcher: ${setup.tour.category}", TelecomManager.PRESENTATION_ALLOWED)
    }

    fun begin() {
        setActive()
        TourCall.active = this
        TourCall.isActive.value = true
        speaker.onIdle = { main.post { listen() } }
        io.execute { say(setup.tour.overview() + " " + setup.tour.announcement()) }
        main.postDelayed(refresh, 10_000)
    }

    private fun announce() = io.execute { say(setup.tour.announcement()) }

    /// Speaks, interrupting the current text and listening; listening resumes after it.
    private fun say(text: String) = main.post {
        if (ended) return@post
        dictation?.stop()
        dictation = null
        speaker.speakNow(text)
    }

    private fun listen() {
        if (ended || dictation != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        dictation = Dictation(context) { text, _ ->
            dictation = null
            if (text == null) main.postDelayed({ listen() }, 1_000) else heard(text)
        }.also { it.start() }
    }

    private fun heard(text: String) {
        val tour = setup.tour
        when (TourCall.command(text)) {
            TourCall.Command.NEXT -> if (tour.next()) announce() else say("That was the last task.")
            TourCall.Command.PREVIOUS -> if (tour.previous()) announce() else say("This is the first task.")
            TourCall.Command.REPEAT -> announce()
            TourCall.Command.HANG_UP -> hangUp()
            null -> send(VoiceTour.spokenReply(text))
        }
    }

    private fun send(text: String) {
        val task = setup.tour.current?.task ?: return
        io.execute {
            val error = runCatching { setup.messageSender.send(task.remote()!!, task.tmux()!!.target(), text) }
                .getOrElse { it.message ?: it.toString() }
            if (error != null) runCatching { TaskWatchService.enqueuedStore(context).add(task.id(), text) }
            say(if (error == null) "Sent." else "Not sent, kept on the phone.")
        }
    }

    fun hangUp() = main.post {
        if (ended) return@post
        ended = true
        main.removeCallbacks(refresh)
        dictation?.stop()
        speaker.shutdown()
        io.shutdown()
        TourCall.active = null
        TourCall.isActive.value = false
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onDisconnect() {
        hangUp()
    }

    override fun onAbort() {
        hangUp()
    }
}
