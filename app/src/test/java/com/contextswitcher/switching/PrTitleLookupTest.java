package com.contextswitcher.switching;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-from-pr~6]
class PrTitleLookupTest {

    @Test
    void ghCommandQueriesTheTitle() {
        assertThat(PrTitleLookup.command("https://github.com/JabRef/jabref/pull/16246"))
                .containsExactly("gh", "pr", "view", "https://github.com/JabRef/jabref/pull/16246",
                        "--json", "title", "-q", ".title");
    }

    @Test
    void fallbackTitleDerivesFromTheUrl() {
        assertThat(PrTitleLookup.fallbackTitle("https://github.com/JabRef/jabref/pull/16246"))
                .isEqualTo("PR #16246 (JabRef/jabref)");
    }

    @Test
    void nonPrUrlsHaveNoTitle() {
        assertThat(PrTitleLookup.fallbackTitle("https://example.org/x")).isNull();
    }
}
