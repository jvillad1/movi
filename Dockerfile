# syntax=docker/dockerfile:1
FROM gradle:8.11-jdk17 AS build
WORKDIR /app

# ── Por qué este Dockerfile es así ────────────────────────────────────────────
#
# El build de Railway se moría a los 20:02 con «Build image ✗», sin error y sin
# «FAILURE» en el log — la firma de un timeout, no de una compilación fallida.
# El panel lo mostró en una columna que el CLI no devuelve:
#
#     RUN gradle :webApp:wasmJsBrowserDistribution …     19m 23s
#
# El wasm solo consume casi todo el presupuesto y el jar del servidor nunca llega
# a terminar. Y el límite no se puede subir: es del plan, no un ajuste.
#
# Entonces el build tiene que caber. Tres cosas, en orden de cuánto ahorran:
#
# 1. CACHÉ QUE SOBREVIVE ENTRE DESPLIEGUES (`--mount=type=cache,id=…`). El `id` es
#    obligatorio en el builder de Railway, y además tiene que llevar el prefijo
#    `s/<id-del-servicio>` — el builder rechaza el Dockerfile sin eso, y lo hace en
#    segundos, que es la única forma barata de fallar en esta serie. Vive fuera de
#    las capas, así que persiste incluso cuando el build falla: las dependencias
#    descargadas, el caché de Kotlin y el de Gradle quedan listos para el próximo
#    intento. Es lo que convierte 19 minutos en algo repetible.
# 2. CAPA DE DEPENDENCIAS APARTE: `COPY . .` invalida todo con cualquier cambio de
#    código. Copiando primero solo los archivos de build, resolver el grafo deja
#    de repetirse en cada despliegue.
# 3. CACHÉ DE BUILD DE GRADLE (`--build-cache`), que no estaba activado: reusa las
#    salidas de tareas cuyas entradas no cambiaron.
#
# Sin `--quiet`, y guardando la salida para imprimir la cola si algo falla de
# verdad: con `--quiet`, el motivo de un fallo nunca llegaba al log.

COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY core/build.gradle.kts core/
COPY shared/build.gradle.kts shared/
COPY webApp/build.gradle.kts webApp/
COPY server/build.gradle.kts server/
COPY androidApp/build.gradle.kts androidApp/

RUN --mount=type=cache,id=s/8fdd793e-509a-42a8-ae98-e1fc6be27577-gradle,target=/root/.gradle \
    gradle --no-daemon --console=plain -q dependencies --configuration compileClasspath > /dev/null 2>&1 || true

COPY . .

RUN echo "── recursos ──" && (free -m || true) && df -h /app && nproc

# ── Un despliegue «exitoso» con el código de otro commit (2026-09-14) ─────────
#
# Dos despliegues terminaron en SUCCESS, con /version en el commit nuevo, y la web
# no conocía las fuentes de #208. La primera explicación fue que el caché de build
# de Gradle del montaje había devuelto un wasm viejo, y por eso está la purga de
# abajo. **Era falsa.** El log del build siguiente mostró 15 pasos con un Dockerfile
# de 16: `railway up` corrido desde un git worktree sube la carpeta PRINCIPAL del
# repositorio, no el worktree. Esa carpeta estaba en #202. Se desplegó desde una
# exportación sin git y salió bien. `/version` no lo delató porque lee una variable
# que pone quien despliega, no el código.
#
# Lo que queda, y por qué:
#
# 1. LA PURGA, atada a la versión de Kotlin. No fue la causa, pero es barata: corre
#    una vez por versión y borra solo el caché de build, no las dependencias ni el
#    caché de Kotlin, que son lo que mantiene el build debajo de los 20 minutos.
# 2. LA GUARDA de fuentes. Esta sí vale: habría frenado ese despliegue, que subió un
#    código anterior a las fuentes. Un build fallido en Railway deja arriba el
#    despliegue anterior; uno exitoso con el código equivocado lo reemplaza sin ruido.
RUN --mount=type=cache,id=s/8fdd793e-509a-42a8-ae98-e1fc6be27577-gradle,target=/root/.gradle \
    KOTLIN=$(sed -n 's/^kotlin = "\(.*\)"$/\1/p' gradle/libs.versions.toml) && \
    MARCA=/root/.gradle/.cache-de-build-purgado-kotlin-$KOTLIN && \
    if [ ! -f "$MARCA" ]; then \
        echo "── purgando el caché de build (primera vez con Kotlin $KOTLIN) ──" && \
        rm -rf /root/.gradle/caches/build-cache-1 && touch "$MARCA"; \
    fi && \
    gradle :webApp:wasmJsBrowserDistribution --no-daemon --console=plain --build-cache > /tmp/wasm.log 2>&1 \
    || (echo "══ FALLÓ EL WASM ══" && tail -120 /tmp/wasm.log && false)

RUN sh scripts/el-paquete-web-trae-las-fuentes.sh webApp/build/dist/wasmJs/productionExecutable

RUN mkdir -p server/src/main/resources/static && \
    cp -r webApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/

RUN --mount=type=cache,id=s/8fdd793e-509a-42a8-ae98-e1fc6be27577-gradle,target=/root/.gradle \
    gradle :server:buildFatJar --no-daemon --console=plain --build-cache > /tmp/jar.log 2>&1 \
    || (echo "══ FALLÓ EL JAR ══" && tail -120 /tmp/jar.log && false)

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar

# Red de contención para `/version`. En runtime, Railway inyecta `RAILWAY_GIT_COMMIT_SHA` en el
# proceso y eso alcanza; esto cubre el caso de que no llegue. El ARG queda en la ÚLTIMA capa a
# propósito: cambiarlo en cada despliegue no invalida ninguna capa de compilación, así que no
# cuesta un solo segundo del presupuesto de build. Vacío por defecto — sin valor, `/version`
# contesta «no lo sé» (503), que es justo lo que tiene que hacer.
ARG RAILWAY_GIT_COMMIT_SHA=""
ENV MOVI_COMMIT_SHA=$RAILWAY_GIT_COMMIT_SHA

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
