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
| `Espacio.xs` | 8 | entre hermanos chicos: chips, píldoras, botones de una fila |
| `Espacio.s` | 12 | dentro de una sección; entre el inicio, el texto y el final de una fila |
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
no lleva borde. `MinCardVariant.High` (que era «tarjeta con borde») ahora es la capa 2.

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

## 6. Los componentes (`ui/components/ds/`)

Cada uno con pocos parámetros; las variantes, en un `enum`.

**`Superficie`** — el contenedor de toda tarjeta. Reemplaza a `MinCard` (que delega acá).
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
`titulo`, `subtitulo?`, `final` (`Cifra`, `Pildora` o `Chevron`). 56 / 64 de alto mínimo, 16 a los
lados, toque de borde a borde. El título va en hasta dos renglones antes del «…» (la fila crece) y el
subtítulo en uno —o en dos con `lineasDelSubtitulo = 2`, solo donde el subtítulo es la razón de la fila
y no se puede acortar («Hace más de un mes que no cuadras Bancolombia Ahorros», vuelta de diseño 2)—;
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
| `LosCamposDelSistemaTest` (Robolectric) | el rótulo en frase, la guía de un monto en la letra de cuerpo y el valor en la de cifras, el error en `sale`, «¿Qué es esto?» abre y cierra, la caja llega a 48, y nada se recorta a 360 dp con la letra a ×1,3 (también en las hojas de crédito y tarjeta) |
| `SistemaDeDisenoTest` (Robolectric) | cada componente se compone en los dos temas; la fila mide 56/64; el botón llega a 48 de toque; la cifra se oculta; la tarjeta de cuenta mide 128 con marca y sin ella; dos widgets de un par miden lo mismo; la casilla que se toca sola mide 48 y dice su estado |
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
   del campo ni la moneda.
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
  (`AtrasCierraEstaHoja`) encima de la hoja, y en la hoja desde abajo Escape llega por `onTecla`. Vale
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
  (`AvatarDeLaCuenta`), el nombre, el saldo con `Cifra` en su moneda y la elegida con la verificación en
  la marca (y `selected` para el lector de pantalla), sobre una `Superficie` de capa 2. Una deuda dice
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
  con `Cifra` a la derecha y la `Casilla` del sistema, pegados con `Espacio.xxs` como un ícono y su
  rótulo (vuelta 16: con `Espacio.s` el subtítulo quedaba 6 dp corto a 360). Se marca la fila entera. Van de borde a borde
  de la tarjeta del vacío (`VacioQueEnsena(contenidoDeBordeABorde = true)`: con el relleno de la
  tarjeta más el de la fila no le quedaba lugar al nombre), separadas con `Separador(ConInicio)`.
  «Crear estos N» y «Crear uno a mano» quedan igual.
- **Movimientos sin «Flujo del día»** (§11).

Lo vigilan `UnaSolaCifraHeroePorPantallaTest` y `PresupuestosConJerarquiaTest` (a 360 dp: la cifra
`Grande` del resumen, una sola señal roja sin píldora, el tope a la derecha y la casilla, nada
recortado).

## 21. Vuelta de diseño 16: crear una cuenta es igual en todos lados

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
