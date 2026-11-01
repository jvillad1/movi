package com.jvillada.movi.shared.model

/**
 * # Las marcas que Movi reconoce, sin preguntarle a nadie
 *
 * Revolut y Monzo ponen el logo del comercio en cada fila, y eso hace que una lista de movimientos se
 * lea de un vistazo: el amarillo del Éxito se ve antes que la palabra. Ellos lo resuelven pidiéndole
 * el logo a un servicio externo con el nombre del comercio. **Movi no**: el dueño decidió (22-oct-2026)
 * que el nombre de un comercio nunca sale del teléfono, así que el catálogo vive acá, escrito a mano.
 *
 * Y sin archivos de imagen de terceros: nada se descarga. Cada marca es un **logotipo tipográfico**
 * —un círculo con su color y una marca corta («é», «D1», «N»)— que es lo que se puede dibujar sin bajar
 * nada. Los colores son aproximados a los de cada marca y **siempre** pasan contraste AA (4,5:1)
 * texto contra fondo: lo vigila `MarcasConocidasTest`.
 *
 * ## Cómo se reconoce una marca
 *
 * El texto (el `merchant`, la descripción, el nombre de una cuenta) se parte en **palabras
 * completas**, sin tildes ni mayúsculas ni apóstrofos ([palabrasDeMarca]). Un patrón es una o varias
 * palabras seguidas: «ara» casa con «Tiendas ARA» pero no con «Para el almuerzo», y «claro» no casa
 * con «Aclaro el pago». Nada de subcadenas: equivocarse hacia «sí, es el Éxito» pinta una fila con la
 * marca equivocada, y eso es peor que no pintar ninguna.
 *
 * ## Cuando casan dos
 *
 * - **Gana el patrón que contiene al otro.** «Rappi Pay» es Rappipay y no Rappi, «Banco Falabella» es
 *   el banco y no la tienda: la coincidencia más corta queda adentro de la larga y se descarta.
 * - **Si no, gana el orden del catálogo.** «GOOGLE *YOUTUBE» es YouTube (va antes que Google, que es
 *   más bien la pasarela), «Fiducuenta Bancolombia» es la Fiducuenta, y en un texto los comercios van
 *   antes que los bancos: «Compra en EXITO con tu tarjeta Bancolombia» es el Éxito.
 *
 * ## Los bancos, aparte
 *
 * Un banco tiene dos juegos de patrones. Los de [Marca.patrones] son seguros en cualquier texto
 * («bancolombia», «nequi»). Los de [Marca.patronesDeCuenta] son palabras que solo significan el banco
 * cuando se está nombrando una cuenta o el origen de un aviso: «Nu», «Visa», «Porvenir» o «Protección»
 * en la descripción de un gasto pueden ser cualquier cosa; en el nombre de una cuenta, no.
 */
data class Marca(
    /** Estable y en ASCII: se puede guardar o comparar sin pensar en tildes. */
    val id: String,
    /** Cómo se dice en pantalla y lo que lee el lector de pantalla. */
    val nombre: String,
    /** El logotipo tipográfico: de 1 a 3 caracteres. */
    val corta: String,
    /** Color del círculo, `0xAARRGGBB`. */
    val fondo: Long,
    /** Color de [corta] sobre [fondo], `0xAARRGGBB`. Pasa AA contra él. */
    val texto: Long,
    val rubro: RubroDeMarca,
    /** Palabras que la reconocen en cualquier texto. Ver el KDoc del archivo. */
    val patrones: List<String>,
    /** Palabras que la reconocen solo en el nombre de una cuenta o en el origen de un aviso. */
    val patronesDeCuenta: List<String> = emptyList(),
)

/** El rubro de una marca. Solo informativo: no decide nada del reconocimiento. */
enum class RubroDeMarca {
    MERCADO, DOMICILIOS_Y_TRANSPORTE, COMBUSTIBLE, SUSCRIPCIONES, TELEFONIA, RESTAURANTES_Y_CAFE,
    SALUD, ROPA_Y_TIENDAS, GIMNASIO, CINE, BANCOS_Y_BILLETERAS,
}

private const val OSCURO = 0xFF1A1A1AL
private const val BLANCO = 0xFFFFFFFFL

private fun comercio(
    id: String, nombre: String, corta: String, fondo: Long, texto: Long, rubro: RubroDeMarca, vararg patrones: String,
) = Marca(id, nombre, corta, fondo, texto, rubro, patrones.toList())

private fun banco(
    id: String, nombre: String, corta: String, fondo: Long, texto: Long,
    patrones: List<String>, patronesDeCuenta: List<String> = emptyList(),
) = Marca(id, nombre, corta, fondo, texto, RubroDeMarca.BANCOS_Y_BILLETERAS, patrones, patronesDeCuenta)

/**
 * Los comercios, **en orden de prioridad** (ver el KDoc del archivo): dentro de un mismo rubro, lo más
 * específico antes («Prime Video» antes que Amazon, YouTube antes que Google).
 */
val COMERCIOS_CONOCIDOS: List<Marca> = listOf(
    // ── Mercado
    comercio("exito", "Éxito", "é", 0xFFFFE600L, OSCURO, RubroDeMarca.MERCADO, "exito", "almacenes exito"),
    comercio("carulla", "Carulla", "C", 0xFF00693EL, BLANCO, RubroDeMarca.MERCADO, "carulla"),
    comercio("d1", "D1", "D1", 0xFFE30613L, BLANCO, RubroDeMarca.MERCADO, "d1", "tiendas d1"),
    comercio("ara", "Ara", "ara", 0xFFD52B1EL, BLANCO, RubroDeMarca.MERCADO, "ara", "tiendas ara"),
    comercio("olimpica", "Olímpica", "O", 0xFFC8102EL, BLANCO, RubroDeMarca.MERCADO, "olimpica", "supertiendas olimpica"),
    comercio("jumbo", "Jumbo", "J", 0xFF007A3DL, BLANCO, RubroDeMarca.MERCADO, "jumbo"),
    comercio("makro", "Makro", "M", 0xFFFFDD00L, OSCURO, RubroDeMarca.MERCADO, "makro"),
    comercio("pricesmart", "PriceSmart", "PS", 0xFF00529BL, BLANCO, RubroDeMarca.MERCADO, "pricesmart", "price smart"),
    comercio("isimo", "Isimo", "i", 0xFF00813FL, BLANCO, RubroDeMarca.MERCADO, "isimo"),
    comercio("surtimax", "Surtimax", "S", 0xFFD4002AL, BLANCO, RubroDeMarca.MERCADO, "surtimax"),
    // ── Domicilios y transporte
    comercio("rappi", "Rappi", "R", 0xFFFF441FL, OSCURO, RubroDeMarca.DOMICILIOS_Y_TRANSPORTE, "rappi"),
    comercio("ifood", "iFood", "iF", 0xFFD2111EL, BLANCO, RubroDeMarca.DOMICILIOS_Y_TRANSPORTE, "ifood"),
    comercio("didi", "DiDi", "D", 0xFFFC4C02L, OSCURO, RubroDeMarca.DOMICILIOS_Y_TRANSPORTE, "didi", "didi food"),
    comercio("uber", "Uber", "U", 0xFF000000L, BLANCO, RubroDeMarca.DOMICILIOS_Y_TRANSPORTE, "uber", "uber eats", "ubereats"),
    comercio("cabify", "Cabify", "C", 0xFF7145D6L, BLANCO, RubroDeMarca.DOMICILIOS_Y_TRANSPORTE, "cabify"),
    comercio("indrive", "inDrive", "iD", 0xFFC1F11DL, OSCURO, RubroDeMarca.DOMICILIOS_Y_TRANSPORTE, "indrive", "indriver"),
    comercio(
        "metro_medellin", "Metro de Medellín", "M", 0xFF0072BCL, BLANCO, RubroDeMarca.DOMICILIOS_Y_TRANSPORTE,
        "metro de medellin", "metro medellin",
    ),
    comercio(
        "transmilenio", "TransMilenio", "TM", 0xFFC8102EL, BLANCO, RubroDeMarca.DOMICILIOS_Y_TRANSPORTE,
        "transmilenio", "tullave", "tu llave",
    ),
    // ── Combustible
    comercio("terpel", "Terpel", "T", 0xFFD7001AL, BLANCO, RubroDeMarca.COMBUSTIBLE, "terpel"),
    comercio("primax", "Primax", "P", 0xFF003C82L, BLANCO, RubroDeMarca.COMBUSTIBLE, "primax"),
    comercio("biomax", "Biomax", "B", 0xFF007A3DL, BLANCO, RubroDeMarca.COMBUSTIBLE, "biomax"),
    comercio("texaco", "Texaco", "T", 0xFFC4161CL, BLANCO, RubroDeMarca.COMBUSTIBLE, "texaco"),
    // ── Suscripciones (Apple y Google al final: muchas veces son la pasarela de otra cosa)
    comercio("netflix", "Netflix", "N", 0xFF000000L, 0xFFF40612L, RubroDeMarca.SUSCRIPCIONES, "netflix"),
    comercio("spotify", "Spotify", "S", 0xFF1DB954L, OSCURO, RubroDeMarca.SUSCRIPCIONES, "spotify"),
    comercio("disney", "Disney+", "D+", 0xFF113CCFL, BLANCO, RubroDeMarca.SUSCRIPCIONES, "disney", "disney plus", "disneyplus"),
    comercio("hbo_max", "Max", "max", 0xFF002BE7L, BLANCO, RubroDeMarca.SUSCRIPCIONES, "hbo", "hbo max", "hbomax", "max com"),
    comercio(
        "prime_video", "Prime Video", "pv", 0xFF00A8E1L, OSCURO, RubroDeMarca.SUSCRIPCIONES,
        "prime video", "primevideo", "amazon prime",
    ),
    comercio("youtube", "YouTube", "YT", 0xFFD90000L, BLANCO, RubroDeMarca.SUSCRIPCIONES, "youtube", "youtube premium"),
    comercio("openai", "ChatGPT", "AI", 0xFF000000L, BLANCO, RubroDeMarca.SUSCRIPCIONES, "openai", "chatgpt", "open ai"),
    comercio("claude", "Claude", "C", 0xFFD97757L, OSCURO, RubroDeMarca.SUSCRIPCIONES, "anthropic", "claude", "claude ai"),
    comercio("microsoft", "Microsoft", "MS", 0xFF0067B8L, BLANCO, RubroDeMarca.SUSCRIPCIONES, "microsoft", "msft", "xbox"),
    comercio("apple", "Apple", "A", 0xFF000000L, BLANCO, RubroDeMarca.SUSCRIPCIONES, "apple", "apple com", "itunes", "icloud"),
    comercio("google", "Google", "G", 0xFFFFFFFFL, 0xFF1A73E8L, RubroDeMarca.SUSCRIPCIONES, "google", "google one", "google play"),
    // ── Telefonía y servicios
    comercio("claro", "Claro", "C", 0xFFDA291CL, BLANCO, RubroDeMarca.TELEFONIA, "claro", "comcel"),
    comercio("movistar", "Movistar", "M", 0xFF0B6DC4L, BLANCO, RubroDeMarca.TELEFONIA, "movistar", "telefonica"),
    comercio("tigo", "Tigo", "T", 0xFF00377BL, BLANCO, RubroDeMarca.TELEFONIA, "tigo"),
    comercio("wom", "WOM", "W", 0xFF4F1A8BL, BLANCO, RubroDeMarca.TELEFONIA, "wom"),
    comercio("etb", "ETB", "ETB", 0xFF0067B1L, BLANCO, RubroDeMarca.TELEFONIA, "etb"),
    // UNE antes que EPM: «UNE EPM Telecomunicaciones» es UNE.
    comercio("une", "UNE", "UNE", 0xFFD4002AL, BLANCO, RubroDeMarca.TELEFONIA, "une", "une telecomunicaciones"),
    comercio("epm", "EPM", "epm", 0xFF00733AL, BLANCO, RubroDeMarca.TELEFONIA, "epm", "empresas publicas de medellin"),
    // ── Restaurantes y café
    comercio(
        "crepes_waffles", "Crepes & Waffles", "C&W", 0xFF6A2C2BL, BLANCO, RubroDeMarca.RESTAURANTES_Y_CAFE,
        "crepes waffles", "crepes y waffles", "crepes and waffles",
    ),
    comercio("el_corral", "El Corral", "EC", 0xFFC8102EL, BLANCO, RubroDeMarca.RESTAURANTES_Y_CAFE, "el corral", "hamburguesas el corral"),
    comercio("frisby", "Frisby", "F", 0xFFD50F25L, BLANCO, RubroDeMarca.RESTAURANTES_Y_CAFE, "frisby"),
    comercio("mcdonalds", "McDonald's", "M", 0xFF27251FL, 0xFFFFC72CL, RubroDeMarca.RESTAURANTES_Y_CAFE, "mcdonalds", "mc donalds", "mcdonald"),
    comercio("juan_valdez", "Juan Valdez", "JV", 0xFFA6192EL, BLANCO, RubroDeMarca.RESTAURANTES_Y_CAFE, "juan valdez"),
    comercio("starbucks", "Starbucks", "S", 0xFF00704AL, BLANCO, RubroDeMarca.RESTAURANTES_Y_CAFE, "starbucks"),
    comercio("dominos", "Domino's", "D", 0xFF006491L, BLANCO, RubroDeMarca.RESTAURANTES_Y_CAFE, "dominos", "domino s", "dominos pizza"),
    comercio("kfc", "KFC", "KFC", 0xFFA3080CL, BLANCO, RubroDeMarca.RESTAURANTES_Y_CAFE, "kfc"),
    comercio("subway", "Subway", "S", 0xFF005A2BL, 0xFFFFC20EL, RubroDeMarca.RESTAURANTES_Y_CAFE, "subway"),
    // ── Salud
    comercio("cruz_verde", "Cruz Verde", "+", 0xFF00733AL, BLANCO, RubroDeMarca.SALUD, "cruz verde", "cruzverde"),
    comercio("pasteur", "Pasteur", "P", 0xFF003DA5L, BLANCO, RubroDeMarca.SALUD, "pasteur"),
    comercio("la_rebaja", "La Rebaja", "LR", 0xFFD50F25L, BLANCO, RubroDeMarca.SALUD, "la rebaja", "drogas la rebaja"),
    comercio("farmatodo", "Farmatodo", "F", 0xFF0057A8L, BLANCO, RubroDeMarca.SALUD, "farmatodo"),
    // ── Ropa y tiendas (Prime Video ya pasó: acá Amazon es la tienda)
    comercio("zara", "Zara", "Z", 0xFF000000L, BLANCO, RubroDeMarca.ROPA_Y_TIENDAS, "zara"),
    comercio("hm", "H&M", "H&M", 0xFFD50010L, BLANCO, RubroDeMarca.ROPA_Y_TIENDAS, "h m", "hm", "hym", "h y m"),
    comercio("falabella", "Falabella", "f", 0xFFB2D235L, OSCURO, RubroDeMarca.ROPA_Y_TIENDAS, "falabella", "falabella com"),
    comercio("homecenter", "Homecenter", "H", 0xFFF26E21L, OSCURO, RubroDeMarca.ROPA_Y_TIENDAS, "homecenter", "home center", "sodimac"),
    comercio(
        "mercado_libre", "Mercado Libre", "ML", 0xFFFFE600L, 0xFF2D3277L, RubroDeMarca.ROPA_Y_TIENDAS,
        "mercado libre", "mercadolibre", "mercado pago", "mercadopago", "mercpago",
    ),
    comercio("amazon", "Amazon", "a", 0xFF232F3EL, 0xFFFF9900L, RubroDeMarca.ROPA_Y_TIENDAS, "amazon", "amzn", "amazon com"),
    comercio("arturo_calle", "Arturo Calle", "AC", 0xFF0B2341L, BLANCO, RubroDeMarca.ROPA_Y_TIENDAS, "arturo calle"),
    // ── Gimnasio
    comercio("bodytech", "Bodytech", "B", 0xFFC8102EL, BLANCO, RubroDeMarca.GIMNASIO, "bodytech", "body tech"),
    comercio("smart_fit", "Smart Fit", "SF", 0xFFFFC20EL, OSCURO, RubroDeMarca.GIMNASIO, "smart fit", "smartfit"),
    // ── Cine
    comercio("cine_colombia", "Cine Colombia", "CC", 0xFFB5121BL, BLANCO, RubroDeMarca.CINE, "cine colombia", "cinecolombia"),
    comercio("cinemark", "Cinemark", "C", 0xFFB3001BL, BLANCO, RubroDeMarca.CINE, "cinemark"),
    comercio("procinal", "Procinal", "P", 0xFF1E3A8AL, BLANCO, RubroDeMarca.CINE, "procinal"),
)

/**
 * Los bancos y billeteras, **en orden de prioridad**: lo que es un producto de otro banco va antes
 * que ese banco (la Fiducuenta y la AMEX antes que Bancolombia, Daviplata antes que Davivienda), y
 * las redes de tarjeta —Visa, Mastercard— al final: «Bancolombia Visa» es Bancolombia.
 */
val BANCOS_CONOCIDOS: List<Marca> = listOf(
    banco("fiducuenta", "Fiducuenta", "F", 0xFF2C2A29L, 0xFFFDDA24L, listOf("fiducuenta", "fiduciaria bancolombia")),
    banco("amex", "American Express", "AX", 0xFF006FCFL, BLANCO, listOf("amex", "american express")),
    banco("rappipay", "RappiPay", "RP", 0xFFFF441FL, OSCURO, listOf("rappipay", "rappi pay", "rappicard", "rappi card")),
    banco("nequi", "Nequi", "N", 0xFF200020L, 0xFFFF4FB8L, listOf("nequi")),
    banco("bancolombia", "Bancolombia", "B", 0xFFFDDA24L, OSCURO, listOf("bancolombia", "bcolombia")),
    // Vuelta 91: así nombran a Nu el SMS de Bancolombia («Pagaste $X a NU Compania de Financiamiento») y
    // el correo de PSE («Empresa: NU Compañía de Financiamiento»). Las palabras van enteras: «Nu» suelta
    // sigue siendo solo de cuenta (en un texto puede ser cualquier cosa).
    banco(
        "nu", "Nu", "nu", 0xFF820AD1L, BLANCO,
        listOf("nubank", "nu colombia", "nu financiera", "nu compania de financiamiento", "nu compania financiamiento"),
        listOf("nu"),
    ),
    banco("daviplata", "Daviplata", "DP", 0xFFC8102EL, BLANCO, listOf("daviplata")),
    banco("davibank", "Davibank", "Db", 0xFF9E1B32L, BLANCO, listOf("davibank")),
    banco("davivienda", "Davivienda", "D", 0xFFD31920L, BLANCO, listOf("davivienda")),
    banco(
        "banco_de_bogota", "Banco de Bogotá", "B", 0xFF0D2B70L, BLANCO,
        listOf("banco de bogota", "bancodebogota", "banbogota", "bco bogota", "bco de bogota"),
    ),
    banco("bbva", "BBVA", "BB", 0xFF004481L, BLANCO, listOf("bbva")),
    banco(
        "occidente", "Banco de Occidente", "O", 0xFF005DA4L, BLANCO,
        listOf("banco de occidente", "bco occidente", "bco de occidente"), listOf("occidente"),
    ),
    banco("av_villas", "AV Villas", "AV", 0xFF0057A0L, BLANCO, listOf("av villas", "avvillas")),
    banco("itau", "Itaú", "I", 0xFFEC7000L, 0xFF001A57L, listOf("itau")),
    banco("scotiabank", "Scotiabank Colpatria", "S", 0xFFC8102EL, BLANCO, listOf("scotiabank", "colpatria", "scotiabank colpatria")),
    banco("banco_popular", "Banco Popular", "P", 0xFF00733AL, BLANCO, listOf("banco popular", "bco popular"), listOf("popular")),
    banco(
        "banco_falabella", "Banco Falabella", "BF", 0xFF1B7340L, BLANCO,
        listOf("banco falabella", "cmr falabella", "tarjeta cmr"), listOf("cmr", "falabella"),
    ),
    banco("lulo", "Lulo Bank", "L", 0xFFCCFF00L, OSCURO, listOf("lulo bank", "lulobank"), listOf("lulo")),
    banco("skandia", "Skandia", "S", 0xFF00594FL, BLANCO, listOf("skandia")),
    banco("porvenir", "Porvenir", "P", 0xFF00539BL, BLANCO, listOf("fondo porvenir", "afp porvenir", "porvenir s a"), listOf("porvenir")),
    banco(
        "proteccion", "Protección", "P", 0xFF002D72L, BLANCO,
        listOf("afp proteccion", "fondo proteccion", "proteccion s a"), listOf("proteccion"),
    ),
    banco("colfondos", "Colfondos", "C", 0xFFC8102EL, BLANCO, listOf("colfondos")),
    banco("glim", "Glim", "G", 0xFF5B2C83L, BLANCO, listOf("glim")),
    banco("movii", "MOVii", "M", 0xFFC8006EL, BLANCO, listOf("movii")),
    // Vuelta 88 · los bancos comunes que faltaban para «Otro banco». Patrones **conservadores**: el
    // nombre entero o una palabra que no es de uso diario; la palabra suelta que sí lo es («agrario»,
    // «pichincha», «coomeva» —también es una EPS—, «confiar», «iris») va solo en el nombre de una cuenta.
    banco(
        "caja_social", "Banco Caja Social", "CS", 0xFF004B93L, BLANCO,
        listOf("banco caja social", "bco caja social", "caja social", "bcsc"),
    ),
    banco(
        "banco_agrario", "Banco Agrario", "BA", 0xFF00713DL, BLANCO,
        listOf("banco agrario", "bco agrario", "bancoagrario"), listOf("agrario"),
    ),
    banco("pichincha", "Banco Pichincha", "P", 0xFFFFDD00L, OSCURO, listOf("banco pichincha", "bco pichincha"), listOf("pichincha")),
    banco("bancoomeva", "Bancoomeva", "Bc", 0xFF006B35L, BLANCO, listOf("bancoomeva"), listOf("coomeva")),
    banco("finandina", "Banco Finandina", "F", 0xFF005F6BL, BLANCO, listOf("banco finandina", "finandina")),
    banco("serfinanza", "Banco Serfinanza", "S", 0xFF003DA5L, BLANCO, listOf("banco serfinanza", "serfinanza")),
    banco(
        "coopcentral", "Banco Coopcentral", "CC", 0xFF1B5E20L, BLANCO,
        listOf("banco cooperativo coopcentral", "banco coopcentral", "coopcentral"),
    ),
    banco("confiar", "Confiar", "C", 0xFFE30613L, BLANCO, listOf("confiar cooperativa", "cooperativa confiar"), listOf("confiar")),
    banco("iris", "Iris", "I", 0xFF3C1E70L, BLANCO, listOf("iris bank", "irisbank"), listOf("iris")),
    banco("uala", "Ualá", "U", 0xFF2A4FD6L, BLANCO, listOf("uala")),
    banco("banco_w", "Banco W", "W", 0xFFF58220L, OSCURO, listOf("banco w", "bco w")),
    banco("mibanco", "Mibanco", "Mi", 0xFF007A3DL, BLANCO, listOf("mibanco")),
    banco("visa","Visa", "V", 0xFF1A1F71L, BLANCO, emptyList(), listOf("visa")),
    banco("mastercard", "Mastercard", "MC", 0xFF1A1A1AL, 0xFFF79E1BL, listOf("mastercard", "master card")),
)

/**
 * Vuelta 88 · **lo que no es una cuenta donde se tiene plata**: las redes de tarjeta (Visa,
 * Mastercard) y la American Express, que es una tarjeta y va en «Tarjetas de crédito». Los fondos
 * (Skandia, Porvenir, Protección, Colfondos, la Fiducuenta) sí quedan: el formulario ofrece «Inversión»
 * y ahí van las cesantías y la pensión voluntaria.
 */
private val NO_SON_CUENTAS_DE_PLATA: Set<String> = setOf("visa", "mastercard", "amex")

/**
 * **«Otro banco»** (vuelta 88): todos los bancos y billeteras de [BANCOS_CONOCIDOS] que pueden ser
 * una cuenta (sin [NO_SON_CUENTAS_DE_PLATA]; con [paraTarjetas], la AMEX sí), en orden alfabético y
 * filtrados por [consulta]. La consulta se compara como en toda búsqueda de la app
 * ([normalizarParaBuscar]: sin tildes ni mayúsculas, «bogota» encuentra «Banco de Bogotá») contra el
 * nombre y contra sus patrones («colpatria» encuentra Scotiabank Colpatria). Vacía, van todos. Pura.
 */
fun bancosParaElegir(consulta: String = "", paraTarjetas: Boolean = false): List<Marca> {
    val q = normalizarParaBuscar(consulta)
    return BANCOS_CONOCIDOS
        .filter { it.id !in NO_SON_CUENTAS_DE_PLATA || (paraTarjetas && it.id == "amex") }
        .filter { m ->
            q.isEmpty() || (listOf(m.nombre) + m.patrones + m.patronesDeCuenta).any { q in normalizarParaBuscar(it) }
        }
        .sortedBy { normalizarParaBuscar(it.nombre) }
}

/** Todas, sin repetir: lo que recorre una prueba. */
val MARCAS_CONOCIDAS: List<Marca> = COMERCIOS_CONOCIDOS + BANCOS_CONOCIDOS

/**
 * **La marca de un movimiento**: primero por su `merchant` y, si no dice nada, por su descripción.
 * Solo con los patrones seguros ([Marca.patrones]): en la descripción de un gasto «Nu» o «Visa»
 * pueden no ser el banco.
 */
fun marcaDe(evento: FinancialEvent): Marca? =
    evento.merchant?.takeIf { it.isNotBlank() }?.let(::marcaDelTexto) ?: marcaDelTexto(evento.description)

/** La marca de una cuenta, por su nombre: primero los bancos (con sus palabras de cuenta), luego el resto. */
fun marcaDeLaCuenta(account: Account): Marca? = marcaDelBanco(account.name)

/**
 * La marca que nombra un texto libre: el comercio de un aviso («Compraste $27.500 en RAPPI*LAS DOCE»),
 * una descripción. Los comercios antes que los bancos.
 */
fun marcaDelTexto(texto: String): Marca? = buscarMarca(texto, ORDEN_PARA_TEXTO, conPatronesDeCuenta = false)

/**
 * La marca que nombra un banco: el nombre de una cuenta («Nu», «Bancolombia Ahorros») o el origen de
 * un aviso («Notificación · Nu», «Correo · Bancolombia»). Acá sí valen [Marca.patronesDeCuenta], y los
 * bancos van antes que los comercios: una cuenta «Falabella» es la tarjeta del banco.
 */
fun marcaDelBanco(texto: String): Marca? = buscarMarca(texto, ORDEN_PARA_CUENTAS, conPatronesDeCuenta = true)

/**
 * La marca de un aviso del banco: el comercio que nombra su texto y, si no nombra ninguno, el banco
 * que lo mandó ([origen] es el `bank` del aviso: «Notificación · Nu», un código de SMS…).
 */
fun marcaDelAviso(texto: String, origen: String): Marca? = marcaDelTexto(texto) ?: marcaDelBanco(origen)

private val ORDEN_PARA_TEXTO: List<Marca> = COMERCIOS_CONOCIDOS + BANCOS_CONOCIDOS

private val ORDEN_PARA_CUENTAS: List<Marca> =
    BANCOS_CONOCIDOS + COMERCIOS_CONOCIDOS.filter { it.id != "falabella" }

/**
 * Las palabras de un texto, como las compara el catálogo: minúsculas, sin tildes, sin apóstrofos
 * («McDonald's» → `mcdonalds`) y partidas en todo lo que no sea letra o dígito («RAPPI*LAS DOCE» →
 * `rappi`, `las`, `doce`; «NETFLIX.COM» → `netflix`, `com`).
 */
fun palabrasDeMarca(texto: String): List<String> {
    val limpio = buildString(texto.length) {
        for (c in texto) {
            when (val m = c.lowercaseChar()) {
                '\'', '’', '´', '`' -> Unit
                'á', 'à', 'ä', 'â' -> append('a')
                'é', 'è', 'ë', 'ê' -> append('e')
                'í', 'ì', 'ï', 'î' -> append('i')
                'ó', 'ò', 'ö', 'ô' -> append('o')
                'ú', 'ù', 'ü', 'û' -> append('u')
                'ñ' -> append('n')
                else -> append(if (m.isLetterOrDigit()) m else ' ')
            }
        }
    }
    return limpio.split(' ').filter { it.isNotEmpty() }
}

/** Los patrones de cada marca, ya partidos en palabras: se calculan una vez. */
private val PATRONES: Map<String, List<List<String>>> =
    MARCAS_CONOCIDAS.associate { m -> m.id to m.patrones.map(::palabrasDeMarca).filter { it.isNotEmpty() } }

private val PATRONES_DE_CUENTA: Map<String, List<List<String>>> =
    MARCAS_CONOCIDAS.associate { m -> m.id to m.patronesDeCuenta.map(::palabrasDeMarca).filter { it.isNotEmpty() } }

/**
 * Dónde casó una marca: el tramo de palabras `[desde, hasta)` de [palabrasDeMarca]. Interna: la usa
 * `nombreParaMostrar` para saber qué palabras del texto son la marca y cuáles vienen después.
 */
internal class Coincidencia(val marca: Marca, val orden: Int, val desde: Int, val hasta: Int) {
    fun estaDentroDe(otra: Coincidencia): Boolean =
        otra !== this && otra.desde <= desde && hasta <= otra.hasta && (otra.hasta - otra.desde) > (hasta - desde)
}

private fun buscarMarca(texto: String, orden: List<Marca>, conPatronesDeCuenta: Boolean): Marca? =
    buscarCoincidencia(texto, orden, conPatronesDeCuenta)?.marca

/** La coincidencia de [marcaDelTexto], con su tramo: los comercios antes que los bancos. */
internal fun coincidenciaEnElTexto(texto: String): Coincidencia? =
    buscarCoincidencia(texto, ORDEN_PARA_TEXTO, conPatronesDeCuenta = false)

private fun buscarCoincidencia(texto: String, orden: List<Marca>, conPatronesDeCuenta: Boolean): Coincidencia? {
    val palabras = palabrasDeMarca(texto)
    if (palabras.isEmpty()) return null
    val coincidencias = mutableListOf<Coincidencia>()
    orden.forEachIndexed { i, marca ->
        val patrones = PATRONES[marca.id].orEmpty() +
            (if (conPatronesDeCuenta) PATRONES_DE_CUENTA[marca.id].orEmpty() else emptyList())
        for (patron in patrones) {
            var desde = 0
            while (desde + patron.size <= palabras.size) {
                if (palabras.subList(desde, desde + patron.size) == patron) {
                    coincidencias += Coincidencia(marca, i, desde, desde + patron.size)
                }
                desde++
            }
        }
    }
    if (coincidencias.isEmpty()) return null
    return coincidencias
        .filter { c -> coincidencias.none { c.estaDentroDe(it) } }
        .minByOrNull { it.orden }
}
