FROM gradle:8.11-jdk17 AS build
WORKDIR /app

# ── Capa de dependencias ──────────────────────────────────────────────────────
#
# El build de Railway se estaba pasando de su límite de 20 minutos: seis intentos
# terminaron en «Build image ✗ (20:02)» — un timeout, no un error de compilación.
# Por eso el log se cortaba a mitad de una tarea sin mensaje y sin «FAILURE», y por
# eso morían en puntos distintos cada vez.
#
# La causa es que `COPY . .` invalida la capa con CUALQUIER cambio de código, así
# que cada despliegue volvía a resolver y descargar todas las dependencias desde
# cero. Copiando primero solo los archivos de build, esa capa se reusa mientras no
# cambien las dependencias — que es casi siempre.
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY core/build.gradle.kts core/
COPY shared/build.gradle.kts shared/
COPY webApp/build.gradle.kts webApp/
COPY server/build.gradle.kts server/
COPY androidApp/build.gradle.kts androidApp/

# Baja el grafo de dependencias sin compilar nada. `|| true`: sin las fuentes, la
# resolución puede quedar incompleta y no importa — lo que se busca es dejar el
# caché de Gradle caliente en esta capa.
RUN gradle --no-daemon --console=plain -q dependencies --configuration compileClasspath > /dev/null 2>&1 || true

# ── Fuentes ───────────────────────────────────────────────────────────────────
COPY . .

RUN echo "── recursos ──" && (free -m || true) && df -h /app && nproc

# Sin `--quiet`, y guardando la salida para imprimir la cola si algo falla: con
# `--quiet` el motivo de un fallo nunca llegaba al log de Railway.
RUN gradle :webApp:wasmJsBrowserDistribution --no-daemon --console=plain > /tmp/wasm.log 2>&1 \
    || (echo "══ FALLÓ EL WASM ══" && tail -120 /tmp/wasm.log && false)

RUN mkdir -p server/src/main/resources/static && \
    cp -r webApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/

RUN gradle :server:buildFatJar --no-daemon --console=plain > /tmp/jar.log 2>&1 \
    || (echo "══ FALLÓ EL JAR ══" && tail -120 /tmp/jar.log && false)

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
