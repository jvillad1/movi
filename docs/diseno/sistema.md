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
| `capital` / `neutro` | `#8A5A18` / `#857C6F` | `neutro`: el relleno de un gráfico sin color propio (las barras de «En qué se va», el área de la curva del patrimonio, la derecha de las filas de Períodos). Vuelta 55: era `#A39A8C` (2,78:1 sobre el blanco, 2,47 sobre el crema); un gráfico pide 3:1 (WCAG 1.4.11) y ahora da 4,11 / 3,65 |

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
| `tituloMedio` | `titulo` en 600 | un nombre que encabeza sin gritar: una cuenta, una categoría, un tercero arriba de su detalle (el título de `EncabezadoDeHoja` fue este hasta la vuelta 41, §45) |
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
`TonoDeIcono.Propio` (el `tinte`, el amarillo suave, con la letra en `aviso`); **nunca** en `Marca`, el
amarillo lleno (vuelta 42).
```kotlin
AvatarDePersona("Caro", tono = TonoDeIcono.Info)
AvatarDePersona("Juan", tono = TonoDeIcono.Propio)
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
| banda | — (chrome) | amarilla (`marca`), `Medida.altoDeLaBandaDelInicio` 168 con `Radio.xxl` 28 abajo; la pinta el scroll (`SduiRenderer(conBanda = true)`) y detrás de la barra del sistema la cáscara; en dos columnas, solo detrás de la del héroe (§45) | — |
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
  `Apagado` (`tonoDeCifraDelTono`). **Qué es cada fila lo decide `comoSePresenta`** (`:core`, vuelta 50),
  la misma función que suma «Gastos» y el «Gastaste» del día: la cuota de un crédito es un gasto (`−`),
  el desembolso un ingreso (`+`), el pago de tarjeta, el traspaso y el abono van entre cuentas. Ninguna fila elige su color a mano. El punto de «por confirmar» y
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
| `PieDeHoja` | **el pie de toda hoja**: el error del guardado en `sale` justo encima, el `Boton` `M` a todo el ancho, y si está apagado, **la razón en una línea centrada justo debajo** («Falta el banco»). Con `secundaria` («Quitar») van dos botones del mismo ancho, la secundaria a la izquierda; **nunca «Cancelar»** (vuelta 41: la × y el arrastre ya cancelan). Anular y quitar van en `Peligro` |

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
  del período de Plan y de Movimientos, donde tiene lugar. *Vuelta 54: con un corte a mitad de mes, el
  rango corto, «23 oct – 24 nov» (§57).*
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

## 45. Vuelta 41: las hojas de «Tú» en el sistema, y el pulido del Inicio

Una revisión fresca de toda la app (28-oct) encontró las hojas de Ajustes con el lenguaje viejo.

- **El título de una hoja es lo más grande de la hoja.** `EncabezadoDeHoja` pasa de `tituloMedio`
  (14,5/600) a `titularDePantalla` (19,5/800, el de una pantalla con flecha: una hoja es una pantalla
  que se abre encima; `estiloDelTituloDeHoja`). En «Inicio del período» el grupo «Si ese día cae en fin
  de semana o festivo» y «Hoy estarías en octubre…» iban en `titulo` (14,5/700) y le ganaban a «¿Qué
  día empieza tu período?». Vale para todas las hojas de la app: ningún `GrupoDeCampos` ni `Seccion`
  de adentro lo supera.
- **Una hoja de «Tú»** (`ui/profile`, `ui/mas`) lleva: `EncabezadoDeHoja` con **una** frase corta de
  subtítulo; los datos en `Campo` / `CampoDeMonto` / `GrupoDeCampos`; si hace falta, **`VistaPrevia`**
  (`ds/VistaPrevia.kt`, nueva) con lo que va a pasar en dos renglones como mucho —«Tu período: del 25
  de octubre al 24 de noviembre» y, debajo, «El 25 de octubre es domingo: empieza el 23.»—, en una
  `Superficie` de capa 2; y un `PieDeHoja` con **una** acción principal, **sin «Cancelar»**. Las
  confirmaciones destructivas (`ConfirmarEnHoja`) conservan el suyo.
- Las hojas, una por una:
  - **Inicio del período** (`PeriodoSheet`): subtítulo de una frase; la grilla de días es
    `CalendarioDeDias` (`ui/profile/`, compartida con «¿Cuándo empezó…?», días en capa 2 y con su
    estado de radio para el lector de pantalla); «Si cae en fin de semana o festivo» es un
    `GrupoDeCampos` con tres `Fila` en una superficie (era `CardRow` en una caja blanca sobre la hoja
    blanca; el rótulo largo no entraba en el renglón de un grupo a 360 dp); los tres renglones de la
    vista previa son uno (`vistaPreviaDelPeriodo`, con `rangoDelPeriodo`, que también dice el mes de
    calendario) más uno opcional (`detalleDelInicioDelPeriodo`: un 29-31 o `ejemploCortoDelCorte`); el
    «Guardar» amarillo hecho a mano es `PieDeHoja`.
  - **¿Cuándo empezó…?** (`InicioDelPeriodoSheet`): una frase («Normalmente arranca el 25 de
    septiembre.»); la vista previa dice el rango y dónde se cierra el anterior; «Este mes empezó el N» en
    `PieDeHoja` con el error encima; «Volver al … como siempre» y «Ver tus períodos» siguen como botones
    de texto.
  - **Ahorro de cada período**: sin «Cancelar»; «Cuánto apartas» es un `GrupoDeCampos` con las
    píldoras y el campo; una frase de subtítulo.
  - **Días de aviso**: las seis cajas blancas son `PildoraDeFiltro`; el botón hecho a mano, `PieDeHoja`.
  - **Tu perfil**: «Alias» es un `Campo` (era `FieldBox`, el de Créditos) y el error va en el pie.
  - **Cambiar contraseña**: abre con su `EncabezadoDeHoja` (no tenía título) y cambia con `PieDeHoja`
    (era una caja negra hecha a mano).
  - **Recibe tus alertas por correo** solo informa: su acción es «Copiar», junto a la dirección.
- **«Volver al orden de Movi»** (Editar inicio): con el Inicio ya en el orden de Movi no se pinta
  (`seVeVolverAlOrdenDeMovi`); apagado se veía en el marrón del texto apagado, como roto. Con algo
  cambiado, `Boton` `Fantasma`, en el azul del enlace.
- **La banda del Inicio a 1.280** va solo detrás de la columna del héroe (`anchoDeLaBanda`): cruzaba
  también la derecha y dejaba ~110 dp de amarillo vacío encima de las acciones. La derecha arranca
  arriba, sobre el crema (`arribaDeLaDerecha = Medida.dentroDeSeccion`), y el rincón donde la banda se
  junta con el encabezado se redondea hacia adentro con `Radio.xxl`. Las acciones no subieron a la banda:
  su primer círculo es amarillo.
- **Las iniciales del usuario salen de una sola función**, `inicialesDelUsuario` (`ui/components/
  AvatarButton.kt`): el Inicio, la tarjeta de «Tú» y `AvatarButton`. El Inicio decía «D» y «Tú» «DM»
  para el mismo usuario; además, el Inicio pone al día el nombre con el perfil que ya pide.

Lo vigilan `LasHojasDeTuSonDelSistemaTest` (Robolectric, motor de texto real: cada hoja con su título,
ningún texto que lo supere en tamaño ni en peso, una sola acción principal, sin «Cancelar», la vista
previa en uno o dos renglones), `LasHojasDeTuNoCancelanTest` (`core/src/jvmTest/.../quality/`: ningún
«Cancelar» a mano ni `onSecundaria = onDismiss` en `ui/profile` y `ui/mas`), `VolverAlOrdenDeMoviTest`
(las dos situaciones), `ElInicioEnPantallaAnchaYAngostaTest` (a 1.280 la banda no cruza la mitad y las
acciones arrancan a su lado; a 390 sigue a todo el ancho; ninguna acción sobre la banda en ningún ancho)
y `LasHojasDeTuDicenLoQueVaAPasarTest` (`commonTest`: las vistas previas, el botón, la banda y las
iniciales, puras).

## 46. Vuelta 42: el segundo nivel con el lenguaje de la opción 3

Una revisión fresca de toda la app (28-oct, capturas `full-*.png`) encontró el lenguaje viejo en
pantallas de segundo nivel. **Sin reglas de plata nuevas** y sin tocar nombres internos (`Espacio*`,
`Screen.Espacios`, rutas, server). Un componente nuevo en `ds/`, `FilaDeNavegacion`; un tono nuevo,
`TonoDeIcono.Propio`; uno que se va, `TonoDePildora.Marca`.

1. **«Grupos» y «Compartidos».** La pantalla de los espacios se llamaba «Personas» y al final abría
   «Personas y comercios» (los terceros): dos nombres casi iguales. Pasa a **«Grupos»**
   (`TITULO_DE_GRUPOS`; `TITULO_DE_PERSONAS` queda como alias con el mismo texto, porque lo nombra Tú), y
   su fila en Tú con ella. El chip de Actividad que deja lo repartido en un grupo pasa a **«Compartidos»**
   (el vacío, «Nada compartido»). Buscar encuentra Grupos también por «espacios» y «personas».
2. **El amarillo lleno es de la acción.** `TonoDeIcono.Neutro` es ahora el círculo de la opción 3 —la
   capa 3 con el ícono en la tinta (era el texto medio sobre su lavado)— y es el tono por defecto de
   `IconoEnCirculo` y `AvatarDePersona` (era la marca: cada fila que no decía su tono salía amarilla).
   - **«Tú»** en los gastos de un grupo y en su detalle va en `TonoDeIcono.Propio`: el `tinte` con la
     letra en `aviso`. En la tinta llena (la primera versión de la vuelta) eran cuatro círculos negros
     seguidos al lado del de Caro; el amarillo **suave** se distingue sin pesar. Lo prohibido es el
     amarillo **lleno**.
   - **«En curso»** (Tus períodos y su detalle) y **«Por confirmar»** (una fila del checklist) van en
     `Aviso`, el tinte con el ocre; el destino elegido de un extracto, en `Neutra`.
   - **Las filas que llevan a otra pantalla** —«Cómo salir de tus deudas» en Créditos, «Tus períodos» en
     Pagos, «Subir comprobante o extracto» y los pagos de tarjeta en Por revisar, «Personas y comercios»
     en Grupos— son `FilaDeNavegacion`, con el mismo círculo neutro que Tú. El aviso de la captura en Por
     revisar, la misma fila con `aviso = true`. «Último mensaje» en Captura, el cuadre en Patrimonio, «Para
     revisar» y la caja: neutros (o en aviso, si avisan).
   - El ícono de un aviso del banco que pide decisión (sin marca ni categoría) va en `Aviso`.
   - Quedan en el círculo amarillo lleno **solo las dos excepciones** que la maqueta pinta así: la
     campana de la tarjeta de avisos de Actividad y el ícono de «Movi propone hoy» en Inicio
     (`TarjetaDePropuesta`). La guarda las nombra y falla si sobra o falta alguna.
   - Lo vigila `ElAmarilloEsDeLaAccionTest`; el componente, `LaFilaDeNavegacionEsUnaSolaTest`
     (`TonoDeIconoKey` dice el tono en la semántica).
3. **«Revisar aviso» (Reconciliar).** El aviso va **plano** (`TarjetaDeAviso(destacada = false)`, la capa
   1 sin sombra) y la sombra queda solo en «Movi sugiere». El comercio de «Confirma o ajusta» va en forma
   de título (`enFormaDeTitulo`: «EXITO SUBA» → «Éxito Suba»; se muestra así, se guarda lo que leyó Movi
   si no lo toca). **«Cambiar» sigue el patrón de Agregar (vuelta 34)**: debajo de la fila «Categoría»
   salen las pastillas de las frecuentes, con la elegida marcada, «+ Nueva» primera y **«Todas ›» al
   final**, que abre el selector completo **en una hoja** (`MarcoDeHoja`; «+ Nueva» la abre con el cursor
   en la búsqueda). La cuadrícula en línea se fue: sus 18 íconos ocupaban la pantalla y empujaban
   «Confirma o ajusta» fuera de vista. Elegir una pastilla o una celda la pone en la fila y cierra. **El «Ojo: el aviso es de Falabella y la cuenta es de Bancolombia» va una vez y entero**, en su
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

## 47. Vuelta 43: «Anotar pago» salda la factura, y la cuenta dice por qué

El día del dueño (28-oct): «Pago vencido · hace 23 días» → «Anotar pago» abría un Agregar genérico —la
categoría de la regla, la cuenta «Más usada» (Nu, la tarjeta)— y guardaba un gasto suelto. La fila seguía
en «Movi no encontró el movimiento de este pago» y «Te quedan» bajaba dos veces.

1. **Agregar en modo pago** (`ui/quickadd/AgregarEnModoPago.kt`, `Screen.QuickAdd.presetPago`). Todo
   «Anotar pago» de una factura —Pagos, «Próximo pago» / «Pago vencido» de Inicio, «Movi propone hoy»—
   pasa por `hojaParaAnotar` y abre la hoja con la factura:
   - **donde van las pestañas, el encabezado** «Pago de Arriendo · octubre» (`EncabezadoDelPago`:
     `tituloMedio`, centrado, `heading()`; «Ingreso de Salario · octubre» en un ingreso). Sin pestañas:
     cambiar a Traspaso o Cuota no tiene sentido cuando el guardado salda ESTA factura;
   - el monto de la regla (se puede cambiar), su categoría (también «Otros» si es la de la regla) y su
     nombre como título; la fecha, hoy;
   - **sin «Guardar y anotar otro» ni «Repartir»**: cada pago es de su factura;
   - **Guardar** manda el movimiento con la ocurrencia que paga (`anotarElPagoDeLaOcurrencia`). La fila
     pasa a «Pagado» con el enlace al movimiento.
   La cuota de un crédito y el pago de una tarjeta siguen abriendo la pestaña «Cuota» (dos patas).
2. **La cuenta que propone Agregar** (`resolverCuentaDelMovimiento`, `CuentaPorDefecto.kt`), y **el
   renglón bajo «Cuenta» dice por qué**:
   - modo pago, la de la regla → «La de la regla»;
   - modo pago, la regla sin cuenta → **no elige**: el valor dice «Elige la cuenta» apagado
     (`ELIGE_LA_CUENTA`), el renglón «Elige de dónde sale», y el selector pone **la sugerida arriba**
     (`conLaSugeridaArriba`). Con una sola cuenta, esa;
   - «Efectivo» en la categoría o el título → la cuenta de efectivo → «Es en efectivo»;
   - la última cuenta con ese nombre o esa categoría (`GET /api/cuentas/de-cada-uso`, la huella de la
     memoria de categorías) → «La que usas para Rappi», «La que usas para Mercado»;
   - después, como antes: «Última usada», «Más usada», «Por defecto».
   «La que usas para Mercado» es una frase: con un rótulo de más de 14 letras el nombre de la cuenta cede
   ancho (`FRACCION_DEL_VALOR_CON_ROTULO_LARGO`, 42 %) y, si ni así entra, se corta con «…»; la fila no se
   parte. La cuenta se vuelve a decidir al cambiar la categoría o la nota, salvo que el dueño la haya
   elegido; «Guardar y anotar otro» la conserva.

Lo vigilan `AnotarPagoSaldaLaFacturaTest` (Robolectric: desde Pagos, Próximo pago y Movi propone, el
encabezado, la cuenta pedida y el pago con su ocurrencia; nunca un `postEvent` suelto),
`LaCuentaQueProponeAgregarTest` (cada caso del orden y su rótulo) y, en `:server`,
`AnotarPagoSaldaLaFacturaTest` / `AnotarPagoDesdeLaFilaRutasTest` («Te quedan» igual, o por la diferencia).
Las guardas en 0 siguen en 0.

## 48. Vuelta 44: los avisos se confirman de un vistazo, y «Repartir» abre el flujo bueno

La revisión de «un día del dueño» (28-oct, la demo con tres SMS de Bancolombia: Uber, Farmatodo y Juan
Valdez) encontró que confirmar avisos costaba dos toques cada uno sin ver lo que Movi entendió, que el
título de Revisar aviso decía «85540», que «Repartir» suponía que pagó Caro y que «¿se repite todos los
meses?» salía después de cada gasto. **Sin reglas de plata nuevas**: confirmar va siempre por el camino
de la fila (`confirmarLoEntendido`).

1. **Por revisar: cada aviso dice lo que Movi entendió.** La tarjeta (`TarjetaDeMensajeDelBanco` y
   `TarjetaDelMismoPago` con `loEntendido`) lleva arriba el comercio en forma de título, el monto y, en
   el lugar del subtítulo, **«Categoría · Cuenta»** (`LoQueMoviEntendio.linea`, `lineaDeLoEntendido`):
   la categoría y la cuenta que propone Movi **entendidas o no** —son las que Revisar aviso va a
   mostrar—, así el mal categorizado salta a la vista. «Banco · hora» baja junto al SMS crudo, que queda
   en **un renglón** plegado (`LoQueDiceElBanco(renglones = 1)`). Con todo entendido, **«Confirmar»**
   (principal, un toque) y **«Cambiar»** (`CAMBIAR_EN_LA_FILA`), que abre Revisar aviso **con las
   categorías ya a la vista** (`Screen.SMSReconcile(abrirCategoria = true)`); si Movi duda,
   **«Revisar»** es el principal. «Ignorar» sigue en todas. «Se anota: …» se fue: lo dice la línea de
   arriba (`TAG_LINEA_DE_LO_ENTENDIDO` pasó a ella).
2. **«Confirmar los N», arriba, es el de Actividad.** Desde dos que Movi entendió (`MINIMO_PARA_EL_LOTE`),
   la tarjeta de los avisos de Actividad (`TarjetaDeAvisosDelBanco`, sin «Revisar uno por uno»: ya estás
   ahí) y **la misma hoja** (`HojaDeConfirmarLosAvisos` + `rememberConfirmarLosAvisos`, en
   `ui/transactions/ConfirmarLosAvisos.kt`, que ahora usan las dos pantallas). El lote propio de la
   bandeja (`TarjetaDelLote`) se fue.
3. **El banco por su nombre.** `bancoDelAviso` traduce el código del remitente con el mapeo que ya
   existía en `:core` (`nombreDelOrigenDeCaptura`, el de «banco mudo»: 85540, 891333 y 87400 son
   Bancolombia): «Bancolombia · 12:05 p. m.» en la tarjeta y **«Bancolombia» como título de Revisar
   aviso** (`tituloDelAvisoEnReconciliar`). Un código que Movi no conoce no se adivina: «SMS» en la línea
   y «Mensaje del banco» como título. Los códigos de Nu, Davivienda y AV Villas **no se agregaron**: no
   hay un SMS suyo a la vista y el repo ya decidió no adivinarlos (`SmsFilterConfigRoutes`).
4. **«Anotado».** Confirmar desde la fila dice abajo «Anotado · Uber Rides $18.300» (`textoDeAnotado`), y
   el lote «3 confirmados». **Sin «Deshacer»**: confirmar un aviso no tiene una vuelta limpia en Movi (se
   anula el movimiento, como siempre; vuelta 40).
5. **Volver donde la dejaste.** La lista recuerda **por llave**, no por número de fila
   (`ui/porrevisar/ElScrollQueSeConserva.kt`: `rememberListaQueSeConserva`, `fila`, `dondeVolver`): las
   filas a la vista y el corrimiento de la primera, guardables. Al volver de Revisar aviso —con el
   esqueleto de la relectura de por medio, que antes la mandaba arriba— vuelve a la primera de esas
   filas que siga; si era el aviso confirmado, la siguiente queda arriba. Toda fila de la bandeja tiene
   llave (`fila(llaves, …)`).
6. **«Por confirmar $103.600» en el Inicio.** `DashboardSummary.porConfirmarSuman` (con default: un APK
   viejo lo ignora, un server viejo no lo manda) es `sumaDeLoPorConfirmar` (`:core`
   `LoQueSumanLosAvisos.kt`): el monto de cada pago pendiente —del aviso que lo propone, o la lectura del
   comprobante— **entero o nada**: sin un monto legible o en otra moneda, `null`. La franja
   (`textosDeLaFranjaPorConfirmar`) dice «Por confirmar $103.600» y debajo «En 3 avisos» (o «En 4 avisos
   · y 1 movimiento»: la cifra dice de qué es); sin la suma, el conteo de la vuelta 39. La pastilla sigue
   siendo «N por revisar». Actividad suma con la misma función.
7. **«Repartir» abre Agregar en modo personas.** La acción de Inicio y la del «+» (`AtajoDeAgregar.REPARTIR`)
   abren `Screen.QuickAdd(presetRepartir = true)` (`pantallaParaRepartir`): «Repartir este gasto ·
   ¿Quién pagó? **Yo** | Caro», el monto en blanco. Con varios grupos la hoja pregunta en cuál; sin
   ninguno, primero se crea. La hoja «¿En qué grupo?» de la vuelta 7 se fue. **«Pagó otro»** sigue en el
   grupo («Caro pagó y yo le debo»), con el selector de Agregar —«Categoría … Todas ›» y las pastillas,
   `EncabezadoDeLaCategoria`/`CategoriaChipsRow`, ahora `internal`— y el selector completo en una hoja;
   el desplegable con «Administrar categorías» se fue de ahí.
8. **«¿Se repite todos los meses?» solo cuando ya se repitió.** `seRepiteParaProponer` (`:core`
   `ElRecurrenteQueSeRepite.kt`): el mismo comercio o título (`claveDeComercio`) con un monto ±15 %
   (`MARGEN_DEL_MONTO_QUE_SE_REPITE`, elegido) en **2 o más períodos anteriores distintos** del dueño, sin
   regla ni suscripción; **nunca** después de repartir, de un gasto en efectivo de menos de $50.000
   (`EFECTIVO_QUE_NO_SE_PROPONE`, elegido) ni de un pago que saldó una ocurrencia (Agregar abierto desde
   «Anotar pago» o con una deuda, `vieneDeUnPagoQueSeEspera`). `RecurringOfferGate` pide la historia, las
   cuentas y el perfil solo cuando las guardas baratas pasaron; si alguna lectura falla, no ofrece.

**Los toques** (desde el Inicio): tres avisos que Movi entendió, de 7 (la franja y «Revisar» +
«Confirmar» por aviso) a **3** (la franja, «Confirmar los 3», el «Confirmar» de la hoja); con uno mal
categorizado, de 9 a **6** (la franja, «Cambiar», la categoría y «Confirmar» en Revisar aviso, «Confirmar
los 2» y la hoja). La meta de la revisión era 5 para ese caso: el sexto es la pregunta de la hoja, que
esta vuelta reutiliza tal cual.

**Las líneas base** no cambian (`dp_sueltos` 117, `radios_con_numero` 3) y las guardas en 0 siguen en 0.
Lo vigilan `LosAvisosDeUnVistazoTest` (lo entendido arriba, el banco por nombre, «Anotado», «Confirmar
los 3» con su hoja y los tres toques desde el Inicio, el mal categorizado), `PorRevisarVuelveDondeLaDejasteTest`
(falla sin la vuelta por llave), `LaBandejaQueSeVaciaTest` (el lote nuevo), `RepartirDesdeElMasTest` y
`RepartirEnAgregarTest` (modo personas con «Yo»; repartir no ofrece el recurrente), `PagoOtroConNotaTest`
(las pastillas), `ElRecurrenteSoloCuandoSeRepiteTest` (el gate), y en `commonTest`/`:core`
`LosAvisosDeUnVistazoLogicaTest`, `LaCabezaDelAvisoTest`, `LoQueSumanLosAvisosTest` y
`ElRecurrenteQueSeRepiteTest`; en el server, `DashboardRoutesTest` (la suma, entera o nada).

## 49. Vuelta 46: lo que Movi no entendió se pide, y «Todas ›» siempre en el renglón del rótulo

La revisión de la vuelta 44 con datos reales: tres avisos entendidos se confirman en 3 toques, pero
con uno en «Otros» eran 7 (8 si la categoría no estaba entre las frecuentes), «Otros» llevaba en
Revisar aviso el mismo ✓ verde de lo confirmado, «Repartir» no dejaba cambiar la categoría ni la
fecha, y «Todas ›» quedaba al final de las pastillas, fuera de la pantalla del teléfono. **Sin reglas
de plata nuevas**: confirmar sigue por `confirmarLoEntendido`.

1. **Un solo componente para elegir categoría: `FilaDeCategorias`** (`ui/components/ds/FilaDeCategorias.kt`).
   Es el de Agregar (vuelta 34), movido **tal cual** —el desplazamiento de las pastillas no cambió—:
   «Categoría … [sugerencia] … **Todas ›**» en un renglón de 48 y debajo las pastillas («Nueva»
   primera, la elegida marcada y siempre a la vista). `RellenoDeLaFilaDeCategorias.Lista` pone el
   margen (dentro de una superficie de lista) y `Ninguno` no (dentro de una tarjeta o una hoja);
   `TagsDeLaFilaDeCategorias` deja a cada pantalla sus `testTag`. Lo usan **Agregar, Revisar aviso, la
   tarjeta de Por revisar, «Repartir este gasto» y «Pagó otro»**. En Revisar aviso «Todas ›» iba al
   final de las pastillas (r44-08); ahora va en el renglón del rótulo, como en todas partes.
2. **Revisar aviso no le pone ✓ a lo que Movi no entendió.** La fila «Categoría» con «Otros» (o vacía)
   dice **«Elige la categoría»** con «Movi no la entendió» debajo, el ícono de pregunta en el `tinte` con
   `aviso` y el subtítulo en `aviso` (`TonoDeSubtitulo.Aviso`); una del catálogo que el dueño nunca usó,
   igual pero con su nombre y «Revísala: Movi la propone»; la que él elige a mano en la pantalla, con ✓
   (`categoriaEnReconciliar`, `categoriaQueNoDiceNada` en `:core`). Lo mismo **la cuenta** que Movi puso
   por defecto («La puso Movi», `cuentaEntendidaEnReconciliar`) y **el comercio** que no se leyó
   («Movimiento»: «Escribe el comercio», `comercioEntendidoEnReconciliar`).
3. **Un aviso al que solo le falta la categoría se resuelve en su tarjeta.** `LoQueMoviEntendio.faltaSoloLaCategoria`
   (las dudas son solo `SIN_CATEGORIA` y la cuenta es segura): el botón principal es **«Elegir
   categoría»** (`ELEGIR_CATEGORIA`), que abre en la tarjeta las frecuentes del tipo con «Todas ›»
   (`FilaDeCategorias`, relleno `Ninguno`, la fila de `Medida.altoDeFilaDePastillas`; sin el uso del Inicio a mano, las que el dueño usa —
   `PropuestasDeLaBandeja.categoriasDelDueno`—); «Todas ›» abre el selector completo en una hoja. Al
   elegir, la bandeja pasa la elegida a `loQueMoviEntendio(categoriaElegida = …)` —como si la hubiera
   elegido en Revisar aviso: cuenta como decidida, `ELEGIDA_EN_LA_TARJETA`— y la tarjeta queda
   **confirmable ahí mismo** («Confirmar», con «Papelería · Bancolombia Ahorros 4057» arriba); también
   entra en «Confirmar los N» (`avisosPorConfirmar(categoriasElegidas = …)`). Si la elegida pide dos
   patas (un pago de tarjeta), vuelve a «Revisar». El pago avisado varias veces
   (`TarjetaDelMismoPago`) sigue yendo a Revisar aviso.
4. **«Repartir este gasto» tiene los campos de Agregar.** La línea de texto «Restaurantes · Hoy» (r44-09)
   pasó a la píldora de la fecha (abre `SelectorDeFecha` en la hoja) y a `FilaDeCategorias` con «Todas ›»
   (el selector completo, en una hoja encima). Escriben en Agregar —son el mismo dato, como el monto—,
   así que lo guardado y repartido lleva lo elegido.
5. **La hoja de «Confirmar los N»** lista los avisos como `Fila` del sistema —el comercio, «Categoría ·
   Cuenta» y la `Cifra`— en una superficie de capa 2 (`ConfirmarEnHoja(lista = …)`); el detalle quedó
   en el total y cuántos quedan. **«N confirmados» sigue sin «Deshacer»**: no existe una vuelta limpia
   —no hay una ruta que devuelva un aviso confirmado a pendiente junto con anular su movimiento, y un
   pago avisado varias veces o repartido en partes deja más de un movimiento—. Inventarla sería una
   regla de plata nueva.

**Los toques**, medidos en `ElOtrosSeResuelveEnLaTarjetaTest` con cuatro avisos (tres entendidos y uno
en «Otros»), desde el Inicio: **5** (la franja, «Elegir categoría», la pastilla, «Confirmar los 4» y el
«Confirmar» de la hoja; eran 7). El de «Otros» se resuelve en **2** («Elegir categoría» y la pastilla).
Si la categoría no está entre las frecuentes, uno más («Todas ›» y elegirla en la hoja): 6 (eran 8).
Tres entendidos, como en la vuelta 44: 3.

**Las líneas base**: `dp_sueltos` baja a 116; `radios_con_numero` 3, y las guardas en 0 siguen en 0. Lo
vigilan `ElOtrosSeResuelveEnLaTarjetaTest` (la tarjeta, los toques, la hoja con filas, sin «Deshacer»,
«Todas ›» arriba de las pastillas a 360), `RepartirEnAgregarTest` (la categoría y la fecha en la hoja),
`ElAvisoDelBancoElegisTusCategoriasTest` («Todas ›» en el renglón del rótulo en Revisar aviso),
`PagoOtroConNotaTest` y, en `commonTest`, `LoQueMoviNoEntendioSePideTest`.

## 50. Vuelta 45: una sola cifra, una sola alarma y un emparejamiento que pregunta más

Lo que quedó de la revisión de «un día del dueño» (28-oct, capturas `dia-*.png` y `r43-*.png`). **Sin
reglas de plata nuevas**: las cifras salen de las funciones de siempre y el emparejamiento solo propone.
Ningún valor suelto nuevo; las guardas en 0 siguen en 0.

1. **Pagos dice lo mismo que el Inicio.** Pagos encabezaba la tarjeta del Disponible con «Disponible del
   período $4.536.800» en grande y dejaba «al período le quedan $773.950» en la letra chica; el Inicio
   decía «Te quedan $773.950». Ahora la protagonista de la tarjeta de Pagos (`TarjetaDelDisponible(comoEnPagos
   = true)`, `ui/sdui/SeccionDisponible.kt`) es **«Te quedan» / «Te pasaste por»**, de `teQuedanDeHoy` sobre el
   MISMO `disponibleDelInicio` que el héroe, en `Cifra` `Grande` (la héroe de Pagos sigue siendo «Falta por
   pagar»), con el mismo detalle («Para 4 días · $1,7M por día»). El total del período baja a un renglón,
   `CifraConRotulo` `Chica` `EnRenglon`, con el nombre de la vuelta 28: **«Disponible del período»**
   (`ROTULO_DISPONIBLE_DEL_PERIODO`). La sección opcional del Inicio (`comoEnPagos = false`) sigue como antes:
   el total y las tres columnas, sin frase. El esqueleto reserva el renglón nuevo.
2. **Una sola alarma, y que diga qué hacer.** «Te pasaste de la meta por $1,3M» salía en el héroe y en la
   frase de Pagos, y Pagos pintaba tres barras rojas.
   - `teQuedanDeHoy` ya no dice la semana ni el día pasados: el detalle del héroe (y de Pagos) es «Para N
     días · $X por día» (o el ritmo, el período pasado, sin margen, como siempre).
   - La alarma sale **una vez, en Pagos** (`alertaDelDisponible`, `AlertaDeLaTarjeta`): «Esta semana te pasaste
     de la meta por $1,3M.» u «Hoy te pasaste…» en `sale`, y debajo **«Mira en qué se fue ›»** (`Boton`
     `Fantasma` `S`, `MIRA_EN_QUE_SE_FUE`) que abre «En qué se va». Con el período pasado la cifra ya dice «Te
     pasaste por $X»: la alarma es solo el enlace. Sin margen no hay alarma aparte (lo explica el detalle).
   - **Las barras van en tinta** (`tonoDeLaBarra`: `TonoDeBarra.Marca`, la tinta desde la vuelta 29); solo
     la del período pasado va en `Peligro` (`barraEnAlerta`, tag `TAG_BARRA_EN_ALERTA`). Las cifras de la
     semana y de hoy van en tinta aunque estén pasadas: su «−» ya lo dice.
3. **Movi AI no abre con cifras viejas** (`ui/ai/LoQueEraDeOtroDia.kt`, `loQueDiceLaRespuestaVieja`). Una
   respuesta **de hoy** que cita cifras se atenúa como la de otro día si pasó más de una hora
   (`VIGENCIA_DE_UNA_RESPUESTA_MS`) o si su «Te quedan $X» ya no es el del Inicio (`DashboardDataCache`,
   solo si se leyó después de la respuesta). Debajo, «Esto era a las 9:40; ahora te quedan $773.950» y
   «Preguntar de nuevo». Lo que llega después de abrir el chat nunca se juzga.
4. **«En qué se va» abre sin elegir** (`Modifier.graficaQueSeToca`, `elMouseSeMovio`). Abría con «25 oct ·
   $0 · período pasado $0»: el clic que la abrió dejó el puntero sobre la gráfica y el movimiento sintético
   de un puntero quieto la elegía. Sin botón apretado, ahora solo elige un movimiento que mueve el puntero;
   la leyenda abre con su texto de siempre. Vale para todas las gráficas que se tocan.
5. **El emparejamiento pregunta más, sin tildar** (`:core` `HastaDondeSePropone.kt`, `ultimoDiaParaProponer`;
   `:server` `ventanaParaProponer`). Pasados los 10 días de la ventana, un movimiento se sigue proponiendo
   («¿Es este?») hasta el último día del período del dueño en que vence. Fuera de la ventana angosta nunca es
   concluyente ni identidad fuerte (`CandidatoPuntuado.enLaVentana`): no se tilda solo ni absorbe el fijo en
   el Disponible. Una regla sin cuenta con la misma categoría y el monto exacto ya era candidata (la fila
   pregunta; no se tilda porque falta la cuenta): ahora lo fija una prueba.
6. **Una factura pagada por el dueño no ofrece «No fue este»** (`accionesDeLaFilaPagada`). Lo que Movi
   emparejó solo: «No fue este». Lo que el dueño selló —«Anotar pago» o «Sí, fue este»—: **«Ver movimiento»**
   (`ETIQUETA_VER_MOVIMIENTO`, abre su hoja) y **«Quitar la marca»**.
7. **Agregar a 360: los chips no se cortan** (`desplazamientoParaLaElegida`, en `ds/FilaDeCategorias.kt` desde la vuelta 46: vale también para Revisar aviso). La fila se corría lo justo
   para la elegida y dejaba «+ Nueva» partida contra el borde. Ahora, si correr lo justo parte una pastilla,
   se corre hasta esconderla entera (la primera visible queda entera, a un aire del borde); si la elegida es
   la última, la fila agrega aire al final para poder llegar.

Lo vigilan, en `commonTest`, `TeQuedanEnHoyTest` (la alarma y las barras, puras),
`LasRespuestasDeHoySePonenViejasTest`, `LaFacturaPagadaOfreceLoQueEsTest` y `LaFilaDePastillasNoCortaTest`;
en Robolectric, `UnaSolaAlarmaTest` (una alarma, a lo sumo una barra en alerta, el enlace a «En qué se
va»), `TeQuedanEnHoyEnPantallaTest` (Pagos y el Inicio, la misma cifra), `LaGraficaAbreSinElegirTest` y
`LosChipsNoSeCortanA360Test`; en `:core`, `HastaDondeSeProponeTest`; y en `:server`,
`ElEmparejamientoPreguntaMasTest` (uno, dos, tarde, fuera del período, otra categoría).

## 51. Vuelta 47: Traspaso y Cuota hablan como Gasto, y dos arreglos de la 46

La revisión de la 46 (r46-10): Traspaso y Cuota eran otra hoja dentro de Agregar —«NOTA (OPCIONAL)» en
mayúsculas, el monto en un campo chico, la fecha en una caja «Hoy», «Bancolombia Ahorros …» cortado al
lado de «Desde» y dos líneas de ayuda debajo del botón—. **Sin tocar las dos patas** ni lo que se
guarda (`transferRequestFor`, `pagoRequestFor`, los mismos ids del borrador).

1. **La misma estructura que Gasto e Ingreso** (`ui/quickadd/TransferForm.kt`):
   - **La cifra protagonista y la píldora de la fecha**: `CabezaDelMonto`, la cabeza de Agregar, ahora
     compartida (con el cursor de la vuelta 34 sin teclado en pantalla). La fecha abre el mismo
     selector.
   - **El teclado de Gasto**: `TecladoNumericoDeAgregar`, compartido. El monto de Traspaso y Cuota vive
     ahora en la hoja (`montoDelTraspaso`, como el de Gasto), así que **el teclado físico** también lo
     escribe (`alTeclear`: dígitos, borrar y Enter, que guarda si el botón se puede tocar); un campo de
     texto propio con el foco —el interés, lo que bajó la deuda en otra moneda— se queda con sus teclas
     (`onCampoDeTextoConFoco`). `conLaTecla` es la regla de una tecla, la misma para los dos montos.
   - **Las dos puntas** (`FilaDeCuentaDelTraspaso`): una `Fila` con la marca de la cuenta, **su nombre
     entero de título** (dos renglones si hace falta) y el rótulo con su porqué debajo —«Desde · Por
     defecto»—; en Cuota, «Sale de» y «Pagas». El selector es el de siempre, con la fila de cuenta de
     Agregar. Sin «Desde:» pegado al valor.
   - **La nota**: la fila «Nota · Agregar nota…» de Gasto, con su sub-editor (`NoteEditor`).
   - **Una sola línea de ayuda** debajo del botón: lo que falta o, con todo listo, «Se guarda en línea:
     las dos puntas, juntas.» (`ayudaDelTraspaso`), con su alto reservado.
   - **Cuota conserva lo suyo** con los mismos componentes: lo que bajó la deuda en otra moneda, el
     interés de este mes y el desglose, debajo de la tarjeta.
   - **`LosRotulosNoGritanTest` no atrapó «NOTA (OPCIONAL)»** porque solo miraba los componentes de
     rótulo (`Seccion`, `GrupoDeCampos`, `RotuloDeCampo`) y los parámetros `rotulo`/`label`, y ese era un
     `Text(…)` a mano. Ahora mira también el texto literal de un `Text(…)` y los parámetros `text`,
     `texto`, `titulo` y `subtitulo`; atrapó otro, «ACCIÓN» en el editor de pantallas (ahora «Acción»).
2. **La hoja del lote**: «Total $57.100 · 2 avisos quedan para revisar uno por uno» (`detalleDeLaConfirmacion`,
   con su separador; iban pegados).
3. **La tarjeta de «Otros» con las pastillas abiertas**: «Elegir categoría» pasa a **«Cerrar»**
   (`CERRAR_LAS_CATEGORIAS`) y deja el amarillo de la acción (`Secundario`), como «Cambiar» → «Cerrar»
   en Revisar aviso.

Lo vigilan `TraspasoYCuotaComoGastoTest` (la cifra, la píldora, las filas con el nombre entero, la nota
y una sola ayuda; Traspaso y Cuota caben a 360; guardan lo mismo: el traspaso con su monto y sus dos
cuentas, la cuota con su deuda y sin interés escrito), `LosRotulosNoGritanTest` (los `Text` sueltos),
`ActividadPorDentroTest` (el separador) y `ElOtrosSeResuelveEnLaTarjetaTest` («Cerrar», `Secundario`);
`HojaDeCuotaConInteresRealTest`, `SelectoresDeTraspasoTest` y `PagarUnaCuotaDesdeElChecklistTest`
escriben ahora el monto con el teclado y tocan las filas por su tag.

## 52. Vuelta 48: Cuota es para pagar la cuota, y una factura pagada no ocupa lugar

Las revisiones de las vueltas 45 y 47 (28-oct, `r45-03`, `r47-04` contra `r47-06`). **Sin reglas de
plata nuevas**: la cuota sigue siendo dos patas (`pagoDeCuotaLegs`), el abono un traspaso puro y el
desembolso plata que entró.

1. **En Cuota, tocar un crédito es pagar su cuota** (`ui/quickadd/TransferForm.kt`). La sección «Créditos»
   de «¿Qué estás pagando?» decía «Elige uno para registrar el desembolso que te entró…» en una pestaña que
   se llama Cuota, con un botón «Registrar pago».
   - **El encabezado de la hoja** (`encabezadoDeLaCuota`, `cuerpoFuerte` centrado, un renglón): «Cuota de
     Libre inversión 9695 · octubre», «Abono extra a …», «Pago de Nu · octubre», o sin nada elegido «La cuota
     de un crédito o el pago de una tarjeta». El mes es el del período del dueño (`mesDelPeriodoDeHoy`) o,
     desde Pagos, el de la fila (`presetMesDeLaCuota`).
   - **La cifra es la cuota** (`cuotaPropuesta`, la de las condiciones del crédito), la fecha hoy, y «Sale de»
     la cuenta del débito automático si la hay (rótulo «Sale de · Débito automático»). Lo que el dueño
     escribió no se pisa.
   - **El abono extra es la opción secundaria**: un `Boton` `Fantasma` `S` debajo del renglón de la deuda,
     «Es un abono extra, no la cuota» (y de vuelta «Es la cuota del mes»). En abono la hoja dice qué es
     (`QUE_ES_UN_ABONO_EXTRA`), sin el interés ni el desglose, y el botón es «Registrar abono».
   - **El desembolso ya no se nombra en Cuota** (`AYUDA_DE_CREDITOS_EN_CUOTA`: «Elige el crédito para anotar
     su cuota de este mes.»). Sigue en **Traspaso**, con el crédito en «Desde», donde la ayuda de «Créditos»
     es la de siempre (`AYUDA_DE_CREDITOS_EN_TRASPASO`), y en el alta del crédito.
   - **Desde Pagos**, «Anotar pago» de una cuota abre exactamente este modo (`hojaParaAnotar`), con el mes de
     su fila.
2. **La hoja de Cuota no salta.** El renglón «Deuda de X: $A pasa a $B» existía recién con una deuda elegida
   y estiraba la hoja ~70 px. Ahora está **siempre** en Cuota, `apoyoMedio` con **dos renglones reservados**
   (`minLines = 2`): vacío sin deuda, «Deuda de Nu: $1.683.000» al elegirla y «… pasa a $1.583.000» con el
   monto. La frase del desglose de un crédito reserva tres (`minLines = 3`). Elegir un crédito suma lo suyo
   —el interés y el enlace del abono—: es cambiar de modo, no escribir.
3. **La factura pagada es compacta** (`FilaDeFacturaPagada`, `ui/recurrentes/FacturasDelPeriodo.kt`). Medía
   más que una pendiente, con «Ver movimiento» y «Quitar la marca» como dos botones con borde apilados.
   - Un renglón: el ícono de su categoría, el nombre y **«Pagado ✓ · $1.800.000 · 27 oct»** («Pagado» en
     `apoyoMedio` color `entra` con el check, el resto `apoyo` `textoMedio`; `resumenDeLaFacturaPagada`).
   - **Tocarla abre el movimiento** que la pagó (sin movimiento, la regla, como siempre).
   - **El «⋯»** (`BotonDeIcono` `MoreHoriz`, «Más opciones de Arriendo») guarda «Quitar la marca» y, solo en
     lo que Movi emparejó sola, «No fue este» (`accionesDelMenuDeLaFilaPagada`), con la frase de dónde salió
     el «Pagado» arriba. Sin nada que ofrecer —la cuota que probó su pago—, no hay «⋯».
   - Se fueron el enlace «Movimiento del 25 oct» y la píldora «Pagado» de la derecha.

Lo vigilan `CuotaDeUnCreditoTest` (el modo «Cuota de X · mes» con su cuota y el débito automático; la
ayuda sin desembolsos; el abono que guarda un traspaso; el desembolso en Traspaso; **el alto estable** al
elegir la tarjeta, al llegar la cifra y al borrar y escribir el monto de un crédito, a 8 dp),
`PagarUnaCuotaDesdeElChecklistTest` (desde Pagos, el mismo modo con el mes de la fila),
`LaFacturaPagadaEsCompactaTest` (un renglón sin botones, que no mide más que una pendiente; tocarla abre
el movimiento; «Quitar la marca» y «No fue este» en el «⋯»), en `commonTest` `LaCuotaDelCreditoTest` y
`LaFacturaPagadaOfreceLoQueEsTest`, y en `:server` `PagoDeCuotaSaldaElPeriodoTest` (las dos patas saldan
el período; el abono no). `HojaDeCuotaConInteresRealTest` ya no escribe la cuota: llega puesta.

## 53. Vuelta 50: la cuota de un crédito se ve como lo que es, un gasto

La revisión del arreglo #541 (28-oct, `r49-06`): en «Últimos movimientos» del Inicio la «Cuota de crédito
$980.000» salía como un traspaso —las flechas, sin signo y en gris azulado—, igual que «Traspaso $50.000» y
«Pago de tarjeta $100.000». Por la regla del dueño **la cuota sí cuenta como gasto** («es plata que
salió») y el pago de tarjeta no, así que mostrarla neutra decía lo contrario. **Sin reglas de plata
nuevas**: la presentación sigue a `isCashFlow`.

1. **Una sola función decide** (`core/.../ComoSePresenta.kt`): `comoSePresenta(evento, tipoDeCuenta)` →
   `GASTO`, `INGRESO` o `NEUTRO`, con `countsAsCashFlow` y `isCashFlow` (sin el tipo de cuenta, como una
   cuenta de activo). No mira la moneda ni «Por confirmar»: un cargo en dólares o un aviso sin confirmar
   se ven como gasto, con su píldora. Para un par, `laPataQueCuenta` / `comoSePresentaElPar`: la pata que
   sale y después la que entra, la primera que no sea neutra.
   - `esGastoDelPeriodo` (la regla de «Gastos», «En qué se va» y el **«Gastaste» del día**) es ahora
     `comoSePresenta == GASTO` más pesos y confirmado, y el «Entró» del día `== INGRESO` con lo mismo. La
     fila y su total salen de la misma función: el «Gastaste» es la suma de las filas con «−».
2. **La fila pinta lo que cuenta**, en todas las listas:

   | | Antes | Ahora |
   |---|---|---|
   | Cuota de crédito (par) | flechas, «$980.000» gris azulado | ícono de «Cuota de crédito», **«−$980.000» en tinta** |
   | Desembolso (par) | flechas, «+$X» verde | ícono del desembolso, «+$X» verde |
   | Pago de tarjeta, traspaso, abono extra | flechas, sin signo, gris azulado | igual |

   - **Actividad e Inicio** (`TransferRow`, `tonoDelRenglon`): la cifra es la de la pata que cuenta
     (`pataQueSeMuestra`: la cuota entera que salió, no el abono a capital) y el ícono, el de su
     categoría. En el chip «Gastos» la pata del dinero ya iba suelta con «−»; la del crédito no entra.
   - **Buscar** (`unaPataPorPar`): de un par se lista la pata que cuenta. Antes se quedaba la primera, y
     una cuota podía salir por la pata del crédito, sin signo y por el abono a capital.
   - **El detalle de un período** («Los más grandes»): la cifra de la fila de un movimiento
     (`CifraDelMovimiento` con `tonoDelEvento`), «−$980.000»; antes iban sin signo.
   - **El detalle de una cuenta no cambia**: ahí la fila pinta el signo de lo que movió el saldo de ESA
     cuenta (vuelta de diseño 32, el total del día es la suma de sus filas). En la cuenta de dinero la
     cuota ya era «−$980.000»; en la del crédito su pata es «+» lo que bajó la deuda (el capital).
   - Un desembolso guardado antes de su categoría («Traspaso») sigue llamándose «Desembolso» por los tipos
     de cuenta, pero se pinta neutro: no suma en «Entró» (la migración de datos lo pasa a
     `DESEMBOLSO_CATEGORY`).
3. **El «Gastaste» del día ya incluía la cuota y excluía el pago de tarjeta y el traspaso** (vuelta 40,
   `resumenDelDia` sobre `esGastoDelPeriodo`); ahora además comparte la función con la fila.

Lo vigilan `ComoSePresentaTest` (`:core`: cuota, pago de tarjeta, traspaso, desembolso y abono con las patas
de `pagoDeCuotaLegs` y `transferLegsFor`), `ComoSePresentaLaFilaTest` (`commonTest`: el tono y el texto de
cada par, «Gastaste» = la suma de las filas con «−», el chip «Gastos» y Buscar) y `LaCuotaSeVeComoGastoTest`
(Robolectric: en Actividad y en el Inicio la cuota dice «−$980.000», el pago de tarjeta y el traspaso no
llevan signo, y el día dice «Gastaste $980.000»). `ColorYEntreCuentasTest` y `DesembolsoEnMovimientosTest`
pasan la cuota de entre cuentas a gasto (y derivan `countsAsCashFlow` como el server).

## 54. Vuelta 51: «Cambiar» en la tarjeta, las categorías internas fuera de los selectores, y los avisos viejos se apartan

La segunda pasada de «un día del dueño» (28-oct, sobre master 8951c0bb): ya no hay resultados equivocados,
pero «Cambiar» y «Elegir categoría» se comportaban distinto, «Cuota de crédito» aparecía en el selector
completo entre Comida e Internet, el Rappi del 2 de septiembre seguía en «Por revisar» dos meses después y
«Revisar» del Carro proponía un tanqueo de $260.000 para una factura de $9.000. **Sin reglas de plata
nuevas**: el punto 2 protege una que ya existía.

1. **«Cambiar» se resuelve en la misma tarjeta** (`TarjetaDeMensajeDelBanco`, `TAG_CAMBIAR_EN_LA_FILA`). En
   un aviso que Movi entendió, «Cambiar» ya no abre Revisar aviso: abre en la tarjeta las mismas pastillas
   que «Elegir categoría» (`ds/FilaDeCategorias`, relleno `Ninguno`, «Todas ›» en el renglón del rótulo, la
   categoría que entendió Movi marcada, `EligeLaCategoria.actual`) y pasa a «Cerrar» (`CERRAR_LAS_CATEGORIAS`).
   Al elegir, la tarjeta queda con la categoría nueva arriba («Salud · Bancolombia Ahorros 4057») y
   «Confirmar»; también entra en «Confirmar los N» (`categoriasElegidasAca`, como en la vuelta 46). Debajo de
   las pastillas, **«Más cambios ›»** (`Boton` `Fantasma` `S`, `MAS_CAMBIOS`, `TAG_MAS_CAMBIOS_EN_LA_TARJETA`)
   lleva a Revisar aviso para la cuenta, el monto o el comercio (`Screen.SMSReconcile` sin `abrirCategoria`).
   Las pastillas salen de `categoriasDeLaTarjeta` (las frecuentes de Agregar o, sin uso a mano, las del
   dueño). El pago avisado varias veces (`TarjetaDelMismoPago`) sigue yendo a Revisar aviso.
2. **Las categorías internas no se ofrecen** (`:core` `CategoriasQueNoSeEligen.kt`). Una sola lista,
   `CATEGORIAS_QUE_NO_SE_ELIGEN` = las reservadas (`RESERVED_CATEGORIES`: Traspaso, Pago de tarjeta, Saldo
   inicial, Ajuste de saldo…) + las de dos patas (`CATEGORIAS_DE_DOS_PATAS`: Cuota de crédito, Desembolso
   de crédito, Avance de tarjeta), con `esCategoriaQueNoSeElige` (sin mayúsculas ni tildes). La leen
   `categoriasQueCoinciden` / `categoriasFrecuentes` / `categoriaSirveParaTipo` (`CategoryField.kt`),
   `categoriasParaPastillas` —**también lo que la pantalla pide adelante**: la fila del movimiento dice su
   categoría, pero no se ofrece—, la hoja «Todas» (`contenidoDelSelectorDeCategoria`: escrita a mano se
   explica y no se crea), Agregar (`categoriaQueNoSeAnotaAca`; en **modo pago** la de la regla sí, solo las
   reservadas se cortan), Revisar aviso, la tarjeta, Repartir (`esCategoriaDeGastoSuelta`) y el detalle de
   un movimiento (`ofreceCategoriaEscritaAMano`, de donde sale también el lote). «Cuota de crédito» y
   «Desembolso de crédito» **siguen sin ser reservadas** (cuentan en el mes). La fila especial «Pago de
   tarjeta» del detalle se queda: es la acción explícita «esto fue el pago de mi tarjeta», no una categoría
   del selector. A su lado, **«Es la cuota de un crédito»** (`ES_LA_CUOTA_DE_UN_CREDITO`,
   `puedeMarcarseCuotaDeCredito`: un gasto suelto con una categoría elegible y algún crédito) reemplaza el
   camino de la Ola Y que pasaba por elegir «Cuota de crédito»: abre «¿De cuál crédito?»
   (`SelectorDeCuentaDeDeuda` con solo los LOAN) y llama a **`vincular-deuda` de siempre** con
   `VincularPagoDeDeudaRequest.esLaCuota = true` —el server acepta entonces un gasto de otra categoría,
   solo con un crédito—: las mismas dos patas de `pagoDeCuotaLegs`, y el período queda saldado. El server rechaza la cuota y el avance a mano con `motivoDeLaCategoriaDeDosPatas`
   (`CUOTA_CATEGORY_NOT_MANUAL`, `AVANCE_CATEGORY_NOT_MANUAL`): en `categoriaQueMoviEscribeSola` (el
   `PUT …/category`, el lote y las propuestas de Movi AI) y en el alta `POST /api/events` **a mano y nueva**
   (un aviso confirmado, `SMS`, y el reenvío de un movimiento que el server ya tiene pasan); el espejo local
   del teléfono, igual.
3. **Los avisos de más de 30 días se apartan** (`:core` `LosAvisosAntiguos.kt`). `esPagoAntiguo`: más de
   `DIAS_PARA_QUE_UN_AVISO_SEA_ANTIGUO` (30, elegido) desde la fecha del aviso más reciente del pago; una
   fecha ilegible nunca se aparta. En Por revisar van en **«Más antiguos · N»** (`Seccion` con «Ver» /
   «Ocultar», plegada), con «Llegaron hace más de 30 días y no cuentan en «Por confirmar».» y **«Ignorar los
   N»** (`Boton` `Secundario` `S`) que pregunta antes (`ConfirmarEnHoja`) e ignora uno por pago con el
   «Ignorar» de siempre: no se borra nada. No cuentan en `ConteoPorRevisar.total` (van en `avisosAntiguos`),
   ni en «Confirmar los N» (`avisosPorConfirmar` → `pagosRecientesPorRevisar`), ni en `pendingSms` /
   `porConfirmarSuman` del server; `DashboardSummary.avisosAntiguos` (default 0) los cuenta y la franja del
   Inicio los menciona aparte («En 1 aviso · y 2 antiguos»). Con solo antiguos la bandeja no está «al día».
4. **Una propuesta de pago muy distinta del monto no va de principal** (`:core` `ElCandidatoQueNoSeParece.kt`).
   `candidatoMuyDistinto`: más del 50 % (`DIFERENCIA_MAXIMA_DEL_CANDIDATO`, elegido) del monto de la regla,
   hacia arriba o hacia abajo; sin un esperado comparable (tarjeta, otra moneda, regla sin monto) no se
   afirma nada. `accionDelPago` y «Movi propone hoy» miran `candidatosParecidos`: con solo uno lejano el
   Inicio dice «Anotar pago», no «Revisar». En Pagos la fila prefiere un candidato parecido
   (`propuestaDeLaFila`); si solo queda uno lejano, no es «Por confirmar»: «Anotar pago» a la derecha y
   debajo el enlace **«¿Es el de $260.000?»** (`PropuestaLejana`, `TAG_PROPUESTA_LEJANA`),
   que abre la pregunta de siempre. **Nunca se tilda solo.** El enlace es corto a propósito: «Hay un
   movimiento de $260.000 · ¿Es este?» se cortaba a 390 dp antes de la pregunta (lo mide
   `ElEnlaceDelCandidatoCabeTest`, a 360 dp con la letra ×1,12).
5. **La cuota pagada también lleva su «⋯»** (revisión de la vuelta 51). Arriendo e Internet tenían «⋯» y la
   cuota de un crédito no. La cuota no se desmarca desde la fila —sus dos patas se deshacen anulando el
   movimiento—, así que su «⋯» ofrece «Ver movimiento» y dice cómo deshacerla (`NOTA_DE_LA_CUOTA_PAGADA`,
   `esPagoDeDeudaProbado`, `notaDelMenuDeLaFilaPagada`). Todas las filas pagadas tienen «⋯».

**Los toques** (desde el Inicio, tres avisos con Farmatodo en «Transporte»): el mal categorizado, **4** (la
franja, «Cambiar», la pastilla y el «Confirmar» de la tarjeta); con «Todas ›», **5**; los tres, **5** (la
franja, «Cambiar», la pastilla, «Confirmar los 3» y la hoja; eran 6).

**Las líneas base** no cambian y las guardas en 0 siguen en 0. Lo vigilan `LosAvisosDeUnVistazoTest` (los
toques, «Cambiar» sin salir de la lista, «Más cambios ›»), `LosAvisosViejosSeApartanTest` (plegada, fuera
del lote, «Ignorar los N» con su pregunta), en `commonTest` `Vuelta51Test` (**la guarda que recorre los
selectores**, los antiguos en el conteo y la franja, el candidato lejano), en `:core` `Vuelta51LogicaTest`,
y en `:server` `TransferRoutesTest` (la cuota a mano rechazada en las tres puertas) y `DashboardRoutesTest`
(los antiguos no cuentan ni suman).

## 55. Vuelta 52: la marca junto a la cifra es «Recurrente», y la nota del pago de tarjeta cabe en un renglón

1. **El glifo junto a la cifra no es «tiene par».** La revisión de la vuelta 50 lo leyó como ⇄ («la cuota
   y el pago de tarjeta lo llevan, el traspaso no»). Es `Icons.Rounded.Repeat` con
   `contentDescription = "Recurrente"` (`FinalDeLaFila`): sale cuando `nombreRecurrenteDe` reconoce que el
   movimiento se repite. La cuota de un crédito y el pago de una tarjeta se repiten por su forma; un
   traspaso a mano no (`NombreRecurrenteDeTest`: «una pata de traspaso nunca matchea»). **La regla queda
   como estaba**: la marca es «se repite», no «tiene par»; ponérsela al traspaso diría que se repite, y
   quitársela a la cuota borraría un dato. Que un movimiento tenga contraparte ya lo dicen su título y su
   subtítulo («De X a Y»), y los traspasos y pagos de tarjeta llevan las flechas (`SwapHoriz`) como ícono
   de entrada. Lo fija `Vuelta52Test`: tres pares en un día, dos marcas.
2. **La nota del pago de tarjeta del encabezado de un día** pasó de dos renglones («Además, $X fueron a
   pagar tarjeta — ya contado en tu gasto del mes cuando compraste, no se duplica.») a uno, apagado:
   **«+ $100.000 a tu tarjeta · no suma, ya contó al comprar»** (`textoPagoDeTarjetaEnElDia`). Si ni así
   cabe (360 dp con la letra ×1,12) baja a **«+ $100.000 a tu tarjeta»** (`textoCortoDelPagoDeTarjeta`),
   siempre en un renglón (`maxLines = 1`, sin salto de alto); la frase entera va en el
   `contentDescription` (`explicacionDelPagoDeTarjeta`). No hay otra nota parecida en ese encabezado
   (los traspasos y abonos no tienen). Lo vigilan `PagoDeTarjetaEnElDiaTest` y `Vuelta52Test`.

## 56. Vuelta 53: la cuota que se parece se sugiere sola, la segunda del mes pregunta, y los botones de avisos caben

La revisión de la vuelta 51 (28-oct, sobre eb3f29f4, `r51b-*`): «Revisar uno por» cortado a 390 dp, «Es la
cuota de un crédito» escondido al fondo del selector, una segunda cuota de octubre de Vehículo 4083 que se
vinculó sin avisar, y un crédito que decía «A favor $980.000». **Sin reglas de plata nuevas**: la cuota
sigue siendo `pagoDeCuotaLegs`, el abono extra el traspaso de `transferLegsFor`, y un pago salda la cuota
del período en que se hizo (`periodoQueSalda`).

1. **Los botones de la tarjeta de avisos nunca se cortan** (`TarjetaDeAvisosDelBanco`, `LadoALadoOApilados`).
   El `FlowRow` con `weight` de la vuelta 40 decidía el renglón con el ancho **mínimo** de cada botón (la
   palabra más larga) y después los apretaba lado a lado: a 390 dp «Revisar uno por uno» quedaba en «Revisar
   uno por». Ahora un `Layout` mide el ancho entero de cada rótulo (`maxIntrinsicWidth`): si la suma y la
   separación caben, van lado a lado y se reparten lo que sobra (llenan el ancho, como antes); si no, uno
   debajo del otro a todo el ancho. Lo mide `LosBotonesDeLosAvisosCabenTest` a 360 y 390 dp con la letra
   ×1,12 y ×1,3 («Confirmar los 2», «Confirmar los 2 que Movi entendió», «Revisar uno por uno»), y a 600 que
   siguen lado a lado.
2. **«¿Es la cuota de Vehículo 4083?» en el detalle de un gasto** (`:core` `LaCuotaQueSeParece.kt`,
   `SugerenciaDeLaCuota`). Arriba de las acciones, en la superficie de propuesta (ícono en el tinte de aviso,
   la pregunta en `cuerpoFuerte`, «Cuota de $980.000» debajo y **«Sí, es la cuota»** `Secundario` `S` en su
   renglón, a la derecha: al lado no cabía a 360). Aparece cuando `cuotaQueSeParece` encuentra **un solo**
   crédito cuya cuota pactada está a ±10 % (`MARGEN_DE_LA_CUOTA_QUE_SE_PARECE`, elegido) del gasto, en su
   moneda, y cuyo vencimiento **sin saldar** cae en el período del dueño del gasto. `cuotasPorSaldar` junta
   el vigente de `GET /api/payments/upcoming` **y** la cuota que `GET /api/payments/occurrences` da por
   pendiente (`occurred = false`, la fuente del checklist desde #541): pasada la gracia `upcoming` ya rodó al
   mes siguiente —la cuota del 5, vista el 28, dice noviembre— y sin `occurrences` la cuota vencida, el caso
   más común, no se sugería (revisión de la vuelta 53). Solo un gasto
   suelto con una categoría del dueño. Con dos parecidos, ninguno. **La palabra «crédito» o «cuota» en la
   descripción no cuenta**, ni sola ni para desempatar: «Cuota de manejo» es un cobro de la tarjeta y «Cuota
   3/12» una compra diferida. «Sí, es la cuota» hace exactamente lo de «Es la cuota de un crédito» con ese
   crédito. Si algo falla al leer, no se sugiere nada. **«Es la cuota de un crédito» también está en «Más»**
   (la misma fila y el mismo «¿De cuál crédito?»), que ahora aparece también por eso.
3. **La segunda cuota del mismo período pregunta antes.** `vincular-deuda` con `esLaCuota` sobre un crédito
   cuya cuota del período donde caería el pago ya saldó **otro** movimiento (`periodoQueSalda` +
   `periodosSaldados`, sin las patas del mismo `transferId`: un reintento no choca consigo mismo) contesta
   **409** con `CuotaYaPagada` (`codigo = CUOTA_YA_PAGADA`, el `periodo` de la cuota, el crédito y la frase)
   y no escribe nada. El detalle lo reconoce (`cuotaYaPagadaDe`) y pregunta en línea, donde se tocó:
   **«La cuota de octubre de Vehículo 4083 ya está pagada. ¿Este pago es un abono extra?»**, con «Cancelar» y
   **«Un abono extra»** (`Principal`). El abono es `vincular-deuda` con `comoAbono`: el server arma **el
   traspaso de siempre** (`transferLegsFor`, «Abono extraordinario a …», las dos patas en «Traspaso», el monto
   entero contra el capital) sobre la pata que ya existía, y no salda ninguna cuota. **No se ofrece «la cuota
   de noviembre (adelantada)»**: no existe ese camino —un pago salda la cuota del período en que se hizo,
   decisión del dueño— y no se inventa. Desde el detalle, todo vínculo a un crédito va con `esLaCuota`, así
   que también la Ola Y (un gasto «Cuota de crédito» desde «Más») pregunta. Revisar aviso hace la misma
   pregunta desde la vuelta 56 (§59).
4. **Un crédito nunca dice «A favor» en «¿De cuál crédito?»** (`textoDeCuentaDeDeuda`). Con deuda, «Debes
   $X»; un crédito en 0 o por debajo, **«Movi no tiene el saldo»** (`SIN_EL_SALDO_EN_EL_SELECTOR`, como la hoja
   de Cuota de la vuelta 49); una tarjeta en 0 o pagada de más, **«Sin deuda»**. El selector de cuentas de
   Agregar no cambia: ahí una tarjeta sobrepagada «A favor» es un dato real (`SelectorDeCuentaTest`).

Lo vigilan `LosBotonesDeLosAvisosCabenTest`, `LaCuotaQueSePareceEnElDetalleTest` (la sugerencia vincula
como «Es la cuota»; sin cuota parecida no hay nada; el 409 pregunta sin «adelantada» y «Un abono extra»
manda `comoAbono`; «Es la cuota de un crédito» en «Más»), en `commonTest` `ElSaldoDeUnCreditoEnElSelectorTest`,
en `:core` `Vuelta53LogicaTest` (los bordes del 10 %, el período, dos parecidos, la palabra que no cuenta, la
frase y el código del 409), y en `:server` `VincularPagoDeDeudaRoutesTest` (de punta a punta: la cuota de agosto
saldada, la segunda responde 409 sin escribir, como abono queda un traspaso de $980.000 que no salda nada, los
reintentos de los dos contestan 200; el abono no vale con una tarjeta).

## 57. Vuelta 54: el primer uso, sin listas que no cuadran ni pasos sin salida

La revisión de primer uso del 28-oct (una cuenta nueva, «Ana Prueba», sobre master ad4bdfa3): llegar a un
«Te quedan» con sentido costaba 6 pantallas y unos 14 toques. El resultado era correcto; lo que costaba era
el camino.

1. **Una sola lista de primeros pasos** (`:core` `PrimerosPasos.kt`). `primerosPasos(LoQueYaHay,
   CapturaDelPrimerUso)` da los pasos de la plataforma, en orden, hechos o pendientes: **Tu período** (el
   corte no es el mes de calendario, `periodoElegidoEn`), **Tus cuentas** (alguna de dinero), **la
   captura** y **Lo que pagas cada mes** (alguna regla de verdad, sin cuotas ni pagos de tarjeta). El
   contador (`contadorDeLosPrimerosPasos`, «2 de 4»), «Te faltan N pasos» (`textoDeLosPasosQueFaltan`) y
   el paso donde abre la guía (`primerPasoPendiente`) salen de la misma lista. La usan la guía
   (`pasosDeLaGuia`: Bienvenida + la lista + Listo), la fila del Inicio, Tú → «Primeros pasos»
   (`ListaDePrimerosPasos`) y el resumen del final (`lineasDelResumen(lista, …)`: lo hecho sale de la
   lista, el resumen solo pone el detalle). Con lo que el Inicio leyó la arma `primerosPasosDe`; mientras
   no se puede afirmar (las lecturas de siempre y el perfil), es `null` y no se pinta nada.
   **Quedaron afuera**: «Si tienes préstamos o tarjetas, cárgalos» (quien no tiene deudas no lo podía
   tildar nunca; las tarjetas van en el paso de las cuentas y los préstamos en Créditos), «Registra un
   movimiento» (es el uso de todos los días, no algo que se prepara; en la web y en iOS lo cubre la
   captura) y «Sube un extracto» como hábito sin tilde (en la web y en iOS es el paso de la captura; en
   Android sigue en «Subir comprobante»).
2. **Ningún paso sin salida.** La captura depende de la plataforma (`capturaDelPrimerUsoEn`): en Android,
   los SMS y las notificaciones como siempre (hecha con el permiso o con un aviso que ya llegó); en la web
   y en iOS, **«Sube un extracto»** (`SUBIR_UN_EXTRACTO_EN_LA_GUIA`): el archivo va a `PapelesCompartidos`
   y la hoja «Movi está leyendo…» lo toma encima de la guía; se da por hecho con el extracto subido o con
   cualquier movimiento. El reenvío de correos va debajo **solo si el server tiene la dirección** (ya no
   «todavía no está disponible»). Sin poder elegir archivos (`NINGUNA`), el paso no está en la lista, así
   que el resumen nunca dice «Pendiente» de algo imposible («Sin activar todavía» en Android, «Sin subir
   todavía» en la web). **«Saltar este paso» en todos los pasos de en medio**, también en las cuentas.
3. **El héroe primero.** «Primeros pasos» ya no es la tarjeta de cinco renglones arriba de «Te quedan»: es
   **una fila** debajo de las acciones (`FilaDePrimerosPasos`, `TAG_FILA_DE_PRIMEROS_PASOS`): «Te faltan
   3 pasos» con «Sigue con «Tus cuentas»», que abre la guía en ese paso (`Screen.PrimerUso(desde = …)`), y
   una × (`DESCARTAR_PRIMEROS_PASOS`) que la oculta (`PrimerosPasosDescartados`, en
   `PropuestasDescartadasStore`: se limpia al cerrar sesión). Con todo hecho no se pinta. Vive en
   `SduiRenderer(despuesDeLasAcciones = …)`: debajo de `ACCIONES`, o del héroe si la definición no trae la
   cuadrícula. Es chrome nativo: la definición SDUI no cambia y un APK viejo sigue con su tarjeta.
4. **El período se explica.** El paso del período muestra la misma `VistaPrevia` de la hoja de Tú
   («Tu período: del 23 de octubre al 24 de noviembre» + el detalle de un 29-31 o del corte que la regla
   corre); era «Hoy estarías en noviembre de 2026». El encabezado del Inicio, con un corte a mitad de mes,
   dice **el rango corto** (`rangoCortoDelPeriodo`: «23 oct – 24 nov», en un renglón a 360 dp con la letra
   ×1,3) y el rango entero en su `contentDescription` (`descripcionDelPeriodoDelEncabezado`); con el mes de
   calendario sigue el nombre («Octubre 2026»). Dice lo mismo que el «Del … al …» de Pagos.
5. **El paso de las cuentas deja lo esencial a la vista.** `FormularioDeCuenta(enUnaFila = true)`: los
   bancos y los tipos van cada uno en una fila de chips que se desplaza de lado (`TAG_BANCOS_DE_UN_TOQUE`,
   `TAG_TIPOS_DE_CUENTA`), y **«Agregar cuenta» va dentro de la tarjeta del formulario, debajo de «Saldo de
   hoy»** (`TAG_AGREGAR_CUENTA_EN_LA_GUIA`, `Secundario`). En la revisión iba fijo en el pie, justo debajo
   de «Tarjetas de crédito · Opcional…», y se leía como agregar una tarjeta; la sección de tarjetas lleva
   su propio «Agregar una tarjeta». Las cuentas ya creadas van debajo del formulario. «Nueva cuenta» de
   Patrimonio sigue con los chips en varios renglones.
6. **Lo escrito no se pierde** (revisión de la vuelta 54, `BorradoresDeLaGuia.kt`). Con el arriendo
   escrito, «Guardar» iba adentro con borde y «Siguiente» apagado: la revisión tocó «Saltar este paso»
   creyendo que avanzaba y el arriendo se perdió. Cada formulario de un paso es un `Borrador` (la cuenta,
   la tarjeta, cada pago de cada mes abierto —cerrarlo no lo borra—) y el pie lo mira: con algo escrito y
   completo, el principal es **«Guardar y seguir»** (`GUARDAR_Y_SEGUIR`: guarda todo lo escrito con la
   creación de siempre y avanza); incompleto, el pie dice qué falta en una línea centrada
   (`razonDelPie`: «Falta el día del arriendo», `TAG_RAZON_DEL_PIE`); y «Saltar este paso» o «Ahora no»
   preguntan antes con `ConfirmarEnHoja` (`preguntaDeDescartar`: «¿Descartar el arriendo que
   escribiste?»). Los «Guardar» de adentro siguen, para agregar varios.

**Las líneas base** no cambian y las guardas en 0 siguen en 0. Lo vigilan `PrimerosPasosTest` (`:core`: los
estados, el contador, la captura por plataforma y el rango corto), `GuiaDePrimerUsoTest` (`commonTest`: los
pasos de la guía son los de la lista, sin captura se saltan, todos se saltan, el resumen toma lo hecho de la
lista y nunca dice «Pendiente» de algo imposible, `primerosPasosDe` con los datos del Inicio),
`ElPrimerDiaTest` (el héroe y las acciones antes de la fila, la fila abre la guía en el paso y la × la
oculta, la lista de Tú, el encabezado con el rango corto), `PrimerUsoEnPantallaTest` (la vista previa, el
extracto en la web, «Saltar» en las cuentas, la guía abierta a mitad de camino con lo que ya había),
`ElPasoDeCuentasALaVistaTest` (a 390 × 844 y 360 × 760, el nombre, el saldo y «Agregar cuenta» sin
desplazar, el botón debajo del saldo y antes de las tarjetas; bancos y tipos en una fila), los de «lo
escrito no se pierde» en `PrimerUsoEnPantallaTest` (llenar el arriendo y tocar el principal lo guarda;
a medias, el pie dice qué falta; saltar pregunta, «Cancelar» lo conserva y «Descartar» lo tira; la
cuenta escrita se guarda con el principal) y `InicioDelRedisenoTest` / `MasScreenTest`, ajustadas.

## 58. Vuelta 55: accesibilidad — la web lo dice, el foco se ve, el gris pasa, 48 de toque y la letra al 130 %

La revisión de accesibilidad del 28-oct (sobre `ad4bdfa3`) encontró seis cosas. Lo que se hizo:

1. **La web no tiene árbol de accesibilidad, y ahora lo dice.** Toda la app es un `<canvas>`, y Compose
   Multiplatform **1.8.2** (la de Movi) no le pasa su semántica al DOM: su `ui-wasm-js` no trae nada de
   accesibilidad web. La **1.9** sí la trae, experimental (`ComposeViewport(configure = { isA11YEnabled =
   true })`, que arma un árbol de `div` con `aria-label`, rol y clic encima del canvas), pero solo con
   `ComposeViewport` —`CanvasBasedWindow` queda deprecado con error y «no soporta A11Y»— y subiendo todo
   Compose (Android e iOS incluidos) de versión: no es una opción ni una actualización menor, queda para su
   propia vuelta. Mientras tanto: el canvas lleva `role="application"`, `aria-label="Movi"` y
   `aria-describedby` hacia un aviso oculto a la vista (`.solo-lector`, no `display: none`) que es lo
   primero del `<body>`: «Movi en la web todavía no funciona con lectores de pantalla. Usa la app de
   Android.» El mismo texto (`LECTOR_DE_PANTALLA_EN_LA_WEB`) va como nota de Ajustes → «Cómo se ve», solo
   donde `elLectorDePantallaLeeLaApp` es falso (la web). Lo vigila `LaWebDiceQueNoTieneLectorTest`.
2. **Los formularios ocultos no estorban.** El overlay del ingreso (`login-form`, `forgot-form`,
   `reset-form`, `register-form` y los cinco «Mostrar contraseña») arranca con `class="hidden" inert
   aria-hidden="true"`, y lo muestra y lo esconde **solo** `window.ponerElIngreso(visible)`, que pone y
   quita la clase, `inert` y `aria-hidden` juntos. Las dos entradas (sin sesión y con `?reset=…`) lo
   muestran con ella; la red de seguridad y el navegador sin WasmGC lo esconden con ella. Cerrar sesión
   recarga la página (`reloadForLogout`), así que volver al ingreso pasa por lo mismo. Lo vigila
   `LosFormulariosOcultosNoEstorbanTest`.
3. **El anillo de foco** (`ds/AnilloDeFoco.kt`). `Modifier.anilloDeFoco(forma)` dibuja, con el foco del
   **teclado** (`seVeElAnilloDeFoco`: enfocado y `InputMode.Keyboard`, el `:focus-visible` de la web),
   un anillo de `Medida.trazoDelAnilloDeFoco` (2) en la tinta a `Medida.separacionDelAnilloDeFoco` (2) del
   borde, con la forma del componente. Va en la cadena **antes del `clip` y del toque**. `adentro = true`
   lo dibuja hacia adentro donde un recorte se lo comería (la `Fila` dentro de su superficie, las
   pestañas, el segmentado); `alrededorDe` lo centra en lo que se ve (la `Casilla`: 24 en un toque de 48);
   `deLoDeAdentro` lo prende cuando el foco está en algo de adentro (la caja de un `Campo` y la de
   búsqueda). Lo llevan `Boton`, `BotonDeIcono`, la acción del encabezado, `Fila`, `PildoraDeFiltro`,
   `PildoraDeAccion`, `AccionRapida`, `Casilla`, `Campo`, `CampoDeBusqueda`, la acción de una `Seccion`,
   `Superficie` tocable, `renglonTocable`, `TarjetaDeCuenta`, «Todas ›» de `FilaDeCategorias`, las
   pestañas (barra y rail), el avatar y el `SelectorSegmentado`. **Revisión:** también el avatar, el ojo y
   la campana del encabezado del Inicio (`EncabezadoDelInicio`), recortados al círculo; el anillo entra
   entero en los 56 del encabezado sobre el amarillo. `ElFocoSeVeConElTecladoTest` (el
   enfocado por teclado lo lleva, con el dedo no) y `TodoLoEnfocableDibujaElAnilloTest` (`:core`, en
   **cero**: un toque de `ds/` o de las pestañas sin anillo en su cadena la rompe).
4. **`neutro` pasa de `#A39A8C` a `#857C6F`**: el gris de los gráficos daba 2,78:1 sobre el blanco y 2,47
   sobre el crema; ahora 4,11 / 3,65 (y 3,88 sobre la capa 2, 3,52 sobre la 3, 3,43 sobre el `hilo`).
   `ContrasteDeLosTokensTest` lo mide con el mínimo de gráficos (3:1). La página compartida copia el
   valor (`PaginaCompartidaTest`); el widget no usa `neutro`.
5. **Nada se toca con menos de 48 × 48** (`NadaSeTocaConMenosDe48Test`): Inicio, Pagos, Por revisar,
   Actividad y Agregar montados con datos a 360 dp; cada nodo que se toca tiene un área de toque
   (`touchBoundsInRoot`: lo que se ve más la extensión de Compose a `minimumTouchTargetSize`) de al menos
   48 por lado. Un enlace en línea («Ahora no», «Todas ›», «y 2 más») se queda del tamaño de su texto y
   se toca en 48: lo que la revisión midió a ojo era lo visible.
6. **La letra al 130 %** (`ElTextoAl130NoSeCortaTest`, 360 dp, `fontScale = 1.3`): el héroe, las ocho
   acciones y «Próximo pago» de Inicio, la fila de una factura con «Anotar pago», la tarjeta de un aviso
   en Por revisar (`TAG_TARJETA_DE_AVISO`) y Agregar en Gasto: ningún «…», ningún renglón de más, nada
   más ancho que su caja ni fuera de la pantalla (una fila que se desplaza de lado sí puede seguir afuera).
7. **Revisión: «Tu período» solo se le pide a una cuenta nueva y vacía.** La vuelta 54 lo daba por hecho
   solo con un corte distinto del mes de calendario, así que una cuenta en uso que se quedó con el mes de
   calendario veía «Te falta 1 paso · Sigue con «Tu período»» para siempre. `periodoHechoEn` (`:core`)
   lo da por hecho también si la cuenta ya estaba en uso (`laCuentaYaEstabaEnUso`: movimientos, alguna
   cuenta o `primerUsoEn` puesto); el Inicio y la guía usan la misma regla. Lo vigila `PrimerosPasosTest`.

## 59. Vuelta 56: cabos sueltos de la 53 y la 55

**Sin reglas de plata nuevas**: la cuota sigue siendo `pagoDeCuotaLegs`, el abono extra el traspaso de
`transferLegsFor`, y un pago salda la cuota del período en que se hizo.

1. **Revisar aviso también pregunta por la segunda cuota del mes.** La vuelta 53 (§56) hizo que
   `vincular-deuda` preguntara, pero Revisar aviso confirma una cuota por las dos patas
   (`DosPatasDelAviso`) y la segunda cuota del mes entraba en silencio. Ahora `pedidoDeDosPatas` manda
   **`esLaCuota`** en toda cuota, y `escribirLasPatasDelAviso` —después de la idempotencia, con la misma
   `cuotaYaPagadaDelPago` de `vincular-deuda`, ahora dentro de la transacción— contesta **409** con el
   mismo cuerpo `CuotaYaPagada`, por el aviso suelto (`/api/sms/{id}/confirm`) y por el pago avisado
   varias veces (`…/grupo/{id}/confirmar`); no escribe nada y el aviso sigue pendiente. La pantalla lo
   reconoce (`cuotaYaPagadaDe`) y, **en el lugar de los botones**, pregunta «La cuota de octubre de
   Vehículo 4083 ya está pagada. ¿Este pago es un abono extra?» (`TAG_CUOTA_YA_PAGADA_EN_EL_AVISO`, los
   mismos textos del detalle) con «Cancelar» y **«Un abono extra»** (`Principal`), que confirma con
   **`comoAbono`**: el server escribe el traspaso de siempre hacia el crédito y no salda ninguna cuota.
   `comoAbono` solo vale con un crédito (`EL_ABONO_EXTRA_SOLO_A_UN_CREDITO`). Sin la marca —un APK viejo,
   los débitos automáticos, las partes de un reparto— todo sigue como antes.
2. **La sugerencia de la cuota no empuja el detalle.** «¿Es la cuota de X?» esperaba `/upcoming` y
   `/occurrences` y al llegar movía las acciones. Mientras carga, la hoja guarda su lugar **solo si el
   gasto puede ser candidato**: `cuotaPrevistaParaElDetalle` hace la misma cuenta pura
   (`cuotaQueSeParece` sobre `cuotasPorSaldar`: monto, categoría, moneda y período) con lo que el Inicio
   ya dejó en `DashboardDataCache`. Si se parece, `SugerenciaDeLaCuota(reserva = true)` —invisible, sin
   semántica ni toque, con su misma pregunta, `TAG_LUGAR_DE_LA_SUGERENCIA_DE_LA_CUOTA`— guarda el alto, y
   la lectura fresca la reemplaza por la real o la suelta. Un gasto que no se parece a ninguna no reserva
   nada (no deja un hueco que después se cierra). Lo que se muestra y se toca sale siempre de la lectura
   fresca.
3. **La letra a la escala real: 1,456.** Un teléfono al 130 % ve la letra a 1,3 × el 1,12 de `App()`.
   Las pruebas de la letra grande corren también a **1,456** y 360 dp
   (`ESCALA_DEL_TELEFONO_AL_130`). Se cortaban tres textos de un renglón, y los tres **se achican hasta
   la letra de siempre** (`seAchicaHastaLaBase`: piso = tamaño ÷ `ESCALA_QUE_ABSORBE_EL_ROTULO`, el mismo
   de los rótulos de la vuelta 55), sin partirse: «Traspaso» en el selector de Agregar
   (`SelectorSegmentado`), «$1.245.900» junto a «Anotar pago» en Pagos (`MontoDeFila`) y «Martes 30 de
   septiembre» en el encabezado de un día de Actividad. Lo demás ya cabía.
4. **Revisar aviso: «Es la cuota de un crédito ›» y «Es el pago de una tarjeta ›»** (regresión de la
   vuelta 51). Las dos patas de una cuota o de un pago de tarjeta se armaban solo eligiendo «Cuota de
   crédito» o «Pago de tarjeta», y la 51 las sacó de todos los selectores: con «Bancolombia: Pagaste
   $980,000.00 a Banco de Occidente S A ATH desde tu producto 4057», que Movi deja en «Otros», no había
   cómo llegar a «Sale de … · entra a …». Ahora, junto a la categoría, dos acciones propias
   (`DeudaQuePagaElAviso`, `accionesDeDeudaDelAviso`: solo en un gasto, mientras la categoría no sea ya de
   dos patas, y solo si hay créditos o tarjetas) dejan la pantalla en el mismo estado que antes armaba
   elegir la categoría, con el selector de la deuda filtrado a su tipo («¿De cuál crédito?» / «¿De cuál
   tarjeta?», `deudasParaElegir`, el saldo de `textoDeCuentaDeDeuda`). **Las reservadas siguen fuera del
   selector.** Desde ahí funciona el punto 1. Se revisó el resto: la tarjeta de Por revisar manda lo de
   dos patas a Revisar aviso (`DudaDelAviso.DOS_PATAS`); el detalle de un movimiento ya tenía sus filas
   propias («Es la cuota de un crédito», vuelta 51, y «Pago de tarjeta»); recategorizar en lote no tiene
   ningún modo especial. Además, «Pagaste $X a tu credito \*4083 desde tu cuenta \*4057» lee el \*4083
   como el destino, igual que «a tu tarjeta» (`antesDeUnDestino`): antes lo tomaba como la cuenta de
   origen.
5. **Documentación.** Había dos «§54» (vueltas 51 y 52): la 52 pasó a §55 y las siguientes corrieron uno;
   las referencias cruzadas (aquí y en `CLAUDE.md`) apuntan a los números nuevos.

**Las líneas base** no cambian y las guardas en 0 siguen en 0. Lo vigilan, en `:server`,
`LasDosPatasDelAvisoTest` (el 409 por los dos caminos sin escribir nada, el abono como traspaso que no
salda, sin la marca como siempre, el abono solo a un crédito); `ReconciliarConLasDosPatasTest` (la
pregunta en el lugar de los botones, «Cancelar», y «Un abono extra» con `comoAbono`);
los de punta a punta con el aviso de Occidente (en `ReconciliarConLasDosPatasTest` y
`LasDosPatasDelAvisoTest`), `LaCuentaPorElNumeroDeVerdadTest` («a tu credito»);
`LaSugerenciaDeLaCuotaNoEmpujaTest` (las acciones se mueven menos de 8 dp; sin parecido no reserva;
la lectura fresca manda) y `LaCuotaPrevistaTest` (`commonTest`); y a 1,456, `ElTextoAl1456NoSeCortaTest`,
`LosBotonesDeLosAvisosCabenTest`, `ElPrimerDiaTest` (el rango corto), `NadaSeRecortaEnElInicioTest`,
`LaAccionDelEncabezadoCedeAlTituloTest`, `ElDiaPlegadoNoCortaLaFechaTest`,
`ElEfectivoEnNegativoSeLeeEnteroTest` y `LosCamposDelSistemaTest`.

## 60. Vuelta 57: lo que encontró la revisión de master después de la 56

**Sin reglas de plata nuevas**: la cuota sigue siendo `pagoDeCuotaLegs`, y lo que cambia es que ninguna
puerta escribe una cuota sin su crédito.

1. **El comercio de Revisar aviso se lee desde el principio.** El campo de una línea (`ComercioQueSeEdita`),
   sin foco, quedaba desplazado hasta el final: a 360 dp se leía «anco de Occidente S A ATH», sin «…».
   Fuera de edición es un `Text` de hasta dos renglones con «…» al final (`TAG_COMERCIO_A_LA_VISTA`);
   tocar la fila (o «Editar») abre el campo de siempre con el foco, y al perderlo vuelve el texto.
2. **Una cuota sin crédito no se confirma.** Con «Cuota de crédito» y sin crédito elegido, Confirmar no
   prende y el pie dice **«Elige el crédito»** (`porQueFaltaElCredito`, `TAG_FALTA_EL_CREDITO`; con un
   crédito en otra moneda dice por qué). **Nunca** se escribe un gasto suelto en «Cuota de crédito»: el
   server contesta **422** `CUOTA_DEL_AVISO_SIN_CREDITO` a `POST /api/events` con `source = SMS | OCR`
   (nuevo, sin `transferId`) y a `…/grupo/{id}/confirmar` con un `evento` en «Cuota de crédito»; el espejo
   local del teléfono, igual. La única puerta que lo escribía a propósito era el APK ≤ 1.69 (antes de
   #439: pata suelta + `vincular-deuda`), y se cierra: desde el APK 1.70 la cuota de un aviso viaja con
   `DosPatasDelAviso`. Lo que ya está en la base (y su reenvío) sigue pasando. Un «Pago de tarjeta»
   suelto (una tarjeta que no está en Movi) sigue como antes: es reservado y no cuenta. **La memoria de
   categorías no aprende ni propone** «Cuota de crédito» ni «Pago de tarjeta»
   (`CATEGORIAS_QUE_LA_MEMORIA_NO_APRENDE`, solo las huellas; el vocabulario del dueño no cambia): para eso
   están «Es la cuota de un crédito ›» y «Es el pago de una tarjeta ›».
3. **El selector de la deuda respeta la categoría.** La deuda que pide la pantalla es la de la acción
   tocada o, si no, la que dice la categoría (`deudaQuePideLaPantalla`): con «Cuota de crédito», solo
   créditos y «¿De cuál crédito?»; con «Pago de tarjeta», solo tarjetas. Movi no autocompleta una deuda del
   otro tipo, y si por alguna vía queda una tarjeta en una cuota, la fila de categoría pasa a «Pago de
   tarjeta» (`categoriaParaLaDeudaElegida`): el pedido y la pantalla dicen lo mismo.
4. **El detalle de una cuota dice lo que es**: «Es la cuota de Vehículo 4083: cuenta como gasto. Para
   cambiarla, anúlala y vuelve a anotarla.» (`porQueLaPataNoSeRecategoriza` en `:core`: el crédito sale de
   la pata de la deuda o del «Cuota de X» de la pata del dinero; sin él, «un crédito»). El texto de
   traspaso queda para traspasos, pagos de tarjeta y abonos; el desembolso sigue con el suyo. «Repartir en
   partes» lleva su aire y su separador respecto de «Fecha».
5. **La fila de Pagos ofrece el gasto parecido.** La cuota pendiente de un crédito usa la misma regla del
   detalle (`gastosParecidosALasCuotas` sobre `cuotaQueSeParece` y `cuotasPorSaldar`: ±10 %, el período del
   vencimiento, un solo crédito posible; entre varios gastos, el de monto más cercano) y ofrece
   **«¿Es el de $980.000?»** (el mismo enlace que la propuesta lejana, `TAG_CUOTA_QUE_SE_PARECE_EN_PAGOS`);
   al tocarlo, «¿Es la cuota de Vehículo 4083?» con **«Sí, es la cuota»** —`vincular-deuda` con
   `esLaCuota`, lo mismo que el detalle— y «No fue este», que lo deja de ofrecer
   (`PropuestasDescartadasStore`, `cuota-parecida:<id>`). Nunca se tilda solo. **Decisión sobre «Carro»
   $9.000 → «¿Es el de $260.000?»**: se queda como enlace, a propósito (vuelta 51). El emparejador de una
   regla común no exige el monto (un recibo varía) y, si el candidato lejano no se ofreciera ni como
   enlace, la única salida sería «Anotar pago», que duplicaría el gasto si sí era ese. Como enlace no
   empuja (no es «Por confirmar» ni «Revisar») y el dueño decide.
6. **La anomalía de categoría dice su base.** «Gasto nuevo en Otros · Llevas $980.000» al lado de «En qué
   se va · Otros $2.780.000» parecía un error: la anomalía no cuenta los pagos de facturas (vuelta 43) y la
   diferencia era el arriendo. Con fijos en esa categoría, `categoriasPorEncima(fijosDelPeriodo = …)` dice
   «Llevas $980.000 este período sin contar tus pagos fijos (con ellos, $2.780.000)». Ni la cifra ni el
   umbral cambian.
7. **Los enlaces de Revisar aviso son parejos**: «Es la cuota de un crédito ›» y «Es el pago de una
   tarjeta ›» son `Boton` Fantasma S, como «Esto pagó varias cosas». La barra de Ignorar / Confirmar va
   debajo de la lista (no la tapa), y la lista termina con un relleno del alto medido de la barra, para que
   lo último suba entero por encima de su borde.
8. **«Ocultar los saldos» en Tú**: el subtítulo va en dos renglones (`lineasDelSubtitulo = 2`); a 390 dp
   se cortaba en «…en est…». Probado a 390 y 360 con la letra ×1,12.

## 61. Vuelta 61: las hojas blancas en la web, nombres viejos y Movi AI sin IA

**Sin reglas de plata nuevas** ni cifras distintas: la forma del héroe de Créditos cambia, sus cuentas
son de la vuelta 59.

1. **Las hojas eran grises en la web** (el panel `#EBEBEB`, el botón principal `#EBB318`: un 8 % de
   tinta encima). **La causa**: el `clickable` del velo de `MarcoDeHoja` usaba la indicación del tema (el
   *ripple* de Material 3). El velo es el **contenedor** del panel, así que con el mouse sobre el panel
   el velo también está en *hover* (Compose reparte el puntero a toda la cadena de ancestros), y la capa
   de estado del *ripple* —0,08 en hover, 0,10 en foco— se dibuja **después del contenido**, encima del
   panel entero. No era el foco: el que tiene el foco del velo es su `focusable()`, que no pinta nada; la
   prueba vio llegar un `HoverInteraction.Enter`. En el teléfono no hay hover y nunca se vio. Ahora el velo
   toca **sin indicación** (`veloSinIndicacion`, en las dos hojas: desde abajo y centrada); los
   enfocables de adentro conservan su anillo de foco (§58). Lo vigila `LasHojasSonBlancasTest`
   (Robolectric: `captureToImage` se cuelga, así que mide la causa con una indicación espía en el tema:
   con el mouse sobre el panel y sobre el velo, y el foco del teclado en el velo, no le llega nada; sin el
   arreglo le llegaba el hover del velo).
2. **Hojas y tarjetas al sistema.**
   - **«Nueva cuenta»** y **«Nuevo préstamo»** abren con su `EncabezadoDeHoja` (`TITULO_DE_NUEVA_CUENTA`,
     `TITULO_DEL_NUEVO_PRESTAMO`). En el préstamo, «¿Acabas de recibir la plata de este crédito?» va en
     `titulo` —la letra de «Términos», el título de un grupo—, no en `cuerpo`; no es una `Seccion` porque
     a 360 con la letra grande necesita dos renglones. **«¿Qué deuda quieres registrar?» es el título de
     su hoja** (iba en `tituloMedio`). Lo vigila `LasHojasDeAltaTienenTituloTest` (un solo encabezado, lo
     primero de la hoja, nada lo supera, y la pregunta en la letra de «Términos»).
   - **El héroe de Créditos**: los renglones del resumen («Los pagas tú», «Contando todas tus deudas»)
     van **sin la sangría** de 8 dp y **topados** a `ANCHO_MAXIMO_DEL_RESUMEN_DE_DEUDA` (420, el de una
     columna de Créditos): a 1.280 el rótulo y su cifra quedaban a ~900 px. `ElHeroeDeCreditosVaJuntoTest`
     (a 1.280 y a 390).
   - **El vacío que enseña** (`VacioQueEnsena`, todo vacío con ícono: «Lo que Movi sabe de ti», Grupos,
     Personas y comercios, los avisos) pinta su ícono en el **círculo neutro** (`TonoDeIcono.Neutro`: la
     capa 3 con la tinta). Era amarillo lleno, armado con `background(marca, CircleShape)`, y
     `ElAmarilloEsDeLaAccionTest` no lo veía porque solo buscaba `TonoDeIcono.Marca`: ahora también
     falla con **un círculo amarillo armado a mano** (`colores.marca` a dos renglones de un
     `CircleShape`) fuera del «+» flotante del rail compacto, que es la acción.
3. **Los nombres viejos.** Hoy → **Inicio**, Plan → **Pagos**, Movimientos → **Actividad** (Espacios →
   **Grupos** en los errores del server) en lo que lee el dueño:
   «Qué secciones ves en Inicio y en qué orden», «Reordena y edita las secciones de Inicio», «El resumen
   sigue en Inicio y en «Tus períodos»», «Escondida: si hay algo urgente, Inicio te lo dice…»,
   «Este período, en Pagos», «tócala para verla en Pagos», «Ver tu plata día a día en Pagos», «Movi puede
   anotarlo en Pagos…», «Reparte un gasto desde su detalle en Actividad…», «…desde Actividad» y «Te avisa
   en Inicio…» (Captura del banco), «Este aviso no se muestra en Inicio…», «en Actividad» (la captura de
   notificaciones en Android), y en Movi AI «…lo tilda solo en Pagos», «la tarjeta Disponible de Pagos»,
   «en «Para revisar», en Inicio». Las instrucciones del asistente (`PERSONA`) nombran las pantallas de
   ahora y piden la preposición delante. **Los nombres de pantalla de una respuesta son enlaces**
   (`enlacesAPantallas`, `ui/ai/PantallasNombradas.kt`, con `LinkAnnotation` en el azul del enlace):
   conservador —con su mayúscula y después de «en/de/desde/a»—, así «Pagos del período» al empezar una
   frase sigue siendo texto. **«Mira en qué se fue», abierto desde Pagos, marca Pagos**: «En qué se va»
   marca la pestaña de donde se abrió, como Buscar (`destinoResaltadoDeLaPila`). Lo vigilan
   `LosNombresViejosDeLasPestanasTest` (`:core`: ningún literal de `ui/` ni de `server/.../ai/` nombra una
   pestaña vieja como lugar), `PantallasNombradasTest`, `EnQueSeVaMarcaSuPestanaTest` y el enlace en
   `MoviAiSinIaFuncionaMejorTest`.
4. **Movi AI sin IA.**
   - **Lo escrito casi igual que una de las tres** se contesta sin la IA, como el chip:
     `PreguntaSugerida.delTexto` (`:core`) compara lo escrito normalizado (`normalizarPregunta`: sin
     tildes, signos ni mayúsculas, espacios juntos) contra una lista corta y explícita por pregunta («cómo
     voy», «qué reviso primero», «en qué se me va la plata»…). El server la usa si no llega el id (también
     un APK viejo), y la app manda el id. «¿Cómo voy con el carro?» o «¿cómo voy este mes?» siguen siendo
     preguntas libres. `PreguntaSugeridaDelTextoTest`, `LasPreguntasSugeridasTest` (server).
   - **Enter envía** con teclado físico (`LocalHayTecladoFisico`: la web, iOS con teclado); Shift+Enter
     es el salto de renglón del campo (`esEnterParaEnviar`). En el teléfono Android, como siempre.
   - **«Lo que Movi sabe de ti» se escribe a mano**: «Agregar» en el encabezado y «Escribir un recuerdo»
     en el vacío abren un recuerdo nuevo que se guarda con el `POST /api/asistente/memoria` de siempre,
     origen `A_MANO` («Lo escribiste tú»). `LaMemoriaSeEscribeAManoTest`.

**Las líneas base** no cambian (`dp_sueltos` 115, `radios_con_numero` 3) y las guardas en 0 siguen en 0.

## 62. Vuelta 60: una sola manera de decir «gastado», y el patrimonio que no confunde un ajuste con una pérdida

Lo que dejó la revisión fresca del 29-oct (capturas `fresca-*.png`). **Sin reglas de plata nuevas**: los
totales salen de las funciones de siempre (`esGastoDelPeriodo`, `spentByCategory`, `gastoVariablePorDia`,
`patrimonioDe`); cambian los rótulos, los desgloses y lo que se propone. El neto del patrimonio no cambia.

1. **La curva del patrimonio separa los ajustes** (`HistoriaDelPatrimonio`, `PatrimonioRoutes.kt`,
   `ui/accounts/CurvaDelPatrimonio.kt`). Registrar con «Ajustar saldo» una deuda que ya existía hacía
   decir «Desde que usas Movi (ago 2026), bajó $34,6M». **Decisión: marcar, no rebasar.** Rebasar hacia
   atrás supondría desde cuándo existía la deuda, y eso Movi no lo sabe; marcar no inventa nada.
   - Cada punto trae `ajustes`: el neto con todo menos el neto **sin** los «Ajuste de saldo» y las
     aperturas («Saldo inicial»/«Deuda inicial») hasta esa fecha —`patrimonioDe` calculado dos veces, una
     consulta agregada más—. `cambioDelPatrimonio` (`:core`) parte el cambio en `porAjustes` y
     `sinAjustes` (una resta).
   - La leyenda sigue diciendo el neto («Desde ago 2026, bajó $34,6M») y debajo va la nota
     (`notaDeLosAjustes`): «Los ajustes suman −$35,5M: deudas y saldos que ya tenías y anotaste después.
     Sin ellos, subió $900.000.» El mes con ajustes lleva un **aro** en la curva, y elegirlo dice «Al
     cierre de oct 2026: $36,5M · ajustes −$35,5M».
   - **«Desde que usas Movi»** sale de `HistoriaDelPatrimonio.primerMovimiento`: el primer movimiento
     vivo que no es apertura ni ajuste (el arranque de «Tus períodos»). Solo si el primer punto de la
     curva es ese mes; si no, «Desde ago 2026». Sin el dato (server viejo) no se afirma.
   - **Los bienes**: con `conBienes`, la nota «Los bienes cuentan con su valor de hoy en todos los
     meses.» El cálculo no cambia.
2. **Una sola manera de decir «gastado»** (`:core` `LoGastado.kt`).
   - **Presupuestos**: debajo del período («Octubre»), «En tus N presupuestos» con la cifra y «de $Y», y
     el renglón **«Fuera de presupuesto»** (`CifraConRotulo` chica en renglón, `TAG_FUERA_DE_PRESUPUESTO`).
     `gastoEnPresupuestos`: las dos cifras suman el gasto del período (un presupuesto repetido cuenta una
     vez). El esqueleto reserva los dos renglones nuevos.
   - **«En qué se va»**: debajo del total, «Día a día $2,1M + fijos pagados $2,8M» (`desgloseDelAnalisis`,
     `TAG_DESGLOSE_DEL_ANALISIS`). El día a día es el gasto variable del Disponible (`gastoVariablePorDia`
     del resumen del Inicio, la misma lectura recordada) y los fijos, el resto: la misma regla que separa
     los fijos en el Disponible. Solo en el período en curso (el server manda ese gasto de ningún otro) y
     solo si cuadra con el total; si no, no se parte.
   - **«Cuánto puedes gastar»**: la columna «Período» decía «gastaste» bajo el día a día; ahora dice **«en
     el día a día»** (`ROTULO_DIA_A_DIA_DEL_PERIODO`). A 1.280: «Llevas gastado» (todo), «en el día a
     día» (sin fijos), «En tus N presupuestos» y «Fuera de presupuesto»: ninguna cifra distinta con el
     mismo nombre.
3. **Una sola regla en las tres columnas** (`DisponibleDelPeriodo`). **Decisión: lo que queda**, la regla
   de «Te quedan» y de «$X por día» (`porDiaParaLoQueQueda`), no el ritmo fijo: la meta de hoy es lo que
   quedaba al **empezar** el día entre los días que quedan (a primera hora, el «$X por día» del héroe; lo
   que se gasta hoy no la achica), y la de la semana, lo que quedaba al empezar la semana entre los días
   que quedaban, por sus días. Sin nada que repartir la meta es cero, y con el período ya pasado
   (`yaTePasaste`) la semana y hoy dicen **«$0 · ya te pasaste · llevas $X»** (`YA_TE_PASASTE`). El ritmo
   fijo (`metaPorDia`) queda solo para la línea «Día a día» de los días pasados en Actividad. Consecuencia
   honesta: la última semana del período se lleva todo lo que queda, así que su columna dice lo mismo que
   «Te quedan» (la del período sigue diciendo lo gastado), y esa semana no se puede pasar sin pasar el período.
4. **El recurrente que cambió de categoría** (`:core` `RecurrenteQueCambioDeCategoria.kt`). Un gasto de
   este período y uno del anterior son el mismo si son ocurrencias de la misma regla (sellos o
   emparejados) o si tienen el mismo nombre y el monto a ±5 % (elegido). En otra categoría, el server lo
   manda como `TipoDeAnomalia.RECURRENTE_CAMBIO_DE_CATEGORIA` con su `cambioDeCategoria`, solo a quien
   pide `?conRecurrentes=1` (un APK viejo no conoce el tipo). «Movi propone hoy» lo pone primero:
   «¿Arriendo va en Hogar, como en septiembre?» con **«Sí, cambiar»** (`aceptarElCambioDeCategoria`: el
   `updateEventCategory` y, si la regla tiene la otra, el `updateRecurringRule` de siempre) y **«No»**
   (descarta la huella `recurrente-categoria:<id>`). **Nunca cambia solo.** Mientras no se resuelve:
   la anomalía de categoría y la de comercio nuevo **no cuentan** ese movimiento (sale de `actual`, como
   los pagos de facturas desde la vuelta 43), y Movi AI («¿En qué se me está yendo…?») compara las
   categorías con el recurrente donde estaba (`comoEnElPeriodoAnterior`) y lo dice. Con «No», cuenta
   como cualquier gasto. El widget «En qué se va» del Inicio no se tocó.

**La revisión de la vuelta** (capturas `rev60-*.png`) encontró y se arregló:
- **El desglose de «En qué se va» no salía nunca**: la lectura del resumen del Inicio depende del
  período, y sin `periodoVigente` `rememberLectura` no lee nada. Ahora va con el período de hoy, y lo
  vigila una prueba de pantalla (`EnQueSeVaScreenTest`, `TAG_DESGLOSE_DEL_ANALISIS`). Dice **«fijos
  pagados»**: en «¿De dónde sale?», «Fijos del período» cuenta también los que faltan.
- **El arriendo de la demo no se proponía**: el de septiembre es «Arriendo octubre» con comercio
  «Inmobiliaria», y se comparaba solo el comercio. Ahora un gasto de una regla (sellado, emparejado o
  que la nombra con su monto) encuentra en el período anterior lo de la misma regla o lo que la nombra
  —en el comercio **o** en la descripción, con el monto a ±5 %—; sin regla se comparan los dos nombres
  de cada lado. El server lee también lo que Movi emparejó solo en el período anterior.
- **El ajuste de este mes no se marcaba** (el aro se saltaba el punto de hoy): ahora hoy lleva un aro por
  fuera (`hoyTraeAjustes`) y el lector oye «Hoy incluye un ajuste».
- **Movi AI decía «Otros $1.800.000 · casi igual que el período pasado»**: el renglón de la categoría
  donde quedó el recurrente dice qué es («es Arriendo, que el período pasado fue Hogar», o «incluye…»).
- **La tarjeta de Presupuestos** ya no se titula «Gastado en octubre» (su cifra grande es solo lo de los
  presupuestos): dice el período, «Octubre» o «Este período».

**Las líneas base** no cambian (`dp_sueltos` 115, `radios_con_numero` 3) y las guardas en 0 siguen en 0.
Lo vigilan, en `:core`, `UnaSolaManeraDeDecirGastadoTest` (día a día + fijos = el gasto del período; los
presupuestos + lo de afuera = lo mismo), `LaMismaReglaEnLasTresColumnasTest` (con el disponible pasado,
cero; con margen, la meta de hoy es el «por día» de la mañana) y `RecurrenteQueCambioDeCategoriaTest`; en
`:server`, `HistoriaDelPatrimonioTest` (los ajustes de cada punto, el primer movimiento real, los
bienes), `AnomaliasRoutesTest` (el arriendo en Otros no es gasto nuevo, se propone y «No» lo saca) y
`EnQueSeVaNoCuentaElCambioComoNuevoTest`; en `commonTest`, `LaCurvaSeparaLosAjustesTest` y
`ElTotalSeParteEnDiaADiaYFijosTest`; en Robolectric, `LasCifrasDelDisponibleEntranTest` («ya te pasaste»,
«llevas $888.888» y «en el día a día» entran a 390), `PresupuestosConJerarquiaTest` y
`ElRecurrenteQueCambioDeCategoriaSeProponeTest`.

## 63. Vuelta 64: los apartados que avisan, y la cuota vencida que se dice

De la revisión fresca del 29-oct (punto 11) y un pendiente de la vuelta 59.

- **La hoja de apartar avisa antes de guardar.** Debajo del monto, una `VistaPrevia`: «Te quedan pasa
  de $1.450.950 a −$549.050». No es una cuenta nueva: `teQuedanConElApartado` (`ui/accounts/
  ElApartadoQueAvisa.kt`) corre `teQuedanDelInicio` —la del héroe— sobre los mismos datos, con lo
  apartado de `PlataDelDisponible` movido en lo que cambia `apartadoFueraDeLosFijos` (la regla con que
  el server manda esa cifra) al sumar el propuesto. Así «antes» es el héroe de ahora y «después» el que
  va a pintar el Inicio al recalcular. Lee lo del Inicio si está fresco; si no, las lecturas del
  disponible (`leerDatosDelDiaADia`) más cuentas y apartados. Sin datos, la hoja guarda igual, sin aviso.
- **«¿Sale de tu ahorro del período?» no se pregunta.** Hoy el ahorro y el apartado se restan cada uno
  una vez y por separado, y nada dice que un apartado «sale de» un ahorro: contarlo adentro sería una
  regla de plata nueva. Por eso la hoja solo advierte, cuando «Te quedan» quedaría negativo, que el
  ahorro se resta aparte y que, si esa plata sale de ahí, se baja en Tú → «Ahorro de cada período».
- **La hoja habla el sistema**: `EncabezadoDeHoja` con «Quitar» (Peligro, S) a su lado y
  `ConfirmacionEnLinea`; `PieDeHoja` con una sola acción, sin «Cancelar»; la categoría con
  `FilaDeCategorias` (frecuentes y «Todas ›», que abre `SelectorDeCategoria`), no la cuadrícula en línea.
- **«Libre» con un solo sentido.** La fila de la condición dice «Sin condición: para cualquier gasto»
  (`PLATA_LIBRE`) y lo que queda sin apartar es «Disponible en la cuenta» (`DISPONIBLE_EN_LA_CUENTA`).
- **«Tus cuentas» del Inicio** dice debajo del saldo «$2.000.000 apartado» (`notaDeLoApartado`, con
  `apartadoEfectivoDe`): saldo − apartado = «Disponible en la cuenta» del detalle. `TarjetaDeCuenta`
  ganó `nota` y `reservarNota`: si alguna del carrusel tiene apartado, todas reservan el renglón.
- **Apartado para un gasto de un mes puntual** (SOAT, predial, matrícula, primas): `Apartado.objetivo`
  y `mesDelGasto` («AAAA-MM»), columnas nullable `apartados.objetivo` / `mes_del_gasto`. La hoja ofrece
  una casilla, el mes (este y los once que siguen) y el costo, y sugiere `aporteMensualSugerido` =
  (objetivo − lo apartado) ÷ meses que faltan, hacia arriba. El detalle lo dice en dos renglones:
  «Llevas $400.000 de $1.200.000 · marzo de 2027» y «Aparta $160.000 al mes». **Es solo una sugerencia**: no es un fijo, no
  resta de «Te quedan» y no mueve plata. Los dos campos viajan siempre (`@EncodeDefault`) y el server
  solo los toca si la clave vino: un APK viejo que edita el monto no borra el objetivo.
- **La cuota vencida se dice.** En el detalle del crédito, la fila «Cuota» sale de la misma primera
  cuota sin pagar con que se cuentan las que faltan (`subtituloDeLaCuota`): «Cuota de octubre vencida
  desde el 5 oct», en el tono de aviso, o «Día 5 · la próxima el 5 de noviembre».

**La revisión de la vuelta** (capturas `rev64-*.png`) pidió y se arregló: el aporte se cortaba en un
renglón («… al m…») y ahora va en dos; un apartado cuyo mes ya pasó se puede editar (el mes guardado se
valida sin `hoy` y se ofrece primero); el «−» de la vista previa va pegado a su «$» (WORD JOINER) y la
cifra a su «a» (espacio que no se parte); la lista de Créditos dice la cuota vencida como el detalle
(`prestamoEnLaLista` sale de la misma primera cuota); y mientras se confirma «Quitar» la vista previa
dice «Te quedan vuelve a $X».

Lo vigilan `ApartadoParaUnGastoTest` (`:core`), `ApartadoRoutesTest` y `SchemaDeArranqueTest`
(`:server`), `ElApartadoQueAvisaTest` y `LaCuotaVencidaSeDiceTest` (`commonTest`), y en Robolectric
`ApartadosEnLaCuentaTest` y `LaHojaDeApartarA360Test` (360 dp, letra ×1,12).

## 64. Vuelta 65: el ícono de Movi es la «m»

El 29-oct el dueño pidió que el ícono y las notificaciones hicieran juego con la opción 3, y entre siete
opciones eligió la **A: la «m» de Figtree ExtraBold en la tinta (`#1F1B16`) sobre el amarillo de la
marca (`#FFC21A`)**, en un cuadrado redondeado. Reemplaza la billetera azul marino con tarjetas verdes
de la paleta vieja.

**Cómo se genera.** `scripts/generar-icono.py` lee el glifo «m» del mismo TTF que usa la app
(`shared/src/commonMain/composeResources/font/figtree_extrabold.ttf`) con fontTools y lo escribe como
`pathData` (coordenadas absolutas, dos decimales) para los vectores y como polígonos para los PNG. Las
proporciones son las de la referencia: respecto del lado visible del ícono, el em mide 330/512 y el
centro sube 10/512. No depende de rutas fuera del repo; necesita fontTools y Pillow (en esta Mac, el
Python de Xcode: `/Applications/Xcode.app/Contents/Developer/usr/bin/python3 scripts/generar-icono.py`,
con `--capturas DIR` para las imágenes de revisión). **Nada de lo que escribe se edita a mano.**

| pieza | archivo | qué es |
|---|---|---|
| Ícono adaptable | `mipmap-anydpi-v26/ic_launcher{,_round}.xml` | fondo `@color/movi_marca`, primer plano y `monochrome` = `drawable/ic_launcher_foreground.xml` (la «m» en `@color/movi_tinta`, en la capa de 108, dentro del círculo seguro de 66) |
| Respaldo API 24-25 | `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png` | 48 dp por densidad: cuadrado redondeado y círculo, con alfa |
| Ícono de los avisos | `drawable/ic_stat_movi.xml` | la misma «m», 20 de ancho en 24, blanca (Android usa solo el alfa) |
| Arranque Android 12+ | `values-v31/themes.xml` | fondo crema (`movi_fondo`), la «m» en su círculo amarillo; `Theme.Movi` hereda de `Base.Theme.Movi` |
| PWA | `icons/movi-{192,512}.png` (maskable, la «m» respecto del 80 % central), `icons/movi-redondeado-{192,512}.png` (any) | `manifest.json` con `background_color`/`theme_color` `#F4F1EC` (antes `#121212`, del tema oscuro que ya no existe) |
| iOS en la web | `icons/apple-touch-icon.png` | 180 × 180, a sangre y sin alfa |
| Favicon | `icons/favicon.png` | 48 × 48 |
| Push web | `push-sw.js` | `icon` el redondeado, `badge` `icons/movi-badge.png` (la «m» blanca sobre transparente) |
| En la app | `theme/LaMDeMovi.kt` | el mismo `pathData` como `ImageVector` (la marca de la guía de primer uso; vector y no texto para que en iOS también sea Figtree) |

**El acento de los avisos es la tinta, no el amarillo.** Desde Android 12 el acento (`setColor`) pinta el
círculo detrás de la «m» blanca en la cabecera de la notificación: blanco sobre amarillo da 1,6:1 y no
se lee; blanco sobre tinta da 16:1. En la cortina oscura el círculo de tinta casi no se separa del gris,
pero la «m» blanca sí, que es lo que tiene que leerse. En Android 8-11 el acento tiñe la «m» y el nombre
de la app, y el sistema lo aclara solo en la cortina oscura. Todos los avisos (Por revisar, pagos,
banco mudo, sesión cerrada, débitos, cierre) salen de `Avisador.publicar`: un ícono y un color.

**Los canales hablan como la app de hoy**, con tuteo, sin cambiar sus ids (un id nuevo sería un canal
nuevo y se perdería lo que el dueño silenció): «Por revisar» (antes «Movimientos por revisar»), «Pagos
por vencer», «Captura del banco», «Débitos automáticos» y «Cierre del período». El push web de los
vencimientos se titula también «Pagos por vencer» (antes «Pagos próximos en movi»).

**iOS**: `iosApp` no tiene `Assets.xcassets` ni `AppIcon`; la app de iOS sigue con el ícono por defecto
de Xcode hasta que se agregue un catálogo (el script ya sabe pintar cualquier tamaño).

Lo vigila `ElIconoDeMoviTest` (JUnit en `:shared:testDebugUnitTest`, lee los archivos): `movi_marca`/`movi_tinta` =
`marca`/`sobreMarca` de `Tokens.kt`; el adaptable con su fondo, primer plano y monocromo; la «m» dentro
del círculo seguro y con el mismo `pathData` que `LaMDeMovi.kt`; `ic_stat_movi` vector blanco y nada
más; cada `NotificationCompat.Builder` con `ic_stat_movi` y la tinta; el manifest en crema con el
`theme-color` del `index.html`; los maskable y el apple-touch-icon a sangre y opacos; y el badge blanco.

## 65. Vuelta 66: los totales se pueden rastrear, y el hogar compartido no pierde plata

Tercera revisión de producto sobre master `32485a7a` (capturas `r3-*.png`). **Sin reglas de plata
nuevas**: la vinculación de un saldado es la de siempre, con una ventana más.

1. **Toda cifra de Actividad sale de las filas.** Tu parte de un gasto que pagó otra persona del grupo
   es una fila en su día —«Mercado del sábado» · «Pagó Caro · Mercado» · −$120.000 y «Tu parte»
   debajo— en Todo, Gastos y Compartidos (`conTuParteDeOtros` en `:core`; `textosDeTuParteDeOtro`, y
   `FinancialEvent.pagadoPor`, derivado, solo en esas filas). El resumen (Entró · Salió · Neto, y el
   panel) sale de los mismos días, así que la suma de los días es «Salió»; un día con solo eso (el lunes
   26) aparece. Tocarla abre **el gasto del grupo** (`AbrirTuParteDeOtro` → `HojaDelGasto`), no un
   movimiento; no se desliza. `movimientosDelAnalisis` no repite una parte que venga en los días y
   aparte.
2. **El saldo del grupo espera la transferencia.** Un «Marcar como saldado» sin movimiento en el que el
   dueño paga o cobra queda **esperando la transferencia** (`esperaLaTransferencia`: hasta 45 días).
   Mientras espera, Movi propone vincular la primera plata de esa persona por ese monto ± 1 %
   (`transferenciasQueEsperan`, desde 7 días antes de saldar); «Sí, saldar» manda el **mismo id** del
   saldado con el movimiento, y el server lo vincula (antes, el upsert por id devolvía el saldado sin
   tocarlo). Vinculado, sale de `monthIncome` como siempre. Las propuestas —las de la deuda y las que
   esperan— van también a **Por revisar** («Para saldar en tus grupos») y a la **campana**
   (`TipoDeNotificacion.TransferenciaParaSaldar`), por `GET /api/espacios/propuestas`; las tres usan
   `TarjetaDeSaldar`. «No» la descarta para siempre (`saldar:<movimiento>` en `anomalias_descartadas`).
3. **Editar inicio no se contradice.** Prender una opcional la saca de `ocultas`, y esconder una la saca
   de `visibles` (`mostrando`). Al leer, **gana `visibles`** (`limpias`): una opcional solo entra ahí
   prendiéndola en el editor, así que es lo último que pidió el dueño. Como el server limpia al leer
   (la preferencia, `GET` y la definición de Hoy), esto arregla también las preferencias ya guardadas
   en producción sin reescribir la fila; la fila queda con las dos listas hasta el próximo guardado.
4. **Historial del grupo.** «Pagos entre ustedes» lista cada saldado —«Caro te pagó $168.050», «29 oct
   · Transferencia de Caro» (o «Esperando la transferencia», o «Sin transferencia vinculada»)— con
   «Deshacer» (el `DELETE …/liquidaciones/{id}` que el server ya tenía; la deuda vuelve y la
   transferencia vuelve a ser ingreso). En la lista de gastos, una marca de corte en cada saldado
   («Saldaron $168.050 · 29 oct», `gastosConSusCortes`). `LiquidacionDelEspacio.movimiento`, derivado.
5. **Crear, nombrar y borrar grupos.** «Nuevo grupo» pide el nombre (Movi sugiere «Casa con Caro») y una
   o varias personas, con «Agregar persona» ahí mismo (la hoja de «Personas y comercios», que pide número
   o llave: sin eso Movi no reconoce sus transferencias); con el mismo nombre y las mismas personas
   avisa (`avisoDeGrupoRepetido`) y el botón dice «Crear otro igual». El lápiz del encabezado del grupo
   abre «Editar grupo»: renombrar (el upsert de siempre) y «Borrar grupo», con su pregunta antes
   (`confirmacionDeBorrarElGrupo`). **Decisión: se deja borrar aunque no esté a mano, diciendo lo que
   pasa**: «Caro te debe $X: al borrarlo, Movi deja de llevar esa cuenta», que tus movimientos vuelven a
   contar enteros, que lo que pagó otra persona deja de contar y que las transferencias vinculadas
   vuelven a ser ingreso o gasto; el botón dice «Borrar de todas formas». Un grupo repetido o de prueba
   casi siempre está a mano, y bloquear el borrado de uno con saldo obligaba a inventar un saldado. El
   reparto distinto de 50/50 no entra: el modelo y el server ya admiten porcentajes
   (`RepartoDelEspacio.PORCENTAJES`, `repartoValido`), falta la hoja.
6. **Una sola cuenta de «N veces».** `vecesEnElComercio` (`:core` `VecesEnElComercio.kt`) con
   `claveDelComercio` (la marca primero, después la clave de comercio) y `claveDeLaCompra` (las partes
   de una compra repartida salen de la misma cuenta, a la misma hora y se anotaron en el mismo
   instante: cuentan una vez). La usan el detalle (`enEsteComercio`) y Buscar.

Lo vigilan `VecesEnElComercioTest`, `EspacioTest` y `PreferenciasDelInicioTest` (`:core`),
`EspacioRoutesTest` (saldar primero, la transferencia que llega, la propuesta, vincular, `monthIncome`;
deshacer; «No») e `InicioRoutesTest` (la preferencia guardada con las dos listas) en `:server`, y en
`commonTest` `ActividadCuadraConSusFilasTest` (la suma de los días es «Salió», con un gasto que pagó
Caro), `UnaSolaCuentaDeVecesTest`, `HoyEnUnaMiradaTest` y `TextosDelGrupoTest`.

## 66. Vuelta 67: lo que dejó la revisión de la 66, y la hoja del movimiento clara

De la revisión de la vuelta 66 (capturas `rev66-*.png`) y de la tercera revisión fresca (`r3-*.png`).
**Sin reglas de plata nuevas**: restaurar un anulado borra su marca de `void_events` y deshacer una
compra dividida anula las partes y restaura el original; los dos devuelven el estado anterior.

1. **El grupo dice lo que espera.** La tarjeta del saldo: «Están a mano · esperando $168.050 de Caro»
   (`tituloDeLaTarjetaDelSaldo`, `loQueEspera`, con `esperaLaTransferencia`); si el que paga es el
   dueño, «esperando tu transferencia de $90.000 a Caro». En «Pagos entre ustedes» el detalle va en
   **dos renglones** —la fecha y, abajo, «Esperando la transferencia»— (`detalleDelPago`,
   `RENGLONES_DEL_DETALLE_DEL_PAGO`): en uno se cortaba a 390 y 360.
2. **Por revisar cuenta lo que salda un grupo.** `ConteoPorRevisar.saldar` entra en `total` y en el
   desglose («1 para saldar»); las propuestas son una lectura más de la bandeja
   (`LecturasPorRevisar.propuestasDeSaldar`, `ClaveDeLectura.PropuestasDeSaldar`), así que «Todo al
   día» no sale debajo de una (`bandejaAlDia(…, propuestasDeSaldar)`), y la insignia del Inicio, la
   campana, «N por revisar» de Actividad y la bandeja dicen el mismo número. En la campana la
   propuesta va corta —«Transferencia de Caro» y, en dos renglones, «¿Es la que marcaste como
   saldada? · Casa con Caro»— (`laPropuestaEnLaCampana`, `NotificationRow.lineasDelSubtitulo`).
3. **Buscar habla como Actividad.** La fila de un movimiento usa la cifra y la línea de la fila de
   Actividad (`FinalDeLaFila`, `montoPrincipalDelMovimiento`, `debajoDeLaCifra`: tu parte y «de $88.600
   · con Caro»), así que la suma del comercio sale de sus filas; el comercio se llama por **su marca**
   («Éxito», `nombreDelComercio`). El subtítulo no repite la categoría si ya es el título
   (`subtituloDeUnMovimientoEnBuscar`). El detalle abierto desde Buscar trae «N veces este período»
   (con los movimientos del período del movimiento) y su «Ver».
4. **La franja sigue al chip** (decisión): Entró · Salió · Neto es **la suma de la lista que tiene
   debajo** con el chip puesto (`laFranjaDeLaLista`, la misma regla de la cifra de cada día,
   `resumenDelDia`). Con «Todo» es el resumen del período; en «Compartidos» suma lo compartido. El
   panel de la pantalla ancha sigue siendo del período entero (no está encima de la lista). Con un
   filtro de «En qué se va» o una búsqueda la franja sigue sin pintarse.
5. **Una sola regla de título por movimiento** (`textosDeLaFila`, decisión): el título es **el concepto,
   el comercio o la categoría, nunca la cuenta**; la cuenta va en el subtítulo. Con el concepto igual a
   la categoría y sin comercio, la categoría de título («Internet», con «Bancolombia Ahorros 4057»
   debajo); el detalle y la hoja de anular se titulan igual. La cuota de un crédito se llama por su
   crédito —«Cuota de Vehículo 4083»— en Todo (el renglón del par, `transferRowTitle`) y en Gastos (la
   pata sola): `tituloDeLaCuota`, sin la nota del dueño.
6. **La hoja del movimiento dice lo que hace.** «Nota» pasa a **«Editar»** (abre monto, cuenta y
   concepto); «Repartir en partes» pasa a **«Dividir en categorías»** (`REPARTIR_EN_PARTES`, también en
   Revisar aviso); «Es la cuota de un crédito» no se ofrece en un gasto compartido
   (`puedeMarcarseCuotaDeCredito`); la hoja de anular dice el título y el subtítulo de la fila, sin
   «MANUAL».
7. **Dividir deja rastro y se deshace.** Cada pata de cada parte lleva el id de su compra
   (`FinancialEvent.repartoDe`, columna `financial_events.reparto_de`, la escribe solo el server). El
   detalle de una parte dice «Parte de una compra de $100.000 · 2 partes» con «Ver» y «Deshacer»
   (`ParteDeUnaCompra`, `GET /api/events/{id}/reparto`, `POST /api/events/{id}/deshacer-reparto`):
   deshacer anula las partes, restaura el original y le devuelve su aviso. En un movimiento sin aviso
   el cuadre dice «Suma $100.000»; la parte nueva empieza **sin categoría** (`parteNuevaSinCategoria`).
   «N veces» cuenta las partes como una compra por `repartoDe` (`claveDeLaCompra`).
8. **Anular tiene «Deshacer».** Al anular, abajo de la pantalla, «Anulado · Deshacer» durante 10 s
   (`AvisoDeDeshacer`, `LocalAvisoDeDeshacer`, en la cáscara: la hoja se cierra al anular). Actividad
   tiene el chip **«Anulados»** (`CHIP_ANULADOS`, `ListaDeAnulados`): lo anulado, el último primero,
   un par una vez, «$40.000 · anulado el 29 oct · Duplicado» y «Restaurar». `DELETE
   /api/events/{id}/void` borra la marca del movimiento **y la de su otra pata**; restaurar el original
   de una compra dividida la deshace, y una parte de una compra que volvió a ser una no se restaura
   (409). En el teléfono, una anulación que no subió se deshace solo en el espejo.
9. **«Mensajes del banco» se recorre.** Lista compacta **por día** con chips Todos · Pendientes ·
   Confirmados · Ignorados (`historialPorDia`, `ChipsDelHistorial`); el comercio de título, no
   «Bancolombia» (`filaDelHistorial`, con `SmsMessage.comercio`/`monto`/`tipo` que pone `GET
   /api/sms`); tocar un pendiente abre Revisar aviso, un confirmado su movimiento
   (`SmsMessage.eventoId`), y lo demás muestra el texto del banco ahí mismo, con «Era un movimiento» si
   Movi lo apartó.
10. **Grupos, lo menor.** Renombrar avisa si ya hay un grupo con ese nombre y esas personas
    (`avisoDeNombreRepetido`, la comprobación de crear; el botón dice «Guardar igual»); la pregunta de
    borrar sale sola, sin la hoja «Editar grupo» detrás; el «No» de una propuesta de saldar pregunta
    antes y dice que la apaga también para un saldo futuro (`DETALLE_DEL_NO_DE_SALDAR`).

Lo vigilan `ElGrupoQueEsperaTest`, `LoQueSaldaUnGrupoSeCuentaTest`, `BuscarHablaComoActividadTest`,
`LaFranjaSigueAlChipTest`, `ElTituloEsLaCategoriaOElConceptoTest`, `LaHojaDelMovimientoDiceLoQueHaceTest`,
`LoAnuladoSeRestauraTest` y `ElHistorialDelBancoSeRecorreTest` (`commonTest`), y en `:server`
`RestaurarUnAnuladoTest` (restaurar, el par, «Anulados», el aislamiento y lo que dice un aviso) y
`ElAvisoEnPartesTest` (el rastro, deshacer, restaurar el original y el aislamiento).

**La revisión de la vuelta 67** (capturas `rev67-*.png`) encontró dos fallas de plata y se arreglaron:

- **Dividir, deshacer y volver a dividir no infla la compra.** `GET /api/events/{id}/reparto` solo
  devuelve las partes vivas; al volver a dividir, las partes de la división deshecha quedan desligadas
  (`reparto_de` en NULL); «Lo anulado» nunca muestra una parte con `MOTIVO_DEL_REPARTO_DESHECHO`, y
  restaurarla contesta 409.
- **Lo que anuló el sistema no se restaura** (`porQueNoSeRestaura`, `:core`): un ajuste de saldo, lo
  anulado al deshacer un extracto (`MOTIVO_DEL_EXTRACTO_DESHECHO`), una parte de una división deshecha y
  un original dividido antes de esta vuelta (`MOTIVO_DEL_REPARTIDO_EN_PARTES` sin partes con
  `repartoDe`). El server contesta 409 con la frase, y la lista no ofrece «Restaurar»: dice por qué
  (`MovimientoAnulado.porQueNo`).
- **Restaurar en un aparato llega al otro**: si el server devuelve un movimiento que el teléfono anuló
  y ya subió, la marca local se borra y su efecto vuelve al saldo guardado
  (`LocalRepository.restauradasEnOtroLado`); una anulación sin subir sigue escondiéndolo.
- La campana: el desglose de lo que entró solo no cuenta la propuesta de saldar, que tiene su renglón.
- **«Anulados» salió de los chips**: Actividad termina con «2 anulados en octubre · Ver» (`FilaDeLosAnulados`),
  que abre **«Lo anulado»** de ese período (`Screen.Anulados(periodo)`); en Tú, junto a los mensajes del
  banco, «Lo anulado» abre todo. Cada fila: «$180.000 · 12 oct · anulado el 29 oct».
- Menores: el historial del banco pone el estado primero y no repite el banco; «Ver» de las partes las
  titula por su categoría; anular un traspaso dice que también se anula la otra mitad
  (`loQueTambienSeAnula`); el panel a 1280 con un chip dice «· del período» (`tituloDelPanel`); la lista de
  grupos dice lo que espera (`saldoEnLaListaDeGrupos`).

## 67. Vuelta 68: el resumen de la mañana, el cierre semanal de la pareja, un solo error por pantalla e Inicio sin repeticiones

Decisión del dueño (29-oct): «Sí, ambos, apagables». Y dos arreglos de la tercera revisión (capturas
`r3-err-*.png` y `r3-inicio-*.png`). **Sin IA y sin reglas de plata nuevas**: las cifras salen de las
funciones de Inicio y de Grupos.

1. **El resumen de la mañana** (8:00, canal «Resumen de la mañana»). Título: lo primero que hay para
   decir; texto: lo demás, separado por « · ».
   - «Ayer gastaste $85.000» (o «Ayer no anotaste gastos»): el «Gastaste» del día en Actividad
     (`gastoDeAyer` → `resumenDelDia`, la regla de «Gastos», con tu parte de lo que pagaron otros).
   - «Te quedan $6,8M para 4 días» («para hoy» el último día, «te pasaste por $X» en rojo): la cifra del
     héroe (`teQuedanDeHoy`), en millones como el detalle del héroe.
   - «2 avisos por confirmar», o «3 por revisar» si hay otras cosas: el número de Por revisar.
   - **No sale** sin cifras, con cifras que no son de hoy (la instantánea de anoche no se dice como de
     hoy: el Worker pide las nuevas, y si no llegan se calla), sin nada útil, o si ese día ya sonó.
   - Tocarlo abre Inicio. Con «Ocultar los saldos», ninguna cifra sale en claro (`PLATA_OCULTA`).
2. **El cierre semanal de la pareja** (domingo 19:00, canal «Resumen semanal»): «Esta semana gastaron
   $480.000 juntos en Casa con Caro» · «Caro te debe $265.000» (o «Le debes a Caro $50.000», o «Están a
   mano»: `fraseDelSaldo`, la de la tarjeta del grupo). El monto es **el gasto entero del grupo** de lunes
   a domingo; «juntos» lo dice. Varios grupos: «… juntos en 2 grupos», uno por renglón al desplegar, y
   tocarlo abre la lista. Sin un grupo con gastos esa semana no suena. Si el domingo no hubo red, el lunes
   dice «La semana pasada…».
3. **Los interruptores** van donde viven los demás avisos del teléfono (Captura del banco → «Avisos de
   Movi»): «Resumen de la mañana, a las 8:00» y «Resumen semanal de tus grupos, el domingo», con
   `FilaDeInterruptor` como los otros. **Prendidos por defecto**, también para quien ya usa Movi.
4. **Un solo error por pantalla.** `ErroresDeLaPantalla` + `NoSePudoLeerEnLaPantalla` + `AvisoDeLaPantalla`
   (`ui/components/UnSoloError.kt`): una sección caída se anota y no pinta nada (ni su título); arriba va
   **una** `NoSePudoLeer` «No pudimos cargar» con un «Reintentar» que reintenta todo. Sin quien junte, la
   sección pinta su tarjeta como siempre. Pagos lo usa (facturas, disponible, caja y la lectura del
   tablero). En Actividad la franja Entró · Salió · Neto **se va** si la lectura falló (`laFranjaSePinta`:
   esqueleto solo mientras carga) y el aviso de abajo no repite la tarjeta. En Inicio, con cifras
   guardadas a la vista, el encabezado dice **«Sin conexión · cifras de las 9:06»** (`lineaSinConexion`;
   «de ayer a las 21:05», «del 3 de octubre…») y tocarlo reintenta; el aviso de abajo queda para cuando
   no hay nada que mostrar. La tarjeta de avisos del banco y «N por revisar» de Actividad solo salen en
   el período en curso (`loPendienteSeVeEn`).
5. **Inicio sin repeticiones.**
   - El widget «Falta por pagar» no repite el pago de «Próximo pago» (es el primero de la misma cola):
     lista los siguientes; si era el único, dice «Es el de «Próximo pago»». La cifra y «Falta 1 de 5
     pagos» siguen contándolo.
   - Una sola puerta a «Editar inicio»: el botón del final. «Tus widgets» va sin «Editar».
   - La franja del héroe: «Por confirmar» y debajo **«$1.100.200 en 5 avisos · y 1 movimiento»**: la
     cifra va pegada a lo que suma (antes iba en el título y se leía como la suma de los seis).

Lo vigilan `ResumenDeLaMananaTest` y `ResumenSemanalTest` (lo que dicen, cuándo no salen, la semana sin
grupo y la huella), `UnSoloErrorYSinRepeticionesTest` (`commonTest`), y en Robolectric
`LosResumenesSeApaganTest` (los interruptores), `PlanScreenTest` (con la API caída, un aviso y un
«Reintentar») y `UnSoloErrorEnActividadTest`. **Las líneas base** no cambian, y las guardas en 0 siguen en 0.

## 68. Vuelta 70: «¿Me alcanza hasta que me paguen?»

De la revisión 4 del 29-oct (`r4-pagos-*.png`, `r4-err-pagos.png`), puntos 1, 2, 3 y 15. **Sin reglas de
plata nuevas**: la cuota cuenta, el pago de tarjeta no, un pago salda la cuota del período en que se hizo,
toda suma sin anulados. Ninguna cifra existente cambió; cambió hasta dónde mira la caja y cuál es su titular.

1. **El horizonte de la caja** (`horizonteDeLaCaja`, `cajaHastaQueTePaguen` en `:core` `CajaProyectada.kt`).
   «Tu plata, día a día» ya no termina el último día del período: llega **al día del próximo salario**, ese
   día incluido —el primer ingreso recurrente pendiente del período siguiente, que el server ya pone en el
   día que se espera (`fechaEsperadaDelIngreso`: el arranque del período, corrido si el corte cae en fin de
   semana o festivo)—; sin un ingreso recurrente, **30 días**, nunca menos que el cierre del período.
   - Los pagos del período siguiente que vencen dentro del horizonte entran con la regla de siempre (lo
     vencido hoy, una tarjeta su mínimo o «No entra a la cuenta», lo saldado no cuenta): son las filas de
     `checklistDelPeriodo` del período siguiente sobre las MISMAS respuestas, no otra lista.
   - El salario entra como subida el día que se espera, rotulado **«Llega tu salario (esperado)»**
     (`LLEGA_TU_SALARIO`).
   - **El vencimiento siguiente** (revisión): una regla cuyo pago de este período sigue pendiente no
     «rueda» (el día del salario, sin emparejar, el salario de hoy). El server manda en
     `UpcomingPayment.siguienteVencimiento` (opcional, `null` para un APK viejo y en un pago único) el que
     viene después, con `dueDateFor` y `fechaEsperada`; `checklistDelPeriodo(…, conElSiguienteVencimiento =
     true)` lo usa **solo para el período siguiente** y solo si el vigente cae antes, así que nada se
     cuenta dos veces y «Falta por pagar» del período no cambia.
2. **Una sola base: lo libre.** Cada día de la caja tiene dos cifras: `saldo`, lo que habrá en las cuentas
   (Tu plata + lo apartado: un apartado no mueve plata), y `libre`, **el titular**: el saldo menos el ahorro
   del período y lo apartado fuera de los fijos (`apartadoFueraDeLosFijos`, la única regla del cruce,
   sobre las filas que la caja resta, también las del período siguiente: un apartado que es un fijo nunca
   se resta dos veces). El día más justo, el rojo y la línea se miden en lo libre.
   - **Una sola frase con la cifra** (`fraseDePuente`, en `texto` o en `sale` si no alcanza): «Hasta el 25
     de nov, en tu peor día (el 5 de nov) tendrás $1,4M libres en tus cuentas · en las cuentas habrá $9,4M,
     de eso $8M es tu ahorro». **No usa «te quedan»**: ese verbo es del héroe, y las dos cifras miden cosas
     distintas («Te quedan» cuenta las compras con tarjeta como gastadas; la caja, cuando se pagan). Con el
     ahorro más grande que el saldo: «… en las cuentas habrá $6,8M: menos que tu ahorro de $8M». Sin ahorro
     ni apartados, sin la segunda mitad. La frase del rojo solo sale si el primer día en rojo no es el
     peor («El 3 de noviembre ya quedarías en −$100.000 libres por Arriendo…»).
   - **Lo libre de hoy contra «Te quedan»** (`clausulaDeLaDiferencia`): «lo libre de hoy» es el del primer
     día de la caja (con lo que vence hoy ya restado), la misma cifra que la frase dice si el peor día es
     hoy: una sola cifra de «hoy». Si difiere de «Te quedan» en más de $1.000, una cláusula dice cuánto
     —«hoy tienes $2,6M libres, $1,1M más que lo que te queda para gastar»— y la causa solo si **una
     medida** la explica con un margen del 10 %: los pagos en pesos que vencen después de hoy (o lo que
     falta por cobrar), o **las compras con tarjeta del período sin pagar** que mide el server
     (`DashboardSummary.comprasConTarjetaSinPagar`: el gasto contado en tarjetas menos lo que entró a ellas en
     el período). No basta con tener deuda en la tarjeta (la Master Black con diferidos a 36 cuotas
     siempre «alcanzaría»): lo que sobra puede ser tu parte de un gasto que pagó otro, una liquidación o
     un apartado cruzado con un fijo del siguiente. Si no se puede medir, sin causa.
   - Si el peor día es el último del horizonte, la fecha va una vez: «Hasta el 5 de nov, tu peor día es
     ese mismo día: tendrás…».
   - El renglón de abajo **ya no repite la cifra** («Saldo proyectado: $10,5M» era la tercera vez): con
     salario, «Llega tu salario: +$7M»; sin él, «Toca un día para verlo». Un día elegido dice
     «5 nov: $1,4M libres · Arriendo».
   - «Tu día más justo» de Inicio (`cajaDeHoy`) dice lo mismo: «Libre hasta que te paguen» (o «Libre en
     los próximos 30 días») · «Tu día más justo es el 5 de noviembre» · la cifra libre; si no alcanza, «No te
     alcanza» · «El 5 de noviembre quedarías en» (sin un salario anotado, «Sin salario anotado: miramos 30
     días»; con un ingreso fijo en otra moneda o sin monto, «Sin un salario en pesos anotado: miramos 30
     días»). Movi AI (`proyectar_caja`) usa la misma función.
3. **Las tres columnas del Disponible con el mismo patrón** (`ColumnaDeVentana`). Las tres dicen en grande
   **lo que queda** —en el período, `periodo.teQuedan`, la MISMA cifra del héroe de Inicio y de «Te
   quedan» en Pagos (`teQuedanDeHoy`)— y debajo, en tres renglones apagados, «gastaste» · $Y · «de $Z» (en
   el período, «en el día a día» en vez de «gastaste»: la regla de la vuelta 60, para no chocar con el
   «Llevas gastado» de «En qué se va»). La
   semana y hoy sin nada que repartir: «$0» y, en el último renglón, «ya te pasaste». Tres renglones y no
   uno: «gastaste $440.000» no entra en un tercio de tarjeta a 390 dp. El esqueleto tiene los mismos
   renglones.
4. **Pagos con ‹ ›** (`NavegadorDelPeriodoDePagos`, `ui/plan/PagosDeOtroPeriodo.kt`). La línea del rango va
   entre dos `BotonDeIcono`, desde el primer cuadro (nada salta). En «Facturas»: ‹ al anterior (sus pagos
   como en «Tus períodos», `PagosFijos`, cerrado), › al siguiente (**solo lectura**: «Lo que vencerá», su
   total con la regla de «Falta por pagar», y la nota de que lo que aún falta de este período aparece al
   pagarlo), y no más allá. Con otro período a la vista solo va su lista. En «Presupuestos» las flechas
   van apagadas. **En dos columnas** el navegador va a la derecha, encima de «Facturas | Presupuestos»;
   la izquierda reserva un renglón del mismo alto y, mirando otro período, dice «Período en curso ·
   octubre» y se atenúa (`OPACIDAD_DE_LO_ATENUADO`).
   - Las filas de «Lo que vencerá» y «Lo que viene» dicen de una tarjeta **lo que va a salir**: «mínimo
     $180.000», con la deuda debajo; sin el mínimo, la deuda y «Falta el pago mínimo: no suma». Sus totales
     usan la regla de la caja (`loQueSaleDe`): la tarjeta suma su mínimo.
5. **«Lo que viene · próximos 15 días»** (`loQueViene` en `:core`, `SeccionLoQueViene`): con 7 días o
   menos del período, debajo de la lista, las filas del siguiente que vencen en 15 días, con su total.
   **No cuenta en «Falta por pagar»**, que sigue siendo del período, y lo dice.
6. **La caja con su fuente caída** no se pinta: sin los pagos, el resumen o el perfil
   (`DisponibleDelPlan.fallo`), se anota en `ErroresDeLaPantalla` como las demás secciones y lo dice el
   aviso único de la vuelta 68. Antes pintaba «$10,5M» debajo del error. El aire de debajo de la caja va
   aparte, siempre: con la caja oculta «Tus períodos» ya no queda pegado a «Lo que se repite al mes».

Lo vigilan `SiguienteVencimientoTest` (server: el vencimiento siguiente con el corte del dueño y el día
del salario sin emparejar), `CajaHastaQueTePaguenTest` (`:core`: el horizonte con y sin salario y con el corte del dueño,
los pagos del siguiente, lo saldado, el mínimo, la subida del salario, lo libre con ahorro y con un
apartado que es un fijo, las invariantes y `loQueViene`), `CajaProyectadaRoutesTest` y
`LasPreguntasSugeridasTest` (el server), `CajaDelPeriodoTest`, `CajaDeHoyTest` y, en Robolectric,
`PagosHastaQueTePaguenTest` (‹ ›, «Lo que viene», la caja oculta con el error), `DisponibleEnInicioTest`
y `LasCifrasDelDisponibleEntranTest` (las tres columnas), `CajaEnHoyTest`, `UnNombrePorConceptoTest` y
`PlanScreenTest` (nada salta más de 8 dp). **Las líneas base** no cambian, y las guardas en 0 siguen en 0.

## 69. Vuelta 71: Agregar no pierde nada, y la compra con tarjeta queda completa

De la revisión fresca 4 del 29-oct (capturas `r4-agregar-*.png`, `r4-mov-mas.png`). **Sin reglas de plata
nuevas**: una compra con tarjeta sigue contando entera el día que se hizo, y solo el extracto de la
tarjeta (`extractoDeLaTarjeta`) la reparte entre los cortes.

1. **Lo escrito viaja entre pestañas.** El monto se lleva al cruzar entre Gasto/Ingreso y Traspaso/Pagar
   (`cambiarDePestana`): con $45.000 en Gasto, Traspaso arranca en $45.000. La fecha y la nota son **una**
   para las cuatro (`TransferBody` las recibe de la hoja: `fechaDeLaHoja`, `notaDeLaHoja`). La categoría
   no viaja: es de cada tipo.
2. **«¿Descartar $1.250.000?».** Con un monto o una nota escritos (distintos de lo que la hoja trajo al
   abrirse: un «Anotar pago» cerrado sin tocar no pregunta, `hayAlgoQuePerder`), la X, el velo, el
   arrastre, Escape y el «atrás» de Android pasan por `pedirCerrar` y abren `ConfirmarEnHoja`
   («¿Descartar $X?» o «¿Descartar la nota?» · «Lo que escribiste no se guarda.» · Cancelar / Descartar en
   `Peligro`). Sin nada escrito, cierran directo. El marco devuelve la hoja a su lugar si no se cerró.
3. **Las pestañas no saltan.** La fila Gasto · Ingreso · Traspaso · Pagar (`TAG_PESTANAS_DE_AGREGAR`) va
   **fuera** del desplazamiento, fija arriba. Con teclado en pantalla la hoja ocupa todo el alto que tiene
   y el cuerpo se apoya abajo: las pestañas no se mueven arriba y el teclado no se mueve abajo en ningún
   tipo; lo que cambia de alto queda en el medio. Con la Nota abierta la hoja se acorta a ella, como desde
   la vuelta de diseño 13. Sin teclado en pantalla (frente a un computador) la hoja nunca se encoge
   mientras está abierta: crece hasta lo más alto que midió.
4. **«¿A cuántas cuotas?»** (`CuotasEnAgregar.kt`). Con una tarjeta de crédito en «Gasto» (no en modo
   pago), debajo de «Cuenta»: pastillas `PildoraDeFiltro` compactas `1 · 3 · 6 · 12 · 24 · 36 · Otra` en un
   renglón que se desliza, 1 por defecto; «Otra» se corre con «−» y «+» (`PildoraDeAccion` compactas) en el
   mismo renglón, de 2 a 72. Debajo, en `apoyo` con tres renglones reservados (elegir no cambia el alto):
   «≈ $100.000 al mes, sin intereses» (con más de una) y «Cuenta completa hoy en tus gastos; en el extracto
   de la tarjeta va $100.000 por mes». Se guarda en `FinancialEvent.cuotas`.
5. **Dividir una compra con tarjeta.** «Dividir en categorías» se ofrece en un gasto suelto de una
   tarjeta de crédito; el editor no pregunta «Qué fue»: solo gastos con su categoría
   (`tiposDeParteDesde`).
6. **Un solo nombre.** «Pagar tarjeta» (`PAGAR_TARJETA`) en la acción del Inicio, el menú del «+» y del
   rail. La pestaña de Agregar que paga una tarjeta o la cuota de un crédito se llama **«Pagar»** (una
   sola: es la misma operación y lo que se paga lo dice el destino), y su destino vacío dice **«¿Qué
   pagas?»** (sin «Elegir cuenta · Pagas»); el selector se titula igual.
7. **Comillas.** Movi cita con «»: «Crear «Mascotas»», «¿«Netflix» se repite todos los meses?». Las
   catorce comillas rectas de `ui/` se cambiaron, y `ComillasRectasScanTest` no deja volver un `\"` dentro
   de un literal de `ui/`.

**La revisión de la vuelta 71** (capturas `rev71-*.png`) cambió cinco cosas:

- **La cuota que propone Pagar no es algo escrito.** `TransferBody` la avisa aparte
  (`onMontoPropuestoPorLaHoja`): mientras la cifra sea esa, no viaja a Gasto ni pregunta «¿Descartar?».
  Teclear encima o tocar una pastilla del extracto la vuelve del dueño.
- **Las cuotas se mandan solo si el dueño tocó la fila** (`cuotasElegidas`, `cuotasParaGuardar`): un «1»
  que nadie eligió no se guarda, porque el extracto solo llena las cuotas de un movimiento que no las
  tiene.
- **La hoja toma todo el alto también sin teclado en pantalla si es angosta** (< 600 dp, la web con
  mouse): ahí el cuerpo va arriba. Solo la centrada sin teclado crece sin encogerse.
- **«Guardar movimiento» siempre a la vista**: la barra (Repartir · Subir comprobante · Guardar, y el
  renglón de «Falta el monto» / «Guardar y anotar otro») va **debajo** del desplazamiento
  (`BarraDeGuardarDeAgregar`), no al final del cuerpo. La ayuda de las cuotas reserva dos renglones:
  «En el extracto, unos $100.000 al mes, sin intereses.» y «Cuenta completa hoy en tus gastos.».
- **Sin glifos que Figtree no tiene.** «≈» salía como un cuadro vacío: se dice «unos» (las cuotas, la
  TRM: «Unos $4.100 por dólar»), «~» en la deuda estimada de una tarjeta, y «pagado» sin «✓».
  `GlifosDeFigtreeScanTest` abre `figtree_regular.ttf` y no deja en un literal de `ui/` un carácter que la
  letra no dibuje («⌫» es la clave de una tecla con ícono y queda afuera).

También: el detalle de una compra diferida dice «a 3 cuotas» debajo de la cifra, y al dividir un
movimiento cuya descripción es solo su categoría cada parte lleva la suya.

Lo vigilan `AgregarNoPierdeNadaTest` (el monto, la nota y la fecha que viajan; «¿Descartar?» y el cierre
directo; las pestañas quietas en los cuatro tipos; la fila de cuotas solo con tarjeta y las cuotas que
llegan a `postEvent`; «Pagar» y «¿Qué pagas?»), `AgregarPorDentroTest` (el «atrás» que pregunta),
`DividirUnaCompraConTarjetaTest`, y en el server `LaCompraConTarjetaTest`. **Las líneas base** no cambian,
y las guardas en 0 siguen en 0.


## 70. Vuelta 72: pagos que se manejan solos

De la revisión 4 del 29-oct (`r4-pagos-es-el-de.png`, `r4-regla-carro*.png`, `r4-anotar-pago-carro.png`),
puntos 4, 5, 6, 13 y la parte de Pagos del 14. **Sin reglas de plata nuevas** más allá de dos: una regla
pausada no cuenta, y una tarjeta pagada por debajo de su mínimo no está hecha. Ninguna cifra de una regla
activa cambió. Un componente nuevo en `ds/`, `SelectorDeDiaDelMes`; `ui/profile/CalendarioDeDias` se fue.

1. **La tarjeta pagada por debajo del mínimo no queda «Pagado ✓»** (`PagoDelPeriodo.faltaDelMinimo` y
   `hecho`, `:core`). Si lo pagado a la tarjeta en el período (`OccurrenceState.pagadoEnElPeriodo`: la suma
   de todos los pagos que la tildan; `montoDelPago` sigue siendo el último y «Llevas $X pagados» no cambia)
   es menor que su `pagoMinimo`, la fila va con lo pendiente y, donde iría la pastilla de vencimiento,
   dice **«Pagaste $100.000 · faltan $80.000 para el mínimo»** en `apoyoMedio` color `aviso` (un texto y no
   una `Pildora`: a 360 dp la frase se parte). No cuenta en «queda 1 de 5» (`avanceDelChecklist` y
   `pagosPendientes` miran `hecho`), `faltaPorPagar` y la caja suman lo que falta del mínimo, y la cola —la
   campana, «Movi propone hoy», «Próximo pago»— lo dice con esa cifra (`cifraDelPago`). Sin mínimo, en otra
   moneda o con el mínimo cubierto, como siempre.
2. **El emparejamiento con sentido** (`candidatoFueraDeProporcion`, `:core`; `candidatosPuntuados`,
   `:server`). Un candidato que pasa del **doble** del monto de la regla o queda por debajo de la **mitad**
   no se propone (el doble y la mitad exactos sí; umbrales elegidos). Solo filtra la lista del «¿Es este?»:
   lo que Movi empareja sola (el nombre que pega; categoría + cuenta + monto exacto; el destino con monto
   exacto) no pasa por ahí, así que ninguna fila tildada se destilda ni se mueve el Disponible. Una tarjeta
   (`montoEsSaldo`), una regla sin monto o un candidato en otra moneda no se comparan; el abono a una deuda
   tiene su emparejador. **«Sí, fue este» va con borde** (`PropuestaDeMovimiento(confirmarEsPrincipal =
   false)` por defecto; `accionPrincipalDeLaFila` con propuesta da `NINGUNA`): tildar es decisión del dueño.
   «Sí, es la cuota» de la cuota que se parece sigue como estaba.
3. **«+ Agregar» siempre y «Pausar».** «Tus facturas del período» lleva **«+ Agregar»** en su `Seccion`
   (`AGREGAR_UN_PAGO`), que abre la misma hoja de alta que el vacío. La hoja de una regla trae **«Pausar»**
   (`LaPausaDeLaRegla`: una frase y `Boton` `Secundario` `S`; pausada, «Pausado desde el 29 oct: …» y
   «Reanudar»), sin confirmación —no borra nada—; «Eliminar» sigue preguntando. Al final de Pagos,
   **«Pausados (N)»** plegada («Ver» / «Ocultar»), cada una con «Pausado desde el 29 oct · $180.000» y
   «Reanudar»; tocarla abre su hoja. Con todo pausado no se pinta el vacío. Qué excluye la pausa está en
   `CLAUDE.md` (una sola regla, `pausadaParaElVencimiento`).
4. **«Anotar pago» de una regla sin cuenta** ya no pide la cuenta cada mes: Agregar en modo pago la propone
   con la lógica de Gasto (`resolverCuentaDelMovimiento`: «La que usas para Mercado», «Más usada»…), editable.
   Después, la fila pagada en Pagos propone **«¿Usar siempre Nu para Carro?»** con «Guardar en la regla»
   (`Secundario`) y «Ahora no» (`Fantasma`, recordado en `PropuestasDescartadasStore` por regla y cuenta),
   con la cuenta del movimiento que la pagó (`OccurrenceState.cuentaDelPago`). Nunca se guarda sola.
5. **Una sola forma de elegir el día del mes**: `ds/SelectorDeDiaDelMes`, filas de siete círculos de
   `Medida.avatar` (lo elegido en tinta, lo demás en la capa 2), la celda entera tocable (un séptimo del
   ancho × `Medida.objetivoTactil`: a 360 dp siete círculos con su aire no cabían), cada día un radio con su
   estado. Lo usan la hoja del pago fijo (eran cuadrados), «Inicio del período» y «¿Cuándo empezó…?».
6. **«Editar recurrente» con el encabezado de hoja del sistema** (§45): `EncabezadoDeHoja` (título grande;
   la X la pone el marco) y **«Eliminar» como `Boton` `Peligro` `S`** a su lado, como la hoja de un tercero
   (era un `tituloMedio` con un texto rojo suelto).

Lo vigilan, en `:core`, `Vuelta72LogicaTest` (el mínimo debajo, igual, encima y sin mínimo; la lista, la
caja, la cola y «Falta por pagar» de acuerdo; los umbrales; la pausa en el checklist y lo pagado que queda);
en `:server`, `ReglaPausadaTest` (pausar y reanudar por API con aislamiento, el PUT viejo que no reanuda, y
la pausada fuera de upcoming, ocurrencias, checklist, caja, recordatorios, débitos, gasto variable, Tus
períodos y Movi AI), `ElEmparejamientoConSentidoTest` (Carro $9.000 contra Terpel $260.000, los bordes, lo
concluyente que no cambia), `LoPagadoALaTarjetaTest` y `SchemaDeArranqueTest` (`pausada_desde`); y en
Robolectric `PagosQueSeManejanSolosTest` («+ Agregar», «Pausados (N)» y «Reanudar», la tarjeta bajo el
mínimo, «Sí, fue este» con borde, la propuesta de la cuenta, el selector único y el encabezado),
`AnotarPagoSaldaLaFacturaTest` y `LaCuentaQueProponeAgregarTest` (la cuenta propuesta). **Las líneas base**
no cambian, y las guardas en 0 siguen en 0.

### La revisión de la vuelta 72, y lo que cambió

1. **Pagar y pausar el mismo día no pierde el pago.** La rama de la pausa acepta un pago de **hasta** el día
   de la pausa, y `/pausar` sella antes lo que Movi emparejó sola en el período en curso.
2. **Lo que falta del mínimo va con su extracto.** El mínimo de $180.000 es del extracto cortado el 20-oct,
   que vence el 5-nov; el pago de $100.000 tilda la fila del 5-oct. Ahora la regla de la tarjeta trae
   `minimoDelExtracto` (fecha de pago, mínimo y lo pagado desde el corte, de `ExtractoDeLaTarjeta`, la misma
   fuente que la hoja «Pagar»), y solo **la fila que vence en esa fecha** lleva lo que falta: su urgencia es
   la de esa fecha, la caja lo resta una vez ahí, y con el mínimo cubierto la fila está hecha («Mínimo
   cubierto») y no queda nada en la caja ni en «Lo que viene». La fila de octubre, pagada, queda «Pagado ✓».
3. **«Llevas $X pagados» y la fila pagada dicen el total del período** (`pagadoParaMostrar`), no el último pago.
4. **La pausa rige desde su día; lo vencido sin pagar sigue pendiente.** «Pausar» con algo vencido pregunta
   «Carro de octubre ($9.000) no aparece pagado» (`PreguntaDeLoVencidoAlPausar`): «Dejarlo pendiente» (por
   defecto), «Ya lo pagué» (pausa y abre «Anotar pago») y «No se debe» (no hay un descarte de ocurrencia en
   Movi: pausa desde ese vencimiento). Desde Pagos va en una hoja con `EncabezadoDeHoja` «Pausar «Carro»».
5. **Menores.** «Pausados · N» como el resto del sistema; «¿Usar siempre…?» no aparece en una pausada;
   «Pausar» también en el «⋯» de una fila sin pagar (`TAG_MENU_DE_LA_FILA_PENDIENTE`, el mismo menú de la
   pagada); y la caja de Pagos sale de las cuentas que el tablero lee con los vencimientos, así que tras
   «Anotar pago» Tu plata no queda vieja.

Lo vigilan `Vuelta72LogicaTest` (la fila del extracto, la caja que resta $80.000 una vez el 5-nov y nada al
pagarlo, la fila de octubre sin «vencido», el total pagado, lo vencido que sigue y «No se debe»),
`ReglaPausadaTest` (pagar hoy y pausar hoy, con y sin sello; lo vencido que sigue pendiente),
`ElMinimoDelExtractoEnLaReglaTest` y, en Robolectric, `PagosQueSeManejanSolosTest` («Pausados · 1», el
«⋯» con «Pausar», la pregunta con «No se debe» y «Dejarlo pendiente», sin propuesta de cuenta en una pausada,
«Llevas $180.000 pagados»).

### La re-revisión de la vuelta 72

1. **El mínimo efectivo nunca pasa de lo facturado al corte** (`ExtractoDeLaTarjeta.minimoEfectivo` y
   `minimoQueFalta`, en `:core`; los usan `loadCardRulePairs` y la hoja «Pagar»). Un mínimo tecleado de $180.000
   sobre $60.000 facturados se salda pagando $60.000, y con lo facturado en cero no se pide nada («Mínimo
   cubierto · sin saldo facturado»).
2. **Una tarjeta sin extracto** (sin día de corte) vuelve a la regla de la primera ronda: lo pagado en el
   período contra su `pagoMinimo` decide si está hecha, y la caja y la cola dicen lo que falta. Con el mínimo de
   un extracto en la regla, las otras filas de la tarjeta (de un extracto anterior, cuyo mínimo no se conoce)
   quedan como siempre: compararlas con el mínimo del extracto nuevo era el «Pago vencido · hace 24 días» falso.
3. **«Pausar» no se ofrece en la fila vencida de una regla ya pausada**, y `/pausar` sobre una regla pausada
   **conserva la fecha más temprana** (la adelanta, nunca la corre hacia adelante): un segundo «Pausar» no
   devuelve lo que «No se debe» sacó.
4. **«Falta por pagar» suma siempre el mínimo pendiente del extracto** —entero sin pagos, lo que falta con un
   abono—, como lo resta la caja: abonar ya no la hace subir.
5. La pregunta de «Pausar» desde el «⋯» usa la fila tocada; Movi AI dice «falta $80.000 del mínimo de
   $180.000»; y el barrido de recordatorios no avisa por un mínimo del extracto ya cubierto (`minimoCubiertoEn`).

## 71. Vuelta 73: Inicio sin cortes, Tú ordenado, los terceros que cuadran con los grupos

De la revisión 4 del 29-oct (`r4-inicio-360.png`, `r4-inicio-390-b.png`, `r4-inicio-1280-b.png`,
`r4-terceros-390.png`, `r4-tercero-caro-390.png`, `r4-anotar-abono.png`), puntos 8, 9, 10, 11 y la parte de
Tú del 14. **Sin reglas de plata nuevas**: ninguna cifra de datos sanos cambió; `patrimonioDe` quedó igual.
Ningún componente nuevo en `ds/`.

1. **La franja «Por confirmar» no se corta a 360** (regresión de la vuelta 68). Con la pastilla «6 por
   revisar» y la flecha a la derecha, el renglón «$1.100.200 en 5 avisos · y 1 movimiento» tenía ~100 dp a
   360 y se cortaba en «y 1 movimien…». Ahora **la pastilla va solo cuando no hay renglón**
   (`pastillaDeLaFranja`): el renglón ya dice qué espera, la pastilla repetía la cuenta. Sin renglón («Por
   confirmar» con una sola fuente y sin suma) la pastilla es lo único que dice cuántos son, y se queda. El
   renglón llega hasta 3 líneas (`LINEAS_DE_LA_FRANJA_POR_CONFIRMAR`, para «· y 2 antiguos» con la letra
   grande). La cifra sigue pegada a lo que nombra (vuelta 68).
2. **El widget «Falta por pagar» no queda casi vacío.** Si su único pendiente es el de «Próximo pago», no se
   pinta (`elWidgetDeFaltaSoloRepiteElProximoPago`, en `visibleSections`): en pantalla ancha y en el
   teléfono «En qué se va» queda solo en su bloque, a todo el ancho (`bloquesDelInicio` ya pintaba así un
   widget suelto); si el widget iba solo, el hueco se cierra. Con dos o más pendientes, como siempre.
3. **El tercero cuadra con los grupos.** La ficha de un tercero miembro de un grupo lleva arriba, por grupo,
   una `Fila` en una `Superficie` de lista: «En Casa con Caro» y debajo «Puso $240.000 · te debe $168.050»
   (o «le debes $X», «están a mano», «a mano contigo» con más de dos), con el círculo neutro y la flecha;
   tocarla abre el grupo. Las cifras salen **tal cual** del `DetalleDelEspacio` que arma el server con
   `saldoDelEspacio`: el `puso` del período (lo de «Quién puso qué») y el reembolso entre los dos (lo de la
   tarjeta del saldo); nada se recalcula (`elTerceroEnLosGrupos`). «Le enviaste / Te envió» siguen siendo
   las transferencias, con el rótulo «Transferencias este período»; sin ninguna y con un grupo de por medio,
   **«Sin transferencias este período»** en vez de dos «Nada». **«Personas y comercios» se mudó a Tú**: una
   `Fila` de «Lo tuyo» (`FilaDePersonasYComercios`, ícono `Contacts`), entre Grupos y Cuadre de saldos; ya
   no vive al final de un grupo, y su «volver» cae en Tú. Desde un grupo se llega con «Nuevo grupo».
4. **El crédito sin saldo.** Causa: la deuda de un préstamo sale de sus movimientos, y las condiciones no
   escriben ninguno; sin desembolso ni ajuste queda en $0 y la primera cuota la deja negativa (la demo:
   Vehículo 4083, capital $40M, −$980.000). `patrimonioDe` suma cada deuda con su signo (decisión de la
   vuelta 59), así que ese negativo sube el neto. **`patrimonioDe` no cambió**: sacarle el signo movería
   cifras de datos sanos (la curva del patrimonio en los meses anteriores a un ajuste de saldo) sin
   hacerlas más ciertas. Lo que cambió es que se dice: `creditoSinSaldo` (`:core`; LOAN con `principal > 0`
   y deuda < 0, o en 0 sin movimientos) hace que **la ficha** diga «Falta el saldo de este crédito» con
   «Escribe lo que debes hoy según el banco» y **«Cargarlo»** (la hoja de ajuste de siempre), y que
   **Patrimonio**, con ese solo crédito sin saldo, diga «Falta el saldo de Vehículo 4083» con «Cargarlo» en
   vez de «Incluye 1 crédito sin saldo · Cuadrar». **«Anotar este abono»** habla de deuda positiva:
   «Debes $980.000 · después del abono, $480.000» (y «después del desembolso» al revés). La cuenta no tenía
   un error de signo —el abono resta, el desembolso suma—; lo que fallaba era el punto de partida, y un
   abono sobre un crédito sin saldo ahora dice «Falta el saldo de este crédito · Cargarlo ›», como la
   pestaña Cuota desde la vuelta 49.
5. **Los avisos en un solo lugar: Tú → Avisos.** En Android, `AvisosDeEsteTelefono` (expect; vacío en web e
   iOS, donde esos avisos no existen) pinta en la misma lista, después del correo de cierre, la fila «Avisos
   en este teléfono» (activos, o «Permitir» / «Abrir ajustes») y los siete interruptores del teléfono:
   movimientos, pagos por vencer, banco mudo, débitos automáticos, cierre del período, resumen de la mañana
   y resumen semanal, como `Fila` + `Interruptor` del sistema (era una tarjeta con `Switch` y `sp` sueltos).
   Se guardan donde siempre (`PreferenciasDeAvisos`): no se migró nada. **Captura del banco** perdió
   «Avisos de Movi»; queda con la captura (permisos, notificaciones del banco, hibernación, historial y el
   umbral de banco mudo, que es de la captura) y una `FilaDeNavegacion` «Avisos · Se encienden y se apagan
   en Tú».

Lo vigilan `Vuelta73Test` (commonTest: la pastilla, el widget en `visibleSections` y `bloquesDelInicio`, las
líneas del tercero con `saldoDelEspacio`, el texto del abono, `creditoSinSaldo` y `patrimonioDe` igual con
créditos sanos) y `Vuelta73EnPantallaTest` (Robolectric, 360 dp, `NATIVE`: la franja a ×1,12 y a ×1,456 sin
«…», el widget oculto con «En qué se va» a lo ancho, la ficha de Caro con su grupo, los avisos en Tú y
Captura sin ellos y con el enlace). Se actualizaron `ElInicioDeLaOpcion3Test`, `MasScreenTest`,
`LosResumenesSeApaganTest` (ahora en `ui/mas`), `LosCreditosDicenLaVerdadEnPantallaTest`,
`EspaciosEnDosColumnasTest`, `TransferFormTest` y `ElAmarilloEsDeLaAccionTest`. **Las líneas base** no
cambian, y las guardas en 0 siguen en 0.

## 72. Vuelta 74: un toque por movimiento

De la revisión fresca 5 del 30-oct (`r5-dia-05-avisos-nuevos.png`, `r5-dia-06-revisar-crepes.png`,
`r5-dia-07-es-este-a.png`, `r5-dia-08-confirmar-a.png`, `r5-dia-17-cuota-aviso.png`, `r5-dia-03-guardado-*.png`),
puntos 1, 5 y 13. **Sin reglas de plata nuevas**: «Es este» vincula (lo que ya hacía `…/confirm?eventoId=`),
«Deshacer» es anular (vuelta 67) o desvincular (la vuelta exacta del vincular), y repartir es el `…/compartir`
de siempre. Ninguna cifra existente cambió. **Un componente pasó a `ds/`**: el aviso de abajo
(`ds/AvisoDeDeshacer.kt`, antes en `ui/components`).

1. **El aviso que ya está anotado se cierra con «Es este».** Con la MISMA detección de «¿Ya lo anotaste?»
   (`PropuestaDelAviso.coincidencias`, lo que contesta `GET /api/sms/{id}/coincidencias`):
   - **La tarjeta de Por revisar** (`TarjetaDeMensajeDelBanco` y `TarjetaDelMismoPago`, parámetro
     `yaAnotado: YaAnotadoEnLaFila`) dice primero «Ya anotado: Restaurantes $32.000 · hoy»
     (`lineaDelYaAnotado`, `apoyoMedio`) y sus acciones son **«Es este»** (`PastillaDelPago` principal, el
     amarillo) · «Revisar» · «Ignorar». «Es este» se resuelve en el toque, sin abrir nada: el aviso suelto por
     `confirmSmsCon(id, eventoId)`, un comprobante por `confirmarComprobante`, un pago avisado varias veces por
     `confirmarElMismoPago(…, eventoExistenteId)`. **Nunca crea un movimiento.** Con dos o más candidatos no
     hay un toque: «Puede ser uno de 2 anotados» y **«Elegir»** (amarillo) abre Revisar aviso.
   - **Revisar aviso**: con un candidato, la barra de abajo es «Anotar aparte» (secundario) · **«Es
     este»** (principal, peso mayor), y debajo «Anotar aparte» crea otro movimiento, además del que ya
     está anotado.» con «Ignorar» como fantasma; la fila del candidato ya no lleva su propio botón. Con
     varios, cada fila lleva «Es este» principal y abajo no hay amarillo («Ignorar» · «Anotar aparte»).
   - **La cuota** ($980.000 que coincide con la cuota ya anotada) va por lo mismo: «Es este» vincula el aviso
     a la pata del dinero que ya estaba. No hay un camino nuevo de cuotas y nada salta
     `escribirLasPatasDelAviso` (no se escribe ninguna pata).
2. **«Anotado · Deshacer», y repartir en un toque.** El aviso de abajo (`AvisoDeDeshacer`, en la cáscara por
   `LocalAvisoDeDeshacer`) es **el** aviso de la app: anular (vuelta 67) y ahora guardar en Agregar (gasto,
   ingreso, traspaso o pago), confirmar un aviso (en la fila o en Revisar aviso) y «Es este». Dice qué quedó
   («Anotado · Restaurantes $32.000»: el comercio si lo hay, si no la categoría — `textoDeLoAnotado`), lleva
   **Deshacer** 8 s (`DURACION_DEL_AVISO_DE_DESHACER`, también para «Anulado»; la cuenta se para mientras
   trabaja o se elige) y **una acción** (`AvisoDeDeshacer.Accion`): **«Repartir con Caro»** si es un gasto que
   se puede repartir (`sePuedeRepartir`, sin grupo todavía) y el dueño tiene grupos; con varios, «Repartir»
   abre una hoja «¿Con qué grupo lo repartes?» (`Accion.opciones`). Repartir usa `repartirMovimiento` (el
   `…/compartir`, con la regla del grupo) y el aviso pasa a «Repartido con Caro · tu parte $16.000 ·
   Deshacer» (deshacer = `quitarDelEspacio`). En el teléfono sin señal, `LocalRepository.repartirMovimiento`
   lanza `MovimientoSinSubir` y el aviso dice `REPARTO_SIN_CONEXION` (vuelta de diseño 24) sin pedir nada.
   **Cómo se deshace cada cosa** (`deshacerLoAnotado`):
   - Agregar (gasto/ingreso): anula el movimiento (`voidEvent`); si se guardó con «Es el aviso de hace 2 h»,
     ese aviso vuelve a Por revisar (desvincular). Traspaso y pago: anula la pata que salió (la otra se
     anula con ella por su `transferId`).
   - Confirmar un aviso que creó su movimiento (fila o Revisar aviso, suelto, pago avisado varias veces o de
     dos patas): primero el aviso vuelve a pendiente (`POST /api/sms/desvincular`), después se anula lo creado.
   - «Es este»: solo desvincula. El movimiento queda igual.
   - **Sin Deshacer** (no hay vuelta limpia): un comprobante (su papel quedó colgado de la cuenta), un aviso
     repartido en partes (varias partes con sus sellos), el modo pago de Agregar y un abono que se tilda (los
     dos dejarían un sello de período apuntando a un anulado). Ahí el aviso dice «Anotado» sin el botón (o no
     sale, en el modo pago, como antes).
   El aviso vive abajo del hueco de la pantalla: encima de la barra de pestañas (no la tapa) y a la derecha
   del rail (donde está el «+»). Entra y sale con `Movimiento.alEntrar` / `alSalir`, que con movimiento
   reducido son un corte. El «¿esto se repite?» de App.kt espera a que se vaya y se descarta si se deshizo.
3. **«Es este» sin recargar la bandeja.** En la fila, la tarjeta sale enseguida (lo resuelto se anota antes
   de que conteste el server) con `animateItem` (sin animación con movimiento reducido), la lista no pasa a
   esqueleto (las lecturas conservan su valor; «Actualizando…» arriba) y el scroll queda donde estaba. Si el
   server falla, la tarjeta vuelve y el error se dice arriba de los mensajes. En Revisar aviso, «Es este»
   pasa por `sinVaciarLaBandeja`: la única escritura de la que se sabe exactamente qué cambió (el estado de
   esos avisos), así que lo que la bandeja recordaba se repone con ellos confirmados y al volver no hay
   esqueleto. El conteo del Inicio y de la campana baja enseguida (`elInicioCuentaOtrosAvisos`, sobre
   `DashboardDataCache.data`; la lectura de verdad llega igual y manda).

Lo vigilan, en el server, `EsEsteYSuDeshacerTest` («Es este» no crea un movimiento y las cifras siguen
iguales; desvincular devuelve el aviso sin tocar el movimiento; segundo deshacer 404; aislado por usuario;
deshacer un aviso que creó su movimiento = desvincular + anular, y las cifras vuelven); en `:shared`
`Vuelta74LogicaTest` (commonTest: las líneas, lo que se repone, los textos del aviso, la acción que se dice a
sí misma y la elección) y, Robolectric, `EsEsteEnLaFilaTest` (la tarjeta con «Es este», «Puede ser uno de 2 ·
Elegir», «Es este» sin esqueleto y con la lista quieta y su Deshacer, la falla que devuelve la tarjeta),
`EsEsteEnRevisarAvisoTest` («Es este» principal y «Anotar aparte» secundario, con varios y sin
ninguno) y `AnotadoConDeshacerTest` (Agregar: «Anotado · Deshacer» que anula, «Repartir con Caro» con grupo
y ausente sin él, varios grupos, sin señal). **Las líneas base** no cambian, y las guardas en 0 siguen en 0.

### La revisión de la vuelta 74, y lo que cambió

La plata cuadraba («Es este» no crea nada; cada Deshacer deja las cifras exactas). Lo que encontró:

1. **[P1] Un movimiento vinculado a un aviso se ofrecía a otro.** Dos compras reales de $32.000 en Crepes
   (12:45 y 12:58): tras «Es este» en la primera, la segunda decía «Ya anotado… Es este», el server lo
   aceptaba y los dos avisos quedaban con el mismo `evento_id` (una compra perdida). Ahora
   `movimientosYaReclamados` (el `evento_id` y las `partes_eventos` de los avisos confirmados del usuario)
   sale de las coincidencias (`coincidenciasDelAviso`, con las dos patas de un traspaso reclamado), y
   vincular uno de esos contesta **409** «Ese movimiento ya está vinculado a otro aviso del banco.»
   (`…/confirm?eventoId=` y `confirmarElMismoPago` con `eventoExistenteId`). Los avisos **del mismo
   pago** (su grupo) sí lo comparten: es lo que hace «Cerrar los avisos».
2. **[P2] Falsos «ya anotado» en un pago de tarjeta.** Un pago de Nu por $50.000 decía «Puede ser uno de 3»
   (las dos patas de un traspaso a Efectivo y un Mercado). `coincidenciasDelSms` ahora, para
   `CARD_PAYMENT_CATEGORY`, acepta solo esa categoría o lo que **entra a una cuenta `CREDIT_CARD`**
   (la pata del pago o el abono anotado a mano; recibe los tipos de cuenta); para un gasto, **nunca la
   pata que entra** de un traspaso; y deja **un candidato por `transferId`** (el que sale).
3. **«Anotar aparte».** «Anotarlo como nuevo» se cortaba a 390 y a 360; el rótulo es corto y «Es este» va
   con peso 1,2 contra 1.
4. **El aviso de abajo no se parte por letras.** Con una acción, el texto va a lo ancho (hasta
   `RENGLONES_DEL_AVISO` = 2, con «…») y «Repartir con Caro» · «Deshacer» en un segundo renglón a la
   derecha; sin acción, como antes. A 360 ocupaba 9 renglones y ~210 px.
5. **Doble toque.** Al resolver una tarjeta (Es este, Confirmar, Ignorar), la lista ignora los toques
   `PAUSA_TRAS_RESOLVER_MS` (300 ms): la de abajo sube bajo el dedo.
6. **Textos.** «Es este» dice «Ya estaba anotado · Restaurantes $32.000» (`textoDeLoQueYaEstaba`). El lote
   de Actividad también usa el aviso de `ds/`. En el Inicio, al bajar el conteo baja la suma
   (`porConfirmarSuman` menos el monto del aviso; si no se sabe en pesos, la suma se calla hasta la
   lectura, nunca a medias).

Lo vigilan `EsEsteYSuDeshacerTest` (las dos compras: no se ofrece ni se acepta, y vuelve a estar libre al
deshacer; el pago de Nu con su traspaso, su Mercado y las patas), `CoincidenciasDelSmsTest`,
`Vuelta74LogicaTest` (el texto y la suma del Inicio), `EsEsteEnLaFilaTest` (el doble toque),
`EsEsteEnRevisarAvisoTest` a 390 y 360 (los dos botones enteros, «Es este» no más angosto) y
`ElAvisoDeAbajoNoSeParteA360Test` / `A390Test` (dos renglones, a lo ancho, ≤ 140 dp, con un comercio largo).

## 73. Vuelta 75: el salario como ancla

De la revisión fresca 5 del 30-oct (`r5-*.png`), puntos 2, 3, 4, 8, 11 y 12. **Una sola regla nueva**, la
del horizonte sin salario (punto 1); ninguna otra cifra cambió. La §72 es de la vuelta 74, en paralelo.

1. **Sin salario, la caja llega al período siguiente** (`horizonteDeLaCaja(…, conElPeriodoElegido)` en
   `:core` `CajaProyectada.kt`). Si el dueño **eligió su período** (`!PeriodSettings.esMesDeCalendario`: otro
   corte, la regla del día hábil o un inicio a mano) y no hay un ingreso recurrente en pesos que cierre el
   horizonte, la caja llega al **arranque del período siguiente** (el día después del cierre: el día que dijo
   que le pagan, ya corrido por fin de semana, festivo o excepción) y no a 30 días: «25 · Es el día que me
   pagan» miraba «Hasta el 28 de nov». Con el mes de calendario que tiene quien nunca lo tocó siguen los 30
   días. `CajaProyectada.hastaElPeriodoSiguiente` solo cambia lo que se dice. La app (`cajaDelPeriodoDe`) y el
   server (`cajaProyectadaDe`, Movi AI) pasan lo mismo. Las invariantes de la vuelta 70 (lo libre, el ahorro,
   los apartados) no cambian y se prueban también con este horizonte.
2. **La línea sin salario** (`lineaSinSalario`): encima de la cifra de la caja, «Sin tu salario, Movi supone
   que no te entra nada» con «Agregar» (`Boton` `Fantasma` S), que abre la hoja de ingreso recurrente ya en
   Ingreso y con el día del corte (`EstadoDelTableroDeRecurrentes.agregarSalario(dia)`,
   `CreateRecurringRuleSheet(diaNuevo)`). Con un ingreso fijo en otra moneda o sin monto no sale: ya hay uno,
   y «¿Qué supone?» dice por qué no cuenta.
3. **«¿Me alcanza?» en el héroe de Inicio** (`RenglonMeAlcanzaDelHeroe`, `ui/dashboard/CajaEnHoy.kt`). Un
   renglón tocable dentro de la tarjeta del héroe, debajo de la cifra y encima de «Por confirmar»: **siempre
   se ve**, también con «Disponible» oculto, y un APK viejo no lo pinta (va dentro de `HERO_BALANCE`, la
   definición SDUI no cambió). «Te alcanza hasta el 25 nov · tu día más justo, el 5 nov» si lo libre del peor
   día es ≥ 0; «No te alcanza: el 5 nov quedarías en −$1,3M» si no (`renglonMeAlcanza`, en rojo). Se pinta
   como `Fila` de dos renglones —«Te alcanza hasta el 25 nov» y debajo «Tu día más justo, el 5 nov»—: en
   uno solo, a 360 dp con la letra al 130 %, se cortaba con «…» (`ElTextoAl130NoSeCortaTest`). Sale de
   `cajaDelPeriodoDe` → `cajaHastaQueTePaguen`, la MISMA de Pagos (`renglonMeAlcanzaDe`); sin período, lista,
   cuentas o el supuesto del gasto del día a día, no se pinta. Tocarlo abre `Screen.Plan(SEGMENTO_PAGOS,
   enLaCaja = true)`: en una columna, Pagos baja hasta la caja (`LlevarALaCaja`, una vez). «Tu día más justo»
   salió de la sección «Disponible»: decirlo dos veces sería repetirlo.
4. **La caja de Pagos: una cifra y una frase** (`ui/plan/CajaDelPeriodo.kt`). La cifra titular es lo libre
   del día más justo (`Cifra` `Grande`, `FormaDeCifra.Abreviada`, `TonoDeCifra.Alerta` solo si es negativa);
   debajo, sin cifras, «Hasta el 25 nov · tu día más justo es el 5 nov» (o «… · el 5 nov no te alcanza»,
   `fraseCortaDeLaCaja`). Lo demás va a «¿Qué supone?» (`loQueSupone`): lo que habrá en las cuentas y el ahorro
   (`lineaDeLasCuentas`), la diferencia con «Te quedan» y su causa medida (`clausulaDeLaDiferencia`, ya como
   frase), el primer día en rojo (`fraseDelRojo`), el supuesto del gasto y de dónde sale la línea. **Una sola
   forma de cifra**: la abreviada en todas las escalas (`formatMoneyAbreviada`: «$683 mil», «$1,3M») en la
   cifra, las frases, la leyenda y la gráfica. Era un párrafo rojo de cinco renglones con «−$683.361» junto a
   «−$1,3M».
5. **Un solo nombre del período** (`nombreDelPeriodo` en `:core` `PeriodoFinanciero.kt`, `nombreEntre`):
   **«23 oct – 24 nov»** por sus fechas cortas, o **«Octubre»** solo cuando el período ES el mes de calendario
   (arranca el 1 y cierra el último día del mismo mes; un inicio a mano lo nombra por sus fechas); con el año
   («Octubre 2025», «25 oct – 24 nov 2025») solo si no es el de hoy. Lo dicen el encabezado de Inicio (y la
   línea bajo «Tu plata»), Actividad (ya sin «Noviembre de 2026» ni el rango largo debajo: mientras el perfil
   no contesta, un esqueleto del mismo alto), Pagos (la línea del período, otro período con ‹ › y «Período en
   curso · …»), Tus períodos (`nombreDelResumen`, desde `desde`/`hasta` del server: un APK nuevo lo dice igual
   con un server viejo) con su detalle, y En qué se va. Buscar no nombra períodos. Las frases («Antes de agosto
   2026 no hay movimientos», «hasta noviembre de 2026» de Créditos) siguen con el mes: no son el nombre de un
   período en pantalla.
6. **El Primer uso** (`ui/primeruso/`). «Paso 2 de 6» es un rótulo chico (`apoyoMedio`, `TAG_ROTULO_DEL_PASO`)
   y el título grande es el del paso (`titular`, `heading`). En «Tu período», con un día elegido y sin un
   ingreso fijo anotado, **«¿Cuánto te pagan? (opcional)»** (`MoneyField`; con cuentas, «¿A qué cuenta te
   llega?» en `PildoraDeFiltro`; sin ellas, «Llega el día 25 a la primera cuenta que agregues…»). **El orden
   de los pasos no cambió** —la lista única de `primerosPasos` la comparten la fila del Inicio y Tú—: el monto
   se pregunta donde se elige el día, y **al terminar** (al llegar a «Listo», o al salir con «Ahora no») la
   guía crea la regla con `createRecurringRule` de siempre (`reglaDelSalario`: «Salario», categoría
   «Salario», INCOME, **el día del corte** —el que `fechaEsperadaDelIngreso` reconoce como el salario—, la
   cuenta elegida o la primera de dinero), una sola vez y sin escrituras propias. El día de un pago de cada
   mes se elige con `SelectorDeDiaDelMes` (vuelta 72); su número ahora se mide a lo ancho del círculo, como
   el día del período de la guía, para no recortarse con la letra grande (`NadaSeRecortaEnLaGuiaTest`). «Listo» abre con una cifra real: «Te quedan
   $4.963.400 hasta el 24 nov» (`fraseDelFinalDeLaGuia`: `teQuedanDeHoy` sobre `disponibleDelInicio` con
   `leerDatosDelDiaADia`, las lecturas de siempre), o «Ya puedes empezar» sin cuentas o sin cifra.
7. **El héroe cambia entero** (`rememberEstadoQueCambia`, `EstadoDelHeroe` en `ui/dashboard/EntradaDelInicio.kt`).
   La cifra grande, su línea («para N días · $X por día») y «Tu plata» salen de UN estado; al llegar uno
   nuevo, el viejo se desvanece entero y el nuevo aparece entero (`Movimiento.LARGO_MS`), sin un cuadro con
   piezas de los dos. Antes solo la cifra contaba 600 ms (`rememberCifraQueCambia`, que se fue) mientras la
   línea y «Tu plata» ya eran las nuevas: «$1.417.943» junto a «$441.916 por día». El primer dato después del
   esqueleto entra sin esperar; con menos movimiento, en el acto.

Lo vigilan `CajaHastaQueTePaguenTest` (`:core`: el horizonte al período siguiente con el corte del dueño, en
fin de semana y con un período excepcional, sin cambios con salario o sin período elegido, y las invariantes),
`NombreDelPeriodoTest` (`:core`: corte 1, corte 25 con fin de semana y festivo, excepcional, el año),
`CajaProyectadaRoutesTest` (server), `RenglonMeAlcanzaTest` y `CajaDelPeriodoTest` (commonTest), y en
Robolectric `CajaEnHoyTest` (el renglón en el Inicio por defecto, con «Disponible» oculto, y a Pagos en la
caja), `PagosHastaQueTePaguenTest` (una cifra, la frase sin cifras, la línea sin salario),
`PrimerUsoEnPantallaTest` (el salario y su regla, el rótulo, el selector, «Listo» con cifra o «Ya puedes
empezar») y `ElHeroeNoMezclaTest` (cuadro por cuadro, nunca lo viejo con lo nuevo). Se actualizaron las
pruebas del nombre del período en Inicio, Actividad, Tus períodos y `LasCifrasDelSistemaSeOcultanTest`.
**Las líneas base** no cambian, y las guardas en 0 siguen en 0.

### La revisión de la vuelta 75, y lo que cambió

1. **El salario se crea una vez** (`asegurarElSalario`): llegar a «Listo» y «Ir a Inicio» comparten UNA
   creación (un `Job`); «Ir a Inicio» la espera y está apagado (con su indicador) mientras corre. Antes,
   tocarlo a los 30 ms creaba dos reglas «Salario» (el POST no es un upsert: el id lo pone el server).
2. **«¿Ya te pagaron este período?»** (sí por defecto, debajo del monto). Con sí, la regla nace con
   `activeFrom` en el arranque del período siguiente (`arranqueDelPeriodoSiguiente`), con
   `arranqueEsDesembolso = false`, el de siempre de una regla escrita a mano: el server toma como piso el
   primer día del período de esa fecha (`arranqueDeLaRegla`), así que la ocurrencia de este período **no
   existe** —ni «Debió llegar hace 7 días» en `/upcoming`, ni por cobrar en la lista del período, que es
   de donde salen «Te quedan», «Listo» y la caja— y la del siguiente sí. No inventa historia: los períodos
   anteriores al arranque no existen, que es lo único que `activeFrom` garantizó siempre. Con no, sin
   arranque (cuenta desde este período). Antes «Listo» decía «Te quedan $7.300.000» con $2M en las cuentas.
3. **El héroe se congela mientras carga** (`rememberEstadoQueCambia(congelado = LocalCargandoElInicio)`): el
   estado intermedio de una carga junta las cuentas nuevas con el resumen viejo, y el héroe ya no lo
   muestra; al terminar cambia entero. El renglón «¿Me alcanza?» va dentro del MISMO estado
   (`EstadoDelHeroe.renglon`, `esUnCambioDelHeroe`) y cambia con la cifra, no antes; si solo aparece el
   renglón, entra sin hacer parpadear la cifra.
4. **Sin una cuenta de dinero, el salario no se crea** («Ahora no» antes de agregar cuentas): la nota lo
   dice («Sin una cuenta no se guarda»).
5. **El menos pegado a la cifra** en el renglón y en las frases de la caja: «quedarías en −$1,2M» va con un
   espacio que no se parte antes del menos y `UNION_QUE_NO_SE_PARTE` entre el menos y el «$».
6. **«Lo que pagas cada mes» cuenta solo gastos** (en la guía y en «Primeros pasos» del Inicio): el salario
   no lo tilda.
7. **‹ antes del primer período**: el server contesta 404 para un período anterior al primer movimiento;
   Pagos ya no lo dice como error: «Movi todavía no llevaba tus cuentas en este período» y ‹ se apaga ahí.
8. **Lo barato**: «Hasta el 29 nov · ese día no te alcanza»; sin salario el renglón dice «Te alcanza hasta
   el fin del período» (o «los próximos 30 días»), no una fecha en la que no entra nada; «Agregar» de la caja
   trae el nombre «Salario»; el detalle del período ya no repite el rango largo debajo del título.

Lo vigilan `CajaProyectadaRoutesTest` (server: el salario con arranque en el siguiente no está vencido, no
está en la lista del período, y cierra la caja en el siguiente), `PrimerUsoEnPantallaTest` (el doble toque, el
arranque con y sin «ya te pagaron», sin cuenta no se crea), `ElHeroeNoMezclaTest` (las cuentas antes que el
resumen) y `RenglonMeAlcanzaTest`/`CajaDelPeriodoTest`.

## 74. Vuelta 76: palabras que dicen una sola cosa

De la revisión fresca 5 del 30-oct (`r5-reg-personas.png`, `r5-reg-caro.png`, `r5-1280-inicio-b.png`,
`r5-1280-actividad.png`, `r5-inicio-390-s2.png`, `r5-reg-traspaso.png`, `r5-tu-390-b.png`), puntos 6, 7, 9, 10
y 14. **Sin reglas de plata nuevas**: ninguna cifra cambió; solo qué se dice, dónde y con qué palabra. Ningún
componente nuevo en `ds/` ni token nuevo.

1. **La fila del tercero dice lo de su ficha.** En «Personas y comercios», un tercero miembro de un grupo lleva
   de subtítulo (hasta 2 renglones) «Te debe $168.050 · Casa con Caro», de las MISMAS líneas de la ficha
   (`elTerceroEnLosGrupos` → `lineaDelTerceroEnLaLista`; con varios grupos, primero el que tiene saldo y «· y N
   grupos más»). La lista lee los grupos una vez (`losTercerosEnLosGrupos`); si falla, cada fila dice lo de
   siempre. Sin transferencias y con un grupo, a la derecha no va «Nada este período»: el subtítulo ya dice lo
   que se deben. Con «Ocultar los saldos», la cifra se tapa (`conCifrasOcultas`).
2. **«Falta por pagar» con todo pagado no se pinta.** Era una tarjeta de ~300 px que solo decía «Ya salió todo
   lo del período». `visibleSections` lo saca con `pagosPendientes(…)` vacío (junto a la regla de la vuelta 73);
   «En qué se va» queda solo en su bloque, a todo el ancho.
3. **«Nuevo» con sentido** (`diferenciaDeLaCategoria(categoria, fijas)`, en el widget «En qué se va» y en el
   panel de Actividad):
   - solo con el período pasado cubierto: sin él la guarda de siempre deja `anterior = null` y no se dice nada;
   - **nunca en lo fijo** (`categoriasFijas`): las categorías de las reglas recurrentes de gasto (incluidas las
     cuotas y tarjetas sintéticas), `CUOTA_CATEGORY` y, en Actividad, las de los gastos del período que el ícono
     de repetición reconoce (`nombreRecurrenteDe`, lo más cerca de «lo que salda un pago del período» que la
     pantalla tiene sin pedir nada). Ahí no va ninguna palabra; un fijo que sube sigue diciendo «$X más»;
   - **un solo tono**, `TONO_DE_LO_NUEVO` = `TonoDeCifra.Neutro` (el color del texto, como lo que sube sin
     alarma). Antes salía en `sale` o en el texto según el monto, y la misma palabra parecía decir dos cosas.
     El rojo queda para «$X más» cuando `subeMucho` (1,5× y $300.000, sin cambios).
4. **Una palabra por cosa.**
   - **«Avisos»** son los mensajes del banco: la acción «Avisos» del Inicio, Por revisar, «5 avisos del banco».
   - **«Notificaciones»** son lo que suena y sus interruptores: la sección de Tú (`TITULO_DE_NOTIFICACIONES`), sus
     filas «En este navegador» (el push de la web) y «En este teléfono» (Android), el botón «Activar» (era
     «Activar avisos»), la fila de Captura del banco que lleva ahí, «Notificaciones en tu iPhone», la tarjeta del
     Inicio («Activar notificaciones»), la fila de recordatorios bloqueados de Pagos («toca para ver tus
     notificaciones») y Buscar («notificaciones» encuentra Tú). Los interruptores siguen siendo «Avisarme…»: el
     verbo no choca. La campana ya se llamaba «Notificaciones».
   - **Lo que se sale de lo normal es «para revisar»**: la tarjeta de «Movi propone hoy» dice «· y 3 cosas más
     para revisar» (`cuerpoDeLaAnomalia`), y el número del encabezado **cuenta lo mismo que las tarjetas**: cada
     una lleva `cuantas` (la suya más su «y N más»: pagos, débitos, cuadres, anomalías) y la sección pinta
     `cuantasCosasProponen`. Antes «Movi propone hoy · 1» encima de «y 3 avisos más».
5. **Pulido.**
   - **Traspaso y Pagar con la barra de abajo.** El botón («Guardar traspaso», «Registrar pago», «Registrar
     abono») va debajo del desplazamiento, como el de Gasto (vuelta 71): `TransferBody` publica su estado en
     `EstadoDeLaBarraDelTraspaso` y `QuickAddScreen` pinta `BarraDeGuardarDelTraspaso` (con un sub-editor
     abierto o la confirmación del desglose, no hay barra). Sale «Se guarda en línea: las dos puntas, juntas.»:
     el renglón reservado de 16 dp solo dice lo que falta.
   - **La fila de las alertas por correo nunca está muerta.** En Tú (web e iOS) se pinta solo si el server
     contestó una dirección (`seVeLaFilaDeCorreo`, `GET /api/correo-entrante/direccion` con `configurado`);
     abre la hoja con la dirección, «Copiar» y el paso a paso de Gmail (la pieza de la guía de primer uso).
     Mientras lee o sin reenvío configurado, no hay fila.
   - **Elegir a la persona de un grupo, corto.** «Nuevo grupo» dice «Elige a la persona», la lista y «Nueva
     persona» (era «Agregar persona · Con su número o su llave, para reconocer sus transferencias», cortado a
     360). El número o la llave los pide la hoja de la persona, como siempre: el server exige un identificador
     para guardar un tercero, y volverlo opcional no entraba sin una regla nueva.

Lo vigilan `Vuelta76Test` (commonTest: la línea del tercero con `saldoDelEspacio`, el widget fuera de
`visibleSections` con todo pagado, «nuevo» cubierto / no cubierto / fijo / cuota / un solo tono y el rojo de
siempre, «cosas para revisar» con `cuantasCosasProponen`, `seVeLaFilaDeCorreo`) y `Vuelta76EnPantallaTest`
(Robolectric, `NATIVE`, 360 dp: la fila de Caro sin «Nada este período» ni cortes, el Inicio sin el widget y
«En qué se va» a lo ancho, «nuevo» en un solo color y la cuota sin palabra, «Notificaciones» en Tú, la fila del
correo con y sin dirección, «Movi propone hoy · 4», Traspaso y Pagar con la barra abajo, «Elige a la persona»
sin cortes con y sin lista). Se actualizaron `MasScreenTest`, `LosAvisosApagadosSeVenTest`,
`AvisosEnElIphoneTest`, `TarjetaDeAvisosTest`, `RepartirDesdeElMasTest` e `InicioDelRedisenoTest`. **Las líneas
base** no cambian, y las guardas en 0 siguen en 0.

## 75. Vuelta 77: una deuda, una cifra

De la revisión fresca 6 del 30-oct (`r6-patrimonio-390-a.png`, `r6-1280-patrimonio.png`, `r6-inicio-tras-credito.png`,
`r6-pagos-390-a.png`, `r6-credito-ficha-390-a.png`, `r6-abono-390-d.png`, `r6-salida-390-d.png`/`-f.png`,
`r6-apartado-patrimonio.png`, `r6-ai-390-b.png`, `r6-ai-390-d1.png`, `r6-1280-creditos.png`), puntos 1, 2, 3, 9, 10,
11, 12, 14 y la parte de Créditos del 15. **Una sola regla de plata nueva** (la del punto 1). Ningún componente nuevo
en `ds/` ni token nuevo.

1. **Un préstamo sin saldo cuenta cero en el patrimonio** (la regla nueva, decisión de esta vuelta). `patrimonioDe`
   suma cada deuda con su signo **salvo un `LOAN` en negativo** (`prestamoSinSaldo`): ese cuenta 0 mientras no tenga
   saldo. Toda cuenta que `creditoSinSaldo` marca y pesa algo cae ahí (la otra mitad, en cero sin movimientos, ya
   sumaba cero). Una **tarjeta** a favor sigue con su signo (es plata real); un préstamo **con** saldo suma igual que
   siempre. Así «Deudas» de Patrimonio dice lo mismo que «Créditos y tarjetas» ($1.583.000, no $603.000), el neto ya
   no sube $980.000 por el Vehículo 4083, y la curva tampoco: la historia (`/api/patrimonio/historia`) usa la misma
   función punto por punto, y el neto «sin ajustes» de cada punto deja en cero **los mismos** préstamos que el neto con
   todo (`patrimonioDe(…, prestamosEnCero)`): si decidiera por su cuenta, un préstamo sano cargado con un ajuste
   (que sin él queda en negativo por sus cuotas) movería la cifra de los ajustes. **Datos sanos, cifras de siempre**:
   lo prueba `ElPrestamoSinSaldoCuentaCeroTest` contra la cuenta de antes escrita a mano, y la historia con un
   préstamo sano, `HistoriaDelPatrimonioTest`. Esto reemplaza la decisión de las vueltas 59 y 73 («`patrimonioDe` no
   cambia») solo para los préstamos.
2. **Un crédito creado hoy no nace con la cuota vencida.** El alta de un crédito que ya venía (con la deuda de hoy, sin
   desembolso) pregunta **«¿Ya pagaste la cuota de este período?»** —una `Fila` con `Casilla`, sí por defecto, «Movi
   la espera desde el próximo período» / «en este período»— solo si la cuota de este período (con el período del
   dueño) ya venció (`laCuotaDeEstePeriodoYaVencio`). Con «Sí», `CreateCreditRequest.yaPagoLaCuotaDeEstePeriodo` y el
   server guarda `CreditTerms.cuotasDesde` = el arranque del período siguiente (el día después del último de este, `diasDelPeriodo`; en `:core`, `arranqueDeLasCuotasSiYaPagaste`;
   columna `credit_terms.cuotas_desde`, sin `@EncodeDefault`, el `PUT` sin la clave la conserva). `virtualRuleFor`
   la usa (`cuotasDesdeVigente`: solo si es posterior al desembolso) como `activeFrom` **con el significado de período** (`arranqueEsDesembolso = false`, el piso es su primer
   día), no el del desembolso: la cuota de este período no existe en `/occurrences`, `/upcoming`, la lista de Pagos
   (Inicio: «Pago vencido», «Te quedan»), Créditos (`mesDeLaPrimeraCuota` respeta el piso) ni Movi AI.
3. **El saldo según el plan.** Un crédito sin saldo con sus condiciones completas (`saldoDelPlanEnLaFicha`: capital,
   tasa o «no cobra intereses», plazo, cuota, día e inicio; la cuenta es `saldoSegunElPlan`) dice en la ficha, debajo
   de «Cargarlo», y en Patrimonio, debajo de «Falta el saldo de Vehículo 4083 · Cargarlo», **«Según tu plan debes unos
   $34,5M y terminas en diciembre de 2030 · Usar este»** (`TAG_SALDO_SEGUN_EL_PLAN`). «Usar este» escribe el ajuste de
   saldo de siempre (`adjustCreditBalance`, fechado hoy); **nunca se escribe solo**. **Una sola acción de saldo**:
   sin saldo, la ficha ofrece «Cargarlo» arriba y «Qué puedes hacer» ya no trae «Ajustar saldo»
   (`laFichaOfreceCargarElSaldo`, también para una tarjeta en negativo); con saldo, «Ajustar saldo».
4. **«Pagar la cuota» en la ficha del préstamo** (`laCuotaEnLaFicha`): la acción principal, un `Boton` a lo ancho
   arriba de «El préstamo» —«Pagar la cuota de noviembre · $770.000»—, con la cuota que corresponde: la del período en
   curso, o la primera del crédito si arranca después, o una anterior vencida sin pagar. Si la que corresponde ya está
   saldada (`cuotasPagadas`), una `Fila` con `TaskAlt` en `Exito`: «Cuota de noviembre · pagada» (sin «✓»: Figtree no
   lo tiene). Sin `cuotasPagadas` (un server viejo) no afirma que esté pagada; la nómina y «la paga otro» siguen con su
   acción. Abre Agregar → Pagar con el crédito, la cuota y su mes (`pantallaDePagarLaCuota`): el pago de dos patas de
   siempre.
5. **«Anotar este abono» abre Pagar en modo abono** (`Screen.QuickAdd.presetAbonoExtra`, `TransferBody(presetAbonoExtra)`):
   «Abono extra a …», «Registrar abono»; no la pestaña Traspaso con «Guardar traspaso». Vale para ese crédito: elegir
   otra deuda vuelve a la cuota. Lo usan el simulador de la ficha, el plan de salida y la fila del abono mensual de
   Pagos (`hojaDelAbono`, a un crédito). Lo que se guarda es el traspaso de siempre.
6. **«Cómo salir de tus deudas».** Debajo del selector, cuánto cuesta elegir un orden (`compararEstrategias`, la misma
   simulación de `planDeSalida` con el mismo abono): «Avalancha te ahorra $X más que bola de nieve», al revés, o «Con
   tus deudas, los dos órdenes dan igual»; sin abono, nada. Avalancha dice «Primero la de mayor tasa: así pagas menos
   interés en total» (se leía al revés). Los supuestos van detrás de **«¿Qué supone?»** (`Boton` `Fantasma`), en el
   plan de salida y en el simulador de la ficha: el resultado primero. El pie («no es una recomendación») queda.
7. **La curva con un solo verbo.** La leyenda abre con el cambio **sin** ajustes —«Desde ago 2026, subió $837.900»—
   y la nota no lleva verbo: «Sin contar −$18,5M de ajustes de saldo: deudas y saldos que ya tenías y anotaste
   después». Antes: «bajó $17,7M» arriba y «Sin ellos, subió $837.900» debajo.
8. **Movi AI.** En una conversación nueva, las tres preguntas que se contestan sin IA (gratis) van **siempre primero**
   y después hasta dos de las que arman las reglas (`preguntasDelArranque`); sin IA, solo las tres. «¿En qué se me
   está yendo más la plata?» usa `categoriasFijas` (ahora en `:core`, la misma del widget de la vuelta 76): una cuota
   o un fijo sin gasto a esta altura del período pasado no se dice «$X más que el período pasado». Como en el widget,
   un fijo que de verdad subió sigue diciéndolo.
9. **Créditos a 1.280.** Los renglones de «Deuda total» y «1 crédito sin saldo · Cuadrar» van a lo ancho de la
   tarjeta: las cifras y «Cuadrar» terminan en el borde derecho, en la columna de la acción de una fila. Se fue
   `ANCHO_MAXIMO_DEL_RESUMEN_DE_DEUDA` (vuelta 61): topados a una columna, las cifras terminaban a mitad de la tarjeta.

Lo vigilan `ElPrestamoSinSaldoCuentaCeroTest` y `Vuelta77CoreTest` (`:core`: la regla con datos sanos y una tarjeta a
favor, la cuota de este período, `cuotasDesde`, avalancha contra bola de nieve, lo fijo), `HistoriaDelPatrimonioTest`,
`ElCreditoNuevoNoNaceVencidoTest` (el alta con «ya pagué» en `/occurrences`, `/upcoming`, la lista y Movi AI) y
`EnQueSeVaNoCuentaLoFijoComoMasTest` (`:server`), `Vuelta77Test` (commonTest) y `Vuelta77EnPantallaTest` (Robolectric:
«Usar este», una sola acción de saldo, «Pagar la cuota» y «pagada», la comparación, la curva con un verbo, las tres
gratis primero). Se actualizaron `LosCreditosDicenLaVerdadTest`, `LaCurvaSeparaLosAjustesTest`,
`CurvaDelPatrimonioEnPantallaTest`, `ElHeroeDeCreditosVaJuntoTest` (Créditos a 1.280), `ElExtractoYElAbonoEnPantallaTest`
y `ElAbonoQueSeTildaEnPantallaTest` (el abono en Pagar), `LaGuiaDelAbonoCabeTest` y `SimuladorDeAbonoEnPantallaTest`
(«¿Qué supone?»). **Las líneas base** no cambian, y las guardas en 0 siguen en 0.

### La revisión de la vuelta 77, y lo que cambió

1. **[Plata] Un pago antes de la primera cuota salda la primera cuota.** Con «ya pagué» (`cuotasDesde = 2026-11-01`)
   y el pago del 30-oct desde «Pagar la cuota de noviembre», `periodoQueSalda` sellaba «2026-10» (una cuota que no
   existe) y noviembre seguía pendiente. Pasaba igual con un crédito desembolsado pagado antes de su primera cuota.
   Ahora nunca devuelve un período anterior a la primera cuota vigente (`primeraCuotaVigente`: la primera en el piso
   de `arranqueDeLaRegla` o después); el encabezado de Agregar → Pagar usa la misma regla
   (`CreditTerms.mesDeLaPrimeraCuotaDelCredito`). `ElPagoAdelantadoSaldaLaPrimeraCuotaTest`: el pago del 30-oct da
   `2026-11`, `/upcoming` el 15-dic, y la ficha «Cuota de noviembre · pagada».
2. **[Plata] El server solo acepta «ya pagué» si la cuota de este período ya venció**
   (`laCuotaDeEstePeriodoYaVencioEl`, con los ajustes del dueño; el piso, `arranqueDeLasCuotasSiYaPagasteEl`). La hoja no
   muestra la pregunta hasta leer el perfil.
3. **Una sola fila de saldo.** Con el plan completo, «Según tu plan debes unos $X y terminas en … · Usar este»; «Usar
   este» abre la hoja de ajustar el saldo **ya llena** con esa cifra («Es la cifra de tu plan. Si el banco dice otra,
   escríbela aquí.») y se guarda con «Registrar ajuste». Sin plan, «Cargarlo». En la ficha y en Patrimonio.
4. En Agregar → «¿Qué pagas?», un préstamo sin saldo dice «Sin saldo en Movi», sin cifra (era «A favor $980.000»).
5. La nota de la curva lleva el signo: «Sin contar −$36,8M de ajustes de saldo…».
6. Al guardar un abono, «Anotado · Abono a …» (`queSeAnoto`), no «Pago a …».

## 76. Vuelta 78: rápida y coherente

Viene de la revisión fresca 6 (puntos 5, 6, 7, 8, 13 y 15, sin Créditos). **Sin reglas de plata nuevas**: todo
lo que se dice sale de funciones que ya existían.

1. **Respuestas livianas** (server). El JSON sale sin sangría y con gzip (`plugins/Compresion.kt`, desde 1 KB, solo
   respuestas, solo JSON y texto; el `.wasm`, las fuentes y los documentos van tal cual), con `Vary: Accept-Encoding`. Los tres clientes piden y
   abren gzip solos. Con la demo local, `/api/events/by-day` pasó de 62,6 KB a 5,9 KB y `/api/dashboard/summary` de
   5,5 KB a 0,9 KB. Ver la sección del server en `CLAUDE.md`.
2. **Peticiones sin repetir** (cliente). `LecturasSinRepetir` une los pedidos iguales en vuelo y devuelve lo que
   contestó hace menos de 30 s; una escritura propia lo vuelve viejo. Al abrir el Inicio `users/me` y `accounts`
   salen una vez (antes dos: el Inicio y la guía de primer uso a la vez), Pagos pide `accounts` una vez, y Actividad
   no vuelve a bajar la historia si la tiene de hace menos de 30 s. La bandeja «Por revisar» siempre pregunta.
   **Lo que entra por fuera de la app** (revisión): `olvidarLoFresco()` (`LoFresco`) da por viejo lo leído —sin
   borrar lo que se ve— y lo llaman tirar para actualizar, «Reintentar», el ↻ de Por revisar, la vuelta del segundo
   plano (el Inicio a la vista vuelve a preguntar) y, en el teléfono, `SmsSyncWorker` tras subir,
   `confirmarDesdeLaNotificacion` tras anotar y el `SyncEngine` cuando sube algo.
3. **El héroe con un solo horizonte.** «Para N días · $X por día» (`detalleDelHeroe`) **se calla** con
   `DIAS_PARA_CALLAR_EL_POR_DIA` = 3 días o menos para el cierre, o si «¿Me alcanza?» dice que no
   (`RenglonMeAlcanza.noAlcanza`). Los avisos de la línea (te pasaste, sin margen, vas por encima de lo previsto)
   se dicen siempre. Sin salario, si el primer día en rojo de la caja cae **después** del cierre, el renglón ya no
   dice «No te alcanza: el 29 nov quedarías en −$1,1M» debajo de un «Te quedan» positivo: dice «El 5 nov, sin
   salario anotado, no te alcanzaría · Agregar salario» (`noTeAlcanzariaSinSalario`, la primera fecha en rojo; sin
   rojo; tocarlo abre la caja de Pagos, con su «Agregar» arriba). La caja de Pagos dice la misma frase debajo de su
   cifra, y la tarjeta «Cuánto puedes gastar» de Pagos calla su «Para N días» con la misma regla
   (`detalleDelHeroe`). Con salario, o con un rojo dentro del período, lo de siempre. El supuesto del gasto del día a
   día va en el primer lote del Inicio y en su instantánea (`DashboardData.gastoDelDiaADia`): el renglón llega con
   el resto del héroe y no 66 px después.
4. **Pagos: una sola invitación al salario.** La de la caja («Sin tu salario, Movi supone… · Agregar»). La tarjeta
   de «Lo que se repite» sin ingresos muestra solo «Gastos recurrentes» (`TAG_SOLO_LOS_GASTOS_RECURRENTES`), sin
   «Agrega tu salario… · Agregar mi salario». Y **las dos cifras se nombran**: debajo de los gastos,
   `lineaDeLasDosCifras` dice «$3.625.338 al mes · $3.662.500 este período, con lo que de verdad pagaste
   y el mínimo de tus tarjetas» —la primera es `ResumenRecurrentes.gastos` (un mes cualquiera, lo anual en doce,
   sin el pago de las tarjetas); la segunda, el «de $X» de la tarjeta amarilla (`ResumenDeFacturas.total`)—. Si
   coinciden, o no hay lista del período, no se dice nada.
5. **Buscar cuenta lo mismo que En qué se va.** Tu parte de lo que pagó otra persona en un grupo (`getTuParteDeOtros`)
   entra a Buscar con `eventosParaBuscar` (la misma `conTuParteDeOtros` de Actividad): «Mercado del sábado»,
   «Mercado · 25 de octubre», la cifra es tu parte y debajo «de $240.000 · pagó Caro» (`debajoDeTuParteEnBuscar`,
   con `FinancialEvent.montoDelGrupo`, que solo se muestra: ningún total lo lee); tocarla abre el gasto del grupo. La suma de las filas de «mercado» en el período es la de la categoría en En qué se va.
6. **El esqueleto reserva el alto real.** Mientras carga el Inicio, la tarjeta del héroe reserva con
   `EsqueletoDeFila` los renglones de «¿Me alcanza?» y «Por confirmar» que llevaba la última vez
   (`FormaRecordada.heroe`, `FormaDelHeroe`; la primera vez, los dos). En frío sin instantánea ya no baja ~110 px.
7. **Pulido.**
   - **El widget «Falta por pagar» y «Próximo pago» ordenan con la misma cola** (`pagosEnElOrdenDeLaCola`: los
     pagos de `colaDePendientes` en su orden y después lo pendiente que la cola no trae). «Próximo pago» es el
     primero; el widget sigue sin repetirlo (vuelta 68, `sinElProximoPago`) y empieza por el segundo.
   - **«Lo que Movi sabe de ti»: una sola acción para agregar.** Con la memoria vacía va «Escribir un recuerdo» del
     vacío y el encabezado no lleva «Agregar»; con recuerdos, «Agregar» arriba.

Lo vigilan `CompresionTest` (server), `LecturasSinRepetirTest` y `ElInicioNoRepitePeticionesTest` (el contador de
peticiones sobre un `MockEngine`), `RenglonMeAlcanzaTest` y `ElHeroeConUnSoloHorizonteTest`,
`ElFlujoLibrePideElSalarioTest` y `PagosHastaQueTePaguenTest` (una sola invitación), `BuscarCuentaComoEnQueSeVaTest`
y `BuscarMuestraTuParteDelGrupoTest`, `ElHeroeReservaSuAltoTest` (≤ 8 dp), `ElWidgetEmpiezaComoElAvisoTest` (el widget empieza por el segundo de la cola) y
`LaMemoriaSeEscribeAManoTest`. **Las líneas base** no cambian, y las guardas en 0 siguen en 0.

## 77. Vuelta 79: que tocar se sienta bien

Viene de la revisión visual 7 del 30-oct (puntos 1, 2, 3, 4, 6, 12 y 13; capturas `r7-*.png`): lo que dejaba a Movi
por debajo de Revolut, Monzo o Copilot era que **se sentía barata al tocarla** y que **el amarillo estaba en todas
partes**. **Sin reglas de plata.**

1. **Un solo presionado** (`ds/AlPresionar.kt`). **Regla general:** todo lo que se toca en `ds/`, la barra de pestañas
   y el rail pasa por `Modifier.alPresionar` (o `alAlternar`, o el par `hundimientoAlPresionar` + `toqueSinRipple`):
   - **sin ripple** (`indication = null`): nada pinta un rectángulo encima;
   - **se hunde**: escala `Movimiento.ESCALA_AL_PRESIONAR` (0,96) en `PRESIONAR_MS` (100 ms) con la curva estándar; el
     `Boton` 0,97 y la `Fila` de lista 0,985;
   - **el velo** (`ColoresDeMovi.velo`, la tinta al 6 %), recortado a la forma del componente, en hover (la web) y,
     con el sistema pidiendo menos movimiento, al presionar (sin escala). **Nunca sobre el amarillo**: el `Boton`
     principal se queda en `marca` al presionar (con menos movimiento pasa a `marcaPresionada`, `#F5B800`, un
     amarillo un paso más hondo) y la `AccionRapida` pone su círculo en la `capa3` (la destacada sigue en la marca).
   - **Barra y rail**: la celda entera se toca, pero la escala y el velo van **solo en la píldora** del destino
     (`TAG_PILDORA_DE_PESTANA`); la activa no lleva velo. El destino del rail ancho, recortado a su píldora.
   - Va **después del `anilloDeFoco` y antes del `clip`/`background`** (si no, la escala solo achica lo de adentro).
     La `Superficie` tocable se hunde con su sombra (`hundimientoAlPresionar` antes de la sombra).
   - `ElToqueEsDelSistemaTest` (en 0): en `ds/`, `MinBottomNav.kt` y `MinNavRail.kt` no hay `clickable`,
     `combinedClickable`, `toggleable` ni `selectable` sueltos, ni `ripple(`, `rememberRipple` o `LocalIndication`;
     `TodoLoEnfocableDibujaElAnilloTest` cuenta `alPresionar` como un toque (lleva anillo).
2. **Cambio de pestaña con fade-through** (`transicionEntrePantallas`, la rama del fundido): sale lo viejo
   (`fadeOut`, `SALIDA_DEL_FUNDIDO_MS` = 90 ms) y **después** entra lo nuevo (`fadeIn` de `MEDIO_MS` con `delay` 90 y
   `scaleIn` desde 0,98). Nunca las dos a la vez (`DobleExposicionTest` muestrea cada ms). Apilar y volver no cambian;
   con menos movimiento, corte en seco.
3. **Un solo amarillo por vista.** **Regla nueva del sistema: un solo `Boton` principal (amarillo) —o una acción
   destacada— visible por vista.** Lo dicen en su semántica (`llevaElAmarillo`) y `UnSoloAmarilloPorVistaTest` lo
   cuenta en Inicio (390 y 1280) y en Pagos.
   - **Inicio**: «Gasto» (anotar un gasto) es la acción amarilla. «Anotar pago» (Próximo pago) y la acción de «Movi propone hoy»
     pasan a `Secundario` (`TarjetaDePropuesta(varianteDeLaAccion = …)`, que por defecto sigue `Principal`); el círculo
     de la propuesta va en el tinte si urge (`TonoDeIcono.Aviso`) o en la capa 3 (`Neutro`). Sin cuentas, la acción
     amarilla es «Crear mi primera cuenta» y «Gasto» no se destaca.
   - **A 1280**: el destino activo del rail sigue en el `tinte` con la tinta. **«Agregar» del rail pasa a secundario**
     (tarjeta, `bordeDeBoton`, tinta; también el «+» del rail compacto): el rail está en todas las vistas, y el amarillo
     de cada vista es su propia acción («Gasto» en Inicio, el pago más urgente en Pagos), no el del chrome.
   - **Pagos**: «Falta por pagar» es una `Superficie` blanca destacada con la `BarraDeProgreso` en tinta sobre la capa
     3 (`FondoDeBarra.Destacada`). El amarillo deja de ser superficie (`ElAmarilloEsDeLaAccionTest`).
   - **Avatares de banco** (`TarjetaDeCuenta`): la letra del banco en la tinta sobre la capa 3
     (`coloresDelAvatarDelBanco`), nunca el color del banco: el amarillo de Bancolombia era el de la acción.
   - **Revisado con la misma regla**: Actividad (sin amarillo de acción: la píldora de la barra es navegación), Tú,
     Grupos, Créditos y Patrimonio no tienen más de un principal a la vista; los que dependen de componentes de `ds/`
     (`TarjetaDePropuesta`, `TarjetaDeCuenta`, el rail) se arreglaron ahí. La píldora activa de la barra del teléfono
     sigue en la marca: es navegación, no una acción (duda abierta para el dueño).
4. **Encabezados que se separan al hacer scroll** (`ds/SeparadoAlDesplazar.kt`). `Encabezado(desplazado = …)` y
   `MinScreenHeader(desplazado = …)`: con scroll > 0, fondo `fondo` y un `hilo` de 1 dp abajo, en `CORTO_MS`
   (`desplazadoDe(lista)` / `desplazadoDe(scroll)`). Lo usan Pagos (el título), Actividad (los chips, abajo de lo fijo)
   e Inicio: **la banda amarilla se vuelve crema al bajar** —el encabezado y lo de detrás de la barra del sistema
   (`LocalBandaDelInicioDesplazada`)—, y sus botones redondos pasan a la tarjeta con borde. El amarillo es de arriba,
   no del chrome.
5. **La cuadrícula de acciones**: rótulos de **un renglón** y cortos («Gasto», «Ingreso», «Comprobante», «Preguntar»; el
   lector de pantalla dice la frase entera; «Anotar ingreso» no entraba a 360 dp con la letra al 130 %), `Espacio.m` entre filas, círculos de `Medida.circuloDeAccion` (52) y, a pantalla ancha,
   dentro de una `Superficie`. Si con la letra agrandada un rótulo no entra, se achica hasta la letra de siempre
   (`seAchicaHastaLaBase`). `LasOchoAccionesDelInicioTest`: a 360 dp con ×1,12 y con el 130 % (×1,456), ninguno en dos
   renglones ni con «…».
6. **Actualizar a mano en la web y en el escritorio.** `actualizarAMano()` (`LoFresco`) olvida lo fresco y sube
   `LoFresco.recargasPedidas`; el Inicio, Actividad y Pagos lo escuchan con `AlPedirseUnaRecarga` y vuelven a
   preguntar. Lo llaman el **↻ del rail ancho** (`BotonDeIcono` junto al logotipo, `TAG_ACTUALIZAR_DEL_RAIL`) y, en la
   web, **la vuelta del foco a la ventana** (`AlVolverElFocoALaVentana`: `focus` y `visibilitychange`), como mucho una
   vez cada `ESPERA_ENTRE_RECARGAS_POR_FOCO_MS` (5 s).
7. **El aviso «Anotado» es un snackbar oscuro de un renglón**: fondo en la tinta (`texto`), letra en `sobreTinta`,
   acciones como texto sin bordes en `enlaceSobreTinta` —«Repartir» · «Deshacer»; el lector de pantalla dice «Repartir
   con Caro» (`AvisoDeDeshacer.Accion.rotuloCorto`)— y la falla en `saleSobreTinta`. Lo que no cabe a 360 se corta con
   «…» y las acciones quedan enteras. Va **en el flujo**, entre la pantalla y la barra (le quita su alto al hueco):
   no tapa la propuesta ni la barra. **La cifra del héroe cuenta** (`rememberCifraQueCuenta`, `LARGO_MS`, curva
   estándar) cuando el cambio lo hizo el dueño: el aviso marca `CambioDelDueno` al anotar, al anular y al deshacer, y
   el héroe la toma una vez (vale 15 s); al deshacer cuenta hacia atrás. Lo demás sigue cambiando entero (vuelta 75).

**Tokens nuevos**: `marcaPresionada`, `velo`, `sobreTinta`, `enlaceSobreTinta`, `saleSobreTinta` (`Tokens.kt`, medidos
en `ContrasteDeLosTokensTest`), `Medida.circuloDeAccion`, y en `Movimiento` `PRESIONAR_MS`, `ESCALA_AL_PRESIONAR`,
`ESCALA_DEL_BOTON_AL_PRESIONAR`, `ESCALA_DE_LA_FILA_AL_PRESIONAR`, `SALIDA_DEL_FUNDIDO_MS`, `ESCALA_AL_ENTRAR_LA_PESTANA`.

Lo vigilan `TocarSeSienteBienTest` (la escala en la semántica y una `IndicationNodeFactory` espía que nadie pide; la
barra sin nada fuera de la píldora; el encabezado con hilo al bajar; el ↻ a pantalla ancha; la recarga pedida; la
cifra que cuenta), `UnSoloAmarilloPorVistaTest`, `DobleExposicionTest`, `LasOchoAccionesDelInicioTest`,
`ElAvisoDeAbajoNoSeParteTest` (un renglón a 360 y 390), `ElToqueEsDelSistemaTest` y `ElAmarilloEsDeLaAccionTest`.

## 78. Vuelta 80: un solo sistema de íconos, y listas que respiran

Viene de la revisión visual 7 del 30-oct (puntos 5, 7, 8, 9, 10, 14 y 15; `r7-*.png`). **Sin reglas de plata.**
Dos componentes nuevos en `ds/` (`IconoDeMovimiento` y el archivo propio de `AvatarDePersona`) y una pieza de
Créditos (`ElPrestamoSinSaldo`). Ningún token nuevo.

1. **Un solo ícono por movimiento** (`ds/IconoDeMovimiento.kt`). Una prioridad, en `iconoDelMovimiento`: la
   marca del **comercio** si Movi la conoce (`marcaDelComercio`: el `merchant` y si no la descripción, con el
   catálogo de `:core`) y si no, la categoría con su ícono y su color. **Un banco no es un comercio**: «Cuota de
   Vehículo 4083» con el `merchant` «Banco de Occidente» llevaba la «O» azul en el detalle de la cuenta y el ícono
   lila de su categoría en el Inicio; ahora la categoría en los dos (y «Pago desde Bancolombia» ya no lleva la
   «B»). Lo pintan **todos** los lugares que muestran un movimiento: `MovementSingleRow` y `TransferRow` (Inicio,
   Actividad, el detalle de la cuenta), la hoja del movimiento, Buscar, Por revisar (la cabeza de un aviso, salvo
   cuando su título es el banco), los gastos de un grupo y su detalle, los anulados, el detalle de un período y la
   vista por comercio de En qué se va. `AvatarDelComercio` se fue. La semántica `ClaveDelIconoKey`
   («marca:uber», «categoria:Mercado») deja comparar dos lugares sin leer píxeles.
2. **Un solo avatar por persona** (`ds/AvatarDePersona.kt`). El dueño es `AvatarDelDueno`: sus iniciales
   (`inicialesDelDueno`, «DM») en el tinte (`TONO_DEL_DUENO`, `Propio`) **en todas partes**: el encabezado del
   Inicio, la tarjeta del perfil en Tú, el botón de los encabezados (`AvatarButton`) y los miembros de un grupo
   (`AvatarDelMiembro`). Nunca en rojo: el color que guardaba el perfil ya no pinta nada, y «Tu perfil» ya no lo
   ofrece (era un selector muerto). Un tercero es `AvatarDeTercero`: su inicial en su tono estable
   (`TONO_DE_UN_TERCERO`, `Info`) en Grupos, Personas y comercios y el detalle de un gasto. Los avatares de banco
   no se tocaron (son de la vuelta 79).
3. **«En qué se va» en el Inicio**: una `Superficie` blanca de lista con su título; cada categoría es una `Fila`
   (nombre y monto) con una `BarraDeProgreso` de 4 dp relativa a la mayor (`fraccionDeLaCategoriaEnElWidget`) y la
   diferencia contra el período anterior en una `Pildora` (`Peligro` solo si sube mucho; la regla de «nuevo» de la
   vuelta 76). Sin «Tus widgets»: cada widget lleva su título. En el teléfono los dos widgets van uno debajo del
   otro, a todo el ancho: a media pantalla y con la letra agrandada, nombre y monto no entraban en una fila. En una
   columna de 450 dp o más siguen lado a lado (`ParDeWidgets`, `ANCHO_PARA_LOS_WIDGETS_LADO_A_LADO`), para que las
   dos columnas del Inicio ancho queden parejas.
4. **Pagos con aire.** «Cuánto puedes gastar» son tres `CifraConRotulo` en renglón —Período, Semana, Meta de hoy—
   con su barra (`VentanasEnRenglones`); lo gastado y la meta de cada una van a «¿De dónde sale?»
   (`desgloseDeLaVentana`). Las cifras van compactas, como en las columnas (la del período repite «Te quedan»).
   El Inicio sigue con las tres columnas. «Lo que se repite al mes» muestra la cifra y el
   aviso de lo que no se pudo convertir; los párrafos (las dos cifras, la TRM, lo anual, las cuotas, los pagos
   únicos) van detrás de **«¿Qué cuenta?»** (`Boton` `Fantasma`, `hayQueExplicarElTotal`). La fila de una factura
   lleva **una** acción al final (`accionDeLaFilaPendiente`): «Anotar pago» o el «⋯», nunca los dos apilados; con
   el botón, «Pausar» queda en la hoja de la regla. «¿Usar siempre X para Y?» salió de la fila: es una
   `TarjetaDePropuesta` arriba de la lista (`PropuestasDeLaCuentaDeLaRegla`), con el ícono neutro.
5. **Agregar sin hueco a 390.** Sin teclado en pantalla y en una ventana angosta la hoja sigue ocupando todo el
   alto (las pestañas quietas de la vuelta 71), pero el cuerpo va centrado y **la cifra es la protagonista**:
   `cifra` × 1,5 (~54 sp, `ESCALA_DE_LA_CIFRA_GRANDE`) con `Espacio.xxxl` arriba y abajo. Con teclado no cambia
   nada. Sin teclado en pantalla, `Espacio.s` entre los chips y la fila de «Cuenta» (con teclado cada dp empuja el
   pie: `HojaAgregarGeometriaTest`). La cuenta va en un renglón con «…» (`FilaDeLaHoja`, `renglones = 1`); con la
   letra agrandada por encima de ×1,2 vuelve a dos (la vuelta 55: «Bancolombia Ahorros» a ×1,3 no entra en uno).
6. **La tarjeta de avisos de Actividad** es una `TarjetaDePropuesta` de un renglón: «5 avisos del banco por
   confirmar», «Confirmar 2» en un `Boton` chico (`botonCompactoDeConfirmar`) y «Revisar» como enlace. La nota del
   pago de tarjeta salió del encabezado del día: la dice el detalle del pago
   (`NOTA_DEL_PAGO_DE_TARJETA_EN_EL_DETALLE`).
7. **Patrimonio.** Con menos de 4 puntos (`PUNTOS_PARA_EL_RELLENO`) la curva va con sus puntos y una línea simple,
   sin el degradado (`CurvaConRellenoKey`). **`Fila` pone el chevrón sola** cuando tiene `onClick` y no trae
   `final` (`chevron`); con un monto que se abre, quien llama pide `chevron = true` (las cuentas, los bienes y
   «Créditos y tarjetas»), y una lista que se elige pasa `false`. «Créditos y tarjetas» dice «−$1.583.000», con el
   signo del renglón «Deudas» del resumen (`cifraDeLasDeudasEnLaFila`).
8. **El préstamo sin saldo, una sola pieza** (`ui/credits/ElPrestamoSinSaldo.kt`): el título en el tono de aviso
   («Falta el saldo de Vehículo 4083», o «2 deudas sin saldo»), el detalle (lo que dice el plan, qué escribir o
   cómo cuadrarla) y el mismo `Boton` `Secundario` chico («Usar este», «Cargarlo» o «Cuadrar»), con el texto de
   `loQueSeDiceSinSaldo`. La usan Patrimonio, la ficha, la fila del préstamo en Créditos (sin la píldora «Sin saldo
   en Movi» ni «cuádrala con el banco» repetidos) y el resumen de Créditos, que ahora cuenta las mismas deudas que
   Patrimonio (`deudasSinSaldoParaDecir`) y también ofrece «Usar este». En la hoja, la diferencia se explica:
   «Se registrará un cargo de $36.810.084 (incluye los $980.000 que Movi tenía a favor)»
   (`loQueSeRegistraAlAjustar`).

Lo vigilan `Vuelta80Test` (commonTest: la prioridad del ícono, las iniciales, la barra del widget, una acción por
factura, la curva, el signo, la pieza sin saldo y la frase del cargo), `Vuelta80EnPantallaTest` (Robolectric: el
mismo ícono en cuatro lugares, el dueño en el tinte en Inicio, Tú y Grupos, el widget con filas y barras y sin «Tus
widgets», Agregar sin hueco a 390, la tarjeta de avisos de un renglón, la curva de tres puntos, el chevrón y la
pieza sin saldo) y la guarda `UnSoloIconoYUnSoloAvatarTest` (`:core`). Se actualizaron las pruebas que leían la
diferencia del widget, la nota del pago de tarjeta en el día, los botones de la tarjeta de avisos, la propuesta de
cuenta y el «⋯» de Pagos, y `ElAmarilloEsDeLaAccionTest` (el avatar vive en su archivo).

## 79. Vuelta 81: la pantalla ancha como app de escritorio

Viene de la revisión visual 7 del 30-oct, punto 11 (`r7-1280-*.png`): a 1.280 Movi todavía parecía un teléfono
estirado en lo fino. **Sin reglas de plata.** Dos tokens nuevos en `Escalas.kt`: **`Medida.anchoMaximoDeLectura`**
(720, la columna de lectura de `Disposicion`, que ahora lo nombra: `ANCHO_DE_LECTURA = Medida.anchoMaximoDeLectura`)
y **`Medida.anchoDeFicha`** (520). **Regla de lo adaptable:** una lista o un formulario de una sola columna nunca
pasa del ancho máximo de lectura; cuando hay dos cosas, van en dos columnas en vez de estirar una. Lo que ya se
sentía de escritorio (el rail, Tú en dos columnas, Agregar como diálogo) fue la referencia.

1. **El detalle de un movimiento es una ficha** (`DetalleDelMovimiento.kt`, sección de la vuelta 81). Fuera del
   teléfono —en el panel de Actividad y como diálogo— el detalle va en una superficie de `anchoDeFicha` como mucho
   (`TAG_FICHA_DEL_MOVIMIENTO` en el panel, pegada al margen izquierdo; el diálogo es `MarcoDeHoja` con
   `anchoMaximo = anchoDeFicha`):
   - arriba **el título y la cifra** (`EncabezadoDelMovimiento(ficha = true)`: sin la línea «Hoy, 07:59 · Nu» ni
     «a 3 cuotas», que pasan a las filas);
   - **los datos como `Fila`s** (`datosDeLaFicha`, en una `Superficie` de capa 2): Cuenta, Fecha, Categoría (con su
     ícono), Nota (lo que va después de « · » en el concepto, `notaDeLaFicha`), Origen («Anotado a mano», «Aviso
     del banco», «Comprobante», «Extracto»), Cuotas y Grupo; solo lo que hay. Tocar Cuenta o Nota abre «Editar»,
     Fecha y Categoría sus secciones (las filas llevan el tag de su acción, así se encuentran como en el teléfono);
     Origen, Cuotas y Grupo solo informan;
   - **las acciones a la izquierda, en un renglón** (`AccionesDeLaFicha`, un `FlowRow` con `Espacio.xs`): `Boton`
     chicos con su ícono —Editar, Repetir, Repartir, Tercero— y «Más» como «⋯» (`BotonDeIcono` secundario). Sin
     Categoría ni Fecha (son filas) y sin el tope de seis: «Repartir» siempre cabe (`accionesDeLaFicha`).
     **Una sola principal** (`principalDeLaFicha`): «Editar»; ninguna si el movimiento espera confirmación (el
     amarillo es «Confirmar», arriba) o si la lista de al lado ya muestra la tarjeta de los avisos con su
     «Confirmar 2» (`otroAmarilloALaVista`, que pasa Actividad).

   Las secciones de edición son las de siempre y se abren debajo de las acciones. **El teléfono no cambia**: chip
   de categoría y la fila de seis acciones.
2. **Pagos a 1.280** (`PlanScreen.kt`). El encabezado —el título, el período con ‹ › y «Facturas | Presupuestos»—
   va **a lo ancho de las dos columnas** (`EncabezadoDePagosALoAncho`, `TAG_ENCABEZADO_DE_PAGOS_A_LO_ANCHO`): el
   navegador a la izquierda y el selector a la derecha, cada uno de `anchoMaximoDeBoton`. Debajo, las dos columnas
   arrancan a la misma altura (las dos con `AIRE_DEBAJO_DEL_SELECTOR` arriba; Presupuestos también). Mirando otro
   período, la izquierda sigue diciendo «Período en curso · …» arriba de sus piezas, atenuada (vuelta 70).
3. **Inicio a 1.280: la banda es una franja limpia a lo ancho.** Decisión, con el estado de la vuelta 79: la banda
   **vuelve a ir a todo el ancho** (`anchoDeLaBanda(ancho) = ancho`), sin la «L» que dejaba la vuelta 41 (bajaba
   solo por la columna del héroe mientras el encabezado seguía a lo ancho, con un rincón cóncavo debajo de la
   derecha). Se pudo porque desde la vuelta 79 la cuadrícula va en su `Superficie` blanca a pantalla ancha: se
   monta sobre la banda igual que el héroe, y su círculo amarillo nunca queda sobre el amarillo. Las dos columnas
   arrancan juntas (`arribaDeLaDerecha = 0`). Al bajar, la banda sigue volviéndose crema (vuelta 79).
4. **Actividad a 1.280: la lista es la protagonista** (`anchoDeLaListaDeMovimientos`): el 60 % de lo que queda sin
   el divisor; el detalle o el desglose, el 40 %, nunca menos de 380. A 1.280, 638 y 425 (eran 420 y 643); a
   1.024, 427 y 380; el umbral de aparición (801) no cambia. **El desglose es una columna lateral**
   (`ResumenDelPeriodoEnElPanel`): Entró, Salió y Neto en filas, una debajo de la otra y enteras
   (`TAG_CIFRAS_DEL_RESUMEN`), y cada categoría como `Fila` con su `BarraDeProgreso` de 4 dp relativa a la mayor
   (`fraccionDeLaBarraDelResumen`, `TAG_BARRA_DEL_RESUMEN`) y la diferencia contra el período pasado en una
   `Pildora`, como «En qué se va» del Inicio (vuelta 80).
5. **El barrido** (Tú, Grupos, Créditos, Patrimonio, Buscar, Por revisar, Tus períodos), a 1.280:
   - **Créditos** — lo único estirado: «Deuda total» y «Cómo salir de tus deudas» iban a lo ancho (1.032 dp), con
     las cifras de «Intereses este mes» a ~1.000 px de su rótulo y el chevrón al otro lado de la pantalla. Ahora
     son dos cosas en dos columnas: a la izquierda el resumen y «Cómo salir», a la derecha Préstamos y debajo
     Tarjetas (también en el esqueleto, para que nada salte). Se fue `repartirEnDos`. Con una sola deuda sigue la
     columna de lectura.
   - **Sin cambios** (ya cumplían): Tú, Patrimonio, Grupos con un espacio y Por revisar con mensajes van en dos
     columnas iguales (508 a 1.280, 616 como mucho a 1.920); Grupos con varios espacios, Por revisar sin mensajes,
     Créditos con una deuda y Buscar van en la columna de lectura (720); Tus períodos es lista + detalle con el
     detalle en dos columnas.

Lo vigilan `Vuelta81Test` (commonTest: los datos y las acciones de la ficha, el amarillo, el 60 %, la barra, la
banda y el ancho de lectura de las pantallas del barrido en una y dos columnas a 1.280, 1.440 y 1.920) y, en
Robolectric, `MovimientosListaYDetalleEnPantallaTest` (la ficha ≤ 520 con las acciones a la izquierda y un solo
amarillo; 520 a 1.920; la fila de la categoría; el desglose con una barra por categoría; el diálogo de 520 a 800;
el teléfono igual; los anchos nuevos a 1.024, 1.280 y 1.440), `PlanScreenTest` (el encabezado a lo ancho y las
columnas a ≤ 8 dp), `PagosHastaQueTePaguenTest`, `ElInicioEnPantallaAnchaYAngostaTest` (la franja sin muesca, las
columnas a la misma altura, las acciones dentro de su superficie; ahora monta con la clase de ancho de verdad),
`CreditosEnColumnasEnPantallaTest` y `Vuelta81EnPantallaTest` (el barrido en la cáscara a 1.280 × 800). La línea
base `dp_sueltos` baja a 113 (el 720 de `Disposicion` pasó al token).

## 80. Vuelta 82: jerarquía en las colas, y una cifra que no se contradice

Viene de la revisión 8 del 30-oct (puntos 1, 2, 4, 5, 7, 9, 13 y 14; capturas `r8-*.png`). **Sin reglas de plata
nuevas**: la deuda total sigue siendo lo que Movi sabe y el optimista del héroe solo adelanta lo que el server va a
decir, con las funciones de siempre. Un componente nuevo en `ds/` (`Interruptor`) y cuatro medidas en `Escalas.kt`
(`anchoDelInterruptor`, `altoDelInterruptor`, `perillaDelInterruptor`, `sombraDeLaPerilla`). Ningún color nuevo.

1. **El amarillo se pide a propósito.** `TarjetaDePropuesta` va por defecto con la acción `Secundario` y el círculo
   `Neutro`; quien llama pide `Principal` solo si es la única acción principal de su vista. Quedan explícitas: la
   tarjeta de avisos de Actividad («Confirmar N»; «Revisar» solo, que navega, va secundario), el lote de Por revisar,
   «Revisar» en Categorías, «Empezar una conversación nueva» en Movi AI, «Completar datos» en la ficha de una deuda
   sin condiciones y, **sin cambiar**, «¿Usar siempre X para Y?» de Pagos (`PausarLaRegla.kt`, explícita como
   estaba: Pagos lo decide la vuelta 83). Lo demás pasa a secundario (Reconciliar, el saldo del aviso en Cuadre,
   saldar en Grupos). **El círculo de la tarjeta de avisos va en `TonoDeIcono.Aviso`**. **En Por revisar** el único
   amarillo es el lote «Confirmar N»: `LocalLaTarjetaLlevaElAmarillo` apaga la acción principal de cada tarjeta
   («Confirmar», «Es este», «Elegir», «Sí, se cobró», vía `PastillaDelPago`) cuando hay lote o más de una tarjeta
   (`laTarjetaLlevaElAmarillo`); sola, sigue amarilla. **La fila de cuenta** (`AvatarDeLaCuenta`, en Patrimonio y
   donde se nombre una cuenta) usa `coloresDelAvatarDelBanco`: la «B» en la capa 3 con la tinta.
   `UnSoloAmarilloPorVistaTest` cuenta ahora también Actividad, Por revisar (con lote y con una tarjeta) y Patrimonio.
2. **La deuda total, honesta.** Debajo de la cifra grande de Créditos va **una línea**
   (`lineaDeLasDeudasSinSaldo`): «Falta Vehículo 4083, sin saldo (unos $35,8M según el plan)» con
   `saldoSegunElPlan`; sin plan, «Falta Vehículo 4083, sin saldo»; varias, «Faltan 2 préstamos, sin saldo». «Unos» y no «≈»: Figtree no tiene ese
   glifo. **La pieza `ElPrestamoSinSaldo` sale una vez por vista**, en la fila del préstamo; el resumen lleva solo
   la línea. **El signo** es el de Patrimonio (`cifraDeLasDeudasEnLaFila`): «Deuda total −$1.583.000» y las filas de
   Créditos con «−».
3. **«Te quedan» y «Tu plata» cambian juntas** (`ui/dashboard/ElHeroeOptimista.kt`).
   - **El optimista**: anotar (`mostrarLoAnotado`), anular (la hoja del movimiento) y sus «Deshacer» registran el
     movimiento en `MovimientosDelDueno` (y marcan `CambioDelDueno`). El héroe toma los datos que tenía cuando el
     dueño lo hizo y los adelanta con `datosConLosMovimientosDelDueno`: el `montoContado` se suma al gasto del día
     (`gastoVariablePorDia`) y el monto se resta del saldo de su cuenta si es de dinero; «Te quedan» y «Tu plata»
     salen de `disponibleDelInicio` y `heroBalance`, como siempre. Un gasto de una cuenta de Tu plata resta en las
     dos; con tarjeta, solo «Te quedan». Lo que cuenta lo dicen `esGastoDelPeriodo` y `cuentaEnGastosEIngresos`.
     **Dudoso → nada**: un ingreso, otra moneda, un traspaso, otro período, por confirmar, una cuenta que no es de
     dinero ni tarjeta, o la categoría de un pago del período (puede ser ese fijo, ya restado).
   - **El server confirma**: cuando termina una carga del Inicio después del cambio
     (`DashboardDataCache.terminadaEn`, por `LocalCargaDelInicioTerminadaEn`), el héroe deja el optimista y muestra lo
     leído; si difiere, **la cifra cuenta** hasta la del server.
   - **Sin optimista, las dos esperan juntas**: `rememberEstadoQueCambia(seCongela = …)` congela todo cambio de cifra
     del héroe mientras la carga está en vuelo, también el del dueño (antes se saltaba la congelación y las cuentas
     pintaban «Tu plata» un segundo antes que «Te quedan»).
   - **Las dos cifras cuentan juntas**: `rememberCifraQueCuenta` ya no consume la marca de `CambioDelDueno` (cuentan
     «Te quedan», el enlace «Tu plata» y la corrección del server mientras la marca vale, 15 s).
4. **El snackbar con el monto**: «Anotado $12.300 · Restaurantes» (`textoDeLoAnotado`; «Ya estaba anotado $12.300 ·
   …»). El aviso es de un renglón y se corta al final: ahora se corta la categoría, nunca el monto. Después de
   «Deshacer» sale **«Deshecho»** sin botones durante `DURACION_DEL_DESHECHO` (1,5 s).
5. **El hilo se ve**: el de los encabezados desplazados es `colorDelHiloAlDesplazar` = `bordeDeBoton` (`#D9D1C3`,
   1,34:1 sobre el crema). El `borde` (`#E7E1D7`) daba 1,15:1, por debajo del piso. `ContrasteDeLosTokensTest` mide lo
   que no es texto: el hilo ≥ 1,3:1 contra el fondo y la tarjeta.
6. **El mismo conteo en todos lados**: la tarjeta de Actividad es una sola —«6 por revisar», debajo «5 avisos · 1
   movimiento» (`textoDePorRevisar` y `desgloseDePorRevisar` sobre el `ConteoPorRevisar`), con «Confirmar N» cuando
   Movi entendió avisos y «Revisar» siempre—; antes eran dos (el renglón sin avisos y «5 avisos del banco» con ellos)
   y en frío se pasaba de una a la otra. Recuerda su forma (`FormaDeMovimientos.tarjetaPorRevisar`,
   `recordarTarjetaPorRevisar`) y mientras se cuenta pinta `EsqueletoDeLaTarjetaDeAvisos`, del mismo alto.
   `recordarLineaDePeriodo` ya copia la forma guardada en vez de reemplazarla.
7. **Hover en superficies grandes**: una `Superficie` tocable y la `TarjetaDeCuenta` no se velan enteras; cambian su
   borde a la tinta al 20 % (`conBordeAlEncima`, `colorDelBordeAlEncima`, `BordeAlEncimaKey`). El velo queda para lo
   chico (botón, fila, píldora).
8. **El interruptor apagado se ve activo** (`ds/Interruptor.kt`): apagado, el riel en la capa 3 con el borde en
   `neutro` (≥ 3:1 contra la tarjeta y el crema: el contorno de un control, WCAG 1.4.11) y la perilla blanca con
   una sombra de 1 dp; encendido, el riel en la tinta y la perilla blanca. Lo usan Tú (el `Interruptor` de Ajustes lo
   envuelve) y «Editar inicio»; ya no queda un `Switch` de Material en la app.

Lo vigilan `Vuelta82Test` (commonTest: el amarillo de Por revisar, la línea de la deuda total, el optimista con
cuenta de dinero, con tarjeta, ida y vuelta y lo dudoso, el snackbar con el monto, el mismo número en Inicio y
Actividad, el borde de hover), `ContrasteDeLosTokensTest` (el hilo y el interruptor), `Vuelta82EnPantallaTest`
(Robolectric: la línea y la pieza una vez con el signo en Créditos, las dos cifras del héroe cuadro a cuadro, la
tarjeta solo en «Te quedan», la corrección del server, el monto que no se corta a 360, «Deshecho», la tarjeta de
avisos y su esqueleto del mismo alto con el círculo de aviso, la superficie con borde y el interruptor) y
`UnSoloAmarilloPorVistaTest` ampliado.

### La revisión de la vuelta 82, y lo que cambió

1. **[Plata] El optimista no se suelta antes de tiempo.** Se soltaba con cualquier carga que *terminara* después
   del cambio. En el teléfono `postEvent`/`voidEvent` escriben en el espejo y el `SyncEngine` sube hasta 30 s
   después, pero el Inicio lee del server: la carga que arranca al guardar traía la cifra sin el gasto, y sin
   señal se sellaba igual. Ahora cada `MovimientoDelDueno` tiene `listoEn` —desde cuándo el server lo tiene— y
   solo lo suelta una carga **buena** (resumen y cuentas del server, sin fallar: `cargaBuenaEmpezadaEn`) que
   **empezó** desde entonces (`DashboardDataCache.cargaBuenaEmpezadaEn`, `LocalCargaBuenaDelInicioEmpezadaEn`).
   `MovimientosDelDueno.listo` (cuando la escritura terminó) pregunta al repositorio
   `elServerYaTieneElMovimiento` —la web, siempre; el teléfono, por el sello de la fila y de sus anulaciones—;
   si no, pide `subirLoPendienteAhora` (una vuelta del `SyncEngine`), y cuando subió anota `listoEn` y pide
   recargar (`actualizarAMano`). Sin señal reintenta cada 30 s y el optimista se queda hasta
   `VIGENCIA_SIN_SUBIR_MS` (10 min); listo, vence a los 15 s, y el héroe recompone al vencer (un
   `LaunchedEffect` que espera `proximoVencimiento`). Se eligió mantener el pendiente hasta que la fila suba, y no
   subir antes de cerrar Agregar: guardar sigue sin esperar la red, y no se tocó Agregar.
2. **[Plata] Más casos dudosos** (`datosConElMovimiento`): una cuenta que no es de Tu plata (`esDeTuPlata`: el
   server lo compensa con «pagado desde fuera»), la categoría de un apartado de esa cuenta (baja el apartado), y lo
   que se parece a un pago del período con las señas del emparejador (`pareceUnPagoDelPeriodo`: categoría,
   `nombreDeMovimientoPegaConRegla` contra el nombre o el comercio, un destino con el mismo monto, un sello o
   candidato en las ocurrencias, el nombre de una suscripción). Quitar más de lo que hay en el gasto del día
   también espera al server.
3. **El interruptor toca 48 dp** de alto (`sizeIn(minHeight = Medida.objetivoTactil)` en la capa del toque); el
   riel sigue de 52 × 32.
4. **El snackbar en dos partes** (`partesDelAviso`): lo de antes de « · » («Anotado $12.300») se mide entero, lo de
   después (`TAG_RESTO_DEL_AVISO`) es lo que se corta. «Tu parte $16.000 · repartido con Caro» (`textoDeRepartido`).
5. **Créditos sin «+»**: «Falta Vehículo 4083, sin saldo (unos $35,8M según el plan)».

### La re-revisión de la vuelta 82

1. **La base no se vuelve a tomar.** La base y los movimientos viven en `MovimientosDelDueno.estado`
   (`EstadoDelOptimista`, un `MutableStateFlow` que se cambia con `update {}`: `listo` escribe desde otro hilo). La
   base es lo último que leyó el Inicio (`DashboardDataCache.data`) al registrar el **primero**, y se suelta recién
   cuando ninguno queda pendiente (`podado`); mientras tanto el héroe muestra la base con **todos** los movimientos
   (`adelanto`). Antes se retomaba al volver al Inicio: sin señal las cuentas del espejo ya traían el gasto y se
   restaba dos veces, y al soltarse el primero de dos también. En el teléfono `listoEn` es el `syncedAt` real de la
   fila y de sus anulaciones (`desdeCuandoLoTieneElServer`, nunca antes de que volviera la escritura), y
   `alSubirAlgo` despierta la espera (`despertar`) en vez de esperar 30 s. Una carga buena avisa con
   `cargaBuenaEmpezo`.
2. **La espera termina.** El bucle de `listo` se corta cuando el movimiento venció o salió, y lo vencido sale del
   estado. Una fila anulada antes de subir cuenta como lista (`DESDE_LA_ESCRITURA`: nunca sube, efecto neto cero).
   `subirLoPendienteAhora` ya no vacía lo recordado al pasar: lo hace `alSubirAlgo` → `olvidarLoFresco` solo cuando
   de verdad subió algo (sin señal, cada reintento dejaba las pantallas en esqueleto).
3. El anillo de foco del interruptor rodea el riel (`anilloDeFoco(alrededorDeLaCaja = …)`), no la caja del toque.
4. `partesDelAviso` deja el código de una moneda que no es pesos ni dólares con su cifra («EUR 45»).
5. **Aceptado**: lo dudoso no tiene optimista, tampoco sin señal. En ese caso las dos cifras esperan juntas el final
   de una carga, y sin señal esa carga puede traer las cuentas del espejo (ya con el gasto) y el resumen de antes:
   es el síntoma original de la revisión 8, que queda solo para lo dudoso y sin conexión.

## 81. Vuelta 83: filas densas y limpias, Agregar útil y Pagos sin repetir

Viene de la revisión 8 del 30-oct (puntos 3, 6, 8, 10, 11, 12 y 15, el amarillo de Pagos del punto 1 y los
detalles 2 y 7 del top 10; capturas `r8-*.png`). **Sin reglas de plata**: nada se guarda solo. Ningún token
nuevo; `Fila` gana dos parámetros (`lineasDelTitulo` y `iconoDelSubtitulo`).

1. **El nombre corto de la cuenta** (`ui/components/NombreCortoDeCuenta.kt`, `nombreCortoDeCuenta`). Con
   número, el banco (la primera palabra) y los últimos cuatro del primer número: «Bancolombia Ahorros 4057» →
   «Bancolombia ·4057»; si el nombre dice la moneda va al final («Master ·3684 USD»: la tarjeta vive en dos
   cuentas con el mismo número). Sin número, el nombre tal cual hasta `LARGO_MAXIMO_DEL_NOMBRE_CORTO` (20)
   letras («Nu», «Efectivo», «Bancolombia Ahorros») y si es más largo, **recortado por el medio** con «…».
   Lo usan la fila «Cuenta» de Agregar, las filas de movimientos (Inicio, Actividad, Buscar) y el subtítulo
   de un traspaso («De Bancolombia ·4057 a Nu»). Lo guardado no cambia. Desde y Hacia del traspaso siguen
   con el nombre entero como título de su fila (vuelta 47: ahí cabe en dos renglones), y la lista del
   selector de cuentas también.
   - **En Agregar, la celda de la cuenta ocupa lo que deja el rótulo** (`FilaDeLaHoja(valorHastaElRotulo =
     true)`: el rótulo «Cuenta» se mide y el valor toma el resto). El porqué de debajo («La que usas para
     Restaurantes») se corta con «…» si no le alcanza. Se fue `FRACCION_DEL_VALOR_CON_ROTULO_LARGO`.
2. **La cuenta solo cuando difiere** (`textosDeLaFilaEnLaLista`). En Inicio, Actividad y Buscar una fila no
   nombra la **cuenta habitual** (`cuentaHabitual`); la regla:
   1. la más usada que ya calcula la app (`CuentaMasUsadaCache`, la de más gastos en 30 días, la misma que
      propone Agregar);
   2. si todavía no se sabe (Actividad antes que el Inicio), la que más movimientos tiene en la lista;
   3. con un empate en el paso 2, ninguna: si la lista mezcla cuentas por partes iguales, todas las filas
      dicen la suya.

   Si sin la cuenta la fila quedaría sin subtítulo (la descripción es la categoría y no hay comercio), la
   nombra igual: todas las filas tienen dos renglones. Por revisar, el detalle de una cuenta y los ajustes
   la nombran siempre (en su nombre corto).
3. **«Repetir uno reciente» en Agregar** (`ui/quickadd/RepetirUnReciente.kt`). En el hueco de la hoja sin
   teclado a 390, debajo de «Nota»: «Repetir uno reciente» y tres píldoras que se tocan (`PildoraDeFiltro`
   compacta) con los últimos gastos distintos —«Uber Rides · $8.400»—. Tocar una **llena** el monto, la
   categoría, la cuenta (como elegida a mano) y el concepto; el dueño toca Guardar. Solo en «Gasto», sin
   teclado en pantalla y fuera del modo pago: con el teclado no hay hueco y cada dp empuja el pie. Los
   recientes salen de la historia que la app ya tiene (`getEventsByDay`, la lectura sin repetir), sin
   pedir nada nuevo al server (`gastosRecientesParaRepetir`: gastos en pesos, sin patas de traspaso, cuotas,
   ajustes ni tu parte de otro, en las cuentas de «Gasto», distintos por lo que dice la píldora). Sin
   recientes no se pinta nada. Las pestañas fijas y «Guardar» siempre a la vista (vuelta 71) no cambian.
4. **Pagos sin repetir.**
   - «Período» dice **lo gastado de su meta** («$2.105.350 de $3.556.300», `TAG_PERIODO_GASTADO_DE_LA_META`),
     no «Te quedan» otra vez.
   - **Un solo formato por tarjeta**: la tarjeta de Pagos va **entera** —«Te quedan», «Disponible del
     período», Período, Semana, Meta de hoy y el desglose de «¿De dónde sale?»—; eran «$1.450.950» junto a
     «$1,5M». Entera y no compacta porque «Te quedan» es la misma cifra del héroe del Inicio, que va entera.
     El Inicio sigue con sus tres columnas compactas.
   - **Los recordatorios** son una `TarjetaDePropuesta` de un renglón: la campana tachada en el tono de
     aviso, «Tus recordatorios no llegan», y debajo «Activar» (o «Ver avisos», con las notificaciones
     bloqueadas en el navegador) y el enlace «Ahora no». Van solos, sin otra superficie alrededor.
   - **El mes** del navegador es un título: `tituloMedio` (15 sp semibold) en la tinta. Iba en gris de 11.
   - **Suscripciones plegadas, una sola `Fila`**: «Suscripciones activas» con «1 cobro · $63.338 al mes»
     debajo y el chevrón (`TAG_SUSCRIPCIONES_PLEGADAS`). Abiertas, el título de la sección con «Ocultar».
   - **Un solo amarillo en Pagos**, a 390 y 1.280: «Anotar pago» de la primera factura. «Guardar en la
     regla» y «Activar» van en `Secundario`, **explícito** en Pagos (no se depende del default de
     `TarjetaDePropuesta`, que la vuelta 82 cambia). Lo cuenta `UnSoloAmarilloPorVistaTest`.
5. **La ficha a 1.280**: el «⋯» de «Más» va a la derecha del renglón del título (`MasDeLaFicha`,
   `EncabezadoDelMovimiento(alFinalDelTitulo = …)`), y abajo «Editar · Repetir · Repartir» caben en un
   renglón. Iba al final de las acciones y quedaba solo en un segundo renglón.
6. **«En qué se va» más denso**: cada categoría mide ~52 dp —el nombre, la `Pildora` de la diferencia junto
   al monto, y la `BarraDeProgreso` a `Espacio.xs` debajo del nombre—. Era una `Fila` de 56 con la barra y la
   píldora en otro renglón (~72 dp, tres categorías en ~275 px).
7. **Filas de Actividad de una altura**: el título va en un renglón con «…» (`Fila(lineasDelTitulo = 1)`) y
   el ↻ del recurrente pasa al subtítulo (`Fila(iconoDelSubtitulo = …)`, `TAG_ICONO_DEL_SUBTITULO`; el lector
   dice «Recurrente»). «Cuota de Vehículo 4083» se partía en dos renglones por el ↻ y el monto. Vale para
   `MovementSingleRow` y `TransferRow` (Inicio, Actividad, Por revisar, el detalle de una cuenta).
8. **El panel derecho de Actividad a 1.280** se titula «Resumen del período»
   (`TITULO_DEL_RESUMEN_EN_EL_PANEL`): decía «Octubre 2026», el mismo mes del navegador de la lista.

Lo vigilan `Vuelta83Test` (commonTest: el nombre corto, la cuenta habitual sí/no, la fila que la nombra
igual y los recientes distintos), `Vuelta83EnPantallaTest` (Robolectric: la cuenta entera en Agregar a 390 y
1.280, «Repetir uno reciente» llena el formulario, el Período con lo gastado y sin cifras compactas, los
recordatorios en un renglón, el widget con filas de ≤ 56 dp y las filas de Actividad del mismo alto a 360
con la letra al 130 %), `UnSoloAmarilloPorVistaTest` (Pagos con la propuesta y los recordatorios, a 390 y
1.280) y `MovimientosListaYDetalleEnPantallaTest` (el «⋯» arriba y las acciones en un renglón; el panel
«Resumen del período»). Se actualizaron `Vuelta80EnPantallaTest` (el nombre corto en Agregar; el widget ya no
es una `Fila`), `LosRecordatoriosVanDebajoDeLosPagosTest`, `LoQueSeVeEnteroTest` y `LaFranjaSigueAlChipTest`.

**Revisión visual de la 83** (`r83-*.png`):

- **Agregar sin recientes va arriba**: debajo de las pestañas, con su espaciado fijo; centrado dejaba el aire
  partido en dos huecos. Con recientes sigue centrado.
- **Los recientes, en un renglón** que se desplaza de lado (`TAG_RENGLON_DE_RECIENTES`, ~40 dp), y **solo si
  caben**: si con ellos el cuerpo no entra en el hueco (360 × 640), se esconden. Guardar y las pestañas no
  cambian.
- **Buscar**: con la cuenta (que no es la habitual), ella primero y la fecha corta, «Nu · Mercado · 21 oct»:
  al final era lo que se cortaba. En la habitual, «Mercado · 21 de octubre».
- **Un recurrente cuyo título es la categoría** («Internet ↻») dice «↻ Recurrente», no la cuenta habitual
  (`textosDeLaFilaEnLaLista(respaldo = …)`). Dice «Recurrente» y no «Mensual» porque la fila no sabe la
  frecuencia: una suscripción puede ser anual.
- **Pagos**: sin el renglón «Disponible del período» (el «de $X» del Período ya lo dice) y la semana se calla
  con `DIAS_PARA_CALLAR_LA_SEMANA` (7) días o menos por delante (`seVeLaSemanaEnPagos`): casi igual a «Te
  quedan».
- **Los recordatorios fuera del teléfono** (≥ 600 dp): el título y «Activar · Ahora no» en un renglón.
- **«Cuenta» en Agregar va centrada** cuando su aviso está reservado y vacío (se reserva el alto, sin
  pintar un renglón vacío).
- **La categoría de Gasto elegida a mano vuelve con Gasto**: elegir un reciente (Comida), pasar a Ingreso y
  volver deja Comida. El monto y la nota siguen viajando entre pestañas (vuelta 71: Agregar no pierde nada);
  la categoría no puede viajar —no sirve para el otro tipo—, así que se guarda al salir y vuelve.
- **La ficha a 1.280**: el «⋯» va junto a la X, en su renglón (`SheetHandleWithClose(antesDeLaX = …)`).
- **«Gasto por categoría» del panel de Actividad**: la fila densa del Inicio (la píldora junto al monto, la
  barra dentro de la fila).
- «¿Qué cuenta?» alineado con el texto de su tarjeta.

## 85. Vuelta 88: la guía de primer uso en el teléfono del papá

Viene de tres fotos del Samsung del papá del dueño (pantalla curva, **navegación de tres botones** y la letra
del sistema agrandada): «Empezar» debajo de la barra del sistema, las píldoras de los bancos cortadas por los
dos lados con solo dos bancos a la vista («faltan más opciones de bancos») y «Dar acceso a las
notificaciones» partido en dos renglones con el segundo afuera del botón, un interlineado enorme y el aviso
de los permisos en rojo. **Sin reglas de plata**; ningún token nuevo.

1. **La barra del sistema la descuenta la cáscara** (`EsqueletoDeLaCascara` en `App.kt`). En el teléfono solo
   la descontaba `MinBottomNav`; toda pantalla **sin la barra de pestañas** dibujaba su pie debajo de la
   barra del sistema. Ahora, sin barra inferior, la columna lleva `navigationBarsPadding()` (con
   `statusBarsPadding()` arriba e `imePadding()`, como siempre). Arregla con una línea la guía de primer uso,
   Entrar, Crear cuenta, la revisión de un extracto, el detalle de una importación y el editor de pantallas
   (las que no muestran la barra; Borrar la cuenta sí la muestra y ya estaba bien). Las hojas siguen con su
   propio descuento (`MarcoDeHoja`), que ve lo ya consumido y no lo dobla.
2. **Un botón nunca se corta.** `Boton` tiene alto **mínimo** (`heightIn`, 48 o 36) en vez de fijo, el texto
   sin tope de renglones (centrado por la fila, sin `TextAlign.Center`: un párrafo centrado se reportaba
   dibujado fuera de su caja), con `Espacio.xxs` arriba y abajo: un rótulo que no cabe baja entero a un
   segundo renglón y el botón crece. Se eligió **crecer y no acortar los rótulos**: «Dar acceso a las
   notificaciones» sigue diciendo lo que hace. `PildoraDeFiltro` igual (alto mínimo, el texto baja si la fila
   que la contiene es más angosta que el nombre; en una fila que se desplaza de lado nunca pasa).
   - **`ParDeBotones`** (`ds/Boton.kt`): «Atrás» y «Siguiente» lado a lado, mitad y mitad, si los dos rótulos
     caben en un renglón de su mitad; si no (×1,5 a 320 dp, «Guardar y seguir»), **apilados con el principal
     arriba** y a todo el ancho. Lo usa el pie de la guía (`TAG_ATRAS_EN_LA_GUIA`).
3. **Los bancos: todos y sin cortes** (`ui/accounts/FormularioDeCuenta.kt`). La causa de las píldoras
   cortadas era la fila que se desplazaba de lado (`enUnaFila`, vuelta 54): se fue. Los bancos y los tipos
   **bajan en renglones** (`FlowRow`) dentro de la tarjeta, en la guía y en «Nueva cuenta». Lo de la vuelta
   54 —«Agregar cuenta» sin desplazar a 390— se cedió: que se vea todo ganó.
   - **«Otro banco»**: la última píldora abre ahí mismo una búsqueda (`CampoDeBusqueda`) con **todos** los
     bancos y billeteras del catálogo (`bancosParaElegir` en `:core`, alfabético, con
     `normalizarParaBuscar` contra el nombre y los patrones: «bogota» → Banco de Bogotá, «colpatria» →
     Scotiabank Colpatria). **Fuera de la lista**: Visa, Mastercard y la American Express (son tarjetas; la
     AMEX sí está en el selector de tarjetas). **Dentro**: los fondos (Skandia, Porvenir, Protección,
     Colfondos, Fiducuenta), porque el formulario tiene «Inversión» (cesantías, pensión voluntaria). El
     elegido de la lista queda a la vista y elegido antes de «Otro banco»; si el banco no está, «Usar «lo
     escrito»» lo toma tal cual (en una cuenta también se puede escribir el nombre a mano, como siempre).
   - **Bancos nuevos en `MarcasConocidas`**: Banco Caja Social, Banco Agrario, Banco Pichincha, Bancoomeva,
     Banco Finandina, Banco Serfinanza, Banco Coopcentral, Confiar, Iris, Ualá, Banco W y Mibanco (Banco
     Falabella ya estaba). Patrones **conservadores**: el nombre entero o una palabra que no es de uso diario;
     «agrario», «pichincha», «coomeva» (también es una EPS), «confiar» e «iris» solo en el nombre de una
     cuenta. `BancosParaElegirTest` prueba que lo que se reconocía antes sigue yendo al mismo banco.
4. **El paso de captura con aire normal** (`SmsSensorSetupSection.android.kt`). Los textos pasaban
   `fontSize` suelto y heredaban el `lineHeight` de 24 sp de `bodyLarge`: 12 sp de letra con 24 de renglón.
   Ahora usan `Movi.textos` (`cuerpo`, `apoyo`), con su interlineado. El aviso de la hibernación va en tinta
   (es información; el rojo es para errores) y el estado de cada permiso es una `Pildora` («Concedido» en
   éxito, «Falta» neutra). Los botones son el `Boton` del sistema (eran una caja de 44 fijos con el texto en
   crema sobre el amarillo).
5. **Un amarillo por paso.** `SmsSensorSetupSection(conAccionPrincipal)`: con `true`, lo primero que falta (el
   permiso de SMS; si no, las notificaciones; si no, la hibernación) es el principal y lo demás secundario;
   la guía pasa `!sePuedeAvanzar`, así que si su «Siguiente» está encendido la sección no pone otro amarillo.
   «Ahora no» y «Saltar este paso» siguen siendo botones de texto (`Fantasma`).

**Pruebas**: `LaGuiaEnElTelefonoDelPapaTest` recorre cada paso de la guía de Android a 320, 360 y 390 dp con
la letra a ×1,0, ×1,3 y ×1,5 (sobre el ×1,12 de la app): ningún rótulo de botón con «…», renglones de más ni
fuera de su botón (medido sin recortar), un solo amarillo, y en las cuentas ninguna píldora fuera del
formulario; el pie apilado a ×1,5; «Otro banco» con «bogota»; y la guía dentro de la cáscara con 48 dp de
barra de navegación simulada (`ViewCompat.dispatchApplyWindowInsets`): el principal y «Saltar este paso»
quedan arriba de la barra.

## Vuelta 89 · Un descarte vive más que su tarjeta

**Regla: lo que se quita en el acto («Está bien», «No», «Sí, cambiar») no lanza su pedido en un
`rememberCoroutineScope` de lo que se quita.** Sacar la última tarjeta de «Movi propone hoy» (o de «Para
revisar») saca la sección entera de la composición; su alcance se cancelaba con el pedido en vuelo, el
`runCatching` tomaba esa `CancellationException` como una falla y deshacía el descarte: la tarjeta volvía en
el mismo cuadro y el dueño veía que «Está bien» no hacía nada, mientras el server sí lo guardaba (31-oct,
`deuda:…:2026-10` y `categoria:Tecnología:2026-11` en `anomalias_descartadas`). Las pruebas no lo veían
porque su repositorio falso contestaba sin suspender.

- Las anomalías van por `AnomaliasDescartadasEnLaSesion.descartarYGuardar(huella) { … }`: saca la huella y
  corre el pedido en un alcance propio (no de ninguna pantalla), con `intentar`, que deja pasar la
  cancelación; solo una falla de verdad devuelve la tarjeta.
- Una prueba de un descarte optimista usa un repositorio que **tarda** (un `CompletableDeferred`), no uno
  que contesta en el acto: `EstaBienSeVaYNoVuelveTest` monta el Inicio entero y «Para revisar».
- Revisados con el mismo patrón y sin el fallo: «Ya lo vi» del cierre (alcance de `DashboardScreen`, que no
  sale, y no deshace), «No ajustar» de los cuadres por aviso, «No se cobró» de los débitos y la bandeja de
  Por revisar (la tarjeta espera la respuesta antes de irse, y su alcance es el de la pantalla).
