package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CapturaDeSms
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La instantánea del Inicio, sobre un almacén de mentira (un mapa): acá `Settings()` no existe, y
 * lo que se prueba es la lógica — la clave por usuario, la ida y vuelta, y que nada de lo que venga
 * guardado pueda tumbar el arranque.
 */
class InstantaneaDelInicioTest {

    private val guardado = mutableMapOf<String, String>()
    private val instantanea = instantaneaEnMemoria(guardado)

    private val datos = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 22_152_488, egresos = 33_882_000),
        accounts = listOf(
            Account(id = "a1", name = "Cuenta de ahorros", type = AccountType.SAVINGS, balance = 558_350),
            Account(id = "l1", name = "Hipoteca", type = AccountType.LOAN, balance = 2_191_000_000),
        ),
        credits = emptyList(),
        cards = emptyList(),
        upcoming = emptyList(),
        spentByCategory = mapOf("Comida" to 2_000_000L),
        pendingSms = 3,
        captura = CapturaDeSms(total = 11, ultimo = "2026-09-22 08:15"),
        gastoVariablePorDia = mapOf("2026-09-21" to 150_000L),
        plataDelDisponible = PlataDelDisponible(1_386_694, 33_930_447, 500_000),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    @Test
    fun `lo que se guarda vuelve igual`() {
        instantanea.guardarDatos("u1", datos)
        assertEquals(datos, instantanea.datos("u1"))
    }

    @Test
    fun `la definicion tambien vuelve igual`() {
        val definicion = defaultDashboardDefinition()
        instantanea.guardarDefinicion("u1", definicion)
        assertEquals(definicion, instantanea.definicion("u1"))
    }

    @Test
    fun `otro usuario no lee la del primero`() {
        instantanea.guardarDatos("u1", datos)
        instantanea.guardarDefinicion("u1", defaultDashboardDefinition())
        assertNull(instantanea.datos("u2"))
        assertNull(instantanea.definicion("u2"))
    }

    @Test
    fun `sin usuario no se lee ni se escribe nada`() {
        instantanea.guardarDatos(null, datos)
        instantanea.guardarDatos("  ", datos)
        instantanea.guardarDefinicion(null, defaultDashboardDefinition())
        assertTrue(guardado.isEmpty(), "sin usuario no hay clave a la que escribir")
        assertNull(instantanea.datos(null))
        assertNull(instantanea.definicion(null))
    }

    /**
     * Una instantánea escrita por otra versión de la app, o a medio escribir: vale `null` y el Inicio
     * arranca como antes de que esto existiera. Nunca una excepción.
     */
    @Test
    fun `lo que no deserializa vale null sin excepcion`() {
        instantanea.guardarDatos("u1", datos)
        instantanea.guardarDefinicion("u1", defaultDashboardDefinition())
        guardado.keys.toList().forEach { guardado[it] = "{\"accounts\": [{\"id\": 3" }
        assertNull(instantanea.datos("u1"))
        assertNull(instantanea.definicion("u1"))

        // Un tipo equivocado en un campo conocido, que es como se ve un cambio de modelo.
        guardado.keys.toList().forEach { guardado[it] = "{\"pendingSms\": \"muchos\", \"version\": \"x\"}" }
        assertNull(instantanea.datos("u1"))
        assertNull(instantanea.definicion("u1"))
    }

    /** Un APK anterior leyendo lo que escribió uno posterior: los campos que no conoce se ignoran. */
    @Test
    fun `un campo desconocido no la invalida`() {
        guardado["inicio_instantanea_u1"] = "{\"pendingSms\": 4, \"campoDelFuturo\": {\"x\": 1}}"
        assertEquals(DashboardData(pendingSms = 4), instantanea.datos("u1"))
    }

    @Test
    fun `borrar se lleva las dos cosas de ese usuario y nada mas`() {
        instantanea.guardarDatos("u1", datos)
        instantanea.guardarDefinicion("u1", defaultDashboardDefinition())
        instantanea.guardarDatos("u2", datos)

        instantanea.borrar("u1")

        assertNull(instantanea.datos("u1"))
        assertNull(instantanea.definicion("u1"))
        assertEquals(datos, instantanea.datos("u2"))
    }

    @Test
    fun `olvidar la definicion deja los datos`() {
        instantanea.guardarDatos("u1", datos)
        instantanea.guardarDefinicion("u1", defaultDashboardDefinition())

        instantanea.olvidarDefinicion("u1")

        assertNull(instantanea.definicion("u1"))
        assertEquals(datos, instantanea.datos("u1"))
    }

    /** En esta JVM `Settings()` no se puede construir: la del aparato tiene que tragárselo. */
    @Test
    fun `la del aparato no explota aunque no haya donde guardar`() {
        InstantaneaDelInicio.delAparato.guardarDatos("u1", datos)
        InstantaneaDelInicio.delAparato.guardarDefinicion("u1", defaultDashboardDefinition())
        InstantaneaDelInicio.delAparato.datos("u1")
        InstantaneaDelInicio.delAparato.definicion("u1")
        InstantaneaDelInicio.delAparato.borrar("u1")
    }
}

/**
 * Una instantánea sobre un mapa, en vez del `Settings` del aparato. La usan también las pruebas de
 * Robolectric (vía `InstantaneaDelInicio.sustitutoDePrueba`), donde `Settings()` no se construye.
 */
internal fun instantaneaEnMemoria(almacen: MutableMap<String, String>) = InstantaneaDelInicio(
    leer = { almacen[it] },
    escribir = { clave, valor -> if (valor == null) almacen.remove(clave) else almacen[clave] = valor },
)
