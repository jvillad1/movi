# Ola 6 — T3: imágenes en el chat de Movi AI (F32) y panel de notificaciones (F5)

## Qué cambié

### F32 — Imágenes en el chat

1. **`core/.../shared/model/Finance.kt`** — `ChatMessage` gana `imageBase64: String? = null` e
   `imageMime: String? = null`. Ambos nulos por defecto: un mensaje sin imagen decodifica y
   round-trips exactamente igual que antes de esta ola (ver `ChatModelTest`).

2. **`server/.../routes/AiRoutes.kt`**:
   - `validateChatImages(messages)`: recorre los mensajes, y para cualquiera con
     `imageBase64` no nulo valida — en este orden — que `imageMime` venga, que el mime sea uno
     que Claude soporta (reusa `ClaudeStatementParser.supportedImageMime`: png/jpeg/webp/gif),
     que el base64 decodifique, y que el resultado decodificado pese ≤ 5 MB
     (`MAX_CHAT_IMAGE_BYTES`). Devuelve el primer mensaje de error o `null`. Se llama
     **antes** de tocar `anthropicClient` o la DB — así el camino de error es 100% testeable
     sin red ni `ANTHROPIC_API_KEY`, y el usuario ve el 422 al toque en vez de esperar una
     llamada a Claude que iba a fallar de todos modos.
   - `toMessageParam(m: ChatMessage)`: arma el `MessageParam` — si el mensaje trae imagen ya
     validada, construye un bloque de imagen (mismo patrón que
     `ClaudeStatementParser.parseImage`: `Base64ImageSource` + `ContentBlockParam.ofImage`) y,
     si el usuario también escribió texto, un bloque de texto aparte
     (`contentOfBlockParams`). Sin imagen, el comportamiento es idéntico a antes de F32
     (`content(String)`).
   - `PERSONA` gana una línea: *"F32: si el usuario te manda una foto de un recibo, un
     extracto o una oferta del banco, extrae lo relevante (montos, fechas, comercio o
     condiciones) y opina usando los datos del usuario en 'DATOS DEL USUARIO'."*
   - Import nuevo: `ClaudeStatementParser` (reuso, sin duplicar la lista de mimes soportados
     ni el mapeo mime→`Base64ImageSource.MediaType`).

3. **`shared/.../ui/ai/AIChatScreen.kt`**:
   - Ícono de clip (`Icons.Rounded.AttachFile`) junto al campo de texto; abre
     `rememberFilePicker` (el mismo de Extractos/Ola 1, sin tocar su firma ni sus `actual`s).
   - El callback filtra por mime en el cliente: si no empieza con `"image/"`, muestra
     **"Por ahora solo imágenes"** en vez de guardar el adjunto (así un PDF elegido por
     error no llega al server).
   - Con una imagen válida elegida, aparece una fila con el nombre del archivo + una X para
     quitarla antes de enviar (sin miniatura decodificada — ver Dudas).
   - `send()`: adjunta `imageBase64` (codificado con `kotlin.io.encoding.Base64`, stdlib
     multiplataforma, `@OptIn(ExperimentalEncodingApi::class)`) e `imageMime` al
     `ChatMessage`; permite enviar con imagen y sin texto (antes exigía texto no vacío).
   - La burbuja del mensaje del usuario muestra "Imagen adjunta" (con ícono) cuando
     `imageBase64 != null`, arriba del texto si también escribió algo.
   - La respuesta se sigue mostrando igual que siempre (no toqué `AIMsgAI`).

### F5 — Panel de notificaciones

4. **`shared/.../ui/dashboard/DashboardLogic.kt`** — `notificationRows(data: DashboardData):
   List<NotificationRow>` (nuevo, no cambia ninguna cifra existente): combina
   `upcomingPaymentsWithin(data.upcoming)` (pagos próximos/vencidos, ventana de 7 días, cada
   fila a `Screen.Credits` si el `rule.id` viene de `CREDIT_RULE_PREFIX`/`CARD_RULE_PREFIX`, o
   a `Screen.Recurrentes` si es una regla real) con `dashboardAlerts(...)` — la misma función
   que ya arma la sección `ALERTS` del Inicio: presupuestos superados → `Screen.Budgets`,
   candidatos de pago de tarjeta → `Screen.Transactions`, SMS pendientes → `Screen.SMSInbox`.
   Cero fetches nuevos: todo sale de `DashboardData`, que el Inicio ya carga.

5. **`shared/.../ui/notifications/NotificationsPanel.kt`** (nuevo archivo/carpeta) — hoja
   deslizante con el mismo patrón que las demás (scrim + `SheetHandleWithClose`, ver
   `CreateAccountSheet.kt`). Sin notificaciones: una línea ancorada **"No tienes
   notificaciones por ahora"** (nada de snackbar — la queja original de F5). Con
   notificaciones: una `CardRow` tocable por fila (reuso del componente existente, con
   chevron), que cierra el panel y navega al `Screen` de la fila.

6. **`shared/.../ui/dashboard/DashboardScreen.kt`** — la campana vuelve al encabezado
   (`Icons.Rounded.Notifications`), a la derecha del avatar+nombre (el `SpaceBetween` ya
   estaba preparado para esto, con un comentario que decía "vuelve en Ola 6"). Punto rojo
   (`StatusDot`, reuso) **solo** si `notificationRows(data)` no está vacío — antes el punto
   era fijo, sin relación con si de verdad había algo. Al tocar la campana abre
   `NotificationsPanel`.

7. **`shared/.../ui/components/MinComponents.kt`** — `StatusDot` gana un parámetro
   `modifier: Modifier = Modifier` (al final, no rompe ningún call site posicional existente)
   para poder alinearlo en la esquina del ícono de la campana con `Modifier.align(TopEnd)`
   dentro del `Box`.

## Textos exactos (tuteo neutro)

- «Por ahora solo imágenes»
- «Imagen adjunta»
- «Adjuntar imagen» / «Quitar imagen» (content descriptions)
- «Notificaciones» (título del panel + content description de la campana)
- «No tienes notificaciones por ahora»
- Filas del panel: reusan los textos ya existentes de `dueLabel` («Vence en 2 días»,
  «Vencido ayer», …) y `dashboardAlerts` («Presupuesto de Mercado superado», «2 pagos de
  tarjeta por confirmar», «1 mensaje del banco por confirmar»).
- Servidor 422: «Falta el tipo de la imagen adjunta.» / «Formato de imagen no soportado. Sube
  PNG, JPG, GIF o WEBP.» / «No pude leer la imagen adjunta.» / «La imagen pesa más de 5 MB.
  Sube una más liviana.»

## Tests

- `core/src/jvmTest/.../shared/model/ChatModelTest.kt` (nuevo, 4 tests): JSON viejo (sin
  `imageBase64`/`imageMime`) decodifica con ambos en `null`; round-trip sin imagen igual que
  antes; round-trip con imagen; `AiChatRequest` con mensajes mixtos.
- `server/src/test/.../routes/AiChatImageRoutesTest.kt` (nuevo, 4 tests, mismo harness H2+JWT
  que `StatementRoutesTest.kt`/`ScreenRoutesTest.kt`): mime no soportado → 422 con "no
  soportado"; imagen de 6 MB decodificados → 422 con "5 MB"; base64 ilegible → 422 (no 500);
  mensaje sin imagen nunca da 422 (verifica que la validación no se dispara sin adjunto).
  Ninguno llama a Claude real — corren sin `ANTHROPIC_API_KEY` ni red, porque la validación
  ocurre antes del chequeo de cliente.
- `shared/src/commonTest/.../ui/dashboard/DashboardLogicTest.kt`: 2 tests nuevos —
  `notificationRows` vacío sin nada pendiente; combina pagos próximos (a su destino correcto
  según sea regla real o synthetic de crédito/tarjeta) + alertas, en el orden esperado.

## Verificación (salida real)

```
$ JAVA_HOME=/usr/local/share/jbrsdk-21/Contents/Home ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew :server:test :core:jvmTest --console=plain -q
(sin salida — BUILD exitoso)

$ ... ./gradlew :shared:compileDebugKotlinAndroid :webApp:compileKotlinWasmJs --console=plain -q
(sin salida — BUILD exitoso)

$ ... ./gradlew :shared:testDebugUnitTest --console=plain -q
(sin salida — BUILD exitoso)
```

Conteos de los tests nuevos/tocados (test-results XML):
- `ChatModelTest`: `tests="4" failures="0" errors="0"`
- `AiChatImageRoutesTest`: `tests="4" failures="0" errors="0"`
- `DashboardLogicTest`: `tests="19" failures="0" errors="0"` (17 previos + 2 nuevos)

No corrí `:shared:compileKotlinIosSimulatorArm64` (no toqué expect/actual — `rememberFilePicker`
se reusa tal cual, sin cambiar su firma).

## Archivos tocados

- `core/src/commonMain/kotlin/com/jvillada/movi/shared/model/Finance.kt`
- `core/src/jvmTest/kotlin/com/jvillada/movi/shared/model/ChatModelTest.kt` (nuevo)
- `server/src/main/kotlin/com/jvillada/movi/server/routes/AiRoutes.kt`
- `server/src/test/kotlin/com/jvillada/movi/server/routes/AiChatImageRoutesTest.kt` (nuevo)
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/ai/AIChatScreen.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardLogic.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/dashboard/DashboardScreen.kt`
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/notifications/NotificationsPanel.kt` (nuevo)
- `shared/src/commonMain/kotlin/com/jvillada/movi/ui/components/MinComponents.kt`
  (`StatusDot` gana un `modifier` opcional — cambio de una línea, aditivo)
- `shared/src/commonTest/kotlin/com/jvillada/movi/ui/dashboard/DashboardLogicTest.kt`

No toqué `PerfilScreen`, `MetasScreen`, `SuscripcionesScreen` ni nada fuera de la lista de
arriba (`DashboardLogic.kt` solo gana una función que expone datos que ya se calculaban con
`upcomingPaymentsWithin`/`dashboardAlerts` — ninguna cifra existente cambió).

## Dudas

- **Miniatura vs. nombre**: la consigna decía "muestra la miniatura o el nombre" (uno u otro).
  Implementé solo el nombre + ícono genérico + X — decodificar bytes arbitrarios a
  `ImageBitmap` en `commonMain` de Compose Multiplatform no tiene una API única entre
  Android/iOS/wasm sin agregar dependencias nuevas (Skia en unos targets, `BitmapFactory` en
  Android). Si se quiere la miniatura real, es un `expect/actual` nuevo — no lo hice para no
  ampliar el alcance de archivos tocados en UI (la consigna limitaba a `AIChatScreen.kt`).
- **Picker en iOS**: `rememberFilePicker` en `iosMain` ya era un no-op vacío desde la Ola 1
  (gap previo, no de esta tarea) — el clip existe en la pantalla pero no abre nada en iOS
  hasta que alguien implemente ese `actual`. No lo toqué (fuera del alcance: solo
  `AIChatScreen.kt`/`DashboardScreen.kt`/`ui/notifications/` en UI).
  Debería quedar registrado en el backlog de Extractos (F27), no en Ola 6.
  Nota: no encontré ese ítem en el mapa de feedback — lo dejo anotado acá para quien integre.
- **`StatusDot` con `modifier`**: es el único archivo compartido fuera de "encabezado/campana"
  que toqué; es un cambio aditivo de una línea (parámetro con default, ningún call site
  existente se rompe) necesario para posicionar el punto rojo en la esquina del ícono de la
  campana. Si se prefiere no tocar `MinComponents.kt`, la alternativa es un `Box` a mano sin
  reusar `StatusDot` — lo dejé como reuso porque es exactamente lo que hace en las otras 4
  pantallas que lo usan.
- El tope de 5 MB es sobre el tamaño **decodificado** (post-base64), igual que
  `/api/statements/upload` no lo limita explícitamente pero el chat sí lo necesitaba porque
  Claude cobra por tokens de imagen en cada turno del chat (a diferencia de un extracto, que
  se sube una sola vez).
