package com.contextswitcher.ssh;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Lists and downloads the files a remote Claude Code session generated.
///
/// A session writes its working files into a scratchpad directory
/// `/tmp/claude-<uid>/<encoded-cwd>/<session-uuid>/scratchpad` — a drafted
/// mail, a generated report, a rendered chart. The chat prints the absolute
/// path, but reading it out of the terminal is not usable: Claude Code's TUI
/// hard-wraps a long path across lines with its own gutter text in between,
/// so neither a click-the-link filter nor a copy of the selection recovers it.
/// The app therefore never parses the terminal — it asks the remote for the
/// newest scratchpad files directly.
///
/// The counterpart of `QueueSendCommands.uploadCommand`, and it uses the same
/// transport: base64 over the ssh channel, so the bytes stay ASCII and no
/// quoting rule can mangle them.
// [impl->dsn~generated-file-download~2]
public class RemoteFiles {

    /// One file offered for download; `modified` is whole seconds since the
    /// epoch (`find -printf %T@` gives fractions we do not need).
    public record RemoteFile(String path, long size, long modified) {

        /// The last path segment, reduced to characters every filesystem the
        /// app runs on accepts — the same sanitising `PaneSnapshots` applies
        /// to task ids. The remote names the file; the local name must not be
        /// whatever that name happens to be.
        public String fileName() {
            int slash = path.lastIndexOf('/');
            String name = slash < 0 ? path : path.substring(slash + 1);
            String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
            return safe.isBlank() || safe.equals(".") || safe.equals("..") ? "download" : safe;
        }
    }

    /// How many of the newest files the list offers. A menu, not a file
    /// browser: what was generated in this session sits at the top.
    static final int LIST_LIMIT = 20;

    /// Refused above this size. The payload travels base64 through an
    /// `SshResult.stdout` string, so it is held in memory some four times
    /// over; a generated document is kilobytes, and a multi-gigabyte log is
    /// not what this button is for.
    public static final long MAX_DOWNLOAD_BYTES = 32L * 1024 * 1024;

    private final SshCommandRunner ssh;

    public RemoteFiles(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    /// The newest scratchpad files across **all** of the remote user's Claude
    /// sessions, newest first.
    ///
    /// Unquoted glob, `$(id -u)`, redirect and pipe: expanded by the remote
    /// shell, so this stays one ssh round-trip (the `ClaudeSessionLookup`
    /// pattern). `2>/dev/null` swallows the "no such file" a glob that matched
    /// nothing leaves behind. No double quotes — Windows `ssh.exe` eats them
    /// (`ProcessSshRunner.buildCommand`); the `\t`/`\n` reach `find` as
    /// backslash escapes inside the single-quoted format and it expands them.
    static List<String> listCommand() {
        return List.of("find", "/tmp/claude-$(id -u)/*/*/scratchpad",
                "-maxdepth", "2", "-type", "f",
                "-printf", LIST_FORMAT, "2>/dev/null",
                "|", "sort", "-rn", "|", "head", "-n", String.valueOf(LIST_LIMIT));
    }

    /// `find`'s output format, shared by [#listCommand] and [#statCommand] so
    /// [#parseList] reads both: modification time, size, path. Single-quoted
    /// through the transport, so `find` — not the shell — expands the escapes.
    static final String LIST_FORMAT = "'%T@\\t%s\\t%p\\n'";

    /// Parses [#listCommand]'s tab-separated output; an unparsable line is
    /// skipped rather than failing the whole listing.
    ///
    /// A path containing a single quote is dropped: it is the one character
    /// [#readCommand]'s quoting cannot carry, and a file that cannot be
    /// downloaded has no business in a download menu.
    static List<RemoteFile> parseList(String stdout) {
        List<RemoteFile> files = new ArrayList<>();
        for (String line : stdout.lines().toList()) {
            String[] parts = line.split("\t", 3);
            if (parts.length < 3 || parts[2].isBlank()) {
                continue;
            }
            if (parts[2].indexOf('\'') >= 0) {
                Logger.debug("Skipping remote file with a quote in its path: {}", parts[2]);
                continue;
            }
            try {
                files.add(new RemoteFile(parts[2].strip(),
                        Long.parseLong(parts[1].strip()),
                        (long) Double.parseDouble(parts[0].strip())));
            } catch (NumberFormatException e) {
                Logger.debug("Unparsable find output line: {}", line);
            }
        }
        return List.copyOf(files);
    }

    /// Reads one remote file as base64 on stdout — single-quoted, so a space
    /// in the path stays one operand.
    static List<String> readCommand(String path) {
        return List.of("base64", "'" + path + "'");
    }

    /// One named file in [#listCommand]'s output format, so [#parseList] reads
    /// it too: `find` again, with `-maxdepth 0` (the path itself, no descent)
    /// and the same `-type f` — a directory or a missing path yields no line
    /// rather than something undownloadable.
    static List<String> statCommand(String path) {
        return List.of("find", "'" + path + "'", "-maxdepth", "0", "-type", "f",
                "-printf", LIST_FORMAT, "2>/dev/null");
    }

    /// A path as the user typed it, made fit for [#statCommand]'s quoting, or
    /// null when it cannot be used.
    ///
    /// `~` and `~user` are dropped: the path travels single-quoted, so the
    /// remote shell would not expand a tilde anyway, and a bare `~/x` is the
    /// same file as the relative `x` — the ssh exec channel starts in the
    /// remote home. A single quote is refused for the reason
    /// [#parseList] drops such a path from the listing.
    public static @Nullable String cleanPath(String typed) {
        String path = typed.strip();
        if (path.isEmpty() || path.indexOf('\'') >= 0) {
            return null;
        }
        if (path.equals("~")) {
            return null;
        }
        return path.startsWith("~/") ? path.substring(2) : path;
    }

    /// The named file on `host`, or null when it does not exist, is not a
    /// regular file, or the host is unreachable. Blocking.
    public @Nullable RemoteFile stat(String host, String path) {
        SshCommandRunner.SshResult result = ssh.run(host, statCommand(path));
        if (!result.ok()) {
            Logger.debug("Cannot stat {} on {}: {}", path, host, result.stderr().strip());
            return null;
        }
        List<RemoteFile> found = parseList(result.stdout());
        return found.isEmpty() ? null : found.get(0);
    }

    /// The user's download folder: `~/Downloads` where it exists (Windows,
    /// macOS and every desktop Linux create it), the home directory otherwise.
    // ponytail: no XDG `user-dirs.dirs` parsing for a localised Linux download
    // folder — add it if someone downloads into the wrong place.
    public static Path downloadsDir() {
        Path home = Path.of(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        return Files.isDirectory(downloads) ? downloads : home;
    }

    /// `dir/name`, or the first free `dir/name-2`, `dir/name-3`, … when that
    /// is taken — downloading the same generated file twice must not silently
    /// overwrite the earlier copy.
    static Path uniquePath(Path dir, String name) {
        if (!Files.exists(dir.resolve(name))) {
            return dir.resolve(name);
        }
        int dot = name.lastIndexOf('.');
        String base = dot <= 0 ? name : name.substring(0, dot);
        String extension = dot <= 0 ? "" : name.substring(dot);
        for (int i = 2; i < 1000; i++) {
            Path candidate = dir.resolve(base + "-" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return dir.resolve(base + "-" + System.currentTimeMillis() + extension);
    }

    /// The [#LIST_LIMIT] newest generated files on `host`, newest first;
    /// empty when the host has no scratchpads or is unreachable. Blocking.
    public List<RemoteFile> list(String host) {
        SshCommandRunner.SshResult result = ssh.run(host, listCommand());
        if (!result.ok()) {
            Logger.warn("Cannot list generated files on {}: {}", host, result.stderr().strip());
            return List.of();
        }
        return parseList(result.stdout());
    }

    /// Where **this machine's** Claude sessions keep their scratchpads: Claude
    /// Code puts them under the temp directory as `claude/<encoded-cwd>/<session>/scratchpad`
    /// — the remote layout without the `-<uid>` suffix.
    // [impl->dsn~terminal-owned-session~3]
    public static Path localScratchpads() {
        return Path.of(System.getProperty("java.io.tmpdir"), "claude");
    }

    /// [#list] for an app-owned local session, which has no host to ask: the
    /// [#LIST_LIMIT] newest files in the scratchpads under `claudeTemp`
    /// ([#localScratchpads]), newest first, as absolute local paths. Same
    /// depth as the remote `find -maxdepth 2`. Empty when there are none.
    /// Blocking.
    // [impl->dsn~terminal-owned-session~3]
    public static List<RemoteFile> listLocal(Path claudeTemp) {
        if (!Files.isDirectory(claudeTemp)) {
            return List.of();
        }
        List<RemoteFile> files = new ArrayList<>();
        try (DirectoryStream<Path> projects = Files.newDirectoryStream(claudeTemp, Files::isDirectory)) {
            for (Path project : projects) {
                try (DirectoryStream<Path> sessions = Files.newDirectoryStream(project, Files::isDirectory)) {
                    for (Path session : sessions) {
                        Path scratchpad = session.resolve("scratchpad");
                        if (!Files.isDirectory(scratchpad)) {
                            continue;
                        }
                        try (Stream<Path> walk = Files.walk(scratchpad, 2)) {
                            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                                addLocal(files, file);
                            }
                        }
                    }
                }
            }
        } catch (IOException | UncheckedIOException e) {
            Logger.warn("Cannot list local scratchpads in {}: {}", claudeTemp, e.getMessage());
        }
        return files.stream()
                .sorted(Comparator.comparingLong(RemoteFile::modified).reversed())
                .limit(LIST_LIMIT)
                .toList();
    }

    private static void addLocal(List<RemoteFile> files, Path file) {
        try {
            files.add(new RemoteFile(file.toAbsolutePath().toString(), Files.size(file),
                    Files.getLastModifiedTime(file).toMillis() / 1000));
        } catch (IOException e) {
            // Gone between the walk and the stat — a session cleaning up.
            Logger.debug("Skipping local scratchpad file {}: {}", file, e.getMessage());
        }
    }

    /// Copies `file` from `host` into [#downloadsDir] and returns the local
    /// path. Blocking; throws [IOException] with a message fit for an alert
    /// when the file is too large, unreadable, or cannot be written.
    public Path download(String host, RemoteFile file) throws IOException {
        if (file.size() > MAX_DOWNLOAD_BYTES) {
            throw new IOException("%s is %s — larger than the %s download limit."
                    .formatted(file.path(), humanSize(file.size()), humanSize(MAX_DOWNLOAD_BYTES)));
        }
        SshCommandRunner.SshResult result = ssh.run(host, readCommand(file.path()));
        if (!result.ok()) {
            throw new IOException("Cannot read %s on %s: %s"
                    .formatted(file.path(), host, result.stderr().strip()));
        }
        byte[] bytes;
        try {
            // MIME decoder, not the basic one: `base64` wraps at 76 columns
            // and the basic decoder rejects the line breaks.
            bytes = Base64.getMimeDecoder().decode(result.stdout());
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot decode %s: %s".formatted(file.path(), e.getMessage()));
        }
        Path target = uniquePath(downloadsDir(), file.fileName());
        Files.write(target, bytes);
        Logger.info("Downloaded {}:{} to {}", host, file.path(), target);
        return target;
    }

    /// A size for humans: `2.1K`, `64K`, `1.4M`.
    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1fK", bytes / 1024.0).replace(".0K", "K");
        }
        return String.format(Locale.ROOT, "%.1fM", bytes / (1024.0 * 1024.0));
    }

    /// How long ago `modified` was, relative to `nowSeconds`: `3m`, `2h`, `4d`.
    public static String age(long modified, long nowSeconds) {
        long seconds = Math.max(0, nowSeconds - modified);
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return seconds / 60 + "m";
        }
        if (seconds < 86_400) {
            return seconds / 3600 + "h";
        }
        return seconds / 86_400 + "d";
    }
}
