# Ola 5 — T3 (F55, F17, F39)

Rama: `ola5-t3` (worktree aislado). Alcance tocado: server (`AccountRoutes.kt`, `FinanceRoutes.kt` § budgets, `SubscriptionSync.kt`, tests), `:core` (repos/modelo), UI solo `AccountDetailScreen.kt` + `DeleteAccountSheet.kt` nueva, `PresupuestosScreen.kt`, `SuscripcionesScreen.kt`. No se tocó `CreateAccountSheet`, `AccountsScreen`, `InversionesScreen`, `CreditosScreen` (los toca otra tarea en paralelo).

## F55 — Eliminar cuenta

**Server** (`AccountRoutes.kt`): `DELETE /api/accounts/{id}`. En una sola transacción de Exposed (`dbQuery { }`): verifica que la cuenta exista y sea del usuario (404 si no), junta los ids de sus eventos, borra `card_payment_dismissals` y `void_events` que apunten a esos eventos, borra los eventos, borra `credit_terms` si es un LOAN, y por último la cuenta. 204 al borrar. Segundo DELETE da 404 (no hay idempotencia, tal como pedía el brief).

**:core**: `WalletRepository.deleteAccount(id)` nueva; `WalletRepositoryImpl` la implementa con el mismo idioma de `ApiException` que `adjustCreditBalance`/`updateEventCategory` (chequea `isSuccess()` antes de `.body()`). `LocalRepository.deleteAccount` es **remote-first sin fallback local**: llama a `remote.deleteAccount` primero y, si falla, **propaga la excepción tal cual** (no la atrapa) en vez de borrar solo local — documentado en el KDoc: un borrado local-only dejaría la fila con `syncedAt` no-nulo (nunca se reintentaría) y la cuenta resucitaría con todos sus movimientos la próxima vez que el server la devolviera en un GET, que es peor que no borrar nada. Si el remote sí borra, se espeja localmente: `financial_event` de esa cuenta (`deleteByAccount`, query nueva en `FinancialEvent.sq`) y la fila de `account` (`deleteById`, query nueva en `Account.sq`).

**UI** (`AccountDetailScreen.kt` + `DeleteAccountSheet.kt` nueva): al final de la lista de movimientos, texto rojo "Eliminar cuenta". Abre una hoja de confirmación con el texto exacto:

> `Se borra "{nombre}" y {su 1 movimiento | sus N movimientos}. Esto no se puede deshacer.`

Botones "Cancelar" / "Eliminar cuenta". Si `deleteAccount` falla, el mensaje inline es el pedido literalmente por el brief: **"No se pudo eliminar — revisa tu conexión"** (no se usa el `toUserMessage()` genérico — la causa casi siempre es de red y ese mensaje es más directo). Al confirmar con éxito: `goBack(Screen.Accounts)`. No hace falta pedirle un refresh a `AccountsScreen`: `App.kt` desmonta del todo cada pantalla al salir de su rama del `when` (solo conserva `rememberSaveable`, y `AccountsScreen` usa `remember` plano para su `refreshKey`), así que al volver recarga sola.

## F17 — Renombrar presupuesto

**Server** (`FinanceRoutes.kt`): `PUT /api/budgets/{category}/rename`, body `{"newCategory": "…"}` (tipo nuevo `RenameBudgetRequest` en `core/.../model/Finance.kt`). En una transacción: 404 si la categoría vieja no existe, 409 si la nueva ya está en uso (salvo que sea la misma — renombrar al mismo nombre es no-op válido), borra e inserta conservando `monthlyLimit`. No toca `financial_event` — el cruce presupuesto↔gasto sigue siendo por nombre.

**:core**: `WalletRepository.renameBudget(category, newCategory): Budget`; `WalletRepositoryImpl` con el mismo idioma `ApiException`; `LocalRepository.renameBudget` delega directo a `remote` (los presupuestos no tienen tabla local, igual que create/update/delete).

**UI** (`PresupuestosScreen.kt`): en la hoja "Editar presupuesto" el campo de categoría pasó de `categoryEditable = false` a `true` (reusa `CategoryField`, el mismo campo con sugerencias que ya usaba "Nuevo presupuesto"). Debajo, solo quando se está editando un presupuesto existente (`onDelete != null`), aparece la advertencia:

> `El gasto se cruza por nombre: si renombras "{categoría vieja}" a otra cosa, los movimientos que digan "{categoría vieja}" dejan de contar aquí.`

"Guardar" llama a `renameBudget` primero si el nombre cambió (y solo entonces a `updateBudget` si además cambió el monto, porque el rename ya conserva el límite viejo).

## F39 — Suscripciones: nada nace activo

**Server** (`SubscriptionSync.kt`): `statusForNew` dejó de mirar la confianza — ahora **siempre** devuelve `CANDIDATE`. `SubStatus.AUTO` deja de producirse (el enum se queda). En `applyExisting` (el upsert por `merchantKey`), las filas `AUTO` existentes se tratan igual que `CONFIRMED` — se refrescan `amount`/`lastSeen`/`occurrences`/`confidence` pero **no** se les toca el `status`; sin este ajuste, `refreshRow` las hubiera bajado a `CANDIDATE` en cada re-scan (porque `statusForNew` ahora siempre da `CANDIDATE`), y una suscripción que el dueño nunca vio pendiente hubiera reaparecido pidiendo confirmación. `DISMISSED` seguía (y sigue) respetada sin cambios — no se resucita.

**Tests de server**: `SubscriptionRoutesTest` — el test que antes esperaba `netflix=AUTO` ahora espera `CANDIDATE` (confianza HIGH incluida) y total mensual en `0`; test nuevo `a legacy AUTO row survives re-detect without downgrading to CANDIDATE` siembra una fila `AUTO` directo en la DB (ya no hay forma de producirla vía `/detect`) y confirma que un re-scan no la baja; el test de "confirmed no se degrada" se ajustó a `26_900` (solo youtube CONFIRMED, netflix ya no suma AUTO). `StatementRoutesTest` — el import de extracto que antes esperaba `netflix=AUTO` ahora espera `CANDIDATE`.

**UI** (`SuscripcionesScreen.kt`): la sección ya existía como "Candidatos a revisar" con "Confirmar"/"Descartar" — se renombró al texto exacto del brief: **"Detectadas · por confirmar"**, y el botón de descarte pasó de "Descartar" a **"No es"**. Ya usaba `updateSubscription` (verificado: el `PUT /api/subscriptions/{id}` ya aceptaba cualquier `status` en el body, no hizo falta tocar `SubscriptionRoutes.kt`). "Activas" y el total mensual ya sumaban `AUTO+CONFIRMED` desde la Ola 4 — se dejó así, coherente con el brief.

## Textos nuevos (tuteo neutro, sin voseo)

- "Se borra \"{nombre}\" y sus N movimientos. Esto no se puede deshacer."
- "No se pudo eliminar — revisa tu conexión"
- "Eliminar cuenta" / "Cancelar" / "Eliminando…"
- "El gasto se cruza por nombre: si renombras \"X\" a otra cosa, los movimientos que digan \"X\" dejan de contar aquí." (ojo: "acá" está en la blacklist de `VoseoScanTest` — se usó "aquí")
- "Detectadas · por confirmar" / "No es"

## Verificación (salida real)

```
$ ./gradlew :server:test :core:jvmTest --console=plain -q
(sin salida — 309 tests de server + 79 de :core, todos verdes)

$ ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew \
    :server:test :core:jvmTest :shared:compileDebugKotlinAndroid :webApp:compileKotlinWasmJs \
    --console=plain
...
BUILD SUCCESSFUL in 880ms
31 actionable tasks: 31 up-to-date
```

No se corrió `:shared:compileKotlinIosSimulatorArm64` — no se tocó ningún `expect`/`actual`.

## Dudas / decisiones que tomé sin preguntar

1. **AUTO viejas y `applyExisting`**: el brief solo decía "las filas AUTO viejas se tratan como confirmadas" sin especificar dónde. Decidí que también aplica al upsert de re-detección (`applyExisting`), no solo a la lectura (`resultFor`) — si no, un re-scan las hubiera degradado a CANDIDATE, lo cual me pareció que contradice "se tratan como confirmadas". Lo documenté en el código; avisar si la intención era otra.
2. **Advertencia de rename**: la muestro solo al editar un presupuesto existente (`onDelete != null`), no en "Nuevo presupuesto" — ahí no aplica (no hay nombre viejo que romper).
3. **Mensaje de error de borrado de cuenta**: usé el texto literal del brief en vez de `toUserMessage()` genérico — es un mensaje fijo, no distingue "sin red" de "el server rechazó" (ambos casos hoy son indistinguibles desde `LocalRepository.deleteAccount`, que no atrapa la excepción).
