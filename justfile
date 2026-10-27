# Developer command runner for ContextSwitcher — https://just.systems
# `just` lists recipes; `just <name>` runs one. Cross-platform: each recipe has
# a [unix] and a [windows] variant (they differ only in the gradle wrapper and
# the built launcher path). Windows recipes run under cmd.
set windows-shell := ["cmd.exe", "/c"]

# Show the recipe list when run without a recipe.
default:
    @just --list

# Build the app image and register the contextswitcher:// URL handler (current user, #47).
[unix]
register:
    ./gradlew :app:registerUrlHandler

[windows]
register:
    gradlew.bat :app:registerUrlHandler

# Build the self-contained app image (bundled JRE, host OS).
[unix]
jpackage:
    ./gradlew :app:jpackageImage

[windows]
jpackage:
    gradlew.bat :app:jpackageImage

# Build the app image and launch the packaged executable.
[unix]
run: jpackage whats-new
    app/build/jpackage/image/ContextSwitcher/bin/ContextSwitcher

[windows]
run: jpackage whats-new
    app\build\jpackage\image\ContextSwitcher\ContextSwitcher.exe

# The first run only records the commit; `--stdout` prints instead of opening
# a window.
#
# Pop up the CHANGELOG bullets added since the previous run, by others and by me.
whats-new *ARGS:
    jbang scripts/WhatsNew.java {{ARGS}}

# Run the app in a loop: the "Restart to update" button (exit code 55) pulls,
# rebuilds and starts it again; a normal quit ends the loop.
[unix]
run-loop:
    scripts/run-loop.sh

[windows]
run-loop:
    scripts\run-loop.cmd

pull-run-debug: && run-debug
    git pull

# Pull the latest main, then build and launch the packaged executable.
pull-run: && run
    git pull

# Run from source via Gradle — fast, no packaging (development).
[unix]
run-debug: whats-new
    ./gradlew run

# Run the TestFX UI tests (MADR 0014) on JavaFX's headless glass platform — no
# display, no Xvfb, nothing on the real desktop is touched.
[unix]
uitest:
    ./gradlew :app:uiTest

[windows]
uitest:
    gradlew.bat :app:uiTest

# Fallback: the same tests on the native GTK platform under Xvfb, should the
# headless platform (a JavaFX 26 prototype) misbehave. The screen size is
# explicit: `xvfb-run`'s default is the distribution's, and nixpkgs ships
# 640x480 — too small for the app window, so clicks aimed at controls that got
# pushed off the visible area silently do nothing.
[unix]
uitest-xvfb:
    xvfb-run -a -s "-screen 0 1920x1200x24" ./gradlew :app:uiTest -Dglass.platform=gtk

[windows]
run-debug: whats-new
    gradlew.bat run
