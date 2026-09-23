#!/usr/bin/env bash
# Mechanical half of CONSISTENCY.md: prints one line per finding, exits 1 if any.
# Run from the repository root. The judgment checks stay in CONSISTENCY.md.
set -u
cd "$(dirname "$0")/.."
finding() { echo "$1: $2"; }

checks() {

defined=$(mktemp)
grep -rhoE '^`(feat|req|dsn)~[a-z0-9-]+~[0-9]+`' docs/requirements/*.md | tr -d '`' | sort -u > "$defined"

# 1. Spec ids cited in living prose exist at that revision.
# CHANGELOG.md and the MADRs are history: they cite the revision current when written.
for f in README.md docs/manual-test.md docs/requirements/*.md; do
  grep -noE '`(feat|req|dsn)~[a-z0-9-]+~[0-9]+`' "$f" | tr -d '`' | sort -u | while IFS=: read -r line id; do
    grep -qx "$id" "$defined" || finding "stale spec id" "$f:$line $id"
  done
done

# 2. Every settings key the app knows is documented in the README's settings block, and vice versa.
catalog=$(grep -oE 'new Option\("[a-zA-Z]+"' core/src/main/java/com/contextswitcher/config/SettingsCatalog.java | grep -oE '"[a-zA-Z]+"' | tr -d '"' | sort -u)
readme=$(awk '/^## Settings/,/^### /' README.md | grep -oE '^[a-zA-Z]+:' | tr -d : | sort -u)
comm -23 <(echo "$catalog") <(echo "$readme") | while read -r k; do finding "settings key missing from README" "$k"; done
comm -13 <(echo "$catalog") <(echo "$readme") | while read -r k; do finding "README settings key unknown to SettingsCatalog" "$k"; done

# 3. Relative Markdown links and images resolve.
for f in README.md docs/*.md docs/requirements/*.md docs/decisions/*.md extension/*/README.md; do
  d=$(dirname "$f")
  grep -oE '\]\([^)#: ]+(#[^)]*)?\)' "$f" | sed -E 's/^\]\(//; s/\)$//; s/#.*//' | sort -u | while read -r l; do
    [ -n "$l" ] && [ ! -e "$d/$l" ] && finding "broken link" "$f -> $l"
  done
done

# 4. MADR numbers cited anywhere exist, and every MADR file is in the decisions index.
grep -rhoE 'MADR [0-9]{4}' --include=*.java --include=*.md --include=*.kts --include=*.js . 2>/dev/null | grep -v '/build/' | sort -u | while read -r m; do
  n=${m#MADR }
  ls docs/decisions/"$n"-*.md >/dev/null 2>&1 || finding "MADR cited but missing" "$m"
done
for f in docs/decisions/[0-9]*.md; do
  grep -q "$(basename "$f")" docs/decisions/README.md || finding "MADR not in index" "$f"
done

# 5. `just` recipes the README names exist.
grep -oE '`just [a-z-]+' README.md CLAUDE.md docs/*.md | grep -oE '[a-z-]+$' | sort -u | while read -r r; do
  grep -qE "^$r:" justfile || finding "README names an unknown just recipe" "$r"
done

rm -f "$defined"
}

out=$(checks)
[ -z "$out" ] && exit 0
echo "$out"
exit 1
