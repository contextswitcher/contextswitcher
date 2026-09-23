package com.contextswitcher.android.watch

/// When the watch service may send the next message enqueued on the phone:
/// on a reading that shows Claude idle (`waiting`, `done`), one message per
/// turn. After a send it waits for `working` again — the reading right after
/// the paste can still say `waiting`, and without the wait the whole queue
/// would go out at once. A failed send waits the same way, so a broken chat is
/// not pasted into every 10 s.
// [impl->dsn~android-enqueue~1]
class EnqueuedDelivery {
    private var blocked = false

    fun ready(status: String?): Boolean {
        return when (status?.lowercase()) {
            "working" -> {
                blocked = false
                false
            }
            "waiting", "done" -> !blocked
            else -> false
        }
    }

    fun delivered() {
        blocked = true
    }
}
