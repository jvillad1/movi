package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # «Editar inicio»: el dueño elige qué secciones ve en Hoy y en qué orden
 *
 * Ola 3 de la revisión de experiencia del 22-oct-2026. Hoy apilaba ocho secciones (~2.600 px en el
 * teléfono) y el dueño no decidió todavía qué sacar. La regla de la casa es que no haya ajustes que
 * solo se cambien tocando código, así que en vez de decidir por él se le da el control: esconder y
 * reordenar las secciones de Hoy.
 *
 * ## Se guarda por usuario en el server
 *
 * `GET/PUT /api/inicio/preferencias`. Así la misma elección vale en la web, el teléfono y el
 * iPhone. **El server la aplica al servir la definición de Hoy** (`GET /api/screens/dashboard`, ver
 * [inicioConPreferencias]): el orden y las ocultas viajan ya puestos, y un APK viejo —que no sabe
 * nada de preferencias— igual ve su Inicio en el orden que el dueño eligió y sin lo que escondió.
 *
 * ## Por tipo de sección
 *
 * La preferencia nombra tipos (`DISPONIBLE_DEL_PERIODO`), no posiciones. Solo las de
 * [SECCIONES_CONFIGURABLES] se pueden mover o esconder: el héroe va siempre primero, y lo que el
 * Editor de pantallas agregue (tarjetas, enlaces, el BANNER de los APK viejos) se queda donde está.
 * Un tipo que la preferencia no nombra —una sección que se agregue a Hoy después de que el dueño
 * eligió— aparece en su lugar por defecto (ver [ordenarConPreferencias]).
 */
@Serializable
data class PreferenciasDelInicio(
    /** Las secciones configurables en el orden que eligió el dueño. Vacía = el orden de Movi. */
    val orden: List<String> = emptyList(),
    /** Las secciones configurables que no quiere ver en Hoy. */
    val ocultas: List<String> = emptyList(),
    /**
     * Las secciones **opcionales** que el dueño prendió (ver [SECCIONES_OPCIONALES]): no vienen en el
     * Inicio de Movi y el server las agrega a la definición de quien las pide. Ola R1: «Disponible» y
     * «Tu patrimonio» salieron del Inicio por defecto porque repetían Plan y Patrimonio.
     *
     * Con default vacío: un APK viejo no lo manda, y el server lo lee como «ninguna prendida».
     */
    val visibles: List<String> = emptyList(),
    /**
     * **Solo en la respuesta**: las secciones configurables de la definición de Hoy, en el orden de
     * Movi. Es lo que la pantalla «Editar inicio» lista y lo que restaura «Volver al orden de Movi».
     * El server la ignora al guardar.
     */
    val ordenDeMovi: List<String> = emptyList(),
) {
    /** Sin orden propio, ocultas ni opcionales prendidas: es Hoy tal como lo arma Movi. */
    val esLaDeMovi: Boolean get() = orden.isEmpty() && ocultas.isEmpty() && visibles.isEmpty()
}

/**
 * **Las secciones de Hoy que se pueden mover y esconder**, con el nombre que ve el dueño, en el
 * orden de Movi. El héroe («Te quedan») no está: va siempre primero. Tampoco las tarjetas de
 * cabecera (Primeros pasos, Cierre del período, Avisos), que no viajan en la definición y aparecen
 * solo cuando hacen falta.
 */
val SECCIONES_CONFIGURABLES: Map<String, String> = linkedMapOf(
    // Ola R1 (generación 9): el carrusel de cuentas y los dos widgets compactos son lo que el dueño
    // elige; la cuadrícula de acciones y «Movi propone hoy» van fijos debajo del héroe, como él.
    "CUENTAS" to "Tus cuentas",
    "CHECKLIST_DEL_PERIODO" to "Falta por pagar",
    "GASTO_POR_CATEGORIA" to "En qué se va",
    "DISPONIBLE_DEL_PERIODO" to "Disponible",
    "PATRIMONIO" to "Tu patrimonio",
    // «Pregúntale a Movi» y «Para revisar» dejaron de ser configurables con la generación 9: el
    // primero es una acción de la cuadrícula y el segundo es «Movi propone hoy», fijo debajo del
    // héroe. ALERTS sigue en la definición solo para los APK viejos, y no se lista en «Editar inicio»
    // porque este cliente no lo pinta. Una preferencia guardada que los nombre los pierde en
    // [limpias], en silencio.
)

/**
 * **Las secciones configurables que NO vienen en el Inicio de Movi** (ola R1): repetían una cifra que
 * tiene su casa en otra pantalla —el disponible en Plan, el patrimonio en Patrimonio—, así que salieron
 * del Inicio por defecto. Siguen en «Editar inicio»: el dueño que las prende las tiene en
 * [PreferenciasDelInicio.visibles], y el server las agrega a su definición ([inicioConPreferencias]).
 */
val SECCIONES_OPCIONALES: List<String> = listOf("DISPONIBLE_DEL_PERIODO", "PATRIMONIO")

/**
 * **«Para revisar», escondida, en una sola línea.** Lo urgente nunca desaparece del todo: si el
 * dueño escondió «Para revisar» ([PreferenciasDelInicio.ocultas] con `ALERTS`), el server deja en
 * su lugar una sección de este tipo, y el cliente la pinta como «2 cosas para revisar ›» **solo**
 * cuando hay algo urgente (si no, no ocupa lugar). Un APK viejo no conoce el tipo y la descarta en
 * silencio: para él, la sección escondida simplemente no está.
 *
 * Es un tipo **personal**: lo produce el server al aplicar la preferencia y nunca va en la
 * definición global (el Editor de pantallas no lo ofrece y `validateDefinition` lo rechaza).
 */
const val PARA_REVISAR_COMPACTO: String = "PARA_REVISAR_COMPACTO"

/** Tope de lo que se acepta guardar: más que esto no es una preferencia, es basura. */
const val MAXIMO_DE_SECCIONES_EN_LA_PREFERENCIA: Int = 32

/**
 * La preferencia sin lo que no corresponde: solo tipos de [SECCIONES_CONFIGURABLES], sin repetidos.
 * Lo que sobra se descarta en silencio —una versión futura de la app puede mandar un tipo que esta
 * no conoce— en vez de rechazar el guardado entero.
 *
 * **Una sección en las dos listas gana [PreferenciasDelInicio.visibles]** (vuelta 66). Quien escondió
 * «Disponible» cuando todavía venía en Hoy quedó con `ocultas:[DISPONIBLE_DEL_PERIODO]`; cuando pasó a
 * opcional y la prendió, el editor la agregó a `visibles` sin sacarla de `ocultas`, y ganaba la oculta:
 * el interruptor prendido y la sección sin aparecer. `visibles` es lo último que el dueño pidió —una
 * opcional solo entra ahí prendiéndola en el editor, después de que existió la lista—, así que se queda
 * y la oculta sale. Como el server limpia **al leer** (la preferencia guardada, `GET` y la definición
 * de Hoy), esto arregla también las preferencias ya guardadas, sin reescribir la fila.
 */
fun PreferenciasDelInicio.limpias(): PreferenciasDelInicio {
    val prendidas = visibles.filter { it in SECCIONES_OPCIONALES }.distinct()
    return PreferenciasDelInicio(
        orden = orden.filter { it in SECCIONES_CONFIGURABLES }.distinct(),
        ocultas = ocultas.filter { it in SECCIONES_CONFIGURABLES && it !in prendidas }.distinct(),
        visibles = prendidas,
    )
}

/**
 * **Reordena [items] según [orden], sin mover lo que el orden no nombra.**
 *
 * Los lugares que ocupan en [items] los elementos que [orden] nombra se conservan como lugares, y se
 * vuelven a llenar con esos mismos elementos en el orden elegido. Lo que [orden] no nombra —el
 * héroe, las secciones del Editor, una sección configurable nueva que el dueño todavía no vio— se
 * queda exactamente donde estaba. Así una sección agregada a Hoy después de que el dueño eligió su
 * orden aparece en su lugar por defecto, y el héroe sigue primero aunque alguien lo nombre (no está
 * en [SECCIONES_CONFIGURABLES], y [limpias] lo saca).
 */
fun <T> ordenarConPreferencias(items: List<T>, tipo: (T) -> String, orden: List<String>): List<T> {
    if (orden.isEmpty()) return items
    val posicion = orden.withIndex().associate { (i, t) -> t to i }
    val lugares = items.indices.filter { tipo(items[it]) in posicion }
    if (lugares.size < 2) return items
    // `sortedBy` es estable: dos secciones del mismo tipo conservan su orden relativo.
    val reordenados = lugares.map { items[it] }.sortedBy { posicion.getValue(tipo(it)) }
    val resultado = items.toMutableList()
    lugares.forEachIndexed { i, lugar -> resultado[lugar] = reordenados[i] }
    return resultado
}

/**
 * **La definición de Hoy con la preferencia del dueño puesta.** La usa el server al servir
 * `GET /api/screens/dashboard`; es pura para que la prueben `:core` y las pantallas.
 *
 * - Las opcionales que el dueño prendió ([PreferenciasDelInicio.visibles]) y que la definición no trae
 *   se agregan justo después de la última sección configurable (antes de lo que queda solo para los
 *   APK viejos), en el orden de [SECCIONES_OPCIONALES]. Ver [conLasOpcionales].
 * - El orden, con [ordenarConPreferencias].
 * - Las ocultas se sacan, salvo «Para revisar» (`ALERTS`), que se cambia por
 *   [PARA_REVISAR_COMPACTO] en el mismo lugar: lo urgente nunca desaparece del todo.
 * - La `version` cambia con la preferencia ([versionConPreferencias]): el cliente pide la definición
 *   con `If-None-Match` y, si la versión fuera la de siempre, el server le contestaría 304 y el
 *   teléfono se quedaría con el Inicio de antes de editarlo.
 *
 * Sin preferencia (o con una vacía), la definición sale idéntica: mismo contenido y misma versión.
 */
fun inicioConPreferencias(def: ScreenDefinition, preferencias: PreferenciasDelInicio): ScreenDefinition {
    val limpias = preferencias.limpias()
    if (limpias.esLaDeMovi) return def
    val ordenadas = ordenarConPreferencias(conLasOpcionales(def.sections, limpias.visibles), { it.type }, limpias.orden)
    val secciones = ordenadas.mapNotNull { seccion ->
        when {
            seccion.type !in limpias.ocultas -> seccion
            seccion.type == "ALERTS" -> ScreenSection(type = PARA_REVISAR_COMPACTO, title = seccion.title)
            else -> null
        }
    }
    return def.copy(version = versionConPreferencias(def.version, limpias), sections = secciones)
}

/**
 * [secciones] con las opcionales [visibles] que todavía no trae, agregadas después de la última
 * sección configurable (o al final, si no hay ninguna). Una opcional que la definición ya trae —el
 * Editor de pantallas la puso para todos— no se duplica.
 */
internal fun conLasOpcionales(secciones: List<ScreenSection>, visibles: List<String>): List<ScreenSection> {
    val faltan = SECCIONES_OPCIONALES
        .filter { it in visibles && secciones.none { s -> s.type == it } }
        .map { ScreenSection(type = it, title = SECCIONES_CONFIGURABLES[it]) }
    if (faltan.isEmpty()) return secciones
    val despuesDe = secciones.indexOfLast { it.type in SECCIONES_CONFIGURABLES } + 1
    val lugar = if (despuesDe == 0) secciones.size else despuesDe
    return secciones.take(lugar) + faltan + secciones.drop(lugar)
}

/**
 * La versión que viaja con una definición personalizada: depende de la versión global **y** de la
 * preferencia, así cambiar cualquiera de las dos invalida el 304 del cliente.
 *
 * Siempre ≥ 2³⁰: nunca coincide con una versión global (que cuenta ediciones desde 1), así que volver
 * al orden de Movi también le contesta al cliente con la definición entera.
 */
fun versionConPreferencias(versionGlobal: Int, preferencias: PreferenciasDelInicio): Int {
    val limpias = preferencias.limpias()
    if (limpias.esLaDeMovi) return versionGlobal
    // Un hash de cadena escrito a mano (no `hashCode()`): tiene que dar lo mismo en cualquier
    // plataforma y en cualquier versión de Kotlin.
    val huella = "$versionGlobal|${limpias.orden.joinToString(",")}|${limpias.ocultas.sorted().joinToString(",")}" +
        // Sin opcionales, la huella es la de antes de la ola R1: la preferencia guardada de quien no
        // prendió ninguna sigue dando la misma versión.
        (if (limpias.visibles.isEmpty()) "" else "|+${limpias.visibles.sorted().joinToString(",")}")
    var h = 0
    for (c in huella) h = 31 * h + c.code
    return (h and 0x3FFF_FFFF) or 0x4000_0000
}

/**
 * Las secciones configurables de [def], en su orden: lo que lista «Editar inicio» (que agrega al final
 * las [SECCIONES_OPCIONALES] que no vengan acá, apagadas).
 */
fun seccionesConfigurablesDe(def: ScreenDefinition): List<String> =
    def.sections.map { it.type }.filter { it in SECCIONES_CONFIGURABLES }.distinct()
