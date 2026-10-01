package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.AvisoPorRevisar
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo que deciden y lo que dicen los avisos del teléfono (Ola 1 · Movi avisa), sin teléfono.
 */
class AvisosDelTelefonoTest {

    // ── Vence mañana / vence hoy ─────────────────────────────────────────────

    private fun pago(
        nombre: String,
        monto: Long,
        dias: Int,
        pagado: Boolean = false,
        esIngreso: Boolean = false,
        montoEsSaldo: Boolean = false,
        moneda: String = "COP",
    ) = PagoDelPeriodo(
        ruleId = "rr_$nombre", nombre = nombre, monto = monto, pagado = pagado, diasParaVencer = dias,
        esIngreso = esIngreso, montoEsSaldo = montoEsSaldo, moneda = moneda,
    )

    @Test
    fun `pagado no avisa aunque venza hoy`() {
        assertEquals(emptyList(), vencimientosParaAvisar(listOf(pago("Celular", 53_000, dias = 0, pagado = true))))
    }

    @Test
    fun `vence pasado manana no avisa todavia`() {
        assertEquals(emptyList(), vencimientosParaAvisar(listOf(pago("Arriendo", 1_800_000, dias = 2))))
    }

    @Test
    fun `vence hoy o manana sin pagar avisa, primero lo de hoy`() {
        val avisar = vencimientosParaAvisar(
            listOf(
                pago("Coomeva Familiar", 138_600, dias = 1),
                pago("Celular", 53_000, dias = 0),
                pago("Arriendo", 1_800_000, dias = 2),
            ),
        )
        assertEquals(listOf("Celular", "Coomeva Familiar"), avisar.map { it.nombre })
    }

    @Test
    fun `lo vencido y lo que entra no se avisan`() {
        val avisar = vencimientosParaAvisar(
            listOf(
                pago("Gimnasio", 90_000, dias = -3),
                pago("Sueldo", 8_500_000, dias = 0, esIngreso = true),
            ),
        )
        assertEquals(emptyList(), avisar)
    }

    @Test
    fun `el resumen dice el primero con su monto y cuantos mas`() {
        val texto = textoDeVencimientos(
            vencimientosParaAvisar(listOf(pago("Coomeva Familiar", 138_600, dias = 1), pago("Seguro", 210_000, dias = 1))),
        )!!
        assertEquals("Mañana vence Coomeva Familiar \$138.600 y 1 más", texto.titulo)
        assertEquals(listOf("Mañana · Coomeva Familiar · \$138.600", "Mañana · Seguro · \$210.000"), texto.lineas)
    }

    @Test
    fun `uno solo, hoy`() {
        val texto = textoDeVencimientos(listOf(pago("Celular", 53_000, dias = 0)))!!
        assertEquals("Hoy vence Celular \$53.000", texto.titulo)
        assertEquals("Toca para ver tus pagos del período", texto.texto)
        assertEquals(emptyList(), texto.lineas)
    }

    @Test
    fun `una tarjeta con montoEsSaldo no dice su saldo como si fuera el pago`() {
        val amex = pago("Pago tarjeta AMEX 9208", 27_501_150, dias = 1, montoEsSaldo = true)
        val solo = textoDeVencimientos(listOf(amex))!!
        assertEquals("Mañana vence Pago tarjeta AMEX 9208", solo.titulo)
        assertFalse("27.501.150" in solo.titulo + solo.texto, "el saldo no va en el aviso")
        assertNull(montoDelVencimiento(amex))

        val conOtro = textoDeVencimientos(listOf(pago("Celular", 53_000, dias = 0), amex))!!
        assertEquals("Mañana · Pago tarjeta AMEX 9208 · revisa cuánto pagar", conOtro.lineas[1])
        assertFalse(conOtro.lineas.any { "27.501.150" in it })
    }

    @Test
    fun `un saldo en dolares tampoco, y un pago en dolares dice US`() {
        val enDolares = pago("Suscripción", 20, dias = 0, moneda = "USD")
        assertEquals("Hoy vence Suscripción US\$20", textoDeVencimientos(listOf(enDolares))!!.titulo)
    }

    @Test
    fun `sin nada que avisar no hay texto`() {
        assertNull(textoDeVencimientos(emptyList()))
    }

    @Test
    fun `la huella cambia con el dia y con lo pendiente`() {
        val dia1 = 1_790_000_000_000L
        val pagos = listOf(pago("Celular", 53_000, dias = 1))
        assertEquals(huellaDeVencimientos(dia1, pagos), huellaDeVencimientos(dia1 + 60_000, pagos))
        assertNotEquals(huellaDeVencimientos(dia1, pagos), huellaDeVencimientos(dia1 + 86_400_000, pagos))
        assertNotEquals(huellaDeVencimientos(dia1, pagos), huellaDeVencimientos(dia1, pagos + pago("Agua", 40_000, dias = 1)))
    }

    // ── Llegó un movimiento ──────────────────────────────────────────────────

    private fun aviso(
        id: String,
        monto: Double,
        descripcion: String,
        origen: String = "85540",
        tipo: TransactionType = TransactionType.EXPENSE,
        moneda: String = "COP",
    ) = AvisoPorRevisar(id = id, origen = origen, monto = monto, moneda = moneda, descripcion = descripcion, tipo = tipo)

    @Test
    fun `uno solo - Movi anoto, a quien y de que banco`() {
        val texto = textoDeMovimientos(listOf(aviso("a", 180_000.0, "Transferencia a Caro")))!!
        assertEquals("Movi anotó \$180.000", texto.titulo)
        assertEquals("Transferencia a Caro · Bancolombia — toca para revisar", texto.texto)
    }

    @Test
    fun `de una notificacion, el nombre de la app`() {
        val texto = textoDeMovimientos(listOf(aviso("a", 28_500.0, "UBER", origen = "Notificación · Nu")))!!
        assertEquals("UBER · Nu — toca para revisar", texto.texto)
    }

    @Test
    fun `plata que entro no dice anoto`() {
        val texto = textoDeMovimientos(listOf(aviso("a", 200_000.0, "Transferencia de Caro", tipo = TransactionType.INCOME)))!!
        assertEquals("Te llegaron \$200.000", texto.titulo)
    }

    @Test
    fun `en dolares con su signo`() {
        val texto = textoDeMovimientos(listOf(aviso("a", 19.99, "STEAM", moneda = "USD")))!!
        assertEquals("Movi anotó US\$19,99", texto.titulo)
    }

    @Test
    fun `varios seguidos - una sola notificacion con cuantos`() {
        val texto = textoDeMovimientos(
            listOf(
                aviso("a", 180_000.0, "Transferencia a Caro"),
                aviso("b", 25_000.0, "UBER TRIP"),
                aviso("c", 44_900.0, "NETFLIX"),
            ),
        )!!
        assertEquals("3 movimientos por revisar", texto.titulo)
        assertEquals("\$180.000 · Transferencia a Caro · \$25.000 · UBER TRIP y 1 más — toca para revisar", texto.texto)
        assertEquals(3, texto.lineas.size)
        assertEquals("\$44.900 · NETFLIX", texto.lineas[2])
    }

    @Test
    fun `sin nada no hay notificacion`() {
        assertNull(textoDeMovimientos(emptyList()))
    }

    @Test
    fun `se agrupan mientras la notificacion sigue a la vista, sin repetidos`() {
        val previos = listOf(aviso("a", 1.0, "A"), aviso("b", 2.0, "B"))
        val nuevos = listOf(aviso("b", 2.0, "B"), aviso("c", 3.0, "C"))
        assertEquals(listOf("a", "b", "c"), acumularMovimientos(previos, nuevos, sigueVisible = true).map { it.id })
    }

    @Test
    fun `si ya se toco o se descarto, empieza de cero`() {
        val previos = listOf(aviso("a", 1.0, "A"))
        assertEquals(listOf("c"), acumularMovimientos(previos, listOf(aviso("c", 3.0, "C")), sigueVisible = false).map { it.id })
    }

    @Test
    fun `el origen como lo diria una persona`() {
        assertEquals("Bancolombia", origenParaElAviso("85540"))
        assertEquals("Bancolombia", origenParaElAviso("Correo · Bancolombia"))
        assertEquals("Google Wallet", origenParaElAviso("Notificación · Google Wallet"))
        assertNull(origenParaElAviso("899776"), "un código que no se conoce no se dice")
        assertNull(origenParaElAviso("SMS"))
    }

    // ── Cuándo y a dónde ─────────────────────────────────────────────────────

    @Test
    fun `las ocho de Bogota - hoy si no pasaron, manana si ya`() {
        // 2026-10-01 06:00 en Bogotá = 11:00 UTC.
        val seisDeLaManana = 1_790_852_400_000L
        assertEquals(2 * 3_600_000L, milisHastaLaProxima(8, seisDeLaManana))
        // 2026-10-01 09:00 en Bogotá: la próxima es mañana a las 8, en 23 horas.
        assertEquals(23 * 3_600_000L, milisHastaLaProxima(8, seisDeLaManana + 3 * 3_600_000L))
        // Justo a las 8 en punto: la de mañana, no un aviso inmediato.
        assertEquals(24 * 3_600_000L, milisHastaLaProxima(8, seisDeLaManana + 2 * 3_600_000L))
    }

    @Test
    fun `tocar un aviso abre su pantalla, y nada mas`() {
        assertEquals(Screen.PorRevisar, destinoDeAviso(ABRIR_POR_REVISAR))
        assertEquals(Screen.Plan(), destinoDeAviso(ABRIR_PLAN))
        assertNull(destinoDeAviso("cualquier-cosa"))
        assertNull(destinoDeAviso(null))
        assertTrue(PARA_QUE_SON_LOS_AVISOS.startsWith("Te aviso"))
    }
}
