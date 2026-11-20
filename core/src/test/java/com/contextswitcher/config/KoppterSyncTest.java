package com.contextswitcher.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.contextswitcher.config.KoppterSync.Selection;
import com.contextswitcher.tasks.TaskFileParser;

// [utest->dsn~koppter-sync~1]
class KoppterSyncTest {

    @Test
    void koppterUrlCarriesProjectsAndContextsEncoded() {
        assertThat(KoppterSync.koppterUrl(new Selection(Set.of("papers>survey"), Set.of("desk"))))
                .isEqualTo("koppter://sync?project=papers%3Esurvey&context=desk");
        assertThat(KoppterSync.koppterUrl(Selection.EMPTY)).isEqualTo("koppter://sync");
        assertThat(KoppterSync.filterUrl(new Selection(Set.of("Küche"), Set.of())))
                .isEqualTo("contextswitcher://filter?project=K%C3%BCche");
    }

    @Test
    void parseQueryReadsWhatKoppterSends() {
        assertThat(KoppterSync.parseQuery("project=papers%3Esurvey&context=desk"))
                .isEqualTo(new Selection(Set.of("papers>survey"), Set.of("desk")));
        // Signs and a literal '+' survive; unknown keys and blanks are ignored.
        assertThat(KoppterSync.parseQuery("project=+Fair&project=K%C3%BCche&context=%40phone&color=red&context="))
                .isEqualTo(new Selection(Set.of("Fair", "Küche"), Set.of("phone")));
        assertThat(KoppterSync.parseQuery(null)).isEqualTo(Selection.EMPTY);
        assertThat(KoppterSync.parseQuery("")).isEqualTo(Selection.EMPTY);
    }

    @Test
    void roundTrip() {
        Selection selection = new Selection(Set.of("a>b", "R&D"), Set.of("desk", "lecture hall"));
        String url = KoppterSync.filterUrl(selection);
        assertThat(KoppterSync.parseQuery(url.substring(url.indexOf('?') + 1))).isEqualTo(selection);
    }

    @Test
    void categoryMatchesProjectAndBelow() {
        Selection papers = new Selection(Set.of("papers"), Set.of());
        assertThat(papers.matches("papers", List.of())).isTrue();
        assertThat(papers.matches("Papers>survey", List.of())).isTrue();
        assertThat(papers.matches("papers2", List.of())).isFalse();
        assertThat(papers.matches(null, List.of())).isFalse();
        assertThat(Selection.EMPTY.matches(null, List.of())).isTrue();
    }

    @Test
    void categoryWithoutContextsBelongsToEveryContext() {
        Selection desk = new Selection(Set.of(), Set.of("desk"));
        assertThat(desk.matches(null, List.of())).isTrue();
        assertThat(desk.matches("x", List.of("desk", "home"))).isTrue();
        assertThat(desk.matches("x", List.of("lecture"))).isFalse();
        // Both criteria hold together.
        Selection both = new Selection(Set.of("contextswitcher"), Set.of("desk"));
        assertThat(both.matches("contextswitcher", List.of("lecture"))).isFalse();
        assertThat(both.matches("contextswitcher", List.of())).isTrue();
    }

    @Test
    void profileContextDefaultsToItsName() {
        String yaml = """
                koppterSync: true
                profiles:
                  default: {gatherWindows: off}
                  desk: {gatherWindows: DELL U3223QE}
                  lecture: {context: "@hall"}
                  screenshare: {context: off}
                """;
        assertThat(KoppterSync.enabled(yaml)).isTrue();
        assertThat(KoppterSync.profileContext(yaml, "desk")).isEqualTo("desk");
        assertThat(KoppterSync.profileContext(yaml, "lecture")).isEqualTo("hall");
        assertThat(KoppterSync.profileContext(yaml, "screenshare")).isNull();
        assertThat(KoppterSync.profileContext(yaml, "default")).isNull();
        assertThat(KoppterSync.profileContext(yaml, null)).isNull();
        assertThat(KoppterSync.profileContext("profiles:\n  default: {context: home}\n", "default"))
                .isEqualTo("home");
    }

    @Test
    void offUnlessSettingsSayTrue() {
        assertThat(KoppterSync.enabled("koppterSync: false\n")).isFalse();
        assertThat(KoppterSync.enabled("tasksDir: x\n")).isFalse();
        assertThat(KoppterSync.enabled("{{ not yaml")).isFalse();
    }

    @Test
    void guardSendsNeitherTwiceNorBack() {
        KoppterSync.Guard guard = new KoppterSync.Guard();
        Selection fair = new Selection(Set.of("Fair"), Set.of());
        assertThat(guard.send(fair)).isTrue();
        assertThat(guard.send(fair)).isFalse();
        Selection desk = new Selection(Set.of(), Set.of("desk"));
        guard.received(desk);
        assertThat(guard.send(desk)).isFalse();
        assertThat(guard.send(Selection.EMPTY)).isTrue();
    }

    @Test
    void categoryNamesProjectAndContexts() {
        String config = "---\nproject: +contextswitcher\ncontexts: [desk, \"@home\"]\n---\n";
        assertThat(TaskFileParser.groupProject(config)).isEqualTo("contextswitcher");
        assertThat(TaskFileParser.groupContexts(config)).containsExactly("desk", "home");
        assertThat(TaskFileParser.groupContexts("---\ncontexts: lecture\n---\n")).containsExactly("lecture");
        assertThat(TaskFileParser.groupContexts("---\nremote: x\n---\n")).isEmpty();
        assertThat(TaskFileParser.groupProject("---\nremote: x\n---\n")).isNull();
    }
}
