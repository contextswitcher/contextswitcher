package com.contextswitcher;

import com.contextswitcher.local.AppUpdate;

/// Entry point NOT extending `Application`: the java launcher refuses to start
/// an `Application` subclass when JavaFX sits on the classpath (as it does
/// here, JabRef-style variant resolution without the module path).
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
        // `launch` has returned, so the FX toolkit is down and `Main.stop` has
        // run: the exit code is all that is left to say.
        // [impl->dsn~restart-to-update~11]
        if (AppUpdate.restartRequested()) {
            System.exit(AppUpdate.RESTART_EXIT_CODE);
        }
    }
}
