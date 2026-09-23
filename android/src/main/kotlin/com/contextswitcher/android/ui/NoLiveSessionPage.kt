package com.contextswitcher.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/// Shown instead of [TerminalPage] for a task with no `tmux:` section — the
/// task detail pager's terminal page has nothing to capture.
// [impl->dsn~android-terminal-snapshot~3]
@Composable
fun NoLiveSessionPage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = "No live session for this task", style = MaterialTheme.typography.bodyLarge)
    }
}
