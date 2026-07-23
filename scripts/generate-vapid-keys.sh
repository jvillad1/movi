#!/bin/bash
# Genera un par de claves VAPID (P-256) en base64url para Web Push.
# Uso: ./scripts/generate-vapid-keys.sh  → imprime VAPID_PUBLIC_KEY y VAPID_PRIVATE_KEY
set -euo pipefail
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
openssl ecparam -name prime256v1 -genkey -noout -out "$TMP/vapid.pem"

PUB=$(openssl ec -in "$TMP/vapid.pem" -pubout -outform DER 2>/dev/null | tail -c 65 | base64 | tr '+/' '-_' | tr -d '=\n')
PRIV_HEX=$(openssl ec -in "$TMP/vapid.pem" -text -noout 2>/dev/null | awk '/priv:/{f=1;next} /pub:/{f=0} f' | tr -d ' :\n')
PRIV=$(python3 -c "import binascii,base64;print(base64.urlsafe_b64encode(binascii.unhexlify('${PRIV_HEX}'.zfill(64))).rstrip(b'=').decode())")

echo "VAPID_PUBLIC_KEY=$PUB"
echo "VAPID_PRIVATE_KEY=$PRIV"
