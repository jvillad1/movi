package com.jvillada.movi.shared.model

/*
 * # El nombre de un tercero en sus movimientos: una sola regla (20-oct-2026)
 *
 * El dueño: *«que cuando transfiero para un tercero que se logre identificar o que yo pueda asociar
 * el nombre de la transferencia se actualice de inmediato … que la gestión de cuentas sea mucho más
 * sencilla»*.
 *
 * Hasta esta fecha un movimiento solo cambiaba de nombre con un paso aparte («Ponerle el nombre a N
 * movimientos anteriores») y solo si su nombre era un número. El nombre que escribe el banco
 * («DANIEL LEONETT»), un nombre viejo del propio tercero («Transferencia a NombreViejo») o «Recibido
 * de Carolina» se quedaban como estaban. Acá vive **la única regla** de qué nombre es de Movi (y se
 * puede cambiar) y cuál es del dueño (y no se toca). La usan:
 *
 * - el server, dentro de la misma transacción de cada escritura que cambia lo que un tercero
 *   reconoce o cómo se llama (crear, editar, sumar un identificador, unir) — ver `DestinoRoutes`;
 * - el server al leer un aviso nuevo ([conElDestinoConocido]);
 * - la app, para mostrar al instante el nombre que el server acaba de escribir.
 *
 * ## Qué es un nombre generado ([esUnNombreGenerado])
 *
 * Se mira lo que va **antes de la nota**: lo que el dueño escribe después de « · » («Transferencia a
 * Caro · arreglos Gardenera») es suyo y se conserva tal cual. Es generado:
 *
 * 1. lo ilegible ([esUnNombreIlegible]): un número, una llave o nada — «Transferencia a la cuenta
 *    *43087514791», «Pago QR · llave 0047142708», «Transferencia»;
 * 2. el nombre del tercero, uno de sus nombres anteriores ([DestinoConocido.nombresAnteriores]) o el
 *    nombre con que lo nombra el banco (un identificador-nombre: «carolina restrepo salazar»), sin
 *    distinguir mayúsculas ni tildes, solo o con «Transferencia a/de», «Transferencia recibida de»,
 *    «Recibido de» o «Pago a» delante — también repetido o con basura del mismo prefijo
 *    («TrTransferencia a X»);
 * 3. con uno de esos prefijos delante, **las primeras palabras** de uno de esos nombres, si suman al
 *    menos [MIN_LETRAS_DE_UN_NOMBRE_PARCIAL] letras: «Recibido de Carolina» es de quien el banco
 *    llama «CAROLINA RESTREPO SALAZAR». Sin prefijo, un nombre a medias («Carolina» a secas) no
 *    cuenta: pudo escribirlo el dueño.
 *
 * Todo lo demás lo escribió el dueño y **no se toca**: «Abono de Caro a la AMEX · Wompi-PSE»,
 * «Partido DIM y arreglo de llanta Caro», «Mercado».
 *
 * ## Qué movimientos ([terceroDelMovimiento])
 *
 * Nunca una pata de un traspaso o de una cuota (`transferId`), un saldo inicial, un ajuste o un pago
 * de tarjeta ([puedeLlevarElNombreDeUnTercero]), ni un anulado. Un movimiento es de un tercero si el
 * texto del banco lo nombra por un número o una llave (la señal fuerte, [elBancoLoNombra]); si no
 * hay señal fuerte, si su nombre lo nombra ([vaHaciaElDestino], [vieneDelDestino], o la regla 2/3 de
 * arriba). **Si lo nombran dos terceros, no es de ninguno**: un empate no se resuelve al azar.
 *
 * ## El formato ([nombreCanonicoDelMovimiento])
 *
 * Una persona (o un tercero sin tipo, que se lee como persona): «Transferencia a Caro» si salió,
 * «Transferencia de Caro» si entró. Un comercio: su nombre a secas, «Parqueadero Corpaul Lleras».
 *
 * ## El nombre del banco (`merchant`)
 *
 * Pasa por la misma regla, con una excepción: **si es un número o una llave, se queda**
 * («Transferencia a la cuenta *0756»). Por dos motivos. Es el único rastro del banco que tiene un
 * movimiento sin `rawPayload` (uno anotado a mano), y sin él el movimiento dejaría de reconocerse
 * por el número. Y es justo la huella con la que la memoria de categorías reconoce el próximo SMS de
 * ese número (`huellaDeUnMovimiento` → `cuenta:…`), que ahora propone el nombre canónico porque la
 * memoria propone la *descripción* de lo anotado. Un nombre (el del banco, uno viejo, otra forma del
 * mismo) sí pasa al canónico: así toda la historia de ese tercero queda bajo una sola huella
 * (`nombre:caro` — «Transferencia a Caro», «Transferencia de Caro» y «Caro» dan la misma) en vez de
 * partida entre el nombre viejo y el nuevo. Ver `MemoriaDeCategorias`.
 */

/** El separador con que el dueño agrega una nota: «Transferencia a Caro · arreglos Gardenera». */
const val SEPARADOR_DE_LA_NOTA: String = " · "

/** Cuántos nombres anteriores se recuerdan por tercero: los más nuevos. */
const val MAX_NOMBRES_ANTERIORES: Int = 10

/**
 * Cuántas letras tiene que tener, como mínimo, un nombre a medias para contar (regla 3): «Carolina»
 * (8) sí, «Juan» (4) no — demasiadas personas se llaman igual de corto. **Elegido, no medido.**
 */
const val MIN_LETRAS_DE_UN_NOMBRE_PARCIAL: Int = 5

/**
 * **El nombre que lleva un movimiento de [destino]**: «Transferencia a Caro» / «Transferencia de
 * Caro» para una persona (o sin tipo), el nombre a secas para un comercio.
 */
fun nombreCanonicoDelMovimiento(destino: DestinoConocido, tipo: TransactionType): String {
    val nombre = destino.nombre.trim()
    return when (destino.tipoOPersona()) {
        TipoDeTercero.COMERCIO -> nombre
        TipoDeTercero.PERSONA -> if (tipo == TransactionType.INCOME) "Transferencia de $nombre" else "Transferencia a $nombre"
    }
}

/**
 * [anteriores] con [nuevos] agregados al final: sin repetidos (sin mayúsculas ni tildes), sin el
 * [nombreDeHoy] y con los [MAX_NOMBRES_ANTERIORES] más nuevos.
 */
fun nombresAnterioresCon(anteriores: List<String>, nuevos: List<String>, nombreDeHoy: String): List<String> {
    val hoy = enPalabrasSimples(nombreDeHoy)
    val vistos = mutableSetOf<String>()
    // Del más nuevo al más viejo, para que un nombre repetido quede en su lugar más reciente.
    val delMasNuevo = (anteriores + nuevos).map { it.trim() }.filter { it.isNotEmpty() }.asReversed()
        .filter { n -> val clave = enPalabrasSimples(n); clave != hoy && vistos.add(clave) }
    return delMasNuevo.take(MAX_NOMBRES_ANTERIORES).asReversed()
}

/**
 * **El texto con el nombre del tercero puesto**, o `null` si lo que va antes de la nota no es un
 * nombre generado ([esUnNombreGenerado]) — entonces es del dueño y no se toca. La nota (lo que va
 * después de « · ») se conserva tal cual. Puede devolver el mismo [texto] si ya estaba bien.
 *
 * [otrosNombres] son nombres que también cuentan como generados para este caso: los que escribe el
 * banco en el aviso que se está leyendo («… a DANIEL LEONETT el …»).
 */
fun textoConElNombreDelTercero(
    texto: String,
    destino: DestinoConocido,
    tipo: TransactionType,
    otrosNombres: Collection<String> = emptyList(),
): String? {
    val partes = texto.split(SEPARADOR_DE_LA_NOTA)
    // De lo más largo a lo más corto: «Pago QR · llave 0047142708» es ilegible entero, y su « · » lo
    // puso el banco, no el dueño.
    for (k in partes.size downTo 1) {
        val cabeza = partes.take(k).joinToString(SEPARADOR_DE_LA_NOTA)
        if (!esUnNombreGenerado(cabeza, destino, otrosNombres)) continue
        val nota = partes.drop(k).joinToString(SEPARADOR_DE_LA_NOTA)
        val canonico = nombreCanonicoDelMovimiento(destino, tipo)
        return if (nota.isBlank()) canonico else canonico + SEPARADOR_DE_LA_NOTA + nota
    }
    return null
}

/** ¿[cabeza] (lo que va antes de la nota) es un nombre que puso Movi o el banco? Ver el comentario del archivo. */
fun esUnNombreGenerado(
    cabeza: String,
    destino: DestinoConocido,
    otrosNombres: Collection<String> = emptyList(),
): Boolean = esUnNombreIlegible(cabeza) || nombraPorSuNombre(cabeza, nombresDelTercero(destino) + otrosNombres.map(::enPalabrasSimples))

/**
 * Reglas 2 y 3: [cabeza], sin los prefijos de Movi, es uno de [nombres] (ya en [enPalabrasSimples]) o
 * —con prefijo— sus primeras palabras.
 */
private fun nombraPorSuNombre(cabeza: String, nombres: Collection<String>): Boolean {
    val (resto, conPrefijo) = sinLosPrefijosDeMovi(enPalabrasSimples(cabeza))
    if (resto.isEmpty()) return false
    val validos = nombres.filter { it.isNotEmpty() }
    if (resto in validos) return true
    if (!conPrefijo || resto.count { it.isLetter() } < MIN_LETRAS_DE_UN_NOMBRE_PARCIAL) return false
    val palabras = resto.split(' ')
    return validos.any { nombre ->
        val suyas = nombre.split(' ')
        suyas.size > palabras.size && suyas.take(palabras.size) == palabras
    }
}

/** Los nombres de [destino] ya en [enPalabrasSimples]: el de hoy, los anteriores y los identificadores-nombre. */
private fun nombresDelTercero(destino: DestinoConocido): List<String> =
    (listOf(destino.nombre) + destino.nombresAnteriores + destino.todosLosIdentificadores()
        .filter { it.tipo == TipoDeIdentificador.LLAVE && laLlaveEsUnNombre(it.valor) }.map { it.valor })
        .map(::enPalabrasSimples)
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Los prefijos con que Movi y el banco escriben a quién fue la plata. Con un relleno de hasta doce
 * letras pegado delante que sea el comienzo del mismo prefijo («trtransferencia a» — un tipeo o un
 * prefijo que se escribió dos veces).
 */
private val PREFIJO_DE_MOVI = Regex(
    """^([a-z]{0,12}?)(transferencia recibida de|transferencia a|transferencia de|recibido de|pago a) (.+)$""",
)

/** [texto] (en [enPalabrasSimples]) sin los prefijos de Movi, y si tenía alguno. */
private fun sinLosPrefijosDeMovi(texto: String): Pair<String, Boolean> {
    var resto = texto
    var tenia = false
    while (true) {
        val m = PREFIJO_DE_MOVI.find(resto) ?: break
        val relleno = m.groupValues[1]
        if (relleno.isNotEmpty() && !m.groupValues[2].startsWith(relleno)) break
        resto = m.groupValues[3]
        tenia = true
    }
    return resto to tenia
}

/** Sin mayúsculas, sin tildes, y todo lo que no es letra ni dígito como un espacio: «CARO.» → «caro». */
internal fun enPalabrasSimples(texto: String): String =
    normalizarParaBuscar(texto)
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")

// ── Sobre un movimiento ─────────────────────────────────────────────────────────

/** Lo que no es plata que fue a un tercero o vino de él, y nunca se renombra. */
private val CATEGORIAS_QUE_NO_SON_DE_UN_TERCERO = setOf(
    TRANSFER_CATEGORY, CARD_PAYMENT_CATEGORY, OPENING_CATEGORY, ADJUSTMENT_CATEGORY, ORPHANED_LEG_CATEGORY,
)

/**
 * ¿[evento] puede llevar el nombre de un tercero? No una pata de traspaso o de cuota (`transferId`),
 * ni un saldo inicial, un ajuste, un pago de tarjeta o una pata que perdió su cuenta.
 */
fun puedeLlevarElNombreDeUnTercero(evento: FinancialEvent): Boolean =
    evento.transferId == null &&
        (evento.type == TransactionType.EXPENSE || evento.type == TransactionType.INCOME) &&
        evento.category !in CATEGORIAS_QUE_NO_SON_DE_UN_TERCERO

/**
 * **La descripción nueva de [evento], ligado a [destino]**, o `null` si no hay que tocarla: no es un
 * nombre generado, ya dice lo que tiene que decir, o el movimiento no puede llevar el nombre de un
 * tercero ([puedeLlevarElNombreDeUnTercero]). No decide si el movimiento ES de [destino]: eso lo
 * decide [terceroDelMovimiento].
 */
fun nombreDelMovimientoParaElDestino(evento: FinancialEvent, destino: DestinoConocido): String? {
    if (!puedeLlevarElNombreDeUnTercero(evento)) return null
    return textoConElNombreDelTercero(evento.description, destino, evento.type)?.takeIf { it != evento.description }
}

/**
 * **El nombre del banco nuevo de [evento]** (`merchant`), o `null` si se queda: misma regla que la
 * descripción, salvo un número o una llave, que se quedan (ver el comentario del archivo).
 */
fun merchantDelMovimientoParaElDestino(evento: FinancialEvent, destino: DestinoConocido): String? {
    if (!puedeLlevarElNombreDeUnTercero(evento)) return null
    val actual = evento.merchant?.takeIf { it.isNotBlank() } ?: return null
    if (huellaDeUnMovimiento(actual)?.let(::laHuellaEsUnNumero) == true) return null
    return textoConElNombreDelTercero(actual, destino, evento.type)?.takeIf { it != actual }
}

/**
 * **¿De qué tercero es [evento]?** De uno solo o de ninguno. Primero la señal fuerte —el texto del
 * banco nombra uno de sus números o llaves—; sin ella, su nombre. Dos que lo reclaman con la misma
 * fuerza: ninguno.
 */
fun terceroDelMovimiento(evento: FinancialEvent, destinos: List<DestinoConocido>): DestinoConocido? {
    if (!puedeLlevarElNombreDeUnTercero(evento)) return null
    val fuertes = destinos.filter { elBancoLoNombra(evento, it) }
    if (fuertes.isNotEmpty()) return fuertes.singleOrNull()
    return destinos.filter { d -> loNombraSuNombre(evento, d) }.singleOrNull()
}

private fun loNombraSuNombre(evento: FinancialEvent, destino: DestinoConocido): Boolean {
    val partes = evento.description.split(SEPARADOR_DE_LA_NOTA)
    val porLaCabeza = (partes.size downTo 1).any { k ->
        nombraPorSuNombre(partes.take(k).joinToString(SEPARADOR_DE_LA_NOTA), nombresDelTercero(destino))
    }
    return porLaCabeza ||
        (evento.type == TransactionType.EXPENSE && vaHaciaElDestino(evento, destino)) ||
        (evento.type == TransactionType.INCOME && vieneDelDestino(evento, destino))
}

/** Un movimiento con su nombre nuevo: lo que el server escribe y la app espeja. */
data class NombreNuevoDelMovimiento(
    val id: String,
    val descripcion: String,
    /** El `merchant` que queda (el de antes si no cambió). */
    val merchant: String?,
)

/**
 * **Los movimientos de [destino] que cambian de nombre**, con el nombre nuevo. [destinos] son todos
 * los terceros del dueño (con o sin [destino]: se usa la versión de [destino] que se pasa), para que
 * un movimiento que nombran dos no se le asigne a ninguno. [anulados] no se tocan nunca.
 */
fun movimientosParaElNombreDelTercero(
    destino: DestinoConocido,
    eventos: List<FinancialEvent>,
    destinos: List<DestinoConocido>,
    anulados: Set<String> = emptySet(),
): List<NombreNuevoDelMovimiento> {
    val todos = destinos.filter { it.id != destino.id } + destino
    return eventos.mapNotNull { ev ->
        if (ev.id in anulados) return@mapNotNull null
        if (terceroDelMovimiento(ev, todos)?.id != destino.id) return@mapNotNull null
        val descripcion = nombreDelMovimientoParaElDestino(ev, destino)
        val merchant = merchantDelMovimientoParaElDestino(ev, destino)
        if (descripcion == null && merchant == null) return@mapNotNull null
        NombreNuevoDelMovimiento(ev.id, descripcion ?: ev.description, merchant ?: ev.merchant)
    }
}
