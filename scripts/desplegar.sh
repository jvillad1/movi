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
# JAR con la web adentro.
#
# **Y Railway lo BAJA de un release de GitHub (vuelta 84, 30-oct-2026).** Subir el JAR (87 MB)
# desde esta Mac a Railway se cortaba («operation timed out») y cada despliegue caía al respaldo.
# Ahora el workflow publica el release **público** `paquete-<sha completo>` (prerelease, nunca
# «latest»; el repo es público y el dueño aprobó que el JAR lo sea) con `server-all.jar`,
# `server-all.jar.sha256` y `commit.txt`. Este script lo baja por la misma URL pública que usará
# Railway, verifica que `commit.txt` sea este commit, que la huella coincida y que el JAR traiga
# la web y la fuente, llena `scripts/despliegue-runtime/Dockerfile.plantilla` con el sha y la
# huella, y sube a Railway SOLO ese `Dockerfile` y el `railway.toml` (unos pocos KB). El builder
# de Railway baja el JAR y el build falla si la huella no coincide. Todo lo demás —servicio
# explícito, `MOVI_COMMIT_SHA` con `--skip-deploys`, el id de ESTE despliegue, la espera y la
# verificación de `/version` y de la fuente— es igual que antes.
#
# **El respaldo**, `--desde-fuente`, hace lo de antes de la vuelta 69: `git archive` de
# origin/master y el `Dockerfile` completo armando todo en Railway. Sirve si GitHub Actions está
# caído o el paquete no se puede armar; vuelve a chocar con el límite de ~20 min. Se repite solo
# por ese camino, UNA vez, en dos casos: si `railway up` no devuelve id de despliegue (la subida
# falló antes de armar nada), o si el build de Railway falla porque no pudo bajar el release (el
# log del build dice «MOVI-SIN-RELEASE»: la red de GitHub caída). Un build que falla por otra
# razón —la huella distinta incluida— sigue siendo un error.
#
# Uso: ./scripts/desplegar.sh                  # paquete de Actions + railway up del JAR, y espera
#      ./scripts/desplegar.sh --sin-esperar
#      ./scripts/desplegar.sh --desde-fuente    # respaldo: Railway arma todo desde el código
#      ./scripts/desplegar.sh --ensayo [--commit <sha>]
#                                               # todo menos railway link/variable/up/logs: baja y
#                                               # verifica el release, arma la carpeta de subida e
#                                               # imprime el Dockerfile lleno y cuánto pesa
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
trap 'if [ "$CONSERVAR" = 1 ]; then rm -rf "$TRABAJO/release"; else rm -rf "$TRABAJO"; fi' EXIT
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

# El release del paquete (vuelta 84). La URL es la pública, sin autenticación: la misma que baja
# el builder de Railway, así que si acá baja, allá también (salvo que GitHub se caiga en el medio).
TAG="paquete-$SHA"
URL_DEL_RELEASE="https://github.com/$REPO/releases/download/$TAG"
ARCHIVOS_DEL_RELEASE="server-all.jar server-all.jar.sha256 commit.txt"
HUELLA=""

# ¿Existe el release de este commit con sus tres archivos?
release_completo() {
    local nombres f
    nombres=$(gh release view "$TAG" -R "$REPO" --json assets --jq '.assets[].name' 2>/dev/null) || return 1
    for f in $ARCHIVOS_DEL_RELEASE; do grep -qx "$f" <<< "$nombres" || return 1; done
}

# Lo publica el penúltimo paso de la corrida: tras un «success» ya debería estar (tope 2 min).
esperar_release() {
    for _ in $(seq 1 12); do
        release_completo && return 0
        sleep 10
    done
    echo "══ La corrida terminó bien, pero el release $TAG no tiene sus tres archivos."; return 1
}

# Baja el release por la URL pública y verifica: que sea de este commit, que la huella del JAR sea
# la publicada y que el JAR traiga la web y la fuente. Deja la huella en HUELLA.
bajar_del_release() {
    local dir="$TRABAJO/release" f
    rm -rf "$dir"; mkdir -p "$dir"
    echo "── Bajo $TAG de $URL_DEL_RELEASE (sin autenticación)…"
    for f in $ARCHIVOS_DEL_RELEASE; do
        curl -fsSL --retry 3 --retry-delay 5 -o "$dir/$f" "$URL_DEL_RELEASE/$f" \
            || { echo "══ No pude bajar $f de $URL_DEL_RELEASE."; return 1; }
    done
    local del_paquete
    del_paquete=$(tr -d ' \n' < "$dir/commit.txt")
    if [ "$del_paquete" != "$SHA" ]; then
        echo "══ El release $TAG dice ser de «$del_paquete», no de $SHA. No se despliega."; exit 1
    fi
    HUELLA=$(awk '{print $1; exit}' "$dir/server-all.jar.sha256")
    if ! [[ "$HUELLA" =~ ^[0-9a-f]{64}$ ]]; then
        echo "══ server-all.jar.sha256 no trae una huella SHA-256 («$HUELLA»)."; exit 1
    fi
    local calculada
    calculada=$(shasum -a 256 "$dir/server-all.jar" | awk '{print $1}')
    if [ "$calculada" != "$HUELLA" ]; then
        echo "══ El JAR bajado da $calculada y el release publica $HUELLA. No se despliega."; exit 1
    fi
    local contenido
    contenido=$(unzip -l "$dir/server-all.jar")
    grep -q ' static/index.html$' <<< "$contenido" || { echo "══ El JAR no trae static/index.html."; exit 1; }
    grep -q 'font/figtree_regular.ttf$' <<< "$contenido" || { echo "══ El JAR no trae figtree_regular.ttf."; exit 1; }
    echo "   commit.txt = ${SHA:0:12}… · sha256 $HUELLA · JAR de $(du -h "$dir/server-all.jar" | cut -f1) con la web y la fuente: ok"
}

conseguir_paquete() {
    # 0. El release ya está publicado: no hace falta ninguna corrida.
    if release_completo; then
        echo "── El release $TAG ya está publicado."
        bajar_del_release && return 0
        exit 1
    fi

    local filas id estado conclusion
    filas=$(corridas_del_commit || true)

    # 1. Una corrida que todavía corre: se espera en vez de armar otra.
    while read -r id estado conclusion; do
        [ -n "${id:-}" ] || continue
        if [ "$estado" != "completed" ]; then
            esperar_corrida "$id" && esperar_release && bajar_del_release && return 0
            echo "══ No pude usar la corrida $id."; exit 1
        fi
    done <<< "$filas"

    # 2. Una exitosa sin release es de antes de la vuelta 84 o su release ya se limpió (quedan
    #    los 5 más recientes): se arma de nuevo.
    if grep -q 'completed success' <<< "$filas"; then
        echo "── Hay una corrida exitosa de ${SHA:0:8}, pero su release ya no está; la armo de nuevo."
    fi

    # 3. Se dispara, si se puede. `workflow_dispatch` exige el workflow en la rama por defecto;
    #    antes del merge solo hay corridas por `push` a `paquete/**`.
    if ! git cat-file -e "origin/master:.github/workflows/$WORKFLOW" 2>/dev/null; then
        echo "══ No hay un release $TAG ni una corrida de «Paquete para Railway» en curso, y el"
        echo "   workflow todavía no está en master, así que no se puede disparar. Empuja el commit"
        echo "   a una rama paquete/** para armarlo, o despliega con --desde-fuente."
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
    esperar_release || exit 1
    bajar_del_release || { echo "══ No pude bajar el release $TAG."; exit 1; }
}

armar_subida_del_paquete() {
    conseguir_paquete
    # La plantilla y el railway.toml salen del MISMO commit que el JAR; un commit de antes de la
    # vuelta 84 no tiene plantilla, y entonces va la de origin/master.
    local origen="$SHA"
    if ! git cat-file -e "$SHA:$RUNTIME/Dockerfile.plantilla" 2>/dev/null; then
        origen="origin/master"
        echo "── ${SHA:0:8} no tiene $RUNTIME/Dockerfile.plantilla: uso la de origin/master."
    fi
    git show "$origen:$RUNTIME/Dockerfile.plantilla" \
        | sed -e "s/@@SHA@@/$SHA/g" -e "s/@@SHA256@@/$HUELLA/g" > "$SUBIDA/Dockerfile" \
        || { echo "══ $origen no tiene $RUNTIME/Dockerfile.plantilla."; exit 1; }
    git show "$origen:$RUNTIME/railway.toml" > "$SUBIDA/railway.toml" \
        || { echo "══ $origen no tiene $RUNTIME/railway.toml."; exit 1; }
    # Nada sin llenar, nada «latest»: la URL es la de ESTE release y la huella la verificada.
    if grep -q '@@' "$SUBIDA/Dockerfile"; then echo "══ Quedó un @@…@@ sin llenar en el Dockerfile."; exit 1; fi
    if awk '!/^#/ && tolower($0) ~ /latest/ {f=1} END {exit !f}' "$SUBIDA/Dockerfile"; then echo "══ El Dockerfile nombra un «latest»."; exit 1; fi
    grep -q "releases/download/$TAG/server-all.jar" "$SUBIDA/Dockerfile" \
        && grep -q "$HUELLA  app.jar" "$SUBIDA/Dockerfile" \
        || { echo "══ El Dockerfile lleno no nombra $TAG y su huella."; exit 1; }
    echo "── Carpeta de subida ($SUBIDA):"
    ls -l "$SUBIDA" | sed 's/^/   /'
    echo "   Total: $(du -sh "$SUBIDA" | cut -f1) ($(cat "$SUBIDA"/* | wc -c | tr -d ' ') bytes); el JAR no viaja: lo baja Railway"
}

# ── Lo de antes de la vuelta 69 ──────────────────────────────────────────────────────────────────

armar_subida_desde_fuente() {
    # Exportación sin git: lo que se sube es exactamente el commit, no una carpeta de trabajo.
    git archive "$SHA" | tar -x -C "$SUBIDA"
}

if [ "$MODO" = "fuente" ]; then armar_subida_desde_fuente; else armar_subida_del_paquete; fi

if [ "$ENSAYO" = 1 ]; then
    CONSERVAR=1
    echo "── El Dockerfile que se subiría ($(wc -c < "$SUBIDA/Dockerfile" | tr -d ' ') bytes):"
    sed 's/^/   │ /' "$SUBIDA/Dockerfile"
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

# Repite UNA vez por --desde-fuente: sube el `git archive` del commit y deja el id nuevo en ID.
repetir_desde_fuente() {
    echo "── Repito una vez por el camino --desde-fuente (Railway arma todo; ~20 min)."
    MODO="fuente"
    local dir="$TRABAJO/fuente"
    mkdir -p "$dir"
    git -C "$RAIZ" archive "$SHA" | tar -x -C "$dir"
    subir "$dir" || { echo "══ Tampoco subió desde la fuente: no encontré el id del despliegue."; exit 1; }
}

# ¿El build $1 falló porque no pudo bajar el release? Lo dice el rótulo que imprime el `RUN` de
# la plantilla; sin log (el CLI falló) la respuesta es no: el fallo queda como error.
fallo_por_no_bajar_el_release() {
    local log
    # Primero a una variable: con `pipefail`, un `grep -q` que corta el tubo daría falso.
    log=$(railway logs "$1" --build -p "$PROYECTO" -s "$SERVICIO" -e "$ENTORNO" --lines 500 2>/dev/null || true)
    grep -q 'MOVI-SIN-RELEASE' <<< "$log"
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
    # **El respaldo automático, una sola vez.** Lo que falló fue SUBIR (la carpeta pesa unos KB,
    # así que es Railway y no el tamaño). Un build que falla DENTRO de Railway no llega acá: ese
    # sí tiene id y se mira abajo.
    echo "══ La subida del paquete falló sin id de despliegue (el motivo está arriba)."
    repetir_desde_fuente
fi

if [ "$ESPERAR" = 0 ]; then
    echo "── Subido; no espero el build (y por eso, si no puede bajar el release, no hay respaldo)."
    exit 0
fi

# Espera el despliegue ID con tope de 40 min. Deja la fila en FILA; 0 = SUCCESS, 1 = falló,
# 2 = sigue sin terminar.
esperar_build() {
    FILA=""
    for _ in $(seq 1 80); do
        FILA=$(railway deployment list --service "$SERVICIO" 2>/dev/null | grep "$ID" || true)
        case "$FILA" in
            *SUCCESS*) return 0 ;;
            *FAILED*|*CRASHED*) return 1 ;;
        esac
        sleep 30
    done
    return 2
}

while true; do
    if [ "$MODO" = "fuente" ]; then echo "── Esperando el build (~20 min)…"; else echo "── Esperando el build (baja el JAR del release y lo empaqueta)…"; fi
    rc=0; esperar_build || rc=$?
    [ "$rc" = 0 ] && break
    if [ "$rc" = 2 ]; then echo "══ Sigue sin terminar después de 40 min: $FILA"; exit 1; fi
    echo "══ El build falló: $FILA"
    if [ "$MODO" = "paquete" ] && fallo_por_no_bajar_el_release "$ID"; then
        echo "══ Railway no pudo bajar el release $TAG (el log del build dice MOVI-SIN-RELEASE)."
        repetir_desde_fuente
        continue
    fi
    echo "   Producción sigue con el despliegue anterior."; exit 1
done

version=$(curl -fsS "$URL/version" || true)
cabecera=$(curl -fsS "$FUENTE" | head -c 4 | od -An -tx1 | tr -d ' \n' || true)
echo "── /version: $version"
if [[ "$version" != *"$SHA"* ]]; then echo "══ /version no dice ${SHA:0:8}"; exit 1; fi
if [ "$cabecera" != "00010000" ]; then echo "══ La web no sirve las fuentes (llegó «$cabecera»): paquete viejo o roto"; exit 1; fi
echo "── Desplegado y verificado: ${SHA:0:8}"
