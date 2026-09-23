package com.contextswitcher.ui;

import com.contextswitcher.ssh.SshCommandRunner;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.contextswitcher.analysis.RefactoringMinerCommands;

/// The tools the setup wizard recommends on a session host and installs in
/// one click each: RefactoringMiner behind the refactoring badge and the
/// AST-diff view, ast-grep and codegraph with their Claude Code skills —
/// what makes a session good rather than what the app itself needs, so
/// none of them gates the wizard.
///
/// Pure argv builders (`sh -c '<script>'`), run through an
/// `SshCommandRunner` on the remote or, via `HostCommandRunner`, on this
/// machine. Same transport constraint as every remote command: no double
/// quotes anywhere (`dsn~ssh-command-runner~6`).
// [impl->dsn~recommended-tools~1]
public final class RecommendedTools {

    /// One tool: its `label`, one line of `why`, the `probe` printing its
    /// version (non-zero exit when missing), the `install` script — printing
    /// the version last — and what the install reads on stdin (the codegraph
    /// skill file), or null.
    public record Tool(String key, String label, String why, List<String> probe,
            List<String> install, byte @Nullable [] installInput) {
    }

    /// The ast-grep release the wizard installs (the binary from the GitHub
    /// release zip, into `~/.local/bin`; the zip's `sg` is left out — it
    /// would shadow shadow-utils' `sg`).
    public static final String AST_GREP_VERSION = "0.45.3";
    /// The codegraph release the wizard installs, pinned for the installer.
    public static final String CODEGRAPH_VERSION = "v1.6.0";

    /// `~/.local/bin` is where the native installers put their binaries and
    /// where a non-interactive shell does not look.
    private static final String PATH_FIX = "PATH=$PATH:$HOME/.local/bin; ";

    private RecommendedTools() {
    }

    /// The tools for a remote host: all three. `refactoringMinerHome` is
    /// the configured directory to probe, null for the default one.
    public static List<Tool> remote(@Nullable String refactoringMinerHome) {
        return List.of(refactoringMiner(refactoringMinerHome), astGrep(), codegraph());
    }

    /// The tools for this machine: RefactoringMiner is left out, since the
    /// app runs it over ssh only (`dsn~refactoring-miner-commands~1`).
    public static List<Tool> local() {
        return List.of(astGrep(), codegraph());
    }

    /// Probes `home` when configured (an install under a custom path shows
    /// as found), else the wizard's own default directory; the installer
    /// always targets the default.
    static Tool refactoringMiner(@Nullable String home) {
        String probe = "H=" + (home == null ? RefactoringMinerCommands.REMOTE_HOME : home) + "; "
                + "[ -x $H/bin/RefactoringMiner ] && echo RefactoringMiner "
                + RefactoringMinerCommands.VERSION;
        return new Tool("refactoringminer", "RefactoringMiner",
                "The refactoring badge on a task row and the AST-diff view of what a session "
                        + "restructured. Needs Java on the host.",
                script(probe), RefactoringMinerCommands.setupCommand(), null);
    }

    static Tool astGrep() {
        String url = "https://github.com/ast-grep/ast-grep/releases/download/" + AST_GREP_VERSION;
        String install = PATH_FIX
                + "A=$(uname -m); case $(uname -s) in Darwin) O=apple-darwin;; *) O=unknown-linux-gnu;; esac; "
                + "mkdir -p $HOME/.local/bin || exit 1; Z=$HOME/.local/ast-grep.zip; U=" + url
                + "/app-$A-$O.zip; "
                + "{ curl -fsSL -o $Z $U || wget -q -O $Z $U; } || exit 1; "
                + "unzip -q -o $Z ast-grep -d $HOME/.local/bin || exit 1; rm -f $Z; "
                + "chmod +x $HOME/.local/bin/ast-grep; "
                + "npx -y skills add ast-grep/agent-skill -g -a claude-code -s '*' -y >/dev/null "
                + "|| echo the ast-grep skills were not installed - no npx? >&2; "
                + "ast-grep --version";
        return new Tool("ast-grep", "ast-grep",
                "Structural code search Claude reaches for instead of grep; installed with its "
                        + "two skills.",
                script(PATH_FIX + "ast-grep --version"), script(install), null);
    }

    static Tool codegraph() {
        String install = "mkdir -p $HOME/.claude/skills/codegraph || exit 1; "
                + "cat > $HOME/.claude/skills/codegraph/SKILL.md || exit 1; "
                + "curl -fsSL https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.sh "
                + "| CODEGRAPH_VERSION=" + CODEGRAPH_VERSION + " sh || exit 1; "
                + PATH_FIX + "codegraph install -y >/dev/null 2>&1; codegraph --version";
        return new Tool("codegraph", "codegraph",
                "A call-graph index of each checkout, so Claude reads the symbols it needs "
                        + "instead of grepping; installed with its MCP server and skill.",
                script(PATH_FIX + "codegraph --version"), script(install), codegraphSkill());
    }

    /// `sh -c '<script>'` — the one quoting layer every runner here accepts.
    private static List<String> script(String script) {
        return List.of("sh", "-c", SshCommandRunner.quote(script));
    }

    /// The shipped codegraph skill, written to `~/.claude/skills/codegraph/`
    /// on install — the tool ships none of its own. Line endings are LF
    /// whatever the checkout made of the resource: a Windows clone has CRLF,
    /// and the file lands on a Unix remote.
    static byte[] codegraphSkill() {
        try (InputStream in = RecommendedTools.class.getResourceAsStream("skills/codegraph-SKILL.md")) {
            if (in == null) {
                throw new IOException("codegraph-SKILL.md resource missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// The version a probe or install printed: its last non-empty stdout
    /// line, or null when the command failed.
    public static @Nullable String version(int exitCode, String stdout) {
        if (exitCode != 0) {
            return null;
        }
        return stdout.lines().map(String::strip).filter(line -> !line.isEmpty())
                .reduce((first, last) -> last).orElse(null);
    }
}
