package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-field-form~7]
class ListFieldEditorPathTest {

    @Test
    void posixDrivePathsBecomeWindowsPaths() {
        assertThat(ListFieldEditor.windowsPath("/c/git-repositories/github.com/x"))
                .isEqualTo("C:\\git-repositories\\github.com\\x");
        assertThat(ListFieldEditor.windowsPath("/cygdrive/d/p")).isEqualTo("D:\\p");
        assertThat(ListFieldEditor.windowsPath("/mnt/c/p ")).isEqualTo("C:\\p");
        assertThat(ListFieldEditor.windowsPath("/c")).isEqualTo("C:\\");
    }

    @Test
    void otherTextStaysAsIs() {
        assertThat(ListFieldEditor.windowsPath("/home/koppor/p")).isEqualTo("/home/koppor/p");
        assertThat(ListFieldEditor.windowsPath("C:\\p")).isEqualTo("C:\\p");
        assertThat(ListFieldEditor.windowsPath("https://x")).isEqualTo("https://x");
    }
}
