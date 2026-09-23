---
status: accepted
date: 2026-07-13
decision-makers: Oliver Kopp
---

# SvgNode + SVG-MaterialDesign for UI Icons

## Context and Problem Statement

The task rows gained hover-only icon buttons (suspend/resume, delete). The first implementation used unicode symbol glyphs (⏸, ▶, 🗑) as button text — JavaFX on Windows renders them as empty boxes (no font substitution to Segoe UI Symbol/Emoji). The UI needs icons that reliably render, styled consistently with AtlantaFX.

## Considered Options

* Maran23 stack: [SvgNode](https://github.com/Maran23/svgnode) (JavaFX node rendering an SVG path) + [SVG-MaterialDesign](https://github.com/Maran23/svg-materialdesign) (all Material Design icon paths as Java enums)
* Ikonli (`ikonli-javafx` + an icon pack) — font-based icons as JavaFX nodes
* Hand-rolled `SVGPath` nodes for the handful of needed icons
* Keep unicode glyphs, force a symbol-capable font

## Decision Outcome

Chosen option: "SvgNode + SVG-MaterialDesign" (`tools.maran:svgnode:1.0.0`, `tools.maran:svg-materialdesign:1.0.0`), decided by Oliver (Ikonli explicitly vetoed). Both are MIT-licensed, dependency-free, on Maven Central, and target exactly this project's stack (Java 25+, JavaFX 25+). `SvgNode` renders a plain SVG path at any size, auto-adjusts its color to the background like text (fits AtlantaFX theming), and an icon is one enum constant away (`MDIInterface.PAUSE.path()`).

### Consequences

* Good, because icons render identically on Windows/Linux — plain path geometry, no font machinery or per-OS font availability.
* Good, because future icon needs are one enum constant away across the full Material Design set (sibling libs exist for Bootstrap/FontAwesome, same `path()` shape).
* Bad, because young libraries (1.0.0, single maintainer); mitigated by the trivial API surface — `new SvgNode(path, size)` is replaceable in one method if ever needed.

## Pros and Cons of the Options

### Ikonli

* Good, because long-established and the pairing AtlantaFX documents.
* Bad, because vetoed by Oliver; font-based icon machinery is heavier than plain SVG paths.

### Hand-rolled SVGPath

* Good, because zero dependencies.
* Bad, because every new icon is manual path curation, and consistent sizing/coloring is do-it-yourself — exactly what SvgNode already solves.

### Unicode glyphs with a forced font

* Good, because no dependency.
* Bad, because font availability differs per OS (the trigger of this decision), and color-emoji fallback looks alien in a themed UI.
