package com.contextswitcher.ssh;

import java.util.List;

/// Runs a command on a remote host. The desktop implementation spawns the
/// system `ssh` executable ([ProcessSshRunner], MADR 0003); the Android app
/// provides an in-process client instead (MADR 0027) — the phone has no ssh
/// binary. Everything remote-facing ([com.contextswitcher.discovery],
/// [com.contextswitcher.queue], the tmux command builders) talks to this
/// interface and works against either.
public interface SshCommandRunner {

    /// Outcome of one remote command; `stderr` is the debugging story on failure.
    record SshResult(int exitCode, String stdout, String stderr) {

        public boolean ok() {
            return exitCode == 0;
        }
    }

    SshResult run(String host, List<String> remoteCommand);

    /// As [#run], but with `input` piped to the remote command's stdin
    /// (`tmux load-buffer -`, `base64 -d`).
    SshResult runWithInput(String host, List<String> remoteCommand, byte[] input);

    /// POSIX single-quoting for one word of a remote command: wraps it in
    /// `'…'`, and an embedded single quote becomes `'\''` (end quote, escaped
    /// quote, reopen) — the remote shell sees the word as written.
    static String quote(String word) {
        return "'" + word.replace("'", "'\\''") + "'";
    }
}
