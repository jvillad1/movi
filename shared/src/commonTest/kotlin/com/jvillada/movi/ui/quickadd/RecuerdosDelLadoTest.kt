package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.GrupoQueSaldaElAviso
import com.jvillada.movi.shared.model.RecuerdoDeCategoria
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.sms.textoDelGrupoQueSalda
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Vuelta 85 (revisión) · Agregar sugiere con la memoria del lado de la pestaña, y el renglón de
 * «¿Salda…?» dice cuando el giro es el que esperaba un saldado ya marcado.
 */
class RecuerdosDelLadoTest {

    private val loQueSale = RecuerdoDeCategoria("nombre:andresrojas", "Vivienda", "Transferencia a Andrés Rojas", 3)
    private val loQueEntra = RecuerdoDeCategoria("nombre:andresrojas", "Transferencia", "Transferencia de Andrés Rojas", 2, ingreso = true)

    @Test
    fun `en Ingreso sugiere lo que entro y en Gasto lo que salio`() {
        val todos = listOf(loQueSale, loQueEntra)
        assertEquals("Transferencia", sugerenciaPorNombre("Andrés Rojas", recuerdosDelLado(todos, TransactionType.INCOME))?.categoria)
        assertEquals("Vivienda", sugerenciaPorNombre("Andrés Rojas", recuerdosDelLado(todos, TransactionType.EXPENSE))?.categoria)
        // Sin el lado, gana la de gasto también en Ingreso: lo que esto evita.
        assertEquals("Vivienda", sugerenciaPorNombre("Andrés Rojas", todos)?.categoria)
    }

    @Test
    fun `el giro que esperaba un saldado lo dice asi`() {
        val texto = textoDelGrupoQueSalda(
            GrupoQueSaldaElAviso("esp_casa", "Casa con Andrés", "Andrés Rojas", debe = 539_625L, yaMarcadoSaldado = true),
        )
        assertEquals(
            "¿Es el saldado de Casa con Andrés? Marcaste \$539.625 con Andrés Rojas como saldado. Al confirmarlo, Movi te lo pregunta.",
            texto,
        )
    }
}
