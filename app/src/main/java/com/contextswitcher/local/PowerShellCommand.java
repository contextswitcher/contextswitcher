package com.contextswitcher.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// The `powershell` argv for a script, with no shell quoting involved.
///
/// A script normally travels as `-EncodedCommand` (base64 of UTF-16LE), which
/// costs 8/3 argv characters per script character. Windows caps a command
/// line at 32 767 characters, so a script beyond roughly 12 000 characters
/// fails to start with `CreateProcess error=206, The filename or extension
/// is too long` — the environment-profile and window-gather scripts crossed
/// that line once they embedded the screen types (field report 2026-11-18,
/// profile `lecture`). Such a script is written to a temp file named by its
/// hash, and the encoded command becomes a one-line loader that dot-sources
/// it: no `-File`, so the execution policy does not apply, and the script
/// runs in the same scope as it would inline (`exit` codes included).
public final class PowerShellCommand {

    /// Longest `-EncodedCommand` argument passed inline: the 32 767-character
    /// command-line cap minus room for `powershell` and the other args.
    static final int MAX_INLINE_ENCODED = 32_000;

    private PowerShellCommand() {
    }

    public static List<String> of(String script) {
        String encoded = encode(script);
        if (encoded.length() <= MAX_INLINE_ENCODED) {
            return argv(encoded);
        }
        Path file = scriptFile(script);
        if (file == null) {
            return argv(encoded); // fails to start, and the caller reports why
        }
        String loader = ". ([scriptblock]::Create([IO.File]::ReadAllText('%s', [Text.Encoding]::UTF8)))"
                .formatted(file.toString().replace("'", "''"));
        return argv(encode(loader));
    }

    private static List<String> argv(String encoded) {
        return List.of("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    private static String encode(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    /// The script under the temp directory, named by its SHA-256 so the
    /// same script is written once and parallel callers never see a
    /// half-written file (written aside, then moved into place).
    static @Nullable Path scriptFile(String script) {
        byte[] bytes = script.getBytes(StandardCharsets.UTF_8);
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "contextswitcher-ps");
        Path file = dir.resolve(sha256(bytes) + ".ps1");
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(dir);
                Path part = Files.createTempFile(dir, "script", ".part");
                Files.write(part, bytes);
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            return file;
        } catch (IOException e) {
            Logger.warn("Cannot write PowerShell script {}: {}", file, e.getMessage());
            return null;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
