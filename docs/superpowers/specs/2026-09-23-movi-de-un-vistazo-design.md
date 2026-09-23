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

