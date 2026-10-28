package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.ui.Screen

/**
 * Las cuatro pestañas de la hoja de «Agregar», con su índice en el selector de arriba. Las usan los
 * atajos (Ola 5) para abrir la hoja directo donde se viene a anotar.
 */
enum class PestanaDeAgregar(internal val indice: Int) {
    GASTO(0),
    INGRESO(1),
    TRASPASO(TIPO_TRASPASO),
    PAGO_DE_TARJETA(TIPO_PAGO_CUOTA),
}

/**
 * # Ola 5 · los atajos del «+»
 *
 * **Mantener presionado** el «+» (la barra del teléfono y el rail) abre un menú corto con lo que se
 * anota casi siempre; en la web y el escritorio también el clic derecho, y en el rail ancho la flecha
 * de «Agregar» (vuelta de diseño 26, ver `BotonDeAgregarConAtajos`). Cada uno abre «Agregar» en su
 * pestaña, «Repartir» abre la hoja del gasto compartido (la de la vuelta 7, `EstadoDeRepartir`) y
 * «Subir comprobante» va al flujo de papeles de siempre («Compartir con Movi», ver
 * `PapelesCompartidos`). **Tocar** el «+» sigue abriendo la hoja como antes.
 *
 * Vuelta de diseño 26: la cuadrícula del Inicio quedó en cuatro acciones, y Traspaso, Pagar tarjeta,
 * Repartir y Comprobante viven acá. Tres ya estaban; «Repartir» llegó de la cuadrícula.
 */
enum class AtajoDeAgregar(val rotulo: String) {
    GASTO("Gasto"),
    INGRESO("Ingreso"),
    TRASPASO("Traspaso"),
    PAGO_DE_TARJETA("Pago de tarjeta"),
    REPARTIR("Repartir"),
    SUBIR_COMPROBANTE("Subir comprobante"),
}

/**
 * La hoja de «Agregar» que abre [atajo], o `null` para «Repartir» y «Subir comprobante», que no son
 * esa hoja: uno lee los espacios y abre la suya, el otro elige un archivo y lo deja en
 * `PapelesCompartidos`.
 */
fun pantallaDelAtajo(atajo: AtajoDeAgregar): Screen.QuickAdd? = when (atajo) {
    AtajoDeAgregar.GASTO -> Screen.QuickAdd(presetPestana = PestanaDeAgregar.GASTO)
    AtajoDeAgregar.INGRESO -> Screen.QuickAdd(presetEsIngreso = true, presetPestana = PestanaDeAgregar.INGRESO)
    AtajoDeAgregar.TRASPASO -> Screen.QuickAdd(presetPestana = PestanaDeAgregar.TRASPASO)
    AtajoDeAgregar.PAGO_DE_TARJETA -> Screen.QuickAdd(presetPestana = PestanaDeAgregar.PAGO_DE_TARJETA)
    AtajoDeAgregar.REPARTIR, AtajoDeAgregar.SUBIR_COMPROBANTE -> null
}

/**
 * **Lo que hace elegir [atajo]** en el menú del «+»: abrir su hoja de «Agregar» ([pantallaDelAtajo]),
 * [repartir] (tocar «Repartir», `EstadoDeRepartir.tocar`) o [elegirComprobante]. Lo llama la cáscara
 * (`App.kt`), que es dueña de los tres.
 */
fun hacerElAtajo(
    atajo: AtajoDeAgregar,
    navegar: (Screen) -> Unit,
    repartir: () -> Unit,
    elegirComprobante: () -> Unit,
) {
    when (atajo) {
        AtajoDeAgregar.REPARTIR -> repartir()
        AtajoDeAgregar.SUBIR_COMPROBANTE -> elegirComprobante()
        else -> pantallaDelAtajo(atajo)?.let(navegar)
    }
}

/**
 * La pestaña con que arranca la hoja: [presetPestana] si vino, y si no lo de siempre —una deuda que
 * pagar abre «Cuota», un ingreso abre «Ingreso», y lo demás «Gasto»—.
 */
internal fun pestanaInicialDeAgregar(presetPestana: PestanaDeAgregar?, presetDeudaId: String?, presetEsIngreso: Boolean): Int =
    when {
        presetPestana != null -> presetPestana.indice
        presetDeudaId != null -> TIPO_PAGO_CUOTA
        presetEsIngreso -> PestanaDeAgregar.INGRESO.indice
        else -> PestanaDeAgregar.GASTO.indice
    }
