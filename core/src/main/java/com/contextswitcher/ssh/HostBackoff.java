package com.contextswitcher.ssh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.tinylog.Logger;

/// Per-host memory of ssh-level failures, so the background polls stop
/// hammering a host that does not answer (a VPN that is off, a machine that
/// is down). Every [ProcessSshRunner] call reports its outcome here; after
/// [#FAILURES_BEFORE_BACKOFF] host failures ([#hostFailure]) in a row the host is
/// backed off — [#backedOff] is true until the delay has passed, the polls
/// skip it, and the first poll after that is the retry. Each further failed
/// retry doubles the delay, from [#FIRST_DELAY] up to [#MAX_DELAY]; any
/// success forgets the host.
///
/// Only the polls consult it: a command the user triggers (a message, a
/// switch) always tries, and its success ends the backoff at once.
///
/// One shared instance ([#SHARED]), since several runner instances talk to
/// the same hosts and should learn from each other's failures.
// [impl->dsn~ssh-host-backoff~1]
public final class HostBackoff {

    /// ssh's own exit code for "could not do it at all" — connect timeout,
    /// unknown host, refused, host key, authentication. A remote command's
    /// failure has its own exit code.
    static final int SSH_ERROR_EXIT = 255;

    /// Whether `result` says the host itself did not answer: ssh's own error
    /// exit, or the runner's timeout — a host whose network path is half
    /// there (VPN routes up, machine gone) hangs past `ConnectTimeout`.
    static boolean hostFailure(SshCommandRunner.SshResult result) {
        return result.exitCode() == SSH_ERROR_EXIT
                || result.exitCode() == -1 && result.stderr().startsWith(ProcessSshRunner.TIMEOUT_PREFIX);
    }

    static final int FAILURES_BEFORE_BACKOFF = 3;
    static final Duration FIRST_DELAY = Duration.ofMinutes(1);
    static final Duration MAX_DELAY = Duration.ofMinutes(5);

    public static final HostBackoff SHARED = new HostBackoff(Clock.systemUTC());

    private record State(int failures, Duration delay, Instant until) {
    }

    private final Clock clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    HostBackoff(Clock clock) {
        this.clock = clock;
    }

    /// Records one finished ssh call to `host`.
    public void record(String host, SshCommandRunner.SshResult result) {
        if (!hostFailure(result)) {
            if (states.remove(host) != null) {
                Logger.info("{} answers again — polling it as usual", host);
            }
            return;
        }
        states.compute(host, (key, old) -> {
            Instant now = clock.instant();
            // Calls still in flight when the backoff began (or several polls
            // retrying at once) fail too — they must not double the delay again.
            if (old != null && now.isBefore(old.until())) {
                return old;
            }
            int failures = old == null ? 1 : old.failures() + 1;
            if (failures < FAILURES_BEFORE_BACKOFF) {
                return new State(failures, Duration.ZERO, Instant.MIN);
            }
            Duration delay = old == null || old.delay().isZero() ? FIRST_DELAY : old.delay().multipliedBy(2);
            if (delay.compareTo(MAX_DELAY) > 0) {
                delay = MAX_DELAY;
            }
            Logger.warn("{} failed {} times in a row ({}) — background polls skip it for {}",
                    host, failures, result.stderr().strip(), delay);
            return new State(failures, delay, now.plus(delay));
        });
    }

    /// Whether the background polls should skip `host` right now.
    public boolean backedOff(String host) {
        State state = states.get(host);
        return state != null && clock.instant().isBefore(state.until());
    }
}
