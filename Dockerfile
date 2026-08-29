FROM gradle:8.11-jdk17 AS build
WORKDIR /app
COPY . .

# El contenedor de build de Railway no tiene los 12 GB que pide gradle.properties
# (8 GB para el daemon de Kotlin + 4 para Gradle). Esos números son para una máquina
# de desarrollo; acá el proceso se queda sin memoria y lo matan — el log se corta de
# golpe a mitad de una tarea, sin «FAILURE» ni excepción, que es la firma de un kill.
#
# Se reescriben DENTRO de la imagen, no por variable de entorno: `org.gradle.jvmargs`
# de gradle.properties **le gana** a GRADLE_OPTS, así que el intento anterior no bajó
# nada. Esta capa no toca las máquinas locales, que siguen leyendo el archivo original.
RUN sed -i \
      -e 's/^org.gradle.jvmargs=.*/org.gradle.jvmargs=-Xmx1800m -Dfile.encoding=UTF-8/' \
      -e 's/^kotlin.daemon.jvmargs=.*/kotlin.daemon.jvmargs=-Xmx2200m/' \
      gradle.properties && cat gradle.properties

# El compilador de Kotlin corre DENTRO del proceso de Gradle en vez de levantar su
# propio daemon: un solo JVM en lugar de dos es la diferencia entre entrar en el
# contenedor y no entrar.
ENV GRADLE_OPTS="-Dkotlin.compiler.execution.strategy=in-process"

# Build wasmJs production bundle
RUN gradle :webApp:wasmJsBrowserDistribution --no-daemon --console=plain

# Copy web app into server resources so it's bundled in the fat JAR
RUN mkdir -p server/src/main/resources/static && \
    cp -r webApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/

# Build server fat JAR (now includes the web app)
RUN gradle :server:buildFatJar --no-daemon --console=plain

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
