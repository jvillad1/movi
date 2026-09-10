#!/bin/bash
# Compila el APK de depuración, lo nombra de forma trazable y lo deja en el Drive de Movi.
#
# El nombre incluye versión, build y commit para que mirando el archivo se sepa
# exactamente qué código corre en el teléfono:
#   movi-debug-1.2.apk  (app + buildType + versión; «-sucio» si hay cambios sin commitear)
#
# Uso:
#   ./scripts/build-apk.sh            # compila y copia a Drive
#   ./scripts/build-apk.sh --no-copy  # solo compila y renombra en build/
#   ./scripts/build-apk.sh --prueba   # lo mismo, pero con nombre de PRUEBA
#
# `--prueba` existe porque en Drive conviven dos cosas distintas y hasta acá se llamaban
# igual: el APK que el dueño usa todos los días y el que se le pasa para probar una rama
# que todavía no está en master. Con el mismo nombre, el segundo pisaba al primero y no
# había forma de volver — «el último» dejaba de significar «el bueno».
#
#   productivo:  movi-debug-1.19.apk
#   de prueba:   movi-prueba-1.19-pr183.apk   (o -<sha> si la rama no tiene PR abierto)
#
# El nombre de prueba dice QUÉ trae, no solo que es de prueba: mirando el archivo se sabe
# qué PR hay adentro, que es justo lo que se está por probar.
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

# Esquema elegido por el dueño (2026-08-20): app + buildType + versión. El commit ya no va en
# el nombre — queda impreso abajo al compilar, así la trazabilidad vive en el log y en git.
BUILD_TYPE="debug"
if [ "${1:-}" = "--prueba" ]; then
  # De qué rama sale, dicho como lo entiende el dueño: el número del PR si hay uno abierto,
  # y si no el commit. `gh` puede no estar o no contestar; el sha siempre está.
  PR=$(gh pr view --json number -q .number 2>/dev/null || true)
  QUE_TRAE="${PR:+pr$PR}"
  QUE_TRAE="${QUE_TRAE:-$SHA}"
  NAME="movi-prueba-${VERSION}-${QUE_TRAE}${DIRTY}.apk"
else
  NAME="movi-${BUILD_TYPE}-${VERSION}${DIRTY}.apk"
fi

echo "Compilando ${NAME}… (commit ${SHA})"
./gradlew :androidApp:assembleDebug -q
SRC=androidApp/build/outputs/apk/debug/androidApp-debug.apk
OUT="androidApp/build/outputs/apk/debug/${NAME}"
cp "$SRC" "$OUT"
printf 'listo: %s (%.1f MB)\n' "$OUT" "$(echo "scale=2; $(stat -f%z "$OUT")/1048576" | bc)"

[ "${1:-}" = "--no-copy" ] && exit 0
[ "${2:-}" = "--no-copy" ] && exit 0

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
