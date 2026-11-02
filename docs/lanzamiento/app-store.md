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
| Información de contacto → **Correo electrónico** | Sí | Funcionalidad de la app; comunicaciones (recordatorios y cierre del período, apagables) |
| Información financiera → **Otra información financiera** (cuentas, saldos, movimientos, créditos) | Sí | Funcionalidad de la app; Personalización |
| Información financiera → Información de pago / Solvencia | No | — |
| Contenido del usuario → **Correos electrónicos o mensajes de texto** (las alertas del banco que reenvía) | Sí | Funcionalidad de la app |
| Contenido del usuario → **Fotos o videos** (fotos de recibos que sube) | Sí | Funcionalidad de la app |
| Contenido del usuario → **Otro contenido del usuario** (extractos en PDF, preguntas a Movi AI, notas) | Sí | Funcionalidad de la app |
| Identificadores → **ID de usuario** | Sí | Funcionalidad de la app |
| Uso → **Interacción con el producto** (qué pantallas abre y qué toca; se puede apagar) | Sí | Análisis |
| Diagnóstico → **Datos de fallos** y **Otros datos de diagnóstico** (modelo y versión del sistema) | Sí | Funcionalidad de la app; Análisis |
| Ubicación, Salud, Contactos, Historial de navegación, Búsquedas, Compras, Datos sensibles | No | — |

---

## 3. Notas para el revisor (App Review Information)

**Iniciar sesión requerido:** sí. Usa la misma cuenta de demostración que se crea para Play
(`google-play.md`, «Acceso a la app»), o una igual con otro correo.

```
Sign in with the demo account below. It contains demonstration data only.

Movi is a personal finance app (Colombia, Spanish). It does not move money and does not connect to bank accounts: users register their accounts and movements, forward their bank alert emails to their Movi address, or upload statement PDFs.

Account deletion (guideline 5.1.1(v)): Tú (last tab) > scroll to the end > "Borrar mi cuenta". It asks for the password and the word BORRAR and deletes the account and all its data immediately. Web version: https://movi-project-production.up.railway.app/borrar-cuenta

Movi AI (the assistant) sends the user's question and a summary of their accounts to Anthropic's API (Claude); the app shows an explanation the first time. No data is used for advertising or tracking.
```

**Borrar la cuenta (guía 5.1.1(v)):** ya existe — Tú → al final, «Borrar mi cuenta»
(`ui/profile/BorrarLaCuentaScreen.kt`, `DELETE /api/me`): pide la contraseña y la palabra BORRAR,
ofrece antes la descarga y borra todo (`TABLAS_DEL_USUARIO`). No es un «desactivar».

**Iniciar sesión con Apple (guía 4.8):** no hace falta: Movi solo tiene cuenta propia (correo y
contraseña), sin entrar con Google ni con otra red.

---

## 4. Qué falta técnicamente para subir iOS

Lo que hay hoy en `iosApp/` (revisado el 2-nov-2026):

| Pieza | Estado |
| --- | --- |
| Proyecto | `iosApp/iosApp.xcodeproj`, una app SwiftUI (`iOSApp.swift`, `ContentView.swift`) que monta `MainViewController()` del framework `ComposeApp` |
| Bundle id | `com.jvillada.movi` (el mismo id que Android; en Apple hay que registrarlo como App ID) |
| Versión | `MARKETING_VERSION = 1.0`, `CURRENT_PROJECT_VERSION = 1` |
| Firma | `CODE_SIGN_STYLE = Automatic` con `DEVELOPMENT_TEAM = ""`: **falta el equipo** |
| Info.plist | Generado (`GENERATE_INFOPLIST_FILE = YES`), sin textos de uso (`NS…UsageDescription`) |
| Ícono | **No hay** `Assets.xcassets` ni `AppIcon` |
| Dispositivos | iPhone **y** iPad (`TARGETED_DEVICE_FAMILY = "1,2"`), iOS 16.0 o más |
| Framework | Apunta a `shared/build/XCFrameworks/debug/ComposeApp.xcframework` (el de depuración) |

**Lo que falta, en orden:**

1. **El equipo de firma.** Con la cuenta de Apple Developer aprobada (US$99 al año): Xcode →
   Settings → Accounts → agregar el Apple ID; en el target `iosApp` → Signing & Capabilities → elegir
   el equipo. Con firma automática, Xcode crea el App ID `com.jvillada.movi` y los certificados.
2. **El ícono.** Agregar `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset` con una imagen de
   **1024×1024 sin transparencia** (desde Xcode 14 basta una). Sale de `scripts/generar-icono.py`
   (la «m» en tinta sobre el amarillo): agregarle la salida de 1024 y conectarla al proyecto
   (`ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon`). Sin ícono, App Store Connect rechaza el archivo.
3. **El manifiesto de privacidad** (`PrivacyInfo.xcprivacy`). Apple lo exige desde mayo de 2024 a las
   apps que usan APIs «de razón requerida». Movi usa `NSUserDefaults` (por `multiplatform-settings`:
   razón `CA92.1`) y probablemente marcas de fecha de archivos (SQLite: razón `C617.1`). Declarar
   también lo recolectado (lo de la sección 2) y `NSPrivacyTracking = false`. Revisar el aviso que
   App Store Connect manda al subir el primer build: dice qué falta.
4. **Cifrado:** Movi solo usa HTTPS del sistema, que está exento. Agregar
   `INFOPLIST_KEY_ITSAppUsesNonExemptEncryption = NO` en los ajustes del target para no contestar la
   pregunta de exportación en cada build.
5. **Textos de uso del Info.plist:** **ninguno hace falta hoy.** Los archivos se eligen con
   `UIDocumentPickerViewController` (la app Archivos), que no pide permiso; no hay cámara, fotos,
   Face ID, ubicación ni contactos. Si algún día se agrega «Entrar con Face ID» en iOS, hará falta
   `NSFaceIDUsageDescription`.
6. **El tema.** `ContentView.swift` pide `.preferredColorScheme(.dark)`, pero Movi tiene un solo tema,
   claro, desde la vuelta 29: la barra de estado sale con letras claras sobre el fondo crema. Cambiarlo
   a `.light` (y verlo en el simulador) antes de sacar las capturas.
7. **iPad: sí o no.** Con `TARGETED_DEVICE_FAMILY = "1,2"`, App Store pide capturas de iPad de 13" y
   la revisión prueba en iPad. Si no se quiere cuidar eso ahora, dejar solo iPhone (`= 1`).
8. **El framework de release.** Para archivar, armar `./gradlew :shared:assembleComposeAppReleaseXCFramework`
   y apuntar el proyecto a `shared/build/XCFrameworks/release/ComposeApp.xcframework` en la
   configuración Release (hoy las dos apuntan al de depuración, que funciona pero es más lento y
   pesado). El link optimizado necesita mucha memoria: correrlo sin nada más abierto.
9. **Archivar y subir desde esta Mac** (Xcode 16 ya está instalado): elegir «Any iOS Device (arm64)» →
   Product → Archive → Organizer → «Distribute App» → App Store Connect → Upload. La Mac es Intel: el
   archivo se arma igual (Xcode compila para arm64), solo tarda más.
10. **TestFlight para invitar:** en App Store Connect → la app → TestFlight. Probadores **internos**
    (hasta 100 personas con acceso a tu cuenta de App Store Connect) sin revisión; **externos** (hasta
    10.000, por correo o con un enlace público) después de una revisión de TestFlight, que suele tardar
    uno o dos días. Para producción no hay un mínimo de probadores ni de días, a diferencia de Play.

## 5. El paso a paso

1. Inscribirse en <https://developer.apple.com/programs/enroll/> como **persona** (Apple ID con
   verificación en dos pasos; tu nombre legal queda como vendedor). La aprobación puede tardar de horas
   a un par de días.
2. Hacer los puntos 1 a 8 de arriba (en una rama, con su commit).
3. App Store Connect → Mis apps → «+» → Nueva app: plataforma iOS, nombre `Movi: tus finanzas`, idioma
   principal Español (México) o Español (España) —App Store no tiene «Latinoamérica»—, bundle id
   `com.jvillada.movi`, SKU `movi-ios`.
4. Llenar la ficha (sección 1), la privacidad (sección 2) y las notas para el revisor (sección 3).
5. Archivar y subir (punto 9), probar con TestFlight interno, invitar externos.
6. Con las capturas (iPhone 6,9": 1320×2868), mandar a revisión.
