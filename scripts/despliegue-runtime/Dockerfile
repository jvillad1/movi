# La imagen de runtime del camino normal de `scripts/desplegar.sh` (vuelta 69).
#
# El JAR (`app.jar`) ya viene armado por el workflow «Paquete para Railway» (`paquete.yml`), con
# la web adentro: acá no se compila nada, así que el build de Railway tarda segundos en vez de
# los 17-25 min que lo hacían chocar con su límite de ~20. Es la segunda etapa del `Dockerfile`
# de la raíz, tal cual; ese sigue siendo el de `desplegar.sh --desde-fuente`.
#
# Lo único que se copia es `app.jar`: la carpeta que se sube no trae nada más que esto, el JAR y
# `railway.toml`.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY app.jar app.jar

# Red de contención para `/version`, igual que en el `Dockerfile` de la raíz: `desplegar.sh` pone
# `MOVI_COMMIT_SHA` como variable del servicio; esto cubre el caso de que no llegue.
ARG RAILWAY_GIT_COMMIT_SHA=""
ENV MOVI_COMMIT_SHA=$RAILWAY_GIT_COMMIT_SHA

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
