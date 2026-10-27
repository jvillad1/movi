package com.jvillada.movi.ui.espacios

import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.RepartoDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.repository.MovimientoSinSubir
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «Repartir este gasto» une dos caminos que ya existen (vuelta de diseño 23)
 *
 * «Pagué yo» es el guardado de Agregar seguido del «Repartir» del detalle de un movimiento, con el
 * movimiento que se acaba de guardar; «Pagó otro» es lo que anota la hoja «Pagó otro». Lo que se fija
 * acá es el orden y los datos, contra caminos falsos que anotan lo que les llega.
 */
class RepartirDesdeAgregarTest {

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private val gasto = GastoParaRepartir(
        monto = 45_000,
        categoria = "Mercado",
        nota = "",
        momento = 1_790_000_000_000,
        fecha = "Hoy",
        cuenta = "Nu",
    )

    private val guardado = FinancialEvent(
        id = "ev_1", accountId = "nu", type = TransactionType.EXPENSE, amount = 45_000,
        category = "Mercado", description = "Mercado", timestamp = 1_790_000_000_000,
    )

    /** Lo que llegó a cada camino, en orden. */
    private val llamadas = mutableListOf<String>()
    private val repartidos = mutableListOf<Pair<String, String>>()
    private val anotados = mutableListOf<Pair<String, GastoDelEspacio>>()

    private fun caminos(
        guardarFalla: Boolean = false,
        repartirFalla: Boolean = false,
        fallaDelReparto: Throwable = ApiException(404, "Movimiento no encontrado"),
    ) = CaminosDeRepartir(
        guardarElGasto = {
            llamadas += "guardar"
            if (guardarFalla) throw ApiException(400, "El monto no puede ser cero")
            guardado
        },
        repartirMovimiento = { espacioId, eventoId ->
            llamadas += "repartir"
            if (repartirFalla) throw fallaDelReparto
            repartidos += espacioId to eventoId
        },
        anotarGastoDeOtro = { espacioId, g ->
            llamadas += "anotarDeOtro"
            anotados += espacioId to g
        },
    )

    @Test
    fun `pague yo guarda el gasto y reparte ese mismo movimiento`() = runBlocking<Unit> {
        val r = repartirDesdeAgregar(gasto, DecisionDeRepartir(casa, MIEMBRO_YO), "gde_1", yaGuardado = null, caminos())
        assertEquals(listOf("guardar", "repartir"), llamadas, "primero se guarda, después se reparte")
        assertEquals(listOf("esp_casa" to "ev_1"), repartidos)
        assertEquals(ResultadoDeRepartir.Listo(guardado), r)
        assertTrue(anotados.isEmpty(), "pagaste tú: no es un gasto de otro")
    }

    @Test
    fun `pago otro va por su camino y no toca tus cuentas`() = runBlocking<Unit> {
        val r = repartirDesdeAgregar(gasto, DecisionDeRepartir(casa, "dst_caro"), "gde_1", yaGuardado = null, caminos())
        assertEquals(listOf("anotarDeOtro"), llamadas)
        val (espacioId, g) = anotados.single()
        assertEquals("esp_casa", espacioId)
        assertEquals("gde_1", g.id)
        assertEquals("dst_caro", g.pagadoPor)
        assertEquals(45_000L, g.monto)
        assertEquals("Mercado", g.categoria)
        assertEquals("Mercado", g.descripcion, "sin nota, la descripción es la categoría (como la hoja «Pagó otro»)")
        assertEquals(gasto.momento, g.fecha, "con la fecha elegida en Agregar")
        assertEquals(ResultadoDeRepartir.Listo(null), r)
    }

    @Test
    fun `si no se guardo no se reparte nada`() = runBlocking<Unit> {
        val r = repartirDesdeAgregar(gasto, DecisionDeRepartir(casa, MIEMBRO_YO), "gde_1", null, caminos(guardarFalla = true))
        assertEquals(listOf("guardar"), llamadas)
        assertIs<ResultadoDeRepartir.NoSeGuardo>(r)
    }

    @Test
    fun `guardado y sin repartir, el reintento reparte sin volver a guardar`() = runBlocking<Unit> {
        val primero = repartirDesdeAgregar(gasto, DecisionDeRepartir(casa, MIEMBRO_YO), "gde_1", null, caminos(repartirFalla = true))
        val sinRepartir = assertIs<ResultadoDeRepartir.GuardadoSinRepartir>(primero)
        assertEquals(guardado, sinRepartir.evento)
        llamadas.clear()
        val segundo = repartirDesdeAgregar(gasto, DecisionDeRepartir(casa, MIEMBRO_YO), "gde_1", sinRepartir.evento, caminos())
        assertEquals(listOf("repartir"), llamadas, "el gasto ya estaba: guardarlo otra vez lo duplicaría")
        assertEquals(ResultadoDeRepartir.Listo(guardado), segundo)
    }

    @Test
    fun `la nota escrita es la descripcion de lo que pago otro`() {
        val g = gastoDeOtroDesdeAgregar(gasto.copy(nota = " Mercado del sábado ", categoria = " Mercado "), DecisionDeRepartir(casa, "dst_caro"), "gde_2")
        assertEquals("Mercado del sábado", g.descripcion)
        assertEquals("Mercado", g.categoria)
    }

    @Test
    fun `como se reparte dice la regla del espacio`() {
        assertEquals("Mitad y mitad", comoSeReparte(casa))
        val porcentajes = casa.copy(
            reparto = RepartoDelEspacio.PORCENTAJES,
            miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan", 60), MiembroDelEspacio("dst_caro", "Caro", 40)),
        )
        assertEquals("Tú 60 % · Caro 40 %", comoSeReparte(porcentajes))
    }

    @Test
    fun `que falta para repartir`() {
        assertEquals("Falta el monto", queFaltaParaRepartir(gasto.copy(monto = 0), pagueYo = true))
        assertEquals("Falta la categoría", queFaltaParaRepartir(gasto.copy(categoria = " "), pagueYo = false))
        assertEquals("Falta la cuenta", queFaltaParaRepartir(gasto.copy(faltaParaGuardar = "Falta la cuenta"), pagueYo = true))
        assertNull(queFaltaParaRepartir(gasto.copy(faltaParaGuardar = "Falta la cuenta"), pagueYo = false), "si pagó otro, la cuenta no importa")
        assertEquals(
            "Por ahora solo se reparten gastos en pesos",
            queFaltaParaRepartir(gasto.copy(moneda = "USD"), pagueYo = true),
        )
        assertNull(queFaltaParaRepartir(gasto, pagueYo = true))
    }

    /*
     * Vuelta de diseño 24: lo que dice la hoja cuando el gasto quedó anotado y el reparto no. En el
     * teléfono, sin señal, el gasto no sube y `LocalRepository.repartirMovimiento` lanza
     * [MovimientoSinSubir] sin pedir el reparto.
     */

    @Test
    fun `sin señal el gasto queda anotado y la hoja pide conexion para repartir`() = runBlocking<Unit> {
        val r = repartirDesdeAgregar(
            gasto, DecisionDeRepartir(casa, MIEMBRO_YO), "gde_1", null,
            caminos(repartirFalla = true, fallaDelReparto = MovimientoSinSubir("ev_1")),
        )
        val sinRepartir = assertIs<ResultadoDeRepartir.GuardadoSinRepartir>(r)
        assertEquals(REPARTO_SIN_CONEXION, sinRepartir.motivo)
        assertEquals(guardado, sinRepartir.evento, "el reintento reparte este, no guarda otro")
        assertEquals(listOf("guardar", "repartir"), llamadas)
    }

    @Test
    fun `el motivo dice lo que paso con el gasto`() {
        assertEquals(REPARTO_SIN_CONEXION, motivoDelRepartoPendiente(java.io.IOException("sin red")))
        assertEquals(REPARTO_SIN_CONEXION, motivoDelRepartoPendiente(MovimientoSinSubir("ev_1")))
        assertEquals(
            "El gasto quedó anotado en este teléfono, pero el servidor no lo recibió: La categoría no existe",
            motivoDelRepartoPendiente(MovimientoSinSubir("ev_1", rechazo = "La categoría no existe")),
        )
        assertTrue(
            motivoDelRepartoPendiente(ApiException(404, "Movimiento no encontrado"))
                .startsWith("El gasto quedó anotado, pero no se pudo repartir: "),
        )
    }
}
