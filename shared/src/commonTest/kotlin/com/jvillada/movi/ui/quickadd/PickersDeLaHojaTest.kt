package com.jvillada.movi.ui.quickadd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La máquina de estados de los sub-pickers de la hoja de «Agregar».
 *
 * Esta hoja lleva nueve rondas de arreglos y las ocho primeras las atrapó alguien MIDIENDO a
 * ojo. Lo que se afirma acá es la pregunta de la que cuelga el invariante caro —*el teclado
 * numérico no se mueve bajo el dedo*—: **¿hay algún sub-picker abierto?**. Mientras esa
 * respuesta sea correcta, `LaunchedEffect(hayPicker)` sigue disparando y el desplazamiento se
 * restaura al cerrar; cuando se quedó pegada en «sí», la restauración murió en las tres pestañas
 * y el dedo que iba al «8» caía en el «5».
 *
 * Lo que estas pruebas NO cubren, dicho para que nadie las lea como una garantía de más: la
 * geometría (altos, huecos, desplazamiento en píxeles) no vive acá y se sigue verificando a ojo.
 */
class PickersDeLaHojaTest {

    // ── Lo básico: abrir y cerrar un sub-picker de la propia pantalla ──────────────────

    @Test
    fun `recien abierta la hoja no hay ningun sub-picker`() {
        val inicial = PickersDeLaHoja()

        assertFalse(inicial.hayPicker)
        assertTrue(inicial.cuerpoCompuesto)
        assertEquals(0, inicial.typeIndex)
    }

    @Test
    fun `abrir y cerrar Categoria enciende y apaga hayPicker`() {
        val abierto = PickersDeLaHoja().abrir(Picker.Category)
        assertTrue(abierto.hayPicker)
        assertFalse(abierto.cuerpoCompuesto)

        assertFalse(abierto.cerrar().hayPicker)
    }

    @Test
    fun `el sub-picker propio sobrevive al cambio de pestaña y se puede cerrar despues`() {
        // Verificado ejecutando: abrir «Nota» en Gasto y tocar «Traspaso» restaura bien, porque
        // el picker propio es estado de la pantalla y nadie lo saca de composición.
        val enTraspaso = PickersDeLaHoja().abrir(Picker.Note).conTipo(TIPO_TRASPASO)

        assertEquals(Picker.Note, enTraspaso.propio)
        assertTrue(enTraspaso.hayPicker)
        assertFalse(enTraspaso.cerrar().hayPicker)
    }

    /**
     * **La excepción de la Ola 15: el selector de CUENTA sí se cierra al cambiar de pestaña.**
     *
     * Dejó de mostrar la misma lista en todas las pestañas — ahora depende del uso
     * (`cuentasPara`)—, así que con «Cuenta» abierto tocar «Ingreso» hacía desaparecer la Nu y la
     * AMEX, aparecer a Skandia y saltar la marca de selección: filas moviéndose bajo un dedo que ya
     * estaba apoyado. Cerrarlo es el arreglo más chico que lo evita, y deja la lista nueva
     * empezando desde arriba en vez de reescribiéndose en el lugar.
     */
    @Test
    fun `el sub-picker de Cuenta si se cierra al cambiar de pestaña`() {
        val enIngreso = PickersDeLaHoja().abrir(Picker.Wallet).conTipo(1)

        assertEquals(Picker.None, enIngreso.propio)
        assertFalse(enIngreso.hayPicker)
        assertTrue(enIngreso.cuerpoCompuesto)
    }

    @Test
    fun `volver a tocar la pestaña en la que ya se esta no cierra el de Cuenta`() {
        // No cambió ninguna lista, así que no hay ninguna fila que se mueva: cerrarlo acá sería
        // castigar un toque que no hizo nada.
        val abierto = PickersDeLaHoja(typeIndex = 1).abrir(Picker.Wallet)

        assertEquals(abierto, abierto.conTipo(1))
    }

    // ── El sub-picker de Traspaso, que es estado de otro @Composable ───────────────────

    @Test
    fun `el sub-picker de traspaso cuenta como picker abierto`() {
        val abierto = PickersDeLaHoja(typeIndex = TIPO_TRASPASO).conPickerDeTraspaso(true)

        assertTrue(abierto.hayPicker)
        assertFalse(abierto.cuerpoCompuesto)
        assertFalse(abierto.conPickerDeTraspaso(false).hayPicker)
    }

    /**
     * **La fuga de la novena ronda.** Entrar a Traspaso, tocar «Desde», ver la lista, darse
     * cuenta de que era un gasto y tocar «Gasto»: [TypeSegments] vive fuera del `Box`, así que
     * sigue tocable con el sub-picker abierto. [TransferBody] sale de composición con su
     * `picking` puesto y nadie avisa que se cerró.
     *
     * Antes de este arreglo el reflejo quedaba en `true` de por vida: `hayPicker` no volvía a
     * cambiar, `LaunchedEffect(hayPicker)` no se disparaba nunca más y **cada** ida y vuelta
     * posterior a Categoría/Cuenta/Nota tiraba el teclado al tope de la hoja, sin ninguna señal.
     */
    @Test
    fun `salir de Traspaso con su sub-picker abierto lo suelta`() {
        val enTraspaso = PickersDeLaHoja(typeIndex = TIPO_TRASPASO).conPickerDeTraspaso(true)
        assertTrue(enTraspaso.hayPicker)

        val enGasto = enTraspaso.conTipo(0)

        assertFalse(enGasto.deTraspaso, "el reflejo del picker de TransferBody quedó pegado")
        assertFalse(enGasto.hayPicker, "hayPicker no vuelve a cambiar y la restauración muere")
        assertTrue(enGasto.cuerpoCompuesto)
    }

    @Test
    fun `y despues de eso abrir y cerrar un sub-picker vuelve a mover hayPicker`() {
        // El síntoma real no era el cambio de pestaña en sí: era que TODO viaje posterior a un
        // sub-picker dejaba de restaurar el desplazamiento. Esto afirma que el ciclo revive.
        val despues = PickersDeLaHoja(typeIndex = TIPO_TRASPASO).conPickerDeTraspaso(true).conTipo(0)

        val conCuenta = despues.abrir(Picker.Wallet)
        assertTrue(conCuenta.hayPicker)
        assertFalse(conCuenta.cerrar().hayPicker)
    }

    @Test
    fun `volver a tocar la pestaña en la que ya se esta no cierra nada`() {
        // No desaparece ningún formulario, así que no hay nada que soltar.
        val enTraspaso = PickersDeLaHoja(typeIndex = TIPO_TRASPASO).conPickerDeTraspaso(true)

        assertEquals(enTraspaso, enTraspaso.conTipo(TIPO_TRASPASO))
    }

    /**
     * El invariante estructural: fuera de la pestaña de traspaso ese sub-picker no existe, así
     * que su reflejo no se puede encender ni con un aviso tardío —uno que llegue después de que
     * la pestaña cambió, que es exactamente el orden en que corre `onDispose`.
     */
    @Test
    fun `un aviso de apertura fuera de Traspaso no enciende nada`() {
        for (tipo in listOf(0, 1)) {
            val estado = PickersDeLaHoja(typeIndex = tipo).conPickerDeTraspaso(true)

            assertFalse(estado.deTraspaso, "se encendió el picker de traspaso en la pestaña $tipo")
            assertFalse(estado.hayPicker)
        }
    }

    @Test
    fun `cerrar el propio no apaga el de traspaso ni al reves`() {
        // Los dos reflejos son independientes: cerrar uno no puede dar por cerrado el otro, o
        // volvemos a tener un `hayPicker` que miente.
        val ambos = PickersDeLaHoja(typeIndex = TIPO_TRASPASO)
            .abrir(Picker.Note)
            .conPickerDeTraspaso(true)

        assertTrue(ambos.cerrar().hayPicker, "todavía queda abierto el de traspaso")
        assertTrue(ambos.conPickerDeTraspaso(false).hayPicker, "todavía queda abierto el propio")
        assertFalse(ambos.cerrar().conPickerDeTraspaso(false).hayPicker)
    }
}
