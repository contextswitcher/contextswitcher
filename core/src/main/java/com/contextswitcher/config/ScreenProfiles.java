package com.contextswitcher.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/// The `screenProfiles:` list of `settings.yaml` — which environment profile
/// the connected screens call for, whatever task is selected: docked at the
/// desk (the big Dell connected) the `desk` profile gathers windows onto it,
/// in the lecture hall (a projector) another one applies.
///
/// ```yaml
/// screenProfiles:          # first rule whose screen is connected wins
///   - screen: DELL U3223QE # EDID name, product code, primary, internal, external
///     profile: desk
/// ```
///
/// The selectors are those of a profile's `screens:`; no rule matching means
/// no screen layer. Read leniently like [EnvironmentProfiles]: an entry
/// without both keys is skipped, an unparseable file reads as no rules.
// [impl->dsn~screen-chosen-profile~1]
public record ScreenProfiles(List<Rule> rules) {

    public static final ScreenProfiles NONE = new ScreenProfiles(List.of());

    public ScreenProfiles {
        rules = List.copyOf(rules);
    }

    /// One entry: when a screen matching `screen` is connected, `profile` applies.
    public record Rule(String screen, String profile) {
    }

    /// A screen as the screen watcher reports it: EDID friendly name (empty
    /// for many laptop panels), product code from the device path
    /// (`DEL4308`), whether it is the laptop's own panel, and whether it is
    /// the primary screen (at 0,0).
    public record ConnectedScreen(String name, String code, boolean internal, boolean primary) {

        /// Whether `selector` names this screen — the same rules as a
        /// profile's `screens:` keys, case-insensitive.
        public boolean matches(String selector) {
            String s = selector.strip();
            return (!name.isEmpty() && s.equalsIgnoreCase(name))
                    || (!code.isEmpty() && s.equalsIgnoreCase(code))
                    || (primary && s.equalsIgnoreCase("primary"))
                    || (internal && s.equalsIgnoreCase("internal"))
                    || (!internal && s.equalsIgnoreCase("external"));
        }

        @Override
        public String toString() {
            String label = name.isEmpty() ? (code.isEmpty() ? "(unnamed)" : code) : name;
            return label + (internal ? " internal" : "") + (primary ? " primary" : "");
        }
    }

    public static ScreenProfiles parse(String settingsYaml) {
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(settingsYaml);
        } catch (RuntimeException e) {
            return NONE;
        }
        if (!(loaded instanceof Map<?, ?> root) || !(root.get("screenProfiles") instanceof List<?> entries)) {
            return NONE;
        }
        List<Rule> rules = new ArrayList<>();
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> map
                    && map.get("screen") instanceof Object screen && map.get("profile") instanceof Object profile
                    && !screen.toString().isBlank() && !profile.toString().isBlank()) {
                rules.add(new Rule(screen.toString().strip(), profile.toString().strip()));
            }
        }
        return new ScreenProfiles(rules);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /// The profile of the first rule one of `screens` matches; null when none does.
    public @Nullable String pick(List<ConnectedScreen> screens) {
        for (Rule rule : rules) {
            for (ConnectedScreen screen : screens) {
                if (screen.matches(rule.screen())) {
                    return rule.profile();
                }
            }
        }
        return null;
    }
}
