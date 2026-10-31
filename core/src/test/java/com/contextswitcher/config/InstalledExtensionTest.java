package com.contextswitcher.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~setup-wizard~12]
class InstalledExtensionTest {

    @Test
    void nothingInstalledIsNull(@TempDir Path home) throws Exception {
        Path profile = Files.createDirectories(home.resolve(".mozilla/firefox/abc.default"));
        Files.writeString(profile.resolve("extensions.json"),
                "{\"addons\":[{\"id\":\"uBlock0@raymondhill.net\"}]}");

        assertThat(InstalledExtension.find(home, Map.of())).isNull();
    }

    @Test
    void firefoxProfileListingOurIdIsFirefox(@TempDir Path home) throws Exception {
        Path profile = Files.createDirectories(home.resolve(".mozilla/firefox/abc.default-release"));
        Files.writeString(profile.resolve("extensions.json"),
                "{\"addons\":[{\"id\":\"other@x\"},{\"id\":\"extension@contextswitcher.org\"}]}");

        assertThat(InstalledExtension.find(home, Map.of())).isEqualTo(Browser.FIREFOX);
    }

    @Test
    void temporaryFirefoxAddonIsFoundThroughPrefsJsUuids(@TempDir Path home) throws Exception {
        Path profile = Files.createDirectories(home.resolve(".mozilla/firefox/abc.default-release"));
        Files.writeString(profile.resolve("extensions.json"), "{\"addons\":[{\"id\":\"other@x\"}]}");
        Files.writeString(profile.resolve("prefs.js"), """
                user_pref("browser.startup.page", 3);
                user_pref("extensions.webextensions.uuids", "{\\"other@x\\":\\"1\\",\\"extension@contextswitcher.org\\":\\"d1c19644\\"}");
                """);

        assertThat(InstalledExtension.find(home, Map.of())).isEqualTo(Browser.FIREFOX);
    }

    @Test
    void ourIdElsewhereInPrefsJsDoesNotCount(@TempDir Path home) throws Exception {
        Path profile = Files.createDirectories(home.resolve(".mozilla/firefox/abc.default-release"));
        Files.writeString(profile.resolve("prefs.js"), """
                user_pref("extensions.webextensions.ExtensionStorageIDB.migrated.extension@contextswitcher.org", true);
                user_pref("extensions.webextensions.uuids", "{\\"other@x\\":\\"1\\"}");
                """);

        assertThat(InstalledExtension.find(home, Map.of())).isNull();
    }

    @Test
    void windowsFirefoxProfilesLiveUnderAppData(@TempDir Path home, @TempDir Path appData)
            throws Exception {
        Path profile = Files.createDirectories(appData.resolve("Mozilla/Firefox/Profiles/x.default"));
        Files.writeString(profile.resolve("extensions.json"),
                "{\"addons\":[{\"id\":\"extension@contextswitcher.org\"}]}");

        assertThat(InstalledExtension.find(home, Map.of("APPDATA", appData.toString())))
                .isEqualTo(Browser.FIREFOX);
    }

    @Test
    void microsoftStoreFirefoxProfilesLiveInItsPackage(@TempDir Path home, @TempDir Path localAppData)
            throws Exception {
        Path profile = Files.createDirectories(localAppData.resolve(
                "Packages/Mozilla.Firefox_n80bbvh6b1yt2/LocalCache/Roaming/Mozilla/Firefox/Profiles/x.default"));
        Files.writeString(profile.resolve("extensions.json"),
                "{\"addons\":[{\"id\":\"extension@contextswitcher.org\"}]}");

        assertThat(InstalledExtension.find(home, Map.of("LOCALAPPDATA", localAppData.toString())))
                .isEqualTo(Browser.FIREFOX);
    }

    @Test
    void aProfileOutsideTheFirefoxDirectoryIsReadThroughProfilesIni(@TempDir Path home,
            @TempDir Path elsewhere) throws Exception {
        Files.writeString(elsewhere.resolve("extensions.json"),
                "{\"addons\":[{\"id\":\"extension@contextswitcher.org\"}]}");
        Path base = Files.createDirectories(home.resolve(".mozilla/firefox"));
        Files.writeString(base.resolve("profiles.ini"),
                "[Profile0]\nName=work\nIsRelative=0\nPath=" + elsewhere + "\n");

        assertThat(InstalledExtension.find(home, Map.of())).isEqualTo(Browser.FIREFOX);
    }

    @Test
    void unpackedChromeExtensionIsFoundThroughItsManifest(@TempDir Path home, @TempDir Path checkout)
            throws Exception {
        Path unpacked = Files.createDirectories(checkout.resolve("extension/chrome"));
        Files.writeString(unpacked.resolve("manifest.json"),
                "{\"manifest_version\":3,\"name\":\"ContextSwitcher\"}");
        Path profile = Files.createDirectories(home.resolve(".config/google-chrome/Default"));
        String path = unpacked.toString().replace("\\", "\\\\");
        Files.writeString(profile.resolve("Secure Preferences"),
                "{\"extensions\":{\"settings\":{\"abcdefgh\":{\"location\":4,\"path\":\"" + path + "\"}}}}");

        assertThat(InstalledExtension.find(home, Map.of())).isEqualTo(Browser.CHROME);
    }

    @Test
    void otherChromeExtensionsAndBrokenFilesAreNotOurs(@TempDir Path home) throws Exception {
        Path profile = Files.createDirectories(home.resolve(".config/chromium/Default"));
        Files.writeString(profile.resolve("Preferences"),
                "{\"extensions\":{\"settings\":{\"x\":{\"path\":\"x/1.0\",\"manifest\":{\"name\":\"Other\"}}}}}");
        Files.writeString(profile.resolve("Secure Preferences"), "{not json");

        assertThat(InstalledExtension.find(home, Map.of())).isNull();
    }
}
