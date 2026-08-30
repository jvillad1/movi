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
# La salida se guarda y solo se imprime la COLA si falla.
#
# El log de Railway devuelve una ventana acotada: en cuatro intentos el error nunca
# entró en ella —el log terminaba a mitad de una tarea y parecía un proceso muerto—
# y eso mandó a perseguir memoria y disco durante tres despliegues. Los recursos
# resultaron ser 58 GB de RAM y 672 GB libres: nunca fue eso.
#
# Con `tail` sobre el archivo, el motivo real queda en las últimas líneas, que son
# las que sí se ven.
RUN gradle :webApp:wasmJsBrowserDistribution --no-daemon --console=plain --stacktrace > /tmp/wasm.log 2>&1 \
    || (echo "══ FALLÓ EL WASM — últimas 120 líneas ══" && tail -120 /tmp/wasm.log && false)

RUN echo "── después del wasm ──" && df -h /app /tmp && (free -m || true)

# Copy web app into server resources so it's bundled in the fat JAR
RUN mkdir -p server/src/main/resources/static && \
    cp -r webApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/

# Build server fat JAR (now includes the web app)
RUN gradle :server:buildFatJar --no-daemon --console=plain --stacktrace > /tmp/jar.log 2>&1 \
    || (echo "══ FALLÓ EL JAR — últimas 120 líneas ══" && tail -120 /tmp/jar.log && false)

RUN echo "── después del jar ──" && df -h /app /tmp && ls -la server/build/libs/

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
