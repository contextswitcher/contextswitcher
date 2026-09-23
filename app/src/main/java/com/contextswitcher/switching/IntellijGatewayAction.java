package com.contextswitcher.switching;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.tasks.Task;

/// Opens the task's project via JetBrains Remote Development. An already
/// open local JetBrains Client window for the project is focused instead
/// (injected predicate, [com.contextswitcher.local.JetBrainsClientFocus]) —
/// relaunching the URL would trigger Gateway's "Requested Project Is
/// Already Running" version prompt or a second window. Otherwise a
/// `jetbrains-gateway://connect#…` URL is launched (MADR 0006); the URL
/// launcher is injected (`Main::openUrl` in the app) so URL building stays
/// unit-testable. A `user@host` host value is split into the URL's `user`
/// and `host` parameters; a plain ssh alias is passed through as `host`.
/// The project path resolves via `Task.intellijProjectPath()` (explicit
/// `intellij.projectPath`, else Claude workspace, else Claude cwd); the IDE
/// path via `intellij.ide`, else the injected remote lookup
/// ([RemoteIdeLookup]) — Gateway 2026.1 refuses a link without one.
// [impl->dsn~gateway-url-action~11]
public class IntellijGatewayAction implements SwitchAction {

    private final Consumer<String> urlLauncher;
    /// Resolves the newest installed backend on a remote; null = none found.
    private final Function<String, @Nullable String> ideLookup;
    /// Focuses an open local JetBrains Client window for the project path;
    /// false = none open (or mechanism unavailable) — launch the URL.
    private final Predicate<String> clientFocus;
    /// How many times to re-check for the project's client window after
    /// launching the URL (0 = do not wait — the launch itself is success).
    private final int upPollAttempts;
    /// Sleep between those checks, in milliseconds.
    private final long pollIntervalMillis;

    /// Non-waiting: the action reports success as soon as the Gateway URL is
    /// launched. Used by tests and by callers that do not need the chip to
    /// track the IDE actually coming up.
    public IntellijGatewayAction(Consumer<String> urlLauncher,
            Function<String, @Nullable String> ideLookup,
            Predicate<String> clientFocus) {
        this(urlLauncher, ideLookup, clientFocus, 0, 0);
    }

    /// Waiting: after launching, the action polls `clientFocus` up to
    /// `upPollAttempts` times (sleeping `pollIntervalMillis` between checks)
    /// for the project's JetBrains Client window to appear, so its status chip
    /// stays RUNNING (the hourglass) until the IDE is really up rather than
    /// flipping green the instant the URL is handed off.
    public IntellijGatewayAction(Consumer<String> urlLauncher,
            Function<String, @Nullable String> ideLookup,
            Predicate<String> clientFocus, int upPollAttempts, long pollIntervalMillis) {
        this.urlLauncher = urlLauncher;
        this.ideLookup = ideLookup;
        this.clientFocus = clientFocus;
        this.upPollAttempts = upPollAttempts;
        this.pollIntervalMillis = pollIntervalMillis;
    }

    @Override
    public String name() {
        return "intellij";
    }

    @Override
    public boolean isConfigured(Task task) {
        return task.intellij() != null && task.intellijRemote() != null
                && task.intellijProjectPath() != null;
    }

    /// The `jetbrains-gateway://connect#…` URL for the given host, project
    /// path, and IDE build path. Parameters (percent-encoded): `host` (and
    /// `user` when the host value is `user@host`), `type=ssh`, `port`
    /// (always 22 — Gateway 2026.1 rejects the URL with "Invalid ssh link
    /// parameters: doesn't contain port" although its docs call the
    /// parameter optional), `projectPath`, plus `idePath` and `deploy=false`.
    /// Gateway 2026.1 also refuses a link without `idePath`; `run` always
    /// resolves one, the parameter stays nullable only for direct callers.
    public static String gatewayUrl(String host, String projectPath, @Nullable String idePath) {
        String user = null;
        int at = host.indexOf('@');
        if (at >= 0) {
            user = host.substring(0, at);
            host = host.substring(at + 1);
        }
        StringBuilder url = new StringBuilder("jetbrains-gateway://connect#host=").append(encode(host));
        if (user != null) {
            url.append("&user=").append(encode(user));
        }
        url.append("&type=ssh");
        url.append("&port=22");
        url.append("&projectPath=").append(encode(projectPath));
        if (idePath != null) {
            url.append("&idePath=").append(encode(idePath));
            url.append("&deploy=false");
        }
        return url.toString();
    }

    /// Percent-encoding for a URL fragment value: `URLEncoder` form encoding
    /// with its `+`-for-space quirk corrected to `%20`.
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public ActionResult run(Task task) {
        Task.IntellijConfig intellij = task.intellij();
        String remote = task.intellijRemote();
        String projectPath = task.intellijProjectPath();
        if (intellij == null || remote == null) {
            return ActionResult.failure("intellij or remote not configured");
        }
        if (projectPath == null) {
            return ActionResult.failure("no projectPath and no claude section to derive it from");
        }
        if (clientFocus.test(projectPath)) {
            return ActionResult.success("focused the open JetBrains Client window");
        }
        String idePath = intellij.ide() != null ? intellij.ide() : ideLookup.apply(remote);
        if (idePath == null) {
            return ActionResult.failure(
                    "Gateway needs an IDE path: set intellij.ide or install a remote backend"
                            + " (~/.cache/JetBrains/RemoteDev/dist) on " + remote);
        }
        String url = gatewayUrl(remote, projectPath, idePath);
        Logger.debug("Launching JetBrains Gateway URL: {}", url);
        try {
            urlLauncher.accept(url);
        } catch (RuntimeException e) {
            return ActionResult.failure("cannot launch Gateway URL: " + e.getMessage());
        }
        // Keep the chip RUNNING (hourglass) until the project's JetBrains Client
        // window actually appears — Gateway uploads the worker binary, connects,
        // and opens the IDE, which is seconds to minutes on a cold start. Reuse
        // the same client detector; when it finds the window it also focuses it.
        // ponytail: fixed poll budget; on timeout report success (the launch
        // worked and the IDE may still be coming up) rather than a red chip.
        for (int attempt = 0; attempt < upPollAttempts && !Thread.currentThread().isInterrupted(); attempt++) {
            sleep(pollIntervalMillis);
            if (clientFocus.test(projectPath)) {
                return ActionResult.success("opened %s on %s".formatted(projectPath, remote));
            }
        }
        return ActionResult.success(upPollAttempts == 0
                ? "Gateway opening %s on %s".formatted(projectPath, remote)
                : "Gateway still starting %s on %s".formatted(projectPath, remote));
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
