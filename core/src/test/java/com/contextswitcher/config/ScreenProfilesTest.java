package com.contextswitcher.config;

import java.util.List;

import com.contextswitcher.config.EnvironmentProfile.Taskbar;
import com.contextswitcher.config.ScreenProfiles.ConnectedScreen;
import com.contextswitcher.config.ScreenProfiles.Rule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~screen-chosen-profile~1]
class ScreenProfilesTest {

    private static final ConnectedScreen LAPTOP = new ConnectedScreen("", "BOE0C9F", true, false);
    private static final ConnectedScreen DELL_MAIN = new ConnectedScreen("DELL U3223QE", "DEL4282", false, true);
    private static final ConnectedScreen DELL_SIDE = new ConnectedScreen("DELL U2713HM", "DELA0B8", false, false);
    private static final ConnectedScreen PROJECTOR = new ConnectedScreen("EPSON PJ", "EPS0001", false, false);

    private static final ScreenProfiles RULES = ScreenProfiles.parse("""
            screenProfiles:
              - screen: dell u3223qe
                profile: desk
              - screen: EPS0001
                profile: lecture
              - {screen: external}
              - not a mapping
              - {screen: '', profile: x}
            """);

    @Test
    void parsesRulesInOrderSkippingIncompleteEntries() {
        assertThat(RULES.rules()).containsExactly(new Rule("dell u3223qe", "desk"), new Rule("EPS0001", "lecture"));
    }

    @Test
    void absentOrBrokenSectionIsNoRules() {
        assertThat(ScreenProfiles.parse("tasksDir: x\n").isEmpty()).isTrue();
        assertThat(ScreenProfiles.parse("screenProfiles: [unclosed").isEmpty()).isTrue();
        assertThat(ScreenProfiles.parse("screenProfiles: desk").isEmpty()).isTrue();
    }

    @Test
    void firstMatchingRuleWins() {
        assertThat(RULES.pick(List.of(LAPTOP, DELL_SIDE, DELL_MAIN))).isEqualTo("desk");
        assertThat(RULES.pick(List.of(LAPTOP, PROJECTOR))).isEqualTo("lecture");
        assertThat(RULES.pick(List.of(DELL_MAIN, PROJECTOR))).isEqualTo("desk");
    }

    @Test
    void noMatchIsNoScreenLayer() {
        assertThat(RULES.pick(List.of(LAPTOP))).isNull();
        assertThat(RULES.pick(List.of())).isNull();
    }

    @Test
    void selectorsMatchLikeProfileScreens() {
        assertThat(DELL_MAIN.matches("DEL4282")).isTrue();
        assertThat(DELL_MAIN.matches("primary")).isTrue();
        assertThat(DELL_MAIN.matches("external")).isTrue();
        assertThat(DELL_MAIN.matches("internal")).isFalse();
        assertThat(LAPTOP.matches("internal")).isTrue();
        assertThat(LAPTOP.matches("")).isFalse();
        assertThat(LAPTOP.matches("primary")).isFalse();
    }

    @Test
    void screenLayerSitsBetweenDefaultAndCategory() {
        EnvironmentProfiles profiles = EnvironmentProfiles.parse("""
                profiles:
                  default: {taskbar: visible, gatherWindows: off}
                  desk: {gatherWindows: DELL U3223QE, taskbar: autohide}
                  lecture: {taskbar: visible}
                """);

        EnvironmentProfile desk = profiles.effective("desk", null, null);
        assertThat(desk.gatherTarget()).isEqualTo("DELL U3223QE");
        assertThat(desk.taskbar()).isEqualTo(Taskbar.AUTOHIDE);

        EnvironmentProfile deskLecture = profiles.effective("desk", "lecture", null);
        assertThat(deskLecture.gatherTarget()).isEqualTo("DELL U3223QE");
        assertThat(deskLecture.taskbar()).isEqualTo(Taskbar.VISIBLE);

        assertThat(profiles.effective(null, null, null).gatherTarget()).isNull();
    }
}
