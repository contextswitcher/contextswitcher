package com.contextswitcher.local;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/// Drives the compiled `CsSleepGuard` through sleeps with made-up
/// wall-clock times: one look every 500 ms, a sleep is a jump of an hour.
// [utest->dsn~screens-sleep-round-trip~1]
@EnabledOnOs(OS.WINDOWS)
class SleepGuardTest {

    private static final String DESK = "laptop;27;32";
    private static final String LAPTOP = "laptop";
    private static final long HOUR = 3_600_000;

    /// Field report 2026-11-26: the external screens dropped 3 s before
    /// the sleep, long enough to be reported; after waking up they came back.
    @Test
    void screensBackAsBeforeTheSleepAreARoundTrip() {
        assertThat(run("""
                $t = 0
                Look $DESK 10000
                Look $LAPTOP 4000
                $t += 3600000
                Look $LAPTOP 2000
                Look $DESK 4000
                """)).isEqualTo("""
                settled laptop;27;32
                settled laptop
                settled laptop;27;32 (round trip)
                """);
    }

    /// The drop did not settle before the processes stopped: waking up to
    /// the same screens reports nothing at all.
    @Test
    void aDropThatDidNotSettleBeforeTheSleepIsNotReported() {
        assertThat(run("""
                Look $DESK 10000
                Look $LAPTOP 1500
                $t += 3600000
                Look $LAPTOP 1000
                Look $DESK 10000
                """)).isEqualTo("""
                settled laptop;27;32
                """);
    }

    /// Docked while asleep: the screens the machine wakes up to are new.
    @Test
    void wakingUpToOtherScreensIsAnOrdinaryChange() {
        assertThat(run("""
                Look $LAPTOP 10000
                $t += 3600000
                Look $DESK 4000
                """)).isEqualTo("""
                settled laptop
                settled laptop;27;32
                """);
    }

    /// Undocked long before the sleep: docking after waking up is docking.
    @Test
    void aChangeLongBeforeTheSleepStays() {
        assertThat(run("""
                Look $DESK 10000
                Look $LAPTOP 60000
                $t += 3600000
                Look $DESK 4000
                """)).isEqualTo("""
                settled laptop;27;32
                settled laptop
                settled laptop;27;32
                """);
    }

    /// Back on the old screens only well after waking up: plugged in again.
    @Test
    void aRoundTripEndsSomeTimeAfterWakingUp() {
        assertThat(run("""
                Look $DESK 10000
                Look $LAPTOP 4000
                $t += 3600000
                Look $LAPTOP 180000
                Look $DESK 4000
                """)).isEqualTo("""
                settled laptop;27;32
                settled laptop
                settled laptop;27;32
                """);
    }

    /// All screens off (standby) is no set: it neither settles nor resets one.
    @Test
    void noScreensIsNoObservation() {
        assertThat(run("""
                Look $DESK 10000
                Look '' 10000
                Look $DESK 1000
                """)).isEqualTo("""
                settled laptop;27;32
                """);
    }

    private static String run(String steps) {
        String script = "$ErrorActionPreference = 'Stop'\n"
                + "Add-Type -TypeDefinition @\"\n" + SleepGuard.types() + "\"@\n"
                + """
                $g = New-Object CsSleepGuard 3000
                $script:t = 0
                $DESK = '%s'; $LAPTOP = '%s'
                function Look($set, $ms) {
                  for ($i = 0; $i -lt $ms; $i += 500) {
                    $s = $g.Look([long]$script:t, $set)
                    if ($s -ne $null) { if ($g.RoundTrip) { "settled $s (round trip)" } else { "settled $s" } }
                    $script:t += 500
                  }
                }
                """.formatted(DESK, LAPTOP)
                + steps.replace("$t", "$script:t");
        LocalCommandRunner.LocalResult result = new LocalCommandRunner(Duration.ofSeconds(60))
                .run(PowerShellCommand.of(script));
        assertThat(result.stderr()).doesNotContain("error CS").doesNotContain("Exception");
        assertThat(result.exitCode()).isZero();
        return result.stdout().replace("\r\n", "\n");
    }
}
