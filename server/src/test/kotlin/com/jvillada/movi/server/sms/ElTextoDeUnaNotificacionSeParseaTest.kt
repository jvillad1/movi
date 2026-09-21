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

    @Test
    fun `un título solo no tiene nada que parsear`() {
        // Lo que no se puede volver un movimiento no tiene que salir del teléfono.
        assertNull(parseSms("Bancolombia:"))
    }
}
