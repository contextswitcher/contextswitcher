package com.contextswitcher.config;

/// The energy saver: a process-wide gate every background poller consults
/// before its periodic tick. While it is on, none of them runs on its own —
/// no ssh round-trip, no `gh` call, no RefactoringMiner run — so a laptop on
/// battery pays only for the mirrored terminal of the selected task, which
/// keeps streaming. State comes back on demand: selecting another task
/// refreshes that task's live status, and the toolbar's refresh button runs
/// every poller once.
///
/// The two refresh actions are installed by `Main` once its pollers exist;
/// until then (and in tests) they are no-ops.
// ponytail: static state — the app has one window and one set of pollers, and
// threading a flag through six poller constructors buys nothing.
// [impl->dsn~energy-saver~1]
public final class EnergySaver {

    private static volatile boolean active;
    private static volatile Runnable onTaskChange = () -> { };
    private static volatile Runnable onManualRefresh = () -> { };

    private EnergySaver() {
    }

    /// Whether the energy saver is on — a poller with this true must skip its
    /// periodic tick (an explicitly requested refresh still runs).
    public static boolean active() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    /// Installs the refresh actions: `taskChange` refreshes the live status of
    /// the selected task (the cheap poll), `manualRefresh` runs every poller.
    public static void setRefreshers(Runnable taskChange, Runnable manualRefresh) {
        onTaskChange = taskChange;
        onManualRefresh = manualRefresh;
    }

    /// Refreshes the selected task's state — called on a selection change while
    /// the energy saver is on, where no tick would otherwise arrive.
    public static void refreshTask() {
        onTaskChange.run();
    }

    /// Runs every poller once, regardless of the gate (the refresh button).
    public static void refreshAll() {
        onManualRefresh.run();
    }
}
