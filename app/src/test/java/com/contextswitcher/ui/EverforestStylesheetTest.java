package com.contextswitcher.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.Theme;
import javafx.css.CssParser;
import javafx.css.Stylesheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/// The Everforest override is appended to an AtlantaFX theme and wins by
/// being last, so anything it forgets keeps the base theme's Nord value —
/// a half-recoloured UI that looks like a design mistake rather than a bug.
/// These are resource checks, no JavaFX needed.
// [utest->dsn~everforest-theme~2]
class EverforestStylesheetTest {

    private static final Pattern DECLARATION =
            Pattern.compile("(-color-[a-z0-9-]+)\\s*:\\s*([^;]+);");

    @ParameterizedTest
    @CsvSource({"everforest-light, false", "everforest-dark, true"})
    void overridesEveryColorVariableOfTheBaseTheme(String variant, boolean dark)
            throws IOException {
        Map<String, String> base = rootBlock(read(baseTheme(dark).getUserAgentStylesheet()));
        Map<String, String> override = rootBlock(read("/com/contextswitcher/ui/" + variant + ".css"));

        assertThat(base).as("AtlantaFX defines its palette in a .root block").isNotEmpty();
        assertThat(override.keySet())
                .as("%s must leave no Nord value showing through", variant)
                .containsAll(base.keySet());
    }

    /// The whole approach rests on the base theme resolving its rules through
    /// those variables. If a future AtlantaFX release inlines literal colors
    /// again, appending a `.root` block silently stops recolouring those rules.
    @ParameterizedTest
    @CsvSource({"false", "true"})
    void baseThemeHasNoLiteralColorOutsideItsRootBlock(boolean dark) throws IOException {
        String css = read(baseTheme(dark).getUserAgentStylesheet());
        String outside = css.substring(0, css.indexOf(".root {"))
                + css.substring(css.indexOf("\n}", css.indexOf(".root {")) + 2);

        assertThat(Pattern.compile("#[0-9a-fA-F]{3,8}").matcher(outside).results().count())
                .as("AtlantaFX still resolves every color through .root variables")
                .isZero();
    }

    /// The two variants must actually differ — a copy-paste slip in the
    /// generator would otherwise ship a light theme that is secretly dark.
    @Test
    void theTwoVariantsAreDifferent() throws IOException {
        Map<String, String> light = rootBlock(read("/com/contextswitcher/ui/everforest-light.css"));
        Map<String, String> dark = rootBlock(read("/com/contextswitcher/ui/everforest-dark.css"));

        assertThat(light.get("-color-bg-default")).isEqualTo("#fdf6e3");
        assertThat(dark.get("-color-bg-default")).isEqualTo("#2d353b");
        assertThat(light).hasSameSizeAs(dark);
    }

    /// What the app actually hands JavaFX: the concatenation on disk, parsed
    /// by JavaFX's own CSS parser. A stylesheet it rejects leaves the whole
    /// window unstyled, and nothing else in this build would notice.
    @ParameterizedTest
    @CsvSource({"false", "true"})
    void theGeneratedStylesheetParses(boolean dark) throws IOException {
        String url = Themes.everforest(baseTheme(dark), dark);
        assertThat(url).startsWith("file:").endsWith(".css");
        assertThat(Themes.everforest(baseTheme(dark), dark)).as("built once, cached").isEqualTo(url);

        Stylesheet parsed = new CssParser().parse(URI.create(url).toURL());
        assertThat(parsed.getRules()).isNotEmpty();
        // The override is appended, and CSS gives the last declaration of equal
        // specificity — that is the whole mechanism, so assert on it rather
        // than on the file being two files glued together.
        List<String> grounds = parsed.getRules().stream()
                .flatMap(rule -> rule.getDeclarations().stream())
                .filter(declaration -> declaration.getProperty().equals("-color-bg-default"))
                .map(declaration -> declaration.getParsedValue().getValue().toString())
                .toList();
        assertThat(grounds).hasSizeGreaterThan(1);
        assertThat(grounds.get(grounds.size() - 1))
                .as("the Everforest ground must be the surviving definition")
                .containsIgnoringCase(dark ? "2d353b" : "fdf6e3");
    }

    /// Field report 2026-09-12, twice: the app came up in JavaFX's own Modena
    /// while the log line said the generated stylesheet was readable and
    /// non-empty — so the failure was inside JavaFX's read of a file written
    /// milliseconds earlier. The file is stable now (`~/.contextswitcher/
    /// themes/`), written only when its content differs, and read back before
    /// its URL is handed over.
    // [utest->dsn~everforest-theme~2]
    @ParameterizedTest
    @CsvSource({"false", "true"})
    void theGeneratedStylesheetIsStableAndReadBack(boolean dark) throws IOException {
        String url = Themes.everforest(baseTheme(dark), dark);
        Path file = Path.of(URI.create(url));

        assertThat(file).isEqualTo(Themes.generatedStylesheet(
                dark ? Themes.EVERFOREST_DARK : Themes.EVERFOREST_LIGHT));
        assertThat(file.getParent().getFileName()).hasToString("themes");
        assertThat(Files.readString(file)).isNotEmpty();
        // A second call neither rewrites nor moves it: same URL, same file.
        assertThat(Themes.everforest(baseTheme(dark), dark)).isEqualTo(url);
    }

    /// Field report 2026-09-12: the app came up with a Modena-grey window
    /// while its terminal ground proved `dark` had resolved correctly — the
    /// signature of a user-agent stylesheet JavaFX could not load, which it
    /// replaces with its own default without a word. The generated file is
    /// checked before its URL is handed over, so the worst case is the base
    /// theme of the right lightness instead of a white window.
    @ParameterizedTest
    @CsvSource({"false", "true"})
    void theGeneratedStylesheetIsReadableAndNotEmpty(boolean dark) throws IOException {
        String url = Themes.everforest(baseTheme(dark), dark);

        Path file = Path.of(URI.create(url));
        assertThat(Files.isReadable(file)).isTrue();
        assertThat(Files.size(file)).isGreaterThan(0);
    }

    private static Theme baseTheme(boolean dark) {
        return dark ? new NordDark() : new NordLight();
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = Theme.class.getResourceAsStream(resource)) {
            assertThat(in).as("resource %s", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /// The `-color-…` declarations of the stylesheet's first `.root` block.
    private static Map<String, String> rootBlock(String css) {
        int start = css.indexOf(".root {");
        String block = css.substring(start, css.indexOf("\n}", start));
        Map<String, String> found = new LinkedHashMap<>();
        Matcher matcher = DECLARATION.matcher(block);
        while (matcher.find()) {
            found.put(matcher.group(1), matcher.group(2).trim());
        }
        return found;
    }
}
