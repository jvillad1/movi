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
fun textoDeUnaNotificacion(titulo: String, cuerpo: String): String = juntar(titulo, cuerpo).take(MAX_TEXTO_DE_UNA_NOTIFICACION)

/** [textoDeUnaNotificacion] sin el tope: la forma de Nu se busca en el texto entero y se recorta al final. */
private fun juntar(titulo: String, cuerpo: String): String {
    val t = titulo.trim()
    val c = cuerpo.trim()
    return when {
        t.isEmpty() -> c
        c.startsWith(t, ignoreCase = true) -> c
        else -> "$t: $c"
    }
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
 * **Las formas de Nu que bastan solas**, sin el nombre de la app delante. Revisión de la vuelta 127: el
 * atajo casi nunca manda remitente, así que lo que entra por acá entra con el texto entero: **cada una
 * pide un monto Y una seña que solo escribe Nu** (su título junto a su cuerpo, o la frase con que cierra el
 * cuerpo). Frases sueltas («tu cajita», «compra rechazada», «te llegó dinero de») no bastan: «Te dejé tu
 * cajita en la portería» es de un amigo. Sobre el texto ya normalizado ([normalizarParaBuscar]: minúsculas,
 * sin tildes) y armado como lo sube el Android («título: cuerpo»).
 */
private val FORMAS_DE_NU_SOLAS: List<List<Regex>> = listOf(
    // La compra, con su título y su cuerpo: «Compra aprobada por $X: Tu compra en … con tu tarjeta terminada en 1336».
    listOf("""^compra\s+aprobada\s+por\s+$MONTO:\s+tu\s+compra\s+en\s.+\scon\s+tu\s+tarjeta\s+terminada\s+en\s+\d{4}"""),
    // …o con el cierre de Nu: «Tu compra en … por $X con tu tarjeta terminada en 1336 ha sido APROBADA».
    listOf(
        """\btu\s+compra\s+en\s.+\spor\s+$MONTO""",
        """\bcon\s+tu\s+tarjeta\s+terminada\s+en\s+\d{4}""",
        """\bha\s+sido\s+aprobada\b""",
    ),
    // El rechazo, con su título y su cuerpo con monto.
    listOf("""^compra\s+rechazada:\s+tu\s+compra\s+en\s.+\spor\s+$MONTO"""),
    listOf("""^¡?\s*uy!?\s+algo\s+salio\s+mal\s+con\s+tu\s+pago:\s+tu\s+pago\s+por\s+$MONTO"""),
    // El pago de la tarjeta: el título nombra a Nu, o el cuerpo cierra como Nu.
    listOf("""\bpagaste\s+tu\s+tarjeta\s+de\s+credito\s+nu\b""", """\brecibimos\s+tu\s+pago\s+por\s+$MONTO"""),
    listOf("""\brecibimos\s+tu\s+pago\s+por\s+$MONTO""", """\ben\s+un\s+rato\s+podras\s+verlo\s+en\s+tu\s+app\b"""),
    // El depósito propio, con su monto y su cierre.
    listOf("""\brecibimos\s+tu\s+deposito\s+por\s+$MONTO""", """\ben\s+unas\s+horas\s+podras\s+revisar\s+la\s+actualizacion\b"""),
    // La plata que llega, con su título y su cuerpo.
    listOf("""^recibiste\s+$MONTO\s+en\s+tu\s+cuenta:\s+te\s+llego\s+dinero\s+de\s"""),
).map { forma -> forma.map { Regex(it, RegexOption.IGNORE_CASE) } }

/**
 * **Las formas de Nu que piden el nombre de la app delante** («Nu: …»): son de Nu, pero solas no lo prueban
 * —sin monto, o algo que otro banco o una persona podría escribir—.
 */
private val FORMAS_DE_NU_CON_LA_APP: List<Regex> = listOf(
    """\bcompra\s+aprobada\s+por\s+$MONTO""",
    """\btu\s+compra\s+en\s+.+\s+con\s+tu\s+tarjeta\s+terminada\s+en\s+\d{4}""",
    """\bcompra\s+rechazada\b""",
    """\balgo\s+salio\s+mal\s+con\s+tu\s+pago\b""",
    """\btu\s+pago\s+por\s+$MONTO\s+no\s+fue\s+aprobado""",
    """\btarjeta\s+verificada\b""",
    """\bverifico\s+tu\s+tarjeta\s+terminada\s+en\s+\d{4}""",
    """\bpagaste\s+tu\s+tarjeta\s+de\s+credito\s+nu\b""",
    """\brecibimos\s+tu\s+pago\s+por\s+$MONTO""",
    """\brecibimos\s+tu\s+deposito\b""",
    """\brecibiste\s+$MONTO\s+en\s+tu\s+cuenta\b""",
    """\bte\s+llego\s+dinero\s+de\b""",
    """\btu\s+cajita\b""",
    """\brecibiste\s+tu\s+rendimiento\b""",
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

/** Cómo cierra Nu el cuerpo de una compra aprobada: «… ha sido APROBADA». */
private val CIERRE_DE_LA_COMPRA = Regex("""\bha\s+sido\s+aprobada\b""", RegexOption.IGNORE_CASE)

/** El cuerpo del pago de la tarjeta, suelto: «Recibimos tu pago por $1.306.937,91. En un rato…». */
private val CUERPO_DEL_PAGO_DE_LA_TARJETA = Regex("""^recibimos\s+tu\s+pago\s+por\s+$MONTO""", RegexOption.IGNORE_CASE)

/** Cómo cierra Nu el cuerpo del pago de la tarjeta: «En un rato podrás verlo en tu app». */
private val CIERRE_DEL_PAGO_DE_LA_TARJETA = Regex("""\ben\s+un\s+rato\s+podr[aá]s\s+verlo\s+en\s+tu\s+app\b""", RegexOption.IGNORE_CASE)

/** El título del pago de la tarjeta, el que Nu le pone siempre (`esElPagoDeLaTarjetaNu`). */
private const val TITULO_DEL_PAGO_DE_LA_TARJETA = "¡Bravo! Pagaste tu tarjeta de crédito Nu"

/**
 * **¿Lo que mandó el atajo es una notificación de Nu?** El texto como lo sube un Android («título:
 * cuerpo», [textoDeUnaNotificacion]), o `null` si no tiene una forma de Nu.
 *
 * 1. Se quita el nombre de la app delante («Nu», «Nu:», «Nu -», «Nu» y un salto de línea), si lo trae.
 * 2. Si lo que queda empieza por un título de Nu ([TITULOS_DE_NU]), título y cuerpo se juntan con «: »,
 *    vengan pegados, en dos renglones o con «:» o «.» de por medio. Si no y el atajo mandó el nombre de la
 *    app, va «Nu: …», como sube el Android las que Nu publica con el nombre de la app de título («Nu:
 *    Acabas de enviar…»). Si no, dos renglones son título y cuerpo, y uno solo queda como está.
 * 3. **Un cuerpo suelto** de las dos formas que tienen un solo título posible recupera el suyo —para que
 *    el texto sea el mismo que sube el Android (el dedupe) y el parser lo lea—, **pero solo con una seña
 *    de Nu**: el nombre de la app delante o el cierre de Nu del cuerpo. La compra («Tu compra en X por $M…
 *    ha sido APROBADA» → «Compra aprobada por $M: …», salvo que diga que no pasó) y el pago de la tarjeta
 *    («Recibimos tu pago por $M. En un rato podrás verlo en tu app» → «¡Bravo! Pagaste tu tarjeta de
 *    crédito Nu: …»). Sin la seña, «Recibimos tu pago por $85.000. Gracias, Claro» no se vuelve el pago de
 *    la tarjeta Nu.
 * 4. Tiene que tener una forma de Nu: sin el nombre de la app, una de [FORMAS_DE_NU_SOLAS] (monto **y**
 *    seña de Nu); con él, también las de [FORMAS_DE_NU_CON_LA_APP]. Y **no puede empezar por otro banco**:
 *    «Bancolombia: Recibimos tu pago…» es un SMS de Bancolombia ([marcaDelRemitente]).
 *
 * Un rechazo y «Tarjeta verificada» se reconocen (solo con su forma entera, o con «Nu» delante) y el server
 * los guarda apartados, como a los del Android (`queEsEsteMensaje`). Lo que no se reconoce sigue el filtro
 * de siempre y se descarta **sin guardar el texto**. Pura.
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
        // «Compra aprobada por $27.500,00. Tu compra…»: el punto también separa, y no queda «: . Tu compra».
        val cuerpo = resto.substring(titulo.range.last + 1).trimStart(' ', '\t', '\n', ':', '.', '-', '–', '—', '·')
        return juntar(titulo.value, cuerpo)
    }
    CUERPO_DE_LA_COMPRA.find(resto)
        ?.takeIf { (conLaApp || CIERRE_DE_LA_COMPRA.containsMatchIn(resto)) && !NO_PASO.containsMatchIn(resto) }
        ?.let { compra -> return juntar("Compra aprobada por ${compra.groupValues[1]}", resto) }
    if (CUERPO_DEL_PAGO_DE_LA_TARJETA.containsMatchIn(resto) && (conLaApp || CIERRE_DEL_PAGO_DE_LA_TARJETA.containsMatchIn(resto))) {
        return juntar(TITULO_DEL_PAGO_DE_LA_TARJETA, resto)
    }
    if (conLaApp) return juntar("Nu", resto)
    val renglones = resto.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return if (renglones.size >= 2) juntar(renglones.first(), resto.substringAfter('\n').trim()) else resto
}

private fun tieneUnaFormaDeNu(texto: String, conLaApp: Boolean): Boolean {
    val t = normalizarParaBuscar(texto)
    if (FORMAS_DE_NU_SOLAS.any { forma -> forma.all { it.containsMatchIn(t) } }) return true
    // Con «Nu» delante: las demás formas, el pago de la tarjeta y el depósito propio de siempre.
    return conLaApp && (FORMAS_DE_NU_CON_LA_APP.any { it.containsMatchIn(t) } || esElPagoDeLaTarjetaNu("Nu", texto) || esUnDepositoPropio(texto))
}
