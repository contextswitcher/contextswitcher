package com.contextswitcher.terminal;

import java.util.List;

import com.contextswitcher.ssh.SshCommandRunner;

/// Routes a command by its host: [TmuxHost#LOCAL] runs on this machine
/// ([LocalTmuxRunner]), a `wsl:<distro>` host inside WSL ([WslTmuxRunner]),
/// every real host over ssh.
///
/// Wrapping the app's one ssh runner in this is what makes a **local** tmux
/// session work everywhere a remote one does — mirror, kill, snapshot, status
/// poll — without any of them learning a second dialect: they keep passing a
/// host and a remote-shell command line, and only the transport differs.
/// Nothing but a local task ever names the pseudo-host, so every other call
/// takes the ssh route exactly as before.
// [impl->dsn~terminal-local-mirror~2]
// [impl->dsn~wsl-sessions~1]
public class HostCommandRunner implements SshCommandRunner {

    private final SshCommandRunner ssh;
    private final SshCommandRunner local;
    private final SshCommandRunner wsl;

    public HostCommandRunner(SshCommandRunner ssh) {
        this(ssh, new LocalTmuxRunner());
    }

    public HostCommandRunner(SshCommandRunner ssh, SshCommandRunner local) {
        this(ssh, local, new WslTmuxRunner());
    }

    public HostCommandRunner(SshCommandRunner ssh, SshCommandRunner local, SshCommandRunner wsl) {
        this.ssh = ssh;
        this.local = local;
        this.wsl = wsl;
    }

    private SshCommandRunner runner(String host) {
        if (TmuxHost.isLocal(host)) {
            return local;
        }
        return TmuxHost.isWsl(host) ? wsl : ssh;
    }

    @Override
    public SshResult run(String host, List<String> command) {
        return runner(host).run(host, command);
    }

    @Override
    public SshResult runWithInput(String host, List<String> command, byte[] input) {
        return runner(host).runWithInput(host, command, input);
    }
}
