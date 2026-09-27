# Ola Y — Al confirmar un pago de deuda, arma el traspaso completo solo

Descubierto en vivo (2026-09-27, cuatro veces el mismo día: Crédito Mamá, Hipoteca Papá, Libranza Papá, y las dos Master Black): el dueño confirma un SMS de traspaso con la categoría correcta («Cuota de crédito» o «Pago de tarjeta»), pero el movimiento queda como un GASTO SUELTO — nunca el traspaso de dos patas que `server/.../reminders/PagosDeDeuda.kt` exige para marcar la deuda pagada (una pata que sale de su cuenta, otra que ENTRA a la cuenta de la deuda misma). Cada vez tuve que corregirlo yo a mano por SQL, con respaldo. Esto tiene que dejar de necesitar mi intervención.

## Qué construir

En la pantalla de reconciliar un SMS (`shared/.../ui/sms/SMSScreens.kt`, `SMSReconcileScreen`) y en el editor de un movimiento ya guardado (`ui/transactions/CategorySheets.kt`, `ContenidoDelMovimiento`):

1. **Cuando el dueño elige categoría «Cuota de crédito» o «Pago de tarjeta»** (`CUOTA_CATEGORY`/`CARD_PAYMENT_CATEGORY`) para un movimiento tipo Gasto que NO es ya la pata de un traspaso (`transferId == null`), ofrece un paso más: **«¿A cuál crédito o tarjeta corresponde?»**, con la lista de sus cuentas LOAN/CREDIT_CARD (nombre + lo que debe). Es opcional — si no elige ninguna, el movimiento se guarda como gasto suelto tal como hoy (no lo bloquees).
2. **Al elegir una cuenta de deuda**, el server arma el traspaso completo:
   - Si la cuenta de deuda es un CRÉDITO (`AccountType.LOAN`, con `credit_terms`): calcula el abono a capital y el `no_amortiza` con la MISMA fórmula que ya usa `pagoDeCuotaLegs`/lo que muestra «Movi estima: $X de interés · $Y a capital» en Plan (busca esa función y reúsala, no la reinventes). Caso especial: un crédito con `sin_intereses`/condiciones que hagan que el abono a capital sea $0 (como «Crédito Mamá», ver sus `notes`) — el abono puede ser $0, sigue siendo válido.
   - Si es una TARJETA (`AccountType.CREDIT_CARD`): la pata de deuda entra por el monto completo pagado (sin descomponer intereses/capital — ver el patrón ya usado en pagos de tarjeta existentes), **en la MISMA MONEDA que la cuenta de la tarjeta**. Si el movimiento origen está en una moneda distinta a la de la tarjeta (pesos pagando una tarjeta en dólares, el caso real de hoy), pide la tasa del día a `FxRateService`/`TasaUsdCop` (ya existe, lo usa `CardReminders.kt`) para convertir, y dilo en la descripción de la pata («… a TRM $X»).
   - Guarda las dos patas con el mismo `transferId`, igual que un traspaso armado a mano hoy.
3. **Si el dueño tiene DOS deudas que podrían corresponder al mismo pago** (como hoy: Master Black en pesos y en dólares, mismo número de tarjeta en el SMS) — no se puede adivinar solo con el texto del banco. Que la lista de cuentas a elegir sea clara (nombre completo, moneda, saldo) para que él elija bien, y no ofrezcas una sola "mejor opción" adivinada.

## Pruebas
- Servidor: confirmar una categoría de deuda + elegir una cuenta LOAN arma las dos patas con el split correcto (reusa las pruebas/fixtures ya existentes de `pagoDeCuotaLegs`); elegir una cuenta CREDIT_CARD en otra moneda convierte con la tasa del día; sin elegir cuenta, el comportamiento de hoy no cambia (gasto suelto).
- UI: el paso "¿a cuál corresponde?" aparece solo con esas dos categorías y solo si el movimiento no es ya parte de un traspaso; es opcional, se puede saltar.
- Caso real a reproducir en una prueba: crédito interés puro (abono a capital $0) arma su pata igual.

## Restricciones
No toques `pagoDeCuotaLegs`/`PagosDeDeuda.kt` más allá de reusarlas — son invariantes de dinero muy finos, ya documentados; si necesitas exponer algo de ahí como función pública para reusarlo, hazlo con cuidado y sin cambiar su comportamiento existente. Español neutro con tuteo (VoseoScanTest). No toques la base de producción. Commit + push tras cada commit. Un solo Gradle a la vez. CI completo con `--rerun-tasks` al final. Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>.
