package com.contextswitcher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolchainSmokeTest {

    @Test
    void testsRunOnJava25Toolchain() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(25);
    }
}
