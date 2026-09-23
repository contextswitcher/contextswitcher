package com.contextswitcher.ui;

import com.contextswitcher.ui.MarkdownTokens.Seg;
import com.contextswitcher.ui.MarkdownTokens.Style;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~markdown-syntax-highlighting~3]
class MarkdownTokensTest {

    @Test
    void yamlIsStyled() {
        assertThat(MarkdownTokens.yamlLine("title: \"X\""))
                .containsExactly(new Seg("title:", Style.YAML_KEY), new Seg(" \"X\"", Style.PLAIN));
        assertThat(MarkdownTokens.yamlLine("  session: \"2\"   # imported"))
                .containsExactly(new Seg("  ", Style.PLAIN), new Seg("session:", Style.YAML_KEY),
                        new Seg(" \"2\"  ", Style.PLAIN), new Seg(" # imported", Style.COMMENT));
        assertThat(MarkdownTokens.yamlLine("# full comment"))
                .containsExactly(new Seg("# full comment", Style.COMMENT));
    }

    @Test
    void markdownIsStyled() {
        assertThat(MarkdownTokens.markdownLine("# Heading"))
                .containsExactly(new Seg("# Heading", Style.HEADING));
        assertThat(MarkdownTokens.markdownLine("- bullet with `code`"))
                .containsExactly(new Seg("- ", Style.BULLET), new Seg("bullet with ", Style.PLAIN),
                        new Seg("`code`", Style.CODE));
        assertThat(MarkdownTokens.markdownLine("> quote"))
                .containsExactly(new Seg("> quote", Style.QUOTE));
        assertThat(MarkdownTokens.markdownLine("plain `code` end"))
                .containsExactly(new Seg("plain ", Style.PLAIN), new Seg("`code`", Style.CODE),
                        new Seg(" end", Style.PLAIN));
    }

    @Test
    void unclosedBacktickStaysPlain() {
        assertThat(MarkdownTokens.markdownLine("a `b"))
                .containsExactly(new Seg("a ", Style.PLAIN), new Seg("`b", Style.PLAIN));
    }

    @Test
    void headingInConfigurationIsComment() {
        assertThat(MarkdownTokens.yamlLine("# Heading").getFirst().style())
                .isEqualTo(Style.COMMENT);
    }
}
