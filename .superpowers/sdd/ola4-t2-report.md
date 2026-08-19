# Ola 4 — T2: búsqueda en Movimientos + Presupuestos dice que se edita y por cuánto tiempo

Rama: `ola4-t2-busqueda` (creada desde el HEAD del worktree aislado, no desde `feat/ola-4-inicio`
del repo principal — instrucción explícita de la tarea, distinta del brief común que asume el
repo principal).

## F13 — Búsqueda real en Movimientos

`shared/src/commonMain/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreen.kt`:

- La lupa (arriba a la derecha) ahora es `clickable`: alterna `searchActive` y, al cerrar, limpia
  `searchQuery`.
- Con `searchActive`, debajo del encabezado aparece un campo (`BasicTextField` con ícono de lupa
  a la izquierda y una X — `Icons.Rounded.Close` — a la derecha para cerrar y limpiar). Autofocus
  vía `FocusRequester` al abrir.
- Placeholder del campo: `"Descripción, comercio o categoría"`.
- Filtro puro `fun matchesQuery(event: FinancialEvent, query: String): Boolean` (top-level,
  pública para poder testearla) — compara sin tildes ni mayúsculas contra `description`,
  `merchant` (nullable) y `category`. Consulta en blanco matchea todo.
- `visibleDays` ahora combina el filtro de tipo (Todo/Egresos/Ingresos/Por confirmar) **y**
  `matchesQuery` sobre los mismos items, antes de recalcular el total del día — sigue el mismo
  criterio `countsAsCashFlow` que ya tenía.
- Estado vacío: si `searchQuery` no está en blanco y no hay resultados, se muestra
  `"Nada coincide con \"$searchQuery\""` (con el texto buscado, recortado) en vez del CTA de
  "Sin movimientos aún" / "Crear una cuenta primero" — ese CTA solo aparece cuando el vacío NO es
  por búsqueda.

**Sobre el normalizador compartido.** El brief sugiere reusar o extraer el normalizador de
`ui/components/CategoryField.kt` (Ola 2, F35). La tarea me restringe a tocar SOLO
`TransactionsScreen.kt` y `PresupuestosScreen.kt` (+ tests) porque otra persona toca Dashboard/
barra/Más en paralelo — no puedo editar `CategoryField.kt` para hacerlo público sin salirme del
alcance. Opté por **duplicar** una versión `private fun normalizeForMatch` idéntica dentro de
`TransactionsScreen.kt`, con un comentario que explica por qué no se extrajo. Queda una deuda
menor: unificar los dos normalizadores en un helper común (`ui/components/TextNormalize.kt` o
similar) en una pasada posterior, cuando ya no haya trabajo en paralelo sobre esos archivos.

Tests nuevos: `shared/src/commonTest/kotlin/com/jvillada/movi/ui/transactions/TransactionsScreenTest.kt`
(7 casos): blanco matchea todo, tildes («Éxito»/"exito"), mayúsculas, comercio nulo (no rompe y
sigue matcheando por descripción), comercio presente, categoría, y consulta sin coincidencias.
Los 7 pasan (`TEST-...TransactionsScreenTest.xml`: `tests="7" failures="0" errors="0"`).

## F15 — Presupuestos: que se note que se edita

`shared/src/commonMain/kotlin/com/jvillada/movi/ui/budgets/PresupuestosScreen.kt`:

- El título de la hoja de edición **ya decía** `"Editar presupuesto"` (no `"Nuevo presupuesto"`)
  — revisé `Sheet.Edit -> BudgetSheet(title = "Editar presupuesto", ...)` y ya estaba distinto del
  de creación. No hizo falta tocarlo; probablemente ya se había corregido en una ola anterior. Lo
  que faltaba era el chevron.
- `BudgetCard`: la fila superior (categoría + %) ahora envuelve el `%` y un `ChevronRight()`
  (mismo componente de `ui/components/MinComponents.kt` que usa la guía de primeros pasos del
  Inicio y la fila de "pagos de tarjeta sin marcar" en Movimientos) en un `Row` a la derecha.

## F16 — Presupuestos: por cuánto tiempo

Mismo archivo:

- Encabezado: `"Gastado del mes"` → `"Gastado en $monthName"` (ej. `"Gastado en agosto"`).
  `monthName` sale de `Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).month`
  (kotlinx-datetime, ya dependencia de `:shared` commonMain) mapeado a español en minúscula con
  un `private fun Month.spanishName()` nuevo (when exhaustivo enero…diciembre, sin `else` — el
  enum `Month` de kotlinx-datetime 0.6.1 tiene exactamente 12 valores).
- Cada tarjeta: `" / ${formatCOP(limit)}"` → `" de ${formatCOP(limit)} este mes"` — queda
  `"$0 de $2.000.000 este mes"`.
- La hoja (crear y editar, mismo componente `BudgetSheet`): `"Límite mensual · COP"` →
  `"Límite mensual · se reinicia cada mes"` — dice el dato que faltaba (la moneda ya es obvia en
  toda la app; que se reinicia el día 1 no lo era). Se aplicó a las dos hojas porque ambas
  comparten el mismo composable y el dato es igual de cierto en las dos.

No se buscó (ni encontró) un `monthName`/`MES` preexistente antes de escribir el helper — grep
confirmó que no había ninguno en `shared/` ni `core/`.

## Verificación

```
$ ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew \
    :shared:testDebugUnitTest :shared:compileDebugKotlinAndroid :webApp:compileKotlinWasmJs \
    --console=plain -q
GRADLE_EXIT=0
```

Sin salida (modo `-q`), exit code 0 — compila Android y wasmJs, y corren los tests de `:shared`
(incluye los 7 nuevos de `TransactionsScreenTest`, confirmados vía el XML de resultados). También
corrí `:core:jvmTest --tests VoseoScanTest` aparte (no pedido explícitamente, pero el brief común
lo exige y toqué texto de usuario nuevo): exit 0, ningún voseo detectado.

No pude verificar en vivo (emulador/simulador) por las restricciones de "sin esperas largas" de
la tarea — todo lo de arriba es lectura de código + tests unitarios + compilación.

## Dudas

1. El título "Editar presupuesto" de F15 ya estaba correcto antes de esta tarea — no hay commit
   propio que lo explique en este worktree; puede que ya viniera de una ola previa (no rehecho,
   solo verificado).
2. Duplicación del normalizador (`normalizeForMatch`) entre `CategoryField.kt` y
   `TransactionsScreen.kt` por la restricción de alcance — ver nota en F13 arriba. Sugiero
   unificarlo en una pasada de limpieza post-Ola 4.
3. "Límite mensual · se reinicia cada mes" se cambió en ambas hojas (crear y editar), no solo en
   la de crear como sugiere literalmente el ítem del plan — mismo componente, mismo dato válido
   en las dos; avisar si se prefería solo en una.
