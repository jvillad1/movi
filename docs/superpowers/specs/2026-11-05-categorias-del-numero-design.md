# Las categorías de un número, y la nota del aviso

*5-nov-2026 · aprobado por el dueño tras ver el antes y el después en su teléfono (prototipo
desechable en la rama local `spike/categorias-del-numero`, que no se publica).*

## Por qué

William lo dijo en una nota de voz: cuando le llega el aviso de un pago a un comercio **por número
de cuenta o por llave** (un QR, una transferencia), quiere poder decir para qué fue. A la misma
llave a veces le paga el parqueadero y a veces la gasolina.

Movi ya aprende la categoría por el número (la huella `llave:`/`cuenta:` de
`huellaDeUnMovimiento`, en `:core` `MemoriaDeCategorias.kt`), pero **propone una sola**: la más
usada (`recuerdosPor`), o la del último envío por el mismo monto exacto si el número es de alguien
guardado (`conElMotivoDelEnvio`, `MotivoDelEnvio.kt`). En Revisar aviso eso se ve como
«Parqueadero · Así lo anotaste 3 veces · Cambiar», y Gasolina —la otra cosa que se le paga a esa
llave— no aparece por ningún lado: hay que tocar «Cambiar» y buscarla entre todas.

Y no hay dónde escribir para qué fue: el aviso guarda el texto del banco, y la única nota que
existe es la que trae el correo de PSE (`ParsedSms.nota`).

## Qué se construye

### 1. Las categorías del número van primero

Cuando el número del aviso (llave o cuenta) **ya se anotó con dos categorías distintas o más**,
Movi las ofrece primero, de la más usada a la menos usada, y dice por qué.

**En Revisar aviso** (lo que el dueño vio y aprobó):

- Las pastillas de `ds/FilaDeCategorias` arrancan **abiertas**, sin tocar «Cambiar».
- Las categorías del número van primero, en su orden; después, las de siempre (las frecuentes del
  dueño), sin repetir.
- La categoría ya elegida sigue siendo la que propone el server hoy (la más usada, o la del mismo
  monto exacto por `conElMotivoDelEnvio`). Esta vuelta no cambia **cuál** se propone, solo **qué
  más** se ofrece al lado.
- Encima de las pastillas, una línea que explica: «A esta llave le pusiste Parqueadero 3 veces y
  Gasolina 2» (ver «Lo que decidí yo» para el texto exacto).
- **La explicación de siempre se queda** (decisión mía tras la revisión; en el prototipo la línea
  nueva la tapaba). La fila de la categoría sigue diciendo por qué está elegida la que está
  (`aprendidoDe`: «Así lo anotaste 3 veces», o «La última vez que le mandaste $9.000 a … fue
  Gasolina» si salió por el mismo monto), y la línea nueva va aparte, encima de las pastillas. Así
  nunca queda elegida una categoría sin que se diga por qué.

**En la tarjeta de Por revisar**: solo cuando la tarjeta ya ofrece elegir categoría (el aviso
confirmaría y la toca «Cambiar», o le falta solo la categoría: `EligeLaCategoria`), las del número
van primero, las pastillas arrancan abiertas y va la misma línea. **En el caso de William la
tarjeta no cambia**: un pago a una llave sin nombre manda a «Revisar» (la tarjeta no ofrece
categorías ahí) y el cambio se ve en Revisar aviso. El dueño lo vio así y lo aprobó.

**Con una sola categoría para el número no cambia nada**: la propuesta de siempre basta.

### 2. La fila «Nota» en Revisar aviso

Debajo de la categoría (dentro de la misma superficie), una fila «Nota» con el campo «¿Para qué
fue? (opcional)». Lo escrito se guarda en la descripción del movimiento después de « · », por la
regla que ya existe (`descripcionConLaNota`): «Pago QR · llave 0099887766 · parqueadero del
centro». El comercio (`merchant`) no cambia, así que la huella y la memoria siguen reconociendo el
número.

## De dónde salen las categorías del número: del server

En el prototipo se contaron en el cliente, con los movimientos que la app ya tenía leídos. **Lo de
verdad las calcula el server**, por tres razones:

1. **El nombre que la memoria pone tapa el número.** Si el dueño ya le puso nombre a esa llave,
   `conLoQueMoviRecuerda` reemplaza `merchant` por ese nombre antes de mandarle el aviso a la
   app. Del lado del cliente ya no hay número para buscar.
2. **La historia completa está allá.** El server arma la memoria con todo lo anotado
   (`anotacionesPasadasDe`); el cliente solo tiene lo que leyó.
3. **Una sola regla.** La memoria ya agrupa por huella y por categoría para elegir la ganadora
   (`recuerdosPor`): ahí mismo está la distribución entera, y se usa esa, en vez de contar otra vez
   con otra regla.

Concretamente:

- `:core`: `@Serializable data class CategoriaDelNumero(val categoria: String, val veces: Int)` y
  un campo nuevo, **opcional y con valor por defecto**, `ParsedSms.categoriasDelNumero:
  List<CategoriaDelNumero> = emptyList()`. Un APK viejo lo ignora (`ignoreUnknownKeys`).
- `MemoriaDeCategorias` guarda, además de la ganadora, la distribución por categoría de cada
  huella **que es un número** (`llave:` y `cuenta:`), y la expone con una función pura
  (`categoriasDelNumero(texto, tipo)` o como el builder prefiera nombrarla). Mismo filtro que la
  memoria: sin las categorías que no aprende (`CATEGORIAS_QUE_LA_MEMORIA_NO_APRENDE`) ni los
  textos rotos (`textoQueNoSeAprende`); lo que entra y lo que sale, cada uno en su lado, como
  `recuerdoDe`.
- Orden: más veces primero; a igual número de veces, la usada más recientemente primero (el mismo
  desempate que `recuerdosPor`).
- El server llena el campo al armar la propuesta del aviso (donde hoy corre
  `conLoQueMoviRecuerda`/`conLaMemoriaYLosTerceros`), buscando con el texto del banco **antes** de
  que el nombre recordado lo reemplace. Solo si son dos o más; con menos, va vacío.
- No se llena en lo que la memoria no toca (`CATEGORIAS_QUE_LA_MEMORIA_NO_TOCA`: pago de tarjeta,
  avance, lo que clasifica el correo de PSE) ni cuando la propuesta sale por marca (`porMarca`).

**Lo que ya se anotó con un nombre también cuenta** (decisión mía tras la revisión). En cuanto el
número tiene nombre —la memoria lo renombra, es un tercero guardado («Transferencia a Caro») o el
dueño escribió el comercio en Revisar aviso—, lo confirmado se guarda con ese nombre en `merchant` y
su huella deja de ser el número. Contar solo por `merchant` dejaría la lista vacía o vieja justo en
los casos con historia. Por eso, para las categorías del número, un movimiento cuenta si **su
`merchant` o su texto del banco guardado (`rawPayload`, el SMS con el que se confirmó)** nombran ese
número — la misma razón por la que `vaHaciaElDestino` ya lee `rawPayload`. Del texto del banco se
saca el destino con la lectura que ya existe (`identificadorDelDestinoEn`), nunca con la regex
`CUENTA` sobre el texto entero. Un movimiento cuenta una sola vez aunque lo nombren los dos.
`anotacionesPasadasDe` tiene que leer también `raw_payload` (hoy no lo selecciona).

**Ojo con la huella**: se busca sobre el comercio que leyó el parser (`ParsedSms.merchant`, «Pago
QR · llave 0099887766»), nunca sobre el texto entero del SMS, porque `CUENTA` (`\*\s?(\d{4,})`)
agarraría el «\*8133» de la cuenta propia del dueño. En el prototipo eso funcionó así.

La app no calcula nada: pinta lo que llega en `categoriasDelNumero`.

## Metas

- Que a William le aparezca Gasolina al lado de Parqueadero cuando paga a la misma llave, sin
  buscarla.
- Que pueda escribir para qué fue, y que quede en el movimiento.
- Que la línea diga de dónde sale la sugerencia, como toda sugerencia de Movi («una sugerencia que
  se explica se puede rechazar»).

## Lo que no se hace

- **No cambia cuál categoría se propone.** Sigue la más usada o la del mismo monto exacto.
- **La tarjeta no ofrece categorías donde hoy no las ofrece** (el pago a una llave sin nombre sigue
  yendo a «Revisar»). Abrir la tarjeta a elegir categoría en ese caso es otra conversación.
- **La nota no va en la tarjeta**, solo en Revisar aviso.
- **Nada de esto vale para Agregar** (el gasto escrito a mano) ni para la importación de extractos.
- **No se migra nada** ni se agrega columna: la nota vive en `description` como la de PSE.
- **Nada se anota solo**, como siempre.

## Lo que lo haría un fracaso aunque funcione

- Que la nota del dueño termine propuesta como **nombre** del destinatario la próxima vez. La
  memoria propone como nombre la `description` del movimiento más reciente
  (`AnotacionPasada.nombre` = `Events.description`, y `conLoQueMoviRecuerda` lo pone en `merchant`
  cuando la huella es un número). Si la descripción es «Parqueadero Calle 85 · lo de ayer», el aviso
  siguiente no puede llegar con «· lo de ayer» pegado. **El builder tiene que comprobarlo con una
  prueba** y, si pasa, quitar la nota del nombre recordado (la misma forma en que se arma con
  `descripcionConLaNota`). Lo mismo ya podía pasar con la nota de PSE; se arregla para las dos.
- Que la línea se corte a lo ancho de un teléfono (en el prototipo decía «…Parqueadero (3) ·
  Gasoli…»). Tiene que leerse entera a 360 dp y con letra grande (las pruebas de 1,456 de la vuelta
  56).
- Que la pantalla salte cuando llega la respuesta: la fila de pastillas abierta y la línea no pueden
  empujar lo de abajo después de pintarse (las guardas de «nada salta más de 8 dp»).

## Lo que decidí yo (el dueño puede cambiarlo)

- **El texto de la línea**: «A esta llave le pusiste Parqueadero 3 veces y Gasolina 2» (con «A esta
  cuenta» cuando es una cuenta, por `identificadorEsLlave`; «1 vez» en singular). Si son más de
  tres categorías, las tres primeras y «… y N más». Puede bajar a dos renglones; nunca se corta con
  «…».
- **Cuántas pastillas del número**: todas las que haya (van primero y después siguen las de
  siempre, sin repetir); en la práctica son dos o tres.
- **Solo llaves y cuentas**: el cajero (`cajero:`) también es una huella de número, pero un retiro
  es efectivo, no hay «para qué» que distinguir. Queda afuera.
- **Lo que entra también**: un ingreso de una cuenta que llegó con dos categorías ofrece las dos, en
  su propio lado de la memoria. Es la misma regla y no cuesta nada más.
- **La nota arranca con lo que trae el aviso**: si el correo de PSE ya trae una «Descripción»
  (`ParsedSms.nota`), el campo arranca con ese texto y el dueño lo edita. Es una sola nota, nunca
  dos pegadas.
- **Largo de la nota**: hasta 80 caracteres (la descripción es `varchar(255)` y el texto del banco
  ya ocupa una parte).
- **Dónde va la Nota**: en el modo de siempre (un movimiento) y en el de dos patas (cuota, pago de
  tarjeta, traspaso: `DosPatasDelAviso.nota` ya existe y ya se pega con « · »). En el modo «Repartir
  en partes» no va: ahí cada parte ya lleva la nota del aviso.
- **«Es este» (ya anotado)**: no escribe nada, así que la Nota no se ofrece cuando la acción
  principal es vincular un movimiento que ya existía.
- **Plataformas**: Revisar aviso es `commonMain`: llega igual a la web, al Android y al iPhone.

## «Confirmar los N» (decidido 5-nov, con la propuesta)

- Un aviso cuyo número tiene un nombre (un tercero guardado) se puede confirmar en la tarjeta y
  entraba al lote, que lo confirmaba callado con la categoría más frecuente: justo el error que
  William no quiere. **Un aviso con 2 o más categorías del número sale del lote** y se confirma uno
  por uno, **salvo** que la categoría venga del mismo monto de la vez pasada
  (`conElMotivoDelEnvio`, que lo marca en `ParsedSms.porElMismoMonto`). Costo aceptado: algunos que
  antes pasaban solos se confirman a mano. El dueño pidió llevarlo a la app sin cambiar la propuesta.

## Lo que queda para el builder

- El nombre de la función de `:core` y dónde se guarda la distribución dentro de
  `MemoriaDeCategorias`.
- En qué punto exacto del armado de la propuesta se llena el campo (tiene que ser con el texto del
  banco, antes del nombre recordado).
- Cómo se abre la fila de pastillas sin que salte (que arranque abierta desde el primer cuadro en
  que se sabe la lista).
- Las pruebas: la regla pura en `:core` (dos categorías, una sola, empate, categorías que no se
  aprenden, ingresos aparte, el «\*8133» propio que no cuenta), la ruta del server que llena el
  campo y lo deja vacío donde corresponde, la pantalla de Revisar aviso (orden de las pastillas,
  la línea entera a 360 dp y a 1,456, la nota en la descripción guardada) y la del nombre recordado
  que no arrastra la nota.
- Documentarlo en `CLAUDE.md` y en `docs/diseno/sistema.md` como se hace en cada vuelta.
