package com.contextswitcher.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.contextswitcher.android.BuildConfig

/// The commit this build of the app was made from, as the desktop shows its
/// own: `<short sha> (<commit date and time>)` — so "which version is on the
/// phone?" has an answer on screen. A long press copies the bare sha.
// [impl->dsn~android-running-commit~1]
@Composable
fun BuildCommitLine(modifier: Modifier = Modifier, commitLine: String = BuildConfig.GIT_COMMIT_LINE) {
    val context = LocalContext.current
    Text(
        text = commitLine,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .combinedClickable(onClick = {}, onLongClick = {
                val sha = commitLine.substringBefore(' ')
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("commit", sha))
                Toast.makeText(context, "Copied $sha", Toast.LENGTH_SHORT).show()
            })
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
