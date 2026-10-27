package com.jvillada.movi.shared.model

/*
 * # El nombre de un comercio, como se lee en pantalla (vuelta de diseño 4)
 *
 * El banco escribe el comercio en MAYÚSCULAS y con la pasarela pegada: «RAPPI», «EXITO SUBA»,
 * «PAYU*NETFLIX», «UBER *TRIP». La tarjeta de un aviso lo mostraba tal cual, gritando. Esto lo
 * convierte en un nombre que se lee —«Rappi», «Éxito Suba», «Netflix», «Uber»— **solo para
 * mostrarlo**: lo que se guarda, las huellas y la memoria de categorías siguen con el texto crudo.
 */

/** Palabras que, después de la marca, no dicen nada: dominios, el «TRIP» de Uber, la razón social. */
private val PALABRAS_QUE_NO_APORTAN: Set<String> = setOf(
    "com", "co", "net", "org", "www", "http", "https", "trip", "bill", "help", "sas", "ltda", "inc", "llc",
)

/** Las que van en minúscula en medio de un nombre («Droguería de La 80»). «La» y «El» no: suelen ser nombre. */
private val CONECTORES: Set<String> = setOf("de", "del", "y", "e", "en", "a", "por", "para", "con")

private const val VOCALES = "aeiouáéíóúü"

/** Lo largo de un código de referencia («A1B2C3», «123456»): de ahí para arriba no es parte del nombre. */
private const val LARGO_DE_UNA_REFERENCIA = 6

/**
 * **El comercio que leyó Movi, para mostrarlo.**
 *
 * - Si el catálogo de [MarcasConocidas][COMERCIOS_CONOCIDOS] reconoce la marca, su nombre bonito
 *   («Rappi», «Éxito») y, detrás, lo que sigue a la marca si aporta («EXITO SUBA» → «Éxito Suba»,
 *   «TIENDA D1 CALLE 80» → «D1 Calle 80»). Lo de antes de la marca es la pasarela o un genérico
 *   («PAYU*NETFLIX» → «Netflix»), y lo de después que no dice nada se cae («UBER *TRIP» → «Uber»).
 * - Si no, el texto en «Tipo Título», respetando las siglas de 2 o 3 letras sin vocales («CRV»), lo
 *   que lleva números («D1», «80») y los conectores en minúscula.
 * - Un texto que ya viene con mayúsculas y minúsculas lo escribió alguien (el dueño, o Movi al
 *   nombrar un tercero): se deja como está.
 *
 * Pura y solo para mostrar: **nunca** para guardar ni para comparar.
 */
fun nombreParaMostrar(crudo: String): String {
    val limpio = crudo.replace('\u00A0', ' ').trim().replace(Regex("\\s+"), " ")
    if (limpio.isEmpty()) return limpio
    val letras = limpio.filter { it.isLetter() }
    if (letras.any { it.isUpperCase() } && letras.any { it.isLowerCase() }) return limpio

    val palabras = palabrasOriginales(limpio)
    val coincidencia = coincidenciaEnElTexto(limpio)
    // Las palabras originales y las del catálogo se parten con la misma regla: los índices casan.
    if (coincidencia != null && palabras.size == palabrasDeMarca(limpio).size) {
        val loQueSigue = palabras.drop(coincidencia.hasta)
            .filter { aporta(it) }
            .map { enTitulo(it, primera = false) }
        return (listOf(coincidencia.marca.nombre) + loQueSigue).joinToString(" ")
    }
    return enTituloConservandoSeparadores(limpio)
}

/** Si una palabra de detrás de la marca dice algo: ni dominio, ni «TRIP», ni un código de referencia. */
private fun aporta(palabra: String): Boolean {
    val minuscula = palabra.lowercase()
    if (minuscula in PALABRAS_QUE_NO_APORTAN) return false
    val esReferencia = palabra.length >= LARGO_DE_UNA_REFERENCIA && palabra.any { it.isDigit() }
    return !esReferencia
}

/**
 * Las palabras del texto **con sus letras originales**, partidas igual que [palabrasDeMarca]: los
 * apóstrofos se saltan y todo lo que no es letra ni dígito separa.
 */
private fun palabrasOriginales(texto: String): List<String> {
    val palabras = mutableListOf<String>()
    val actual = StringBuilder()
    for (c in texto) {
        when {
            c == '\'' || c == '’' || c == '´' || c == '`' -> Unit
            c.lowercaseChar().isLetterOrDigit() -> actual.append(c)
            else -> if (actual.isNotEmpty()) { palabras += actual.toString(); actual.clear() }
        }
    }
    if (actual.isNotEmpty()) palabras += actual.toString()
    return palabras
}

/** Una palabra en «Tipo Título»: siglas y números en mayúscula, conectores en minúscula. */
private fun enTitulo(palabra: String, primera: Boolean): String {
    val minuscula = palabra.lowercase()
    return when {
        palabra.any { it.isDigit() } -> palabra.uppercase()
        palabra.length in 2..3 && palabra.all { it.isLetter() } && minuscula.none { it in VOCALES } -> palabra.uppercase()
        !primera && minuscula in CONECTORES -> minuscula
        else -> minuscula.replaceFirstChar { it.uppercaseChar() }
    }
}

/**
 * Sin marca, cada palabra en «Tipo Título» y lo demás como estaba —salvo el `*` y el `_` de la
 * pasarela, que pasan a ser un espacio—: «PANADERIA EL TRIGAL S.A.» → «Panaderia El Trigal S.A.».
 */
private fun enTituloConservandoSeparadores(texto: String): String {
    val salida = StringBuilder()
    val palabra = StringBuilder()
    var primera = true
    fun cerrarPalabra(siguiente: Char? = null) {
        if (palabra.isEmpty()) return
        // Una letra sola antes de un punto es una inicial («S.A.»), no el conector «a».
        val inicial = palabra.length == 1 && siguiente == '.'
        salida.append(if (inicial) palabra.toString().uppercase() else enTitulo(palabra.toString(), primera))
        primera = false
        palabra.clear()
    }
    for (c in texto) {
        if (c.isLetterOrDigit() || c == '\'' || c == '’') {
            palabra.append(c)
        } else {
            cerrarPalabra(siguiente = c)
            salida.append(if (c == '*' || c == '_') ' ' else c)
        }
    }
    cerrarPalabra()
    return salida.toString().replace(Regex("\\s+"), " ").trim()
}
