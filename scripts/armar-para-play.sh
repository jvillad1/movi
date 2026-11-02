#!/bin/bash
# Arma el AAB firmado de Movi para Google Play (canal `play`: sin SMS).
#
# Uso:
#   ./scripts/armar-para-play.sh                 # arma con la versión que ya está en el build
#   ./scripts/armar-para-play.sh --version 1.93  # antes sube versionName a 1.93 y versionCode en 1
#   ./scripts/armar-para-play.sh --sin-firmar    # ensayo: arma sin la llave (no sirve para subir)
#
# Qué verifica, en orden:
#   1. Que la llave de subida exista: las cuatro propiedades MOVI_UPLOAD_* en ~/.gradle/gradle.properties
#      (o como variables de entorno) y el .jks al que apuntan. Sin eso, nada (salvo --sin-firmar).
#   2. `:androidApp:bundlePlayRelease`, que ya corre:
#        - verificaElManifiestoDePlayRelease: el manifiesto fusionado no pide READ_SMS, RECEIVE_SMS,
#          SEND_SMS, RECEIVE_MMS ni el registro de llamadas (antes de empaquetar);
#        - verificaElDexDelAabDePlayRelease: el AAB define TODAS las clases de Movi que el build
#          compiló (lee `base/dex/classes*.dex` del AAB con dexdump; no hace falta bundletool).
#   3. Que el AAB salga firmado con la llave de subida (jarsigner) y con qué certificado.
#   4. Imprime versionCode, versionName, tamaño y ruta.
#
# El AAB queda en androidApp/build/outputs/bundle/playRelease/. Subirlo es a mano, en Play Console
# (ver docs/lanzamiento/google-play.md): este script no sube nada.
set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE_KTS=androidApp/build.gradle.kts
PROPS="$HOME/.gradle/gradle.properties"
SIN_FIRMAR=0
NUEVA_VERSION=""

while [ $# -gt 0 ]; do
  case "$1" in
    --version) NUEVA_VERSION="${2:-}"; shift 2 ;;
    --sin-firmar) SIN_FIRMAR=1; shift ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "No conozco la opción $1 (usa --help)." >&2; exit 1 ;;
  esac
done

# ── 1 · La llave ──────────────────────────────────────────────────────────────────────────────
prop() {
  # Primero ~/.gradle/gradle.properties, después el entorno: el mismo orden que el build.
  local v=""
  [ -f "$PROPS" ] && v=$(grep -E "^$1=" "$PROPS" | tail -1 | cut -d= -f2- || true)
  [ -z "$v" ] && v="${!1:-}"
  printf '%s' "$v"
}

if [ "$SIN_FIRMAR" = 0 ]; then
  for n in MOVI_UPLOAD_STORE_FILE MOVI_UPLOAD_STORE_PASSWORD MOVI_UPLOAD_KEY_ALIAS MOVI_UPLOAD_KEY_PASSWORD; do
    if [ -z "$(prop "$n")" ]; then
      echo "ERROR: falta $n (en $PROPS o en el entorno). ¿Corriste scripts/crear-llave-de-subida.sh?" >&2
      exit 1
    fi
  done
  JKS=$(prop MOVI_UPLOAD_STORE_FILE)
  JKS="${JKS/#\~/$HOME}"
  if [ ! -f "$JKS" ]; then
    echo "ERROR: MOVI_UPLOAD_STORE_FILE apunta a $JKS, que no existe." >&2
    exit 1
  fi
else
  echo "AVISO: --sin-firmar. El AAB sale SIN firma: sirve para ensayar, no para subir a Play."
  # Que ninguna propiedad del entorno firme sin querer el ensayo.
  unset MOVI_UPLOAD_STORE_FILE MOVI_UPLOAD_STORE_PASSWORD MOVI_UPLOAD_KEY_ALIAS MOVI_UPLOAD_KEY_PASSWORD
fi

# ── La versión (solo si se pide) ────────────────────────────────────────────────────────────────
if [ -n "$NUEVA_VERSION" ]; then
  if ! [[ "$NUEVA_VERSION" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
    echo "ERROR: --version espera algo como 1.93 (recibí «$NUEVA_VERSION»)." >&2
    exit 1
  fi
  CODE_ANTES=$(grep -m1 'versionCode = ' "$GRADLE_KTS" | sed 's/[^0-9]//g')
  CODE_NUEVO=$((CODE_ANTES + 1))
  sed -i '' -E "s/^( *versionCode = )[0-9]+/\1${CODE_NUEVO}/; s/^( *versionName = )\"[^\"]*\"/\1\"${NUEVA_VERSION}\"/" "$GRADLE_KTS"
  echo "Versión: ${NUEVA_VERSION} (versionCode ${CODE_ANTES} → ${CODE_NUEVO}). Acuérdate de commitearla."
fi

VERSION=$(grep -m1 'versionName = ' "$GRADLE_KTS" | sed 's/.*"\(.*\)".*/\1/')
CODE=$(grep -m1 'versionCode = ' "$GRADLE_KTS" | sed 's/[^0-9]//g')

# ── 2 · El AAB, con las verificaciones del build ────────────────────────────────────────────────
echo "Armando el AAB de Play ${VERSION} (${CODE}), commit $(git rev-parse --short HEAD)…"
LOG=$(mktemp -t movi-aab)
MARCA=$(mktemp -t movi-aab-marca)
EXTRA=()
if [ "$SIN_FIRMAR" = 1 ]; then
  # Vacías en la línea de comandos le ganan a ~/.gradle/gradle.properties: el ensayo nunca firma.
  EXTRA=(-PMOVI_UPLOAD_STORE_FILE= -PMOVI_UPLOAD_STORE_PASSWORD= -PMOVI_UPLOAD_KEY_ALIAS= -PMOVI_UPLOAD_KEY_PASSWORD=)
fi
if ! ./gradlew :androidApp:bundlePlayRelease --console=plain ${EXTRA[@]+"${EXTRA[@]}"} >"$LOG" 2>&1; then
  grep -E "verificaEl|FAILURE|What went wrong|ERROR|error:|> " "$LOG" | tail -30 >&2 || true
  echo "ERROR: el build falló. Log completo: $LOG" >&2
  exit 1
fi
grep -E "clases de Movi|manifiesto fusionado" "$LOG" || true
AAB=androidApp/build/outputs/bundle/playRelease/androidApp-play-release.aab
if [ ! -f "$AAB" ] || [ ! "$AAB" -nt "$MARCA" ]; then
  echo "ERROR: el build no dejó un AAB nuevo en $AAB. Log: $LOG" >&2
  exit 1
fi
rm -f "$MARCA"

# ── 3 · La firma ────────────────────────────────────────────────────────────────────────────────
# jarsigner y keytool vienen con el JDK: si no están en el PATH, los del JAVA_HOME.
herramienta() { command -v "$1" 2>/dev/null || echo "${JAVA_HOME:-/usr}/bin/$1"; }
if [ "$SIN_FIRMAR" = 0 ]; then
  if ! "$(herramienta jarsigner)" -verify "$AAB" >/dev/null 2>&1; then
    echo "ERROR: el AAB no quedó firmado. Revisa las propiedades MOVI_UPLOAD_* y la contraseña." >&2
    exit 1
  fi
  echo "Firma: $("$(herramienta keytool)" -printcert -jarfile "$AAB" 2>/dev/null | awk -F': ' '/SHA256:/ {print "SHA-256 " $2; exit}')"
fi

# ── 4 · Lo que salió ───────────────────────────────────────────────────────────────────────────
BYTES=$(stat -f%z "$AAB")
printf '\nlisto: %s\n  versionName %s · versionCode %s · %.1f MB\n' "$AAB" "$VERSION" "$CODE" "$(echo "scale=2; $BYTES/1048576" | bc)"
[ "$SIN_FIRMAR" = 1 ] && echo "  (sin firmar: NO lo subas a Play)"
echo "Súbelo a mano en Play Console → Prueba interna (ver docs/lanzamiento/google-play.md)."
