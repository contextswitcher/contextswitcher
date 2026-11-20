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
        assertThat(parser.accept("end")).containsExactly(
                new ConnectedScreen("", "BOE0C9F", true, false),
                new ConnectedScreen("DELL U3223QE", "DEL4282", false, true));

        assertThat(parser.accept("screen\t\tBOE0C9F\t1\t1")).isNull();
        assertThat(parser.accept("end")).containsExactly(new ConnectedScreen("", "BOE0C9F", true, true));
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
