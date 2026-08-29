#!/usr/bin/env bash
# Holt die neueste Version des Haupt-Branches.
#
#   ./scripts/update-main.sh              -> Main in den aktuellen Branch mergen
#   ./scripts/update-main.sh --rebase     -> statt merge rebasen
#   ./scripts/update-main.sh --checkout   -> auf Main wechseln und dort pullen
#
# Remote/Branch koennen ueberschrieben werden:
#   REMOTE=origin MAIN=main ./scripts/update-main.sh

set -euo pipefail
cd "$(dirname "$0")/.."

MODE=merge
for arg in "$@"; do
  case "$arg" in
    --rebase)   MODE=rebase ;;
    --checkout) MODE=checkout ;;
    --merge)    MODE=merge ;;
    -h|--help)  sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "Unbekannte Option: $arg" >&2; exit 2 ;;
  esac
done

# Remote bestimmen: ENV > origin > erster vorhandener
if [ -z "${REMOTE:-}" ]; then
  if git remote | grep -qx origin; then REMOTE=origin; else REMOTE="$(git remote | head -n1)"; fi
fi
[ -n "$REMOTE" ] || { echo "Kein Git-Remote konfiguriert." >&2; exit 1; }

# Haupt-Branch bestimmen: ENV > HEAD des Remotes > main > master
if [ -z "${MAIN:-}" ]; then
  MAIN="$(git remote show "$REMOTE" 2>/dev/null | sed -n 's/.*HEAD branch: //p' | head -n1)"
fi
if [ -z "${MAIN:-}" ]; then
  if git ls-remote --exit-code --heads "$REMOTE" main >/dev/null 2>&1; then MAIN=main; else MAIN=master; fi
fi

CURRENT="$(git rev-parse --abbrev-ref HEAD)"
echo ">> Remote: $REMOTE | Main: $MAIN | Aktueller Branch: $CURRENT"

# Nur getrackte Aenderungen blockieren; untracked Dateien (Logs, DBs, ...) sind egal.
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "!! Es gibt uncommittete Aenderungen. Bitte committen oder stashen." >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

echo ">> git fetch $REMOTE $MAIN"
git fetch "$REMOTE" "$MAIN" --prune

case "$MODE" in
  checkout)
    git checkout "$MAIN"
    git merge --ff-only "$REMOTE/$MAIN"
    ;;
  rebase)
    git rebase "$REMOTE/$MAIN"
    ;;
  merge)
    git merge --no-edit "$REMOTE/$MAIN"
    ;;
esac

echo ">> Fertig. HEAD: $(git log -1 --oneline)"
