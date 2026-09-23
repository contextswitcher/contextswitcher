package com.contextswitcher.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// [utest->dsn~extension-server-protocol~4]
class ExtensionProtocolTest {

    @Test
    void deeplinkRoundTrips() {
        String encoded = ExtensionProtocol.encodeDeeplink("secret", "contextswitcher://task/jabref/fix-npe");
        assertThat(ExtensionProtocol.parse(encoded))
                .isEqualTo(new ExtensionProtocol.Deeplink("secret", "contextswitcher://task/jabref/fix-npe"));
    }

    @Test
    void deeplinkWithoutUrlRejected() {
        assertThatThrownBy(() -> ExtensionProtocol.parse("{\"type\":\"deeplink\",\"token\":\"secret\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void focusUrlEncodesAllFields() {
        assertThat(ExtensionProtocol.encodeFocusUrl("abc-1", "https://github.com/JabRef/jabref/pull/1", true))
                .isEqualTo("{\"type\":\"focus-url\",\"id\":\"abc-1\","
                        + "\"url\":\"https://github.com/JabRef/jabref/pull/1\",\"openIfMissing\":true}");
    }

    // [utest->dsn~pr-open-on-category-desktop~3]
    @Test
    void focusUrlNamesTheTargetWindowOnlyWhenGiven() {
        assertThat(ExtensionProtocol.encodeFocusUrl("abc-1", "https://example.org/", true, "Home — Mozilla Firefox"))
                .isEqualTo("{\"type\":\"focus-url\",\"id\":\"abc-1\",\"url\":\"https://example.org/\","
                        + "\"openIfMissing\":true,\"windowTitle\":\"Home — Mozilla Firefox\"}");
        assertThat(ExtensionProtocol.encodeFocusUrl("abc-1", "https://example.org/", true, null))
                .doesNotContain("windowTitle");
    }

    // [utest->dsn~browser-tab-group~2]
    @Test
    void focusUrlNamesTheTabGroupOnlyWhenGiven() {
        assertThat(ExtensionProtocol.encodeFocusUrl("abc-1", "https://example.org/", true, null, "339"))
                .isEqualTo("{\"type\":\"focus-url\",\"id\":\"abc-1\",\"url\":\"https://example.org/\","
                        + "\"openIfMissing\":true,\"group\":\"339\"}");
        assertThat(ExtensionProtocol.encodeFocusUrl("abc-1", "https://example.org/", true, null, null))
                .doesNotContain("group");
    }

    // [utest->dsn~browser-tab-group~2]
    @Test
    void focusUrlMarksABackgroundTabOnlyWhenItIsOne() {
        assertThat(ExtensionProtocol.encodeFocusUrl("abc-1", "https://example.org/", true, null, "339", true))
                .isEqualTo("{\"type\":\"focus-url\",\"id\":\"abc-1\",\"url\":\"https://example.org/\","
                        + "\"openIfMissing\":true,\"group\":\"339\",\"background\":true}");
        assertThat(ExtensionProtocol.encodeFocusUrl("abc-1", "https://example.org/", true, null, "339", false))
                .doesNotContain("background");
    }

    @Test
    void closeUrlEncodesAllFields() {
        assertThat(ExtensionProtocol.encodeCloseUrl("abc-2", "https://example.org/"))
                .isEqualTo("{\"type\":\"close-url\",\"id\":\"abc-2\",\"url\":\"https://example.org/\"}");
    }

    // [utest->dsn~browser-tab-context-count~2]
    @Test
    void tabCountEncodesTheTasksInOrder() {
        assertThat(ExtensionProtocol.encodeTabCount("https://example.org/", java.util.List.of(
                new ExtensionProtocol.TabTask("jabref/fix-npe", "Fix the NPE", "active"),
                new ExtensionProtocol.TabTask("jabref/review", "Review #15785", "suspended"))))
                .isEqualTo("{\"type\":\"tab-count\",\"url\":\"https://example.org/\",\"tasks\":["
                        + "{\"id\":\"jabref/fix-npe\",\"title\":\"Fix the NPE\",\"status\":\"active\"},"
                        + "{\"id\":\"jabref/review\",\"title\":\"Review #15785\",\"status\":\"suspended\"}]}");
    }

    // [utest->dsn~browser-tab-context-count~2]
    @Test
    void tabCountWithoutTasksSendsAnEmptyList() {
        assertThat(ExtensionProtocol.encodeTabCount("https://example.org/", java.util.List.of()))
                .isEqualTo("{\"type\":\"tab-count\",\"url\":\"https://example.org/\",\"tasks\":[]}");
    }

    // [utest->dsn~browser-tab-task-popup~1]
    @Test
    void openLinkParses() {
        assertThat(ExtensionProtocol.parse(
                "{\"type\":\"open-link\",\"url\":\"contextswitcher://switch/jabref/fix-npe\"}"))
                .isEqualTo(new ExtensionProtocol.OpenLink("contextswitcher://switch/jabref/fix-npe"));
    }

    // [utest->dsn~browser-tab-task-popup~1]
    @Test
    void openLinkWithoutUrlRejected() {
        assertThatThrownBy(() -> ExtensionProtocol.parse("{\"type\":\"open-link\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void helloParses() {
        assertThat(ExtensionProtocol.parse("{\"type\":\"hello\",\"token\":\"secret\",\"client\":\"firefox\"}"))
                .isEqualTo(new ExtensionProtocol.Hello("secret", "firefox"));
    }

    @Test
    void resultParses() {
        assertThat(ExtensionProtocol.parse("{\"type\":\"result\",\"id\":\"abc-1\",\"ok\":true,"
                + "\"detail\":\"focused existing tab\"}"))
                .isEqualTo(new ExtensionProtocol.Result("abc-1", true, "focused existing tab"));
    }

    @Test
    void resultWithTitleParses() {
        assertThat(ExtensionProtocol.parse("{\"type\":\"result\",\"id\":\"abc-1\",\"ok\":true,"
                + "\"detail\":\"focused existing tab\",\"title\":\"My PR — Mozilla Firefox\"}"))
                .isEqualTo(new ExtensionProtocol.Result("abc-1", true, "focused existing tab",
                        "My PR — Mozilla Firefox"));
    }

    @Test
    void listTabsEncodesTypeAndId() {
        assertThat(ExtensionProtocol.encodeListTabs("abc-3"))
                .isEqualTo("{\"type\":\"list-tabs\",\"id\":\"abc-3\"}");
    }

    @Test
    // [utest->dsn~browser-teardown-quiet-tabs~1]
    void activeTabEncodesTypeAndId() {
        assertThat(ExtensionProtocol.encodeActiveTab("abc-5"))
                .isEqualTo("{\"type\":\"active-tab\",\"id\":\"abc-5\"}");
    }

    @Test
    void closeWindowEncodesAllFields() {
        assertThat(ExtensionProtocol.encodeCloseWindow("abc-4", 17))
                .isEqualTo("{\"type\":\"close-window\",\"id\":\"abc-4\",\"windowId\":17}");
    }

    @Test
    void resultWithWindowsParses() {
        assertThat(ExtensionProtocol.parse("{\"type\":\"result\",\"id\":\"abc-3\",\"ok\":true,"
                + "\"detail\":\"2 window(s)\",\"windows\":["
                + "{\"id\":1,\"title\":\"My PR\",\"urls\":[\"https://a.example/\",\"https://b.example/\"]},"
                + "{\"id\":2,\"title\":\"Docs\",\"urls\":[]}]}"))
                .isEqualTo(new ExtensionProtocol.Result("abc-3", true, "2 window(s)", null,
                        java.util.List.of(
                                new ExtensionProtocol.TabWindow(1, "My PR",
                                        java.util.List.of("https://a.example/", "https://b.example/")),
                                new ExtensionProtocol.TabWindow(2, "Docs", java.util.List.of()))));
    }

    @Test
    void resultWithoutDetailParsesWithEmptyDetail() {
        assertThat(ExtensionProtocol.parse("{\"type\":\"result\",\"id\":\"abc-1\",\"ok\":false}"))
                .isEqualTo(new ExtensionProtocol.Result("abc-1", false, ""));
    }

    // [utest->dsn~browser-tab-selects-task~5]
    @Test
    void tabActivatedParses() {
        assertThat(ExtensionProtocol.parse(
                "{\"type\":\"tab-activated\",\"url\":\"https://github.com/JabRef/jabref/pull/16659\"}"))
                .isEqualTo(new ExtensionProtocol.TabActivated("https://github.com/JabRef/jabref/pull/16659"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "[]",
            "{}",
            "{\"type\":\"unknown\"}",
            "{\"type\":\"hello\"}",
            "{\"type\":\"result\",\"ok\":true}",
            "{\"type\":\"tab-activated\"}"
    })
    void malformedMessagesRejected(String json) {
        assertThatThrownBy(() -> ExtensionProtocol.parse(json))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
