#!/bin/bash
# Compila el APK de depuración, lo nombra de forma trazable y lo deja en el Drive de Movi.
#
# El nombre incluye versión, build y commit para que mirando el archivo se sepa
# exactamente qué código corre en el teléfono:
#   movi-sensor-1.2-build3-47634b4.apk
#
# Uso:
#   ./scripts/build-apk.sh          # compila y copia a Drive
#   ./scripts/build-apk.sh --no-copy  # solo compila y renombra en build/
set -euo pipefail
cd "$(dirname "$0")/.."

DRIVE="$HOME/Library/CloudStorage/GoogleDrive-jvillad1@gmail.com"
# `|| true`: si macOS bloquea el acceso a CloudStorage (TCC), ls falla y con
# `set -e` + pipefail el script moriría acá sin decir nada. Queremos llegar al
# mensaje del final, que explica cómo copiarlo a mano.
DEST=$(ls -d "$DRIVE"/*/Work/Movi 2>/dev/null | head -1 || true)

VERSION=$(grep -m1 'versionName' androidApp/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
BUILD=$(grep -m1 'versionCode' androidApp/build.gradle.kts | sed 's/[^0-9]//g')
SHA=$(git rev-parse --short HEAD)
DIRTY=""
[ -n "$(git status --porcelain -- androidApp shared core server 2>/dev/null)" ] && DIRTY="-sucio"

NAME="movi-sensor-${VERSION}-build${BUILD}-${SHA}${DIRTY}.apk"

echo "Compilando ${NAME}…"
./gradlew :androidApp:assembleDebug -q
SRC=androidApp/build/outputs/apk/debug/androidApp-debug.apk
OUT="androidApp/build/outputs/apk/debug/${NAME}"
cp "$SRC" "$OUT"
printf 'listo: %s (%.1f MB)\n' "$OUT" "$(echo "scale=2; $(stat -f%z "$OUT")/1048576" | bc)"

[ "${1:-}" = "--no-copy" ] && exit 0

if [ -z "$DEST" ]; then
  echo "AVISO: no encontré la carpeta de Drive. ¿Está montada la cuenta jvillad1@gmail.com?" >&2
  exit 1
fi
# Puede fallar por TCC (privacidad de macOS): el proceso necesita permiso para
# escribir en CloudStorage. Si falla, se corre a mano o se concede Acceso a disco
# completo a la app desde la que se ejecuta esto.
if cp "$OUT" "$DEST/$NAME" 2>/dev/null; then
  echo "subido a Drive: Work/Movi/$NAME"
else
  echo "NO pude escribir en Drive (permisos de macOS). Copialo con:" >&2
  echo "  cp \"$PWD/$OUT\" \"$DEST/\"" >&2
  exit 1
fi
