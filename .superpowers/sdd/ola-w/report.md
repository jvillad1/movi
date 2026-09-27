# Ola W — reporte

## Estado: DONE

## Qué se hizo
- `shared/.../ui/transactions/TransactionsScreen.kt`: `diasVisibles` ahora recibe `accountTypes`
  (default `emptyMap()`, no rompe los llamadores existentes) y resta
  `montoPagoDeTarjetaEnElDia(filtered, accountTypes)` del total que arma para la cabecera de cada
  día («Flujo del día»), sin tocar `aporteAlFlujoDelDia`/`countsAsCashFlow` en `:core`. El call site
  (`val visibleDays = remember(...)`) pasa `accountTypes`, ya calculado más arriba en la pantalla.
- `shared/.../ui/transactions/LineaPagoDeTarjeta.kt`: `textoPagoDeTarjetaEnElDia` cambia de
  «Además, $X salieron a pagar tarjeta (ya contado al comprar)» (aclaraba una exclusión que ya no
  existe) a «De eso, $X fueron a pagar tarjeta — ya contado en tu gasto del mes cuando compraste,
  no se duplica.» (aclara que no se cuenta dos veces). `montoPagoDeTarjetaEnElDia` no cambió: sigue
  siendo la única fuente de esa suma, reusada ahora también por el total.
- Doc comments actualizados en ambos archivos para explicar la Ola W (y dejar constancia de que
  `aporteAlFlujoDelDia`/Disponible/Salió siguen sin cambios).

## Hallazgo (no bloqueante, no tocado)
`server/.../routes/EventRoutes.kt` (`GET /api/events/by-day`, línea ~447) usa
`aporteAlFlujoDelDia` para el `total` por día que manda el server — ese total NO incluye el pago de
tarjeta. En Movimientos esto no se nota porque `diasVisibles` **siempre recalcula** el total del
día en el cliente (ver el propio comentario de la función), así que lo que manda el server ahí se
descarta y se reemplaza por el del cliente. Ninguna otra pantalla que use `EventDay`/`getEventsByDay`
muestra ese total del server sin pasar por `diasVisibles` (revisé `PorRevisarScreen`,
`AccountDetailScreen` —que usa su propio `accountDayTotal`, no `aporteAlFlujoDelDia`—,
`PresupuestosScreen`, `GastosDelPresupuesto`). Si en el futuro se agrega un consumidor directo de
`/by-day` que muestre ese total sin recalcularlo, quedaría desalineado con lo que ve el dueño en
Movimientos — vale la pena tenerlo presente, pero el brief pedía explícitamente no tocar esa ruta.

## Pruebas actualizadas
- `shared/src/commonTest/.../PagoDeTarjetaEnElDiaTest.kt`: strings esperados actualizados al nuevo
  texto (la función `montoPagoDeTarjetaEnElDia` no cambió, solo `textoPagoDeTarjetaEnElDia`).
- `shared/src/androidUnitTest/.../PagoDeTarjetaEnMovimientosTest.kt`:
  - El día de puros pagos de tarjeta ahora espera «−$1.929.536» en el encabezado (no «$0») + la
    línea de contexto nueva.
  - Día sin pagos de tarjeta: sin cambios (el substring que buscaba se ajustó de «salieron a pagar
    tarjeta» a «fueron a pagar tarjeta»).
  - Día con gasto normal + pago de tarjeta: el total ahora suma los dos (−$18.500 + −$386.902 =
    −$405.402).

## Commits
- `41d60c95` — "Ola W: Flujo del día incluye el pago de tarjeta como salida real" (pusheado a
  `origin/ola-w`).

## Tests
CI completo (`--rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest
:shared:compileKotlinWasmJs`): BUILD SUCCESSFUL en 2m 56s, 4475 pruebas, 0 fallas, 0 errores.

## Dudas / decisiones tomadas con criterio propio
- El texto de la línea de contexto quedó en dos oraciones cortas («De eso, $X fueron a pagar
  tarjeta — ya contado en tu gasto del mes cuando compraste, no se duplica.»); si el dueño prefiere
  algo más corto se puede recortar sin tocar la lógica.
- `diasVisibles` quedó con `accountTypes` como parámetro con default `emptyMap()` (mismo patrón que
  ya usa esta pantalla en otras funciones) para no tener que tocar los ~10 call sites de prueba que
  no necesitan la nueva cuenta.
