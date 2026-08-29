FROM gradle:8.11-jdk17 AS build
WORKDIR /app
COPY . .

# El contenedor de build de Railway no tiene los 12 GB que pide gradle.properties
# (8 GB para el daemon de Kotlin + 4 para Gradle). Esos números están pensados para
# una máquina de desarrollo; acá el JVM se queda sin memoria y el build muere.
# Se acotan SOLO para esta imagen — gradle.properties no se toca, que es lo que usan
# las máquinas locales.
#
# Y sin `--quiet`: con él, el motivo del fallo no llega al log de Railway. Un build
# que falla en silencio cuesta más que un log largo.
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx2g"

# Build wasmJs production bundle
RUN gradle :webApp:wasmJsBrowserDistribution --no-daemon -Pkotlin.daemon.jvmargs=-Xmx3g --console=plain

# Copy web app into server resources so it's bundled in the fat JAR
RUN mkdir -p server/src/main/resources/static && \
    cp -r webApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/

# Build server fat JAR (now includes the web app)
RUN gradle :server:buildFatJar --no-daemon -Pkotlin.daemon.jvmargs=-Xmx3g --console=plain

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
