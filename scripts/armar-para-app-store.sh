#!/bin/bash
# Arma el archivo (.xcarchive) de Movi para subirlo a App Store Connect.
#
# Uso:
#   MOVI_APPLE_TEAM_ID=XXXXXXXXXX ./scripts/armar-para-app-store.sh
#   MOVI_APPLE_TEAM_ID=XXXXXXXXXX ./scripts/armar-para-app-store.sh --sin-framework
#       (no vuelve a armar el XCFramework de release: usa el que ya está; sirve para reintentar
#        el archivo después de un problema de firma sin esperar otra vez el link de Kotlin)
#
# MOVI_APPLE_TEAM_ID es el «Team ID» de tu cuenta de Apple Developer (10 letras y números): está en
# developer.apple.com → Account → Membership details. Va solo en la variable de entorno, nunca en el
# repo. Antes, en Xcode → Settings → Accounts, agrega tu Apple ID: el archivo se firma con firma
# automática (-allowProvisioningUpdates crea el App ID com.jvillada.movi y los certificados).
#
# Qué hace, en orden:
#   1. Sin MOVI_APPLE_TEAM_ID (o con uno que no tiene la forma de un Team ID) dice qué falta y sale
#      con 1 sin armar ni archivar nada.
#   2. Arma el XCFramework de RELEASE (`:shared:assembleComposeAppReleaseXCFramework`) y, junto a él,
#      las fuentes de Movi (`:shared:recursosDeComposeParaIphoneRelease`, que deja los recursos de
#      Compose en shared/build/XCFrameworks/release/compose-resources). El link optimizado de
#      Kotlin/Native pide mucha memoria: cierra todo lo demás antes (navegador, Android Studio, otros
#      Gradle). Tarda bastante en esta Mac.
#   3. `xcodebuild archive` en Release para «Any iOS Device» (arm64). La configuración Release del
#      proyecto toma el framework de shared/build/XCFrameworks/release (Debug, el de debug), y su fase
#      «Copiar las fuentes de Movi» copia las fuentes a la app (vuelta 115).
#   4. Comprueba que el archivo trae el manifiesto de privacidad y las cinco Figtree donde Compose las
#      busca, imprime dónde quedó y cómo subirlo. Este script NO sube nada.
set -euo pipefail
cd "$(dirname "$0")/.."

SIN_FRAMEWORK=0
while [ $# -gt 0 ]; do
  case "$1" in
    --sin-framework) SIN_FRAMEWORK=1; shift ;;
    -h|--help) sed -n '2,27p' "$0"; exit 0 ;;
    *) echo "No conozco la opción $1 (usa --help)." >&2; exit 1 ;;
  esac
done

# ── 1 · El equipo ─────────────────────────────────────────────────────────────────────────────
EQUIPO="${MOVI_APPLE_TEAM_ID:-}"
if [ -z "$EQUIPO" ]; then
  cat >&2 <<'TEXTO'
Falta MOVI_APPLE_TEAM_ID: el Team ID de tu cuenta de Apple Developer (10 letras y números).
Lo ves en developer.apple.com → Account → Membership details. Corre así:

  MOVI_APPLE_TEAM_ID=XXXXXXXXXX ./scripts/armar-para-app-store.sh

No se armó ni se archivó nada.
TEXTO
  exit 1
fi
if ! [[ "$EQUIPO" =~ ^[A-Z0-9]{10}$ ]]; then
  echo "MOVI_APPLE_TEAM_ID no tiene la forma de un Team ID (10 letras mayúsculas y números). No se armó nada." >&2
  exit 1
fi
command -v xcodebuild >/dev/null || { echo "No encuentro xcodebuild: instala Xcode." >&2; exit 1; }

PROYECTO=iosApp/iosApp.xcodeproj
XCF=shared/build/XCFrameworks/release/ComposeApp.xcframework
RECURSOS=shared/build/XCFrameworks/release/compose-resources
ARCHIVO=build/ios/Movi.xcarchive

# ── 2 · El framework de release ───────────────────────────────────────────────────────────────
if [ "$SIN_FRAMEWORK" = 0 ]; then
  echo "Armando el XCFramework de release. Cierra todo lo demás: el link optimizado usa mucha memoria."
  # movi pide el JetBrains Runtime 21 para el daemon de Gradle (ver CLAUDE.md, «Machine toolchain»).
  JBR=/usr/local/share/jbrsdk-21/Contents/Home
  if [ -z "${JAVA_HOME:-}" ] && [ -d "$JBR" ]; then export JAVA_HOME="$JBR"; fi
  ./gradlew :shared:assembleComposeAppReleaseXCFramework :shared:recursosDeComposeParaIphoneRelease \
    -Dorg.gradle.workers.max=1 -Dorg.gradle.jvmargs="-Xmx${MOVI_GRADLE_XMX:-8g}"
fi
if [ ! -d "$XCF/ios-arm64" ]; then
  echo "No está $XCF/ios-arm64 (el framework para iPhone). Sin él no se puede archivar." >&2
  exit 1
fi
if [ ! -d "$RECURSOS/composeResources" ]; then
  echo "No están las fuentes de Movi en $RECURSOS. Corre ./gradlew :shared:recursosDeComposeParaIphoneRelease." >&2
  exit 1
fi

# ── 3 · El archivo ────────────────────────────────────────────────────────────────────────────
mkdir -p "$(dirname "$ARCHIVO")"
xcodebuild archive \
  -project "$PROYECTO" \
  -scheme iosApp \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVO" \
  -allowProvisioningUpdates \
  DEVELOPMENT_TEAM="$EQUIPO"

# ── 4 · Qué sigue ─────────────────────────────────────────────────────────────────────────────
APP="$ARCHIVO/Products/Applications/iosApp.app"
VERSION=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Info.plist" 2>/dev/null || echo "?")
BUILD=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$APP/Info.plist" 2>/dev/null || echo "?")
[ -f "$APP/PrivacyInfo.xcprivacy" ] || { echo "El archivo no trae PrivacyInfo.xcprivacy: revisa el proyecto." >&2; exit 1; }
# Las fuentes, en la ruta exacta donde Compose las busca (`NSBundle.mainBundle.resourcePath +
# "/compose-resources"` + la ruta del recurso). Sin ellas la app abre con la letra del sistema.
for PESO in regular medium semibold bold extrabold; do
  FUENTE="$APP/compose-resources/composeResources/com.jvillada.movi.resources/font/figtree_$PESO.ttf"
  [ -f "$FUENTE" ] || { echo "El archivo no trae $FUENTE: revisa la fase «Copiar las fuentes de Movi»." >&2; exit 1; }
done
cat <<TEXTO

Listo: $ARCHIVO (versión $VERSION, build $BUILD).
Para subirlo: open "$ARCHIVO" → Organizer → «Distribute App» → App Store Connect → Upload.
Una versión nueva necesita un CURRENT_PROJECT_VERSION mayor que el último build subido.
TEXTO
