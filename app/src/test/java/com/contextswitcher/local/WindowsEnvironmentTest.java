package com.contextswitcher.local;

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
                new Resolution(1920, 1080), Displays.CLONE, Taskbar.AUTOHIDE, null));

        assertThat(script)
                .contains("if ([CsEnv]::Topology() -ne 2)")
                .contains("[CsEnv]::SetTopology(2)")
                .contains("if ([CsEnv]::Resolution() -ne '1920x1080')")
                .contains("[CsEnv]::SetResolution(1920, 1080)")
                .contains("if ([CsEnv]::AutoHide() -ne $true)")
                .contains("[CsEnv]::SetAutoHide($true)");
        // The projection mode settles before the resolution is set on top.
        assertThat(script.indexOf("SetTopology(2)")).isLessThan(script.indexOf("SetResolution(1920"));
    }

    @Test
    void scriptLeavesOutKeysTheProfileDoesNotSet() {
        String script = WindowsEnvironment.script(new EnvironmentProfile(
                null, null, Taskbar.VISIBLE, null));

        assertThat(script)
                .contains("[CsEnv]::SetAutoHide($false)")
                .doesNotContain("[CsEnv]::SetTopology(")
                .doesNotContain("[CsEnv]::SetResolution(");
    }

    @Test
    void topologyConstantsFollowSetDisplayConfig() {
        assertThat(WindowsEnvironment.script(new EnvironmentProfile(null, Displays.INTERNAL, null, null)))
                .contains("SetTopology(1)");
        assertThat(WindowsEnvironment.script(new EnvironmentProfile(null, Displays.EXTEND, null, null)))
                .contains("SetTopology(4)");
        assertThat(WindowsEnvironment.script(new EnvironmentProfile(null, Displays.EXTERNAL, null, null)))
                .contains("SetTopology(8)");
    }

    @Test
    void bookmarksBarOnlyNeedsNoPowershell() {
        EnvironmentProfile barOnly = new EnvironmentProfile(null, null, null, BookmarksBar.HIDDEN);

        assertThat(WindowsEnvironment.handles(barOnly)).isFalse();
        assertThat(new WindowsEnvironment(new LocalCommandRunner()).apply(barOnly).ok()).isTrue();
    }
}
