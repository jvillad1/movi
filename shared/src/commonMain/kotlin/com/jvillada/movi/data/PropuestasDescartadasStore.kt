package com.jvillada.movi.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * **Las propuestas de «Ordena tus categorías» que el dueño ya dijo «Ahora no»** (Ola B, tarea 6),
 * recordadas en este aparato para no volver a ofrecérselas cada vez que abre «Más → Categorías».
 *
 * La clave es la que arma [com.jvillada.movi.ui.categorias.claveDePropuesta]: tipo de regla más
 * los nombres normalizados de la propuesta (p. ej. `"unificar:credito>cuota de credito"`). No
 * guarda la propuesta entera —ni hace falta, ni sobreviviría a un rename— solo que ESA combinación
 * ya se ofreció y se rechazó.
 *
 * **Es del usuario, no del aparato** (a diferencia de `TemaStore`): un «Ahora no» a una propuesta
 * sobre SUS categorías no significa nada para otra cuenta que entre en este mismo teléfono. Por
 * eso se limpia en `SessionManager.clear()`, y `ElForkLlegaLimpioTest` lo prueba.
 *
 * **Nada de esto puede tumbar el arranque.** Misma lección que `LastAccountStore`/`DiasPlegadosStore`:
 * `Settings()` explota AL CONSTRUIRSE con el almacenamiento bloqueado, así que la construcción
 * queda diferida (`by lazy`) y toda lectura/escritura va adentro de un `runCatching`. Sin dónde
 * guardar, la tarjeta sigue ofreciendo la propuesta durante la sesión — mejor que reventar el
 * arranque por un «Ahora no» que no se pudo guardar.
 */
private const val KEY_PROPUESTAS_DESCARTADAS = "categorias_propuestas_descartadas"

/** Tope de claves guardadas — una fuga lenta si solo creciera para siempre. Sobra de sobra para lo que ofrece [MAX_PROPUESTAS_DE_ORDEN][com.jvillada.movi.ui.categorias.MAX_PROPUESTAS_DE_ORDEN] por vez. */
private const val MAX_DESCARTADAS = 200

/** Top-level y `by lazy`, por lo mismo que en `LastAccountStore` (ver su KDoc). */
private val propuestasSettings: Settings by lazy { Settings() }

object PropuestasDescartadasStore {
    private var memoria: Set<String> = leer()

    /** Las claves que el dueño ya descartó con «Ahora no». */
    fun descartadas(): Set<String> = memoria

    fun estaDescartada(clave: String): Boolean = clave in memoria

    /** «Ahora no»: deja de ofrecerse, sin tocar la categoría ni sus movimientos. Devuelve el conjunto nuevo, como `DiasPlegadosStore.alternar`. */
    fun marcar(clave: String): Set<String> {
        val nuevo = acotar(memoria + clave)
        memoria = nuevo
        guardar(nuevo)
        return nuevo
    }

    /** Al cerrar sesión: son los «Ahora no» del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        memoria = emptySet()
        guardar(emptySet())
    }

    private fun acotar(claves: Set<String>): Set<String> =
        if (claves.size <= MAX_DESCARTADAS) claves else claves.toList().takeLast(MAX_DESCARTADAS).toSet()

    // Separador `\n` y no `,`: la clave lleva nombres de categoría de texto libre, que sí pueden
    // traer comas («Comida, snacks») pero no un salto de línea — el campo donde se escriben es
    // `singleLine = true` en las tres pantallas que crean categorías.
    private fun leer(): Set<String> = runCatching {
        propuestasSettings.getStringOrNull(KEY_PROPUESTAS_DESCARTADAS)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.toSet()
    }.getOrNull() ?: emptySet()

    private fun guardar(claves: Set<String>) {
        runCatching {
            if (claves.isEmpty()) propuestasSettings.remove(KEY_PROPUESTAS_DESCARTADAS)
            else propuestasSettings[KEY_PROPUESTAS_DESCARTADAS] = claves.joinToString("\n")
        }
    }
}
