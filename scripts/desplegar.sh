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
#     carpeta sin git armada para este commit.
#  3. **Creer que salió.** SUCCESS y `/version` no prueban nada: `/version` lee una variable que uno
#     mismo pone. Acá se verifica que la web servida traiga las fuentes (un TTF, no el index.html).
#
# **El JAR se arma en GitHub Actions (vuelta 69, 29-oct-2026).** Railway armaba todo con el
# `Dockerfile` de la raíz (wasm + fat JAR): 17 a 25 min contra un límite del plan de ~20, así que
# cerca de 1 de cada 2 builds moría con «DeadlineExceeded» y un despliegue pedía hasta 4
# reintentos. Ahora el workflow «Paquete para Railway» (`.github/workflows/paquete.yml`) arma el
# JAR con la web adentro, este script lo baja, verifica que sea de este commit, y sube a Railway
# solo `app.jar` + la imagen de runtime de `scripts/despliegue-runtime/` (que no compila nada).
# Todo lo demás —servicio explícito, `MOVI_COMMIT_SHA` con `--skip-deploys`, el id de ESTE
# despliegue, la espera y la verificación de `/version` y de la fuente— es igual que antes.
#
# **El respaldo**, `--desde-fuente`, hace lo de antes de la vuelta 69: `git archive` de
# origin/master y el `Dockerfile` completo armando todo en Railway. Sirve si GitHub Actions está
# caído o el paquete no se puede armar; vuelve a chocar con el límite de ~20 min.
#
# Uso: ./scripts/desplegar.sh                  # paquete de Actions + railway up del JAR, y espera
#      ./scripts/desplegar.sh --sin-esperar
#      ./scripts/desplegar.sh --desde-fuente    # respaldo: Railway arma todo desde el código
#      ./scripts/desplegar.sh --ensayo [--commit <sha>]
#                                               # todo menos railway link/variable/up: arma la
#                                               # carpeta de subida y dice qué lleva y cuánto pesa
set -euo pipefail

PROYECTO="a0e6fd4a-60e8-479b-bf11-70f10a8991b9"
SERVICIO="movi-project"
ENTORNO="production"
URL="https://movi-project-production.up.railway.app"
FUENTE="$URL/composeResources/com.jvillada.movi.resources/font/figtree_regular.ttf"
REPO="jvillad1/movi"
WORKFLOW="paquete.yml"
RUNTIME="scripts/despliegue-runtime"

MODO="paquete"      # paquete | fuente
ENSAYO=0
ESPERAR=1
COMMIT=""
while [ $# -gt 0 ]; do
    case "$1" in
        --sin-esperar) ESPERAR=0 ;;
        --desde-fuente) MODO="fuente" ;;
        --ensayo) ENSAYO=1 ;;
        --commit) COMMIT="${2:?--commit pide un sha}"; shift ;;
        *) echo "══ No conozco «$1». Uso: desplegar.sh [--sin-esperar] [--desde-fuente] [--ensayo [--commit <sha>]]"; exit 2 ;;
    esac
    shift
done
if [ -n "$COMMIT" ] && [ "$ENSAYO" = 0 ]; then
    echo "══ --commit solo vale con --ensayo: a producción va siempre origin/master."; exit 2
fi
if [ "$ENSAYO" = 1 ] && [ "$MODO" = "fuente" ]; then
    echo "══ --ensayo es del camino del paquete; --desde-fuente no tiene nada que ensayar."; exit 2
fi

cd "$(dirname "$0")/.."
RAIZ=$(pwd)
git fetch -q origin master
if [ -n "$COMMIT" ]; then
    SHA=$(git rev-parse --verify "$COMMIT^{commit}")
else
    SHA=$(git rev-parse origin/master)
fi
if [ "$ENSAYO" = 1 ]; then ACCION="Ensayo con"; else ACCION="Despliego"; fi
echo "── $ACCION ${SHA:0:8} ($MODO): $(git log -1 --format=%s "$SHA")"

TRABAJO=$(mktemp -d "${TMPDIR:-/tmp}/movi-despliegue.XXXXXX")
CONSERVAR=0   # un ensayo que termina bien deja la carpeta de subida para mirarla
trap 'if [ "$CONSERVAR" = 1 ]; then rm -rf "$TRABAJO/artefacto"; else rm -rf "$TRABAJO"; fi' EXIT
SUBIDA="$TRABAJO/subida"
mkdir -p "$SUBIDA"

# ── El paquete de Actions ────────────────────────────────────────────────────────────────────────

# Corridas de este commit, por el título («Paquete <sha>»): en una corrida a mano `headSha` es la
# punta de master al dispararla, no el commit que se armó. Imprime «id estado conclusión» por fila.
corridas_del_commit() {
    gh run list -R "$REPO" --workflow "$WORKFLOW" --limit 100 \
        --json databaseId,displayTitle,status,conclusion \
        --jq ".[] | select(.displayTitle == \"Paquete $SHA\") | \"\(.databaseId) \(.status) \(.conclusion)\""
}

# Espera una corrida con tope (aquí no hay `timeout`): 45 min, lo mismo que el del workflow.
esperar_corrida() {
    local id=$1 estado=""
    echo "── Esperando la corrida $id del paquete (https://github.com/$REPO/actions/runs/$id)…"
    for _ in $(seq 1 90); do
        estado=$(gh run view "$id" -R "$REPO" --json status,conclusion --jq '"\(.status) \(.conclusion)"' 2>/dev/null || true)
        case "$estado" in
            "completed success") return 0 ;;
            completed*) echo "══ La corrida $id terminó «$estado»."; return 1 ;;
        esac
        sleep 30
    done
    echo "══ La corrida $id sigue «$estado» después de 45 min."; return 1
}

# Baja el artefacto y verifica que sea de este commit y traiga la web. Falla si expiró (7 días).
bajar_paquete() {
    local id=$1
    rm -rf "$TRABAJO/artefacto"
    gh run download "$id" -R "$REPO" -n server-all -D "$TRABAJO/artefacto" >/dev/null 2>&1 || return 1
    local del_paquete
    del_paquete=$(tr -d ' \n' < "$TRABAJO/artefacto/commit.txt" 2>/dev/null || true)
    if [ "$del_paquete" != "$SHA" ]; then
        echo "══ El paquete de la corrida $id es de «$del_paquete», no de $SHA. No se despliega."; exit 1
    fi
    local jar="$TRABAJO/artefacto/server-all.jar"
    [ -s "$jar" ] || { echo "══ La corrida $id no trae server-all.jar."; exit 1; }
    local contenido
    contenido=$(unzip -l "$jar")
    grep -q ' static/index.html$' <<< "$contenido" || { echo "══ El JAR no trae static/index.html."; exit 1; }
    grep -q 'font/figtree_regular.ttf$' <<< "$contenido" || { echo "══ El JAR no trae figtree_regular.ttf."; exit 1; }
}

conseguir_paquete() {
    local filas id estado conclusion
    filas=$(corridas_del_commit || true)

    # 1. Una corrida exitosa de este commit: se reusa si su artefacto sigue vivo.
    while read -r id estado conclusion; do
        [ -n "${id:-}" ] || continue
        if [ "$estado $conclusion" = "completed success" ]; then
            echo "── Reuso la corrida $id, ya exitosa para ${SHA:0:8}."
            if bajar_paquete "$id"; then return 0; fi
            echo "   Su artefacto no se pudo bajar (¿pasaron los 7 días?); busco otra."
        fi
    done <<< "$filas"

    # 2. Una que todavía corre: se espera en vez de armar otra.
    while read -r id estado conclusion; do
        [ -n "${id:-}" ] || continue
        if [ "$estado" != "completed" ]; then
            esperar_corrida "$id" && bajar_paquete "$id" && return 0
            echo "══ No pude usar la corrida $id."; exit 1
        fi
    done <<< "$filas"

    # 3. Ninguna: se dispara, si se puede. `workflow_dispatch` exige el workflow en la rama por
    #    defecto; antes del merge solo hay corridas por `push` a `paquete/**`.
    if ! git cat-file -e "origin/master:.github/workflows/$WORKFLOW" 2>/dev/null; then
        echo "══ No hay una corrida exitosa de «Paquete para Railway» para ${SHA:0:8}, y el workflow"
        echo "   todavía no está en master, así que no se puede disparar. Empuja el commit a una rama"
        echo "   paquete/** para armarlo, o despliega con --desde-fuente."
        exit 1
    fi
    echo "── Disparo el paquete de ${SHA:0:8}."
    local salida
    salida=$(gh workflow run "$WORKFLOW" -R "$REPO" -f commit="$SHA" 2>&1)
    echo "   $salida"
    id=$(grep -o 'actions/runs/[0-9]*' <<< "$salida" | head -1 | cut -d/ -f3 || true)
    # Versiones de gh que no devuelven el enlace: se busca por el título, con tope (2 min).
    for _ in $(seq 1 12); do
        [ -n "${id:-}" ] && break
        sleep 10
        id=$(corridas_del_commit | awk '$2 != "completed" {print $1; exit}' || true)
    done
    [ -n "${id:-}" ] || { echo "══ Disparé el paquete pero no encontré su corrida."; exit 1; }
    esperar_corrida "$id" || exit 1
    bajar_paquete "$id" || { echo "══ No pude bajar el artefacto de la corrida $id."; exit 1; }
}

armar_subida_del_paquete() {
    conseguir_paquete
    mv "$TRABAJO/artefacto/server-all.jar" "$SUBIDA/app.jar"
    # El Dockerfile y el railway.toml de runtime salen del MISMO commit que el JAR.
    for f in Dockerfile railway.toml; do
        git show "$SHA:$RUNTIME/$f" > "$SUBIDA/$f" 2>/dev/null \
            || { echo "══ El commit ${SHA:0:8} no tiene $RUNTIME/$f."; exit 1; }
    done
    echo "── Carpeta de subida ($SUBIDA):"
    ls -l "$SUBIDA" | sed 's/^/   /'
    echo "   Total: $(du -sh "$SUBIDA" | cut -f1) · el JAR pesa $(du -h "$SUBIDA/app.jar" | cut -f1)" \
        "($(wc -c < "$SUBIDA/app.jar" | tr -d ' ') bytes)"
}

# ── Lo de antes de la vuelta 69 ──────────────────────────────────────────────────────────────────

armar_subida_desde_fuente() {
    # Exportación sin git: lo que se sube es exactamente el commit, no una carpeta de trabajo.
    git archive "$SHA" | tar -x -C "$SUBIDA"
}

if [ "$MODO" = "fuente" ]; then armar_subida_desde_fuente; else armar_subida_del_paquete; fi

if [ "$ENSAYO" = 1 ]; then
    CONSERVAR=1
    echo "── Ensayo: no corro railway link/variable/up. La carpeta queda en $SUBIDA."
    exit 0
fi

# ── Subir, esperar y verificar: el mismo contrato en los dos caminos ─────────────────────────────

# Sube la carpeta $1 y deja en ID el id de ESTE despliegue, sacado del enlace a los logs. Mirar
# «la primera fila» de la lista puede leer el despliegue anterior y dar un SUCCESS que no es el
# nuestro. Sin id, la subida falló antes de que Railway armara nada (un 500, un límite de tamaño):
# devuelve 1 y la salida de `railway up` queda impresa con el motivo.
subir() {
    local carpeta=$1 salida
    cd "$carpeta"
    # `railway link` es por carpeta: cada carpeta que se sube se enlaza con todo explícito.
    railway link -p "$PROYECTO" -e "$ENTORNO" -s "$SERVICIO" >/dev/null
    salida=$(railway up --service "$SERVICIO" --environment "$ENTORNO" --detach 2>&1) || true
    echo "$salida"
    ID=$(echo "$salida" | grep -o 'id=[0-9a-f-]\{36\}' | head -1 | cut -d= -f2 || true)
    [ -n "$ID" ]
}

cd "$SUBIDA"
railway link -p "$PROYECTO" -e "$ENTORNO" -s "$SERVICIO" >/dev/null
# --skip-deploys: sin él, setear la variable dispara OTRO despliegue y se paga doble.
railway variable set "MOVI_COMMIT_SHA=$SHA" --skip-deploys -s "$SERVICIO" >/dev/null
ID=""
if ! subir "$SUBIDA"; then
    if [ "$MODO" != "paquete" ]; then
        echo "══ No encontré el id del despliegue en la salida de railway up"; exit 1
    fi
    # **El respaldo automático, una sola vez.** Si lo que falló fue SUBIR el paquete (el JAR pesa
    # ~84 MB y Railway no documenta un límite: ver CLAUDE.md), se repite por el camino de
    # --desde-fuente, que sube solo el código. Un build que falla DENTRO de Railway no llega acá:
    # ese sí tiene id, y abajo sigue siendo un error.
    echo "══ La subida del paquete falló sin id de despliegue (el motivo está arriba)."
    echo "── Repito una vez por el camino --desde-fuente (Railway arma todo; ~20 min)."
    MODO="fuente"
    FUENTE_DIR="$TRABAJO/fuente"
    mkdir -p "$FUENTE_DIR"
    git -C "$RAIZ" archive "$SHA" | tar -x -C "$FUENTE_DIR"
    subir "$FUENTE_DIR" || { echo "══ Tampoco subió desde la fuente: no encontré el id del despliegue."; exit 1; }
fi

if [ "$ESPERAR" = 0 ]; then echo "── Subido; no espero el build."; exit 0; fi

if [ "$MODO" = "fuente" ]; then echo "── Esperando el build (~20 min)…"; else echo "── Esperando el build (solo empaqueta el JAR)…"; fi
fila=""
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
