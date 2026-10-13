package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Cuándo Movi entendió un aviso (la bandeja que se vacía sola)
 *
 * La fila de «Por revisar» ofrece «Confirmar» y el lote confirma **solo** lo que sale sin dudas de
 * [dudasDelAviso]. Cada duda manda a «Revisar»: el lote salta lo que tiene alguna.
 */
class LoQueMoviEntendioTest {

    private val delDueno = listOf("Comida", "Mercado", "Cuota de crédito")
    private val comida = ParsedSms(46_000.0, "TOSTAO CAFE", TransactionType.EXPENSE, "Comida")

    private fun dudas(
        leido: ParsedSms? = comida,
        coincidencias: Int = 0,
        destinoSinNombre: Boolean = false,
        cuentaSegura: Boolean = true,
        nombraOtraCuentaSuya: Boolean = false,
        antesDeLaCuenta: Boolean = false,
    ) = dudasDelAviso(leido, coincidencias, destinoSinNombre, cuentaSegura, nombraOtraCuentaSuya, delDueno, antesDeLaCuenta)

    @Test
    fun `con monto, cuenta segura y una categoria suya, Movi lo entendio`() {
        assertTrue(dudas().isEmpty())
        // Una categoría que propuso su memoria vale aunque no esté en la lista (la lista es la de sus movimientos).
        assertTrue(dudas(leido = comida.copy(category = "Panadería", aprendidoDe = "Así lo anotaste 4 veces")).isEmpty())
    }

    @Test
    fun `cada duda manda a Revisar`() {
        assertEquals(setOf(DudaDelAviso.NO_SE_LEYO), dudas(leido = null))
        assertEquals(setOf(DudaDelAviso.SIN_MONTO), dudas(leido = comida.copy(amount = 0.0)))
        assertEquals(setOf(DudaDelAviso.SIN_CUENTA), dudas(cuentaSegura = false))
        assertEquals(setOf(DudaDelAviso.YA_ANOTADO), dudas(coincidencias = 1))
        assertEquals(setOf(DudaDelAviso.DESTINO_SIN_NOMBRE), dudas(destinoSinNombre = true))
        assertEquals(setOf(DudaDelAviso.DOS_PATAS), dudas(nombraOtraCuentaSuya = true))
        assertEquals(setOf(DudaDelAviso.ANTES_DE_LA_CUENTA), dudas(antesDeLaCuenta = true))
    }

    @Test
    fun `una categoria del catalogo que el duenno no usa, u Otros, no cuenta como entendida`() {
        assertEquals(setOf(DudaDelAviso.SIN_CATEGORIA), dudas(leido = comida.copy(category = "Restaurantes")))
        assertEquals(setOf(DudaDelAviso.SIN_CATEGORIA), dudas(leido = comida.copy(category = "Otros")))
        // «Otros» no se vuelve entendida ni aunque la diga la memoria.
        assertFalse(categoriaEntendida(comida.copy(category = "Otros", aprendidoDe = "x"), delDueno))
        // Sin tildes ni mayúsculas: «cuota de credito» es la suya.
        assertTrue(categoriaEntendida(comida.copy(category = "comida"), delDueno))
    }

    @Test
    fun `un pago que necesita elegir patas nunca se confirma desde la fila`() {
        listOf(CARD_PAYMENT_CATEGORY, CUOTA_CATEGORY, TRANSFER_CATEGORY, AVANCE_DE_TARJETA_CATEGORY).forEach { categoria ->
            assertTrue(DudaDelAviso.DOS_PATAS in dudas(leido = comida.copy(category = categoria, aprendidoDe = "x")), categoria)
        }
        assertTrue(DudaDelAviso.DOS_PATAS in dudas(leido = comida.copy(deudaSugeridaId = "acc-vehiculo")))
        assertTrue(DudaDelAviso.DOS_PATAS in dudas(leido = comida.copy(traspasoHaciaId = "acc-nu")))
        assertTrue(DudaDelAviso.DOS_PATAS in dudas(leido = comida.copy(traspasoDesdeId = "acc-fidu")))
    }

    @Test
    fun `el lote salta los que tienen dudas`() {
        val avisos = mapOf(
            "cafe" to dudas(),
            "mercado" to dudas(leido = comida.copy(category = "Mercado", amount = 180_000.0)),
            "ya-anotado" to dudas(coincidencias = 1),
            "a-alguien-sin-nombre" to dudas(destinoSinNombre = true),
            "pago-de-tarjeta" to dudas(leido = comida.copy(category = CARD_PAYMENT_CATEGORY)),
        )
        assertEquals(listOf("cafe", "mercado"), avisos.filterValues { it.isEmpty() }.keys.toList())
    }

    @Test
    fun `un aviso de antes del primer movimiento de la cuenta se contaria dos veces`() {
        val inicio = epochDeFecha("2026-09-01")
        assertTrue(esAntesDeLaCuenta(epochDeFecha("2026-08-20"), inicio))
        assertFalse(esAntesDeLaCuenta(epochDeFecha("2026-09-01"), inicio))
        assertFalse(esAntesDeLaCuenta(epochDeFecha("2026-08-20"), null))
    }
}
