# Ola T — «Tu plata» en Hoy muestra el desglose por cuenta

Pedido del dueño (2026-09-27): «Eso debería quedar explícito en la tarjeta de "Tu Plata" o en algún lugar». Ya existe el componente (`PatrimonioSection` en `shared/.../ui/sdui/SeccionesDeUnVistazo.kt:551`, con la fila «Tu plata» que se expande en una lista de cuentas cuando `cuentas.size > 1`, ~línea 590-611), pero esa sección («PATRIMONIO» en `SduiRenderer.kt:171`) no está en la definición de pantalla de Hoy — el hero «Tu plata» de arriba (`HERO_BALANCE_TITLE` en `DashboardLogic.kt:314`) es un componente distinto, sin desglose.

## Qué construir
Conecta el hero «Tu plata» de Hoy con el desglose por cuenta, SIN duplicar la tarjeta «Patrimonio neto» completa (esa ya vive en Patrimonio/Hoy si aplica — solo quieres el desglose de LA CIFRA «Tu plata», no todo el patrimonio con Bienes/Deudas).

Opciones, elige la que calce mejor con lo que ya hay armado (decide con criterio, no me preguntes):
1. Al tocar la cifra «Tu plata» o su tarjeta en Hoy, se despliega inline la lista de cuentas (nombre + saldo) que ya arma `PatrimonioSection`/el modelo de `cuentas` — reutiliza esa lógica en vez de escribir una nueva.
2. O: agrega una fila «Ver detalle ›» bajo el hero que navegue a Patrimonio (o abra un sheet chico) con exactamente esas cuentas.

Cualquiera que elijas, verifica primero de dónde sale hoy la lista de `cuentas` que ya usa `PatrimonioSection` (busca quién arma ese modelo en el servidor/cliente — probablemente el mismo endpoint que ya arma «Tu plata» del hero) y REUSA esos datos: no dupliques la query de saldos por cuenta.

## Pruebas
Robolectric: en Hoy, tocar «Tu plata» (o su fila «Ver detalle») muestra las cuentas de Tu plata con sus saldos; con una sola cuenta de Tu plata, no hace falta el desglose (mismo criterio `cuentas.size > 1` que ya usa `PatrimonioSection`). Verifica que la suma de las filas coincide con la cifra grande.

## Restricciones
Español neutro con tuteo (VoseoScanTest). No cambies el cálculo de `saldoDeTuPlata`/`esDeTuPlata`. No toques la base de producción. Commit + push tras cada commit. Un solo Gradle a la vez. CI completo con `--rerun-tasks` al final. Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>.
