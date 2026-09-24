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

    /**
     * **La plata que le llega a la cuenta de Nu**, tal como la tenía el dueño en «Reconciliar
     * movimiento» el 23-sep: la pantalla se quedaba en «Parseando…» con «Algo salió mal», porque la
     * regla de Nu solo aceptaba compras y pagos, y porque el monto no trae ni «$» ni «por».
     */
    private val RECIBISTE_NU =
        "Recibiste 300.000,00 en tu cuenta: Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave."

    @Test
    fun `la plata que llega a Nu se lee como ingreso con quien la mandó`() {
        val parsed = assertNotNull(parseSms(RECIBISTE_NU, "Notificación · Nu"), "no parseó: $RECIBISTE_NU")
        assertEquals(300_000.0, parsed.amount)
        assertEquals("COP", parsed.currency)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("CAROLINA RESTREPO SALAZAR", parsed.merchant)
    }

    @Test
    fun `lo que rindió la Cajita de Nu sigue sin ser un movimiento`() {
        assertNull(parseSms("Recibiste tu rendimiento: Tu Cajita generó \$1.234,00 hoy.", "Notificación · Nu"))
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
    // ── Lo que no pasó, y lo de Nu que no es un movimiento ─────────────────────────────────

    private val DE_NU = "Notificación · Nu"

    @Test
    fun `una compra rechazada no es un gasto, venga de donde venga`() {
        val rechazadas = listOf(
            "Compra rechazada: Tu compra en RAPPI por \$45.000,00 con tu tarjeta terminada en 1336 fue rechazada.",
            "Bancolombia: Transacción rechazada por \$80.000 en EXITO COLINA con tu T.Cred *4057, el 21/09/2026.",
            "Bancolombia: Tu compra por \$120.000 en FALABELLA fue declinada. Comunícate con nosotros.",
            "Tu pago por \$300.000 no fue exitoso.",
            "Compra no aprobada por \$15.000,00 en UBER.",
        )
        for (texto in rechazadas) {
            assertNull(parseSms(texto), "se leyó como movimiento: $texto")
            assertNull(parseSms(texto, DE_NU), "se leyó como movimiento (Nu): $texto")
            assertNull(parseSms(texto, "Notificación · Bancolombia"), "se leyó como movimiento (Bancolombia): $texto")
        }
    }

    @Test
    fun `los avisos de Nu que no son compra ni pago no se vuelven gasto`() {
        val avisos = listOf(
            "Tu factura está lista: El total a pagar de tu tarjeta es \$1.250.300,00 y vence el 5 de octubre.",
            "Recuerda tu pago mínimo: Paga al menos \$85.000,00 antes de la fecha límite.",
            "Tu Cajita creció: Ganaste \$1.234,56 en rendimientos esta semana.",
            "¡Tienes un beneficio! Hasta \$50.000,00 de cashback en tus compras de octubre.",
        )
        for (texto in avisos) {
            assertNull(parseSms(texto, DE_NU), "se leyó como movimiento: $texto")
        }
    }

    @Test
    fun `las compras aprobadas de Nu se siguen leyendo con el origen de Nu`() {
        val crepes = assertNotNull(parseSms(COMPRA_NU_CREPES, DE_NU))
        assertEquals(130_200.0, crepes.amount)
        assertEquals(TransactionType.EXPENSE, crepes.type)
        assertEquals("CREPES Y WAFFLES LEMON", crepes.merchant)

        val google = assertNotNull(parseSms(COMPRA_NU_GOOGLE, DE_NU))
        assertEquals(39_920.0, google.amount)
        assertEquals("GOOGLE *MINTROCKET", google.merchant)
    }

    @Test
    fun `la regla de Nu no toca lo de Bancolombia`() {
        // Con su propio origen, con el de un SMS (el código del remitente) y sin origen, la compra de
        // Bancolombia se lee igual que antes: la regla de Nu solo mira el origen de Nu.
        for (origen in listOf(null, "85540", "Notificación · Bancolombia", "Correo · Bancolombia")) {
            val parsed = assertNotNull(parseSms(DE_LA_NOTIFICACION, origen), "origen=$origen")
            assertEquals(12_345.0, parsed.amount)
            assertEquals(TransactionType.EXPENSE, parsed.type)
            assertEquals("PRUEBA DE MOVI", parsed.merchant)
        }
        // Y al revés: la forma de Bancolombia («Compraste … con tu T.Deb») no tiene la forma de una
        // compra de Nu, así que un texto así bajo el origen de Nu no se cuela como compra aprobada.
        assertNull(parseSms(DE_LA_NOTIFICACION, DE_NU))
        // «Nu» tiene que ser una palabra: un rótulo que solo la contiene no activa la regla.
        assertNotNull(parseSms(DE_LA_NOTIFICACION, "Notificación · Numeral"))
    }
}
