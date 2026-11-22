package com.contextswitcher.config;

import java.util.Map;

import com.contextswitcher.config.EnvironmentProfile.BookmarksBar;
import com.contextswitcher.config.EnvironmentProfile.Displays;
import com.contextswitcher.config.EnvironmentProfile.GatherWhen;
import com.contextswitcher.config.EnvironmentProfile.Resolution;
import com.contextswitcher.config.EnvironmentProfile.Screen;
import com.contextswitcher.config.EnvironmentProfile.Taskbar;
import com.contextswitcher.tasks.TaskFileParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// [utest->dsn~environment-profiles~1]
class EnvironmentProfilesTest {

    private static final String SETTINGS = """
            tasksDir: /tmp/tasks
            profiles:
              default:
                screens: {primary: {resolution: 2560x1440}}
                displays: extend
                taskbar: visible
                bookmarksBar: visible
              lecture:
                screens:
                  primary:
                    resolution: 1920 x 1080
                displays: clone
                taskbar: AutoHide
              screenshare:
                bookmarksBar: hidden
              broken:
                screens: {primary: {resolution: huge}}
                taskbar: sometimes
              notAMap: 42
            """;

    private static Map<String, Screen> primary(int width, int height) {
        return Map.of("primary", new Screen(new Resolution(width, height)));
    }

    @Test
    void parsesNamedProfilesInFileOrderLeniently() {
        EnvironmentProfiles profiles = EnvironmentProfiles.parse(SETTINGS);

        assertThat(profiles.names()).containsExactly("default", "lecture", "screenshare", "broken");
        assertThat(profiles.byName().get("lecture")).isEqualTo(new EnvironmentProfile(
                primary(1920, 1080), Displays.CLONE, Taskbar.AUTOHIDE, null));
        // A typo reads as "not set", not as a broken settings file.
        assertThat(profiles.byName().get("broken").isEmpty()).isTrue();
    }

    @Test
    void layersOverrideOverCategoryOverDefaultPerKey() {
        EnvironmentProfiles profiles = EnvironmentProfiles.parse(SETTINGS);

        assertThat(profiles.effective(null, null)).isEqualTo(new EnvironmentProfile(
                primary(2560, 1440), Displays.EXTEND, Taskbar.VISIBLE, BookmarksBar.VISIBLE));
        assertThat(profiles.effective("lecture", null)).isEqualTo(new EnvironmentProfile(
                primary(1920, 1080), Displays.CLONE, Taskbar.AUTOHIDE, BookmarksBar.VISIBLE));
        // The override only hides the bookmarks bar; the category keeps the rest.
        assertThat(profiles.effective("lecture", "screenshare")).isEqualTo(new EnvironmentProfile(
                primary(1920, 1080), Displays.CLONE, Taskbar.AUTOHIDE, BookmarksBar.HIDDEN));
        assertThat(profiles.effective(null, "screenshare").bookmarksBar()).isEqualTo(BookmarksBar.HIDDEN);
    }

    @Test
    void gatherWindowsLayersAndOffSwitchesALowerLayerOff() {
        EnvironmentProfiles profiles = EnvironmentProfiles.parse("""
                profiles:
                  default: {taskbar: visible}
                  lecture: {gatherWindows: internal}
                  desk: {gatherWindows: off}
                  share: {bookmarksBar: hidden}
                  weird: {gatherWindows: [a, b]}
                """);

        assertThat(profiles.effective(null, null).gatherTarget()).isNull();
        assertThat(profiles.effective("lecture", null).gatherTarget()).isEqualTo("internal");
        // An override without the key keeps the category's gathering.
        assertThat(profiles.effective("lecture", "share").gatherTarget()).isEqualTo("internal");
        // YAML's `off` reads as false: an explicit "no gathering" over a lower layer.
        assertThat(profiles.effective("lecture", "desk").gatherTarget()).isNull();
        assertThat(profiles.byName().get("desk").gatherWindows()).isEqualTo(EnvironmentProfile.GATHER_OFF);
        assertThat(profiles.byName().get("lecture").isEmpty()).isFalse();
        assertThat(profiles.byName().get("weird").isEmpty()).isTrue();
    }

    @Test
    void gatherWhenDefaultsToAlwaysAndLayersPerKey() {
        EnvironmentProfiles profiles = EnvironmentProfiles.parse("""
                profiles:
                  lecture: {gatherWindows: internal}
                  desk: {gatherWindows: DELL U3223QE, gatherWhen: docking}
                  typo: {gatherWindows: internal, gatherWhen: sometimes}
                """);

        assertThat(profiles.effective("lecture", null).gatherMode()).isEqualTo(GatherWhen.ALWAYS);
        assertThat(profiles.effective("desk", null).gatherMode()).isEqualTo(GatherWhen.DOCKING);
        // The override sets the target only; the category's `docking` stays.
        assertThat(profiles.effective("desk", "lecture").gatherTarget()).isEqualTo("internal");
        assertThat(profiles.effective("desk", "lecture").gatherMode()).isEqualTo(GatherWhen.DOCKING);
        assertThat(profiles.effective("typo", null).gatherMode()).isEqualTo(GatherWhen.ALWAYS);
    }

    @Test
    void screensMergePerSelector() {
        EnvironmentProfiles profiles = EnvironmentProfiles.parse("""
                profiles:
                  default:
                    screens:
                      internal: {resolution: 1920x1200}
                      DELL U4025QW: {resolution: 5120x2160}
                      bogus: {resolution: wide}
                  lecture:
                    screens:
                      External: {resolution: 1920x1080}
                      dell u4025qw: {resolution: 3440x1440}
                """);

        assertThat(profiles.byName().get("default").screens()).containsOnlyKeys("internal", "DELL U4025QW");
        assertThat(profiles.effective("lecture", null).screens()).containsExactly(
                Map.entry("internal", new Screen(new Resolution(1920, 1200))),
                Map.entry("External", new Screen(new Resolution(1920, 1080))),
                Map.entry("dell u4025qw", new Screen(new Resolution(3440, 1440))));
    }

    @Test
    void scaleIsAPercentDefaulting100AndMayStandAlone() {
        EnvironmentProfiles profiles = EnvironmentProfiles.parse("""
                profiles:
                  default:
                    screens:
                      internal:
                        resolution: 2880x1800
                        scale: 125
                      external: {resolution: 2560x1440, scale: 150%}
                      projector: {resolution: 1920x1080}
                      DEL4308: {scale: 175}
                      odd: {resolution: 1920x1080, scale: 110}
                      typo: {resolution: 1920x10800x, scale: 125}
                      nothing: {}
                      scalar: 1920x1080
                """);

        assertThat(profiles.byName().get("default").screens()).containsExactly(
                Map.entry("internal", new Screen(new Resolution(2880, 1800), 125)),
                Map.entry("external", new Screen(new Resolution(2560, 1440), 150)),
                Map.entry("projector", new Screen(new Resolution(1920, 1080), 100)),
                Map.entry("DEL4308", new Screen(null, 175)));
    }

    @Test
    void unknownProfileNameIsAnError() {
        assertThatThrownBy(() -> EnvironmentProfiles.parse(SETTINGS).effective("lectrue", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lectrue");
    }

    @Test
    void noProfilesSectionOrUnparseableFileIsNone() {
        assertThat(EnvironmentProfiles.parse("tasksDir: /tmp").isEmpty()).isTrue();
        assertThat(EnvironmentProfiles.parse("profiles: [a, b").isEmpty()).isTrue();
        assertThat(EnvironmentProfiles.NONE.effective(null, null).isEmpty()).isTrue();
    }

    @Test
    void categoryNamesItsProfile() {
        assertThat(TaskFileParser.groupProfile("---\nprofile: lecture\n---\n")).isEqualTo("lecture");
        assertThat(TaskFileParser.groupProfile("---\nremote: x\n---\n")).isNull();
    }

    @Test
    void settingsReferenceDescribesProfiles() {
        EnvironmentProfiles fromReference = EnvironmentProfiles.parse(AppSettings.settingsReference());

        assertThat(fromReference.names()).contains(EnvironmentProfiles.DEFAULT);
        assertThat(fromReference.byName().values()).noneMatch(EnvironmentProfile::isEmpty);
    }
}
