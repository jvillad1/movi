package com.jvillada.movi.ui.porrevisar

/*
 * # «Confirmar los N que Movi entendió»
 *
 * Desde la vuelta 44 el lote de la bandeja es el de Actividad: la tarjeta de los avisos
 * (`TarjetaDeAvisosDelBanco`, sin «Revisar uno por uno») y la misma hoja de confirmación
 * (`HojaDeConfirmarLosAvisos`), que confirma uno por uno por el camino de la fila
 * ([confirmarLoEntendido], [confirmarElLote]). Acá queda lo que nombra el lote.
 */

/** Con uno solo, el «Confirmar» de su fila basta: el lote aparece desde dos. */
const val MINIMO_PARA_EL_LOTE: Int = 2

/** El tag de la tarjeta del lote. */
const val TAG_LOTE_DE_LA_BANDEJA: String = "por-revisar:lote"

/** «Confirmar los 3 que Movi entendió». */
fun tituloDelLote(cuantos: Int): String =
    if (cuantos == 1) "Confirmar el que Movi entendió" else "Confirmar los $cuantos que Movi entendió"
