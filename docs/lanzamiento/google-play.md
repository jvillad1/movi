# Google Play

Todo listo para copiar y pegar en Play Console. La versión que va a Play es el canal **`play`**:
**sin SMS** (captura por las notificaciones de las apps del banco, por correo reenviado y por
extractos). La excepción de SMS se pide después: `declaracion-sms.md`.

Fuentes consultadas el 2-nov-2026 (Ayuda de Play Console y developer.android.com), citadas en cada
sección.

---

## 1. Ficha de la tienda

**Nombre de la app** (máx. 30): `Movi: tus finanzas` (18)

> Si «Movi» choca con otra app en la búsqueda, alternativas: `Movi · Plata clara` (18),
> `Movi: plata en orden` (20).

**Descripción breve** (máx. 80):

```
Cuánto te queda, qué vence y en qué se va tu plata. Sin publicidad.
```
(67 caracteres)

**Descripción completa** (máx. 4000):

```
Movi te dice, de un vistazo, cuánta plata te queda hasta que te paguen, qué pagos vienen y en qué se te está yendo. Sin publicidad y sin vender tus datos.

CUÁNTO TE QUEDA
• «Te quedan» en el inicio: lo que tienes, menos tus pagos fijos del período, menos lo que quieres ahorrar.
• ¿Me alcanza hasta que me paguen? Movi proyecta tus cuentas día por día y te dice tu día más justo.
• Tu período empieza el día que te pagan (y se corre solo si cae en fin de semana o festivo).

TUS PAGOS, A TIEMPO
• Arriendo, servicios, tarjetas y cuotas de créditos en una lista, con lo vencido primero.
• Movi te avisa antes de que venza y tilda el pago cuando lo anotas.
• Tarjetas de crédito con su corte, su pago mínimo y lo que falta del extracto.
• Créditos con su plan: cuánto debes, cuándo terminas y cuánto te ahorra un abono extra.

QUE MOVI SE ENTERE SOLO
• Notificaciones del banco: con tu permiso, Movi lee las notificaciones de las apps de tu banco (de una lista cerrada) y te las deja en «Por revisar». Tú confirmas cada una: nada se anota solo.
• Correos del banco: reenvía las alertas de tu banco a tu dirección de Movi.
• Extractos: comparte el PDF de tu banco con Movi y te propone los movimientos.

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
• Descarga todos tus datos o borra tu cuenta cuando quieras, desde la app.
• Entra con tu huella, y oculta los saldos con un toque.

Funciona también en el navegador (movi-project-production.up.railway.app) con la misma cuenta.
```

**Categoría:** Finanzas. **Etiquetas** (Play deja elegir hasta 5 de su lista): Presupuesto,
Finanzas personales, Gastos, Ahorro, Administración del dinero (elige las más cercanas que ofrezca la
lista en español).

**Correo de contacto:** el mismo de `CORREO_DE_CONTACTO` (Play lo muestra en la ficha).
**Sitio web:** `https://movi-project-production.up.railway.app`
**Política de privacidad:** `https://movi-project-production.up.railway.app/privacidad`

**Gráficos:** ícono 512×512 (sale de `scripts/generar-icono.py`: la «m» en tinta sobre el amarillo),
gráfico de funciones 1024×500, y de 2 a 8 capturas de teléfono (pendientes, ver `README.md`).

---

## 2. Seguridad de los datos (Data safety)

Respuestas para la versión `play`, pregunta por pregunta. Fuente del formulario:
<https://support.google.com/googleplay/android-developer/answer/10787469>.

**¿Tu app recopila o comparte alguno de los tipos de datos de usuario obligatorios?** Sí.

**¿Todos los datos recopilados se encriptan en tránsito?** Sí (todo va por HTTPS).

**¿Ofreces una forma de que los usuarios soliciten que se borren sus datos?** Sí: en la app
(Tú → «Borrar mi cuenta») y en la web. **URL para borrar la cuenta:**
`https://movi-project-production.up.railway.app/borrar-cuenta`.

**«Compartir»**: Play no cuenta como compartir lo que procesan proveedores de servicio por cuenta de la
app (Railway, Anthropic, Resend). Movi no le pasa datos a nadie más → **no se comparte ningún tipo de
dato.**

| Tipo de dato (categoría de Play) | ¿Se recopila? | ¿Obligatorio u opcional? | Para qué (propósitos de Play) |
| --- | --- | --- | --- |
| **Información personal → Nombre** | Sí | Obligatorio | Funcionalidad de la app, Administración de la cuenta |
| **Información personal → Dirección de correo** | Sí | Obligatorio | Funcionalidad de la app, Administración de la cuenta, Comunicaciones del desarrollador (recordatorios y cierre del período, apagables) |
| **Información personal → ID de usuario** | Sí | Obligatorio | Funcionalidad de la app, Administración de la cuenta |
| **Información financiera → Historial de compras** | Sí (los movimientos que registras o confirmas) | Obligatorio | Funcionalidad de la app, Personalización |
| **Información financiera → Otra información financiera** (saldos, cuentas, créditos, deudas; el texto de las notificaciones del banco) | Sí | Obligatorio | Funcionalidad de la app, Personalización |
| Información financiera → Información de pago del usuario / Puntaje crediticio | **No** (Movi no guarda números de tarjeta completos ni consulta centrales de riesgo) | — | — |
| **Mensajes → Correos electrónicos** (las alertas del banco que reenvías) | Sí | Opcional | Funcionalidad de la app |
| Mensajes → SMS o MMS | **No en la versión de Play** | — | — |
| **Fotos y videos → Fotos** (fotos de recibos o comprobantes que compartes) | Sí | Opcional | Funcionalidad de la app |
| **Archivos y documentos** (extractos en PDF) | Sí | Opcional | Funcionalidad de la app |
| **Actividad en la app → Interacciones con la app** (qué pantallas abres y qué tocas) | Sí | Opcional (Tú → Tus datos → «Mandar datos de uso…») | Estadísticas |
| **Actividad en la app → Otro contenido generado por el usuario** (preguntas a Movi AI, notas) | Sí | Opcional | Funcionalidad de la app |
| **Información y rendimiento de la app → Registros de fallos** | Sí | Obligatorio (no lleva datos financieros) | Estadísticas, Funcionalidad de la app |
| **Información y rendimiento de la app → Diagnóstico** (modelo del teléfono, versión de Android) | Sí | Obligatorio | Estadísticas |
| Ubicación, Contactos, Calendario, Salud, Audio, Web, Apps instaladas | No | — | — |
| Dispositivo u otros identificadores | No (sin ID de publicidad) | — | — |

**¿Los datos se procesan de forma efímera?** No para los de arriba (se guardan en la cuenta).

---

## 3. Contenido de la app (App content)

- **Política de privacidad:** la URL de arriba.
- **Anuncios:** «No, mi app no contiene anuncios».
- **Acceso a la app:** «Toda la funcionalidad o parte está restringida» → credenciales de la cuenta de
  demostración (ver abajo).
- **Clasificación del contenido** (cuestionario IARC): categoría «Todos los demás tipos de apps»
  (no es juego, ni red social, ni de comunicación entre usuarios). Violencia, sexo, lenguaje, drogas,
  apuestas: **No** a todo. ¿Los usuarios interactúan o intercambian contenido entre sí? **No** (el
  enlace de «Compartir» es un resumen de solo lectura que el dueño manda a quien quiera; no hay chat
  ni perfiles públicos). ¿Comparte la ubicación? **No**. ¿Compras digitales? **No**. Resultado
  esperado: apta para todos (la edad la limita el público objetivo, no la clasificación).
- **Público objetivo y contenido:** **18 años o más** únicamente. La app no está diseñada para niños.
- **Funciones financieras** (Financial features declaration): marcar **«Administración de finanzas
  personales / presupuesto»** (o la opción equivalente que muestre la lista). **No** ofrece préstamos
  personales, ni líneas de crédito, ni banca, ni pagos, ni inversiones, ni cripto, ni seguros. Movi no
  mueve plata: registra y ordena la del usuario.
- **App gubernamental:** No. **Noticias:** No. **Salud:** No. **COVID-19:** No.
- **Seguridad de los datos:** la sección 2.

### Acceso a la app: la cuenta de demostración para los revisores

La crea el dueño en **producción** (Play revisa la app instalada contra el server de verdad). No uses
tu cuenta personal ni tu correo de siempre.

1. Crea un correo solo para esto (por ejemplo, un alias nuevo de Gmail) y una contraseña larga que
   guardes en tu gestor de contraseñas.
2. En la web de Movi, «¿No tienes cuenta? Regístrate» con ese correo y el nombre «Revisión Play».
3. En la guía de primer uso: período del 1 de cada mes; dos cuentas («Ahorros Demo» con $2.500.000 y
   «Efectivo» con $200.000); una tarjeta de crédito («Tarjeta Demo», corte el 20, pago el 5); dos pagos
   fijos (Arriendo $1.200.000 el 5, Internet $90.000 el 15); salario $4.000.000 el día 1.
4. Agrega 8 a 10 movimientos de las últimas dos semanas (mercado, restaurantes, transporte, una
   transferencia entre cuentas) para que Inicio, Actividad y «En qué se va» tengan algo que mostrar.
5. En Play Console → Acceso a la app → «Agregar instrucciones»: nombre «Cuenta de demostración»,
   el correo y la contraseña, y este texto:

```
Inicia sesión con este correo y contraseña. La cuenta tiene datos de demostración (no son reales).

La captura automática por notificaciones del banco necesita una app de banco colombiano instalada que publique notificaciones; si no la tienen, se puede ver la pantalla en Tú > Ajustes > Captura del banco > Captura en este teléfono. Movi solo lee las notificaciones de las apps de bancos de una lista cerrada; las demás se descartan en el teléfono sin leerlas.

Para probar la lectura de extractos: Inicio > Subir comprobante, con cualquier PDF de un estado de cuenta.
```

---

## 4. El acceso a las notificaciones (`NotificationListenerService`)

**No hay formulario de declaración para este acceso** (a diferencia de SMS, el registro de llamadas,
la accesibilidad o la ubicación en segundo plano): no figura en la política de
[Permisos y APIs que acceden a información sensible](https://support.google.com/googleplay/android-developer/answer/9888170).
Lo que sí aplica es la política de **Datos del usuario**, sección «Divulgación destacada y
consentimiento» ([answer/10144311](https://support.google.com/googleplay/android-developer/answer/10144311)):
cuando el uso de un dato no es el que el usuario esperaría, la app tiene que decirlo **dentro de la
app**, en el flujo normal (no solo en la política), describiendo qué dato y para qué, **inmediatamente
antes** de pedir el permiso.

Movi ya lo cumple, y conviene decirlo así si un revisor pregunta:

- La tarjeta «Acceso a notificaciones» (guía de primer uso y Tú → Captura del banco) dice, **antes** del
  botón «Dar acceso a las notificaciones»: qué lee (las notificaciones de las apps de banco de su
  lista), para qué (dejarlas en «Por revisar» para que el usuario confirme cada movimiento) y qué no
  (las de cualquier otra app se descartan sin leerlas ni contarlas; no abre, no borra ni responde
  notificaciones).
- El acceso se da a mano en la pantalla del sistema; la app nunca lo pide sola.
- El filtro vive en el teléfono: solo sube las de los paquetes de bancos de la lista
  (Bancolombia, Nu, Davivienda, Davibank, BBVA, Banco de Bogotá, Occidente, AV Villas, Popular,
  Falabella, Lulo, Nequi, Daviplata, Glim/Prontomás y Google Wallet).

Texto para el campo de la declaración si Play lo pidiera:

```
Movi es una app de finanzas personales. Su función central es registrar los movimientos de la plata del usuario. Con el acceso a las notificaciones que el usuario concede a mano, Movi lee SOLO las notificaciones que publican las apps de bancos de una lista cerrada (cada compra, transferencia o pago que el banco avisa) y las deja en «Por revisar», donde el usuario confirma cada una. Las notificaciones de cualquier otra app se descartan en el teléfono sin leerlas y nunca salen de él. Movi no abre, no borra ni responde notificaciones. Antes de pedir el acceso, la app explica qué lee, para qué y qué no.
```

**El ahorro de batería:** la versión de Play pide `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` para la
tarjeta «Quitar el ahorro de batería». Play solo lo permite cuando el ahorro de batería rompe la
función central ([Doze, casos aceptables](https://developer.android.com/training/monitoring-device-state/doze-standby#exemption-cases)).
Justificación: Android desenchufa el `NotificationListenerService` con el ahorro de batería y sin él
Movi deja de enterarse de los movimientos (medido en la vuelta 92). Si la revisión lo objeta, ver
«Lo pendiente» en `README.md`.

---

## 5. La prueba cerrada obligatoria

**Cifra vigente** (Ayuda de Play Console, [answer/14151465](https://support.google.com/googleplay/android-developer/answer/14151465),
consultada el 2-nov-2026): las cuentas **personales** creadas después del 13-nov-2023 tienen que correr
una **prueba cerrada con al menos 12 testers que hayan estado inscritos sin interrupción durante al
menos 14 días** antes de poder pedir acceso a producción. Los 14 días tienen que ser seguidos, y al
pedir producción tiene que haber al menos 12 inscritos. (Una cuenta de **organización** no tiene este
requisito, pero exige un número D-U-N-S.)

**El plan:**

1. **Prueba interna** (hasta 100 testers, sin revisión larga): el dueño y quien quiera probar primero.
   Sirve para ver que el AAB instala, abre y entra. No cuenta para los 14 días.
2. **Prueba cerrada** con las personas que el dueño quiere invitar, **al menos 12** (mejor 15, por si
   alguien se sale: si bajan de 12, los 14 días se reinician). Se invitan por una lista de correos
   (cuentas de Google) o un Grupo de Google; cada uno abre el enlace de inscripción y la instala desde
   Play. **Que no se desinscriban** durante los 14 días.
3. A los 14 días, **pedir acceso a producción** (Play Console → Panel → «Solicitar acceso a
   producción»): Play hace preguntas sobre la prueba (cuántos testers, qué comentarios hubo, qué se
   cambió). Contestar con lo que pasó de verdad.
4. **Producción**, con un lanzamiento por etapas si quieres (por ejemplo, 20 % y después 100 %).

### Quién prueba qué

- La versión de Play y la de Drive tienen **el mismo `applicationId` y firmas distintas**: no conviven
  en un teléfono, y una no actualiza a la otra.
- **El dueño y su papá pueden seguir con la de Drive** (con SMS). Si alguno quiere pasarse a la de
  Play: abrir Movi con conexión, esperar a que no quede nada por subir (lo anotado sin señal vive en el
  teléfono hasta que sube), desinstalar, e instalar desde Play. Los datos están en el server: al entrar
  vuelven.
- Los demás testers instalan directo desde Play.

---

## 6. El paso a paso de mañana

1. **Crear la cuenta** en <https://play.google.com/console/signup>: cuenta **personal**, pago único de
   US$25, verificación de identidad (documento) y de un teléfono. El nombre legal queda como
   desarrollador visible en la ficha.
2. **Crear la llave de subida** (una sola vez): `./scripts/crear-llave-de-subida.sh`. Respaldar el
   `.jks` y la contraseña en dos lugares (ver `README.md`).
3. **Poner en Railway** `RESPONSABLE_LEGAL` y `CORREO_DE_CONTACTO`, desplegar y abrir las tres URL.
4. **Armar el AAB:** `./scripts/armar-para-play.sh --version 1.93` (sube versionName y versionCode;
   commitearlo). Verifica que el manifiesto de `play` no pida SMS y que el AAB traiga todas las clases;
   deja el AAB en `androidApp/build/outputs/bundle/playRelease/androidApp-play-release.aab`.
5. En Play Console, **«Crear app»**: nombre `Movi: tus finanzas`, idioma predeterminado Español
   (Latinoamérica) – es-419, App, Gratis, aceptar las declaraciones.
6. **Panel → «Configura tu app»**: llenar todo lo de la sección 3 (privacidad, anuncios, acceso a la
   app, clasificación, público objetivo, funciones financieras, seguridad de los datos) y la ficha de la
   sección 1.
7. **Prueba interna → «Crear versión»**: aceptar la **Firma de apps de Play**, subir el AAB, nombre de
   la versión «1.93», notas «Primera versión en Play». Agregar testers (tu correo) y publicar. Instalar
   desde el enlace de la prueba interna y probar: entrar, Captura del banco (sin SMS), subir un
   extracto, borrar una cuenta de prueba.
8. **Prueba cerrada → «Crear versión»** con el mismo AAB (o «Promover» desde interna), la lista de
   testers (al menos 12) y **enviar a revisión**. La primera revisión puede tardar varios días.
9. Contar 14 días desde que haya 12 inscritos, y pedir acceso a producción (sección 5).

**Para cada versión nueva:** `./scripts/armar-para-play.sh --version X.Y`, commitear la versión,
subir a la pista que corresponda. El `versionCode` siempre sube; Play rechaza uno repetido.
