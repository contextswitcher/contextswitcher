package com.contextswitcher.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.contextswitcher.config.OneNoteBacklinks.Failure;
import com.contextswitcher.config.OneNoteBacklinks.Link;
import com.contextswitcher.config.OneNoteBacklinks.Result;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskEntry;
import com.contextswitcher.tasks.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// [utest->dsn~onenote-backlinks~1]
class OneNoteBacklinksTest {

    private static final String PAGE = "onenote:https://d.docs.live.net/abc/Notes/Section.one"
            + "#Fix%20NPE&section-id={1}&page-id={2}&end";

    private static TaskEntry task(String id, String title, TaskStatus status, String note) {
        return new TaskEntry.Loaded(new Task(id, title, status, null, null, null, null, null, null,
                note, ""));
    }

    @Test
    void taskWithOneNoteLinkBacklinksWithTitleAndTaskUrl() {
        assertThat(OneNoteBacklinks.links(
                List.of(task("jabref/fix npe", "Fix NPE", TaskStatus.ACTIVE, PAGE)), Map.of()))
                .containsExactly(new Link(PAGE, "Fix NPE", "contextswitcher://task/jabref/fix%20npe", false));
    }

    @Test
    void doneTaskIsStruckThroughSuspendedIsNot() {
        List<Link> links = OneNoteBacklinks.links(List.of(
                task("a", "A", TaskStatus.DONE, PAGE),
                task("b", "B", TaskStatus.SUSPENDED, PAGE)), Map.of());
        assertThat(links).extracting(Link::done).containsExactly(true, false);
    }

    @Test
    void pastedClipboardKeepsOnlyTheDesktopLink() {
        String pasted = "https://onedrive.live.com/view.aspx?x=1 " + PAGE;
        assertThat(OneNoteBacklinks.links(List.of(task("a", "A", TaskStatus.ACTIVE, pasted)), Map.of()))
                .extracting(Link::note).containsExactly(PAGE);
    }

    @Test
    void nonOneNoteNotesFailedEntriesAndNoteLessTasksAreSkipped() {
        assertThat(OneNoteBacklinks.links(List.of(
                task("a", "A", TaskStatus.ACTIVE, "https://example.org/wiki"),
                new TaskEntry.Loaded(new Task("b", "B", TaskStatus.ACTIVE, null, null, null, null,
                        null, null, null, "")),
                new TaskEntry.Failed("c", "c.md", "broken")),
                Map.of("web", "https://example.org"))).isEmpty();
    }

    @Test
    void categoryBacklinksWithEncodedName() {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("my project", PAGE);
        notes.put("none", null);
        assertThat(OneNoteBacklinks.links(List.of(), notes))
                .containsExactly(new Link(PAGE, "Category my project",
                        "contextswitcher://category/my%20project", false));
    }

    @Test
    void sortedSoEqualSetsCompareEqual() {
        List<TaskEntry> entries = List.of(task("b", "B", TaskStatus.ACTIVE, PAGE),
                task("a", "A", TaskStatus.ACTIVE, PAGE));
        assertThat(OneNoteBacklinks.links(entries, Map.of()))
                .isEqualTo(OneNoteBacklinks.links(entries.reversed(), Map.of()))
                .extracting(Link::label).containsExactly("A", "B");
    }

    @Test
    void requestIsTheContractObject() throws Exception {
        var json = new ObjectMapper().readTree(OneNoteBacklinks.request(
                List.of(new Link(PAGE, "Fix \"NPE\"", "contextswitcher://task/a", true))));
        assertThat(json.path("source").asText()).isEqualTo("contextswitcher");
        assertThat(json.path("name").asText()).isEqualTo("ContextSwitcher");
        byte[] icon = java.util.Base64.getDecoder().decode(json.path("icon").asText());
        assertThat(icon).hasSizeLessThan(64 * 1024);
        assertThat(java.util.Arrays.copyOf(icon, 4)).containsExactly(0x89, 'P', 'N', 'G');
        assertThat(json.path("links")).hasSize(1);
        var link = json.path("links").get(0);
        assertThat(link.path("note").asText()).isEqualTo(PAGE);
        assertThat(link.path("label").asText()).isEqualTo("Fix \"NPE\"");
        assertThat(link.path("url").asText()).isEqualTo("contextswitcher://task/a");
        assertThat(link.path("done").asBoolean()).isTrue();
        assertThat(OneNoteBacklinks.request(List.of())).contains("\"links\":[]");
    }

    @Test
    void parsesTheAnswerAndTreatsSilenceAsTooOld() {
        assertThat(OneNoteBacklinks.parseResult("log line\n{\"updated\":2,\"removed\":1,\"unchanged\":3,"
                + "\"failed\":[{\"note\":\"onenote:x\",\"error\":\"gone\"}]}\r\n"))
                .isEqualTo(new Result(2, 1, 3, List.of(new Failure("onenote:x", "gone"))));
        assertThat(OneNoteBacklinks.parseResult(" \n")).isNull();
        assertThatThrownBy(() -> OneNoteBacklinks.parseResult("Usage: ankd …"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ankdOnlyOnWindowsWhereTheInstallerPutsIt(@TempDir Path local) throws Exception {
        assertThat(OneNoteBacklinks.installedAnkd("Windows 11", local.toString())).isNull();
        Path ankd = local.resolve("Programs/AnchorHub/ankd.exe");
        Files.createDirectories(ankd.getParent());
        Files.writeString(ankd, "");
        assertThat(OneNoteBacklinks.installedAnkd("Windows 11", local.toString())).isEqualTo(ankd);
        assertThat(OneNoteBacklinks.installedAnkd("Linux", local.toString())).isNull();
        assertThat(OneNoteBacklinks.installedAnkd("Windows 11", null)).isNull();
    }

    @Test
    void onUnlessTurnedOff() {
        assertThat(OneNoteBacklinks.enabled("tasksDir: t\n")).isTrue();
        assertThat(OneNoteBacklinks.enabled("")).isTrue();
        assertThat(OneNoteBacklinks.enabled("oneNoteBacklinks: false\n")).isFalse();
        assertThat(OneNoteBacklinks.enabled("oneNoteBacklinks: true\n")).isTrue();
    }
}
