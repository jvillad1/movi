# Movi AI: un asesor que no inventa (2026-09-23)

## El pedido

> «segui trabajando para que Movi AI sea deterministico sin perder toda la magía que da la GenAI pero
> sabiendo responder cosas desde datos que tiene y conoce sin inventar o asumir tantas cosas, llegando
> a un buen balance en este aspecto, la idea es que sea tu asesor de finanzas personales»

## El caso que lo motivó (23-sep, teléfono del dueño, APK 1.43)

Pregunta sugerida por el Inicio: «¿Por qué Hipotecario 2334 no baja aunque pago la cuota?». Respuesta
(Haiku, sin herramientas, `criterio = f`):

- «la cuota que paga **no alcanza a cubrir los intereses**» → y en el renglón siguiente mostró cuota
  $2.613.714 > intereses $2.427.883. **Se contradijo.** Causa: el contexto decía esa frase falsa
  (arreglado en #375: ahora trae la resta con los seguros).
- «**la diferencia es apenas $185.831** al mes, que es lo que baja la deuda» → **cifra calculada por el
  modelo**, sin los seguros ($209.219). La deuda en realidad CRECE ~$23.388/mes. El modelo hizo una
  cuenta que Movi ya tenía hecha bien, y la hizo mal.
- «esa cuota la paga **Skandia (tu seguro)**» → **supuesto inventado** sobre qué es una entidad.
  Skandia es su pensión voluntaria (arreglado en #375 para `paid_by`).
- «no estás aportando nada extra de tu plata» → **supuesto** que el contexto no sostiene.

Los tres tipos de falla: **frase falsa en los datos**, **aritmética del modelo**, **supuestos sobre
lo que no está en los datos**.

## Lo que la API permite (y lo que no)

- **Sonnet 5 no acepta `temperature`/`top_p`/`top_k`** (400). Haiku 4.5 sí. La determinación no puede
  venir de una perilla del modelo en el camino de consejos: tiene que venir de **qué datos recibe** y
  de **verificar lo que contesta**.
- Sonnet 5: thinking adaptativo; esfuerzo por `output_config.effort` (ya en `medium`, #367).

## La idea: separar lo que es DATO de lo que es CRITERIO

El dato lo pone Movi, calculado por código y verificable. El criterio (qué priorizar, cómo decirlo,
qué preguntar) lo pone el modelo. La «magia» queda en el criterio y en el lenguaje; los números no
se inventan nunca.

### 1. Hechos exactos para ESTA pregunta (determinístico, sin LLM)

Antes de llamar al modelo, el server mira la pregunta y **enlaza por nombre** lo que nombra: cuentas,
créditos, tarjetas, bienes, categorías, presupuestos, recurrentes, «este período», «patrimonio»,
«intereses». Para cada cosa enlazada agrega un bloque «Datos exactos para esta pregunta» con TODO lo
que el modelo podría querer calcular, **ya calculado**: para un crédito, saldo, tasa, cuota, seguros,
otros cargos, lo que queda de la cuota, interés del mes, cuánto baja o crece la deuda, cuotas
restantes, quién paga (y si es una cuenta propia); para una categoría, lo gastado en el período, el
límite, cuánto falta o cuánto se pasó, los 3 movimientos más grandes; etc. Mismas funciones que las
pantallas (`planDeUnaDeuda`, `patrimonioDe`, …): la cifra del chat y la de la pantalla son la misma.

Si la pregunta no nombra nada, no se agrega nada (el contexto general ya está).

### 2. Contrato explícito en la PERSONA

- **No calcules.** Toda cifra que digas tiene que estar en los datos que recibiste o en lo que
  devolvió una herramienta. Si necesitás una cifra que no está, decí cuál falta y ofrecé averiguarla
  (herramienta) o pedísela al dueño.
- **No supongas qué es algo.** Si un nombre (una entidad, una cuenta, un tercero) no está explicado en
  los datos, no le inventes una naturaleza.
- **Separá** lo que dicen sus datos de lo que le recomendás. Para preguntas de criterio: diagnóstico
  en una frase → 2 o 3 acciones concretas con cifras de sus datos → lo que habría que confirmar.
- Está bien decir «no lo sé con estos datos».

### 3. Verificador de cifras (determinístico, después de la respuesta)

Del texto de la respuesta se extraen **todas las cifras de plata y porcentajes** («$2.613.714»,
«$2,6M», «15,24 %», «US$71»). Cada una tiene que tener respaldo en el conjunto de números conocidos
de ese turno (contexto + bloque de hechos + resultados de herramientas + la pregunta), aceptando:
- los formatos de la app (miles con punto, compacto «$2,6M» redondeado a un decimal, millones
  «$2.191M»), y
- sumas o diferencias de DOS números conocidos (lo que un asesor dice legítimamente: «te faltan $X»).

Cifras sin respaldo → **un solo reintento** con un mensaje de corrección («Estas cifras no están en
los datos: …. Reescribí usando solo cifras de los datos, o decí que no lo sabés») y el mismo modelo.
Si el reintento sigue con cifras sin respaldo, se entrega igual pero **se registra** (columna nueva en
`ai_turns`, p. ej. `cifras_sin_respaldo`) y al final de la respuesta va una línea honesta y corta
(«No pude verificar estas cifras con tus datos: …»). Nada de borrar texto a ciegas.

Es la guarda que hace medible el «sin inventar»: la tabla `ai_turns` dice cuántas respuestas tuvieron
cifras sin respaldo.

### 4. Temperatura baja donde se puede

En el camino de datos (Haiku), `temperature` baja (0,2 o lo que la skill recomiende): misma pregunta,
misma respuesta. En Sonnet 5 no existe el parámetro: ahí mandan 1-3.

## Cómo se sabe que funcionó

- Pruebas unitarias del verificador (formatos, redondeos, sumas/diferencias, falsos positivos: años,
  días del mes, cantidad de cuotas, números de cuenta de 4 dígitos no son plata).
- Pruebas del enlazado de entidades con los nombres reales del dueño.
- **Regresión con la respuesta real del 23-sep**: con el contexto nuevo, «$185.831» ya no tiene
  respaldo (no es cuota − interés − seguro de ninguna combinación de DOS números… verificar) — o, si
  lo tiene, que el bloque de hechos haga innecesario calcularlo.
- **Prueba en vivo en el teléfono del dueño** después de desplegar: las 3 sugerencias del Inicio + 2
  preguntas abiertas; cada cifra de cada respuesta cotejada contra la base.

## Cómo quedó (implementado el 23-sep)

### Piezas

| Pieza | Archivo | Qué hace |
|---|---|---|
| Hechos de la pregunta | `server/.../ai/HechosDeLaPregunta.kt` | `queNombraLaPregunta` enlaza por nombre (entero, o por una palabra que solo ese nombre tiene: «2334», «Skandia», «Almendros»; sin tildes ni mayúsculas; «hipoteca», «tarjeta», «cuenta» solas NO enlazan). `hechosParaLaPregunta` arma un bloque por cosa nombrada (máximo 5) y por palabra clave («período», «patrimonio», «intereses», «presupuesto»). Mismas funciones que las pantallas (`planDeUnaDeuda`, `patrimonioDe`, `estadoDePresupuesto`, `deudaDelBien`). |
| Contrato en la PERSONA | `server/.../routes/AiRoutes.kt` | «CIFRAS — NO CALCULES» (solo suma o resta de DOS datos, con la operación escrita), «NO SUPONGAS», separar datos de recomendación, «no lo sé con estos datos». Se quitó el ejemplo «un tercero (por ejemplo Skandia…)», que era justo un supuesto. |
| Verificador de cifras | `server/.../ai/VerificadorDeCifras.kt` | Extrae plata y porcentajes (solo lo que lleva `$`, `US$`, `COP`, `USD`, «pesos», «millones», «mil», `M` o `%`) y los busca en contexto + hechos + resultados de herramientas + conversación. |
| Reintento | `server/.../ai/ResponderSinInventar.kt` | Caso normal: nada más. Con cifras sin respaldo: UN reintento al mismo modelo, sin herramientas (`tool_choice: none`, no toca el prefijo cacheado). Si persiste, línea final «No pude verificar estas cifras con tus datos: …». Con foto adjunta no se verifica (los montos salen de la imagen). |
| Registro | `ai_turns.cifras_sin_respaldo`, `ai_turns.cifras_corregidas` | Texto nullable (`«$185.831 · 12 %»`). NULL = limpia. `AiTurns` entró a `createMissingTablesAndColumns`. |
| Temperatura | `ElModeloDeAnthropic.armarLlamada` | `0.2` solo cuando el modelo es Haiku y no piensa. El respaldo (Opus 4.7) se arma de nuevo para su modelo y no hereda la temperatura (antes se copiaban los params: habría sido un 400 seguro). |
| Rótulo de seguros | `renglonDelCredito`, bloque de hechos | «incluye seguros por $X» en vez de «seguro de vida $X»: `insurance_monthly` suma todos los seguros (en el 2334, $209.219 = vida $69.600 + incendio y terremoto $139.619). Con el rótulo viejo, la respuesta buena de la tarde del 23-sep (con #375) dijo «el seguro de vida de $209.219», su único error. |

### El bloque de hechos del 2334 (lo que ve el modelo)

```
DATOS EXACTOS PARA ESTA PREGUNTA (calculados por Movi con las mismas cuentas que sus pantallas; usa estas cifras tal cual y no las recalcules):

Crédito «Hipotecario 2334» (Davibank):
- Debe hoy: $204.183.376
- Tasa: 15,24 % EA (1,19 % mensual)
- Cuota: $2.613.714 el día 5; plazo pactado 240 meses
- Seguros dentro de la cuota (todos los que cobra el crédito): $209.219 al mes (no bajan la deuda)
- De la cuota, después de $209.219 de cargos, le quedan $2.404.495 para interés y capital
- Quién paga la cuota: se paga con plata de su propia cuenta «Skandia pensión voluntaria» (no es un seguro ni un tercero); no sale de su plata del día a día, pero es plata suya
- Interés de este mes: $2.427.883 (el 92,9 % de la cuota)
- Qué hace la cuota con la deuda: la deuda CRECE unos $23.388 al mes aunque pague, porque lo que queda de la cuota no alcanza para el interés
- A este ritmo NO se termina de pagar
```

Va como un bloque de texto aparte **en el último mensaje del dueño**, después de su pregunta: la
PERSONA y el contexto siguen cacheados y en el mismo orden.

### Qué respalda una cifra

- La cifra tal cual, con la tolerancia que ella misma dice tener: `$2.613.714` ±1; `$2,6M` ±50.000;
  `$2.191M` ±500.000 (millones con punto de miles); `$2.600.000` hasta el 1 % por los ceros finales;
  `15,24 %` ±0,005. Miles con punto o con coma, decimales con coma o con punto.
- Un porcentaje solo lo respalda un número que en los datos lleva `%` (un plazo de 180 meses no
  respalda «180 %»).
- La suma o la resta de DOS números **del mismo bloque** de los datos (el renglón de un crédito, la
  sección de un presupuesto, el bloque de hechos de una cosa), o de dos cifras respaldadas **que la
  respuesta misma cita** («$2.613.714 − $209.219 = $2.404.495»). No de dos cualesquiera: con
  doscientos números hay decenas de miles de pares y alguno cae cerca de casi cualquier invento.

### La regresión del 23-sep y el límite conocido

**$185.831 SÍ es una resta de dos números conocidos**: cuota $2.613.714 − interés $2.427.883, los
dos en el mismo renglón. Por números solos, el verificador la acepta. Es el límite conocido: **una
cuenta válida con el significado equivocado no se detecta por números**.

Para este caso —el más dañino, porque invierte el diagnóstico— se agregaron las **cifras trampa**
(`cifrasTrampa`): en todo crédito con seguros u otros cargos dentro de la cuota, `cuota − interés`
se declara de antemano como la lectura equivocada de «lo que baja la deuda». No viajan al modelo
(cero fichas); solo las usa el verificador. Con ellas, la respuesta del 23-sep queda marcada
(`HechosDeLaPreguntaTest › la respuesta del 23-sep…`), y la prueba también fija que sin trampas
pasaría. Para cualquier otra resta bien hecha y mal leída, la defensa es el bloque de hechos, que ya
trae la cuenta correcta con su nombre.

Otros límites: no verifica multiplicaciones («$2,4M al año» se marca y se reintenta); no lee
imágenes; una cifra que el modelo copia de un turno anterior suyo cuenta como respaldada.

### Costo esperado

- **Caso normal: igual que antes + el bloque de hechos** cuando la pregunta nombra algo (unas
  150–350 fichas de entrada sin caché; Haiku ≈ US$0,0002–0,0004 por pregunta, Sonnet ≈ el doble).
  Sin nada nombrado, cero.
- **Reintento: solo con cifras sin respaldo.** Una llamada que lee el prefijo desde la caché y paga
  la respuesta vieja, la corrección y la nueva: Haiku ≈ US$0,002–0,005; Sonnet con pensamiento ≈
  US$0,01–0,05. Cuántas veces pasa se mide con `ai_turns.cifras_corregidas is not null`.
- La temperatura y las trampas no cuestan nada.

### Pendiente

- Prueba en vivo en el teléfono del dueño después de desplegar (las 3 sugerencias del Inicio + 2
  preguntas abiertas), cotejando cada cifra contra la base, y una semana después mirar
  `select count(*) filter (where cifras_corregidas is not null), count(*) filter (where cifras_sin_respaldo is not null), count(*) from ai_turns where created_at > …`.
