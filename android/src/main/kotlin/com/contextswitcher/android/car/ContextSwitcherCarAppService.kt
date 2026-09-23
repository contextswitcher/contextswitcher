package com.contextswitcher.android.car

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.contextswitcher.android.ui.buildTaskListModel
import com.contextswitcher.android.watch.TaskWatchService
import com.contextswitcher.tasks.Task
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/// ContextSwitcher on the Android Auto head unit: the tasks with a Claude
/// session, the waiting ones first, and per task a dictated or canned message.
/// The app is sideloaded, so Android Auto shows it only with *Unknown sources*
/// on in its developer settings — hence any host may bind.
// [impl->dsn~android-auto-tasks~1]
class ContextSwitcherCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = CarTaskListScreen(carContext)
    }
}

/// SSH and file work of the car screens; tests run it inline.
internal val carIo: Executor = Executors.newSingleThreadExecutor()

/// Reads the clone the phone app synced last (no git sync from the car) and
/// polls the statuses once per load; *Refresh* loads again.
// [impl->dsn~android-auto-tasks~1]
class CarTaskListScreen(carContext: CarContext, private val io: Executor = carIo) : Screen(carContext) {

    private var tasks: List<CarTask>? = null

    init {
        load()
    }

    private fun load() {
        tasks = null
        io.execute {
            val groups = runCatching { buildTaskListModel(File(carContext.filesDir, "taskrepo").toPath()) }.getOrDefault(emptyList())
            val loaded = carTasks(groups, pollStatuses(TaskWatchService.sshFactory(carContext), groups))
            ContextCompat.getMainExecutor(carContext).execute {
                tasks = loaded
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder().setTitle("ContextSwitcher").setStartHeaderAction(Action.APP_ICON)
            .addEndHeaderAction(Action.Builder().setTitle("Refresh").setOnClickListener {
                load()
                invalidate()
            }.build())
            .build()
        val current = tasks ?: return ListTemplate.Builder().setHeader(header).setLoading(true).build()
        val limit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val items = ItemList.Builder().setNoItemsMessage("No task with a Claude session. Sync the phone app first.")
        current.take(limit).forEach { entry ->
            items.addItem(Row.Builder().setTitle(entry.task.title()).addText(entry.status ?: "no status")
                .setOnClickListener { screenManager.push(CarTaskScreen(carContext, entry.task, io)) }
                .build())
        }
        return ListTemplate.Builder().setHeader(header).setSingleList(items.build()).build()
    }
}

/// One task: *Dictate message* and the canned replies. Opening it makes the
/// task the watched one, like the phone's detail screen, where Android lets a
/// car session start the service.
// [impl->dsn~android-auto-message~1]
class CarTaskScreen(carContext: CarContext, private val task: Task, private val io: Executor = carIo) : Screen(carContext) {

    init {
        // ponytail: Android may refuse a foreground-service start from a car
        // session; then there is no finish alert, and messages are sent
        // directly anyway (sendFromCar), so nothing is lost.
        runCatching { TaskWatchService.start(carContext, task) }
    }

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        items.addItem(Row.Builder().setTitle("Dictate message").setOnClickListener {
            screenManager.push(CarDictationScreen(carContext, task, io))
        }.build())
        CANNED.forEach { reply ->
            items.addItem(Row.Builder().setTitle("Send “$reply”").setOnClickListener {
                sendFromCar(carContext, task, reply, io)
            }.build())
        }
        return ListTemplate.Builder()
            .setHeader(Header.Builder().setTitle(task.title()).setStartHeaderAction(Action.BACK).build())
            .setSingleList(items.build()).build()
    }

    companion object {
        internal val CANNED = listOf("continue", "yes", "no")
    }
}

/// Sends `text` to the task's Claude session right away — Claude Code queues
/// it itself while a turn runs. A message that cannot be sent is kept in the
/// phone's enqueued store, so a dead spot on the road never loses it.
// [impl->dsn~android-auto-message~1]
internal fun sendFromCar(carContext: CarContext, task: Task, text: String, io: Executor) {
    io.execute {
        val error = runCatching {
            TaskWatchService.messageSender(carContext, TaskWatchService.sshFactory(carContext))
                .send(task.remote()!!, task.tmux()!!.target(), text)
        }.getOrElse { it.message ?: it.toString() }
        if (error != null) {
            TaskWatchService.enqueuedStore(carContext).add(task.id(), text)
        }
        ContextCompat.getMainExecutor(carContext).execute {
            CarToast.makeText(carContext, if (error == null) "Sent" else "Not sent, kept on the phone: $error", CarToast.LENGTH_LONG).show()
        }
    }
}

/// Dictation with confirmation: listens, shows what was understood, and sends
/// only on *Send* — a misheard sentence must not reach a Claude session that
/// may run without permission prompts.
// [impl->dsn~android-auto-message~1]
class CarDictationScreen(carContext: CarContext, private val task: Task, private val io: Executor = carIo) : Screen(carContext) {

    private var heard: String? = null
    private var problem: String? = null
    private var dictation: CarDictation? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dictation?.stop()
            }
        })
        listen()
    }

    private fun listen() {
        heard = null
        problem = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            problem = "Dictation needs Android 13 or later."
        } else if (ContextCompat.checkSelfPermission(carContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            problem = "Allow the microphone on the phone, then retry."
            carContext.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { _, _ -> }
        } else {
            dictation = CarDictation(carContext) { text, error ->
                heard = text
                problem = error
                invalidate()
            }.also { it.start() }
        }
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder().setTitle(task.title()).setStartHeaderAction(Action.BACK).build()
        val retry = Action.Builder().setTitle("Retry").setOnClickListener {
            dictation?.stop()
            listen()
            invalidate()
        }.build()
        val text = heard
        return when {
            text != null -> MessageTemplate.Builder(text).setHeader(header)
                .addAction(Action.Builder().setTitle("Send").setOnClickListener {
                    sendFromCar(carContext, task, text, io)
                    screenManager.pop()
                }.build())
                .addAction(retry).build()
            problem != null -> MessageTemplate.Builder(problem!!).setHeader(header).addAction(retry).build()
            else -> MessageTemplate.Builder("Listening …").setHeader(header).setLoading(true).build()
        }
    }
}
