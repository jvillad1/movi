#!/bin/bash
# Compila el APK de depuración, lo nombra de forma trazable y lo deja en el Drive de Movi.
#
# El nombre incluye versión, build y commit para que mirando el archivo se sepa
# exactamente qué código corre en el teléfono:
#   movi-1.2-dea0d20.apk  (nombre de app + versión + commit; «-sucio» si hay cambios sin commitear)
#
# Uso:
#   ./scripts/build-apk.sh          # compila y copia a Drive
#   ./scripts/build-apk.sh --no-copy  # solo compila y renombra en build/
set -euo pipefail
cd "$(dirname "$0")/.."

# Se sube con rclone, NO copiando a la carpeta de Drive de escritorio: macOS (TCC)
# niega el acceso a ~/Library/CloudStorage desde el shell, y ese permiso no quedó
# concedido de forma estable ni siquiera tras autorizarlo una vez. rclone sube por
# la API de Drive, así que no toca el sistema de archivos y el bloqueo no aplica.
RCLONE_DEST="drive:Work/Movi"

VERSION=$(grep -m1 'versionName' androidApp/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
BUILD=$(grep -m1 'versionCode' androidApp/build.gradle.kts | sed 's/[^0-9]//g')
SHA=$(git rev-parse --short HEAD)
DIRTY=""
[ -n "$(git status --porcelain -- androidApp shared core server 2>/dev/null)" ] && DIRTY="-sucio"

# Sin «sensor» (reliquia de cuando el APK era solo la app del sensor de SMS) y sin
# «buildN» (el versionCode casi nunca cambia; el commit es la trazabilidad real).
NAME="movi-${VERSION}-${SHA}${DIRTY}.apk"

echo "Compilando ${NAME}…"
./gradlew :androidApp:assembleDebug -q
SRC=androidApp/build/outputs/apk/debug/androidApp-debug.apk
OUT="androidApp/build/outputs/apk/debug/${NAME}"
cp "$SRC" "$OUT"
printf 'listo: %s (%.1f MB)\n' "$OUT" "$(echo "scale=2; $(stat -f%z "$OUT")/1048576" | bc)"

[ "${1:-}" = "--no-copy" ] && exit 0

if ! command -v rclone >/dev/null 2>&1; then
  echo "AVISO: falta rclone. Instalalo con: brew install rclone" >&2
  exit 1
fi

echo "Subiendo a Drive ($RCLONE_DEST)…"
# Sin `| grep -v NOTICE` en la condición: grep devuelve 1 cuando no queda ninguna
# línea, así que una subida EXITOSA —cuya única salida es el NOTICE del client_id
# compartido de rclone— se leía como fallo. El código de salida lo tiene que dar
# rclone, no el filtro.
if rclone copy "$OUT" "$RCLONE_DEST" --stats-one-line 2>/dev/null; then
  # Verificar contra el remoto en vez de confiar en el código de salida: el tamaño
  # que reporta Drive tiene que coincidir con el local o la subida quedó a medias.
  REMOTE_SIZE=$(rclone lsl "$RCLONE_DEST/$NAME" 2>/dev/null | grep -v NOTICE | awk '{print $1}')
  LOCAL_SIZE=$(stat -f%z "$OUT")
  if [ "$REMOTE_SIZE" = "$LOCAL_SIZE" ]; then
    echo "subido y verificado: Work/Movi/$NAME ($LOCAL_SIZE bytes)"
  else
    echo "ERROR: el remoto reporta '$REMOTE_SIZE' bytes y el local $LOCAL_SIZE — subida incompleta" >&2
    exit 1
  fi
else
  echo "ERROR: rclone no pudo subir. Verificá el remoto con: rclone lsd drive:" >&2
  exit 1
fi
