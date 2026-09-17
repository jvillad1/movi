package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # La moneda del movimiento que arma la hoja de «Agregar»
 *
 * El defecto que estas pruebas cierran, dicho con las cuentas del dueño: elegir la **Master Black
 * (USD)**, tipear 500000 y guardar. La hoja decía «$500.000 · COP», el evento se armaba sin
 * `currency` —o sea con el default `"COP"` del modelo— y, como `encodeDefaults` está apagado, la
 * clave no viajaba: el server la completaba con la de la cuenta y guardaba **US$500.000**. El
 * teléfono, en cambio, sí guardaba «COP» en su espejo, así que `deltaDelEspejo` movía la columna
 * de pesos por el número crudo de un gasto en dólares.
 *
 * Una tarjeta de crédito es un origen de primera clase en el selector de un gasto, así que el
 * camino no era raro: era el normal para la única tarjeta en dólares que él tiene.
 */
class MovimientoDeLaHojaTest {

    private val masterBlack = Account("c-usd", "Master Black", AccountType.CREDIT_CARD, 0L, currency = "USD")
    private val ahorros = Account("a-cop", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000L)

    @Test
    fun un_gasto_sobre_una_cuenta_en_dolares_se_anota_en_dolares() {
        val evento = movimiento(cuentaId = masterBlack.id)

        assertEquals("USD", evento.currency, "la moneda sale de la cuenta elegida, no del default")
    }

    @Test
    fun un_gasto_sobre_una_cuenta_en_pesos_se_anota_en_pesos() {
        val evento = movimiento(cuentaId = ahorros.id)

        assertEquals("COP", evento.currency)
    }

    /**
     * **Sin la lista de cuentas no se inventa nada.** Es el caso «B3» que la hoja ya tenía
     * anotado: `getAccounts()` falla y la hoja se abrió con un `presetAccountId`, así que la cuenta
     * está elegida pero no se sabe nada de ella. Queda el default del modelo, que es exactamente lo
     * que pasaba antes de este arreglo para todos los movimientos: la clave no viaja y el server la
     * rellena con la de la cuenta.
     */
    @Test
    fun sin_la_lista_de_cuentas_queda_el_default_del_modelo() {
        val evento = movimientoDeLaHoja(
            id = "ev-1", cuentaId = "c-usd", cuentas = emptyList(),
            tipo = TransactionType.EXPENSE, monto = 500_000L,
            categoria = "Comida", nota = "", timestamp = 1_700_000_000_000L,
        )

        assertEquals("COP", evento.currency)
    }

    /** El id viene de afuera — es lo que hace idempotente al reintento. Ver `idDelBorrador`. */
    @Test
    fun el_id_es_el_que_le_pasan_y_no_uno_nuevo() {
        assertEquals("ev-del-borrador", movimiento(id = "ev-del-borrador").id)
    }

    /** Lo de siempre, para que el refactor no se lleve nada puesto por el camino. */
    @Test
    fun sin_nota_el_concepto_es_la_categoria() {
        assertEquals("Comida", movimiento(nota = "").description)
        assertEquals("Almuerzo con Ana", movimiento(nota = "Almuerzo con Ana").description)
    }

    private fun movimiento(
        id: String = "ev-1",
        cuentaId: String = ahorros.id,
        nota: String = "",
    ) = movimientoDeLaHoja(
        id = id,
        cuentaId = cuentaId,
        cuentas = listOf(ahorros, masterBlack),
        tipo = TransactionType.EXPENSE,
        monto = 500_000L,
        categoria = "Comida",
        nota = nota,
        timestamp = 1_700_000_000_000L,
    )
}
