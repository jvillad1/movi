package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.espacios.rotuloDeRepartirCon
import com.jvillada.movi.ui.espacios.textoDeLoAnotado
import com.jvillada.movi.ui.espacios.textoDeRepartido
import com.jvillada.movi.ui.sms.lineaDelYaAnotado
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 74 · «un toque por movimiento», la parte sin dibujo. */
class Vuelta74LogicaTest {

    private val hoy = LocalDate(2026, 10, 29)
    private fun a(dia: LocalDate) = dia.atTime(LocalTime(12, 0)).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private val restaurantes = FinancialEvent(
        id = "ev-1", accountId = "a1", type = TransactionType.EXPENSE, amount = 32_000L,
        category = "Restaurantes", description = "Restaurantes", timestamp = a(hoy),
    )

    @Test
    fun `la linea del ya anotado dice cual y cuando, o cuantos`() {
        assertEquals("Ya anotado: Restaurantes \$32.000 · hoy", lineaDelYaAnotado(listOf(restaurantes), hoy))
        assertEquals(
            "Ya anotado: Cuota Carro \$980.000 · ayer",
            lineaDelYaAnotado(listOf(restaurantes.copy(description = "Cuota Carro", amount = 980_000L, timestamp = a(LocalDate(2026, 10, 28)))), hoy),
        )
        assertEquals("Puede ser uno de 2 anotados", lineaDelYaAnotado(listOf(restaurantes, restaurantes.copy(id = "ev-2")), hoy))
    }

    @Test
    fun `lo que se repone en la bandeja tras Es este solo cambia esos avisos`() {
        val avisos = listOf(
            SmsMessage(id = "a", time = "2026-10-29 13:05", bank = "85540", text = "x", state = SMS_STATE_PENDING, det = ""),
            SmsMessage(id = "b", time = "2026-10-29 14:05", bank = "85540", text = "y", state = SMS_STATE_PENDING, det = ""),
        )
        val repuestos = conLosAvisosConfirmados(avisos, setOf("a"))
        assertEquals(listOf(SMS_STATE_CONFIRMED, SMS_STATE_PENDING), repuestos.map { it.state })
        assertEquals(avisos.map { it.copy(state = "") }, repuestos.map { it.copy(state = "") })
    }

    @Test
    fun `los textos del aviso de abajo`() {
        val casa = Espacio(id = "e1", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("d1", "Caro")))
        assertEquals("Anotado · Restaurantes \$32.000", textoDeLoAnotado(restaurantes))
        assertEquals("Anotado · Traspaso \$500.000", textoDeLoAnotado("Traspaso", 500_000L, "COP"))
        assertEquals("Repartir con Caro", rotuloDeRepartirCon(casa))
        assertEquals("Repartido con Caro · tu parte \$16.000", textoDeRepartido(casa, 16_000L))
    }

    @Test
    fun `una accion que se dice a si misma deja su aviso, y deshacer cuenta`() = sinEsperar {
        val aviso = AvisoDeDeshacer()
        aviso.mostrar("Anotado · Restaurantes \$32.000", accion = AvisoDeDeshacer.Accion("Repartir con Caro", hacer = {
            aviso.mostrar("Repartido con Caro · tu parte \$16.000") {}
        })) {}
        aviso.tocarLaAccion()
        assertEquals("Repartido con Caro · tu parte \$16.000", aviso.actual?.texto)
        assertNull(aviso.actual?.accion)
        aviso.deshacer()
        assertNull(aviso.actual)
        assertEquals(1, aviso.deshechos)
    }

    @Test
    fun `una accion con opciones abre la eleccion y no corre nada`() = sinEsperar {
        var hechas = 0
        val aviso = AvisoDeDeshacer()
        aviso.mostrar("Anotado", accion = AvisoDeDeshacer.Accion("Repartir", opciones = listOf(
            AvisoDeDeshacer.Opcion("Casa") { hechas++ },
            AvisoDeDeshacer.Opcion("Viaje") { hechas++ },
        )))
        aviso.tocarLaAccion()
        assertTrue(aviso.eligiendo)
        assertEquals(0, hechas)
        aviso.elegir(aviso.actual!!.accion!!.opciones!![1])
        assertEquals(1, hechas)
        assertNull(aviso.actual, "hecha, el aviso se cierra")
    }

    /** Corre un bloque `suspend` que no suspende de verdad (todo es inmediato acá). */
    private fun sinEsperar(bloque: suspend () -> Unit) {
        var fallo: Throwable? = null
        bloque.startCoroutine(Continuation(EmptyCoroutineContext) { r -> fallo = r.exceptionOrNull() })
        fallo?.let { throw it }
    }
}
