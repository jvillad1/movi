# Atajos de iPhone: cómo probarlos con el iPhone de Caro (vuelta 111, corregida en la 114)

Lo que hay que hacer **una vez** en un iPhone real para dejar andando la captura por Atajos, y qué mirar para
saber que funcionó. Nada de esto se pudo probar en la Mac: los disparadores «Mensaje» y «Transacción» solo
existen en el iPhone.

## 0. La prueba real del 2-nov-2026 (vuelta 114)

El dueño probó la vuelta 111 en el iPhone de Caro (iPhone18,2, **iOS 27.0.1**).

**Funcionó de punta a punta:**
- un SMS real de Bancolombia (85540, «Recibiste una transferencia…») entró solo a «Por revisar» como
  «Atajo · Bancolombia», **con el iPhone bloqueado**;
- el mismo texto dos veces dio **una** fila (dedupe);
- «Hola, ya llegué» solo se contó (no se guardó).

**Falló o no coincidía, y qué se cambió en la vuelta 114:**
1. **El nombre.** El atajo se instaló como «movi-mensaje», el nombre del archivo, y no «Movi · Avisar un
   mensaje» como decía la guía. Atajos instala un `.shortcut` con el nombre del archivo, y el firmado no lleva
   nombre por dentro (es un Apple Archive con un solo `Shortcut.wflow`). Ahora el archivo se llama
   **«Movi - Avisar un mensaje.shortcut»** (en el repo, en la ruta `/atajos/Movi%20-%20Avisar%20un%20mensaje.shortcut`
   y en `Content-Disposition`), y la guía dice ese nombre. Va con guion y no con «·»: un nombre de archivo con
   caracteres fuera de ASCII depende de cada navegador. La ruta vieja `/atajos/movi-mensaje.shortcut` sigue
   sirviendo el mismo archivo, ya con el nombre nuevo.
2. **«Agregar atajo» no respondió** en «Configurar este atajo» con el código pegado en la pregunta. Con
   **«Omitir configuración»** y el código pegado a mano en la acción **«Texto»** sí quedó. (Vuelta 115,
   verificado de nuevo con iOS 27 y el atajo de producción: «Agregar atajo» tampoco responde con el teclado
   cerrado, pero «Omitir configuración» agrega el atajo y **conserva el código que se pegó** en la pregunta: no
   hace falta pegarlo a mano.) No se sabe la causa:
   la pregunta de importación (`WFWorkflowImportQuestions`) se revisó contra atajos exportados por la app y
   publicados (cliente 4610, «Share Wi-Fi» de huaminghuangtw/Shortcutomation) y contra el compilador Cherri, y
   coincide en todo (`ActionIndex`, `Category` «Parameter», `ParameterKey` «WFTextActionText», `DefaultValue`
   vacío, y el texto de la acción vacío como texto plano). No se cambió. La guía trae el plan B.
3. **En iOS 27 no existe la pestaña «Automatización»** («Tus automatizaciones ahora se guardan como atajos»).
   La guía tiene ahora dos variantes (abajo, §4).
4. **El código quedó a la vista en fotos.** La guía recuerda que, al tocar «Crear otro», hay que abrir el atajo
   y cambiar el código de la acción «Texto».
5. **La tarjeta de arriba de «Mensajes del banco»** (app de iOS) decía «Este aparato no lee los mensajes del
   banco» con el atajo funcionando (último aviso hace 24 min). En un iPhone ahora dice «En este iPhone los
   mensajes llegan por el atajo de Atajos.» si hay un código vigente que ya mandó algo, y «Este iPhone no los
   lee solo: configúralo en «Desde tu iPhone».» si no.
6. **Una promoción o una clave del banco** que llega por el atajo se contaba como «no era de un banco». Ahora
   va aparte (`SIN_FORMA_DE_AVISO`): «N mensajes nombraban un banco pero no tenían la forma de un aviso».

**Para volver a probar** con el archivo de la rama (sin desplegar): bajarlo de
`https://raw.githubusercontent.com/jvillad1/movi/vuelta-114/server/src/main/resources/atajos/Movi%20-%20Avisar%20un%20mensaje.shortcut`.
Tiene que instalarse como «Movi - Avisar un mensaje».

**Antes**: esta vuelta tiene que estar desplegada (`./scripts/desplegar.sh`). Sin eso, `/api/captura/codigo` da
404 y el botón «Agregar el atajo de mensajes» no descarga nada.

## 1. El código

1. En el iPhone de Caro, abre Movi en Safari (o la app de iOS) con **su** cuenta.
2. Tú → Ajustes → **«Mensajes del banco»** → sección **«Desde tu iPhone»** → **«Crear tu código»**.
3. Toca **«Copiar»**. El código se ve una sola vez: si se pierde, «Crear otro» (el anterior deja de servir).

## 2. El atajo de mensajes (firmado)

1. En la misma sección toca **«Agregar el atajo de mensajes»**. Safari descarga `Movi - Avisar un mensaje.shortcut`.
2. **Safari no lo abre solo** (verificado el 2-nov en dos iPhone con iOS 27): después de «Descargar», toca el
   ícono a la izquierda de la barra de direcciones → **Descargas** → «Movi - Avisar un mensaje». Si no lo ves,
   está en la app **Archivos**, en **Descargas** (dentro de «En mi iPhone» o «iCloud Drive»). Al abrirlo,
   Atajos muestra **«Configurar este atajo»** con la pregunta «Pega tu código de Movi…». (La app lo dice en
   el paso 2, `ABRIR_LO_DESCARGADO`.)
3. Pega el código y toca **«Agregar atajo»**. El atajo queda como **«Movi - Avisar un mensaje»**.
4. **Plan B** (probado el 2-nov en iOS 27 con el atajo de producción): pega tu código. Si «Agregar atajo» no
   responde (tampoco con el teclado cerrado), toca **«Omitir configuración»**: el atajo se agrega **con el código
   que pegaste** (la acción «Texto» queda con ese valor). Si después el atajo no avisa, ábrelo y revisa que el
   código esté en la acción **«Texto»**.
5. Si después tocas **«Crear otro»** en Movi, el código de antes deja de servir: abre el atajo y cambia el
   código de la acción «Texto».

**Qué anotar**: si Safari no descarga, si Atajos dice que el atajo no se puede abrir, o si no aparece la pregunta
del código. En ese caso, arma el atajo a mano con la receta que muestra la app (botón «Agregar el atajo de
mensajes» cuando el server no tiene el firmado, o los mismos pasos que el de Apple Pay de abajo, con «tipo» =
«mensaje» y «texto» = «Entrada del atajo»).

## 3. Probarlo a mano

1. En Atajos toca «Movi - Avisar un mensaje». Como corre sin automatización, pide un texto.
2. Pega el texto de prueba (en la app: «Copiar el texto de prueba»), o este SMS real de Bancolombia de los
   fixtures del repo (`server/src/test/.../sms/AvisosDelMismoPagoTest.kt`):

   ```
   Bancolombia: Compraste $12.300,00 en PANADERIA LA ESQ con tu T.Deb *1111, el 25/09/2026 a las 09:15.
   ```

3. En Movi, «Por revisar» tiene que mostrarlo en unos segundos, con origen **«Atajo · Bancolombia»**. En
   «Desde tu iPhone» la línea pasa a «último aviso hace un momento».
4. Corre el atajo otra vez con el **mismo** texto: no tiene que aparecer una segunda fila (dedupe).
5. Córrelo con un texto personal («Hola, ya llegué», o «Hola, ya te pagué por Bancolombia $50.000»): no tiene
   que aparecer nada (se descarta y solo se cuenta). El atajo firmado no manda el remitente, así que el
   server solo mira el texto: se guarda lo que **empieza** por el nombre de un banco y trae un monto. Por eso
   un SMS de un amigo que empiece igual («Bancolombia me cobró $50.000 de más, qué rabia») **sí** se guarda:
   es el límite de la regla (vuelta 113, `seGuardaElSmsDelAtajo`), y queda en «Por revisar» sin anotarse.
6. Después, ignora los de prueba en «Por revisar».

## 4. La automatización «Mensaje»

La app elige la variante por la versión de iOS (`shared/.../ui/sms/LaVersionDeIos.kt`): en la app de iOS la lee
del sistema; en la web, del *user agent*, **solo si es seguro** (desde Safari 26 el «CPU iPhone OS» está
congelado en 18_6/18_7 a propósito —WebKit bug 298260— y «Version/26» o más sigue la versión de Safari, que en
el iPhone va atada a la del sistema). Si se sabe, muestra esa y deja la otra detrás de «¿Tu iPhone muestra otra
cosa?»; si no, las dos con su rótulo, la de iOS 27 primero. Se recomiendan siempre dos: **`Bancolombia`** y
**`DAVIVIENDA`** (como firman su SMS), más las palabras de los bancos de sus cuentas.

### iOS 27 o más nuevo (verificado a mano el 2-nov en iOS 27.0.1)

1. Abre el atajo **«Movi - Avisar un mensaje»**.
2. Abajo, **«Buscar»**, y luego la pastilla **«Automatización»**.
3. En la sección «Comunicación», **«Mensaje»** («Al recibir un mensaje de mi mamá»). Se agrega al atajo una
   tarjeta: «Al recibir un mensaje en el que **Remitente** es Remitente».
4. Toca **«Remitente»** y elige **«Mensaje»**, para que quede «Mensaje **contiene** Texto».
5. Escribe `Bancolombia`.
6. Toca la flecha (**⌄**) junto a «que»: **«Automatización»** prendido y **«Confirmar antes de ejecutar»**
   apagado (venía apagado; hay que dejarlo así).
7. Para cada banco más agrega **otra** tarjeta «Mensaje» (quedan unidas por «o»), **no «+ Agregar filtro»**: un
   filtro se une con «y» y la automatización no correría nunca. `DAVIVIENDA` y las de sus bancos. (La app dice
   lo mismo.)

**Alternativa: todos tus bancos con tres tarjetas** (vuelta 115, pedido del dueño, que ratificó la decisión de
privacidad). En vez de una tarjeta por banco, tres tarjetas «Mensaje contiene», unidas por «o» (cada una otra
tarjeta, **nunca «+ Agregar filtro»**): `$`, `COP` y `USD`. Más simple: tres tarjetas, con «$», «COP» y «USD».
Atrapan los avisos de todos tus bancos, porque todos traen el monto (Bancolombia escribe `$132.347,00`, y
`COP86.900,00` / `USD20,00` en las compras con tarjeta de crédito: con solo «$» esas se perdían). Los mensajes
personales con un monto también pasan por Movi, que los descarta sin guardarlos: el server solo guarda lo que
empieza por un banco y trae un monto (`seGuardaElSmsDelAtajo`; lo demás queda contado por día y motivo en
`atajos_descartados`, sin texto ni remitente). La recomendada sigue siendo una tarjeta por banco con las palabras
sugeridas.

Las imágenes del atajo muestran también «Recibir **Contenido y 20 más** de **Ningún lado** · Si no hay entrada:
Solicitar Texto».

### iOS 17 a 26

1. Atajos → pestaña **«Automatización»** → **«+»** (o «Nueva automatización»).
2. **«Mensaje»** → **«El mensaje contiene»** → «Bancolombia» (una automatización por palabra: `DAVIVIENDA` y las
   de sus bancos; la app las sugiere).
3. **«Ejecutar de inmediato»** → **«Siguiente»** → el atajo **«Movi - Avisar un mensaje»**. «Ejecutar de
   inmediato» existe para «Mensaje» desde iOS 17.

**Alternativa: tres automatizaciones**, cada una con «El mensaje contiene»: `$`, `COP` y `USD`, en vez de una por
banco, con lo mismo que las tres tarjetas de iOS 27 (arriba).

### Antes de iOS 17

El iPhone pide tocar una notificación cada vez. La app lo dice en una línea y propone reenviarle a Movi los
correos del banco.

### Probarla

Para probarla sin esperar una compra: desde otro teléfono mándale a Caro un SMS que diga
`Bancolombia: Compraste $1.000,00 en PRUEBA DE MOVI con tu T.Deb *0000.` y mira «Por revisar».

**Qué anotar** (lo que falta verificar):
- En iOS 17 a 26, los nombres exactos de las pantallas en español (en iOS 26 «Ejecutar de inmediato» puede ser
  el interruptor «Preguntar antes de ejecutar», que va **apagado**). Si difieren, corregir
  `pasosDeLaAutomatizacionDeMensajes` en `shared/.../ui/sms/DesdeTuIphone.kt`.
- Que la **entrada del atajo**, convertida en texto, sea el **contenido del SMS** (en iOS 27, verificado: el
  SMS real llegó).

### Las notificaciones de Nu (vuelta 127)

En el iPhone de Caro (iOS 27) hay una automatización «Notificación» de la app Nu que le pasa cada notificación
al mismo atajo «Movi - Avisar un mensaje» (el atajo firmado no cambia). El 3-nov el server descartó 9 de esas
(7 `NO_ES_DEL_BANCO`, 2 `SIN_FORMA_DE_AVISO`): el filtro pedía la forma de un SMS. Desde la vuelta 127 el server
las reconoce por su forma (`notificacionDeNuDelAtajo` en `:core`), con o sin el título y con o sin «Nu» delante,
y las guarda con el texto que sube un Android y `bank` = «Atajo · Notificación · Nu».

Sin «Nu» delante solo se reconoce lo que trae un monto **y** una seña que solo escribe Nu (su título junto a su
cuerpo, o el cierre del cuerpo: «ha sido APROBADA», «En un rato podrás verlo en tu app», «En unas horas podrás
revisar la actualización»): el atajo casi nunca manda remitente y lo reconocido se guarda con el texto entero.
«Tarjeta verificada», la Cajita, los envíos y el pago desde la cuenta se reconocen solo con «Nu» delante: «Nu» con
N mayúscula y un separador («Nu:», «Nu -», «Nu ·» o un salto de línea, nunca un espacio solo), y aun así con un
monto (o la tarjeta, en «Tarjeta verificada»). Las formas sin «Nu» van ancladas al principio del texto (un aviso
citado en un chat no entra) y piden la cifra como la escribe Nu («$27.500,00», dos decimales con coma).

**Riesgo que queda**: «ha sido aprobada» (el cierre de la compra de Nu) también podría ser como escribe otro banco
el cuerpo de su notificación; una compra así, con «Tu compra en … por $X,XX con tu tarjeta terminada en NNNN», se
guardaría como de Nu y se propondría contra la tarjeta Nu (el dueño la ve antes de confirmar).

**El dedupe con el Android depende de dos cosas**, y se confirma con la primera captura real de Caro:
- **Que el texto sea idéntico** al que sube el Android («título: cuerpo», byte por byte). `SmsDedupe` compara el
  texto exacto: si Atajos manda el cuerpo con otras palabras, otro corte, otros espacios o sin el título (salvo la
  compra y el pago de la tarjeta, que lo recuperan), las dos capturas quedan como dos filas (las junta «el mismo
  pago», que las muestra como un pago avisado dos veces, pero no las borra).
- **Que lleguen a ±1 minuto** (`SMS_DEDUPE_TOLERANCE`). El atajo se fecha con la hora del **server** al recibirlo
  (`momentoDelCorreo`), y el Android con la hora de la notificación en el teléfono; si el atajo corre tarde (el
  iPhone sin red, la automatización demorada) o el reloj del teléfono está corrido, quedan dos filas.

**Qué anotar**: qué manda Atajos de verdad (solo el cuerpo, título y cuerpo, o el nombre de la app delante). Se
ve en la base sin el texto de nadie más que Caro: `SELECT time, bank, text FROM sms_messages WHERE user_id = '<id
de Caro>' AND bank = 'Atajo · Notificación · Nu' ORDER BY time DESC LIMIT 5;` (solo lectura). Si llegara solo el
cuerpo del depósito («¡No te preocupes! En unas horas…», sin monto), no se puede reconocer: habría que pedirle al
atajo el título.

## 5. Apple Pay (a mano)

El atajo de Apple Pay **no está firmado**: para mandar comercio, monto y tarjeta hay que leer propiedades de la
transacción de Wallet cuyos nombres internos no se pueden comprobar en una Mac (el borrador está en
`scripts/atajos/movi-apple-pay.plist`). Se arma a mano, con la receta de la app («Agregar el atajo de Apple
Pay»):

1. Atajos → «+» → nombre «Movi - Pago con Apple Pay».
2. Acción **«Obtener contenido de URL»**, URL `https://movi-project-production.up.railway.app/api/captura/atajo`.
3. Método **POST**; en **Encabezados**, `Authorization` = `Bearer ` + el código.
4. **Cuerpo de la solicitud: JSON**, campos de texto: `tipo` = `transaccion`; `comercio`, `monto` y `tarjeta` =
   esas propiedades de la **Entrada del atajo** (toca la variable y elige cada una).
5. **iOS 17 a 26:** pestaña Automatización → «+» → **«Transacción»** → marca las tarjetas → «Ejecutar de
   inmediato» → «Siguiente» → ese atajo. **iOS 27: por verificar en el iPhone.** La app no adelanta un camino:
   dice que todavía no se verificó y que, si no está la pestaña, se busque dentro del atajo, como la de mensajes.
   Al probarlo, anotar dónde quedó «Transacción» para escribir los pasos.
6. Paga algo chico con Apple Pay. En «Por revisar» tiene que aparecer «Apple Pay · <comercio> · $X · <tarjeta>».
   Si la misma compra también llegó por SMS (otra automatización), los dos avisos van en **una** tarjeta.

**Qué anotar**: cómo se llaman las propiedades en español (para corregir el borrador y firmarlo), y **cómo
escribe el monto** el iPhone («$27.500,00», «COP 27.500»…). Si Movi no lo entiende queda «sin monto» en el
texto: pasar ese formato para agregarlo a `montoDelAtajo` (`server/.../sms/ElPagoConApplePay.kt`) con su prueba.
Si se confirman los nombres, en `scripts/generar-atajos.py` se corrigen y se pone `FIRMAR_APPLE_PAY = True`.

## 6. Qué mirar en la base (solo lectura)

```sql
-- El código: nunca en claro, solo el hash.
SELECT id, creado_en, revocado_en, ultimo_uso FROM codigos_de_captura WHERE user_id = '<id de Caro>';

-- Lo que llegó por los atajos.
SELECT id, time, bank, state, motivo_apartado, left(text, 80) AS texto
FROM sms_messages WHERE user_id = '<id de Caro>' AND bank LIKE 'Atajo%' ORDER BY time DESC LIMIT 20;

-- Lo que se descartó (sin texto): cuántos por día y motivo (NO_ES_DEL_BANCO, SIN_FORMA_DE_AVISO, VACIO,
-- DEMASIADOS). Vuelta 114: SIN_FORMA_DE_AVISO es lo que nombra un banco pero no empieza por él o no trae
-- monto (una promoción o una clave del banco); antes se contaba como NO_ES_DEL_BANCO.
SELECT dia, motivo, cuantos FROM atajos_descartados WHERE user_id = '<id de Caro>' ORDER BY dia DESC;

-- El tracking del server (estado y clase, nunca el texto).
SELECT nombre, propiedades, ocurrido_en FROM uso_de_la_app
WHERE user_id = '<id de Caro>' AND nombre IN ('CAPTURA_POR_ATAJO', 'CAPTURA_ACTIVA', 'AVISO_CAPTURADO')
ORDER BY ocurrido_en DESC LIMIT 20;
```

En los logs de Railway, un código que no sirve deja `captura por atajo rechazada: código inexistente o
revocado` (sin el código ni el cuerpo).

## 7. Desde la terminal (opcional)

```sh
read -s CODIGO   # pega el código; así no queda en el historial del shell
curl -s -X POST https://movi-project-production.up.railway.app/api/captura/atajo \
  -H "Authorization: Bearer $CODIGO" -H 'Content-Type: application/json' \
  -d '{"tipo":"transaccion","comercio":"Prueba","monto":"$1.000,00","tarjeta":"Nu"}'
```

## Regenerar los atajos

`python3 scripts/generar-atajos.py` (en una Mac: firma con `shortcuts sign --mode anyone`). El firmado se llama
como el atajo (`Movi - Avisar un mensaje.shortcut`): el nombre tiene que ser el de `NOMBRE_DEL_ATAJO_DE_MENSAJES`
(`LosAtajosDelRepoTest` lo compara con el del script). Para apuntarlos a
un server de desarrollo sin tocar los del repo:
`python3 scripts/generar-atajos.py --url http://<ip-de-la-mac>:8080 --salida /tmp/atajos-dev --plist /tmp/atajos-dev`.
