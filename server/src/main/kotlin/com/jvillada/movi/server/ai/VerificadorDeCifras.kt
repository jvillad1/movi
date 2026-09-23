package com.jvillada.movi.server.ai

import kotlin.math.abs
import kotlin.math.pow

/**
 * # El verificador de cifras: lo que hace medible el «sin inventar»
 *
 * El 23-sep, a «¿Por qué Hipotecario 2334 no baja aunque pago la cuota?», Movi AI contestó que «la
 * diferencia es apenas $185.831 al mes, que es lo que baja la deuda». Esa cifra **la calculó el
 * modelo**, y mal: restó cuota menos interés y se olvidó de los $209.219 de seguros. La deuda en
 * realidad CRECE unos $23.388 al mes. Movi ya tenía la cuenta bien hecha; el modelo hizo otra.
 *
 * Una instrucción en la PERSONA («no calcules») baja la frecuencia pero no la vuelve cero, y sin
 * medir no se sabe cuánto la baja. Esto es la otra mitad: **después** de la respuesta, se sacan del
 * texto todas las cifras de plata y los porcentajes y se busca cada una en lo que el modelo tenía
 * delante en ese turno —el contexto, los datos exactos de la pregunta, lo que devolvieron las
 * herramientas, la pregunta misma y la conversación—. Lo que no aparece es una cifra sin respaldo.
 *
 * ## Qué cuenta como respaldo
 *
 * - **La cifra tal cual**, en cualquiera de los formatos de la app y del modelo: el contexto dice
 *   `$204183376` (sin puntos), el modelo escribe `$204.183.376`, la pantalla `$204,2M` o `$2.191M`
 *   (millones con punto de miles), el dólar va como `US$71`, las tasas `15.24 %` o `15,24 %`. Todo
 *   se lleva a un número y se compara con la tolerancia que **la propia cifra dice tener**: `$2,6M`
 *   cubre de $2.550.000 a $2.650.000 porque así redondea quien la escribió; `$2.613.714` no cubre
 *   nada más que un peso para cada lado.
 * - **La suma o la resta de DOS cifras conocidas**, que es lo que un asesor dice legítimamente
 *   («te faltan $X»). Pero no de dos cualquiera: con doscientos números en el contexto hay decenas
 *   de miles de pares, y alguna resta cae cerca de casi cualquier cifra inventada. Por eso se
 *   aceptan solo pares que (a) vivan en el mismo bloque de los datos —el renglón de un crédito, la
 *   sección de un presupuesto, el bloque de hechos de una entidad— o (b) que la propia respuesta
 *   cite, que es justo lo que la PERSONA le pide cuando hace una cuenta: «escribe la operación».
 *
 * ## Qué NO es plata
 *
 * Solo se extrae lo que el texto marca como plata o porcentaje: un `$`, `US$`, `COP`, `USD`,
 * «pesos», «millones», «mil», o un `%`. Los años (2026), los días («el día 5»), la cantidad de
 * cuotas («le quedan 180 cuotas») y las colas de cuenta («el 2334») no llevan marca y no se miran:
 * confundirlos con plata sería reintentar respuestas buenas, que es plata gastada en nada.
 *
 * ## Lo que NO detecta (el límite conocido)
 *
 * Una cuenta **válida con el significado equivocado**. Los $185.831 del 23-sep son exactamente
 * cuota − interés, dos números que están en el mismo renglón del crédito: por números solos, son
 * una resta legítima. Lo que estaba mal era decir que eso «es lo que baja la deuda». Para ese caso
 * particular —el más dañino, porque invierte el diagnóstico— existen las [cifrasTrampa]: la resta
 * que ignora los seguros se declara de antemano como lectura equivocada. Para cualquier otra resta
 * bien hecha y mal leída, la defensa no es este archivo: es el bloque de hechos de la pregunta, que
 * ya trae la cuenta correcta escrita con su nombre para que el modelo no tenga que hacerla.
 */

/** Una cifra que el modelo escribió, ya leída como número. */
internal data class CifraDicha(
    /** Tal como aparece en la respuesta («$2,6M», «15,24 %»): es lo que se le muestra al dueño. */
    val texto: String,
    val valor: Double,
    /** Cuánto puede separarse de un dato y seguir siendo ese dato redondeado. */
    val tolerancia: Double,
    val esPorcentaje: Boolean,
)

/**
 * **La línea que se le agrega al dueño cuando una cifra no se pudo respaldar ni con el reintento.**
 * Corta y honesta: no borra nada de la respuesta —borrar a ciegas podría dejar una frase sin
 * sentido— pero tampoco deja pasar la cifra como si fuera un dato.
 */
internal const val NO_PUDE_VERIFICAR = "No pude verificar estas cifras con tus datos:"

/** Números con separadores: «2.613.714», «15,24», «204183376», «2,6». */
private val NUMERO = Regex("""\d+(?:[.,]\d+)*""")

/** Lo que multiplica una cifra: «$2,6M», «185 mil», «2.191 millones», «1,5 mil millones». */
private val MULTIPLICADOR = Regex("""^\s?(mil millones|millones|millón|millon|MM|M|mil|k)(?![\p{L}\d])""")

/** Lo que dice que un número suelto es plata aunque no traiga el `$` delante. */
private val MONEDA_DESPUES = Regex("""^\s?(pesos|COP|USD|dólares|dolares)(?![\p{L}\d])""", RegexOption.IGNORE_CASE)

private val PORCENTAJE = Regex("""^\s?%""")

/** Miles con punto y, opcional, decimales con coma: la forma de la app y del español de Colombia. */
private val MILES_CON_PUNTO = Regex("""^\d{1,3}(\.\d{3})+(,\d+)?$""")

/** Miles con coma y decimales con punto: como a veces escribe el modelo («$2,613,714»). */
private val MILES_CON_COMA = Regex("""^\d{1,3}(,\d{3})+(\.\d+)?$""")

private val DECIMAL_SUELTO = Regex("""^\d+[.,]\d+$""")

/**
 * Lee un número escrito por una persona (o por el modelo) y dice cuántos decimales mostró, que es
 * lo que fija cuánto pudo haber redondeado. `null` si no es un número.
 *
 * Con [conMultiplicador] la coma es SIEMPRE decimal: «$2,191M» es dos coma uno millones, no dos mil
 * ciento noventa y uno. Sin multiplicador, «2,613,714» tiene grupos de tres y es miles a la inglesa.
 */
internal fun leerNumero(crudo: String, conMultiplicador: Boolean = false): Pair<Double, Int>? {
    val s = crudo.trim()
    return when {
        MILES_CON_PUNTO.matches(s) -> {
            val (entero, decimales) = s.split(',').let { it[0].replace(".", "") to it.getOrNull(1) }
            val valor = (entero + (decimales?.let { ".$it" } ?: "")).toDoubleOrNull() ?: return null
            valor to (decimales?.length ?: 0)
        }
        !conMultiplicador && MILES_CON_COMA.matches(s) -> {
            val (entero, decimales) = s.split('.').let { it[0].replace(",", "") to it.getOrNull(1) }
            val valor = (entero + (decimales?.let { ".$it" } ?: "")).toDoubleOrNull() ?: return null
            valor to (decimales?.length ?: 0)
        }
        DECIMAL_SUELTO.matches(s) -> {
            val normal = s.replace(',', '.')
            (normal.toDoubleOrNull() ?: return null) to normal.substringAfter('.').length
        }
        s.all { it.isDigit() } -> (s.toDoubleOrNull() ?: return null) to 0
        else -> null
    }
}

private fun multiplicadorDe(palabra: String): Double = when (palabra) {
    "mil millones" -> 1e9
    "millones", "millón", "millon", "MM", "M" -> 1e6
    "mil", "k" -> 1e3
    else -> 1.0
}

/**
 * **Las cifras de plata y los porcentajes de un texto.** Todo lo demás —años, días, cuotas, colas
 * de cuenta— se ignora a propósito: ver el KDoc del archivo.
 */
internal fun cifrasDe(texto: String): List<CifraDicha> = NUMERO.findAll(texto).mapNotNull { m ->
    // Ventanas cortas y no el texto entero: el contexto tiene cientos de números, y copiar todo lo
    // anterior y lo posterior a cada uno es cuadrático. Lo que marca una cifra está pegado a ella.
    val antes = texto.substring((m.range.first - 8).coerceAtLeast(0), m.range.first)
    val despues = texto.substring(m.range.last + 1, (m.range.last + 1 + 24).coerceAtMost(texto.length))
    // Un número pegado a letras («T2», «4x1000») no es una cifra que alguien diga.
    if (antes.lastOrNull()?.isLetter() == true && !antes.endsWith("US") && !antes.endsWith("COP")) return@mapNotNull null

    val pegado = antes.trimEnd()
    val signoDePesos = pegado.endsWith("$")
    val monedaAntes = signoDePesos || pegado.endsWith("USD") || pegado.endsWith("COP")
    val porcentaje = PORCENTAJE.find(despues)
    val multiplicador = MULTIPLICADOR.find(despues)
    val monedaDespues = MONEDA_DESPUES.find(despues)
    val esPorcentaje = porcentaje != null
    val esPlata = !esPorcentaje && (monedaAntes || multiplicador != null || monedaDespues != null)
    if (!esPorcentaje && !esPlata) return@mapNotNull null

    val (numero, decimales) = leerNumero(m.value, conMultiplicador = multiplicador != null) ?: return@mapNotNull null
    val factor = multiplicador?.groupValues?.get(1)?.let(::multiplicadorDe) ?: 1.0
    val valor = numero * factor
    val mediaUnidad = 0.5 * 10.0.pow(-decimales) * factor
    val tolerancia = when {
        esPorcentaje -> mediaUnidad + 1e-9
        multiplicador != null -> mediaUnidad
        else -> {
            // «$2.427.900» puede ser «$2.427.883» redondeado; «$2.427.883» no puede ser otra cosa.
            // Los ceros del final dicen cuánto redondeó quien la escribió, con techo en el 1 % para
            // que «$500.000» no respalde cualquier cosa entre $450.000 y $550.000.
            val ceros = if (decimales == 0) m.value.filter { it.isDigit() }.trimStart('0').takeLastWhile { it == '0' }.length else 0
            val porLosCeros = 0.5 * 10.0.pow(ceros)
            maxOf(1.0, minOf(porLosCeros, valor * 0.01))
        }
    }

    // El texto que se le muestra al dueño, con su signo y su multiplicador: «$2,6M», no «2,6».
    val desde = when {
        pegado.endsWith("US$") -> m.range.first - (antes.length - pegado.length) - 3
        signoDePesos -> m.range.first - (antes.length - pegado.length) - 1
        else -> m.range.first
    }.coerceAtLeast(0)
    val hasta = m.range.last + 1 + ((porcentaje ?: multiplicador ?: monedaDespues)?.value?.length ?: 0)
    CifraDicha(
        texto = texto.substring(desde, hasta).trim(),
        valor = valor,
        tolerancia = tolerancia,
        esPorcentaje = esPorcentaje,
    )
}.toList()

/**
 * **Todos los números que el modelo tenía delante**, con todas las lecturas posibles de cada uno.
 *
 * Acá se lee de más a propósito: «15.24» se guarda como 15,24 y como 1.524, «2.613» como 2.613 y
 * como 2,613. Un número conocido de más solo puede hacer que una cifra inventada pase —el error
 * barato—; uno de menos haría reintentar una respuesta correcta.
 *
 * Los bloques (separados por renglones en blanco o por los títulos `==`) guardan aparte sus números
 * de plata, para las sumas y restas: ver el KDoc del archivo.
 */
internal class NumerosConocidos(fuentes: List<String>) {
    private val todos: DoubleArray
    private val porcentajes: DoubleArray
    private val bloques: List<DoubleArray>

    init {
        val valores = mutableListOf<Double>()
        val deLosPorcentajes = mutableListOf<Double>()
        val porBloque = mutableListOf<DoubleArray>()
        fuentes.forEach { fuente ->
            // **Una respuesta vieja marcada no respalda lo que se le marcó.** El teléfono reenvía
            // las respuestas anteriores del asistente, y si una salió con la línea «No pude
            // verificar estas cifras…: $X», su propio cuerpo trae $X: leída como dato, blanquearía
            // en la pregunta siguiente justo la cifra que no tenía respaldo. Así que de esa fuente
            // se descartan las cifras que su línea nombra; el resto de lo que dice sigue valiendo.
            val cuerpo = fuente.substringBefore(NO_PUDE_VERIFICAR)
            val marcadas = cifrasDe(fuente.substringAfter(NO_PUDE_VERIFICAR, missingDelimiterValue = ""))
            val noMarcada = { v: Double -> marcadas.none { abs(abs(it.valor) - abs(v)) <= it.tolerancia } }
            bloquesDe(cuerpo).forEach { bloque ->
                val delBloque = mutableListOf<Double>()
                NUMERO.findAll(bloque).forEach { m -> valores += lecturasDe(m.value).filter(noMarcada) }
                cifrasDe(bloque).filter { noMarcada(it.valor) }.forEach { cifra ->
                    if (cifra.esPorcentaje) {
                        deLosPorcentajes += cifra.valor
                    } else {
                        valores += cifra.valor
                        delBloque += cifra.valor
                    }
                }
                // Los números sin `$` también son plata en los datos: el contexto dice «debe
                // 204183376» y las herramientas «Comida: 1161535». Para las sumas cuentan los que
                // tienen pinta de plata; los días y los plazos no.
                NUMERO.findAll(bloque).forEach { m ->
                    leerNumero(m.value)?.first?.takeIf { it >= MINIMO_PARA_SUMAR && noMarcada(it) }?.let { delBloque += it }
                }
                if (delBloque.size >= 2) porBloque += delBloque.distinct().toDoubleArray()
            }
        }
        todos = valores.map { abs(it) }.distinct().sorted().toDoubleArray()
        porcentajes = deLosPorcentajes.map { abs(it) }.distinct().sorted().toDoubleArray()
        bloques = porBloque
    }

    /** ¿Hay un número conocido a no más de [tolerancia] de [valor]? */
    fun contiene(valor: Double, tolerancia: Double): Boolean = hayCerca(todos, valor, tolerancia)

    /**
     * ¿Hay un **porcentaje** de los datos a no más de [tolerancia]? Solo los que los datos marcan
     * con `%`: si no, cualquier «12 %» inventado quedaría respaldado por un plazo de 12 meses.
     */
    fun contienePorcentaje(valor: Double, tolerancia: Double): Boolean = hayCerca(porcentajes, valor, tolerancia)

    private fun hayCerca(ordenados: DoubleArray, valor: Double, tolerancia: Double): Boolean {
        val objetivo = abs(valor)
        var lo = 0
        var hi = ordenados.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (ordenados[mid] < objetivo - tolerancia) lo = mid + 1 else hi = mid - 1
        }
        return lo < ordenados.size && ordenados[lo] <= objetivo + tolerancia
    }

    /** ¿Es la suma o la resta de dos números de un mismo bloque de los datos? */
    fun esCuentaDeUnBloque(valor: Double, tolerancia: Double): Boolean =
        bloques.any { esSumaORestaDeDos(abs(valor), tolerancia, it) }

    private companion object {
        /**
         * Por debajo de esto un número de los datos casi nunca es plata: es un día, un plazo, una
         * cantidad. Sumarlos a las cuentas solo agregaría ruido («$2.613.714 + 5»).
         */
        const val MINIMO_PARA_SUMAR = 1_000.0
    }
}

private fun lecturasDe(crudo: String): List<Double> = buildList {
    crudo.filter { it.isDigit() }.toDoubleOrNull()?.let(::add)
    leerNumero(crudo)?.first?.let(::add)
    leerNumero(crudo, conMultiplicador = true)?.first?.let(::add)
    if (crudo.count { it == '.' || it == ',' } == 1) crudo.replace(',', '.').toDoubleOrNull()?.let(::add)
}

private fun bloquesDe(fuente: String): List<String> {
    val bloques = mutableListOf<StringBuilder>(StringBuilder())
    fuente.lineSequence().forEach { linea ->
        if (linea.isBlank() || linea.trimStart().startsWith("==")) bloques += StringBuilder()
        bloques.last().appendLine(linea)
    }
    return bloques.map { it.toString() }.filter { it.isNotBlank() }
}

private fun esSumaORestaDeDos(valor: Double, tolerancia: Double, numeros: DoubleArray): Boolean {
    for (i in numeros.indices) {
        for (j in i + 1 until numeros.size) {
            val a = abs(numeros[i])
            val b = abs(numeros[j])
            if (abs(a + b - valor) <= tolerancia || abs(abs(a - b) - valor) <= tolerancia) return true
        }
    }
    return false
}

/**
 * **Las cifras de [respuesta] que no tienen respaldo en [fuentes]**, tal como las escribió el
 * modelo y sin repetir. Vacío es el caso normal —y en ese caso no se hace nada más, ni una llamada—.
 *
 * [trampas] son cifras que se sabe de antemano que son una lectura equivocada de los datos (ver
 * [cifrasTrampa]): si una cifra cae en una y no está literalmente en los datos, no la salva ninguna
 * suma ni resta.
 *
 * Idempotente: la línea de [NO_PUDE_VERIFICAR] que se agrega al final no se vuelve a verificar, así
 * que pasar dos veces la misma respuesta da lo mismo.
 */
internal fun cifrasSinRespaldo(
    respuesta: String,
    fuentes: List<String>,
    trampas: Map<Long, String> = emptyMap(),
): List<String> {
    val cuerpo = respuesta.substringBefore(NO_PUDE_VERIFICAR)
    val dichas = cifrasDe(cuerpo)
    if (dichas.isEmpty()) return emptyList()
    val conocidos = NumerosConocidos(fuentes)

    val directas = dichas.filter {
        it.valor == 0.0 ||
            (if (it.esPorcentaje) conocidos.contienePorcentaje(it.valor, it.tolerancia) else conocidos.contiene(it.valor, it.tolerancia))
    }
    // Las que la respuesta misma cita y están respaldadas: una cuenta hecha a la vista con dos de
    // ellas («$2.613.714 − $209.219 = $2.404.495») se puede comprobar sin adivinar.
    val citadas = directas.filterNot { it.esPorcentaje }.map { abs(it.valor) }.distinct().toDoubleArray()

    return dichas.asSequence()
        .filterNot { it in directas }
        .filter { cifra ->
            if (cifra.esPorcentaje) return@filter true
            val caeEnUnaTrampa = trampas.keys.any { abs(it - abs(cifra.valor)) <= cifra.tolerancia }
            if (caeEnUnaTrampa) return@filter true
            !conocidos.esCuentaDeUnBloque(cifra.valor, cifra.tolerancia) &&
                !esSumaORestaDeDos(abs(cifra.valor), cifra.tolerancia, citadas)
        }
        .map { it.texto }
        .distinct()
        .toList()
}

/** La respuesta con la línea honesta al final, una sola vez. */
internal fun conLineaHonesta(respuesta: String, sinRespaldo: List<String>): String =
    if (sinRespaldo.isEmpty() || NO_PUDE_VERIFICAR in respuesta) respuesta
    else respuesta.trimEnd() + "\n\n" + NO_PUDE_VERIFICAR + " " + sinRespaldo.joinToString(", ") + "."

/**
 * **Lo que se le dice al modelo en el único reintento.** Va como un turno del usuario, así que
 * está en tuteo como todo lo demás; y le prohíbe mencionar la revisión porque el dueño no escribió
 * esto y una respuesta que empiece con «tienes razón, me equivoqué» lo desconcertaría.
 */
internal fun mensajeDeCorreccion(sinRespaldo: List<String>): String =
    "Revisión automática de Movi (esto no lo escribió el usuario): estas cifras de tu respuesta no " +
        "están en los datos ni salen de sumar o restar dos de ellos: ${sinRespaldo.joinToString(", ")}. " +
        "Reescribe la respuesta completa usando solo cifras que aparezcan en los datos (DATOS DEL " +
        "USUARIO, los DATOS EXACTOS PARA ESTA PREGUNTA o lo que devolvieron las herramientas). Si una " +
        "cifra que necesitas no está, di que no la sabes con estos datos. No menciones esta revisión."
