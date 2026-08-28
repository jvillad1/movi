package com.jvillada.movi.ui.quickadd

/**
 * La pestaña «Traspaso» del selector de arriba de la hoja de «Agregar» ([TypeSegments]): la
 * única que dibuja [TransferBody], que es el único formulario con un sub-picker propio.
 */
internal const val TIPO_TRASPASO = 2

/** Cuál de los sub-pickers de la hoja de «Agregar» está tapando el cuerpo del editor. */
internal sealed class Picker {
    data object None : Picker()
    data object Category : Picker()
    data object Wallet : Picker()
    data object Note : Picker()

    /**
     * Ola 13: el selector de fecha del movimiento. Es un sub-picker **propio de la hoja** —lo
     * dibuja [QuickAddScreen] con su propio `fecha`, reemplazando el cuerpo del editor—, así que
     * entra acá y no por [PickersDeLaHoja.conPickerDeTraspaso]. El de la pestaña Traspaso es
     * otro: vive adentro de [TransferBody] con la fecha del traspaso, y se refleja por
     * [PickersDeLaHoja.deTraspaso] como el de cuentas.
     */
    data object Date : Picker()
}

/**
 * Qué está abierto en la hoja de «Agregar», en un solo estado y con transiciones puras.
 *
 * **Por qué esto vive afuera del `@Composable`.** La pregunta «¿hay algún sub-picker abierto?»
 * gobierna las dos mitades de la disciplina que sostiene el invariante más caro de esta hoja —
 * *el teclado numérico no se mueve bajo el dedo*—: el alto que se le fija al sub-picker y la
 * restauración del desplazamiento al cerrarlo (ver `recordarScroll` en [QuickAddScreen]). Esa
 * pregunta se contestaba con dos variables sueltas, `picker` acá y un espejo de `picking`, que
 * es estado de adentro de [TransferBody]. Un espejo se queda viejo: bastaba abrir «Desde» en
 * Traspaso y tocar «Gasto» —[TypeSegments] vive fuera del `Box`, así que sigue tocable con el
 * sub-picker abierto— para que [TransferBody] saliera de composición sin avisar que su picker se
 * había ido. El espejo quedaba en `true` de por vida, `hayPicker` no volvía a cambiar nunca, y
 * la restauración del desplazamiento moría en las TRES pestañas: cada ida y vuelta a
 * Categoría/Cuenta/Nota tiraba el teclado al tope de la hoja, sin ninguna señal, hasta cerrar y
 * reabrir. Medido: el mismo scroll (60) daba `shift 0` viniendo de Gasto y `shift -60` viniendo
 * de Traspaso — el dedo que iba al «8» caía en el «5».
 *
 * Con el tipo adentro del mismo estado el espejo no puede quedar colgado: **la única forma de
 * cambiar de pestaña es [conTipo], y esa transición suelta el picker de traspaso**. Y como es
 * una función pura, eso se afirma en `PickersDeLaHojaTest` sin levantar un teléfono — que es el
 * punto: esta hoja lleva ocho rondas de arreglos atrapados a ojo y ninguna atrapada por una
 * prueba. Es el mismo camino que se tomó con `nextMoneyField` cuando el campo del monto produjo
 * dos bugs seguidos de guardar otra cifra.
 *
 * Invariante que sostienen las transiciones, y que la prueba afirma: **[deTraspaso] solo puede
 * ser cierto en la pestaña [TIPO_TRASPASO]**, que es la única donde ese sub-picker existe.
 *
 * @param typeIndex la pestaña elegida: 0 Gasto, 1 Ingreso, [TIPO_TRASPASO] Traspaso.
 * @param propio el sub-picker de esta pantalla (Categoría, Cuenta, Nota), o [Picker.None].
 * @param deTraspaso el sub-picker que [TransferBody] tenga abierto —el de cuentas
 *   («Desde»/«Hacia») o, desde la Ola 13, el de su fecha—, que es estado de allá adentro y acá
 *   solo se refleja.
 */
internal data class PickersDeLaHoja(
    val typeIndex: Int = 0,
    val propio: Picker = Picker.None,
    val deTraspaso: Boolean = false,
) {
    /** Hay un sub-picker abierto, sea de esta pantalla o el de la pestaña de traspaso. */
    val hayPicker: Boolean get() = propio != Picker.None || deTraspaso

    /** El cuerpo del editor está compuesto y se puede medir: no hay ningún sub-picker tapándolo. */
    val cuerpoCompuesto: Boolean get() = !hayPicker

    /** Se tocó una fila que abre un sub-picker de esta pantalla. */
    fun abrir(destino: Picker): PickersDeLaHoja = copy(propio = destino)

    /** Se cerró el sub-picker de esta pantalla (con su X, o eligiendo un valor). */
    fun cerrar(): PickersDeLaHoja = copy(propio = Picker.None)

    /**
     * [TransferBody] avisa que abrió o cerró su sub-picker de cuentas.
     *
     * El `&&` no es paranoia decorativa: es lo que hace que un aviso tardío —uno que llegue
     * cuando la pestaña ya cambió— no pueda volver a encender el espejo.
     */
    fun conPickerDeTraspaso(abierto: Boolean): PickersDeLaHoja =
        copy(deTraspaso = abierto && typeIndex == TIPO_TRASPASO)

    /**
     * Se eligió otra pestaña en [TypeSegments].
     *
     * Salir de Traspaso saca a [TransferBody] de composición y con él a su `picking`, así que el
     * espejo se suelta acá. Volver a tocar la pestaña en la que ya se está no cierra nada: no
     * desaparece ningún formulario.
     *
     * El sub-picker propio NO se cierra a propósito: sigue siendo estado de esta pantalla,
     * sobrevive al cambio de pestaña y se puede cerrar normalmente (verificado ejecutando —
     * abrir «Cuenta» en Gasto y tocar «Traspaso» restaura bien).
     */
    fun conTipo(nuevo: Int): PickersDeLaHoja =
        if (nuevo == typeIndex) this else copy(typeIndex = nuevo, deTraspaso = false)
}
