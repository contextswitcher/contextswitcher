package com.contextswitcher.android.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.contextswitcher.android.ui.TaskGroupUi
import com.contextswitcher.android.ui.buildTaskListModel
import com.contextswitcher.android.voice.Speaker
import com.contextswitcher.android.voice.VoiceTour
import com.contextswitcher.android.watch.TaskWatchService
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/// *Tour*: which category to walk through.
// [impl->dsn~android-voice-tour~1]
class CarTourCategoryScreen(carContext: CarContext, private val io: Executor = carIo) : Screen(carContext) {

    private var groups: List<TaskGroupUi>? = null

    init {
        io.execute {
            val loaded = runCatching { buildTaskListModel(File(carContext.filesDir, "taskrepo").toPath()) }.getOrDefault(emptyList())
            ContextCompat.getMainExecutor(carContext).execute {
                groups = loaded
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder().setTitle("Voice tour").setStartHeaderAction(Action.BACK).build()
        val current = groups ?: return ListTemplate.Builder().setHeader(header).setLoading(true).build()
        val items = ItemList.Builder().setNoItemsMessage("No category with a task to talk to.")
        VoiceTour.categories(current).forEach { category ->
            items.addItem(Row.Builder().setTitle(category).setOnClickListener {
                screenManager.push(CarTourScreen(carContext, category, current, io))
            }.build())
        }
        return ListTemplate.Builder().setHeader(header).setSingleList(items.build()).build()
    }
}

/// The tour on the head unit: the spoken card is also shown, *Dictate* replies
/// into the current task's session (with the spoken-reply hint), *Next* and
/// *Previous* step, *Repeat* speaks again. Every ten seconds the statuses are
/// re-read; a finished turn of the current task is read aloud.
// [impl->dsn~android-voice-tour~1]
class CarTourScreen(
    carContext: CarContext,
    private val category: String,
    private val groups: List<TaskGroupUi>,
    private val io: Executor = carIo,
) : Screen(carContext) {

    private var tour: VoiceTour? = null
    private var text = "Loading …"
    private val speaker = Speaker(carContext)
    private val poller = Executors.newSingleThreadScheduledExecutor()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                poller.shutdownNow()
                speaker.shutdown()
            }
        })
        io.execute {
            val loaded = VoiceTour.of(category, groups, TaskWatchService.sshFactory(carContext))
            val overview = loaded.overview()
            val card = loaded.announcement()
            ContextCompat.getMainExecutor(carContext).execute {
                tour = loaded
                show(card, speak = "$overview $card")
            }
        }
        poller.scheduleWithFixedDelay({
            val t = tour ?: return@scheduleWithFixedDelay
            if (t.refreshStatuses(groups) && t.current?.status?.lowercase() != "working") {
                announce(t)
            }
        }, 10, 10, TimeUnit.SECONDS)
    }

    private fun announce(t: VoiceTour) {
        io.execute {
            val card = t.announcement()
            ContextCompat.getMainExecutor(carContext).execute { show(card, speak = card) }
        }
    }

    private fun show(card: String, speak: String) {
        text = card
        speaker.speakNow(speak)
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val t = tour
        val header = Header.Builder().setTitle(t?.category ?: category).setStartHeaderAction(Action.BACK)
        if (t != null) {
            header.addEndHeaderAction(Action.Builder().setTitle("Previous").setEnabled(t.hasPrevious).setOnClickListener {
                if (t.previous()) announce(t)
            }.build())
            header.addEndHeaderAction(Action.Builder().setTitle("Repeat").setOnClickListener { speaker.speakNow(text) }.build())
        }
        val template = MessageTemplate.Builder(text).setHeader(header.build())
        val task = t?.current?.task
        if (t == null) {
            template.setLoading(true)
        } else if (task != null) {
            template.addAction(Action.Builder().setTitle("Dictate").setOnClickListener {
                speaker.stop()
                screenManager.push(CarDictationScreen(carContext, task, io, VoiceTour::spokenReply))
            }.build())
            template.addAction(Action.Builder().setTitle("Next").setEnabled(t.hasNext).setOnClickListener {
                if (t.next()) announce(t)
            }.build())
        }
        return template.build()
    }
}
