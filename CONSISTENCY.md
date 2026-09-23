# Consistency checks

What must agree with what, and how to find out when it stopped agreeing.
The build catches the code-side drift (`traceRequirements`, heylogs, the tests); this file covers the drift the build cannot see: prose that describes code, and prose that describes other prose.

Run: `scripts/consistency.sh` first (the mechanical checks; one line per finding, exit 1 if any), then walk the judgment checks below.
A Claude session doing the weekly run reads this file, runs the script, works through every check, fixes what is mechanical, and reports the rest.

## Mechanical (`scripts/consistency.sh`)

1. **Spec ids in living prose exist at the cited revision.**
   A `dsn~x~3` in README.md, `docs/manual-test.md` or the requirement documents must be defined in `docs/requirements/` at exactly `~3`.
   Exempt as history: `CHANGELOG.md` and the MADRs — they cite the revision that was current when written.
   OpenFastTrace checks the tags in code, never a backtick in prose, which is why this drifts.
2. **Settings keys.** Every `Option` in `SettingsCatalog` appears in the README's `settings.yaml` block, and nothing in that block is unknown to the catalog.
3. **Relative links resolve.** Every relative Markdown link and image in README.md, `docs/`, and the extension READMEs points at a file that exists.
4. **MADRs.** Every `MADR NNNN` cited in code or docs has a file; every decision file is listed in `docs/decisions/README.md`.
5. **`just` recipes** named in the READMEs exist in the `justfile`.

## Judgment (a reader with the code open)

6. **Keyboard shortcuts.** Every key the README names does what the README says, and every chord the code installs is documented somewhere a user finds it.
   Sources of truth in code: `ReadlineKeys.bindings()`, the `KeyCombination` constants in `QueuePane` and `MainWindow`, the `KEY_PRESSED` filters in `TerminalPane` (`installControlKeyForwarding`).
   Prose that names keys: README.md (editor `Ctrl+S`, the compose chords, `Ctrl+F`, `readlineKeys`), `docs/requirements/ui.md` and `terminal.md`, `docs/manual-test.md`.
   Compare direction both ways: a chord in code without prose is undocumented, prose without a chord is a lie.
7. **README describes the current app.** For each README section, the behavior it describes is what the code does now: button names, menu items, the dot colours, the status-bar messages, the `settings.yaml` comments, the task-file keys in the frontmatter example versus `FrontmatterCatalog`.
   Cheapest route: diff the README section against the requirement item it links to, then the item against its `[impl->…]` tags.
8. **Requirement text matches the tagged code.** For each `dsn` item touched since the last run (`git log --since` on `docs/requirements/`), read the classes carrying its `[impl->dsn~…]` tag and confirm the item still describes what they do.
   A `Needs: impl` without any covering tag, or a tag on code that no longer does the described thing, is a finding even though `traceRequirements` is green.
9. **CLAUDE.md conventions are followed.** Spot-check the ones a build cannot enforce: `///` doc comments, `Styles.SMALL` on new controls, `@NullMarked` in every `package-info.java`, one sentence per line in edited Markdown, tinylog only.
   A grep per convention is enough; do not read every file.
10. **`docs/manual-test.md` names real things.** Buttons, menu items and settings keys it tells the tester to use still exist under that name.
11. **CHANGELOG agrees with the code.** The topmost CHANGELOG section describes features that exist.

## Adding a check

A check earns a place here when its drift was found by hand at least once.
Mechanical if a grep can decide it: add it to `scripts/consistency.sh` and a line above.
Judgment otherwise: a line above naming the two things that must agree and where each lives.
