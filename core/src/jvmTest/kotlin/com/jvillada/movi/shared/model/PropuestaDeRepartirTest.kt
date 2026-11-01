package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 97 · Movi propone repartir
 *
 * La regla pura: un gasto confirmado de los últimos 15 días, sin grupo, que es un recurrente del hogar
 * o un comercio repartido al menos 2 veces en ese grupo en los últimos 90 días, y que no se descartó.
 */
class PropuestaDeRepartirTest {

    private val dia = 24L * 60 * 60 * 1000
    private val ahora = 1_800_000_000_000L
    private val caro = MiembroDelEspacio("dst_caro", "Caro")
    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), caro))
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 2_400_000, dayOfMonth = 1,
        type = TransactionType.EXPENSE, accountId = "acc",
    )

    private fun gasto(id: String, desc: String, monto: Long, haceDias: Int, merchant: String? = null) = FinancialEvent(
        id = id, accountId = "acc", type = TransactionType.EXPENSE, amount = monto, category = "Mercado",
        description = desc, merchant = merchant, timestamp = ahora - haceDias * dia, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private fun repartido(e: FinancialEvent, reparto: RepartoDelGasto? = null) = GastoRepartido(
        GastoDelEspacio(
            id = "g_${e.id}", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = e.amount, categoria = e.category,
            descripcion = e.description, fecha = e.timestamp, eventoId = e.id,
            partes = partesDelGasto(e.amount, casa, MIEMBRO_YO, reparto), reparto = reparto,
        ),
        e.copy(espacioId = casa.id),
    )

    private fun proponer(
        eventos: List<FinancialEvent>,
        repartidos: List<GastoRepartido> = emptyList(),
        delHogar: Map<String, String> = emptyMap(),
        descartadas: Set<String> = emptySet(),
    ) = propuestasDeRepartir(eventos, repartidos, listOf(casa), listOf(arriendo), delHogar, descartadas, ahora)

    private val delArriendo = gasto("e_arr", "Arriendo", 2_400_000, 2).copy(category = "Vivienda")

    @Test
    fun `un recurrente del hogar se propone en su grupo`() {
        val p = proponer(listOf(delArriendo), delHogar = mapOf(arriendo.id to casa.id)).single()
        assertEquals("e_arr", p.eventoId)
        assertEquals(casa.id, p.espacio.id)
        assertEquals(MotivoDeReparto.RECURRENTE, p.motivo)
        assertEquals(claveDeRepartoDeLaRegla(arriendo.id), p.clave)
        assertEquals(null, p.reparto)
    }

    @Test
    fun `un recurrente sin marcar no se propone`() {
        assertTrue(proponer(listOf(delArriendo)).isEmpty())
    }

    @Test
    fun `un comercio repartido 2 veces en 90 dias se propone con el reparto de la ultima vez`() {
        val setenta = RepartoDelGasto(FormaDeRepartir.PORCENTAJES, porcentajes = mapOf(MIEMBRO_YO to 70, caro.id to 30))
        val antes = listOf(
            repartido(gasto("e_1", "Éxito Laureles", 200_000, 60)),
            repartido(gasto("e_2", "Éxito Laureles", 300_000, 30), setenta),
        )
        val nuevo = gasto("e_3", "Éxito Laureles", 250_000, 1)
        val p = proponer(listOf(nuevo), antes).single()
        assertEquals(MotivoDeReparto.COMERCIO, p.motivo)
        assertEquals(setenta, p.reparto)
        assertTrue(p.clave.startsWith("comercio:"))
    }

    @Test
    fun `por debajo del umbral nada - una sola vez o hace mas de 90 dias`() {
        val nuevo = gasto("e_3", "Éxito Laureles", 250_000, 1)
        assertTrue(proponer(listOf(nuevo), listOf(repartido(gasto("e_1", "Éxito Laureles", 1, 10)))).isEmpty())
        val viejos = listOf(repartido(gasto("e_1", "Éxito Laureles", 1, 100)), repartido(gasto("e_2", "Éxito Laureles", 1, 95)))
        assertTrue(proponer(listOf(nuevo), viejos).isEmpty())
    }

    @Test
    fun `un gasto de hace mas de 15 dias, por confirmar o en dolares no se propone`() {
        val marcado = mapOf(arriendo.id to casa.id)
        assertTrue(proponer(listOf(delArriendo.copy(timestamp = ahora - 16 * dia)), delHogar = marcado).isEmpty())
        assertTrue(proponer(listOf(delArriendo.copy(reconciliationStatus = ReconciliationStatus.UNCONFIRMED)), delHogar = marcado).isEmpty())
        assertTrue(proponer(listOf(delArriendo.copy(currency = "USD")), delHogar = marcado).isEmpty())
    }

    @Test
    fun `ya repartido nada`() {
        assertTrue(proponer(listOf(delArriendo.copy(espacioId = casa.id)), delHogar = mapOf(arriendo.id to casa.id)).isEmpty())
    }

    @Test
    fun `descartado nada - no es compartido`() {
        val p = proponer(listOf(delArriendo), delHogar = mapOf(arriendo.id to casa.id), descartadas = setOf(huellaDeNoEsCompartido("e_arr")))
        assertTrue(p.isEmpty())
    }

    @Test
    fun `nunca para este recurrente o comercio nada`() {
        val marcado = mapOf(arriendo.id to casa.id)
        assertTrue(proponer(listOf(delArriendo), delHogar = marcado, descartadas = setOf(huellaDeNuncaRepartir(claveDeRepartoDeLaRegla(arriendo.id)))).isEmpty())
        val antes = listOf(repartido(gasto("e_1", "Éxito Laureles", 1, 20)), repartido(gasto("e_2", "Éxito Laureles", 1, 10)))
        val nuevo = gasto("e_3", "Éxito Laureles", 250_000, 1)
        val clave = proponer(listOf(nuevo), antes).single().clave
        assertTrue(proponer(listOf(nuevo), antes, descartadas = setOf(huellaDeNuncaRepartir(clave))).isEmpty())
    }

    @Test
    fun `como mucho 2, los recurrentes primero`() {
        val antes = listOf(repartido(gasto("e_1", "Éxito Laureles", 1, 20)), repartido(gasto("e_2", "Éxito Laureles", 1, 10)))
        val nuevos = listOf(gasto("e_3", "Éxito Laureles", 1, 1), gasto("e_4", "Éxito Laureles", 1, 0), delArriendo)
        val p = proponer(nuevos, antes, delHogar = mapOf(arriendo.id to casa.id))
        assertEquals(listOf("e_arr", "e_4"), p.map { it.eventoId })
    }

    @Test
    fun `el recurrente se reconoce por cuenta, categoria y monto aunque se llame distinto`() {
        val conOtroNombre = delArriendo.copy(description = "Transferencia a Inmobiliaria")
        assertTrue(esElRecurrente(conOtroNombre, arriendo))
        assertTrue(!esElRecurrente(conOtroNombre.copy(amount = 2_400_001), arriendo))
    }
}
