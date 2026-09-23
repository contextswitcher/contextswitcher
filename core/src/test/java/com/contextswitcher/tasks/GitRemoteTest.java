package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-issue-links~2]
class GitRemoteTest {

    @ParameterizedTest
    @CsvSource({
            "git@github.com:contextswitcher/contextswitcher.git, https://github.com/contextswitcher/contextswitcher",
            "git@github.com:JabRef/jabref, https://github.com/JabRef/jabref",
            "ssh://git@github.com/JabRef/jabref.git, https://github.com/JabRef/jabref",
            "ssh://git@gitlab.example.org:2222/group/project.git, https://gitlab.example.org/group/project",
            "https://github.com/JabRef/jabref.git, https://github.com/JabRef/jabref",
            "http://gitea.local/owner/repo/, https://gitea.local/owner/repo",
    })
    void mapsCloneUrlToWebUrl(String remote, String expected) {
        assertThat(GitRemote.repoUrl(remote)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "/data/koppor/checkout", "../sibling.git", "git@github.com:"})
    void rejectsWhatIsNoRemoteUrl(String remote) {
        assertThat(GitRemote.repoUrl(remote)).isNull();
    }

    @Test
    void stripsTheTrailingNewlineOfGitConfigOutput() {
        assertThat(GitRemote.repoUrl("git@github.com:JabRef/jabref.git\n"))
                .isEqualTo("https://github.com/JabRef/jabref");
    }
}
