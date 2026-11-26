package com.contextswitcher.local;

import java.util.ArrayList;
import java.util.List;

import com.contextswitcher.config.ScreenProfiles.ConnectedScreen;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~screen-chosen-profile~1]
class ScreenWatchTest {

    @Test
    void scriptCompilesTheScreenTypesAlongsideAndWaitsForAStableSet() {
        String script = ScreenWatch.script(4711);

        assertThat(script.split("Add-Type", -1)).hasSize(2);
        assertThat(script.indexOf("public class CsScreens {")).isLessThan(script.indexOf("\"@"));
        assertThat(script).endsWith("[CsScreenWatch]::Run(4711, " + ScreenWatch.STABLE_MS + ")\n");
        assertThat(script).doesNotContain("__SCREENS__");
    }

    @Test
    void parserCollectsScreensUntilEnd() {
        ScreenWatch.Parser parser = new ScreenWatch.Parser();

        assertThat(parser.accept("screen\t\tBOE0C9F\t1\t0")).isNull();
        assertThat(parser.accept("some compiler warning")).isNull();
        assertThat(parser.accept("screen\tDELL U3223QE\tDEL4282\t0\t1")).isNull();
        assertThat(parser.accept("end")).isEqualTo(new ScreenWatch.Change(List.of(
                new ConnectedScreen("", "BOE0C9F", true, false),
                new ConnectedScreen("DELL U3223QE", "DEL4282", false, true)), false));

        assertThat(parser.accept("screen\t\tBOE0C9F\t1\t1")).isNull();
        assertThat(parser.accept("end")).isEqualTo(
                new ScreenWatch.Change(List.of(new ConnectedScreen("", "BOE0C9F", true, true)), false));
    }

    // [utest->dsn~screens-sleep-round-trip~1]
    @Test
    void endSleepMarksTheSetAsBackFromASleep() {
        ScreenWatch.Parser parser = new ScreenWatch.Parser();

        parser.accept("screen\tDELL U3223QE\tDEL4282\t0\t1");
        assertThat(parser.accept("end\tsleep")).isEqualTo(new ScreenWatch.Change(
                List.of(new ConnectedScreen("DELL U3223QE", "DEL4282", false, true)), true));
    }

    // [utest->dsn~screens-sleep-round-trip~1]
    @Test
    void scriptReportsThroughTheSleepGuard() {
        assertThat(ScreenWatch.script(4711))
                .contains("public class CsSleepGuard {")
                .contains("guard.Look(sb.ToString())")
                .contains("guard.RoundTrip ? \"end\\tsleep\" : \"end\"")
                .doesNotContain("__SLEEP__");
    }

    /// Compiles the watcher's C# for real: with a parent pid that does not
    /// exist, `Run` returns at once.
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void watcherCompilesAndExitsWithoutItsParent() {
        LocalCommandRunner.LocalResult result = new LocalCommandRunner(java.time.Duration.ofSeconds(60))
                .run(PowerShellCommand.of(ScreenWatch.script(Integer.MAX_VALUE)));

        assertThat(result.stderr()).doesNotContain("error CS");
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void startsNothingOffWindows() {
        List<List<String>> started = new ArrayList<>();
        ScreenWatch watch = new ScreenWatch(command -> {
            started.add(command);
            throw new java.io.IOException("not expected");
        }, false);

        watch.start(screens -> { });

        assertThat(started).isEmpty();
    }
}
