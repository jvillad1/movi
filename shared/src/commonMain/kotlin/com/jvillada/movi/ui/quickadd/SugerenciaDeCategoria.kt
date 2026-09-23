package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.RecuerdoDeCategoria
import com.jvillada.movi.shared.model.huellaDeUnMovimiento
import com.jvillada.movi.shared.model.isReservedCategory

/**
 * Cuántas letras tiene que tener, como mínimo, la clave de una huella `nombre:` para que un
 * PREFIJO valga como coincidencia — ver el punto 2 del KDoc de [sugerenciaPorNombre]. Con tres
 * letras («mor») ya empiezan a juntarse comercios sin ninguna relación entre sí («Mora Soccer» y
 * «Morocho's»); con cuatro, la lista de falsos positivos posibles se reduce mucho más de lo que
 * cuesta escribir una letra más.
 */
private const val LARGO_MINIMO_DEL_PREFIJO = 4

/**
 * **Task 5 — el nombre que el dueño está escribiendo sugiere una categoría, antes de que termine
 * de escribirlo.** Es la mitad «leer» de la memoria que [MemoriaDeCategoriasCache] trae del
 * server: acá se decide, para una nota puntual, cuál de los [recuerdos] (si alguno) aplica.
 *
 * Pura y sin acceso a `UsedCategoriesCache`, a propósito — así se puede probar sin Compose ni
 * Robolectric. Lo único que sabe de "escondida" es lo que sabe [isReservedCategory] (una
 * categoría reservada nunca sale de acá, esté o no en la lista de [recuerdos]); filtrar las
 * categorías que el dueño escondió en «Más → Categorías» es trabajo de quien arma [recuerdos]
 * antes de llamar a esta función — ver `QuickAddScreen.kt`, que cruza con
 * `UsedCategoriesCache.prefs` antes de pasarlos acá.
 *
 * Dos pasos, y el primero que encuentra algo gana:
 *
 * 1. **Coincidencia exacta de huella.** [huellaDeUnMovimiento] de la nota es la MISMA huella que
 *    algún recuerdo — venga de un nombre, una llave, una cuenta o un cajero. Es el caso de
 *    siempre: el dueño ya anotó este mismo destinatario y Movi se acuerda con qué categoría.
 * 2. **Prefijo de nombre**, solo si el paso 1 no encontró nada Y la huella de la nota es un
 *    `nombre:` (no una llave ni una cuenta, que no se pueden completar a medias) con una clave de
 *    al menos [LARGO_MINIMO_DEL_PREFIJO] caracteres: mientras el dueño escribe «Mora» y el
 *    destinatario guardado es «Mora Soccer», la huella de la nota (`nombre:mora`) todavía no es
 *    igual a la guardada (`nombre:morasoccer`) pero sí es un prefijo suyo. Si TODOS los recuerdos
 *    que empiezan con esa clave apuntan a la misma categoría, se propone la de más `cuantos`; si
 *    apuntan a categorías distintas, no se adivina — `null` es la respuesta correcta cuando «Mor»
 *    podría ser tanto «Mora Soccer» (Fútbol) como «Morocho's» (Comida).
 *
 * `null` en cualquier otro caso: nota vacía, nota que no identifica a nadie
 * ([huellaDeUnMovimiento] ya devuelve `null` para eso), o sin memoria que la respalde.
 */
fun sugerenciaPorNombre(nota: String, recuerdos: List<RecuerdoDeCategoria>): RecuerdoDeCategoria? {
    val huella = huellaDeUnMovimiento(nota) ?: return null
    val elegibles = recuerdos.filter { !isReservedCategory(it.categoria) }

    elegibles.firstOrNull { it.huella == huella }?.let { return it }

    if (!huella.startsWith("nombre:")) return null
    val clave = huella.removePrefix("nombre:")
    if (clave.length < LARGO_MINIMO_DEL_PREFIJO) return null

    val candidatos = elegibles.filter {
        it.huella.startsWith("nombre:") && it.huella.removePrefix("nombre:").startsWith(clave)
    }
    if (candidatos.isEmpty()) return null
    // Ambiguo: dos comercios distintos que empiezan igual no se pueden desempatar adivinando.
    if (candidatos.map { it.categoria }.toSet().size > 1) return null
    return candidatos.maxByOrNull { it.cuantos }
}
