# La excepción de SMS en Google Play (para después)

**Primero Movi sale en Play sin SMS** (canal `play`). Esto es para cuando ya esté en producción y se
quiera pedir la excepción, que puede tardar semanas o ser rechazada.

## Lo que dice la política (consultada el 2-nov-2026)

Fuente: «Use of SMS or Call Log permission groups»,
<https://support.google.com/googleplay/android-developer/answer/10208820>, y el formulario,
<https://support.google.com/googleplay/android-developer/answer/9214102>.

- Solo la app predeterminada de SMS puede pedir los permisos de SMS, salvo las **excepciones
  temporales** que Play aprueba «cuando el permiso habilita la función central y no existe hoy un
  método alternativo».
- El nombre exacto de la excepción es **«SMS-based money management»**. Permite `READ_SMS`,
  `RECEIVE_SMS`, `RECEIVE_MMS` y `RECEIVE_WAP_PUSH` (nada de enviar ni escribir SMS). El ejemplo que da
  la política: apps de presupuesto y administración del dinero.
- Condición: aplica la política de software espía; **«personal loans or budgeting apps may not
  exfiltrate or share non-financial or personal SMS history of a user»**: una app de presupuesto no
  puede sacar del teléfono SMS que no sean financieros.
- (Hay otra, «SMS-based financial transactions», que incluye `SEND_SMS`; es para pagos tipo UPI y no
  aplica a Movi.)
- Se declara en Play Console con el **formulario de declaración de permisos**: casos de uso, pasos para
  el revisor (opcional), **video obligatorio** (enlace de YouTube, preferido, o de un almacenamiento en
  la nube con un mp4), acceso a la app (credenciales) y confirmación. La revisión **puede tardar varias
  semanas**, y mientras está pendiente **no se puede publicar ningún cambio de la app** que lleve esos
  permisos; quitar los permisos es la única salida rápida.

## El filtro: qué SMS salen del teléfono (vuelta 113)

La condición de la política es que **ningún SMS personal salga del teléfono**. Desde la vuelta 113 la
regla, en **todas** las versiones de Android que leen SMS (la de Drive y, cuando se apruebe, la de Play),
es una sola función de `:core`, `seSubeElSms` (`FiltroDeSms.kt`), que usan la captura en tiempo real
(`SmsRealtimeReceiver`) y el barrido del inbox (`SmsBackfillWorker`). Un SMS sale del teléfono solo si:

- su remitente **no es un número de teléfono de una persona**: quitando espacios, guiones, paréntesis y el
  «+», no son 10 dígitos o más (un celular `3001234567`, `+57 300 123 4567`, un fijo con indicativo, un
  número internacional). Los bancos mandan desde **códigos cortos** (4 a 6 dígitos) o desde un **nombre**
  («Bancolombia», «DAVIVIENDA»), y una persona no puede usar ninguno de los dos; **y**
- su remitente contiene un código conocido de Bancolombia (85540, 891333, 87400), **o** su texto nombra un
  banco de una lista cerrada (`PALABRAS_DE_BANCOS` en `SmsFilterConfigRoutes.kt`: bancolombia, nubank,
  davivienda, davibank, bbva, banco de bogota/bogotá, av villas, banco de occidente, colpatria, scotiabank,
  itau/itaú, falabella, lulo bank, rappipay). Sin remitente, la palabra no basta.

Lo que esto **sí** deja salir y hay que decir con honestidad: lo que un código corto o un remitente con
nombre manda nombrando a un banco, aunque no sea un movimiento (una promoción del banco, una clave
dinámica, la promoción de un comercio que dice «paga con Bancolombia»). No son SMS personales; el server
los aparta en «Por revisar» (`queEsEsteMensaje`) y nada se anota solo. Lo que **nunca** sale: cualquier SMS
de un número de teléfono, nombre al banco o no («ya te pagué por Bancolombia»).

Medido en producción el 2-nov-2026 (solo lectura): los 359 SMS capturados venían de 16 remitentes, todos
códigos cortos de 5 o 6 dígitos; ninguno de un celular. La regla nueva no habría perdido ninguno
(`FiltroDeSmsTest`, «los 16 remitentes de producción siguen pasando»).

**Un APK anterior a la vuelta 113 sigue con la regla vieja** (código **o** palabra, de cualquier
remitente): la condición sobre el remitente va compilada, no la sirve el server. La versión que se suba a
Play para pedir la excepción ya la trae; las instaladas por Drive la tienen cuando se actualicen.

## Y la divulgación antes del permiso

La política de Datos del usuario pide una explicación **dentro de la app e inmediatamente antes** del
diálogo del permiso ([answer/10144311](https://support.google.com/googleplay/android-developer/answer/10144311)).
Hoy la tarjeta «Permiso de SMS» de Captura del banco solo dice el estado y «Conceder permisos»; la
explicación está en el texto de la guía de primer uso, no en la tarjeta. **Antes de pedir la
excepción**, agregar a esa tarjeta (como ya hace la de las notificaciones) qué se lee, qué sale del
teléfono y qué no, arriba del botón. Ejemplo:

```
Movi lee los SMS que llegan a este teléfono y solo sube los de tu banco, para dejarlos en «Por revisar». Los demás —tus conversaciones, tus códigos de otras apps, todo lo que no sea de un banco— se descartan aquí y nunca salen del teléfono. Movi no envía, no borra ni modifica tus SMS.
```

## Activar SMS en el canal `play` cuando la aprueben

1. Quitar de `androidApp/src/play/AndroidManifest.xml` las tres líneas `tools:node="remove"`
   (`READ_SMS`, `RECEIVE_SMS` y el receiver `SmsRealtimeReceiver`).
2. Quitar `READ_SMS` y `RECEIVE_SMS` de `PERMISOS_PROHIBIDOS_EN_PLAY` en `androidApp/build.gradle.kts`
   (si no, `verificaElManifiestoDePlayRelease` frena el AAB, que es lo que tiene que hacer hasta ese día).
3. Nada más en la app: lo decide el manifiesto (`elAparatoPuedeLeerSms`). Captura del banco y la guía
   vuelven a ofrecer el permiso de SMS y el historial.

## El texto para el formulario

**Función central (core functionality):** «SMS-based money management».

**Descripción del uso** (pegar en inglés o en español; el revisor lee los dos):

```
Movi is a personal finance app for Colombia. Its core function is to keep an accurate record of the user's money: account balances, card and loan payments, and every expense and income, so it can tell the user how much they have left until payday and which bills are due.

Colombian banks notify each card purchase, transfer and payment by SMS, and most of them do not give individuals any way to share their transactions with a third-party app. The bank SMS is the only automatic, real-time record of a movement that the user receives. Without it, the user must type every movement by hand, which is the main reason people abandon budgeting apps.

How Movi uses the permissions:
- RECEIVE_SMS: when an SMS arrives, a filter that runs on the device checks whether it comes from a bank. An SMS from a phone number (10 or more digits) never leaves the device, even if it mentions a bank. Otherwise, it leaves the device only if its sender is a short code or alphanumeric sender ID and either the sender is a known bank short code or the text names a bank from a closed list. Only messages that pass the filter are sent to the user's Movi account; every other SMS is discarded on the device and never leaves it.
- READ_SMS: when the user opens the app, and every 6 hours, Movi re-reads the inbox from the first day of the previous month with the same filter, to recover bank messages that Android did not deliver to the receiver while the app was in the background. Again, only messages that pass the same filter leave the device.

Nothing is recorded automatically: each bank message appears in "Por revisar" (To review), where the user confirms, edits or ignores it. Movi never sends, deletes or modifies SMS, and does not access the call log. SMS data is never shared with third parties, never used for advertising, and the user can delete all of it at any time by deleting the account (in-app and at https://movi-project-production.up.railway.app/borrar-cuenta).

Alternatives we already offer and why they are not equivalent: Movi also reads bank app notifications (only from a closed list of bank apps), forwarded bank emails and statement PDFs. But many banks only send movements by SMS, several do not send emails, notifications require the bank app to be installed and are frequently disconnected by battery savers, and statements arrive once a month. For users of those banks, the SMS is the only real-time source.

Before asking for the permission, the app shows an in-app explanation of what is read, what is sent and what is not.
```

**Pasos para el revisor (opcional):**

```
1. Sign in with the demo account (credentials in "App access").
2. Go to Tú (last tab) > Captura del banco > "Captura en este teléfono" and grant "Permiso de SMS".
3. Open Actividad > "Por revisar": the bank messages already captured on the demo phone appear there as proposed movements (for example, an expense at Éxito). Tap "Confirmar" on one. The video shows a real bank SMS (sent by the bank from its short code) arriving and appearing here.
4. Send an SMS to the test phone from any personal phone number, even one that names a bank: "Bancolombia: Compraste $25.000 en EXITO con tu T.Deb *1234." It never appears in Movi and is not uploaded: SMS from phone numbers never leave the device. A bank message can only come from the bank's short code, which a reviewer cannot send from.
5. Send a personal SMS without a bank name ("Hola, ¿nos vemos mañana?"): it is not uploaded either.
```

(El paso 3 necesita una cuenta de demostración con avisos del banco ya capturados en un teléfono: un
SMS de prueba mandado desde un celular ya no pasa el filtro, y el revisor no puede mandar desde el código
corto de un banco.)

## El guion del video (30 a 60 segundos)

Grabar la pantalla del teléfono con la versión que pide SMS (canal `play` con los permisos devueltos,
instalada desde la prueba interna), con datos de demostración. Sin cuentas reales, sin números de
cuenta completos. Subtítulos o una voz en español o inglés.

| Segundo | Qué se ve | Qué se dice / el texto en pantalla |
| --- | --- | --- |
| 0–5 | Movi abre en Inicio: «Te quedan $X para N días». | «Movi lleva tu plata: cuánto te queda hasta que te paguen.» |
| 5–15 | Tú → Captura del banco → «Captura en este teléfono»: la tarjeta «Permiso de SMS» con su explicación; tocar «Conceder permisos» y aceptar el diálogo del sistema. | «Movi pide leer los SMS de tu banco. Solo los de bancos salen del teléfono; los demás se descartan ahí.» |
| 15–25 | Llega un SMS **real** de un banco, desde su código corto (una compra pequeña con una tarjeta de prueba o del dueño; se ven solo los últimos cuatro). Un SMS de prueba mandado desde otro celular no sirve: no pasa el filtro. | «Llega el SMS de una compra, desde el código del banco.» |
| 25–40 | Actividad → «Por revisar»: aparece «Éxito · $25.000» propuesto; tocar «Confirmar»; el movimiento queda en Actividad y «Te quedan» baja. | «Movi lo propone y tú lo confirmas: nada se anota solo.» |
| 40–50 | Desde otro celular llega «Bancolombia: Compraste $25.000 en EXITO» (y uno personal, «Hola, ¿nos vemos mañana?»); volver a «Por revisar»: no aparece ninguno. | «Un SMS de un número de teléfono nunca sale del teléfono, aunque nombre a tu banco.» |
| 50–60 | Tú → «Borrar mi cuenta» (sin confirmar). | «Puedes borrar todo cuando quieras.» |

Subirlo a YouTube como **«No listado»** y pegar el enlace en el formulario.
