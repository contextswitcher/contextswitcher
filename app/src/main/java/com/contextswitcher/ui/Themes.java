package com.contextswitcher.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.scene.Group;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.Scene;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Applies the user's `theme` setting as the JavaFX user-agent stylesheet — a
/// light or dark AtlantaFX Nord theme, the Everforest recolouring of one
/// (`dsn~everforest-theme~2`), or whichever of the two matches the operating
/// system for `system`. The app's own `main.css` (added to every window) layers over
/// whichever theme is active, and its color variables (`-color-bg-subtle`, …)
/// resolve against it, so the panels recolor with the theme.
// [impl->dsn~theme-select~8]
public final class Themes {

    /// The `theme` values that ask for the Everforest recolouring: the bare
    /// name follows the OS, the two suffixed ones force a variant.
    public static final String EVERFOREST = "everforest";
    public static final String EVERFOREST_LIGHT = "everforest-light";
    public static final String EVERFOREST_DARK = "everforest-dark";

    /// Stylesheet URL per built Everforest variant — the concatenation is done
    /// once, not on every live theme switch.
    private static final Map<String, String> GENERATED = new HashMap<>();

    /// Whether the currently applied theme is dark — set by [#apply], read by
    /// what CSS cannot reach (the terminal's palette).
    private static boolean dark;

    /// Whether the currently applied theme is an Everforest one — set by
    /// [#apply]. Picks the terminal's palette (`dsn~terminal-theme~2`).
    private static boolean everforest;

    private Themes() {
    }

    /// Makes JavaFX really restyle every window with the theme [#apply] set.
    ///
    /// JavaFX ignores a user-agent stylesheet it already holds (same URL and
    /// checksum), so setting it again cannot repair a theme that did not take —
    /// and the ShellFX shell starts that way. Switching to JavaFX's default and
    /// back forces the restyle; both happen before the next pulse, so nothing
    /// flickers. For a host that set its own stylesheet (ShellFX, while it
    /// initializes) and once the main window is on screen.
    // [impl->dsn~theme-select~8]
    public static void refresh() {
        String stylesheet = requested;
        if (stylesheet == null) {
            return;
        }
        Application.setUserAgentStylesheet(null);
        Application.setUserAgentStylesheet(stylesheet);
        applied(stylesheet, "refresh");
    }

    /// Sets the user-agent stylesheet to the theme `pref` resolves to. Safe to
    /// call at startup and again at runtime (a live theme switch restyles every
    /// open window). Records whether the applied theme is dark for [#isDark].
    public static void apply(@Nullable String pref) {
        String name = pref == null ? "system" : pref.trim().toLowerCase(Locale.ROOT);
        dark = switch (name) {
            case "dark", EVERFOREST_DARK -> true;
            case "light", EVERFOREST_LIGHT -> false;
            default -> systemPrefersDark();
        };
        everforest = name.startsWith(EVERFOREST);
        Theme base = dark ? new NordDark() : new NordLight();
        String stylesheet = everforest
                ? everforest(base, dark)
                : base.getUserAgentStylesheet();
        // What the whole UI is about to be drawn with. Logged because the one
        // failure mode here is silent: JavaFX falls back to its own Modena
        // when it cannot load the URL, and the app then comes up in a light
        // window with no warning anywhere (field report 2026-09-12 — "on
        // startup, sometimes, the theme is not applied", with a terminal
        // ground that proved `dark` had been resolved correctly).
        // [impl->dsn~theme-select~8]
        Logger.info("Theme {} ({}): {}", name, dark ? "dark" : "light", stylesheet);
        Application.setUserAgentStylesheet(stylesheet);
        // What JavaFX *kept*. It replaces a stylesheet it cannot use with its
        // own Modena without a word, and three field reports (2026-09-12/13)
        // have now shown a correct preference, a valid readable file and a
        // Modena window at the same time — with the round trip passing in a
        // test here. This is the one step never measured on that machine.
        // [impl->dsn~everforest-theme~2]
        requested = stylesheet;
        applied(stylesheet, "set");
    }

    /// Where the generated stylesheet lives: beside the app's other state, not
    /// in `%TEMP%`. A file that survives between runs is never a file something
    /// else is still looking at when JavaFX opens it.
    // [impl->dsn~everforest-theme~2]
    static Path generatedStylesheet(String variant) {
        return Path.of(System.getProperty("user.home"), ".contextswitcher", "themes",
                variant + ".css");
    }

    /// `file`'s content, or null when it cannot be read for any reason — the
    /// read JavaFX is about to attempt, done first.
    private static @Nullable String readOrNull(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger.debug("Cannot read {}: {}", file, messageOf(e));
            return null;
        }
    }

    /// Logs whether JavaFX is holding `expected` as the user-agent stylesheet.
    /// `where` names the moment, so a stylesheet that is taken and then lost is
    /// told apart from one that never took.
    // [impl->dsn~everforest-theme~2]
    public static void applied(@Nullable String expected, String where) {
        String actual = Application.getUserAgentStylesheet();
        if (expected == null || expected.equals(actual)) {
            Logger.debug("User-agent stylesheet at {}: as requested", where);
            return;
        }
        Logger.warn("User-agent stylesheet at {}: JavaFX holds {} instead of {}",
                where, actual, expected);
    }

    /// The stylesheet [#apply] last handed to JavaFX, so a later check can say
    /// whether it is still the one in force.
    private static @Nullable String requested;

    /// The stylesheet URL [#apply] last resolved — what anything that sets the
    /// user-agent stylesheet on its own (the ShellFX shell) must hand over.
    public static @Nullable String stylesheet() {
        return requested;
    }

    /// Whether the theme's colour variables resolve right now: a probe node in
    /// a scene of its own, styled only by `main.css`'s `.theme-probe` rule
    /// (`-color-bg-default`), gets a background exactly when the user-agent
    /// stylesheet in force defines that variable. Holding a stylesheet URL and
    /// resolving its variables are different things — every unthemed start
    /// so far logged the URL "as requested", and the root fill it also logged
    /// is transparent in a themed and an unthemed window alike (2026-09-16).
    /// FX thread.
    // [impl->dsn~theme-select~8]
    static boolean variablesResolve() {
        Region probe = new Region();
        probe.getStyleClass().add("theme-probe");
        Scene scene = new Scene(new Group(probe));
        java.net.URL css = Themes.class.getResource("main.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        probe.applyCss();
        Background background = probe.getBackground();
        return background != null && !background.getFills().isEmpty();
    }

    /// Checks at `where` that the theme's variables resolve, and when they do
    /// not, forces the restyle once more ([#refresh]) and logs whether that
    /// repaired them. Called when the window is shown and again once the
    /// start-up stalls are over: an unthemed start was so far only ever
    /// repaired by a restart or a theme switch in the settings dialog, well
    /// after start-up. Returns whether the variables resolve afterwards.
    /// FX thread.
    // [impl->dsn~theme-select~8]
    public static boolean verify(String where) {
        if (requested == null || variablesResolve()) {
            Logger.debug("Theme variables resolve at {}", where);
            return true;
        }
        Logger.warn("Theme variables do not resolve at {} although JavaFX holds {} — refreshing",
                where, Application.getUserAgentStylesheet());
        refresh();
        boolean repaired = variablesResolve();
        if (repaired) {
            Logger.warn("Theme refresh at {} repaired the theme variables", where);
        } else {
            Logger.error("Theme refresh at {} did not repair the theme variables", where);
        }
        return repaired;
    }

    /// Re-checks [#apply]'s stylesheet at `where` — after the window is up, a
    /// stylesheet that was taken and then replaced shows here and nowhere else.
    // [impl->dsn~everforest-theme~2]
    public static void checkApplied(String where) {
        applied(requested, where);
    }

    /// As [#checkApplied(String)], plus what `scene` actually resolved.
    ///
    /// Three field reports (2026-09-12/13) ended with the user-agent stylesheet
    /// reported "as requested" at set *and* at window-shown while the window
    /// rendered in JavaFX's own greys. Holding a stylesheet and painting with
    /// it are different things, so this logs the scene's own sheets and the
    /// colour its root computed: the theme's ground says CSS resolved, Modena's
    /// says it did not, and that is the fork the next fix depends on.
    // [impl->dsn~everforest-theme~2]
    public static void checkApplied(String where, @Nullable Scene scene) {
        checkApplied(where);
        if (scene == null) {
            return;
        }
        // The first fill's paint, not the Background object: an identity hash
        // says nothing about which theme's ground the root was painted with.
        String background = scene.getRoot() instanceof Region root
                && root.getBackground() != null && !root.getBackground().getFills().isEmpty()
                ? String.valueOf(root.getBackground().getFills().getFirst().getFill())
                : "none";
        Logger.info("Scene at {}: {} stylesheet(s) {}, root fill {}",
                where, scene.getStylesheets().size(), scene.getStylesheets(), background);
    }

    /// The Everforest recolouring of `base`, as a stylesheet URL. Package-private
    /// so a test can parse what this actually writes, rather than a re-derivation
    /// of it — the concatenation is the part that can produce a broken stylesheet.
    ///
    /// An AtlantaFX theme resolves every colour through the lookup variables of
    /// its `.root` block and holds no literal colour outside it, so appending a
    /// second `.root` block recolours the whole UI — including the dialogs,
    /// which get no scene stylesheet of their own and would keep the base
    /// theme's palette if this were layered per scene instead.
    ///
    /// JavaFX takes one URL, not two stylesheets, so the concatenation is
    /// written to a temp file once per variant and reused. `base` stays the
    /// theme of the matching mode: a variable the override forgets then falls
    /// back to a value of the right lightness rather than the wrong one.
    /// Any failure falls back to the plain base theme — a broken recolouring
    /// must not cost the user their window.
    // [impl->dsn~everforest-theme~2]
    static String everforest(Theme base, boolean dark) {
        String variant = dark ? EVERFOREST_DARK : EVERFOREST_LIGHT;
        @Nullable String cached = GENERATED.get(variant);
        if (cached != null) {
            return cached;
        }
        try (InputStream theme = Theme.class.getResourceAsStream(base.getUserAgentStylesheet());
                InputStream override = Themes.class.getResourceAsStream(variant + ".css")) {
            if (theme == null || override == null) {
                throw new IOException("stylesheet resource missing for " + variant);
            }
            String content = new String(theme.readAllBytes(), StandardCharsets.UTF_8)
                    + "\n" + new String(override.readAllBytes(), StandardCharsets.UTF_8);
            Path file = generatedStylesheet(variant);
            // Rewritten only when missing or no longer matching - so an
            // upgrade that changes either stylesheet takes effect, while an
            // ordinary start touches nothing.
            if (!content.equals(readOrNull(file))) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, content, StandardCharsets.UTF_8);
            }
            // A stylesheet JavaFX cannot read leaves it on its own Modena
            // default - a light window, silently, whatever `dark` says. So the
            // content is read *back* before the URL is handed over: the same
            // operation JavaFX is about to perform, unlike the metadata checks
            // that passed while it failed. The fallback is the base theme of
            // the right lightness.
            if (!content.equals(readOrNull(file))) {
                Logger.warn("Generated {} stylesheet at {} does not read back"
                        + " - falling back to Nord", variant, file);
                return base.getUserAgentStylesheet();
            }
            String url = file.toUri().toString();
            GENERATED.put(variant, url);
            return url;
        } catch (IOException e) {
            Logger.warn("Cannot build the {} stylesheet, falling back to Nord: {}",
                    variant, messageOf(e));
            return base.getUserAgentStylesheet();
        }
    }

    /// Whether the theme [#apply] last resolved is a dark one — read by what
    /// cannot ride the CSS variables, in particular the terminal pane's own
    /// palette (`dsn~terminal-theme~2`).
    public static boolean isDark() {
        return dark;
    }

    /// Whether the theme [#apply] last resolved is an Everforest one — the
    /// terminal is drawn in Everforest only then (`dsn~terminal-theme~2`).
    public static boolean isEverforest() {
        return everforest;
    }

    /// Whether the OS is set to a dark appearance. Implemented for Windows (the
    /// primary target) via the registry `AppsUseLightTheme` value; every other
    /// OS falls back to light — a later port can add its own probe.
    // [impl->dsn~theme-select~8]
    private static boolean systemPrefersDark() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme")
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            // AppsUseLightTheme is 1 for light, 0 for dark.
            Matcher matcher = Pattern.compile("AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)")
                    .matcher(output);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1), 16) == 0;
            }
        } catch (@SuppressWarnings("unused") Exception e) {
            Logger.debug("Cannot read the Windows theme preference: {}", messageOf(e));
        }
        return false;
    }

    private static String messageOf(Exception e) {
        @Nullable String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }

    /// The window property marking a window whose scene is already watched.
    private static final String STYLESHEET_WATCHED = Themes.class.getName() + ".stylesheetWatched";

    /// Sheets [#addToEveryWindow] already installed. `Window.getWindows()` is
    /// global, so a second install — `MainWindow.show` again, as UI tests do —
    /// must not stack another listener on it. FX thread only.
    private static final Set<String> ON_EVERY_WINDOW = new HashSet<>();

    /// Adds the app stylesheet `sheet` to the scene of every open and every
    /// future window, and again whenever a window gets a new scene: a dialog
    /// builds a scene of its own, and a rule it misses — an icon's fill above
    /// all — leaves a glyph invisible there. FX thread.
    // [impl->dsn~theme-select~8]
    static void addToEveryWindow(String sheet) {
        if (!ON_EVERY_WINDOW.add(sheet)) {
            return;
        }
        Window.getWindows().forEach(window -> watchScene(window, sheet));
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(window -> watchScene(window, sheet));
            }
        });
    }

    /// A window re-enters the window list each time it is shown again, so its
    /// scene listener is added once only.
    private static void watchScene(Window window, String sheet) {
        addStylesheet(window.getScene(), sheet);
        if (window.getProperties().putIfAbsent(STYLESHEET_WATCHED, Boolean.TRUE) == null) {
            // A dialog is registered as a window before its scene is set.
            window.sceneProperty().addListener((obs, old, scene) -> addStylesheet(scene, sheet));
        }
    }

    private static void addStylesheet(@Nullable Scene scene, String sheet) {
        if (scene != null && !scene.getStylesheets().contains(sheet)) {
            scene.getStylesheets().add(sheet);
        }
    }
}
