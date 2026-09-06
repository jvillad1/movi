package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertEquals

/**
 * PR 1 del rediseño de Recurrentes (2026-09): [nombreRecurrenteDe] es lo que usa Movimientos para
 * el chip «Recurrentes» y la marca en cada fila — la misma comparación por nombre que
 * [equivalenteYaAnotado], pero SIN el sello de ocurrencia (esa lectura es por movimiento y acá se
 * pinta un día entero de una sola vez; ver el KDoc de la función en `RecurringOffer.kt`).
 */
class NombreRecurrenteDeTest {

    private fun evento(
        id: String = "ev_1",
        category: String = "Vivienda",
        description: String = "Arriendo",
        amount: Long = 1_800_000L,
        type: TransactionType = TransactionType.EXPENSE,
        transferId: String? = null,
    ) = FinancialEvent(
        id = id,
        accountId = "acc_1",
        type = type,
        amount = amount,
        category = category,
        description = description,
        timestamp = 1_754_406_000_000L,
        transferId = transferId,
    )

    private fun regla(name: String) = RecurringRule(
        id = "rr_1",
        name = name,
        category = "Vivienda",
        amount = 1_800_000L,
        dayOfMonth = 5,
        type = TransactionType.EXPENSE,
    )

    private fun cobro(nombre: String, status: SubStatus = SubStatus.AUTO) = Subscription(
        id = "sub_$nombre",
        merchantKey = nombre.lowercase(),
        displayName = nombre,
        amount = 44_900L,
        currency = "COP",
        dayOfMonth = 12,
        status = status,
        confidence = SubConfidence.HIGH,
        firstSeen = 0L,
        lastSeen = 0L,
        occurrences = 3,
    )

    @Test
    fun `matchea una regla activa por nombre`() {
        assertEquals(
            "Arriendo",
            nombreRecurrenteDe(evento(description = "Arriendo"), listOf(regla("Arriendo")), emptyList()),
        )
    }

    @Test
    fun `matchea una suscripcion que ya suma`() {
        val activas = nombresDeSuscripcionesQueYaSuman(listOf(cobro("Netflix", SubStatus.CONFIRMED)))
        assertEquals(
            "Netflix",
            nombreRecurrenteDe(evento(description = "Netflix", category = "Entretenimiento"), emptyList(), activas),
        )
    }

    @Test
    fun `no matchea una suscripcion descartada o candidata`() {
        val descartada = nombresDeSuscripcionesQueYaSuman(listOf(cobro("Spotify", SubStatus.DISMISSED)))
        assertNull(nombreRecurrenteDe(evento(description = "Spotify"), emptyList(), descartada))
        val candidata = nombresDeSuscripcionesQueYaSuman(listOf(cobro("Spotify", SubStatus.CANDIDATE)))
        assertNull(nombreRecurrenteDe(evento(description = "Spotify"), emptyList(), candidata))
    }

    @Test
    fun `no matchea nada cuando no hay ninguna correspondencia`() {
        assertNull(nombreRecurrenteDe(evento(description = "Mercado"), listOf(regla("Arriendo")), listOf("Netflix")))
    }

    @Test
    fun `la comparacion ignora mayusculas, tildes y espacios, igual que claveDeNombre`() {
        assertEquals(
            "Educación Hija",
            nombreRecurrenteDe(
                evento(description = "  EDUCACION hija  "),
                listOf(regla("Educación Hija")),
                emptyList(),
            ),
        )
    }

    @Test
    fun `una pata de traspaso nunca matchea, aunque el nombre coincida por accidente`() {
        assertNull(
            nombreRecurrenteDe(
                evento(description = "Arriendo", category = TRANSFER_CATEGORY, transferId = "tr_1"),
                listOf(regla("Arriendo")),
                emptyList(),
            ),
        )
    }

    @Test
    fun `una categoria reservada nunca matchea`() {
        assertNull(
            nombreRecurrenteDe(
                evento(description = "Arriendo", category = OPENING_CATEGORY),
                listOf(regla("Arriendo")),
                emptyList(),
            ),
        )
    }
}
