FROM gradle:8.11-jdk17 AS build
WORKDIR /app
COPY . .

# Build wasmJs production bundle
RUN gradle :composeApp:wasmJsBrowserDistribution --no-daemon --quiet

# Copy web app into server resources so it's bundled in the fat JAR
RUN mkdir -p server/src/main/resources/static && \
    cp -r composeApp/build/dist/wasmJs/productionExecutable/. server/src/main/resources/static/

# Build server fat JAR (now includes the web app)
RUN gradle :server:buildFatJar --no-daemon --quiet

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
