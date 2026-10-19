package com.contextswitcher.config;

import com.contextswitcher.config.EnvironmentProfile.BookmarksBar;
import com.contextswitcher.config.EnvironmentProfile.Displays;
import com.contextswitcher.config.EnvironmentProfile.Resolution;
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
                resolution: 2560x1440
                displays: extend
                taskbar: visible
                bookmarksBar: visible
              lecture:
                resolution: 1920 x 1080
                displays: clone
                taskbar: AutoHide
              screenshare:
                bookmarksBar: hidden
              broken:
                resolution: huge
                taskbar: sometimes
              notAMap: 42
            """;

    @Test
    void parsesNamedProfilesInFileOrderLeniently() {
        EnvironmentProfiles profiles = EnvironmentProfiles.parse(SETTINGS);

        assertThat(profiles.names()).containsExactly("default", "lecture", "screenshare", "broken");
        assertThat(profiles.byName().get("lecture")).isEqualTo(new EnvironmentProfile(
                new Resolution(1920, 1080), Displays.CLONE, Taskbar.AUTOHIDE, null));
        // A typo reads as "not set", not as a broken settings file.
        assertThat(profiles.byName().get("broken").isEmpty()).isTrue();
    }

    @Test
    void layersOverrideOverCategoryOverDefaultPerKey() {
        EnvironmentProfiles profiles = EnvironmentProfiles.parse(SETTINGS);

        assertThat(profiles.effective(null, null)).isEqualTo(new EnvironmentProfile(
                new Resolution(2560, 1440), Displays.EXTEND, Taskbar.VISIBLE, BookmarksBar.VISIBLE));
        assertThat(profiles.effective("lecture", null)).isEqualTo(new EnvironmentProfile(
                new Resolution(1920, 1080), Displays.CLONE, Taskbar.AUTOHIDE, BookmarksBar.VISIBLE));
        // The override only hides the bookmarks bar; the category keeps the rest.
        assertThat(profiles.effective("lecture", "screenshare")).isEqualTo(new EnvironmentProfile(
                new Resolution(1920, 1080), Displays.CLONE, Taskbar.AUTOHIDE, BookmarksBar.HIDDEN));
        assertThat(profiles.effective(null, "screenshare").bookmarksBar()).isEqualTo(BookmarksBar.HIDDEN);
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
