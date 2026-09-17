package com.jvillada.movi.ui.documentos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.theme.MoviTheme
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # De qué cuenta es este papel
 *
 * `Documento.accountId` existía desde el primer día y **no lo llenaba nadie**: la subida no lo
 * mandaba, el archivador de extractos tampoco, y la hoja de corrección ni siquiera lo mostraba. El
 * contexto del asistente agrupa los documentos por cuenta, así que todos caían bajo «Sin cuenta
 * asociada» y preguntarle «¿qué tienes guardado de la cuenta 2334?» devolvía la lista entera.
 *
 * Estas pruebas fijan las dos puertas por las que la cuenta entra: la hoja de **subida**, que no
 * existía, y la de **edición**, que existía sin el campo.
 *
 * Lo que NO cubre: es Robolectric, así que no dice nada de iOS ni de la web.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class HojasDeDocumentoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val master = Account("c1", "Mastercard 3684", AccountType.CREDIT_CARD, 1_240_000)
    private val efectivo = Account("e1", "Efectivo", AccountType.CASH, 200_000)
    private val hipotecario = Account("l1", "Hipotecario 7712", AccountType.LOAN, 177_200_000)
    private val todas = listOf(ahorros, master, efectivo, hipotecario)

    private var subido: Pair<TipoDeDocumento, String?>? = null

    private fun montarSubida(cuentas: List<Account> = todas, nombre: String = "Extracto_2334_08_2026.pdf") {
        subido = null
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SubirDocumentoSheet(
                        archivo = ArchivoElegido(nombre, ByteArray(2048), "application/pdf"),
                        cuentas = cuentas,
                        onDismiss = {},
                        onSubir = { tipo, cuenta -> subido = tipo to cuenta },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun la_hoja_de_subida_manda_la_cuenta_que_se_eligio() {
        montarSubida()

        composeRule.onNodeWithText("Bancolombia Ahorros").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Subir").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(TipoDeDocumento.EXTRACTO to "a1", subido)
    }

    /**
     * El paso nuevo se cobra barato: todo llega elegido, así que quien no quiera decidir nada toca
     * «Subir» y el papel entra igual que antes — con el tipo adivinado por el nombre y sin cuenta.
     */
    @Test
    fun sin_tocar_nada_sube_con_el_tipo_adivinado_y_sin_cuenta() {
        montarSubida(nombre = "Desprendible_agosto.pdf")

        composeRule.onNodeWithText("Subir").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(TipoDeDocumento.NOMINA, subido?.first)
        assertNull(subido?.second, "«Sin cuenta» es la opción de entrada")
    }

    /**
     * **Del efectivo no hay papeles que guardar**, pero del hipotecario sí: la tabla de
     * amortización y el pagaré son justamente papeles de un crédito ya desembolsado. Por eso el
     * uso de cuenta de un documento no es el del extracto, que deja los préstamos abajo.
     */
    @Test
    fun el_credito_se_ofrece_para_un_papel_y_el_efectivo_queda_detras_de_ver_todas() {
        montarSubida()

        composeRule.onNodeWithText("Hipotecario 7712").assertIsDisplayed()
        composeRule.onNodeWithText("Efectivo").assertDoesNotExist()

        // No es un filtro duro: se abre y se elige.
        composeRule.onNodeWithText("Ver todas las cuentas (1 más)").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Efectivo").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Subir").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("e1", subido?.second)
    }

    @Test
    fun la_hoja_de_edicion_llega_con_la_cuenta_del_documento_marcada() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    EditarDocumentoSheet(
                        doc = Documento(
                            id = "doc1",
                            nombre = "TC_Master_3684_09_2026.pdf",
                            tipo = TipoDeDocumento.EXTRACTO,
                            mimeType = "application/pdf",
                            bytes = 2048,
                            subidoEn = 1_700_000_000_000,
                            accountId = "c1",
                        ),
                        cuentas = todas,
                        onDismiss = {},
                        onGuardado = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // La cuenta puesta se ve —no hay forma de corregir lo que no se muestra— y «Sin cuenta»
        // está ahí para descolgarla, que era imposible hasta ahora.
        composeRule.onNodeWithText("Mastercard 3684").assertIsDisplayed()
        composeRule.onNodeWithText("Sin cuenta").assertIsDisplayed()
    }
}
