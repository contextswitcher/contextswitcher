package com.contextswitcher.tasks;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Machine-scoped key suffixes: `key-<os>` and `key-<hostname>` fold into
/// `key` for the machine reading the file, most specific last.
// [utest->dsn~machine-key-variants~1]
class MachineKeyVariantsTest {

    /// This machine, for these tests: Linux, host `devbox` — passed in
    /// explicitly so the expectations do not depend on the test runner's box.
    private static final List<String> TOKENS = List.of("linux", "devbox");

    private static Map<String, Object> fold(Map<String, Object> data) {
        return TaskFileParser.applyMachineVariants(data, TOKENS);
    }

    @Test
    void aPlainKeyStaysUntouched() {
        assertThat(fold(Map.of("workdir", "/data/koppor/jabref")))
                .containsEntry("workdir", "/data/koppor/jabref");
    }

    @Test
    void theOsVariantReplacesThePlainValue() {
        Map<String, Object> folded = fold(new java.util.LinkedHashMap<>(Map.of(
                "workdir", "C:\\git\\jabref",
                "workdir-linux", "/data/koppor/jabref")));
        assertThat(folded).containsEntry("workdir", "/data/koppor/jabref");
    }

    /// The host is more specific than the OS, whatever order the file lists
    /// them in — the resolution is by token, not by position.
    @Test
    void theHostVariantBeatsTheOsVariant() {
        Map<String, Object> folded = fold(new java.util.LinkedHashMap<>(Map.of(
                "mainCheckout-devbox", "/data/koppor/jabref-workspaces/jabref",
                "mainCheckout-linux", "/home/other/jabref",
                "mainCheckout", "C:\\git\\jabref")));
        assertThat(folded).containsEntry("mainCheckout", "/data/koppor/jabref-workspaces/jabref");
    }

    /// A value scoped to a machine that is not this one must not leak into the
    /// plain key; it stays as an unknown key, which every parse rule ignores.
    @Test
    void aForeignVariantDoesNotApply() {
        Map<String, Object> folded = fold(new java.util.LinkedHashMap<>(Map.of(
                "workdir", "/data/koppor/jabref",
                "workdir-windows", "C:\\git\\jabref",
                "workdir-laptop", "D:\\jabref")));
        assertThat(folded).containsEntry("workdir", "/data/koppor/jabref");
    }

    @Test
    void listValuesFoldLikeScalars() {
        Map<String, Object> folded = fold(new java.util.LinkedHashMap<>(Map.of(
                "folders", List.of("C:\\git\\jabref"),
                "folders-linux", List.of("/data/koppor/jabref", "/data/koppor/notes"))));
        assertThat(folded).containsEntry("folders", List.of("/data/koppor/jabref", "/data/koppor/notes"));
    }

    /// Sections are folded too, so a nested key can be machine-scoped without
    /// restating the whole section.
    @Test
    void nestedSectionKeysFoldAsWell() {
        Map<String, Object> folded = fold(Map.of("intellij", new java.util.LinkedHashMap<>(Map.of(
                "projectPath", "C:\\git\\jabref",
                "projectPath-devbox", "/data/koppor/jabref"))));
        assertThat(folded.get("intellij")).isInstanceOfSatisfying(Map.class,
                intellij -> assertThat(intellij).containsEntry("projectPath", "/data/koppor/jabref"));
    }

    /// A key that *is* only the suffix (`-linux`) is a plain key, not a
    /// variant of an empty one.
    @Test
    void aBareSuffixIsAPlainKey() {
        assertThat(fold(Map.of("-linux", "x"))).containsEntry("-linux", "x");
    }

    /// With no host name resolvable the token is empty; the empty suffix must
    /// not turn every key into a variant of itself.
    @Test
    void anEmptyTokenMatchesNothing() {
        Map<String, Object> folded = TaskFileParser.applyMachineVariants(
                Map.of("workdir", "/data/koppor/jabref"), List.of("linux", ""));
        assertThat(folded).containsExactlyEntriesOf(Map.of("workdir", "/data/koppor/jabref"));
    }
}
