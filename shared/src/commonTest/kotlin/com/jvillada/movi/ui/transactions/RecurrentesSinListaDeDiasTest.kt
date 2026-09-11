package com.jvillada.movi.ui.transactions

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Recurrentes deja de repetir abajo lo que ya dice arriba.**
 *
 * El dueño: *«Me gusta lo de Ya ocurrieron pero se repiten más abajo agrupando por día, creo que
 * esto no tiene mucho sentido: solo debería tener pendientes y ya ocurrieron, nada más»*.
 *
 * «Ya ocurrieron» no es una lista de movimientos: es la respuesta a «¿este mes ya pagaste el
 * arriendo?», con el pago que lo prueba y un «Deshacer» si no era ese. Repetir abajo esos mismos
 * pagos, ahora sueltos y sin la pregunta encima, obliga a leer dos veces para enterarse de lo
 * mismo.
 */
class RecurrentesSinListaDeDiasTest {

    @Test
    fun `con Recurrentes no se pinta la lista por dia`() {
        assertFalse(mostrarLaListaDeDias(CHIP_RECURRENTES, query = ""))
    }

    @Test
    fun `los demas filtros la siguen pintando`() {
        // Los movimientos siguen enteros donde se leen como movimientos.
        assertTrue(mostrarLaListaDeDias(CHIP_TODO, query = ""))
        assertTrue(mostrarLaListaDeDias(CHIP_GASTOS, query = ""))
        assertTrue(mostrarLaListaDeDias(CHIP_INGRESOS, query = ""))
        assertTrue(mostrarLaListaDeDias(CHIP_POR_CONFIRMAR, query = ""))
        assertTrue(mostrarLaListaDeDias(CHIP_ENTRE_CUENTAS, query = ""))
    }

    @Test
    fun `buscar dentro de Recurrentes vuelve a mostrarla`() {
        // Tercera vez que esta pantalla hace la misma excepción, y por el mismo motivo que
        // `showsInMovements` y `agruparAjustesDeSaldo`: escribir una consulta es pedir
        // explícitamente que algo aparezca.
        assertTrue(mostrarLaListaDeDias(CHIP_RECURRENTES, query = "Netflix"))
    }

    @Test
    fun `el tablero de arriba sigue siendo lo que Recurrentes muestra`() {
        // Lo que queda con ese chip: vencimientos, «ya ocurrieron», candidatas y el resumen.
        assertTrue(mostrarResumenDeRecurrentes(CHIP_RECURRENTES))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_TODO))
    }
}
