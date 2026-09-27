package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.quickadd.categoriasParaLasPastillasDeAgregar
import com.jvillada.movi.ui.sms.categoriasParaElegirEnElSms
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Ola N — el orden de las pastillas de «Agregar» tiene que coincidir con el de Reconciliar.**
 *
 * Reconciliar (el detalle de un SMS, [categoriasParaElegirEnElSms]) ya pasaba por
 * [categoriasParaPastillas]; «Agregar» ([categoriasParaLasPastillasDeAgregar]) armaba su propia
 * lista con [categoriasFrecuentes] a secas, en vez de esa función común — y las dos podían
 * terminar en un orden distinto (frecuentes, luego propias, luego catálogo) si solo una de las
 * dos la usaba de verdad. Con el mismo `usadas`/`prefs`/`usos` y la misma categoría "adelante", el
 * resultado tiene que ser idéntico.
 */
class PastillasDeCategoriaOrdenCompartidoTest {

    private val gasto = TransactionType.EXPENSE

    private val usadas = mapOf(
        "Hija" to setOf(gasto),
        "Fútbol" to setOf(gasto),
        "Mercado extra" to setOf(gasto),
        "Gimnasio" to setOf(gasto),
    )
    private val prefs = emptyMap<String, CategoryPref>()
    private val usos = mapOf("Hija" to 12, "Fútbol" to 7, "Mercado extra" to 3, "Comida" to 9)

    @Test
    fun `agregar y reconciliar ofrecen el mismo orden para el mismo uso`() {
        val deAgregar = categoriasParaLasPastillasDeAgregar(
            categoriaActual = "Hija",
            tipo = gasto,
            usadas = usadas,
            prefs = prefs,
            usos = usos,
        )
        val deReconciliar = categoriasParaElegirEnElSms(
            propuesta = null,
            tipo = gasto,
            usadas = usadas,
            prefs = prefs,
            usos = usos,
            elegida = "Hija",
            cuantas = 6,
        )

        assertEquals(deReconciliar, deAgregar, "«Agregar» y Reconciliar difieren: $deAgregar vs $deReconciliar")
    }

    @Test
    fun `sin categoria actual pinneada tambien coinciden`() {
        val deAgregar = categoriasParaLasPastillasDeAgregar(
            categoriaActual = "Otros",
            tipo = gasto,
            usadas = usadas,
            prefs = prefs,
            usos = usos,
        )
        val deReconciliar = categoriasParaElegirEnElSms(
            propuesta = "Otros",
            tipo = gasto,
            usadas = usadas,
            prefs = prefs,
            usos = usos,
            cuantas = 6,
        )

        assertEquals(deReconciliar, deAgregar)
    }
}
