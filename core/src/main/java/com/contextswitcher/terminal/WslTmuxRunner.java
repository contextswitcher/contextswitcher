package com.contextswitcher.terminal;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.ssh.SshCommandRunner;

/// The side channel for a tmux server inside **WSL** — the same commands
/// [TmuxMirrorCommands] builds for a remote, run through `wsl.exe` instead of
/// through `ssh`.
///
/// A WSL session is a remote in every way the app cares about: its task
/// carries a `remote:` line (`wsl:<distro>`), a `tmux:` section and a mirrored
/// pane, and every poller, the message queue and the attachment drop address
/// it by host. Only the transport differs, which is the whole point of
/// [HostCommandRunner] — so this class is the one place that knows WSL exists.
///
/// Each command runs as `sh -lc "<the words the remote shell would have
/// seen>"`: a **login** shell, unlike the local runner's `sh -c`, because the
/// tools live inside the distribution and are found the way the tmux window
/// finds them — the same reason [com.contextswitcher.ui.SetupWizard]'s remote
/// probe uses one.
// [impl->dsn~wsl-sessions~1]
public class WslTmuxRunner implements SshCommandRunner {

    /// The Windows executable every WSL command goes through.
    public static final String WSL = "wsl.exe";

    /// The shell inside the distribution.
    public static final String SHELL = "sh";

    /// Prepended to every script, so a `claude` put under `~/.local/bin` by
    /// the native installer is found even when the profile does not add it —
    /// the same widening the ssh probe does.
    public static final String PATH_PREFIX = "PATH=$PATH:$HOME/.local/bin; ";

    private final LocalCommandRunner runner;

    public WslTmuxRunner() {
        this(new LocalCommandRunner());
    }

    public WslTmuxRunner(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// The Windows argv running `command` inside `host`'s distribution.
    ///
    /// The command words are joined into one `sh -lc` argument exactly as
    /// [LocalTmuxRunner#script] does, so the tmux builders need no WSL
    /// dialect: their `\;` separators and `'#{…}'` formats survive one shell
    /// either way.
    // ponytail: relies on Java's MSVC-style quoting of that one argument
    // surviving wsl.exe's own command-line parsing. If a tmux format with
    // embedded quotes comes through mangled on a real Windows box, send the
    // script base64-encoded instead (`echo <b64> | base64 -d | sh`), which no
    // quoting layer can touch.
    public static List<String> argv(String host, List<String> command) {
        List<String> argv = new ArrayList<>();
        argv.add(WSL);
        String distro = TmuxHost.distro(host);
        if (!distro.isEmpty()) {
            argv.add("-d");
            argv.add(distro);
        }
        argv.add("--");
        argv.add(SHELL);
        argv.add("-lc");
        argv.add(PATH_PREFIX + String.join(" ", command));
        return List.copyOf(argv);
    }

    @Override
    public SshResult run(String host, List<String> command) {
        return run(host, command, null);
    }

    /// As [#run(String, List)], with `input` on the shell's stdin — what
    /// `tmux load-buffer -` and `base64 -d` read, so a queued message reaches
    /// a WSL chat the same way it reaches a remote one.
    @Override
    public SshResult runWithInput(String host, List<String> command, byte[] input) {
        return run(host, command, input);
    }

    private SshResult run(String host, List<String> command, byte @Nullable [] input) {
        LocalCommandRunner.LocalResult result = runner.run(argv(host, command), null, input);
        return new SshResult(result.exitCode(), result.stdout(), result.stderr());
    }
}
