package com.jvillada.movi.ui.periodos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.FUENTE_CREDITO
import com.jvillada.movi.shared.model.FUENTE_SALDO_INICIAL
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.FuenteDePlata
import com.jvillada.movi.shared.model.GastoDeCategoria
import com.jvillada.movi.shared.model.PAGO_FIJO_CON_DUDAS
import com.jvillada.movi.shared.model.PAGO_FIJO_LISTO
import com.jvillada.movi.shared.model.PAGO_FIJO_PENDIENTE
import com.jvillada.movi.shared.model.PagoFijoDelPeriodo
import com.jvillada.movi.shared.model.PresupuestoDelPeriodo
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.data.Lectura
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El detalle de un período, ya montado
 *
 * Con los datos reales del dueño: corte 25 y octubre arrancado a mano el 24 de septiembre. «Hoy» se
 * fija en el 20 de octubre de 2026, el día en que el sueldo de noviembre llegó antes del corte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class DetalleDePeriodoScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = LocalDate(2026, 10, 20)

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private val mercado = FinancialEvent(
        id = "ev-mercado", accountId = banco.id, type = TransactionType.EXPENSE, amount = 420_000L,
        category = "Mercado", description = "Éxito Laureles", timestamp = 1_790_000_000_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
    )
    private val cena = FinancialEvent(
        id = "ev-cena", accountId = banco.id, type = TransactionType.EXPENSE, amount = 180_000L,
        category = "Comida", description = "Cena de cumpleaños", timestamp = 1_790_100_000_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
    )

    private val octubre = DetalleDePeriodo(
        resumen = ResumenDePeriodo(
            id = "2026-10", nombre = "Octubre 2026", desde = "2026-09-24", hasta = "2026-10-20",
            inicioPropio = true, enCurso = true, entradas = 9_000_000L, salidas = 2_500_000L, movimientos = 12,
        ),
        porCategoria = listOf(GastoDeCategoria("Mercado", 600_000L), GastoDeCategoria("Comida", 300_000L)),
        pagosFijos = listOf(
            PagoFijoDelPeriodo("r1", "Arriendo", 1_500_000L, esIngreso = false, vencimiento = "2026-10-05",
                estado = PAGO_FIJO_LISTO, eventId = "ev-arriendo", montoReal = 1_550_000L),
            PagoFijoDelPeriodo("r2", "Celular", 53_000L, esIngreso = false, vencimiento = "2026-10-22",
                estado = PAGO_FIJO_PENDIENTE),
            PagoFijoDelPeriodo("r3", "Gimnasio", 120_000L, esIngreso = false, vencimiento = "2026-10-10",
                estado = PAGO_FIJO_CON_DUDAS),
        ),
        presupuestos = listOf(PresupuestoDelPeriodo("Comida", limite = 250_000L, gastado = 300_000L)),
        masGrandes = listOf(mercado, cena),
        tuPlataAlEmpezar = 5_000_000L,
        tuPlataAlCerrar = 11_500_000L,
    )

    private val septiembre = DetalleDePeriodo(
        resumen = ResumenDePeriodo(
            id = "2026-09", nombre = "Septiembre 2026", desde = "2026-08-25", hasta = "2026-09-23",
            entradas = 0L, salidas = 0L,
        ),
        tuPlataAlEmpezar = 4_000_000L,
        tuPlataAlCerrar = 5_000_000L,
    )

    private val perfil = UserProfile(
        id = "u1", email = "jvillad1@gmail.com", name = "Juan", avatarColor = "#FF0000",
        periodCutoffDay = 25, periodStarts = mapOf("2026-10" to "2026-09-24"),
    )

    private val navegado = mutableListOf<Screen>()

    private open inner class ConDetalle(
        private val porId: Map<String, DetalleDePeriodo>,
        private val perfilQueContesta: UserProfile? = perfil,
    ) : RepositorioDePrueba() {
        val lecturas = mutableListOf<String>()
        val escrituras = mutableListOf<UpdateProfileRequest>()
        override suspend fun getDetalleDePeriodo(id: String): DetalleDePeriodo {
            lecturas += id
            return porId.getValue(id)
        }
        override suspend fun getUserProfile(): UserProfile = perfilQueContesta ?: error("sin red")
        override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
            escrituras += request
            return perfil.copy(periodStarts = request.periodStarts ?: perfil.periodStarts)
        }
        override suspend fun getAccounts(): List<Account> = listOf(banco)
    }

    private fun montar(repo: RepositorioDePrueba, id: String, hoy: LocalDate = this.hoy) {
        navegado.clear()
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    DetalleDePeriodoScreen(onNavigate = { navegado += it }, id = id, hoy = hoy)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun hayTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto, substring = true) }
    }

    @Test
    fun `el periodo en curso pinta todas sus secciones con los datos del server`() {
        montar(ConDetalle(mapOf("2026-10" to octubre)), "2026-10")
        esperarTexto("LOS MÁS GRANDES")

        // Arriba: rango, el arranque propio contra el día de siempre, cifras y Tu plata «hoy».
        assertTrue(hay("Octubre 2026"))
        assertTrue(hay("Del 24 de septiembre al 20 de octubre"))
        assertTrue(hay("Este período empezó el 24 de septiembre (antes del día 25)"))
        assertTrue(hay(formatMoneyCompact(9_000_000L)))
        assertTrue(hay(formatMoneyCompact(2_500_000L)))
        assertTrue(hay("Tu plata: ${formatMoneyCompact(5_000_000L)} al empezar → ${formatMoneyCompact(11_500_000L)} hoy"))

        // En qué se fue.
        assertTrue(hay("EN QUÉ SE FUE"))
        assertTrue(hay("Mercado"))
        assertTrue(hay(formatMoneyCompact(600_000L)))

        // Pagos fijos: con los grupos y los textos de Plan. En el período en curso lo abierto
        // «falta»; el listo con otro monto dice el real, con qué se sabe y lo que decía la regla.
        assertTrue(hay("PAGOS DEL PERÍODO"))
        assertTrue(hay(" · 3"), "el encabezado cuenta los tres pagos fijos")
        assertTrue(hay("Falta por pagar · 2"))
        assertTrue(hay("Ya pagaste · 1"))
        assertTrue(hay(formatCOP(1_550_000L)))
        assertTrue(hay("Con un movimiento de ${formatCOP(1_550_000L)} · la regla dice ${formatCOP(1_500_000L)}"))
        assertTrue(hay("vence el 22 de octubre"), "el Celular todavía no vence (hoy es 20 de octubre)")
        assertTrue(hay("venció hace 10 días"), "el Gimnasio venció el 10")
        assertTrue(hay(TEXTO_CON_DUDAS))
        // El vocabulario viejo no queda en ningún lado.
        assertTrue(!hay("PAGOS FIJOS", substring = true))
        assertTrue(!hay("pendiente", substring = true))

        // Presupuestos, gastado de límite.
        assertTrue(hay("PRESUPUESTOS"))
        assertTrue(hay("${formatCOP(300_000L)} de ${formatCOP(250_000L)}"))

        // Los más grandes.
        assertTrue(hay("Éxito Laureles"))
        assertTrue(hay("Cena de cumpleaños"))
    }

    /**
     * **Un período cerrado dice «No se pagó»**, no «Falta por pagar»: ya no falta, pasó. Y la fecha
     * es el día («venció el 22 de septiembre»), no una cuenta de días desde hoy sobre un período
     * que ya terminó. Mismo vocabulario que la lista de Plan — el caso del dueño: Celular y Cotrafa
     * sin pagar en el período de septiembre, Crediágil pagado.
     */
    @Test
    fun `un periodo cerrado agrupa en ya pagaste y no se pago`() {
        val cerrado = septiembre.copy(
            pagosFijos = listOf(
                PagoFijoDelPeriodo("r1", "Crediágil", 1_204_064L, esIngreso = false, vencimiento = "2026-09-05",
                    estado = PAGO_FIJO_LISTO, eventId = "ev-crediagil", montoReal = 1_204_064L),
                PagoFijoDelPeriodo("r2", "Celular", 53_000L, esIngreso = false, vencimiento = "2026-09-22",
                    estado = PAGO_FIJO_PENDIENTE),
                PagoFijoDelPeriodo("r3", "Cotrafa", 410_000L, esIngreso = false, vencimiento = "2026-09-22",
                    estado = PAGO_FIJO_PENDIENTE),
                // Con dudas: en un período cerrado no se puede afirmar que no se pagó.
                PagoFijoDelPeriodo("r4", "Coomeva", 350_000L, esIngreso = false, vencimiento = "2026-09-20",
                    estado = PAGO_FIJO_CON_DUDAS),
            ),
        )
        montar(ConDetalle(mapOf("2026-09" to cerrado)), "2026-09")
        esperarTexto("PAGOS DEL PERÍODO")

        assertTrue(hay("No se pagó · 2"))
        assertTrue(hay("Ya pagaste · 1"))
        assertTrue(!hay("Falta por pagar", substring = true), "en un período cerrado ya no falta: no se pagó")
        assertTrue(hay("venció el 22 de septiembre"))
        assertTrue(hay("Con un movimiento de ${formatCOP(1_204_064L)}"))
        assertTrue(hay("vencía el 5 de septiembre"))
        assertTrue(hay("$TITULO_SIN_CONFIRMAR · 1"), "lo que tiene dudas no va bajo «No se pagó»")
        assertTrue(hay(TEXTO_CON_DUDAS))
    }

    @Test
    fun `el periodo en curso ofrece empezar uno nuevo hoy`() {
        montar(ConDetalle(mapOf("2026-10" to octubre)), "2026-10")
        esperarTexto("LOS MÁS GRANDES")
        assertTrue(hayTag(TAG_EMPEZAR_PERIODO_HOY))
    }

    @Test
    fun `un periodo pasado no ofrece empezar hoy, y sus vacios ensenan`() {
        montar(ConDetalle(mapOf("2026-09" to septiembre)), "2026-09")
        esperarTexto("Septiembre 2026")
        esperarTexto("PAGOS DEL PERÍODO")

        assertTrue(!hayTag(TAG_EMPEZAR_PERIODO_HOY))
        assertTrue(hayTag(TAG_VER_MOVIMIENTOS_DEL_PERIODO))
        assertTrue(hay("Sin movimientos de flujo"))
        assertTrue(hay("Sin gastos en este período"))
        assertTrue(hay("Sin pagos fijos en este período"))
        assertTrue(hay("Agregar un pago fijo"))
        assertTrue(hay("al cerrar", substring = true))
        // Sin presupuestos ni gastos grandes, esas secciones no aparecen (los rótulos van en versales).
        assertTrue(!hay("PRESUPUESTOS"))
        assertTrue(!hay("LOS MÁS GRANDES"))
    }

    @Test
    fun `confirmar guarda el arranque de noviembre sumado a los existentes y recarga`() {
        // Después de guardar, el server ya dice que octubre cerró.
        val octubreCerrado = octubre.copy(resumen = octubre.resumen.copy(enCurso = false, hasta = "2026-10-19"))
        var guardado = false
        val repo = object : ConDetalle(mapOf("2026-10" to octubre)) {
            override suspend fun getDetalleDePeriodo(id: String): DetalleDePeriodo {
                super.getDetalleDePeriodo(id)
                return if (guardado) octubreCerrado else octubre
            }
            override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
                guardado = true
                return super.updateUserProfile(request)
            }
        }
        montar(repo, "2026-10")
        esperarTexto("LOS MÁS GRANDES")

        composeRule.onNodeWithTag(TAG_EMPEZAR_PERIODO_HOY, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()

        // La confirmación, en la misma pantalla, con el texto exacto.
        assertTrue(
            hay(
                "Octubre termina hoy y Noviembre empieza hoy, 20 de octubre. Úsalo cuando tu plata del mes " +
                    "nuevo ya llegó (por ejemplo, el sueldo se pagó antes).",
            ),
        )
        assertTrue(repo.escrituras.isEmpty(), "abrir la confirmación no guarda nada")

        composeRule.onNodeWithTag(TAG_CONFIRMAR_PERIODO_HOY, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.lecturas.size >= 2 && !hayTag(TAG_EMPEZAR_PERIODO_HOY) }

        assertEquals(
            mapOf("2026-10" to "2026-09-24", "2026-11" to "2026-10-20"),
            repo.escrituras.single().periodStarts,
        )
        // Solo el mapa: nada más del perfil se toca.
        assertEquals(UpdateProfileRequest(periodStarts = repo.escrituras.single().periodStarts), repo.escrituras.single())
        assertEquals(listOf("2026-10", "2026-10"), repo.lecturas, "después de guardar vuelve a leer el detalle")
        assertTrue(!hay("Octubre termina hoy", substring = true), "la confirmación se cierra")
    }

    /**
     * Otro aparato declaró diciembre mientras esta pantalla estaba abierta: la escritura relee el
     * perfil y suma noviembre a lo recién leído, en vez de pisar diciembre con el mapa viejo.
     */
    @Test
    fun `confirmar suma noviembre al perfil recien leido, no al que se leyo al abrir`() {
        var otroAparatoEscribio = false
        val conDiciembre = perfil.copy(periodStarts = perfil.periodStarts + ("2026-12" to "2026-11-24"))
        val repo = object : ConDetalle(mapOf("2026-10" to octubre)) {
            override suspend fun getUserProfile(): UserProfile = if (otroAparatoEscribio) conDiciembre else perfil
        }
        montar(repo, "2026-10")
        esperarTexto("LOS MÁS GRANDES")

        composeRule.onNodeWithTag(TAG_EMPEZAR_PERIODO_HOY, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        otroAparatoEscribio = true
        composeRule.onNodeWithTag(TAG_CONFIRMAR_PERIODO_HOY, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.escrituras.isNotEmpty() }

        assertEquals(
            mapOf("2026-10" to "2026-09-24", "2026-11" to "2026-10-20", "2026-12" to "2026-11-24"),
            repo.escrituras.single().periodStarts,
        )
    }

    @Test
    fun `si guardar falla, la tarjeta dice por que y se puede volver a intentar`() {
        val repo = object : ConDetalle(mapOf("2026-10" to octubre)) {
            override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
                escrituras += request
                error("sin red")
            }
        }
        montar(repo, "2026-10")
        esperarTexto("LOS MÁS GRANDES")

        composeRule.onNodeWithTag(TAG_EMPEZAR_PERIODO_HOY, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CONFIRMAR_PERIODO_HOY, useUnmergedTree = true).performScrollTo().performClick()
        esperarTexto("Algo salió mal. Intenta de nuevo.")

        assertEquals(1, repo.escrituras.size, "solo la escritura que falló")
        assertTrue(hayTag(TAG_CONFIRMAR_PERIODO_HOY), "la confirmación sigue ahí para reintentar")
        assertTrue(hay("Empezar Noviembre hoy"))
        assertEquals(listOf("2026-10"), repo.lecturas, "sin guardar no se recarga")
    }

    /**
     * La pantalla quedó abierta desde anoche: «hoy» se lee al confirmar, así que se guarda el día
     * en que de verdad se confirmó y no el del montaje.
     */
    @Test
    fun `hoy se lee al confirmar, no al montar la pantalla`() {
        var reloj = LocalDate(2026, 10, 20)
        val repo = ConDetalle(mapOf("2026-10" to octubre))
        Repositories.sustitutoDePrueba = repo
        val estado = EstadoDelDetalleDePeriodo(CoroutineScope(Dispatchers.Unconfined), "2026-10") { reloj }
        // Lo que la composición conecta al montar la pantalla, ya leído.
        estado.conectar(LecturasDelDetalle(perfil = Lectura(perfil), detalle = Lectura(octubre), cuentas = Lectura(listOf(banco))))
        estado.abrirConfirmacion()
        assertEquals(LocalDate(2026, 10, 20), estado.hoyDeLaConfirmacion)

        reloj = LocalDate(2026, 10, 21)
        estado.confirmarEmpezarHoy()

        assertEquals("2026-10-21", repo.escrituras.single().periodStarts?.get("2026-11"))
    }

    @Test
    fun `si hoy no es un arranque valido para el siguiente, la accion no aparece`() {
        // El 26 de septiembre ya es octubre, pero noviembre solo puede arrancar en octubre.
        montar(ConDetalle(mapOf("2026-10" to octubre)), "2026-10", hoy = LocalDate(2026, 9, 26))
        esperarTexto("LOS MÁS GRANDES")
        assertTrue(!hayTag(TAG_EMPEZAR_PERIODO_HOY))
    }

    @Test
    fun `sin el perfil no se sabe si hoy vale, y la accion no aparece`() {
        montar(ConDetalle(mapOf("2026-10" to octubre), perfilQueContesta = null), "2026-10")
        esperarTexto("LOS MÁS GRANDES")
        assertTrue(!hayTag(TAG_EMPEZAR_PERIODO_HOY))
        // Sin el corte, la frase del arranque dice solo la fecha.
        assertTrue(hay("Este período empezó el 24 de septiembre"))
    }

    @Test
    fun `ver los movimientos abre Movimientos en ese periodo`() {
        montar(ConDetalle(mapOf("2026-09" to septiembre)), "2026-09")
        esperarTexto("Septiembre 2026")

        composeRule.onNodeWithTag(TAG_VER_MOVIMIENTOS_DEL_PERIODO, useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(Screen.Transactions(periodoInicial = "2026-09"), navegado.single())
    }

    @Test
    fun `tocar un gasto grande abre el movimiento como en Movimientos`() {
        montar(ConDetalle(mapOf("2026-10" to octubre)), "2026-10")
        esperarTexto("Éxito Laureles")

        composeRule.onAllNodesWithTag(TAG_GASTO_GRANDE, useUnmergedTree = true).onFirst().performScrollTo().performClick()
        esperarTexto("MONTO, CUENTA Y CONCEPTO")
    }

    @Test
    fun `una lectura que falla dice que no se pudo, y Reintentar la trae`() {
        var falla = true
        montar(object : ConDetalle(mapOf("2026-10" to octubre)) {
            override suspend fun getDetalleDePeriodo(id: String): DetalleDePeriodo {
                if (falla) error("sin red")
                return super.getDetalleDePeriodo(id)
            }
        }, "2026-10")
        esperarTexto("No pudimos cargar este período")
        // El título no depende de la lectura: sale del id.
        assertTrue(hay("Octubre 2026"))

        falla = false
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText("Reintentar")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        esperarTexto("LOS MÁS GRANDES")
        assertTrue(!hay("No pudimos cargar este período"))
    }

    // ── De dónde salió lo que faltó ──────────────────────────────────────────

    /**
     * El septiembre del dueño: entró $22,5M (con $10M del crédito Techo Gardenera), salió $34M. Lo que
     * faltó lo pagaron los saldos que Movi conoció a mitad del período.
     */
    private val septiembreQueFalto = DetalleDePeriodo(
        resumen = ResumenDePeriodo(
            id = "2026-09", nombre = "Septiembre 2026", desde = "2026-08-25", hasta = "2026-09-23",
            entradas = 22_500_000L, salidas = 34_000_000L, movimientos = 40, creditosRecibidos = 10_000_000L,
        ),
        fuentesQueNoSonIngreso = listOf(
            FuenteDePlata(FUENTE_SALDO_INICIAL, 22_200_211L, listOf("Nu", "AFC Davibank", "Bancolombia Ahorros 0031")),
        ),
    )

    @Test
    fun `si salio mas de lo que entro, la tarjeta dice de donde salio lo que falto`() {
        montar(ConDetalle(mapOf("2026-09" to septiembreQueFalto)), "2026-09")
        esperarTexto("PAGOS DEL PERÍODO")

        assertTrue(hayTag(TAG_DE_DONDE_SALIO_LO_QUE_FALTO))
        assertTrue(hay("De dónde salió lo que faltó"))
        assertTrue(!hay("Créditos que te desembolsaron", substring = true))
        // El faltante es $11,5M (entró $22,5M, salió $34M): los $22,2M de saldos no se dicen enteros.
        assertTrue(hay("Saldos que ya tenías y cargaste en el período · \$11,5M — Nu, AFC Davibank, Bancolombia Ahorros 0031"))
        assertTrue(!hay("22,2", substring = true))
        assertTrue(
            hay(
                "Esta plata ya estaba en tus cuentas cuando Movi la conoció, así que no cuenta como ingreso.",
            ),
        )
    }

    /** Agosto: el hueco es $529.368 y los saldos $471.600, menos que el hueco: se dicen enteros. */
    @Test
    fun `si los saldos son menos que lo que falto se dicen enteros`() {
        val agosto = DetalleDePeriodo(
            resumen = septiembreQueFalto.resumen.copy(entradas = 10_000_000L, salidas = 10_529_368L, creditosRecibidos = 0),
            fuentesQueNoSonIngreso = listOf(FuenteDePlata(FUENTE_SALDO_INICIAL, 471_600L, listOf("Nu"))),
        )
        assertEquals(listOf("Saldos que ya tenías y cargaste en el período · \$471.600 — Nu"), filasDeLoQueFalto(agosto))
    }

    /** Septiembre completo: entró $32,5M, salió $34M, saldos $22,2M: la fila dice $1,5M. */
    @Test
    fun `con saldos mucho mayores que el faltante la fila dice el faltante`() {
        val septiembre = septiembreQueFalto.copy(resumen = septiembreQueFalto.resumen.copy(entradas = 32_500_000L))
        assertEquals(
            listOf("Saldos que ya tenías y cargaste en el período · \$1,5M — Nu, AFC Davibank, Bancolombia Ahorros 0031"),
            filasDeLoQueFalto(septiembre),
        )
    }

    @Test
    fun `si entro lo mismo o mas de lo que salio, la tarjeta no aparece aunque haya fuentes`() {
        val alDia = septiembreQueFalto.copy(resumen = septiembreQueFalto.resumen.copy(entradas = 34_000_000L))
        montar(ConDetalle(mapOf("2026-09" to alDia)), "2026-09")
        esperarTexto("PAGOS DEL PERÍODO")

        assertTrue(!hayTag(TAG_DE_DONDE_SALIO_LO_QUE_FALTO))
        assertTrue(!hay("De dónde salió lo que faltó"))
        assertTrue(!hay("Saldos que ya tenías", substring = true))
    }

    /** Bajo el encabezado se dice cuánto de lo que entró es crédito; con o sin fuentes. */
    @Test
    fun `el encabezado dice cuanto de lo que entro son creditos desembolsados`() {
        montar(ConDetalle(mapOf("2026-09" to septiembreQueFalto)), "2026-09")
        esperarTexto("PAGOS DEL PERÍODO")
        assertTrue(hayTag(TAG_INCLUYE_CREDITOS))
        assertTrue(hay("Incluye \$10M de créditos desembolsados."))
    }

    @Test
    fun `sin creditos el encabezado no dice nada de creditos`() {
        val sinCreditos = septiembreQueFalto.copy(resumen = septiembreQueFalto.resumen.copy(creditosRecibidos = 0))
        montar(ConDetalle(mapOf("2026-09" to sinCreditos)), "2026-09")
        esperarTexto("PAGOS DEL PERÍODO")
        assertTrue(!hayTag(TAG_INCLUYE_CREDITOS))
    }

    @Test
    fun `sin fuentes la tarjeta no aparece`() {
        montar(ConDetalle(mapOf("2026-09" to septiembreQueFalto.copy(fuentesQueNoSonIngreso = emptyList()))), "2026-09")
        esperarTexto("PAGOS DEL PERÍODO")

        assertTrue(!hayTag(TAG_DE_DONDE_SALIO_LO_QUE_FALTO))
        assertTrue(!hay("De dónde salió lo que faltó"))
    }

    /** Un tipo que esta versión no conoce no se dice, y sin nada que decir no hay tarjeta. */
    @Test
    fun `con solo fuentes que esta version no conoce la tarjeta no aparece`() {
        val raras = listOf(FuenteDePlata("HERENCIA", 5_000_000L, listOf("La tía")))
        montar(ConDetalle(mapOf("2026-09" to septiembreQueFalto.copy(fuentesQueNoSonIngreso = raras))), "2026-09")
        esperarTexto("PAGOS DEL PERÍODO")

        assertTrue(!hayTag(TAG_DE_DONDE_SALIO_LO_QUE_FALTO))
        assertTrue(!hay("La tía", substring = true))
    }

    @Test
    fun `cada fuente dice cuanto y de donde, y una sin nombres no deja un guion colgando`() {
        assertEquals(
            // El faltante es $11,5M: la fila dice eso y las cuentas se listan enteras.
            listOf(
                "Saldos que ya tenías y cargaste en el período · \$11,5M — Nu, AFC Davibank, Bancolombia Ahorros 0031",
            ),
            filasDeLoQueFalto(septiembreQueFalto),
        )
        assertEquals(
            listOf("Saldos que ya tenías y cargaste en el período · \$500.000"),
            filasDeLoQueFalto(septiembreQueFalto.copy(fuentesQueNoSonIngreso = listOf(FuenteDePlata(FUENTE_SALDO_INICIAL, 500_000L)))),
        )
        // Un monto en cero no se dice: «$0» no explica nada.
        assertEquals(
            emptyList(),
            filasDeLoQueFalto(septiembreQueFalto.copy(fuentesQueNoSonIngreso = listOf(FuenteDePlata(FUENTE_SALDO_INICIAL, 0L)))),
        )
        // Lo que mandaba un server anterior (un crédito como fuente) ya no se dice.
        assertEquals(
            emptyList(),
            filasDeLoQueFalto(septiembreQueFalto.copy(fuentesQueNoSonIngreso = listOf(FuenteDePlata(FUENTE_CREDITO, 500_000L)))),
        )
    }

    @Test
    fun `mientras no llega muestra el esqueleto y ninguna cifra`() {
        val llega = CompletableDeferred<DetalleDePeriodo>()
        montar(object : ConDetalle(mapOf("2026-10" to octubre)) {
            override suspend fun getDetalleDePeriodo(id: String): DetalleDePeriodo = llega.await()
        }, "2026-10")

        assertTrue(hayTag(TAG_DETALLE_DE_PERIODO_ESQUELETO))
        assertTrue(!hay("$", substring = true))
        assertTrue(!hay("Sin gastos en este período"))

        llega.complete(octubre)
        esperarTexto("LOS MÁS GRANDES")
        assertTrue(!hayTag(TAG_DETALLE_DE_PERIODO_ESQUELETO))
    }
}
