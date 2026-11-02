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

## Antes de pedirla: angostar el filtro de la versión de Play

Hoy el teléfono sube un SMS si **el remitente contiene un código corto de Bancolombia** (85540,
891333, 87400) **o si el cuerpo contiene el nombre de un banco** de la lista
(`BankSenderFilter` en `:shared` y `PALABRAS_DE_BANCOS` en `SmsFilterConfigRoutes.kt`: bancolombia,
nubank, davivienda, davibank, bbva, banco de bogota/bogotá, av villas, banco de occidente, colpatria,
scotiabank, itau/itaú, falabella, lulo bank, rappipay). La regla del cuerpo existe por los bancos que
mandan desde códigos que Movi no conoce (el de Davivienda del papá del dueño). Pero significa que **un
SMS personal que nombre un banco** («te consigno en Bancolombia») **sí sale del teléfono**, aunque el
server después lo aparte por no ser un movimiento.

Eso es exactamente lo que la condición de la política prohíbe. **Antes de pedir la excepción**, la
recomendación es que en el canal `play` solo pasen los SMS de remitentes que sean **códigos cortos**
(números de 4 a 6 dígitos, que una persona no puede usar) **y** que traigan un monto, o solo los
códigos conocidos más la palabra del banco. Es un cambio chico en `BankSenderFilter.matches` (elegido
por `elAparatoPuedeLeerSms`, o por una propiedad del manifiesto de `play`), con su prueba. No se hizo
en la vuelta 109 porque Play sale sin SMS y la versión de Drive no debe cambiar de comportamiento.

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
- RECEIVE_SMS: when an SMS arrives, a filter that runs on the device checks whether it comes from a bank (known bank short codes and bank names). Only messages that pass the filter are sent to the user's Movi account; every other SMS is discarded on the device and never leaves it.
- READ_SMS: when the user opens the app, and every 6 hours, Movi re-reads the inbox from the first day of the previous month with the same filter, to recover bank messages that Android did not deliver to the receiver while the app was in the background. Again, only bank messages leave the device.

Nothing is recorded automatically: each bank message appears in "Por revisar" (To review), where the user confirms, edits or ignores it. Movi never sends, deletes or modifies SMS, and does not access the call log. SMS data is never shared with third parties, never used for advertising, and the user can delete all of it at any time by deleting the account (in-app and at https://movi-project-production.up.railway.app/borrar-cuenta).

Alternatives we already offer and why they are not equivalent: Movi also reads bank app notifications (only from a closed list of bank apps), forwarded bank emails and statement PDFs. But many banks only send movements by SMS, several do not send emails, notifications require the bank app to be installed and are frequently disconnected by battery savers, and statements arrive once a month. For users of those banks, the SMS is the only real-time source.

Before asking for the permission, the app shows an in-app explanation of what is read, what is sent and what is not.
```

**Pasos para el revisor (opcional):**

```
1. Sign in with the demo account (credentials in "App access").
2. Go to Tú (last tab) > Captura del banco > "Captura en este teléfono" and grant "Permiso de SMS".
3. Send an SMS to the test phone that imitates a bank message, for example from any number: "Bancolombia: Compraste $25.000 en EXITO con tu T.Deb *1234, el 02/11/2026 a las 10:15." (it passes the filter because it names the bank).
4. Open Actividad > "Por revisar": the message appears as a proposed expense of $25,000 at Éxito. Tap "Confirmar".
5. Send a personal SMS without a bank name ("Hola, ¿nos vemos mañana?"): it never appears in Movi and is not uploaded.
```

(Si se angosta el filtro como se recomienda arriba, el paso 3 tiene que usar un remitente que sea
código corto: decirle al revisor que el SMS de prueba se manda desde un número de 5 dígitos, o cambiar
el ejemplo por una captura del SMS real de un banco en el video.)

## El guion del video (30 a 60 segundos)

Grabar la pantalla del teléfono con la versión que pide SMS (canal `play` con los permisos devueltos,
instalada desde la prueba interna), con datos de demostración. Sin cuentas reales, sin números de
cuenta completos. Subtítulos o una voz en español o inglés.

| Segundo | Qué se ve | Qué se dice / el texto en pantalla |
| --- | --- | --- |
| 0–5 | Movi abre en Inicio: «Te quedan $X para N días». | «Movi lleva tu plata: cuánto te queda hasta que te paguen.» |
| 5–15 | Tú → Captura del banco → «Captura en este teléfono»: la tarjeta «Permiso de SMS» con su explicación; tocar «Conceder permisos» y aceptar el diálogo del sistema. | «Movi pide leer los SMS de tu banco. Solo los de bancos salen del teléfono; los demás se descartan ahí.» |
| 15–25 | Llega un SMS de un banco (notificación del sistema con el SMS de prueba). | «Llega el SMS de una compra.» |
| 25–40 | Actividad → «Por revisar»: aparece «Éxito · $25.000» propuesto; tocar «Confirmar»; el movimiento queda en Actividad y «Te quedan» baja. | «Movi lo propone y tú lo confirmas: nada se anota solo.» |
| 40–50 | Llega un SMS personal («Hola, ¿nos vemos mañana?»); volver a «Por revisar»: no aparece. | «Un SMS personal nunca sale del teléfono.» |
| 50–60 | Tú → «Borrar mi cuenta» (sin confirmar). | «Puedes borrar todo cuando quieras.» |

Subirlo a YouTube como **«No listado»** y pegar el enlace en el formulario.
