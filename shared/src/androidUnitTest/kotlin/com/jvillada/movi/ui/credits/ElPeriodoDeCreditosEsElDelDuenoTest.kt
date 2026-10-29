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
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuotaSinPagar
import com.jvillada.movi.shared.model.mesDeLaUltimaCuota
import com.jvillada.movi.ui.fecha.hoyEnAppZone
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

/**
 * # La última cuota se cuenta desde la primera cuota sin pagar (vuelta 59)
 *
 * Esta prueba nació para que Créditos contara los meses desde el **período del dueño** y no desde el
 * mes de calendario (con corte 25, el 26 de septiembre el resto de la app ya está en octubre). La
 * vuelta 59 encontró que las dos respuestas eran la misma pregunta mal hecha: «próxima 5 de
 * noviembre · te faltan 49 cuotas · la última en octubre de 2030» era noviembre, porque se contaba el
 * mes en curso como la cuota 1 aunque la de octubre ya estuviera pagada.
 *
 * Las cuotas que faltan empiezan en **la primera sin pagar**, y una cuota vence un día del calendario:
 * su mes no depende del corte. La prueba sigue montando un corte que corre el mes ([ajustesQueCorrenElMes])
 * para que se vea que la fecha ya no se mueve con él.
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
    private val hoy = hoyEnAppZone()
    private val ajustesDelDueno = ajustesQueCorrenElMes(ahora)
    private val mesesQueFaltan = planDelCredito(libreInversion)!!.mesesHastaLaUltimaCuota!!

    /** El mes que la pantalla TIENE que decir sin saber qué se pagó: desde el próximo vencimiento. */
    private val mesCorrecto = nombreDe(mesDeLaUltimaCuota(mesDeLaPrimeraCuotaSinPagar(15, hoy, pagadas = null), mesesQueFaltan))

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(cutoffDay: Int, iniciosPropios: Map<String, String>, credito: CreditSummary = libreInversion) {
        val perfil = UserProfile(
            id = "u1",
            email = "jvillad1@gmail.com",
            name = "Juan",
            avatarColor = "#FF0000",
            periodCutoffDay = cutoffDay,
            periodStarts = iniciosPropios,
        )
        composeRule.montarElDetalleDe(credito.account, RepositorioDeLaDeuda(creditos = listOf(credito), perfil = perfil))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("la última en", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `la fecha de la ultima cuota no se corre con el corte del dueno`() {
        montar(ajustesDelDueno.cutoffDay, ajustesDelDueno.iniciosPropios)

        composeRule.onNodeWithText("la última en $mesCorrecto", substring = true, useUnmergedTree = true)
            .assertExists("se cuenta desde la primera cuota sin pagar, no desde el período en curso")
    }

    @Test
    fun `con corte 1 dice lo mismo`() {
        montar(cutoffDay = 1, iniciosPropios = emptyMap())

        composeRule.onNodeWithText("la última en $mesCorrecto", substring = true, useUnmergedTree = true)
            .assertExists("el corte no cambia el mes en que vence una cuota")
    }

    /** Con la cuota de este mes sin pagar, la primera de las que faltan es la de este mes. */
    @Test
    fun `con la cuota del mes sin pagar se cuenta desde este mes`() {
        val sinPagar = libreInversion.copy(cuotasPagadas = emptyList())
        val esperado = nombreDe(mesDeLaUltimaCuota(mesDeLaPrimeraCuotaSinPagar(15, hoy, pagadas = emptyList()), mesesQueFaltan))
        montar(cutoffDay = 1, iniciosPropios = emptyMap(), credito = sinPagar)

        composeRule.onNodeWithText("la última en $esperado", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(PeriodoFinanciero(hoy.year, hoy.monthNumber), mesDeLaPrimeraCuotaSinPagar(15, hoy, pagadas = emptyList()))
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
