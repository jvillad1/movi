# Ola A de «Movi sin grasa» — plan de implementación

Origen: revisión de estructura del 23-sep-2026 (artefacto https://claude.ai/artifact/88BSTvLsPp8SjJBUjZkFpn).
El dueño aprobó la **ola A**: propuesta 1 (abrir sin parpadeo), 3 (anotar en dos toques), 8 (filas
limpias) y el texto de la 4 («Marcaste» → «ya salieron»).

Repo: `/private/tmp/movi-ola-a` (worktree, rama `ola-a-sin-grasa`, base `origin/master` a996c5f0).
Leer `CLAUDE.md` del repo antes de empezar (módulos, convenciones).

## Global Constraints

- **Pruebas**: `JAVA_HOME=/usr/local/share/jbrsdk-21/Contents/Home ./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs`
  es lo que corre CI. Cada tarea corre al menos las suites que toca (sin `--rerun-tasks` está bien
  durante la tarea) y **mira el `BUILD SUCCESSFUL`/`BUILD FAILED` real**, no el código de salida de
  un pipe. **Nunca `./gradlew build`.** La máquina tiene 16 GB: no correr dos Gradle a la vez.
  `timeout` no existe en esta Mac.
- **Todo texto visible al usuario en español neutro latinoamericano, tuteo, sin voseo**
  (`VoseoScanTest` lo vigila). Los comentarios pueden seguir el estilo de los que ya hay.
- **Colores, espacios, formas y estilos de texto solo desde `Movi.*`** (`shared/.../theme/Tokens.kt`).
  Nada de `Color(0xFF…)` nuevo en pantallas.
- **Nada de valores nuevos en un `enum` que viaje serializado** (un APK viejo revienta). Campos nuevos
  en modelos `@Serializable` de `:core` siempre **con valor por defecto**. Un enum que solo vive en la
  UI (p. ej. `OrigenCuenta`) sí puede crecer.
- **Todo `object` mutable nuevo que guarde datos del usuario** se limpia en `SessionManager.clear()` y se
  agrega a `shared/src/androidUnitTest/.../aislamiento/ElForkLlegaLimpioTest.kt`.
- Toda lectura de movimientos del server que sume o cuente **excluye los anulados** (`VoidEvents.originalEventId`).
- Categorías del sistema: `OPENING_CATEGORY` («Saldo inicial»), `ADJUSTMENT_CATEGORY` («Ajuste de saldo»),
  `TRANSFER_CATEGORY` («Traspaso»), `ORPHANED_LEG_CATEGORY`, y lo que ya marca `isReservedCategory(...)`
  (`:core`). **Nunca** se ofrecen como categoría frecuente ni se sugieren.
- Commits pequeños, uno o más por tarea, con mensaje en español. Terminar cada mensaje con
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `git add` siempre con rutas explícitas.
- Comentarios KDoc al estilo del archivo que se toca: dicen el **porqué** (la evidencia del teléfono
  del dueño está en este plan), no el qué.

---

### Task 1: La ventana de Android arranca oscura

**Problema.** Al abrir Movi en el Pixel del dueño hay ~0,6 s de ventana **blanca** antes de que Compose
pinte: el manifiesto usa `@android:style/Theme.Material.Light.NoActionBar` y la app es oscura por
defecto (`TemaStore.oscuro` arranca en `true`).

**Hacer.**
- Crear `androidApp/src/main/res/values/themes.xml` con un tema propio, p. ej. `Theme.Movi`, padre
  `android:Theme.Material.NoActionBar`, con `android:windowBackground` = `#07090C` (es `fondo` del tema
  oscuro de `Tokens.kt`; declararlo como `<color name="movi_fondo">` en `res/values/colors.xml`),
  `android:statusBarColor`/`android:navigationBarColor` transparentes.
- Usarlo en el `<application android:theme=…>` del `AndroidManifest.xml`.
- Comentario en el XML: por qué oscuro aunque exista el tema claro (el default es oscuro; con el claro
  elegido se ve un instante de oscuro, que es mucho menos chocante que un destello blanco en una app
  oscura, y la preferencia vive en `Settings` y no se puede leer antes de que el sistema pinte la ventana).
- No tocar `MainActivity.enableEdgeToEdge`.

**Prueba.** `./gradlew :androidApp:assembleDebug` compila. (La verificación a ojo la hace el controlador
en el teléfono.)

---

### Task 2: Filas de Movimientos que no se desbordan y el checklist que no dice «Marcaste»

**Problema A.** En Movimientos, el título de una fila es `tx.description` sin límite de líneas: una
descripción larga («Ajuste al saldo de Skandia — quedó en $95.812.553 al 21-sep (rendimientos,
devolución de comisión y el redondeo del saldo inicial)») ocupa cuatro renglones.

**Hacer A.** En `shared/src/commonMain/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreen.kt`:
- `MovementSingleRow`: el `Text` del título con `maxLines = 2`, `overflow = TextOverflow.Ellipsis`, y un
  `Modifier.weight(1f, fill = false)` en ese `Text` para que el punto de «sin confirmar» (`StatusDot`)
  siga visible al lado cuando el título se recorta.
- Lo mismo en el título de `TransferRow` y en el de `RenglonDeAjustes` si alguno de los dos puede
  crecer sin tope (revisar).
- La descripción completa sigue a un toque (la hoja del movimiento no cambia).

**Problema B.** El Inicio dice «Marcaste los 12 pagos de este período» cuando fue Movi quien los
emparejó solo con los movimientos (el checklist es de solo lectura desde #363).

**Hacer B.** En `shared/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/ResumenDelPeriodo.kt`:
- `lineaDeLoQueFalta`: 
  - `faltan == 0 && total == 1` → `"Ya salió el único pago de este período"`
  - `faltan == 0` → `"Ya salieron los $total pagos de este período"`
  - el resto igual.
- `pieDeLoYaPagado`: `"Ya salieron $pagados. El checklist completo está en «Ver todos»."`, y con
  `pagados == 1` → `"Ya salió 1. El checklist completo está en «Ver todos»."`
- Buscar con `git grep -n "arcaste\|arcado"` otros textos visibles del checklist del período / Inicio
  que atribuyan al dueño algo que hizo Movi y corregirlos con el mismo criterio (NO tocar el de
  `CategorySheets.kt` sobre «no se repite»: ese sí lo marca el dueño).
- Actualizar las pruebas que afirman los textos viejos:
  `shared/src/commonTest/.../ui/recurrentes/ChecklistDelPeriodoTest.kt` y
  `shared/src/androidUnitTest/.../ui/dashboard/ChecklistEnInicioTest.kt`, y agregar un caso para el
  pie en singular y en plural.

**Pruebas.** `:shared:testDebugUnitTest` (las dos clases de arriba y las de Movimientos) y
`:core:jvmTest` (VoseoScan).

---

### Task 3: El server sabe qué cuenta y qué categorías usa el dueño, y qué recuerda de cada nombre

Todo en `:core` (modelos) y `:server`. Sin UI.

**3a — cuenta más usada.** En `core/.../shared/model/DashboardSummary.kt` agregar a `DashboardSummary`:
`val cuentaMasUsada: String? = null` con KDoc. En `server/.../routes/DashboardRoutes.kt` calcularla: el
`accountId` con **más movimientos de tipo `EXPENSE`** en los **últimos 30 días** (por `Events.timestamp`,
epoch ms), **no anulados**, excluyendo las categorías del sistema (ver Global Constraints) y
`CARD_PAYMENT_CATEGORY`. Empate → la del movimiento más reciente. Sin movimientos → `null`.
(Evidencia: el dueño tiene 71 gastos en 60 días en «Bancolombia Ahorros» y 0 en «AMEX 9208», y hoy
«Agregar» le arranca con AMEX porque es la primera por orden alfabético.)

**3b — usos recientes por categoría.** Agregar a `UsedCategory` (mismo archivo):
`val usosRecientes: Int = 0` — cuántos movimientos **no anulados** tiene esa categoría en los últimos
**60 días** (cualquier tipo). Llenarlo en `usedCategories(uid)` de `DashboardRoutes.kt` con una sola
consulta agregada (no una por categoría). Las filas que existen solo por una preferencia quedan en 0.

**3c — la memoria de nombres, para el cliente.** Hoy `MemoriaDeCategorias` (`core/.../MemoriaDeCategorias.kt`)
solo la usa el server al clasificar SMS (`server/.../sms/MemoriaDelDueno.kt`, `memoriaDe(uid)`).
- En `:core`: modelo `@Serializable data class RecuerdoDeCategoria(val huella: String, val categoria: String, val nombre: String, val cuantos: Int)`
  y un método en `MemoriaDeCategorias` que exponga sus entradas como `List<RecuerdoDeCategoria>`
  (el mapa sigue privado; no cambiar cómo se arma).
- En `:server`: `GET /api/categorias/memoria` (autenticada, como las demás rutas de categorías — ver
  `CategoryRoutes.kt` y cómo se obtiene el `uid`), devuelve `memoriaDe(uid)` como esa lista.
- En `:core`, repositorio: agregar `suspend fun getMemoriaDeCategorias(): List<RecuerdoDeCategoria>` a
  la interfaz del repositorio que usa la app (`Repositories.wallets` → `WalletRepository`) y a **todas**
  sus implementaciones (la de Ktor llama al endpoint; la local/offline-first y cualquier fake de pruebas
  delegan o devuelven vacío según el patrón que ya siguen métodos parecidos; `InvalidaElInicioAlEscribir`
  es una lectura, no invalida).

**Pruebas (server).** Casos en las pruebas existentes de `DashboardRoutes` (buscar la clase de prueba que
ya cubre `usedCategories`/el resumen): cuenta más usada con anulados que cambiarían el ganador, con
traspasos/ajustes que no cuentan, con empate, sin movimientos; `usosRecientes` que no cuenta anulados ni
lo de hace 61 días. Prueba de la ruta de memoria: un usuario con dos anotaciones del mismo nombre recibe
una entrada con `cuantos = 2`, y no ve la memoria de otro usuario. Prueba en `:core` del método nuevo.

---

### Task 4: «Agregar» arranca con la cuenta y las categorías de siempre

Cliente (`:shared`). Depende de la Task 3.

**4a — cuenta.** En `shared/.../ui/quickadd/CuentaPorDefecto.kt`:
- Nuevo valor de UI `OrigenCuenta.MAS_USADA`, entre `ULTIMA` y `PRIMERA` en prioridad.
- `resolverCuenta(cuentas, contexto, ultima, masUsada = null, excluir)`: contexto > última > **más usada**
  > primera; la más usada vale solo si está en las elegibles (misma regla que las otras).
- `avisoDeCuenta`: `MAS_USADA -> "Más usada"` (corto a propósito: ver el comentario sobre los 12
  caracteres).
- Dónde vive el dato: un `object` en `shared/.../data/` (p. ej. `CuentaMasUsadaCache`, `var id: String?`)
  que el Inicio llena desde `DashboardSummary.cuentaMasUsada` en el mismo `onSuccess` donde hoy llama a
  `UsedCategoriesCache.recordFromServer` (`DashboardScreen.kt`). Limpio en `SessionManager.clear()` y en
  `ElForkLlegaLimpioTest`.
- Pasarlo desde `QuickAddScreen.kt` donde hoy se llama a `resolverCuenta`. El traspaso (`TransferForm.kt`)
  también llama a `resolverCuenta`: pasar la más usada solo si tiene sentido para el lado «Desde».
- Pruebas puras de `resolverCuenta`/`avisoDeCuenta` (hay un archivo de prueba existente para
  `CuentaPorDefecto`: buscarlo y extenderlo).

**4b — categorías frecuentes.**
- `UsedCategoriesCache` guarda también `usosRecientes` por nombre (desde `recordFromServer`).
- Función pura (en `CategoryField.kt` o un archivo nuevo junto a él):
  `categoriasFrecuentes(tipo: TransactionType, usadas, prefs, usos, cuantas = 6): List<String>` —
  las de más `usosRecientes` que **sirvan para ese tipo** (reusar `categoriaSirveParaTipo`), no
  escondidas, no reservadas/del sistema; empate por orden alfabético con `categorySortKey`. Sin datos de
  uso → lista vacía.
- `categoriaPorDefectoPara` usa la primera frecuente del tipo cuando hay datos; si no, el comportamiento
  de hoy.
- En la hoja de «Agregar» (`QuickAddScreen.kt`), para Gasto e Ingreso (no Traspaso ni Cuota): una fila
  de hasta 6 **chips** con las frecuentes, justo **debajo de la fila «Categoría»** y encima de «Cuenta».
  Tocar un chip pone esa categoría (la elegida se ve activa). Si la lista está vacía, la fila no ocupa
  lugar. Con desplazamiento horizontal si no caben; alto fijo. Estilo desde `Movi.*`, parecido a los
  chips que ya existen en la app (buscar un chip existente, p. ej. los filtros de Movimientos, y
  reusarlo si encaja).
- Pruebas: la función pura (tipo, escondidas, reservadas, empate, sin datos) y una prueba de UI
  Robolectric (`androidUnitTest`, siguiendo `HojaAgregar*Test`) que muestra los chips y que tocar uno
  cambia la categoría.

---

### Task 5: Escribir el nombre sugiere la categoría

Cliente (`:shared`). Depende de la Task 3.

- Un `object` (p. ej. `MemoriaDeCategoriasCache` en `shared/.../data/`) con la lista de
  `RecuerdoDeCategoria`, cargada con `Repositories.wallets.getMemoriaDeCategorias()` la primera vez que se
  abre «Agregar» en la sesión (y recargada después de guardar un movimiento). Limpio en
  `SessionManager.clear()` y en `ElForkLlegaLimpioTest`. Si falla la carga: sin sugerencias, sin error visible.
- Función pura `sugerenciaPorNombre(nota: String, recuerdos: List<RecuerdoDeCategoria>): RecuerdoDeCategoria?`:
  1. `huellaDeUnMovimiento(nota)` (de `:core`) igual a una `huella` → esa.
  2. Si no, y la huella de la nota empieza con `nombre:` y su clave tiene **≥ 4 caracteres**: los
     recuerdos con huella `nombre:` que **empiezan con** esa clave; si todos apuntan a **la misma
     categoría**, el de más `cuantos`; si apuntan a categorías distintas → `null` (ambiguo: no adivinar).
  3. Nunca sugiere una categoría escondida o reservada (filtrar con las prefs de `UsedCategoriesCache`
     y `isReservedCategory`).
- En `QuickAddScreen.kt`: mientras el dueño escribe la nota (Gasto o Ingreso), si hay sugerencia y el
  dueño **no eligió la categoría a mano** en esta hoja (llevar un flag; un preset de recurrente cuenta
  como elegida), poner esa categoría y mostrar debajo de la fila «Categoría» una línea de apoyo corta:
  `"Movi la reconoce: <nombre>"` (texto en `Movi.textos.apoyo`, `Movi.colores.textoMedio`). Si la
  sugerencia desaparece (borró la nota), volver a la categoría que había antes de sugerir. Si después
  el dueño toca la categoría o un chip, gana lo suyo y no se vuelve a pisar.
- Pruebas: la función pura (exacta, prefijo único, prefijo ambiguo, clave corta, escondida/reservada,
  nota vacía) y una prueba Robolectric: escribir «Mora» con un recuerdo `nombre:morasoccer → Fútbol`
  pone Fútbol y muestra la línea; con la categoría elegida a mano antes, no la pisa.

---

### Task 6: El Inicio se pinta al instante con lo último que se supo

Cliente (`:shared`). Evidencia en el Pixel del dueño: arranque en frío → 1,8 s de «Tu plata —» con
preguntas genéricas → a los ~4 s llegan los datos y la tarjeta «Pregúntale a Movi» salta ~270 px y sus
preguntas cambian mientras se leen. Causas: `DashboardDataCache` y `ScreenDefCache` viven **solo en
memoria** (se pierden al cerrar la app o recargar la web), y la definición SDUI se pide **antes** que los
datos (en serie).

**Hacer.**
- Persistir en el aparato, **por usuario** (clave con el `SessionManager.userId`), la última
  `DashboardData` que **salió bien** (la misma condición con que hoy se sella `cargadoEn`:
  `data.puedeAfirmarVacio`) y la última `ScreenDefinition` válida del Inicio. Con `Settings()` de
  multiplatform-settings, construido `by lazy` y todo adentro de `runCatching`, como `TemaStore` y
  `LastAccountStore` (leer sus KDoc: `Settings()` explota al construirse en una JVM sin contexto o en un
  navegador con el almacenamiento bloqueado). Serializar con `kotlinx.serialization` (JSON con
  `ignoreUnknownKeys = true`); hacer `@Serializable` a `DashboardData` y a lo que haga falta dentro, o
  usar una clase instantánea propia si eso es más limpio. Si la lectura falla o no deserializa → como hoy
  (sin instantánea), nunca un crash.
- Al montar el Inicio: si `DashboardDataCache.data` es `null`, leer la instantánea del usuario actual y
  usarla como valor inicial (lo mismo con `ScreenDefCache.dashboard`). **Una instantánea NO sella
  `cargadoEn`**: el Inicio recarga igual, en segundo plano.
- Mientras recarga **con** datos ya pintados: en vez de la barra de progreso de ancho completo arriba
  (`LinearProgressIndicator` en `DashboardScreen.kt`), una línea discreta `"Actualizando…"`
  (`Movi.textos.apoyo`, `Movi.colores.textoApagado`) en un lugar que **no desplace** el contenido (p. ej.
  superpuesta en la cabecera, o en un espacio de alto fijo que ya exista). Sin datos pintados, se
  mantiene la barra de hoy (la Task 7 agrega los skeletons).
- Si hay una definición SDUI cacheada (memoria o instantánea), pedir la nueva **en paralelo** con los
  datos en vez de antes; sin definición cacheada, se mantiene el orden de hoy (primero la definición).
- Al cerrar sesión: borrar la instantánea de ese usuario (en `SessionManager.clear()` o en
  `DashboardDataCache.clear()`, que ya se llama desde ahí). El cambio de tema NO la borra.
- La animación de entrada del hero (`rememberProgresoDeEntrada`) sigue siendo una vez por proceso: con
  instantánea, anima al pintar la instantánea y no vuelve a animar al llegar lo nuevo.
- Pruebas: ida y vuelta de la instantánea (serializa → deserializa igual), usuario distinto no lee la
  del otro, JSON corrupto → `null` sin excepción, `clear` la borra; Robolectric: con instantánea
  guardada, el Inicio muestra la cifra de la instantánea antes de que conteste el repositorio falso y
  muestra «Actualizando…»; al contestar, muestra la nueva y la línea desaparece. Revisar que las
  pruebas existentes del Inicio (`androidUnitTest/.../ui/dashboard/`) siguen pasando: `AppDePrueba`
  limpia los `object` entre métodos, pero una instantánea en `Settings` persiste entre pruebas del mismo
  fork — asegurarse de que `SessionManager.clear()`/`AppDePrueba` también la borre.

---

### Task 7: Skeletons con la forma de lo que viene

Cliente (`:shared`). Para la primera carga de la vida (sin instantánea) y para las listas que hoy
muestran una rueda.

- Componente en `shared/.../ui/components/` (p. ej. `Esqueleto.kt`): un bloque redondeado del color de
  un plano apagado del tema (elegir el token que mejor funcione en claro y oscuro, p. ej.
  `Movi.colores.borde` o el que use `Hairline`; justificarlo en el KDoc), con un **pulso suave de
  opacidad** (no un barrido), ~1,2 s por ciclo, con `rememberInfiniteTransition`. Variantes mínimas:
  `BloqueEsqueleto(ancho, alto)` y `LineaEsqueleto(fraccionDelAncho)` con el alto de un estilo de texto
  dado. En Android las animaciones de Compose ya respetan la escala de animación del sistema.
- **Hero del Inicio** (`HeroDeUnVistazo` en `shared/.../ui/sdui/SeccionesDeUnVistazo.kt`): cuando
  `data.accounts == null && data.summary == null`, en lugar de «—» y de nada: un bloque del alto de
  `CifraProtagonista`, dos líneas en lugar del veredicto, la barra de dos tramos y la fila Entró/Salió
  como esqueletos. **El alto del hero cargando tiene que ser igual al alto cargado** con veredicto y
  barra (medirlo en una prueba Robolectric con un margen chico, p. ej. ±8 dp).
- **Pregúntale a Movi** (`PreguntaleAMoviSection`): con los mismos datos vacíos, tres filas esqueleto
  del alto de `FilaDePregunta` en lugar de las preguntas genéricas (el campo para escribir se muestra
  igual: se puede preguntar sin datos).
- **Cuentas** (`AccountsScreen.kt`) y **Movimientos** (`TransactionsScreen.kt`): donde hoy se muestra
  una rueda/barra antes de la primera lista, 5-6 filas esqueleto con la forma de la fila real (título +
  subtítulo a la izquierda, monto a la derecha). Si ya hay filas pintadas, no se muestran.
- Pruebas Robolectric: hero cargando vs cargado (alto), Pregúntale con datos vacíos muestra 3 esqueletos
  y no las preguntas genéricas, y con datos muestra las preguntas.
