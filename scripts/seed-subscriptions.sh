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
# Idempotente: consulta las suscripciones existentes y salta las que ya tienen ese nombre
# EN ESA MONEDA, normalizando el nombre igual que el server (POST /api/subscriptions arma
# `manual_<nombre normalizado>` y su clave única es (usuario, esa clave, moneda)). Se compara
# contra el nombre y no contra la `merchantKey`, a propósito: la fila que encontró el detector
# se llama `netflix` y la que escribe este script se llamaría `manual_netflix`, así que
# comparar claves dejaría pasar un duplicado del mismo cobro real sin que el server lo frene.
# Las DISMISSED no cuentan: son las que el dueño sacó a mano, el server las reemplaza al
# recrearlas, y saltarlas por ellas sería no sembrar algo que él pidió sembrar.
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

# **Un `python3` que de verdad corra.** En esta máquina el `python3` del PATH es el shim de
# Apple y aborta con «You have not agreed to the Xcode license agreements» (exit 69) sin
# ejecutar una línea. No alcanza con que el binario exista: hay que probarlo. `MOVI_PYTHON`
# permite forzar uno.
PYBIN=""
for candidato in "${MOVI_PYTHON:-}" python3 /opt/homebrew/bin/python3 /usr/local/bin/python3 \
                 /Applications/Xcode.app/Contents/Developer/usr/bin/python3; do
  [ -n "$candidato" ] || continue
  command -v "$candidato" >/dev/null 2>&1 || continue
  if "$candidato" -c "import json, subprocess" >/dev/null 2>&1; then PYBIN="$candidato"; break; fi
done
if [ -z "$PYBIN" ]; then
  echo "ERROR: no encontré un python3 que corra. Probá uno a mano:" >&2
  echo "       MOVI_PYTHON=/ruta/a/python3 $0 ${1:-}" >&2
  exit 1
fi

APPLY="$APPLY" BASE="$BASE" EXISTING="$EXISTING" ACCOUNTS="$ACCOUNTS" "$PYBIN" - "$SEED" <<'PY'
import json, os, re, subprocess, sys

apply_ = os.environ["APPLY"] == "1"
base = os.environ["BASE"]
token = os.environ["MOVI_TOKEN"]

# **La misma normalización que el server**, copiada de `manualMerchantKey` en
# SubscriptionRoutes.kt: minúsculas, todo lo que no sea a-z0-9 a "_", sin "_" en las puntas.
# El server arma con eso `manual_<clave>` y su unicidad es (usuario, esa clave, MONEDA).
#
# Se compara contra el NOMBRE de lo que ya existe, no contra su `merchantKey`: la fila que
# encontró el detector se llama `netflix` a secas y la de este script sería `manual_netflix`,
# así que comparar claves dejaría pasar un segundo registro del mismo cobro real —y el server
# tampoco lo frenaría, porque para él son claves distintas.
def clave(nombre):
    return re.sub(r"[^a-z0-9]+", "_", nombre.strip().lower()).strip("_") or "sub"

# Las DISMISSED afuera: son las que el dueño quitó a mano, el server las reemplaza al
# recrearlas (ver el delete del POST), y saltarlas por ellas sería no sembrar lo que pidió.
existing = {
    (clave(s["displayName"]), s["currency"])
    for s in json.loads(os.environ["EXISTING"])["subscriptions"]
    if s.get("status") != "DISMISSED"
}
mismo_nombre_otra_moneda = {k for k, _ in existing}
# Los nombres tal como se guardaron, para poder avisar de los parecidos-pero-no-iguales.
nombres_existentes = sorted(mismo_nombre_otra_moneda)

# Dos cuentas pueden llamarse igual —"Ahorros" en dos bancos es un caso soportado, ver
# AccountRoutes— y entonces el nombre no alcanza para elegir. Quedarse con la última en
# silencio sería atarle la suscripción a una cuenta al azar.
cuentas, ambiguas = {}, set()
for a in json.loads(os.environ["ACCOUNTS"]):
    if a["name"] in cuentas:
        ambiguas.add(a["name"])
    cuentas[a["name"]] = a["id"]

seed = json.load(open(sys.argv[1], encoding="utf-8"))

REQ = ["displayName", "amount", "currency", "dayOfMonth"]
listos, incompletos, saltados = [], [], []

for s in seed:
    if (clave(s["displayName"]), s.get("currency")) in existing:
        saltados.append(s["displayName"]); continue
    # `in (None, "")` no alcanza para los números: un `amount` o un `dayOfMonth` en 0 son tan
    # inservibles como uno ausente, y sin esto llegarían al server para volver como un 400.
    faltan = [f for f in REQ if s.get(f) in (None, "", 0)]
    (incompletos if faltan else listos).append((s, faltan))

for name in saltados:
    print(f"  ya existe    {name}")
for s, faltan in incompletos:
    print(f"  incompleto   {s['displayName']}  → faltan: {', '.join(faltan)}")

errores = 0
for s, _ in listos:
    # **Los dos casos en que un duplicado se cuela por la criba**, que no se saltan —los dos son
    # legítimos y el server los acepta— pero sí se dicen, porque son la única forma que tiene el
    # mismo cobro real de quedar anotado dos veces:
    #
    #   - mismo nombre, otra moneda: para el server son filas distintas;
    #   - nombre PARECIDO: «Netflix» contra un «Netflix Colombia» que ya está. Ahí las claves
    #     normalizadas no coinciden y nada lo frena, ni acá ni allá.
    k = clave(s["displayName"])
    if k in mismo_nombre_otra_moneda:
        print(f"  ⚠ ojo         {s['displayName']}: ya existe una con ese nombre en otra moneda")
    else:
        parecidas = [n for n in nombres_existentes if k != n and (k in n or n in k)]
        if parecidas:
            print(f"  ⚠ ojo         {s['displayName']}: ya existe «{parecidas[0]}», parecida pero "
                  f"no igual; si son el mismo cobro van a quedar duplicadas")
    nombre_cuenta = s.get("account")
    account_id = cuentas.get(nombre_cuenta) if nombre_cuenta else None
    if nombre_cuenta in ambiguas:
        print(f"  ⚠ cuenta ambigua  {s['displayName']}: hay más de una cuenta llamada "
              f"«{nombre_cuenta}»; se usaría una de ellas al azar")
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
    if not ok:
        errores += 1
    print(f"  {'CREADA' if ok else 'ERROR ' + code:12} {s['displayName']:20} {detalle}"
          + ("" if ok else f"  {out[0][:200]}"))

# El resumen se imprime SIEMPRE, y en --apply es el único lugar donde una siembra a medias se
# ve: un POST que falla no corta el resto (a propósito: lo que sí entró, entró), así que sin
# esta línea un «3 creadas, 2 ERROR» quedaba disperso entre las filas. Y el exit code lo
# acompaña, para que se note incluso sin leer.
print(f"\n  {len(listos) - errores} creadas · {errores} con error · "
      f"{len(incompletos)} incompletas · {len(saltados)} ya existen"
      if apply_ else
      f"\n  {len(listos)} por sembrar · {len(incompletos)} incompletas · {len(saltados)} ya existen")

if not apply_:
    print("  Esto fue un dry-run. Para sembrar de verdad: ./scripts/seed-subscriptions.sh --apply")
if errores:
    sys.exit(1)
PY
