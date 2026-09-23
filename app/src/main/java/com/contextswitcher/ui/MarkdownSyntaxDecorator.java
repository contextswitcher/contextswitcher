package com.contextswitcher.ui;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.RichParagraph;

/// Styles the editor lane's tabs: YAML keys/comments in the configuration
/// tab, Markdown headings, bullets, quotes, inline code in the notes tab.
/// Each token gets a style name (`.md-*` in main.css) rather than a colour, so
/// the highlighting follows the theme.
// [impl->dsn~markdown-syntax-highlighting~3]
public class MarkdownSyntaxDecorator implements SyntaxDecorator {

    private final boolean yaml;

    public MarkdownSyntaxDecorator(boolean yaml) {
        this.yaml = yaml;
    }

    /// The style name per token; plain text has none.
    private static final Map<MarkdownTokens.Style, String> STYLE_NAMES =
            new EnumMap<>(Map.of(
                    MarkdownTokens.Style.YAML_KEY, "md-yaml-key",
                    MarkdownTokens.Style.COMMENT, "md-comment",
                    MarkdownTokens.Style.HEADING, "md-heading",
                    MarkdownTokens.Style.BULLET, "md-bullet",
                    MarkdownTokens.Style.QUOTE, "md-quote",
                    MarkdownTokens.Style.CODE, "md-code"));

    @Override
    public RichParagraph createRichParagraph(CodeTextModel model, int index) {
        String line = model.getPlainText(index);
        List<MarkdownTokens.Seg> segments =
                yaml ? MarkdownTokens.yamlLine(line) : MarkdownTokens.markdownLine(line);
        RichParagraph.Builder builder = RichParagraph.builder();
        for (MarkdownTokens.Seg segment : segments) {
            String name = STYLE_NAMES.get(segment.style());
            if (name == null) {
                builder.addSegment(segment.text());
            } else {
                builder.addWithStyleNames(segment.text(), name);
            }
        }
        return builder.build();
    }

    @Override
    public void handleChange(CodeTextModel model, TextPos start, TextPos end,
            int charsTop, int linesAdded, int charsBottom) {
        // no caches to invalidate; paragraphs are re-created on demand
    }
}
