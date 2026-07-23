# Trigger de detección de suscripciones al importar — diseño

**Fecha:** 2026-07-22
**Alcance:** solo `:server`. Cierra el deferido explícito del spec del subscription
tracker (`2026-06-16-subscription-tracker-design.md`, "Fuera de alcance": *"auto-detección
al importar (v1 es por botón 'Re-escanear', el trigger en import se puede sumar
después)"*).

## Problema / valor

Hoy el usuario debe abrir Suscripciones y tocar "Re-escanear" después de cada import de
extracto. El sistema debería enterarse solo: importar un extracto es exactamente el
momento en que aparecen datos nuevos de suscripciones.

## Decisiones (locked)

- **Silencioso** (elegido por el usuario): sin cambios de wire model ni de UI. Las
  suscripciones nuevas aparecen la próxima vez que se abre la pantalla.
- **Síncrono, no fire-and-forget:** la detección es en memoria sobre los eventos del
  usuario (milisegundos); una coroutine suelta haría los fallos invisibles y el testing
  frágil.
- **Un fallo de detección JAMÁS falla el import:** el trigger va envuelto en
  `runCatching` + log de warning. El import ya persistió sus eventos; la detección es
  best-effort (el botón Re-escanear sigue existiendo como fallback manual).
- **Solo el import de extractos dispara** (`POST /api/statements/import`). La
  confirmación de SMS no (un evento suelto casi nunca cambia una detección — YAGNI;
  sumable después con la misma función).

## Diseño

1. **Extraer** el cuerpo de `POST /api/subscriptions/detect` (filtro Famirios +
   `loadNonVoidedEvents` + `detectSubscriptions` + upsert por estados con SAVEPOINT) a:

   ```kotlin
   // server/src/main/kotlin/com/jvillada/movi/server/subscriptions/SubscriptionSync.kt
   /** Corre la detección y el upsert para [uid]. Best-effort: el caller decide si un fallo importa. */
   suspend fun runSubscriptionDetection(uid: String)
   ```

   La ruta `/detect` queda: `runSubscriptionDetection(uid)` + `call.respond(resultFor(uid))`.
   Comportamiento observable idéntico (los tests HTTP existentes son la red de seguridad).

2. **Disparar** al final del handler `POST /api/statements/import`
   (`StatementRoutes.kt`), después de persistir eventos/reconciliaciones y ANTES del
   respond:

   ```kotlin
   runCatching { runSubscriptionDetection(uid) }
       .onFailure { call.application.log.warn("detect-on-import falló para $uid: ${it.message}") }
   ```

   (Si el logger de application no está accesible en ese scope, usar el patrón de
   logger existente en el archivo/proyecto — p.ej. `org.slf4j.LoggerFactory` como hace
   `ReminderScheduler`.)

## Testing

- **Sin regresión:** la suite existente de `SubscriptionRoutesTest` (detect, upsert,
  estados, concurrencia, Famirios) debe pasar sin cambios — cubre la extracción.
- **Trigger e2e (HTTP, harness H2):** nuevo test en `StatementRoutesTest` (archivo
  nuevo, patrón `CreditRoutesTest`): sembrar cuenta CREDIT_CARD del usuario A; `POST
  /api/statements/import` con un `ImportDecision` cuyos `imports` traen 3
  `ParsedTransaction` de `PAYU*NETFLIX` 44.900 en meses consecutivos → 200; LUEGO `GET
  /api/subscriptions` (sin llamar `/detect`) muestra netflix con status AUTO. Segundo
  test: el import de un extracto sin patrones recurrentes no crea suscripciones.
- **Compile:** `:server:test` verde completo.

## Fuera de alcance (futuro)

Aviso en la UI tras importar ("N suscripciones detectadas"); trigger en confirmación de
SMS; trigger al void de eventos (una detección queda potencialmente obsoleta hasta el
próximo import/re-escaneo — aceptado).
