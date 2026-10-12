package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The name proposed for an existing category whose name a new category
/// from a repository URL wants: named after the machine it lives on.
// [utest->dsn~category-name-clash~1]
class CategoryClashProposalTest {

    @Test
    void localWithoutRemote() {
        assertThat(MainWindow.clashRenameProposal("jabref", null)).isEqualTo("jabref-local");
    }

    @Test
    void localPseudoHost() {
        assertThat(MainWindow.clashRenameProposal("jabref", "(local)")).isEqualTo("jabref-local");
    }

    @Test
    void sshAliasAsIs() {
        assertThat(MainWindow.clashRenameProposal("jabref", "swkgpu1")).isEqualTo("jabref-swkgpu1");
    }

    @Test
    void userAndDomainDropped() {
        assertThat(MainWindow.clashRenameProposal("jabref", "me@Devbox.example.org"))
                .isEqualTo("jabref-devbox");
    }

    @Test
    void ipAddressKept() {
        assertThat(MainWindow.clashRenameProposal("jabref", "me@10.0.0.5"))
                .isEqualTo("jabref-10.0.0.5");
    }

    @Test
    void wslDistribution() {
        assertThat(MainWindow.clashRenameProposal("jabref", "wsl:Ubuntu"))
                .isEqualTo("jabref-wsl-ubuntu");
    }
}
