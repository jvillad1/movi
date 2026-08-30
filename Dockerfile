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
#    obligatorio en el builder de Railway: sin él rechaza el Dockerfile. Vive fuera de
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

RUN --mount=type=cache,id=movi-gradle,target=/root/.gradle \
    gradle --no-daemon --console=plain -q dependencies --configuration compileClasspath > /dev/null 2>&1 || true

COPY . .

RUN echo "── recursos ──" && (free -m || true) && df -h /app && nproc

RUN --mount=type=cache,id=movi-gradle,target=/root/.gradle \
    gradle :webApp:wasmJsBrowserDistribution --no-daemon --console=plain --build-cache > /tmp/wasm.log 2>&1 \
    || (echo "══ FALLÓ EL WASM ══" && tail -120 /tmp/wasm.log && false)

RUN mkdir -p server/src/main/resources/static && \
    cp -r webApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/

RUN --mount=type=cache,id=movi-gradle,target=/root/.gradle \
    gradle :server:buildFatJar --no-daemon --console=plain --build-cache > /tmp/jar.log 2>&1 \
    || (echo "══ FALLÓ EL JAR ══" && tail -120 /tmp/jar.log && false)

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
