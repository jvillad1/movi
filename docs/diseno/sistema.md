# El sistema de diseño de Movi

**Vigente desde el 28-oct-2026 (vuelta 29): la opción 3 «Acción primero»** del lienzo «Movi · 5
caminos», elegida por el dueño —«me encantó a mí y a mi esposa mucho más que 1, 2 y 4»—. Reemplaza al
camino 1 «Noche premium» (oscuro, lavanda, Space Grotesk y Martian Mono) que había fijado la ola R0
(26-oct-2026): **un solo tema, claro**, fondo crema, tarjetas blancas, la tinta casi negra, el amarillo
como acción y **Figtree** para todo. Lo que la ola R0 ordenó —las escalas, los componentes, las
guardas— sigue igual; cambió la piel (§3, §4, §5 y §41).

El pedido de la R0 sigue en pie: **que los espaciados, los componentes y todo sea extremadamente
estandarizado y correcto en toda la app, que no haya inconsistencias entre pantallas y que las
secciones tengan sentido.**

Una pantalla **no decide** espacios, radios, colores ni tamaños: elige de esta página. Lo que no
está acá no existe; si hace falta, se agrega acá primero (y su prueba con él).

- Tokens: `shared/src/commonMain/kotlin/com/jvillada/movi/theme/Escalas.kt` (`Espacio`, `Radio`,
  `Icono`, `Medida`) y `Tokens.kt` (colores, textos; `Movi.colores`, `Movi.textos`).
- Componentes: `shared/src/commonMain/kotlin/com/jvillada/movi/ui/components/ds/`.
- Por qué cada número: `docs/diseno/auditoria-2026-10.md`.

## 1. Espacio

Ocho pasos y nada más:

| token | dp | cuándo |
|---|---:|---|
| `Espacio.xxs` | 4 | entre un ícono y su rótulo; entre dos renglones apretados |
| `Espacio.xs` | 8 | entre hermanos chicos: chips, píldoras, botones de una fila; entre el texto y el final de una fila |
| `Espacio.s` | 12 | dentro de una sección; entre el inicio y el texto de una fila |
| `Espacio.m` | 16 | el margen de la pantalla; el relleno de una superficie |
| `Espacio.l` | 20 | el relleno horizontal de un botón |
| `Espacio.xl` | 24 | entre dos secciones |
| `Espacio.xxl` | 32 | respiro de un vacío |
| `Espacio.xxxl` | 40 | respiro de una pantalla de una sola cosa |

Las reglas que se repiten tienen nombre propio (`Medida`), y se usa el nombre, no el número:

| regla | valor |
|---|---:|
| `Medida.margenDePantalla` | 16 |
| `Medida.entreSecciones` | 24 |
| `Medida.rellenoDeSuperficie` | 16 |
| `Medida.dentroDeSeccion` | 12 |
| `Medida.altoDeFila` / `altoDeFilaDoble` | 56 / 58 (era 64; vuelta 29) |
| `Medida.objetivoTactil` | 48 — nada que se toque mide menos |
| `Medida.altoChico` | 36 — botón chico, solo dentro de una tarjeta |
| `Medida.altoDeFiltro` | 44 — un filtro (`PildoraDeFiltro`), el de la opción 3 |
| `Medida.anchoDePildoraDePestana` × `altoDePildoraDePestana` | 56 × 30 — la píldora amarilla de la pestaña activa |
| `Medida.altoDeBarraDePestanas` | 76 (era 64; vuelta 38) — la barra de 4 pestañas del teléfono |
| `Medida.altoDeEncabezado` | 56 |
| `Medida.avatar` / `avatarGrande` / `accionRapida` | 40 / 48 / 56 |
| `Medida.altoDeMarcaDeBarra` | 12 — la marca sobre una `BarraDeProgreso` («la mitad» en Espacios) |
| `Medida.altoDeTarjetaDeCuenta` | 128 — la tarjeta de una cuenta en el carrusel (ola R1) |

`Movi.espacios.*` sigue existiendo (lo nombra media app) y ahora sale de acá. **Con la R0 cambiaron en
toda la app a la vez:** el margen de pantalla de 20 a 16 y la separación entre secciones de 32 a 24.

**Ola R4b:** `Grafica` (en `Escalas.kt`) nombra las medidas de una gráfica dibujada a mano en un
`Canvas` —`alto` 120, `margen` 6, `trazo` 2, `punto` 2,5, `aroDelPunto` 3,5, `puntoDestacado` 4—: la
de «Tu plata, día a día» de Plan las tenía sueltas.

## 2. Radios e íconos

| token | dp | para |
|---|---:|---|
| `Radio.s` | 8 | chips chicos, campos, muestras |
| `Radio.m` | 12 | filas destacadas |
| `Radio.l` | 18 | superficies: listas y tarjetas (opción 3) |
| `Radio.xl` | 22 | la tarjeta principal (el héroe, una propuesta), tarjetas de cuenta, hojas |
| `Radio.pill` | 999 | botones, píldoras, filtros, círculos |

Antes eran 6 / 11 / 16 / píldora: el 6 pasó a 8 y el 11 a 12 (a la grilla de 4, y los valores que la
app ya escribía a mano), y las hojas de 28 a 24. **Vuelta 29:** `Radio.l` de 16 a 18 y `Radio.xl` de
24 a 22, los de la maqueta de la opción 3; el `Boton` pasó de `Radio.m` a píldora. `Movi.formas.*` sale
de acá.

Íconos: **`Icono.s` 16** (en un texto, en una píldora), **`Icono.m` 20** (fila, botón, pestañas),
**`Icono.l` 24** (solo, en un encabezado o en un círculo grande). Nada más.

## 3. Superficies: blanco sobre crema, sombra solo donde destaca

Un solo tema desde la vuelta 29. La paleta es la de la maqueta de la opción 3 tal cual
(`project/p3/*.dc.html`), salvo los tres valores que puso la prueba (ver §4):

| capa | valor | para |
|---|---|---|
| `fondo` | `#F4F1EC` | el lienzo: el crema |
| `capa1` (= `tarjeta`) | `#FFFFFF` | tarjetas, listas, hojas, barra de pestañas, botón secundario, filtro sin elegir |
| `capa2` | `#FBF8F3` | la superficie suave: la franja de abajo de una tarjeta («Por confirmar» debajo del Disponible), un campo |
| `capa3` | `#F1EDE6` | pistas de progreso, la píldora neutra, un botón apagado |
| `propuesta` | `#FFFFFF` | una propuesta de Movi: blanca, **con sombra** y radio 22 |

Una tarjeta se despega del crema **por ser blanca** (1,13:1); la que destaca —la principal de una
pantalla, una propuesta— además por la **sombra suave** de la maqueta (`0 1px 2px rgba(31,27,22,.06),
0 10px 24px rgba(31,27,22,.08)`; en Compose, `Modifier.shadow` con `Sombra.destacada` = 6 dp y el color
`sombra`, la tinta). **Las listas van sin sombra.** `Superficie(destacada = true)` la pide, y el nivel
`Propuesta` la lleva siempre (`laSuperficieLlevaSombra`, y `SuperficieDestacadaKey` en la semántica
para vigilarlo). La capa 2 casi no se distingue del blanco (1,06): lo que la separa es el `hilo` de
arriba, como en la maqueta.

Los bordes son tres, de menos a más firme, y van alrededor de algo **blanco**, nunca de una tarjeta:

| token | valor | para |
|---|---|---|
| `hilo` | `#EFEAE1` | el separador entre filas de una lista |
| `borde` | `#E7E1D7` | el círculo de una acción rápida, el botón redondo de un encabezado, el borde de arriba de la barra de pestañas, un campo |
| `bordeDePildora` | `#E0D9CD` | un filtro o una píldora de acción sin elegir |
| `bordeDeBoton` | `#D9D1C3` | el botón secundario (a 1,5 dp, `Medida.trazoDeBoton`) |

`ContrasteDeLosTokensTest` mide: cada texto ≥ 4,5:1 sobre el fondo, cada capa, la propuesta y el tinte;
la tarjeta ≥ 1,12 del fondo (era 1,15 en el camino 1: el blanco sobre el crema de la maqueta da 1,13 y
lo que despega lo destacado es la sombra); la capa 2 ≥ 1,05 de la 1 y la 3 ≥ 1,08 de la 2; cada borde
≥ 1,25 contra la tarjeta y en orden; cada color de estado ≥ 4,5 sobre su propio lavado.

## 4. Marca, enlace y estado

| rol | valor | para |
|---|---|---|
| `marca` | `#FFC21A` | **relleno** de la acción: botón principal, acción destacada, píldora de la pestaña activa, botón flotante |
| `sobreMarca` | `#1F1B16` | lo que va sobre el amarillo (10,58:1) |
| `sobreMarcaMedio` | `#3D3220` | el texto secundario sobre el amarillo (7,75:1) |
| `marcaSuave` | `#FFE08A` | los botones redondos sobre la franja amarilla |
| `tinte` | `#FFF2C6` | el fondo de un aviso y de «por confirmar»; es el `lavado` de la marca y del aviso |
| `aviso` | `#6B4A00` | el texto sobre el tinte (7,21:1): «Vence el martes», «3 avisos» |
| `enlace` | `#1D4E9E` | enlaces y acciones de texto: «Ver toda la actividad», `Boton` `Fantasma`, la acción de un encabezado, el foco de un campo |
| `texto` / `textoMedio` / `textoApagado` | `#1F1B16` / `#5C554C` / `#6E665B` | la tinta, el apoyo de la maqueta y el más tenue que pasa sobre la capa 3 (4,84:1) |
| `entra` | `#147849` | entró plata (sobre su lavado ≈ `#E3F1E8`) |
| `sale` | `#B3261E` | **solo alertas**: lo que se pasó o se debe, «Borrar mi cuenta» |
| `entreCuentas` | `#556372` | un gris azulado: el `#1F5B96` de antes se leía como el enlace (1,07:1 entre los dos) |
| `capital` / `neutro` | `#8A5A18` / `#A39A8C` | sin cambio de papel |

- **La marca es un relleno, nunca una letra.** El amarillo da 1,6:1 sobre el blanco: no se lee como
  texto, ni como ícono, ni como borde. Lo que hasta la vuelta 28 era «texto en la marca» (un enlace, «Ver
  todo», un check de «elegido», el foco de un campo, la línea de una gráfica) pasó a `enlace`; lo que era
  `lavado(marca)` como fondo de algo elegido, a `lavado(enlace)`. La prueba de contraste lo sostiene: la
  marca ya no está en la lista de textos y hay una prueba de que el amarillo no se lee como letra.
- **Lo elegido va en tinta, siempre**: el filtro elegido, el segmentado (`SelectorSegmentado`, Gasto ·
  Ingreso · Traspaso · Cuota en Agregar, Pagos · Presupuestos en Plan), la casilla marcada, el interruptor
  encendido, el día o la moneda elegidos en una hoja: la tinta de fondo con la letra blanca
  (`coloresDeLoElegido`). Una fila elegida de una lista lleva el lavado de la tinta (`fondoDeLaFilaElegida`)
  y un «elegido» con visto, el visto en tinta. **El amarillo lleno es solo de la acción principal** y el
  azul, solo de un enlace. El `primary` de Material también es la tinta. La barra de un avance propio
  (`TonoDeBarra.Marca`) va en tinta.
- **Los colores de estado son solo para estado**: `entra`, `sale` (alerta), `aviso` (por vencer, por
  confirmar), `entreCuentas`. Una píldora en aviso dice «vence», no «esto es importante».
- **Un gasto no es una alarma**: va en el color del texto. `sale` es para lo que se pasó o se debe.
- **El color nunca es la única pista**: un monto lleva «+» solo cuando entra plata, y el subtítulo dice
  el verbo.
- **El lavado** de un color es `Movi.colores.lavado(color)` (10 %), nunca un `copy(alpha = …)` a mano. La
  marca y el aviso no se lavan con alfa: su lavado es el `tinte`.
- **Los colores de marca de los bancos van solo en su avatar** (`MarcasConocidas`): la `TarjetaDeCuenta` es
  blanca, con o sin marca, y el color del banco va en el círculo con su letra. El amarillo de Bancolombia
  de fondo se confundía con el de la marca de Movi, y el morado de Nu pesaba en el carrusel.

Los tres pares que la prueba pidió cambiar de la maqueta: **`entra` `#147849`** (la maqueta dice
`#14794A`, que daba 4,47:1 sobre su lavado encima de la capa 2; un paso más oscuro da 4,53), **`sale`
`#B3261E`** (la maqueta no trae rojo) y **`textoApagado` `#6E665B`** (la maqueta tiene dos niveles de
texto; el tercero es el más claro de la familia que pasa sobre la capa 3).

**Solo claro.** No hay tema oscuro ni preferencia: «Cómo se ve» en Ajustes deja solo «Ocultar los
saldos»; `TemaStore` se borró (lo guardado en `tema`/`tema_oscuro` se ignora); la app, el widget, el
ingreso web (`index.html`) y la página compartida con un tercero se pintan claros aunque el teléfono
esté en modo oscuro (`SoloHayUnTemaTest`).

## 5. Tipografía

**Figtree para todo, también las cifras** (vuelta 29). Cinco TTF estáticos —400, 500, 600, 700 y 800—
en `composeResources/font/figtree_*.ttf`, licencia OFL en `docs/licencias/OFL-Figtree.txt`. Las cifras
(`cifra`, `cifraDeTarjeta`, `monto`) van en la misma letra **con números tabulares**
(`fontFeatureSettings = "tnum"`): una columna de montos alinea sin la monoespaciada. Space Grotesk y
Martian Mono se borraron. Ningún `fontSize` suelto (lo vigila `LosTamanosSueltosSoloBajanTest`).

**Los sp son la maqueta ÷ 1,12.** `App()` agranda toda la letra ×1,12, así que un estilo escrito con los
px de la maqueta se veía 12 % más grande: la primera versión de la vuelta partía «Bancolombia Ahorros» y
«Mercado del sábado» en dos renglones y cortaba «Arrie…». Cada estilo es lo que, ×1,12, da la maqueta
(lo fija `TipografiaTest`):

| estilo | sp | se ve como | maqueta |
|---|---|---|---|
| `cifra` | 36 / 800, tracking −1,1, tabular | 40,3 | la cifra héroe (40/800) |
| `cifraDeTarjeta` | 21,5 / 800, tabular | 24 | la cifra de una tarjeta de cuenta o un widget |
| `portada` | 28,5 / 800 | 32 | «Movi» en la entrada, la bienvenida de la guía |
| `titular` | 25 / 800, interlineado 30 | 28 | el título de una **pestaña** (el encabezado con el avatar) |
| `titularDePantalla` | 19,5 / 800 | 22 | el título de una pantalla con flecha («Personas» en la maqueta) |
| `saludo` | 15 / 700 | 17 | «Hola, Juan» en Hoy y el nombre en la tarjeta del perfil, con la fecha o el correo debajo |
| `titulo` | 14,5 / 700 | 16 | el título de una sección o de una tarjeta |
| `cuerpo` | 13,5 / 600 | 15 | el título de una fila, un filtro sin elegir, un enlace |
| `monto` | 13,5 / 700, tabular | 15 | el monto de una fila |
| `apoyo` | 11 / 400 | 12,3 | el subtítulo |
| `rotulo` | 12 / 600 | 13,4 | un rótulo chico; la base de las pestañas (sin tocar: la barra la rehace la vuelta 38) |

El `Encabezado` elige el suyo con `TamanoDelTitulo`: `Pestana` con el avatar, `Pantalla` con la flecha,
`Saludo` en Hoy. Con el título de pestaña a interlineado 30, el encabezado sigue en 56.

**Un renglón mide su interlineado** (`RENGLON_ENTERO`: `Trim.None`, centrado), en todos los estilos menos
`cifra`. Un texto de N renglones mide N × `lineHeight`, que es lo que reservan los esqueletos
(`LineaEsqueleto`, `altoDeUnRenglon`): con el recorte de siempre, lo cargado y su esqueleto diferían de a
unos dp por renglón y lo de abajo saltaba al llegar el dato. La `cifra` (36 sp) se queda con el recorte: su
letra es más alta que su interlineado y Agregar, anclada abajo, no tiene ese lugar.

**El menos va pegado al «$».** El `−` (U+2212) de Figtree existe y es del ancho de un dígito con el trazo
centrado, así que «−$85.000» se leía «- $85.000». `Cifra`, `CifraProtagonista` y la `Cifra` de texto le ponen
`Movi.textos.menosPegado` (−0,12 em) solo al menos (`conElMenosPegado`); el texto no cambia
(`ElMenosVaPegadoTest`, y `LoQueSeVeEnteroTest` lo mide).

**Los pesos con nombre** (vuelta de diseño 21). Una pantalla no escribe `fontWeight =`: elige un estilo.
Son los de arriba con otro peso de Figtree, y se derivan de su base en el cuerpo de `TextosDeMovi`, así
llevan su familia y nunca se separan de ella en tamaño ni interlineado (lo fija `TipografiaTest`):

| estilo | es | para |
|---|---|---|
| `apoyoMedio` | `apoyo` en 600 | el mini-título: el rótulo de un dato arriba de su valor, el texto de una píldora, el rótulo de una acción rápida |
| `tituloMedio` | `titulo` en 600 | un nombre que encabeza sin gritar: una cuenta, una categoría, un tercero arriba de su detalle; el título de `EncabezadoDeHoja` |
| `tituloRegular` | `titulo` en 400 | lo que acompaña a un título en su renglón: el conteo de una `Seccion` |
| `cuerpoFuerte` | `cuerpo` en 700 | lo elegido y lo que se toca: el texto de un `Boton` (15/700 a la vista), el filtro elegido |
| `cuerpoRegular` | `cuerpo` en 400 | un párrafo (la respuesta de Movi AI, una explicación larga) |
| `pestana` / `pestanaActiva` | `rotulo` en 500 y en 700 | el rótulo de una pestaña |
| `enfasis` | `SpanStyle` en 700 | el énfasis dentro de una frase (`withStyle(Movi.textos.enfasis)`) |

**iOS sigue con la letra del sistema** (`laPlataformaTraeLasFuentes = false` en `LetraPropia.ios.kt`),
con los mismos tamaños, pesos y cifras tabulares. Prenderlo pide abrir la app en un iPhone o el
simulador y ver que `Font(recurso)` encuentra el archivo en el framework estático; ver §41.

## 6. Los componentes (`ui/components/ds/`)

Cada uno con pocos parámetros; las variantes, en un `enum`.

**`Superficie`** — el contenedor de toda tarjeta. Reemplazó a `MinCard`, borrada en la vuelta de diseño 21.
Radio 18; `destacada = true` (y el nivel `Propuesta`) con radio 22 y la sombra suave (§3).
```kotlin
Superficie { Text("Tu plata", style = Movi.textos.apoyo); Cifra(tuPlata, VarianteDeCifra.Grande) }
Superficie(relleno = RellenoDeSuperficie.Lista) { Fila(…); Separador(); Fila(…) }
Superficie(nivel = NivelDeSuperficie.Capa2) { … }
```

**`Seccion`** — el título de una sección. **La única forma de titular una.** En minúsculas como se
escribe, en `titulo`, con conteo y acción opcionales; trae su espacio de abajo.
```kotlin
Seccion(titulo = "Tarjetas", conteo = 3, accion = "Ver todo", onAccion = ::verTodas)
```

**`Fila`** — la fila de lista de toda la app: `inicio` (avatar de comercio, `IconoEnCirculo`, persona),
`titulo`, `subtitulo?`, `final` (`Cifra`, `Pildora` o `Chevron`). 12 entre el inicio y el texto, 8 entre
el texto y el final (vuelta de diseño 15). 56 / 58 de alto mínimo (vuelta 29), 4 arriba y abajo, 16 a los
lados, toque de borde a borde. El título va en hasta dos renglones antes del «…» (la fila crece) y el
subtítulo en uno —o en dos con `lineasDelSubtitulo = 2`, solo donde el subtítulo es la razón de la fila
y no se puede acortar («Bancolombia Ahorros · Nunca la has cuadrado», vuelta de diseño 2)—;
el final se mide primero, así que un monto nunca se recorta. `tonoDelSubtitulo = TonoDeSubtitulo.Aviso`
pinta el subtítulo en el color de aviso cuando pide algo al dueño («Faltan datos: tócala para
completarlos», vuelta de diseño 4). **El subtítulo dice algo que el título y la
sección no dicen**: dentro de «Dinero · 2» una cuenta no dice «Dinero» (dice «Ahorros», o nada si su
nombre ya lo dice). Va directo sobre el fondo o en una `Superficie` con relleno `Lista`.
```kotlin
Fila(
    titulo = "Rappi · Las Doce", subtitulo = "Restaurantes · Nu",
    inicio = { AvatarDelComercio(evento) }, final = { Cifra(-27_500) },
    onClick = { abrir(evento) },
)
```

**Vuelta de diseño 5: el tono del título** (`tonoDelTitulo: TonoDeFila`). `Normal` (el color del texto)
casi siempre; `Alerta` (`sale`) para lo que no tiene vuelta —«Borrar mi cuenta» en Ajustes, con su
ícono en `TonoDeIcono.Peligro`—; `Apagado` (`textoMedio`) para una fila que solo informa y no se toca
(«Antes de agosto 2026 no hay movimientos» en Tus períodos). Sale del tema (`colorDelTituloDeFila`),
nunca un `Color` suelto en la pantalla.
```kotlin
Fila(titulo = "Borrar mi cuenta", tonoDelTitulo = TonoDeFila.Alerta,
    inicio = { IconoEnCirculo(Icons.Rounded.DeleteForever, tono = TonoDeIcono.Peligro) },
    final = { Chevron() }, onClick = ::borrar)
```

**`Boton`** — `Principal`, `Secundario`, `Fantasma`, `Peligro`; tamaño `M` (48) o `S` (36, solo dentro
de una tarjeta; su toque igual llega a 48). Ícono opcional y `cargando`. **Opción 3 (vuelta 29):**
píldora; el `Principal` amarillo con la tinta encima, el `Secundario` blanco con `bordeDeBoton` a 1,5,
el `Fantasma` en el azul del `enlace`, el `Peligro` el rojo de alerta sobre su lavado; apagado, la capa 3
con el texto apagado. Texto en `cuerpoFuerte` (15/700).
```kotlin
Boton("Guardar", onClick = ::guardar, anchoCompleto = true, cargando = guardando)
Boton("Borrar", onClick = ::borrar, variante = VarianteDeBoton.Peligro)
```

**`Fantasma` es el botón de texto**: solo el texto en el azul del enlace, sin fondo, **tampoco apagado** (vuelta de
diseño 5: los demás se apagan sobre la capa 2; uno de texto que se rellena de gris parece otro botón,
ver `coloresDelBotonApagado`). Va debajo o al lado de la acción con peso, nunca a todo el ancho: «Saltar
este paso» en la guía de primer uso es `Fantasma`, tamaño `S`, centrado debajo de «Atrás · Siguiente».
```kotlin
Boton("Saltar este paso", onClick = ::saltar, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S,
    modifier = Modifier.align(Alignment.CenterHorizontally))
```

**`Pildora`** y **`PildoraDeFiltro`** — estado (`Neutra`, `Info`, `Aviso`, `Peligro`, `Exito`) y filtro
que se elige. Vuelta 29: la neutra en la capa 3, la de aviso en el tinte con el ocre. **No hay píldora
amarilla** (vuelta 42: el tono `Marca` se borró; pintaba estados como «En curso»). El filtro mide 44 (`Medida.altoDeFiltro`): **elegido, en tinta con la letra blanca**
(`cuerpoFuerte`); sin elegir, blanco con `bordeDePildora` (`cuerpo`).
```kotlin
Pildora("Vence en 2 días", TonoDePildora.Aviso)
PildoraDeFiltro("Gastos", seleccionada = filtro == Gastos, onClick = { filtro = Gastos })
// Vuelta 3: con `inicio`, lo que no es un ícono (el avatar de la marca de un banco, 20 dp).
PildoraDeFiltro(marca.nombre, elegido, onClick, inicio = { CirculoDeMarca(marca, tamano = TamanoDeAvatar.Linea) })
```

**`PildoraDeAccion`** (vuelta de diseño 12) — una píldora que **hace algo** al tocarla: se ve como un
`PildoraDeFiltro` sin elegir (blanca con `bordeDePildora`, `cuerpo`, ícono en el `enlace`) pero es un botón, no dice «elegido».
Alto mínimo `Medida.altoChico`; un texto largo baja a un segundo renglón. `descripcion` es lo que oye
el lector de pantalla si el texto es una versión corta. Hoy: las preguntas para seguir de Movi AI.
```kotlin
PildoraDeAccion("¿Cómo voy?", onClick = ::preguntar, icono = Icons.Rounded.AutoAwesome, descripcion = "¿Cómo voy este período?")
```
Vuelta de diseño 20: `inicio` (lo que no es un ícono: el ícono de una categoría en su color, como en
`PildoraDeFiltro`), `iconoFinal` (la flecha de algo que se despliega) y `habilitada`. Las usan la
categoría de un movimiento, la fecha y «Nueva» en Agregar, las sugerencias de un presupuesto y el
«Es este» de un aviso pendiente.
```kotlin
PildoraDeAccion(nombre, onClick = ::alternar, inicio = { IconoDeCategoria(nombre, tamano = TamanoDeIconoDeCategoria.Chico) },
    iconoFinal = if (abierto) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown)
```

**`AccionRapida`** y **`CuadriculaDeAcciones`** — la cuadrícula del camino 3: cuatro columnas, ícono en
círculo de 56 (vuelta 29: la destacada amarilla con el ícono en tinta; las demás blancas con `borde` y el
ícono en tinta; la insignia en tinta con el número blanco y un aro del fondo; rótulo en `apoyoMedio`) y **rótulo de un renglón** (vuelta de diseño 2: «Gasto», «Comprobante», «Preguntar»; si uno
no entra se achica hasta la letra de `rotulo` y recién ahí lleva «…»), con la frase entera en
`descripcion` para el lector de pantalla («Anotar un gasto»). Una destacada como mucho, insignia
opcional. `RenglonesDelRotulo.Dos` es para rótulos que son nombres de pantallas (eran las fichas de Ajustes, que desde la vuelta 3 es una lista de `Fila`).
```kotlin
CuadriculaDeAcciones(listOf(
    Accion("Gasto", Icons.Rounded.Edit, ::anotar, destacada = true, descripcion = "Anotar un gasto"),
    Accion("Avisos", Icons.Rounded.Inbox, ::revisar, insignia = 3, descripcion = "Revisar los avisos"),
))
```

**`TarjetaDePropuesta`** — el feed del camino 5 y la forma de **toda** propuesta de Movi: avisos por
confirmar, pagos por vencer, anomalías, cuadres, saldos del hogar. Movi propone, el dueño decide.
Una sola anatomía, cada pieza en su renglón: ícono, título, cifra opcional (`Cifra`), subtítulo
(`cuerpo`, a todo el ancho, debajo de la cifra) y acciones. La cifra y el subtítulo nunca van lado a
lado (vuelta de diseño 1: «y 2 pagos más por vencer» se partía en dos al lado del monto).
```kotlin
TarjetaDePropuesta(
    icono = Icons.Rounded.Inbox, titulo = "Llegaron 3 avisos del banco",
    cifra = 186_400, cuerpo = "por confirmar",
    accion = "Confirmar los 3", onAccion = ::confirmar,
    accionSecundaria = "Ver", onAccionSecundaria = ::ver,
)
```

**`Cifra`** — un monto: `Heroe` (una por pantalla), `Grande` (se achica hasta `Fila` si no entra, como
la héroe hasta `Grande`), `Fila`, `Chica`; tono `Neutro`, `Ingreso`,
`Alerta`, `Apagado` y `EntreCuentas` (un traspaso: en `entreCuentas` y sin signo); signo y «Ocultar
los saldos» incluidos.

**Una sola `Cifra` Héroe por pantalla** (vuelta de diseño 14). La héroe es la pregunta de la pantalla
(«Te quedan» en Hoy, el patrimonio neto, la deuda total); todo lo demás —el resumen de una tarjeta
secundaria, como «Gastado en este período» en Presupuestos— va en `Grande`. Dos cifras gigantes
compiten y ninguna se lee primero. Cada `Cifra` dice su variante en la semántica (`VarianteDeCifraKey`;
`CifraProtagonista`, la héroe ya escrita, también) y `UnaSolaCifraHeroePorPantallaTest` las cuenta en
Hoy, Plan (Pagos y Presupuestos), Patrimonio, Créditos, Movimientos y Espacios.
```kotlin
Cifra(-27_500)
Cifra(4_500_000, tono = TonoDeCifra.Ingreso)        // +$4.500.000
Cifra(500_000, tono = TonoDeCifra.EntreCuentas)     // $500.000, sin signo
Cifra(teQuedan, VarianteDeCifra.Heroe)
```

**`FormaDeCifra`** (vuelta de diseño 19) — cómo se escribe: `Entera` («$15.600.000») **siempre**,
salvo en una **columna de tres** (el Disponible del período, las cifras de Tus períodos), donde va
`Compacta` («$15,6M» de un millón para arriba, los pesos por debajo): un tercio de tarjeta a 360 dp
deja ~90 dp y Martian Mono pedía ~9,5 dp por carácter con la letra de la app (0,7 em a 12,5 sp × 1,12),
así que «$15.600.000» no entraba (con Figtree tabular, desde la vuelta 29, la regla se quedó igual). En otra moneda no se compacta. Una fila, una tarjeta, dos cifras lado
a lado o el patrimonio de Hoy van enteras: «$1,5M» al lado de un héroe entero se leía como otro número.
```kotlin
Cifra(resumen.entradas, tono = TonoDeCifra.Ingreso, forma = FormaDeCifra.Compacta)   // +$15,6M
```

**`FormaDeCifra.Abreviada`** (vuelta de diseño 32) — abreviada en **todas** las escalas: «$850 mil»
(con espacio duro), «$1,8M», «$500». No se elige a mano: la decide **`formaComunDeLasCifras`** para un
renglón de cifras que tienen que leerse en la misma forma (la franja Entró · Salió · Neto de
Movimientos y su panel de escritorio). Si **todas** caben enteras en su lugar van `Entera`; si una no
cabe, van `Abreviada` **las tres**. Nunca una entera al lado de una abreviada. Se mide de verdad con
`rememberFormaComunDeLasCifras` (el texto entero de cada una con la letra de su variante; una que se
achica, en su tamaño más chico: `pisoDeLaCifra`).
```kotlin
val forma = rememberFormaComunDeLasCifras(cifrasDelResumen(resumen), VarianteDeCifra.Fila, anchoDeCadaUna)
CifraConRotulo("Entró", resumen.entro, Modifier.weight(1f), tono = TonoDeCifra.Ingreso, forma = forma)
```

**`CifraConRotulo`** (vuelta de diseño 19, `ds/CifraConRotulo.kt`) — una cifra con su nombre:
«Tienes $1.528.700.000», «Entró +$15,6M», «Tuyo $1.250.000». El rótulo en `apoyo`, `textoMedio` y
como se escribe (nunca en mayúsculas); la cifra es la `Cifra` del sistema, con su variante, tono,
moneda y forma como parámetros, así que trae el signo y «Ocultar los saldos». Dos disposiciones:
`Apilada` (lo normal: el rótulo arriba, `Espacio.xxs`, la cifra; `alFinal` alinea las dos a la
derecha, para la última de un renglón de cifras) y `EnRenglon` (el rótulo a la izquierda, `Espacio.xs`
y la cifra a la derecha: un desglose que se lee de arriba abajo, como el resumen de Créditos; el rótulo
se parte antes de que la cifra se recorte). `monto = null` es «todavía no se sabe»: «—» con la letra
de la cifra y el mismo alto. `EsqueletoDeCifraConRotulo` reserva lo mismo mientras carga, y
`RotuloConValor` es la misma pieza para un valor que no es plata (la fecha de la última cuota). Eran
cuatro implementaciones, cada una con su alto y su espacio (`ParDeCifra` en Hoy, `ParDeCifraDePeriodo`,
`FilaDelResumen` de Créditos y los cuatro `Text` del flujo libre de Plan).
```kotlin
Row {
    CifraConRotulo("Tienes", tienes, Modifier.weight(1f))
    CifraConRotulo("Debes", debes, alFinal = true)
}
CifraConRotulo("Flujo libre", cifras?.disponible, variante = VarianteDeCifra.Grande)
CifraConRotulo("Tuyo", interes, disposicion = DisposicionDeCifraConRotulo.EnRenglon)
```

**`TarjetaDeCuenta`** — la tarjeta de una cuenta del carrusel «Tus cuentas» (ola R1): el color y la
marca de su banco (`MarcasConocidas`; la marca va invertida en un círculo), el nombre, el tipo y el
saldo en `Cifra.Grande`. El nombre va en hasta dos renglones (nunca «Bancolombia Ah…») y el tipo baja
junto al rótulo del saldo. **El tipo no se repite** (vuelta de diseño 16): con la regla de Patrimonio
(`tipoSiElNombreNoLoDice`), «Bancolombia Ahorros» va sin «Ahorros» —`tipo = null`— y el renglón queda
con «Saldo» solo, del mismo alto: ninguna tarjeta del carrusel o la grilla salta. Alto mínimo `Medida.altoDeTarjetaDeCuenta` (crece con la letra agrandada),
radio `Radio.xl`, sin borde. Sin marca,
la capa 2 y el ícono del tipo. En un carrusel ocupa `FRACCION_DE_TARJETA_DE_CUENTA` del ancho, para que
la siguiente asome. **Fuera del teléfono va en grilla** (vuelta de diseño 9, `tarjetasPorRenglon`): en
el Inicio a 1280 la segunda tarjeta del carrusel quedaba cortada; con la clase de ancho mediana o de
escritorio van 2 por renglón, 3 si la sección tiene la columna de lectura entera (720), con
`Medida.dentroDeSeccion` entre ellas y las de un renglón del mismo alto.
```kotlin
TarjetaDeCuenta(
    nombre = "Bancolombia", tipo = "Ahorros", rotuloDelSaldo = "Saldo", saldo = 13_845_800,
    marca = marcaDelBanco("Bancolombia"), icono = Icons.Filled.AccountBalance, onClick = ::abrir,
    modifier = Modifier.fillParentMaxWidth(FRACCION_DE_TARJETA_DE_CUENTA),
)
```

**`Widget`** y **`ParDeWidgets`** — un bloque compacto que **resume** una cifra que vive en otra
pantalla y lleva allá (ola R1: «Falta por pagar» → Plan, «En qué se va» → su análisis). Es una
`Superficie` tocable entera con su título y un detalle opcional. **Un monto nunca se recorta**: en una
fila de widget el monto va sin peso (se mide primero, entero) y lo que se achica es la barra o el nombre
(el único que lleva «…»); lo vigila `NadaSeRecortaEnElInicioTest` a 360 y 390 dp, también con la letra
agrandada. Dos van lado a lado y del mismo alto
con `ParDeWidgets`; uno solo va a todo el ancho. **El título nunca se pierde** (vuelta de diseño 7): se
mide primero y el `detalle` se queda con lo que sobra; a media pantalla, «vs. período pasado» a la
derecha se comía «En qué se va» entero.

**«En qué se va» contesta «¿en qué gasto más que el período pasado?»** (vuelta de diseño 7): debajo del
título, una vez, «Contra el período pasado»; por categoría, el ícono y el nombre, el monto con `Cifra`
y la diferencia a la misma altura en palabras —«$120.000 más», «$45.000 menos», «igual», «nuevo»—
(`diferenciaDeLaCategoria`, sobre `contraElAnterior`: ninguna cuenta nueva). **Sin «+»**, que en Movi
es plata que entra. En `sale` solo si sube mucho (los umbrales de «categoría muy por encima»: 1,5× y
$300.000), en el color del texto si sube, apagada si baja. Sin barra: la que había medía contra el
total del período y no decía contra qué. **El monto va siempre neutro** (vuelta de diseño 16): iba en
`sale` si la categoría se pasaba de su presupuesto, y la señal salía dos veces; el tono va solo en la
diferencia. `Cifra` dice su tono en la semántica (`TonoDeCifraKey`) para poder vigilarlo.
```kotlin
ParDeWidgets(
    izquierdo = { m -> Widget("Falta por pagar", onClick = ::abrirPlan, modifier = m) { Cifra(falta, VarianteDeCifra.Grande) } },
    derecho = { m -> Widget("En qué se va", onClick = ::abrirAnalisis, modifier = m) { … } },
)
```

**`Encabezado`** — el de toda pantalla: 56 de alto, 16 de margen, título (y subtítulo), flecha o
avatar, y a la derecha `acciones` (`BotonDeIcono`, una `Pildora` como «Beta», «Actualizando…») y
`accion`. Sin línea debajo. `MinScreenHeader` delega acá (con `action` y `accion`).

**La acción principal de un encabezado tiene una sola forma** (vuelta de diseño 4): `accion =
AccionDeEncabezado(texto, icono, onClick)`, que el `Encabezado` pinta con `AccionDelEncabezado` —un
`Boton` `Fantasma` `S` con su ícono: texto e ícono en la marca, sin relleno, toque de 48— al final de
la fila, con el tag `ds-accion-del-encabezado`. Ninguna pantalla arma su propio botón de encabezado
(Patrimonio lo tenía como texto morado y Créditos como botón tonal con relleno). Si una pantalla tiene
dos o más acciones, la principal va en `accion` y las demás como `BotonDeIcono` con su descripción en
`acciones`. Mientras dice «Actualizando…», la acción no va (las dos juntas cortan el título a 390 dp).

| pantalla | `accion` |
|---|---|
| Patrimonio | «Nueva cuenta» |
| Créditos | «Nuevo crédito» |
| Personas y comercios | «Nueva» |
| Grupos (antes Espacios y Personas) | «Nuevo grupo» (con uno o más; sin ninguno, lo ofrece el vacío) |
| Presupuestos, Plan (segmento Presupuestos) | «Nuevo» |
| Documentos | «Subir archivo» |
| Movi AI | «Nueva conversación» con conversación; sin ella, la `Pildora` «Beta» |

Movimientos (la lupa), Por revisar y Captura del banco (actualizar) e Inicio (lupa y campana) solo
llevan íconos. Lo vigila `LaAccionDelEncabezadoEsUnaSolaTest`.

**El título manda** (vuelta de diseño 8: «Personas y comerc…»). Si el título entero y la acción con
texto no caben, la acción queda en su ícono (`AccionDelEncabezadoSoloIcono`: la marca, 48 de toque, el
texto como descripción y el mismo tag); si ni así cabe, el título baja a dos renglones. Nunca «…». El
`Encabezado` decide midiendo el título y calculando el ancho del botón sin componerlo
(`anchoDeLaAccionConTexto`, `laAccionVaConTexto`). Lo vigila `LaAccionDelEncabezadoCedeAlTituloTest`
en las siete pantallas de la tabla, a 360 dp con la letra a ×1,3.

**La prioridad es título > subtítulo > texto de la acción** (vuelta de diseño 12: «Conoce tus fi…» en
Movi AI). La acción conserva su texto solo si el título **y** el subtítulo caben enteros a su lado
(`laAccionVaConTexto` recibe los dos anchos); si no, queda en su ícono. Si ni así cabe el subtítulo,
baja a dos renglones (`RENGLONES_DEL_SUBTITULO_DEL_ENCABEZADO`), como el título. Nunca «…». Se eligió
ceder antes que partir el subtítulo porque el ícono ya existe y conserva el nombre para el lector de
pantalla; el segundo renglón queda para lo que ni así entra. Lo vigila el mismo
`LaAccionDelEncabezadoCedeAlTituloTest` (Movi AI con «Nueva conversación», y la regla en el
componente).
```kotlin
Encabezado("Movimientos", acciones = { BotonDeIcono(Icons.Rounded.Search, "Buscar", ::buscar) })
Encabezado("Patrimonio", accion = AccionDeEncabezado("Nueva cuenta", Icons.Rounded.Add, ::crear))
```

**`Vacio`** (es `VacioQueEnsena`), **`EsqueletoDeFila`** (mide lo mismo que la `Fila`), **`Separador`**
(`hilo`, con sangría `Ninguna`, `Margen` o `ConInicio`) y **`BarraDeProgreso`** (pista en capa 3, tono
del relleno; con `marca` opcional, una raya vertical en esa fracción: «la mitad» de «Quién puso qué»).

**`Casilla`** (vuelta de diseño 14, `ds/Casilla.kt`) — marcar o desmarcar algo de una lista. La caja
mide `Icono.l` con `Radio.s`: **marcada**, rellena de la marca con el visto en `sobreMarca`; **sin
marcar**, vacía con el borde (`Medida.trazo`) en `textoMedio`. Se distingue de lejos (la de antes,
`CasillaDeSeleccion`, era un cuadradito lavanda de 20 dp que casi no cambiaba; se borró, y Primeros
pasos, «Recordarme unos días antes» y la hoja del recurrente usan esta). **El toque es de 48
siempre**: con `onCambiar` la casilla se toca sola y mide `Medida.objetivoTactil` por lado; sin él va
dentro de algo que se marca entero —una `Fila` envuelta en `toggleable` con `Role.Checkbox`— y ese algo
es el toque (de borde a borde, 56 o más de alto); la casilla solo dice el estado y no ocupa el ancho
de un toque que no es suyo, como el `Checkbox` de Material sin `onCheckedChange`.
```kotlin
Casilla(marcada = aceptado, onCambiar = { aceptado = it })
Box(Modifier.toggleable(marcada, role = Role.Checkbox, onValueChange = { alternar() })) {
    Fila(titulo = "Mercado", subtitulo = "Gastaste $1.020.000",
        inicio = { IconoDeCategoria("Mercado") },
        final = { Row { Cifra(1_050_000); Casilla(marcada) } })
}
```

**`AvatarDePersona`** (R3) — una persona: su inicial en un círculo de `Medida.avatar`, en el fondo de
su tono (`TonoDeIcono`). Los miembros de un espacio y quien pagó un gasto. Neutro por defecto; «Tú» en
`TonoDeIcono.Tinta` (la tinta llena con la letra blanca); **nunca** en `Marca` (vuelta 42).
```kotlin
AvatarDePersona("Caro", tono = TonoDeIcono.Info)
AvatarDePersona("Juan", tono = TonoDeIcono.Tinta)
```

**`FilaDeNavegacion`** (vuelta 42, `ds/Fila.kt`) — **la fila que lleva a otra pantalla**: una `Fila` sola
en su `Superficie` de lista, con el ícono en el **círculo neutro** (`capa3` con el ícono en la tinta), el
título, una línea de apoyo y la `Chevron`. Con `aviso = true` (algo que pide atención) el ícono y el
subtítulo van en el tinte con el ocre: el amarillo suave es solo para eso. Es el mismo círculo que llevan
las filas de Tú y de Ajustes (`IconoEnCirculo` es neutro por defecto).
```kotlin
FilaDeNavegacion("Tus períodos", Icons.Rounded.History, onClick = ::abrir, subtitulo = "Cómo te fue en cada ciclo")
```

**`Campo`**, **`CampoDeMonto`**, **`GrupoDeCampos`**, **`RotuloDeCampo`**, **`AyudaDeCampo`** y
**`PieDeHoja`** (vuelta de diseño 6, `ds/Campo.kt`) — los formularios. Ver §15.

**`CampoDeBusqueda`** (vuelta 35, `ds/CampoDeBusqueda.kt`) — lo que se escribe para filtrar una lista:
la lupa, el texto (la guía en `textoApagado`, de un renglón) y la × solo con texto, que borra y deja el
foco. Una superficie sin borde, `Radio.m`, de 48 de alto con la × o sin ella. `Capa1` sobre el fondo de
una pantalla; `Capa2` dentro de una hoja. No es un `Campo`: no tiene rótulo, ayuda ni error. Ver §37.
```kotlin
CampoDeBusqueda(consulta, { consulta = it }, guia = "Gasto, comercio o persona", alBuscar = ::anotar)
CampoDeBusqueda(busqueda, { busqueda = it }, guia = "Buscar o crear categoría", nivel = NivelDeSuperficie.Capa2)
```

## 7. La regla de secciones

1. **Cada pantalla es dueña de una pregunta.** Inicio (antes «Hoy»): «¿cuánto me queda y qué hago
   hoy?» —el héroe, las acciones, lo que Movi propone, las cuentas y los widgets que el dueño elige—. Movimientos: «¿en qué se fue?».
   Plan: «¿qué viene y cuánto puedo gastar?». Patrimonio / Espacios: «¿qué tengo y qué debo?». Una
   sección que contesta otra pregunta va en la pantalla de esa pregunta, con un enlace si hace falta.
2. **Una cifra no se repite entre pantallas.** Cada número tiene una casa; en las demás se nombra con
   un enlace («Tu plata →»), no se vuelve a pintar con otra suma al lado.
3. **Cada sección tiene un `Seccion`** arriba, y entre secciones va `Medida.entreSecciones`.
4. **Las propuestas van como `TarjetaDePropuesta`**, juntas, arriba de lo informativo.

## 8. Las guardas

| prueba | qué vigila |
|---|---|
| `LosValoresSueltosSoloBajanTest` (`core/src/jvmTest/.../quality/`) | los `.dp` sueltos y los `RoundedCornerShape` con número en `ui/` no suben de la línea base (`docs/diseno/lineas-base.txt`); los `Color(0x…)` fuera de `theme/` están en cero |
| `ElSistemaNoTieneValoresSueltosTest` | cero `.dp`/`.sp`/`Color(0x)`/radio/`fontSize`/`alpha` a mano dentro de `ds/` |
| `ContrasteDeLosTokensTest` | el contraste de cada par: texto, enlace y estados sobre cada superficie; tinta sobre el amarillo; blanco sobre la tinta; los bordes; y que la marca no sea letra |
| `SoloHayUnTemaTest` (Robolectric, vuelta 29) | con el teléfono en modo oscuro, el tema y el esquema de Material son los claros |
| `ElWidgetHablaLosTokensTest` / `ElIngresoHablaLosTokensTest` / `PaginaCompartidaTest` | el widget, el ingreso web y la página compartida copian los colores del único tema |
| `LosTamanosSueltosSoloBajanTest` | los `fontSize` sueltos |
| `LosCamposSonDelSistemaTest` | ningún `BasicTextField` / `TextField` / `OutlinedTextField` en `ui/` fuera de `ui/components/` y de cuatro excepciones con nombre (§15; Buscar salió en la vuelta 35, §37) |
| `LosRotulosNoGritanTest` | ninguna función `…SectionLabel` en `ui/`, y ningún `Seccion` / `GrupoDeCampos` / `RotuloDeCampo` / `rotulo =` / `label =` con un texto entero en mayúsculas |
| `LasGuiasNoParecenDatosTest` | ningún `guia =` / `placeholder =` con un literal puramente numérico en `ui/` («20», «29,6», «$ 0»): va `ejemplo =`, y sin su propio «Ej:» (§15) |
| `LosCamposDelSistemaTest` (Robolectric) | el rótulo en frase, la guía de un monto en la letra de cuerpo y el valor en la de cifras, el error en `sale`, «¿Qué es esto?» abre y cierra, la caja llega a 48, y nada se recorta a 360 dp con la letra a ×1,3 (también en las hojas de crédito y tarjeta) |
| `SistemaDeDisenoTest` (Robolectric) | cada componente se compone en el tema (uno solo desde la vuelta 29); los colores de botón, filtro, píldora e ícono son los de la opción 3; la sombra va en la propuesta y la destacada, no en una lista; el filtro mide 44; la fila mide 56/58; el botón llega a 48 de toque; la cifra se oculta; la tarjeta de cuenta mide 128 con marca y sin ella; dos widgets de un par miden lo mismo; la casilla que se toca sola mide 48 y dice su estado |
| `CifraConRotuloTest` (Robolectric, motor de texto real) | el rótulo como se escribe, la cifra del sistema con su signo y tono, nada en claro con los saldos ocultos, «—» sin dato, y ninguna cifra recortada ni partida a 360 dp con la letra ×1,12 (tres compactas, dos enteras de diez dígitos, una en renglón) |
| `LasCifrasDelSistemaSeOcultanTest` (Robolectric) | con «Ocultar los saldos», ni un «$» seguido de un dígito en el flujo libre de Plan, Presupuestos, la hoja de anular, los candidatos a pago de tarjeta y, en Hoy, «Tu patrimonio», «Para revisar» (con la evidencia abierta) y «Tu día más justo» |
| `LoQueNoEsDelSistemaSoloBajaTest` (`core/src/jvmTest/.../quality/`, vuelta de diseño 18) | en `ui/` fuera de `ds/`, seis cosas que solo pueden bajar de su línea base (`lineas-base.txt`): `Button(`/`OutlinedButton(`/`TextButton(` de Material, `Color.White`/`Color.Black` (salvo el velo de `MarcoDeHoja`), `Hairline(`, `MinCard(`, un `Text(` cuyo primer argumento es `formatCOP(`/`formatMoney(` (aproximación por regex) y `fontWeight =` (§24). **Desde la vuelta 21 las cinco primeras se exigen en 0**, aunque alguien suba su línea base (§27) |
| `ElAmarilloEsDeLaAccionTest` (`core/src/jvmTest/.../quality/`, vuelta 42) | ninguna píldora amarilla (`TonoDePildora.Marca` no existe), ninguna persona en `TonoDeIcono.Marca`, `IconoEnCirculo` y `AvatarDePersona` neutros por defecto, `TonoDeIcono.Marca` fuera de `ds/` solo en las dos propuestas de la maqueta (la tarjeta de avisos de Actividad y «Movi propone hoy»), y las filas que llevan a otra pantalla son `FilaDeNavegacion` |
| `UnaSolaCifraHeroePorPantallaTest` (Robolectric) | a lo sumo una `Cifra` `Heroe` en Hoy, Plan (Pagos y Presupuestos), Patrimonio, Créditos, Movimientos y Espacios; exactamente una donde la pantalla tiene su protagonista (Hoy, Patrimonio, Créditos) |

## 9. Migrar una pantalla

1. Mirarla antes (web a 390 dp; desde la vuelta 29 hay un solo tema).
2. Cambiar tarjetas por `Superficie`, filas por `Fila`, títulos por `Seccion`, botones por `Boton`,
   montos por `Cifra`; los números que queden, por `Espacio` / `Medida` / `Radio` / `Icono`.
3. `python3 scripts/auditoria-de-diseno.py --resumen` y bajar `docs/diseno/lineas-base.txt` al número
   nuevo (la prueba lo exige).
4. Mirarla después. Casi cada pantalla migrada tuvo un defecto visible.

## 10. El Inicio (vuelta 39: la opción 3)

El Inicio se arma desde la definición del server (`DashboardDefaults`, **generación 10**) con las
preferencias del dueño («Editar inicio»). Cada bloque contesta una sola pregunta, con los componentes de
arriba. De arriba abajo:

| bloque | tipo SDUI | componente | lleva a |
|---|---|---|---|
| banda | — (chrome) | amarilla (`marca`), `Medida.altoDeLaBandaDelInicio` 168 con `Radio.xxl` 28 abajo; la pinta el scroll (`SduiRenderer(conBanda = true)`) y detrás de la barra del sistema la cáscara | — |
| encabezado | — (chrome) | `EncabezadoDelInicio`: avatar blanco con iniciales, «Hola, Juan» (`saludo`) con el período (`apoyoMedio`, `sobreMarcaMedio`), ocultar saldos y campana en círculos `marcaSuave` de 44 (`Medida.circuloDeLaBanda`); la campana con punto solo si hay algo vencido o urgente | Tú, Notificaciones |
| héroe | `HERO_BALANCE` | `Superficie(destacada = true)`: «Te quedan» con el enlace «Tu plata $X», `Cifra.Heroe`, «Para N días · $X por día»; abajo, con el hilo y en `capa2`, la franja «Por confirmar» con `Pildora` «N por revisar» (solo si hay algo) | «¿De dónde sale?»; Patrimonio; Por revisar |
| acciones | `ACCIONES` | `CuadriculaDeAcciones` 4 × 2, rótulos `RenglonesDelRotulo.Dos` en `rotuloDeAccion` | los atajos del «+» (`LocalAtajosDeAgregar`), Por revisar, Movi AI |
| próximo pago | `PROXIMO_PAGO` | `Superficie`: «Próximo pago · vence …» (o «Pago vencido · hace N días»), el nombre y la `Cifra` en texto, `Boton` «Anotar pago» a la derecha; «y N más» | Agregar con el pago; Pagos |
| Movi propone hoy | `PROPUESTAS` | `TarjetaDePropuesta`, una por tipo, tope 4, **sin el pago** cuando hay `PROXIMO_PAGO`; sin nada, no se pinta | su acción; «Ver todo» → Por revisar |
| últimos movimientos | `ULTIMOS_MOVIMIENTOS` | `Superficie` de lista con 3 `MovementSingleRow`/`TransferRow` (la fila de Actividad) | la hoja del movimiento; «Ver toda la actividad» → Actividad |
| tus cuentas | `CUENTAS` | `TarjetaDeCuenta` en carrusel (en grilla fuera del teléfono) | el detalle de la cuenta; «Ver todo» → Patrimonio |
| widgets | `CHECKLIST_DEL_PERIODO`, `GASTO_POR_CATEGORIA` | `ParDeWidgets` | Pagos, En qué se va |

`DISPONIBLE_DEL_PERIODO` y `PATRIMONIO` no vienen por defecto (repetían el héroe y Patrimonio): son
`SECCIONES_OPCIONALES` que el dueño prende en «Editar inicio». ALERTS y el BANNER siguen al final de la
definición solo para los APK viejos; este cliente no los pinta. Ver §43.

## 11. Movimientos y los avisos (ola R4a)

- **Una sola búsqueda.** La lupa de Movimientos abre «Buscar». Su texto guía es corto y cabe a 360
  dp con la letra agrandada («Gasto, comercio o persona», con la lupa al lado); lo que se puede buscar
  entero lo dice el texto de ayuda de abajo. Sus chips van «Todo · Gastos · Ingresos · Este período ·
  Cuenta»: los tres primeros eligen el tipo, con «Todo» elegido por defecto (vuelta de diseño 5).
  Lo que otra pantalla pide filtrar (un
  comercio tocado en Buscar, «En este comercio», una categoría de «En qué se va») llega como un
  `PildoraDeFiltro` elegido con su ×, al lado de los filtros de tipo.
- **El día en un renglón**: la fecha como se escribe («Hoy», «23 de agosto») con el estilo de `Seccion`
  y el flujo del día a la derecha; todo el renglón pliega el día (alto `Medida.objetivoTactil`). La
  comparación «Día a día» salió: es de Plan. **La fecha nunca se recorta** (vuelta de diseño 2): la
  fecha, la cifra y el chevron se miden primero; si no cabe todo, se corta el «· N movimientos».
  **Sin el rótulo «Flujo del día»** (vuelta de diseño 14): la cifra del día sola —`Cifra` `Chica`, con
  su signo, en `entra` si entró plata— se entiende en todos los anchos; el rótulo sobraba en cada día.
  **Su resaltado tiene aire y radio** (vuelta de diseño 16, `Modifier.renglonTocable` en
  `ds/Resaltado.kt`): con el mouse encima pintaba una banda gris recta pegada a la fecha; ahora sale
  `SALIDA_DEL_RESALTADO` (`Espacio.xs`) hacia cada lado, dentro del margen, con `Radio.m`, y la fecha
  sigue alineada con el borde de la superficie de abajo.
- **«N por revisar» es una `TarjetaDePropuesta`**, como en «Movi propone hoy». «Personas y comercios»
  ya no está arriba de la lista. **Es el primer ítem de la lista y se va con el scroll** (vuelta de
  diseño 1): fijos quedan solo el encabezado, el selector del período y los chips.
- **La fila de un movimiento es una `Fila`** (Movimientos, Por revisar, Buscar, el detalle de una
  cuenta) y **su monto es la `Cifra` del sistema** (`CifraDelMovimiento`): el gasto `Neutro` con «−»,
  el ingreso `Ingreso` con «+», el traspaso `EntreCuentas` sin signo y lo que no movió plata
  `Apagado` (`tonoDeCifraDelTono`). Ninguna fila elige su color a mano. El punto de «por confirmar» y
  el ícono de repetición van junto al monto y dicen su nombre al lector de pantalla.
- **Un aviso del banco se ve igual en todas partes** (`TarjetaDeAviso`, en `ui/sms/ElMismoPago.kt`):
  la superficie de la propuesta cuando pide decisión y la capa 1 cuando es historial, el estado en una
  `Pildora` en frase («Pendiente», «Confirmado»), lo que dijo el banco como cita y las acciones en
  `Boton` chicos. La usan Por revisar, Captura del banco, Reconciliar y los débitos automáticos.
- **La cabeza del aviso se lee primero** (vuelta de diseño 2, `cabezaDelAviso` en
  `ui/sms/LaCabezaDelAviso.kt`): el título es el comercio o lo que Movi entendió («Rappi»), con su marca
  (o el ícono de su categoría); a la derecha el monto con `Cifra` (gasto neutro, ingreso `entra`, pago
  entre cuentas `entreCuentas`); debajo «banco · hora». Si Movi no entendió el comercio, el título es el
  banco con su marca. El texto del banco va después, apagado y en dos renglones que se abren al tocarlo.
  **En la bandeja no va la píldora «Pendiente»**: ahí todo lo está; quedan las que dicen algo («Ya
  anotado», «Avisado 3 veces»). El historial de Captura del banco sí la lleva.
- **Un bloque que falta no deja su lugar** (vuelta de diseño 7, `bloqueDeAbajo`): debajo del texto del
  banco va «Se anota: …» o el detalle del server, y si el detalle está en blanco no va nada (un
  `Text("")` era un renglón vacío: ~30 px de hueco antes de los botones). Entre bloques, `Espacio.xs`
  de la tarjeta; la distancia del último texto a los botones es la misma con «Se anota» y sin él.

- **El panel de Movimientos sin nada elegido resume el período** (vuelta de diseño 9,
  `ResumenEnElPanel.kt`): en pantalla ancha, en vez de «Elige un movimiento» y ~800 px en blanco, lo
  que entró, lo que salió y el neto (`Cifra.Grande`), y el gasto por categoría como `Fila` con la
  diferencia contra el período pasado del widget «En qué se va» (`diferenciaDeLaCategoria`) y el
  enlace «Ver en qué se va». Tocar una categoría filtra la lista (`FiltroDeMovimientos`, con su chip).
  Ninguna cuenta nueva: `analisisDelPeriodo`/`filasDelAnalisis` y la regla del chip «Ingresos» sobre
  la historia que la lista ya leyó. Al elegir un movimiento, su detalle como siempre.

## 12. Plan, Presupuestos, Tus períodos y Créditos (ola R4b)

Plan es dueño de «¿cuánto puedo gastar y qué me falta pagar?», en este orden:

| sección | componente | nota |
|---|---|---|
| Cuánto puedes gastar | `Seccion` + `Superficie`, barras con `BarraDeProgreso` | la versión completa del disponible vive acá |
| Tus períodos | `Fila` en una `Superficie` de lista | lleva a la lista de períodos |
| selector | «Pagos» · «Presupuestos» | era «Pagos del mes»: el período del dueño no es el mes |
| Pagos del período | `Fila` + `Pildora` de estado (Pagado, Vencido, Por confirmar, Hoy, En N días, Falta) | «¿Ya pagaste…?» va en la superficie de las propuestas con `Boton` S; el resumen dice «Falta por pagar $X · Ya pagaste $Y» y los grupos («Por pagar · 3», «Ya pagaste · 2») van sin cifra |
| Tu plata, día a día | `Seccion` + `Superficie` + `Grafica` | debajo de los pagos que la mueven |
| Lo que se repite al mes, suscripciones | `Cifra.Grande`, `Fila` | |
| Tus recordatorios no te van a llegar | `Fila` en tono de aviso, dentro de «Pagos del período», debajo del resumen | vuelta 28: era una `TarjetaDePropuesta`; «Ahora no» la descarta por el período |

**Lo que cuelga de una fila** (`ui/recurrentes/BajoLaFila.kt`): una fila de Plan o de un período
ofrece acciones («Anotar este pago») o trae una línea más (la evidencia, la barra de un presupuesto,
el avance de una deuda). Va debajo de la `Fila`, con `BajoLaFila`: alineado con el
texto de la fila (`SANGRIA_DEL_TEXTO_DE_LA_FILA` = margen + avatar + 12), nunca con una sangría
propia. `MontoDeFila` es la letra de `Cifra.Fila` para un monto ya escrito (en dólares, «al año»), y
`NumeroEnCirculo`, el inicio de una fila que se nombra por un número (el día de un cobro, el orden de
una deuda).

**Los meses vacíos no son tarjetas** (vuelta de diseño 5, generalizada en la 7: `piezasDeLosPeriodos`):
los períodos sin movimientos de flujo que quedan antes del primero con movimientos se juntan al final de
Tus períodos en una sola `Fila` apagada y sin toque («Antes de agosto 2026 no hay movimientos»), y
**toda racha de dos o más vacíos seguidos en el medio** va en una `Fila` igual, en su lugar («Octubre
2025 a julio 2026 · sin movimientos»; el año una vez si es el mismo: «Mayo a julio 2026»). Un vacío
**solo** entre dos con movimientos sigue siendo tarjeta («Sin movimientos de flujo»): ahí sí dice
algo. El en curso es siempre tarjeta. Ningún período desaparece ni cambia sus cifras; solo cómo se
pintan.

**El período en curso no repite Plan**: su detalle muestra cómo va (entró, salió, en qué se fue) y,
en vez de sus pagos fijos y presupuestos, dos filas que llevan a Plan. Un período cerrado sí los
pinta: mirar atrás es lo que contesta esa pantalla.

Lo vigila `LasPantallasDePlanUsanElSistemaTest` (a 360 dp: secciones sin mayúsculas, filas de 56 o
más, ninguna cifra recortada).

## 13. Ola R4c: Patrimonio, Ajustes y lo que cuelga de ellos

Patrimonio, Detalle de cuenta, Cuadre de saldos, Personas y comercios, Ajustes, Perfil, Categorías,
Documentos, Captura del banco, Compartir, Movi AI, Agregar, la entrada, Editar inicio y Primeros pasos
pasaron al sistema. Lo que se decidió y vale para las que vengan:

| pantalla | qué pregunta contesta | qué se fue o se movió |
|---|---|---|
| Patrimonio | ¿cuánto tengo y cuánto debo? | los subtotales de cada grupo («DINERO · 5 · $20,9M»): repetían la resta de la tarjeta de arriba; «Personas y comercios» (no es tuyo) |
| Personas y comercios | ¿a quién le mandé plata? | de cuatro puertas quedan dos: la fila al final de **Espacios** y la ficha de **Ajustes** |
| Ajustes | ¿dónde se ajusta X? | **vuelta de diseño 3: una sola pantalla.** Arriba la tarjeta del perfil; debajo `Seccion` + `Fila` en orden —Tu plata, Movi, Cómo se ve, Avisos, Tus datos, (Entrar), Cuenta— y al final, aparte, «Borrar mi cuenta» como `Fila` (sin relleno rojo; desde la vuelta 5, con el título en `TonoDeFila.Alerta`). «Perfil» dejó de ser pantalla; la cuadrícula de fichas se fue |
| Metas, Extractos | — | **borradas**; los destinos SDUI `goals` y `extractos` llevan a Patrimonio y a Documentos |

- **Grupos de cuentas**: `Seccion(titulo, conteo)` + `Superficie(relleno = Lista)` con una `Fila` por
  cuenta; el inicio es la marca del banco (`AvatarDeLaCuenta`), el final la `Cifra` en la moneda de la
  cuenta. Un grupo vacío no se pinta.
- **Patrimonio en dos columnas** (vuelta de diseño 9): a la izquierda la cifra con su curva y debajo
  Herramientas; a la derecha Dinero, Inversión, Bienes y Deudas, en el orden de una columna. Deudas
  vivía en la izquierda y la derecha quedaba con «Dinero · 2»: dos columnas cojas.
- **Una lista perezosa sobre una superficie** (Categorías, cientos de filas): cada fila es un tramo de
  la superficie —la primera redondea arriba, la última abajo, `Separador` entre ellas—, para no
  componer la lista entera dentro de una sola `Superficie`.
- **Lo que una fila no alcanza a decir** (el alcance de la huella, para qué es el correo del cierre)
  va debajo de la superficie como nota, en `apoyo`, alineada con el texto de la fila. La fila no crece
  a tres renglones.
- **Los rótulos en frase**: «Patrimonio neto», «Saldo actual», «Lo que dice el banco», nunca en
  mayúsculas ni con espaciado de letra. Un rótulo de `:core` que llega en mayúsculas se pasa a frase al
  pintarlo (`enFrase`).
- **Una propuesta de Movi en otra pantalla** (el saldo que dijo el aviso, en Cuadre) es una
  `TarjetaDePropuesta`, igual que en el Inicio.
- **Botones de una tarjeta**: `Boton` tamaño `S` (Personas y comercios, Lo que Movi sabe, Movi AI); los
  de pie de hoja, `Boton` `M` a todo el ancho; «Borrar», «Revocar», «Cerrar sesión» en `Peligro`.

## 14. Vuelta de diseño 4: Créditos es una lista, y los nombres no gritan

- **Cada deuda es una `Fila`, no un formulario** (`FilaDeDeuda` en `ui/credits/CreditosScreen.kt`):
  ícono, nombre, el próximo pago de subtítulo («Cuota $1.250.000 · 5 de noviembre», «Pago mínimo
  $412.300 · 14 de noviembre», hasta dos renglones) y el saldo con `Cifra`. Las de un grupo van en
  una `Superficie` de lista, como las cuentas de Patrimonio. Debajo, con `BajoLaFila`, solo lo
  verdadero: la `BarraDeProgreso` con «50 % pagado» cuando hay términos que la sostengan y algo
  pagado, o la nota que la barra no dice (la deuda que crece, en `sale`; el desembolso que falta, en
  `aviso`). **Nunca un «0 % pagado» suelto.** Sin términos: «Faltan datos: tócala para completarlos»,
  en aviso y sin cortarse, y tocarla abre la hoja de completarlos; en cero y con movimientos, la
  `Pildora` «Pagado» en vez del saldo. La regla es pura: `prestamoEnLaLista` / `tarjetaEnLaLista`.
- **Ningún botón dentro de una lista.** Tocar la deuda abre el detalle de su cuenta, que trae la
  sección de la deuda (`ui/credits/LaDeudaEnSuDetalle.kt`): «El préstamo» / «La tarjeta» (la cuota
  con quién la paga, la tasa, el plazo, el interés, cómo va, el avance; el pago, el corte y el cupo)
  y «Qué puedes hacer», una `Fila` por acción: «¿Y si abonas de más?», «Registrar pago de …»,
  «Registrar descuento», «Ajustar saldo», «Editar las condiciones». Lo vigilan
  `LaListaDeCreditosEsDeFilasTest` (a 360 dp) y `LaDeudaEnSuDetalleTest` (ninguna acción se perdió).
- **El comercio de un aviso se muestra con su nombre** (`nombreParaMostrar` en `:core`
  `NombreParaMostrar.kt`): «RAPPI» → «Rappi», «EXITO SUBA» → «Éxito Suba», «PAYU*NETFLIX» →
  «Netflix», «UBER *TRIP» → «Uber», «TIENDA D1 CALLE 80» → «D1 Calle 80»; sin marca conocida, en
  «Tipo Título» con las siglas sin vocales («CRV») y los números en mayúscula. Lo que ya viene con
  mayúsculas y minúsculas se deja. **Solo para mostrar**: la cabeza de la tarjeta de aviso,
  Reconciliar, el lote de Por revisar y la notificación; lo que se guarda, las huellas y la memoria
  de categorías siguen con el texto crudo.

## 15. Vuelta de diseño 6: los formularios entran al sistema

Antes, cada una de las veinte hojas que piden datos armaba su caja con un `BasicTextField`: rótulos
en mayúsculas (`SectionLabel("TÉRMINOS")`, `SheetSectionLabel("NOMBRE")`), el nombre del campo metido
en el texto guía («$ Capital original (COP)», en la letra de las cifras y cortado), párrafos de tres
o cuatro renglones debajo de cada campo y el botón apagado con la razón a veces sí, a veces no. Ahora
un formulario se arma con seis piezas de `ds/Campo.kt`:

| pieza | cuándo |
|---|---|
| `Campo` | todo texto que se escribe: nombre, banco, día, notas, contraseña (`secreto`), tasa (`sufijo = "%"`), texto largo (`renglones`) |
| `CampoDeMonto` | toda cifra de plata. El valor en la letra de las cifras (`monto`), la guía en la de cuerpo y la moneda (`$`, `US$`) pintada aparte, nunca dentro de la guía. Reusa `nextMoneyField` / `parseMoneyDigits`; `MoneyField` delega acá |
| `GrupoDeCampos` | el título de un bloque de un formulario, en frase («Términos», «El crédito»): es un `Seccion` con una descripción opcional y los campos separados por `Medida.dentroDeSeccion`. Entre grupos, `Medida.entreSecciones` |
| `RotuloDeCampo` | el rótulo de algo que pide un dato y no es una caja (las píldoras de la moneda, un selector de cuenta, el tipo) |
| `AyudaDeCampo` | la ayuda de una línea + «¿Qué es esto?» de algo que no es una caja (el selector de persona o comercio) |
| `PieDeHoja` | **el pie de toda hoja**: el error del guardado en `sale` justo encima, el `Boton` `M` a todo el ancho, y si está apagado, **la razón en una línea centrada justo debajo** («Falta el banco»). Con `secundaria` («Cancelar», «Quitar») van dos botones del mismo ancho, la secundaria a la izquierda. Anular y quitar van en `Peligro` |

**La anatomía de un campo**, de arriba a abajo:

1. **El rótulo**, arriba y siempre, en frase, en `apoyo` medio y `textoMedio`, un renglón, con
   `Espacio.xs` hasta la caja. Es el nombre del campo y no se borra al escribir. Si llega en
   mayúsculas, se pinta en frase.
2. **La caja**: capa 2, `Radio.s`, relleno `Espacio.s`, alto mínimo `Medida.objetivoTactil` (48). El
   borde (`Medida.trazo`) va en `borde`, en la marca con foco y en `sale` con error.
3. **El texto guía**, en `cuerpo` y `textoApagado`: un ejemplo («Ej: Bancolombia Ahorros»), no el nombre
   del campo ni la moneda. **Un ejemplo numérico va siempre con «Ej:»** (vuelta de diseño 17): un «20»
   gris solo en la caja del corte parece un corte ya cargado. Se pasa con `ejemplo = "20"` y el campo
   pinta «Ej: 20» (`Campo`, `CampoDeMonto`, `CampoDeTasa`); una `guia` puramente numérica que llegue
   suelta también sale con «Ej:» (`textoGuiaDelCampo`). **Un monto vacío muestra la moneda y nada
   más** —antes un «0» en la letra de las cifras—, o `ejemplo = "1.250.000"` si el campo lo necesita.
4. **La ayuda, de una línea**, en `apoyo`. Lo que no cabe va en `masAyuda`, que abre «¿Qué es esto?» a
   la derecha de la ayuda: **se mueve, no se borra**. «Opcional» va en el rótulo («Notas (opcional)»).
5. **El error**, en `sale`, en el lugar de la ayuda; el campo lo anuncia al lector de pantalla.

```kotlin
GrupoDeCampos("Términos") {
    Campo(banco, { banco = it }, rotulo = "Banco", guia = "Ej: Bancolombia")
    CampoDeMonto(capital, { capital = it }, rotulo = "Capital original")
    CampoDeMonto(
        seguro, { seguro = it },
        rotulo = "Seguro mensual (opcional)",
        ayuda = "El seguro de vida deudor que va dentro de la cuota.",
        masAyuda = "Esa parte no baja la deuda, así que sin este dato Movi te mostraría menos deuda.",
    )
}
PieDeHoja("Guardar crédito", ::guardar, habilitado = puedeGuardar, razon = loQueFalta, error = error)
```

**Pasaron al sistema:** las hojas de crédito y tarjeta (y las de ajustar saldo y simular un abono,
que usaban el mismo `SectionLabel`), crear cuenta, apartado, bien, para qué es esta plata, anular, el
recurrente, el monto y el concepto de un movimiento, renombrar y buscar categorías, los espacios
(«Pagó otro», «Marcar como saldado»), editar y subir un documento, lo que Movi sabe de ti, la
entrada, el registro, cambiar la contraseña, borrar la cuenta y la ficha de una persona o comercio.
`FieldBox` (créditos) y `MoneyField` siguen con su API y delegan en `Campo` / `CampoDeMonto`, así que
Agregar (traspaso), Reconciliar, Cuadre, Perfil y Primeros pasos también pintan el campo nuevo.

**Se quedan con su campo propio** (lo vigila `LosCamposSonDelSistemaTest`, que también falla si una
excepción ya no tiene campo): la caja de Buscar, el compositor de Movi AI, el Editor de pantallas
(admin), la guía de primer uso y la fila «Comercio» de Reconciliar, que se edita en su lugar dentro de
una `Fila`. Agregar salió de la lista en la vuelta de diseño 13 (§19): su nota es un `Campo`, y lo que le
queda propio —el teclado numérico y el monto grande— no es un campo de texto.

## 16. Vuelta de diseño 8: títulos enteros, cifras del sistema y avisos de un vistazo

- **Un renglón de partes se une sin separadores colgando** (`enUnaLinea` en `ui/destinos/`): las partes
  vacías o nulas no dejan « · » adelante, atrás ni doble. El número de un tercero va nombrado,
  «Cuenta ·0756 · esposa», y no «·0756» a secas, que se leía como un separador suelto.
- **Categorías**: los montos del mes son `Cifra` (el gasto neutro; lo que entró, `Ingreso` con «+», uno
  debajo del otro si hubo los dos) y «Movi encontró N cosas para ordenar» es una `TarjetaDePropuesta`
  con «Revisar». La búsqueda ya era el `Campo` del sistema (vuelta 6). `TarjetaDePropuesta` avisa
  cuántos renglones ocupó el título (`alMedirElTitulo`) para que el esqueleto reserve el mismo alto.
- **La campana** (`NotificationsPanel`): cada aviso es una `Fila` en una superficie de lista, con un
  `IconoEnCirculo` según su tipo (`TipoDeNotificacion` → `iconoDelAviso`: vencido, banco que se calló y
  presupuesto pasado en peligro; por vencer y por confirmar en aviso), el título en frase («Internet
  venció ayer»), el monto con `Cifra` cuando lo hay y lo urgente primero. Los avisos son los mismos de
  `dashboardAlerts`; sin ninguno, un `Vacio`.
- **Mensajes del banco / Captura del banco**: la primera tarjeta es una `Fila` («Último mensaje · hoy,
  11:02 a. m.») y una línea de `AyudaDeCampo`; lo largo (lo que llegó, por qué este aparato no lee
  mensajes) se abre con «¿Qué es esto?» (`elUltimoMensaje`). «Avisarme si un banco se calla», igual
  (`ayudaDelBancoQueSeCalla`). Se pliega, no se pierde.

## 17. Vuelta de diseño 11: el primer día de alguien nuevo

Revisado con una cuenta nueva (sin cuentas, sin movimientos, corte el 25) que saltó la guía de primer
uso. La regla que sale de acá: **ningún cero se muestra como si fuera un dato.** Una sección que sin
datos solo puede decir «$0» o «nada» no se pinta, o es un `Vacio` que enseña qué va a aparecer ahí y
cómo llenarlo (como «Aquí van tus pagos fijos» en Plan).

- **El subtítulo del Inicio es corto siempre** (`periodoDelEncabezado`): el nombre del período,
  «Noviembre 2026» (`tituloDelPeriodo`, el mismo de «Tus períodos»), también con un corte a mitad de
  mes. El rango entero («Del 23 de octubre al 24 de noviembre») se cortaba a 360 dp; sigue en la línea
  del período de Plan y de Movimientos, donde tiene lugar.
- **Un solo llamado a crear la primera cuenta** (`elHeroePideLaCuenta`): sin cuentas, el héroe es el
  vacío «Aquí vas a ver tu plata» con «Crear mi primera cuenta» y va primero; «Primeros pasos» va
  debajo de él (`SduiRenderer`, `despuesDelHeroe`) y sin el paso de la cuenta, contando solo lo que
  lista («0 de 3»). Desde Ajustes («Primeros pasos» como pantalla) la tarjeta sigue con sus cuatro.
- **El pie de «Primeros pasos» dice lo que hace**: «Sube un extracto y Movi anota los movimientos»
  elige el archivo y lo deja en la misma puerta de papeles que «Comprobante» de la cuadrícula (era
  «Deja que la app se llene sola · Documentos»); en Android, «Activa los SMS del banco y Movi anota
  cada compra» lleva a Captura del banco. Son `Fila` con el ícono en la marca y sin tilde: son
  hábitos, no pasos.
- **«Movi propone hoy» sin nada no se pinta** (`visibleSections`): mientras las lecturas no alcanzan
  para afirmarlo, su esqueleto; con todo leído y nada que decidir, la sección sale entera (era un
  título y «Nada por decidir hoy»). Por revisar sigue en «Avisos» de la cuadrícula.
- **Sin cuentas no hay caja** (`cajaDelPeriodoDe`): «Tu plata, día a día» de Plan decía «Hoy es tu
  día más justo: tienes $0» sobre una línea plana en cero. Con las cuentas leídas y vacías no se
  dibuja, ni su esqueleto; tampoco «Tu día más justo» de Hoy, que sale de la misma función.
- **Patrimonio y Espacios ya cumplían**: sin cuentas, Patrimonio es su `Vacio` («Aquí vive lo que
  tienes y lo que debes»), Deudas dice «Sin deudas registradas» sin cifra y Herramientas sigue (la
  Ola D lo decidió así: cada fila enseña o navega); Espacios es su `Vacio` («Comparte gastos con
  alguien») y la fila de Personas y comercios invita a guardar. Ningún «$0».

Lo vigila `ElPrimerDiaTest` (Robolectric, 360 dp con la letra a ×1,3): un solo llamado en Hoy, la
guía debajo del héroe, ningún «$0» como cifra en Hoy ni en Plan, el subtítulo entero (y el rango de
antes cortado, para que la prueba mida lo que dice) y «Movi propone hoy» que no aparece vacío.

## 18. Vuelta de diseño 12: Movi AI conversa con formato

- **Las respuestas con formato** (`ui/ai/TextoConFormato.kt` + `TextoDelAsistente.kt`). Un subconjunto
  chico y seguro de Markdown: párrafos (un renglón vacío los separa; un salto simple se respeta),
  listas con viñetas («- », «* », «• ») y numeradas («1. », con su número), dos niveles con sangría,
  **negrita** y un «# título» que se pinta como párrafo en negrita. **Nada de HTML, enlaces ni
  imágenes**: se ven tal cual, como texto, y lo que no se entiende (un «**» sin cerrar, una tabla)
  también; el peor caso es el texto plano de antes. Las dos fuentes escriben así: el modelo y las tres
  respuestas sin IA del server (`LasPreguntasSugeridas.kt`, que ya usan «- » y «1. », pegadas a la
  frase que las presenta). Ningún cambio en el server.
- **Cómo se pinta**: `cuerpo` en el color del texto; entre bloques `Espacio.s`, entre ítems
  `Espacio.xxs`; la viñeta («•») o el número en `textoMedio`, con sangría `Espacio.xs` (y `Espacio.m`
  más por nivel) y el texto de un ítem largo alineado debajo de sí mismo. La negrita en `SemiBold`.
  **Las cifras en pesos** («$1.800.000», «US$1.200», «−$23.388», «$11,7M»: el signo pesos delante y los
  miles con punto) van en la letra de las cifras (`monto`), como en el resto de la app. Un año o un
  día no se confunden con plata.
- **La burbuja del dueño** es el lavado de la marca con el texto del sistema encima
  (`fondoDeLaBurbujaDelDueno` / `textoDeLaBurbujaDelDueno`). Era el color del texto (negra en el tema
  claro). `ContrasteDeLosTokensTest` mide el par en los dos temas. Su ancho máximo sale de un respiro
  de `Espacio.xxxl` a la izquierda (eran 280 dp sueltos) y la respuesta del asistente ocupa el ancho
  que queda (eran 290 dp sueltos).
- **Seguir la conversación** (`preguntasParaSeguir`, `PreguntasParaSeguir`): debajo de la última
  respuesta, como `PildoraDeAccion`, las preguntas sugeridas que **no** se hicieron en esta
  conversación (por id o por texto), de la misma fuente que el chat vacío (`preguntasParaTocar` y las
  tres de respaldo para completar), como mucho tres. Dicen su versión corta («¿Cómo voy?») y mandan la
  completa con su id (`ChatMessage.preguntaSugerida`), así que las de respaldo se siguen contestando sin
  IA. No van mientras Movi escribe, ni cuando el aviso de «IA no disponible» ya pone las tres debajo.
- Lo vigilan `TextoConFormatoTest` y `PreguntasParaSeguirTest` (puras) y `MoviAiConversaConFormatoTest`
  (Robolectric: viñetas y no «- », la burbuja con los tokens en los dos temas, las píldoras y su id).

## 19. Vuelta de diseño 13: Agregar, por dentro

Agregar es la pantalla que más se usa, y sus sub-editores (Categoría, Cuenta, Fecha, Nota) se habían
quedado atrás de la hoja. El teclado numérico, el monto, los chips de categoría rápida, el pie y el
guardado no cambiaron.

- **Un sub-editor se sale volviendo** (`PickerHeader` en `ui/quickadd/`): arriba a la izquierda la
  flecha del `Encabezado` (`BotonDeIcono` con `ROTULO_VOLVER`, 48 de toque) y el título, «‹ Categoría».
  La única «×» es la de la hoja, y la cierra entera (antes había dos cruces apiladas). **Escape y el atrás
  de Android vuelven al formulario**, no cierran la hoja: el sub-editor se anota en la pila de hojas
  (`AtrasCierraEstaHoja`) encima de la hoja, y en la hoja desde abajo Escape llega por `onEscape` (desde la vuelta 17; antes, por `onTecla`). Vale
  también para «Desde», «Hacia» y la fecha de Traspaso y Cuota.
- **La cuadrícula de categorías habla con una sola letra** (`SelectorDeCategoria`, en toda la app): todos
  los rótulos en `apoyo`, hasta dos renglones partidos entre palabras, sin achicarse. Antes cada rótulo que
  no entraba se achicaba solo («Entretenimiento» diminuto a 390 dp). Ahora cede la cuadrícula: baja de
  columnas hasta que la palabra más ancha entra (`columnasParaQueEntrenLasPalabras`, nunca menos de
  `COLUMNAS_MINIMAS_DE_LA_CUADRICULA` = 3). Solo una palabra que ni así entra (una categoría propia
  larguísima) va en un renglón con «…», a la misma letra. Las celdas de un renglón miden lo mismo.
- **La nota es el `Campo` del sistema**, con el foco al abrir, el cursor al final de lo que había, el
  `Boton` principal «Guardar nota» encendido con texto (y vacío solo para quitar una nota que había), y
  Enter —o «Listo» en el teléfono— guarda. **Mide lo que necesita**: ya no se le fija el alto del
  formulario.
- **Mientras la hoja vuelve de un sub-editor, el formulario no toma toques** (`volviendoDeUnSubEditor`,
  `Movimiento.LARGO_MS`). Con la nota más corta la hoja se estira al volver, y bajo el dedo que tocó
  «Guardar nota» queda «Guardar movimiento»; bajo una celda de la cuadrícula o un día del calendario, una
  tecla. El segundo toque de un doble toque no le llega a nadie. El teclado físico y la accesibilidad no
  pasan por ahí.
- **La Cuenta se elige como en Patrimonio**: una `Fila` por cuenta con la marca de su banco
  (`AvatarDeLaCuenta`), el nombre, el saldo con `Cifra` en su moneda y la elegida con la verificación
  (desde la vuelta 15, sobre la marca del banco; y `selected` para el lector de pantalla), sobre una `Superficie` de capa 2. Una deuda dice
  «Debes» (o «A favor») en el subtítulo, con la cifra sin signo.

Lo vigila `AgregarPorDentroTest` (Robolectric, motor de texto real, 360 y 390 dp): la flecha y una sola
«×», el atrás que vuelve con el monto intacto, los rótulos a la misma letra y sin palabras partidas, la
caja de la nota, el botón encendido con texto, Enter y «Listo» que guardan, la nota sin hoja vacía
debajo y el toque que no pasa durante la vuelta. `SelectorDeCuentaTest` mira la `Fila`, la `Cifra`, la
marca y la elegida.

## 20. Vuelta de diseño 14: Presupuestos con jerarquía y una sola señal por estado

Revisado en Plan → Presupuestos a 390 px con la cuenta demo, sin presupuestos (las propuestas) y con
los cuatro propuestos creados.

- **Una sola héroe** (§6): «Gastado en este período» es `Cifra` `Grande` (y su esqueleto, la letra de
  `cifraDeTarjeta`); en Plan iba debajo de «Disponible del período» en una letra más grande que él.
- **Una señal por estado en cada presupuesto** (`FilaDePresupuesto`): una categoría pasada llevaba tres
  rojos —la píldora «273%», la barra y «Sobrepasado · $120.700»—. Ahora el estado lo dicen **la barra**
  (su color: `Exito`, `Aviso` o `Peligro`, el semáforo del dueño) y **el texto de abajo**, que dice lo
  que importa (`loQueDiceElPresupuesto`): «Te pasaste por $120.700» en `sale` (o `aviso` si se pasó
  poco), «Te quedan $16.100» en `aviso` si está cerca y en `textoMedio` si va bien —el verde ya lo dice
  la barra: gastar no es un éxito ni una alarma—, «Llegaste justo al límite» en `aviso`. El porcentaje
  («273 %») va neutro, en `apoyo` y `textoMedio`, al final de la fila, sin píldora. En el resumen se
  quedan las `Pildora` «Sobrepasados · N» y «Sin margen o cerca · N»: son el resumen, una de cada una.
- **Las propuestas son `Fila`** (`FilaDePropuesta`): el ícono de la categoría, el nombre, «Gastaste $X»
  (desde la vuelta 16 sin «el período pasado»: el párrafo de arriba ya dice las fechas; a 360 dp con un
  tope de siete cifras entra en un renglón, y con la letra agrandada puede bajar hasta tres), el tope
  con `Cifra` a la derecha y la `Casilla` del sistema, a `Espacio.xs`, como dos hermanos chicos
  (vuelta 16: con `Espacio.s` el subtítulo quedaba corto a 360). Se marca la fila entera. Van de borde a borde
  de la tarjeta del vacío (`VacioQueEnsena(contenidoDeBordeABorde = true)`: con el relleno de la
  tarjeta más el de la fila no le quedaba lugar al nombre), separadas con `Separador(ConInicio)`.
  «Crear estos N» y «Crear uno a mano» quedan igual.
- **Movimientos sin «Flujo del día»** (§11).

Lo vigilan `UnaSolaCifraHeroePorPantallaTest` y `PresupuestosConJerarquiaTest` (a 360 dp: la cifra
`Grande` del resumen, una sola señal roja sin píldora, el tope a la derecha y la casilla, nada
recortado).

## 21. Vuelta de diseño 15: filas que respiran y la misma cifra en Categorías

- **«Bancolombia Ahorros» va en un renglón a 390 dp.** Con la marca, el nombre y «$13.845.800», el
  título se partía en dos con la fila medio vacía. Medido con la letra de la app (×1,12): el título
  pide ~154 dp y le quedaban ~155 en Android (en la web, que dibuja la misma letra un par de px más
  ancha, no entraba). Los px no estaban en el relleno de la fila (16, el margen de toda lista), ni en
  el avatar (40), ni en la `Cifra` (Martian Mono, que no se recorta): estaban en el aire entre el
  título y el monto. **Ese aire es `Espacio.xs` (8)**, era `Espacio.s` (12)
  (`ESPACIO_ANTES_DEL_FINAL_DE_FILA`); entre el avatar y el texto sigue 12
  (`ESPACIO_ANTES_DEL_TEXTO_DE_FILA`, la que usa `SANGRIA_DEL_TEXTO_DE_LA_FILA`). A 360 dp, o con la
  letra del sistema agrandada, el título puede bajar a dos renglones —entre palabras—.
- **Los selectores de cuenta de Agregar son uno.** «Desde» y «Hacia» de Traspaso y Cuota usan la fila
  del de «Cuenta» (`FilaDeCuentaEnAgregar`): la marca, el nombre, el saldo con `Cifra` en su moneda,
  «Debes» / «A favor» en una deuda y la elegida marcada, sobre una `Superficie` de capa 2 (los créditos
  siguen en su grupo, al final). Eran el nombre pelado y un «Elegida» en texto, sin saldo. **La
  verificación de la elegida va sobre la marca** (una insignia de `Medida.insignia` en la marca, con el
  borde de la capa 2), no al final: ahí reservaba 28 dp en todas las filas y el nombre se partía.
- **Categorías dice lo mismo que «En qué se va».** La cifra de cada categoría (`monthTotal` /
  `monthIncomeTotal` de `GET /api/categories`) sale de `movimientosDeFlujo`, la regla única del
  server —la de `spentByCategory` del Inicio y la de `esGastoDelPeriodo` + `montoContado` de la app—:
  solo tu parte de un gasto repartido, tu parte de lo que pagó otro, sin traspasos, pagos de tarjeta
  ni lo que espera en «Por confirmar»; lo que entró, con la regla del chip «Ingresos». El conteo
  («N movimientos») sigue contando todo uso. El rótulo de la hoja dice «Este período» y solo «Este
  mes» cuando el período es el mes del calendario (`rotuloDelPeriodoDeCategorias`).

Lo vigilan `LaFilaNoSeParteConAireTest` (Robolectric, motor de texto real: un renglón y con aire a
390 dp, también en el selector de Agregar; el aire de 8; dos renglones entre palabras a 360 dp o con la
letra ×1,3), `SelectoresDeTraspasoTest` (Desde y Hacia, en Traspaso y Cuota, pintan `Fila` con
`Cifra` y la marca), y en el server `EspacioRoutesTest` (con un gasto repartido y uno que pagó otro,
Mercado da lo mismo en Categorías, en «En qué se va» y en el Inicio) y `CategoryRoutesTest`.

## 22. Vuelta de diseño 16: crear una cuenta es igual en todos lados

Revisado en master a 390 px, en claro y oscuro.

- **Un solo bloque para crear una cuenta** (`FormularioDeCuenta`, `ui/accounts/FormularioDeCuenta.kt`,
  tag `formulario-de-cuenta`). La guía de primer uso ofrecía bancos con su marca y «Ahorros · Corriente ·
  Efectivo · Inversión»; Patrimonio → «Nueva cuenta» (`CreateAccountSheet`), «Dinero / Inversión / Bien»
  en tarjetas grandes y sin bancos. Ahora los dos pintan, en este orden: **Banco** (`PildoraDeFiltro` con
  el avatar de la marca, `SelectorDeBanco`), **Tipo** (`TIPOS_DE_CUENTA`; «Bien» solo si quien llama pasa
  `onElegirBien`: Patrimonio sí, la guía no), **Nombre** (`Campo`) y **Saldo de hoy** (`CampoDeMonto`).
  Al elegir el banco el nombre se propone solo, con el tipo («Bancolombia Ahorros»,
  `nombrePropuestoDeLaCuenta`), mientras esté vacío o siga siendo la propuesta; lo escrito a mano no se
  pisa, y «Bien» no hereda el nombre propuesto para una cuenta. La cuenta sale de
  `EstadoDelFormularioDeCuenta.cuentaNueva()` y se crea con `crearCuentaConSaldo`, la de siempre: los
  mismos tipos (`SAVINGS`, `CHECKING`, `CASH`, `INVESTMENT`), ninguna lógica nueva. La hoja ahora se
  desplaza (`verticalScroll` + `weight(1f, fill = false)`).
- **«En qué se va» pinta el monto neutro** (§6).
- **La tarjeta de cuenta no repite el tipo** (§6): `tipoSiElNombreNoLoDice`, la regla de Patrimonio,
  también en el Inicio y en la lista de cuentas de la guía.
- **Las propuestas de presupuesto dicen «Gastaste $X»** (§20).
- **El encabezado del día resalta con aire y radio** (§11, `renglonTocable`).

Lo vigilan `CrearUnaCuentaEsIgualEnTodosLadosTest` (los dos lugares pintan el bloque por su tag y crean
la misma cuenta para la misma elección; «Bien» solo en Patrimonio; la tarjeta sin tipo mide lo mismo),
`FormularioDeCuentaTest`, `ContraElPeriodoPasadoEnPantallaTest` (el monto neutro aunque la diferencia
sea de alerta), `PresupuestosConJerarquiaTest` (un renglón a 360 dp) y `ElDiaPlegadoNoCortaLaFechaTest`.

## 23. Vuelta de diseño 17: hojas que obedecen y ejemplos que no parecen datos

Revisado en master a 390 px con la cuenta demo.

- **Escape cierra también la hoja desde abajo** (`MarcoDeHoja`). En la web angosta (< 600 dp) la hoja
  del teléfono no atendía Escape: Patrimonio → «Nueva cuenta» solo se cerraba con la «×». Ahora lo
  atrapa en el *preview*, como la centrada, así que un campo enfocado no se lo traga, y pasa por
  `onEscape`: en Agregar el primer Escape sigue volviendo del sub-editor al formulario. Con teclado
  físico (`LocalHayTecladoFisico`) y sin `onTecla`, el velo toma el foco al abrir si nadie de adentro lo
  tomó, para que Escape llegue sin haber tocado un campo. Un menú desplegable es un `Popup` con su
  propia raíz: su Escape no pasa por la hoja.
- **«Repartir» es una acción de la fila del detalle** (`AccionDelMovimiento.Repartir`, ícono de
  Espacios, después de «Repetir»), no una tarjeta de ~110 dp debajo de la categoría de cada gasto.
  Abre una fila por espacio con «Tu parte $X» (`SeccionDeRepartir`). La tarjeta «con razón» (la
  categoría o el comercio ya se repartieron) no quedó: el cliente no lo sabe sin pedir el detalle de
  cada espacio, y los parecidos llegan sin el espacio. Un gasto repartido sigue mostrando «Repartido en
  … · Tu parte · Quitar» (`BloqueDeRepartido`) al abrir. La fila no pasa de
  `MAXIMO_DE_ACCIONES_DEL_MOVIMIENTO` (6; con siete, «Categoría» se corta a 360 dp): con «Tercero» y
  «Repartir» a la vez, «Repartir» va al principio de «Más».
- **Los ejemplos numéricos dicen «Ej:»** (§15): corte, día de pago, tasa y plazo de las hojas de
  tarjeta y crédito; un monto vacío, solo la moneda.
- **`EncabezadoDeHoja`** (`ds/Encabezado.kt`): el nombre en `titulo` medio y, debajo, qué es en
  `cuerpo` `textoMedio` —el patrón que las hojas con título escribían a mano—. La hoja de la tarjeta abre
  con «Nu · Tarjeta» (antes «La tarjeta» como título de grupo y el nombre suelto) y la del crédito con
  «Vehículo 4083 · Préstamo», con el nombre editable debajo. Los títulos de las hojas de Espacios
  (`TituloDeLaHoja`) delegan acá.

Lo vigilan `EscapeEnLaHojaAngostaTest` (Escape con un `Campo` enfocado cierra la hoja a 390 dp, respeta
`dismissEnabled`, llega sin foco con teclado físico, y en Agregar vuelve primero de la Nota),
`EspaciosEnPantallaTest` y `AccionesDelMovimientoTest` (la acción, el bloque, el tope de seis),
`LasGuiasNoParecenDatosTest` y `LosCamposDelSistemaTest` (las guías con «Ej:», el monto sin «0», el
encabezado de las dos hojas).

## 24. Vuelta de diseño 18: hojas y confirmaciones con un solo patrón

Un revisor independiente auditó master buscando lo que todavía no usaba el sistema y ninguna guarda
contaba. Esta vuelta toma las hojas y las confirmaciones.

- **Toda hoja abre con `EncabezadoDeHoja`** (§23). Solo tres lo usaban; dieciséis escribían el título
  a mano, con tres variantes (título + párrafo, rótulo chico arriba + nombre debajo, «Alias» suelto
  haciendo de título). Ahora el título en `titulo` medio y, debajo, la frase que la hoja ya decía, en
  `cuerpo` `textoMedio`: eliminar una cuenta, apartado, bien, para qué es esta plata, ajustar el
  saldo y simular un abono de un crédito, los días de aviso, el inicio del mes y el de un período,
  tu perfil, leyendo un papel, «¿En qué espacio?», la ficha y la hoja de una persona o comercio y
  la confirmación de borrar. En **ajustar saldo** y **simular un abono** el nombre del crédito es
  el título y qué es la hoja el subtítulo (era un rótulo chico arriba del nombre), y la deuda va en
  `Cifra`, como los montos de la ficha de un tercero («Este período», «por motivo», cada movimiento).
- **Las confirmaciones destructivas son `Boton`** (`BotonesDeConfirmar` en
  `ui/components/ConfirmarAntesDeBorrar.kt`): «Cancelar» en `Fantasma` y la acción («Borrar»,
  «Eliminar», «Cerrar en todos») en `Peligro`, al final de la fila; `M` en la hoja propia
  (`ConfirmarEnHoja`, cuya pregunta y detalle son su `EncabezadoDeHoja`) y `S` dentro de un bloque
  (`ConfirmacionEnLinea`). Mientras borra, la ruedita del `Peligro` y «Cancelar» apagado. Eran dos
  `Text` tocables de 10/14 dp, con el botón sin vuelta igual de liviano que el que cierra.
  Documentos dejó su «Cancelar / Borrar» propio y usa `ConfirmarEnHoja`; eliminar una cuenta, sus
  dos píldoras de 48 hechas a mano. `Boton` dice su variante en la semántica (`VarianteDeBotonKey`)
  para poder vigilarlo sin leer colores.
- **Los botones hechos a mano son `Boton`**: el «Guardar» de 54 dp de Personas y comercios
  (`BotonDeGuardar`, borrado) es `Principal` a todo el ancho; el del inicio de un período también,
  y «Volver al día de siempre» y «Ver tus períodos» son `Fantasma` `S`. Los textos tocables que
  hacían de botón —«Activar notificaciones», «Ahora no», «Volver al de Movi», «Sin categoría»,
  «Responder», «Ver», «Editar» en la ficha— son `Fantasma` `S`. **«Eliminar términos»** (crédito y
  tarjeta) y «Eliminar» en la hoja de un tercero son `Peligro` `S`: destruyen algo, y `Fantasma` es la
  marca.
- **`monto` es solo para plata.** Un párrafo («Por confirmar»), el nombre de una cuenta, una URL
  (Compartir) y el número de `NumeroEnCirculo` iban en la letra de las cifras; van en `cuerpo`. El
  enlace no tiene estilo monoespaciado propio: la superficie de capa 2 y la selección ya lo apartan.
- **`sobreAvatar`** (`Tokens.kt`): lo que va encima del círculo de un avatar, blanco en los dos temas,
  porque el color del círculo es del dueño (`AvatarPalette`, tonos «600» hechos para blanco) y no del
  tema. `sobreMarca` no servía: en oscuro es casi negro y sobre el índigo de la paleta daba 2,3:1.
  Reemplaza tres `Color.White` (Ajustes, Tu perfil, el botón del avatar); `ContrasteDeLosTokensTest`
  mide cada color de la paleta contra él (≥ 3:1, el de texto grande).
- Los `Hairline()` de los archivos tocados (las hojas de arriba, Categorías y Documentos) pasaron a `Separador(SangriaDeSeparador.Ninguna)`, que dibuja lo mismo.

**La guarda nueva**, `LoQueNoEsDelSistemaSoloBajaTest`: el trinquete de `LosValoresSueltosSoloBajanTest`
para seis cosas más (§8), con sus líneas base en `lineas-base.txt` y en
`scripts/auditoria-de-diseno.py --resumen`. Antes → después de esta vuelta: botones de Material 4 → 4,
`Color.White`/`Black` 6 → 3 (dos son el velo de la hoja, exceptuado), `Hairline` 26 → 14, `MinCard`
22 → 22, `Text(formatCOP…)` 14 → 10, `fontWeight =` 224 → 190; y los de siempre, `.dp` sueltos 261 →
242 y radios con número 22 → 19.

Lo vigilan `HojasYConfirmacionesDelSistemaTest` (Robolectric: la confirmación pinta dos `Boton`,
`Fantasma` y `Peligro`, el de borrar en el `sale` del tema, «Cancelar» apagado mientras borra; eliminar
una cuenta, ajustar el saldo de un crédito y el inicio del mes abren con `EncabezadoDeHoja`), la guarda
nueva y `ContrasteDeLosTokensTest`.

## 25. Vuelta de diseño 19: Hoy y las cifras con rótulo, todas del sistema

Un revisor auditó master buscando lo que todavía no usaba el sistema. Lo de Hoy y lo de las cifras:

- **Hoy: «Tu patrimonio», «Para revisar», «Pregúntale a Movi» y «Tu día más justo»** eran `MinCard`
  con rellenos sueltos (18 × 6, 18 × 12), `CardRow`/`Hairline`, puntos de 7 dp a mano y la `Cifra`
  vieja con números compactos («$5M») en la misma pantalla donde el héroe dice la cifra entera. Ahora
  son `Superficie(Lista)` + `Fila` + `Separador`:
  - **«Tu patrimonio»**: arriba el neto (`CifraConRotulo` `Grande`), la barra y «Tienes · Debes», con
    las cifras enteras; debajo un tramo por `Fila`, con el ícono de lo que es en su círculo (`Exito` lo
    que tienes, `Neutro` lo que debes: los dos lados de la barra) y la `Cifra` entera al final. Las
    cuentas de «Tu plata» cuelgan de su fila con `BajoLaFila`, en `Cifra` `Chica` apagada en su moneda
    (`CuentaHero` trae el saldo y la moneda sin escribir).
  - **«Para revisar»**: cada cosa es una `Fila` con el `IconoEnCirculo` de la pantalla donde se
    resuelve, en `Aviso` si es urgente y en la marca si no, el detalle de subtítulo (dos renglones) y el
    `Chevron`. Lo que se sale de lo normal, con `WarningAmber` en aviso; la evidencia (con su `Cifra`
    `Chica`) y «Ver los movimientos · Está bien» cuelgan con `BajoLaFila`. La línea compacta es una
    `Fila` con el ícono de la bandeja en aviso.
  - **«Pregúntale a Movi»**: cada pregunta es una `Fila` con el ícono de Movi AI y el `Chevron`; su
    esqueleto, `EsqueletoDeFila` (los dos miden 56). El campo de abajo sigue siendo un botón con cara
    de campo —escribir ahí y saltar al chat perdería el foco—, con el margen de la fila.
  - **«Tu día más justo»**: una `Fila` —lo que es de título (en `sale` si quedarías en rojo), cuándo de
    subtítulo, la `Cifra` al final—. Cargando, el subtítulo vacío reserva su renglón y la cifra es un
    bloque del mismo alto: nada salta.
  - **El Disponible** sigue con sus tres columnas, ahora con la `Cifra` del sistema `Compacta` (§6).
- **Montos con `Text`** (el flujo libre de Plan, el tope y los movimientos de Presupuestos, la hoja de
  anular, los candidatos a pago de tarjeta, la fila de una categoría en el detalle de un período):
  `Cifra`. La hoja de anular dice el gasto con «−» y el ingreso con «+» por el tono, no armando el texto.
- **`CifraConRotulo`** (§6) en Hoy, Tus períodos (compacta: son tres), Créditos (en renglón) y el flujo
  libre de Plan (también su cifra grande, con «—» sin datos).
- **`Hairline` → `Separador` y `MinCard` → `Superficie`** en los archivos tocados.

## 26. Vuelta de diseño 20: Agregar, la revisión de extractos y los chips, al sistema

La misma auditoría de la vuelta 18 dejó una lista de lo que seguía hecho a mano. Esta vuelta la toma.

- **La revisión de un extracto** (`extractos/StatementReviewScreen.kt`) era el archivo más fuera del
  sistema, y se usa cada mes. Sus dos `Button` y dos `OutlinedButton` de Material son `Boton`
  («Importar N seleccionadas» principal a todo el ancho, con la ruedita de `cargando`; «Anotar los N»
  y «No son el mismo» secundarios chicos; **«Sí, es el mismo»** —era «Confirmar reconciliación», que
  no entraba en un botón chico al lado de «No son el mismo»— principal chico). El texto iba en
  `Color.White`, que sobre la lavanda del tema oscuro no pasa AA; el `Boton` pone `sobreMarca`. Cada
  fila del extracto es una `Fila` con la `Cifra` y la `Casilla` al final, tocable de borde a borde
  (`toggleable`, `Role.Checkbox`), y la nota («Ya anotado en…») cuelga debajo alineada con el título.
  Los títulos son `Seccion` con su conteo («Cargos y abonos del banco · 4», «Nuevas transacciones ·
  12» con «Seleccionar todas» como su acción). La coincidencia es una `Superficie` sin borde y su
  estado lo dice una `Pildora` («Posible duplicado» en aviso, «Reconciliado» en éxito, «Se importará
  como nuevo» neutra); era el color de un borde con `copy(alpha = 0.5f)`. La cuenta destino es una
  `Pildora` (marca, o aviso si falta). Las revisiones de saldo y de crédito usan `RotuloDeCampo`
  para «El extracto dice», «En tu cuenta» y «¿En qué cuenta?»: el estilo `rotulo` es el de mayúsculas
  espaciadas, y en frase el rótulo del sistema es ese.
- **Agregar**, solo lo que quedaba a mano (el teclado, el monto y guardar no se tocaron):
  - los chips de categorías frecuentes son `PildoraDeFiltro` y «Nueva» una `PildoraDeAccion` con el «+»
    como ícono. La `PildoraDeAccion` no le pone `weight` a su texto: dentro de una fila con
    desplazamiento horizontal el ancho máximo es infinito y un hijo con peso se quedaba en cero —en la
    web «+ Nueva» se veía como un «+» y una «N»— (lo fija `HojaAgregarChipsSeLeenEnterosTest` a 390 y
    360 dp). La fecha es una `PildoraDeAccion` con el ícono de calendario en **`AltoDePildora.Compacta`**
    (24 dp, el texto en `apoyo`: lo que medía la de antes; el toque igual llega a 48);
  - «Guardar y anotar otro» es un `Boton` `Fantasma` `S`. Su renglón reservado mide 16 y el botón 36:
    `wrapContentHeight(unbounded = true)` lo deja medir lo suyo centrado en el renglón, y como el
    botón de texto no tiene fondo, lo que sobresale no se ve. La hoja no cambia de alto;
  - «Pagó otro» y «Subir comprobante» son **`BotonDeIcono` `Secundario`** (nuevo: 48 × 48 en la capa 2
    con el radio de un `Boton`, para ir al lado de uno de alto `M`); `BotonDeIcono` ganó también
    `habilitado`;
  - Categoría, Cuenta, Nota y Desde / Hacia del traspaso son `Fila` en **`DensidadDeFila.Compacta`**
    (`FilaDeLaHoja`: 48 dp con o sin subtítulo, el valor a la derecha con su techo de ancho y el
    `Chevron`, un `Separador` entre filas como los `Hairline` de antes; el renglón de «Última usada» se
    reserva como antes para que el teclado no se mueva). Eran `CardRow` de 48 dp. **La hoja de Agregar
    no puede medir más que antes**: con la densidad de siempre (56 / 64) y la fecha de 36 crecía ~30 dp
    y a 360 × 740 «Guardar movimiento» quedaba cortado y «Guardar y anotar otro» no se veía. La
    densidad compacta es solo para un formulario así; toda lista sigue en 56 / 64.
    `HojaAgregarGeometriaTest` lo fija a 360 × 740: el botón entero sin gestos, la tarjeta ≤ 195 dp y
    la fecha ≤ 24 dp, lo que medían en master;
  - el aviso pendiente («Es este / No») usa `PildoraDeAccion`, y los atajos «Hoy · Ayer» del selector
    de fecha, `PildoraDeFiltro` (que ahora centra su texto si ocupa más que él) y `lavado()`.
- **Chips y filas de otras pantallas.** Categorías: los filtros y el tipo (en un `FlowRow`, que ya no
  corta «Automático») son `PildoraDeFiltro`, y Renombrar / Unificar / Esconder son `Fila` en una
  `Superficie` de capa 2. La categoría de un movimiento y las sugerencias de un presupuesto,
  `PildoraDeAccion`. En la hoja de un crédito, `SelectRow` y `OpcionDeAlta` son una `Fila` en capa 2
  con el visto en la marca (semántica de radio) y la casilla es la `Casilla`. La fila de un documento
  es una `Fila` (tocarla abre el archivo) con sus acciones como `Boton` chicos —«Borrar» en
  `Peligro`— en un `FlowRow`. «Editar inicio» es un `Boton` `Fantasma` `S`; `NewItemButton` delega en
  `Boton`; el ofrecimiento de recurrente usa `Boton` y `BotonDeIcono`; «Reintentar», `lavado()`.
  `ScopeToggle` no lo usaba nadie y se borró.

**Las líneas base**, antes → después: botones de Material 4 → **0**, `Color.White`/`Black` 3 → **0**
(fuera del velo de `MarcoDeHoja`, exceptuado), `Hairline` 14 → 13, `MinCard` 22 → 20,
`Text(formatCOP…)` 10 → 10, `fontWeight =` 190 → 145; `.dp` sueltos 242 → 213 y radios con número
19 → 16. Con la vuelta 19 ya mezclada (que bajó `Hairline`, `MinCard`, `Text(formatCOP…)`, `fontWeight`
y `.dp` en Hoy y las cifras), las líneas base quedan en: `.dp` sueltos 196, radios 16, botones de
Material 0, blanco o negro 0, `Hairline` 4, `MinCard` 14, `Text(formatCOP…)` 4, `fontWeight =` 134.

Lo vigilan `LaRevisionDelExtractoEsDelSistemaTest` (cada botón es un `Boton` con su variante, una
`Casilla` por fila que se destilda al tocarla, los montos son `Cifra`, y el texto del botón
principal pasa AA en el tema oscuro mientras el blanco de antes no), `AgregarEnElSistemaTest` (los
chips son `PildoraDeFiltro` y dicen el elegido, «Nueva» y la fecha son `PildoraDeAccion`, «Guardar
y anotar otro» es un `Boton` `Fantasma` que aparece sin mover el teclado, las filas miden lo de una
`Fila`, «Subir comprobante» es un `BotonDeIcono` de 48 con su descripción),
`CategoriasConPildorasYFilasTest`, `FilaDeDocumentoTest`, dos más en `SistemaDeDisenoTest`, y las de
siempre de la hoja de Agregar (`HojaAgregarGeometriaTest`, `AgregarPorDentroTest`, el teclado).

## 27. Vuelta de diseño 21: llevar a cero lo que no es del sistema

Una vuelta de limpieza: bajar las cuentas de la guarda sin cambiar cómo se ve ninguna pantalla.

- **`fontWeight =` de 134 a 2.** 33 repetían el peso de su estilo (`cuerpo` + Medium, `titulo` +
  SemiBold, `monto` + Normal) y dos eran un Bold que se dibujaba como el SemiBold de su estilo: se
  borraron. El resto pasó a los **pesos con nombre** de §5 (más de cuarenta a `apoyoMedio`, diecisiete
  a `tituloMedio`, y `cuerpoRegular`, `cuerpoFuerte`, `tituloRegular`, `pestana` / `pestanaActiva`,
  `enfasis`). Lo elegido de un selector es `if (elegido) estiloA else estiloB`. El
  `Cifra(texto, estilo)` viejo de `ui/components` ya no fuerza Medium: recibe un estilo que ya lo trae
  (`estiloDeLaCifra(VarianteDeCifra.Fila, …)` o `apoyoMedio`). `ds/` también los usa (`Boton`,
  `Pildora`, `RotuloDeCampo`, `Seccion`, `EncabezadoDeHoja`, `TarjetaDeCuenta`, `AccionRapida`).
  Quedan dos a propósito: el logotipo «movi» del riel (que tampoco tiene estilo: su `fontSize` es una de
  las dos excepciones de tamaño) y el nombre de una categoría en su hoja (`titular` en Medium, único).
- **`MinCard` de 14 a 0, y borrada** (con `MinCardVariant`), también sus 6 usos fuera de `ui/`
  (la oferta de avisos y el sensor de SMS de Android). Un relleno que no es de `RellenoDeSuperficie`
  va dentro del contenido, en tokens: las listas de `CardRow` del Inicio por SDUI y la de cuentas de la
  revisión de un extracto son `Superficie(Lista)` con su margen horizontal adentro; «No se pudo leer»,
  el vacío que enseña y «No pudimos actualizar», `Superficie(Ninguno)` con el relleno en su columna.
  `SuperficieConRellenoLibre` quedó privada.
- **`Hairline` de 4 a 0, y borrada**: `Separador(SangriaDeSeparador.Ninguna)` dibuja lo mismo.
- **Montos en `Text` de 4 a 0**: la fila de un movimiento parecido en la hoja de categoría, el monto
  que encabeza «Monto, cuenta y concepto», el total de los movimientos de un presupuesto y lo que toma
  cada pago al repartir son `Cifra`. Ahora también se ocultan con «Ocultar los saldos».
- **De paso, `.dp` sueltos de 196 a 119 y radios con número de 16 a 3**, solo donde el valor
  coincidía exacto con un token (rellenos y separaciones a `Espacio`, radios a `Radio`, bordes de 1 a
  `Medida.trazo`, íconos a `Icono`): nada cambia de tamaño.

**La guarda**: `hairline`, `mincard` y `cifra_en_text` llegaron a 0, como antes `botones_de_material` y
`blanco_o_negro`. `LoQueNoEsDelSistemaSoloBajaTest` las exige en **0** —falla si reaparecen o si su
línea base deja de ser 0—, no solo «no subir».

| clave | antes | después |
|---|---:|---:|
| `dp_sueltos` | 196 | 119 |
| `radios_con_numero` | 16 | 3 |
| `botones_de_material` | 0 | 0 |
| `blanco_o_negro` | 0 | 0 |
| `hairline` | 4 | **0** |
| `mincard` | 14 | **0** |
| `cifra_en_text` | 4 | **0** |
| `font_weight` | 134 | **2** |

**Lo que se ve distinto** (casi nada): tres de los cuatro montos que eran `Text` ahora van en la letra
de las cifras (Martian Mono), como toda cifra de plata —el del encabezado de «Monto, cuenta y concepto»
pasó de `titulo` (15,5) a la cifra de una fila (12,5)—; el total de un presupuesto es la `Cifra`
`Chica`, que es la misma letra que tenía. La negrita de un aviso del banco es SemiBold (era Bold sobre la
monoespaciada del sistema, que la sintetiza igual).

## 28. Vuelta de diseño 22: una sola verdad en Hoy

Un diseñador recorrió la app con la cuenta demo y la encontró contradiciéndose. Esta vuelta no cambia
qué cuenta como plata ni qué es un pendiente: cambia el **orden**, el **origen** de cada contador y el
**texto**.

- **Una sola cola de lo pendiente** (`colaDePendientes`, `:core` `ColaDePendientes.kt`). Junta los pagos
  del checklist del período (vencidos y de la semana), lo que el banco cobró solo y lo que espera en «Por
  revisar» (una sola entrada), ordenados por urgencia —vencido, hoy, débito por confirmar, por revisar,
  por vencer— y, dentro de cada grupo, **el monto más grande primero** (lo de más adelante, por fecha).
  La leen la campana (`notificationRows`), «Movi propone hoy» (`colaDelInicio`, `pagoPropuestoHoy`), el
  widget «Falta por pagar» y la lista «Por pagar» de Plan (`pagosEnOrdenDeUrgencia`).
  - **Por qué el Arriendo no entraba a la campana**: la campana leía el vencimiento VIGENTE de
    `/api/payments/upcoming`, que rueda al período siguiente pasados los días de gracia (`dueDateFor`).
    Vencido hace 22 días, el Arriendo figuraba «vence en 9 días» y la ventana de 7 lo dejaba afuera; el
    checklist, donde manda la ocurrencia, sí lo veía. Ahora la campana sale del checklist.
  - **Hoy no repite un pago** (deshecho en la vuelta 28, §40: el widget esconde así la deuda grande): el widget no lista como fila el pago que ya está en «Movi propone hoy»
    (`filasDelWidgetDeFalta`, solo si esa sección está en el Inicio) y dice «y N más» cuando recorta; las
    filas más ese número dan lo que dice «Faltan N». Si el único pendiente es el de arriba, lo dice
    («Está arriba, en «Movi propone hoy»»).
- **Los contadores cuadran** (`ConteoPorRevisar`, `ui/porrevisar/PorRevisar.kt`): avisos (uno por pago),
  movimientos que entraron solos (renglones: un traspaso es uno, `renglonesPorConfirmar` en `:core`),
  pagos de tarjeta y débitos. La insignia de «Avisos», «Movi propone hoy», la campana y «N por revisar»
  de Movimientos dicen el mismo total, y donde se ve el total se dice de qué es («2 avisos · 1
  movimiento», `desgloseDePorRevisar`). El Inicio no se baja la historia: el server manda
  `DashboardSummary.movimientosPorConfirmar` con la misma regla (default 0 para un server viejo).
- **La frase de abajo del héroe** (`fraseDelHeroe`): con el Disponible calculable, «Gastaste $1,7M · lo
  que más: Mercado $316.050» —la MISMA cifra que la columna «Período» del Disponible de Plan, escrita
  igual—; «lo que más» es la categoría más grande que cabe en esa cifra (un fijo no es gasto del día a
  día). Sin Disponible, el flujo con su verbo («Salió $893.300 más de lo que entró · lo que más: …»).
  El lector de pantalla oye la frase larga, que dice lo mismo. El héroe («Te quedan…») no se tocó.
- **Los avisos apagados se ven a tiempo**: en Ajustes → Avisos, la fila «Avisarme antes de un
  vencimiento» dice «Apagado: no te va a llegar» en `TonoDeSubtitulo.Aviso`, con «Activar» (salvo si el
  navegador las bloqueó), mientras no haya canal (`estadoDeLosRecordatorios`, la regla de
  `ReminderWarning` con nombre). En Plan, «Tus recordatorios no te van a llegar» sube justo encima de
  «Pagos del período» (antes, al final del segmento); una vez.
- **Sin salario no hay flujo libre negativo**: sin ingresos recurrentes (`pideElSalario`), la tarjeta de
  «Lo que se repite al mes» invita «Agrega tu salario para ver tu flujo libre» con «Agregar mi salario»,
  que abre la hoja del recurrente ya en Ingreso (`CreateRecurringRuleSheet(tipoNuevo = …)`), y deja
  «Gastos recurrentes» a la vista.

**Las líneas base** no cambian (todo con componentes y tokens): `dp_sueltos` 119, `radios_con_numero` 3, y
las guardas en 0 siguen en 0.

Lo vigilan `ColaDePendientesTest` (`:core`: el orden, la urgencia, el mínimo de una tarjeta, el Arriendo
con su vencimiento rodado, los renglones por confirmar), `UnaSolaVerdadEnHoyTest` (la campana con el
Arriendo primero, la campana y la propuesta priorizan igual, Hoy no repite un pago, «y N más», y con 2
avisos y 1 movimiento los cuatro lugares dicen lo mismo), `HoyEnUnaMiradaTest` (la frase usa la cifra de
Plan), `LosAvisosApagadosSeVenTest`, `ElFlujoLibrePideElSalarioTest`, `ChecklistEnInicioTest` y
`DashboardRoutesTest` (el server cuenta los movimientos por confirmar como renglones).

## 29. Vuelta de diseño 24: repartir en el teléfono sin esperar, y los cabos sueltos

- **«Pagué yo» reparte de una también en el teléfono.** En Android e iOS el gasto se guarda primero en
  la base local y lo sube el `SyncEngine` cada 30 s; el reparto lo hace el server, que no lo conocía, y
  la hoja caía siempre a «Reintentar el reparto». Ahora `LocalRepository.repartirMovimiento`, si la fila
  sigue pendiente, da antes **una vuelta de la misma subida** (`SyncEngine.subirLoPendiente`, con el
  candado que comparte con el ciclo: cuentas antes que movimientos, el `POST /api/events` idempotente y
  el mismo sello) y después reparte. Sin señal no pide el reparto: la hoja dice `REPARTO_SIN_CONEXION`
  («El gasto quedó anotado. Para repartirlo hace falta conexión: reintenta cuando vuelva la señal.») y
  deja «Reintentar el reparto», que nunca vuelve a guardar. No se difiere solo: las escrituras de un
  espacio no tienen cola en el teléfono. Sin endpoint nuevo ni regla de plata nueva.
- **El efectivo en negativo se lee entero.** La fila de Patrimonio decía «No puede quedar en negativ…» a
  390 px. Ahora dice «En negativo: cuádralo» (la cifra ya va en rojo y con signo; el porqué completo
  está en el detalle) y, solo en esa fila, el subtítulo puede bajar a un segundo renglón con la letra
  muy grande antes que cortarse. A 390 con la letra de Movi entra en uno.
- **La tarjeta de cuenta sigue con el color de su banco.** La revisión vio que una tarjeta rellena de
  amarillo (Bancolombia) o morado (Nu) puede leerse como un botón. No se cambia: es la maqueta elegida
  del camino 1 «Noche premium» (la tarjeta de Bancolombia en `#FDDA24` con el texto en `#111111`, la de Nu
  en `#820AD1` con blanco, el círculo de la marca invertido) y está escrita en `TarjetaDeCuenta` (§ de
  componentes) desde la R1. El contraste de la cifra no depende del tema: va en el texto de la marca
  sobre su fondo, y `MarcasConocidasTest` exige 4,5:1 a cada marca del catálogo. Si el dueño quiere la
  tarjeta en la superficie del sistema con la marca solo en el avatar, es un cambio de esa decisión.

**Las líneas base** no cambian: `dp_sueltos` 119, `radios_con_numero` 3, y las guardas en 0 siguen en 0.

Lo vigilan `RepartirSubeElMovimientoAntesTest` (`:core`, con la base local de verdad: sube y después
reparte, sin señal no pide el reparto, el gasto nunca va dos veces, la cuenta creada sin señal sube
primero, lo rechazado dice por qué), `RepartirEnElTelefonoTest` (la hoja contra `LocalRepository`:
cierra sin «Reintentar», y sin señal dice el mensaje y el reintento no guarda otra vez),
`RepartirDesdeAgregarTest` y `ElEfectivoEnNegativoSeLeeEnteroTest` (360 dp ×1,3 entero; 390 dp ×1,12 en
un renglón).

## 30. Vuelta de diseño 26: Hoy con cuatro acciones y «Pedirle a Caro»

Dos decisiones del dueño (27-oct).

- **La cuadrícula de Hoy es una fila** (`AccionDelInicio`, `ui/dashboard/InicioDelRediseno.kt`): Gasto
  (la destacada), Ingreso, Avisos (con la insignia de `ConteoPorRevisar`) y Preguntar. La arma el
  cliente —la definición SDUI solo trae `ACCIONES`—, así que el server y la generación no cambian y un
  APK viejo sigue con sus ocho. **Traspaso, Pago de tarjeta, Repartir y Subir comprobante** viven en el
  menú del «+» (`AtajoDeAgregar`, una vez cada una; «Repartir» llegó de la cuadrícula y abre la misma hoja
  de la vuelta 7 por `hacerElAtajo` y el `EstadoDeRepartir` de la cáscara). El menú se abre manteniendo
  presionado el «+» (teléfono), con el **clic derecho** (web y escritorio, `BotonDeAgregarConAtajos`) y,
  en el rail ancho, con la **flecha «▾»** al final de «Agregar» (`conFlecha`, 48 dp de toque).
- **Espacios: «Pedirle a Caro».** Cuando el otro te debe (`principalDelSaldo`), el principal de la
  tarjeta del saldo es «Pedirle a {nombre}»: arma un mensaje corto en tuteo (`mensajeParaPedir`: «Hola,
  Caro. Me debes $76.050 de Casa con Caro:», los tres gastos más recientes desde el último saldado con
  «tu parte» de quien lee, «y N más», y el enlace de solo lectura si algún día existe; las cifras van
  enteras aunque los saldos estén ocultos) y lo manda con `HojaDeCompartir.mandarMensaje`: la hoja del
  sistema en Android e iOS, `navigator.share` en la web y, si no hay, copiar con «Mensaje copiado» y
  «Abrir WhatsApp» (`wa.me/?text=…`). «Marcar como saldado» pasa a `Fantasma` y, sin transferencias
  propuestas, pregunta antes con `ConfirmarEnHoja` («¿Caro ya te pagó $76.050?» · «Esto deja el
  espacio en cero.»); con transferencias, la hoja de siempre para elegir cuál vincular. Saldar es la
  misma liquidación de siempre. Cuando tú le debes, el principal sigue siendo «Marcar como saldado».
  `ConfirmarEnHoja` ganó `varianteConfirmar` (por defecto `Peligro`): lo que no destruye nada va en
  `Principal`.
- **Cada gasto del espacio dice su fecha**: «Pagó Caro · Mercado · 18 oct» (`quienPagoElGasto`).

**Las líneas base** no cambian, y las guardas en 0 siguen en 0.

Lo vigilan `InicioDelRedisenoTest` y `LasAccionesDelInicioEnUnRenglonTest` (cuatro en una fila a 360
dp), `AtajosDelMasTest` (las cuatro en el menú, el clic derecho, la flecha), `BuscarEnMoviTest`
(`hacerElAtajo`), `RepartirDesdeElMasTest` (Repartir desde el menú abre lo de la vuelta 7),
`PedirleACaroTest` (el mensaje, el principal, la pregunta, la fecha) y `EspaciosEnPantallaTest`.

## 31. Vuelta 27: un aviso que pagó varias cosas

El giro al papá que paga dos cuotas y sobra un poco se reparte en Reconciliar, sin componentes nuevos
en `ds/` (`ui/sms/RepartirEnPartes.kt`, `ui/sms/RepartirEnReconciliar.kt`):

- **La propuesta de entrada es una `TarjetaDePropuesta`** arriba, con las demás propuestas: «¿Pagaste
  las cuotas de Libranza Papá y Hipoteca Papá? Suman $4.250.000 · sobran $23.333», el cuerpo que dice
  qué se anota y «Repartir así», que **abre el editor lleno, no confirma**. Reemplaza a la tarjeta de
  #447 («Son 2 pagos…» con su lista a mano, en la que el último pago tomaba el resto).
- **«Esto pagó varias cosas»** es un `Boton` `Fantasma` `S` debajo de «Movi sugiere». Abre «Repartir en
  partes» (`Seccion`) y esconde «Confirma o ajusta»: en partes, cada parte dice lo suyo.
- **Cada parte es una `Superficie` de capa 1** (en una `Capa2` las píldoras sin elegir, que son de
  capa 2, no se veían y la fila parecía texto suelto): «Parte N» en `cuerpoFuerte` con «Quitar»
  (`Fantasma` `S`); **«Qué fue»** (`RotuloDeCampo`) con una sola fila de `PildoraDeFiltro`, siempre los
  cinco tipos en este orden: Gasto · Cuota · Pago de tarjeta · Traspaso · A un tercero; y debajo **solo
  lo que ese tipo necesita, con su rótulo**: «Categoría» si es un gasto (y «Pago del período» si hay
  uno pendiente), «Crédito», «Tarjeta» o «Cuenta» con las cuentas que `operacionEntre` acepta, y
  «Persona» (la del aviso, o un `Campo` para escribirla) más su «Categoría» si va a un tercero. El
  monto en `CampoDeMonto`. **Las filas de píldoras ruedan en horizontal**: no se parten ni se cortan a
  360 dp. Lo que le falta a la parte, en una línea de `apoyo`.
- **La línea en vivo** va en `cuerpoFuerte`: «Faltan $23.333» en `texto`, «Te pasaste por $X» en `sale`,
  «Cuadra con el aviso» en `entra`. Debajo, cuando falta, «El resto a Papá» / «El resto como gasto» en
  `PildoraDeAccion`: **siempre agregan una parte nueva por lo que falta** y no tocan las que estaban.
  «Agregar otra parte» y «Volver a un solo movimiento» en `Fantasma` `S`.
- **Un solo botón confirma**: el principal de la pantalla dice «Confirmar las N partes» y se prende solo
  cuando cuadran (`sePuedenConfirmar`). **Apagado, dice por qué** en una línea centrada debajo, como
  `PieDeHoja` (`razonDeLasPartes`): «Una sola parte no es un reparto: agrega otra o vuelve a un solo
  movimiento», «Parte 2: elige el crédito», o cuánto falta o sobra.
- **Un movimiento ya anotado** usa el mismo editor dentro de «Más» de su detalle («Repartir en partes»,
  `Secundario` a todo el ancho; al abrirlo, «Confirmar las N partes» en un `PieDeHoja` con su razón).

**Las líneas base** no cambian: `dp_sueltos` 119, `radios_con_numero` 3, y las guardas en 0 siguen en 0.
Lo vigila `RepartirEnReconciliarTest` (la propuesta del caso del dueño con cifras de prueba, «Faltan»,
el botón que se prende solo si cuadra y su razón, «El resto como gasto» que agrega la parte 2 sin tocar
la 1, «Qué fue» con su detalle por tipo, y nada recortado a 360 dp).

## 32. Vuelta de diseño 30: Movimientos con su resumen, tu parte primero y Movi AI sin cifras viejas

Revisado en producción (6ce5327e) con la cuenta demo, a 390 px.

- **Movi AI dice de qué día es cada respuesta** (`ui/ai/LoQueEraDeOtroDia.kt`). Cada mensaje trae
  `ChatMessage.enviadoEn` (el server lo pone desde `ai_turns.created_at` al recargar; la app, al mandar;
  default `null` para clientes y servers viejos). Si algún mensaje no es de hoy, los días se separan
  con una `Seccion` («Hoy», «Ayer», «2 sep»: `etiquetaCortaDeFecha`, la forma corta de los avisos). La
  respuesta de un día anterior va en `textoMedio` (`TextoDelAsistente(apagado = true)`) con «Estas cifras
  eran de ayer» y «Preguntar de nuevo» (`Boton` `Fantasma` `S`), que manda la misma pregunta —con su id
  si era una de las tres: el server ahora la devuelve en el globo recargado (`preguntaSugeridaDelTurno`)—.
  Sin la IA, «Preguntar de nuevo» solo va en las tres. Si la conversación entera es de otro día, arriba
  de la lista una `TarjetaDePropuesta` ofrece «Empezar una conversación nueva» (y «Seguir con esta»):
  nunca se borra sola.
- **Sin la IA, el chat no sugiere lo que no puede contestar.** `GET /api/ai/conversacion` manda
  `iaDisponible` (`EstadoDeLaIa` en el server: sin clave, o una falla de la cuenta —sin crédito, clave
  rechazada— en la última hora; la saturación no cuenta; una respuesta del modelo la vuelve a prender).
  Con `false`, el arranque y las preguntas para seguir son solo las tres de respaldo
  (`preguntasQueSePuedenHacer`). La respuesta sin clave ahora trae `codigo = IA_NO_DISPONIBLE`.
- **El encabezado de Movi AI va en su capa**, con `fondo` opaco y encima de la lista; la lista recorta
  lo suyo (`clipToBounds`).
- **Movimientos en el teléfono tiene su resumen** (`ResumenDelPeriodoEnElTelefono`): el **primer ítem
  de la lista** —se va con el scroll, antes de «N por revisar»; fijos siguen solo el encabezado, el
  selector y los chips (§11)—, Entró · Salió · Neto con `CifraConRotulo` en forma `Compacta` (columna de tres), con
  **la misma `resumenDelPanel`** del panel de escritorio, y tocarla abre «En qué se va». Mientras no
  hay historia, `EsqueletoDeCifraConRotulo` (sin «$0»). Con un filtro que recorta la lista (categoría,
  comercio, búsqueda) no va: el resumen es del período entero y encima de lo filtrado se leería como
  su total. Los chips de tipo no lo esconden.
- **En un gasto repartido, la cifra es tu parte** (`ui/transactions/LaFilaDelMovimiento.kt`): la fila y
  el detalle dicen «−$44.300» y debajo «de $88.600 · con Caro» (o el nombre del espacio si son más);
  el detalle agrega «Salieron $88.600 de Nu» en su renglón de cuándo y dónde. El saldo no cambia: el
  movimiento sigue siendo de $88.600. En el detalle de una cuenta (`tonoForzado`), donde el total del
  día es lo que movió el saldo, la fila sigue con el total y «Tu parte» debajo.
- **«Mercado / Mercado»** (`textosDeLaFila`): si la descripción es la categoría, el título es el
  comercio o la cuenta y el subtítulo no la repite. Solo presentación.
- **Buscar marca la pestaña de donde se abrió** (`destinoResaltadoDeLaPila`) y **el total de un comercio
  lleva el signo** del sistema (`CifraDelMovimiento` con el tono del movimiento).

**Las líneas base** no cambian: `dp_sueltos` 119, `radios_con_numero` 3, y las guardas en 0 siguen en 0.

Lo vigilan `LoQueEraDeOtroDiaTest` y `LaFilaDelMovimientoTest` (puras), `BuscarMarcaSuPestanaTest`,
`MoviAiNoMienteConCifrasViejasTest`, `MovimientosConSuResumenYTuParteTest` y
`LosComerciosLlevanSuSignoTest` (Robolectric), y en el server `LoQueMoviDijoOtroDiaTest` y
`LasCifrasDeComoVoySonLasDeHoyTest` («¿Cómo voy?» y `/api/finance-summary` cuentan lo mismo con un gasto
repartido).

## 33. Vuelta 31: Créditos y Espacios se consultan, el cuadre dice una sola cosa y Ajustes se ordena

Sin componentes nuevos en `ds/` (solo el tag `TAG_ICONO_EN_CIRCULO` de `IconoEnCirculo`, para las pruebas):

- **Tocar una deuda siempre abre su detalle** (`FilaDeDeuda`), también sin términos («Nu», «Vehículo
  4083»): antes abría el formulario y esa deuda no se podía consultar. Si faltan datos, el detalle lo
  dice **arriba, antes del saldo**, con una `TarjetaDePropuesta` en `TonoDeIcono.Aviso` y «Completar
  datos» (`AvisoDeFaltanDatos` en `ui/credits/LaDeudaEnSuDetalle.kt`), que abre el formulario de
  siempre; «El préstamo / La tarjeta» no se pinta vacío. El campo **Banco llega lleno** cuando el
  nombre es una marca del catálogo (`bancoSugeridoPara`: solo bancos y billeteras, nunca una red como
  Visa, Mastercard o AMEX); escribirlo a mano manda.
- **Cada gasto de un espacio es una `Fila` tocable.** Con movimiento propio abre el MISMO detalle de
  Movimientos (`HojaDelMovimiento`); sin él («Pagó Caro») abre `HojaDelGasto` (`ui/espacios/DetalleDelGasto.kt`):
  quién pagó, cuánto, tu parte, la categoría y la fecha en `Fila`s sobre capa 2, y «Quitar del espacio»
  en `Peligro` con su pregunta antes. El cliente ganó `quitarGastoDelEspacio`, sobre la ruta
  `DELETE /api/espacios/{id}/gastos/{gastoId}` que el server ya tenía (sin cambios de server). El
  subtítulo **empieza por la fecha** («21 oct · Pagó Caro · Mercado»), no repite la categoría si ya es
  el título, y va en dos renglones antes de cortarse.
- **Una sola frase del cuadre de una cuenta**: `fraseDelCuadre` (`:core` `CuadreDeSaldos.kt`) la decide
  y **nunca pierde la fecha** si hubo un cuadre: «Sin movimientos todavía», «Nunca la has cuadrado»,
  «Cuadrada el 3 oct» (o «hoy», «ayer») hasta 45 días, y después «Hace más de un mes que no la cuadras ·
  última el 3 ago» (con el año si es de otro). La dicen el detalle de la cuenta y Cuadre de saldos
  (`textoDelUltimoCuadre`); Patrimonio, Hoy y Movi AI dicen su versión con nombre y la misma regla, más
  corta (`textoDelAvisoDeCuadre`: «Bancolombia Ahorros · Nunca la has cuadrado», «Bancolombia Ahorros ·
  última el 3 ago»). Donde un largo no cabe, baja a dos renglones antes que cortarse.
- **Ajustes, más corto y con íconos**: cada fila lleva su `IconoEnCirculo` neutro (`IconoDeAjuste`;
  «Borrar mi cuenta» sigue en peligro). El tema es **una fila «Tema» con su valor** que abre
  `HojaDelTema` con las tres opciones. En Avisos hay **un solo «Activar avisos»** (en la fila de las
  notificaciones; la de los recordatorios ya no repite su «Activar»). «Inicio del mes» pasó a
  **«Inicio del período»** y su hoja pregunta «¿Qué día empieza tu período?». Donde no se leen SMS (la
  web, iOS), «Tu plata» trae **«Recibe tus alertas por correo»** con la dirección a la vista; abre una
  hoja con la MISMA pieza de la guía de primer uso (`ReenvioPorCorreo` en `ui/primeruso/`). **«Personas
  y comercios» quedó solo en Espacios**: es de esa pestaña (`navTabFor` ya la marcaba) y ahí se elige
  con quién compartir; en Ajustes era una segunda puerta a lo mismo.

**Las líneas base** no cambian: `dp_sueltos` 119, `radios_con_numero` 3, y las guardas en 0 siguen en 0.
Lo vigilan `LaDeudaEnSuDetalleTest`, `LaListaDeCreditosEsDeFilasTest`, `EspaciosEnPantallaTest`,
`LosGastosDelEspacioSeLeenTest` (360 dp, motor de texto real), `ElCuadreDiceLoMismoTest`,
`MasScreenTest` y `LosAvisosApagadosSeVenTest`.

## 34. Vuelta de diseño 32: las cifras hablan igual en toda la pantalla

Revisado en la vista previa de las vueltas 30 y 31 (3995d638) con la cuenta demo, a 390 px. Sin
componentes nuevos en `ds/`: una forma más de `Cifra` (`FormaDeCifra.Abreviada`) y su regla.

- **El total del día dice lo mismo que sus filas** (`totalDelDiaComoSusFilas`, `ui/accounts/ElTotalDelDia.kt`).
  En el detalle de la tarjeta «Nu» el día decía «+$42.000» y su fila «−$42.000»: el total salía de
  `accountDayTotal` (`:core`, ya borrada), con la convención de la deuda —una compra la sube—, y la fila
  pinta el signo del gasto. Ahora, en **toda** cuenta, el total es la suma de sus filas con la
  convención de Movimientos: lo que entró suma, todo lo demás resta. Se pinta como el de Movimientos:
  `Cifra` `Chica`, `Ingreso` si es positivo. En una cuenta de dinero da lo mismo que antes. El saldo no
  cambia: sigue saliendo del server con `signedDelta`.
- **Una franja, una forma** (ver `FormaDeCifra.Abreviada` en §6): «Entró +$850.000 · Salió $1,8M · Neto
  −$902.250» pasó a ser enteras las tres si caben, y abreviadas las tres si una no cabe. Lo mismo en el
  panel de escritorio, donde antes cada cifra se achicaba por su cuenta.
- **Lo que el banco manda en MAYÚSCULAS se lee en forma de título** (`enFormaDeTitulo`, `:core`
  `NombreParaMostrar.kt`): «EXITO SUBA» → «Éxito Suba» (las tildes y mayúsculas salen del catálogo de
  `MarcasConocidas`, palabra por palabra), «PAGO EPM» → «Pago EPM», «DIAN» → «DIAN». Respeta lo que trae
  minúsculas, las siglas de 2 a 4 letras (sin vocales, del catálogo o conocidas) y los números; a
  diferencia de `nombreParaMostrar` no quita nada. **Solo presentación**: lo guardado no cambia. La usan
  la fila de un movimiento (`textosDeLaFila`, también en el detalle de una cuenta), su detalle
  (`descripcionParaMostrar`), Buscar y el título de un gasto de un espacio.
- **En Espacios el subtítulo de un gasto cabe en un renglón** a 360 dp: «26 oct · Caro · Mercado» (antes
  «Pagó Caro» / «Pagaste tú»; el avatar ya dice quién pagó), sin la categoría cuando el título ya la
  dice por palabras («Mercado del sábado»), y de un renglón: si aun así no cabe, la `Fila` corta la
  categoría con «…» al final, nunca la fecha.
- **La tarjeta también tiene «Qué puedes hacer»**, con o sin sus datos: «Registrar un pago» abre
  «Agregar» en la pestaña del pago con la tarjeta en «Hacia» (`Screen.QuickAdd(presetDeudaId)`, la misma
  hoja de «Anotar este pago»), y con datos, «Editar las condiciones». **«Ajustar saldo» no**: para una
  tarjeta no existe en la app ni en el server (las dos rutas de ajuste la rechazan), y la vuelta no crea
  flujos nuevos.

**Las líneas base** no cambian: `dp_sueltos` 119, `radios_con_numero` 3, y las guardas en 0 siguen en 0.
Lo vigilan `ElTotalDelDiaTest`, `LasCifrasDeUnRenglonHablanIgualTest`, `LaFilaDelMovimientoTest`,
`PedirleACaroTest` y `NombreParaMostrarTest` (puras), y `LaTarjetaEnSuDetalleTest`,
`LaFranjaHablaEnUnaFormaTest` y `LosGastosDelEspacioSeLeenTest` (Robolectric; las dos últimas a 360 dp con
el motor de texto real).

## 35. Vuelta 33: la tarjeta se cuadra, y la pantalla ancha se aprovecha

Revisado en la vista previa de la vuelta 32 (2208acaf) con la cuenta demo, a 390 y a 1.280 px. Sin
componentes nuevos en `ds/`: un token, `Medida.anchoMaximoDeBoton` (360 dp).

- **Una tarjeta se cuadra contra el banco.** No había dónde: `POST /api/credits/{id}/balance-adjustment`
  solo acepta préstamos y `POST /api/accounts/{id}/balance-adjustment` rechazaba toda deuda. Ahora la
  de **cuentas** acepta `CREDIT_CARD` (es la que menos duplica: misma respuesta `AdjustAccountBalanceResponse`
  que el teléfono ya espeja, el saldo en la moneda de la cuenta —una tarjeta en dólares también— y el
  mismo `balanceAdjustmentEventFor`, que pone el signo de la deuda: subir lo que se debe es un cargo).
  Un préstamo sigue yendo a Créditos («El saldo de un préstamo se cuadra en Créditos»). Sin columnas
  nuevas. En la app, el detalle de la tarjeta suma «Ajustar saldo» a «Qué puedes hacer» (con o sin sus
  datos), con la MISMA hoja que el préstamo (`CardBalanceSheet`, que comparte el cuerpo con
  `CreditBalanceSheet`): «Cuánto debes hoy según el banco» y la ayuda «la deuda total de hoy, no el pago
  mínimo ni el cupo disponible». Movi calcula la diferencia contra el saldo sin anulados; nunca se ajusta
  solo. Patrimonio lee el saldo nuevo (sale de los movimientos), el ajuste no cuenta como gasto
  (`ADJUSTMENT_CATEGORY`), y Cuadre de saldos sigue siendo de Dinero e Inversión (`sePuedeCuadrar` no
  cambió: una tarjeta no se «atrasa» en el aviso de cuadre).
- **Movi AI en el rail es una raíz.** Tocada en el rail deja la pila como cualquier pestaña (`[Hoy, Movi
  AI]`, `NavStack.navegarALaPestana`) y su encabezado lleva el avatar (`moviAiEsLaRaizDeSuPestana`). En
  el teléfono —sin pestaña de Movi AI— y con el rail apilada encima de Ajustes, conserva la «←».
- **Espacios, el detalle de un espacio y el de una cuenta son tableros** (`disposicionDe`). Con un
  espacio, a la izquierda el espacio con su total, «Quién puso qué» y el saldo con «Pedirle a Caro»; a la
  derecha «Gastos» con «Anotar lo que pagó Caro», «Lo que paga otra persona» y «Personas y comercios»
  (`espacioEnDosColumnas`). En el detalle de una cuenta, la cuenta (saldo, deuda, para qué es, apartados,
  «Eliminar cuenta») a la izquierda y sus movimientos a la derecha (`cuentaEnDosColumnas`). Las cuentas
  del ancho son las de Patrimonio: columnas de 420 dp, cada una con su scroll. Con varios espacios o sin
  ninguno, una columna de lectura centrada. Fuera del teléfono, los botones de ancho completo de Espacios
  no pasan de `Medida.anchoMaximoDeBoton`; en el teléfono no cambia nada.
- **Créditos no se tocó**: desde la Ola W3 ya va en dos columnas a 1.280 (con dos deudas o más).

**Las líneas base** no cambian: `dp_sueltos` 119, `radios_con_numero` 3, y las guardas en 0 siguen en 0.
Lo vigilan, en el server, los casos de tarjeta de `AccountRoutesTest` (de $1.499.000 a $1.520.000, hacia
abajo, sin ensuciar el período, un ajuste anulado no cuenta, la de otro usuario es 404, el préstamo manda
a Créditos); `MoviAiComoRaizTest` (pura); y `LaTarjetaEnSuDetalleTest`, `ElEncabezadoDeMoviAiTest`,
`EspaciosEnDosColumnasTest` y `LaCuentaEnDosColumnasTest` (Robolectric; las dos últimas dentro de la
cáscara, a 1.280 y a 390 dp).

## 36. Vuelta 34: Agregar con teclado de verdad

Revisado en la vista previa de la vuelta 33 (aa82588a) con la cuenta demo, a 1.280 y a 390 px. Sin
componentes nuevos en `ds/`; dos tokens: `Medida.anchoDelCursor` (2 dp) y
`Movimiento.PARPADEO_DEL_CURSOR_MS` (530, lo único que se repite: no es una transición).

- **Frente a un computador, Agregar no dibuja el teclado numérico.** A 1.280 px la hoja medía
  600 × 720 y casi la mitad eran las teclas 1–9, 000, 0 y ⌫, con un teclado físico al lado. **La señal
  es el puntero principal** (`LocalPunteroFino`, `platform/PunteroPrincipal.kt`): en la web,
  `matchMedia("(pointer: fine)")` —un mouse o un trackpad—; en Android e iOS, siempre `false`. No es la
  clase de ancho: una tablet es mediana o expandida y se toca (ahí el teclado se queda), y una ventana
  de escritorio angosta es compacta y tiene teclado. Tampoco `LocalHayTecladoFisico`, que es
  `!isAndroid` y vale también en un iPhone. Sin teclado en pantalla:
  - el monto se escribe con el teclado físico por el camino que ya existía (`alTeclear` →
    `accionDeTecla` → el mismo `onKey` de los botones): los dígitos suman, Retroceso borra, Enter
    guarda si se puede, ⇧Enter guarda y deja anotar otro; Escape lo sigue atendiendo el marco;
  - al abrir, el foco ya es de la hoja (el velo de la centrada, el panel de la de abajo), así que la
    primera tecla va al monto sin hacer clic;
  - la cifra sigue siendo la protagonista, con un **cursor** de la marca pegado a su derecha, del alto
    de la letra de la cifra, que parpadea (fijo si el sistema pide menos movimiento);
  - la hoja se acorta a lo que tiene (~212 dp: cuatro filas de 50 y su respiro) y va del ancho de las
    demás hojas (560): los 600 eran para que el teclado respirara;
  - el renglón del error / «Anotado» / aviso pendiente, entre la tarjeta y el pie, no reserva sus 32 dp
    ni su respiro (estaban para que el teclado no se moviera bajo el dedo): mide lo que diga, y nada si
    no dice nada. Sin eso quedaban ~50 dp vacíos entre «Nota» y «Guardar movimiento».
  En el teléfono y en una tablet que se toca no cambia nada.
- **La categoría se lee en un solo lugar.** Debajo del monto había una fila «Categoría · Restaurantes ›»
  y, justo debajo, los chips con «Restaurantes» marcado. La fila se fue: queda un renglón
  **«Categoría ……… Todas ›»** encima de los chips —el rótulo en `cuerpo`, la letra de los títulos de
  «Cuenta» y «Nota»; «Todas ›» a la derecha, como el «Ver todo» de una `Seccion` (en la marca) con el
  `KeyboardArrowRight` de lo que se abre, y un toque de 48—, que abre el selector entero, sin teclado,
  como lo abría la fila. «Todas» iba primero al final de los chips, pero ahí quedaba fuera de la
  pantalla a 390 y a 1.280, y desplazar de lado con el mouse es incómodo: en el renglón del rótulo se
  ve siempre. La elegida va marcada; si no está entre
  las frecuentes va primera (lo hacía ya `categoriasParaLasPastillasDeAgregar`), y sin datos de uso es
  la única pastilla (`pastillasDeLaCategoria`): sin eso quien recién empieza no la vería en ningún
  lado. Si la elegida queda fuera del desplazamiento de la fila, la fila se corre lo justo para
  mostrarla, solo en horizontal (un `bringIntoView` también correría la hoja). «Movi la reconoce: …» va
  en el mismo renglón, entre el rótulo y «Todas ›», y es lo único que cede (se corta con «…»): antes era
  un renglón propio que se iba al elegir otra a mano y movía el teclado.
- **A 360 dp** la tarjeta mide lo de antes menos un separador (el renglón del rótulo mide 48, lo de la
  fila que reemplaza), nada se corta, y elegir un chip, una categoría en el selector u otra cuenta no mueve el teclado.

**Las líneas base** no cambian: `dp_sueltos` 119, `radios_con_numero` 3, y las guardas en 0 siguen en 0.
Lo vigilan `AgregarConElTecladoDeVerdadTest` (a 1.280 sin teclas ni «⌫» y con el cursor a la derecha
de la cifra; 4-2-0-0-0 deja $42.000, Retroceso deja $4.200 y Enter lo guarda; la hoja ≥ 200 dp más
corta; de la tarjeta a «Guardar» no más de 16 dp, y con teclado el renglón sigue en 32; a 1.280 con
tacto y a 390 el teclado sigue y escribe) y `UnaSolaCategoriaEnAgregarTest` (a 360:
la elegida una sola vez y marcada, «Categoría» no es una `Fila`, «Todas» entero y sin desplazar en el
renglón del rótulo —con seis frecuentes que desbordan los chips—, de 48 de toque, abre el selector, la
elegida en el selector vuelve primera y entera, y elegir chip, categoría o cuenta no mueve el teclado
ni corta «Guardar movimiento»), más un caso en `HojaAgregarSugerenciaDeCategoriaTest` (la sugerencia
aparece y se va sin mover el teclado, y va antes de «Todas ›», que no se corta). Las pruebas que abrían el selector tocando la fila ahora tocan
«Todas».

## 37. Vuelta 35: Buscar y Categorías se ven como el resto

Revisado en la vista previa de la vuelta 34 (d567ba4c) con la cuenta demo, a 390 px. Un componente
nuevo en `ds/`, `CampoDeBusqueda`; ningún token nuevo. **Solo presentación**: sin cambios de plata ni
de server.

- **Los resultados de Buscar van en su superficie.** «Movimientos · 2» y «Comercios · 1» eran filas
  sueltas sobre el fondo. Ahora cada grupo —movimientos, comercios, personas y comercios, categorías,
  cuentas, pantallas y las recientes— es su `Seccion` y, debajo, una `Superficie` de lista con el
  `Separador` entre filas, como en Movimientos, Ajustes y Patrimonio. Las filas no cambiaron: la
  `Fila` con el avatar del comercio, título, subtítulo y la `Cifra` con su signo (`CifraDelMovimiento`).
  Un grupo es un solo `item` de la lista perezosa (son pocas filas: 30 movimientos, 8 por grupo), así
  la superficie no se parte.
- **Una sola caja de búsqueda** (`CampoDeBusqueda`, §6). La de Buscar era blanca, con lupa y ×; la de
  Categorías, el `Campo` gris de los formularios, sin lupa ni ×; el selector de categoría y Personas y
  comercios armaban la suya. Ahora la usan Buscar, Categorías (la lista y «Unificar en…»), el selector
  de categoría —Agregar, recategorizar, Presupuestos, Recurrentes y, por ser el mismo componente,
  Reconciliar— y Personas y comercios. Buscar salió de las excepciones de `LosCamposSonDelSistemaTest`,
  y el `18.dp` de la lupa del selector se fue (`dp_sueltos` 119 → 118).
- **En Categorías, las cifras dicen de qué son.** La fila decía «Carro · 3 movimientos · $260.000»
  sin decir el alcance, y **medía dos cosas**: el conteo era de toda la historia (`movements` + los de
  otra moneda) y la cifra, del período en curso (`monthTotal` / `monthIncomeTotal`, la regla de «En qué
  se va», §21). Ahora los dos son del período —la regla que esta pantalla ya usaba para la plata—: el
  renglón es `monthMovements` (`resumenDelPeriodoCorto`; «Sin movimientos tuyos» si solo hay tu parte
  de lo que pagó otra persona), y la lista lleva arriba la `Seccion` «Este período» —«Este mes» si el
  período es el mes del calendario, el mismo `rotuloDelPeriodoDeCategorias` de la hoja de detalle—,
  también mientras carga, para que la lista no baje. Lo de toda la historia sigue en la hoja de detalle
  (`resumenDeUso`). El orden de la lista no cambió (alfabético).

**Las líneas base:** `dp_sueltos` 118, `radios_con_numero` 3, y las guardas en 0 siguen en 0. Lo
vigilan `LosResultadosDeBuscarVanEnSuSuperficieTest` (ningún resultado ni reciente fuera de la
superficie de su grupo, una por grupo, el título encima), `UnSoloCampoDeBusquedaTest` (la × solo con
texto, borra y deja el foco, la caja mide 48 con la × o sin ella; Buscar, Categorías, el selector y
Personas y comercios escriben dentro de la caja del sistema y la × borra) y
`CategoriasDicenDeQueSonLasCifrasTest` (el título «Este período» / «Este mes» encima de las filas, y el
conteo de la fila del período: 3, no 40), más los casos de `resumenDelPeriodoCorto` en
`CategoriasLogicTest`.

## 38. Vuelta 36: el detalle dice lo mismo que la fila, y ningún rótulo se parte

Revisado en la vista previa de la vuelta 35 (fc2eeb17) con la cuenta demo, a 390 px. Ningún componente
ni token nuevo. **Solo presentación**: sin cambios de plata ni de server.

- **El detalle de un movimiento se titula como su fila.** En Movimientos la fila decía «Éxito Suba»
  («Mercado · Bancolombia Ahorros» debajo) y, al tocarla, la hoja se titulaba «Mercado»: la fila sabía
  (vuelta 30) que una descripción igual a la categoría no dice nada; el detalle no. Ahora los dos salen
  de `textosDeLaFila` por `tituloDelMovimiento(evento, cuenta)` (`ui/transactions/LaFilaDelMovimiento.kt`),
  con el nombre de la cuenta igual que la fila. Lo usan también la rama del traspaso y la del saldo
  inicial de la misma hoja, la lista de «¿Y los parecidos?», los pagos de tarjeta sin marcar, la
  confirmación de anular (`VoidEventSheet`), los movimientos de una persona o comercio, los de un
  presupuesto, «Los más grandes» del detalle de un período, el importe de un extracto, «ya anotado» al
  leer un papel y el aviso de lo que no se pudo subir. La propuesta «¿Fue este?» de un recurrente
  (`descripcionPropuesta`) tampoco repite la categoría: con una nota igual a ella va el comercio, o nada.
  **Al editar la nota, el campo sigue trayendo lo guardado** (`description`), no el título.
- **Los rótulos de la cuadrícula de íconos no se parten dentro de una palabra.** A 390 dp entran
  cuatro columnas y «Medicamentos» salía «Medicament» / «os». La celda más angosta de un teléfono no
  es la de 360 dp (tres columnas de ~106) sino la de **364 dp**, donde empiezan las cuatro de 80 dp
  justos (`ANCHO_MINIMO_DE_CELDA`) y el rótulo tiene 72: ninguna palabra puede ser más ancha que eso.
  Se puede partir entre palabras («Arreglos del hogar», «Banco y comisiones»), en dos renglones como
  mucho. Cinco rótulos cambiaron por uno más corto del mismo significado —la letra no se achicó—:
  «Restaurante» → «Comer fuera», «Medicamentos» → «Medicinas», «Entretenimiento» → «Diversión»,
  «Suscripciones» → «Membresía», «Transferencia» → «Traspaso». **Las claves no cambiaron**
  (`restaurante`, `medicamentos`, `entretenimiento`, `suscripciones`, `transferencia`): lo guardado en
  `category_prefs.icono` se sigue viendo igual. La regla está en el KDoc de `IconoDelCatalogo.rotulo`.

**Las líneas base:** `dp_sueltos` 118, `radios_con_numero` 3, y las guardas en 0 siguen en 0. Lo
vigilan `ElDetalleSeTitulaComoLaFilaTest` (Mercado con «EXITO SUBA» titula su detalle «Éxito Suba»,
«Mercado» queda solo en el chip, el campo de la nota trae «Mercado», una descripción propia sigue siendo
el título) y `LosRotulosDeIconosNoSePartenTest` (con la hoja de una categoría abierta de verdad, el
motor de texto real —`NATIVE`, sdk 34— y la escala de letra de la app, ningún renglón termina entre dos
letras ni con «…», a 360, 364 y 390 dp; y las cinco claves siguen siendo las de antes), más el caso
nuevo de `descripcionPropuesta` en `OccurrenceLogicTest`.

## 39. Vuelta 37: Cuadre de saldos se lee de una pasada, y «Entre cuentas» es un modo claro

Revisado en la vista previa de la vuelta 36 (0b84be13) con la cuenta demo, a 390 px. Ningún componente
ni token nuevo. Sin cambios de server ni de reglas de plata: el ajuste de una tarjeta ya existía
(vuelta 33).

- **Cuadre de saldos tiene menos texto que formulario.** Arriba había un párrafo de seis renglones en
  su superficie, el encabezado repetía «Compara con lo que dice tu banco hoy», y cada cuenta repetía
  «Escribe el saldo que te muestra el banco hoy.» y cerraba con un rótulo «Dinero» suelto. Ahora:
  - la entrada es una frase sin superficie, «Escribe lo que dice tu banco y Movi anota la diferencia.»
    (`QUE_ES_EL_CUADRE`), y el encabezado va sin subtítulo;
  - lo que el párrafo explicaba va en una nota debajo del botón (`NOTA_DEL_CUADRE`): que el ajuste
    queda como un movimiento visible, que no cuenta como gasto ni ingreso, y que lo que quede en
    blanco no se toca;
  - las cuentas van bajo su `Seccion` —«Dinero», «Inversión», «Tarjetas», en ese orden, sin secciones
    vacías (`seccionesDelCuadre`)— y el rótulo suelto se fue. Con avisos que traen el saldo, «Lo que
    dijo el banco» va primero y las secciones siguen; el «O escribe tú el saldo» de entre medio salió
    (eran dos títulos seguidos);
  - el rótulo del campo basta: `textoDelCuadre` es `null` con el campo vacío y solo habla cuando hay
    una cifra («Movi dice … tú dices …: se va a anotar …»);
  - cada cuenta sigue con «Movi dice» y su estado («Nunca la has cuadrado», «Cuadrada el …»). El
    esqueleto perdió las dos líneas de abajo y ganó el título de la sección, para que nada salte.
- **Las tarjetas también se cuadran ahí.** La sección «Tarjetas» lista las `CREDIT_CARD`: el campo
  dice «Cuánto debes según el banco», «Movi dice» muestra la deuda, y el texto dice **cargo** o
  **abono** como la hoja «Ajustar saldo» de la tarjeta. Al anotar va por la misma ruta que
  `CardBalanceSheet` (`adjustAccountBalance`, `POST /api/accounts/{id}/balance-adjustment`). Lo decide
  un predicado propio de la pantalla, `seccionDelCuadre`; **`sePuedeCuadrar` de `:core` no cambió**: el
  server, Movi AI y los avisos de «sin cuadrar» siguen sin tarjetas, así que una tarjeta nunca sale en
  ámbar ni se reclama como atrasada. Los préstamos siguen fuera (se cuadran en Créditos).
- **«Entre cuentas» es un modo claro.** Desde Patrimonio → Herramientas → «Movimientos entre
  cuentas», Movimientos abría sin ningún chip marcado, con la franja Entró · Salió · Neto de todo el
  período, la tarjeta «N por revisar» y un encabezado «Entre cuentas · Ver todos». Ahora el modo es un
  chip marcado **«Entre cuentas ×»** (`TAG_CHIP_DEL_MODO`), el patrón del «Solo …» de la ola 4: lo
  dice y lo quita (vuelve a «Todo»). El encabezado con «Ver todos» se fue. En el modo no van la franja
  ni «por revisar», ni el resumen del período en el panel de pantalla ancha (`loDeArribaEnElModo`).
  **Se eligió ocultar la franja** y no cambiarla por «Movido entre cuentas $X»: lo que se mueve entre
  cuentas propias no es plata que entró ni que salió, y sumar patas —con tarjetas en dólares y cuotas
  que bajan una deuda— sería una cifra nueva sin regla de plata detrás.

**Las líneas base** no cambian: `dp_sueltos` 118, `radios_con_numero` 3, y las guardas en 0 siguen en 0.
Lo vigilan `CuadreDeSaldosScreenTest` (cada cuenta bajo su sección, «Dinero» una sola vez, ninguna ayuda
repetida, la frase y la nota una vez, «Movi dice» y el estado en cada cuenta; la tarjeta pide la deuda,
dice el cargo y manda 2.100.000 por `adjustAccountBalance`; el préstamo no aparece) y
`EntreCuentasEsUnModoClaroTest` (en el modo, el chip marcado con «Salir de Entre cuentas», sin franja,
sin «por revisar» y sin «Ver todos»; la × vuelve a «Todo» con las dos), más los casos puros de
`CuadreDeSaldosLogicTest` (secciones, rótulos, cargo/abono, `sePuedeCuadrar` sin tarjetas) y de
`EntreCuentasFueraDeLosChipsTest`.

## 40. Vuelta de diseño 28: una cifra que se explica sola, y Hoy y Plan sin ruido

Un segundo diseñador revisó la app sin sesgo (27-oct): «la plata disponible sale con cinco cifras
distintas y una contradice a otra». Cada una era correcta para su concepto, pero no se distinguían. Sin
reglas de plata nuevas: la cascada y los rótulos usan las funciones de siempre.

- **Tocar el héroe explica la cifra en la misma pantalla.** Con «Te quedan», el héroe abre la hoja «¿De
  dónde sale?» (`HojaDeDondeSale`, `ui/dashboard/HojaDeDondeSale.kt`) con la cascada de
  `DisponibleDelPeriodo` (`cascadaDelDisponible`, pura): una `Fila` por renglón con su `Cifra` y su signo
  —«Tenías el 25», «+ Lo que entró», «+ Por recibir», «− Guardaste en un ahorro», «− Apartaste», «− Otros
  pagos de deuda», «− Fijos del período» (pagados y por pagar), «− Tu ahorro del período»—, un `Separador`
  y **«Disponible del período»** (el nombre que Plan le da a esa cifra), «− Gastaste» (el día a día, sin
  los fijos), otro `Separador` y el total con el rótulo del héroe («Te quedan» / «Te pasaste por»). Sin
  margen, un renglón apagado «Sin margen, Movi cuenta desde $0» hace que la suma dé lo del héroe (la meta
  del período nunca es negativa). Abajo, «Ver tus períodos» (`Boton` `Fantasma` `S`), lo que antes abría
  el toque. **Un solo componente para los dos lugares**: `CascadaDelDisponibleEnPantalla`, una
  `Superficie` en la capa 2 con relleno de lista, que también abre «¿De dónde sale?» en la tarjeta del
  Disponible (Plan y Hoy): eran dos líneas de texto que no sumaban a la vista (`desgloseDelDisponible`,
  borrada). Sin «Te quedan» (el héroe dice «Tu plata»), el toque sigue llevando a «Tus períodos».
- **Un nombre por concepto.**

  | dónde | antes | ahora |
  |---|---|---|
  | Hoy (héroe) y la frase de Plan | «Te quedan» | igual: lo libre para gastar hasta el cierre |
  | Plan, la tarjeta | «Disponible del período» | igual: lo que había para gastar en todo el período |
  | Plan (y Hoy), la columna de hoy | «Hoy» | «Meta de hoy» (`ROTULO_META_DE_HOY`) |
  | Tu plata, día a día · el día más justo | «El 24 de octubre llegas a $11,9M» | «El 24 de octubre tu saldo proyectado es $11,9M» |
  | Tu plata, día a día · el renglón de abajo | «Al cierre: $11,9M» | «Saldo proyectado: $11,9M» (el lector oye «… al cierre, el 24 oct») |
  | Tu plata, día a día · el supuesto | «Suponiendo que gastas como siempre, unos $26.523 por día (tu promedio …)» | «Supone que sigues como siempre: gastas en promedio $26.523 por día (en tus últimos 3 períodos).» |
  | Tus períodos | «Te quedó» (en `sale` si es negativo) | «Neto del período» (`ROTULO_NETO_DEL_PERIODO`), neutro si es negativo: no es una alerta |

  El tono del neto es UNO para el mismo concepto: «Neto» de la franja de Movimientos (vuelta 30) y «Neto
  del período» de Tus períodos salen de `tonoDelNeto` (en `entra` si quedó, en el color del texto si
  salió más, apagado en cero); la franja lo pintaba en `sale`. La única cifra «por día» sin otro nombre es la del héroe y la frase de Plan (`porDiaParaLoQueQueda`).
  «Saldo proyectado al cierre» no entra a 360 dp entre las dos fechas de la caja (lo mide
  `UnNombrePorConceptoTest`): el cierre lo dice la fecha de la derecha.
- **El widget «Falta por pagar» no esconde la deuda grande.** La vuelta 22 sacaba el pago de «Movi
  propone hoy», y el Arriendo de $1,8M vencido hace 22 días quedaba en «y 1 más» mientras se veían Carro
  $9.000 e Internet $3.000. Ahora lista lo más urgente primero (el orden de la cola: lo vencido, el monto
  más grande), aunque también esté en la propuesta, con su «Vencido hace 22 días» en `aviso`
  (`filasDelWidgetDeFalta` ya no recibe `conPropuestas`; `EL_PAGO_ESTA_ARRIBA` se fue).
- **Lo pendiente se avisa una vez.** La tarjeta «N por revisar» sale de «Movi propone hoy» (era un
  contador, no una decisión; `TipoDePropuesta.AVISOS` se fue): lo cuenta la insignia de «Avisos», siempre en
  la cuadrícula. La campana sigue siendo la bandeja de todo, pero su punto rojo se prende solo con algo
  vencido o urgente (`campanaConPunto`, `NotificationRow.urgente`: un pago vencido o que vence hoy, lo
  que el banco cobró solo, un banco que se calló).
- **Plan: los recordatorios van debajo de los pagos.** «Tus recordatorios no te van a llegar» es una
  `Fila` compacta (`FilaDeRecordatoriosSinCanal`): el ícono en su círculo y el subtítulo en `aviso`, toda
  la fila activa las notificaciones (o lleva a Ajustes si el navegador las bloqueó), y «Ahora no»
  (`Fantasma` `S`) la descarta hasta el período que viene (`PropuestasDescartadasStore`, clave
  `recordatorios-sin-canal:<período>`; se limpia al cerrar sesión). Va dentro de «Pagos del período»,
  debajo del resumen (`SeccionChecklistDelPeriodo(debajoDelResumen = …)`). Y en ese resumen una cifra en
  cero va apagada (`tonoDelResumen`): «Ya pagaste $0» ya no pesa lo mismo que lo que falta.

- **La tarjeta de una anomalía no deja «). ·» pegados.** El detalle viene con su punto final y el «y N
  avisos más» se le pegaba después de « · »; `cuerpoDeLaAnomalia` saca el punto antes de unir.

**Las líneas base** no cambian: `dp_sueltos` 118 (lo que dejó la vuelta 34), `radios_con_numero` 3, y las
guardas en 0 siguen en 0.

Lo vigilan `CascadaDelDisponibleTest` (la suma da la cifra del héroe al peso: sin y con ahorro, con un
apartado, lo guardado y otros pagos de deuda, con fijos pendientes, sin margen y con un server viejo),
`TeQuedanEnHoyEnPantallaTest` (tocar el héroe abre la hoja, su total es la cifra del héroe y «Ver tus
períodos» lleva allá), `DisponibleEnInicioTest` y `AhorroDelPeriodoEnHoyYPlanTest` (la cascada en la
tarjeta), `UnNombrePorConceptoTest`, `CajaDelPeriodoTest` y `ElNetoDelPeriodoTest` y `ResumenDelPanelTest` (los rótulos y el tono del neto),
`UnaSolaVerdadEnHoyTest` y `ChecklistEnInicioTest` (el Arriendo primero en el widget),
`LoPendienteSeAvisaUnaVezTest` (con 3 por revisar y nada vencido, ni tarjeta ni punto) y
`LosRecordatoriosVanDebajoDeLosPagosTest` (la fila debajo del resumen, «Ahora no» por período, el $0
apagado).

## 41. Vuelta 29: la opción 3 «Acción primero» es el tema de toda la app

El 28-oct el dueño volvió a la opción 3 del lienzo «Movi · 5 caminos» («me encantó a mí y a mi esposa
mucho más que 1, 2 y 4»). Esta vuelta cambia **la piel, no el orden**: tokens, letra, formas, sombras
y los componentes de `ds/`. La navegación de 4 pestañas y las 8 acciones de Hoy van en la vuelta 30.

- **Paleta** (§3, §4): crema, blanco, tinta, amarillo de acción, azul de enlace. Tres valores los puso
  la prueba: `entra` `#147849`, `sale` `#B3261E`, `textoApagado` `#6E665B`.
- **La marca es relleno**: cada `Movi.colores.marca` usado como letra, ícono, borde o línea pasó a
  `enlace` (70 lugares en 34 archivos fuera de `ds/`), y los `lavado(marca)` de algo elegido a `lavado(enlace)`.
  Los rellenos amarillos llevan la tinta encima (`sobreMarca`): tres hojas de Ajustes ponían `fondo`
  como letra sobre la marca y no se habría leído.
- **Letra** (§5): Figtree 400–800, también las cifras, tabulares.
- **Formas** (§2): `Radio.l` 18, `Radio.xl` 22, botones en píldora, la fila de dos renglones a 58, el
  filtro a 44 (36, `AltoDeFiltro.Compacto`, en los chips de Agregar: la hoja está anclada abajo y con 44
  el pie se salía a 360 × 740). El encabezado sigue en 56.
- **Sombra** (§3): solo en la superficie destacada y la propuesta.
- **Componentes**: `Boton`, `Pildora`, `PildoraDeFiltro`, `PildoraDeAccion`, `AccionRapida`, `Fila`
  (4 arriba y abajo), `IconoEnCirculo`/`AvatarDePersona` (la marca: círculo amarillo con la tinta),
  `Casilla` (marcada en tinta), `BarraDeProgreso` (`Marca` en tinta), `Campo` (foco en el enlace),
  `BotonDeIcono` `Secundario` (círculo blanco con borde), `Seccion` (la acción en el enlace),
  `TarjetaDeCuenta` (sin marca, blanca). La barra de pestañas: blanca con el borde de arriba y el ícono
  activo sobre la píldora amarilla de 56 × 30, rótulos 12/700 y 12/500 en el texto medio; el rail igual.
- **Solo claro**: sin `COLORES_OSCUROS`, `CATEGORIAS_OSCURAS`, `TemaStore`, `PreferenciaDeTema`,
  `HojaDelTema` ni el parámetro `oscuro` de `MoviTheme` y de `AjustarBarrasDelSistema`; las barras del
  sistema con íconos oscuros; el arranque de Android en el crema (`movi_fondo`, `Theme.Material.Light`);
  el widget, el ingreso web y la página compartida, claros; los atajos del ícono, amarillos.
- **iOS**: `assembleIosX64MainResources` arma los cinco `figtree_*.ttf`, y `linkDebugFrameworkIosX64`
  enlaza, pero el `ComposeApp.framework` no los lleva adentro y el proyecto de Xcode no los copia al
  paquete de la app. Sin ese paso (y sin simulador en esta vuelta para verlo), iOS sigue con la letra
  del sistema (`laPlataformaTraeLasFuentes = false`): mismos tamaños, pesos y cifras tabulares.
- **Lo que queda para las vueltas siguientes**: el héroe de Hoy todavía no es una tarjeta (cuando lo
  sea, `Superficie(destacada = true)`), la franja amarilla y los botones `marcaSuave` del encabezado,
  las 4 pestañas y las 8 acciones (vuelta 30).

**La revisión de la vuelta 29 (a 390, 360 y 1280 contra las maquetas p3), y lo que cambió:**

1. **Un solo «seleccionado»: la tinta.** El segmentado pasó del amarillo a la tinta con la letra blanca;
   los selectores armados en pantallas (el día de un recurrente, la moneda de una tarjeta, la clase de un
   bien, el día del período y del aviso, la acción abierta de un movimiento, la celda del extracto) que
   habían quedado en azul sobre su lavado usan `coloresDeLoElegido`, y las filas elegidas
   `fondoDeLaFilaElegida`. Los vistos y los bordes de lo elegido, en tinta. El interruptor encendido y el
   `primary` de Material, en tinta.
2. **`TarjetaDeCuenta` blanca**: el color del banco solo en el avatar.
3. **Títulos**: «Hola, {nombre}» y el nombre del perfil en `saludo`; el título de 28 solo en las pestañas
   y uno de 22 con la flecha; el encabezado volvió a 56.
4. **Lo que se partía o se cortaba**: los estilos pasaron a la maqueta ÷ 1,12 (§5). Además, el widget «Falta
   por pagar» lleva el nombre de cada pago en su renglón y el monto debajo; la fila de acciones de un
   movimiento achica su rótulo hasta el 85 % antes de recortarse; «Tus recordatorios no llegan» (más
   corto) lleva «Ahora no» debajo, con su sangría, y el subtítulo en hasta dos renglones. Lo mide
   `LoQueSeVeEnteroTest` a 390 y 360. «Bancolombia Ahorros» va en un renglón a 390; a 360, al lado de un
   saldo de ocho cifras, en dos, entero. Los rótulos de íconos de categoría a 364 dp
   (`LosRotulosDeIconosNoSePartenTest`) siguen verdes con la letra nueva.
5. **La acción del encabezado de una pestaña va siempre en su botón redondo** (blanco con borde, el
   ícono en tinta, su nombre para el lector de pantalla): «+» en Patrimonio, Espacios y Presupuestos por
   igual. Con flecha, la regla de la vuelta 8 de siempre (con texto si cabe).
6. **El menos pegado al «$»** (§5).
7. **`entreCuentas` gris azulado** `#556372` (§4); `ContrasteDeLosTokensTest` pide que se despegue del enlace.

## 42. Vuelta 38: la navegación de la opción 3 — Inicio · Actividad · Pagos · Tú

El dueño eligió las **4 pestañas de la opción 3**, con Patrimonio, Personas y Ajustes dentro de «Tú».
Esta vuelta cambia la navegación; Inicio con sus 8 acciones es la 39, Actividad y Pagos por dentro la
40. Sin componentes nuevos en `ds/`; un token cambió: `Medida.altoDeBarraDePestanas` 64 → 76.

- **La barra del teléfono** (`MinBottomNav`): blanca, 76 de alto con el borde de arriba en `borde`,
  **cuatro pestañas de un cuarto del ancho cada una**, con los íconos de la maqueta (casa, lista,
  calendario con visto, persona). La activa lleva la píldora `marca` de 56 × 30 detrás del ícono y el
  rótulo en `pestanaActiva` (12/700, tinta); las demás `pestana` (12/500, `textoMedio`). **Las cuatro
  llevan su rótulo siempre, también a 360 dp y con la letra del sistema al 130 %**: se fue la regla de
  la R3 que, con cinco y el «+», dejaba solo el de la activa (`rotulosQueEntran` ya no existe).
- **Sin «+» en el teléfono.** Anotar vive en las acciones de Inicio. Mientras la vuelta 39 no ponga las
  8, Traspaso, Pago de tarjeta, Repartir y Subir comprobante se abren con **«Más formas de anotar»**
  (`MasFormasDeAnotar`, un `Boton` `Fantasma` debajo de las acciones), que abre el MISMO menú del «+»
  (`MenuDeAtajosDeAgregar`) con esas cuatro (`ATAJOS_FUERA_DEL_INICIO`). La cáscara pone lo que hace
  cada atajo en `LocalAtajosDeAgregar` (`hacerElAtajo`: la hoja de Agregar, las hojas de Repartir, el
  selector del comprobante); sin ella, el botón no se pinta.
- **El rail** (`MinNavRail`) lleva las mismas cuatro, Movi AI y «Agregar» con su menú (en el escritorio
  no hay un Inicio siempre a la vista, y el teclado físico de Agregar de la vuelta 34 se usa ahí). **Un
  solo amarillo lleno: «Agregar».** El destino activo del rail ancho va en el amarillo suave (`tinte`,
  `#FFF2C6`) con la tinta encima (`fondoDelDestinoDelRailAncho`); el del rail compacto, con la píldora
  chica detrás del ícono, como la barra.
- **Los títulos**: Movimientos se titula «Actividad» (`TITULO_DE_ACTIVIDAD`), Plan «Pagos»
  (`TITULO_DE_PAGOS`; desde la 40 el selector dice «Facturas | Presupuestos», ver §44) y
  Espacios «Personas» (`TITULO_DE_PERSONAS`, la palabra de la maqueta; un espacio —«Casa con Caro»—
  sigue llamándose espacio). En el código los nombres viejos se quedaron (`NavTab.MOVIMIENTOS`,
  `Screen.Plan`, `Screen.Espacios`): renombrarlos no le cambiaba nada al dueño.
- **«Tú»** (`Screen.Tu`, `MasScreen` + `LoTuyo`): arriba la tarjeta del perfil (avatar, nombre y
  correo, «Editar»); después **«Lo tuyo»**: «Tu patrimonio» con la cifra neta (`heroBalance` →
  `patrimonioDe`, la misma de Patrimonio) que abre Patrimonio; «Personas» con el saldo pendiente
  (`resumenDePersonas` sobre `fraseDelSaldo`: «Caro te debe $390.000», o cuántos espacios tienen saldo,
  o «Gastos compartidos y personas») que abre Personas; y «Cuadre de saldos» (con su modo «Tarjetas»).
  Debajo, **las secciones de Ajustes tal cual**, en `Fila`s con su `IconoEnCirculo`. Es raíz: sin avatar
  ni flecha (`HeaderLeading.Ninguno`), y re-tocar la pestaña sube. En pantalla ancha es un tablero de
  dos columnas —lo tuyo a la izquierda, los ajustes a la derecha— con el mismo mínimo por columna que
  Patrimonio (`tuEnDosColumnas`); Patrimonio, Personas y el detalle de una cuenta conservan sus columnas
  de la vuelta 33, ahora apiladas sobre Tú.
- **El avatar** del encabezado de Inicio, Actividad y Pagos abre «Tú».
- **Ninguna ruta se pierde.** Las raíces son las cuatro (`NavStack.esRaizDePestana`). Patrimonio y
  Personas, pedidos desde fuera de Tú (el tramo del patrimonio de Inicio, «Repartir» sin espacios, un
  destino SDUI), se abren **encima de Tú**: la pila queda `[Inicio, Tú, X]`, la barra marca Tú y volver
  lleva a Tú (`NavStack.seAbreSobreTu`); desde dentro de Tú se apilan como cualquiera. `Screen.Mas`
  (Ajustes) lleva a Tú (`destinoVigente`), así que el destino SDUI `"mas"` y `"profile"`, las pilas
  viejas y las flechas que volvían a Ajustes llegan ahí. Patrimonio, Créditos, el cuadre, cualquier
  cuenta, Personas, «Personas y comercios» y lo que era Ajustes marcan Tú (`navTabFor`); Movi AI no
  marca ninguna en el teléfono y tiene su lugar en el rail. «Entre cuentas» sigue en Patrimonio →
  Herramientas, y el cuadre con «Tarjetas» en Tú y en Patrimonio. `direccionDe` no cambió: abrir
  Patrimonio desde Inicio apila (`[Inicio]` → `[Inicio, Tú, Patrimonio]`), volver a Tú vuelve.

| destino viejo | ahora |
|---|---|
| pestaña Hoy | pestaña **Inicio** (la misma `Screen.Dashboard`) |
| pestaña Movimientos | pestaña **Actividad** (`Screen.Transactions`) |
| pestaña Plan | pestaña **Pagos** (`Screen.Plan`) |
| pestaña Patrimonio (`Screen.Accounts`) | Tú → «Tu patrimonio»; pedida de afuera, `[Inicio, Tú, Patrimonio]` |
| pestaña Espacios (`Screen.Espacios`) | Tú → «Personas»; pedida de afuera, `[Inicio, Tú, Personas]` |
| Ajustes por el avatar (`Screen.Mas`) | pestaña **Tú** (`Screen.Tu`); el avatar abre Tú |
| SDUI `"mas"`, `"profile"` | Tú |
| SDUI `"accounts"`, `"goals"`, `"investments"` | Patrimonio sobre Tú |
| avisos `por_revisar`, `aviso:<id>`, `aviso-categoria:<id>` | Por revisar / Reconciliar, marcando Actividad |
| aviso `plan`, `periodo:<id>` | Pagos / el detalle del período, marcando Pagos |
| aviso `captura` | Captura del banco, marcando Tú |
| atajos del ícono `agregar:gasto`, `agregar:traspaso` | la hoja de Agregar encima de Inicio |
| el «+» de la barra y sus atajos | Inicio: las 8 acciones (vuelta 39; en la 38 iba «Más formas de anotar»); el rail conserva el «+» |

**Las líneas base** no cambian: `dp_sueltos` 118, `radios_con_numero` 3, y las guardas en 0 siguen en
0. Lo vigilan `LasCuatroPestanasTest` y `LasCuatroPestanasA360Test` (Robolectric, motor de texto real:
las cuatro con su rótulo entero a 390 y a 360, con la letra ×1,3, la barra de 76, la activa
`selected`, sin «+»; el rail con las cuatro, Movi AI y «Agregar»), `AtajosDelMasTest` (las cuatro
formas de anotar desde «Más formas de anotar»), `MasScreenTest` (lo tuyo con su cifra, cada fila abre
su pantalla, Tú sin flecha), y en `commonTest` `NavStackTest`, `MinNavRailTest` (el amarillo suave del
rail), `ResumenDePersonasTest` y **`LasRutasViejasLleganTest`**, que recorre la tabla de arriba.

**La revisión de la vuelta 38** (aca3f04b, a 390, 360 y 1.280), y lo que cambió:

1. **El avatar va solo en Inicio**, como en la maqueta. Actividad, Pagos, Tú y Movi AI como raíz del
   rail no llevan nada a la izquierda del título (`leadingFor` da `HeaderLeading.Ninguno` en una raíz);
   desde Inicio el avatar sigue abriendo Tú.
2. **Personas no habla de espacios**: lo que el dueño lee dice **grupo** —«+ Nuevo grupo», «Crear
   grupo», «Crea un grupo, como «Casa con Caro»…», «¿En qué grupo?», «Quitar del grupo», «Esto deja el
   grupo en cero», «2 grupos con saldo pendiente»—. Los nombres internos (`Espacio*`, `Screen.Espacios`,
   las rutas y el server) no cambian.
3. **«Tu patrimonio» en Tú va sin subtítulo**: «Lo que tienes menos lo q…» se cortaba a 390 al lado de
   la cifra, que ya lo dice. El del cuadre es «Contra lo que dice el banco» (`SUBTITULO_DEL_CUADRE`).
   `LoTuyoA360Test` mide que nada de «Lo tuyo» se corte a 360 con una cifra de diez dígitos.
4. **El activo del rail ancho no lleva hover** (`elHoverSeVeEnElDestino`): el velo en tinta volvía el
   amarillo suave un beige oliva. Los demás destinos sí lo llevan.
5. **Las dos columnas de Tú, parejas**: a la izquierda lo tuyo, «Tu plata» y «Movi»; desde «Cómo se ve»,
   a la derecha (antes todos los ajustes iban a la derecha y la izquierda acababa a los 390 px). Lo mide
   `MasScreenTest` a 1.064 dp de panel (1.280 de ventana con el rail).

Que el rail tenga Movi AI y la barra no es a propósito: en el teléfono va en la acción «Preguntar a
Movi» de las 8 acciones (vuelta 39).

## 43. Vuelta 39: Inicio de la opción 3 — banda, héroe en tarjeta, 8 acciones, próximo pago y últimos movimientos

El dueño volvió a la opción 3 y eligió **las 8 acciones**. Esta vuelta rediseña solo Inicio (la tabla
de §10). Sin reglas de plata nuevas: cada cifra sale de una función de siempre.

- **La banda amarilla** va detrás del encabezado y de la mitad de arriba de la tarjeta héroe, que se
  monta encima. El encabezado es fijo, en `marca`; el resto de la banda lo dibuja el scroll
  (`Modifier.bandaDelInicio`: lo que le falta de 168 al encabezado, con `Radio.xxl` abajo) y se va con
  él. Detrás de la barra del sistema va el mismo amarillo (`App.kt`, solo en `Screen.Dashboard`, del
  ancho del contenido y nunca detrás del rail), con los íconos oscuros de siempre: arriba no queda una
  franja crema. En pantalla ancha la banda va solo detrás de Inicio. **La banda llega hasta media
  tarjeta héroe y las acciones van siempre sobre el crema** (la revisión: a 1.280 la primera fila de la
  cuadrícula caía sobre el amarillo y el círculo de «Anotar gasto» desaparecía): en una columna el bloque
  del héroe mide al menos lo que la banda (`ALTO_DE_LA_BANDA_EN_EL_SCROLL`), y en dos la derecha arranca
  debajo de ella. En ningún ancho un círculo `marca` queda sobre `marca`.
- **El encabezado** (`EncabezadoDelInicio`): avatar blanco con las iniciales (`inicialesDelDueno`, abre
  Tú), «Hola, Juan» y el período; recargando con cifras a la vista, «Actualizando…» toma el lugar del
  período (mismo alto). Los dos botones de la maqueta: **ocultar los saldos** (el ojo salió de la línea
  del rótulo del héroe; mismo `SaldosOcultosStore`, rótulo y tag) y la campana. La lupa salió de Inicio:
  Buscar sigue en Actividad.
- **El héroe es una tarjeta** (`Superficie(destacada = true)`, radio 22, la sombra suave): «Te quedan»
  (no «Disponible»: un nombre por concepto, vuelta 28) con el enlace «Tu plata $X» (`heroBalance` →
  `patrimonioDe`) que abre Patrimonio; la cifra (`teQuedanDelInicio`), que abre «¿De dónde sale?»; y
  «Para N días · $X por día». El veredicto («Gastaste $X · lo que más…») salió de la tarjeta. **La franja
  «Por confirmar»** cuenta lo que espera en Por revisar (`conteoPorRevisarDelInicio`, el número de la
  insignia de «Revisar avisos») en la pastilla «N por revisar» —«2 avisos» contradecía el desglose «1
  aviso · 1 movimiento» de debajo—; la maqueta dice además cuánta plata espera, y la franja va solo con
  el conteo (decisión del coordinador).
- **Nada dice $0 mientras carga.** Al recargar la web el héroe decía «Te quedan $0 · para 4 días ·
  $696.137 por día» varios segundos: la cifra entraba contando desde cero (`rememberProgresoDeEntrada`)
  y en la web los cuadros no llegaban mientras se leían las diez lecturas. Ahora la cifra se muestra
  tal cual llega —la de la instantánea o la de la carga— y solo un cambio posterior se anima, de una
  cifra real a otra (`rememberCifraQueCambia`, `cifraEntre`). Sin instantánea, el esqueleto de siempre.
- **Las 8 acciones** (`AccionDelInicio`): Anotar gasto · Anotar ingreso · Traspasar · Pagar tarjeta ·
  Repartir · Subir comprobante · Revisar avisos (con la insignia de `ConteoPorRevisar`) · Preguntar a
  Movi. Las seis de anotar son `DestinoDeLaAccion.Atajo`: la cuadrícula le pide el atajo a la cáscara
  (`LocalAtajosDeAgregar` → `hacerElAtajo`), así que abren **exactamente** lo que abre el menú del «+».
  Los rótulos van en dos renglones de alto fijo en `rotuloDeAccion` (12/600 efectivos, sin espaciado); a
  360 dp ninguno parte una palabra (`partePalabras`). Salió «Más formas de anotar»; el «+» con su menú
  queda en el rail de pantalla ancha.
- **«Próximo pago»** (`proximoPagoDelInicio`): el primero de `pagosEnOrdenDeUrgencia` —lo vencido
  primero y el monto más grande, la regla del widget y de la cola—: arriba «Próximo pago · vence …»
  (`fechaDeLaFila`), o **«Pago vencido · hace 23 días»** si ya venció (un pago vencido no es
  «próximo»); el nombre (un renglón) y `cifraDelPago` en texto, y el botón «Anotar pago» a la derecha.
  Sin ícono ni pastilla: a 360 «Arriendo» se partía en «Arrien / do». El botón
  (`accionDelPago`, el mismo de «Movi propone hoy»: con candidatos, «Revisar» en Pagos). «y N más» abre
  Pagos. Sin pendientes, no está. **«Movi propone hoy» no lo repite** cuando la definición trae
  `PROXIMO_PAGO` (`propuestasDelInicio(conElPago = false)`).
- **«Últimos movimientos»**: las tres primeras filas de Actividad (`ultimosMovimientosDe`, con
  `diasVisibles` en «Todo» y `collapseTransfers`) sobre `getUltimosDias(3)`: en la web
  `GET /api/events/by-day?ultimos=3`, que devuelve solo los días de los últimos movimientos —enteros,
  en el mismo orden, con el mismo total y sin anulados (`diasConLosUltimos`, un traspaso cuenta una
  vez)—; sin el parámetro la ruta responde como siempre; en el teléfono sale de lo local. Inicio no se
  baja la historia entera en cada carga. Se pintan con la misma
  `MovementSingleRow`/`TransferRow` —tu parte primero, «−» en un gasto, «+» verde en un ingreso— y la
  misma `HojaDelMovimiento` al tocarlas; si se edita o anula ahí, Inicio recarga. Viajan en la
  instantánea (`DashboardData.ultimosMovimientos`).
- **Pantalla ancha: dos columnas parejas** (`repartoEnDosColumnas`, `DosColumnasParejas`): el héroe a la
  izquierda y cada bloque siguiente a la columna más corta, con la derecha arrancando debajo de la banda
  —así la cuadrícula queda arriba a la derecha, sobre el crema—. Con los datos de todos los días la más
  corta mide al menos el 70 % de la más larga (la revisión: la izquierda acababa en ~600 px). Un bloque
  que no pintó nada no suma separación.
- **Sin saltos con la instantánea**: con la definición y los datos de la generación 10 en el aparato,
  «Próximo pago» se pinta desde el primer cuadro (sale de los vencimientos que la instantánea ya
  guarda) y no se mueve al llegar lo nuevo. El salto que vio la revisión era la instantánea de la
  generación 9 (sin `PROXIMO_PAGO`): pasa una vez, el primer arranque después del despliegue.
- **SDUI, generación 10**: entran `PROXIMO_PAGO` y `ULTIMOS_MOVIMIENTOS`. Un APK de la generación 9 los
  descarta (`renderableSections`) y ve su Inicio de siempre, con el pago dentro de su «Movi propone
  hoy» y sus cuatro acciones (la cuadrícula viaja en el binario). Las preferencias guardadas siguen
  valiendo (`versionConPreferencias` cambia con la generación).
- **12 px efectivos como piso**: ningún texto de Inicio queda por debajo (lo mide
  `ElInicioDeLaOpcion3Test`).

**Las líneas base** no cambian: `dp_sueltos` 118, `radios_con_numero` 3, y las guardas en 0 siguen en 0.
Lo vigilan `LasOchoAccionesDelInicioTest` (las 8 en orden, dos filas, ninguna palabra partida a 360 dp,
una prueba por acción), `ElInicioDeLaOpcion3Test` (el orden de arriba abajo, la franja con y sin avisos,
el próximo pago con «Anotar pago» e «y N más», tres últimos movimientos en orden y «Ver toda la
actividad», el piso de 12 px), `InicioDeUnVistazoEnPantallaTest` (la cifra nunca arranca en $0; las dos
columnas), `EsqueletosDelInicioTest`, `AtajosDelMasTest`, `RepartirDesdeElMasTest` y en `commonTest`
`InicioDelRedisenoTest` (el próximo pago es el vencido mayor, «Movi propone hoy» sin el pago, los últimos
movimientos, `cifraEntre`, las iniciales) e `InicioDeUnVistazoTest` (las columnas). En `:core`,
`DashboardDefaultsTest` (la generación 10 y lo que ve un APK de la 9) y `DiasConLosUltimosTest`; en el
server, `EventRoutesTest` (`?ultimos` con y sin el parámetro, anulados fuera, por usuario); y
`LosUltimosMovimientosPidenSoloLoUltimoTest` (Inicio pide con el límite, nunca la historia);
`ElInicioEnPantallaAnchaYAngostaTest` (ninguna acción sobre la banda a 360, 390 y 1.280; las columnas
parejas al 70 % a 1.280; «Arriendo» entero a 360) y `ElInicioNoSaltaConLaInstantaneaTest`.

## 44. Vuelta 40: Actividad y Pagos por dentro, como la opción 3

El dueño volvió a la opción 3 («me encantó a mí y a mi esposa»). La 38 puso las cuatro pestañas; esta
vuelta rediseña **Actividad** (antes Movimientos) y **Pagos** (antes Plan) por dentro, con las maquetas
`p3/Movimientos.dc.html` y `p3/Plan.dc.html`. **Sin reglas de plata nuevas**: todas las cifras salen de
las funciones de siempre. Tokens nuevos: `Medida.altoDeBarraEnTarjeta` (10, la barra de «Falta por
pagar») y `LETRA_MINIMA_QUE_SE_VE` (12 px); `BarraDeProgreso` ganó `fondo = FondoDeBarra.SobreLaMarca`
(la pista en `marcaSuave`). Ningún valor suelto nuevo.

### Actividad, de arriba abajo

1. **«Actividad»** en `titular` (28/800) y **la lupa en su botón redondo blanco** (`BotonDeIcono`
   `Secundario`), que abre Buscar como siempre. Sin avatar (vuelta 38).
2. **La tarjeta de los avisos del banco** (`TarjetaDeAvisosDelBanco`, superficie `Propuesta`: blanca con
   la sombra suave), solo con avisos pendientes: la campana en su círculo amarillo, «3 avisos del banco
   por confirmar», «Suman $186.400 · Rappi, D1 y Uber» (la suma solo si Movi leyó todos en pesos; si no,
   solo los comercios) y dos botones de 48 que van lado a lado si caben enteros y si no uno debajo del
   otro (`FlowRow`): **«Confirmar los N»** (`Principal`) y **«Revisar uno por uno»** (`Secundario`, abre
   Por revisar). Reemplaza el renglón «N por revisar», que vuelve solo cuando no hay avisos pero sí otra
   cosa por revisar (movimientos que entraron solos, pagos de tarjeta, débitos).
   - **«Confirmar los N» confirma solo lo que la fila confirmaría.** `avisosPorConfirmar` pasa cada pago
     por `loQueMoviEntendio` (monto, cuenta segura y categoría del dueño, sin «¿ya lo anotaste?», sin dos
     patas, sin reparto): esos son los `confirmables`. Si son todos, «Confirmar los 3»; si son algunos,
     «Confirmar los 2 que Movi entendió» y el resto queda; si ninguno, solo «Revisar uno por uno». Antes
     pregunta con `ConfirmarEnHoja` (variante `Principal`): «¿Confirmar estos 2 avisos?», cada uno en una
     línea («Tostao Café: Comida · Bancolombia Ahorros · $46.000»), el total y cuántos quedan. Confirma
     uno por uno por el MISMO camino que la fila (`confirmarElLote` → `confirmarLoEntendido`), muestra
     enseguida lo confirmado (`conLoResueltoEnLaBandeja`) y avisa «2 confirmados» (o cuántos no se
     pudieron) **sin deshacer**: confirmar un aviso no tiene vuelta en Movi.
3. **Chips de 44**: Todo · Gastos · Ingresos · **Personas** (`CHIP_PERSONAS` = 6, al final: los índices de
   antes no se renumeran). «Personas» deja lo repartido con alguien en un grupo (`espacioId`, el mismo dato
   de «de $88.600 · con Caro»); una transferencia a un tercero no entra.
4. La franja **Entró · Salió · Neto** (vuelta 30) sigue igual.
5. **El encabezado de cada día**: «Hoy · martes 28», «Ayer · lunes 27», «Domingo 26 de octubre»
   (`encabezadoDelDiaDeActividad`; `formatDayHeading` sigue en el detalle de una cuenta) en
   `cuerpoFuerte` (15/700 a la vista: la maqueta dice 14/700 y no hay estilo de 14), y a la derecha
   **«Gastaste $110.500»** en texto medio (`resumenDelDia`: `esGastoDelPeriodo` + tu parte con
   `montoContado`, la regla de «Gastos» y de «En qué se va»). Si ese día solo entró plata, **«Entró
   +$6.200.000»** en verde; sin nada que cuente (un traspaso, un pago de tarjeta), nada. Cuando la fecha
   y la cifra no caben con «Gastaste» al lado (360 dp con la letra al 130 %), cede **el rótulo** (el
   lector de pantalla lo sigue oyendo) y después el conteo de un día plegado, nunca la fecha ni la cifra. Era «Flujo del día», que sumaba el pago de tarjeta: ahora esa plata la dice la
   línea de abajo, que pasó de «De eso, $X fueron a pagar tarjeta…» a «**Además**, $X fueron a pagar
   tarjeta…». Se sigue plegando tocando el renglón.
6. Las filas siguen como en la 29: 58 de alto en la lista blanca, ingreso en verde con «+», tu parte
   primero.

### Pagos, de arriba abajo

1. **«Pagos»** y debajo «Del 25 de octubre al 24 de noviembre» en `textoMedio` (`encabezadoDelPeriodo`
   sin «quedan N días»).
2. **El «Pagos» repetido**: el selector pasó a **«Facturas | Presupuestos»** (`ROTULO_SEGMENTO_PAGOS`) y
   subió a justo debajo del período, antes de todo lo que cambia con los datos (es lo que
   `PlanScreenTest` vigila que no salte). «Facturas» es lo que lista adentro.
3. **La tarjeta amarilla «Falta por pagar»** (`TarjetaFaltaPorPagar`): fondo `marca`, radio 22, la tinta
   encima y lo secundario en `sobreMarcaMedio`. La cifra es la **héroe** de la pantalla
   (`VarianteDeCifra.Heroe`, 40/800 a la vista: la maqueta dice 36 y el sistema no tiene un tamaño entre
   la héroe y la de tarjeta), `faltaPorPagar`; «de $X · quedan N de M pagos» (`X` = falta +
   `yaPagadoEnElPeriodo`, sin los ingresos); la barra en tinta sobre `marcaSuave`; «Llevas $X pagados».
   El Disponible, más abajo, sigue en `Grande`: una sola héroe (`UnaSolaCifraHeroePorPantallaTest`).
4. **«Tus facturas del período»** (`FilaDeFactura`): el ícono de la categoría, el nombre en 15/700, la
   cifra y la pastilla de vencimiento —lo vencido y lo que vence en tres días o menos en el `tinte` con
   `aviso` («Venció hace 2 días», «Vence el sábado»), lo demás neutra («Vence el 3 nov»); «Por confirmar»
   si Movi pregunta «¿fue este?»—; a la derecha **«Anotar pago»** (la más urgente en `Principal`, las
   demás `Secundario`, tamaño `S`), que abre la MISMA hoja de Agregar prellenada de siempre
   (`hojaParaAnotar`). Las pagadas: **«Pagado ✓»** en verde (el visto es un ícono) y el enlace
   **«Movimiento del 25 oct»** que abre la hoja de ese movimiento (`HojasDelTableroDeRecurrentes`). Orden
   (`enOrdenDeFacturas`): vencidas, por vencer por fecha, pagadas al final. Desde la sexta, **«Ver los N
   pagos del período ›»**. Lo que la lista ofrecía sigue colgando de su fila (`LoQueOfreceLaFila`): «¿Ya
   pagaste …? Sí, fue este / No fue este», «No fue este», «Quitar la marca», «Movi no encontró el
   movimiento de este pago.» y la estimación de una cuota. Lo que abre la regla (o Créditos) es el ícono
   con los textos, no la fila entera: el botón de la derecha es otro control.
   Los ingresos fijos van aparte en «Tus ingresos del período» («Anotar ingreso», «Recibido»), y lo que
   un período anterior dejó abierto, al final, como antes.
5. **Lo demás se queda, debajo**: el Disponible con «¿De dónde sale?», «Tus períodos», la caja «Tu
   plata, día a día», los cobros detectados, «Lo que se repite al mes» y las suscripciones; «Tus
   recordatorios no llegan» va arriba de la lista. En «Presupuestos», los presupuestos y debajo el
   Disponible y «Tus períodos». En pantalla ancha, la columna de la izquierda no cambia.

### El mínimo de letra

Ningún estilo de `Movi.textos` se ve por debajo de **12 px** con la escala ×1,12 (el más chico, `apoyo`,
11 sp → 12,3): lo vigila `TipografiaTest` con `LETRA_MINIMA_QUE_SE_VE`. Lo único por debajo era la
marca de tres letras en el círculo de una fila (0,29 × 36 = 10,4 px): pasó a 0,34 (12,2 px,
`fraccionDeLaMarca`).

Lo vigilan `ActividadPorDentroTest` y `FacturasDelPeriodoTest` (`commonTest`),
`ActividadPorDentroEnPantallaTest` (la tarjeta con avisos y sin ellos, «Confirmar» que solo toma lo
entendido, «Revisar uno por uno», «Gastaste» y «Personas») y las pruebas de Plan y del tablero,
ajustadas.

### La revisión de la vuelta 40 (a 390 y 360), y lo que cambió

1. **El período no salía en Pagos con el usuario demo.** No esperaba un dato ni se escondía al cargar:
   el demo no tiene corte (`period_cutoff_day` nulo = el 1) y `rangoLegibleDe` se calla con el mes de
   calendario por diseño. Pagos usa ahora `rangoDelPeriodo` (`:core`), que lo dice siempre: «Del 1 al
   31 de octubre» (dentro del mismo mes, el mes una vez —`rangoEntre`—; cuando cruza, «Del 25 de
   octubre al 24 de noviembre»). Mientras el perfil viaja, la línea se reserva. Lo cubre `PlanScreenTest`
   con un perfil sin corte.
2. **«Por confirmar» ya no va llena de amarillo**: es una píldora de estado, en el `tinte` con `aviso`.
   El amarillo lleno queda para la acción principal.
3. **«Llevas $0 pagados» → «Aún no pagas nada de esto»** (`AUN_NO_PAGAS_NADA`, la frase de la vuelta 28).
4. **«¿Ya pagaste el de octubre?» dentro de la lista** es un bloque plano en la capa 2, sin sombra, con
   «Sí, fue este» y «No fue este» adentro (fuera de la lista, en una hoja, sigue siendo una propuesta).
   Los botones de una fila (`AccionesDeLaFila`) van en un `FlowRow`: a 360 dp «No fue este» se cortaba
   en «No fue», y ahora el que no cabe baja a su renglón (`LosBotonesDeLaPropuestaCabenTest`).
5. **«Tus recordatorios no llegan» salió de la lista**: es una `Fila` compacta en tono de aviso, con
   «Ahora no», en su propia superficie entre la tarjeta amarilla y «Tus facturas del período».
6. **La pastilla de vencimiento va en su renglón**, a todo el ancho desde el texto: al lado de «Anotar
   pago», a 360 dp, «Venció hace 23 días» se cortaba. Lo mide `LaPastillaCabeEnteraTest`.
7. **Actividad con un solo aviso, o sin ninguno que Movi entienda**: el botón dice «Revisar» a secas
   (`botonDeRevisarLosAvisos`); «Revisar uno por uno» solo al lado de un «Confirmar» de varios.
8. **Arriendo con la casa**: la palabra «arriendo» (y «alquiler») va a la casa —la llave queda para
   «Arriendo recibido»—, y una factura en «Otros» toma el ícono de su nombre
   (`nombreParaElIconoDeLaFactura`): el arriendo del demo está en «Otros».

## 45. Vuelta 42: el segundo nivel con el lenguaje de la opción 3

Una revisión fresca de toda la app (28-oct, capturas `full-*.png`) encontró el lenguaje viejo en
pantallas de segundo nivel. **Sin reglas de plata nuevas** y sin tocar nombres internos (`Espacio*`,
`Screen.Espacios`, rutas, server). Un componente nuevo en `ds/`, `FilaDeNavegacion`; un tono nuevo,
`TonoDeIcono.Tinta`; uno que se va, `TonoDePildora.Marca`.

1. **«Grupos» y «Compartidos».** La pantalla de los espacios se llamaba «Personas» y al final abría
   «Personas y comercios» (los terceros): dos nombres casi iguales. Pasa a **«Grupos»**
   (`TITULO_DE_GRUPOS`; `TITULO_DE_PERSONAS` queda como alias con el mismo texto, porque lo nombra Tú), y
   su fila en Tú con ella. El chip de Actividad que deja lo repartido en un grupo pasa a **«Compartidos»**
   (el vacío, «Nada compartido»). Buscar encuentra Grupos también por «espacios» y «personas».
2. **El amarillo lleno es de la acción.** `TonoDeIcono.Neutro` es ahora el círculo de la opción 3 —la
   capa 3 con el ícono en la tinta (era el texto medio sobre su lavado)— y es el tono por defecto de
   `IconoEnCirculo` y `AvatarDePersona` (era la marca: cada fila que no decía su tono salía amarilla).
   - **«Tú»** en los gastos de un grupo y en su detalle va en `TonoDeIcono.Tinta`.
   - **«En curso»** (Tus períodos y su detalle) y **«Por confirmar»** (una fila del checklist) van en
     `Aviso`, el tinte con el ocre; el destino elegido de un extracto, en `Neutra`.
   - **Las filas que llevan a otra pantalla** —«Cómo salir de tus deudas» en Créditos, «Tus períodos» en
     Pagos, «Subir comprobante o extracto» y los pagos de tarjeta en Por revisar, «Personas y comercios»
     en Grupos— son `FilaDeNavegacion`, con el mismo círculo neutro que Tú. El aviso de la captura en Por
     revisar, la misma fila con `aviso = true`. «Último mensaje» en Captura, el cuadre en Patrimonio, «Para
     revisar» y la caja: neutros (o en aviso, si avisan).
   - El ícono de un aviso del banco que pide decisión (sin marca ni categoría) va en `Aviso`.
   - Quedan en el círculo amarillo solo las dos propuestas que la maqueta pinta así: la campana de la
     tarjeta de avisos de Actividad y el ícono de «Movi propone hoy» en Inicio (`TarjetaDePropuesta`).
   - Lo vigila `ElAmarilloEsDeLaAccionTest`; el componente, `LaFilaDeNavegacionEsUnaSolaTest`
     (`TonoDeIconoKey` dice el tono en la semántica).
3. **«Revisar aviso» (Reconciliar).** El aviso va **plano** (`TarjetaDeAviso(destacada = false)`, la capa
   1 sin sombra) y la sombra queda solo en «Movi sugiere». El comercio de «Confirma o ajusta» va en forma
   de título (`enFormaDeTitulo`: «EXITO SUBA» → «Éxito Suba»; se muestra así, se guarda lo que leyó Movi
   si no lo toca). **Las pastillas de categoría salen debajo de la fila «Categoría» solo al tocar
   «Cambiar»** (con «+ Nueva» primera, y el selector completo debajo); elegir una la pone en la fila y las
   guarda. **El «Ojo: el aviso es de Falabella y la cuenta es de Bancolombia» va una vez y entero**, en su
   línea debajo de la fila de la cuenta (`BajoLaFila`), y la fila dice solo su porqué («La puso Movi»):
   pegados en un subtítulo de dos renglones se cortaba en «…de Bancolo…».
4. **Por revisar → «Entraron solos»**: el punto marrón delante de la cifra («• +$30.000») marcaba «por
   confirmar»; ahora es una `Pildora` «Sin confirmar» en `Aviso`, debajo del monto (`SIN_CONFIRMAR`, en
   toda fila de movimiento que entró solo).
5. **El detalle de una cuenta**: el ícono del tipo iba solo, sin rótulo, a la derecha del título. Ahora va
   en la tarjeta del saldo junto a su rótulo («🏛 COP · Ahorros», `Icono.s`), y el encabezado queda con la
   flecha y el título.
6. **Cómo salir de tus deudas**: la guía del abono es el ejemplo del sistema —`CampoDeMonto(ejemplo =
   "500.000")`, que se ve «$ Ej: 500.000» (el «$» ya lo pone el campo)—; era «…por ejemplo $500....»,
   cortada. Debajo del campo, una sola frase de apoyo; los supuestos pasaron a la nota del final, con
   el pie (`notaDelPlanDeSalida`).
7. **A 1.280.** **Por revisar es un tablero** (`Disposicion.Tablero`, `porRevisarEnDosColumnas`, el umbral
   de Patrimonio y de una cuenta: columnas de 420): «Mensajes del banco» (con «Confirmar los N») a la
   izquierda y lo demás —subir comprobante, la captura, lo que el banco cobró solo, «Entraron solos» y los
   pagos de tarjeta— a la derecha, cada una con su scroll. Solo con mensajes del banco por revisar; sin
   ellos, o al día, una columna de lectura. En el teléfono, el orden de siempre. **Movi AI** sigue en la
   columna de lectura (720), y las propuestas van a todo su ancho (tenían un tope de 320 dp suelto).

**Las líneas base**: `dp_sueltos` baja a 117 (el tope de 320 de las propuestas de Movi AI);
`radios_con_numero` 3, y las guardas en 0 siguen en 0. Lo vigilan `ElAmarilloEsDeLaAccionTest`,
`LaFilaDeNavegacionEsUnaSolaTest`, `ReconciliarDeLaOpcion3Test` (una sola sombra, el comercio en forma
de título, el «Ojo» una vez y entero a 360), `ElAvisoDelBancoElegisTusCategoriasTest` (las pastillas
solo con «Cambiar»), `PorRevisarDeLaOpcion3Test` («Sin confirmar», la fila de subir neutra, las dos
columnas a 1.280 y una a 390), `ElTipoDeLaCuentaConSuRotuloTest`, `LaGuiaDelAbonoCabeTest` y
`SalidaDeDeudasLogicTest`, `TarjetaDePropuestaTest` (la propuesta a todo el ancho de la columna), y en
`commonTest` `ActividadPorDentroTest` («Compartidos»), `BuscarEnMoviTest` («Grupos» por sus tres
nombres) y `DisposicionTest`.
