# Lanzamiento: Google Play y App Store

Todo lo que no depende de tener las cuentas de las tiendas quedó listo en la vuelta 109 (2-nov-2026).
Mañana es crear las cuentas, pegar los textos de estos archivos y subir.

| Archivo | Para qué |
| --- | --- |
| [`google-play.md`](google-play.md) | Ficha, Seguridad de los datos, clasificación, acceso para revisores, prueba cerrada y el paso a paso de Play Console |
| [`declaracion-sms.md`](declaracion-sms.md) | Para DESPUÉS: el texto y el guion del video para pedir la excepción «SMS-based money management» |
| [`app-store.md`](app-store.md) | Ficha, etiquetas de privacidad, notas para el revisor y lo que falta técnicamente para subir iOS |

> **Los textos legales (`/privacidad`, `/terminos`, `/borrar-cuenta`) son un borrador cuidadoso, no
> asesoría legal.** Están escritos a partir de lo que Movi hace de verdad (el código y `CLAUDE.md`) y
> siguen la Ley 1581 de 2012, pero conviene que los lea un abogado colombiano antes de que la app sea
> pública para gente que no conoces.

## El orden de mañana

1. **Antes de las tiendas, en Railway** (servicio `movi-project`): poner `RESPONSABLE_LEGAL` y
   `CORREO_DE_CONTACTO` (ver abajo) y desplegar esta rama con `./scripts/desplegar.sh` cuando esté en
   master. Comprobar que <https://movi-project-production.up.railway.app/privacidad>,
   `/terminos` y `/borrar-cuenta` abren y dicen tu nombre y tu correo, no los marcadores.
2. **Google Play primero** (`google-play.md`): crear la cuenta, crear la llave de subida, armar el AAB
   y subirlo a **prueba interna**. Ese mismo día puedes instalarlo desde Play en tu teléfono.
3. **Apple después** (`app-store.md`): la cuenta de Apple Developer tarda en aprobarse (verificación
   de identidad), y a iOS le faltan piezas técnicas (ícono, manifiesto de privacidad, firma). Se puede
   arrancar en paralelo, pero no bloquea a Play.

## Los secretos que hay que crear

| Qué | Dónde vive | Cómo se crea | Respaldo |
| --- | --- | --- | --- |
| **Llave de subida de Play** (`~/.movi/llave-de-subida.jks`) | Tu Mac, fuera del repo (permisos 600) | `./scripts/crear-llave-de-subida.sh` (una sola vez; no pisa una que exista) | El `.jks` **y** su contraseña en tu gestor de contraseñas, o en una memoria USB guardada aparte. El script imprime el SHA-256 del certificado: anótalo. |
| Sus cuatro propiedades (`MOVI_UPLOAD_STORE_FILE`, `MOVI_UPLOAD_STORE_PASSWORD`, `MOVI_UPLOAD_KEY_ALIAS`, `MOVI_UPLOAD_KEY_PASSWORD`) | `~/.gradle/gradle.properties` (o variables de entorno) | Las escribe el mismo script | Con la contraseña de arriba |
| Firma de apps de Play (la llave que firma lo que llega a los teléfonos) | Google | Play Console la crea al subir el primer AAB («Firma de apps de Play») | La guarda Google |
| Certificados de Apple | Tu llavero de macOS | Xcode los crea solo con «Automatically manage signing» al elegir tu equipo | Apple los puede reemitir |

**Si pierdes la llave de subida** no pierdes la app: Play Console → Integridad de la app → «Solicitar
restablecimiento de la llave de subida». Tarda unos días.

**El APK de Drive no cambia:** sigue siendo el de depuración (`./scripts/build-apk.sh`, ahora del
canal `directa`), con SMS, firmado con la llave de depuración de siempre. Por eso **la versión de
Drive y la de Play no se actualizan la una a la otra** (mismo `applicationId`, firmas distintas):
quien tiene la de Drive tiene que desinstalarla para instalar la de Play. Ver «Quién prueba qué» en
`google-play.md`.

## Variables de entorno nuevas del server

| Variable | Qué hace | Sin ella |
| --- | --- | --- |
| `RESPONSABLE_LEGAL` | El nombre del responsable del tratamiento de datos (Ley 1581) en `/privacidad` y `/terminos` | La página dice «el responsable de Movi» |
| `CORREO_DE_CONTACTO` | El correo para consultas, reclamos y pedir el borrado sin acceso, en las tres páginas | La página dice «el correo de contacto de Movi (pendiente de publicar)»; un valor sin forma de correo se ignora |

Las dos aparecen en el panel de salud del admin (Fallos y uso → Servidor) como puestas o no, sin su
valor. **Ponlas antes de mandar nada a revisión**: una política de privacidad sin contacto es motivo
de rechazo en las dos tiendas. Usa un correo que vayas a leer (puede ser uno nuevo solo para Movi).

## Lo que cambió en el código (vuelta 109)

- **Dos canales de Android** (`androidApp`, dimensión `canal`): `directa` (Drive, con SMS, el de
  siempre) y `play` (sin `READ_SMS`/`RECEIVE_SMS` ni el receiver de SMS). La app decide qué ofrecer
  leyendo su propio manifiesto (`elAparatoPuedeLeerSms`), no un flag de build.
- **`targetSdk` y `compileSdk` 36**: lo que Play exige a una app nueva desde el 31-ago-2026.
- **`scripts/armar-para-play.sh`**: el AAB firmado, con la verificación del manifiesto (nada de SMS)
  y del dex (todas las clases de Movi adentro). `--sin-firmar` para ensayar.
- **`/privacidad`, `/terminos`, `/borrar-cuenta`** en el server, y en la app: Tú → Cuenta →
  «Privacidad» y «Términos de uso», y la línea «Al crear tu cuenta aceptas…» en el registro.

## Lo pendiente

- **R8 (minificar el release) sigue apagado.** El AAB pesa ~17 MB; con R8 bajaría bastante, pero un
  release minificado hay que probarlo en un teléfono de verdad (una regla de keep que falte crashea
  al abrir, no en el build), y en esta Mac no hay emuladores. Cuando se haga: `isMinifyEnabled = true`
  en `release`, reglas para kotlinx.serialization, Ktor y SQLDelight, y probar el AAB con
  `bundletool` en el teléfono antes de subirlo.
- **Subir AGP** a una versión que conozca la API 36 (hoy 8.7.3 compila contra 36 con
  `android.suppressUnsupportedCompileSdk=36`; APK, AAB y Robolectric pasan).
- **Capturas de pantalla para las fichas.** No hay en el repo un script documentado para sacarlas, y
  en esta Mac no se compila el wasm de distribución ni se abren emuladores. Camino propuesto: bajar el
  bundle web que arma GitHub Actions (`paquete.yml`), servirlo con la API simulada y sacar las
  capturas con Playwright a 1080×2400 (Play) y 1320×2868 (iPhone 6,9"); o, más simple, tomarlas en tu
  teléfono con la cuenta de demostración (`google-play.md`, «Acceso a la app»). Play pide de 2 a 8
  capturas de teléfono; Apple, al menos una de 6,9" (y de iPad si la app se publica para iPad).
- **La excepción de SMS en Play** (`declaracion-sms.md`): se pide después de que la app esté en
  producción sin SMS. Antes de pedirla, conviene angostar el filtro de la variante de Play (ver ahí).
- **El ahorro de batería en Play.** La versión de Play conserva `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  (la tarjeta «Quitar el ahorro de batería» de Captura del banco). Play lo permite solo si el ahorro de
  batería rompe la función central; para Movi es defendible (Android desenchufa la lectura de
  notificaciones del banco), pero si la revisión lo objeta, quitarlo del manifiesto de `play` con
  `tools:node="remove"` y abrir en su lugar `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
- **iOS**: el proyecto está listo (vueltas 110 y 112: ícono, tema claro, `PrivacyInfo.xcprivacy`,
  cifrado exento, solo iPhone en vertical y el framework de release). Falta lo del dueño: la cuenta, el
  equipo y correr `scripts/armar-para-app-store.sh` (ver «Qué falta técnicamente» en `app-store.md`).
