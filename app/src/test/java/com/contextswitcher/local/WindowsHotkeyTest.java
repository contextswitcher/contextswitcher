package com.contextswitcher.local;

import com.contextswitcher.config.WindowHotkey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~switch-by-window-hotkey~1]
class WindowsHotkeyTest {

    @Test
    void scriptRegistersTheParsedKeyWithNoRepeatAndKnowsItsOwnProcess() {
        String script = WindowsHotkey.script(WindowHotkey.parse("Win+Shift+F"), 4242);
        assertThat(script).contains("exit [CsHotkey]::Run(12, 70, 4242)")
                .contains("RegisterHotKey(IntPtr.Zero, 1, mods | MOD_NOREPEAT, vk)")
                .contains("MOD_NOREPEAT = 0x4000")
                // stdin end-of-file is the quit signal, so a dead JVM frees the key
                .contains("Console.In.Read()").contains("PostThreadMessage(tid, WM_QUIT")
                .contains("UnregisterHotKey");
        assertThat(script).doesNotContain("__TYPES__", "__MODS__", "__VK__", "__PID__");
    }

    @Test
    void parsesAPress() {
        assertThat(WindowsHotkey.parsePress("hotkey:1234\tms-teams\tThesis weekly sync | Microsoft Teams"))
                .isEqualTo(new WindowsHotkey.Press(1234, "ms-teams", "Thesis weekly sync | Microsoft Teams"));
        assertThat(WindowsHotkey.parsePress("hotkey:0\t\t"))
                .isEqualTo(new WindowsHotkey.Press(0, "", ""));
        assertThat(WindowsHotkey.parsePress("registered")).isNull();
    }

    @Test
    void protocolLineGluedBehindAClixmlRecordStillParses() {
        String glued = "<Objs Version=\"1.1.0.1\"><Obj S=\"progress\"></Obj></Objs>hotkey:7\tms-teams\tSync";
        assertThat(WindowsHotkey.parsePress(WindowsHotkey.unglued(glued)))
                .isEqualTo(new WindowsHotkey.Press(7, "ms-teams", "Sync"));
        assertThat(WindowsHotkey.unglued("registered")).isEqualTo("registered");
    }

    @Test
    void parsesAFailure() {
        assertThat(WindowsHotkey.parseFailure("failed:1409")).isEqualTo(1409);
        assertThat(WindowsHotkey.parseFailure("failed:x")).isEqualTo(-1);
        assertThat(WindowsHotkey.parseFailure("hotkey:1\ta\tb")).isNull();
    }
}
