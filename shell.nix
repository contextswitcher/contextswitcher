# Development shell for NixOS / nix-enabled Linux: `nix-shell` in the repo root.
#
# Nothing in the project needs Nix — this only supplies what a normal Linux
# distribution has in `/usr/lib` and NixOS deliberately does not.
#
# JavaFX is pulled as plain Maven artifacts (see `app/build.gradle.kts`), so its
# native libraries ship as prebuilt `.so` files inside the jars. JavaFX extracts
# them to `~/.openjfx/cache/<version>/amd64/` and `dlopen`s them from there;
# they carry no RPATH and are not patchelf'ed, so the GTK/X11 stack they link
# against has to be reachable through `LD_LIBRARY_PATH`. Without it the toolkit
# dies with `UnsatisfiedLinkError: no glassgtk3 in java.library.path`, which is
# what any UI test or `./gradlew run` hits on a bare NixOS box.
#
# `xvfb-run` is here for `./gradlew :app:uiTest` (MADR 0014); NixOS ships the
# `Xvfb` binary in `xorg.xorgserver` but the wrapper script in its own package.
{ pkgs ? import <nixpkgs> { } }:

let
  # nixpkgs moved the X libraries out of the `xorg` set and lower-cased them
  # (`xorg.libX11` -> `libx11`); take the new name where it exists so the shell
  # is warning-free on current nixpkgs and still evaluates on an older channel.
  x11 = name: legacy: pkgs.${name} or pkgs.xorg.${legacy};

  # Everything the extracted JavaFX natives link against:
  #   libglassgtk3.so -> gtk3, gdk, pango, atk, cairo, gdk-pixbuf, glib, libXtst
  #   libglass.so     -> libX11
  #   libprism_es2.so -> libX11, libXxf86vm, libGL
  #   libjavafx_font_freetype.so -> freetype, fontconfig
  javafxRuntimeLibs = (with pkgs; [
    gtk3
    glib
    pango
    cairo
    gdk-pixbuf
    atk
    freetype
    fontconfig
    libGL
  ]) ++ [
    (x11 "libx11" "libX11")
    (x11 "libxtst" "libXtst")
    (x11 "libxxf86vm" "libXxf86vm")
    (x11 "libxrender" "libXrender")
    (x11 "libxext" "libXext")
  ];
in
pkgs.mkShell {
  packages = with pkgs; [
    jdk25
    just
    xvfb-run
    jbang # `jbang heylogs@nbbrd check CHANGELOG.md` — the changelog gate
  ] ++ javafxRuntimeLibs;

  # Gradle's toolchain detection picks the JDK up from JAVA_HOME.
  JAVA_HOME = "${pkgs.jdk25}";

  LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath javafxRuntimeLibs;
}
