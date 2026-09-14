#!/bin/bash
# Despliega master a producción en Railway, de la única forma que no sorprende.
#
# El auto-deploy de Railway está apagado (ahorro, 2026-09-13), así que se despliega a mano. Hacerlo
# a mano falló de tres maneras distintas en dos días, y este script cierra las tres:
#
#  1. **El servicio equivocado.** La carpeta principal estaba enlazada en el CLI al servicio
#     Postgres: un `railway up` sin `--service` le subía el código de Movi a la base de datos y el
#     build fallaba (correo «Build failed · Service: Postgres»). Acá el servicio va SIEMPRE explícito.
#  2. **El código equivocado.** `railway up` desde un git worktree sube la carpeta principal, no el
#     worktree: dos despliegues «SUCCESS» mandaron el checkout de otra sesión. Acá se sube una
#     exportación de `origin/master` hecha con `git archive`, en una carpeta sin git.
#  3. **Creer que salió.** SUCCESS y `/version` no prueban nada: `/version` lee una variable que uno
#     mismo pone. Acá se verifica que la web servida traiga las fuentes (un TTF, no el index.html).
#
# Uso: ./scripts/desplegar.sh            # despliega origin/master y espera
#      ./scripts/desplegar.sh --sin-esperar
set -euo pipefail

PROYECTO="a0e6fd4a-60e8-479b-bf11-70f10a8991b9"
SERVICIO="movi-project"
ENTORNO="production"
URL="https://movi-project-production.up.railway.app"
FUENTE="$URL/composeResources/com.jvillada.movi.resources/font/space_grotesk_regular.ttf"

cd "$(dirname "$0")/.."
git fetch -q origin master
SHA=$(git rev-parse origin/master)
echo "── Despliego origin/master = ${SHA:0:8}: $(git log -1 --format=%s origin/master)"

# Exportación sin git: lo que se sube es exactamente el commit, no una carpeta de trabajo.
EXPORT=$(mktemp -d "${TMPDIR:-/tmp}/movi-despliegue.XXXXXX")
trap 'rm -rf "$EXPORT"' EXIT
git archive origin/master | tar -x -C "$EXPORT"

cd "$EXPORT"
railway link -p "$PROYECTO" -e "$ENTORNO" -s "$SERVICIO" >/dev/null
# --skip-deploys: sin él, setear la variable dispara OTRO despliegue y se paga doble.
railway variable set "MOVI_COMMIT_SHA=$SHA" --skip-deploys -s "$SERVICIO" >/dev/null
SALIDA=$(railway up --service "$SERVICIO" --environment "$ENTORNO" --detach 2>&1)
echo "$SALIDA"
# El id de ESTE despliegue, sacado del enlace a los logs. Mirar «la primera fila» de la lista
# puede leer el despliegue anterior y dar un SUCCESS que no es el nuestro.
ID=$(echo "$SALIDA" | grep -o 'id=[0-9a-f-]\{36\}' | head -1 | cut -d= -f2)
[ -n "$ID" ] || { echo "══ No encontré el id del despliegue en la salida de railway up"; exit 1; }

if [ "${1:-}" = "--sin-esperar" ]; then echo "── Subido; no espero el build."; exit 0; fi

echo "── Esperando el build (~20 min)…"
for _ in $(seq 1 80); do
    fila=$(railway deployment list --service "$SERVICIO" 2>/dev/null | grep "$ID" || true)
    case "$fila" in
        *SUCCESS*) break ;;
        *FAILED*|*CRASHED*) echo "══ El build falló: $fila"; echo "   Producción sigue con el despliegue anterior."; exit 1 ;;
    esac
    sleep 30
done
case "$fila" in *SUCCESS*) ;; *) echo "══ Sigue sin terminar después de 40 min: $fila"; exit 1 ;; esac

version=$(curl -fsS "$URL/version" || true)
cabecera=$(curl -fsS "$FUENTE" | head -c 4 | od -An -tx1 | tr -d ' \n' || true)
echo "── /version: $version"
if [[ "$version" != *"$SHA"* ]]; then echo "══ /version no dice ${SHA:0:8}"; exit 1; fi
if [ "$cabecera" != "00010000" ]; then echo "══ La web no sirve las fuentes (llegó «$cabecera»): paquete viejo o roto"; exit 1; fi
echo "── Desplegado y verificado: ${SHA:0:8}"
