package com.contextswitcher.local;

/// Tells a sleep apart from docking for the screen watchers ([ScreenWatch],
/// [WindowGather]): the inline C# class `CsSleepGuard`, compiled into each
/// watcher's single `Add-Type`.
///
/// Going to sleep, Windows drops the external screens a few seconds before
/// the processes stop; waking up, it brings them back. Both looked like
/// undocking and docking again (field report 2026-11-26: after waking up at
/// the desk, the windows were swept onto the 32" and flickered, though
/// nothing had changed).
///
/// The guard is fed each look at the screens with the wall-clock time. A
/// set counts once it has stayed unchanged for the settle time; a jump of
/// the wall clock of more than [#GAP_MS] between two looks is a sleep, and
/// the set being settled is dropped. A set settled at most [#PRE_SLEEP_MS]
/// before the sleep was the sleep's doing: the set before it is what the
/// machine went to sleep with. Back on that set within [#WAKE_MS] of waking
/// up, the guard reports the change as a round trip (`RoundTrip`), and the
/// watchers leave everything alone. A set that only flickered away before
/// the sleep and came back is not reported at all. Waking up to other
/// screens (docked or undocked while asleep) is an ordinary change.
// [impl->dsn~screens-sleep-round-trip~1]
final class SleepGuard {

    /// A wall-clock jump this long between two looks (taken every 500 ms) is a sleep.
    static final long GAP_MS = 10_000;

    /// A set settled this shortly before a sleep was the sleep's doing.
    static final long PRE_SLEEP_MS = 30_000;

    /// How long after waking up a return to the pre-sleep set is a round trip.
    static final long WAKE_MS = 120_000;

    private static final String TYPES = """
            public class CsSleepGuard {
              readonly long settleMs;
              string settled, previous, candidate, sleepBase;
              long candidateSince, settledAt = long.MinValue / 2, lastWall = long.MinValue, wokeAt;
              public bool RoundTrip;
              public string Settled { get { return settled; } }
              public CsSleepGuard(long settleMs) { this.settleMs = settleMs; }
              public static long Wall() { return System.DateTime.UtcNow.Ticks / 10000; }
              public string Look(string now) { return Look(Wall(), now); }
              public string Look(long wall, string now) {
                if (lastWall != long.MinValue && wall - lastWall > __GAP__L) {
                  sleepBase = lastWall - settledAt <= __PRE_SLEEP__L && previous != null ? previous : settled;
                  wokeAt = wall;
                  candidate = null;
                }
                lastWall = wall;
                if (sleepBase != null && wall - wokeAt > __WAKE__L) { sleepBase = null; }
                if (string.IsNullOrEmpty(now)) { return null; }
                if (now != candidate) { candidate = now; candidateSince = wall; }
                if (candidate == settled || wall - candidateSince < settleMs) { return null; }
                previous = settled; settled = candidate; settledAt = wall;
                RoundTrip = sleepBase != null && settled == sleepBase;
                sleepBase = null;
                return settled;
              }
            }
            """;

    private SleepGuard() {
    }

    /// The C# source of `CsSleepGuard`, to go into a watcher's `Add-Type`.
    static String types() {
        return TYPES.replace("__GAP__", Long.toString(GAP_MS))
                .replace("__PRE_SLEEP__", Long.toString(PRE_SLEEP_MS))
                .replace("__WAKE__", Long.toString(WAKE_MS));
    }
}
