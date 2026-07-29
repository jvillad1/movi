#!/bin/bash
# Siembra créditos reales en Movi desde server/movi-data/credits-seed.json.
#
# El token NUNCA se pasa como argumento (quedaría en el historial del shell): se lee
# de la variable de entorno MOVI_TOKEN.
#
# Cómo obtener el token:
#   1. Abre la PWA (https://movi-project-production.up.railway.app) e inicia sesión.
#   2. DevTools → Application → Local Storage → copia el valor de `auth_token`.
#   3. export MOVI_TOKEN='...'      (con el espacio inicial, así zsh no lo guarda en el historial)
#
# Uso:
#    export MOVI_TOKEN='...'
#   ./scripts/seed-credits.sh              # dry-run: muestra qué se sembraría
#   ./scripts/seed-credits.sh --apply      # siembra de verdad
#
# Idempotente: consulta los créditos existentes y salta los que ya tienen ese nombre.
# Las entradas con campos obligatorios en null se reportan como incompletas y se saltan.
set -euo pipefail

BASE="${MOVI_BASE_URL:-https://movi-project-production.up.railway.app}"
SEED="$(dirname "$0")/../server/movi-data/credits-seed.json"
APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

if [ -z "${MOVI_TOKEN:-}" ]; then
  echo "ERROR: falta MOVI_TOKEN. Ver las instrucciones en la cabecera de este script." >&2
  exit 1
fi
[ -f "$SEED" ] || { echo "ERROR: no existe $SEED" >&2; exit 1; }

EXISTING=$(curl -sf -H "Authorization: Bearer $MOVI_TOKEN" "$BASE/api/credits") || {
  echo "ERROR: no pude leer $BASE/api/credits (¿token vencido?)" >&2; exit 1; }

APPLY="$APPLY" BASE="$BASE" EXISTING="$EXISTING" python3 - "$SEED" <<'PY'
import json, os, subprocess, sys

apply_ = os.environ["APPLY"] == "1"
base = os.environ["BASE"]
existing = {c["account"]["name"] for c in json.loads(os.environ["EXISTING"])}
token = os.environ["MOVI_TOKEN"]
seed = json.load(open(sys.argv[1], encoding="utf-8"))

# Solo estos cuatro mueven algo en la app: currentDebt es la deuda que se muestra,
# principal alimenta la barra de % pagado, e installment + dayOfMonth generan los
# recordatorios. rateEa / termMonths / startDate se guardan y se muestran, pero no
# entran en ningún cálculo — así que no bloquean la siembra.
REQ = ["currentDebt", "principal", "installment", "dayOfMonth"]
listos, incompletos, saltados = [], [], []

for c in seed:
    if c["name"] in existing:
        saltados.append(c["name"]); continue
    faltan = [f for f in REQ if c.get(f) in (None, "")]
    (incompletos if faltan else listos).append((c, faltan))

for name in saltados:
    print(f"  ya existe   {name}")
for c, faltan in incompletos:
    print(f"  incompleto  {c['name']}  → faltan: {', '.join(faltan)}")

for c, _ in listos:
    # Los informativos que falten se mandan vacíos y se anotan, en vez de inventarles
    # un valor: un 0 visible es honesto, un plazo fabricado no.
    sin_dato = [f for f in ("rateEa", "termMonths", "startDate") if c.get(f) in (None, "")]
    nota = c.get("notes")
    if sin_dato:
        aviso = "faltan: " + ", ".join(sin_dato)
        nota = f"{nota} · {aviso}" if nota else aviso
    body = {
        "name": c["name"],
        "initialDebt": int(c["currentDebt"]),
        "terms": {
            "accountId": "", "bank": c["bank"], "principal": int(c["principal"]),
            "rateEa": float(c.get("rateEa") or 0.0), "termMonths": int(c.get("termMonths") or 0),
            "installment": int(c["installment"]), "dayOfMonth": int(c["dayOfMonth"]),
            "startDate": c.get("startDate") or "", "notes": nota,
        },
    }
    if not apply_:
        print(f"  [dry-run]   {c['name']}  deuda ${body['initialDebt']:,}  cuota ${body['terms']['installment']:,}  día {body['terms']['dayOfMonth']}")
        continue
    out = subprocess.run(
        ["curl", "-s", "-w", "\n%{http_code}", "-X", "POST", f"{base}/api/credits",
         "-H", f"Authorization: Bearer {token}", "-H", "Content-Type: application/json",
         "-d", json.dumps(body)],
        capture_output=True, text=True).stdout.rsplit("\n", 1)
    code = out[-1]
    print(f"  {'CREADO' if code == '201' else 'ERROR ' + code:11} {c['name']}" + ("" if code == "201" else f"  {out[0][:200]}"))

print(f"\n{len(listos)} listos · {len(incompletos)} incompletos · {len(saltados)} ya existen")
if listos and not apply_:
    print("Corre con --apply para sembrarlos.")
PY
