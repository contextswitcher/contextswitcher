#!/bin/sh
# Runs ContextSwitcher until it is really quit: exit code 55 means "Restart to
# update" was clicked, so pull, rebuild and start it again. Any other code ends
# the loop. Run it from anywhere — it works on its own checkout.
set -e
cd "$(dirname "$0")/.."

# Look before leaping: JavaFX ships its natives as prebuilt `.so` files that
# expect the GTK/X11 stack in the usual system paths, and on NixOS there is none
# (see the NixOS section of README.md). Without `shell.nix` entered, the launcher
# below dies with `UnsatisfiedLinkError: no glassgtk3` — but only after this
# script has pulled and run a full jpackage build, so the loop spends minutes to
# report a missing `nix-shell`. The packaged image does not help: jpackage
# bundles a JRE, not the host's GTK.
#
# The search is what the dynamic loader does with LD_LIBRARY_PATH plus the
# default directories, so a normal Linux distribution finds the library in
# /usr/lib and falls straight through; anything but Linux skips the check.
have_lib() {
    for dir in $(echo "${LD_LIBRARY_PATH:-}" | tr ':' ' ') \
               /usr/lib /usr/lib64 /lib /lib64 /usr/lib/*-linux-gnu; do
        if [ -e "$dir/$1" ]; then
            return 0
        fi
    done
    return 1
}

if [ "$(uname -s)" = Linux ] && ! have_lib libgtk-3.so.0; then
    echo "run-loop: libgtk-3.so.0 is neither on LD_LIBRARY_PATH nor in the system library paths." >&2
    echo "          JavaFX needs it — the app would fail with 'UnsatisfiedLinkError: no glassgtk3'." >&2
    echo "          On NixOS, run this inside the project shell:" >&2
    echo "              nix-shell --run 'just run-loop'" >&2
    echo "          or run 'direnv allow' once in the repo: .envrc then enters shell.nix on cd." >&2
    exit 1
fi

# Tells the app a restart request will be acted on (`AppUpdate.RUN_LOOP_ENV`).
export CONTEXTSWITCHER_RUN_LOOP=1

while :; do
    git pull --no-rebase
    ./gradlew :app:jpackageImage
    jbang scripts/WhatsNew.java
    set +e
    app/build/jpackage/image/ContextSwitcher/bin/ContextSwitcher
    code=$?
    set -e
    [ "$code" -eq 55 ] || exit "$code"
done
