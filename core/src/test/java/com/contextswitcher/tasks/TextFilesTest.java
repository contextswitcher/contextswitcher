package com.contextswitcher.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TextFilesTest {

    @Test
    void writesAndReadsUtf8(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("t.md");
        TextFiles.write(file, "Grüße — ✓\n");
        assertThat(TextFiles.read(file)).isEqualTo("Grüße — ✓\n");
    }

    /// `:core` runs on Android, whose runtimes need not have these two (a
    /// Lenovo tablet on Android 16 crashed on `Files.readString`).
    @Test
    void coreMainCodeDoesNotCallFilesReadStringOrWriteString() throws IOException {
        Path main = Path.of("src/main/java");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(main)) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String source = Files.readString(p);
                            return source.contains("Files.readString(") || source.contains("Files.writeString(");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertThat(offenders).isEmpty();
    }
}
