# Atajos de iPhone: cómo probarlos con el iPhone de Caro (vuelta 111)

Lo que hay que hacer **una vez** en un iPhone real para dejar andando la captura por Atajos, y qué mirar para
saber que funcionó. Nada de esto se pudo probar en la Mac: los disparadores «Mensaje» y «Transacción» solo
existen en el iPhone.

**Antes**: esta vuelta tiene que estar desplegada (`./scripts/desplegar.sh`). Sin eso, `/api/captura/codigo` da
404 y el botón «Agregar el atajo de mensajes» no descarga nada.

## 1. El código

1. En el iPhone de Caro, abre Movi en Safari (o la app de iOS) con **su** cuenta.
2. Tú → Ajustes → **«Mensajes del banco»** → sección **«Desde tu iPhone»** → **«Crear tu código»**.
3. Toca **«Copiar»**. El código se ve una sola vez: si se pierde, «Crear otro» (el anterior deja de servir).

## 2. El atajo de mensajes (firmado)

1. En la misma sección toca **«Agregar el atajo de mensajes»**. Safari descarga `movi-mensaje.shortcut`.
2. Ábrelo (desde la barra de descargas o Archivos): Atajos muestra «Movi · Avisar un mensaje» con sus acciones.
3. Toca **«Agregar atajo»**. Atajos pregunta «Pega tu código de Movi…»: pega el código y sigue.

**Qué anotar**: si Safari no descarga, si Atajos dice que el atajo no se puede abrir, o si no aparece la pregunta
del código. En ese caso, arma el atajo a mano con la receta que muestra la app (botón «Agregar el atajo de
mensajes» cuando el server no tiene el firmado, o los mismos pasos que el de Apple Pay de abajo, con «tipo» =
«mensaje» y «texto» = «Entrada del atajo»).

## 3. Probarlo a mano

1. En Atajos toca «Movi · Avisar un mensaje». Como corre sin automatización, pide un texto.
2. Pega el texto de prueba (en la app: «Copiar el texto de prueba»), o este SMS real de Bancolombia de los
   fixtures del repo (`server/src/test/.../sms/AvisosDelMismoPagoTest.kt`):

   ```
   Bancolombia: Compraste $12.300,00 en PANADERIA LA ESQ con tu T.Deb *1111, el 25/09/2026 a las 09:15.
   ```

3. En Movi, «Por revisar» tiene que mostrarlo en unos segundos, con origen **«Atajo · Bancolombia»**. En
   «Desde tu iPhone» la línea pasa a «último aviso hace un momento».
4. Corre el atajo otra vez con el **mismo** texto: no tiene que aparecer una segunda fila (dedupe).
5. Córrelo con un texto personal («Hola, ya llegué»): no tiene que aparecer nada (se descarta y solo se cuenta).
6. Después, ignora los de prueba en «Por revisar».

## 4. La automatización «Mensaje»

1. Atajos → **«Automatización»** → **«+»** (o «Nueva automatización»).
2. **«Mensaje»** → **«El mensaje contiene»** → «Bancolombia» (una automatización por banco de sus cuentas; la
   app sugiere las palabras).
3. **«Ejecutar de inmediato»** → **«Siguiente»** → el atajo **«Movi · Avisar un mensaje»**.
4. Para probarla sin esperar una compra: desde otro teléfono mándale a Caro un SMS que diga
   `Bancolombia: Compraste $1.000,00 en PRUEBA DE MOVI con tu T.Deb *0000.` y mira «Por revisar».

**Qué anotar** (son las cosas que no se pudieron verificar):
- Los nombres exactos de las pantallas en iOS en español: «Automatización», «Mensaje», «El mensaje contiene»,
  «Ejecutar de inmediato» (en iOS 17 y 18 era la opción frente a «Ejecutar tras confirmación»; en iOS 26
  puede ser el interruptor «Preguntar antes de ejecutar», que va **apagado**). Si difieren, corregir
  `pasosDeLaAutomatizacionDeMensajes` en `shared/.../ui/sms/DesdeTuIphone.kt`.
- Que la **entrada del atajo**, convertida en texto, sea el **contenido del SMS** (y no otra cosa, como el
  remitente). Si llega otra cosa, se ve en «Por revisar» o no llega nada (lo descarta el filtro).
- Que corra **con el iPhone bloqueado** y sin pedir confirmación.

## 5. Apple Pay (a mano)

El atajo de Apple Pay **no está firmado**: para mandar comercio, monto y tarjeta hay que leer propiedades de la
transacción de Wallet cuyos nombres internos no se pueden comprobar en una Mac (el borrador está en
`scripts/atajos/movi-apple-pay.plist`). Se arma a mano, con la receta de la app («Agregar el atajo de Apple
Pay»):

1. Atajos → «+» → nombre «Movi · Pago con Apple Pay».
2. Acción **«Obtener contenido de URL»**, URL `https://movi-project-production.up.railway.app/api/captura/atajo`.
3. Método **POST**; en **Encabezados**, `Authorization` = `Bearer ` + el código.
4. **Cuerpo de la solicitud: JSON**, campos de texto: `tipo` = `transaccion`; `comercio`, `monto` y `tarjeta` =
   esas propiedades de la **Entrada del atajo** (toca la variable y elige cada una).
5. Automatización → «+» → **«Transacción»** → marca las tarjetas → «Ejecutar de inmediato» → «Siguiente» → ese atajo.
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

-- Lo que se descartó (sin texto): cuántos por día y motivo (NO_ES_DEL_BANCO, VACIO).
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

`python3 scripts/generar-atajos.py` (en una Mac: firma con `shortcuts sign --mode anyone`). Para apuntarlos a
un server de desarrollo sin tocar los del repo:
`python3 scripts/generar-atajos.py --url http://<ip-de-la-mac>:8080 --salida /tmp/atajos-dev --plist /tmp/atajos-dev`.
