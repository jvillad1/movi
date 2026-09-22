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
 * Un nombre, el número y opcionalmente de quién es. Nada de saldo, de cupo ni de moneda: cada campo
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
    /** El número de la cuenta como lo escribió, solo dígitos (ver [soloLosDigitos]). */
    val numero: String,
    /** «esposa», «papá». Opcional: el nombre suele alcanzar, y obligar a llenarlo no agrega nada. */
    val deQuien: String? = null,
    /**
     * **Cuánto se le mandó, por moneda** — derivado. Un mapa y no un `Long` porque sumar pesos con
     * dólares da un número que no existe, y este destino puede recibir los dos (él tiene cuentas en
     * las dos monedas). Casi siempre va a traer una sola entrada, `COP`.
     */
    val totales: Map<String, Long> = emptyMap(),
    /** Cuántos movimientos se le contaron — derivado. */
    val cuantos: Int = 0,
)

/** **Los movimientos que fueron a un destino, con su total.** Lo que contesta `GET /api/destinos/{id}/movimientos`. */
@Serializable
data class MovimientosDelDestino(
    val destino: DestinoConocido,
    val movimientos: List<FinancialEvent>,
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
fun rechazoDelDestino(nombre: String, numero: String, deQuien: String? = null): String? {
    val limpio = nombre.trim()
    if (limpio.isEmpty()) return DESTINO_SIN_NOMBRE
    if (limpio.length > MAX_NOMBRE_DEL_DESTINO) return NOMBRE_DEL_DESTINO_DEMASIADO_LARGO
    if ((deQuien?.trim()?.length ?: 0) > MAX_DE_QUIEN) return DE_QUIEN_DEMASIADO_LARGO
    val digitos = soloLosDigitos(numero)
    if (digitos.length < MIN_DIGITOS_DEL_NUMERO) return NUMERO_DEMASIADO_CORTO
    if (digitos.length > MAX_DIGITOS_DEL_NUMERO) return NUMERO_DEMASIADO_LARGO
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
        val coinciden = destinos.filter { ultimosCuatro(it.numero) == cola }
        if (coinciden.size == 1) return coinciden.single()
    }
    return null
}

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
private fun sePuedeRenombrar(nombreActual: String): Boolean {
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
 * Solo gastos: un destino contesta «¿qué le mandé?», y un ingreso no es algo que se mandó.
 */
fun conElDestinoConocido(
    parsed: ParsedSms,
    textoDelMensaje: String,
    destinos: List<DestinoConocido>,
): ParsedSms {
    if (parsed.type != TransactionType.EXPENSE) return parsed
    val destino = destinoQueNombra(textoDelMensaje, destinos) ?: return parsed
    if (!sePuedeRenombrar(parsed.merchant)) return parsed
    return parsed.copy(merchant = nombreHaciaElDestino(destino))
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
 * **¿Este movimiento fue a este destino?**
 *
 * Dos señales, y cualquiera alcanza:
 *
 * 1. **El número, en cualquiera de los textos del movimiento** — el concepto, el nombre del banco o
 *    el texto crudo del que salió ([FinancialEvent.rawPayload], que guardan el SMS confirmado y la
 *    fila de extracto importada). Esta es la señal fuerte: **sobrevive a que el dueño le cambie el
 *    nombre al movimiento**, que es justo lo que hizo con los tres que ya tiene («Mercado»,
 *    «Colegio Hija», «Cuota de Cotrafa»). El texto del banco no se reescribe nunca.
 * 2. **El nombre del destino, como palabra completa en el concepto o en el nombre.** Es lo que hace
 *    que «Cuota de Cotrafa 5413 · transferida a Caro» cuente, y lo que hace que un movimiento
 *    anotado a mano se pueda enganchar sin tocar código: se le escribe el nombre y ya.
 *
 * **Palabra completa y no subcadena**: sin eso «Caro» se llevaría «Carolina», «Carozo» y cualquier
 * palabra que la contenga. Y un nombre de menos de tres letras no participa de esta segunda señal —
 * «Jo» o «RH» adentro de un concepto no es evidencia de nada.
 *
 * **Solo gastos**, por lo mismo que [conElDestinoConocido]: lo que entró no es lo que se mandó.
 */
fun vaHaciaElDestino(evento: FinancialEvent, destino: DestinoConocido): Boolean {
    if (evento.type != TransactionType.EXPENSE) return false
    val cola = ultimosCuatro(destino.numero) ?: return false
    val textos = listOfNotNull(evento.description, evento.merchant, evento.rawPayload)
    val porElNumero = textos.any { texto ->
        NUMEROS_QUE_NOMBRA_EL_TEXTO.findAll(texto)
            .any { it.groupValues[1].takeLast(MIN_DIGITOS_DEL_NUMERO) == cola }
    }
    if (porElNumero) return true
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

/**
 * **Cuánto se le mandó, por moneda.** Un mapa y no una suma única porque sumar pesos con dólares da
 * una cifra que no existe — el mismo criterio que `computeBalances`, que agrupa por moneda.
 */
fun totalesHaciaElDestino(movimientos: List<FinancialEvent>): Map<String, Long> =
    movimientos.groupBy { it.currency }.mapValues { (_, del) -> del.sumOf { it.amount } }

/** [destino] con [DestinoConocido.totales] y [DestinoConocido.cuantos] llenos. */
fun conLoQueSeLeMando(destino: DestinoConocido, eventos: List<FinancialEvent>): DestinoConocido {
    val suyos = movimientosHaciaElDestino(destino, eventos)
    return destino.copy(totales = totalesHaciaElDestino(suyos), cuantos = suyos.size)
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
