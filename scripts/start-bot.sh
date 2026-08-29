#!/usr/bin/env bash
# Startet den Bot: erst Main pullen, dann bauen, dann das Jar starten.
#   ./scripts/start-bot.sh
#   SLOTH_SKIP_UPDATE=1 ./scripts/start-bot.sh   # ohne Pull starten
set -euo pipefail
cd "$(dirname "$0")/.."

if [ "${SLOTH_SKIP_UPDATE:-0}" = "1" ]; then
  echo ">> Update uebersprungen (SLOTH_SKIP_UPDATE=1)."
else
  # Fehlschlag (offline, Konflikt, ...) darf den Start nicht verhindern.
  bash scripts/update-main.sh || echo "!! Update fehlgeschlagen - starte mit dem aktuellen lokalen Stand."
fi

echo ">> Baue Jar..."
./gradlew --quiet shadowJar

JAR="$(ls -t build/libs/*-all.jar build/libs/*.jar 2>/dev/null | head -n1)"
[ -n "$JAR" ] || { echo "Kein Jar in build/libs gefunden." >&2; exit 1; }

echo ">> Starte $JAR"
exec java -jar "$JAR" "$@"
