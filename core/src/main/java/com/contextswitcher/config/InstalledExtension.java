package com.contextswitcher.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Whether the ContextSwitcher browser extension is installed on this machine,
/// read from the browsers' own profile files — so the answer does not depend
/// on a browser running and its extension being connected right now.
///
/// Firefox lists every add-on of a profile in `extensions.json` (`addons[].id`,
/// ours is [#FIREFOX_ID]); a temporary add-on is listed only while Firefox
/// runs. Its profiles are the `Path=` entries of `profiles.ini` — a profile
/// may live anywhere (`IsRelative=0`) — plus the directories beside it. Chrome has no fixed id for an unpacked extension (the id derives
/// from its path), so each entry of `extensions.settings` in a profile's
/// `Preferences`/`Secure Preferences` is followed to its `manifest.json`
/// (absolute for an unpacked one, under the profile's `Extensions` otherwise)
/// and matched on the manifest name [#NAME].
///
/// Every unreadable file counts as *not installed*: this only decides whether
/// the setup wizard's extension page is still worth showing.
// [impl->dsn~setup-wizard~11]
public final class InstalledExtension {

    /// The Firefox extension's `browser_specific_settings.gecko.id`.
    static final String FIREFOX_ID = "extension@contextswitcher.org";

    /// Both extensions' manifest `name`.
    static final String NAME = "ContextSwitcher";

    private static final ObjectMapper JSON = new ObjectMapper();

    private InstalledExtension() {
    }

    /// The browser the extension is installed in on this machine, Firefox
    /// first, or null when neither has it.
    public static @Nullable Browser find() {
        return find(Path.of(System.getProperty("user.home")), System.getenv());
    }

    /// [#find()] against the home directory `home` and the environment `env`
    /// (`APPDATA`/`LOCALAPPDATA` on Windows).
    static @Nullable Browser find(Path home, Map<String, String> env) {
        if (firefoxProfiles(home, env).stream().anyMatch(InstalledExtension::inFirefoxProfile)) {
            return Browser.FIREFOX;
        }
        if (profiles(chromeRoots(home, env)).stream().anyMatch(InstalledExtension::inChromeProfile)) {
            return Browser.CHROME;
        }
        return null;
    }

    /// Firefox's own directories, the ones holding `profiles.ini`: Windows
    /// (classic installer and Microsoft Store package), the Linux classic, Snap and Flatpak locations, macOS.
    private static List<Path> firefoxBases(Path home, Map<String, String> env) {
        List<Path> bases = new ArrayList<>();
        String appData = env.get("APPDATA");
        if (appData != null) {
            bases.add(Path.of(appData, "Mozilla", "Firefox"));
        }
        // The Microsoft Store build keeps its roaming data inside its package
        // (`Mozilla.Firefox_<publisher hash>`), not under %APPDATA%.
        String localAppData = env.get("LOCALAPPDATA");
        if (localAppData != null) {
            for (Path pkg : profiles(List.of(Path.of(localAppData, "Packages")))) {
                if (pkg.getFileName().toString().startsWith("Mozilla.Firefox")) {
                    bases.add(pkg.resolve("LocalCache/Roaming/Mozilla/Firefox"));
                }
            }
        }
        bases.add(home.resolve(".mozilla/firefox"));
        bases.add(home.resolve("snap/firefox/common/.mozilla/firefox"));
        bases.add(home.resolve(".var/app/org.mozilla.firefox/.mozilla/firefox"));
        bases.add(home.resolve("Library/Application Support/Firefox"));
        return bases;
    }

    /// Every Firefox profile directory: what `profiles.ini` names, and the
    /// subdirectories of each base and of its `Profiles` (where Windows and
    /// macOS put them).
    private static List<Path> firefoxProfiles(Path home, Map<String, String> env) {
        List<Path> bases = firefoxBases(home, env);
        List<Path> roots = new ArrayList<>(bases);
        bases.forEach(base -> roots.add(base.resolve("Profiles")));
        List<Path> profiles = profiles(roots);
        for (Path base : bases) {
            profiles.addAll(iniProfiles(base));
        }
        return profiles;
    }

    /// The `Path=` entries of `base/profiles.ini`, relative ones resolved
    /// against `base` (Firefox writes `IsRelative=` per section; a relative
    /// path never starts at a root, so the path itself tells).
    private static List<Path> iniProfiles(Path base) {
        Path ini = base.resolve("profiles.ini");
        if (!Files.isRegularFile(ini)) {
            return List.of();
        }
        List<Path> profiles = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(ini)) {
                if (!line.startsWith("Path=")) {
                    continue;
                }
                try {
                    Path path = Path.of(line.substring("Path=".length()).strip());
                    profiles.add(path.isAbsolute() ? path : base.resolve(path));
                } catch (InvalidPathException e) {
                    Logger.debug("Skipping profile path in {}: {}", ini, e.getMessage());
                }
            }
        } catch (IOException e) {
            Logger.debug("Cannot read {}: {}", ini, e.getMessage());
        }
        return profiles;
    }

    /// Where Chrome and Chromium keep their profiles (the `User Data`
    /// directory, whose children are `Default`, `Profile 1`, …).
    private static List<Path> chromeRoots(Path home, Map<String, String> env) {
        List<Path> roots = new ArrayList<>();
        String localAppData = env.get("LOCALAPPDATA");
        if (localAppData != null) {
            roots.add(Path.of(localAppData, "Google", "Chrome", "User Data"));
            roots.add(Path.of(localAppData, "Chromium", "User Data"));
        }
        roots.add(home.resolve(".config/google-chrome"));
        roots.add(home.resolve(".config/chromium"));
        roots.add(home.resolve("Library/Application Support/Google/Chrome"));
        roots.add(home.resolve("Library/Application Support/Chromium"));
        return roots;
    }

    /// The direct subdirectories of every existing root.
    private static List<Path> profiles(List<Path> roots) {
        List<Path> profiles = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> children = Files.list(root)) {
                children.filter(Files::isDirectory).forEach(profiles::add);
            } catch (IOException e) {
                Logger.debug("Cannot list browser profiles in {}: {}", root, e.getMessage());
            }
        }
        return profiles;
    }

    private static boolean inFirefoxProfile(Path profile) {
        JsonNode addons = read(profile.resolve("extensions.json")).path("addons");
        for (JsonNode addon : addons) {
            if (FIREFOX_ID.equals(addon.path("id").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean inChromeProfile(Path profile) {
        for (String file : List.of("Secure Preferences", "Preferences")) {
            JsonNode settings = read(profile.resolve(file)).path("extensions").path("settings");
            for (JsonNode entry : settings) {
                if (NAME.equals(entry.path("manifest").path("name").asText())
                        || namedOurs(profile, entry.path("path").asText(""))) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Whether the extension directory `path` of a Chrome settings entry
    /// holds our manifest.
    private static boolean namedOurs(Path profile, String path) {
        if (path.isBlank()) {
            return false;
        }
        try {
            Path dir = Path.of(path);
            if (!dir.isAbsolute()) {
                dir = profile.resolve("Extensions").resolve(path);
            }
            return NAME.equals(read(dir.resolve("manifest.json")).path("name").asText());
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /// The JSON in `file`, or a missing node when it is absent or unreadable.
    private static JsonNode read(Path file) {
        if (!Files.isRegularFile(file)) {
            return JSON.missingNode();
        }
        try {
            return JSON.readTree(file.toFile());
        } catch (IOException e) {
            Logger.debug("Cannot read {}: {}", file, e.getMessage());
            return JSON.missingNode();
        }
    }
}
