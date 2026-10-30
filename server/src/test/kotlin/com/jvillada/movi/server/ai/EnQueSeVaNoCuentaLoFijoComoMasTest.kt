package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.PreguntaSugerida
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 77 · «¿En qué se va?» no dice «$980.000 más» de una cuota
 *
 * La revisión fresca 6: la sugerida sin IA decía «Cuota de crédito $980.000 · $980.000 más que el período
 * pasado» —la cuota de este período cayó antes que la del pasado—, donde el widget de Inicio no dice nada
 * (`categoriasFijas`, vuelta 76). Ahora la sugerida usa la misma regla: una cuota o un fijo sin gasto a
 * esta altura del período pasado no se compara. Lo variable sigue diciéndolo.
 */
class EnQueSeVaNoCuentaLoFijoComoMasTest {

    private val datos = DatosDeLasSugeridas(
        rango = "1 al 31 de octubre",
        entro = 5_000_000,
        gastoPorCategoria = mapOf(CUOTA_CATEGORY to 980_000L, "Gimnasio" to 120_000L, "Ropa" to 300_000L, "Mercado" to 400_000L),
        anterior = mapOf("Mercado" to 380_000L),
        diasComparados = 28,
        fijas = setOf(CUOTA_CATEGORY, "Gimnasio"),
    )

    @Test
    fun `una cuota o un fijo sin gasto a esta altura no es mas que el periodo pasado`() {
        val texto = enQueSeVa(datos)
        assertTrue(texto.contains("$CUOTA_CATEGORY \$980.000\n") || texto.lines().any { it.endsWith("$CUOTA_CATEGORY \$980.000") }, texto)
        assertFalse(texto.contains("\$980.000 más que el período pasado"), texto)
        assertFalse(texto.contains("Gimnasio \$120.000 ·"), texto)
        // Lo variable sigue comparando, igual que antes.
        assertTrue(texto.contains("Ropa \$300.000 · \$300.000 más que el período pasado"), texto)
        assertTrue(texto.contains("Mercado \$400.000 · \$20.000 más que el período pasado"), texto)
    }

    @Test
    fun `sin las fijas dice lo de antes`() {
        val texto = enQueSeVa(datos.copy(fijas = emptySet()))
        assertTrue(texto.contains("$CUOTA_CATEGORY \$980.000 · \$980.000 más que el período pasado"), texto)
    }

    /** Las fijas salen de las reglas de Inicio: la cuota de un crédito (sintética) y la categoría de la cuota. */
    @Test
    fun `la sugerida lee lo fijo de las reglas del duenio`() {
        val a = AndamioDelAsistente("en_que_se_va_fijas")
        transaction {
            Accounts.insert { it[id] = "acc_veh"; it[userId] = a.duenoA; it[name] = "Vehículo 4083"; it[type] = "LOAN"; it[currency] = "COP" }
            Credits.insert {
                it[accountId] = "acc_veh"; it[userId] = a.duenoA; it[bank] = "Occidente"; it[principal] = 40_000_000L
                it[rateEa] = 18.0; it[termMonths] = 60; it[installment] = 980_000L; it[dayOfMonth] = 5
                it[startDate] = "2026-01-05"; it[remindMe] = true
            }
        }
        val d = runBlocking { datosDeLasSugeridas(a.duenoA, PreguntaSugerida.EN_QUE_SE_VA, LocalDate.of(2026, 10, 28)) }
        assertTrue(CUOTA_CATEGORY in d.fijas, d.fijas.toString())
    }
}
