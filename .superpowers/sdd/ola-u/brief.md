# Ola U — «Flujo del día $0» no puede quedar sin explicación cuando salió plata de verdad

Pedido del dueño (2026-09-27, con captura): pagó dos tarjetas hoy desde Bancolombia Ahorros ($386.902 + $1.542.634 = $1.929.536 que salieron de verdad de su cuenta disponible), y «Flujo del día» en Movimientos dice «$0». Su reacción textual: *«eso es una mentira»*.

## No cambies la regla de plata — arréglale la honestidad al texto
La exclusión de «Pago de tarjeta» del flujo/gasto del día es correcta y a propósito (evita contar dos veces: la compra ya contó como gasto cuando se hizo con la tarjeta). **No toques `isCashFlow`/`countsAsCashFlow` ni la definición de `Flujo del día`.** El problema real es que un día con pagos de tarjeta muestra «$0» sin ningún indicio de que sí salió plata de la cuenta — se lee como que no pasó nada, cuando $1,9M sí se movieron. Es el mismo principio que ya sigue el resto de la app (nunca «$0» ni un vacío sin explicación — ver Ola I, «cada peso cuenta una vez» en Disponible, etc.).

## Qué construir
En `shared/.../ui/transactions/TransactionsScreen.kt` (el encabezado de cada día, donde ya está «Flujo del día $X» y, desde la Ola M, «Día a día: …»): cuando el «Flujo del día» de un grupo de día es distinto de la suma real que salió/entró de las cuentas de Tu plata (porque hay uno o más «Pago de tarjeta» ese día), agrega una línea corta y clara debajo, del estilo:

> «Además, $1,9M salieron a pagar tarjeta (ya contado al comprar)»

- Calcula el monto sumando los movimientos de ese día con categoría de pago de tarjeta (`CARD_PAYMENT_CATEGORY`) que sean `EXPENSE` en una cuenta de Tu plata (no en la propia tarjeta).
- Si no hay ningún pago de tarjeta ese día, no agregues nada (el «Flujo del día» normal sigue igual, sin línea de más).
- El texto debe leerse bien tanto si «Flujo del día» ya es distinto de $0 (un día con gastos normales Y un pago de tarjeta) como si es exactamente $0 (el caso que el dueño reportó).
- Revisa si esta información ya está disponible en el modelo que arma el encabezado del día (probablemente ya tienes los movimientos de ese día a mano) para no agregar una lectura nueva — es un cálculo puro sobre datos que ya están en memoria.

## Pruebas
- Función pura que arma este texto (dado un día con N movimientos, cuánto de pago de tarjeta): la línea aparece con el monto correcto solo si hay pagos de tarjeta ese día, y no aparece si no los hay.
- Robolectric: un día con dos «Pago de tarjeta» y ningún otro gasto muestra «Flujo del día $0» + la línea nueva con $1.929.536; un día normal sin pagos de tarjeta no cambia.

## Restricciones
Español neutro con tuteo (VoseoScanTest). No cambies `isCashFlow`, `CARD_PAYMENT_CATEGORY`, ni el cálculo del Disponible. No toques la Ola M («Día a día») más allá de agregar esta línea aparte. No toques la base de producción. Commit + push tras cada commit. Un solo Gradle a la vez. CI completo con `--rerun-tasks` al final. Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>.
