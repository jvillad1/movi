# Ola V — Gestionar cuentas conocidas de terceros como algo propio, no escondido en «Te deben»

Pedido del dueño (2026-09-27): «ayúdame a poder gestionar dichas cuentas o registrarlas para poder asociar movimientos, recurrencias y demás que suceden a esas cuentas». La función YA EXISTE en el código (`core/.../shared/model/DestinoConocido.kt`, `server/.../sms/DestinosDelDueno.kt`, tabla `known_destinations`, `ui/destinos/DestinosScreen.kt` + `DestinoSheet.kt`), pero:
1. Solo se llega desde **Patrimonio → «Te deben»** (`AccountsScreen.kt:447`, `SeccionDeTeDeben`), un lugar que no tiene nada que ver con "registrar quién es esta cuenta": el dueño no la encontró buscando en Ajustes.
2. Solo sirve para RENOMBRAR (`«Transferencia a Caro»` en vez del número), en tres lugares puntuales (`DestinosDelDueno.kt`, KDoc: «el detalle de un mensaje del banco, la push del sync y la revisión de un extracto»). No se puede asociar un destino conocido a una regla recurrente para que Movi reconozca solo sus pagos.

## Qué construir (dos partes independientes, en dos commits)

### 1. Un lugar propio para gestionarlas
- Agrega una entrada a **Ajustes** (`ui/perfil`/donde esté la pantalla de Ajustes con las tarjetas Perfil/Categorías/Documentos/Compartir/Movi AI/Mensajes del banco) llamada algo como **«Cuentas de terceros»** o **«Personas y cuentas»** (elige el nombre que mejor calce con el tono de la app — corto, sin tecnicismo) que navegue a `Screen.Destinos` (la pantalla ya existe, `DestinosScreen.kt`).
- Deja también el acceso desde Patrimonio → «Te deben» tal como está (no lo quites, solo deja de ser la ÚNICA puerta).
- Si `DestinosScreen`/`DestinoSheet` tienen textos que asumen que TODO destino es alguien que le debe plata al dueño (revisa los textos), ajústalos para que tengan sentido también para "una cuenta que reconozco, sin que nadie deba nada" — sin romper el caso de "Te deben" que ya funciona.

### 2. Asociar un destino conocido a una regla recurrente
Hoy `candidatosPuntuados` (`server/.../reminders/OccurrenceMatching.kt`) reconoce un movimiento por NOMBRE (`nombrePega`) o por CATEGORÍA (`categoriaPega`) + opcionalmente la CUENTA PROPIA del dueño (`rule.accountId`, la cuenta de la que sale el pago) — pero no tiene forma de reconocer que un traspaso va a una cuenta de un TERCERO en particular, aunque el dueño ya la registró con nombre (`known_destinations`).
- Agrega la posibilidad de que una `RecurringRule` (una regla real, no las sintéticas de crédito/tarjeta) guarde opcionalmente el ID de un `DestinoConocido` (nueva columna en `recurring_rules`, nullable, no rompe nada existente).
- En `candidatosPuntuados`, si la regla tiene un destino conocido asociado, un movimiento cuyo texto trae el número de cuenta de ESE destino (mismo patrón `CUENTA = Regex("""\*\s?(\d{4,})""")` que ya usa `core/.../MemoriaDeCategorias.kt`) cuenta como una seña fuerte — decide con criterio si equivale al peso del nombre (`SENA_DEL_NOMBRE`) o es una seña nueva propia; documenta la decisión en el KDoc, con el mismo cuidado que el resto del archivo (es dinero real).
- En la UI de crear/editar una regla recurrente (`ui/recurrentes/CreateRecurringRuleSheet.kt`), si el tipo de gasto es un traspaso a un tercero, ofrece elegir (opcional) uno de los destinos ya registrados.
- Caso a probar: «Tía Caro» ($100.000/mes, ya existe como regla) asociada al destino «Caro» (`*31973270756`) — una transferencia futura a esa cuenta debe reconocerse como el pago de esa regla aunque el texto no diga «Tía Caro».

## Pruebas
- Servidor: regla con destino conocido asociado + movimiento que solo trae el número de esa cuenta en el texto (sin nombre) → entra como candidato y completa el ítem.
- Sin destino asociado, el comportamiento de hoy no cambia (columna nueva nullable, default sin romper nada).
- UI: Ajustes tiene la entrada nueva y navega a Destinos; crear/editar una regla recurrente ofrece elegir un destino conocido.

## Restricciones
No toques `esDeTuPlata`, `saldoDeTuPlata`, ni las reglas de plata de créditos/tarjetas (Ola J/O/S). Español neutro con tuteo (VoseoScanTest). No toques la base de producción — la migración de columna nueva (nullable) la corres tú solo, sin tocar datos existentes. Commit + push tras cada commit. Un solo Gradle a la vez. CI completo con `--rerun-tasks` al final. Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>.
