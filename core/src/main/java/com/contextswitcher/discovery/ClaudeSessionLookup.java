package com.contextswitcher.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.contextswitcher.ssh.SshCommandRunner;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Finds the newest Claude Code session id for a working directory on the
/// remote host: Claude stores transcripts under
/// `~/.claude/projects/<encoded-cwd>/<session-uuid>.jsonl`.
// [impl->dsn~claude-session-capture~3]
public class ClaudeSessionLookup {

    private final SshCommandRunner ssh;

    public ClaudeSessionLookup(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    /// Claude Code encodes the project cwd by replacing every separator and
    /// dot with `-`. On Windows the drive colon and the backslashes count too,
    /// so `C:\\git\\cs` becomes `C--git-cs` — the shape actually found under
    /// `%USERPROFILE%\\.claude\\projects`.
    static String encodeProjectDir(String cwd) {
        return cwd.replaceAll("[/\\\\:.]", "-");
    }

    /// The newest session id for a directory on **this** machine, read from
    /// `~/.claude/projects/<encoded>` directly — an app-owned local session
    /// (`dsn~terminal-owned-session~3`) has no host to ask over ssh.
    /// Null when the directory holds no transcript.
    public static @Nullable String findLatestLocalSession(String cwd) {
        Path dir = Path.of(System.getProperty("user.home"), ".claude", "projects",
                encodeProjectDir(cwd));
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".jsonl"))
                    .max(Comparator.comparing(ClaudeSessionLookup::modifiedAt))
                    .map(file -> {
                        String name = file.getFileName().toString();
                        return name.substring(0, name.length() - ".jsonl".length());
                    })
                    .orElse(null);
        } catch (IOException e) {
            Logger.debug("No Claude transcripts in {}: {}", dir, e.getMessage());
            return null;
        }
    }

    private static FileTime modifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    /// Unquoted glob and pipe: expanded by the remote shell (single ssh call).
    static List<String> remoteCommand(String cwd) {
        return List.of("ls", "-t",
                "~/.claude/projects/" + encodeProjectDir(cwd) + "/*.jsonl",
                "|", "head", "-1");
    }

    /// The newest session id for `cwd`, or null when none exists.
    public @Nullable String findLatestSession(String host, String cwd) {
        SshCommandRunner.SshResult result = ssh.run(host, remoteCommand(cwd));
        if (!result.ok() || result.stdout().isBlank()) {
            return null;
        }
        String path = result.stdout().strip();
        int slash = path.lastIndexOf('/');
        String fileName = slash < 0 ? path : path.substring(slash + 1);
        return fileName.endsWith(".jsonl") ? fileName.substring(0, fileName.length() - 6) : fileName;
    }
}
