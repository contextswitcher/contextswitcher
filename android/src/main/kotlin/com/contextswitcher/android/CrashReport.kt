package com.contextswitcher.android

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/// A crash on a device without adb leaves nothing to read: the process
/// stack trace goes to logcat only. The default handler therefore writes it,
/// with the build and the device, to app-private storage before Android kills
/// the process, and [LastCrashDialog] shows it at the next start.
// [impl->dsn~android-crash-report~1]
object CrashReport {

    fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val file = file(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(
                    "${BuildConfig.GIT_COMMIT_LINE}\n" +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Thread ${thread.name}\n\n" +
                        error.stackTraceToString(),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

/// Installs [CrashReport] before any activity or service of the process runs.
class ContextSwitcherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
    }
}

/// The last crash's report, if any, instead of [content]: selectable, *Copy*
/// puts it on the clipboard to paste into a chat, *Close* deletes it and
/// shows [content].
// [impl->dsn~android-crash-report~1]
@Composable
fun LastCrashDialog(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(runCatching { CrashReport.file(context).readText() }.getOrNull()) }
    val text = report
    if (text == null) {
        content()
        return
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("The app crashed last time") },
        text = {
            SelectionContainer(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("crash", text))
                Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = {
                CrashReport.file(context).delete()
                report = null
            }) { Text("Close") }
        },
    )
}
