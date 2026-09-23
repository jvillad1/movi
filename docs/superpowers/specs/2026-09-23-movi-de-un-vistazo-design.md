# Movi de un vistazo — revisión de la experiencia (2026-09-23)

## El pedido

> «ayudame a revisar toda la experiencia de usuario de la aplicación, mi intención es que cuando yo
> entre a la app pueda ver toda mi información financiera y control de finanzas personales de una
> manera muy simple minimalista, clara y con efecto wow posible, habilitando el luego poder compartir
> dicha información con un tercero o poder hacer preguntas via chat de IA para poder obtener valiosa
> asesoría y consejos financieros»

Tres entregables: **(1)** ver todo de un vistazo, simple y con efecto wow; **(2)** compartir con un
tercero; **(3)** asesoría valiosa por chat de IA.

## Lo que se encontró (auditoría del 22-23 sep, con los datos reales del dueño)

### El Inicio muestra ~12 cifras antes de hacer scroll, y se contradicen

En la web, arriba del pliegue: Tu plata **$558.350** · «Además $116,2M de uso condicionado» · la
lista de 4 cuentas · Patrimonio neto **−$2.074,2M** · Ingresos **$22,2M** · Gastos **$33,9M** · Flujo
del mes **−$11,7M** · Disponible del período **$13,8M** · «Este período $14,4M de $13,8M» · «Te
pasaste por $563.456» · semana «$72.380 de $1,8M» · hoy «$0 de $444.873».

Tres conceptos de «lo que tengo» (Tu plata, Disponible, Flujo) y dos veredictos opuestos en la misma
pantalla («Te pasaste» en rojo y «Vas bien» un renglón abajo). No se puede contestar «¿cómo estoy?»
sin leer todo.

### El patrimonio neto miente por omisión: −$2.074M

Cuenta **$2.191M de deudas** (dos hipotecas, dos libranzas, el vehículo…) y **cero bienes**. La casa de
Almendros de Zúñiga tiene avalúo comercial de **$1.411.903.920** (Banco Popular, 28-ago; está en la
nota de la Hipoteca 1254) y no existe en Movi, ni el vehículo, ni Gardenera. «Toda mi información
financiera» sin lo que uno tiene es media foto, y la mitad que falta es la que da tranquilidad.

### Movi AI está al final, mudo en el arranque y (hasta ayer) roto para consejos

- Es la **última** sección del Inicio, un banner.
- Abre con «Pregúntame lo que quieras» y nada más: el dueño escribió «Hola, me puedes ayudar?» porque
  no sabía qué preguntar.
- Toda pregunta de criterio contestaba «(sin respuesta)» (arreglado en #367, desplegado 78bec1a0).

### No hay ninguna forma de compartir

Ni link, ni exportación, ni vista para otra persona. Si el dueño quiere mostrarle su situación a Caro
o a un asesor, hoy tiene que darle su contraseña o mandar capturas.

### En escritorio, una columna de 500 px en un lienzo de 1.400

La web se usa en el computador (el dueño la tiene abierta en Chrome) y ahí el Inicio es una tira angosta
en el medio de un fondo vacío.

## La dirección

**El Inicio contesta tres preguntas, en este orden, con UNA cifra cada una:**

1. **¿Cómo estoy?** — Tu plata disponible, grande, con **un veredicto en lenguaje simple** debajo
   («Este período entró más de lo que salió» / «Este período salieron $X más de los que entraron»),
   derivado de UNA sola regla y sin contradicciones con el resto de la pantalla.
2. **¿Qué viene?** — Falta por pagar (lo que ya existe: el checklist de solo lectura, #363).
3. **¿En qué se va?** — Las categorías del período (ya existe).

Y dos piezas nuevas que hoy no existen:

- **Tu patrimonio, honesto**: lo que tenés (plata + uso condicionado + **bienes**) contra lo que debés,
  con una barra de dos colores que se entiende sin leer números. Requiere **bienes** en el modelo.
- **Pregúntale a Movi**, arriba y no al final, con **tres preguntas sugeridas a partir de los datos**
  («¿Qué deuda me conviene abonar primero?», «¿Cómo cierro el período si me pasé $563.456?»…).

**Compartir**: un link de solo lectura, con vencimiento y revocable, que abre una página liviana (HTML
servido por el server, sin login, sin wasm) con el resumen. Pensado para Caro o para un asesor.

**Efecto wow, con mesura** (ver el sistema de diseño, `Tokens.kt`): la cifra grande entra contando,
las barras crecen al cargar (≤600 ms, una sola vez), jerarquía tipográfica fuerte, mucho aire. En
escritorio, dos columnas.

## Reglas que no se negocian

- **Nada de valores nuevos en un `enum` serializado** (`AccountType` y cía.): un APK viejo revienta al
  deserializar. Campos nuevos con default, siempre (ver el KDoc de `OccurrenceState.derivadaDeUnMovimiento`).
- El Inicio es **SDUI** (`screen_definitions`, `DASHBOARD_LAYOUT_VERSION`): leer el KDoc de
  `DashboardDefaults.kt` antes de agregar, quitar o reordenar secciones. Cambiar cómo se PINTA un tipo
  existente viaja en el binario; cambiar la LISTA necesita subir la generación.
- Texto de usuario en español neutro, tuteo, sin voseo (`VoseoScanTest`).
- Colores, espacios, formas y textos solo desde `Movi.*` (`Tokens.kt`); `ContrasteDeLosTokensTest` mide
  todo color nuevo.
- Movi AI: Haiku por defecto, Sonnet solo para criterio (`QueModeloUsar.kt`); nada que encarezca el
  camino de datos.
- Un link compartido es una **capacidad**: token impredecible, vencimiento, revocable, sin números de
  cuenta completos, y **fuera de los logs** (`CallLogging` imprime la ruta).

## Las entregas

| # | Qué | Depende de |
|---|---|---|
| A | **Bienes**: inmuebles y vehículos como activo (campo nuevo, no enum), en patrimonio y fuera de «Tu plata» | — |
| C | **Movi asesor**: preguntas sugeridas desde los datos, arranque del chat con sugerencias, contexto completo (bienes, quién paga, tasas), respuesta con estructura | — |
| D | **Compartir**: link de solo lectura con vencimiento, página HTML del server, administrar/revocar | — |
| B | **Inicio de un vistazo**: las tres preguntas, patrimonio honesto, «Pregúntale a Movi» arriba, animación de entrada, dos columnas en escritorio | A, C |

## Entrega A — cómo quedó (bienes)

- **Forma**: un bien es una cuenta `INVESTMENT` con un campo nuevo `Account.bien` (`Bien`: `clase`
  como texto —`INMUEBLE`/`VEHICULO`/`OTRO`—, `valor` en pesos, `valorAl` ISO y `deudaId`
  opcional). Ningún enum serializado cambió. El server fuerza la forma y manda `balance = 0`: un APK
  viejo ve una inversión en $0 (Tu plata y patrimonio no cambian para él); el nuevo la cuenta en
  bienes. Ver el KDoc de `Account.bien`.
- **La regla**: `patrimonioDe` en `:core` (`Patrimonio.kt`) parte las cuentas en tu plata /
  condicionado / bienes / deudas. La usan el hero del Inicio y la tarjeta de Cuentas (vía
  `heroBalance`/`assetsDebtsNet`), `/api/dashboard/summary` (campo nuevo `patrimonio`),
  `/api/finance-summary` (reemplazó a `netWorth`) y el contexto de Movi AI (bloque «== Patrimonio ==»).
- **API**: se crea con `POST /api/accounts` con `bien` adentro; se actualiza con
  `PUT /api/accounts/{id}/bien` (`{"bien":{...}}`, el bien entero); el nombre con `PUT /{id}/name`.
  Un bien no se cuadra (422) y borrar su deuda lo suelta.
- **UI**: Cuentas tiene la sección «Bienes» (valor, «avalúo del 28 de agosto», «Debes … · tuyo …»)
  y el renglón «Bienes» en la tarjeta de patrimonio; se crea desde «Nueva cuenta» → «Bien» y se
  edita tocando el renglón (`BienSheet`). La línea del patrimonio del Inicio nombra los bienes.
- **Para B**: `DashboardSummary.patrimonio` trae las cifras ya partidas (`loQueTienes`, `neto`)
  para la barra de dos colores sin pedir la lista de cuentas.


## Entrega C — cómo quedó (Movi como asesor)

- **Preguntas sugeridas, sin LLM**: `preguntasSugeridas(data: DashboardData): List<String>` en
  `:shared` (`ui/ai/PreguntasSugeridas.kt`). Devuelve **siempre tres**, sin repetidas, de la más
  relevante a la menos: deuda que no baja → lo que falta por pagar no cabe en Tu plata → presupuesto
  pasado → salió más de lo que entró (con la diferencia) → intereses propios altos (con la cifra) →
  deudas con tasas distintas → patrimonio con bienes; lo que falte lo llenan `PREGUNTAS_DE_RESPALDO`.
  Una lectura en `null` apaga su regla, no inventa. Las reglas sueltas se prueban sobre
  `SenalesParaPreguntar`. Las de criterio están redactadas para escalar al modelo de consejos y las
  de dato no (lo fija `QueModeloUsarTest`).
- **El chat abre con una pregunta lista**: `Screen.AIChat(preguntaInicial: String? = null)`. La
  pantalla la manda **sola, una vez** (un `rememberSaveable` evita pagarla dos veces al volver).
- **Arranque del chat**: saludo con el primer nombre de la sesión (ya no «Camilo» escrito a mano),
  qué mira Movi, las tres sugerencias como filas tocables (tocar = enviar) y la nota de que no
  reemplaza a un asesor certificado. Las sugerencias salen de `DashboardDataCache`; sin caché, las
  de respaldo.
- **Contexto**: cada crédito dice **quién paga la cuota** en palabras (su bolsillo / la nómina / un
  tercero), el saldo, y —con `planDeUnaDeuda`, la misma cuenta de Créditos— el interés del mes, lo
  que baja la deuda, las cuotas que faltan o que la deuda crece; van de la tasa más alta a la más
  baja, con los totales de intereses y cuotas partidos por quién paga. Los presupuestos llevan lo
  gastado y cuánto se pasó (regla `estadoDePresupuesto`). Los recurrentes, el total que falta.
- **PERSONA**: una pregunta de criterio se contesta con diagnóstico en una frase → dos o tres
  acciones con sus números → el riesgo o lo que hay que confirmar; y antes de opinar de una deuda,
  mirar quién paga la cuota.
- **No se agregó «Disponible del período» al contexto**: se calcula en `:shared`
  (`disponibleDelPeriodo`, con el checklist) y el server no tiene esa cuenta; replicarla daría dos
  cifras distintas para lo mismo. El modelo tiene Tu plata y el total de recurrentes pendientes.

## Entrega B — cómo quedó (el Inicio de un vistazo)

- **Generación 8 del Inicio** (`DASHBOARD_LAYOUT_VERSION = 8`, `DashboardDefaults.kt`). Orden, que
  es el del teléfono: `HERO_BALANCE` → `PREGUNTALE_A_MOVI` → `CHECKLIST_DEL_PERIODO` →
  `DISPONIBLE_DEL_PERIODO` → `GASTO_POR_CATEGORIA` → `PATRIMONIO` → `ALERTS` → `BANNER`. Dos tipos
  nuevos en `ScreenTaxonomy.SECTION_TYPES`: `PREGUNTALE_A_MOVI` y `PATRIMONIO`. Ningún enum
  serializado cambió.
- **Qué ve un APK viejo**: los dos tipos nuevos no están en su taxonomía y `renderableSections` los
  descarta, así que ve EXACTAMENTE su Inicio de la generación 7 — hero (el suyo, con el patrimonio),
  falta por pagar, disponible, categorías, para revisar y **el banner de Movi AI**. Por eso el
  `BANNER` se queda último en la lista: sin él, ese teléfono se quedaba sin Movi AI en el Inicio. El
  cliente nuevo lo esconde cuando la definición trae `PREGUNTALE_A_MOVI` (`visibleSections`,
  `esElBannerDeMoviAi`). Lo fija `DashboardDefaultsTest.un_apk_viejo_ve_el_inicio_de_la_generacion_7`.
- **Y un cliente nuevo con una fila vieja** (generación 7, server sin desplegar): pinta el banner y el
  hero dice el patrimonio en una línea, porque la definición no trae la tarjeta `PATRIMONIO`.
- **¿Cómo estoy?** (`HeroDeUnVistazo`, `ui/sdui/SeccionesDeUnVistazo.kt`): Tu plata grande, el rango
  del período, **un veredicto** y la barra entró/salió con «Entró / Salió». El veredicto es
  `veredictoDelPeriodo` (`ui/dashboard/InicioDeUnVistazo.kt`): con el flujo en contra nombra las
  cuotas de crédito si las hay (son deuda que baja, no consumo) o la categoría que más pesó; a favor,
  la diferencia; y si está a favor pero el disponible se pasó, lo dice en la misma frase. Con los
  números del dueño: **«Este período salieron $11,7M más de los que entraron — las cuotas de crédito
  fueron $12,9M»**. Salieron del hero la lista de cuentas, el uso condicionado, el patrimonio y el
  «Flujo del mes»; tocar la cifra lleva a Cuentas.
- **Coherencia**: la tarjeta del disponible tiene UNA frase (`fraseDelDisponible`), manda la peor
  ventana y nunca dice «vas bien»; el hero y la tarjeta comparten `excesoDelDisponible`. La prueba
  `el veredicto y el disponible nunca se contradicen` recorre una grilla de flujos y gastos. Se
  borraron `comoVieneElPeriodo/LaSemana/Hoy` y `rotuloDelPeriodo`.
- **El disponible, compacto**: la cifra, la frase y tres columnas (Período / Semana / Hoy) con lo
  gastado, «de» la meta y la barra. De dónde sale queda detrás de «¿De dónde sale?».
- **Pregúntale a Movi** (`PreguntaleAMoviSection`): las tres de `preguntasSugeridas(data)`, tocables
  (`Screen.AIChat(preguntaInicial = …)`), y un campo «Escribe tu pregunta…» que abre el chat vacío.
  El campo es un botón con cara de campo: el chat ya tiene el suyo (el que adjunta extractos).
- **Tu patrimonio** (`PatrimonioSection`, regla `patrimonioDelInicio`): neto en el color del texto
  (no en rojo), la barra de dos colores Tienes/Debes y los tramos con nombre (Tu plata —con el saldo
  de cada cuenta debajo, que el dueño había pedido en el hero—, uso condicionado, bienes, a favor en
  créditos, deudas). Deudas → Créditos; lo demás → Cuentas. Se calcula desde `data.accounts` (la
  misma lista que suma el hero) y cae al `DashboardSummary.patrimonio` solo si las cuentas no
  llegaron: así «Tu plata» arriba y el tramo abajo coinciden por construcción.
- **¿En qué se va?**: las primeras cuatro categorías y el resto agrupado; «Ver todas» las despliega
  en el lugar.
- **Escritorio**: `anchoMaximoDeLaPantalla` deja al Inicio llegar a 1.200 dp (el resto de las
  pantallas sigue en 600). Desde 900 dp, `columnasDelInicio` reparte por tipo — izquierda: hero,
  categorías, para revisar; derecha: Pregúntale a Movi, falta por pagar, disponible, patrimonio —,
  conservando el orden de la definición en cada una. El renderer dejó de ser una `LazyColumn`
  (dos columnas que scrollean juntas no caben en una) y registra su scroll en el relevo de los márgenes.
- **La entrada**: la cifra cuenta y las barras crecen en 600 ms (`rememberProgresoDeEntrada`), una
  vez por proceso y por bloque (`DashboardDataCache.entradasHechas`, que `clear()` vacía y vigila
  `ElForkLlegaLimpioTest`). Volver al Inicio no la repite.
- **«Para revisar» ya no repite el veredicto**: su aviso «Vas gastando más de lo que entró este
  período» salía debajo de un hero que dice lo mismo con el número (se vio a ojo en escritorio, con
  los dos a la vista). El puente `cosasParaRevisarDe` no le pasa el flujo cuando el hero da su
  veredicto; la regla pura de `cosasParaRevisar` no cambió.
- **Lo que no se tocó**: la lógica del checklist, las demás reglas de «Para revisar», el ícono de
  compartir.
- **Mirado a ojo** (wasm armado en CI, API simulada con datos de ejemplo, Playwright): 390 px y
  1.400 px, tema oscuro y claro, y 390 px con una fila de la generación 7 (el hero dice el
  patrimonio y el banner aparece al final). La animación de entrada no se miró en el navegador; la
  prueban `InicioDeUnVistazoEnPantallaTest` (reloj detenido) y el marcador de `entradasHechas`.
