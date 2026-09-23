# Requirements

Requirements are traced with [OpenFastTrace](https://github.com/itsallcode/openfasttrace) (see [decision 0005](../decisions/0005-requirements-tracing-with-openfasttrace.md)).

## Structure

| File | Content |
|------|---------|
| [features.md](features.md) | Vision-level features (`feat~…`) distilled from the [README](../../README.md) |
| [tasks.md](tasks.md) | Task storage: file format, parsing, watching (`req~` + `dsn~`) |
| [switching.md](switching.md) | The switch actions and orchestration (`req~` + `dsn~`) |
| [browser-integration.md](browser-integration.md) | Firefox extension and its protocol (`req~` + `dsn~`) |
| [terminal.md](terminal.md) | Terminal snapshot preview and (M2) live mirror (`req~` + `dsn~`) |
| [ui.md](ui.md) | Main window (`req~` + `dsn~`) |
| [analysis.md](analysis.md) | Refactoring insight: RefactoringMiner view and badge (`req~` + `dsn~`) |
| [android.md](android.md) | Android companion app: task repo sync and read-only task list (`feat~` + `req~` + `dsn~`) |
| [provenance.md](provenance.md) | Provenance of sent messages: records, replies, commit ranges, report (`req~` + `dsn~`) |

Chain: `feat` → `req` (user stories; link related GitHub idea issues) → `dsn` (design) → `[impl->dsn~…~1]` / `[utest->dsn~…~1]` tags in source code.

## Conventions

* A `dsn` item gains `Needs: impl` (and `utest` where unit-testable logic exists) **in the same commit** that adds the covering code and tags. This keeps `gradlew traceRequirements` green at every commit.
* Spec item names are kebab-case and stable; increase the revision on semantic change and update all coverage tags in the same commit.
* Every `req` and `dsn` carries a `Tags:` line per [Platform targets](#platform-targets): a new design names the targets it implements, a new requirement the targets it is decided for.

## Platform targets

The targets are `windows`, `linux` and `android` (the list lives in the root `build.gradle.kts`, `requirementTargets`; a new platform is a new entry there).
Every item says for itself what it means per target, in a `Tags:` line above its `Covers:` block — no tag says anything about another target.

A `req` holds, per target, at most one decision:

| Tag | Decision |
|-----|----------|
| none of the three | undecided — the default, so writing a requirement for one platform costs no thought about the others |
| `<target>` | wanted now |
| `<target>_later` | wanted, not now |
| `<target>_no` | not for this target |

A `dsn` carries only plain target tags: the targets it implements (`Tags: windows, linux` for the shared JavaFX desktop code, `Tags: android` for the app).
Whether a wanted requirement is done on a target is never written down — the trace works it out from the designs.

Tags must be plain words: OpenFastTrace silently ignores a whole `Tags:` line that holds a hyphenated tag, so it is `android_later`, never `android-later`.
`checkRequirementTags` (run by `traceRequirements`) fails on such a tag, an unknown one, a design without tags or with a `_later`/`_no` tag, and two decisions for one target on one requirement.

### Reports

```
gradlew requirementReportAndroid
```

lists, for that target, the requirements **wanted now** without a design for it (OpenFastTrace traced over the target's tag only), those **wanted later** without one, and the **undecided** ones.
`requirementReportWindows` and `requirementReportLinux` work the same way; the reports never fail the build.

## Running the trace

```
gradlew traceRequirements
```

Report: `build/reports/tracing.txt`. The build fails on any tracing defect.
