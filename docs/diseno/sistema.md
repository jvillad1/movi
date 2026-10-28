# El sistema de diseño de Movi

Ola R0 del rediseño (26-oct-2026). La base es el camino 1 «Noche premium», con la cuadrícula de
acciones del camino 3 y el feed de propuestas del camino 5. El pedido del dueño: **que los
espaciados, los componentes y todo sea extremadamente estandarizado y correcto en toda la app, que
no haya inconsistencias entre pantallas y que las secciones tengan sentido.**

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
| `Medida.altoDeFila` / `altoDeFilaDoble` | 56 / 64 |
| `Medida.objetivoTactil` | 48 — nada que se toque mide menos |
| `Medida.altoChico` | 36 — botón chico y filtro, solo dentro de una tarjeta |
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
| `Radio.m` | 12 | filas destacadas, botones |
| `Radio.l` | 16 | superficies |
| `Radio.xl` | 24 | hojas, tarjetas de cuenta |
| `Radio.pill` | 999 | píldoras, filtros, círculos |

Antes eran 6 / 11 / 16 / píldora: el 6 pasó a 8 y el 11 a 12 (a la grilla de 4, y los valores que la
app ya escribía a mano), y las hojas de 28 a 24. `Movi.formas.*` sale de acá.

Íconos: **`Icono.s` 16** (en un texto, en una píldora), **`Icono.m` 20** (fila, botón, pestañas),
**`Icono.l` 24** (solo, en un encabezado o en un círculo grande). Nada más.

## 3. Superficies por capas, sin bordes duros

Una superficie se distingue de lo de abajo **por su color, no por una línea**:

| capa | oscuro | claro | para |
|---|---|---|---|
| `fondo` | `#08090D` | `#E9ECF1` | el lienzo |
| `capa1` (= `tarjeta`) | `#191B25` | `#FFFFFF` | tarjetas, listas, hojas, barra de pestañas |
| `capa2` | `#222530` | `#F2F4F7` | encima de una capa 1: chips sin elegir, campos, botón secundario |
| `capa3` | `#2B2F3D` | `#E3E7EC` | pistas de progreso, lo apretado |
| `propuesta` | `#1D1A31` | `#F8F5FF` | una propuesta de Movi (tinte de la marca) |

Los valores de referencia del camino 1 (`#0A0B10 / #12141C / #1A1D28 / #232737`) **no pasaban la prueba
de contraste**: la capa 1 quedaba a 1,07:1 del fondo, más plana que el 1,15 que Movi exige. Se ajustaron
lo justo. `ContrasteDeLosTokensTest` mide: cada texto ≥ 4,5:1 sobre el fondo **y cada capa**; capa 1 ≥
1,15 del fondo; cada capa ≥ 1,08 de la de abajo; la propuesta ≥ 1,08 del fondo y distinta de toda capa;
cada color de estado ≥ 4,5 sobre su propio lavado.

El borde (`borde`) sigue existiendo para campos y separaciones que de verdad lo necesitan; una tarjeta
no lleva borde. `MinCardVariant.High` (que era «tarjeta con borde») pasó a ser la capa 2; desde la vuelta
de diseño 21, `MinCard` ya no existe.

## 4. Marca y estado

- **La marca (lavanda) es acción y foco.** Botón principal, filtro elegido, pestaña activa, enlace,
  lo que propone Movi. **Nunca significa plata.**
- **Los colores de estado son solo para estado**: `entra` (entró plata, éxito), `sale` (se pasó,
  peligro), `aviso` (por vencer, por confirmar), `entreCuentas` (traspaso, información). Una píldora en
  aviso dice «vence», no «esto es importante».
- **Un gasto no es una alarma**: va en el color del texto. `sale` es para lo que se pasó o se debe.
- **El color nunca es la única pista**: un monto lleva «+» solo cuando entra plata, y el subtítulo
  dice el verbo.
- **El lavado** de un color es `Movi.colores.lavado(color)` (16 % en oscuro, 10 % en claro), nunca un
  `copy(alpha = …)` a mano.

## 5. Tipografía

Los siete estilos de siempre (`cifra`, `titular`, `titulo`, `cuerpo`, `monto`, `apoyo`, `rotulo`) y uno
nuevo: **`cifraDeTarjeta`** (Martian Mono, 24 sp), la cifra de una tarjeta de cuenta o un widget. La
cifra héroe es `cifra` (Martian Mono, 42 sp). Ningún `fontSize` suelto (lo vigila
`LosTamanosSueltosSoloBajanTest`).

**Vuelta de diseño 3: `portada`** (Space Grotesk bold, 32 sp), el título de una pantalla de una sola
cosa: el logotipo «Movi» de la entrada (era el `fontSize` suelto de `LoginScreen`) y «Te damos la
bienvenida a Movi» en la guía de primer uso. No es para una cifra ni para el título de una pantalla
con contenido (`titular`).

**Ola R1: toda cifra de plata va en Martian Mono.** `monto` —el de una fila, una propuesta, un
widget— pasó de Space Grotesk tabular a Martian Mono a 12,5 sp (la monoespaciada es más ancha: un
punto menos que el cuerpo se lee del mismo tamaño). Así las cifras de la app se ven de la misma familia
en todas partes. Space Grotesk queda para todo lo que no es un monto.

**Vuelta de diseño 21: los pesos con nombre.** Una pantalla no escribe `fontWeight =`: elige un estilo.
Son los de arriba con otro de los pesos que la escala ya usa —Normal, Medium, SemiBold; **ningún peso
nuevo**— y se derivan de su base en el cuerpo de `TextosDeMovi`, así llevan su familia y nunca se
separan de ella en tamaño ni interlineado (lo fija `TipografiaTest`):

| estilo | es | para |
|---|---|---|
| `apoyoMedio` | `apoyo` en Medium | el mini-título: el rótulo de un dato arriba de su valor, el nombre en una fila apretada, el número de un círculo. Es la letra de `RotuloDeCampo` (que además trae su color y su margen) |
| `tituloMedio` | `titulo` en Medium | un nombre que encabeza sin gritar (vuelta 4): una cuenta, una categoría, un tercero arriba de su detalle; el título de `EncabezadoDeHoja` |
| `tituloRegular` | `titulo` en Normal | lo que acompaña a un título en su renglón: el conteo de una `Seccion`, «· 3 movimientos» junto a un día plegado |
| `cuerpoFuerte` | `cuerpo` en SemiBold | lo elegido y lo que se toca: la opción elegida de un selector, el texto de un `Boton` |
| `cuerpoRegular` | `cuerpo` en Normal | un párrafo (la respuesta de Movi AI, una explicación larga) y la opción no elegida donde así iba |
| `pestana` / `pestanaActiva` | `rotulo` en frase, casi sin espaciado, Normal / SemiBold | el rótulo de una pestaña (barra del teléfono, riel compacto) |
| `enfasis` | `SpanStyle` en SemiBold | el énfasis dentro de una frase (`withStyle(Movi.textos.enfasis)`): la **negrita** de Movi AI, lo que se resalta de un aviso del banco |

Lo elegido se escribe con el estilo, no con el peso: `style = if (elegido) Movi.textos.cuerpoFuerte else
Movi.textos.cuerpo`. Un `Bold` sobre la letra de la interfaz se dibujaba igual que SemiBold (Space
Grotesk viene en 400, 500 y 600): los pocos que había pasaron a su estilo sin cambiar nada.

## 6. Los componentes (`ui/components/ds/`)

Cada uno con pocos parámetros; las variantes, en un `enum`.

**`Superficie`** — el contenedor de toda tarjeta. Reemplazó a `MinCard`, borrada en la vuelta de diseño 21.
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
el texto y el final (vuelta de diseño 15). 56 / 64 de alto mínimo, 16 a los
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
de una tarjeta; su toque igual llega a 48). Ícono opcional y `cargando`.
```kotlin
Boton("Guardar", onClick = ::guardar, anchoCompleto = true, cargando = guardando)
Boton("Borrar", onClick = ::borrar, variante = VarianteDeBoton.Peligro)
```

**`Fantasma` es el botón de texto**: solo el texto en marca, sin fondo, **tampoco apagado** (vuelta de
diseño 5: los demás se apagan sobre la capa 2; uno de texto que se rellena de gris parece otro botón,
ver `coloresDelBotonApagado`). Va debajo o al lado de la acción con peso, nunca a todo el ancho: «Saltar
este paso» en la guía de primer uso es `Fantasma`, tamaño `S`, centrado debajo de «Atrás · Siguiente».
```kotlin
Boton("Saltar este paso", onClick = ::saltar, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S,
    modifier = Modifier.align(Alignment.CenterHorizontally))
```

**`Pildora`** y **`PildoraDeFiltro`** — estado (`Neutra`, `Info`, `Aviso`, `Peligro`, `Exito`, `Marca`)
y filtro que se elige.
```kotlin
Pildora("Vence en 2 días", TonoDePildora.Aviso)
PildoraDeFiltro("Gastos", seleccionada = filtro == Gastos, onClick = { filtro = Gastos })
// Vuelta 3: con `inicio`, lo que no es un ícono (el avatar de la marca de un banco, 20 dp).
PildoraDeFiltro(marca.nombre, elegido, onClick, inicio = { CirculoDeMarca(marca, tamano = TamanoDeAvatar.Linea) })
```

**`PildoraDeAccion`** (vuelta de diseño 12) — una píldora que **hace algo** al tocarla: se ve como un
`PildoraDeFiltro` sin elegir (capa 2, `cuerpo`, ícono en la marca) pero es un botón, no dice «elegido».
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
círculo de 56 y **rótulo de un renglón** (vuelta de diseño 2: «Gasto», «Comprobante», «Preguntar»; si uno
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
deja ~90 dp y Martian Mono pide ~9,5 dp por carácter con la letra de la app (0,7 em a 12,5 sp × 1,12),
así que «$15.600.000» no entra. En otra moneda no se compacta. Una fila, una tarjeta, dos cifras lado
a lado o el patrimonio de Hoy van enteras: «$1,5M» al lado de un héroe entero se leía como otro número.
```kotlin
Cifra(resumen.entradas, tono = TonoDeCifra.Ingreso, forma = FormaDeCifra.Compacta)   // +$15,6M
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
| Espacios | «Nuevo espacio» (con uno o más; sin ninguno, lo ofrece el vacío) |
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

**`AvatarDePersona`** (R3) — una persona: su inicial en un círculo de `Medida.avatar`, en el lavado de
su tono (`TonoDeIcono`). Los miembros de un espacio y quien pagó un gasto.
```kotlin
AvatarDePersona("Caro", tono = TonoDeIcono.Info)
```

**`Campo`**, **`CampoDeMonto`**, **`GrupoDeCampos`**, **`RotuloDeCampo`**, **`AyudaDeCampo`** y
**`PieDeHoja`** (vuelta de diseño 6, `ds/Campo.kt`) — los formularios. Ver §15.

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
| `ContrasteDeLosTokensTest` | el contraste de cada par, con las capas nuevas |
| `LosTamanosSueltosSoloBajanTest` | los `fontSize` sueltos |
| `LosCamposSonDelSistemaTest` | ningún `BasicTextField` / `TextField` / `OutlinedTextField` en `ui/` fuera de `ui/components/` y de seis excepciones con nombre (§15) |
| `LosRotulosNoGritanTest` | ninguna función `…SectionLabel` en `ui/`, y ningún `Seccion` / `GrupoDeCampos` / `RotuloDeCampo` / `rotulo =` / `label =` con un texto entero en mayúsculas |
| `LasGuiasNoParecenDatosTest` | ningún `guia =` / `placeholder =` con un literal puramente numérico en `ui/` («20», «29,6», «$ 0»): va `ejemplo =`, y sin su propio «Ej:» (§15) |
| `LosCamposDelSistemaTest` (Robolectric) | el rótulo en frase, la guía de un monto en la letra de cuerpo y el valor en la de cifras, el error en `sale`, «¿Qué es esto?» abre y cierra, la caja llega a 48, y nada se recorta a 360 dp con la letra a ×1,3 (también en las hojas de crédito y tarjeta) |
| `SistemaDeDisenoTest` (Robolectric) | cada componente se compone en los dos temas; la fila mide 56/64; el botón llega a 48 de toque; la cifra se oculta; la tarjeta de cuenta mide 128 con marca y sin ella; dos widgets de un par miden lo mismo; la casilla que se toca sola mide 48 y dice su estado |
| `CifraConRotuloTest` (Robolectric, motor de texto real) | el rótulo como se escribe, la cifra del sistema con su signo y tono, nada en claro con los saldos ocultos, «—» sin dato, y ninguna cifra recortada ni partida a 360 dp con la letra ×1,12 (tres compactas, dos enteras de diez dígitos, una en renglón) |
| `LasCifrasDelSistemaSeOcultanTest` (Robolectric) | con «Ocultar los saldos», ni un «$» seguido de un dígito en el flujo libre de Plan, Presupuestos, la hoja de anular, los candidatos a pago de tarjeta y, en Hoy, «Tu patrimonio», «Para revisar» (con la evidencia abierta) y «Tu día más justo» |
| `LoQueNoEsDelSistemaSoloBajaTest` (`core/src/jvmTest/.../quality/`, vuelta de diseño 18) | en `ui/` fuera de `ds/`, seis cosas que solo pueden bajar de su línea base (`lineas-base.txt`): `Button(`/`OutlinedButton(`/`TextButton(` de Material, `Color.White`/`Color.Black` (salvo el velo de `MarcoDeHoja`), `Hairline(`, `MinCard(`, un `Text(` cuyo primer argumento es `formatCOP(`/`formatMoney(` (aproximación por regex) y `fontWeight =` (§24). **Desde la vuelta 21 las cinco primeras se exigen en 0**, aunque alguien suba su línea base (§27) |
| `UnaSolaCifraHeroePorPantallaTest` (Robolectric) | a lo sumo una `Cifra` `Heroe` en Hoy, Plan (Pagos y Presupuestos), Patrimonio, Créditos, Movimientos y Espacios; exactamente una donde la pantalla tiene su protagonista (Hoy, Patrimonio, Créditos) |

## 9. Migrar una pantalla

1. Mirarla antes (web a 390 dp, los dos temas).
2. Cambiar tarjetas por `Superficie`, filas por `Fila`, títulos por `Seccion`, botones por `Boton`,
   montos por `Cifra`; los números que queden, por `Espacio` / `Medida` / `Radio` / `Icono`.
3. `python3 scripts/auditoria-de-diseno.py --resumen` y bajar `docs/diseno/lineas-base.txt` al número
   nuevo (la prueba lo exige).
4. Mirarla después. Casi cada pantalla migrada tuvo un defecto visible.

## 10. El Inicio (ola R1)

El Inicio se arma desde la definición del server (`DashboardDefaults`, generación 9) con las
preferencias del dueño («Editar inicio»). Cada bloque contesta una sola pregunta, con los componentes de
arriba:

| bloque | tipo SDUI | componente | lleva a |
|---|---|---|---|
| encabezado | — (chrome) | `Encabezado`: avatar, «Hola, Juan» con el período, lupa y campana | Ajustes, Buscar |
| héroe | `HERO_BALANCE` | `Cifra.Heroe` «Te quedan», el ojo, «para N días · $X por día» (lo que queda entre los días que faltan), el veredicto | Tus períodos |
| acciones | `ACCIONES` | `CuadriculaDeAcciones` 4 × 2 | Agregar, papeles, Por revisar, Movi AI; «Repartir» abre la hoja «Pagó otro» del espacio (vuelta 7: elige el espacio si hay varios, lo crea si no hay) |
| Movi propone hoy | `PROPUESTAS` | `TarjetaDePropuesta`, una por tipo, tope 4; sin nada, no se pinta (vuelta 11) | su acción; «Ver todo» → Por revisar |
| tus cuentas | `CUENTAS` | `TarjetaDeCuenta` en carrusel (en grilla fuera del teléfono) | el detalle de la cuenta; «Ver todo» → Patrimonio |
| widgets | `CHECKLIST_DEL_PERIODO`, `GASTO_POR_CATEGORIA` | `ParDeWidgets` | Plan, En qué se va |

`DISPONIBLE_DEL_PERIODO` y `PATRIMONIO` ya no vienen por defecto (repetían Plan y Patrimonio): son
`SECCIONES_OPCIONALES` que el dueño prende en «Editar inicio». La barra Entró/Salió y el desglose de
«Tu plata» salieron del héroe. ALERTS y el BANNER siguen al final de la definición solo para los APK
viejos; este cliente no los pinta.

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
| Tus recordatorios no te van a llegar | `TarjetaDePropuesta`, al final | era un cartel en el medio de la lista |

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
  - **Hoy no repite un pago**: el widget no lista como fila el pago que ya está en «Movi propone hoy»
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

## 32. Vuelta 31: Créditos y Espacios se consultan, el cuadre dice una sola cosa y Ajustes se ordena

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
