package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * # La fecha de un gasto del espacio se lee entera (vuelta 31), en un renglón (vuelta 32)
 *
 * En producción, a 360 dp, el subtítulo «Pagó Caro · Mercado · 21 …» cortaba justo la fecha, que es
 * lo que distingue dos mercados del mismo monto. La vuelta 31 puso la fecha primero y dejó bajar a un
 * segundo renglón, donde «Mercado» quedaba solo. Ahora el subtítulo es un renglón: «Tú» y «Caro» en vez
 * de «Pagaste tú» y «Pagó Caro», sin la categoría que el título ya dice, y si aun así no cabe se corta
 * la categoría, al final; la fecha y quién pagó se leen enteros. Con el motor de texto de verdad
 * (`sdk = [34]`, `NATIVE`), como `LaListaDeCreditosEsDeFilasTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class LosGastosDelEspacioSeLeenTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Carolina")),
    )

    private val dia21 = LocalDateTime(2026, 10, 21, 12, 0).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private fun gasto(id: String, desc: String, categoria: String, pagadoPor: String, monto: Long) = GastoDelEspacio(
        id = id, espacioId = casa.id, pagadoPor = pagadoPor, monto = monto, categoria = categoria,
        descripcion = desc, fecha = dia21, partes = partesDelGasto(monto, casa, pagadoPor),
    )

    private val gastos = listOf(
        gasto("g1", "Mercado del sábado en el Éxito", "Mercado", "dst_caro", 1_320_000L),
        gasto("g2", "Arriendo del apartamento", "Vivienda y servicios", MIEMBRO_YO, 12_100_000L),
    )

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEspacios(): List<Espacio> = listOf(casa)
            override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio = DetalleDelEspacio(
                espacio = casa,
                saldo = saldoDelEspacio(casa, gastos, emptyList()),
                delPeriodo = saldoDelEspacio(casa, gastos, emptyList()),
                gastos = gastos,
            )
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { EspaciosScreen(onNavigate = {}) } } }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Arriendo del apartamento", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textosDentro(nodo: SemanticsNode): List<SemanticsNode> =
        listOf(nodo).filter { texto(it).isNotEmpty() } + nodo.children.flatMap(::textosDentro)

    /**
     * [prefijo] se dibuja entero en el único renglón de [nodo]: el subtítulo es de un renglón, y si se
     * corta con «…» (siempre al final) el corte cae después de [prefijo], con lugar para el «…».
     */
    private fun unRenglonConElPrefijoEntero(nodo: SemanticsNode, prefijo: String): Boolean {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val d = resultados.single()
        val ancho = nodo.size.width + 0.5f
        if (d.lineCount != 1) return false
        val finDelPrefijo = d.getHorizontalPosition(prefijo.length, usePrimaryDirection = true)
        // El «…» mide menos que dos letras de la misma letra.
        val lugarParaElCorte = if (d.isLineEllipsized(0)) 2 * d.getBoundingBox(prefijo.length - 1).width else 0f
        return finDelPrefijo + lugarParaElCorte <= ancho
    }

    @Test
    fun `a 360 dp el subtitulo es un renglon con la fecha y quien pago enteros`() {
        montar()
        val subtitulos = composeRule.onAllNodesWithTag(TAG_GASTO_DEL_ESPACIO, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap(::textosDentro)
            .filter { texto(it).contains(" · ") }
        assertTrue(subtitulos.size == 2, "un subtítulo por gasto: ${subtitulos.map(::texto)}")
        val esperados = mapOf("21 oct · Carolina" to "21 oct · Carolina", "21 oct · Tú · Vivienda y servicios" to "21 oct · Tú")
        subtitulos.forEach { nodo ->
            val prefijo = esperados[texto(nodo)]
            assertTrue(prefijo != null, "subtítulo inesperado: «${texto(nodo)}»")
            assertTrue(unRenglonConElPrefijoEntero(nodo, prefijo), "«${texto(nodo)}»: la fecha y quién pagó no caben en un renglón")
        }
    }
}
