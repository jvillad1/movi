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

`Movi.espacios.*` sigue existiendo (lo nombra media app) y ahora sale de acá. **Con la R0 cambiaron en
toda la app a la vez:** el margen de pantalla de 20 a 16 y la separación entre secciones de 32 a 24.

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
lados, toque de borde a borde. Va directo sobre el fondo o en una `Superficie` con relleno `Lista`.
```kotlin
Fila(
    titulo = "Rappi · Las Doce", subtitulo = "Restaurantes · Nu",
    inicio = { AvatarDelComercio(evento) }, final = { Cifra(-27_500) },
    onClick = { abrir(evento) },
)
```

**`Boton`** — `Principal`, `Secundario`, `Fantasma`, `Peligro`; tamaño `M` (48) o `S` (36, solo dentro
de una tarjeta; su toque igual llega a 48). Ícono opcional y `cargando`.
```kotlin
Boton("Guardar", onClick = ::guardar, anchoCompleto = true, cargando = guardando)
Boton("Borrar", onClick = ::borrar, variante = VarianteDeBoton.Peligro)
```

**`Pildora`** y **`PildoraDeFiltro`** — estado (`Neutra`, `Info`, `Aviso`, `Peligro`, `Exito`, `Marca`)
y filtro que se elige.
```kotlin
Pildora("Vence en 2 días", TonoDePildora.Aviso)
PildoraDeFiltro("Gastos", seleccionada = filtro == Gastos, onClick = { filtro = Gastos })
```

**`AccionRapida`** y **`CuadriculaDeAcciones`** — la cuadrícula del camino 3: cuatro columnas, ícono en
círculo de 56 y rótulo de dos renglones, una destacada como mucho, insignia opcional.
```kotlin
CuadriculaDeAcciones(listOf(
    Accion("Anotar gasto", Icons.Rounded.Edit, ::anotar, destacada = true),
    Accion("Por revisar", Icons.Rounded.Inbox, ::revisar, insignia = 3),
))
```

**`TarjetaDePropuesta`** — el feed del camino 5 y la forma de **toda** propuesta de Movi: avisos por
confirmar, pagos por vencer, anomalías, cuadres, saldos del hogar. Movi propone, el dueño decide.
```kotlin
TarjetaDePropuesta(
    icono = Icons.Rounded.Inbox, titulo = "Llegaron 3 avisos del banco",
    cifra = 186_400, cuerpo = "por confirmar",
    accion = "Confirmar los 3", onAccion = ::confirmar,
    accionSecundaria = "Ver", onAccionSecundaria = ::ver,
)
```

**`Cifra`** — un monto: `Heroe` (una por pantalla), `Grande`, `Fila`, `Chica`; tono `Neutro`, `Ingreso`,
`Alerta`, `Apagado`; signo y «Ocultar los saldos» incluidos.
```kotlin
Cifra(-27_500)
Cifra(4_500_000, tono = TonoDeCifra.Ingreso)        // +$4.500.000
Cifra(teQuedan, VarianteDeCifra.Heroe)
```

**`Encabezado`** — el de toda pantalla: 56 de alto, 16 de margen, título, flecha o avatar, acciones
(`BotonDeIcono`). Sin línea debajo. `MinScreenHeader` delega acá.
```kotlin
Encabezado("Movimientos", acciones = { BotonDeIcono(Icons.Rounded.Search, "Buscar", ::buscar) })
```

**`Vacio`** (es `VacioQueEnsena`), **`EsqueletoDeFila`** (mide lo mismo que la `Fila`), **`Separador`**
(`hilo`, con sangría `Ninguna`, `Margen` o `ConInicio`) y **`BarraDeProgreso`** (pista en capa 3, tono
del relleno; con `marca` opcional, una raya vertical en esa fracción: «la mitad» de «Quién puso qué»).

**`AvatarDePersona`** (R3) — una persona: su inicial en un círculo de `Medida.avatar`, en el lavado de
su tono (`TonoDeIcono`). Los miembros de un espacio y quien pagó un gasto.
```kotlin
AvatarDePersona("Caro", tono = TonoDeIcono.Info)
```

## 7. La regla de secciones

1. **Cada pantalla es dueña de una pregunta.** Hoy: «¿cómo voy?». Movimientos: «¿en qué se fue?».
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
| `SistemaDeDisenoTest` (Robolectric) | cada componente se compone en los dos temas; la fila mide 56/64; el botón llega a 48 de toque; la cifra se oculta |

## 9. Migrar una pantalla

1. Mirarla antes (web a 390 dp, los dos temas).
2. Cambiar tarjetas por `Superficie`, filas por `Fila`, títulos por `Seccion`, botones por `Boton`,
   montos por `Cifra`; los números que queden, por `Espacio` / `Medida` / `Radio` / `Icono`.
3. `python3 scripts/auditoria-de-diseno.py --resumen` y bajar `docs/diseno/lineas-base.txt` al número
   nuevo (la prueba lo exige).
4. Mirarla después. Casi cada pantalla migrada tuvo un defecto visible.
