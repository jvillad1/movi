package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 66 · una sola cuenta de «N veces este período»
 *
 * El detalle de Juan Valdez decía «5 veces · $66.800» y Buscar «4 veces · $60.800»: cada uno con su
 * clave, y las partes de una compra repartida contaban como visitas. [vecesEnElComercio] es la cuenta
 * de los dos.
 */
class VecesEnElComercioTest {

    private val ayer = 1_761_700_000_000L

    private fun mov(
        id: String,
        monto: Long,
        descripcion: String,
        merchant: String? = null,
        cuenta: String = "ahorros",
        timestamp: Long = ayer,
        createdAt: Long? = null,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = monto, category = "Restaurantes",
        description = descripcion, merchant = merchant, timestamp = timestamp, createdAt = createdAt,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    /**
     * El caso de la revisión: una compra de $25.100 repartida en dos partes (Comida $13.900 y
     * Restaurantes $11.200, misma cuenta, misma hora, anotadas juntas), otras dos visitas y un «Tinto y
     * pan» en efectivo que es Juan Valdez por la marca.
     */
    private val juanValdez = listOf(
        mov("p1", 13_900, "Juan Valdez Cafe", "JUAN VALDEZ CAFE", timestamp = ayer + 3_000, createdAt = 9_000),
        mov("p2", 11_200, "Juan Valdez Cafe", "JUAN VALDEZ CAFE", timestamp = ayer + 3_000, createdAt = 9_000),
        mov("v2", 12_300, "Juan Valdez Cafe", "JUAN VALDEZ CAFE", timestamp = ayer + 2_000, createdAt = 8_000),
        mov("v3", 23_400, "Juan Valdez Cafe", "JUAN VALDEZ CAFE", timestamp = ayer + 1_000, createdAt = 7_000),
        mov("tinto", 6_000, "Tinto y pan", "Juan Valdez", cuenta = "efectivo", timestamp = ayer - 86_400_000, createdAt = 1_000),
    )

    @Test
    fun `Juan Valdez - la compra repartida en partes cuenta una vez, y el tinto por la marca`() {
        val clave = claveDelComercio(juanValdez.first())!!
        assertEquals("marca:juan_valdez", clave)
        val cuenta = vecesEnElComercio(juanValdez, clave)
        assertEquals(4, cuenta.veces)
        assertEquals(66_800L, cuenta.total)
    }

    @Test
    fun `Exito - dos cafes de ayer anotados a mano son dos visitas aunque compartan el mediodia`() {
        val exito = listOf(
            mov("a", 60_000, "Éxito Suba", "EXITO SUBA", timestamp = ayer, createdAt = 100),
            mov("b", 40_000, "Éxito Suba", "EXITO SUBA", timestamp = ayer, createdAt = 100),
            mov("c", 15_000, "Éxito", "EXITO", timestamp = ayer, createdAt = 200),
            mov("d", 5_000, "Éxito", "EXITO", timestamp = ayer, createdAt = null),
            mov("e", 7_000, "Éxito", "EXITO", timestamp = ayer, createdAt = null),
        )
        val cuenta = vecesEnElComercio(exito, claveDelComercio(exito.first())!!)
        // a+b son una compra en partes; c otra; d y e, sin instante de anotación, una cada una.
        assertEquals(4, cuenta.veces)
        assertEquals(127_000L, cuenta.total)
    }

    @Test
    fun `ni tu parte de lo que pago otro, ni un traspaso, ni otro tipo cuentan`() {
        val base = mov("x", 10_000, "Juan Valdez", "Juan Valdez", createdAt = 1)
        val clave = claveDelComercio(base)!!
        val otros = listOf(
            base.copy(id = "espacio:g1", createdAt = 2),
            base.copy(id = "tr", transferId = "t1", createdAt = 3),
            base.copy(id = "ing", type = TransactionType.INCOME, createdAt = 4),
        )
        assertEquals(1, vecesEnElComercio(listOf(base) + otros, clave, comoEste = base).veces)
        assertNull(claveDelComercio(mov("y", 1, "?!")))
    }
}
