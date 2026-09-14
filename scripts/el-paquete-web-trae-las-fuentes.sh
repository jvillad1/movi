#!/bin/sh
# ¿El paquete web que se va a desplegar trae las fuentes de Movi?
#
# Lo corre el Dockerfile después de armar el wasm. Existe porque el 2026-09-14 Railway
# desplegó con SUCCESS un wasm viejo, sacado del caché de build, que no conocía las
# fuentes: la web se veía con la letra del sistema y nadie lo habría notado. Un build
# que falla deja arriba el despliegue anterior; uno que pasa con un paquete viejo lo
# reemplaza. Por eso esto falla fuerte.
#
# Uso: el-paquete-web-trae-las-fuentes.sh <carpeta del paquete>
set -eu
PAQUETE="${1:?falta la carpeta del paquete}"
FUENTES="$PAQUETE/composeResources/com.jvillada.movi.resources/font"
faltan=""
for f in space_grotesk_regular space_grotesk_medium space_grotesk_semibold martian_mono_regular martian_mono_medium; do
    [ -s "$FUENTES/$f.ttf" ] || faltan="$faltan $f"
done
if [ -n "$faltan" ]; then
    echo "══ EL PAQUETE WEB NO TRAE LAS FUENTES:$faltan ══"
    echo "   Es un wasm viejo (caché de build) o roto. No se despliega."
    exit 1
fi
echo "── paquete web con sus cinco fuentes: ok ──"
