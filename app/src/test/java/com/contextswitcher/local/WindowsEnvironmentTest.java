package com.contextswitcher.local;

import java.util.LinkedHashMap;
import java.util.Map;

import com.contextswitcher.config.EnvironmentProfile;
import com.contextswitcher.config.EnvironmentProfile.BookmarksBar;
import com.contextswitcher.config.EnvironmentProfile.Displays;
import com.contextswitcher.config.EnvironmentProfile.Resolution;
import com.contextswitcher.config.EnvironmentProfile.Taskbar;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~environment-profile-windows~1]
class WindowsEnvironmentTest {

    @Test
    void scriptChecksEachKeyBeforeChangingIt() {
        String script = WindowsEnvironment.script(new EnvironmentProfile(
                Map.of("primary", new Resolution(1920, 1080)), Displays.CLONE, Taskbar.AUTOHIDE, null));

        assertThat(script)
                .contains("if ([CsEnv]::Topology() -ne 2)")
                .contains("[CsEnv]::SetTopology(2)")
                .contains("[CsScreens]::Apply(@('primary'), @(1920), @(1080))")
                .contains("if ([CsEnv]::AutoHide() -ne $true)")
                .contains("[CsEnv]::SetAutoHide($true)");
        // The projection mode settles before the resolutions are set on top.
        assertThat(script.indexOf("SetTopology(2)")).isLessThan(script.indexOf("[CsScreens]::Apply("));
    }

    @Test
    void screenSelectorsBecomeQuotedLiteralsInOrder() {
        Map<String, Resolution> screens = new LinkedHashMap<>();
        screens.put("internal", new Resolution(1920, 1200));
        screens.put("Oliver's DELL", new Resolution(5120, 2160));

        assertThat(WindowsEnvironment.script(new EnvironmentProfile(screens, null, null, null)))
                .contains("[CsScreens]::Apply(@('internal', 'Oliver''s DELL'), @(1920, 5120), @(1200, 2160))");
    }

    @Test
    void screensMatchByNameOrCodeBeforePrimaryBeforeInternalOrExternal() {
        String script = WindowsEnvironment.script(new EnvironmentProfile(
                Map.of("external", new Resolution(1920, 1080)), null, null, null));

        assertThat(script)
                .contains("foreach (string want in new[] { s.Name, s.Code })")
                .contains("new[] { s.Primary ? \"primary\" : null, s.Internal ? \"internal\" : \"external\" }")
                // staged per screen, applied together
                .contains("0x1 | 0x10000000");
    }

    @Test
    void scriptLeavesOutKeysTheProfileDoesNotSet() {
        String script = WindowsEnvironment.script(new EnvironmentProfile(
                Map.of(), null, Taskbar.VISIBLE, null));

        assertThat(script)
                .contains("[CsEnv]::SetAutoHide($false)")
                .doesNotContain("[CsEnv]::SetTopology(")
                .doesNotContain("[CsScreens]::Apply(");
    }

    @Test
    void topologyConstantsFollowSetDisplayConfig() {
        assertThat(WindowsEnvironment.script(new EnvironmentProfile(Map.of(), Displays.INTERNAL, null, null)))
                .contains("SetTopology(1)");
        assertThat(WindowsEnvironment.script(new EnvironmentProfile(Map.of(), Displays.EXTEND, null, null)))
                .contains("SetTopology(4)");
        assertThat(WindowsEnvironment.script(new EnvironmentProfile(Map.of(), Displays.EXTERNAL, null, null)))
                .contains("SetTopology(8)");
    }

    @Test
    void bookmarksBarOnlyNeedsNoPowershell() {
        EnvironmentProfile barOnly = new EnvironmentProfile(Map.of(), null, null, BookmarksBar.HIDDEN);

        assertThat(WindowsEnvironment.handles(barOnly)).isFalse();
        assertThat(new WindowsEnvironment(new LocalCommandRunner()).apply(barOnly).ok()).isTrue();
    }
}
