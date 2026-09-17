package com.jvillada.movi.ui.credits

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.mas
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.planDelCredito
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * # Créditos cuenta los meses desde el período del dueño, no desde el mes de calendario
 *
 * La fecha de la última cuota se dice contando meses **desde el mes en curso**, y esta pantalla
 * resolvía «el mes en curso» con `PeriodSettings()` a secas — corte 1 — ignorando el
 * `periodCutoffDay` que Movimientos, el Inicio y Presupuestos sí cargan del perfil.
 *
 * Con corte 25, el 26 de septiembre el resto de la app ya está en octubre y Créditos seguía en
 * septiembre: la misma deuda se anunciaba «la última en octubre de 2029» donde habría que leer
 * noviembre. Seis días de cada mes, en la pantalla cuyo trabajo es decir cuándo se termina de
 * pagar — y al lado de fechas que corren con el otro calendario.
 *
 * **La prueba se monta para que los dos calendarios discrepen el día que corra**, porque si no
 * coincidirían ~24 días de cada 30 y estaría verde sin probar nada: ver [ajustesQueCorrenElMes].
 * Y el mes esperado sale de las mismas funciones que usa la pantalla, no de una constante: fijar
 * «noviembre de 2029» volvería falsa la prueba el mes que viene.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD_DEL_PERIODO)
class ElPeriodoDeCreditosEsElDelDuenoTest {

    @get:Rule val composeRule = createComposeRule()

    /** Libre inversión ·9695: amortiza, así que tiene fecha de última cuota. */
    private val libreInversion = CreditSummary(
        account = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
            insuranceMonthly = 124_800L,
        ),
        paidPct = 0.5,
    )

    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val ajustesDelDueno = ajustesQueCorrenElMes(ahora)
    private val mesesQueFaltan = planDelCredito(libreInversion)!!.mesesHastaLaUltimaCuota!!

    /** El mes que la pantalla TIENE que decir: contado desde el período del dueño. */
    private val mesCorrecto = nombreDe(periodoDe(ahora, ajustesDelDueno).mas(mesesQueFaltan - 1))

    /** Y el que decía antes: contado desde el mes de calendario. */
    private val mesDelCalendario = nombreDe(periodoDe(ahora, PeriodSettings()).mas(mesesQueFaltan - 1))

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(cutoffDay: Int, iniciosPropios: Map<String, String>) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = listOf(libreInversion)
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getUserProfile(): UserProfile = UserProfile(
                id = "u1",
                email = "jvillad1@gmail.com",
                name = "Juan",
                avatarColor = "#FF0000",
                periodCutoffDay = cutoffDay,
                periodStarts = iniciosPropios,
            )
        }
        composeRule.setContent { MoviTheme { CreditosScreen(onNavigate = {}) } }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("la última en", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `la fecha de la ultima cuota se cuenta desde el periodo del dueno`() {
        // La prueba no serviría si los dos calendarios nombraran el mismo mes hoy.
        assertNotEquals(
            mesDelCalendario,
            mesCorrecto,
            "los ajustes de la prueba tienen que correr el mes: si no, esto pasa sin probar nada",
        )

        montar(ajustesDelDueno.cutoffDay, ajustesDelDueno.iniciosPropios)

        composeRule.onNodeWithText("la última en $mesCorrecto", substring = true, useUnmergedTree = true)
            .assertExists("Créditos tiene que contar desde el período del dueño, no desde el mes de calendario")
        assertEquals(
            0,
            composeRule.onAllNodesWithText("la última en $mesDelCalendario", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
            "el mes de calendario es justamente el que estaba mal",
        )
    }

    /**
     * El contracaso: un dueño con corte 1 —el que no tocó nada— sigue viendo el mes de calendario.
     * Sin esto, «leer el perfil» podría haber sido «correr todas las fechas un mes».
     */
    @Test
    fun `con corte 1 nada cambia`() {
        montar(cutoffDay = 1, iniciosPropios = emptyMap())

        composeRule.onNodeWithText("la última en $mesDelCalendario", substring = true, useUnmergedTree = true)
            .assertExists("con el mes de calendario, la fecha es la de siempre")
    }
}

/**
 * Unos ajustes de período con los que **hoy** no cae en el mes de calendario, corra el día que
 * corra.
 *
 * Con un corte `d > 1`, un día `>= d` ya pertenece al período del mes SIGUIENTE, así que tomar el
 * día de hoy como corte alcanza… salvo el día 1, donde ningún corte sirve: el 1 siempre cae antes
 * del corte y el período se llama como el mes. Para ese día se usa la otra mitad de
 * [PeriodSettings] —un inicio propio— que adelanta el arranque del período siguiente al día 1.
 */
private fun ajustesQueCorrenElMes(ahora: Long): PeriodSettings {
    val delCalendario = periodoDe(ahora, PeriodSettings())
    for (corte in 2..28) {
        val candidato = PeriodSettings(cutoffDay = corte)
        if (periodoDe(ahora, candidato) != delCalendario) return candidato
    }
    val siguiente: PeriodoFinanciero = periodoSiguiente(delCalendario)
    return PeriodSettings(
        cutoffDay = 2,
        iniciosPropios = mapOf(siguiente.prefijo to "${delCalendario.prefijo}-01"),
    )
}

/** Mismo tamaño que el AVD con el que se mira la app a ojo. */
private const val TELEFONO_DEL_AVD_DEL_PERIODO = "w411dp-h731dp-xhdpi"
