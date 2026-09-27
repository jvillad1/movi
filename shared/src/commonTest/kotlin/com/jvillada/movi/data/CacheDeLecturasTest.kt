package com.jvillada.movi.data

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.Scope
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Lo que [CacheDeLecturas] promete no mostrar nunca como «lo último», y cómo [Lectura] pasa de lo
 * recordado a lo leído. Lo que falta —que TODA escritura vacíe el cache y cómo se ve en una
 * pantalla— vive en `androidUnitTest` (`CadaEscrituraVaciaLoRecordadoTest`,
 * `MovimientosRecuerdaLoUltimoTest`), donde hay repositorio de prueba y Robolectric.
 */
class CacheDeLecturasTest {

    private val t0 = 1_758_000_000_000L
    private val DETALLE = DetalleDePeriodo(
        resumen = ResumenDePeriodo(id = "2026-08", nombre = "agosto", desde = "2026-07-25", hasta = "2026-08-24"),
    )
    private val dias = listOf(EventDay(date = "2026-09-20", total = 25_000L, items = emptyList()))

    @BeforeTest fun limpiarAntes() = SessionManager.clear()
    @AfterTest fun limpiarDespues() = SessionManager.clear()

    private fun entrarComo(id: String) = SessionManager.save(token = "t-$id", userId = id, name = id, email = "$id@ejemplo.com")

    @Test
    fun `sin nada guardado no hay nada que mostrar`() {
        entrarComo("u1")
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0))
        assertNull(Lectura<List<EventDay>>(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0)).valor)
    }

    @Test
    fun `lo guardado se muestra a la misma persona`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, "u1", t0)
        assertEquals(dias, CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0 + 1_000))
    }

    @Test
    fun `cada clave es suya, y la del detalle depende del periodo pedido`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, "u1", t0)
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.Cuentas, t0))
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.DetalleDePeriodo("2026-08"), t0))
    }

    @Test
    fun `lo de otra persona no se muestra`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, "u1", t0)
        entrarComo("u2")
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0))
    }

    @Test
    fun `sin sesion no se guarda ni se muestra`() {
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, null, t0)
        assertEquals(0, CacheDeLecturas.cuantas)
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0))
    }

    @Test
    fun `cerrar sesion lo borra todo`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, "u1", t0)
        SessionManager.clear()
        entrarComo("u1")
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0))
    }

    @Test
    fun `una lectura que vuelve despues de cerrar sesion no se guarda`() {
        entrarComo("u1")
        val usuarioAlSalir = SessionManager.userId
        SessionManager.clear()
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, usuarioAlSalir, t0)
        assertEquals(0, CacheDeLecturas.cuantas)

        // Ni aunque para cuando vuelva ya haya entrado otra persona.
        entrarComo("u2")
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, usuarioAlSalir, t0)
        assertEquals(0, CacheDeLecturas.cuantas)
    }

    @Test
    fun `borrarTodo, lo que corre tras cada escritura, no deja nada`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, "u1", t0)
        CacheDeLecturas.borrarTodo()
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0))
    }

    @Test
    fun `una lectura que salio antes de una escritura no se guarda al volver`() {
        entrarComo("u1")
        val generacionAlSalir = CacheDeLecturas.generacion
        CacheDeLecturas.borrarTodo() // se anuló un movimiento mientras la historia viajaba
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, "u1", t0, generacionAlLeer = generacionAlSalir)
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0))
    }

    @Test
    fun `pasada la edad maxima no se muestra`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, "u1", t0)
        val limite = t0 + CacheDeLecturas.EDAD_MAXIMA_PARA_MOSTRAR
        assertEquals(dias, CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, limite))
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, limite + 1))
        // Un reloj que volvió para atrás: no se sabe cuán vieja es.
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0 - 1))
    }

    @Test
    fun `lo guardado en otro periodo no se muestra en este`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.Periodos, emptyList(), "u1", t0, periodo = "2026-09")
        assertEquals(emptyList(), CacheDeLecturas.ultima(ClaveDeLectura.Periodos, t0, periodoVigente = "2026-09"))
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.Periodos, t0, periodoVigente = "2026-10"))
    }

    @Test
    fun `las claves que dependen del periodo no se muestran sin decir cual es el vigente`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.Periodos, emptyList(), "u1", t0, periodo = "2026-09")
        CacheDeLecturas.guardar(ClaveDeLectura.Presupuestos, emptyList(), "u1", t0, periodo = "2026-09")
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.Periodos, t0))
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.Presupuestos, t0))
    }

    @Test
    fun `las claves que dependen del periodo no se guardan sin periodo`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.Periodos, emptyList(), "u1", t0)
        CacheDeLecturas.guardar(ClaveDeLectura.Presupuestos, emptyList(), "u1", t0)
        CacheDeLecturas.guardar(ClaveDeLectura.DetalleDePeriodo("2026-09"), DETALLE, "u1", t0)
        CacheDeLecturas.guardar(ClaveDeLectura.ResumenDelTablero(Scope.SELF), DashboardSummary(), "u1", t0)
        assertEquals(0, CacheDeLecturas.cuantas)
    }

    @Test
    fun `cuales dependen del periodo`() {
        assertTrue(ClaveDeLectura.Periodos.dependeDelPeriodo)
        assertTrue(ClaveDeLectura.Presupuestos.dependeDelPeriodo)
        assertTrue(ClaveDeLectura.DetalleDePeriodo("2026-09").dependeDelPeriodo)
        assertTrue(ClaveDeLectura.ResumenDelTablero(Scope.SELF).dependeDelPeriodo)
        assertFalse(ClaveDeLectura.EventosPorDia.dependeDelPeriodo)
        assertFalse(ClaveDeLectura.Perfil.dependeDelPeriodo)
        assertFalse(ClaveDeLectura.Cuentas.dependeDelPeriodo)
    }

    @Test
    fun `el detalle de un periodo se muestra en ese periodo`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.DetalleDePeriodo("2026-08"), DETALLE, "u1", t0, periodo = "2026-09")
        assertEquals(DETALLE, CacheDeLecturas.ultima(ClaveDeLectura.DetalleDePeriodo("2026-08"), t0, periodoVigente = "2026-09"))
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.DetalleDePeriodo("2026-08"), t0, periodoVigente = "2026-10"))
    }

    @Test
    fun `lo vencido se suelta al encontrarlo`() {
        entrarComo("u1")
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, "u1", t0)
        CacheDeLecturas.guardar(ClaveDeLectura.Cuentas, emptyList(), "u1", t0 + 10)
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.EventosPorDia, t0 + CacheDeLecturas.EDAD_MAXIMA_PARA_MOSTRAR + 1))
        assertEquals(1, CacheDeLecturas.cuantas, "la historia vencida ya no ocupa memoria; las cuentas siguen")
    }

    // ── Lectura ──────────────────────────────────────────────────────────────────────────────

    private val ahorros = Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 100L)

    @Test
    fun `con algo recordado, arranca mostrando lo ultimo y actualizando`() {
        val lectura = Lectura(listOf(ahorros))
        assertEquals(listOf(ahorros), lectura.valor)
        assertTrue(lectura.actualizando)
        assertTrue(lectura.mostrandoLoUltimo)
        assertFalse(lectura.fallo)
    }

    @Test
    fun `sin nada recordado, no llego y no muestra lo ultimo`() {
        val lectura = Lectura<List<Account>>(null)
        assertNull(lectura.valor)
        assertTrue(lectura.actualizando)
        assertFalse(lectura.mostrandoLoUltimo)
    }

    @Test
    fun `una lectura igual a lo que habia no lo reemplaza`() {
        val recordado = listOf(ahorros)
        val lectura = Lectura(recordado)
        lectura.alEmpezar()
        lectura.alContestar(listOf(ahorros.copy()))
        assertSame(recordado, lectura.valor, "lo mismo leído otra vez no es una lista nueva")
        assertFalse(lectura.actualizando)
        assertFalse(lectura.mostrandoLoUltimo)
    }

    @Test
    fun `una lectura distinta reemplaza lo que habia`() {
        val lectura = Lectura(listOf(ahorros))
        lectura.alEmpezar()
        val nuevo = listOf(ahorros.copy(balance = 200L))
        lectura.alContestar(nuevo)
        assertSame(nuevo, lectura.valor)
    }

    @Test
    fun `si falla, lo que habia se queda y se sabe que es lo ultimo`() {
        val recordado = listOf(ahorros)
        val lectura = Lectura(recordado)
        lectura.alEmpezar()
        lectura.alFallar(IllegalStateException("sin red"))
        assertSame(recordado, lectura.valor)
        assertTrue(lectura.fallo)
        assertTrue(lectura.mostrandoLoUltimo)
        assertFalse(lectura.actualizando)
        assertTrue(lectura.terminada)
    }

    @Test
    fun `si falla sin nada recordado, sigue sin llegar`() {
        val lectura = Lectura<List<Account>>(null)
        lectura.alEmpezar()
        lectura.alFallar(IllegalStateException("sin red"))
        assertNull(lectura.valor)
        assertTrue(lectura.fallo)
        assertFalse(lectura.mostrandoLoUltimo)
    }

    @Test
    fun `reintentar despues de una falla borra el aviso mientras viaja`() {
        val lectura = Lectura(listOf(ahorros))
        lectura.alEmpezar()
        val primera = IllegalStateException("uno")
        lectura.alFallar(primera)
        lectura.alEmpezar()
        assertFalse(lectura.fallo)
        assertTrue(lectura.actualizando)
        val segunda = IllegalStateException("dos")
        lectura.alFallar(segunda)
        assertNotSame(primera, lectura.error, "cada falla es una llave nueva para avisar otra vez")
    }
}
