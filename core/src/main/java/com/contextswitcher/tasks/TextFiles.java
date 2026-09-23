package com.contextswitcher.tasks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/// UTF-8 text file reads and writes for `:core`, in place of
/// `Files.readString`/`writeString`: those are not public Android API, and
/// some devices' runtimes lack them (a Lenovo tablet on Android 16 crashed with
/// `NoSuchMethodError` reading the first task file). `readAllBytes` and
/// `write` exist since Android 8 (API 26, the app's `minSdk`).
/// One difference: bytes that are not valid UTF-8 read as U+FFFD instead of
/// failing.
public final class TextFiles {

    private TextFiles() {
    }

    public static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    public static void write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }
}
