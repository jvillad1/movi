#!/bin/bash
# Siembra suscripciones en Movi desde server/movi-data/subscriptions-seed.json.
#
# Mismo contrato que seed-credits.sh, y por los mismos motivos: el token NUNCA se pasa
# como argumento (quedaría en el historial del shell), se lee de MOVI_TOKEN.
#
# Cómo obtener el token:
#   1. Abre la PWA (https://movi-project-production.up.railway.app) e inicia sesión.
#   2. DevTools → Application → Local Storage → copia el valor de `auth_token`.
#   3. export MOVI_TOKEN='...'      (con el espacio inicial, así zsh no lo guarda en el historial)
#
# Uso:
#    export MOVI_TOKEN='...'
#   ./scripts/seed-subscriptions.sh              # dry-run: muestra qué se sembraría
#   ./scripts/seed-subscriptions.sh --apply      # siembra de verdad
#
# El JSON de datos NO se versiona (movi-data/ está en .gitignore: ahí van montos reales).
# Formato — una lista de objetos con estas claves:
#
#   [
#     {
#       "displayName": "Netflix",          # obligatorio
#       "amount": 44900,                   # obligatorio · COP en pesos, USD en dólares ENTEROS
#       "currency": "COP",                 # obligatorio · "COP" | "USD"
#       "dayOfMonth": 19,                  # obligatorio · 1..31
#       "periodicidad": "MENSUAL",         # opcional · "MENSUAL" (default) | "ANUAL"
#       "account": "Master Black 3684",    # opcional · NOMBRE de la cuenta, no su id
#       "fuente": "Extracto … 19-jul"      # opcional · no viaja: es para quien lea el archivo
#     }
#   ]
#
# `amount` es el cobro REAL, no el prorrateado: un anual de $369.900 va como 369900 con
# periodicidad ANUAL, nunca ya dividido en doce (ver Subscription.amount). Y no tiene
# centavos: un cobro de US$29,17 hay que anotarlo como 29.
#
# Idempotente: consulta las suscripciones existentes y salta las que ya tienen ese nombre.
# La cuenta se nombra en el JSON y se resuelve contra /api/accounts al correr — un id
# pegado a mano en un archivo se pudre en cuanto se recrea una cuenta, y el nombre es lo
# que el dueño de verdad reconoce. Si el nombre no resuelve, la suscripción se siembra
# igual SIN cuenta: `accountId` null es un valor legítimo del modelo (ver Subscription),
# y perder la cuenta es mucho menos malo que no registrar el cobro.
set -euo pipefail

BASE="${MOVI_BASE_URL:-https://movi-project-production.up.railway.app}"
SEED="$(dirname "$0")/../server/movi-data/subscriptions-seed.json"
APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

if [ -z "${MOVI_TOKEN:-}" ]; then
  echo "ERROR: falta MOVI_TOKEN. Ver las instrucciones en la cabecera de este script." >&2
  exit 1
fi
[ -f "$SEED" ] || { echo "ERROR: no existe $SEED" >&2; exit 1; }

EXISTING=$(curl -sf -H "Authorization: Bearer $MOVI_TOKEN" "$BASE/api/subscriptions") || {
  echo "ERROR: no pude leer $BASE/api/subscriptions (¿token vencido?)" >&2; exit 1; }
ACCOUNTS=$(curl -sf -H "Authorization: Bearer $MOVI_TOKEN" "$BASE/api/accounts") || {
  echo "ERROR: no pude leer $BASE/api/accounts" >&2; exit 1; }

APPLY="$APPLY" BASE="$BASE" EXISTING="$EXISTING" ACCOUNTS="$ACCOUNTS" python3 - "$SEED" <<'PY'
import json, os, subprocess, sys

apply_ = os.environ["APPLY"] == "1"
base = os.environ["BASE"]
token = os.environ["MOVI_TOKEN"]

# El choque se mide como lo mide el server (POST /api/subscriptions): por nombre
# normalizado. Acá alcanza con comparar en minúsculas y sin espacios de sobra — el
# server tiene la última palabra y devuelve 409 si se le escapa algo a esta criba.
def clave(nombre):
    return " ".join(nombre.split()).lower()

existing = {clave(s["displayName"]) for s in json.loads(os.environ["EXISTING"])["subscriptions"]}
cuentas = {a["name"]: a["id"] for a in json.loads(os.environ["ACCOUNTS"])}
seed = json.load(open(sys.argv[1], encoding="utf-8"))

REQ = ["displayName", "amount", "currency", "dayOfMonth"]
listos, incompletos, saltados = [], [], []

for s in seed:
    if clave(s["displayName"]) in existing:
        saltados.append(s["displayName"]); continue
    faltan = [f for f in REQ if s.get(f) in (None, "")]
    (incompletos if faltan else listos).append((s, faltan))

for name in saltados:
    print(f"  ya existe    {name}")
for s, faltan in incompletos:
    print(f"  incompleto   {s['displayName']}  → faltan: {', '.join(faltan)}")

for s, _ in listos:
    nombre_cuenta = s.get("account")
    account_id = cuentas.get(nombre_cuenta) if nombre_cuenta else None
    if nombre_cuenta and account_id is None:
        print(f"  ⚠ sin cuenta  {s['displayName']}: no existe una cuenta llamada «{nombre_cuenta}»")
    body = {
        "displayName": s["displayName"],
        "amount": int(s["amount"]),
        "currency": s["currency"],
        "dayOfMonth": int(s["dayOfMonth"]),
        "periodicidad": s.get("periodicidad", "MENSUAL"),
    }
    if account_id:
        body["accountId"] = account_id

    simbolo = "US$" if s["currency"] == "USD" else "$"
    cada = "al año" if body["periodicidad"] == "ANUAL" else "al mes"
    detalle = f"{simbolo}{body['amount']:,} {cada} · día {body['dayOfMonth']} · {nombre_cuenta or 'sin cuenta'}"

    if not apply_:
        print(f"  [dry-run]    {s['displayName']:20} {detalle}")
        continue
    out = subprocess.run(
        ["curl", "-s", "-w", "\n%{http_code}", "-X", "POST", f"{base}/api/subscriptions",
         "-H", f"Authorization: Bearer {token}", "-H", "Content-Type: application/json",
         "-d", json.dumps(body)],
        capture_output=True, text=True).stdout.rsplit("\n", 1)
    code = out[-1]
    ok = code in ("200", "201")
    print(f"  {'CREADA' if ok else 'ERROR ' + code:12} {s['displayName']:20} {detalle}"
          + ("" if ok else f"  {out[0][:200]}"))

if not apply_:
    print("\n  Esto fue un dry-run. Para sembrar de verdad: ./scripts/seed-subscriptions.sh --apply")
PY
