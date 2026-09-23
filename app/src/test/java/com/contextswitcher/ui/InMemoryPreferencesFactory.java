package com.contextswitcher.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/// The `uiTest` Java `Preferences` store: held in memory, so a test run never
/// touches the developer's real preferences on any OS. The `userRoot` redirect
/// it replaces only reached the file-backed store of Linux/macOS — on Windows
/// the tests wrote into the registry, and their cleanup
/// (`UiTestSupport.clearStoredFilters`) erased the user's remembered filters.
/// Lives for the test JVM, so the TestFX per-method restart still sees what
/// the previous method stored.
public final class InMemoryPreferencesFactory implements PreferencesFactory {

    private static final Preferences USER = new Node(null, "");
    private static final Preferences SYSTEM = new Node(null, "");

    @Override
    public Preferences userRoot() {
        return USER;
    }

    @Override
    public Preferences systemRoot() {
        return SYSTEM;
    }

    /// One node's key/value pairs; `AbstractPreferences` caches the children.
    private static final class Node extends AbstractPreferences {

        private final Map<String, String> values = new HashMap<>();

        Node(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(String[]::new);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return new Node(this, name);
        }

        @Override
        protected void syncSpi() {
        }

        @Override
        protected void flushSpi() {
        }
    }
}
