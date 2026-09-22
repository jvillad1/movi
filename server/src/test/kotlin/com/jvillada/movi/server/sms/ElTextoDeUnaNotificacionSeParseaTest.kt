package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **La otra mitad de la captura por notificaciones**, la que vive de este lado del cable.
 *
 * El teléfono arma el texto (`textoDeLaNotificacion` en `:shared`: título + cuerpo en una línea) y
 * lo sube por `POST /api/sms/sync`; acá se fija que ese texto —el de verdad, el de la app del
 * banco— lo pueda leer el MISMO `parseSms` que lee los SMS. Cada mitad se podía probar verde por
 * separado sin que nadie fijara que se encuentran en el medio.
 *
 * El segundo caso es la fila que dejó la prueba de punta a punta del 21-sep contra el teléfono del
 * dueño: «Bancolombia:» y nada más (el porqué —las comillas de `adb shell`— está en el KDoc de
 * `EscuchaDeNotificaciones`). No es un movimiento a medias: no es nada. `parseSms` no le encuentra
 * monto, así que una fila así queda en la bandeja esperando una confirmación que nunca va a poder
 * darse y solo se saca a mano — por eso el teléfono ni siquiera la sube (`MotivoDeDescarte.SIN_TEXTO`).
 */
class ElTextoDeUnaNotificacionSeParseaTest {

    /** Lo que la app de Bancolombia publica como `bigText`, ya juntado con su título. */
    private val DE_LA_NOTIFICACION =
        "Bancolombia: Compraste \$12.345,00 en PRUEBA DE MOVI con tu T.Deb *4057, el 21/09/2026 a las 14:00."

    @Test
    fun `la alerta de compra de la app se lee igual que un SMS`() {
        val parsed = assertNotNull(parseSms(DE_LA_NOTIFICACION), "no parseó: $DE_LA_NOTIFICACION")
        assertEquals(12_345.0, parsed.amount)
        assertEquals("COP", parsed.currency)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("PRUEBA DE MOVI", parsed.merchant)
    }

    /**
     * **Las dos compras de Nu que el dueño tenía en el teléfono**, tal como las junta
     * `textoDeLaNotificacion` (título + «: » + cuerpo recortado). `FiltroDeNotificacionesTest` fija
     * del otro lado que el teléfono arma exactamente estas cadenas.
     *
     * Antes de este cambio la primera se leía con el comercio «CREPES Y WAFFLES LEMON por $130»: el
     * lector genérico de «en …» cortaba en el primer punto, que es el de miles del monto.
     */
    private val COMPRA_NU_CREPES =
        "Compra aprobada por \$130.200,00: Tu compra en CREPES Y WAFFLES LEMON por \$130.200,00 " +
            "con tu tarjeta terminada en 1336 ha sido APROBADA."
    private val COMPRA_NU_GOOGLE =
        "Compra aprobada por \$39.920,00: Tu compra en GOOGLE *MINTROCKET por \$39.920,00 " +
            "con tu tarjeta terminada en 1336 ha sido APROBADA."

    @Test
    fun `la compra de Nu se lee con monto, comercio y tipo`() {
        val crepes = assertNotNull(parseSms(COMPRA_NU_CREPES), "no parseó: $COMPRA_NU_CREPES")
        assertEquals(130_200.0, crepes.amount)
        assertEquals("COP", crepes.currency)
        assertEquals(TransactionType.EXPENSE, crepes.type)
        assertEquals("CREPES Y WAFFLES LEMON", crepes.merchant)

        val google = assertNotNull(parseSms(COMPRA_NU_GOOGLE), "no parseó: $COMPRA_NU_GOOGLE")
        assertEquals(39_920.0, google.amount)
        assertEquals("COP", google.currency)
        assertEquals(TransactionType.EXPENSE, google.type)
        assertEquals("GOOGLE *MINTROCKET", google.merchant)
    }

    @Test
    fun `la compra de Nu no se confunde con un pago de tarjeta`() {
        // «con tu tarjeta» está en el texto: no puede caer en la regla de plata del abono a la tarjeta.
        val parsed = assertNotNull(parseSms(COMPRA_NU_CREPES))
        assertEquals(false, parsed.merchant == "Pago de tarjeta")
        assertEquals(false, parsed.category == com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY)
    }

    @Test
    fun `un título solo no tiene nada que parsear`() {
        // Lo que no se puede volver un movimiento no tiene que salir del teléfono.
        assertNull(parseSms("Bancolombia:"))
    }
}
