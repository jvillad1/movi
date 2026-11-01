package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 85 · «¿Salda Casa con Andrés?» antes de confirmar, y el lote sin ingresos
 *
 * El aviso de Andrés todavía pendiente ya dice qué grupo saldaría: la misma regla de
 * [transferenciasParaSaldar] sobre el movimiento que el aviso va a crear. Y un ingreso nunca entra al
 * lote «Confirmar N».
 */
class ElAvisoQueSaldaUnGrupoTest {

    private val ahora = 1_793_000_000_000L
    private val andres = DestinoConocido(id = "dst_andres", nombre = "Andrés Rojas", numero = "31234567890")
    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Andrés",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Tú"), MiembroDelEspacio(andres.id, "Andrés Rojas")),
    )

    /** Pagué el arriendo entero: Andrés me debe la mitad. */
    private fun conDeuda(monto: Long = 1_079_250L): GrupoConSuSaldo {
        val gasto = GastoDelEspacio(
            id = "g1", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = monto, categoria = "Vivienda",
            descripcion = "Arriendo", fecha = ahora - 86_400_000L, partes = partesDelGasto(monto, casa, MIEMBRO_YO),
        )
        return GrupoConSuSaldo(casa, saldoDelEspacio(casa, listOf(gasto), emptyList()))
    }

    private val textoDeAndres =
        "Bancolombia: Recibiste \$539.625 de la cuenta *31234567890 en tu cuenta *4057 el 30/10/2026 a las 13:05."

    private fun ingreso(monto: Double = 539_625.0, nombre: String = "Transferencia de Andrés Rojas") =
        ParsedSms(monto, nombre, TransactionType.INCOME, "Transferencia")

    @Test
    fun `el ingreso de Andres dice que saldaria Casa con Andres`() {
        val salda = assertNotNull(grupoQueSaldaElAviso(ingreso(), textoDeAndres, ahora, listOf(conDeuda()), listOf(andres)))
        assertEquals("esp_casa", salda.espacioId)
        assertEquals("Casa con Andrés", salda.nombreDelEspacio)
        assertEquals("Andrés Rojas", salda.quien)
        assertEquals(539_625L, salda.debe)
        assertTrue(salda.entra)
    }

    @Test
    fun `por el numero del aviso, aunque el nombre todavia sea ilegible`() {
        assertNotNull(grupoQueSaldaElAviso(ingreso(nombre = "Transferencia de *7890"), textoDeAndres, ahora, listOf(conDeuda()), listOf(andres)))
    }

    @Test
    fun `no se propone si es mas de lo que debe, de otra persona, o sin deuda`() {
        assertNull(grupoQueSaldaElAviso(ingreso(monto = 600_000.0), textoDeAndres, ahora, listOf(conDeuda()), listOf(andres)), "más de lo que debe")
        val deOtro = "Bancolombia: Recibiste \$480.000 de la cuenta *77700012345 en tu cuenta *4057 el 28/10/2026 a las 10:00."
        assertNull(grupoQueSaldaElAviso(ingreso(480_000.0, "Transferencia de *2345"), deOtro, ahora, listOf(conDeuda()), listOf(andres)))
        val sinDeuda = GrupoConSuSaldo(casa, saldoDelEspacio(casa, emptyList(), emptyList()))
        assertNull(grupoQueSaldaElAviso(ingreso(), textoDeAndres, ahora, listOf(sinDeuda), listOf(andres)))
        assertNull(grupoQueSaldaElAviso(ingreso(), textoDeAndres, ahora, emptyList(), listOf(andres)), "sin grupos")
    }

    @Test
    fun `un gasto hacia Andres no salda lo que el me debe`() {
        val envio = ParsedSms(200_000.0, "Transferencia a Andrés Rojas", TransactionType.EXPENSE, "Otros")
        val texto = "Bancolombia: Transferiste \$200.000 desde tu cuenta *4057 a la cuenta *31234567890 el 30/10/2026 a las 14:00."
        assertNull(grupoQueSaldaElAviso(envio, texto, ahora, listOf(conDeuda()), listOf(andres)))
    }

    @Test
    fun `ningun ingreso entra al lote, los gastos si`() {
        assertFalse(entraEnElLote(ingreso()))
        assertFalse(entraEnElLote(ingreso(480_000.0, "Transferencia de *2345")))
        assertTrue(entraEnElLote(ParsedSms(32_000.0, "Juan Valdez Cafe", TransactionType.EXPENSE, "Restaurantes")))
    }

    @Test
    fun `si ya marco saldado, el giro que esperaba ese saldado se reconoce`() {
        // Vuelta 66: «Andrés ya me pagó» se marcó antes de que llegara la plata; la deuda quedó en cero.
        val gasto = GastoDelEspacio(
            id = "g1", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = 1_079_250L, categoria = "Vivienda",
            descripcion = "Arriendo", fecha = ahora - 3 * 86_400_000L, partes = partesDelGasto(1_079_250L, casa, MIEMBRO_YO),
        )
        val saldado = LiquidacionDelEspacio(
            id = "liq1", espacioId = casa.id, de = andres.id, a = MIEMBRO_YO, monto = 539_625L, fecha = ahora - 86_400_000L,
        )
        val grupo = GrupoConSuSaldo(casa, saldoDelEspacio(casa, listOf(gasto), listOf(saldado)), listOf(saldado))
        assertTrue(grupo.saldo.reembolsos.isEmpty(), "ya no se deben")

        val salda = assertNotNull(grupoQueSaldaElAviso(ingreso(), textoDeAndres, ahora, listOf(grupo), listOf(andres)))
        assertTrue(salda.yaMarcadoSaldado)
        assertEquals(539_625L, salda.debe)
        // Sin pasarle los saldados, no se veía.
        assertNull(grupoQueSaldaElAviso(ingreso(), textoDeAndres, ahora, listOf(grupo.copy(liquidaciones = emptyList())), listOf(andres)))
    }

    @Test
    fun `un pago a la tarjeta llega como ingreso pero es una salida, y un avance si entra`() {
        val pagoATarjeta = ParsedSms(500_000.0, "Pago de tarjeta", TransactionType.INCOME, CARD_PAYMENT_CATEGORY)
        assertFalse(entraPlata(pagoATarjeta))
        assertFalse(entraPlata(ParsedSms(1_300_000.0, "Cuota", TransactionType.INCOME, CUOTA_CATEGORY)))
        assertTrue(entraPlata(ParsedSms(800_000.0, "Avance de la tarjeta *9208", TransactionType.INCOME, AVANCE_DE_TARJETA_CATEGORY)))
        assertTrue(entraPlata(ingreso()))
        assertFalse(entraPlata(ParsedSms(32_000.0, "Juan Valdez Cafe", TransactionType.EXPENSE, "Restaurantes")))
    }
}
