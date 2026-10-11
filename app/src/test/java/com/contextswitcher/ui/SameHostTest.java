package com.contextswitcher.ui;

import com.contextswitcher.terminal.TmuxHost;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Which categories' roots the setup wizard proposes a new root next to:
/// only those on the chosen session host.
// [utest->dsn~setup-wizard~10]
class SameHostTest {

    @Test
    void theUserPartAndCaseDoNotMatter() {
        assertThat(MainWindow.sameHost("nixos", "koppor@nixos")).isTrue();
        assertThat(MainWindow.sameHost("koppor@NixOS", "nixos")).isTrue();
    }

    @Test
    void anotherMachineIsNotTheSame() {
        assertThat(MainWindow.sameHost("koppor@swkgpu1", "koppor@nixos")).isFalse();
    }

    @Test
    void aCategoryWithoutRemoteRunsOnThisMachine() {
        assertThat(MainWindow.sameHost(null, TmuxHost.LOCAL)).isTrue();
        assertThat(MainWindow.sameHost(null, "koppor@nixos")).isFalse();
        assertThat(MainWindow.sameHost("koppor@nixos", TmuxHost.LOCAL)).isFalse();
    }
}
