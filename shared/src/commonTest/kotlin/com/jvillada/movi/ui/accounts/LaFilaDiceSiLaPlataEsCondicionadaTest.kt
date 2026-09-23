package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `una cuenta libre dice su grupo, como siempre`() {
        val fila = subtituloDeLaCuenta(cuenta("Bancolombia Ahorros", AccountType.SAVINGS))
        assertEquals("Dinero", fila)
        assertFalse(esDeUsoCondicionado(cuenta("Bancolombia Ahorros", AccountType.SAVINGS)))
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
        assertEquals("Dinero", subtituloDeLaCuenta(cuenta("Glim", AccountType.SAVINGS, "  ")))
    }
}
