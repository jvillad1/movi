# App Store (iPhone)

La versión de iOS es la misma app de Compose (`:shared`) dentro de la cáscara Swift de `iosApp/`. En
iPhone **no hay captura de SMS ni de notificaciones** (iOS no lo permite): Movi se entera por los
correos reenviados del banco y los extractos, y todo lo demás es igual que en Android y en la web.

---

## 1. Ficha

**Nombre** (máx. 30): `Movi: tus finanzas` (18)

**Subtítulo** (máx. 30): `Cuánto te queda y qué vence` (27)

**Texto promocional** (máx. 170, se cambia sin nueva versión):

```
Movi te dice cuánta plata te queda hasta que te paguen, qué pagos vienen y en qué se te va. Sin publicidad y sin vender tus datos.
```

**Descripción** (máx. 4000):

```
Movi te dice, de un vistazo, cuánta plata te queda hasta que te paguen, qué pagos vienen y en qué se te está yendo. Sin publicidad y sin vender tus datos.

CUÁNTO TE QUEDA
• «Te quedan» en el inicio: lo que tienes, menos tus pagos fijos del período, menos lo que quieres ahorrar.
• ¿Me alcanza hasta que me paguen? Movi proyecta tus cuentas día por día y te dice tu día más justo.
• Tu período empieza el día que te pagan (y se corre solo si cae en fin de semana o festivo).

TUS PAGOS, A TIEMPO
• Arriendo, servicios, tarjetas y cuotas de créditos en una lista, con lo vencido primero.
• Tarjetas de crédito con su corte, su pago mínimo y lo que falta del extracto.
• Créditos con su plan: cuánto debes, cuándo terminas y cuánto te ahorra un abono extra.

QUE MOVI SE ENTERE SOLO
• Correos del banco: reenvía las alertas de tu banco a tu dirección de Movi y te quedan en «Por revisar».
• Extractos: sube el PDF de tu banco y Movi te propone los movimientos. Tú confirmas cada uno: nada se anota solo.

EN QUÉ SE VA
• Gastos por categoría, comercio y cuenta, comparados con el período pasado.
• Lo que se sale de lo normal: un cobro repetido, una suscripción que subió, una categoría disparada.
• Tu patrimonio en el tiempo: cuentas, inversiones, bienes y deudas.

GASTOS COMPARTIDOS
• Grupos con tu pareja o tu familia: Movi cuenta solo tu parte y te dice quién le debe a quién.

MOVI AI
• Pregúntale «¿cómo voy este período?» o «¿qué me recomiendas revisar primero?». Contesta con tus cifras y nunca anota nada sin que lo confirmes.

TUS DATOS SON TUYOS
• Movi no es un banco y no mueve tu plata: no se conecta a tus cuentas bancarias.
• Descarga una copia de tus cuentas, movimientos, créditos y mensajes del banco, o borra tu cuenta cuando quieras, desde la app.
• Oculta los saldos con un toque.

Funciona también en el navegador y en Android con la misma cuenta.
```

**Palabras clave** (máx. 100 caracteres, separadas por coma, sin espacios después de la coma):

```
presupuesto,gastos,finanzas,ahorro,plata,deudas,tarjeta,crédito,pagos,cuentas,quincena,extracto
```
(95 caracteres)

**Categoría:** Finanzas (secundaria: Productividad). **Clasificación por edad:** 4+ en el cuestionario
(no hay contenido objetable); la app igual está pensada para mayores de 18.
**URL de soporte:** `https://movi-project-production.up.railway.app/terminos` (tiene el contacto)
**URL de la política de privacidad:** `https://movi-project-production.up.railway.app/privacidad`
**Copyright:** `2026 <tu nombre>`.

---

## 2. Etiquetas de privacidad (App Privacy)

**¿Recopilas datos de esta app?** Sí. **¿Se usan para rastrear (tracking)?** **No** (sin publicidad, sin
SDK de terceros, nada se cruza con datos de otras empresas). **Todos los datos de abajo están
vinculados a la identidad del usuario** (su cuenta).

| Tipo (App Store Connect) | Se recopila | Propósitos |
| --- | --- | --- |
| Información de contacto → **Nombre** | Sí | Funcionalidad de la app |
| Información de contacto → **Correo electrónico** | Sí | Funcionalidad de la app (incluye los recordatorios y el cierre del período por correo, apagables) |
| Información financiera → **Otra información financiera** (cuentas, saldos, movimientos, créditos) | Sí | Funcionalidad de la app; Personalización |
| Información financiera → Información de pago / Solvencia | No | — |
| Contenido del usuario → **Correos electrónicos o mensajes de texto** (las alertas del banco que reenvía) | Sí | Funcionalidad de la app |
| Contenido del usuario → **Fotos o videos** (fotos de recibos que sube) | Sí | Funcionalidad de la app |
| Contenido del usuario → **Otro contenido del usuario** (extractos en PDF, preguntas a Movi AI, notas) | Sí | Funcionalidad de la app |
| Identificadores → **ID de usuario** | Sí | Funcionalidad de la app |
| Uso → **Interacción con el producto** (qué pantallas abre y qué toca; se puede apagar) | Sí | Análisis |
| Diagnóstico → **Datos de fallos** y **Otros datos de diagnóstico** (modelo y versión del sistema) | Sí | Funcionalidad de la app; Análisis |
| Ubicación, Salud, Contactos, Historial de navegación, Búsquedas, Compras, Datos sensibles | No | — |

Apple no tiene un propósito «comunicaciones»: los correos de recordatorio y de cierre son parte de lo
que la app hace (no son publicidad ni marketing), así que van en «Funcionalidad de la app». Esta tabla
y `iosApp/iosApp/PrivacyInfo.xcprivacy` dicen lo mismo; `ElProyectoDeIphoneTest` lo vigila (si cambias
una fila, cambia el manifiesto).

---

## 3. Notas para el revisor (App Review Information)

**Iniciar sesión requerido:** sí. Usa la misma cuenta de demostración que se crea para Play
(`google-play.md`, «Acceso a la app»), o una igual con otro correo.

```
Sign in with the demo account below. It contains demonstration data only.

Movi is a personal finance app (Colombia, Spanish). It does not move money and does not connect to bank accounts: users register their accounts and movements, forward their bank alert emails to their Movi address, or upload statement PDFs.

Account deletion (guideline 5.1.1(v)): Tú (last tab) > scroll to the end > "Borrar mi cuenta". It asks for the password and the word BORRAR and deletes the account and all its data immediately. Web version: https://movi-project-production.up.railway.app/borrar-cuenta

Movi AI (the assistant) sends the user's question and a summary of their accounts to Anthropic's API (Claude); the app shows an explanation the first time. No data is used for advertising or tracking.

The app does not read messages by itself. Optionally, the user can create a personal automation in Apple's Shortcuts app that sends a bank text message (or an Apple Pay transaction) to their Movi account; the user creates and runs it, and the app only offers the shortcut and the steps.
```

(La última línea es por los Atajos de la vuelta 111: `docs/lanzamiento/atajos-de-iphone.md`.)

**Borrar la cuenta (guía 5.1.1(v)):** ya existe — Tú → al final, «Borrar mi cuenta»
(`ui/profile/BorrarLaCuentaScreen.kt`, `DELETE /api/me`): pide la contraseña y la palabra BORRAR,
ofrece antes la descarga y borra todo (`TABLAS_DEL_USUARIO`). No es un «desactivar».

**Iniciar sesión con Apple (guía 4.8):** no hace falta: Movi solo tiene cuenta propia (correo y
contraseña), sin entrar con Google ni con otra red.

---

## 4. Qué falta técnicamente para subir iOS

Lo que hay hoy en `iosApp/` (revisado el 2-nov-2026, vueltas 112 y 115):

| Pieza | Estado |
| --- | --- |
| Proyecto | `iosApp/iosApp.xcodeproj`, una app SwiftUI (`iOSApp.swift`, `ContentView.swift`) que monta `MainViewController()` del framework `ComposeApp` |
| Bundle id | `com.jvillada.movi` (el mismo id que Android; en Apple hay que registrarlo como App ID) |
| Versión | `MARKETING_VERSION = 1.0`, `CURRENT_PROJECT_VERSION = 1` (súbelo en cada build que mandes) |
| Firma | `CODE_SIGN_STYLE = Automatic`; el equipo **no** va en el repo: lo pone el script con `MOVI_APPLE_TEAM_ID` |
| Info.plist | Generado (`GENERATE_INFOPLIST_FILE = YES`), sin textos de uso (`NS…UsageDescription`): ninguno hace falta (los archivos se eligen con la app Archivos, que no pide permiso; no hay cámara, fotos, Face ID, ubicación ni contactos) |
| Ícono | ✓ `Assets.xcassets/AppIcon.appiconset/movi-1024.png` (vuelta 110), sale de `scripts/generar-icono.py` |
| Tema | ✓ Claro: `UIUserInterfaceStyle = Light` y `.preferredColorScheme(.light)` (vuelta 110) |
| Manifiesto de privacidad | ✓ `iosApp/iosApp/PrivacyInfo.xcprivacy`, en Resources: `NSPrivacyTracking = false`, los diez tipos de la §2 y las APIs de razón requerida medidas con `nm -u` sobre el framework: `UserDefaults` (`CA92.1`: la app y `multiplatform-settings`), marcas de archivo (`C617.1`: `stat`/`fstat` de Skia e ICU sobre archivos del propio paquete) y tiempo de arranque (`35F9.1`: `mach_absolute_time` del temporizador del SDK de DNG de Skia). Ni Compose, ni Ktor, ni SQLDelight traen un manifiesto propio dentro del framework (es estático): el de la app cubre todo. Lo vigila `ElProyectoDeIphoneTest` |
| Cifrado | ✓ `ITSAppUsesNonExemptEncryption = NO`: el cliente solo usa HTTPS del sistema (`NSURLSession`, por Ktor Darwin); no enlaza CommonCrypto ni Security. El cifrado de documentos (AES) vive en el server |
| Dispositivos | ✓ Solo iPhone (`TARGETED_DEVICE_FAMILY = 1`), **en vertical** (`UIInterfaceOrientationPortrait`), iOS 16.0 o más. Sin iPad no hacen falta capturas de iPad, **pero la revisión igual puede probarla en un iPad** en modo compatibilidad (guía 2.4.1: una app de iPhone tiene que funcionar ahí, en una ventana de iPhone); por eso la hoja de compartir ya cuida el iPad (`HojaDeCompartir.ios.kt`) |
| Framework | ✓ Por configuración (`MOVI_XCFRAMEWORK_DIR`): Debug toma `shared/build/XCFrameworks/debug`, Release `shared/build/XCFrameworks/release` |
| Letra | ✓ **Figtree, como en Android y la web** (vuelta 115): las capturas de la App Store salen con la letra de Movi. El framework es estático y no lleva las fuentes; Gradle las deja junto al XCFramework (`XCFrameworks/<config>/compose-resources`, tarea `:shared:recursosDeComposeParaIphone<Debug\|Release>`, que corre sola después de armar el XCFramework) y la fase «Copiar las fuentes de Movi» del proyecto las copia a `iosApp.app/compose-resources/composeResources/com.jvillada.movi.resources/font/`, donde Compose las busca. Sin ellas el build de Xcode falla; si igual faltara una, la app abre con la letra del sistema y lo anota en sus fallos. El script de archivar comprueba las cinco dentro del `.xcarchive`. Lo vigila `LasFuentesDelIphoneTest` |
| Archivar | ✓ `scripts/armar-para-app-store.sh`: arma el XCFramework de release (con sus fuentes) y corre `xcodebuild archive` para «Any iOS Device» con el equipo de `MOVI_APPLE_TEAM_ID` (sin la variable, no hace nada y lo dice) |

**Lo que falta (todo es del dueño):**

1. **La cuenta de Apple Developer** (US$99 al año, sección 5, paso 1).
2. **El equipo en Xcode.** Xcode → Settings → Accounts → agregar el Apple ID. El Team ID (10 letras y
   números) está en developer.apple.com → Account → Membership details. No hace falta tocar el
   proyecto: el script lo pasa como `DEVELOPMENT_TEAM`, y con firma automática Xcode crea el App ID
   `com.jvillada.movi` y los certificados.
3. **Correr el script** con todo lo demás cerrado (el link optimizado de Kotlin/Native usa mucha
   memoria y tarda en esta Mac Intel):
   `MOVI_APPLE_TEAM_ID=XXXXXXXXXX ./scripts/armar-para-app-store.sh`. Si la firma falla después del
   link, reintenta con `--sin-framework` para no volver a armar el framework.
4. **Subir:** `open build/ios/Movi.xcarchive` → Organizer → «Distribute App» → App Store Connect →
   Upload. Revisar el correo que App Store Connect manda al procesar el build: si dice que falta algo
   del manifiesto, se agrega ahí.
5. **TestFlight para invitar:** en App Store Connect → la app → TestFlight. Probadores **internos**
   (hasta 100 personas con acceso a tu cuenta de App Store Connect) sin revisión; **externos** (hasta
   10.000, por correo o con un enlace público) después de una revisión de TestFlight, que suele tardar
   uno o dos días. Para producción no hay un mínimo de probadores ni de días, a diferencia de Play.

## 5. El paso a paso

1. Inscribirse en <https://developer.apple.com/programs/enroll/> como **persona** (Apple ID con
   verificación en dos pasos; tu nombre legal queda como vendedor). La aprobación puede tardar de horas
   a un par de días.
2. Correr `scripts/armar-para-app-store.sh` con `MOVI_APPLE_TEAM_ID` (sección 4, puntos 2 y 3).
3. App Store Connect → Mis apps → «+» → Nueva app: plataforma iOS, nombre `Movi: tus finanzas`, idioma
   principal Español (México) o Español (España) —App Store no tiene «Latinoamérica»—, bundle id
   `com.jvillada.movi`, SKU `movi-ios`.
4. Llenar la ficha (sección 1), la privacidad (sección 2) y las notas para el revisor (sección 3).
5. Subir el archivo (sección 4, punto 4), probar con TestFlight interno, invitar externos.
6. Con las capturas (iPhone 6,9": 1320×2868), mandar a revisión.
