package com.jvillada.movi.aislamiento

import com.jvillada.movi.data.CuentaMasUsadaCache
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.FormaDeCategorias
import com.jvillada.movi.data.FormaDeCreditos
import com.jvillada.movi.data.FormaDeCuentas
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.formaEnMemoria
import com.jvillada.movi.data.LastAccountStore
import com.jvillada.movi.data.MemoriaDeCategoriasCache
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.ReminderChannelsCache
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.ScreenDefCache
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.RecuerdoDeCategoria
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.ReminderChannels
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.platform.Huella
import com.jvillada.movi.platform.HuellaDelAparato
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.PropositoDeHuella
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import com.jvillada.movi.ui.dashboard.instantaneaEnMemoria
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
        // `usosRecientes` solo lo llena `recordFromServer` (ver su KDoc): es la fila de chips de
        // «Agregar», y con resaca una prueba de la hoja arrancaría con chips de otra.
        UsedCategoriesCache.recordFromServer(
            listOf(UsedCategory("Transporte", listOf(TransactionType.EXPENSE), usosRecientes = 4)),
        )
        ScreenDefCache.dashboard = DEFINICION_DE_OTRA_PRUEBA
        DashboardDataCache.data = DashboardData()
        DashboardDataCache.cargadoEn = 1_700_000_000_000L
        DashboardDataCache.tickDeLaCarga = 7
        // La marca de «ya hizo su entrada»: con resaca, el Inicio de la prueba siguiente arrancaría
        // con la cifra quieta y una prueba de la animación mediría otra cosa.
        DashboardDataCache.entradasHechas += "hero.cifra"
        LastAccountStore.recordAccount("acc-de-otra-prueba")
        LastAccountStore.recordTransfer("acc-origen", "acc-destino")
        CuentaMasUsadaCache.recordFromServer("acc-de-otra-prueba")
        // `canales` solo lo escribe `cargar()`, así que se ensucia por el camino de verdad: con un
        // repositorio enchufado que conteste, que es exactamente lo que hace una pantalla. Lo
        // mismo para la memoria de categorías (Task 5) — comparte el mismo repositorio de prueba.
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getReminderChannels(): ReminderChannels =
                ReminderChannels(email = true, emailTo = "alguien@ejemplo.com")
            override suspend fun getMemoriaDeCategorias(): List<RecuerdoDeCategoria> =
                listOf(RecuerdoDeCategoria(huella = "nombre:otraprueba", categoria = "Fútbol", nombre = "Otra Prueba", cuantos = 1))
        }
        runBlocking { ReminderChannelsCache.cargar() }
        runBlocking { MemoriaDeCategoriasCache.cargarSiHaceFalta() }
        DiasPlegadosStore.alternar("2024-03-15")
        // Ola B · tarea 6: un «Ahora no» de otra prueba.
        PropuestasDescartadasStore.marcar("unificar:otra>prueba")
        RecurringOfferGate.recordarLoQueYaHay(listOf(ARRIENDO), emptyList())
        Huella.sustitutoDePrueba = LECTOR_DE_OTRA_PRUEBA
        SessionManager.huellaActivada = true
        SessionManager.save(
            token = "token-de-otra-prueba",
            userId = "u1",
            name = "Otra Prueba",
            email = "otra@ejemplo.com",
        )
        // La instantánea del Inicio NO es un `object` en memoria: vive en el `Settings` del
        // aparato, que sobrevive a todo menos al logout. Acá, en un almacén de mentira que es
        // estático de este archivo (sobrevive de un método al siguiente, como el de verdad). La
        // limpia el logout con el id de la sesión que se va — por eso va después de `save`.
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(ALMACEN_DE_LA_INSTANTANEA)
        InstantaneaDelInicio.delAparato.guardarDatos("u1", DashboardData(pendingSms = 1))
        InstantaneaDelInicio.delAparato.guardarDefinicion("u1", DEFINICION_DE_OTRA_PRUEBA)
        // La forma recordada de Créditos/Categorías/Cuentas: mismo caso que la instantánea — vive
        // en el aparato, con clave por id, y la borra el logout.
        FormaRecordada.sustitutoDePrueba = formaEnMemoria(ALMACEN_DE_LA_FORMA)
        FormaRecordada.delAparato.guardarCreditos("u1", FormaDeCreditos(renglonesDelAvisoRojo = 2, prestamos = 12))
        FormaRecordada.delAparato.guardarCategorias("u1", FormaDeCategorias(renglonesDeLaTarjetaDeOrden = 1, filas = 20))
        FormaRecordada.delAparato.guardarCuentas("u1", FormaDeCuentas(renglonesDelPatrimonio = 4, filasPorGrupo = listOf(5, 2)))

        // No se afirma «quedó sucio» por prolijidad: si alguno de estos setters dejara de escribir,
        // el método de abajo pasaría sin ejercitar nada y esta clase sería decorativa.
        assertTrue("«Mercado» no entró al caché", "Mercado" in UsedCategoriesCache.used)
        assertTrue("Los usos recientes no quedaron cargados", UsedCategoriesCache.usosRecientes.isNotEmpty())
        assertTrue("La sesión no quedó puesta", SessionManager.loggedIn)
        assertTrue("El día no quedó plegado", "2024-03-15" in DiasPlegadosStore.plegados())
        assertTrue(
            "El «Ahora no» no quedó guardado",
            PropuestasDescartadasStore.estaDescartada("unificar:otra>prueba"),
        )
        assertNotNull("Los canales de aviso no quedaron cargados", ReminderChannelsCache.canales)
        assertTrue("La memoria de categorías no quedó cargada", MemoriaDeCategoriasCache.recuerdos.isNotEmpty())
        assertNotNull("La última cuenta no quedó guardada", LastAccountStore.lastAccountId)
        assertNotNull("La cuenta más usada no quedó guardada", CuentaMasUsadaCache.id)
        assertNotNull("La definición de pantalla no quedó cacheada", ScreenDefCache.dashboard)
        assertNotNull("El repositorio de prueba no quedó enchufado", Repositories.sustitutoDePrueba)
        assertNotNull("El lector de huellas de prueba no quedó enchufado", Huella.sustitutoDePrueba)
        assertTrue("«Entrar con huella» no quedó prendida", SessionManager.huellaActivada)
        assertNotNull("La instantánea del Inicio no quedó guardada", InstantaneaDelInicio.delAparato.datos("u1"))
        assertNotNull("La definición del Inicio no quedó guardada", InstantaneaDelInicio.delAparato.definicion("u1"))
        assertNotNull("La forma de Créditos no quedó guardada", FormaRecordada.delAparato.creditos("u1"))
        assertNotNull("La forma de Categorías no quedó guardada", FormaRecordada.delAparato.categorias("u1"))
        assertNotNull("La forma de Cuentas no quedó guardada", FormaRecordada.delAparato.cuentas("u1"))
    }

    @Test
    fun bLosGlobalesLleganEnCero() {
        assertEquals("UsedCategoriesCache trae la resaca del método anterior", emptyMap<String, Any>(), UsedCategoriesCache.used)
        assertEquals("Las preferencias de categoría traen resaca", emptyMap<String, Any>(), UsedCategoriesCache.prefs)
        assertEquals("Los usos recientes de categoría traen resaca", emptyMap<String, Int>(), UsedCategoriesCache.usosRecientes)
        assertNull("ScreenDefCache trae resaca", ScreenDefCache.dashboard)
        assertNull("DashboardDataCache trae resaca", DashboardDataCache.data)
        assertEquals("DashboardDataCache trae la marca de tiempo anterior", 0L, DashboardDataCache.cargadoEn)
        assertEquals("DashboardDataCache trae el tick anterior", 0, DashboardDataCache.tickDeLaCarga)
        assertEquals("DashboardDataCache trae entradas ya hechas", emptySet<String>(), DashboardDataCache.entradasHechas)
        assertNull("ReminderChannelsCache trae resaca", ReminderChannelsCache.canales)
        assertEquals("MemoriaDeCategoriasCache trae resaca", emptyList<RecuerdoDeCategoria>(), MemoriaDeCategoriasCache.recuerdos)
        assertNull("LastAccountStore trae la cuenta de otra prueba", LastAccountStore.lastAccountId)
        assertNull("CuentaMasUsadaCache trae la cuenta de otra prueba", CuentaMasUsadaCache.id)
        assertNull("LastAccountStore trae el origen de otra prueba", LastAccountStore.lastTransferFromId)
        assertNull("LastAccountStore trae el destino de otra prueba", LastAccountStore.lastTransferToId)
        assertEquals("DiasPlegadosStore trae los días de otra prueba", emptySet<String>(), DiasPlegadosStore.plegados())
        assertEquals(
            "PropuestasDescartadasStore trae los «Ahora no» de otra prueba",
            emptySet<String>(),
            PropuestasDescartadasStore.descartadas(),
        )
        assertFalse("La sesión de otra prueba sigue abierta", SessionManager.loggedIn)
        assertNull("El token de otra prueba sigue puesto", SessionManager.token)
        assertNull("El repositorio de prueba de otra clase sigue enchufado", Repositories.sustitutoDePrueba)
        assertNull("El lector de huellas de otra clase sigue enchufado", Huella.sustitutoDePrueba)
        assertFalse("«Entrar con huella» trae la resaca del método anterior", SessionManager.huellaActivada)
        assertEquals("La instantánea del Inicio de otra prueba sigue guardada", emptyMap<String, String>(), ALMACEN_DE_LA_INSTANTANEA)
        assertNull("El almacén de mentira de la instantánea sigue enchufado", InstantaneaDelInicio.sustitutoDePrueba)
        assertEquals("La forma recordada de otra prueba sigue guardada", emptyMap<String, String>(), ALMACEN_DE_LA_FORMA)
        assertNull("El almacén de mentira de la forma sigue enchufado", FormaRecordada.sustitutoDePrueba)

        // El estado de `RecurringOfferGate` es privado; lo único que lo delata es lo que ofrece.
        // Sin repositorio enchufado, limpio devuelve dos listas vacías; sucio devolvería la regla
        // que quedó cacheada arriba.
        val (reglas, cobros) = runBlocking { RecurringOfferGate.listasParaMovimientos() }
        assertEquals("RecurringOfferGate trae las reglas cacheadas por otra prueba", emptyList<RecurringRule>(), reglas)
        assertEquals("RecurringOfferGate trae las suscripciones cacheadas por otra prueba", emptyList<Any>(), cobros)
    }
}

private val DEFINICION_DE_OTRA_PRUEBA = defaultDashboardDefinition()

private val ALMACEN_DE_LA_INSTANTANEA = mutableMapOf<String, String>()

private val ALMACEN_DE_LA_FORMA = mutableMapOf<String, String>()

private val LECTOR_DE_OTRA_PRUEBA = object : HuellaDelAparato {
    override fun estado() = EstadoDeHuella.LISTA
    override fun pedir(proposito: PropositoDeHuella, alTerminar: (ResultadoDeHuella) -> Unit) = Unit
}

private val ARRIENDO = RecurringRule(
    id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
    amount = 1_800_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
)
