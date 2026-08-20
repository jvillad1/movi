# Ola 6 — T2 (F26, F38)

Rama: `ola6-t2` (worktree aislado, creada desde HEAD = `480d4ce`, ola 5 ya mergeada). Alcance tocado: server (`Tables.kt` § `Goals`, `GoalRoutes.kt` nueva, `FinanceRoutes.kt` — se quitó el `GET /api/goals` hardcodeado —, `SubscriptionRoutes.kt` § POST manual, `Routing.kt`, `DatabaseFactory.kt`, tests), `:core` (`Finance.kt` § `Goal`, `Subscription.kt` § `CreateSubscriptionRequest`, `WalletRepository`/`WalletRepositoryImpl`/`LocalRepository`/`NoOpRepository`), UI (`MetasScreen.kt`, `GoalSheet.kt` nueva, `SuscripcionesScreen.kt`, `CreateSubscriptionSheet.kt` nueva). No se tocó `PerfilScreen.kt`, `DashboardScreen.kt`, `DashboardLogic.kt`, `AIChatScreen.kt`.

## F26 — Crear meta de ahorro

**Server** (`GoalRoutes.kt`, nueva): tabla `Goals` en `Tables.kt` — `id PK, user_id, name, target LONG, account_id, target_date VARCHAR(10)? , created_at`. `saved` NO vive en la tabla: `GET /api/goals` lo deriva siempre del saldo real de la cuenta con `accountCopValue` (el mismo cálculo que ya usa `AccountRoutes.kt` para enriquecer cuentas) — así nunca se desincroniza del dinero real. `POST`/`PUT` validan nombre no vacío, `target > 0`, que la cuenta exista y sea del usuario (404 si no) y que no sea de deuda (`AccountType.group == AccountGroup.DEUDA` → 422 "Elige una cuenta de Dinero o Inversión — una meta no se ahorra en una deuda"). `DELETE` normal, 404 en el segundo borrado. Aislamiento por `userId` en las cuatro rutas. El `get("/api/goals") { call.respond(emptyList<Goal>()) }` hardcodeado que vivía en `FinanceRoutes.kt:41` se quitó (import de `Goal` también). Registrada en `Routing.kt` (`goalRoutes()`) y `Goals` agregada al `SchemaUtils.create(...)` de `DatabaseFactory.kt`.

Tests nuevos: `GoalRoutesTest.kt` (7 casos) — GET vacío, POST crea y GET/POST devuelven `saved` derivado del saldo (no de un aporte manual), POST sobre cuenta de deuda → 422, POST sobre cuenta ajena → 404, PUT actualiza nombre/target y re-deriva `saved`, DELETE + segundo DELETE → 404, aislamiento completo entre user A y B (con cuenta propia de B para que el 404 del PUT sea por "meta ajena", no por "cuenta ajena"). `IsolationTest.kt` se ajustó: `Goals` sumada al schema H2 y el comentario del test de `/api/goals` actualizado (el comportamiento observable — `[]` sin filas — no cambió).

**:core**: `Goal` (en `Finance.kt`) pasó de `(name, target, saved, deadline, monthly)` a `(id = "", name, target, accountId, targetDate: String? = null, saved: Long = 0)`. Revisé quién consumía el modelo viejo: `DashboardLogic.kt`/`DashboardScreen.kt` solo usan `.saved`/`.size` (compilan sin tocarlos — **no hace falta cablear nada nuevo ahí**, `getGoals()` ya devuelve datos reales ahora en vez de `[]`); `PerfilScreen.kt` "Meta principal" **no consume el modelo Goal en absoluto** (es un texto estático "Aún sin meta" con link a Metas, F45) — queda igual, sin tocarla, tal como pediste. `WalletRepository`/`WalletRepositoryImpl`/`LocalRepository`/`NoOpRepository` ganan `createGoal`/`updateGoal`/`deleteGoal` (mismo idioma `ApiException` que `createCard`/`adjustCreditBalance`: chequea `isSuccess()` antes de deserializar, para que un 404/422 con texto del server no se pierda). `LocalRepository` es remote-only para metas (sin caché SQLDelight), igual que presupuestos y recurrentes.

**UI** (`MetasScreen.kt` + `GoalSheet.kt` nueva): el "+" decorativo se reemplaza por `NewItemButton` (ancho completo cuando la lista está vacía, debajo del encabezado; compacto arriba a la derecha con la lista ya poblada — mismo patrón que Recurrentes). La hoja: nombre, `MoneyField` para el objetivo, selector de cuenta (solo tipos con `group != DEUDA`, es decir Dinero o Inversión — si no hay ninguna, mensaje "No tienes cuentas de Dinero o Inversión — crea una en Cuentas primero"), fecha objetivo opcional AAAA-MM-DD reusando **la validación de la Ola 1** (`isValidCreditDate`/`filterDateInput`, importadas directo de `CreditTermsSheet.kt` — son funciones top-level públicas, no había motivo para reimplementarlas). Botón "Falta…" con el primer campo que falta, mismo idioma que las demás hojas. Tocar una tarjeta abre la hoja en modo edición (con "Eliminar"). Cada tarjeta ahora pinta un anillo de progreso (`GoalRing`, Canvas propio, `saved/target`) en vez de la barra lineal que tenía antes; el total de arriba sigue sumando `saved`/`target` de todas las metas (sin cambios, los nombres de campo se mantuvieron).

## F38 — Alta manual de suscripción

**Server** (`SubscriptionRoutes.kt`): `POST /api/subscriptions`, body `CreateSubscriptionRequest(displayName, amount, currency, dayOfMonth)` (tipo nuevo en `core/.../model/Subscription.kt`). Nace **CONFIRMED** (la creó el dueño — no hay nada que confirmar), `confidence = HIGH` (no aplica a un alta manual; es el valor menos falso de los tres, y no se lee para nada en este camino). `merchantKey` = nombre normalizado (lowercase, no-alfanumérico → `_`, trim) con prefijo **`manual_`**. Verifiqué `SubscriptionSync.kt`: `runSubscriptionDetection` calcula su `merchantKey` con `normalizeMerchant(description)` sobre la descripción del EVENTO bancario — ese algoritmo nunca antepone `manual_`, así que una fila `manual_*` queda estructuralmente fuera de lo que `upsertDetected`/`applyExisting` pueden generar o reescribir; no hizo falta ningún caso especial ahí, solo un comentario explicando por qué. 400 si falta nombre/monto o la moneda no es COP/USD; 409 si ya existe una suscripción con ese nombre+moneda para el usuario (mismo `(userId, merchantKey, currency)` único que ya protegía el detector).

Tests nuevos en `SubscriptionRoutesTest.kt` (4 casos): crear → `CONFIRMED` y cuenta en `monthlyTotalCop` inmediatamente; un re-scan (que además detecta netflix/youtube de los eventos sembrados) no la toca ni la duplica (mismo id, mismo status, mismo monto); 400 en nombre en blanco / monto ≤0 / moneda desconocida; 409 en nombre repetido.

**:core**: `WalletRepository.createSubscription(request): Subscription` + implementaciones (mismo idioma `ApiException` para que el 409 se lea en la UI).

**UI** (`SuscripcionesScreen.kt` + `CreateSubscriptionSheet.kt` nueva): "+ Nueva suscripción" (`NewItemButton`) junto a "Re-escanear" cuando hay activas, ancho completo debajo del encabezado cuando no hay ninguna. Hoja: nombre, chips COP/USD, `MoneyField`, día de cobro (1–31), patrón "Falta…" igual que las demás hojas.

## Textos nuevos (tuteo neutro, sin voseo)

- "Nueva meta" / "Editar meta" / "Crear meta" / "Guardar cambios" / "Guardando…" / "Creando…" / "Eliminar"
- "Falta el nombre" / "Falta el monto objetivo" / "Elige una cuenta" / "La fecha objetivo tiene que ser AAAA-MM-DD"
- "No tienes cuentas de Dinero o Inversión — crea una en Cuentas primero"
- "Meta para el {fecha}" / "Sin fecha objetivo"
- "Elige una cuenta de Dinero o Inversión — una meta no se ahorra en una deuda" (422 del server, mostrado vía `toUserMessage()`)
- "Nueva suscripción" / "Crear suscripción" / "Falta el monto" / "El día de cobro tiene que estar entre 1 y 31"
- "Ya tienes una suscripción llamada "X" en COP/USD" (409 del server)

## Pendiente / anotado (no resuelto en esta tarea)

- **F55 no cubre metas huérfanas**: si se borra la cuenta de una meta, la fila de `goals` queda con un `account_id` que ya no existe. `GET /api/goals` no revienta (cae a `saved = 0` cuando no encuentra el tipo de cuenta — ver comentario en `GoalRoutes.kt`), pero la meta queda "viva" mostrando 0 ahorrado en vez de limpiarse o avisar. `AccountRoutes.kt` no está en el alcance de esta tarea; si querés, se agrega un `Goals.deleteWhere` (o un aviso) al mismo `DELETE /api/accounts/{id}` que ya limpia `credit_terms`/`card_terms`/eventos.
- El acceso del Inicio a Metas **no necesita recablearse**: `DashboardScreen.kt`/`DashboardLogic.kt` ya llaman `getGoals()` sin cambios y ya usan `.saved`/`.size`, así que empiezan a mostrar datos reales solos.
- `PerfilScreen.kt` "Meta principal" sigue mostrando el texto estático "Aún sin meta" (F45) aunque ya existan metas reales — no consumía el modelo antes y no lo toqué (fuera del alcance que me diste), pero ahora que F26 existe de verdad, valdría la pena que muestre la meta principal real en vez del placeholder.

## Verificación (salida real)

```
$ ./gradlew :server:test :core:jvmTest --console=plain -q
(sin salida — todo verde; GoalRoutesTest 7/7, SubscriptionRoutesTest 13/13, IsolationTest 9/9)

$ ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew \
    :shared:testDebugUnitTest :shared:compileDebugKotlinAndroid :webApp:compileKotlinWasmJs \
    --console=plain -q
(sin salida — BUILD SUCCESSFUL; DashboardLogicTest 17/17 con el Goal nuevo)
```

No se corrió `:shared:compileKotlinIosSimulatorArm64` — no se tocó ningún `expect`/`actual`.
