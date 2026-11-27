package com.contextswitcher.config;

import com.contextswitcher.config.WindowHotkey.Hotkey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~switch-by-window-hotkey~1]
class WindowHotkeyTest {

    @Test
    void parsesModifiersAndKeyInAnyCaseAndOrder() {
        assertThat(WindowHotkey.parse("Win+Shift+F"))
                .isEqualTo(new Hotkey(WindowHotkey.MOD_WIN | WindowHotkey.MOD_SHIFT, 0x46, "Win+Shift+F"));
        assertThat(WindowHotkey.parse(" shift + win + f "))
                .isEqualTo(new Hotkey(WindowHotkey.MOD_WIN | WindowHotkey.MOD_SHIFT, 0x46, "Win+Shift+F"));
        assertThat(WindowHotkey.parse("Ctrl+Alt+Shift+Win+7"))
                .isEqualTo(new Hotkey(0xF, 0x37, "Win+Ctrl+Alt+Shift+7"));
        assertThat(WindowHotkey.parse("Ctrl+F12")).isEqualTo(new Hotkey(WindowHotkey.MOD_CONTROL, 0x7B, "Ctrl+F12"));
        assertThat(WindowHotkey.parse("Alt+F1").virtualKey()).isEqualTo(0x70);
    }

    @Test
    void rejectsBareKeysTwoKeysAndUnknownTokens() {
        assertThat(WindowHotkey.parse("F")).isNull();
        assertThat(WindowHotkey.parse("Win+Shift")).isNull();
        assertThat(WindowHotkey.parse("Win+F+G")).isNull();
        assertThat(WindowHotkey.parse("Win+Space")).isNull();
        assertThat(WindowHotkey.parse("Win+F25")).isNull();
    }

    @Test
    void absentSettingIsTheDefaultOffTurnsItOff() {
        assertThat(WindowHotkey.fromSettings("theme: dark\n").hotkey()).isEqualTo(WindowHotkey.parse("Win+Shift+F"));
        assertThat(WindowHotkey.fromSettings("switchByWindowHotkey: Ctrl+Win+K\n").hotkey().label())
                .isEqualTo("Win+Ctrl+K");
        for (String off : new String[] {"off", "false", "''", ""}) {
            WindowHotkey.Setting setting = WindowHotkey.fromSettings("switchByWindowHotkey: " + off + "\n");
            assertThat(setting.hotkey()).as(off).isNull();
            assertThat(setting.error()).as(off).isNull();
        }
    }

    @Test
    void unparsableSettingNamesItself() {
        WindowHotkey.Setting setting = WindowHotkey.fromSettings("switchByWindowHotkey: Hyper+F\n");
        assertThat(setting.hotkey()).isNull();
        assertThat(setting.error()).contains("switchByWindowHotkey", "Hyper+F");
    }

    @Test
    void failureExplainsTheTakenKey() {
        assertThat(WindowHotkey.failure("Win+F", 1409))
                .contains("Win+F", "already taken", "switchByWindowHotkey");
        assertThat(WindowHotkey.failure("Win+K", 5)).contains("Windows error 5");
        assertThat(WindowHotkey.failure("Win+K", -1)).contains("did not start");
    }
}
