FROM gradle:8.11-jdk17 AS build
WORKDIR /app
COPY . .

# Sin `--quiet` (a diferencia del Dockerfile original): con él, el motivo del fallo
# no llegaba nunca al log de Railway y hubo que adivinar cuatro veces.
#
# Los presupuestos de memoria vuelven a los de gradle.properties, que es la
# configuración con la que este build venía funcionando. Dos intentos bajándolos
# fallaron igual y en puntos distintos, así que la memoria no era la causa — y
# `-Xmx` no reserva nada por adelantado, así que pedir de más nunca fue el problema.
#
# Lo que sí falta es evidencia: estos tres comandos la dejan en el log ANTES de que
# el build muera, para que el próximo fallo no haya que adivinarlo.
RUN echo "── recursos del contenedor ──" && \
    (free -m || true) && \
    df -h /app /tmp && \
    nproc && \
    cat gradle.properties

# Build wasmJs production bundle
RUN gradle :webApp:wasmJsBrowserDistribution --no-daemon --console=plain

RUN echo "── después del wasm ──" && df -h /app /tmp && (free -m || true)

# Copy web app into server resources so it's bundled in the fat JAR
RUN mkdir -p server/src/main/resources/static && \
    cp -r webApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/

# Build server fat JAR (now includes the web app)
RUN gradle :server:buildFatJar --no-daemon --console=plain

RUN echo "── después del jar ──" && df -h /app /tmp && ls -la server/build/libs/

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
