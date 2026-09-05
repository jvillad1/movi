#!/usr/bin/env bash
# Espera a que producción reporte un commit determinado en `/version`, y falla si no llega.
#
# **Por qué existe.** Un merge a master NO es un despliegue. Cuando el build de Railway falla, la
# instancia vieja sigue arriba y contesta 200 a todo: el 2026-09-02 producción se quedó cuatro
# merges atrás durante horas y nadie se enteró. Este script convierte ese silencio en una
# notificación de GitHub.
#
# Es un archivo aparte del workflow —y no un `run:` embebido— para poder ejercitar el camino de
# falla a mano, que es lo único que prueba que la alarma suena:
#
#   ./scripts/esperar-despliegue.sh <sha-esperado> [minutos]
#   MOVI_URL=http://localhost:8080 ./scripts/esperar-despliegue.sh abc1234 1
#
# Salida: 0 si producción reportó el commit; 1 si se agotó el plazo (con el diagnóstico de qué
# estaba viendo cuando se agotó).

set -uo pipefail

URL="${MOVI_URL:-https://movi-project-production.up.railway.app}"
ESPERADO="${1:-}"
MINUTOS="${2:-35}"
INTERVALO="${INTERVALO:-20}"

if [ -z "$ESPERADO" ]; then
  echo "uso: $0 <sha-esperado> [minutos]" >&2
  exit 2
fi

LIMITE=$(( $(date +%s) + MINUTOS * 60 ))
INTENTOS=0

# Estado de la última respuesta, para que el mensaje de falla diga QUÉ estaba pasando y no solo
# «se agotó el tiempo». Los tres diagnósticos posibles son distintos y se arreglan distinto.
ULTIMO_CODIGO="(ninguna respuesta todavía)"
ULTIMO_COMMIT=""
ULTIMO_DIAGNOSTICO="producción no contestó ni una vez"
ULTIMA_GUIA="Producción no respondió. Puede ser la red del runner o la instancia caída."

echo "Esperando que $URL/version reporte $ESPERADO (hasta $MINUTOS minutos, sondeo cada ${INTERVALO}s)."

while [ "$(date +%s)" -lt "$LIMITE" ]; do
  INTENTOS=$((INTENTOS + 1))

  # `|| true`: un error de red NO es una falla del despliegue. Se sigue sondeando; si el problema
  # persiste hasta el final, el diagnóstico lo dice.
  RESPUESTA=$(curl -s --max-time 15 -w $'\n%{http_code}' "$URL/version" 2>/dev/null || true)
  ULTIMO_CODIGO=$(printf '%s' "$RESPUESTA" | tail -n1)
  CUERPO=$(printf '%s' "$RESPUESTA" | sed '$d')
  [ -n "$ULTIMO_CODIGO" ] || ULTIMO_CODIGO="(sin respuesta: no se pudo conectar)"

  # Se extrae con una forma estricta a propósito. Hoy, antes de que este cambio se despliegue,
  # `/version` cae en el catch-all que sirve la PWA y devuelve 200 con el index.html: un parseo
  # laxo podría tomar cualquier cosa de ese HTML por una respuesta. Solo cuenta un campo
  # `"commit"` con algo que parezca un SHA.
  ULTIMO_COMMIT=$(printf '%s' "$CUERPO" \
    | grep -oE '"commit"[[:space:]]*:[[:space:]]*"[0-9a-fA-F]{7,40}"' \
    | grep -oE '[0-9a-fA-F]{7,40}' \
    | head -n1 \
    | tr 'A-F' 'a-f')

  if [ -n "$ULTIMO_COMMIT" ]; then
    # Prefijo en cualquiera de los dos sentidos: producción puede reportar el SHA completo y el
    # esperado venir corto, o al revés.
    case "$ESPERADO" in "$ULTIMO_COMMIT"*) COINCIDE=1 ;; *) COINCIDE=0 ;; esac
    case "$ULTIMO_COMMIT" in "$ESPERADO"*) COINCIDE=1 ;; esac

    if [ "$COINCIDE" = "1" ]; then
      echo ""
      echo "✓ Producción está corriendo $ULTIMO_COMMIT (tras $INTENTOS sondeos)."
      exit 0
    fi
    ULTIMO_DIAGNOSTICO="producción sigue en otro commit ($ULTIMO_COMMIT)"
    ULTIMA_GUIA="El build de Railway falló y la instancia vieja quedó sirviendo — con 200 en todo,
  que es por lo que esto no se nota solo. Revisá el log del despliegue en Railway."
  elif printf '%s' "$CUERPO" | grep -q '"commit"[[:space:]]*:[[:space:]]*null'; then
    ULTIMO_DIAGNOSTICO="/version existe pero contesta «no lo sé»: el proceso no conoce su commit"
    ULTIMA_GUIA="El despliegue SÍ llegó; lo que falta es la variable. Verificá que el servicio de
  Railway exponga RAILWAY_GIT_COMMIT_SHA al proceso (o definí MOVI_COMMIT_SHA)."
  elif [ "$ULTIMO_CODIGO" = "200" ]; then
    ULTIMO_DIAGNOSTICO="/version no existe en producción (devuelve el index.html de la PWA con 200)"
    ULTIMA_GUIA="La versión que está sirviendo es anterior a este endpoint: o el despliegue no
  llegó, o el build falló. Revisá el log del despliegue en Railway."
  elif [ "$ULTIMO_CODIGO" = "000" ] || [ "$ULTIMO_CODIGO" = "(sin respuesta: no se pudo conectar)" ]; then
    # curl devuelve 000 cuando no hubo respuesta HTTP: conexión rechazada, DNS, timeout.
    ULTIMO_DIAGNOSTICO="no se pudo conectar con $URL"
    ULTIMA_GUIA="Ni siquiera contestó. O la instancia está caída, o hay un problema de red."
  else
    ULTIMO_DIAGNOSTICO="producción respondió $ULTIMO_CODIGO sin un commit legible"
    ULTIMA_GUIA="Respuesta inesperada. Mirá a mano: curl -i $URL/version"
  fi

  printf '.'
  sleep "$INTERVALO"
done

echo ""
echo "══════════════════════════════════════════════════════════════════════════"
echo "  PRODUCCIÓN NO REPORTÓ ESTE COMMIT EN $MINUTOS MINUTOS"
echo "══════════════════════════════════════════════════════════════════════════"
echo "  Esperado : $ESPERADO"
echo "  URL      : $URL/version"
echo "  Sondeos  : $INTENTOS (último HTTP $ULTIMO_CODIGO)"
echo "  Qué pasa : $ULTIMO_DIAGNOSTICO"
echo ""
echo "  $ULTIMA_GUIA"
echo "══════════════════════════════════════════════════════════════════════════"
exit 1
