package com.jvillada.movi.shared.model

/**
 * # «Personas y comercios», perfecto (4-oct-2026)
 *
 * El dueño: *«Hagamos que lo de cuentas funcione de forma perfecta. Pensá en todo lo necesario e
 * indispensable, pero también en ser ágil y no ofrecer campos basura. Además, que tenga un acceso
 * muy fácil de encontrar para ver cuentas de terceros.»*
 *
 * Este archivo junta las reglas puras que esa ronda agregó sobre [DestinoConocido]:
 *
 * - **Un solo nombre**: [PERSONAS_Y_COMERCIOS]. Hasta acá la misma cosa se llamaba «Cuentas de
 *   otros», «terceros» y «destinos», y para un parqueadero «la cuenta de otro» no se entiende.
 * - **Persona o comercio sin preguntar** ([tipoInferido]): el banco ya lo dice — un pago por código
 *   QR es a un comercio; «Transferiste a la cuenta …», un Bre-B «a DANIEL …» o «Te llegó dinero de …»
 *   son personas. El dueño lo puede cambiar; nunca se le pregunta.
 * - **Un solo campo «número o llave»** ([identificadorEscrito]): Movi clasifica lo que se escribe, y
 *   el dueño lo da vuelta con un toque si se equivocó.
 * - **Unir dos fichas** ([unirTerceros]): cuando la misma persona quedó guardada dos veces.
 * - **Lo enviado y lo recibido, período por período** ([loDeCadaPeriodo]).
 */
const val PERSONAS_Y_COMERCIOS: String = "Personas y comercios"

// ── Persona o comercio, deducido ─────────────────────────────────────────────────

/**
 * Un pago por código QR. Es lo que Bancolombia escribe cuando se paga en un comercio («pagaste
 * $13.500 por codigo QR … a la llave 3127…»), aunque la llave sea un celular.
 */
private val PAGO_A_UN_COMERCIO = Regex("""\b(?:c[oó]digo\s+qr|pago\s+qr|qr)\b""", RegexOption.IGNORE_CASE)

/**
 * La forma de las llaves de QR de Bancolombia: diez dígitos que empiezan en `00` («0088044519»). Un
 * número de cuenta que empieza en `00` tiene once («00818970404»), así que no se confunden.
 */
fun esLlaveDeQr(identificador: IdentificadorDelDestino): Boolean =
    identificador.tipo == TipoDeIdentificador.LLAVE && esFormaDeQr(identificador.valor)

private fun esFormaDeQr(valor: String): Boolean =
    valor.length == 10 && valor.startsWith("00") && valor.all { it.isDigit() }

/**
 * **¿Persona o comercio?, según lo que dicen los avisos del banco que lo nombran.** [textosDelBanco]
 * son los textos crudos (avisos y el `rawPayload` de los movimientos); se cuentan solo los que
 * nombran a [destino] por alguno de sus números o llaves.
 *
 * - Más pagos por QR que transferencias → **comercio** (la cancha, el parqueadero).
 * - Sin ningún aviso que lo nombre, una llave con forma de QR ([esLlaveDeQr]) → **comercio**.
 * - Todo lo demás → **persona**, que es lo que se asumía hasta hoy.
 */
fun tipoInferido(destino: DestinoConocido, textosDelBanco: List<String>): TipoDeTercero {
    var porQr = 0
    var otros = 0
    val solo = listOf(destino)
    textosDelBanco.forEach { texto ->
        if (destinoQueNombra(texto, solo) != null) {
            if (PAGO_A_UN_COMERCIO.containsMatchIn(texto)) porQr++ else otros++
        }
    }
    return when {
        porQr > otros -> TipoDeTercero.COMERCIO
        porQr == 0 && otros == 0 && destino.todosLosIdentificadores().any(::esLlaveDeQr) -> TipoDeTercero.COMERCIO
        else -> TipoDeTercero.PERSONA
    }
}

/**
 * [destino] con su tipo: el que eligió el dueño si lo eligió, o el deducido ([tipoInferido]) con
 * [DestinoConocido.tipoInferido] en `true`. **No se escribe en la base**: es una lectura.
 */
fun conTipoInferido(destino: DestinoConocido, textosDelBanco: List<String>): DestinoConocido =
    if (destino.tipo != null) {
        destino.copy(tipoInferido = false)
    } else {
        destino.copy(tipo = tipoInferido(destino, textosDelBanco), tipoInferido = true)
    }

/**
 * El tipo que se manda al guardar: `null` si lo dedujo Movi y el dueño no lo tocó (así sigue siendo
 * una deducción), o el elegido.
 */
fun tipoParaGuardar(destino: DestinoConocido, elegido: TipoDeTercero): TipoDeTercero? =
    if (destino.tipoInferido && elegido == destino.tipo) null else elegido

// ── Un solo campo: número o llave ────────────────────────────────────────────────

/**
 * **Lo que el dueño escribió en «Número o llave», clasificado.** `null` si no hay nada.
 *
 * - Con una arroba o una letra → **llave** (`@caro`, un correo, el nombre con que lo nombra el banco).
 * - Solo dígitos (y los `*`, espacios, guiones o puntos con que el banco los escribe):
 *   - con `*` delante → **número de cuenta**: así lo escribe el banco («*31973270756»);
 *   - diez dígitos que empiezan en `3` → **llave**: un celular colombiano;
 *   - diez dígitos que empiezan en `00` → **llave**: la forma de las de QR ([esLlaveDeQr]);
 *   - lo demás → **número de cuenta**.
 *
 * Si se equivoca (una cuenta de diez dígitos que empieza en 3), la pantalla ofrece darlo vuelta con
 * [otraLecturaDe].
 */
fun identificadorEscrito(crudo: String): IdentificadorDelDestino? {
    val limpio = crudo.trim()
    if (limpio.isEmpty()) return null
    if (limpio.any { it == '@' || it.isLetter() }) {
        return IdentificadorDelDestino(TipoDeIdentificador.LLAVE, normalizarLlave(limpio))
    }
    val digitos = soloLosDigitos(limpio)
    if (digitos.isEmpty()) return null
    val esLlave = !limpio.startsWith("*") && digitos.length == 10 &&
        (digitos.startsWith("3") || esFormaDeQr(digitos))
    return IdentificadorDelDestino(if (esLlave) TipoDeIdentificador.LLAVE else TipoDeIdentificador.NUMERO, digitos)
}

/**
 * La otra forma de leer lo mismo: un número como llave, o una llave de solo dígitos como número.
 * `null` si no hay otra (una llave con letras o arroba no puede ser un número de cuenta).
 */
fun otraLecturaDe(identificador: IdentificadorDelDestino): IdentificadorDelDestino? = when (identificador.tipo) {
    TipoDeIdentificador.NUMERO -> IdentificadorDelDestino(TipoDeIdentificador.LLAVE, soloLosDigitos(identificador.valor))
    TipoDeIdentificador.LLAVE -> soloLosDigitos(identificador.valor)
        .takeIf { it.isNotEmpty() && it.length == normalizarLlave(identificador.valor).length }
        ?.let { IdentificadorDelDestino(TipoDeIdentificador.NUMERO, it) }
}

/** Cómo dice la hoja lo que entendió: «Número de cuenta ·0756», «Llave 3001112222». */
fun comoLoEntiendeMovi(identificador: IdentificadorDelDestino): String = when {
    identificador.tipo == TipoDeIdentificador.NUMERO ->
        "Número de cuenta ·" + soloLosDigitos(identificador.valor).takeLast(MIN_DIGITOS_DEL_NUMERO)
    laLlaveEsUnNombre(identificador.valor) -> "Nombre con que lo nombra el banco"
    else -> "Llave " + normalizarLlave(identificador.valor)
}

/** El motivo por el que [identificador] no sirve, o `null`. La misma regla que el server. */
fun rechazoDelIdentificador(identificador: IdentificadorDelDestino): String? = when (identificador.tipo) {
    TipoDeIdentificador.NUMERO -> when {
        soloLosDigitos(identificador.valor).length < MIN_DIGITOS_DEL_NUMERO -> NUMERO_DEMASIADO_CORTO
        soloLosDigitos(identificador.valor).length > MAX_DIGITOS_DEL_NUMERO -> NUMERO_DEMASIADO_LARGO
        else -> null
    }
    TipoDeIdentificador.LLAVE -> when {
        normalizarLlave(identificador.valor).length < MIN_LARGO_DE_LA_LLAVE -> LLAVE_DEMASIADO_CORTA
        normalizarLlave(identificador.valor).length > MAX_LARGO_DE_LA_LLAVE -> LLAVE_DEMASIADO_LARGA
        else -> null
    }
}

// ── Unir dos fichas ─────────────────────────────────────────────────────────────

/**
 * **[queda] con todo lo de [seVa]**: sus identificadores se suman, y la nota y el tipo que [queda]
 * no tenga los toma de [seVa]. El nombre es el de [queda] — es la ficha que el dueño eligió
 * conservar. Los movimientos no se tocan: se reconocen por el texto del banco, y ahora todos esos
 * datos son de [queda].
 */
fun unirTerceros(seVa: DestinoConocido, queda: DestinoConocido): DestinoConocido =
    queda.conIdentificadores(queda.todosLosIdentificadores() + seVa.todosLosIdentificadores())
        .copy(
            deQuien = queda.deQuien ?: seVa.deQuien,
            // Un tipo deducido no se hereda como si alguien lo hubiera elegido.
            tipo = queda.tipo.takeUnless { queda.tipoInferido } ?: seVa.tipo.takeUnless { seVa.tipoInferido },
        )

// ── Lo enviado y lo recibido, período por período ───────────────────────────────

/** Lo que se le envió y lo que envió en un período del dueño. */
data class EnviadoYRecibido(
    val periodo: PeriodoFinanciero,
    val enviado: Map<String, Long>,
    val recibido: Map<String, Long>,
)

/**
 * **Lo enviado y lo recibido, período por período** — del más reciente al más viejo, por el período
 * DEL DUEÑO (ver [loQueSeLeMandoPorPeriodo]). Un período aparece si hubo algo en cualquiera de las
 * dos direcciones; nunca se suman entre sí.
 */
fun loDeCadaPeriodo(
    enviados: List<FinancialEvent>,
    recibidos: List<FinancialEvent>,
    settings: PeriodSettings,
): List<EnviadoYRecibido> {
    val ida = enviados.groupBy { periodoDe(it.timestamp, settings) }
    val vuelta = recibidos.groupBy { periodoDe(it.timestamp, settings) }
    return (ida.keys + vuelta.keys).distinct()
        .map { p ->
            EnviadoYRecibido(
                periodo = p,
                enviado = totalesHaciaElDestino(ida[p].orEmpty()),
                recibido = totalesHaciaElDestino(vuelta[p].orEmpty()),
            )
        }
        .sortedWith(compareByDescending<EnviadoYRecibido> { it.periodo.year }.thenByDescending { it.periodo.month })
}

/**
 * **El tercero que nombra una pregunta** («¿cuánto le he mandado a Caro?»): por su nombre (sin
 * tildes ni mayúsculas, entero o como comienzo de palabra), o por uno de sus números o llaves. Si
 * coinciden varios, el de nombre exacto; si sigue habiendo más de uno, ninguno — se contesta con la
 * lista y que el dueño elija.
 */
fun terceroQueNombra(pregunta: String, destinos: List<DestinoConocido>): DestinoConocido? {
    val q = normalizarParaBuscar(pregunta.trim())
    if (q.isEmpty()) return null
    val exacto = destinos.filter { normalizarParaBuscar(it.nombre) == q }
    if (exacto.size == 1) return exacto.single()
    val digitos = soloLosDigitos(q)
    val candidatos = destinos.filter { d ->
        val nombre = normalizarParaBuscar(d.nombre)
        nombre.split(' ').any { it.startsWith(q) } || nombre.startsWith(q) ||
            d.todosLosIdentificadores().any { id ->
                normalizarParaBuscar(id.valor) == q || (digitos.length >= 4 && soloLosDigitos(id.valor).endsWith(digitos))
            }
    }
    return candidatos.singleOrNull()
}
