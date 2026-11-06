# Las categorías del número y la nota del aviso · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cuando el número (llave o cuenta) de un aviso ya se anotó con 2+ categorías, Movi las ofrece primero y dice por qué; Revisar aviso gana una fila «Nota»; esos avisos salen de «Confirmar los N».

**Architecture:** La memoria de categorías (`:core` `MemoriaDeCategorias`) guarda, además de la ganadora, la distribución por categoría de cada huella de número. El server la pone en un campo nuevo y opcional de `ParsedSms` al armar la propuesta del aviso. La app solo pinta lo que llega. La nota viaja por `ParsedSms.nota`, que ya termina en la descripción con `descripcionConLaNota`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (`:shared`), Ktor + Exposed (`:server`), kotlinx.serialization. Pruebas: `kotlin.test` en `:core:jvmTest`, `testApplication` + H2 en `:server:test`, Robolectric + compose-ui-test en `:shared:testDebugUnitTest`.

**Spec:** `docs/superpowers/specs/2026-11-05-categorias-del-numero-design.md` (leerlo entero antes de cada tarea).

## Global Constraints

- Todo texto visible en español neutro, tuteo, sin voseo (`VoseoScanTest`); citas con «», nunca `\"` dentro de un literal de `shared/.../ui` (`ComillasRectasScanTest`); solo glifos que Figtree dibuja (`GlifosDeFigtreeScanTest`).
- Campos nuevos del wire: **opcionales con valor por defecto** (un APK viejo los ignora).
- Espacios, radios, colores y piezas solo de `theme/` y `ui/components/ds/` (`LosValoresSueltosSoloBajanTest`).
- Nada se anota solo. No cambia **cuál** categoría se propone.
- Solo huellas `llave:` y `cuenta:` (no `cajero:`, no `nombre:`). Solo con **2 o más** categorías distintas; con menos, lista vacía.
- Nota: hasta **80** caracteres; rótulo «Nota», pista «¿Para qué fue? (opcional)».
- Línea: «A esta llave le pusiste Parqueadero 3 veces y Gasolina 2» / «A esta cuenta …»; «1 vez» en singular; más de tres → las tres primeras y «y N más»; nunca se corta con «…» (puede ir en dos renglones).
- Pruebas en la Mac solo con `scripts/pruebas-dirigidas.sh <Clase> …` (nunca `./gradlew build` ni la batería completa).

## Review Focus

1. Un número **propio** del dueño («desde tu cuenta \*8133») en el texto del banco no cuenta como el número del destino: la huella se saca del comercio leído (`ParsedSms.merchant`) y, del `rawPayload`, solo con `identificadorDelDestinoEn` (Task 1 y 2 lo prueban).
2. La nota escrita no reaparece como **nombre** del destinatario en el aviso siguiente (Task 2 lo prueba).
3. Un número que ya tiene nombre (tercero guardado o renombrado por la memoria) sigue contando su historia por el `rawPayload` (Task 2 lo prueba).
4. La línea entera a 360 dp y con letra a 1,456, y nada salta al llegar la lectura (Task 4 lo prueba).
5. Un ingreso y un gasto al mismo número no se mezclan (Task 1 lo prueba).

---

### Task 1: `:core` · la distribución del número en la memoria

**Files:**
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/MemoriaDeCategorias.kt`
- Modify: `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Finance.kt` (`ParsedSms`, junto a `aprendidoDe`)
- Test: `core/src/jvmTest/kotlin/com/jvillada/movi/shared/model/LasCategoriasDelNumeroTest.kt` (nuevo)

**Interfaces:**
- Produces:
  - `@Serializable data class CategoriaDelNumero(val categoria: String, val veces: Int)` (en `MemoriaDeCategorias.kt`).
  - `ParsedSms.categoriasDelNumero: List<CategoriaDelNumero> = emptyList()` y `ParsedSms.porElMismoMonto: Boolean = false`, ambos con KDoc.
  - `AnotacionPasada.delBanco: String? = null` (el `rawPayload` del movimiento).
  - `fun huellaDelIdentificador(id: IdentificadorDelDestino): String?`: `NUMERO` → `"cuenta:" + valor`; `LLAVE` → `null` si `laLlaveEsUnNombre(valor)`, si no `"llave:" + claveComparableDeNombre(valor)`.
  - `fun MemoriaDeCategorias.categoriasDelNumero(texto: String, tipo: TransactionType?, identificador: IdentificadorDelDestino? = null): List<CategoriaDelNumero>`: la huella es `huellaDeUnMovimiento(texto)` si empieza por `llave:`/`cuenta:`, si no `identificador?.let(::huellaDelIdentificador)`. Devuelve la distribución del lado de `tipo` (INCOME aparte, como `recuerdoDe`) **solo si tiene 2+ categorías**, si no `emptyList()`.
  - `fun nombreSinLaNota(merchant: String?, descripcion: String): String`: si `merchant` no está vacío y `descripcion` empieza por `"$merchant · "`, devuelve `merchant`; si no, `descripcion`.

- [ ] **Step 1: Escribir las pruebas que fallan**

`LasCategoriasDelNumeroTest`, con `AnotacionPasada` armadas a mano (`comoLlego = "Pago QR · llave 0099887766"`, `tipo = EXPENSE`):
- `dos categorías a la misma llave vuelven en orden de veces`: 3 Parqueadero + 2 Gasolina → `[("Parqueadero",3),("Gasolina",2)]`.
- `con una sola categoría no vuelve nada`: 4 Parqueadero → `emptyList()`.
- `a igual número de veces gana la más reciente`: 2 Gasolina (cuando 10, 20) + 2 Parqueadero (cuando 5, 30) → Parqueadero primero.
- `las categorías que la memoria no aprende no cuentan`: 2 Parqueadero + 1 «Otros» + 1 `CARD_PAYMENT_CATEGORY` → `emptyList()`.
- `lo que entra va aparte`: 2 Parqueadero + 1 Gasolina (EXPENSE) y 2 Salario + 1 Reembolso (INCOME) a la misma cuenta `"Transferencia a la cuenta *41279033068"`; con `tipo = EXPENSE` solo las de gasto; con `INCOME` solo las de ingreso.
- `el número del texto del banco cuenta aunque el comercio ya tenga nombre`: una anotación con `comoLlego = "Caro"`, `delBanco = "Bancolombia: Transferiste $50,000 desde tu cuenta *8133 a la cuenta *41279033068 …"` categoría Mercado + dos con `comoLlego` `cuenta:41279033068` en Arriendo → `[("Arriendo",2),("Mercado",1)]` para `"Transferencia a la cuenta *41279033068"`.
- `la cuenta propia del texto del banco no cuenta`: una anotación con `delBanco` = el SMS de arriba pero buscando `"Transferencia a la cuenta *8133"` → no la cuenta (usar un texto real de los fixtures de `:server` para el SMS; `identificadorDelDestinoEn` solo toma la cuenta de destino).
- `un movimiento nombrado por los dos cuenta una vez`: `comoLlego` y `delBanco` con el mismo número → `veces` 1 por ese movimiento.
- `el cajero no es un número para esto`: huella `cajero:` → `emptyList()`.
- `huellaDelIdentificador coincide con la huella del comercio`: para el texto `"Bancolombia: pagaste $9,000.00 por codigo QR desde tu cuenta *8133 a la llave 0099887766 el 05/11/2026 a las 10:00."`, `huellaDelIdentificador(identificadorDelDestinoEn(texto)!!) == huellaDeUnMovimiento("Pago QR · llave 0099887766")`.
- `nombreSinLaNota`: `("Pago QR · llave 0099887766", "Pago QR · llave 0099887766 · parqueadero del centro")` → el comercio; `("Coomeva", "Coomeva")` → «Coomeva»; `(null, "Panadería")` → «Panadería»; `("Rappi", "Mercado de Rappi")` → «Mercado de Rappi».
- `entraEnElLote`: un gasto con `categoriasDelNumero` de 2 → `false`; con `porElMismoMonto = true` → `true`; sin la lista → `true`; un INCOME → `false` (como hoy).

- [ ] **Step 2: Correrlas y verlas fallar**

Run: `scripts/pruebas-dirigidas.sh LasCategoriasDelNumeroTest`
Expected: falla de compilación (no existen `CategoriaDelNumero`, `categoriasDelNumero`, …).

- [ ] **Step 3: Implementar**

- En `MemoriaDeCategorias.de`, sobre las mismas `aprendibles` partidas en gastos/ingresos, armar dos mapas privados `huella → List<CategoriaDelNumero>` con las huellas de número de cada anotación: la de `comoLlego` (si es `llave:`/`cuenta:`) y la de `delBanco?.let(::identificadorDelDestinoEn)?.let(::huellaDelIdentificador)`, **un conjunto** por anotación (cuenta una vez). Orden: `veces` desc, luego el `cuando` más reciente desc (el desempate de `recuerdosPor`). `textoQueNoSeAprende(delBanco)` **no** se aplica (el SMS crudo siempre dice «tu cuenta»).
- `entraEnElLote` (en `LoQueMoviEntendio.kt`): `leido.type != INCOME && (leido.categoriasDelNumero.size < 2 || leido.porElMismoMonto)`.
- `conElMotivoDelEnvio` (en `MotivoDelEnvio.kt`) pone `porElMismoMonto = true` en el `copy` que devuelve con el motivo.

- [ ] **Step 4: Correrlas y verlas pasar**

Run: `scripts/pruebas-dirigidas.sh LasCategoriasDelNumeroTest MemoriaDeCategoriasTest ElAvisoQueSaldaUnGrupoTest`
Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "Categorías del número · :core: la memoria guarda la distribución de cada llave o cuenta"`

---

### Task 2: `:server` · el aviso trae las categorías de su número

**Files:**
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/sms/MemoriaDelDueno.kt` (`anotacionesPasadasDe`)
- Modify: `server/src/main/kotlin/com/jvillada/movi/server/routes/SmsRoutes.kt` (`conLaMemoriaYLosTerceros`)
- Test: `server/src/test/kotlin/com/jvillada/movi/server/routes/LasCategoriasDelNumeroDelAvisoTest.kt` (nuevo, armado como `LaCategoriaAprendeDelAvisoTest`: H2, token, `subir`, leer la propuesta del aviso por la misma ruta que ese test usa en `leer`)

**Interfaces:**
- Consumes: Task 1 (`categoriasDelNumero`, `AnotacionPasada.delBanco`, `nombreSinLaNota`, `ParsedSms.categoriasDelNumero`).
- Produces: `ParsedSms.categoriasDelNumero` lleno en toda propuesta que pasa por `conLaMemoriaYLosTerceros` (Revisar aviso, Por revisar, comprobantes, «Anotarlo igual»).

- [ ] **Step 1: Escribir las pruebas que fallan**

Sembrar movimientos del dueño con `merchant` `"Pago QR · llave 0099887766"` (3 Parqueadero, 2 Gasolina) y subir el aviso `"Bancolombia: pagaste $9,000.00 por codigo QR desde tu cuenta *8133 a la llave 0099887766 el …"`:
- `el aviso a una llave con dos historias trae las dos, en orden`: `categoriasDelNumero == [("Parqueadero",3),("Gasolina",2)]`, `category == "Parqueadero"` (la de siempre), `aprendidoDe == "Así lo anotaste 3 veces"`.
- `con una sola historia el campo va vacío`.
- `lo anotado con nombre cuenta por el texto del banco`: los 3 Parqueadero con `merchant = "Parqueadero Calle 85"` y `raw_payload` = el SMS de un pago QR a la misma llave; 2 Gasolina con `merchant` de la llave → las dos categorías.
- `los anulados no cuentan`: anular los 2 de Gasolina → vacío.
- `el pago de tarjeta no lo lleva`: un aviso que `parseSms` lee como `CARD_PAYMENT_CATEGORY` → vacío aunque su número tenga historia.
- `la nota no vuelve como nombre`: un movimiento con `merchant` `"Pago QR · llave 0099887766"`, `description` `"Pago QR · llave 0099887766 · lo de ayer"`, más otro con `description` `"Parqueadero Calle 85"` y la misma llave más viejo; el aviso siguiente llega con `merchant` que **no** contiene «lo de ayer».

- [ ] **Step 2: Correrlas y verlas fallar**

Run: `scripts/pruebas-dirigidas.sh LasCategoriasDelNumeroDelAvisoTest`
Expected: FAIL (`categoriasDelNumero` vacío; la nota en el nombre).

- [ ] **Step 3: Implementar**

- `anotacionesPasadasDe`: seleccionar también `Events.rawPayload` → `delBanco`; `nombre = nombreSinLaNota(fila[Events.merchant], fila[Events.description])`.
- `conLaMemoriaYLosTerceros`: al entrar, antes de cualquier renombre, `val delNumero = if (parsed.category in CATEGORIAS_QUE_LA_MEMORIA_NO_TOCA) emptyList() else memoria.categoriasDelNumero(parsed.merchant, parsed.type, parsed.identificador())`; al salir, si el resultado no es `porMarca` y su categoría no está en `CATEGORIAS_QUE_LA_MEMORIA_NO_TOCA`, `copy(categoriasDelNumero = delNumero)`.

- [ ] **Step 4: Correrlas y verlas pasar**

Run: `scripts/pruebas-dirigidas.sh LasCategoriasDelNumeroDelAvisoTest LaCategoriaAprendeDelAvisoTest MemoriaDelDuenoTest LosAvisosQueNombranAAlguienTest`
Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "Categorías del número · server: el aviso trae las categorías de su llave o cuenta"`

---

### Task 3: `:shared` · las categorías del número primero, en Revisar aviso y en la tarjeta

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/sms/LasCategoriasDelNumero.kt`
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/sms/SMSScreens.kt` (`SMSReconcileScreen`: `categoryOptions`, `eligiendoCategoria`, las pastillas; `TarjetaDeMensajeDelBanco`: la línea)
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/sms/ElegirCategoriaEnLaTarjeta.kt` (`EligeLaCategoria.delNumero: String? = null`)
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/porrevisar/PorRevisarScreen.kt` (donde arma `EligeLaCategoria`, ~línea 455)
- Test: `shared/src/androidUnitTest/kotlin/com/jvillada/movi/ui/sms/LasCategoriasDelNumeroTest.kt` (pura) y `…/ui/sms/LasCategoriasDelNumeroEnPantallaTest.kt` (Robolectric, armada como `RevisarAvisoA360Test`)

**Interfaces:**
- Consumes: `ParsedSms.categoriasDelNumero`, `ParsedSms.identificadorEsLlave` (Task 1).
- Produces (en `LasCategoriasDelNumero.kt`):
  - `fun lineaDelNumero(categorias: List<CategoriaDelNumero>, esLlave: Boolean): String?` (`null` con menos de 2).
  - `fun primeroLasDelNumero(categorias: List<CategoriaDelNumero>, resto: List<String>): List<String>` (las del número en su orden, después `resto` sin repetir).
  - `const val TAG_LINEA_DEL_NUMERO = "linea-del-numero"`.

- [ ] **Step 1: Escribir las pruebas que fallan**

Pura:
- `lineaDelNumero([P3,G2], esLlave = true) == "A esta llave le pusiste Parqueadero 3 veces y Gasolina 2"`.
- con `esLlave = false` → «A esta cuenta le pusiste …».
- `[P3, G1]` → «… Parqueadero 3 veces y Gasolina 1 vez».
- `[A3,B2,C1]` → «… A 3 veces, B 2 y C 1 vez».
- cinco → «… A 3 veces, B 2, C 2 y 2 más».
- una sola → `null`.
- `primeroLasDelNumero([P,G], ["Comida","Gasolina","Mercado"]) == ["Parqueadero","Gasolina","Comida","Mercado"]`.

Pantalla (Revisar aviso con un `ParsedSms` de `categoriasDelNumero = [P3,G2]`, `category = "Parqueadero"`, `aprendidoDe = "Así lo anotaste 3 veces"`, servido como en `RevisarAvisoA360Test`):
- `las pastillas arrancan abiertas con las del número primero`: sin tocar «Cambiar», las dos primeras pastillas (`TAG_PASTILLAS_DE_CATEGORIA_DEL_SMS`) son Parqueadero y Gasolina.
- `la explicación de la fila se queda`: la fila Categoría sigue diciendo «Así lo anotaste 3 veces».
- `la línea se lee entera a 360 dp y a 1,456`: el nodo `TAG_LINEA_DEL_NUMERO` tiene el texto completo y no está cortado (el mismo chequeo de corte que usan las pruebas de la vuelta 56 con `ESCALA_DEL_TELEFONO_AL_130`).
- `nada salta al llegar la lectura`: la fila de botones de abajo no se mueve más de 8 dp entre el primer cuadro con la lectura y el siguiente (como las guardas existentes de esta pantalla).
- `con una sola categoría todo queda como antes`: `categoriasDelNumero` vacío → pastillas cerradas, sin la línea.

- [ ] **Step 2: Correrlas y verlas fallar**

Run: `scripts/pruebas-dirigidas.sh shared/src/androidUnitTest/kotlin/com/jvillada/movi/ui/sms/LasCategoriasDelNumeroTest.kt LasCategoriasDelNumeroEnPantallaTest`
Expected: FAIL.

- [ ] **Step 3: Implementar**

- `SMSReconcileScreen`: `eligiendoCategoria` pasa a `Boolean?` (`null` = no lo tocó); abiertas = `eligiendoCategoria ?: (parsed?.categoriasDelNumero?.size ?: 0) >= 2`, así abre en el primer cuadro con la lectura, sin `LaunchedEffect`. Elegir una pastilla las cierra igual que hoy (`false`). `categoryOptions = primeroLasDelNumero(parsed?.categoriasDelNumero.orEmpty(), categoriasParaElegirEnElSms(…))`. La línea (`Movi.textos.apoyo`, `textoMedio`, `TAG_LINEA_DEL_NUMERO`, sin `maxLines`) va encima de `FilaDeCategorias`, dentro del mismo bloque abierto.
- Tarjeta: `PorRevisarScreen` pasa `delNumero = lineaDelNumero(leido.categoriasDelNumero, leido.identificadorEsLlave)` y las frecuentes con `primeroLasDelNumero`; `TarjetaDeMensajeDelBanco` arranca `eligiendoLaCategoria` en `eligeLaCategoria?.delNumero != null` y pinta la línea encima de su `FilaDeCategorias`. Solo donde la tarjeta ya arma `EligeLaCategoria`.

- [ ] **Step 4: Correrlas y verlas pasar**

Run: `scripts/pruebas-dirigidas.sh shared/src/androidUnitTest/kotlin/com/jvillada/movi/ui/sms/LasCategoriasDelNumeroTest.kt LasCategoriasDelNumeroEnPantallaTest RevisarAvisoA360Test ElAvisoDelBancoElegisTusCategoriasTest ReconciliarMovimientoScrollAlAbrirSelectorTest`
Expected: PASS (`pruebas-dirigidas.sh` suma `:shared:compileKotlinWasmJs` y las guardas de `:core`).

- [ ] **Step 5: Commit** — `git commit -m "Categorías del número · app: van primero y Movi dice por qué"`

---

### Task 4: `:shared` · la fila «Nota» en Revisar aviso

**Files:**
- Create: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/sms/LaNotaDelAviso.kt` (la fila)
- Modify: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/sms/SMSScreens.kt` (estado de la nota; la fila debajo de las pastillas de Categoría, dentro de la misma superficie; `leido` con la nota en `movimientoDelAviso` y en `pedidoDeDosPatas`)
- Test: `shared/src/androidUnitTest/kotlin/com/jvillada/movi/ui/sms/LaNotaDelAvisoTest.kt`

**Interfaces:**
- Consumes: `ParsedSms.nota`, `descripcionConLaNota`, `pedidoDeDosPatas(…, leido, …)` (ya pone `nota = leido.nota`).
- Produces: `@Composable fun FilaDeLaNota(valor: String, habilitada: Boolean, onCambio: (String) -> Unit, modifier: Modifier = Modifier)`, `const val TAG_NOTA_DEL_AVISO = "nota-del-aviso"`, `const val LARGO_MAXIMO_DE_LA_NOTA = 80`.

- [ ] **Step 1: Escribir las pruebas que fallan**

- `la nota escrita queda en la descripción`: escribir «parqueadero del centro» y confirmar → el `FinancialEvent` mandado (repositorio falso, como en `ReconciliarSinDuplicarTest`) tiene `description == "Pago QR · llave 0099887766 · parqueadero del centro"` y `merchant == "Pago QR · llave 0099887766"`.
- `la nota del correo de PSE arranca escrita`: con `ParsedSms.nota = "Pago de saldo plan familiar"` el campo empieza con ese texto, y la descripción guardada la lleva una sola vez.
- `no pasa de 80`: escribir 100 caracteres deja 80.
- `en dos patas también`: un aviso de cuota con nota escrita → `DosPatasDelAviso.nota` es lo escrito.
- `no aparece con «Es este»` (un solo ya anotado) `ni en «Repartir en partes»`.
- `sin escribir nada todo queda como antes`: descripción igual al comercio.

- [ ] **Step 2: Correrlas y verlas fallar**

Run: `scripts/pruebas-dirigidas.sh LaNotaDelAvisoTest`
Expected: FAIL.

- [ ] **Step 3: Implementar**

- `FilaDeLaNota`: piezas de `ds/` (`Campo` si su forma calza en una fila con rótulo; si no, la forma de la fila del prototipo de la rama `spike/categorias-del-numero`, `NotaDelPrototipo`, con tokens). Rótulo «Nota», pista «¿Para qué fue? (opcional)», `take(LARGO_MAXIMO_DE_LA_NOTA)`.
- Estado: `var notaEscrita by remember { mutableStateOf<String?>(null) }`; valor = `notaEscrita ?: parsed?.nota.orEmpty()`. Se ofrece si `parsed != null && unicoYaAnotado == null && !enPartes` y el aviso está pendiente. Al confirmar, `p.copy(nota = valor.trim().ifEmpty { null })` en los dos caminos.

- [ ] **Step 4: Correrlas y verlas pasar**

Run: `scripts/pruebas-dirigidas.sh LaNotaDelAvisoTest ReconciliarConLasDosPatasTest ReconciliarSinDuplicarTest RepartirEnReconciliarTest RevisarAvisoA360Test`
Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "Categorías del número · app: la fila «Nota» en Revisar aviso"`

---

### Task 5: Documentación

**Files:**
- Modify: `CLAUDE.md` (un bullet «Vuelta 132 · las categorías del número y la nota», junto a la memoria de categorías de la vuelta 85: el campo, la regla de 2+, el `rawPayload`, el lote, la nota y que el nombre recordado no la arrastra)
- Modify: `docs/diseno/sistema.md` (sección «Vuelta 132»: la línea, las pastillas abiertas, la fila Nota)

- [ ] **Step 1:** Escribir los dos textos con lo que de verdad quedó (nombres de funciones y tags reales).
- [ ] **Step 2: Commit** — `git commit -m "Categorías del número · docs"`
