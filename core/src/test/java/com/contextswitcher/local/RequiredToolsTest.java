package com.contextswitcher.local;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~startup-tool-check~1]
class RequiredToolsTest {

    /// Invented names, so no real installation in the well-known directories
    /// (which are searched after `PATH`) can decide the outcome.
    private static final String PRESENT = "cs-present-tool";
    private static final String ABSENT = "cs-absent-tool";

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void namesOnlyTheToolsNoSearchedDirectoryHolds(@TempDir Path bin, @TempDir Path other)
            throws Exception {
        Path tool = bin.resolve(PRESENT);
        Files.writeString(tool, "#!/bin/sh\n");
        tool.toFile().setExecutable(true);
        String path = String.join(File.pathSeparator, other.toString(), bin.toString());

        assertThat(RequiredTools.missing(path, List.of(PRESENT, ABSENT)))
                .containsExactly(ABSENT);
        assertThat(RequiredTools.find(path, PRESENT)).isEqualTo(tool);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aNonExecutableFileDoesNotCount(@TempDir Path bin) throws Exception {
        Files.writeString(bin.resolve(PRESENT), "not executable\n");
        bin.resolve(PRESENT).toFile().setExecutable(false);

        assertThat(RequiredTools.missing(bin.toString(), List.of(PRESENT))).containsExactly(PRESENT);
    }

    /// With no `PATH` at all the well-known directories are still searched —
    /// a GUI-launched app on a Nix or Homebrew machine has the tool installed
    /// but not on the environment it inherited.
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void fallsBackToTheWellKnownDirectories() {
        assertThat(RequiredTools.missing(null, List.of(ABSENT))).containsExactly(ABSENT);
        assertThat(RequiredTools.find(null, "sh")).isNotNull();
    }

    /// A tool that cannot be found stays a bare name, so the caller can still
    /// build an argv and let the OS produce the error.
    @Test
    void resolveKeepsTheBareNameWhenNothingHoldsIt() {
        assertThat(RequiredTools.resolve(ABSENT)).isEqualTo(ABSENT);
    }
}
