package com.jvillada.movi.ui.sms

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
)

/** El botón principal de un aviso al que solo le falta la categoría. */
const val ELEGIR_CATEGORIA: String = "Elegir categoría"

/** El tag de «Elegir categoría» en una tarjeta de Por revisar. */
const val TAG_ELEGIR_CATEGORIA_EN_LA_FILA: String = "por-revisar:elegir-categoria"

/** El tag de las pastillas que abre «Elegir categoría». */
const val TAG_CATEGORIAS_EN_LA_TARJETA: String = "por-revisar:categorias-en-la-tarjeta"

/** El tag de «Todas ›» en la tarjeta. */
const val TAG_TODAS_EN_LA_TARJETA: String = "por-revisar:todas-en-la-tarjeta"

/** El tag de la pastilla de [categoria] en la tarjeta. */
fun tagDeCategoriaEnLaTarjeta(categoria: String): String = "por-revisar:categoria:$categoria"
