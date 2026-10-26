# Auditoría del diseño de Movi — octubre 2026

Ola R0 del rediseño (26-oct-2026). Antes de tocar una pantalla, contar lo que cada una decide por su
cuenta. **Todos los números son del commit `f1383692` (master antes de la R0)** y salen de
`python3 scripts/auditoria-de-diseno.py` sobre `shared/src/commonMain/.../ui/` (188 archivos, 65.931
líneas, sin comentarios). Las líneas citadas (`archivo:línea`) son de ese commit.

## Resumen

| qué | cuántos | valores distintos |
|---|---:|---:|
| `N.dp` escritos a mano | **2.674** | **76** |
| … de ellos en `padding` | 956 | 29 |
| … en `size`/`height`/`width` | 1.072 | 48 |
| … en separaciones (`spacedBy`, `Spacer`) | 179 | 11 |
| `RoundedCornerShape` con un número | **247** de 317 | 13 radios |
| `fontSize = N.sp` sueltos | 11 | 6 tamaños |
| `TextStyle(` armado en una pantalla | 0 | — |
| `Color(0x…)` fuera de `theme/` | **18** (todos en `mas/MasScreen.kt`) | 12 |
| `copy(alpha = …)` a mano | 59 (44 son el mismo `0.16f`) | 10 |
| rellenos distintos pasados a `MinCard` | **27** | — |
| tarjetas armadas a mano (sin `MinCard`) | 62 | — |
| píldoras armadas a mano | 54 | — |
| tamaños de ícono | 11 (11 a 26 dp) | — |

**Lo que dicen los números.** La app ya tiene escalas (`Movi.espacios`, `Movi.formas`, `Movi.textos`)
y el texto está casi migrado (11 tamaños sueltos de 721 en septiembre). Lo que no tiene es **reglas
de espacio y de componente**: los cuatro `.dp` más usados que no están en ninguna escala son 14 (254),
10 (214), 6 (177) y 18 (154) — 799 usos, casi un tercio —, y son siempre «el de al lado» de un valor
que sí está (12/16, 8/12, 4/8, 16/20). Nadie eligió 14 en vez de 16: se copió de la pantalla de al lado.
Y los tokens que existen se usan menos que el número suelto que los imita: `Movi.formas.normal` (11 dp)
aparece 20 veces; `RoundedCornerShape(12.dp)`, 72.

## Las inconsistencias concretas

### 1. La tarjeta tiene 27 rellenos

`MinCard` es una sola, pero cada llamada le pasa su relleno:

| relleno | llamadas | ejemplos |
|---|---:|---|
| `horizontal = 18, vertical = 2` | 30 | `sms/SMSScreens.kt:1512`, `plan/TableroDeRecurrentes.kt:1012` |
| `18` | 24 | `destinos/DestinosScreen.kt:339`, `sms/SMSScreens.kt:200` |
| `16` | 14 | `periodos/DetalleDePeriodoScreen.kt:535`, `periodos/DetalleDePeriodoScreen.kt:604` |
| `20` (y el default `Movi.espacios.margen`, también 20) | 9 + 11 | `auth/LoginScreen.kt:233`, `periodos/DetalleDePeriodoScreen.kt:648` |
| `Movi.espacios.amplio` (16) | 9 | `plan/CajaDelPeriodo.kt:215`, `plan/PlanScreen.kt:367` |
| `horizontal = 16, vertical = 12` | 7 | `destinos/DestinosScreen.kt:428`, `porrevisar/PorRevisarScreen.kt:614` |
| `horizontal = 18, vertical = 14` | 6 | `destinos/DestinosScreen.kt:688`, `sms/SMSScreens.kt:1251` |
| `22` | 5 | `goals/MetasScreen.kt:100`, `budgets/PresupuestosScreen.kt:564`, `credits/CreditosScreen.kt:258` |
| `14` | 2 | `destinos/DetalleDelDestinoSheet.kt:385`, `sdui/SduiRenderer.kt:468` |
| `32` | 2 | `components/NoSePudoLeer.kt:39`, `components/VacioQueEnsena.kt:63` |
| … 17 combinaciones más | 1–4 cada una | ver A6 |

La misma tarjeta lleva 14, 16, 18, 20 o 22 de relleno según la pantalla.

### 2. Los títulos de sección en cinco estilos

| estilo | dónde |
|---|---|
| Rótulo en mayúsculas, 10,5 sp, espaciado 1,7, sangría de 8 (`MinSectionHeader`) | 70 llamadas en 18 pantallas |
| Mayúsculas en `apoyo` (11,5 sp), espaciado 0,5 | `accounts/AccountsScreen.kt:727` |
| Mayúsculas en `apoyo`, espaciado 0,3 | `sms/SMSScreens.kt:1930` |
| Mayúsculas en `apoyo`, espaciado 0,4 (los días) | `transactions/TransactionsScreen.kt:1623`, `accounts/AccountDetailScreen.kt:424` |
| Literal en mayúsculas en `rotulo` | `destinos/DetalleDelDestinoSheet.kt:169` («ESTE PERÍODO»), `destinos/DetalleDelDestinoSheet.kt:207`, `destinos/DetalleDelDestinoSheet.kt:240` |
| En minúsculas, `cuerpo` Medium | `plan/PlanScreen.kt:420` («Tus períodos») |

### 3. Las filas tocables tienen nueve rellenos verticales y ningún alto mínimo

| relleno vertical | filas | ejemplos |
|---|---:|---|
| 2 | 1 | `extractos/StatementReviewScreen.kt:209` |
| 5 | 1 | `quickadd/QuickAddScreen.kt:1548` |
| 6 | 4 | `transactions/TransactionsScreen.kt:1389`, `buscar/BuscarScreen.kt:262` |
| 8 | 5 | `components/ScopeToggle.kt:38`, `categorias/CategoriasScreen.kt:822` |
| 9 | 2 | `dashboard/DashboardScreen.kt:757`, `papeles/HojaLeyendoElPapel.kt:233` |
| 10 | 6 | `documentos/DocumentosScreen.kt:889`, `sdui/SeccionesDelPeriodo.kt:371` |
| 12 | 5 | `goals/GoalSheet.kt:269`, `transactions/VincularDeudaPicker.kt:85` |
| 13 | 2 | `compartir/CompartirScreen.kt:399`, `components/ListaDeCuentasElegibles.kt:78` |
| 14 | 6 | `components/MinComponents.kt:167` (`CardRow`), `compartir/CompartirScreen.kt:381` |

Ninguna fila declara un alto mínimo (`heightIn(min = …)` aparece dos veces en toda `ui/`, con 32 y 58).
Su alto sale del texto más el relleno: con 6 dp y un renglón, una fila mide ~30 dp, por debajo del
objetivo táctil de 48.

### 4. El botón principal mide 46, 48, 50 o 54

| alto | ejemplos | forma |
|---|---|---|
| 46 | `transactions/CategorySheets.kt:637`, `periodos/DetalleDePeriodoScreen.kt:1084`, `profile/InicioDelPeriodoSheet.kt:195` | píldora |
| 48 | `auth/LoginScreen.kt:290`, `auth/RegisterScreen.kt:132`, `auth/PuertaAlVolver.kt:139` | 12 |
| 50 | `sms/SMSScreens.kt:1768`, `accounts/ApartadoSheet.kt:310`, `accounts/DeleteAccountSheet.kt:191` | 14 o píldora |
| 54 | `goals/GoalSheet.kt:236`, `accounts/CreateAccountSheet.kt:275`, `quickadd/QuickAddScreen.kt:1795` | píldora |

29 botones con alto fijo entre 46 y 54. Y el botón «lleno» de `NewItemButton` (`components/NewItemButton.kt`)
no tiene alto: sale de 14 de relleno más el texto, ~46.

### 5. El mismo radio dicho de cinco maneras

El token de «lo habitual» era 11 (`Movi.formas.normal`, 20 usos); a mano se escribe 12 (72), 10 (32),
14 (26) u 8 (21) para lo mismo. Las hojas llevan 28 a mano (`components/MarcoDeHoja.kt:515`,
`components/MarcoDeHoja.kt:658`) y las tarjetas 16. Hay 67 `RoundedCornerShape(999.dp)` y 30
`Movi.formas.pleno` para la misma píldora.

### 6. El lavado de la marca, escrito 44 veces

`Movi.colores.marca.copy(alpha = 0.16f)` aparece a mano en la barra de pestañas, el rail, los vacíos
y los botones de las hojas; al lado viven 0.10, 0.12, 0.14 y 0.28 para lo mismo.

### 7. Colores pensados solo para el tema oscuro

Los 18 `Color(0x…)` de `mas/MasScreen.kt:56-77` (nueve pares ícono/fondo) son pasteles claros con
alfa: en el tema claro, un ícono `#B3C8FF` sobre blanco no llega a 2:1.

### 8. Los íconos en once tamaños

11, 12, 13, 14, 15, 16, 18, 20, 22, 24 y 26 dp. La barra del teléfono usa 22
(`components/MinBottomNav.kt:176`) y el rail 20 (`components/MinNavRail.kt:256`) para el mismo
destino; el chevron de una fila, 18 (`components/MinComponents.kt:261`).

### 9. El encabezado: alto variable y una flecha de 44

`components/MinScreenHeader.kt:84-130`: el alto sale del contenido (8 + 12 de relleno más lo más alto
de la fila), así que una pantalla con flecha mide distinto que una con avatar. La flecha es 22 dp con
11 de relleno: 44 de área táctil, debajo de 48.

### 10. El margen de la pantalla: 16, 20, 14 y 18

`Movi.espacios.margen` (20) se usa 30 veces; a mano, `padding(horizontal = 16.dp)` aparece 103 veces,
`14.dp` 46, `20.dp` 16 y `18.dp` 5. El camino 1 usa 16.

### 11. Tarjetas y píldoras a mano

62 tarjetas se arman con `clip(RoundedCornerShape…)` + `background(tarjeta)` sin `MinCard`, y 54
píldoras con forma de píldora y un fondo propio. Cada una elige su relleno y su radio.

## Lo que se hizo con esto (ola R0)

- Las escalas y reglas en `theme/Escalas.kt` (`Espacio`, `Radio`, `Icono`, `Medida`) y las capas en
  `Tokens.kt`. Ver `docs/diseno/sistema.md`.
- Los componentes en `ui/components/ds/`. `MinCard`, `MinSectionHeader` y `MinScreenHeader` delegan
  ahí; los puntos 2 (en las 70 llamadas de `MinSectionHeader`), 6 (en la barra y el rail), 7 y 9
  quedaron resueltos.
- Las guardas: `LosValoresSueltosSoloBajanTest` (línea base en `docs/diseno/lineas-base.txt`) y
  `ElSistemaNoTieneValoresSueltosTest`. El resto de los puntos se resuelve pantalla por pantalla en
  las olas siguientes, y cada una baja la línea base.

---

# Anexo: la salida del script

_Generado con `python3 scripts/auditoria-de-diseno.py` sobre 188 archivos y 65931 líneas de `ui/` (sin `ui/components/ds/`, sin comentarios)._

## A1. Los `.dp` sueltos

**2674** literales `N.dp` con **76 valores distintos**.

### Por contexto

| contexto | cuántos | valores distintos | los 8 más usados |
|---|---:|---:|---|
| tamaño | 1072 | 48 | 8 (180), 12 (126), 14 (102), 6 (95), 20 (85), 10 (83), 16 (75), 18 (60) |
| padding | 956 | 29 | 16 (187), 14 (119), 12 (94), 18 (93), 8 (85), 4 (78), 10 (59), 2 (57) |
| radio | 249 | 13 | 12 (72), 999 (67), 10 (32), 14 (26), 8 (21), 16 (9), 6 (7), 1 (6) |
| separación | 179 | 11 | 8 (57), 10 (39), 12 (29), 6 (18), 4 (14), 16 (5), 14 (5), 3 (4) |
| otro | 119 | 56 | 2 (9), 0 (8), 16 (8), 6 (6), 1 (6), 80 (5), 96 (4), 48 (3) |
| borde | 98 | 4 | 1 (87), 2 (8), 1.5 (2), 2.4 (1) |
| elevación | 1 | 1 | 6 (1) |

### Histograma por valor

| dp | cuántos | | dp | cuántos | | dp | cuántos |
|---:|---:|---|---:|---:|---|---:|---:|
| 0 | 13 | | 28 | 9 | | 120 | 2 |
| 1 | 108 | | 32 | 14 | | 150 | 1 |
| 1.5 | 3 | | 34 | 1 | | 168 | 1 |
| 2 | 120 | | 36 | 8 | | 170 | 1 |
| 2.4 | 1 | | 38 | 1 | | 180 | 1 |
| 2.5 | 1 | | 40 | 11 | | 216 | 1 |
| 3 | 28 | | 44 | 9 | | 260 | 2 |
| 3.5 | 2 | | 46 | 12 | | 280 | 1 |
| 4 | 147 | | 48 | 10 | | 285 | 1 |
| 5 | 3 | | 50 | 10 | | 290 | 1 |
| 6 | 177 | | 52 | 4 | | 320 | 2 |
| 7 | 8 | | 54 | 9 | | 360 | 3 |
| 8 | 344 | | 56 | 2 | | 380 | 1 |
| 9 | 3 | | 58 | 1 | | 400 | 2 |
| 10 | 214 | | 60 | 3 | | 420 | 6 |
| 11 | 6 | | 64 | 6 | | 460 | 1 |
| 12 | 324 | | 72 | 5 | | 480 | 1 |
| 13 | 7 | | 76 | 1 | | 560 | 1 |
| 14 | 254 | | 80 | 29 | | 600 | 2 |
| 15 | 21 | | 84 | 2 | | 680 | 2 |
| 16 | 284 | | 88 | 2 | | 720 | 1 |
| 18 | 154 | | 96 | 4 | | 840 | 1 |
| 20 | 129 | | 100 | 1 | | 900 | 1 |
| 22 | 24 | | 104 | 1 | | 999 | 67 |
| 24 | 36 | | 110 | 1 | |  |  |
| 26 | 3 | | 112 | 1 | |  |  |

### Los archivos con más `.dp` sueltos

| archivo | `.dp` |
|---|---:|
| `sms/SMSScreens.kt` | 146 |
| `transactions/CategorySheets.kt` | 135 |
| `credits/CreditosScreen.kt` | 125 |
| `categorias/CategoriasScreen.kt` | 91 |
| `transactions/TransactionsScreen.kt` | 89 |
| `budgets/PresupuestosScreen.kt` | 82 |
| `documentos/DocumentosScreen.kt` | 79 |
| `credits/CreditTermsSheet.kt` | 78 |
| `recurrentes/CreateRecurringRuleSheet.kt` | 72 |
| `accounts/AccountsScreen.kt` | 72 |
| `extractos/StatementReviewScreen.kt` | 66 |
| `sdui/editor/ScreenEditorScreen.kt` | 63 |
| `profile/PerfilScreen.kt` | 57 |
| `plan/TableroDeRecurrentes.kt` | 55 |
| `quickadd/QuickAddScreen.kt` | 55 |

## A2. Los radios (`RoundedCornerShape`)

**317** llamadas; **247** con un número escrito a mano.

| radio | cuántos |
|---|---:|
| `12 dp (literal)` | 72 |
| `999 dp (literal)` | 67 |
| `10 dp (literal)` | 32 |
| `Movi.formas.pleno` | 30 |
| `14 dp (literal)` | 26 |
| `8 dp (literal)` | 21 |
| `Movi.formas.normal` | 20 |
| `Movi.formas.amplia` | 14 |
| `16 dp (literal)` | 9 |
| `6 dp (literal)` | 7 |
| `1 dp (literal)` | 6 |
| `Movi.formas.minima` | 5 |
| `4 dp (literal)` | 3 |
| `9 dp (literal)` | 1 |
| `otro: topStart = 28.dp, topEnd = 28.dp` | 1 |
| `28 dp (literal)` | 1 |
| `2 dp (literal)` | 1 |
| `24 dp (literal)` | 1 |

Los literales, dónde:

- **12 dp (literal)** (72): `goals/GoalSheet.kt:272`, `goals/GoalSheet.kt:274`, `auth/LoginScreen.kt:291`, `auth/LoginScreen.kt:419`, `auth/PuertaAlVolver.kt:140`, `auth/RegisterScreen.kt:133` y 66 más
- **999 dp (literal)** (67): `periodos/DetalleDePeriodoScreen.kt:1086`, `destinos/DestinoSheet.kt:499`, `destinos/Pastillas.kt:39`, `goals/GoalSheet.kt:237`, `sms/AnotarloIgualEnReconciliar.kt:100`, `sms/AnotarloIgualEnReconciliar.kt:102` y 61 más
- **10 dp (literal)** (32): `destinos/DestinoSheet.kt:476`, `plan/TableroDeRecurrentes.kt:1552`, `documentos/DocumentosScreen.kt:462`, `documentos/DocumentosScreen.kt:473`, `documentos/DocumentosScreen.kt:569`, `documentos/DocumentosScreen.kt:892` y 26 más
- **14 dp (literal)** (26): `sms/SMSScreens.kt:1769`, `sms/SMSScreens.kt:1770`, `sms/SMSScreens.kt:1795`, `documentos/DocumentosScreen.kt:923`, `extractos/RevisionDelExtractoDeCredito.kt:158`, `extractos/RevisionDelExtractoDeCredito.kt:161` y 20 más
- **8 dp (literal)** (21): `auth/LoginScreen.kt:346`, `documentos/DocumentosScreen.kt:629`, `extractos/StatementReviewScreen.kt:457`, `extractos/StatementReviewScreen.kt:487`, `extractos/StatementReviewScreen.kt:633`, `extractos/StatementReviewScreen.kt:652` y 15 más
- **16 dp (literal)** (9): `recurrentes/RecurringOfferBar.kt:51`, `recurrentes/RecurringOfferBar.kt:53`, `extractos/ExtractosScreen.kt:157`, `extractos/ExtractosScreen.kt:159`, `components/ConfirmarAntesDeBorrar.kt:47`, `components/MomentoDeCelebracion.kt:89` y 3 más
- **6 dp (literal)** (7): `extractos/ExtractosScreen.kt:204`, `extractos/StatementReviewScreen.kt:720`, `extractos/StatementReviewScreen.kt:722`, `components/CasillaDeSeleccion.kt:32`, `components/CasillaDeSeleccion.kt:34`, `credits/CreditTermsSheet.kt:1146` y 1 más
- **1 dp (literal)** (6): `periodos/DetalleDePeriodoScreen.kt:912`, `periodos/DetalleDePeriodoScreen.kt:919`, `budgets/PresupuestosScreen.kt:894`, `budgets/PresupuestosScreen.kt:901`, `credits/CreditosScreen.kt:679`, `credits/CreditosScreen.kt:686`
- **4 dp (literal)** (3): `sms/RepartirEnReconciliar.kt:255`, `sms/RepartirEnReconciliar.kt:258`, `extractos/StatementReviewScreen.kt:228`
- **9 dp (literal)** (1): `recurrentes/CreateRecurringRuleSheet.kt:1354`
- **28 dp (literal)** (1): `components/MarcoDeHoja.kt:658`
- **2 dp (literal)** (1): `components/SheetHandle.kt:51`
- **24 dp (literal)** (1): `ai/AIChatScreen.kt:305`

## A3. Tamaños de letra sueltos

- `fontSize = N.sp`: **11** (10 sp × 1, 20 sp × 4, 22 sp × 3, 28 sp × 1, 32 sp × 1, 48 sp × 1)
  - `goals/MetasScreen.kt:142`
  - `sms/SMSScreens.kt:1926`
  - `auth/LoginScreen.kt:193`
  - `plan/TableroDeRecurrentes.kt:1090`
  - `components/MinNavRail.kt:62`
  - `profile/PerfilScreen.kt:164`
  - `ai/AIChatScreen.kt:358`
  - `budgets/PresupuestosScreen.kt:756`
  - `budgets/PresupuestosScreen.kt:1202`
  - `budgets/PresupuestosScreen.kt:1240`
  - `quickadd/QuickAddScreen.kt:1766`
- `TextStyle(` armado en una pantalla: **0**

## A4. Colores literales fuera de `theme/`

**18** `Color(0x…)`:

- `mas/MasScreen.kt:56` → `#FFB3C8FF`
- `mas/MasScreen.kt:56` → `#24B3C8FF`
- `mas/MasScreen.kt:58` → `#FF7DDDB0`
- `mas/MasScreen.kt:58` → `#1A7DDDB0`
- `mas/MasScreen.kt:61` → `#FFB3C8FF`
- `mas/MasScreen.kt:61` → `#1AB3C8FF`
- `mas/MasScreen.kt:64` → `#FFFFCC80`
- `mas/MasScreen.kt:64` → `#24FFCC80`
- `mas/MasScreen.kt:67` → `#FF7DDDB0`
- `mas/MasScreen.kt:67` → `#1A7DDDB0`
- `mas/MasScreen.kt:68` → `#FFE8BBF8`
- `mas/MasScreen.kt:68` → `#24E8BBF8`
- `mas/MasScreen.kt:70` → `#FFE8BBF8`
- `mas/MasScreen.kt:70` → `#24E8BBF8`
- `mas/MasScreen.kt:74` → `#FF81D4FA`
- `mas/MasScreen.kt:74` → `#2481D4FA`
- `mas/MasScreen.kt:77` → `#FFFFD479`
- `mas/MasScreen.kt:77` → `#24FFD479`

## A5. Las variantes de cada pieza, y dónde se usan

### Tarjeta

| componente | llamadas | pantallas |
|---|---:|---|
| `MinCard` | 148 | sdui (17), sms (16), periodos (13), profile (13), accounts (13), credits (10), plan (9), destinos (8), extractos (5), porrevisar (5), dashboard (4), transactions (4), cuadre (4), budgets (4), analisis (4), goals (3), auth (3), recurrentes (3), compartir (3), components (3), quickadd (2), documentos (1), papeles (1) |
| `VacioQueEnsena` | 15 | periodos (3), budgets (2), plan (1), documentos (1), porrevisar (1), compartir (1), sdui (1), transactions (1), cuadre (1), accounts (1), ai (1), credits (1) |
| `PanelDeTablero` | 4 | plan (1), accounts (1), analisis (1), credits (1) |

### Fila

| componente | llamadas | pantallas |
|---|---:|---|
| `CardRow` | 30 | profile (12), accounts (6), quickadd (6), sdui (5), notifications (1) |
| `FilaDeCategoria` | 3 | periodos (1), sdui (1), categorias (1) |

### Botón

| componente | llamadas | pantallas |
|---|---:|---|
| `NewItemButton` | 13 | budgets (3), destinos (2), goals (2), credits (2), plan (1), documentos (1), components (1), accounts (1) |
| `Button` | 2 | extractos (2) |
| `OutlinedButton` | 2 | extractos (2) |
| `TextButton` | 2 | dashboard (2) |

### Chip

| componente | llamadas | pantallas |
|---|---:|---|
| `SelectorSegmentado` | 5 | analisis (2), plan (1), quickadd (1), credits (1) |

### Encabezado de sección

| componente | llamadas | pantallas |
|---|---:|---|
| `MinSectionHeader` | 70 | sdui (13), profile (11), credits (10), sms (7), plan (6), periodos (5), porrevisar (4), documentos (2), accounts (2), buscar (2), destinos (1), goals (1), recurrentes (1), extractos (1), dashboard (1), compartir (1), transactions (1), budgets (1) |

### Encabezado de pantalla

| componente | llamadas | pantallas |
|---|---:|---|
| `MinScreenHeader` | 34 | extractos (5), dashboard (3), periodos (2), sms (2), profile (2), accounts (2), ai (2), credits (2), mas (1), destinos (1), goals (1), plan (1), documentos (1), porrevisar (1), compartir (1), sdui (1), transactions (1), cuadre (1), categorias (1), budgets (1), analisis (1), buscar (1) |

Además, armados a mano (sin componente):

- **Tarjetas** (`clip(RoundedCornerShape…)` + `background(tarjeta)` sin `MinCard`): **62**
- **Píldoras/chips** (forma `pleno` + fondo propio): **54**
- **Botones de marca** (`Row` tocable con fondo `marca`): **2**
- **Rótulos en mayúsculas** (`.uppercase()` fuera de `MinSectionHeader`): **17**

## A6. Datos para la lista de inconsistencias

### El padding de `MinCard`, según la llamada

| padding | llamadas | dónde (muestra) |
|---|---:|---|
| `PaddingValues(horizontal = 18.dp, vertical = 2.dp)` | 30 | `sms/SMSScreens.kt:1512`, `plan/TableroDeRecurrentes.kt:1012`, `plan/TableroDeRecurrentes.kt:1340`, `documentos/DocumentosScreen.kt:419` |
| `PaddingValues(18.dp)` | 24 | `destinos/DestinosScreen.kt:339`, `destinos/DestinosScreen.kt:775`, `sms/RepartirEnReconciliar.kt:160`, `sms/SMSScreens.kt:200` |
| `PaddingValues(16.dp)` | 14 | `periodos/DetalleDePeriodoScreen.kt:535`, `periodos/DetalleDePeriodoScreen.kt:604`, `periodos/DetalleDePeriodoScreen.kt:1065`, `periodos/DetalleDePeriodoScreen.kt:1119` |
| `PaddingValues(20.dp)` | 9 | `sms/SMSScreens.kt:1385`, `auth/LoginScreen.kt:233`, `auth/LoginScreen.kt:398`, `auth/RegisterScreen.kt:85` |
| `PaddingValues(Movi.espacios.amplio)` | 9 | `plan/CajaDelPeriodo.kt:215`, `plan/CajaDelPeriodo.kt:224`, `plan/PlanScreen.kt:367`, `dashboard/TarjetaDeCierre.kt:63` |
| `PaddingValues(Movi.espacios.margen)` | 7 | `periodos/DetalleDePeriodoScreen.kt:648`, `periodos/DetalleDePeriodoScreen.kt:1130`, `sdui/SeccionesDeUnVistazo.kt:249`, `sdui/SeccionesDeUnVistazo.kt:919` |
| `PaddingValues(horizontal = 16.dp, vertical = 12.dp)` | 7 | `periodos/DetalleDePeriodoScreen.kt:869`, `destinos/AccesoAPersonasYComercios.kt:65`, `destinos/DestinosScreen.kt:428`, `porrevisar/PorRevisarScreen.kt:614` |
| `PaddingValues(horizontal = 18.dp, vertical = 14.dp)` | 6 | `destinos/DestinosScreen.kt:688`, `sms/SMSScreens.kt:1251`, `sms/SMSScreens.kt:1457`, `plan/TableroDeRecurrentes.kt:1326` |
| `PaddingValues(22.dp)` | 5 | `goals/MetasScreen.kt:100`, `budgets/PresupuestosScreen.kt:564`, `budgets/PresupuestosScreen.kt:1339`, `credits/CreditosScreen.kt:258` |
| `PaddingValues(horizontal = 16.dp, vertical = 14.dp)` | 4 | `periodos/DetalleDePeriodoScreen.kt:1035`, `accounts/AccountsScreen.kt:454`, `accounts/AccountsScreen.kt:1040`, `accounts/AccountsScreen.kt:1077` |
| `default (Movi.espacios.margen = 20 dp)` | 4 | `goals/MetasScreen.kt:181`, `sdui/editor/ScreenEditorScreen.kt:271`, `sdui/editor/ScreenEditorScreen.kt:287`, `components/MinComponents.kt:57` |
| `PaddingValues(horizontal = 18.dp, vertical = 6.dp)` | 4 | `sdui/SeccionesDelPeriodo.kt:453`, `credits/SalidaDeDeudasScreen.kt:198`, `credits/SalidaDeDeudasScreen.kt:219`, `credits/SalidaDeDeudasScreen.kt:248` |
| `PaddingValues(vertical = 4.dp)` | 3 | `periodos/DetalleDePeriodoScreen.kt:954`, `periodos/DetalleDePeriodoScreen.kt:995`, `transactions/VincularDeudaPicker.kt:77` |
| `PaddingValues(horizontal = 18.dp, vertical = 16.dp)` | 3 | `recurrentes/ChecklistDelPeriodo.kt:238`, `profile/PerfilScreen.kt:578`, `profile/PerfilScreen.kt:884` |
| `PaddingValues(horizontal = 18.dp, vertical = 12.dp)` | 3 | `dashboard/CajaEnHoy.kt:101`, `dashboard/DashboardScreen.kt:706`, `sdui/SeccionesDelPeriodo.kt:509` |
| `PaddingValues(14.dp)` | 2 | `destinos/DetalleDelDestinoSheet.kt:385`, `sdui/SduiRenderer.kt:468` |
| `PaddingValues(horizontal = 18.dp, vertical = 18.dp)` | 2 | `goals/MetasScreen.kt:164`, `recurrentes/ChecklistDelPeriodo.kt:227` |
| `PaddingValues(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.medio)` | 2 | `plan/PlanScreen.kt:408`, `credits/CreditosScreen.kt:1173` |
| `PaddingValues(32.dp)` | 2 | `components/NoSePudoLeer.kt:39`, `components/VacioQueEnsena.kt:63` |
| `PaddingValues(horizontal = 16.dp, vertical = 2.dp)` | 2 | `quickadd/QuickAddScreen.kt:1576`, `quickadd/TransferForm.kt:1030` |
| `PaddingValues(horizontal = 16.dp, vertical = 16.dp)` | 1 | `periodos/DetalleDePeriodoScreen.kt:785` |
| `PaddingValues(horizontal = 14.dp, vertical = 14.dp)` | 1 | `sms/SMSScreens.kt:1577` |
| `PaddingValues(horizontal = 18.dp, vertical = 4.dp)` | 1 | `sms/SMSScreens.kt:1604` |
| `PaddingValues(horizontal = 16.dp, vertical = 4.dp)` | 1 | `extractos/StatementReviewScreen.kt:246` |
| `PaddingValues(horizontal = 14.dp, vertical = 2.dp)` | 1 | `dashboard/EditarInicioScreen.kt:233` |
| `PaddingValues(
                start = Movi.espacios.amplio,
                end = Movi.espacios.amplio,
                top = Movi.espacios.minimo,
                bottom = Movi.espacios.amplio,
            )` | 1 | `sdui/SeccionesDeUnVistazo.kt:777` |
| `PaddingValues(vertical = Movi.espacios.corto)` | 1 | `analisis/EnQueSeVaScreen.kt:221` |

### Filas tocables a mano: padding vertical

| padding vertical | filas | dónde (muestra) |
|---|---:|---|
| 2 dp | 1 | `extractos/StatementReviewScreen.kt:209` |
| 5 dp | 1 | `quickadd/QuickAddScreen.kt:1548` |
| 6 dp | 4 | `destinos/DetalleDelDestinoSheet.kt:249`, `transactions/DetalleDelMovimiento.kt:192`, `transactions/TransactionsScreen.kt:1389`, `buscar/BuscarScreen.kt:262` |
| 8 dp | 5 | `components/ScopeToggle.kt:38`, `transactions/TransactionsScreen.kt:1594`, `categorias/CategoriasScreen.kt:822`, `budgets/PresupuestosScreen.kt:1042` |
| 9 dp | 2 | `dashboard/DashboardScreen.kt:757`, `papeles/HojaLeyendoElPapel.kt:233` |
| 10 dp | 6 | `periodos/DetalleDePeriodoScreen.kt:1001`, `documentos/DocumentosScreen.kt:889`, `extractos/RevisionDelExtractoDeSaldo.kt:203`, `sdui/SeccionesDelPeriodo.kt:371` |
| 12 dp | 5 | `goals/GoalSheet.kt:269`, `sdui/SeccionesDelPeriodo.kt:595`, `sdui/editor/ScreenEditorScreen.kt:606`, `transactions/VincularDeudaPicker.kt:85` |
| 13 dp | 2 | `compartir/CompartirScreen.kt:399`, `components/ListaDeCuentasElegibles.kt:78` |
| 14 dp | 6 | `sms/SMSScreens.kt:1906`, `extractos/ExtractosScreen.kt:267`, `compartir/CompartirScreen.kt:381`, `components/MinComponents.kt:167` |

### Filas tocables a mano: alto fijo o mínimo

| alto | filas | dónde (muestra) |
|---|---:|---|

### Rótulos en mayúsculas a mano

- `destinos/DetalleDelDestinoSheet.kt:219`
- `sms/SMSScreens.kt:565`
- `sms/SMSScreens.kt:1930`
- `recurrentes/ChecklistDelPeriodo.kt:818`
- `extractos/ExtractosScreen.kt:277`
- `extractos/ImportDetailScreen.kt:185`
- `dashboard/DisponibleDelPeriodo.kt:69`
- `dashboard/InicioDeUnVistazo.kt:313`
- `porrevisar/TarjetaDelDebitoAutomatico.kt:108`
- `components/MinComponents.kt:108`
- `transactions/TransactionsScreen.kt:1320`
- `transactions/TransactionsScreen.kt:1623`
- `accounts/AccountDetailScreen.kt:424`
- `accounts/AccountsScreen.kt:727`
- `accounts/BienSheet.kt:208`
- `quickadd/TransferForm.kt:1097`
- `credits/CreditosLogic.kt:588`

