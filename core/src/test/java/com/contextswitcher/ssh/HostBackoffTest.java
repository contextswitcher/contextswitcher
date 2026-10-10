package com.contextswitcher.ssh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~ssh-host-backoff~1]
class HostBackoffTest {

    private static final SshCommandRunner.SshResult UNREACHABLE =
            new SshCommandRunner.SshResult(255, "", "ssh: connect to host swkgpu1 port 22: Connection timed out");
    private static final SshCommandRunner.SshResult OK = new SshCommandRunner.SshResult(0, "", "");

    /// A clock the test moves by hand.
    private static final class Manual extends Clock {
        Instant now = Instant.parse("2026-10-10T20:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }
    }

    @Test
    void backsOffAfterThreeSshFailuresInARow() {
        Manual clock = new Manual();
        HostBackoff backoff = new HostBackoff(clock);
        backoff.record("swkgpu1", UNREACHABLE);
        backoff.record("swkgpu1", UNREACHABLE);
        assertThat(backoff.backedOff("swkgpu1")).isFalse();
        backoff.record("swkgpu1", UNREACHABLE);
        assertThat(backoff.backedOff("swkgpu1")).isTrue();
        assertThat(backoff.backedOff("nixos")).isFalse();

        clock.advance(HostBackoff.FIRST_DELAY);
        assertThat(backoff.backedOff("swkgpu1")).as("the retry is due").isFalse();
    }

    @Test
    void theRunnersOwnTimeoutCountsAsAHostFailure() {
        assertThat(HostBackoff.hostFailure(new SshCommandRunner.SshResult(-1, "",
                ProcessSshRunner.TIMEOUT_PREFIX + "PT10S"))).isTrue();
        assertThat(HostBackoff.hostFailure(new SshCommandRunner.SshResult(-1, "", "Interrupted"))).isFalse();
        assertThat(HostBackoff.hostFailure(UNREACHABLE)).isTrue();
        assertThat(HostBackoff.hostFailure(OK)).isFalse();
    }

    @Test
    void failedRetriesDoubleTheDelayUpToTheCap() {
        Manual clock = new Manual();
        HostBackoff backoff = new HostBackoff(clock);
        for (int i = 0; i < HostBackoff.FAILURES_BEFORE_BACKOFF; i++) {
            backoff.record("h", UNREACHABLE);
        }
        // Concurrent polls failing during the backoff do not extend it.
        backoff.record("h", UNREACHABLE);
        clock.advance(HostBackoff.FIRST_DELAY);
        backoff.record("h", UNREACHABLE);
        clock.advance(HostBackoff.FIRST_DELAY.multipliedBy(2).minusSeconds(1));
        assertThat(backoff.backedOff("h")).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(backoff.backedOff("h")).isFalse();

        for (int i = 0; i < 5; i++) {
            backoff.record("h", UNREACHABLE);
            clock.advance(HostBackoff.MAX_DELAY);
        }
        backoff.record("h", UNREACHABLE);
        clock.advance(HostBackoff.MAX_DELAY);
        assertThat(backoff.backedOff("h")).as("capped").isFalse();
    }

    @Test
    void anAnswerOrARemoteCommandFailureEndsTheBackoff() {
        Manual clock = new Manual();
        HostBackoff backoff = new HostBackoff(clock);
        for (int i = 0; i < HostBackoff.FAILURES_BEFORE_BACKOFF; i++) {
            backoff.record("h", UNREACHABLE);
        }
        backoff.record("h", OK);
        assertThat(backoff.backedOff("h")).isFalse();

        for (int i = 0; i < HostBackoff.FAILURES_BEFORE_BACKOFF; i++) {
            backoff.record("h", UNREACHABLE);
        }
        // tmux's exit 1 (no server) means ssh got through.
        backoff.record("h", new SshCommandRunner.SshResult(1, "", "no server running"));
        assertThat(backoff.backedOff("h")).isFalse();
    }
}
