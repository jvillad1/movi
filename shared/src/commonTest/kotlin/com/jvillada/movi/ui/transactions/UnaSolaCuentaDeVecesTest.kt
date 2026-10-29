package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.buscar.DatosDeBusqueda
import com.jvillada.movi.ui.buscar.RangoDelPeriodo
import com.jvillada.movi.ui.buscar.ResultadoDeBusqueda
import com.jvillada.movi.ui.buscar.TipoDeResultado
import com.jvillada.movi.ui.buscar.buscarEnMovi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 66 · el detalle y Buscar dicen el mismo «N veces este período»
 *
 * La revisión: el detalle de Juan Valdez decía «5 veces · $66.800» y Buscar «4 veces · $60.800». Los
 * dos cuentan ahora con `vecesEnElComercio` de `:core`: la marca junta el «Tinto y pan», y la compra
 * repartida en dos partes es una visita.
 */
class UnaSolaCuentaDeVecesTest {

    private val ayer = 1_500_000L

    private fun mov(id: String, monto: Long, descripcion: String, merchant: String, cuenta: String = "ahorros", t: Long = ayer, creado: Long) =
        FinancialEvent(
            id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = monto, category = "Restaurantes",
            description = descripcion, merchant = merchant, timestamp = t, createdAt = creado,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        )

    private val delPeriodo = listOf(
        mov("p1", 13_900, "Juan Valdez Cafe", "JUAN VALDEZ CAFE", t = ayer + 30, creado = 90),
        mov("p2", 11_200, "Juan Valdez Cafe", "JUAN VALDEZ CAFE", t = ayer + 30, creado = 90),
        mov("v2", 12_300, "Juan Valdez Cafe", "JUAN VALDEZ CAFE", t = ayer + 20, creado = 80),
        mov("v3", 23_400, "Juan Valdez Cafe", "JUAN VALDEZ CAFE", t = ayer + 10, creado = 70),
        mov("tinto", 6_000, "Tinto y pan", "Juan Valdez", cuenta = "efectivo", t = ayer - 100, creado = 10),
    )

    @Test
    fun `el detalle y Buscar cuentan 4 veces y 66800 en Juan Valdez`() {
        val detalle = enEsteComercio(delPeriodo.single { it.id == "v2" }, delPeriodo)!!
        assertEquals(4, detalle.veces)
        assertEquals(66_800L, detalle.total)

        val comercio = buscarEnMovi("juan", DatosDeBusqueda(eventos = delPeriodo), periodo = RangoDelPeriodo(1_000_000L, 2_000_000L))
            .single { it.tipo == TipoDeResultado.COMERCIO }.resultados.single() as ResultadoDeBusqueda.Comercio
        assertEquals(detalle.veces, comercio.enElPeriodo)
        assertEquals(detalle.total, comercio.totalDelPeriodo)
        assertEquals("JUAN VALDEZ CAFE", comercio.nombre, "el nombre que más se repite")
    }
}
