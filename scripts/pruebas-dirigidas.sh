#!/bin/bash
# Corre SOLO las pruebas de lo que tocaste, más las guardas de calidad de :core (vuelta 122).
#
# La batería completa la corre GitHub Actions («Las cuatro suites», obligatoria para mergear). En la
# Mac de 16 GB correrla entera tarda 13 a 23 minutos y la comparten varias sesiones; esto corre las
# clases que le pases, en su módulo, y las guardas de `core/src/jvmTest/.../quality/` (voseo,
# comillas rectas, glifos de Figtree, valores sueltos…), que son baratas y miran todo el código.
#
# Uso:
#   scripts/pruebas-dirigidas.sh [--wasm|--sin-wasm] [--sin-calidad] <archivo o clase>…
#
# Cada argumento puede ser:
#   - un archivo de prueba:   shared/src/androidUnitTest/kotlin/…/HojaDeAgregarTest.kt
#   - el nombre de una clase: HojaDeAgregarTest   o   com.jvillada.movi.ui.HojaDeAgregarTest
#   - un archivo de código:   shared/src/commonMain/kotlin/…/Agregar.kt  → corre `AgregarTest`
#                             si existe (si no, avisa y sigue)
#
# Qué tarea corre cada una sale de dónde vive el archivo:
#   core/src/{jvmTest,commonTest}            → :core:jvmTest
#   server/src/test                          → :server:test
#   shared/src/{androidUnitTest,commonTest}  → :shared:testDebugUnitTest
#
# `--wasm` agrega `:shared:compileKotlinWasmJs`. Sin `--wasm` ni `--sin-wasm`, se agrega solo si lo
# que cambiaste respecto de origin/master (commits y sin commitear) toca `core/src/commonMain` o
# `shared/src/commonMain`: la regla del proceso.
#
# Las pruebas SIEMPRE corren (el build lo exige desde la vuelta 122: una `Test` nunca está al día ni
# sale de la caché); la compilación sí sale de la caché de build compartida entre worktrees
# (`org.gradle.caching=true` en `~/.gradle/gradle.properties`).
#
# Candado: si existe la carpeta del proceso (`/private/tmp/brief-jk`), espera el candado de Gradle
# (`gradle.lock`, o el que diga `MOVI_CANDADO_GRADLE`) y lo suelta al salir, también con Ctrl-C.
# Corre en primer plano y no deja nada propio corriendo; el demonio de Gradle es el de siempre
# (se apaga solo tras 30 min sin uso, `org.gradle.daemon.idletimeout` del usuario).
set -u

RAIZ=$(cd "$(dirname "$0")/.." && pwd)
cd "$RAIZ" || exit 1

WASM=auto
CALIDAD=1
ARGS=()
for a in "$@"; do
  case "$a" in
    --wasm) WASM=si ;;
    --sin-wasm) WASM=no ;;
    --sin-calidad) CALIDAD=0 ;;
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
    *) ARGS+=("$a") ;;
  esac
done

if [ ${#ARGS[@]} -eq 0 ] && [ $CALIDAD = 0 ] && [ $WASM != si ]; then
  echo "Nada que correr. Uso: $0 [--wasm|--sin-wasm] [--sin-calidad] <archivo o clase>…" >&2
  exit 2
fi

# Filtros por tarea, como líneas «tarea<TAB>clase» (bash 3.2 de macOS: sin arreglos asociativos).
FILTROS=""
agregar() { FILTROS="$FILTROS$1	$2
"; }

tarea_de() { # ruta relativa de un archivo de prueba → tarea de Gradle (o vacío)
  case "$1" in
    core/src/jvmTest/*|core/src/commonTest/*) echo ":core:jvmTest" ;;
    server/src/test/*) echo ":server:test" ;;
    shared/src/androidUnitTest/*|shared/src/commonTest/*) echo ":shared:testDebugUnitTest" ;;
    *) echo "" ;;
  esac
}

clase_de() { # archivo .kt → nombre completo de la clase (paquete del archivo + nombre)
  local paquete nombre
  paquete=$(sed -n 's/^package[[:space:]]\{1,\}\([A-Za-z0-9_.]*\).*/\1/p' "$1" | head -1)
  nombre=$(basename "$1" .kt)
  if [ -n "$paquete" ]; then echo "$paquete.$nombre"; else echo "$nombre"; fi
}

buscar_pruebas() { # nombre simple de clase → archivos de prueba con ese nombre
  find core/src/jvmTest core/src/commonTest server/src/test shared/src/androidUnitTest shared/src/commonTest \
    -name "$1.kt" -type f 2>/dev/null
}

agregar_archivo_de_prueba() {
  local t
  t=$(tarea_de "$1")
  if [ -z "$t" ]; then echo "── $1 no es de una suite de pruebas conocida" >&2; return 1; fi
  agregar "$t" "$(clase_de "$1")"
}

FALTAN=0
for a in "${ARGS[@]+"${ARGS[@]}"}"; do
  a=${a#./}
  case "$a" in "$RAIZ"/*) a=${a#"$RAIZ"/} ;; esac
  if [ -f "$a" ]; then
    if [ -n "$(tarea_de "$a")" ]; then
      agregar_archivo_de_prueba "$a" || FALTAN=1
    else
      # Un archivo de código: su prueba se llama igual + «Test».
      encontrados=$(buscar_pruebas "$(basename "$a" .kt)Test")
      if [ -z "$encontrados" ]; then
        echo "── $a no tiene una $(basename "$a" .kt)Test: pásale las clases de prueba a mano" >&2
      fi
      for f in $encontrados; do agregar_archivo_de_prueba "$f"; done
    fi
  else
    simple=${a##*.}
    encontrados=$(buscar_pruebas "$simple")
    if [ -z "$encontrados" ]; then echo "── No encontré la prueba «$a»" >&2; FALTAN=1; continue; fi
    for f in $encontrados; do
      # Con el nombre completo, solo la del paquete pedido.
      if [ "$simple" != "$a" ] && [ "$(clase_de "$f")" != "$a" ]; then continue; fi
      agregar_archivo_de_prueba "$f"
    done
  fi
done
[ $FALTAN = 1 ] && { echo "── Corrige los argumentos de arriba; no corrí nada." >&2; exit 2; }

if [ $CALIDAD = 1 ]; then agregar ":core:jvmTest" "com.jvillada.movi.shared.quality.*"; fi

if [ $WASM = auto ]; then
  WASM=no
  base=$(git merge-base HEAD origin/master 2>/dev/null || true)
  if { [ -n "$base" ] && git diff --name-only "$base" HEAD; git diff --name-only HEAD; git ls-files --others --exclude-standard; } 2>/dev/null \
      | grep -q -E '^(core|shared)/src/commonMain/'; then
    WASM=si
    echo "── Tocaste commonMain: también compilo :shared:compileKotlinWasmJs (--sin-wasm para saltarlo)"
  fi
fi

# Arma «:tarea --tests A --tests B :otra --tests C» (cada --tests va justo después de su tarea).
LINEA=()
for t in ":core:jvmTest" ":server:test" ":shared:testDebugUnitTest"; do
  clases=$(printf '%s' "$FILTROS" | awk -F'\t' -v t="$t" '$1 == t { print $2 }' | sort -u)
  [ -z "$clases" ] && continue
  LINEA+=("$t")
  while IFS= read -r c; do LINEA+=(--tests "$c"); done <<EOF
$clases
EOF
done
[ $WASM = si ] && LINEA+=(":shared:compileKotlinWasmJs")

if [ ${#LINEA[@]} -eq 0 ]; then echo "Nada que correr." >&2; exit 2; fi

CARPETA_DEL_PROCESO=/private/tmp/brief-jk
CANDADO=${MOVI_CANDADO_GRADLE:-$CARPETA_DEL_PROCESO/gradle.lock}
TENGO_EL_CANDADO=0
soltar() { [ $TENGO_EL_CANDADO = 1 ] && rmdir "$CANDADO" 2>/dev/null; TENGO_EL_CANDADO=0; }
trap soltar EXIT
trap 'soltar; exit 130' INT TERM HUP
if [ -d "$(dirname "$CANDADO")" ]; then
  if ! mkdir "$CANDADO" 2>/dev/null; then
    # Con tope: un candado que dejó un proceso muerto (SIGKILL no pasa por la trampa) no puede
    # dejar esperando para siempre a quien llega después.
    echo "── Esperando el candado de Gradle ($CANDADO)…"
    espera=0
    until mkdir "$CANDADO" 2>/dev/null; do
      sleep 15; espera=$((espera + 15))
      if [ $espera -ge 1800 ]; then
        echo "── El candado lleva 30 min tomado. Si nadie corre Gradle, bórralo: rmdir $CANDADO" >&2
        exit 3
      fi
    done
  fi
  TENGO_EL_CANDADO=1
fi

echo "── ./gradlew ${LINEA[*]}"
inicio=$(date +%s)
./gradlew "${LINEA[@]}"
rc=$?
echo "── $(( $(date +%s) - inicio )) s, salida $rc"
exit $rc
