package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * **Movi se acuerda de cómo categorizaste antes.**
 *
 * El dueño lo dijo mirando su propia lista: *«muchos movimientos quedan en Otro»*. La causa no era
 * un parser flojo sino una tabla de palabras clave escrita a mano, con categorías que él no usa
 * («Restaurantes», «Suscripción») y sin ninguna de las que sí («Fútbol», «Hija», «Gardenera»).
 * Ninguna lista fija va a adivinar que *Café 9 ¾ Bookstore* es «Hija» — pero él ya lo anotó así, y
 * eso es un dato que estaba ahí sin usarse.
 *
 * Acá vive esa memoria: una huella por movimiento y, contra ella, la categoría con la que él lo
 * anotó. Es lógica pura y vive en `:core` a propósito — el server la usa al leer un SMS y el
 * cliente al ofrecer categorías, y si normalizaran distinto el server propondría cosas que la
 * pantalla no sabría explicar.
 *
 * Ver [huellaDeUnMovimiento] para qué cuenta como «el mismo de siempre», y [MemoriaDeCategorias]
 * para cómo se resuelve un empate.
 */

/**
 * Lo que el banco escribe antes del nombre y **no dice a quién se le pagó**. Se recortan para que
 * el mismo comercio se reconozca venga como venga: «Pago QR Mora Soccer» y «Mora Soccer» son el
 * mismo lugar, y hoy quedan en dos renglones que no se hablan.
 *
 * El orden importa —se prueba del más largo al más corto— para que «transferencia a la cuenta» no
 * se recorte como «transferencia» y deje un «a la cuenta» colgando.
 */
private val ARRANQUES_QUE_NO_NOMBRAN: List<String> = listOf(
    "transferencia recibida de", "transferencia recibida", "transferencia a la cuenta",
    "transferencia desde", "transferencia a", "transferencia de", "transferencia",
    "pago por codigo qr", "pago codigo qr", "pago qr", "pago pse",
    "compra con tarjeta en", "compra con tarjeta", "compra en", "compra",
    "pagaste a", "pagaste en", "pago a", "pago de", "pago",
    "retiro en cajero", "retiro en", "retiro",
    "abono a", "abono de", "recibiste de", "nomina recibida",
).sortedByDescending { it.length }

/**
 * Lo que queda cuando se recortó todo y no sobró un nombre: no identifican a nadie y **no pueden
 * ser una huella**. Sin esta lista, todos los pagos por QR sin destinatario compartirían huella y
 * la primera categoría que él eligiera se le aplicaría a todos los demás.
 */
private val NO_IDENTIFICAN: Set<String> = setOf(
    "", "qr", "pse", "movimiento", "cajero", "tarjeta", "cuenta", "lacuenta", "nomina",
    "efectivo", "transferencia", "pago", "compra", "retiro", "abono", "banco", "sucursal",
)

/** «… a la llave 0092184713», «… a la llave @pedro». */
private val LLAVE = Regex("""\bllave\s+@?([A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ.@_-]{3,})""", RegexOption.IGNORE_CASE)

/** «… a la cuenta *41279033068», «… desde tu producto *8133». Cuatro dígitos ya identifican. */
private val CUENTA = Regex("""\*\s?(\d{4,})""")

/** «Retiro en cajero SUCVIVAPAL1»: el código del cajero es el único nombre que trae. */
private val CAJERO = Regex("""\bcajero\s+([A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ]{3,})""", RegexOption.IGNORE_CASE)

/**
 * **La huella de un movimiento**: lo que lo hace reconocible entre dos anotaciones distintas, o
 * `null` cuando el texto no identifica a nadie.
 *
 * Se busca en este orden, y el primero que aparece manda:
 *
 * | En el texto | Huella | Por qué primero |
 * |---|---|---|
 * | `llave 0092184713` | `llave:0092184713` | dos pagos QR distintos comparten el nombre «Pago QR» y **solo** la llave los separa |
 * | `*41279033068` | `cuenta:41279033068` | igual: «Transferencia a la cuenta» es el nombre de todas |
 * | `cajero SUCVIVAPAL1` | `cajero:sucvivapal1` | el cajero es el dato, no «Retiro» |
 * | cualquier otro | `nombre:moraSoccer` | el nombre, sin el arranque del banco, sin tildes y sin puntuación |
 *
 * **`null` no es un error**: es Movi diciendo «este texto no me deja reconocer nada», y es la
 * respuesta correcta para «Pago QR» a secas o «Movimiento». Devolver una huella ahí sería peor que
 * no devolver ninguna — juntaría cosas que no tienen nada que ver.
 */
fun huellaDeUnMovimiento(texto: String): String? {
    LLAVE.find(texto)?.let { return "llave:" + claveComparableDeNombre(it.groupValues[1]) }
    CUENTA.find(texto)?.let { return "cuenta:" + it.groupValues[1] }
    CAJERO.find(texto)?.let { return "cajero:" + claveComparableDeNombre(it.groupValues[1]) }

    var limpio = normalizarParaBuscar(texto)
    for (arranque in ARRANQUES_QUE_NO_NOMBRAN) {
        if (limpio == arranque) return null
        if (limpio.startsWith("$arranque ")) {
            limpio = limpio.removePrefix("$arranque ").trim()
            break
        }
    }
    val clave = claveComparableDeNombre(limpio)
    return if (clave in NO_IDENTIFICAN || clave.length < 3) null else "nombre:$clave"
}

/**
 * ¿La huella la puso el banco con un número, en vez de con un nombre? Un `llave:` o un `cuenta:` no
 * se pueden leer: por eso, cuando Movi ya tiene un nombre que el dueño le puso a ese mismo
 * destinatario, lo propone en lugar del texto del banco. Con un nombre de comercio de verdad
 * («Zelo Group») no hace falta y manda lo que diga el mensaje.
 */
fun laHuellaEsUnNumero(huella: String): Boolean =
    huella.startsWith("llave:") || huella.startsWith("cuenta:") || huella.startsWith("cajero:")

/**
 * **Categorías que no se aprenden**, aunque estén anotadas: no describen en qué se fue la plata,
 * las pone Movi sola y proponerlas sería empujar al dueño a ensuciar sus propias cifras.
 *
 * «Otro»/«Otros» está acá por otro motivo: es la ausencia de categoría. Aprenderla convertiría un
 * «no supe» en una decisión, y Movi seguiría proponiéndola para siempre.
 */
internal val CATEGORIAS_QUE_NO_SE_APRENDEN: Set<String> = setOf(
    OPENING_CATEGORY, ADJUSTMENT_CATEGORY, TRANSFER_CATEGORY, ORPHANED_LEG_CATEGORY,
    "Otro", "Otros",
)

/** Un movimiento ya anotado, reducido a lo único que la memoria necesita de él. */
data class AnotacionPasada(
    /** El texto crudo del banco (`merchant`) o, si no hay, el nombre que quedó en pantalla. */
    val comoLlego: String,
    /** Cómo quedó escrito el movimiento: es el nombre que el dueño reconoce. */
    val nombre: String,
    val categoria: String,
    /** Para desempatar: entre dos categorías con el mismo respaldo gana la más reciente. */
    val cuando: Long,
)

/**
 * Lo que Movi recuerda de un destinatario: con qué categoría lo anotó el dueño, cómo lo llamó, y
 * **cuántas veces** — el número va a la pantalla, porque una sugerencia que se explica («la usaste
 * 4 veces») se puede aceptar o rechazar, y una que no, solo se obedece.
 */
data class LoQueMoviRecuerda(
    val categoria: String,
    val nombre: String,
    val cuantos: Int,
)

/**
 * **La misma memoria, en forma de lista y serializable.** Hasta acá [MemoriaDeCategorias] solo la
 * usaba el server, adentro de la misma JVM, al clasificar un SMS entrante ([memoriaDe] en
 * `server/sms/MemoriaDelDueno.kt`). Ola A: el cliente también quiere ofrecerle una categoría al
 * dueño («la anotaste 4 veces como Hija») al escribir un movimiento a mano, así que la memoria
 * tiene que poder viajar por `GET /api/categorias/memoria`. [huella] es lo que [recuerdoDe] busca
 * — sin ella, la respuesta sería una lista de categorías sin decir a qué destinatario pertenece
 * cada una.
 */
@Serializable
data class RecuerdoDeCategoria(
    val huella: String,
    val categoria: String,
    val nombre: String,
    val cuantos: Int,
)

/**
 * La memoria armada: huella → [LoQueMoviRecuerda]. Se construye con [de] a partir de lo que el
 * dueño ya anotó.
 */
class MemoriaDeCategorias private constructor(
    private val porHuella: Map<String, LoQueMoviRecuerda>,
) {
    val cuantasHuellas: Int get() = porHuella.size

    /** Lo que Movi recuerda de este texto, o `null` si nunca vio nada parecido. */
    fun recuerdoDe(texto: String): LoQueMoviRecuerda? =
        huellaDeUnMovimiento(texto)?.let { porHuella[it] }

    /**
     * Las entradas de la memoria, listas para viajar por la red — ver [RecuerdoDeCategoria]. El
     * mapa [porHuella] sigue privado y esto es una copia de solo lectura: cómo se arma no cambia,
     * solo se expone lo que ya había.
     */
    fun entradas(): List<RecuerdoDeCategoria> =
        porHuella.map { (huella, recuerdo) ->
            RecuerdoDeCategoria(
                huella = huella,
                categoria = recuerdo.categoria,
                nombre = recuerdo.nombre,
                cuantos = recuerdo.cuantos,
            )
        }

    companion object {
        /**
         * **Cómo se resuelve un destinatario con dos historias.** «Café 9 ¾ Bookstore» está anotado
         * una vez como «Comida» y otra como «Hija»: gana la que más veces se usó, y si empatan, la
         * más reciente — que es la última vez que él lo pensó.
         *
         * El nombre que se propone es el del movimiento más reciente de la categoría que ganó, no
         * el del más viejo: si le cambió el nombre, ese cambio es la corrección.
         */
        fun de(anotaciones: List<AnotacionPasada>): MemoriaDeCategorias {
            val porHuella = mutableMapOf<String, LoQueMoviRecuerda>()
            anotaciones
                .filter { it.categoria.isNotBlank() && it.categoria !in CATEGORIAS_QUE_NO_SE_APRENDEN }
                .groupBy { huellaDeUnMovimiento(it.comoLlego) }
                .forEach { (huella, delMismo) ->
                    if (huella == null) return@forEach
                    val ganadora = delMismo
                        .groupBy { it.categoria }
                        .maxWithOrNull(
                            compareBy<Map.Entry<String, List<AnotacionPasada>>> { it.value.size }
                                .thenBy { entrada -> entrada.value.maxOf { it.cuando } },
                        ) ?: return@forEach
                    val masReciente = ganadora.value.maxByOrNull { it.cuando } ?: return@forEach
                    porHuella[huella] = LoQueMoviRecuerda(
                        categoria = ganadora.key,
                        nombre = masReciente.nombre.ifBlank { masReciente.comoLlego },
                        cuantos = ganadora.value.size,
                    )
                }
            return MemoriaDeCategorias(porHuella)
        }

        val vacia: MemoriaDeCategorias get() = MemoriaDeCategorias(emptyMap())
    }
}

/**
 * **La red de seguridad, cuando la memoria no sabe nada.** Unas pocas palabras que sí son
 * universales — Uber es transporte en cualquier vida.
 *
 * Lo importante acá es el **vocabulario**: devuelve nombres de [PREDEFINED_CATEGORIES], las
 * categorías que la app ofrece en todas las demás pantallas. La tabla anterior inventaba
 * «Restaurantes», «Mercado» y «Suscripción», que no existían en ningún selector ni en los datos del
 * dueño: cada SMS confirmado abría una categoría nueva de un solo movimiento, y el gráfico de «en
 * qué se fue la plata» quedaba partido entre «Comida» y «Restaurantes» sin que nadie lo hubiera
 * pedido.
 */
fun categoriaProbablePorElNombre(nombre: String): String? {
    val texto = normalizarParaBuscar(nombre)
    val palabras = texto.split(' ')
    /** Nombres largos y propios: aparecer adentro ya es evidencia suficiente. */
    fun contiene(vararg claves: String) = claves.any { it in texto }
    /**
     * Marcas cortas, y por eso **palabra completa**: «d1» y «ara» adentro de otra palabra no son la
     * tienda («Cámara», «Guitarra»), y una categoría equivocada cuesta más que una sin adivinar.
     */
    fun esPalabra(vararg claves: String) = claves.any { clave -> palabras.any { it == clave } }
    return when {
        esPalabra("uber", "didi", "cabify", "terpel", "primax") ||
            contiene("taxi", "estacion de servicio") -> "Transporte"
        esPalabra("d1", "ara", "exito", "carulla", "olimpica") ||
            contiene("crepes", "waffles", "rappi", "mcdonald", "dominos", "juan valdez",
                "starbucks", "panaderia", "carnes y", "supermercado") -> "Comida"
        contiene("drogas", "farmacia", "cruz verde", "medicina prepagada", "clinica", "laboratorio") ||
            esPalabra("eps", "farmatodo", "colsubsidio") -> "Salud"
        contiene("netflix", "spotify", "disney", "youtube", "prime video", "crunchyroll") ||
            esPalabra("hbo") -> "Entretenimiento"
        contiene("movistar", "acueducto", "gas natural") ||
            esPalabra("claro", "tigo", "wom", "epm", "energia") -> "Servicios"
        contiene("anthropic", "openai", "github", "railway", "google cloud", "microsoft", "apple.com") -> "Tecnología"
        else -> null
    }
}
