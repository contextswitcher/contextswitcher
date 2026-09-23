package com.contextswitcher.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~drive-the-connected-browser~1]
class BrowserTest {

    /// The tolerant reading: a hand-edited settings file naming nothing we
    /// know still starts the app, on the browser it shipped for.
    // [utest->dsn~browser-choice~2]
    @ParameterizedTest
    @ValueSource(strings = {"safari", "", "  ", "edge"})
    void ofFallsBackToFirefox(String value) {
        assertThat(Browser.of(value)).isEqualTo(Browser.FIREFOX);
        assertThat(Browser.of(null)).isEqualTo(Browser.FIREFOX);
    }

    // [utest->dsn~browser-choice~2]
    @Test
    void ofReadsBothNamesWhateverTheCasing() {
        assertThat(Browser.of("chrome")).isEqualTo(Browser.CHROME);
        assertThat(Browser.of(" CHROME ")).isEqualTo(Browser.CHROME);
        assertThat(Browser.of("Firefox")).isEqualTo(Browser.FIREFOX);
    }

    /// The strict reading, used on the socket: an extension whose `hello`
    /// names something else has *not* told us it is Firefox, and answering
    /// Firefox there would raise the wrong browser's window.
    @ParameterizedTest
    @ValueSource(strings = {"test", "safari", "edge"})
    void namedAnswersNullForANameItDoesNotKnow(String value) {
        assertThat(Browser.named(value)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void namedAnswersNullForNoName(String value) {
        assertThat(Browser.named(value)).isNull();
    }

    @Test
    void namedReadsBothNames() {
        assertThat(Browser.named("firefox")).isEqualTo(Browser.FIREFOX);
        assertThat(Browser.named("Chrome")).isEqualTo(Browser.CHROME);
    }

    /// Chrome and Firefox must not share a window class or a process name —
    /// the pair is what tells one browser's window from the other's, and from
    /// every other Chromium window on the machine.
    // [utest->dsn~browser-choice~2]
    @Test
    void eachBrowserIsIdentifiableByItsOwnClassAndProcess() {
        assertThat(Browser.FIREFOX.windowClass()).isNotEqualTo(Browser.CHROME.windowClass());
        assertThat(Browser.FIREFOX.processName()).isNotEqualTo(Browser.CHROME.processName());
        assertThat(Browser.KEYS).containsExactlyInAnyOrder(
                Browser.FIREFOX.key(), Browser.CHROME.key());
    }
}
