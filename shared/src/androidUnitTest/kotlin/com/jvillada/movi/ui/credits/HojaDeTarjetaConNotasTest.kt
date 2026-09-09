package com.jvillada.movi.ui.credits

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * # La nota de una tarjeta sobrevive a que se guarde la hoja
 *
 * Gemela de [HojaDeCondicionesOtrosCargosTest], por el mismo agujero y en la hoja de al lado:
 * `CardTermsSheet.save()` arma el `CardTerms` **campo por campo a mano**, así que un campo que la
 * hoja no nombra viaja en su default. Con `notes` eso no era «no se puede editar»: era **se
 * borra**. `fillCardTerms` escribe todas las columnas, así que cada «Guardar tarjeta» mandaba
 * `notes = null` y dejaba la nota de la tarjeta en NULL, sin campo donde volver a escribirla y sin
 * nada en pantalla que dijera que pasó.
 *
 * Se afirma el **viaje redondo**, no un tecleo: se abre la hoja con la nota ya guardada, se toca
 * «Guardar tarjeta» sin escribir nada, y se mira qué `CardTerms` llegó al repositorio. Bajo
 * Robolectric el click no llega solo; se usa la acción semántica, igual que el resto de las
 * pruebas de hoja de este repo.
 *
 * El caso es el real: la Master Black, cuya nota es la que explica por qué difiere a 36 cuotas
 * toda compra internacional — dato que no está en ninguna otra parte de Movi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TELEFONO_DEL_AVD_DE_TARJETAS)
class HojaDeTarjetaConNotasTest {

    @get:Rule val composeRule = createComposeRule()

    private val masterBlack = Account("acc_master_black", "Master Black", AccountType.CREDIT_CARD, 24_500_000)

    private val condicionesDeLaMasterBlack = CardTerms(
        accountId = masterBlack.id,
        bank = "Bancolombia",
        creditLimit = 40_000_000L,
        cutoffDay = 10,
        paymentDay = 25,
        pagoMinimo = 1_843_014L,
        notes = "Difiere a 36 cuotas toda compra internacional",
    )

    /** Lo que la hoja le mandó al repositorio al guardar. */
    private var guardado: CardTerms? = null

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montarLaHoja(terms: CardTerms = condicionesDeLaMasterBlack) {
        guardado = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(masterBlack)
            override suspend fun putCardTerms(terms: CardTerms): CardSummary {
                guardado = terms
                return CardSummary(account = masterBlack, terms = terms)
            }
        }
        composeRule.setContent {
            MoviTheme {
                CardTermsSheet(
                    editing = CardSummary(account = masterBlack, terms = terms),
                    onDismiss = {},
                    onSaved = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `la hoja muestra la nota guardada`() {
        montarLaHoja()

        composeRule.onNodeWithText("36 cuotas", substring = true).assertExists()
    }

    @Test
    fun `guardar sin tocar nada devuelve la nota, no un null`() {
        montarLaHoja()

        composeRule.onNodeWithText("Guardar tarjeta").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { guardado != null }

        val terms = assertNotNull(guardado, "la hoja tiene que haber guardado algo")
        assertEquals(
            "Difiere a 36 cuotas toda compra internacional",
            terms.notes,
            "cada guardado desde esta hoja le borraba la nota a la tarjeta",
        )
    }

    @Test
    fun `una tarjeta sin nota igual ofrece el campo`() {
        // El caso normal tiene que poder escribir la primera: si el campo solo apareciera cuando
        // ya hay una nota, no habría por dónde empezar. Regla del proyecto — nada se configura
        // tocando código.
        montarLaHoja(condicionesDeLaMasterBlack.copy(notes = null))

        composeRule.onNodeWithText("Notas (opcional)", substring = true).assertExists()
    }
}

/** Mismo tamaño que el AVD con el que se mira la app a ojo. */
private const val TELEFONO_DEL_AVD_DE_TARJETAS = "w411dp-h731dp-xhdpi"
