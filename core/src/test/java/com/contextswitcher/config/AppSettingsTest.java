package com.contextswitcher.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~app-settings~5]
class AppSettingsTest {

    @Test
    void firstRunCreatesFileWithRandomToken(@TempDir Path configDir) throws Exception {
        AppSettings settings = AppSettings.loadOrCreate(configDir);

        assertThat(configDir.resolve("settings.yaml")).exists();
        assertThat(settings.tasksDir()).isEqualTo(configDir.resolve("tasks"));
        assertThat(settings.wsPort()).isEqualTo(AppSettings.DEFAULT_WS_PORT);
        assertThat(settings.wsToken()).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void tokensDifferAcrossFirstRuns(@TempDir Path dirA, @TempDir Path dirB) throws Exception {
        assertThat(AppSettings.loadOrCreate(dirA).wsToken())
                .isNotEqualTo(AppSettings.loadOrCreate(dirB).wsToken());
    }

    // [utest->dsn~claude-auto-permissions~1]
    @Test
    void claudeAutoIsOptInAndDefaultsToFalse(@TempDir Path configDir) throws Exception {
        assertThat(AppSettings.loadOrCreate(configDir).claudeAuto()).isFalse();

        String content = Files.readString(configDir.resolve("settings.yaml"));
        Files.writeString(configDir.resolve("settings.yaml"),
                content.replace("claudeAuto: false", "claudeAuto: true"));
        assertThat(AppSettings.loadOrCreate(configDir).claudeAuto()).isTrue();
    }

    // [utest->dsn~window-desktop-pin~2]
    @Test
    void showOnAllDesktopsIsOptInAndDefaultsToFalse(@TempDir Path configDir) throws Exception {
        assertThat(AppSettings.loadOrCreate(configDir).showOnAllDesktops()).isFalse();

        String content = Files.readString(configDir.resolve("settings.yaml"));
        Files.writeString(configDir.resolve("settings.yaml"),
                content.replace("showOnAllDesktops: false", "showOnAllDesktops: true"));
        assertThat(AppSettings.loadOrCreate(configDir).showOnAllDesktops()).isTrue();
    }

    // [utest->dsn~readline-keys~1]
    // [utest->dsn~auto-suspend-idle~2]
    @Test
    void autoSuspendDefaultsToAnHourAndReadsZeroAsOff(@TempDir Path configDir) throws Exception {
        assertThat(AppSettings.loadOrCreate(configDir).autoSuspendMinutes()).isEqualTo(2880);
        assertThat(AppSettings.parse("tasksDir: t\nwsPort: 1\nwsToken: x\n").autoSuspendMinutes()).isEqualTo(2880);
        assertThat(AppSettings.parse("tasksDir: t\nwsPort: 1\nwsToken: x\nautoSuspendMinutes: 0\n")
                .autoSuspendMinutes()).isZero();
    }

    @Test
    void readlineKeysAreOptInAndDefaultToFalse(@TempDir Path configDir) throws Exception {
        assertThat(AppSettings.loadOrCreate(configDir).readlineKeys()).isFalse();

        String content = Files.readString(configDir.resolve("settings.yaml"));
        Files.writeString(configDir.resolve("settings.yaml"),
                content.replace("readlineKeys: false", "readlineKeys: true"));
        assertThat(AppSettings.loadOrCreate(configDir).readlineKeys()).isTrue();
    }

    // [utest->dsn~terminal-markdown-copy~1]
    @Test
    void autoCopyRepliesIsOptInAndDefaultsToFalse(@TempDir Path configDir) throws Exception {
        assertThat(AppSettings.loadOrCreate(configDir).autoCopyReplies()).isFalse();

        String content = Files.readString(configDir.resolve("settings.yaml"));
        Files.writeString(configDir.resolve("settings.yaml"),
                content.replace("autoCopyReplies: false", "autoCopyReplies: true"));
        assertThat(AppSettings.loadOrCreate(configDir).autoCopyReplies()).isTrue();
    }

    @Test
    void settingsRoundTrip(@TempDir Path configDir) throws Exception {
        AppSettings written = new AppSettings(Path.of("C:\\somewhere\\tasks"), 4242, "secret-token",
                java.util.List.of("koppor@devbox", "devbox"));
        written.store(configDir);

        AppSettings read = AppSettings.loadOrCreate(configDir);

        assertThat(read).isEqualTo(written);
    }

    @Test
    void tagPaletteRoundTrips(@TempDir Path configDir) throws Exception {
        AppSettings written = new AppSettings(configDir.resolve("tasks"), 4242, "secret", java.util.List.of(),
                java.util.List.of(new AppSettings.TagDef("phone", "#2da44e"),
                        new AppSettings.TagDef("jabref", "#8250df")));
        written.store(configDir);

        assertThat(AppSettings.loadOrCreate(configDir).tags()).isEqualTo(written.tags());
    }

    @Test
    void colorlessTagKeptButNamelessSkipped(@TempDir Path configDir) throws Exception {
        Files.writeString(configDir.resolve("settings.yaml"), """
                tasksDir: %s
                wsPort: 17872
                wsToken: t
                tags:
                  - name: phone
                    color: "#2da44e"
                  - name: nocolor
                  - color: "#123456"
                """.formatted(configDir.resolve("tasks").toString().replace("\\", "\\\\")));

        assertThat(AppSettings.loadOrCreate(configDir).tags())
                .containsExactly(new AppSettings.TagDef("phone", "#2da44e"),
                        new AppSettings.TagDef("nocolor", null));
    }

    // The hints flag defaults to intro mode when absent; false is honored.
    // [utest->dsn~skeleton-hints~4]
    @Test
    void hintsDefaultTrueWhenAbsentAndParseFalse(@TempDir Path configDir) throws Exception {
        Files.writeString(configDir.resolve("settings.yaml"), """
                tasksDir: /t
                wsPort: 17872
                wsToken: t
                """);
        assertThat(AppSettings.loadOrCreate(configDir).hints()).isTrue();

        Files.writeString(configDir.resolve("settings.yaml"), """
                tasksDir: /t
                wsPort: 17872
                wsToken: t
                hints: false
                """);
        assertThat(AppSettings.loadOrCreate(configDir).hints()).isFalse();
    }

    // Theme defaults to everforest, is case-normalized, and an unknown value
    // falls back to everforest.
    // [utest->dsn~theme-select~8]
    @Test
    void themeDefaultsToEverforestAndNormalizesUnknown(@TempDir Path configDir) throws Exception {
        assertThat(AppSettings.loadOrCreate(configDir).theme()).isEqualTo("everforest");

        writeMinimalSettings(configDir, "theme: DARK");
        assertThat(AppSettings.loadOrCreate(configDir).theme()).isEqualTo("dark");

        writeMinimalSettings(configDir, "theme: solarized");
        assertThat(AppSettings.loadOrCreate(configDir).theme()).isEqualTo("everforest");
    }

    // The RefactoringMiner integration (https://github.com/contextswitcher/contextswitcher-private/issues/50) is off until a home is set;
    // blank normalizes to unset, the port defaults to RM's own 6789.
    // [utest->dsn~refactoring-web-view~1]
    @Test
    void refactoringMinerDefaultsToOffWithDefaultPort(@TempDir Path configDir) throws Exception {
        AppSettings settings = AppSettings.loadOrCreate(configDir);
        assertThat(settings.refactoringMinerHome()).isNull();
        assertThat(settings.refactoringMinerPort())
                .isEqualTo(AppSettings.DEFAULT_REFACTORING_MINER_PORT);

        writeMinimalSettings(configDir, "refactoringMinerHome: ''");
        assertThat(AppSettings.loadOrCreate(configDir).refactoringMinerHome()).isNull();

        writeMinimalSettings(configDir,
                "refactoringMinerHome: /data/koppor/RefactoringMiner-3.1.4\n"
                        + "refactoringMinerPort: 7000");
        AppSettings configured = AppSettings.loadOrCreate(configDir);
        assertThat(configured.refactoringMinerHome())
                .isEqualTo("/data/koppor/RefactoringMiner-3.1.4");
        assertThat(configured.refactoringMinerPort()).isEqualTo(7000);
    }

    private static void writeMinimalSettings(Path configDir, String extraLine) throws Exception {
        Files.writeString(configDir.resolve("settings.yaml"),
                "tasksDir: /t\nwsPort: 17872\nwsToken: t\n" + extraLine + "\n");
    }

    // The F1 reference must name every key the app actually stores/reads —
    // keys are taken from a real stored file, so a newly added setting
    // without a reference line fails here.
    // [utest->dsn~settings-editor~4]
    @Test
    void settingsReferenceNamesEveryStoredKey(@TempDir Path configDir) throws Exception {
        AppSettings.loadOrCreate(configDir);
        Object stored = new org.yaml.snakeyaml.Yaml()
                .load(Files.readString(configDir.resolve("settings.yaml")));

        for (Object key : ((java.util.Map<?, ?>) stored).keySet()) {
            assertThat(AppSettings.settingsReference()).containsPattern("(?m)^" + key + ":");
        }
    }

    /// The extension dials in and names itself, so the setting exists only for
    /// the Windows helpers; anything the file does not say is Firefox.
    // [utest->dsn~browser-choice~2]
    @Test
    void browserDefaultsToFirefoxAndIsRoundTripped(@TempDir Path configDir) throws Exception {
        writeMinimalSettings(configDir, "");
        assertThat(AppSettings.loadOrCreate(configDir).browser()).isEqualTo(Browser.FIREFOX);

        writeMinimalSettings(configDir, "browser: chrome");
        AppSettings chrome = AppSettings.loadOrCreate(configDir);
        assertThat(chrome.browser()).isEqualTo(Browser.CHROME);
        assertThat(chrome.dump()).contains("browser: chrome");
        assertThat(AppSettings.parse(chrome.dump()).browser()).isEqualTo(Browser.CHROME);

        // A hand-edited file must not break the load, and the tolerant reading
        // is the same "fall back to the shipped default" the theme key uses.
        writeMinimalSettings(configDir, "browser: safari");
        assertThat(AppSettings.loadOrCreate(configDir).browser()).isEqualTo(Browser.FIREFOX);
        writeMinimalSettings(configDir, "browser: CHROME");
        assertThat(AppSettings.loadOrCreate(configDir).browser()).isEqualTo(Browser.CHROME);
    }

    @Test
    void secondLoadReturnsSameToken(@TempDir Path configDir) throws Exception {
        AppSettings first = AppSettings.loadOrCreate(configDir);
        AppSettings second = AppSettings.loadOrCreate(configDir);

        assertThat(second).isEqualTo(first);
        assertThat(Files.readString(configDir.resolve("settings.yaml"))).contains(first.wsToken());
    }
}
