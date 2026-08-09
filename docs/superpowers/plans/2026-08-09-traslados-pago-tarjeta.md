# Plan — Traslados: el pago de la tarjeta deja de contar como egreso

## Contexto

Movi es una app KMP de finanzas personales con datos financieros **reales** del dueño.
Módulos: `:core` (modelos + repos + SQLDelight), `:shared` (Compose UI), `:server`
(Ktor + Exposed + Postgres), `:androidApp`, `:webApp`.

La rama `feat/ajustar-saldo` introdujo `isCashFlow(accountType, type)` en
`core/src/commonMain/kotlin/com/jvillada/movi/shared/model/CashFlow.kt`: los movimientos de
cuentas de deuda no son flujo de caja del mes. Regla actual:

- `LOAN` → nunca
- `CREDIT_CARD` → solo `EXPENSE` (la compra)
- cuentas de activo → siempre

Eso arregló un ingreso falso de $60.000.000, pero dejó al descubierto una duplicación
preexistente que antes quedaba disimulada.

## El problema

Una compra con tarjeta genera **dos** movimientos en la base:

1. `EXPENSE` en la cuenta `CREDIT_CARD` — la compra. Cuenta como egreso. Correcto.
2. Cuando se paga el extracto: `EXPENSE` en la cuenta de ahorros (`PAGO AUTOM TC MASTER PESOS`).
   También cuenta como egreso. **Es la misma plata, contada dos veces.**

Antes el `INCOME` de la tarjeta ("Pago a tarjeta") se sumaba a Ingresos y compensaba el
neto; al excluirlo correctamente, la duplicación quedó a la vista.

Medido contra los datos reales de junio de 2026: egresos $28.027.375, de los cuales
$1.042.955 son pagos de tarjeta desde ahorros que no deberían estar. El "Flujo del mes"
queda ~$1M por debajo de la realidad, todos los meses.

**Aparear por monto no funciona** — verificado contra los datos reales: el abono de
$808.940 está duplicado en la tarjeta (dos eventos, mismo monto, descripciones distintas)
y el pago en dólares de $234.015 no tiene contraparte del lado de la tarjeta.

## La decisión (tomada por el dueño)

El gasto con tarjeta se cuenta **cuando se compra**, no cuando se paga el extracto. El pago
del extracto es un traslado y no cuenta.

Se implementa con una **categoría dedicada**, no con heurísticas invisibles: el importador
la propone, el dueño la confirma y la puede corregir. Nada se recategoriza en silencio.

Nota deliberada: la **cuota de un crédito** (`LOAN`) pagada desde ahorros **sí sigue
contando** como egreso. Ahí no hay doble conteo — el desembolso del préstamo nunca se
registró como compra, así que la cuota es el único momento en que ese consumo aparece.

## Global Constraints

- **Los saldos de las cuentas no cambian.** Se siguen derivando de todos los eventos vía
  `signedDelta`/`computeBalances`. Esto solo decide qué se suma como ingreso/egreso.
- **`countsAsCashFlow` es derivado, nunca almacenado.** Sale de (tipo de cuenta, tipo de
  movimiento, categoría) y se recalcula en cada lectura. Lo que mande un cliente se ignora.
- **Nada se recategoriza automáticamente sin confirmación del dueño.** El server puede
  *proponer* candidatos; cambiarlos es una acción explícita.
- **Aislamiento por usuario:** toda consulta nueva filtra por `userId`. Sin excepción.
- **La app es offline-first en Android:** SQLDelight en `:core` (`nonWasmMain`), y el
  `SyncEngine` **solo empuja, nunca trae**. Cualquier mutación que el usuario deba ver en el
  teléfono tiene que espejarse en la DB local — ver el precedente en
  `LocalRepository.adjustCreditBalance`.
- Nombre exacto de la categoría nueva: **`"Pago de tarjeta"`**. Id: **`cat_card_payment`**.
- Nunca correr `./gradlew build` (tarda 48 minutos por los links de iOS release). Usar
  `:server:test`, `:core:jvmTest`, `:shared:compileDebugKotlinAndroid`,
  `:webApp:compileKotlinWasmJs`.
- La base local de Postgres (`psql -d movi`) tiene las finanzas reales del dueño. Se puede
  **leer** para verificar. **No escribir.**

---

## Task 1 — `isCashFlow` toma la categoría (`:core`, TDD)

**Archivos:** `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/CashFlow.kt`,
`core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Category.kt`, y los call sites.

1. Agregar la constante `const val CARD_PAYMENT_CATEGORY = "Pago de tarjeta"` en `CashFlow.kt`.
2. Agregarla a `PREDEFINED_CATEGORIES` en `Category.kt`:
   `Category("cat_card_payment", "Pago de tarjeta", "💳", "#B0A8B9", "EXPENSE")`.
   Ubicarla junto a las de EXPENSE, antes de `cat_other_exp`.
3. Cambiar la firma a `isCashFlow(accountType: AccountType, type: TransactionType, category: String): Boolean`.
   Regla, en este orden:
   - `category == CARD_PAYMENT_CATEGORY` → `false` (es un traslado, mire de qué cuenta salga)
   - `LOAN` → `false`
   - `CREDIT_CARD` → `type == TransactionType.EXPENSE`
   - resto → `true`
4. Actualizar todos los call sites: `server/.../balance/EventQueries.kt` (`withCashFlowFlag`),
   `server/.../routes/FinanceRoutes.kt`, `server/.../routes/AiRoutes.kt`,
   `server/.../routes/EventRoutes.kt`, `core/src/nonWasmMain/.../LocalRepository.kt`.
5. Actualizar el KDoc de `CashFlow.kt` explicando **por qué** el pago de tarjeta se excluye
   (doble conteo contra la compra) y **por qué la cuota de un LOAN no** (no hay compra previa
   registrada; la cuota es el único momento en que ese consumo aparece).

**Tests** (`core/src/commonTest/.../CashFlowTest.kt`, crear si no existe): la matriz completa
—cada `AccountType` × `INCOME`/`EXPENSE`— más el caso de la categoría en una cuenta de
activo, en una tarjeta y en un préstamo. Un test por comportamiento, con nombre que diga qué
protege.

---

## Task 2 — Detectar candidatos a pago de tarjeta (server, TDD)

**Objetivo:** que el dueño pueda encontrar los pagos de tarjeta que ya están en su base
categorizados como "Otros", sin que nada cambie solo.

1. Función pura `looksLikeCardPayment(description: String, category: String): Boolean` en
   `server/src/main/kotlin/com/jvillada/movi/server/balance/CardPayments.kt`.
   Patrones observados en los datos reales de Bancolombia (case-insensitive):
   `"pago autom tc"`, `"pago tarjeta"`, `"pago tc "`, `"abono tarjeta"`, `"pago a tarjeta"`.
   Debe devolver `false` si la categoría ya es `CARD_PAYMENT_CATEGORY` (nada que proponer).
   Documentar que es una **propuesta**, no una clasificación: por eso puede errar de más y
   está bien — el dueño confirma.
2. `GET /api/events/card-payment-candidates` (en `EventRoutes.kt`): devuelve los eventos
   `EXPENSE` de cuentas **de activo** del usuario que matcheen y que no estén anulados.
   No modifica nada. Filtrar por `userId`.
3. Enseñarle la categoría al importador: en `ClaudeStatementParser.kt`, agregar
   `"Pago de tarjeta"` a la lista de categorías de EXPENSE del prompt y reemplazar la regla
   actual de traslados (línea ~61, hoy dice que usen "Otros") por una que indique usar
   `"Pago de tarjeta"` cuando el movimiento sea el pago del extracto de una tarjeta.
4. En `SmsRoutes.categoryFor`, devolver `CARD_PAYMENT_CATEGORY` cuando el texto matchee
   `looksLikeCardPayment`.

**Tests:** unitarios de `looksLikeCardPayment` (los cinco patrones, el caso negativo
`"PAGO QR Dogger"` que es un gasto real y **no** un pago de tarjeta, y el caso ya
categorizado); y un test HTTP del endpoint que cubra aislamiento entre usuarios y que
confirme que **no** modifica ningún evento.

---

## Task 3 — Recategorizar un movimiento (server + `:core`)

**Archivos:** `EventRoutes.kt`, `WalletRepository.kt`, `WalletRepositoryImpl.kt`,
`LocalRepository.kt`.

1. `PUT /api/events/{id}/category` con body `{"category":"..."}`. Valida que el evento sea
   del usuario (404 si no), que la categoría no sea vacía y que no supere 60 caracteres
   (400). Responde el `FinancialEvent` actualizado **con `countsAsCashFlow` derivado**.
2. `suspend fun updateEventCategory(id: String, category: String): FinancialEvent` en
   `WalletRepository` + impl en `WalletRepositoryImpl` (usar el mismo idioma de
   `adjustCreditBalance`: mirar el status y lanzar `ApiException` con el cuerpo antes de
   deserializar).
3. En `LocalRepository`: llamar al server y **espejar** el resultado en SQLDelight, siguiendo
   el precedente de `adjustCreditBalance`. Sin el espejo, en Android el cambio es invisible
   (Movimientos/Análisis/Presupuestos leen de local y el `SyncEngine` no trae).
   Agregar la query `.sq` que haga falta.
4. `NoOpRepository` en `core/src/jvmTest` necesita el método nuevo.

**Tests:** HTTP (éxito, 404 de otro usuario, 400 de categoría vacía, y que la respuesta traiga
`countsAsCashFlow` correcto para una cuenta de activo con categoría "Pago de tarjeta"), más
uno en `LocalRepositoryTest` que verifique el espejo local.

---

## Task 4 — UI: cambiar la categoría y confirmar los candidatos

**Archivos:** `shared/src/commonMain/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreen.kt`
y una hoja nueva en el mismo paquete.

1. Hacer clickeable la fila de un movimiento (hoy no lo es) para abrir una hoja
   **"Cambiar categoría"**: lista las `PREDEFINED_CATEGORIES` del tipo del movimiento
   (`EXPENSE`/`INCOME`), marca la actual, y al elegir llama a `updateEventCategory` y
   refresca. Seguir el estilo de las hojas existentes (`CreditBalanceSheet` es un buen
   modelo: fondo `MinSurfaceContainerHigh`, esquinas 28.dp, estado `saving`/`error`,
   `toUserMessage()` para los errores).
2. Una entrada visible para los candidatos: cuando `GET /api/events/card-payment-candidates`
   devuelva alguno, mostrar en Movimientos una tarjeta discreta arriba —
   *"N pagos de tarjeta sin marcar"*— que abra la lista y permita marcarlos como
   "Pago de tarjeta" uno por uno. **Sin acción masiva silenciosa:** cada uno se confirma.
   Explicar en la hoja, en una línea, por qué importa: marcarlos evita contar dos veces la
   misma plata.

**Verificación:** `:shared:compileDebugKotlinAndroid` y `:webApp:compileKotlinWasmJs`.

---

## Task 5 — Verificación e2e contra los datos reales

1. Levantar el server contra la Postgres local y, **leyendo únicamente**, comprobar que
   `GET /api/events/card-payment-candidates` encuentra los pagos reales de junio de 2026:
   `PAGO AUTOM TC MASTER PESOS` ($808.940) y `PAGO AUTOM TC MASTER DOLAR` ($234.015).
2. Verificar con una consulta de solo lectura que, si esos dos quedaran categorizados como
   "Pago de tarjeta", los egresos de junio bajarían de $28.027.375 a $26.984.420 y las
   compras con tarjeta ($4.271.382) seguirían contando.
3. Reportar los números antes/después. No escribir en la base.
