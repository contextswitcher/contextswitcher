package com.contextswitcher.terminal;

import java.nio.file.Path;
import java.util.List;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.local.RequiredTools;
import com.contextswitcher.ssh.SshCommandRunner;
import org.jspecify.annotations.Nullable;

/// The mirror's side channel for a **local** tmux server — the same commands
/// [TmuxMirrorCommands] builds for a remote, run on this machine instead of
/// through `ssh`.
///
/// A local Claude session lives in the local tmux server
/// ([LocalClaudeLauncher]), so its task has a `tmux:` section but no
/// `remote:`; the pane addresses it as [TmuxMirrorCommands#LOCAL_HOST] and
/// routes its commands here. Each one runs as `sh -c "<the words the remote
/// shell would have seen>"`, so the builders need no local dialect: the same
/// `\;` separators and quoted formats survive one shell either way.
// [impl->dsn~terminal-local-mirror~2]
public class LocalTmuxRunner implements SshCommandRunner {

    /// The shell the command line goes through — the local stand-in for the
    /// remote login shell `ssh` would have handed it to.
    public static final String SHELL = "/bin/sh";

    private final LocalCommandRunner runner;

    public LocalTmuxRunner() {
        this(new LocalCommandRunner());
    }

    public LocalTmuxRunner(LocalCommandRunner runner) {
        this.runner = runner;
    }

    /// The shell script a remote command becomes locally: its words joined as
    /// the remote shell would have received them, with tmux's own directory
    /// prepended to `PATH`. A GUI-launched app inherits the desktop session's
    /// environment, not the login shell's, so an installed tmux can be off
    /// this process's `PATH` (`dsn~startup-tool-check~1`) — and the script
    /// names `tmux` in several places, so the fix belongs in `PATH` rather
    /// than in each argv.
    public static String script(List<String> command) {
        Path tmux = RequiredTools.find("tmux");
        Path directory = tmux == null ? null : tmux.getParent();
        String prefix = directory == null ? "" : "PATH=\"" + directory + ":$PATH\"; ";
        return prefix + String.join(" ", command);
    }

    /// Runs the command locally; `host` is ignored — it is only ever
    /// [TmuxHost#LOCAL], the pseudo-host naming this machine.
    @Override
    public SshResult run(String host, List<String> command) {
        return run(command, null);
    }

    /// As [#run(String, List)], with `input` on the shell's stdin — what
    /// `tmux load-buffer -` and `base64 -d` read (`dsn~message-queue-send~5`),
    /// so a queued message reaches a local chat the same way it reaches a
    /// remote one.
    @Override
    public SshResult runWithInput(String host, List<String> command, byte[] input) {
        return run(command, input);
    }

    private SshResult run(List<String> command, byte @Nullable [] input) {
        LocalCommandRunner.LocalResult result =
                runner.run(List.of(SHELL, "-c", script(command)), home(), input);
        return new SshResult(result.exitCode(), result.stdout(), result.stderr());
    }

    /// The working directory every command starts in: the user's home — where
    /// a fresh ssh exec channel starts too, which is what the relative paths
    /// in the remote command lines (the attachment drop directory) assume.
    private static @Nullable Path home() {
        String home = System.getProperty("user.home", "");
        return home.isBlank() ? null : Path.of(home);
    }
}
