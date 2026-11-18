package com.contextswitcher.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.contextswitcher.config.EnvironmentProfile;
import com.contextswitcher.config.EnvironmentProfile.Resolution;
import com.contextswitcher.config.EnvironmentProfile.Screen;

class PowerShellCommandTest {

    /// Windows' cap on a whole command line (CreateProcess error 206 above it).
    private static final int COMMAND_LINE_CAP = 32_767;

    private static String decoded(List<String> command) {
        return new String(Base64.getDecoder().decode(command.getLast()), StandardCharsets.UTF_16LE);
    }

    private static int length(List<String> command) {
        return String.join(" ", command).length();
    }

    @Test
    void smallScriptTravelsInline() {
        List<String> command = PowerShellCommand.of("Write-Output 'hi'");

        assertThat(command).startsWith("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand");
        assertThat(decoded(command)).isEqualTo("Write-Output 'hi'");
    }

    @Test
    void largeScriptIsLoadedFromItsFile() throws Exception {
        String script = "# " + "x".repeat(15_000) + "\nWrite-Output 'ä'\n";

        List<String> command = PowerShellCommand.of(script);

        assertThat(length(command)).isLessThan(1_000);
        Path file = PowerShellCommand.scriptFile(script);
        assertThat(file).isNotNull();
        assertThat(decoded(command)).contains(file.toString()).startsWith(". ([scriptblock]::Create(");
        assertThat(Files.readString(file)).isEqualTo(script);
    }

    /// The field report: profile `lecture` (screens + gatherWindows) failed
    /// with error 206 because both scripts exceeded the command-line cap.
    @Test
    void screenScriptsFitTheCommandLine() {
        EnvironmentProfile profile = new EnvironmentProfile(
                Map.of("primary", new Screen(new Resolution(1920, 1080))), null, null, null);

        assertThat(length(WindowsEnvironment.command(profile))).isLessThan(COMMAND_LINE_CAP);
        assertThat(length(WindowGather.command("primary", 1))).isLessThan(COMMAND_LINE_CAP);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void largeScriptRunsWithItsExitCode() {
        String script = "# " + "x".repeat(15_000) + "\n$v = 'ok'\nWrite-Output $v\nexit 3\n";

        LocalCommandRunner.LocalResult result = new LocalCommandRunner().run(PowerShellCommand.of(script));

        assertThat(result.stdout().strip()).isEqualTo("ok");
        assertThat(result.exitCode()).isEqualTo(3);
    }
}
