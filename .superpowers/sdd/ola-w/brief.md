# Ola W — «Flujo del día» refleja lo que de verdad salió de tu plata

Decisión del dueño (2026-09-27, tras preguntarle directo): «Flujo del día» en Movimientos debe incluir los pagos de tarjeta como salida real (su cuenta sí perdió esa plata ese día), en vez de excluirlos con una nota aparte (Ola U). Se acepta que esto haga que «Flujo del día» (por día, en Movimientos) sea una cifra DISTINTA de «Salió»/Disponible (por período, en Hoy/Plan/Tus períodos), que sigue excluyendo el pago de tarjeta para no duplicar el gasto real del mes. Son dos métricas separadas, cada una honesta a su manera: «Flujo del día» = tu extracto bancario del día; «Salió» = cuánto gastaste de verdad ese período.

## Qué NO tocar
- `aporteAlFlujoDelDia` (`core/.../shared/model/CashFlow.kt:241`) y `countsAsCashFlow`/`isCashFlow` **no cambian**. Los usa además `server/.../routes/EventRoutes.kt` — investiga primero para qué (probablemente algo de por-día del servidor); si esa ruta también debería reflejar el cambio, dilo como hallazgo, pero el brief es SOLO sobre lo que se ve en la cabecera de cada día de Movimientos (cliente).
- El cálculo de Disponible, Salió, Entradas, Tus períodos: sin cambios.
- La Ola U (`shared/.../ui/transactions/LineaPagoDeTarjeta.kt`, `montoPagoDeTarjetaEnElDia`): la función de cálculo del monto de tarjeta del día se puede REUSAR (ya suma exactamente lo que hace falta), pero el composable/línea cambia de propósito: ya no es una ACLARACIÓN de una exclusión (porque ya no se excluye), es un contexto breve de que esa parte no cuenta dos veces en tu gasto del mes.

## Qué construir
1. En `shared/.../ui/transactions/TransactionsScreen.kt` (~línea 202, donde arma `total = filtered.sumOf { aporteAlFlujoDelDia(it) }` para la cabecera de cada día), agrega el monto de pago de tarjeta del día (`montoPagoDeTarjetaEnElDia`, ya existe) al total mostrado como «Flujo del día», SOLO para esta cabecera del cliente — sin tocar `aporteAlFlujoDelDia` en `:core`.
2. Cambia el texto de `LineaPagoDeTarjeta.kt`: ya no dice «Además, $X salieron a pagar tarjeta (ya contado al comprar)» como si estuviera afuera del total de arriba — ahora que SÍ está incluido en «Flujo del día», el texto debe aclarar lo contrario: que esa parte no vuelve a contarse en el gasto del período/Disponible. Redacta algo corto y claro, ej.: «De eso, $X fueron a pagar tarjeta — ya contado en tu gasto del mes cuando compraste, no se duplica.» (ajusta el texto con criterio, en español neutro con tuteo, corto).
3. Verifica que el signo sea correcto: un pago de tarjeta siempre RESTA del flujo del día (es una salida), nunca suma.

## Pruebas
- Actualiza las pruebas de Ola U que fijaban «Flujo del día $0» + la línea aparte (`PagoDeTarjetaEnElDiaTest.kt`, `PagoDeTarjetaEnMovimientosTest.kt`) para el nuevo comportamiento: el día del dueño (2 pagos de tarjeta, sin otro gasto) debe mostrar «Flujo del día −$1.929.536» (no $0), con la línea de contexto abajo.
- Un día con gasto normal + pago de tarjeta: el total debe sumar los dos.
- Un día sin pagos de tarjeta: sin cambios (mismo comportamiento de siempre).

## Restricciones
Español neutro con tuteo (VoseoScanTest). No toques `:core`/Disponible/Salió. No toques la base de producción. Commit + push tras cada commit. Un solo Gradle a la vez. CI completo con `--rerun-tasks` al final. Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>.
