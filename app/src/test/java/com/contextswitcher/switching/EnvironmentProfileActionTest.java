package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.config.EnvironmentProfile;
import com.contextswitcher.config.EnvironmentProfiles;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~environment-profiles~1]
class EnvironmentProfileActionTest {

    private static final EnvironmentProfiles PROFILES = EnvironmentProfiles.parse("""
            profiles:
              default: {resolution: 2560x1440, taskbar: visible, bookmarksBar: visible}
              lecture: {resolution: 1920x1080, taskbar: autohide, bookmarksBar: hidden}
              screenshare: {bookmarksBar: hidden}
            """);

    private final List<EnvironmentProfile> machine = new ArrayList<>();
    private final List<Boolean> bars = new ArrayList<>();
    private final AtomicReference<@Nullable String> override = new AtomicReference<>();

    private EnvironmentProfileAction action(EnvironmentProfiles profiles) {
        return new EnvironmentProfileAction(() -> profiles,
                Map.of("teaching", "lecture", "typo", "lectrue")::get, override::get,
                profile -> {
                    machine.add(profile);
                    return ActionResult.success("");
                },
                hidden -> {
                    bars.add(hidden);
                    return ActionResult.success("");
                });
    }

    private static Task task(String id) {
        return new Task(id, "T", TaskStatus.ACTIVE, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), null, null, "");
    }

    @Test
    void configuredExactlyWhenSettingsDefineProfiles() {
        assertThat(action(PROFILES).isConfigured(task("work/a"))).isTrue();
        assertThat(action(EnvironmentProfiles.NONE).isConfigured(task("work/a"))).isFalse();
    }

    @Test
    void categoryProfileThenDefaultOnLeaving() {
        EnvironmentProfileAction action = action(PROFILES);

        ActionResult lecture = action.run(task("teaching/slides"));
        assertThat(lecture.ok()).isTrue();
        assertThat(lecture.detail()).startsWith("lecture: ");
        assertThat(machine.getLast().resolution().get("primary")).hasToString("1920x1080");
        assertThat(bars).containsExactly(true);

        ActionResult desk = action.run(task("work/fix"));
        assertThat(desk.detail()).startsWith("default: ");
        assertThat(machine.getLast().taskbar()).isEqualTo(EnvironmentProfile.Taskbar.VISIBLE);
        assertThat(bars).containsExactly(true, false);
    }

    @Test
    void bookmarksBarIsSentOnlyOnChange() {
        EnvironmentProfileAction action = action(PROFILES);

        action.run(task("work/a"));
        action.run(task("work/b"));
        action.run(task("root-level-task"));

        assertThat(bars).containsExactly(false);
        assertThat(machine).hasSize(3);
    }

    @Test
    void overrideLaysOverTheCategoryAndReappliesWithoutSwitch() {
        EnvironmentProfileAction action = action(PROFILES);
        action.run(task("work/a"));

        override.set("screenshare");
        ActionResult result = action.reapply();

        assertThat(result.detail()).startsWith("default + screenshare: ");
        assertThat(bars).containsExactly(false, true);
        assertThat(machine.getLast().resolution().get("primary")).hasToString("2560x1440");
    }

    @Test
    void unknownCategoryProfileFailsTheChipNamingIt() {
        ActionResult result = action(PROFILES).run(task("typo/a"));

        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("lectrue");
        assertThat(machine).isEmpty();
    }

    @Test
    void failedBookmarksBarIsRetriedOnTheNextSwitch() {
        List<Boolean> attempts = new ArrayList<>();
        EnvironmentProfileAction action = new EnvironmentProfileAction(() -> PROFILES,
                group -> null, () -> null, profile -> ActionResult.success(""),
                hidden -> {
                    attempts.add(hidden);
                    return attempts.size() == 1
                            ? ActionResult.failure("Browser extension not connected")
                            : ActionResult.success("");
                });

        assertThat(action.run(task("work/a")).ok()).isFalse();
        assertThat(action.run(task("work/a")).ok()).isTrue();
        assertThat(attempts).hasSize(2);
    }
}
