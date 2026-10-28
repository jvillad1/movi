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

/**
 * Las siglas que llevan vocal y no se reconocerían por la regla de «sin vocales» («DIAN», «SOAT»):
 * en minúscula, sin tildes, como las da [palabrasDeMarca]. Las de una marca del catálogo («EPM»,
 * «ETB», «KFC») no hace falta ponerlas acá: salen de [ESCRITURA_DE_LAS_MARCAS].
 */
private val SIGLAS_CONOCIDAS: Set<String> = setOf(
    "dian", "soat", "iva", "gmf", "pse", "eps", "ips", "icbf", "sena", "runt", "cdt", "afc", "ach", "atm",
    "nit", "eds", "sas",
)

/**
 * **Cómo escribe el catálogo cada palabra de sus marcas**, para devolverle a un texto en mayúsculas las
 * tildes y las mayúsculas de la marca: «EXITO» → «Éxito», «OLIMPICA» → «Olímpica», «MCDONALDS» →
 * «McDonald's», «EPM» → «EPM», «ITAU» → «Itaú». Palabra por palabra de [Marca.nombre] (solo las que
 * no van todas en minúscula, como el «de» de «Metro de Medellín», y sin signos: «Disney+» no le pone
 * el «+» a un «DISNEY»), con la llave como la da [palabrasDeMarca]. La primera marca del catálogo gana.
 */
private val ESCRITURA_DE_LAS_MARCAS: Map<String, String> = buildMap {
    for (marca in MARCAS_CONOCIDAS) {
        for (palabra in marca.nombre.split(' ')) {
            if (palabra.length < 2 || palabra == palabra.lowercase()) continue
            if (!palabra.all { it.isLetterOrDigit() || it == '\'' || it == '’' }) continue
            val llave = palabrasDeMarca(palabra).singleOrNull() ?: continue
            if (llave !in this) put(llave, palabra)
        }
    }
}

/** Una palabra en «Tipo Título»: siglas y números en mayúscula, conectores en minúscula. */
private fun enTitulo(palabra: String, primera: Boolean): String {
    val minuscula = palabra.lowercase()
    val llave = palabrasDeMarca(palabra).singleOrNull()
    return when {
        palabra.any { it.isDigit() } -> palabra.uppercase()
        llave != null && llave in ESCRITURA_DE_LAS_MARCAS -> ESCRITURA_DE_LAS_MARCAS.getValue(llave)
        palabra.length in 2..4 && palabra.all { it.isLetter() } && minuscula.none { it in VOCALES } -> palabra.uppercase()
        llave != null && llave in SIGLAS_CONOCIDAS -> palabra.uppercase()
        !primera && minuscula in CONECTORES -> minuscula
        else -> minuscula.replaceFirstChar { it.uppercaseChar() }
    }
}

/**
 * **Lo que el banco escribió en MAYÚSCULAS, en forma de título** (vuelta de diseño 32): «EXITO SUBA» →
 * «Éxito Suba», «PAGO EPM» → «Pago EPM», «DIAN» → «DIAN», «TIENDA D1 CALLE 80» → «Tienda D1 Calle 80».
 * La descripción de un movimiento que llegó del banco se veía así, gritando, en Movimientos, en su
 * detalle, en Buscar y en el detalle de la cuenta.
 *
 * A diferencia de [nombreParaMostrar] **no quita nada** —ni la pasarela ni lo que sigue a la marca—:
 * es la descripción entera, solo que escrita como se lee. Es la misma regla de las palabras (siglas de
 * 2 a 4 letras sin vocales o conocidas, números, conectores en minúscula y la escritura de las marcas
 * del catálogo, con sus tildes).
 *
 * - Un texto con alguna minúscula lo escribió alguien (el dueño, Movi al nombrar un tercero): **se
 *   devuelve tal cual**.
 * - Sin letras («123456»), tal cual.
 *
 * Pura y solo para mostrar: **nunca** para guardar ni para comparar.
 */
fun enFormaDeTitulo(texto: String): String {
    val letras = texto.filter { it.isLetter() }
    if (letras.isEmpty() || letras.any { it.isLowerCase() }) return texto
    val limpio = texto.replace('\u00A0', ' ').trim().replace(Regex("\\s+"), " ")
    return enTituloConservandoSeparadores(limpio)
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
