package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # Una cuenta que no es suya, guardada con nombre
 *
 * El dueño lo pidió con un caso concreto: *«Es la cuenta de Caro, yo le transferí a ella lo de
 * Cotrafa. Guarda esa cuenta como una cuenta no mía pero sí de mi esposa, me interesa tenerla
 * guardada y poder ver los movimientos hacia esa cuenta.»*
 *
 * ## Por qué NO es una [Account]
 *
 * Es la decisión de fondo de todo este archivo, y es de plata: una `Account` **entra en las
 * cifras**. Suma en «Tu plata», suma en el patrimonio, aparece en la lista de cuentas y en todo
 * selector de «¿de dónde sale?». La cuenta de su esposa no es plata suya: anotarla como cuenta le
 * inflaría el patrimonio con dinero de otra persona, que es exactamente la clase de mentira
 * silenciosa que Movi viene cerrando (ver `void_events` y los SUM que los excluyen).
 *
 * Así que esto es un **registro de números de cuenta ajenos**, y nada más: un nombre, el número, y
 * de quién es. No tiene saldo, no tiene moneda, no tiene tipo y no se puede anotar un movimiento
 * «en» ella. Hace dos cosas, las dos sobre movimientos que siguen viviendo en las cuentas de él:
 *
 * 1. **Le pone nombre a lo que el banco manda sin nombre.** Ver [conElDestinoConocido]: un SMS que
 *    dice «a la cuenta *31973270756» se propone como «Transferencia a Caro».
 * 2. **Junta lo que fue para allá.** Ver [movimientosHaciaElDestino] y [totalesHaciaElDestino].
 *
 * ## Los campos, y por qué no hay más
 *
 * Un nombre, el número (o la [llave], o los dos — 29-sep) y opcionalmente de quién es. Nada de
 * saldo, de cupo ni de moneda: cada campo
 * que se agregue acá es un campo que alguien va a querer que cuadre con algo, y no hay nada con qué
 * cuadrarlo — Movi no ve el extracto de la cuenta de otra persona.
 *
 * [totales] y [cuantos] son **derivados y nunca almacenados**: los calcula cada lectura a partir de
 * los movimientos del dueño, igual que [Goal.saved] sale del saldo real de su cuenta. Lo que manda
 * un cliente en ellos se ignora.
 */
@Serializable
data class DestinoConocido(
    /** `dst_<uuid>`, lo pone el server al crear. Vacío en el cuerpo de un POST. */
    val id: String = "",
    /** Cómo lo reconoce el dueño: «Caro». Es lo que va a leer en sus movimientos. */
    val nombre: String,
    /**
     * El número de la cuenta como lo escribió, solo dígitos (ver [soloLosDigitos]). **Vacío** cuando
     * el destino se conoce solo por su [llave] (29-sep): la columna es la de siempre, y ninguna
     * fila que ya existe cambia.
     */
    val numero: String,
    /** «esposa», «papá». Opcional: el nombre suele alcanzar, y obligar a llenarlo no agrega nada. */
    val deQuien: String? = null,
    /**
     * **La llave**, el otro identificador (29-sep): `@usuario`, un celular, un correo o un código
     * numérico — Nu y Bancolombia (Bre-B) identifican por llave en vez de por número de cuenta, y
     * el mismo destino puede tener las dos cosas (la cuenta de Caro Y su llave). También guarda el
     * nombre con que el banco nombra a quien manda plata («Te llegó dinero de CAROLINA RESTREPO
     * SALAZAR con tu llave»). Normalizada con [normalizarLlave]; se compara **exacta**.
     *
     * `null` en todo destino anterior y en todo cuerpo que mande un APK viejo. Por eso en un `PUT`
     * `null` quiere decir «no la toques» y `""` «bórrala» — ver `DestinoRoutes`.
     */
    val llave: String? = null,
    /**
     * **Cuánto se le mandó, por moneda** — derivado. Un mapa y no un `Long` porque sumar pesos con
     * dólares da un número que no existe, y este destino puede recibir los dos (él tiene cuentas en
     * las dos monedas). Casi siempre va a traer una sola entrada, `COP`.
     */
    val totales: Map<String, Long> = emptyMap(),
    /** Cuántos movimientos se le contaron — derivado. */
    val cuantos: Int = 0,
    /**
     * **Cuánto se le mandó en el período en curso del dueño**, por moneda — derivado, igual que
     * [totales]. Lo lee la tarjeta «Cuentas de otros» de Patrimonio («Caro, Mamá · $X este
     * período»). Por el período DEL DUEÑO (ver [loQueSeLeMandoPorPeriodo]), no por mes de
     * calendario. Vacío si el server no sabe el período (un cliente viejo lo ignora sin más).
     */
    val totalesDelPeriodo: Map<String, Long> = emptyMap(),
    /**
     * **El último envío** — derivado, igual que [totales] (30-sep). Lo lee la ficha de «Cuentas de
     * otros» («Último: Mercado · 18 sep · $2.000.000») para que se sepa cuándo fue la última vez
     * sin abrir el detalle. `null` sin envíos, y en lo que mande un server viejo.
     */
    val ultimo: UltimoEnvio? = null,
    /**
     * **Todas las formas de reconocerlo** (4-oct-2026): números de cuenta, llaves y el nombre con
     * que el banco lo nombra («a DANIEL LEONETT», «Te llegó dinero de CAROLINA RESTREPO SALAZAR»).
     * Un tercero ya no es «un número y una llave como máximo»: Caro tiene su cuenta ·0756 Y la
     * llave-nombre con que Nu la nombra cuando ella le manda plata.
     *
     * [numero] y [llave] **siguen llenos** con el primero de cada clase, para el APK instalado, que
     * no conoce esta lista. Lo que se lee para reconocer es [todosLosIdentificadores], que junta los
     * tres. Vacía en lo que mande un cliente viejo — ver `DestinoRoutes`.
     */
    val identificadores: List<IdentificadorDelDestino> = emptyList(),
    /**
     * **Persona o comercio.** Los pagos por QR suelen ser comercios (la arepería, el parqueadero); las
     * transferencias por llave o a una cuenta, personas. Ordena «Cuentas de otros» en dos secciones.
     * `null` = nadie lo dijo (todo destino anterior): se trata como persona.
     */
    val tipo: TipoDeTercero? = null,
    /**
     * **Lo que te envió**, por moneda — derivado, el espejo de [totales] del otro lado: «Te llegó
     * dinero de CAROLINA RESTREPO SALAZAR» se cuenta acá y no en lo que le mandaste.
     */
    val recibidos: Map<String, Long> = emptyMap(),
    /** Cuántos movimientos te llegaron de este tercero — derivado. */
    val cuantosRecibidos: Int = 0,
    /** Lo que te envió en el período en curso — derivado, como [totalesDelPeriodo]. */
    val recibidosDelPeriodo: Map<String, Long> = emptyMap(),
    /** Lo último que te llegó de este tercero — derivado. `null` si nunca te mandó nada. */
    val ultimoRecibido: UltimoEnvio? = null,
)

/**
 * **¿Persona o comercio?** Separa «Cuentas de otros» en dos secciones y decide cómo se lee la ficha.
 * Se infiere (un pago por QR o un nombre con «SAS» es un comercio) y el dueño lo cambia.
 */
@Serializable
enum class TipoDeTercero { PERSONA, COMERCIO }

/** El tipo, con el `null` de los destinos viejos leído como persona. */
fun DestinoConocido.tipoOPersona(): TipoDeTercero = tipo ?: TipoDeTercero.PERSONA

/**
 * Lo que la ficha dice del último envío a un destino: el nombre que tiene HOY el movimiento (el que
 * el dueño reconoce en Movimientos), el monto, la moneda y cuándo. Una foto liviana y no el
 * [FinancialEvent] entero: la lista de destinos no necesita la cuenta, la categoría ni el texto del
 * banco de cada uno.
 */
@Serializable
data class UltimoEnvio(
    val descripcion: String,
    val monto: Long,
    val moneda: String = "COP",
    val timestamp: Long,
)

/** **Los movimientos que fueron a un destino, con su total.** Lo que contesta `GET /api/destinos/{id}/movimientos`. */
@Serializable
data class MovimientosDelDestino(
    val destino: DestinoConocido,
    val movimientos: List<FinancialEvent>,
    /**
     * **Lo que te llegó de este tercero** (4-oct-2026), aparte de [movimientos] a propósito: el APK
     * instalado pinta [movimientos] como «lo que le enviaste», y mezclarle los ingresos ahí le
     * cambiaría el sentido a su lista.
     */
    val recibidos: List<FinancialEvent> = emptyList(),
)

/** Largo de `known_destinations.nombre` en el server. */
const val MAX_NOMBRE_DEL_DESTINO: Int = 60

/** Largo de `known_destinations.de_quien` en el server. */
const val MAX_DE_QUIEN: Int = 40

/**
 * Mínimo de dígitos de un número de cuenta. Cuatro, como en todo el resto de la app: es lo que el
 * banco escribe cuando escribe poco («*9586») y lo que [ultimosCuatro] compara.
 */
const val MIN_DIGITOS_DEL_NUMERO: Int = 4

/** Largo de `known_destinations.numero` en el server. */
const val MAX_DIGITOS_DEL_NUMERO: Int = 30

const val DESTINO_SIN_NOMBRE: String =
    "Ponle un nombre: es lo que vas a leer en tus movimientos."

const val NOMBRE_DEL_DESTINO_DEMASIADO_LARGO: String =
    "El nombre no puede superar $MAX_NOMBRE_DEL_DESTINO caracteres."

const val DE_QUIEN_DEMASIADO_LARGO: String =
    "«De quién es» no puede superar $MAX_DE_QUIEN caracteres."

const val NUMERO_DEMASIADO_CORTO: String =
    "Escribe al menos los últimos $MIN_DIGITOS_DEL_NUMERO dígitos de la cuenta."

const val NUMERO_DEMASIADO_LARGO: String =
    "Ese número tiene demasiados dígitos. Revisa lo que escribiste."

/** Mínimo de caracteres de una llave: «@jo» no identifica a nadie. */
const val MIN_LARGO_DE_LA_LLAVE: Int = 3

/** Largo de `known_destinations.llave` en el server. */
const val MAX_LARGO_DE_LA_LLAVE: Int = 80

const val FALTA_NUMERO_O_LLAVE: String =
    "Escribe el número de la cuenta (al menos los últimos $MIN_DIGITOS_DEL_NUMERO dígitos) o su llave."

const val LLAVE_DEMASIADO_CORTA: String =
    "Esa llave es muy corta. Escríbela como te la muestra el banco."

const val LLAVE_DEMASIADO_LARGA: String =
    "Esa llave es demasiado larga. Revisa lo que escribiste."

/**
 * **Solo los dígitos.** El dueño va a pegar el número como se lo mandó el banco —`*31973270756`,
 * `319-7327-0756`, con espacios— y el que se guarda tiene que ser el mismo en los tres casos, si no
 * dos destinos iguales se ven distintos y ninguno encuentra sus movimientos.
 */
fun soloLosDigitos(crudo: String): String = crudo.filter { it.isDigit() }

/**
 * Los últimos cuatro dígitos, o `null` si no hay cuatro.
 *
 * **Se compara por los últimos cuatro y no por el número entero**, por el mismo motivo que
 * `cuentaPorElNumero` lo hace con las cuentas propias: el banco a veces escribe el número corto
 * (`*9586`) y a veces el largo (`* 43087514791`), y lo único que las dos formas comparten es la
 * cola.
 */
fun ultimosCuatro(numero: String): String? =
    soloLosDigitos(numero).takeLast(MIN_DIGITOS_DEL_NUMERO).takeIf { it.length == MIN_DIGITOS_DEL_NUMERO }

/**
 * **La única definición de qué destino se acepta**, para que el server y la pantalla digan lo
 * mismo. Devuelve el motivo del rechazo, o `null` si está bien.
 *
 * No incluye la regla de «ese número es de una cuenta tuya» ([cuentaPropiaConEseNumero]): esa
 * necesita las cuentas del dueño, que la pantalla tiene y esta función no, y su mensaje nombra la
 * cuenta que chocó.
 */
fun rechazoDelDestino(nombre: String, numero: String, deQuien: String? = null, llave: String? = null): String? {
    val limpio = nombre.trim()
    if (limpio.isEmpty()) return DESTINO_SIN_NOMBRE
    if (limpio.length > MAX_NOMBRE_DEL_DESTINO) return NOMBRE_DEL_DESTINO_DEMASIADO_LARGO
    if ((deQuien?.trim()?.length ?: 0) > MAX_DE_QUIEN) return DE_QUIEN_DEMASIADO_LARGO
    val digitos = soloLosDigitos(numero)
    val laLlave = llave?.let(::normalizarLlave).orEmpty()
    // Sin llave, el número es obligatorio — la regla de siempre, con el mensaje de siempre.
    if (laLlave.isEmpty() && digitos.isEmpty()) return FALTA_NUMERO_O_LLAVE
    if (digitos.isNotEmpty() || laLlave.isEmpty()) {
        if (digitos.length < MIN_DIGITOS_DEL_NUMERO) return NUMERO_DEMASIADO_CORTO
        if (digitos.length > MAX_DIGITOS_DEL_NUMERO) return NUMERO_DEMASIADO_LARGO
    }
    if (laLlave.isNotEmpty()) {
        if (laLlave.length < MIN_LARGO_DE_LA_LLAVE) return LLAVE_DEMASIADO_CORTA
        if (laLlave.length > MAX_LARGO_DE_LA_LLAVE) return LLAVE_DEMASIADO_LARGA
    }
    return null
}

/**
 * **Un número registrado como ajeno no puede ser una cuenta suya.** Devuelve la cuenta que choca,
 * o `null`.
 *
 * ### Por qué esta guarda es la más importante del archivo
 *
 * Las cuentas de Movi no guardan su número: lo llevan en el NOMBRE, porque así las escribió él
 * («Fiducuenta 9586», «Master Black 3684»). Y `cuentaPorElNumero` resuelve a qué cuenta SUYA se
 * refiere un SMS comparando esos dígitos. O sea que las dos resoluciones —la de sus cuentas y la de
 * estos destinos— leen el mismo dato del mismo texto.
 *
 * Si el mismo número pudiera estar en los dos lados, un SMS de un retiro de su propia Fiducuenta se
 * propondría como «Transferencia a Caro»: un movimiento suyo, con el nombre de otra persona, y
 * después contado en «lo que le mandé a Caro». Por eso el alta lo rechaza de entrada, con el nombre
 * de la cuenta que chocó, en vez de dejarlo entrar y desambiguar después.
 */
fun cuentaPropiaConEseNumero(numero: String, cuentas: List<Account>): Account? {
    val cola = ultimosCuatro(numero) ?: return null
    return cuentas.firstOrNull { cuenta -> elNombreLlevaLaCola(cuenta.name, cola) }
}

/**
 * Lo mismo, pero contra **los nombres** y devolviendo el que chocó.
 *
 * Existe para el server, que solo necesita comparar texto: armar una `Account` entera para eso lo
 * obligaría a inventarle un saldo y un tipo a una fila que nadie va a leer.
 */
fun nombreDeLaCuentaPropiaConEseNumero(numero: String, nombresDeCuentas: List<String>): String? {
    val cola = ultimosCuatro(numero) ?: return null
    return nombresDeCuentas.firstOrNull { elNombreLlevaLaCola(it, cola) }
}

private fun elNombreLlevaLaCola(nombreDeLaCuenta: String, cola: String): Boolean =
    DIGITOS_DEL_NOMBRE.findAll(nombreDeLaCuenta).any { it.value.takeLast(4) == cola }

/** Las corridas de dígitos de un nombre de cuenta: «Fiducuenta 9586» → 9586. */
private val DIGITOS_DEL_NOMBRE = Regex("""\d+""")

/** Lo que se le dice a quien intenta registrar como ajeno un número que es de una cuenta suya. */
fun mensajeDeNumeroPropio(nombreDeLaCuenta: String): String =
    "Ese número es el de tu cuenta «$nombreDeLaCuenta». Un destino es una cuenta de otra persona: " +
        "si la registras aquí, Movi le pondría el nombre de otro a tus propios movimientos."

/** Lo que se le dice a quien registra dos veces la misma llave. */
fun mensajeDeLlaveRepetida(nombreDelOtro: String): String =
    "Ya tienes «$nombreDelOtro» con esa llave. Edítalo en vez de agregar otro: dos destinos con la " +
        "misma llave se llevarían los mismos movimientos."

/** Lo que se le dice a quien registra dos veces el mismo número. */
fun mensajeDeNumeroRepetido(nombreDelOtro: String): String =
    "Ya tienes «$nombreDelOtro» con ese número. Edítalo en vez de agregar otro: dos destinos con el " +
        "mismo número se llevarían los mismos movimientos."

/**
 * Los números de cuenta que un texto del banco nombra.
 *
 * Dos formas, y ninguna más:
 *
 * - **`*` delante** — «a la cuenta *31973270756», «desde tu producto * 8133». Es lo que distingue un
 *   número de cuenta de una fecha, un monto o un teléfono, igual que en `cuentaPorElNumero`.
 * - **la palabra «cuenta» delante** — «a la cuenta 31973270756». Bancolombia manda las dos, y sin
 *   esta segunda forma la mitad de sus transferencias no se reconocerían.
 *
 * **No** se acepta una corrida de dígitos suelta: «Dudas al 6045109009» y todo monto sin separadores
 * pasarían a ser números de cuenta, y una coincidencia falsa acá le pone a un gasto el nombre de
 * otra persona.
 */
private val NUMEROS_QUE_NOMBRA_EL_TEXTO =
    Regex("""(?:\*\s?|\bcuenta\s+\*?\s?)(\d{4,})""", RegexOption.IGNORE_CASE)

/**
 * **A qué destino registrado se refiere un texto que nombra un número**, o `null`.
 *
 * Misma forma que `cuentaPorElNumero` —y a propósito: es la misma pregunta del otro lado del
 * mostrador— con la misma regla de oro: **si coincide más de uno, no se elige ninguno**. Dos
 * destinos con la misma cola de cuatro dígitos es un empate que no se resuelve al azar; el alta ya
 * lo previene (ver [mensajeDeNumeroRepetido]), pero dos números distintos pueden terminar en los
 * mismos cuatro dígitos y ahí no hay nada que deducir.
 *
 * Se recorren los números en el orden en que aparecen: un SMS de traspaso nombra dos —de dónde sale
 * y a dónde va— y el de origen es una cuenta suya, que por construcción no puede estar registrada
 * acá.
 */
fun destinoQueNombra(texto: String, destinos: List<DestinoConocido>): DestinoConocido? {
    if (destinos.isEmpty()) return null
    NUMEROS_QUE_NOMBRA_EL_TEXTO.findAll(texto).forEach { hallazgo ->
        val cola = hallazgo.groupValues[1].takeLast(MIN_DIGITOS_DEL_NUMERO)
        // 4-oct: por CUALQUIERA de sus números, no solo el primero (ver [todosLosIdentificadores]).
        val coinciden = destinos.filter { d -> d.numeros().any { ultimosCuatro(it) == cola } }
        if (coinciden.size == 1) return coinciden.single()
    }
    // 29-sep: y las llaves, que se comparan EXACTAS (ver [llavesQueNombra]). Van después de los
    // números porque un texto que nombra una cuenta de destino ya dijo a dónde fue. Desde el 4-oct
    // entran acá también los nombres con que el banco nombra a la persona («a DANIEL LEONETT»,
    // «recibiste una transferencia de JUAN …»), que se guardan como una llave con espacios.
    llavesQueNombra(texto).forEach { llave ->
        val coinciden = destinos.filter { d -> llave in d.llavesComparables() }
        if (coinciden.size == 1) return coinciden.single()
    }
    return null
}

/** Nombre que Movi propone para lo que LLEGÓ de [destino]: «Transferencia de Caro». */
fun nombreDesdeElDestino(destino: DestinoConocido): String = "Transferencia de ${destino.nombre}"

/** El nombre que Movi propone para un movimiento que va a [destino]. */
fun nombreHaciaElDestino(destino: DestinoConocido): String = "Transferencia a ${destino.nombre}"

/**
 * **¿Este nombre se puede reemplazar por el del destino?**
 *
 * Solo cuando lo que hay es un número o nada. Es la misma regla que usa la memoria de categorías
 * (ver `conLoQueMoviRecuerda`) y por el mismo motivo: si el banco mandó un nombre de verdad
 * («DANIEL LEONETT», «Zelo Group») ese nombre dice más que cualquier cosa que Movi deduzca, y si el
 * dueño ya le puso uno, **ese nombre es suyo**.
 *
 * `huella == null` es «este texto no identifica a nadie» («Transferencia», «Movimiento»): ahí poner
 * el nombre del destino es una mejora estricta.
 */
fun sePuedeRenombrar(nombreActual: String): Boolean {
    val huella = huellaDeUnMovimiento(nombreActual) ?: return true
    return laHuellaEsUnNumero(huella)
}

/**
 * **El SMS leído, con el nombre del destino puesto** si el mensaje nombra un número registrado.
 *
 * Va DESPUÉS de `conLoQueMoviRecuerda` en la cadena del server, y eso es una decisión: la memoria
 * es lo que el dueño ya decidió sobre ese mismo destinatario, así que si él a ese número lo llamó
 * «Mercado», sigue diciendo «Mercado». Este paso solo habla donde no hablaba nadie.
 *
 * [textoDelMensaje] es el SMS completo, no [ParsedSms.merchant]: el número puede no estar en el
 * nombre —justamente porque un paso anterior ya lo reemplazó— y este es el único lugar donde el
 * dato crudo todavía existe.
 *
 * Un gasto se propone «Transferencia a Caro»; un ingreso, «Transferencia de Caro» (4-oct-2026).
 *
 * **El nombre que escribe el banco para la persona** («a DANIEL LEONETT», «Te llegó dinero de …») se
 * reemplaza por el del tercero guardado (4-oct-2026; antes ganaba el del banco): el dueño ya le dio
 * un nombre y es el que pidió leer en cuanto llega el aviso. Un nombre de comercio que no es el de
 * la persona («Zelo Group») o el que puso la memoria, no.
 */
fun conElDestinoConocido(
    parsed: ParsedSms,
    textoDelMensaje: String,
    destinos: List<DestinoConocido>,
): ParsedSms {
    if (parsed.type == TransactionType.INCOME) return conElRemitenteConocido(parsed, textoDelMensaje, destinos)
    if (parsed.type != TransactionType.EXPENSE) return parsed
    val destino = destinoQueNombra(textoDelMensaje, destinos) ?: return parsed
    // 4-oct: el nombre que escribió el BANCO para esa persona («a DANIEL LEONETT») también se
    // reemplaza — el dueño ya le dio uno —; el que puso la memoria (lo que él decidió) no.
    if (!sePuedeRenombrar(parsed.merchant) && claveDeLlave(parsed.merchant) !in nombresDelBancoEn(textoDelMensaje)) return parsed
    return parsed.copy(merchant = nombreHaciaElDestino(destino))
}

/**
 * **La plata que LLEGÓ de alguien guardado** (29-sep): «Te llegó dinero de CAROLINA RESTREPO
 * SALAZAR con tu llave» se propone como «Transferencia de Caro» si él guardó ese nombre en Caro.
 *
 * Solo reemplaza el nombre que mandó el banco (o uno ilegible): si la memoria ya le puso otro, ese
 * es suyo. Y no suma en «lo que le mandaste» — eso sigue siendo solo gastos ([vaHaciaElDestino]).
 */
private fun conElRemitenteConocido(
    parsed: ParsedSms,
    textoDelMensaje: String,
    destinos: List<DestinoConocido>,
): ParsedSms {
    // 4-oct: cualquier forma en que el banco nombre a quien mandó («Te llegó dinero de X con tu
    // llave», «recibiste una transferencia de X por …») y cualquiera de sus identificadores.
    val destino = destinoQueNombra(textoDelMensaje, destinos) ?: return parsed
    val remitentes = nombresDelBancoEn(textoDelMensaje)
    if (claveDeLlave(parsed.merchant) !in remitentes && !sePuedeRenombrar(parsed.merchant)) return parsed
    return parsed.copy(merchant = nombreDesdeElDestino(destino))
}

/**
 * Lo mismo, para una fila de extracto. Mismas reglas: solo gastos, solo si el nombre que trae es un
 * número o nada, y leyendo el número del texto CRUDO de la fila.
 *
 * El concepto se mueve junto con el nombre solo si venían iguales — que es el caso normal. Cuando el
 * extracto trajo un concepto propio, ese concepto es un dato del papel y no se pisa.
 */
fun conElDestinoConocido(
    tx: ParsedTransaction,
    destinos: List<DestinoConocido>,
): ParsedTransaction {
    if (tx.type != TransactionType.EXPENSE) return tx
    val destino = destinoQueNombra(tx.rawText.ifBlank { tx.merchant }, destinos) ?: return tx
    if (!sePuedeRenombrar(tx.merchant)) return tx
    val nuevo = nombreHaciaElDestino(destino)
    return tx.copy(
        merchant = nuevo,
        description = if (tx.description == tx.merchant) nuevo else tx.description,
    )
}

/**
 * **¿El texto de [evento] nombra el NÚMERO de cuenta de [destino]?** Mira el concepto, el nombre del
 * banco y el texto crudo del que salió ([FinancialEvent.rawPayload], que guardan el SMS confirmado y
 * la fila de extracto importada). Es la señal fuerte de [vaHaciaElDestino]: **sobrevive a que el
 * dueño le cambie el nombre al movimiento**, porque el texto del banco no se reescribe nunca.
 *
 * **Solo esto — no el nombre del destino como palabra suelta** (ver [vaHaciaElDestino] para esa
 * segunda señal, más floja). Existe separada porque Ola V la reusa donde un falso positivo no es
 * gratis: `OccurrenceMatching.kt` la usa para decidir SOLO si un movimiento es la ocurrencia de una
 * regla recurrente, y ahí «Almuerzo caro» no puede pegar con el destino «Caro» solo porque «caro» es
 * un adjetivo común en español — el número, en cambio, es un hecho del banco.
 *
 * **Solo gastos**, por lo mismo que [conElDestinoConocido]: lo que entró no es lo que se mandó.
 */
fun nombraElNumeroDelDestino(evento: FinancialEvent, destino: DestinoConocido): Boolean {
    if (evento.type != TransactionType.EXPENSE) return false
    return elTextoNombraUnNumero(evento, destino)
}

/** La señal del número sin mirar el tipo: la usan lo enviado y lo recibido. */
private fun elTextoNombraUnNumero(evento: FinancialEvent, destino: DestinoConocido): Boolean {
    val colas = destino.numeros().mapNotNull(::ultimosCuatro).toSet()
    if (colas.isEmpty()) return false
    val textos = listOfNotNull(evento.description, evento.merchant, evento.rawPayload)
    return textos.any { texto ->
        NUMEROS_QUE_NOMBRA_EL_TEXTO.findAll(texto)
            .any { it.groupValues[1].takeLast(MIN_DIGITOS_DEL_NUMERO) in colas }
    }
}

/** La señal de la llave (o del nombre que escribe el banco) sin mirar el tipo. */
private fun elTextoNombraUnaLlave(evento: FinancialEvent, destino: DestinoConocido): Boolean {
    val llaves = destino.llavesComparables()
    if (llaves.isEmpty()) return false
    return listOfNotNull(evento.description, evento.merchant, evento.rawPayload)
        .any { texto -> llavesQueNombra(texto).any { it in llaves } }
}

/**
 * **¿El texto de [evento] nombra la LLAVE de [destino]?** La hermana de [nombraElNumeroDelDestino],
 * con la misma fuerza: una llave es un hecho que escribió el banco. Se compara **exacta** (no por
 * los últimos cuatro, como el número): «llave 0092184713» y una cuenta que termina en 4713 no
 * tienen nada que ver, y por eso las dos señales no se mezclan — una llave nunca pasa por
 * [NUMEROS_QUE_NOMBRA_EL_TEXTO] (exige `*` o «cuenta») y un número nunca pasa por [llavesQueNombra].
 *
 * **Solo gastos**, como su hermana.
 */
fun nombraLaLlaveDelDestino(evento: FinancialEvent, destino: DestinoConocido): Boolean {
    if (evento.type != TransactionType.EXPENSE) return false
    return elTextoNombraUnaLlave(evento, destino)
}

/**
 * **La señal fuerte, entera**: el texto del banco nombra el número o la llave de [destino]. Es la
 * que usan el agrupador ([vaHaciaElDestino]) y el emparejador de recurrentes.
 */
fun nombraAlDestino(evento: FinancialEvent, destino: DestinoConocido): Boolean =
    nombraElNumeroDelDestino(evento, destino) || nombraLaLlaveDelDestino(evento, destino)

/**
 * **¿Este movimiento fue a este destino?** Usado para AGRUPAR «lo que le mandaste» en «Cuentas de
 * otros» (ver [movimientosHaciaElDestino]/[totalesHaciaElDestino]) — no para decidir sola la
 * ocurrencia de un recurrente, que es un problema con otro perfil de riesgo (ver
 * [nombraElNumeroDelDestino]).
 *
 * Dos señales, y cualquiera alcanza:
 *
 * 1. **El número o la llave** — [nombraAlDestino].
 * 2. **El nombre del destino, como palabra completa en el concepto o en el nombre.** Es lo que hace
 *    que «Cuota de Cotrafa 5413 · transferida a Caro» cuente, y lo que hace que un movimiento
 *    anotado a mano se pueda enganchar sin tocar código: se le escribe el nombre y ya. Esta señal es
 *    **a propósito más suelta** que la del número: acá un falso positivo solo infla un total que se
 *    ve en pantalla, nunca sella nada ni apaga un aviso.
 *
 * **Palabra completa y no subcadena**: sin eso «Caro» se llevaría «Carolina», «Carozo» y cualquier
 * palabra que la contenga. Y un nombre de menos de tres letras no participa de esta segunda señal —
 * «Jo» o «RH» adentro de un concepto no es evidencia de nada.
 *
 * **Solo gastos**, por lo mismo que [conElDestinoConocido]: lo que entró no es lo que se mandó.
 */
fun vaHaciaElDestino(evento: FinancialEvent, destino: DestinoConocido): Boolean {
    if (nombraAlDestino(evento, destino)) return true
    if (evento.type != TransactionType.EXPENSE) return false
    return elConceptoDiceElNombre(evento, destino)
}

/**
 * **¿Este movimiento vino de este tercero?** (4-oct-2026) El espejo de [vaHaciaElDestino] para la
 * plata que ENTRA: «Te llegó dinero de CAROLINA RESTREPO SALAZAR», «recibiste una transferencia de
 * JUAN GUILLERMO VILLADA ARANGO», o un ingreso cuyo concepto dice el nombre («Transferencia de
 * Caro»). Mismas dos señales, misma regla de palabra completa. Solo ingresos.
 */
fun vieneDelDestino(evento: FinancialEvent, destino: DestinoConocido): Boolean {
    if (evento.type != TransactionType.INCOME) return false
    if (elTextoNombraUnNumero(evento, destino) || elTextoNombraUnaLlave(evento, destino)) return true
    return elConceptoDiceElNombre(evento, destino)
}

private fun elConceptoDiceElNombre(evento: FinancialEvent, destino: DestinoConocido): Boolean {
    val nombre = enPalabras(destino.nombre)
    if (nombre.trim().length < 3) return false
    return listOfNotNull(evento.description, evento.merchant).any { enPalabras(it).contains(nombre) }
}

/**
 * El texto en palabras, con un espacio de centinela a cada punta: así `contains(" caro ")` es una
 * coincidencia de palabra completa y no de subcadena, sin necesidad de una regex por destino.
 *
 * Pasa por `normalizarParaBuscar` (minúsculas, sin tildes) y convierte todo lo que no sea letra ni
 * dígito en separador, para que «… transferida a Caro.» y «·Caro/» den la misma palabra.
 */
private fun enPalabras(texto: String): String =
    normalizarParaBuscar(texto)
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(separator = " ", prefix = " ", postfix = " ")

/** Los movimientos que fueron a [destino], del más reciente al más viejo. */
fun movimientosHaciaElDestino(
    destino: DestinoConocido,
    eventos: List<FinancialEvent>,
): List<FinancialEvent> =
    eventos.filter { vaHaciaElDestino(it, destino) }.sortedByDescending { it.timestamp }

/** Lo que te llegó de [destino], del más reciente al más viejo. Ver [vieneDelDestino]. */
fun movimientosDesdeElDestino(
    destino: DestinoConocido,
    eventos: List<FinancialEvent>,
): List<FinancialEvent> =
    eventos.filter { vieneDelDestino(it, destino) }.sortedByDescending { it.timestamp }

/**
 * **Cuánto se le mandó, por moneda.** Un mapa y no una suma única porque sumar pesos con dólares da
 * una cifra que no existe — el mismo criterio que `computeBalances`, que agrupa por moneda.
 */
fun totalesHaciaElDestino(movimientos: List<FinancialEvent>): Map<String, Long> =
    movimientos.groupBy { it.currency }.mapValues { (_, del) -> del.sumOf { it.amount } }

/**
 * [destino] con sus derivados llenos: [DestinoConocido.totales], [DestinoConocido.cuantos] y —si se
 * pasan [ajustes] y [ahora]— [DestinoConocido.totalesDelPeriodo], lo del período en curso del dueño.
 */
fun conLoQueSeLeMando(
    destino: DestinoConocido,
    eventos: List<FinancialEvent>,
    ajustes: PeriodSettings? = null,
    ahora: Long? = null,
): DestinoConocido {
    val suyos = movimientosHaciaElDestino(destino, eventos)
    val deEl = movimientosDesdeElDestino(destino, eventos)
    fun delPeriodo(lista: List<FinancialEvent>): Map<String, Long> = if (ajustes != null && ahora != null) {
        val enCurso = periodoActual(ahora, ajustes)
        totalesHaciaElDestino(lista.filter { periodoDe(it.timestamp, ajustes) == enCurso })
    } else {
        emptyMap()
    }
    return destino.copy(
        totales = totalesHaciaElDestino(suyos),
        cuantos = suyos.size,
        totalesDelPeriodo = delPeriodo(suyos),
        // `suyos` ya viene del más reciente al más viejo (ver [movimientosHaciaElDestino]).
        ultimo = suyos.firstOrNull()?.let { UltimoEnvio(it.description, it.amount, it.currency, it.timestamp) },
        recibidos = totalesHaciaElDestino(deEl),
        cuantosRecibidos = deEl.size,
        recibidosDelPeriodo = delPeriodo(deEl),
        ultimoRecibido = deEl.firstOrNull()?.let { UltimoEnvio(it.description, it.amount, it.currency, it.timestamp) },
    )
}

/**
 * **Lo que se les mandó a todas en el período en curso**, por moneda: la cifra de la tarjeta
 * «Cuentas de otros» de Patrimonio. Suma los [DestinoConocido.totalesDelPeriodo] ya derivados por
 * el server, sin recalcular nada. Sin `0`: una moneda en cero no es algo que se mandó.
 */
fun loQueSeLesMandoEstePeriodo(destinos: List<DestinoConocido>): Map<String, Long> =
    destinos.flatMap { it.totalesDelPeriodo.entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, montos) -> montos.sum() }
        .filterValues { it != 0L }

/** Cuántos nombres dice la tarjeta de Patrimonio antes de resumir el resto en «y N más». */
const val NOMBRES_EN_LA_TARJETA_DE_CUENTAS_DE_OTROS: Int = 3

/**
 * **Los nombres, como los diría él**: «Caro, Mamá y Papá», «Caro, Mamá, Papá y 2 más». Los que
 * más recibieron este período van primero —son a quienes se refiere la cifra de al lado—, y
 * después el orden alfabético que ya trae la lista.
 */
fun nombresDeLasCuentasDeOtros(destinos: List<DestinoConocido>): String {
    if (destinos.isEmpty()) return ""
    val ordenados = destinos.sortedByDescending { d -> d.totalesDelPeriodo.values.sum() }
    val nombres = ordenados.map { it.nombre }
    return when {
        nombres.size == 1 -> nombres.single()
        nombres.size <= NOMBRES_EN_LA_TARJETA_DE_CUENTAS_DE_OTROS ->
            nombres.dropLast(1).joinToString(", ") + " y " + nombres.last()
        else -> nombres.take(NOMBRES_EN_LA_TARJETA_DE_CUENTAS_DE_OTROS).joinToString(", ") +
            " y ${nombres.size - NOMBRES_EN_LA_TARJETA_DE_CUENTAS_DE_OTROS} más"
    }
}

/** Lo que se le mandó a un destino en un período del dueño. */
data class LoDeUnPeriodo(
    val periodo: PeriodoFinanciero,
    val totales: Map<String, Long>,
    val cuantos: Int,
)

/**
 * **Lo que se le mandó, período por período** — del más reciente al más viejo.
 *
 * Por el período DEL DUEÑO ([PeriodSettings]) y no por mes de calendario: su corte es 25, así que
 * una transferencia del 26 de agosto pertenece a «septiembre», igual que su salario. Si esta
 * pantalla agrupara por calendario diría «agosto» donde el resto de la app dice «septiembre», que es
 * la clase de contradicción que `PeriodoFinanciero` vino a cerrar.
 */
fun loQueSeLeMandoPorPeriodo(
    movimientos: List<FinancialEvent>,
    settings: PeriodSettings,
): List<LoDeUnPeriodo> =
    movimientos
        .groupBy { periodoDe(it.timestamp, settings) }
        .map { (periodo, del) -> LoDeUnPeriodo(periodo, totalesHaciaElDestino(del), del.size) }
        .sortedWith(compareByDescending<LoDeUnPeriodo> { it.periodo.year }.thenByDescending { it.periodo.month })

// ── Llaves y lo que el banco dice de a quién fue la plata (29-sep) ─────────────────────

/**
 * **La llave, como se guarda y como se compara.** Una llave es lo que Nu y Bancolombia (Bre-B)
 * usan en vez del número de cuenta: `@usuario`, un celular, un correo, o un código numérico
 * («llave 0092184713»). Se guarda **tal cual venga, en minúsculas y sin espacios** — un celular
 * pegado como «300 123 4567» y el mismo escrito junto tienen que ser la misma llave, y un correo no
 * distingue mayúsculas.
 *
 * **La única excepción es un nombre de persona**, que es lo que Nu escribe cuando la plata llega
 * («Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave»): ahí no hay llave ni número, el
 * nombre ES lo que identifica a quien mandó. Se guarda con un espacio entre palabras («carolina
 * restrepo salazar») para que se pueda leer en la ficha; como ninguna llave real tiene espacios
 * entre palabras de solo letras, esto no cambia nada para las demás.
 */
fun normalizarLlave(crudo: String): String {
    val palabras = crudo.trim().lowercase().split(ESPACIOS).filter { it.isNotEmpty() }
    return palabras.joinToString(if (esNombreDePersona(palabras)) " " else "")
}

private fun esNombreDePersona(palabras: List<String>): Boolean =
    palabras.size > 1 && palabras.all { palabra -> palabra.all { it.isLetter() } }

private val ESPACIOS = Regex("""\s+""")

/** La llave de [this] normalizada, o `null` si no tiene. */
fun DestinoConocido.llaveNormalizada(): String? = llave?.let(::normalizarLlave)?.takeIf { it.isNotEmpty() }

/**
 * **La forma de comparar una llave**: [normalizarLlave] y, si es un nombre de persona, además sin
 * tildes — el banco escribe «JOSE» donde el dueño escribió «José», y los dos son la misma persona.
 * Una llave de verdad (`@caro`, un celular) no tiene tildes que quitar y queda igual.
 */
fun claveDeLlave(crudo: String): String {
    val llave = normalizarLlave(crudo)
    return if (' ' in llave) normalizarParaBuscar(llave) else llave
}

/** El identificador en su forma guardada: solo dígitos un número, [normalizarLlave] una llave. */
fun IdentificadorDelDestino.normalizado(): IdentificadorDelDestino = when (tipo) {
    TipoDeIdentificador.NUMERO -> copy(valor = soloLosDigitos(valor))
    TipoDeIdentificador.LLAVE -> copy(valor = normalizarLlave(valor))
}

/**
 * **La clave con que se recuerda un identificador**: `NUMERO:31973270756`, `LLAVE:@caro`,
 * `LLAVE:carolina restrepo salazar`. Es lo que se guarda al decir «Ignorar» o «Es mía» sobre un
 * sugerido, y lo que distingue dos identificadores iguales escritos distinto.
 */
val IdentificadorDelDestino.clave: String
    get() = when (tipo) {
        TipoDeIdentificador.NUMERO -> "NUMERO:" + soloLosDigitos(valor)
        TipoDeIdentificador.LLAVE -> "LLAVE:" + claveDeLlave(valor)
    }

/**
 * **Todas las formas de reconocer a este tercero** (4-oct-2026): la lista [DestinoConocido.identificadores]
 * más el [DestinoConocido.numero] y la [DestinoConocido.llave] de siempre — que un server viejo, o
 * un destino creado por el APK instalado, traen solos. Sin repetidos y en su forma guardada.
 *
 * **Toda** pregunta de «¿este texto es de este tercero?» pasa por acá: los números por
 * [numeros], las llaves y los nombres por [llavesComparables].
 */
fun DestinoConocido.todosLosIdentificadores(): List<IdentificadorDelDestino> {
    val deSiempre = listOfNotNull(
        soloLosDigitos(numero).takeIf { it.isNotEmpty() }?.let { IdentificadorDelDestino(TipoDeIdentificador.NUMERO, it) },
        llaveNormalizada()?.let { IdentificadorDelDestino(TipoDeIdentificador.LLAVE, it) },
    )
    return (identificadores.map { it.normalizado() } + deSiempre)
        .filter { it.valor.isNotEmpty() }
        .distinctBy { it.clave }
}

/** Los números de cuenta de este tercero, solo dígitos. */
fun DestinoConocido.numeros(): List<String> =
    todosLosIdentificadores().filter { it.tipo == TipoDeIdentificador.NUMERO }.map { it.valor }

/** Las llaves (y los nombres con que lo nombra el banco) de este tercero, listas para comparar. */
fun DestinoConocido.llavesComparables(): Set<String> =
    todosLosIdentificadores().filter { it.tipo == TipoDeIdentificador.LLAVE }.map { claveDeLlave(it.valor) }.toSet()

/**
 * **El tercero con [lista] como sus identificadores**, y con [DestinoConocido.numero] y
 * [DestinoConocido.llave] puestos en el primero de cada clase — lo que el APK instalado sigue
 * leyendo. Lo usan el server al guardar y la app al sumar o quitar un identificador.
 */
fun DestinoConocido.conIdentificadores(lista: List<IdentificadorDelDestino>): DestinoConocido {
    val limpia = lista.map { it.normalizado() }.filter { it.valor.isNotEmpty() }.distinctBy { it.clave }
    return copy(
        identificadores = limpia,
        numero = limpia.firstOrNull { it.tipo == TipoDeIdentificador.NUMERO }?.valor.orEmpty(),
        llave = limpia.firstOrNull { it.tipo == TipoDeIdentificador.LLAVE }?.valor,
    )
}

/** ¿[a] y [b] son el mismo identificador? Un número, por los últimos cuatro; una llave, exacta. */
fun mismoIdentificador(a: IdentificadorDelDestino, b: IdentificadorDelDestino): Boolean =
    a.tipo == b.tipo && when (a.tipo) {
        TipoDeIdentificador.NUMERO -> ultimosCuatro(a.valor)?.let { it == ultimosCuatro(b.valor) } ?: false
        TipoDeIdentificador.LLAVE -> claveDeLlave(a.valor) == claveDeLlave(b.valor)
    }

/**
 * **Cómo se lee la llave guardada**: «llave @caro», «llave 3001234567», o —si lo guardado es el
 * nombre con que Nu nombra a quien manda plata— «Carolina Restrepo Salazar», que no es una llave y
 * no se presenta como tal. `null` si no tiene.
 */
fun DestinoConocido.llaveComoSeLee(): String? = llaveNormalizada()?.let(::comoSeLeeLaLlave)

private fun comoSeLeeLaLlave(llave: String): String =
    if (laLlaveEsUnNombre(llave)) enTituloCaso(llave) else "llave $llave"

/** Un identificador como lo dice la ficha: «·0756», «llave @caro», «Carolina Restrepo Salazar». */
fun comoSeLeeEnLaFicha(identificador: IdentificadorDelDestino): String = when (identificador.tipo) {
    TipoDeIdentificador.NUMERO -> "·" + soloLosDigitos(identificador.valor).takeLast(MIN_DIGITOS_DEL_NUMERO)
    TipoDeIdentificador.LLAVE -> comoSeLeeLaLlave(normalizarLlave(identificador.valor))
}

/**
 * **Los identificadores de un destino, como los dice la ficha**: «·0756», «llave @caro», «Carolina
 * Restrepo Salazar» — todos ([todosLosIdentificadores]). Del número va solo la cola, que es lo que
 * escribe el banco.
 */
fun identificadoresDelDestino(destino: DestinoConocido): List<String> =
    destino.todosLosIdentificadores().map(::comoSeLeeEnLaFicha)

/** ¿La llave guardada es el nombre de una persona (lo que Nu escribe al recibir) y no una llave? */
fun laLlaveEsUnNombre(llave: String): Boolean = ' ' in normalizarLlave(llave)

/**
 * «CAROLINA RESTREPO SALAZAR» → «Carolina Restrepo Salazar». Lo que el banco manda en mayúsculas,
 * como lo escribiría él. Las partículas («de», «del», «la», «y») van en minúscula salvo al empezar.
 */
fun enTituloCaso(texto: String): String =
    texto.trim().split(ESPACIOS).filter { it.isNotEmpty() }.mapIndexed { i, palabra ->
        val min = palabra.lowercase()
        if (i > 0 && min in PARTICULAS) min else min.replaceFirstChar { it.uppercaseChar() }
    }.joinToString(" ")

private val PARTICULAS = setOf("de", "del", "la", "las", "los", "y", "e")

/**
 * Qué clase de dato identifica al destino en un texto del banco. Un nombre de persona («CAROLINA
 * RESTREPO SALAZAR») es una [LLAVE] con espacios — ver [laLlaveEsUnNombre]: así viaja desde el
 * 29-sep en `ParsedSms.identificadorEsLlave`, y un tercer valor rompería al APK instalado.
 */
@Serializable
enum class TipoDeIdentificador { NUMERO, LLAVE }

/**
 * **Lo que un texto del banco dice sobre a quién fue (o de quién vino) la plata.** [valor] ya viene
 * normalizado: solo dígitos para un número, [normalizarLlave] para una llave o un nombre.
 *
 * Desde el 4-oct también es **lo que se guarda** en [DestinoConocido.identificadores].
 */
@Serializable
data class IdentificadorDelDestino(val tipo: TipoDeIdentificador, val valor: String) {
    /** Cómo se lo nombra en una pregunta: «la cuenta ·0756», «la llave 0087», «Carolina Restrepo». */
    val comoSeDice: String
        get() = when {
            tipo == TipoDeIdentificador.NUMERO -> "la cuenta ·${valor.takeLast(MIN_DIGITOS_DEL_NUMERO)}"
            laLlaveEsUnNombre(valor) -> enTituloCaso(valor)
            else -> "la llave $valor"
        }
}

/**
 * La cuenta **de destino** — detrás de «a» o «hacia», nunca la de «desde tu cuenta», que es de
 * dónde salió. La misma forma que `cuentaDestinoRegex` del server, más «hacia la cuenta», que
 * también escribe Bancolombia.
 */
private val CUENTA_DE_DESTINO =
    Regex("""\b(?:a|hacia)\s+(?:la\s+)?cuenta\s+\*?\s?(\d{4,})""", RegexOption.IGNORE_CASE)

/**
 * «a la llave 0087», «Pago QR · llave 0092184713», «llave @juan», «llave caro@correo.com». **No**
 * «con tu llave» ni «conectada a la llave @…» (4-oct): las dos son la llave DEL DUEÑO, la que
 * recibió la plata — Bancolombia escribe «recibiste una transferencia de X … en tu cuenta *8133
 * conectada a la llave @SUYA». Y la llave tiene que tener un dígito o una arroba: una palabra suelta
 * después de «llave» («la llave registrada») no identifica a nadie.
 */
private val LLAVE_DEL_TEXTO =
    Regex(
        """(?<!\btu\s)(?<!conectada\sa\sla\s)\bllave\s+(@?[A-Za-z0-9._+\-]+(?:@[A-Za-z0-9.\-]+)?)""",
        RegexOption.IGNORE_CASE,
    )

/** «Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave» (Nu): el nombre de quien mandó. */
private val REMITENTE_CON_TU_LLAVE =
    Regex("""te\s+lleg[oó]\s+dinero\s+de\s+(.+?)\s+con\s+tu\s+llave""", RegexOption.IGNORE_CASE)

/** «JUAN, recibiste una transferencia de JUAN GUILLERMO VILLADA ARANGO por $…» (Bancolombia). */
private val REMITENTE_DE_UNA_TRANSFERENCIA =
    Regex("""recibiste\s+una\s+transferencia\s+de\s+(.+?)\s+por\s""", RegexOption.IGNORE_CASE)

/** «Recibiste $500.000 de CARO RESTREPO en tu cuenta *8133 …» — la forma del comprobante leído. */
private val REMITENTE_DEL_COMPROBANTE =
    Regex(
        """recibiste\s+(?:USD\s*)?\$?\s?[0-9][0-9.,]*\s+de\s+(.+?)(?=\s+en\s+tu\s+cuenta|\s+el\s+\d|\.\s|\.$|$)""",
        RegexOption.IGNORE_CASE,
    )

/** «… a la llave @juan desde tu cuenta *8133 a DANIEL LEONETT el 31/07/26 …» (Bre-B). */
private val DESTINATARIO_DE_BRE_B =
    Regex("""desde\s+tu\s+cuenta\s+\*\s?\d{4,}\s+a\s+(.+?)\s+el\s+\d""", RegexOption.IGNORE_CASE)

/**
 * Palabras que delatan una empresa: un nombre con alguna de estas no es «una persona que te mandó
 * plata» (el salario, una devolución) y no se ofrece guardarlo como si lo fuera.
 */
private val PALABRAS_DE_EMPRESA = setOf("sas", "ltda", "sa", "s", "banco", "bancolombia", "fiduciaria", "sociedad", "inc", "corp")

/**
 * El nombre, si parece el de una persona: de dos a seis palabras, solo letras, y ninguna de
 * empresa. «la llave 0087» o «la cuenta *0756» no son un nombre (tienen dígitos).
 */
private fun nombreDePersonaO(crudo: String): String? {
    val palabras = crudo.trim().split(ESPACIOS).filter { it.isNotEmpty() }
    if (palabras.size !in 2..6) return null
    if (!palabras.all { p -> p.all { it.isLetter() } }) return null
    if (palabras.any { normalizarParaBuscar(it) in PALABRAS_DE_EMPRESA }) return null
    return palabras.joinToString(" ")
}

/**
 * **Los nombres con que el banco nombra a la otra persona**, ya listos para comparar ([claveDeLlave]):
 * a quién le mandaste por Bre-B («… a DANIEL LEONETT el …») y de quién te llegó («Te llegó dinero
 * de …», «recibiste una transferencia de …»). Solo nombres de persona ([nombreDePersonaO]).
 */
fun nombresDelBancoEn(texto: String): List<String> = nombresCrudosDelBancoEn(texto).map(::claveDeLlave)

/** Lo mismo, como lo escribió el banco («DANIEL LEONETT»): para proponer el nombre en Título Caso. */
fun nombresCrudosDelBancoEn(texto: String): List<String> =
    listOf(REMITENTE_CON_TU_LLAVE, REMITENTE_DE_UNA_TRANSFERENCIA, REMITENTE_DEL_COMPROBANTE, DESTINATARIO_DE_BRE_B)
        .flatMap { regex -> regex.findAll(texto).map { it.groupValues[1] } }
        .mapNotNull(::nombreDePersonaO)
        .distinctBy(::claveDeLlave)

private fun llavesDelTexto(texto: String): List<String> =
    LLAVE_DEL_TEXTO.findAll(texto)
        .map { it.groupValues[1].trimEnd('.', ',', '-', ';', ':') }
        .filter { it.any { c -> c.isDigit() || c == '@' } }
        .map(::normalizarLlave)
        .filter { it.length >= MIN_LARGO_DE_LA_LLAVE }
        .toList()

/** Las llaves (y los nombres de persona que escribe el banco) que nombra un texto, listas para comparar. */
private fun llavesQueNombra(texto: String): List<String> =
    llavesDelTexto(texto).map(::claveDeLlave) + nombresDelBancoEn(texto)

/**
 * **A quién fue (o de quién vino) la plata, según el texto del banco**, o `null` si no lo dice.
 *
 * Tres formas, en este orden: la cuenta de destino («a la cuenta *31973270756»), una llave («a la
 * llave 0087»), o el nombre de la persona («Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu
 * llave», «recibiste una transferencia de JUAN …», «… a DANIEL LEONETT el …»). Es lo que el server
 * pone en [ParsedSms.identificadorDelDestino] y lo que la bandeja y el detalle de un movimiento leen
 * para ofrecer «¿De quién es esta cuenta?».
 */
fun identificadorDelDestinoEn(texto: String): IdentificadorDelDestino? {
    CUENTA_DE_DESTINO.find(texto)?.let { return IdentificadorDelDestino(TipoDeIdentificador.NUMERO, it.groupValues[1]) }
    llavesDelTexto(texto).firstOrNull()?.let { return IdentificadorDelDestino(TipoDeIdentificador.LLAVE, it) }
    nombresCrudosDelBancoEn(texto).firstOrNull()?.let { nombre ->
        val llave = normalizarLlave(nombre)
        if (llave.length >= MIN_LARGO_DE_LA_LLAVE) return IdentificadorDelDestino(TipoDeIdentificador.LLAVE, llave)
    }
    return null
}

/**
 * Lo mismo para un movimiento ya anotado: primero el texto crudo del banco ([FinancialEvent.rawPayload],
 * que no se reescribe nunca), después el nombre del banco y el concepto.
 */
fun identificadorDelDestinoEn(evento: FinancialEvent): IdentificadorDelDestino? =
    listOfNotNull(evento.rawPayload, evento.merchant, evento.description)
        .firstNotNullOfOrNull { identificadorDelDestinoEn(it) }

/** El identificador que el server leyó del SMS ([ParsedSms.identificadorDelDestino]), o `null`. */
fun ParsedSms.identificador(): IdentificadorDelDestino? =
    identificadorDelDestino?.takeIf { it.isNotBlank() }?.let {
        IdentificadorDelDestino(if (identificadorEsLlave) TipoDeIdentificador.LLAVE else TipoDeIdentificador.NUMERO, it)
    }

/**
 * **¿[destino] se reconoce por [identificador]?** Por **cualquiera** de sus identificadores
 * ([todosLosIdentificadores]): un número, por los últimos cuatro (la regla de siempre, ver
 * [ultimosCuatro]); una llave o un nombre, exacta ([claveDeLlave]).
 */
fun elDestinoConoce(destino: DestinoConocido, identificador: IdentificadorDelDestino): Boolean =
    destino.todosLosIdentificadores().any { mismoIdentificador(it, identificador) }

/** El tercero guardado que se reconoce por [identificador], si es uno solo. */
fun destinoConEseIdentificador(
    identificador: IdentificadorDelDestino,
    destinos: List<DestinoConocido>,
): DestinoConocido? = destinos.filter { elDestinoConoce(it, identificador) }.singleOrNull()

/**
 * **¿Hay que ofrecer «¿De quién es esta cuenta?»?** Sí cuando hay un identificador, ningún destino
 * guardado lo conoce, el dueño no dijo antes que es suyo o que lo ignore ([descartados], claves de
 * [clave], más `COLA:NNNN` para las colas que el banco dice que son suyas) y —si es un número— no es el de una cuenta suya (un traspaso entre sus cuentas nombra la
 * de destino igual que una transferencia a Caro).
 */
fun ofreceGuardarElDestino(
    identificador: IdentificadorDelDestino?,
    destinos: List<DestinoConocido>,
    cuentas: List<Account>,
    descartados: Set<String> = emptySet(),
): Boolean {
    if (identificador == null) return false
    if (destinos.any { elDestinoConoce(it, identificador) }) return false
    if (identificador.clave in descartados) return false
    if (identificador.tipo == TipoDeIdentificador.NUMERO) {
        if (cuentaPropiaConEseNumero(identificador.valor, cuentas) != null) return false
        // «desde tu cuenta *8133»: el banco dijo que esa cola es suya (ver `colasPropiasEn`).
        if ("COLA:" + ultimosCuatro(identificador.valor) in descartados) return false
    }
    return true
}

/**
 * **El nombre con que se ofrece guardarlo**: el que mandó el banco, en Título Caso («Carolina
 * Restrepo Salazar», no en mayúsculas), o vacío si lo que hay es un número o nada («Transferencia a
 * la cuenta *0756» no es el nombre de nadie).
 */
fun nombreSugeridoParaElDestino(nombreDelBanco: String?): String {
    val nombre = nombreDelBanco?.trim().orEmpty()
    if (nombre.isEmpty() || sePuedeRenombrar(nombre)) return ""
    return enTituloCaso(nombre).take(MAX_NOMBRE_DEL_DESTINO)
}

/**
 * **Qué se guarda al tocar «Guardar»** desde donde apareció el identificador: el destino que ya se
 * llama así (sin distinguir mayúsculas ni tildes), con el dato agregado a sus identificadores —
 * «Caro» ya tenía la cuenta y ahora suma la llave —, o uno nuevo.
 *
 * Desde el 4-oct un tercero tiene tantos identificadores como haga falta, así que el mismo nombre
 * **siempre** suma (antes solo si le faltaba ese tipo de dato).
 */
fun destinoParaGuardar(
    identificador: IdentificadorDelDestino,
    nombre: String,
    deQuien: String?,
    destinos: List<DestinoConocido>,
    tipo: TipoDeTercero? = null,
): DestinoConocido {
    val limpio = nombre.trim()
    val quien = deQuien?.trim()?.ifBlank { null }
    val mismoNombre = destinos.firstOrNull { normalizarParaBuscar(it.nombre.trim()) == normalizarParaBuscar(limpio) }
    if (mismoNombre != null) return sumarIdentificador(mismoNombre, identificador).copy(deQuien = mismoNombre.deQuien ?: quien)
    val nuevo = DestinoConocido(nombre = limpio, numero = "", deQuien = quien, tipo = tipo)
    return nuevo.conIdentificadores(listOf(identificador))
}

/** [destino] con [identificador] sumado a los que ya tenía (sin repetir). */
fun sumarIdentificador(destino: DestinoConocido, identificador: IdentificadorDelDestino): DestinoConocido =
    destino.conIdentificadores(destino.todosLosIdentificadores() + identificador)

/** [destino] sin [identificador]. */
fun quitarIdentificador(destino: DestinoConocido, identificador: IdentificadorDelDestino): DestinoConocido =
    destino.conIdentificadores(destino.todosLosIdentificadores().filterNot { it.clave == identificador.normalizado().clave })

/** El nombre que se propone para el movimiento una vez guardado el destino. */
fun nombreDelMovimientoConElDestino(destino: DestinoConocido, tipo: TransactionType): String =
    if (tipo == TransactionType.INCOME) nombreDesdeElDestino(destino) else nombreHaciaElDestino(destino)
