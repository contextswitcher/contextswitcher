package com.contextswitcher.android.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.contextswitcher.android.ui.TaskGroupUi
import com.contextswitcher.android.ui.buildTaskListModel
import com.contextswitcher.android.watch.TaskWatchService
import com.contextswitcher.queue.MessageSender
import com.contextswitcher.ssh.SshCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/// The voice tour on the phone (a mount and the car's Bluetooth audio do):
/// the spoken card as text, big buttons to step, repeat and dictate, and the
/// dictated reply shown before *Send* — nothing reaches a session unconfirmed.
// [impl->dsn~android-voice-tour~1]
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTourScreen(
    category: String,
    repoDir: File,
    ssh: SshCommandRunner,
    messageSender: MessageSender,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = remember { Speaker(context) }
    var groups by remember { mutableStateOf<List<TaskGroupUi>>(emptyList()) }
    var tour by remember { mutableStateOf<VoiceTour?>(null) }
    var card by remember { mutableStateOf("Loading …") }
    var heard by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var dictation by remember { mutableStateOf<Dictation?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            dictation?.stop()
            speaker.shutdown()
        }
    }

    fun show(text: String, speak: String = text) {
        card = text
        speaker.speakNow(speak)
    }

    fun announce() {
        val t = tour ?: return
        scope.launch { show(withContext(Dispatchers.IO) { t.announcement() }) }
    }

    LaunchedEffect(category) {
        val loaded = withContext(Dispatchers.IO) {
            groups = runCatching { buildTaskListModel(repoDir.toPath()) }.getOrDefault(emptyList())
            VoiceTour.of(category, groups, ssh)
        }
        tour = loaded
        val first = withContext(Dispatchers.IO) { loaded.announcement() }
        show(first, speak = loaded.overview() + " " + first)
        while (true) {
            delay(10_000)
            val changed = withContext(Dispatchers.IO) { loaded.refreshStatuses(groups) }
            if (changed && TourCall.active == null && loaded.current?.status?.lowercase() != "working") {
                announce()
            }
        }
    }

    fun listen() {
        heard = null
        notice = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            notice = "Allow the microphone to dictate."
            return
        }
        speaker.stop()
        dictation = Dictation(context) { text, error ->
            heard = text
            notice = error
        }.also { it.start() }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else notice = "Allow the microphone to dictate."
    }

    fun send(text: String) {
        val task = tour?.current?.task ?: return
        heard = null
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { messageSender.send(task.remote()!!, task.tmux()!!.target(), text) }.getOrElse { it.message ?: it.toString() }
            }
            if (error != null) {
                withContext(Dispatchers.IO) { TaskWatchService.enqueuedStore(context).add(task.id(), text) }
            }
            notice = if (error == null) "Sent." else "Not sent, kept on the phone: $error"
            speaker.speak(if (error == null) "Sent." else "Not sent, kept on the phone.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$category tour") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { speaker.speakNow(card) }) { Text("Repeat") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(card, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
            heard?.let { text ->
                Text("Heard: $text", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { send(VoiceTour.spokenReply(text)) }, modifier = Modifier.weight(1f)) { Text("Send") }
                    OutlinedButton(onClick = { listen() }, modifier = Modifier.weight(1f)) { Text("Retry") }
                }
            }
            notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val t = tour
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (t?.previous() == true) announce() }, enabled = t?.hasPrevious == true, modifier = Modifier.weight(1f)) { Text("Previous") }
                OutlinedButton(onClick = { if (t?.next() == true) announce() }, enabled = t?.hasNext == true, modifier = Modifier.weight(1f)) { Text("Next") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = t?.current != null,
                    modifier = Modifier.weight(2f),
                ) { Text("Dictate") }
                OutlinedButton(onClick = { send("continue") }, enabled = t?.current != null, modifier = Modifier.weight(1f)) { Text("continue") }
            }
            val inCall by TourCall.isActive
            OutlinedButton(
                onClick = {
                    if (inCall) TourCall.hangUp()
                    else {
                        speaker.stop()
                        dictation?.stop()
                        notice = TourCall.start(context, TourCall.Setup(t!!, groups, ssh, messageSender))
                            ?: "Calling. Say next, previous, repeat or hang up; anything else is sent as the reply."
                    }
                },
                enabled = inCall || t?.current != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (inCall) "Hang up" else "Call (Android Auto)") }
        }
    }
}
