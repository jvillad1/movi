package com.jvillada.movi.ui.espacios

import com.jvillada.movi.shared.model.ConexionDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.InvitacionPendiente
import com.jvillada.movi.shared.model.InvitacionVista
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.LoQueSeDejaDeVer
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** # Lo que dice la pantalla del hogar conectado (vuelta 131), en funciones puras. */
class TextosDelHogarConectadoTest {
    private val zona = TimeZone.of("America/Bogota")
    private fun dia(d: Int): Long = LocalDateTime(2026, 11, d, 12, 0).toInstant(zona).toEpochMilliseconds()

    private val casa = Espacio(
        id = "esp_casa", nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )
    private val conectada = casa.copy(conexion = ConexionDelEspacio("dst_caro", "Caro", 1L))

    @Test
    fun `el mensaje de la invitacion dice el grupo, el camino, el codigo y cuando vence`() {
        assertEquals(
            "Te invito a «Casa con Caro» en Movi. Abre Movi → Tú → Grupos → «Tengo un código de invitación» y escribe K7P3Q9. Vence el 12 nov.",
            mensajeDeLaInvitacion("Casa con Caro", "K7P3Q9", dia(12), zona),
        )
        assertEquals("Invitación enviada · vence el 12 nov", invitacionEnviada(InvitacionPendiente(dia(12)), zona))
        assertEquals("Juan te invita a «Casa con Caro»", teInvita(InvitacionVista("Casa con Caro", "Juan")))
        assertEquals("Invita a Caro a Movi", tituloDeInvitar("Caro"))
    }

    @Test
    fun `la marca, el anoto y el aviso solo en un grupo conectado`() {
        assertEquals("Conectado con Caro", conectadoCon(conectada))
        assertEquals(
            "Conectado con Caro · Carolina en Movi",
            conectadoCon(casa.copy(conexion = ConexionDelEspacio("dst_caro", "Caro", 1L, nombreEnMovi = "Carolina"))),
        )
        assertNull(conectadoCon(casa))
        assertEquals("Caro anota lo que paga desde su Movi", loAnotaDesdeSuMovi(conectada))
        assertNull(loAnotaDesdeSuMovi(casa))
        assertEquals("Así anota lo que paga y los dos ven lo mismo", SUBTITULO_DE_INVITAR, "sin género")
        assertEquals("Anotó Caro", anotoElOtro(conectada, "dst_caro"))
        assertNull(anotoElOtro(conectada, null))
    }

    @Test
    fun `el gasto que anoto Caro dice Anoto Caro abajo, y su saldado tambien`() {
        val g = GastoDelEspacio(id = "g", espacioId = "esp_casa", pagadoPor = "dst_caro", monto = 1, categoria = "Mercado", descripcion = "Mercado", fecha = dia(5), anotadoPor = "dst_caro")
        assertEquals("5 nov · Caro\nAnotó Caro", quienPagoElGasto(conectada, g, zona))
        assertEquals(2, renglonesDelSubtituloDelGasto(g))
        assertEquals(1, renglonesDelSubtituloDelGasto(g.copy(anotadoPor = null)))
        val l = LiquidacionDelEspacio(de = "dst_caro", a = MIEMBRO_YO, monto = 1, fecha = dia(5), anotadoPor = "dst_caro")
        assertEquals("5 nov\nAnotó Caro", detalleDelPago(l, ahora = dia(6), zona = zona, espacio = conectada))
    }

    @Test
    fun `desconectar dice cuanto deja de verse y el saldo de antes y despues`() {
        assertEquals(
            "¿Desconectar de Caro?" to "Dejarás de ver 3 gastos y 1 saldado que anotó Caro. El saldo pasa de \$20.000 a favor de Caro a \$50.000 a tu favor. Nada se borra: cada uno sigue con lo que anotó.",
            confirmacionDeDesconectar(conectada, LoQueSeDejaDeVer(gastos = 3, saldados = 1, netoAntes = -20_000, netoDespues = 50_000)),
        )
        assertEquals(
            "No hay gastos de Caro en este grupo. El saldo no cambia. Nada se borra: cada uno sigue con lo que anotó.",
            confirmacionDeDesconectar(conectada, LoQueSeDejaDeVer(0, 0, 10, 10)).second,
        )
    }

    /** Revisión 2 (N-2): lo de antes va en su línea, con su dirección; «Pedirle» usa solo lo que ven los dos. */
    @Test
    fun `lo de antes de conectarse va aparte, con su direccion, y el mensaje no lo pide`() {
        val g = GastoDelEspacio(
            id = "g", espacioId = "esp_casa", pagadoPor = MIEMBRO_YO, monto = 100_000, categoria = "Mercado", descripcion = "Mercado",
            fecha = dia(5), partes = com.jvillada.movi.shared.model.partesDelGasto(100_000, casa, MIEMBRO_YO),
        )
        val viejo = g.copy(id = "v", descripcion = "Mercado viejo", monto = 60_000, partes = com.jvillada.movi.shared.model.partesDelGasto(60_000, casa, MIEMBRO_YO), deAntes = true)
        val saldo = com.jvillada.movi.shared.model.saldoDelEspacio(conectada, listOf(g), emptyList())
        val detalle = com.jvillada.movi.shared.model.DetalleDelEspacio(conectada, saldo, saldo, listOf(g, viejo), deAntesDeConectarse = 30_000)
        assertEquals("Antes de conectarse: Caro te debía \$30.000", lineaDeAntesDeConectarse(detalle))
        assertEquals("Antes de conectarse: le debías \$30.000 a Caro", lineaDeAntesDeConectarse(detalle.copy(deAntesDeConectarse = -30_000)))
        assertNull(lineaDeAntesDeConectarse(detalle.copy(deAntesDeConectarse = 0)))
        assertNull(lineaDeAntesDeConectarse(detalle.copy(espacio = casa)))
        val mensaje = mensajeParaPedir(detalle, zona = zona)!!
        assertTrue("Me debes \$50.000" in mensaje, mensaje)
        assertTrue("Mercado viejo" !in mensaje && "Incluye" !in mensaje, mensaje)
        val saldado = saldadoDeLoDeAntes(detalle, "liq_x", dia(6))!!
        assertEquals(listOf("dst_caro", MIEMBRO_YO, "30000", "true"), listOf(saldado.de, saldado.a, saldado.monto.toString(), saldado.deAntes.toString()))
    }

    @Test
    fun `el codigo dice que caracteres no existen`() {
        assertEquals("Los códigos de Movi no usan 0, O, 1, I ni L: revisa esa letra", razonDelCodigo("K7P3O9"))
        assertEquals("El código tiene 6 letras y números", razonDelCodigo("K7P"))
        assertNull(razonDelCodigo("K7P3Q9"))
    }
}
