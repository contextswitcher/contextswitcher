package com.contextswitcher.switching;

import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~gateway-url-action~11]
class RemoteIdeLookupTest {

    /// Ssh runner answering every call with the one scripted result.
    private static final class FakeSsh implements SshCommandRunner {
        final SshResult result;

        FakeSsh(SshResult result) {
            this.result = result;
        }

        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            return result;
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            return run(host, remoteCommand);
        }
    }

    @Test
    void commandListsDistsNewestFirstCanonicalizedWithoutQuotes() {
        assertThat(RemoteIdeLookup.remoteCommand())
                .containsExactly("ls", "-1td", "$HOME/.cache/JetBrains/RemoteDev/dist/*",
                        "|", "head", "-n", "1", "|", "xargs", "realpath")
                .noneMatch(part -> part.contains("\""));
    }

    @Test
    void newestDistIsTheFirstLine() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(0,
                "/home/koppor/.cache/JetBrains/RemoteDev/dist/b1e1_idea-262.8665.176\n"
                        + "/home/koppor/.cache/JetBrains/RemoteDev/dist/a0d0_idea-261.1111.11\n", ""));

        assertThat(new RemoteIdeLookup(ssh).newestIdeDist("koppor@devbox"))
                .isEqualTo("/home/koppor/.cache/JetBrains/RemoteDev/dist/b1e1_idea-262.8665.176");
    }

    @Test
    void missingDistDirectoryYieldsNull() {
        FakeSsh ssh = new FakeSsh(new SshCommandRunner.SshResult(2, "",
                "ls: cannot access '/home/koppor/.cache/JetBrains/RemoteDev/dist/*': No such file or directory"));

        assertThat(new RemoteIdeLookup(ssh).newestIdeDist("koppor@devbox")).isNull();
    }
}
