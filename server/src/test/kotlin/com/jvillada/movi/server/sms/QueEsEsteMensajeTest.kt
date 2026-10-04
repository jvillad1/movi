package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.MotivoDeApartado
import com.jvillada.movi.shared.model.MotivoDeApartado.AVISO_DE_SEGURIDAD
import com.jvillada.movi.shared.model.MotivoDeApartado.CODIGO_DE_VERIFICACION
import com.jvillada.movi.shared.model.MotivoDeApartado.EXTRACTO_DISPONIBLE
import com.jvillada.movi.shared.model.MotivoDeApartado.NO_PASO
import com.jvillada.movi.shared.model.MotivoDeApartado.PROMOCION
import com.jvillada.movi.shared.model.MotivoDeApartado.RECORDATORIO_DE_PAGO
import com.jvillada.movi.shared.model.MotivoDeApartado.RESUMEN_DE_GASTOS
import com.jvillada.movi.shared.model.MotivoDeApartado.SIN_MONTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # Qué aparta Movi, y qué nunca
 *
 * La regla de [queEsEsteMensaje] es asimétrica a propósito: **ante la duda, es movimiento**. Por
 * eso esta prueba tiene dos mitades del mismo peso: los avisos que se apartan, cada uno con su
 * motivo, y los movimientos típicos de cada banco que **no** se pueden apartar nunca.
 *
 * Los textos son sintéticos: tienen la forma de los que llegan de verdad (comprobada contra el
 * histórico del dueño, que no está en el repo), con montos, números y nombres inventados.
 */
class QueEsEsteMensajeTest {

    private val bancolombia = "85540"
    private val deNu = "Notificación · Nu"
    private val deGlim = "Notificación · Glim"
    private val deWallet = "Notificación · Google Wallet"
    private val deCorreo = "Correo · Bancolombia"

    private fun motivo(texto: String, origen: String = bancolombia): MotivoDeApartado? = motivoParaApartar(texto, origen)

    private fun esMovimiento(texto: String, origen: String = bancolombia) =
        assertNull(motivo(texto, origen), "lo apartó y es un movimiento: $texto")

    // ── Los movimientos de cada banco: NUNCA se apartan ──────────────────────────────────────

    @Test
    fun `los movimientos de Bancolombia siguen siendo movimientos`() {
        val movimientos = listOf(
            "Bancolombia: Compraste \$43.210,00 en TIENDA DE EJEMPLO con tu T.Deb *1111, el 01/10/2026 a las 10:15. Si tienes dudas, encuentranos aqui: 6040000000 o 018000000000. Estamos cerca.",
            "Bancolombia: Compraste COP25.900,00 en STREAMING DE PRUEBA con tu T.Cred *2222, el 02/10/2026 a las 21:04.",
            "Bancolombia: Compraste USD15,00 en SERVICIO EN LA NUBE con tu T.Cred *2222, el 02/10/2026 a las 03:10.",
            "Bancolombia: Transferiste \$450,000 desde tu cuenta *3333 a la cuenta *00011122233 el 03/10/2026 a las 17:59. ¿Dudas? Llamanos al 018000000000. Estamos cerca.",
            "Bancolombia: Transferiste \$12,300.00 por QR desde tu cuenta 3333 a la cuenta 4444, el 2026/10/01 12:59. ¿Dudas? Llamanos al 018000000000. Estamos cerca.",
            "Bancolombia: PERSONA DE PRUEBA pagaste \$7,500.00 por codigo QR desde tu cuenta *3333 a la llave 0011223344 el 01/10/2026 a las 13:33. Con codigo QR es facil y de una.",
            "Bancolombia: JUAN, transferiste \$95,000.00 a la llave @ejemplo123 desde tu cuenta *3333 a PERSONA DE PRUEBA el 01/10/26 a las 21:04. Con Bre-b es de una y gratis.",
            "Bancolombia: Pagaste \$98,700.00 a EMPRESA DE SALUD S A desde tu producto 3333 el 01/10/2026.",
            "Bancolombia: Recibiste \$45,000.00 por QR de PERSONA DE PRUEBA en tu cuenta *3333 el 2026/10/01 a las 13:41.",
            "Bancolombia: Retiraste \$200.000,00 en CAJERO DE PRUEBA de tu T.Deb **1111 el 15/09/2026 a las 16:20.",
            "Bancolombia le informa Compra por \$54.300,00 en SUPERMERCADO EJEMPLO 15:18. 08/02/2024 T.Deb *5555. Inquietudes al 6040000000/018000000000.",
            "Bancolombia le informa Transferencia por \$38,000 desde cta *3333 a cta 99988877766. 27/10/2022 18:15.",
            "Bancolombia te informa recepcion transferencia de PERSONA DE PRUEBA por \$27,000 en la cuenta *3333. 30/06/2023 20:46.",
            "Bancolombia le informa un Pago PROVEEDOR de EMPRESA EJEMPLO SAS por \$1,234,567.00 en su Cuenta Ahorros.",
            "Bancolombia le informa el desembolso de su Crediagil a la cta *3333 por valor de \$150,000.00.",
            "Bancolombia: Recibimos pago por 1.234.567 a tu tarjeta de credito **2222.",
            "Bancolombia: JUAN, esta transaccion fue aprobada: compra por \$99,900.00 en TIENDA EN LINEA el 04/09/2024 a las 23:49.",
            "Bancolombia le informa que el reclamo de fraude 1234567890, se soluciona con devolucion a su T.Credito.",
            // Un cobro anunciado sale de la cuenta aunque no diga «compraste».
            "Bancolombia informa que hoy 10/01/2025 se activo su PLAN DE SEGURO por 99,990.00. No Fuiste tu? Puede cancelar el cargo aqui.",
        )
        for (texto in movimientos) esMovimiento(texto)
        for (texto in movimientos) esMovimiento(texto, deCorreo)
    }

    @Test
    fun `los movimientos de Nu, Glim y Google Wallet siguen siendo movimientos`() {
        esMovimiento("Compra aprobada por \$49.900,00: Tu compra en TIENDA DIGITAL por \$49.900,00 con tu tarjeta terminada en 9999 ha sido APROBADA.", deNu)
        esMovimiento("Recibiste 250.000,00 en tu cuenta: Te llegó dinero de PERSONA DE PRUEBA con tu llave.", deNu)
        esMovimiento("Nu: Pago aprobado por \$84.321,50 Pagaste en Empresa de Servicios S.A. con tu cuenta de ahorros. Si tienes dudas contáctanos vía chat.", deNu)
        esMovimiento("¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por \$1.500,00. En un rato podrás verlo en tu app.", deNu)
        esMovimiento("Aleluya: ¡tienes nuevo saldo! 💳: Empresa Empleadora SAS recargó 500.000,00 COP en tu tarjeta de beneficios.", deGlim)
        esMovimiento("¡Usaste tus beneficios!: Pagaste \$21.300,00 COP con tu tarjeta de beneficios Glim el 02/10/2026 a las 12:46 en CAFE DE PRUEBA.", deGlim)
        esMovimiento("CAFE DE PRUEBA CENTRO: COP21,300 with Glim ••0000", deWallet)
        esMovimiento("TIENDA EJEMPLO: COP120,000 with Nu Mastercard Gold ••9999", deWallet)
    }

    /**
     * **Lo que el dueño confirmó aunque no fuera una compra** se queda en la bandeja: la ampliación
     * de plazo, la bienvenida a un plan, la cuenta de un tercero inscrita, y una oferta sobre su
     * propio crédito. En el histórico real están todas como confirmadas.
     */
    @Test
    fun `lo que el dueno decide el mismo no se aparta`() {
        esMovimiento("Bancolombia confirma ampliacion de plazo por COP 1,234,567.89 en su TC MASTER *2222. La tasa es de 2.10%, el plazo de 36 meses. Conoce mas en https://bit.ly/ejemplo")
        esMovimiento("Bienvenido al plan de Asistencia de EJEMPLO en alianza con Bancolombia. ¡Disfrutalo ya! llamando al 6010000000 +info https://ejemplo.at/abc")
        esMovimiento("Bancolombia: Muy bien. Inscribiste la cuenta de un tercero desde APP Bancolombia. Si no fuiste tu, llamanos ahora: 6040000000.")
        esMovimiento("Bancolombia: Octubre trae planes. Con tu Crediagil, del 18 al 20 aprovecha tasa especial de 1.5% M.V. (19.56% E.A.) Usalo desde app Mi Bancolombia")
        // Un enlace solo no hace promoción: el aviso del 4x1000 también trae uno.
        esMovimiento("Bancolombia. Tu cta de ahorros 3333 exenta del Impto de Gob 4x1000 supero el tope mensual de \$15.000.000 y se te cobrara el impuesto desde hoy bit.ly/ejemplo")
    }

    @Test
    fun `lo que no encaja en ningun motivo es movimiento`() {
        esMovimiento("Bancolombia: algo nuevo que el banco nunca había mandado por \$12.000.")
        esMovimiento("")
    }

    // ── Lo que se aparta, cada cosa con su motivo ─────────────────────────────────────────────

    @Test
    fun `el recordatorio de un pago se aparta`() {
        assertEquals(RECORDATORIO_DE_PAGO, motivo("Tienes un pago por \$125.400,00 de Empresa de Salud Ejemplo S.A.: Completa tu pago de forma fácil y segura en tu app Nu.", deNu))
        assertEquals(RECORDATORIO_DE_PAGO, motivo("Recuerda tu pago mínimo: paga al menos \$85.000,00 antes de la fecha límite.", deNu))
        assertEquals(RECORDATORIO_DE_PAGO, motivo("Bancolombia: tu tarjeta *2222 vence el 05/11/2026. Te recordamos pagar \$300.000."))
    }

    @Test
    fun `un codigo de verificacion se aparta, pero pide el numero`() {
        assertEquals(CODIGO_DE_VERIFICACION, motivo("Bancolombia: Tu clave dinamica es 482913. Es personal e intransferible."))
        assertEquals(CODIGO_DE_VERIFICACION, motivo("Bancolombia: Esta informacion es solo para ti, no la compartas. 771204 es el codigo de activacion para tu billetera."))
        assertEquals(CODIGO_DE_VERIFICACION, motivo("Tu código de verificación es 5521. No lo compartas con nadie.", deNu))
        // Sin el número no es un código: «usa el código PROMO» es otra cosa.
        assertEquals(PROMOCION, motivo("Usa el codigo EJEMPLO y paga con tus tarjetas en Tu360Compras. Aplica TyC"))
    }

    @Test
    fun `una promocion se aparta`() {
        assertEquals(PROMOCION, motivo("Bancolombia: hasta 40% dcto en tecnología pagando con nuestras tarjetas. Aplican TyC"))
        assertEquals(PROMOCION, motivo("Tienda Ejemplo: 30% OFF en toda la tienda pagando con BANCOLOMBIA. T&C web"))
        assertEquals(PROMOCION, motivo("Ya van muchos: Invita a alguien a Nu y participa por viajes y premios en efectivo de hasta 2 millones.", deNu))
        assertEquals(PROMOCION, motivo("Puntos Colombia: Juan, acumulaste 120 Puntos con tu T.Debito Bancolombia."))
        assertEquals(PROMOCION, motivo("Bancolombia: 0% interes* a 6 y 12 cuotas en tiendas aliadas con TDC Bancolombia."))
    }

    @Test
    fun `un aviso de seguridad se aparta`() {
        assertEquals(AVISO_DE_SEGURIDAD, motivo("Bancolombia informa que el 08/20/2026 a las 20:38 te autenticaste exitosamente con clave principal en la sucursal telefonica."))
        assertEquals(AVISO_DE_SEGURIDAD, motivo("Bancolombia: JUAN, necesitamos tu confirmacion sobre la compra en TIENDA EJEMPLO por \$123,456.00 con tu tarjeta terminada en *2222."))
        assertEquals(AVISO_DE_SEGURIDAD, motivo("Bancolombia: Aun te esperamos para confirmar que fuiste tu quien realizo compra por \$45,000.00 en TIENDA EJEMPLO."))
        assertEquals(AVISO_DE_SEGURIDAD, motivo("Nos alegra que todo este bien! Si tu transaccion fue aprobada, continua. Si tu transaccion fue rechazada, intenta nuevamente."))
        assertEquals(AVISO_DE_SEGURIDAD, motivo("Iniciaste sesión en un nuevo dispositivo el 01/10/2026 a las 10:00.", deNu))
    }

    @Test
    fun `el extracto disponible se aparta`() {
        assertEquals(EXTRACTO_DISPONIBLE, motivo("Bancolombia: tu extracto de septiembre 2026 ya está disponible en la app.", deCorreo))
        assertEquals(EXTRACTO_DISPONIBLE, motivo("Tu factura está lista: el total de tu tarjeta es \$1.250.300,00.", deNu))
    }

    @Test
    fun `el resumen de gastos del banco se aparta`() {
        assertEquals(RESUMEN_DE_GASTOS, motivo("Bancolombia: Juan, \$1.234.567 fue tu gasto de junio. Entra ya a app Mi Bancolombia >Dia a Dia."))
        assertEquals(RESUMEN_DE_GASTOS, motivo("Bancolombia: Juan, en agosto has gastado \$9.876.543 y aun no lo revisas."))
        assertEquals(RESUMEN_DE_GASTOS, motivo("Bancolombia: Juan, hiciste 12 transferencias en agosto. Tienes claro en que gastaste?"))
    }

    @Test
    fun `lo que no paso se aparta, venga de donde venga`() {
        assertEquals(NO_PASO, motivo("Bancolombia: Ups! El pago en TIENDA EJEMPLO por \$ 45,000 fue rechazada por saldo insuficiente."))
        assertEquals(NO_PASO, motivo("Bancolombia: tu compra en TIENDA EN LINEA por COP99.999,00 no fue exitosa, el cupo de tu T.Credito *2222 no se afecto."))
        assertEquals(NO_PASO, motivo("Fondos insuficientes ⛔: Se rechazó tu pago por \$12.000,00 COP.", deGlim))
        assertEquals(NO_PASO, motivo("TIENDA EJEMPLO: DECLINED - COP12,000 with Glim ••0000", deWallet))
    }

    @Test
    fun `el aviso de una app sin un solo numero se aparta, el SMS no`() {
        assertEquals(SIN_MONTO, motivo("Set up a shortcut to pay: double press the power button", deWallet))
        // Un SMS del banco sin números es su canal con el banco: no se aparta por no tener monto.
        assertNull(motivo("Bancolombia: tu nueva tarjeta llegara pronto a tu direccion"))
    }

    /** El rechazo de Google Wallet tampoco se lee como gasto (antes era un gasto de $12.000 que no salió). */
    @Test
    fun `el DECLINED de Google Wallet no es un gasto`() {
        assertNull(parseSms("TIENDA EJEMPLO: DECLINED - COP12,000 with Glim ••0000", deWallet))
        assertNotNull(parseSms("TIENDA EJEMPLO: COP12,000 with Glim ••0000", deWallet))
    }
}
