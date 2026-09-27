# Ola V — reporte final

## Estado: DONE

## Commits
1. `6cf55adf` — «Cuentas de otros» ahora también se abre desde Ajustes (Parte 1).
2. (este) — Destino conocido asociado a una regla recurrente (Parte 2).

## Parte 1 — un lugar propio en Ajustes
- `shared/.../ui/mas/MasScreen.kt`: nueva ficha «Cuentas de otros» (ícono `Icons.Rounded.Groups`) → `Screen.Destinos`. Patrimonio → «Te deben» se deja intacto como segunda puerta.
- `shared/.../ui/Navigation.kt`, `shared/.../ui/destinos/DestinosScreen.kt`: KDoc actualizado (ya no dice que Patrimonio es la ÚNICA puerta).
- Los textos de `DestinosScreen`/`DestinoSheet`/`DetalleDelDestinoSheet` ya eran neutrales (no asumían «te deben plata»): no hizo falta tocarlos.
- `MasScreenTest.kt`: el invariante viejo («ninguna ficha de Ajustes es ya una pestaña») tenía a «Cuentas de otros» en la lista de PROHIBIDAS — se corrigió (única excepción, documentada) y se agregó un test de navegación.

## Parte 2 — destino conocido asociado a una regla recurrente
- `core/.../shared/model/Finance.kt`: `RecurringRule.destinoConocidoId: String? = null`, mismos 3 estados de wire que `accountId` (null = no tocar, "" = quitar, id = asociar).
- `server/.../db/Tables.kt`: columna nueva `recurring_rules.destino_conocido_id` (nullable, vía `createMissingTablesAndColumns`, DDL seguro).
- `server/.../routes/ReminderRoutes.kt`: `toRule()`, POST y PUT (3 estados) + `destinoConocidoIdIfOwned`.
- `server/.../routes/DestinoRoutes.kt`: borrar un destino suelta la referencia en las reglas que lo tenían asociado (mismo criterio que `AccountRoutes` con `accountId`), no las borra.
- `server/.../reminders/OccurrenceMatching.kt`: `nombrePegaCon` ahora también es verdadero cuando el texto del movimiento nombra el número de cuenta (o el nombre) del destino asociado a la regla — reusa `vaHaciaElDestino` de `:core` (la misma función que ya usa «Cuentas de otros» para juntar «lo que le mandaste») en vez de duplicar el regex de `MemoriaDeCategorias`. Pesa **igual** que el nombre (`SENA_DEL_NOMBRE`), con el razonamiento en el KDoc. Todas las funciones del archivo reciben `destinos: Map<String, DestinoConocido> = emptyMap()` — default vacío, cero cambio de comportamiento para quien no lo pasa.
- El mapa de destinos se resuelve y enhebra hasta los tres consumidores reales: `ReminderRoutes.ocurrenciasReales` (checklist del período + candidatos de Recurrentes), `PeriodosRoutes.pagosFijosDelPeriodo`/`existiaEnElPeriodo`/`conPagoAutomaticoAntes` («Tus períodos»), y `DashboardRoutes.parteFijaDelDisponible` → `PagosDelChecklist.parteFijaDelChecklist` (el «Disponible» del Inicio). En los tres, la lectura de `known_destinations` es **condicional**: solo se dispara si alguna regla real tiene `destinoConocidoId != null`. Esto no es solo una optimización — evita romper los esquemas de prueba acotados que no crean esa tabla (ver «hallazgo» abajo).
- UI: `shared/.../ui/recurrentes/CreateRecurringRuleSheet.kt` — selector opcional «DESTINO CONOCIDO» (solo en GASTO, nunca en suscripción ni en ingreso), con los mismos 3 estados de wire (`destinoParaElWire` en `RecurrentesLogic.kt`, gemelo de `cuentaParaElWire`), y un efecto que suelta la elección si el tipo deja de ser gasto (mismo patrón que ya usa la cuenta).
- Tests nuevos en `OccurrenceMatchingTest.kt` (4): el caso exacto del brief («Tía Caro» / destino «Caro» *31973270756, texto sin el nombre) entra como candidato Y es concluyente; sin el mapa de destinos no cambia nada; una regla sin destino asociado no se contagia de los destinos de otra; un id que no resuelve en el mapa (destino borrado o no traído) no rompe nada.

## Hallazgo durante la verificación
Correr `:server:test` con **varias clases de prueba juntas** en un solo comando `--tests A --tests B --tests C --tests D --tests E` (5 clases) disparó fallas cruzadas de «Tabla "known_destinations" no encontrada» en clases que ni siquiera tocan esa tabla en su código (p.ej. `DashboardRoutesTest`). Cada clase por separado pasa siempre. Esto apunta a una fragilidad preexistente de la conexión H2 global de Exposed (`Database.connect` es un singleton por JVM) cuando Gradle intercala/paraleliza clases de test en el mismo proceso — **no es una regresión de esta ola** (se reprodujo igual antes de mi primer intento de arreglo, cuando cambié la carga de destinos a incondicional, y desapareció al correr cada clase sola). La suite completa (`:server:test`, como la corre CI) no lo dispara: corrió verde. Vale la pena investigarlo aparte si se vuelve a ver.

Sí encontré y arreglé un problema real, más chico, de la misma familia: mi primera versión leía `known_destinations` **incondicionalmente** dentro de `ocurrenciasReales` — eso SÍ rompía in‑isolation tests que arman su propio esquema acotado sin esa tabla (`ReminderRoutesTest`, `DashboardRoutesTest`, `PeriodosRoutes`), porque hasta esta ola ningún camino de recordatorios la tocaba. Se resolvió haciendo la lectura condicional a que alguna regla real tenga `destinoConocidoId` puesto.

## Verificado
- `JAVA_HOME=.../jbrsdk-21 ./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs` → **BUILD SUCCESSFUL** (después del arreglo de `MasScreenTest`).
- Cada suite tocada corrida también individualmente en el camino: `OccurrenceMatchingTest`, `ReminderRoutesTest`, `DashboardRoutesTest`, `DestinoRoutesTest`, `PagosDelChecklistTest`, `DisponibleVeLoEmparejadoTest`, `MasScreenTest`.

## Dudas / decisiones que valen una segunda mirada
- El nombre de la ficha en Ajustes es «Cuentas de otros» (igual que el título de la pantalla y no «Cuentas de terceros» / «Personas y cuentas») para que Ajustes y la pantalla digan lo mismo — el brief dejaba elegir.
- La seña del destino se hizo pesar EXACTAMENTE igual que el nombre (`SENA_DEL_NOMBRE`), no una seña nueva propia — razonado en el KDoc de `nombrePegaCon`. Si el dueño prueba el caso real de «Tía Caro» y el peso se siente mal calibrado (por ejemplo, dos destinos que compiten), vale la pena revisarlo con datos reales.
- No se creó ninguna noción nueva de «esto es un traspaso a un tercero» en el modelo: el selector de destino se ofrece en CUALQUIER regla de gasto (real, no sintética), porque el brief no pedía un campo nuevo para distinguir tipos de gasto y cualquier gasto puede en los hechos ser una transferencia a alguien.
