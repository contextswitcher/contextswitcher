package com.contextswitcher.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/// Verifies that [YamlPatch] sets a single top-level key while leaving every
/// other line — comments, key order, and unknown keys — untouched, the property
/// the settings editor relies on to keep the raw file the source of truth.
class YamlPatchTest {

    @Test
    void replacesAScalarInPlace() {
        String doc = "tasksDir: /old\nwsPort: 17872\nhints: true\n";
        // The trailing newline is part of the untouched tail — it survives.
        assertThat(YamlPatch.set(doc, "wsPort", 9999))
                .isEqualTo("tasksDir: /old\nwsPort: 9999\nhints: true\n");
    }

    @Test
    void appendsAMissingKey() {
        String doc = "tasksDir: /old\n";
        assertThat(YamlPatch.set(doc, "claudeAuto", true))
                .isEqualTo("tasksDir: /old\nclaudeAuto: true\n");
    }

    @Test
    void preservesCommentsAndUnknownKeysAroundThePatchedKey() {
        String doc = """
                # top comment
                tasksDir: /old
                # keep me
                futureKey: something
                wsPort: 1
                """;
        String patched = YamlPatch.set(doc, "wsPort", 2);
        assertThat(patched).contains("# top comment");
        assertThat(patched).contains("# keep me");
        assertThat(patched).contains("futureKey: something");
        assertThat(patched).contains("wsPort: 2");
    }

    @Test
    void replacesAListBlockWithoutTouchingNeighbours() {
        String doc = """
                wsPort: 1
                remotes:
                - old1
                - old2
                hints: true
                """;
        String patched = YamlPatch.set(doc, "remotes", List.of("box"));
        assertThat(patched).contains("wsPort: 1");
        assertThat(patched).contains("hints: true");
        assertThat(patched).contains("- box");
        assertThat(patched).doesNotContain("old1");
        assertThat(patched).doesNotContain("old2");
    }

    @Test
    void doesNotMistakeANestedKeyForATopLevelOne() {
        // A `name:` inside the tags block must not be picked up when patching a
        // top-level `name` — here we patch wsPort and confirm the nested keys stay.
        String doc = """
                wsPort: 1
                tags:
                - name: jabref
                  color: '#2da44e'
                """;
        String patched = YamlPatch.set(doc, "wsPort", 5);
        assertThat(patched).contains("wsPort: 5");
        assertThat(patched).contains("- name: jabref");
        assertThat(patched).contains("color: '#2da44e'");
    }

    @Test
    void patchedListOfMapsRoundTripsThroughTheParser() {
        String doc = "wsPort: 1\ntags: []\n";
        String patched = YamlPatch.set(doc, "tags",
                List.of(Map.of("name", "phone", "color", "#bf3989")));
        // The result must still be a well-formed mapping the loader accepts.
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        Map<String, Object> parsed = yaml.load(patched);
        assertThat(parsed).containsKey("tags");
        assertThat(parsed.get("tags")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(1);
    }

    @Test
    void removesAKeyWithItsWholeBlock() {
        String doc = """
                tasksDir: /old
                remotes:
                - devbox
                - devbox
                wsPort: 1
                """;
        assertThat(YamlPatch.remove(doc, "remotes"))
                .isEqualTo("tasksDir: /old\nwsPort: 1\n");
        assertThat(YamlPatch.remove(doc, "notThere")).isEqualTo(doc);
    }

    /// The last key's block must not swallow the document's trailing newline —
    /// the frontmatter form patches a block that is followed by a closing `---`.
    @Test
    void patchingTheLastKeyKeepsTheTrailingNewline() {
        assertThat(YamlPatch.set("tasksDir: /old\nwsPort: 1\n", "wsPort", 2))
                .isEqualTo("tasksDir: /old\nwsPort: 2\n");
    }

    /// A key carrying a machine suffix (`folders-windows`) is a key of its own:
    /// patching the key above it must not swallow — and delete — it.
    @Test
    void aDashedKeyTerminatesTheBlockAboveIt() {
        String doc = "folders: [/data/p]\nfolders-windows: [C:/git/p]\nwsPort: 1\n";
        assertThat(YamlPatch.set(doc, "folders", List.of("/data/q")))
                .contains("folders-windows: [C:/git/p]");
        assertThat(YamlPatch.remove(doc, "folders"))
                .isEqualTo("folders-windows: [C:/git/p]\nwsPort: 1\n");
    }
}
