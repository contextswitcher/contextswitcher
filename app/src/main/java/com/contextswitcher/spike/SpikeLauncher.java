package com.contextswitcher.spike;

import javafx.application.Application;

/// Launch indirection for the classpath-loaded JavaFX (like
/// `com.contextswitcher.Launcher`): the java launcher refuses a main class
/// that extends `Application` when JavaFX is not on the module path.
public class SpikeLauncher {

    public static void main(String[] args) {
        Application.launch(TerminalSpike.class, args);
    }
}
