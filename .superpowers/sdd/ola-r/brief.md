# Ola R — Negrita en lo importante del texto crudo del SMS

Pedido del dueño (2026-09-27, con captura de «Por revisar»): en la tarjeta de un mensaje del banco, el texto citado tal cual («Bancolombia: Pagaste $386.902 en la tarjeta de crédito *3684 desde la cuenta *8133, el 27/09/2026 09:17…») se ve todo con el mismo peso. Quiere que los montos y la entidad/cuenta se vean en **negrita**, para que salte a la vista lo importante al escanear la lista.

## Dónde
`shared/src/commonMain/kotlin/com/jvillada/movi/ui/sms/SMSScreens.kt`, `TarjetaDeMensajeDelBanco` (~línea 349-351): hoy es
```kotlin
Text(sms.text, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontFamily = FontFamily.Monospace, lineHeight = 17.sp, modifier = Modifier.padding(start = 12.dp))
```
Es el ÚNICO lugar donde se pinta el texto crudo del SMS (la reconciliación no lo repite con este mismo componente — confírmalo, y si encuentras otro lugar que también cite el texto crudo entre comillas, aplícale la misma función).

## Qué construir
1. Función pura `resaltadoDelTextoDelBanco(texto: String): AnnotatedString` (o el nombre que sigas la convención del archivo), que recibe el texto crudo del SMS y devuelve un `AnnotatedString` con `SpanStyle(fontWeight = FontWeight.Bold)` en:
   - **Montos en pesos**: `$` seguido de dígitos y puntos/comas (`$386.902`, `$1,300,000`, `$4.000.000`). Cubre los dos formatos que aparecen en los SMS reales (con punto y con coma como separador de miles — mira ejemplos reales en `server/.../sms` o en las pruebas de `ParsedSms`/`SmsRoutes` para la lista de formatos que el parser ya reconoce, y reusa esa regex o una equivalente en vez de inventar una nueva).
   - **Números de cuenta/tarjeta enmascarados**: el patrón `*` seguido de 4 o más dígitos (`*3684`, `*8133`, `*10272432504`) — el mismo patrón que ya usa `huellaDeUnMovimiento`/`CUENTA` en `core/.../MemoriaDeCategorias.kt` (`Regex("""\*\s?(\d{4,})""")`) o `cuentaPorElNumero` en `ui/components/CuentaDelBanco.kt`. Reusa esa regex si es pública, o declárala igual para no divergir.
   - **El nombre del banco/entidad al inicio** (`Bancolombia`, la palabra antes del primer `:`), si aparece — pon en negrita esa primera palabra/frase antes de los dos puntos.
2. Aplica el resultado en `TarjetaDeMensajeDelBanco` reemplazando el `Text(sms.text, ...)` de texto plano por uno que reciba el `AnnotatedString`, conservando `style`, `color`, `fontFamily = FontFamily.Monospace`, `lineHeight`. El color y la fuente base no cambian — solo el peso de esos fragmentos.
3. No falles si el texto no tiene ninguno de estos patrones (un SMS raro sin monto): devuelve el texto tal cual, sin negrita, sin excepción.

## Pruebas
- Puras: con el SMS real de ejemplo del dueño («Bancolombia: Pagaste $386.902 en la tarjeta de crédito *3684 desde la cuenta *8133, el 27/09/2026 09:17…»), verifica que los `spanStyles` del `AnnotatedString` resultante cubren exactamente los rangos de «Bancolombia», «$386.902», «*3684» y «*8133» con `FontWeight.Bold`, y que el resto del texto no lleva ese estilo. Prueba también con un SMS de transferencia (dos cuentas: origen y destino) y uno sin ningún monto.
- Robolectric (opcional si ya hay pruebas de geometría de `TarjetaDeMensajeDelBanco`): que la tarjeta sigue rindiendo sin romperse con el nuevo `AnnotatedString`.

## Restricciones
Español neutro con tuteo (VoseoScanTest — aunque acá no agregas texto nuevo, solo resaltas el existente). No cambies `sms.text` en sí (el dato crudo), solo cómo se pinta. No toques el parser de SMS (`ParsedSms`) ni las reglas de negocio. No toques la base de producción. Commit + push tras cada commit. Un solo Gradle a la vez. CI completo con `--rerun-tasks` al final. Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>.
