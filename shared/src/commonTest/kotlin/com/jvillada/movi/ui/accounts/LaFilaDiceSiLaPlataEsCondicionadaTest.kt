package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Una cuenta de uso condicionado no se lee como plata disponible.**
 *
 * En Cuentas, el 23-sep: arriba «Tu plata $558.350» y abajo «DINERO · 5 · $20.556.753», con Nu
 * ($13.515.164, ahorro) y la AFC Davibank ($6.894.302, vivienda) en el mismo verde y con el mismo
 * subtítulo «Dinero» que Bancolombia Ahorros. Dos cifras distintas para lo que parece lo mismo — lo
 * que el dueño pidió que no pasara: *«ese dinero está reservado, debería mostrarlo como un detalle
 * pero no es dinero disponible»*.
 */
class LaFilaDiceSiLaPlataEsCondicionadaTest {

    private fun cuenta(nombre: String, tipo: AccountType, condicion: String? = null) =
        Account(id = nombre, name = nombre, type = tipo, balance = 1_000L, condicionadaA = condicion)

    /**
     * Vuelta de diseño 2: dentro de la sección «Dinero · 2» cada cuenta decía «Dinero». Ahora dice su
     * tipo, y nada si el nombre ya lo dice o si es una inversión (la sección ya lo dice).
     */
    @Test
    fun `una cuenta libre dice su tipo, no el grupo que ya dice la seccion`() {
        assertEquals("Ahorros", subtituloDeLaCuenta(cuenta("Nu", AccountType.SAVINGS)))
        assertEquals("Corriente", subtituloDeLaCuenta(cuenta("Davivienda", AccountType.CHECKING)))
        assertEquals("Efectivo", subtituloDeLaCuenta(cuenta("Billetera", AccountType.CASH)))
        assertFalse(esDeUsoCondicionado(cuenta("Bancolombia Ahorros", AccountType.SAVINGS)))
    }

    @Test
    fun `si el nombre ya dice el tipo, o es una inversion, no hay subtitulo`() {
        assertNull(subtituloDeLaCuenta(cuenta("Bancolombia Ahorros", AccountType.SAVINGS)))
        assertNull(subtituloDeLaCuenta(cuenta("Cuenta de ahorros 8133", AccountType.SAVINGS)))
        assertNull(subtituloDeLaCuenta(cuenta("Efectivo", AccountType.CASH)))
        assertNull(subtituloDeLaCuenta(cuenta("Skandia", AccountType.INVESTMENT)))
        listOf("Dinero", "Inversión").forEach { grupo ->
            listOf(AccountType.SAVINGS, AccountType.CHECKING, AccountType.CASH, AccountType.INVESTMENT).forEach { tipo ->
                assertNotEquals(grupo, subtituloDeLaCuenta(cuenta("Nu", tipo)))
            }
        }
    }

    @Test
    fun `una cuenta condicionada dice para que es la plata`() {
        assertEquals("Uso condicionado · ahorro", subtituloDeLaCuenta(cuenta("Nu", AccountType.SAVINGS, "ahorro")))
        assertEquals("Uso condicionado · vivienda", subtituloDeLaCuenta(cuenta("AFC Davibank 9497", AccountType.SAVINGS, "vivienda")))
        assertEquals(
            "Uso condicionado · pensión voluntaria",
            subtituloDeLaCuenta(cuenta("Skandia pensión voluntaria", AccountType.INVESTMENT, "pensión voluntaria")),
        )
        assertTrue(esDeUsoCondicionado(cuenta("Nu", AccountType.SAVINGS, "ahorro")))
    }

    @Test
    fun `una condicion en blanco es plata libre`() {
        assertFalse(esDeUsoCondicionado(cuenta("Glim", AccountType.SAVINGS, "  ")))
        assertEquals("Ahorros", subtituloDeLaCuenta(cuenta("Glim", AccountType.SAVINGS, "  ")))
    }

    /**
     * Vuelta de diseño 5: la fila del detalle de la cuenta era «¿Solo sirve para algo?», una pregunta
     * que no se entendía. Ahora dice el estado: «Para qué es esta plata» · «Sin condición: para cualquier
     * gasto», o para qué es.
     */
    @Test
    fun `la fila del detalle dice si la plata es libre o para que es`() {
        assertEquals("Para qué es esta plata", PARA_QUE_ES_ESTA_PLATA)
        assertEquals("Sin condición: para cualquier gasto", subtituloDeParaQueEs(cuenta("Bancolombia Ahorros", AccountType.SAVINGS)))
        assertEquals("Sin condición: para cualquier gasto", subtituloDeParaQueEs(cuenta("Glim", AccountType.SAVINGS, "  ")))
        assertEquals(
            "Solo para vivienda · no suma en «Tu plata»",
            subtituloDeParaQueEs(cuenta("AFC Davibank 9497", AccountType.SAVINGS, " vivienda ")),
        )
    }
}
