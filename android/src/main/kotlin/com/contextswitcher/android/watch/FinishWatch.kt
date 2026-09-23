package com.contextswitcher.android.watch

import com.contextswitcher.discovery.TmuxStatusPoller

/// What the phone tells the user about the watched task's Claude session.
enum class Alert(val title: String) {
    FINISHED("Claude finished"),
    NEEDS_YOU("Claude needs you"),
    LIMIT("Claude hit its usage limit"),
}

/// Turns the watched window's successive `@cs_status` readings into alerts.
/// Only a change after Claude was seen `working` alerts — opening a task that
/// is already idle stays silent, and a status that stays the same over many
/// polls alerts once. `attention` (a question or permission prompt) does not
/// end the turn, so the later `waiting` still reports it finished.
// [impl->dsn~android-finish-notification~1]
class FinishWatch {
    private var last: String? = null
    private var turnRunning = false

    /// Feeds one poll's status (`null`: the window published none) and
    /// returns the alert it calls for, if any.
    fun update(status: String?): Alert? {
        val current = status?.lowercase()
        if (current == last) {
            return null
        }
        last = current
        return when (current) {
            "working" -> {
                turnRunning = true
                null
            }
            "attention" -> if (turnRunning) Alert.NEEDS_YOU else null
            "waiting", "done" -> endTurn(Alert.FINISHED)
            TmuxStatusPoller.LIMIT -> endTurn(Alert.LIMIT)
            else -> null
        }
    }

    private fun endTurn(alert: Alert): Alert? =
        if (turnRunning) alert.also { turnRunning = false } else null
}
