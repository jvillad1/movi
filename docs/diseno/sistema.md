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
```kotlin
Cifra(-27_500)
Cifra(4_500_000, tono = TonoDeCifra.Ingreso)        // +$4.500.000
Cifra(500_000, tono = TonoDeCifra.EntreCuentas)     // $500.000, sin signo
Cifra(teQuedan, VarianteDeCifra.Heroe)
```

**`TarjetaDeCuenta`** — la tarjeta de una cuenta del carrusel «Tus cuentas» (ola R1): el color y la
marca de su banco (`MarcasConocidas`; la marca va invertida en un círculo), el nombre, el tipo y el
saldo en `Cifra.Grande`. El nombre va en hasta dos renglones (nunca «Bancolombia Ah…») y el tipo baja
junto al rótulo del saldo. Alto mínimo `Medida.altoDeTarjetaDeCuenta` (crece con la letra agrandada),
radio `Radio.xl`, sin borde. Sin marca,
la capa 2 y el ícono del tipo. En un carrusel ocupa `FRACCION_DE_TARJETA_DE_CUENTA` del ancho, para que
la siguiente asome.
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
con `ParDeWidgets`; uno solo va a todo el ancho.
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
```kotlin
Encabezado("Movimientos", acciones = { BotonDeIcono(Icons.Rounded.Search, "Buscar", ::buscar) })
Encabezado("Patrimonio", accion = AccionDeEncabezado("Nueva cuenta", Icons.Rounded.Add, ::crear))
```

**`Vacio`** (es `VacioQueEnsena`), **`EsqueletoDeFila`** (mide lo mismo que la `Fila`), **`Separador`**
(`hilo`, con sangría `Ninguna`, `Margen` o `ConInicio`) y **`BarraDeProgreso`** (pista en capa 3, tono
del relleno; con `marca` opcional, una raya vertical en esa fracción: «la mitad» de «Quién puso qué»).

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
| `SistemaDeDisenoTest` (Robolectric) | cada componente se compone en los dos temas; la fila mide 56/64; el botón llega a 48 de toque; la cifra se oculta; la tarjeta de cuenta mide 128 con marca y sin ella; dos widgets de un par miden lo mismo |

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
| acciones | `ACCIONES` | `CuadriculaDeAcciones` 4 × 2 | Agregar, papeles, Por revisar, Movi AI |
| Movi propone hoy | `PROPUESTAS` | `TarjetaDePropuesta`, una por tipo, tope 4 | su acción; «Ver todo» → Por revisar |
| tus cuentas | `CUENTAS` | `TarjetaDeCuenta` en carrusel | el detalle de la cuenta; «Ver todo» → Patrimonio |
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
  fecha, la cifra y el chevron se miden primero; si no cabe todo, se cae primero el rótulo «Flujo del
  día» y después se corta el «· N movimientos».
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

**Los meses vacíos del principio no son tarjetas** (vuelta de diseño 5, `separarLosVaciosDelPrincipio`):
los períodos sin movimientos de flujo que quedan antes del primero con movimientos se juntan al final de
Tus períodos en una sola `Fila` apagada y sin toque («Antes de agosto 2026 no hay movimientos»). Un
período vacío **entre** dos con movimientos sigue siendo tarjeta («Sin movimientos de flujo»): ahí sí
dice algo. Ningún período desaparece ni cambia sus cifras; solo cómo se pintan.

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
excepción ya no tiene campo): la caja de Buscar, el compositor de Movi AI, Agregar (su teclado
numérico y la nota), el Editor de pantallas (admin), la guía de primer uso y la fila «Comercio» de
Reconciliar, que se edita en su lugar dentro de una `Fila`.

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
