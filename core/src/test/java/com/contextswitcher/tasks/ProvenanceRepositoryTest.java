package com.contextswitcher.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.local.LocalCommandRunner.LocalResult;
import com.contextswitcher.provenance.ProvenanceRecord;
import com.contextswitcher.provenance.ProvenanceStore;

import static org.assertj.core.api.Assertions.assertThat;

/// The desktop's path for a fresh provenance repository, with real git: an empty
/// repository cloned, a record written, one sync round — and the record is on
/// the remote. The clone of an empty repository already tracks its default
/// branch, so the first push needs no `-u`.
// [utest->dsn~provenance-repository~1]
class ProvenanceRepositoryTest {

    /// System git with a fixed identity, so commits work on CI too.
    private static final Function<List<String>, LocalResult> GIT = command -> {
        List<String> argv = new ArrayList<>(List.of("git", "-c", "user.name=Test",
                "-c", "user.email=test@example.org", "-c", "init.defaultBranch=main",
                // Pinned, so a global autoSetupRemote cannot make the first push pass by itself.
                "-c", "push.autoSetupRemote=false"));
        argv.addAll(command.subList(1, command.size()));
        return new LocalCommandRunner(Duration.ofSeconds(30)).run(argv);
    };

    @Test
    void aRecordReachesAnEmptyRemoteThroughOneRound(@TempDir Path temp) throws Exception {
        Path remote = temp.resolve("log.git");
        Path copy = temp.resolve("provenance");
        assertThat(GIT.apply(List.of("git", "init", "--bare", remote.toString())).ok()).isTrue();
        assertThat(GIT.apply(List.of("git", "clone", remote.toString(), copy.toString())).ok()).isTrue();

        ProvenanceStore.write(copy, new ProvenanceRecord(Instant.parse("2026-09-17T10:00:00Z"), "desktop-box",
                "work/fix", "Fix", null, "me@box", "@1", null, null, null, "hello"));
        boolean pushed = new TaskGitBackup(copy, GIT).syncWhileRunning("ContextSwitcher provenance").get(60, TimeUnit.SECONDS);

        assertThat(pushed).isTrue();
        Path check = temp.resolve("check");
        assertThat(GIT.apply(List.of("git", "clone", remote.toString(), check.toString())).ok()).isTrue();
        assertThat(check.resolve("work/fix/20260917T100000.000Z-desktop-box.md")).exists();
    }
}
