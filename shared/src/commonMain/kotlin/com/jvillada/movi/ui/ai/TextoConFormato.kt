package com.jvillada.movi.ui.ai

/**
 * # Lo que contesta Movi AI, con formato (vuelta de diseño 12)
 *
 * Las respuestas llegan de dos lados y las dos escriben con un Markdown mínimo:
 *
 * - **Las tres sin IA** (`LasPreguntasSugeridas.kt` en el server): párrafos separados por un renglón
 *   vacío, listas con «- » y numeradas con «1. », y una lista pegada a la frase que la presenta
 *   («Te faltan 3 pagos del período:\n- Arriendo $1.800.000 · venció hace 22 días»).
 * - **El modelo**: lo mismo, más **negrita**, a veces «* » o «• » como viñeta, una sublista con
 *   sangría y algún «## Título».
 *
 * Antes se pintaba todo como un solo texto: los guiones crudos, los renglones vacíos como huecos y
 * las cifras en la letra del texto. Esto lo parte en bloques que [TextoDelAsistente] pinta con el
 * sistema: párrafos, listas con viñeta o número y sangría, negrita y las cifras en pesos en la
 * letra de las cifras.
 *
 * ### Un subconjunto chico, a propósito
 *
 * Párrafos, listas (con viñetas y numeradas, dos niveles), **negrita** y un título «#» que se pinta
 * como un párrafo en negrita. **Nada de HTML, enlaces ni imágenes**: «<b>», «[texto](url)» y
 * «![foto](url)» se ven tal cual, como texto. Lo que no se entiende —un «**» sin cerrar, un «*» suelto,
 * una tabla— se ve tal cual y nunca se rompe: el peor caso es el texto de antes.
 *
 * La función es pura (sin Compose) para probarla con textos sueltos: `TextoConFormatoTest`.
 */

/** Un pedazo de un renglón: su texto, si va en negrita y si es una cifra en pesos. */
data class Tramo(val texto: String, val negrita: Boolean = false, val cifra: Boolean = false)

/** Un bloque de la respuesta. */
sealed interface BloqueDeTexto {
    /**
     * Un párrafo. Sus renglones van separados por «\n» dentro de [tramos] (un salto simple de la
     * respuesta se respeta: el server escribe así las líneas que van juntas).
     */
    data class Parrafo(val tramos: List<Tramo>) : BloqueDeTexto

    /** Una lista, con viñetas o numerada. */
    data class Lista(val numerada: Boolean, val items: List<ItemDeLista>) : BloqueDeTexto
}

/**
 * Un ítem de una lista. [marca] es lo que va a la izquierda: «•» o el número como vino («1.»,
 * «2.»). [nivel] es 0, o 1 para una sublista (con sangría en la respuesta).
 */
data class ItemDeLista(val marca: String, val tramos: List<Tramo>, val nivel: Int = 0)

/** La viñeta de una lista sin números. */
const val VINETA: String = "•"

private val ITEM_CON_VINETA = Regex("""^( *)[-*•]\s+(.*\S.*)$""")
private val ITEM_NUMERADO = Regex("""^( *)(\d{1,3})[.)]\s+(.*\S.*)$""")
private val TITULO = Regex("""^#{1,6}\s+(.*\S.*)$""")

/**
 * **Una cifra en pesos**: «$1.800.000», «$27.500», «US$1.200», «-$23.388», «$11,7M». Solo con el
 * signo pesos delante y los miles con punto, para no confundir un año o un día con plata.
 */
private val CIFRA_EN_PESOS = Regex("""[-−]?(?:US)?\$\s?(?:\d{1,3}(?:\.\d{3})+|\d+)(?:,\d+)?(?:M\b)?""")

/** Desde cuántos espacios de sangría un ítem es de una sublista. */
private const val SANGRIA_DE_UNA_SUBLISTA = 2

/** **Parte [texto] en bloques.** Pura. Un texto vacío da una lista vacía. */
fun bloquesDeTexto(texto: String): List<BloqueDeTexto> {
    val bloques = mutableListOf<BloqueDeTexto>()
    val parrafo = mutableListOf<String>()
    // Los ítems se juntan crudos (marca, texto, nivel) y se parten en tramos al cerrar la lista: un
    // renglón que continúa un ítem se suma a su texto antes de buscar la negrita.
    var lista: MutableList<Triple<String, String, Int>>? = null
    var listaNumerada = false

    fun cerrarParrafo() {
        if (parrafo.isNotEmpty()) {
            bloques += BloqueDeTexto.Parrafo(tramosDe(parrafo.joinToString("\n")))
            parrafo.clear()
        }
    }

    fun cerrarLista() {
        lista?.let { items ->
            if (items.isNotEmpty()) {
                bloques += BloqueDeTexto.Lista(listaNumerada, items.map { (marca, crudo, nivel) -> ItemDeLista(marca, tramosDe(crudo), nivel) })
            }
        }
        lista = null
    }

    fun agregarItem(numerada: Boolean, marca: String, crudo: String, nivel: Int) {
        cerrarParrafo()
        // Una lista con viñetas pegada a una numerada es otra lista; una sublista no corta la de
        // arriba (un «1.» con sus «-» debajo sigue siendo la misma lista numerada).
        if (lista == null || (nivel == 0 && listaNumerada != numerada)) {
            cerrarLista()
            lista = mutableListOf()
            listaNumerada = numerada
        }
        lista!!.add(Triple(marca, crudo, nivel))
    }

    for (crudo in texto.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
        val renglon = crudo.replace('\t', ' ').trimEnd()
        if (renglon.isBlank()) {
            cerrarParrafo()
            cerrarLista()
            continue
        }
        val conVineta = ITEM_CON_VINETA.matchEntire(renglon)
        val numerado = ITEM_NUMERADO.matchEntire(renglon)
        val titulo = TITULO.matchEntire(renglon.trimStart())
        when {
            // «**Hoy**» al principio de un renglón no es una viñeta: «*» seguido de otro «*».
            conVineta != null && !renglon.trimStart().startsWith("**") -> {
                val nivel = if (conVineta.groupValues[1].length >= SANGRIA_DE_UNA_SUBLISTA) 1 else 0
                agregarItem(numerada = false, VINETA, conVineta.groupValues[2].trim(), nivel)
            }
            numerado != null -> {
                val nivel = if (numerado.groupValues[1].length >= SANGRIA_DE_UNA_SUBLISTA) 1 else 0
                agregarItem(numerada = true, "${numerado.groupValues[2]}.", numerado.groupValues[3].trim(), nivel)
            }
            titulo != null -> {
                cerrarParrafo()
                cerrarLista()
                bloques += BloqueDeTexto.Parrafo(tramosDe(titulo.groupValues[1].trim()).map { it.copy(negrita = true) })
            }
            // Un renglón con sangría debajo de un ítem es la continuación de ese ítem.
            lista != null && crudo.startsWith(" ") -> {
                val items = lista!!
                val (marca, anterior, nivel) = items.last()
                items[items.lastIndex] = Triple(marca, anterior + " " + renglon.trim(), nivel)
            }
            else -> {
                cerrarLista()
                parrafo += renglon.trim()
            }
        }
    }
    cerrarParrafo()
    cerrarLista()
    return bloques
}

/**
 * **Los tramos de un renglón**: la negrita entre «**», y dentro de cada pedazo las cifras en pesos.
 * Un «**» sin pareja, o una pareja que encierra nada o empieza/termina con espacio, queda como texto.
 */
fun tramosDe(texto: String): List<Tramo> {
    val tramos = mutableListOf<Tramo>()
    var i = 0
    val normal = StringBuilder()
    while (i < texto.length) {
        val abre = texto.indexOf("**", i)
        if (abre < 0) break
        val cierra = texto.indexOf("**", abre + 2)
        if (cierra < 0) break
        val adentro = texto.substring(abre + 2, cierra)
        if (adentro.isBlank() || adentro.first().isWhitespace() || adentro.last().isWhitespace()) {
            // No es negrita: el «**» de apertura queda como texto y se sigue buscando después de él.
            normal.append(texto, i, abre + 2)
            i = abre + 2
            continue
        }
        normal.append(texto, i, abre)
        tramos += conCifras(normal.toString(), negrita = false)
        normal.clear()
        tramos += conCifras(adentro, negrita = true)
        i = cierra + 2
    }
    normal.append(texto.substring(i))
    tramos += conCifras(normal.toString(), negrita = false)
    return tramos.filter { it.texto.isNotEmpty() }
}

/** Parte [texto] en lo que es cifra en pesos y lo que no. */
private fun conCifras(texto: String, negrita: Boolean): List<Tramo> {
    if (texto.isEmpty()) return emptyList()
    val tramos = mutableListOf<Tramo>()
    var desde = 0
    for (m in CIFRA_EN_PESOS.findAll(texto)) {
        if (m.range.first > desde) tramos += Tramo(texto.substring(desde, m.range.first), negrita)
        tramos += Tramo(m.value, negrita, cifra = true)
        desde = m.range.last + 1
    }
    if (desde < texto.length) tramos += Tramo(texto.substring(desde), negrita)
    return tramos
}

/** El texto plano de unos tramos (sin marcas de formato). */
fun textoDe(tramos: List<Tramo>): String = tramos.joinToString("") { it.texto }
