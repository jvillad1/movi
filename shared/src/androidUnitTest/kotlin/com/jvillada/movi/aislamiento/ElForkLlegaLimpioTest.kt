package com.jvillada.movi.aislamiento

import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.LastAccountStore
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.ReminderChannelsCache
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.ScreenDefCache
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.ReminderChannels
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner

/**
 * # La resaca de una prueba no llega a la siguiente
 *
 * Es la prueba de `AppDePrueba` — leé su KDoc para el porqué. Acá se afirma el mecanismo, no la
 * intención: [aEnsuciaTodosLosGlobales] deja sucio **cada** `object` que una pantalla puede llenar
 * y no limpia nada; [bLosGlobalesLleganEnCero] afirma que llegaron en cero igual.
 *
 * **Por qué dos métodos de la misma clase y no dos clases.** Robolectric rehace el `Application`
 * —y con él el `beforeTest` que limpia— **una vez por método**, así que dos métodos ejercitan
 * exactamente el mismo mecanismo que dos clases. Y el orden entre métodos se fija con
 * `@FixMethodOrder`, que es determinista; el orden entre clases lo decide Gradle y sería una
 * suposición.
 *
 * **Si alguien borra la línea de `robolectric.properties`, o el `beforeTest`, o el logout deja de
 * limpiar uno de estos cachés, esto se pone rojo acá** — y no una vez de cada tres en la máquina
 * de otro, que es como se descubrió el defecto que originó todo esto.
 *
 * ## Lo que esta clase NO cubre
 *
 * La lista de abajo es la de **hoy**. Un `object` nuevo que una pantalla llene, y que el logout no
 * limpie, volvería a filtrarse sin que esto se entere. No hay forma de enumerarlos solo: lo que sí
 * hay es un único lugar donde arreglarlo.
 */
@RunWith(RobolectricTestRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ElForkLlegaLimpioTest {

    @Test
    fun aEnsuciaTodosLosGlobales() {
        UsedCategoriesCache.record("Mercado", TransactionType.EXPENSE)
        ScreenDefCache.dashboard = DEFINICION_DE_OTRA_PRUEBA
        DashboardDataCache.data = DashboardData()
        DashboardDataCache.cargadoEn = 1_700_000_000_000L
        DashboardDataCache.tickDeLaCarga = 7
        LastAccountStore.recordAccount("acc-de-otra-prueba")
        LastAccountStore.recordTransfer("acc-origen", "acc-destino")
        // `canales` solo lo escribe `cargar()`, así que se ensucia por el camino de verdad: con un
        // repositorio enchufado que conteste, que es exactamente lo que hace una pantalla.
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getReminderChannels(): ReminderChannels =
                ReminderChannels(email = true, emailTo = "alguien@ejemplo.com")
        }
        runBlocking { ReminderChannelsCache.cargar() }
        DiasPlegadosStore.alternar("2024-03-15")
        RecurringOfferGate.recordarLoQueYaHay(listOf(ARRIENDO), emptyList())
        SessionManager.save(
            token = "token-de-otra-prueba",
            userId = "u1",
            name = "Otra Prueba",
            email = "otra@ejemplo.com",
        )

        // No se afirma «quedó sucio» por prolijidad: si alguno de estos setters dejara de escribir,
        // el método de abajo pasaría sin ejercitar nada y esta clase sería decorativa.
        assertTrue("«Mercado» no entró al caché", "Mercado" in UsedCategoriesCache.used)
        assertTrue("La sesión no quedó puesta", SessionManager.loggedIn)
        assertTrue("El día no quedó plegado", "2024-03-15" in DiasPlegadosStore.plegados())
        assertNotNull("Los canales de aviso no quedaron cargados", ReminderChannelsCache.canales)
        assertNotNull("La última cuenta no quedó guardada", LastAccountStore.lastAccountId)
        assertNotNull("La definición de pantalla no quedó cacheada", ScreenDefCache.dashboard)
        assertNotNull("El repositorio de prueba no quedó enchufado", Repositories.sustitutoDePrueba)
    }

    @Test
    fun bLosGlobalesLleganEnCero() {
        assertEquals("UsedCategoriesCache trae la resaca del método anterior", emptyMap<String, Any>(), UsedCategoriesCache.used)
        assertEquals("Las preferencias de categoría traen resaca", emptyMap<String, Any>(), UsedCategoriesCache.prefs)
        assertNull("ScreenDefCache trae resaca", ScreenDefCache.dashboard)
        assertNull("DashboardDataCache trae resaca", DashboardDataCache.data)
        assertEquals("DashboardDataCache trae la marca de tiempo anterior", 0L, DashboardDataCache.cargadoEn)
        assertEquals("DashboardDataCache trae el tick anterior", 0, DashboardDataCache.tickDeLaCarga)
        assertNull("ReminderChannelsCache trae resaca", ReminderChannelsCache.canales)
        assertNull("LastAccountStore trae la cuenta de otra prueba", LastAccountStore.lastAccountId)
        assertNull("LastAccountStore trae el origen de otra prueba", LastAccountStore.lastTransferFromId)
        assertNull("LastAccountStore trae el destino de otra prueba", LastAccountStore.lastTransferToId)
        assertEquals("DiasPlegadosStore trae los días de otra prueba", emptySet<String>(), DiasPlegadosStore.plegados())
        assertFalse("La sesión de otra prueba sigue abierta", SessionManager.loggedIn)
        assertNull("El token de otra prueba sigue puesto", SessionManager.token)
        assertNull("El repositorio de prueba de otra clase sigue enchufado", Repositories.sustitutoDePrueba)

        // El estado de `RecurringOfferGate` es privado; lo único que lo delata es lo que ofrece.
        // Sin repositorio enchufado, limpio devuelve dos listas vacías; sucio devolvería la regla
        // que quedó cacheada arriba.
        val (reglas, cobros) = runBlocking { RecurringOfferGate.listasParaMovimientos() }
        assertEquals("RecurringOfferGate trae las reglas cacheadas por otra prueba", emptyList<RecurringRule>(), reglas)
        assertEquals("RecurringOfferGate trae las suscripciones cacheadas por otra prueba", emptyList<Any>(), cobros)
    }
}

private val DEFINICION_DE_OTRA_PRUEBA = defaultDashboardDefinition()

private val ARRIENDO = RecurringRule(
    id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
    amount = 1_800_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
)
