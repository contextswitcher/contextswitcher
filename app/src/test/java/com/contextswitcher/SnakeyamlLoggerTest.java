package com.contextswitcher;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/// Guards the silencing of SnakeYAML's fileless duplicate-key warning.
// [utest->dsn~frontmatter-duplicate-keys~1]
class SnakeyamlLoggerTest {

    @Test
    void duplicateKeyWarningIsSilenced() throws Exception {
        Class.forName(Main.class.getName());
        assertFalse(Logger.getLogger("org.yaml.snakeyaml.constructor.SafeConstructor")
                .isLoggable(Level.WARNING));
    }
}
