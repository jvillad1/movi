package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.categoriaQueNoDiceNada
import com.jvillada.movi.shared.model.esCategoriaQueNoSeElige

/*
 * # Elegir la categoría en la misma tarjeta (vuelta 46)
 *
 * Un aviso que Movi entendió entero menos la categoría («Otros», o una que el dueño nunca usó) iba a
 * Revisar aviso: abrirlo, «Cambiar», elegir y «Confirmar». Ahora la tarjeta de Por revisar la pide
 * ahí mismo: «Elegir categoría» abre las pastillas de las frecuentes con «Todas ›»
 * (`FilaDeCategorias`), y al elegir la tarjeta queda confirmable —«Confirmar», o entra en
 * «Confirmar los N»—. Lo que se elige solo vale para ese aviso; se anota por el camino de siempre.
 */

/**
 * Lo que la tarjeta necesita para pedir la categoría: las pastillas, la que el dueño ya eligió (o
 * `null`), qué hacer al elegir una y «Todas ›».
 */
class EligeLaCategoria(
    val categorias: List<String>,
    val elegida: String?,
    val onElegir: (String) -> Unit,
    val onTodas: () -> Unit,
    /** Vuelta 51: la que entendió Movi, para marcarla en las pastillas si el dueño no eligió otra. */
    val actual: String? = null,
)

/**
 * **Las pastillas de la tarjeta** (vuelta 51, antes dentro de la bandeja): las frecuentes del tipo
 * ([frecuentes], las de Agregar) o, sin el uso a mano, las que el dueño usa ([delDueno], las de la
 * propuesta del server), hasta [CATEGORIAS_EN_LA_TARJETA]. **Nunca una de las que solo escribe Movi**
 * ([esCategoriaQueNoSeElige]): la lista del server trae todas las que el dueño usó, también «Cuota de
 * crédito». Pura.
 */
fun categoriasDeLaTarjeta(frecuentes: List<String>, delDueno: List<String>): List<String> =
    frecuentes.filterNot { esCategoriaQueNoSeElige(it) }.ifEmpty {
        delDueno
            .filterNot { categoriaQueNoDiceNada(it) || esCategoriaQueNoSeElige(it) }
            .take(CATEGORIAS_EN_LA_TARJETA)
    }

/** Cuántas pastillas lleva la tarjeta cuando no hay frecuentes a mano. */
const val CATEGORIAS_EN_LA_TARJETA: Int = 8

/** Vuelta 51: el enlace que lleva a Revisar aviso desde las pastillas abiertas de la tarjeta. */
const val MAS_CAMBIOS: String = "Más cambios ›"

/** El tag de «Más cambios ›» en la tarjeta. */
const val TAG_MAS_CAMBIOS_EN_LA_TARJETA: String = "por-revisar:mas-cambios"

/** El tag de «Cambiar» en la tarjeta de un aviso entendido (vuelta 51: abre las pastillas ahí). */
const val TAG_CAMBIAR_EN_LA_FILA: String = "por-revisar:cambiar"

/** El botón principal de un aviso al que solo le falta la categoría. */
const val ELEGIR_CATEGORIA: String = "Elegir categoría"

/** Vuelta 47: «Elegir categoría» con las pastillas abiertas. */
const val CERRAR_LAS_CATEGORIAS: String = "Cerrar"

/** El tag de «Elegir categoría» en una tarjeta de Por revisar. */
const val TAG_ELEGIR_CATEGORIA_EN_LA_FILA: String = "por-revisar:elegir-categoria"

/** El tag de las pastillas que abre «Elegir categoría». */
const val TAG_CATEGORIAS_EN_LA_TARJETA: String = "por-revisar:categorias-en-la-tarjeta"

/** El tag de «Todas ›» en la tarjeta. */
const val TAG_TODAS_EN_LA_TARJETA: String = "por-revisar:todas-en-la-tarjeta"

/** El tag de la pastilla de [categoria] en la tarjeta. */
fun tagDeCategoriaEnLaTarjeta(categoria: String): String = "por-revisar:categoria:$categoria"
