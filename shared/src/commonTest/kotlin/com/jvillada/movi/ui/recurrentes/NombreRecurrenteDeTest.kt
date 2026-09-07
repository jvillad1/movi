package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertFalse
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

    // ── La cuota de un crédito ya pagada ──────────────────────────────────────
    //
    // El dueño: «en recurrentes no estoy viendo los pagos de cuota realizados para mis créditos».
    // Sus ocho créditos son ~$15.500.000 mensuales de su bolsillo, o sea lo más grande de su flujo
    // de caja, y no aparecían: las dos patas llevan `transferId` y el corte de arriba las mataba.
    //
    // Se reconoce por FORMA y no por nombre, porque no hay nombre contra el cual comparar — la
    // regla de cada crédito la fabrica el server al vuelo (`CREDIT_RULE_PREFIX`) y nunca llega en
    // `GET /api/recurring-rules`. Por eso estas pruebas pasan listas vacías: si alguien
    // "simplificara" esto a un match por nombre, todas fallarían.

    /** Las dos patas que escribe `pagoDeCuotaLegs` para la cuota del carro. */
    private fun cuotaDinero(nombreDelCredito: String = "Vehículo") = evento(
        id = "ev_cuota_dinero",
        category = CUOTA_CATEGORY,
        description = "Cuota de $nombreDelCredito",
        amount = 4_215_223L,
        type = TransactionType.EXPENSE,
        transferId = "tr_cuota",
    )

    private fun cuotaDeuda() = evento(
        id = "ev_cuota_deuda",
        category = CUOTA_CATEGORY,
        description = "Abono a capital desde Bancolombia",
        amount = 1_733_905L,
        type = TransactionType.INCOME,
        transferId = "tr_cuota",
    )

    @Test
    fun `la pata del dinero de una cuota se reconoce y se llama como el credito`() {
        assertEquals("Cuota de Vehículo", nombreRecurrenteDe(cuotaDinero(), emptyList(), emptyList()))
        assertEquals("Cuota de Vehículo", nombreDeCuotaPagada(cuotaDinero()))
    }

    /**
     * La pata de la deuda es el OTRO LADO del mismo hecho. Si también se reconociera, la lista
     * mostraría dos filas por cada cuota justo donde el dueño está sumando lo que le sale al mes.
     * Las separa el `type`: la del dinero es EXPENSE, la de la deuda INCOME.
     */
    @Test
    fun `la pata de la deuda no se reconoce, para no contar la cuota dos veces`() {
        assertNull(nombreDeCuotaPagada(cuotaDeuda()))
        assertNull(nombreRecurrenteDe(cuotaDeuda(), emptyList(), emptyList()))
    }

    /**
     * **El pago de una tarjeta NO es un gasto recurrente**, y esta es la prueba que lo fija.
     *
     * Tiene exactamente la misma forma que la cuota —un par enlazado, la plata sale de una cuenta
     * y baja una deuda— pero las compras ya contaron cuando se hicieron: contar también el pago
     * sería contar la misma plata dos veces. Lo que lo distingue es la categoría reservada
     * [CARD_PAYMENT_CATEGORY], que es la que la app escribe en la pata del dinero de una tarjeta.
     */
    @Test
    fun `un pago de tarjeta no se reconoce como recurrente por ninguna de sus dos patas`() {
        val dinero = evento(
            id = "ev_tarjeta_dinero",
            category = CARD_PAYMENT_CATEGORY,
            description = "Pago de Nubank",
            amount = 1_200_000L,
            type = TransactionType.EXPENSE,
            transferId = "tr_tarjeta",
        )
        val deuda = evento(
            id = "ev_tarjeta_deuda",
            category = CARD_PAYMENT_CATEGORY,
            description = "Pago desde Bancolombia",
            amount = 1_200_000L,
            type = TransactionType.INCOME,
            transferId = "tr_tarjeta",
        )
        assertNull(nombreDeCuotaPagada(dinero))
        assertNull(nombreDeCuotaPagada(deuda))
        assertNull(nombreRecurrenteDe(dinero, emptyList(), emptyList()))
        assertNull(nombreRecurrenteDe(deuda, emptyList(), emptyList()))
    }

    /** Un traspaso entre cuentas propias tampoco: no es plata que salió del bolsillo. */
    @Test
    fun `un traspaso entre cuentas propias no se reconoce como cuota`() {
        val salida = evento(
            id = "ev_tr_out",
            category = TRANSFER_CATEGORY,
            description = "Traspaso a CDT",
            type = TransactionType.EXPENSE,
            transferId = "tr_1",
        )
        assertNull(nombreDeCuotaPagada(salida))
        assertNull(nombreRecurrenteDe(salida, emptyList(), emptyList()))
    }

    /**
     * Y **la regla vieja sigue viva**: un gasto normal que matchea una regla real se reconoce
     * igual que siempre. El camino nuevo se suma, no reemplaza.
     */
    @Test
    fun `un gasto normal que matchea una regla se sigue reconociendo`() {
        assertEquals(
            "Arriendo",
            nombreRecurrenteDe(evento(description = "Arriendo"), listOf(regla("Arriendo")), emptyList()),
        )
        assertNull(nombreDeCuotaPagada(evento(description = "Arriendo")))
    }

    /**
     * **Las dos funciones divergen a propósito.** Que la cuota se LEA como recurrente no la hace
     * candidata a que le ofrezcan CREAR una regla: `RecurringRule` no modela un par y el crédito
     * ya arma su propio recordatorio. Si alguien vuelve a unificar las guardas, esto falla.
     */
    @Test
    fun `una cuota se lee como recurrente pero no se puede crear una regla desde ella`() {
        assertEquals("Cuota de Vehículo", nombreRecurrenteDe(cuotaDinero(), emptyList(), emptyList()))
        assertFalse(puedeOfrecerseComoRecurrenteDesdeElDetalle(cuotaDinero()))
    }
}
