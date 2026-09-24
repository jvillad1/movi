package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import com.jvillada.movi.data.FormaDeCategorias
import com.jvillada.movi.data.FormaDeCreditos
import com.jvillada.movi.data.FormaDeCuentas
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.formaEnMemoria
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_INMUEBLE
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.accounts.TAG_GRUPO_DE_CUENTAS
import com.jvillada.movi.ui.accounts.TAG_TARJETA_DEL_PATRIMONIO
import com.jvillada.movi.ui.categorias.CategoriasScreen
import com.jvillada.movi.ui.categorias.TAG_TARJETA_DE_ORDEN
import com.jvillada.movi.ui.categorias.tagDeFilaDeCategoria
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.credits.TAG_ESQUELETO_DE_AVISO
import com.jvillada.movi.ui.credits.TAG_ESQUELETO_TARJETA_DE_PRESTAMO
import com.jvillada.movi.ui.credits.TAG_TARJETA_DEL_RESUMEN_DE_DEUDA
import kotlinx.coroutines.CompletableDeferred
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El esqueleto copia la forma de la última carga
 *
 * Medido en el teléfono del dueño: en Créditos la tarjeta de resumen traía el aviso ámbar y el rojo
 * que el esqueleto no reservaba (la lista bajaba ~130 dp al llegar), en Categorías la tarjeta de
 * «ordenar» aparecía con los datos (~50 dp), y en Cuentas el esqueleto tenía un grupo de 4 filas
 * donde había dos grupos con otras cantidades. Ver `FormaRecordada`.
 *
 * Cada prueba monta la pantalla **dos veces**, como el dueño al abrirla dos días seguidos:
 *
 * 1. La primera, con la lectura contestando al toque: la pantalla anota su forma. Se afirma lo que
 *    anotó —la forma de LOS DATOS, no una escrita a mano—.
 * 2. La segunda (`key` que cambia = pantalla nueva), con la lectura detenida en una
 *    [CompletableDeferred]: el esqueleto tiene que reservar lo mismo, y al contestar con los mismos
 *    datos el bloque de arriba mide lo mismo cargando que cargado, ±8 dp.
 *
 * Que la forma salga de la pantalla real y no de un literal es lo que hace que esto pruebe la
 * cadena entera: si mañana la tarjeta de resumen gana un grupo y `gruposDelResumen` no se entera,
 * la segunda vuelta mide distinto y esto se pone rojo.
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]` para que el texto mida lo que mide en el teléfono (ver el
 * KDoc de `Esqueleto.kt`), y la densidad de letra ×1,12 de `App.kt`: los renglones que ocupa un
 * aviso dependen de ella. La pantalla es alta para que las listas perezosas compongan todo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class LaFormaRecordadaEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val almacen = mutableMapOf<String, String>()

    /** Cambiarlo saca la pantalla de la composición y monta una nueva: la segunda apertura. */
    private val vuelta = mutableIntStateOf(0)

    @Before
    fun entrar() {
        SessionManager.save(token = "t", userId = USUARIO, name = "Dueño", email = "d@ejemplo.com")
        FormaRecordada.sustitutoDePrueba = formaEnMemoria(almacen)
    }

    private fun montar(pantalla: @Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
                // La escala de letra de Movi: `App.kt` la multiplica por 1,12 en toda la app.
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    key(vuelta.intValue) { Box(Modifier.fillMaxSize()) { pantalla() } }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun abrirDeNuevo() {
        vuelta.intValue++
        composeRule.waitForIdle()
    }

    private fun contarTag(tag: String): Int = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun caja(nodo: SemanticsNodeInteraction): DpRect = nodo.getUnclippedBoundsInRoot()

    private fun casiIgual(cargando: Float, cargado: Float, que: String) {
        val diferencia = abs(cargado - cargando)
        assertTrue(
            diferencia <= 8f,
            "$que: $cargando dp cargando y $cargado dp cargado — diferencia de $diferencia dp, el máximo son 8 dp",
        )
    }

    // ── Créditos ──────────────────────────────────────────────────────────────────

    @Test
    fun `Creditos reserva los dos avisos y los prestamos de la ultima carga`() {
        val puerta = CompletableDeferred<List<CreditSummary>>()
        var lecturas = 0
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> =
                if (lecturas++ == 0) CARTERA_DEL_DUENO else puerta.await()
            override suspend fun getCards(): List<CardSummary> = emptyList()
        }
        montar { CreditosScreen(onNavigate = {}) }

        val anotada = assertNotNull(FormaRecordada.delAparato.creditos(USUARIO), "la carga que salió bien no anotó su forma")
        assertEquals(3, anotada.prestamos)
        assertTrue(anotada.renglonesDelAvisoAmbar > 0, "la cartera tiene el aviso ámbar: $anotada")
        assertTrue(anotada.renglonesDelAvisoRojo > 0, "la cartera tiene el aviso rojo: $anotada")
        val altoDeLaPrimera = caja(composeRule.onNodeWithTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA)).height.value

        abrirDeNuevo()
        assertEquals(2, contarTag(TAG_ESQUELETO_DE_AVISO), "el esqueleto tiene que reservar los dos avisos")
        assertEquals(3, contarTag(TAG_ESQUELETO_TARJETA_DE_PRESTAMO))
        val cargando = caja(composeRule.onNodeWithTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA))

        puerta.complete(CARTERA_DEL_DUENO)
        composeRule.waitForIdle()
        assertEquals(0, contarTag(TAG_ESQUELETO_DE_AVISO))
        val cargado = caja(composeRule.onNodeWithTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA))

        casiIgual(altoDeLaPrimera, cargado.height.value, "La misma tarjeta con los mismos datos")
        casiIgual(cargando.height.value, cargado.height.value, "La tarjeta de «Deuda total» (resumen + avisos)")
    }

    @Test
    fun `Creditos sin nada recordado usa el esqueleto de siempre, sin avisos`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = CompletableDeferred<List<CreditSummary>>().await()
            override suspend fun getCards(): List<CardSummary> = emptyList()
        }
        montar { CreditosScreen(onNavigate = {}) }

        assertNull(FormaRecordada.delAparato.creditos(USUARIO))
        assertEquals(0, contarTag(TAG_ESQUELETO_DE_AVISO))
        assertEquals(3, contarTag(TAG_ESQUELETO_TARJETA_DE_PRESTAMO))
    }

    // ── Categorías ────────────────────────────────────────────────────────────────

    @Test
    fun `Categorias reserva la tarjeta de ordenar y la lista arranca donde arrancaba`() {
        val puerta = CompletableDeferred<List<CategoryUsage>>()
        var lecturas = 0
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> =
                if (lecturas++ == 0) CATEGORIAS_CON_ALGO_POR_ORDENAR else puerta.await()
        }
        montar { CategoriasScreen(onNavigate = {}) }

        val anotada = assertNotNull(FormaRecordada.delAparato.categorias(USUARIO), "la carga que salió bien no anotó su forma")
        assertTrue(anotada.renglonesDeLaTarjetaDeOrden > 0, "había tarjeta de ordenar: $anotada")
        assertEquals(CATEGORIAS_CON_ALGO_POR_ORDENAR.size, anotada.filas)

        abrirDeNuevo()
        assertEquals(1, contarTag(TAG_TARJETA_DE_ORDEN), "el esqueleto tiene que reservar la tarjeta de ordenar")
        assertEquals(CATEGORIAS_CON_ALGO_POR_ORDENAR.size, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
        val tarjetaCargando = caja(composeRule.onNodeWithTag(TAG_TARJETA_DE_ORDEN))
        val filaCargando = caja(composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).onFirst())

        puerta.complete(CATEGORIAS_CON_ALGO_POR_ORDENAR)
        composeRule.waitForIdle()
        assertEquals(0, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
        val tarjetaCargada = caja(composeRule.onNodeWithTag(TAG_TARJETA_DE_ORDEN))
        val primeraFila = CATEGORIAS_CON_ALGO_POR_ORDENAR
            .map { caja(composeRule.onNodeWithTag(tagDeFilaDeCategoria(it.name))) }
            .minBy { it.top.value }

        // El borde de ABAJO y no el alto: en la tarjeta real el tag queda en el mismo nodo de
        // semántica que su `clickable`, que reporta la caja de adentro del relleno de arriba; la
        // esqueleto no se toca y reporta la de afuera. Lo que empuja la lista es dónde termina.
        casiIgual(tarjetaCargando.bottom.value, tarjetaCargada.bottom.value, "El borde de abajo de la tarjeta de «ordenar»")
        casiIgual(filaCargando.top.value, primeraFila.top.value, "El borde de arriba de la primera fila")
        casiIgual(filaCargando.bottom.value, primeraFila.bottom.value, "El borde de abajo de la primera fila")
    }

    @Test
    fun `Categorias sin tarjeta la ultima vez no la reserva`() {
        almacen.clear()
        FormaRecordada.delAparato.guardarCategorias(USUARIO, FormaDeCategorias(renglonesDeLaTarjetaDeOrden = 0, filas = 2))
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = CompletableDeferred<List<CategoryUsage>>().await()
        }
        montar { CategoriasScreen(onNavigate = {}) }

        assertEquals(0, contarTag(TAG_TARJETA_DE_ORDEN))
        assertEquals(2, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
    }

    // ── Cuentas ───────────────────────────────────────────────────────────────────

    @Test
    fun `Cuentas dibuja los grupos con las filas de la ultima carga`() {
        val puerta = CompletableDeferred<List<Account>>()
        var lecturas = 0
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> =
                if (lecturas++ == 0) CUENTAS_DEL_DUENO else puerta.await()
            // Ola C, tarea 4: «Deudas» y «Te deben» leen lo suyo aparte — vacíos acá, esta prueba
            // mide la forma de la tarjeta del patrimonio y los grupos, no esas dos tarjetas.
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        }
        montar { AccountsScreen(onNavigate = {}) }

        assertEquals(
            FormaDeCuentas(renglonesDelPatrimonio = 4, filasPorGrupo = listOf(3, 1)),
            FormaRecordada.delAparato.cuentas(USUARIO),
        )

        abrirDeNuevo()
        assertEquals(2, contarTag(TAG_GRUPO_DE_CUENTAS), "dos grupos, como la última vez")
        assertEquals(4, contarTag(TAG_FILA_DE_LISTA_ESQUELETO), "3 filas de «Dinero» y 1 de «Inversión»")
        val patrimonioCargando = caja(composeRule.onNodeWithTag(TAG_TARJETA_DEL_PATRIMONIO))
        val gruposCargando = composeRule.onAllNodesWithTag(TAG_GRUPO_DE_CUENTAS).fetchSemanticsNodes().size
        val primerGrupoCargando = caja(composeRule.onAllNodesWithTag(TAG_GRUPO_DE_CUENTAS)[0])
        val segundoGrupoCargando = caja(composeRule.onAllNodesWithTag(TAG_GRUPO_DE_CUENTAS)[1])

        puerta.complete(CUENTAS_DEL_DUENO)
        composeRule.waitForIdle()
        assertEquals(0, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
        assertEquals(gruposCargando, contarTag(TAG_GRUPO_DE_CUENTAS))
        val patrimonioCargado = caja(composeRule.onNodeWithTag(TAG_TARJETA_DEL_PATRIMONIO))
        val primerGrupoCargado = caja(composeRule.onAllNodesWithTag(TAG_GRUPO_DE_CUENTAS)[0])
        val segundoGrupoCargado = caja(composeRule.onAllNodesWithTag(TAG_GRUPO_DE_CUENTAS)[1])

        casiIgual(patrimonioCargando.height.value, patrimonioCargado.height.value, "La tarjeta del patrimonio")
        casiIgual(primerGrupoCargando.height.value, primerGrupoCargado.height.value, "El primer grupo («Dinero»)")
        casiIgual(segundoGrupoCargando.top.value, segundoGrupoCargado.top.value, "Dónde arranca el segundo grupo")
        casiIgual(segundoGrupoCargando.height.value, segundoGrupoCargado.height.value, "El segundo grupo («Inversión»)")
    }

    @Test
    fun `Cuentas sin nada recordado usa el esqueleto de siempre`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = CompletableDeferred<List<Account>>().await()
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        }
        montar { AccountsScreen(onNavigate = {}) }

        assertEquals(1, contarTag(TAG_GRUPO_DE_CUENTAS))
        assertEquals(4, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
    }

    @Test
    fun `cerrar sesion se lleva la forma`() {
        FormaRecordada.delAparato.guardarCreditos(USUARIO, FormaDeCreditos(prestamos = 12))
        SessionManager.clear()
        assertTrue(almacen.isEmpty(), "quedó la forma del usuario que salió: $almacen")
    }
}

private const val USUARIO = "u-forma"

/**
 * Los tres casos de la cartera del dueño (ver `CreditosDiceLoQueCuestaLaDeudaTest`): uno que
 * amortiza, el hipotecario cuya deuda crece sola (aviso rojo, y lo paga otro: filas «ajenas» en el
 * resumen) y el de su mamá, que no se termina (aviso ámbar).
 */
private val CARTERA_DEL_DUENO = listOf(
    CreditSummary(
        account = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
            insuranceMonthly = 124_800L,
        ),
        paidPct = 0.5,
    ),
    CreditSummary(
        account = Account("acc_2334", "Hipotecario 2334", AccountType.LOAN, balance = 204_183_376L),
        terms = CreditTerms(
            accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
            termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
            insuranceMonthly = 209_219L, paidBy = "Skandia",
        ),
        paidPct = 0.0,
    ),
    CreditSummary(
        account = Account("acc_mama", "Crédito Mamá", AccountType.LOAN, balance = 100_000_000L),
        terms = CreditTerms(
            accountId = "acc_mama", bank = "Mamá", principal = 100_000_000L, rateEa = 16.7652,
            termMonths = 240, installment = 1_300_000L, dayOfMonth = 27, startDate = "2020-01-01",
        ),
        paidPct = 0.0,
    ),
)

/** Una del catálogo sin uso es una propuesta pendiente (ver `TarjetaDeOrdenTest`); las otras dos no. */
private val CATEGORIAS_CON_ALGO_POR_ORDENAR = listOf(
    CategoryUsage(name = "Arriendo recibido", scope = CategoryScope.PREDEFINED),
    CategoryUsage(name = "Comida", movements = 3),
    CategoryUsage(name = "Transporte", movements = 5),
)

/**
 * «Dinero» con tres cuentas (una de uso condicionado), «Inversión» con una, la casa (un bien, que no
 * es inversión) y su hipoteca: los cuatro renglones del patrimonio.
 */
private val CUENTAS_DEL_DUENO = listOf(
    Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L),
    Account("acc-afc", "AFC", AccountType.SAVINGS, 12_000_000L, condicionadaA = "vivienda"),
    Account("acc-efectivo", "Efectivo", AccountType.CASH, 200_000L),
    Account("acc-cdt", "CDT", AccountType.INVESTMENT, 30_000_000L),
    Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 1_030_600_000L),
    Account(
        "acc-casa", "Casa Almendros", AccountType.INVESTMENT, 0L,
        bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = "2026-08-28", deudaId = "acc-1254"),
    ),
)
