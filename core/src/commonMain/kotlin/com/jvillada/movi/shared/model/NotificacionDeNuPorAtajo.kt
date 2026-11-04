package com.jvillada.movi.shared.model

/*
 * # Vuelta 127 · el iPhone avisa las notificaciones de Nu por el atajo
 *
 * En el iPhone de Caro (iOS 27) una automatización «al llegar una notificación de la app Nu» le pasa la
 * notificación al atajo «Movi - Avisar un mensaje», que la manda a `POST /api/captura/atajo` como un
 * mensaje más. El filtro del atajo (`seGuardaElSmsDelAtajo`) pide lo que tiene un SMS —que el texto
 * **empiece por el nombre de un banco y traiga monto**— y una notificación de Nu no es así: «Compra
 * aprobada por $27.500,00: Tu compra en RAPPI…» no empieza por un banco, y «Recibimos tu depósito por
 * 1.500.000,00» ni siquiera trae «$». El 3-nov el server descartó 9 así.
 *
 * Esto las reconoce **por su forma**, la que ya conocen el parser (`loDeNuEsUnMovimiento`), el pago de la
 * tarjeta ([esElPagoDeLaTarjetaNu]) y el depósito propio ([esUnDepositoPropio]), y devuelve el texto como
 * lo sube un Android («título: cuerpo», [textoDeUnaNotificacion]): el resto del camino —el parser, el
 * mismo pago, el dedupe por texto y tiempo— lo lee igual. Lo que no tiene una forma de Nu sigue por el
 * filtro de siempre y, si no pasa, se descarta **sin guardar el texto**.
 *
 * No se sabe qué manda Atajos (solo el cuerpo, título y cuerpo con o sin separador, o el nombre de la app
 * adelante), así que se aceptan todas las formas.
 */

/** Tope del texto de una notificación: el mismo con que el teléfono Android la sube (`FiltroDeNotificaciones`). */
const val MAX_TEXTO_DE_UNA_NOTIFICACION: Int = 500

/**
 * **Título + cuerpo en una sola línea**, como sube una notificación el teléfono Android y como la lee el
 * parser del server. Si el cuerpo ya empieza con el título no se repite: eso solo le daría al dedupe por
 * texto una cadena distinta para el mismo hecho. Vivía en el `androidMain` de `:shared`; bajó a `:core`
 * en la vuelta 127 para que el atajo del iPhone arme **el mismo texto** que el Android y el dedupe los
 * vea iguales.
 */
fun textoDeUnaNotificacion(titulo: String, cuerpo: String): String {
    val t = titulo.trim()
    val c = cuerpo.trim()
    val junto = when {
        t.isEmpty() -> c
        c.startsWith(t, ignoreCase = true) -> c
        else -> "$t: $c"
    }
    return junto.take(MAX_TEXTO_DE_UNA_NOTIFICACION)
}

/** El nombre de la app delante: «Nu», «Nu:», «Nu -», «Nu ·» o «Nu» y un salto de línea. Nunca «Nubank» ni «Nuevo». */
private val PREFIJO_DE_LA_APP = Regex("""^nu(?![\p{L}\p{N}])[ \t]*(?:[:\-–—·][ \t]*)?\s*""", RegexOption.IGNORE_CASE)

/** Una cifra como la escribe Nu: «$27.500,00», «USD12.300,00», «1.500.000,00». */
private const val MONTO = """(?:\$\s?|(?:cop|usd)\s?)?\d(?:[\d.,]*\d)?"""

/**
 * **Los títulos con que Nu encabeza sus notificaciones**, los que un Android sube antes de «: ». Se leen al
 * principio del texto para partirlo en título y cuerpo cuando el atajo los manda pegados («Compra
 * aprobada por $27.500,00 Tu compra en…») o en dos renglones.
 */
private val TITULOS_DE_NU: List<Regex> = listOf(
    """compra\s+aprobada\s+por\s+$MONTO""",
    """compra\s+rechazada""",
    """¡?\s*bravo!?\s+pagaste\s+tu\s+tarjeta\s+de\s+cr[eé]dito\s+nu""",
    """recibimos\s+tu\s+dep[oó]sito\s+por\s+$MONTO""",
    """¡?\s*uy!?\s+algo\s+sali[oó]\s+mal\s+con\s+tu\s+pago""",
    """tarjeta\s+verificada""",
    """recibiste\s+$MONTO\s+en\s+tu\s+cuenta""",
    """recibiste\s+tu\s+rendimiento""",
    """enviaste\s+$MONTO""",
).map { Regex("^(?:$it)(?=$|[\\s:.\\-–—])", RegexOption.IGNORE_CASE) }

/**
 * **Las formas de Nu que bastan solas**, sin el nombre de la app delante: frases que ningún SMS de otro
 * banco escribe así. Sobre el texto ya normalizado ([normalizarParaBuscar]: minúsculas, sin tildes).
 */
private val FORMAS_DE_NU: List<Regex> = listOf(
    // La compra: el título, o el cuerpo («Tu compra en RAPPI por $27.500,00 con tu tarjeta terminada en 1336»).
    """\bcompra\s+aprobada\s+por\s+$MONTO""",
    """\btu\s+compra\s+en\s+.+\s+con\s+tu\s+tarjeta\s+terminada\s+en\s+\d{4}""",
    // El rechazo: «Compra rechazada», «¡Uy! Algo salió mal con tu pago: Tu pago por $X no fue aprobado».
    """\bcompra\s+rechazada\b""",
    """\balgo\s+salio\s+mal\s+con\s+tu\s+pago\b""",
    """\btu\s+pago\s+por\s+$MONTO\s+no\s+fue\s+aprobado""",
    // «Tarjeta verificada: ANTHROPIC verificó tu tarjeta terminada en 1336».
    """\btarjeta\s+verificada\b""",
    """\bverifico\s+tu\s+tarjeta\s+terminada\s+en\s+\d{4}""",
    // El pago de la tarjeta («¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por $X»).
    """\bpagaste\s+tu\s+tarjeta\s+de\s+credito\s+nu\b""",
    """\brecibimos\s+tu\s+pago\s+por\s+$MONTO""",
    // La plata que llega: «Recibiste 300.000,00 en tu cuenta: Te llegó dinero de …».
    """\brecibiste\s+$MONTO\s+en\s+tu\s+cuenta\b""",
    """\bte\s+llego\s+dinero\s+de\b""",
    // Lo que rinde la Cajita (Movi lo aparta o no lo lee: no es un movimiento).
    """\btu\s+cajita\b""",
    """\brecibiste\s+tu\s+rendimiento\b""",
).map { Regex(it, RegexOption.IGNORE_CASE) }

/**
 * **Las formas de Nu que piden el nombre de la app delante** («Nu: …»): son de Nu (el pago desde la
 * cuenta, los envíos), pero otro banco podría escribirlas parecido.
 */
private val FORMAS_DE_NU_CON_LA_APP: List<Regex> = listOf(
    """\bpago\s+aprobado\s+por\s+$MONTO""",
    """\bpagaste\s+en\s+.+\s+con\s+(?:tu\s+)?cuenta\b""",
    """\benviaste\s+$MONTO""",
    """\bacabas\s+de\s+enviar\b""",
    """\b(?:le|se\s+los)\s+enviaste\s+a\s""",
).map { Regex(it, RegexOption.IGNORE_CASE) }

/**
 * Lo que dice que una compra **no** pasó: con esto no se le pone el título «Compra aprobada» a un cuerpo
 * suelto (ver [notificacionDeNuDelAtajo]).
 */
private val NO_PASO = Regex("""rechaz|no\s+fue\s+aprobad|no\s+aprobad|declin""", RegexOption.IGNORE_CASE)

/** El cuerpo de la compra, suelto: «Tu compra en RAPPI por $27.500,00 con tu tarjeta…» → el monto, para su título. */
private val CUERPO_DE_LA_COMPRA = Regex("""^tu\s+compra\s+en\s+.+?\s+por\s+($MONTO)""", RegexOption.IGNORE_CASE)

/** El cuerpo del pago de la tarjeta, suelto: «Recibimos tu pago por $1.306.937,91. En un rato…». */
private val CUERPO_DEL_PAGO_DE_LA_TARJETA = Regex("""^recibimos\s+tu\s+pago\s+por\s+$MONTO""", RegexOption.IGNORE_CASE)

/** El título del pago de la tarjeta, el que Nu le pone siempre (`esElPagoDeLaTarjetaNu`). */
private const val TITULO_DEL_PAGO_DE_LA_TARJETA = "¡Bravo! Pagaste tu tarjeta de crédito Nu"

/**
 * **¿Lo que mandó el atajo es una notificación de Nu?** El texto como lo sube un Android («título:
 * cuerpo», [textoDeUnaNotificacion]), o `null` si no tiene una forma de Nu.
 *
 * 1. Se quita el nombre de la app delante («Nu», «Nu:», «Nu -», «Nu» y un salto de línea), si lo trae.
 * 2. Si lo que queda empieza por un título de Nu ([TITULOS_DE_NU]), título y cuerpo se juntan con «: »,
 *    vengan pegados, en dos renglones o ya con los dos puntos. Si no y el atajo mandó el nombre de la
 *    app, va «Nu: …», como sube el Android las que Nu publica con el nombre de la app de título («Nu:
 *    Acabas de enviar…»). Si no, dos renglones son título y cuerpo, y uno solo queda como está.
 * 3. **Un cuerpo suelto** de las dos formas que tienen un solo título posible recupera el suyo, para que
 *    el texto sea el mismo que sube el Android (el dedupe) y el parser lo lea: la compra («Tu compra en X
 *    por $M…» → «Compra aprobada por $M: …», salvo que diga que no pasó) y el pago de la tarjeta
 *    («Recibimos tu pago por $M…» → «¡Bravo! Pagaste tu tarjeta de crédito Nu: …»). Los demás cuerpos
 *    quedan como llegaron.
 * 4. Tiene que tener una forma de Nu: las de [FORMAS_DE_NU], el pago de la tarjeta
 *    ([esElPagoDeLaTarjetaNu]), el depósito propio ([esUnDepositoPropio]), o —solo con el nombre de la
 *    app delante— las de [FORMAS_DE_NU_CON_LA_APP]. Y **no puede empezar por otro banco**: «Bancolombia:
 *    Recibimos tu pago…» es un SMS de Bancolombia ([marcaDelRemitente]).
 *
 * Un rechazo y «Tarjeta verificada» se reconocen (son de Nu) y el server los guarda apartados, como a
 * los del Android (`queEsEsteMensaje`). Una promoción de Nu sin ninguna de estas formas no se reconoce:
 * sigue el filtro de siempre y se descarta sin guardar el texto. Pura.
 */
fun notificacionDeNuDelAtajo(texto: String): String? {
    val crudo = texto.replace('\r', '\n').trim()
    if (crudo.isEmpty()) return null
    val prefijo = PREFIJO_DE_LA_APP.find(crudo)
    val resto = prefijo?.let { crudo.substring(it.range.last + 1).trim() } ?: crudo
    if (resto.isEmpty()) return null
    val conLaApp = prefijo != null

    val normalizado = normalizado(resto, conLaApp) ?: return null
    if (!tieneUnaFormaDeNu(normalizado, conLaApp)) return null
    // Un SMS de otro banco que escribe parecido («Bancolombia: Recibimos tu pago…») no es de Nu.
    if (marcaDelRemitente("", normalizado)?.let { it.id != "nu" } == true) return null
    return normalizado.take(MAX_TEXTO_DE_UNA_NOTIFICACION)
}

/** Título y cuerpo juntos como los sube el Android (los pasos 2 y 3 de [notificacionDeNuDelAtajo]). */
private fun normalizado(resto: String, conLaApp: Boolean): String? {
    TITULOS_DE_NU.firstNotNullOfOrNull { it.find(resto) }?.let { titulo ->
        val cuerpo = resto.substring(titulo.range.last + 1).trimStart(' ', '\t', '\n', ':', '-', '–', '—', '·')
        return textoDeUnaNotificacion(titulo.value, cuerpo)
    }
    CUERPO_DE_LA_COMPRA.find(resto)?.takeIf { !NO_PASO.containsMatchIn(resto) }?.let { compra ->
        return textoDeUnaNotificacion("Compra aprobada por ${compra.groupValues[1]}", resto)
    }
    if (CUERPO_DEL_PAGO_DE_LA_TARJETA.containsMatchIn(resto)) return textoDeUnaNotificacion(TITULO_DEL_PAGO_DE_LA_TARJETA, resto)
    if (conLaApp) return textoDeUnaNotificacion("Nu", resto)
    val renglones = resto.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return if (renglones.size >= 2) textoDeUnaNotificacion(renglones.first(), resto.substringAfter('\n').trim()) else resto
}

private fun tieneUnaFormaDeNu(texto: String, conLaApp: Boolean): Boolean {
    val t = normalizarParaBuscar(texto)
    return FORMAS_DE_NU.any { it.containsMatchIn(t) } ||
        esElPagoDeLaTarjetaNu("Nu", texto) ||
        esUnDepositoPropio(texto) ||
        (conLaApp && FORMAS_DE_NU_CON_LA_APP.any { it.containsMatchIn(t) })
}
