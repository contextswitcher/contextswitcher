package com.contextswitcher.switching;

import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Finds the newest JetBrains Remote Development backend installed on the
/// remote (`~/.cache/JetBrains/RemoteDev/dist/…`). Gateway 2026.1 requires
/// `idePath` in connect links ("Invalid ssh link parameters: doesn't contain
/// idePath"), so a task without a pinned `intellij.ide` falls back to this
/// lookup.
// [impl->dsn~gateway-url-action~11]
public class RemoteIdeLookup {

    private final SshCommandRunner ssh;

    public RemoteIdeLookup(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    /// `-t` sorts newest first, `-d` lists the glob matches as paths instead
    /// of their contents; `realpath` canonicalizes the winner — the backend
    /// registers itself under its real path (e.g. `/export/home/...` behind
    /// a `/home` symlink), and Gateway treats a non-canonical `idePath` as a
    /// different IDE, prompting "Requested Project Is Already Running".
    /// `$HOME`, the glob, and the pipes expand in the remote shell — no
    /// quotes anywhere (Windows ssh.exe swallows embedded double quotes).
    static List<String> remoteCommand() {
        return List.of("ls", "-1td", "$HOME/.cache/JetBrains/RemoteDev/dist/*",
                "|", "head", "-n", "1", "|", "xargs", "realpath");
    }

    /// The absolute path of the newest installed backend, or null when the
    /// remote has none (or the lookup failed — logged, not thrown).
    public @Nullable String newestIdeDist(String remote) {
        SshCommandRunner.SshResult result = ssh.run(remote, remoteCommand());
        if (!result.ok() || result.stdout().isBlank()) {
            Logger.debug("No remote IDE backend found on {}: {}", remote, result.stderr().strip());
            return null;
        }
        return result.stdout().lines().findFirst().map(String::strip).orElse(null);
    }
}
