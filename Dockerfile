FROM gradle:8.11-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :server:buildFatJar --no-daemon --quiet

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
