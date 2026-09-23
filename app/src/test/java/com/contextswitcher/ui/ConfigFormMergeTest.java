package com.contextswitcher.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.contextswitcher.tasks.Frontmatter;
import com.contextswitcher.tasks.FrontmatterCatalog;
import com.contextswitcher.tasks.FrontmatterCatalog.Field;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The configuration form's two pure halves: the seeds it fills its controls
/// from, and the merge that writes the edited values back. No display needed —
/// the robot test drives the dialog itself.
// [utest->dsn~task-field-form~4]
class ConfigFormMergeTest {

    private static final String TASK = """
            ---
            title: A task
            status: active
            remote: devbox
            # keep me
            claude:
              cwd: /home/user/p
              workspace: /home/user/p/sub
            browser:
              urls:
              - url: https://example.org/pr/1
                title: The PR
              - https://example.org/wiki
            ---

            # Notes
            """;

    private static final String CATEGORY = """
            ---
            desktop: jabref
            workspacesRoot: /data/koppor/jabref-workspaces
            folder: /data/koppor/jabref
            ---

            # jabref — group defaults
            """;

    @Test
    void seedsEveryControlFromTheFile() {
        Map<String, Object> seeds = ConfigForm.seeds(FrontmatterCatalog.TASK, TASK);

        assertThat(seeds.get("title")).isEqualTo("A task");
        assertThat(seeds.get("status")).isEqualTo("active");
        assertThat(seeds.get("claude.cwd")).isEqualTo("/home/user/p");
        assertThat(seeds.get("pinned")).isEqualTo(false);
        assertThat(seeds.get("intellij")).isEqualTo(false);
        assertThat(seeds.get("chat")).isEqualTo("");
        // A URL entry's title is editable on its line, not silently dropped.
        assertThat(seeds.get("browser.urls")).isEqualTo(
                List.of("https://example.org/pr/1 — The PR", "https://example.org/wiki"));
    }

    @Test
    void anUnchangedFormWritesNothingAtAll() {
        assertThat(merge(FrontmatterCatalog.TASK, TASK, Map.of())).isEqualTo(TASK);
        assertThat(merge(FrontmatterCatalog.CATEGORY, CATEGORY, Map.of())).isEqualTo(CATEGORY);
    }

    @Test
    void writesTheChangedKeysAndKeepsTheRest() {
        String out = merge(FrontmatterCatalog.TASK, TASK, Map.of(
                "remote", "devbox",
                "chat", "https://matrix.to/#/!room:matrix.org",
                "tags", List.of("phone", "jabref")));

        Map<String, Object> data = Frontmatter.parse(out);
        assertThat(data.get("remote")).isEqualTo("devbox");
        assertThat(data.get("chat")).isEqualTo("https://matrix.to/#/!room:matrix.org");
        assertThat(data.get("tags")).isEqualTo(List.of("phone", "jabref"));
        // The untouched keys, the comment and the body are byte-for-byte there.
        assertThat(out).contains("# keep me").contains("workspace: /home/user/p/sub")
                .contains("# Notes");
    }

    @Test
    void anEmptiedFieldRemovesItsKey() {
        String out = merge(FrontmatterCatalog.TASK, TASK, Map.of("remote", ""));

        assertThat(Frontmatter.parse(out)).doesNotContainKey("remote");
        assertThat(out).contains("title: A task").contains("# keep me");
    }

    /// A section child is written into its parent, and the children the form
    /// does not show (`claude.workspace`) survive the rewrite.
    @Test
    void aSectionChildKeepsTheSiblingsTheFormDoesNotShow() {
        String out = merge(FrontmatterCatalog.TASK, TASK, Map.of("claude.sessionId", "abc123"));

        Map<String, Object> data = Frontmatter.parse(out);
        assertThat(Frontmatter.get(data, "claude.sessionId")).isEqualTo("abc123");
        assertThat(Frontmatter.get(data, "claude.workspace")).isEqualTo("/home/user/p/sub");
    }

    /// `intellij:` is enabled by its mere presence, so the checkbox alone adds
    /// the section — and unticking it takes the whole section away again.
    @Test
    void theIntellijCheckboxAddsAndRemovesTheBareSection() {
        String enabled = merge(FrontmatterCatalog.TASK, TASK, Map.of("intellij", true));
        assertThat(Frontmatter.parse(enabled)).containsKey("intellij");

        String withPath = merge(FrontmatterCatalog.TASK, enabled,
                Map.of("intellij", true, "intellij.projectPath", "/data/koppor/p"));
        assertThat(Frontmatter.get(Frontmatter.parse(withPath), "intellij.projectPath"))
                .isEqualTo("/data/koppor/p");

        String off = merge(FrontmatterCatalog.TASK, withPath, Map.of("intellij", false));
        assertThat(Frontmatter.parse(off)).doesNotContainKey("intellij");
    }

    @Test
    void urlLinesRoundTripThroughTheirTitles() {
        String out = merge(FrontmatterCatalog.TASK, TASK, Map.of("browser.urls",
                List.of("https://example.org/pr/2 — Second PR", "https://example.org/wiki")));

        assertThat(ConfigForm.seeds(FrontmatterCatalog.TASK, out).get("browser.urls"))
                .isEqualTo(List.of("https://example.org/pr/2 — Second PR",
                        "https://example.org/wiki"));
    }

    /// The category's `desktop:` is the plain name, or the nested
    /// `{name, completeControl}` form the complete-control suspend reads.
    @Test
    void completeControlSwitchesTheDesktopKeyBetweenItsTwoShapes() {
        assertThat(ConfigForm.seeds(FrontmatterCatalog.CATEGORY, CATEGORY).get("desktop"))
                .isEqualTo("jabref");

        String nested = merge(FrontmatterCatalog.CATEGORY, CATEGORY, Map.of(
                "desktop", "jabref", "completeControl", true));
        assertThat(Frontmatter.get(Frontmatter.parse(nested), "desktop.completeControl"))
                .isEqualTo(true);
        Map<String, Object> seeds = ConfigForm.seeds(FrontmatterCatalog.CATEGORY, nested);
        assertThat(seeds.get("desktop")).isEqualTo("jabref");
        assertThat(seeds.get("completeControl")).isEqualTo(true);

        String flat = merge(FrontmatterCatalog.CATEGORY, nested, Map.of(
                "desktop", "jabref", "completeControl", false));
        assertThat(Frontmatter.parse(flat).get("desktop")).isEqualTo("jabref");
    }

    /// The `folders` list subsumes the single-directory `folder:` a local
    /// category seeds: it is shown as the list, and writing the list drops it.
    @Test
    void theLegacyFolderScalarIsShownAndReplacedByTheList() {
        assertThat(ConfigForm.seeds(FrontmatterCatalog.CATEGORY, CATEGORY).get("folders"))
                .isEqualTo(List.of("/data/koppor/jabref"));

        String out = merge(FrontmatterCatalog.CATEGORY, CATEGORY,
                Map.of("folders", List.of("/data/koppor/a", "/data/koppor/b")));
        Map<String, Object> data = Frontmatter.parse(out);
        assertThat(data.get("folders")).isEqualTo(List.of("/data/koppor/a", "/data/koppor/b"));
        assertThat(data).doesNotContainKey("folder");
    }

    /// A task without a title cannot be applied: the result names why, and
    /// nothing is merged.
    @Test
    void anEmptyTitleIsRefusedWithAReason() {
        Map<String, Object> values = new LinkedHashMap<>(ConfigForm.seeds(FrontmatterCatalog.TASK, TASK));
        values.put("title", "  ");

        assertThat(ConfigForm.mergeValues(TASK, FrontmatterCatalog.TASK, values))
                .isEqualTo(new ConfigForm.MergeResult.Invalid("The title cannot be empty."));
    }

    @Test
    void aTitledFormIsMerged() {
        Map<String, Object> values = new LinkedHashMap<>(ConfigForm.seeds(FrontmatterCatalog.TASK, TASK));
        values.put("title", "Renamed");

        assertThat(ConfigForm.mergeValues(TASK, FrontmatterCatalog.TASK, values))
                .isInstanceOfSatisfying(ConfigForm.MergeResult.Merged.class,
                        merged -> assertThat(merged.content()).contains("title: Renamed").contains("# keep me"));
    }

    /// Merges `changes` (field key → the control's value) over the file's own
    /// seeds, the way the dialog reads a form the user only partly touched.
    private static String merge(List<Field> fields, String content, Map<String, Object> changes) {
        Map<String, Object> values = new LinkedHashMap<>(ConfigForm.seeds(fields, content));
        values.putAll(changes);
        return ConfigForm.merge(content, fields, values);
    }
}
