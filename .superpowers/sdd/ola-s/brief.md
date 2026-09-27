# Ola S — Bug real: las cuotas de crédito nunca se emparejan con un movimiento

Descubierto en vivo (2026-09-27): el dueño confirmó un SMS de $1.300.000 con categoría «Cuota de crédito» (exacta, tal cual la exige la app) para pagar la cuota de «Crédito Mamá», y el checklist de Plan lo siguió mostrando como sin pagar. Encontré la causa exacta leyendo el código.

## El bug
`candidatosPuntuados` (`server/.../reminders/OccurrenceMatching.kt` ~línea 230-234) exige que el NOMBRE o la CATEGORÍA del movimiento coincidan con los de la regla (`categoriaPega = claveComparableDeNombre(event.category) == claveComparableDeNombre(rule.category)`); si ninguno coincide, el movimiento ni siquiera entra como candidato.

Para créditos y tarjetas, la "regla" no es una fila de `recurring_rules`: se arma al vuelo desde `credit_terms`/`card_terms` con `virtualRuleFor` en:
- `server/.../reminders/CreditReminders.kt` (~línea 23): `category = "Créditos"`
- `server/.../reminders/CardReminders.kt` (~línea 52): `category = "Créditos"`

Pero la categoría real que usan los movimientos de cuota en TODA la app es `CUOTA_CATEGORY = "Cuota de crédito"` (`core/.../shared/model/PagoDeCuota.kt:18`) — un texto distinto. Como el texto de un SMS de transferencia nunca dice el nombre del crédito («Cuota Mamá»/«Cuota Crédito Mamá»), el nombre tampoco pega casi nunca. Resultado: **ningún movimiento, con ninguna categoría, puede emparejarse jamás con la cuota de un crédito o de una tarjeta**, para ningún usuario — no es un caso aislado de "Mamá", es sistémico.

## El arreglo
En los dos archivos, cambia `category = "Créditos"` por `category = CUOTA_CATEGORY` (importa `com.jvillada.movi.shared.model.CUOTA_CATEGORY` de `core`). Verifica que no rompe nada que dependa de que esa regla sintética diga "Créditos" (busca todo lo que lee `.category` de una regla venida de `loadCreditRulePairs`/`virtualRuleFor` o del equivalente en tarjetas, por ejemplo en `ReminderRoutes.kt`, `PagosDelChecklist.kt`, la UI de Plan que pinta "En qué se va" por categoría — si algo agrupaba estas reglas bajo el rótulo "Créditos" a propósito, dilo como hallazgo y decide con criterio si conviene mantener un rótulo de VISUALIZACIÓN distinto del de MATCHING, o si de verdad no hay tal necesidad y el cambio es directo).

## Pruebas
- Servidor: una regla virtual de crédito (o de tarjeta) con un movimiento cuya única evidencia es la categoría «Cuota de crédito» (sin que el nombre coincida) debe entrar como candidato y completar el ítem — prueba que falla con el bug actual y pasa con el arreglo.
- Verifica que las pruebas existentes de `CreditReminders`/`CardReminders`/`PagosDelChecklist` que ya cubrían "Créditos" como categoría (si las hay) se actualicen a "Cuota de crédito" y seguían con el comportamiento correcto (no rotas por casualidad).
- Prueba de integración/servidor si es posible: reproducir el escenario real del dueño (crédito «Mamá» $1.300.000/mes, un movimiento con esa categoría y monto exacto dentro del período) y confirmar que el checklist lo marca resuelto.

## Restricciones
No toques `CUOTA_CATEGORY`, `esCuotaQueSaleDelBolsillo`, ni el resto de las reglas de plata (Ola J, K, O) más allá de este cambio puntual. Español neutro con tuteo si agregas algún texto (no debería hacer falta). No toques la base de producción — esta corrección hay que desplegarla y el ítem "Cuota Crédito Mamá" del dueño debería resolverse SOLO al desplegar, sin tocar sus datos (ya tiene el movimiento bien categorizado). Commit + push tras cada commit. Un solo Gradle a la vez. CI completo con `--rerun-tasks` al final. Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>.
