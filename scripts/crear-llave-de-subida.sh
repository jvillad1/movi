#!/bin/bash
# Crea la llave de subida de Google Play (la «upload key») FUERA del repo y deja sus cuatro
# propiedades en ~/.gradle/gradle.properties para que `scripts/armar-para-play.sh` firme el AAB.
#
# Uso (una sola vez, en la Mac del dueño):
#   ./scripts/crear-llave-de-subida.sh
#
# Qué hace:
#   - Genera ~/.movi/llave-de-subida.jks con keytool: RSA 4096, validez de 30 años (la guía de
#     Android recomienda 25 años o más para una llave de firma), alias `movi-subida`.
#   - La contraseña es aleatoria (openssl rand) y la misma para el almacén y la llave (PKCS12 no
#     admite dos distintas).
#   - Escribe MOVI_UPLOAD_STORE_FILE, MOVI_UPLOAD_STORE_PASSWORD, MOVI_UPLOAD_KEY_ALIAS y
#     MOVI_UPLOAD_KEY_PASSWORD en ~/.gradle/gradle.properties SOLO si ninguna existe ya.
#   - Imprime cómo respaldarla y el SHA-256 del certificado (Play Console lo muestra en
#     «Integridad de la app» para comprobar que es la misma).
#
# Qué NO hace, a propósito:
#   - **Nunca pisa una llave existente.** Si el .jks ya está, sale con error: perder la llave de
#     subida no es el fin del mundo (Play deja cambiarla con un trámite), pero reemplazarla sin
#     querer sí deja al dueño sin poder subir hasta que Google la reemplace.
#   - No sube nada a ningún lado ni toca el repo.
#
# Con «Firma de apps de Play» (Play App Signing, obligatoria para apps nuevas) la llave que firma lo
# que llega a los teléfonos la guarda Google. Esta es solo la que prueba que el AAB lo subiste tú.
set -euo pipefail

DIR="$HOME/.movi"
JKS="$DIR/llave-de-subida.jks"
ALIAS="movi-subida"
PROPS="$HOME/.gradle/gradle.properties"
NOMBRES=(MOVI_UPLOAD_STORE_FILE MOVI_UPLOAD_STORE_PASSWORD MOVI_UPLOAD_KEY_ALIAS MOVI_UPLOAD_KEY_PASSWORD)

if [ -e "$JKS" ]; then
  echo "ERROR: ya existe $JKS. No la piso: si de verdad quieres otra, muévela tú a otro lado primero." >&2
  exit 1
fi

if [ -f "$PROPS" ]; then
  for n in "${NOMBRES[@]}"; do
    if grep -q "^${n}=" "$PROPS"; then
      echo "ERROR: $PROPS ya tiene $n. No escribo encima: revisa a qué llave apunta antes de crear otra." >&2
      exit 1
    fi
  done
fi

command -v keytool >/dev/null 2>&1 || { echo "ERROR: falta keytool (viene con el JDK)." >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "ERROR: falta openssl." >&2; exit 1; }

mkdir -p "$DIR"
chmod 700 "$DIR"

# 32 bytes aleatorios en base64 sin los caracteres que molestan en un .properties.
CLAVE=$(openssl rand -base64 32 | tr -d '/+=\n')

# El nombre del certificado no lleva datos personales: Play no lo muestra y no hace falta.
keytool -genkeypair \
  -keystore "$JKS" \
  -storetype PKCS12 \
  -storepass "$CLAVE" \
  -keypass "$CLAVE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10950 \
  -dname "CN=Movi, OU=Subida a Google Play, C=CO" \
  >/dev/null 2>&1
chmod 600 "$JKS"

mkdir -p "$(dirname "$PROPS")"
touch "$PROPS"
chmod 600 "$PROPS"
{
  echo ""
  echo "# Movi · llave de subida de Google Play (scripts/crear-llave-de-subida.sh, $(date +%Y-%m-%d))."
  echo "MOVI_UPLOAD_STORE_FILE=$JKS"
  echo "MOVI_UPLOAD_STORE_PASSWORD=$CLAVE"
  echo "MOVI_UPLOAD_KEY_ALIAS=$ALIAS"
  echo "MOVI_UPLOAD_KEY_PASSWORD=$CLAVE"
} >> "$PROPS"

SHA=$(keytool -list -v -keystore "$JKS" -storepass "$CLAVE" -alias "$ALIAS" 2>/dev/null \
  | awk -F': ' '/SHA256:/ {print $2; exit}')

cat <<FIN

Listo: la llave de subida quedó en
  $JKS   (permisos 600, fuera del repo)
y sus cuatro propiedades en
  $PROPS

SHA-256 del certificado:
  $SHA

RESPÁLDALA HOY, en dos lugares que no sean esta Mac:
  1. El archivo $JKS (cópialo a un gestor de contraseñas que guarde archivos, o a una
     memoria USB que guardes aparte).
  2. La contraseña: está en $PROPS, en la línea MOVI_UPLOAD_STORE_PASSWORD. Guárdala en tu
     gestor de contraseñas junto al archivo.
Sin el archivo Y la contraseña no se puede firmar una actualización. Si se pierden, Play deja
pedir un cambio de llave de subida (Play Console → Integridad de la app), pero tarda días.

Nunca la subas a git ni a Drive en claro.
FIN
