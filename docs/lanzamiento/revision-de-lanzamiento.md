# Revisión de lanzamiento (4-nov-2026)

Revisión de seguridad, arquitectura, lenguaje y cumplimiento de Google Play y del App Store antes de
publicar. Se hizo leyendo el código; cada hallazgo cita su archivo. **Lo que se arregló en esta rama no
se pudo compilar acá** (el contenedor no llega al Maven de Google): lo corre CI al abrir el PR.

**Veredicto:** no hay nada en el código que por sí solo haga rechazar la app en las tiendas. Lo que
bloquea es operativo (§1). Lo de seguridad que más importaba antes de abrir el registro al público se
arregló (§2); lo que queda está ordenado en §3.

---

## 1. Bloquea el envío a revisión (lo hace el dueño)

| # | Qué | Por qué |
| --- | --- | --- |
| B1 | Poner `RESPONSABLE_LEGAL` y `CORREO_DE_CONTACTO` en Railway y desplegar | Sin contacto, `/privacidad` y `/borrar-cuenta` muestran un marcador: rechazo en las dos tiendas |
| B2 | La cuenta de demostración con datos, en producción, y sus credenciales en las dos consolas | Una app con login obligatorio sin credenciales que funcionen se rechaza (`google-play.md` «Acceso a la app», `app-store.md`) |
| B3 | Prueba cerrada de Play: 12 testers durante 14 días (cuenta personal) | Requisito de Play para cuentas personales nuevas (`google-play.md`) |
| B4 | Confirmar `ALLOWED_ORIGINS` en Railway | Sin ella el CORS queda abierto (`plugins/CORS.kt:28`); no hay cookies, pero no debe quedar así |
| B5 | Capturas de pantalla de las fichas | Pendientes desde la vuelta 109 (`README.md`) |
| B6 | Que un abogado lea `/privacidad` y `/terminos` | Son un borrador cuidadoso, no asesoría legal |

---

## 2. Arreglado en esta revisión

**Tiendas**

- **El canal `play` ya no pide `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** (`androidApp/src/play/AndroidManifest.xml`).
  Era el riesgo de rechazo más alto: Play solo lo acepta en casos puntuales. «Quitar el ahorro de
  batería» abre ahora la lista del sistema, que no pide permiso; la app mira su propio manifiesto
  (`pedirLaExencionDeBateria`, `sensor/AccesoANotificaciones.kt`), porque sin el permiso el diálogo
  directo se cierra solo y el respaldo anterior nunca se activaba. `verificaElManifiestoDePlay*` falla
  si el permiso vuelve.
- **La explicación del acceso a notificaciones dice adónde va el texto**: «se envía a tu cuenta de
  Movi, en el servidor de Movi» (`SmsSensorSetupSection.android.kt`). La «divulgación destacada» de
  Play pide decir qué se recoge **y cómo se usa o se comparte**, antes del botón.
- **«Qué ve la IA» pide un sí explícito**: «Aceptar y continuar» (antes «Entendido») y «Ahora no» a la
  vista (`ui/ai/AvisoDeLaIa.kt`). Apple, guía 5.1.2(i): permiso explícito antes de compartir datos
  personales con una IA de terceros.
- **El respaldo de Android ya no se lleva los avisos del banco sin subir** (`movi_capturas_por_subir.xml`,
  con el texto de SMS y notificaciones) a la nube de Google: excluido en `backup_rules.xml` y
  `data_extraction_rules.xml`, y vigilado por `RespaldoDeAndroidTest`.
- **«Compartir con Movi» solo lee `content://` de otra app** (`compartir/LoQueLlegaCompartido.kt`).
  `MainActivity` está exportada: un `file://` o un proveedor propio le hacía leer a Movi sus propios
  archivos (la base, el token) y subirlos como un papel. Es un patrón que marca el análisis de
  seguridad de Play.
- Una palabra en inglés en la interfaz: «El checklist completo» → «La lista completa».

**Server**

- **Login, registro y recuperación cuentan por dirección** (`routes/AuthRoutes.kt`, `pasaElTope`). Antes
  la clave era el peer TCP, que detrás de Railway es uno solo: 60 logins malos de un script dejaban a
  todo internet sin entrar, y 10 registros cerraban el registro. Ahora se cuenta por el valor de la
  **derecha** de `X-Forwarded-For` (el que agrega el borde; el cliente no lo elige, `ipDelPedido`, el
  mismo de la captura por atajo), con un techo de todos más alto que solo frena una inundación. El
  balde por correo no cambió. **Verificar en producción** que el borde de Railway agrega la dirección
  al final (los atajos ya dependen de eso).
- **Suscripciones push solo a servicios de push reales, por https** (`routes/PushRoutes.kt`,
  `esEndpointDePush`; también al enviar, `push/WebPushSender.kt`). Antes el server hacía un POST a
  cualquier URL guardada por un usuario: servía para sondear la red privada de Railway (SSRF).
- **La subida de extractos lee con tope** (`routes/StatementRoutes.kt`): `readBytes()` metía el archivo
  entero en memoria antes de mirar los 10 MB; usa el mismo `leerConTope` de Documentos.
- **Tope de lecturas de papeles con IA** (`ai/ElTopeDeLosPapeles.kt`): ver §3, punto 1.
- **Cabeceras de seguridad en toda respuesta** (`plugins/CabecerasDeSeguridad.kt`): `nosniff`, sin
  iframes ajenos (`X-Frame-Options` + `frame-ancestors`), `Referrer-Policy` y HSTS (sin
  `includeSubDomains`: `up.railway.app` es compartido). Lo que una ruta ya ponía gana.

---

## 3. Pendiente, por prioridad

### Alto (antes de abrir el registro a desconocidos)

1. **El gasto en IA — hecho, salvo el correo.** Dos topes: las lecturas de papeles por hora
   (`ai/ElTopeDeLosPapeles.kt`: 15 por usuario y 200 de todos, 429 «Movi leyó muchos papeles…») y el
   **gasto diario** de toda la IA (`ai/ElGastoDelDia.kt`: `IA_GASTO_MAXIMO_DIARIO_USD`, 25 por defecto, `0`
   lo apaga; suma el costo de cada llamada con `PreciosDeLaIa`, arranca el día desde `ia_por_dia` y,
   pasado el tope, `crearMensaje` no llama y todo contesta «sin crédito» hasta el día siguiente; las tres
   preguntas sugeridas siguen). Falta verificar el correo antes de dar IA, y revisar que la app muestre bien
   el 429 de los papeles.
2. **Migraciones sin versión.** `DatabaseFactory.init()` hace `SchemaUtils.create` +
   `createMissingTablesAndColumns` (~50 tablas) en cada arranque. No se puede renombrar, borrar ni
   revertir, no queda registro de qué se aplicó, y un ALTER que falla deja el server sin arrancar.
   Hacer: Flyway con el esquema de hoy como línea base V1.
3. **Una sola réplica, a propósito.** El tope de pedidos (`auth/RateLimiter.kt`), el tope del mes de la
   IA (`ai/ElTopeDelMes.kt`), el estado de la IA, las métricas y las alertas viven en memoria, y los
   barridos corren en cada instancia. Con dos réplicas los topes se multiplican y los recordatorios
   salen dos veces. Hacer: dejarlo escrito en `railway.toml`; si algún día se escala, los topes van a
   Postgres y los jobs con `pg_advisory_lock`.
4. **PDF que se infla al leerse.** `Loader.loadPDF(bytes)` + `PDFTextStripper` sin tope de memoria ni de
   tiempo (`parsing/StatementParser.kt`, `documentos/PdfConClave.kt`). Un PDF de 10 MB bien armado puede
   llenar el heap del único proceso. Hacer: `IOUtils.createTempFileOnlyStreamCache()`, `withTimeout` y
   un `Semaphore` de lecturas a la vez. (POI ya tiene sus topes.)

### Medio

5. **R8 apagado y AGP 8.7.3 contra el SDK 36** (`androidApp/build.gradle.kts:129`,
   `android.suppressUnsupportedCompileSdk=36`). No es motivo de rechazo, pero la app sale sin reducir
   (~17 MB) y la combinación no está soportada. Hacer: subir AGP, prender R8 con reglas para
   kotlinx-serialization, Ktor y SQLDelight, y probar el AAB en un teléfono con `bundletool`.
6. **Dependencias.** **Probado y revertido (4-nov):** con POI 5.4.1 + logback 1.5.38 falla `CerrarSesionesTest` («cerrar sesiones deja afuera a todos los tokens…») de forma repetible; hay que reproducirlo antes de subirlos. Subir Ktor a 3.2.x o más nueva (trae Netty ≥ 4.1.125: CVE-2025-58056/55163; Ktor
   3.0.3 tiene CVE-2025-29904), POI a ≥ 5.4.0 (CVE-2025-31672) y logback a ≥ 1.5.19. Exposed 0.55 está
   atrasado. Koin está declarado en `server/build.gradle.kts` y nadie lo usa.
7. **El token de la web vive en `localStorage`** y la web no tiene una CSP de scripts (el `index.html`
   tiene scripts en línea). Cualquier XSS futuro se lleva una sesión de 30 días. Hacer: sacar los
   scripts a un archivo y poner `script-src 'self' 'wasm-unsafe-eval'`.
8. **La dirección de reenvío de correo no se puede revocar**: es `sha256("movi-correo-entrante:"+userId)`
   sin secreto del server (`correo/CorreoEntrante.kt:270`). Con Resend, cualquiera que la sepa mete avisos
   falsos en «Por revisar» (nada se anota solo). Hacer: HMAC con un secreto y un token rotable, como el
   código de los atajos.
9. **`runCatching` atrapa la cancelación de corrutinas** (676 usos). Ya causó un bug (vuelta 89). Hacer:
   un `runCatching` que relance `CancellationException` y una prueba que busque `runCatching` en `suspend`.
10. **Sin versión de API.** La compatibilidad con APKs viejos va por 8 banderas en la URL (`?conAbonos`,
    `?conFrecuencias`…). Hacer: el cliente manda su versión en una cabecera y el server tiene una versión
    mínima que obliga a actualizar; después se dan de baja las banderas.
11. **`:androidApp` no tiene pruebas.** Los Workers de captura (`SmsSyncWorker`, `VigiaDeLaEscucha`…) solo
    tienen probada su lógica pura en `:shared`. Hacer: Robolectric + `WorkManagerTestInitHelper`.
12. **iOS guarda el token en `NSUserDefaults`**, que viaja al respaldo de iCloud. No es motivo de rechazo;
    mejor el Keychain.

### Bajo

13. El contenedor corre como root (`Dockerfile`, `scripts/despliegue-runtime/Dockerfile.plantilla`).
14. `JWT_SECRET` no tiene largo mínimo (`auth/JwtConfig.kt`): exigir ≥ 32 bytes al arrancar (antes,
    comprobar el de producción).
15. Los topes de `/api/fallos` y `/api/errores-del-cliente` son uno solo para todos: un tercero puede
    silenciar el reporte de fallos. Mismo arreglo que el login.
16. El registro dice si un correo ya tiene cuenta (409, decisión del dueño) y no verifica el correo.
17. Archivos de más de 1.500 líneas con lógica de plata (`QuickAddScreen.kt` 3.119, `LocalRepository.kt`
    2.927, `ReminderRoutes.kt` 1.845) y rutas que mezclan HTTP, SQL y reglas (578 `dbQuery` en `routes/`).
    Partirlos de a poco, empezando por esos tres.
18. `CLAUDE.md` pesa 267 KB y se volvió un registro de cambios: llevar la historia a `docs/decisiones/` y
    dejar ahí la estructura, los comandos y las reglas vigentes.
19. Todo el texto está escrito en el código (cero `stringResource`): localizar algún día sería reescribir
    miles de literales. Aceptable si no está en los planes.
20. Datos de uso encendidos por defecto y declarados «Opcional» en Seguridad de los datos: se pueden
    apagar en Ajustes y la política lo dice, así que alcanza; para ir a lo seguro, declararlos «Obligatorio».

---

## 4. Verificado y bien (no rehacer)

- **Sin IDOR**: se muestrearon cuentas, movimientos, documentos, grupos, terceros, reglas, apartados,
  conversaciones de IA, SMS y extractos; todo filtra por el usuario, y un id del cliente que es de otro
  da 409.
- **Autenticación**: JWT HS256 con algoritmo, emisor y audiencia fijos; versión de sesión revisada en cada
  pedido (sube al cambiar la contraseña y con «cerrar sesiones»); BCrypt costo 12 y mínimo de 12
  caracteres; el reset de contraseña es de un solo uso, hasheado, de 1 h y no enumera.
- **Webhooks y rutas públicas**: secretos comparados en tiempo constante, firma Svix correcta sobre el
  cuerpo crudo con 5 min de tolerancia, secretos nunca en la URL, topes de cuerpo; atajos con código de
  256 bits guardado como hash; `/compartido` con el token en el fragmento.
- **Datos**: papeles cifrados con AES-256-GCM; SQL sin datos del pedido concatenados; nada de cuerpos,
  tokens ni contraseñas en el log; sin secretos en el repo; el borrado de la cuenta recorre todas las
  tablas del usuario (una prueba lo exige).
- **Android**: `targetSdk` 36, sin tráfico en claro en release, el canal Play quita SMS y lo vigila el
  build, `PendingIntent` inmutables, componentes no exportados salvo los que deben, token y base local
  fuera del respaldo.
- **iOS**: `PrivacyInfo.xcprivacy` coherente con las etiquetas, cifrado exento, sin permisos que
  declarar, sin login de terceros (no hace falta Iniciar sesión con Apple), sin pagos, borrar la cuenta
  desde la app.
- **Arquitectura**: `:core` no importa Compose ni Android, el server no depende de `:shared`, sin ciclos;
  las reglas de plata viven una vez en `:core`; la base no bloquea Netty (`dbQuery` en
  `Dispatchers.IO`); 500 sin cuerpo (no se filtran trazas).
- **Lenguaje**: sin voseo en la interfaz (`VoseoScanTest`), sin inglés visible salvo el que se arregló;
  «Movi» en Android y en iOS.
