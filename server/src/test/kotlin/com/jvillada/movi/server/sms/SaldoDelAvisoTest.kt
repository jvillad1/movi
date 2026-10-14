package com.jvillada.movi.server.sms

import com.jvillada.movi.server.correo.textoDelCorreo
import com.jvillada.movi.server.correo.textoDePse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **El lector del saldo de un aviso.** Los positivos son **sintéticos**: no hay un solo aviso real con
 * saldo entre los mensajes del dueño (ver el KDoc de `saldoDelAviso`). Los negativos sí son reales o
 * tienen la forma de los reales: los avisos que nombran «saldo» sin decir uno.
 */
class SaldoDelAvisoTest {

    /**
     * **Los textos reales**, copiados de los avisos que llegaron a producción (13-oct-2026). El
     * único aviso de Bancolombia que trae el saldo es el de una compra rechazada, y dice «Tu saldo
     * actual es»; los de Glim dicen «saldo» sin cifra o con la de una recarga, que no es un saldo.
     */
    @Test
    fun `los avisos reales que dicen saldo`() {
        assertEquals(
            78_508.0,
            saldoDelAviso(
                "Bancolombia: Ups! Tu compra con Tarjeta Debito *4057 de \$ 116,600 fue rechazada por saldo insuficiente. Tu saldo actual es \$ 78,508.",
            )?.monto,
        )
        assertEquals(
            138_408.0,
            saldoDelAviso(
                "Bancolombia: Ups! El pago en ACTION BLACK VNP por \$ 180,000 fue rechazada por saldo insuficiente. Tu saldo actual es \$ 138,408.",
            )?.monto,
        )
        assertNull(saldoDelAviso("Fondos insuficientes ⛔: Se rechazó tu pago por \$581.800,00 COP. Revisa tus saldos actualizados en la sección de beneficios de tu app."))
        assertNull(saldoDelAviso("Tu compra no se pudo completar: Tu pago de \$581.800,00 COP en RAPPI fue rechazado. Motivo: Saldo insuficiente."))
        assertNull(saldoDelAviso("Aleluya: ¡tienes nuevo saldo! 💳: Mercado Libre Colombia Ltda recargó 55.500,00 COP en tu tarjeta de beneficios."))
    }

    @Test
    fun `saldo disponible con signo pesos y miles con punto`() {
        val s = assertNotNull(saldoDelAviso("Bancolombia: Compraste \$46.000 en TIENDA. Saldo disponible \$138.407"))
        assertEquals(138_407.0, s.monto)
        assertEquals("COP", s.moneda)
    }

    @Test
    fun `saldo disponible con dos puntos, con es de y con centavos`() {
        assertEquals(138_407.2, saldoDelAviso("Saldo disponible: \$ 138.407,20")?.monto)
        assertEquals(1_250_000.0, saldoDelAviso("Tu saldo disponible es de \$1,250,000.00")?.monto)
        assertEquals(243_238.0, saldoDelAviso("Retiraste \$50.000. Saldo disponible es \$243.238")?.monto)
    }

    @Test
    fun `tu saldo es, y saldo con dos puntos`() {
        assertEquals(500_000.0, saldoDelAviso("Nu: recibiste \$100.000,00. Tu saldo es \$500.000,00")?.monto)
        assertEquals(77_000.0, saldoDelAviso("Pago aprobado por \$23.000. Saldo: \$77.000")?.monto)
    }

    @Test
    fun `la moneda delante sin espacio o detras`() {
        assertEquals("USD", saldoDelAviso("Saldo disponible USD1,234.50")?.moneda)
        assertEquals(1_234.5, saldoDelAviso("Saldo disponible USD1,234.50")?.monto)
        assertEquals("COP", saldoDelAviso("Saldo disponible COP249.000,00")?.moneda)
        assertEquals("USD", saldoDelAviso("Saldo: 80,00 USD")?.moneda)
    }

    @Test
    fun `si dice dos saldos se queda con el ultimo`() {
        assertEquals(90_000.0, saldoDelAviso("Saldo: \$100.000. Compraste \$10.000. Saldo: \$90.000")?.monto)
    }

    @Test
    fun `un aviso sin saldo no tiene saldo`() {
        assertNull(saldoDelAviso("Bancolombia: Compraste USD20,00 en ANTHROPIC* CLAUDE SU, el 09/09/2026 a las 00:53."))
        assertNull(saldoDelAviso("Bancolombia: Recibimos pago por 9.809.799 a tu tarjeta de credito **9208 desde Wompi-PSE"))
    }

    @Test
    fun `lo que nombra el saldo sin decirlo no es un saldo`() {
        listOf(
            // Glim: lo que llegó, no el saldo.
            "Aleluya: ¡tienes nuevo saldo! 💳: Mercado Libre Colombia Ltda recargó 640.000,00 COP en tu tarjeta de beneficios.",
            textoDelCorreo(
                "Nuevo saldo disponible",
                "¡Tienes nuevo saldo!: Mercado Libre Colombia Ltda recargó 640.000,00 COP en tu tarjeta de beneficios.",
            ),
            "Fondos insuficientes ⛔: Se rechazó tu pago por \$17.150,00 COP. Revisa tus saldos actualizados en la sección de beneficios de tu app.",
            "Bancolombia: Ups! El pago en TIENDA EJEMPLO por \$ 45,000 fue rechazada por saldo insuficiente.",
            // La descripción de un pago por PSE: «Pago de saldo plan familiar».
            textoDePse(),
            // El texto de un recurrente cuyo monto es el saldo: sin los dos puntos no es «Saldo:».
            "Pago tarjeta AMEX 9208 — saldo \$27.501.150",
            "Saldo disponible para avances en la app",
        ).forEach { texto -> assertNull(saldoDelAviso(texto), texto) }
    }
}
