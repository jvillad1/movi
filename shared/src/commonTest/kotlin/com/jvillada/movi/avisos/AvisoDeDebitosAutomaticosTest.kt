package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.OrigenDelDebito
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El aviso del día del débito** (hueco de #440): qué se avisa —cada débito una sola vez por
 * período— y qué dice. El Worker de `:androidApp` solo lee, llama a esto y publica.
 */
class AvisoDeDebitosAutomaticosTest {

    private val hoy = LocalDate(2026, 10, 5)

    private fun debito(ruleId: String, periodo: String, vence: String = "2026-10-05", origen: OrigenDelDebito = OrigenDelDebito.CUOTA_DE_CREDITO, nombre: String = "Cuota Libre inversión 9695", monto: Long = 1_204_064L) =
        DebitoAutomaticoPorConfirmar(
            ruleId = ruleId, periodo = periodo, origen = origen, nombre = nombre, monto = monto,
            vence = vence, cuentaId = "acc-ahorros", cuentaNombre = "Bancolombia Ahorros", categoria = "Cuota de crédito",
            pataDelDineroId = "ev_deb_$ruleId@$periodo",
        )

    private val libre = debito("credit_9695", "2026-10")

    @Test
    fun un_debito_nuevo_se_avisa_con_su_cuota_y_su_monto() {
        val nuevos = debitosParaAvisar(listOf(libre), yaAvisados = emptyList())
        assertEquals(listOf(libre), nuevos)
        val texto = textoDeDebitosAutomaticos(nuevos, hoy)!!
        assertEquals("Débito automático por confirmar", texto.titulo)
        assertEquals("El banco debió cobrar hoy la cuota de Libre inversión 9695 (\$1.204.064). ¿Se cobró?", texto.texto)
    }

    @Test
    fun el_mismo_debito_no_suena_dos_veces_en_el_periodo() {
        val avisados = recordarDebitosAvisados(emptyList(), listOf(libre))
        // Al día siguiente la propuesta sigue sin contestar: no vuelve a sonar.
        assertTrue(debitosParaAvisar(listOf(libre), avisados).isEmpty())
        assertNull(textoDeDebitosAutomaticos(debitosParaAvisar(listOf(libre), avisados), hoy))
    }

    @Test
    fun el_periodo_siguiente_es_otro_debito_y_si_suena() {
        val avisados = recordarDebitosAvisados(emptyList(), listOf(libre))
        val noviembre = debito("credit_9695", "2026-11", vence = "2026-11-05")
        assertEquals(listOf(noviembre), debitosParaAvisar(listOf(noviembre), avisados))
    }

    @Test
    fun entre_varios_solo_los_nuevos_y_un_renglon_por_cada_uno() {
        val avisados = recordarDebitosAvisados(emptyList(), listOf(libre))
        val seguro = debito("rule-seguro", "2026-10", origen = OrigenDelDebito.RECURRENTE, nombre = "Seguro del carro", monto = 210_000L)
        val crediagil = debito("credit_3090", "2026-10", nombre = "Cuota Crediágil 3090", monto = 26_485L)
        val nuevos = debitosParaAvisar(listOf(libre, seguro, crediagil), avisados)
        assertEquals(listOf(seguro, crediagil), nuevos)
        val texto = textoDeDebitosAutomaticos(nuevos, hoy)!!
        assertEquals("2 débitos automáticos por confirmar", texto.titulo)
        assertEquals(listOf("Seguro del carro · \$210.000", "La cuota de Crediágil 3090 · \$26.485"), texto.lineas)
    }

    @Test
    fun si_el_telefono_no_corrio_ese_dia_dice_que_dia_era() {
        val tarde = debito("credit_9695", "2026-10", vence = "2026-10-03")
        assertEquals(
            "El banco debió cobrar el 3 de octubre la cuota de Libre inversión 9695 (\$1.204.064). ¿Se cobró?",
            textoDeDebitosAutomaticos(listOf(tarde), hoy)!!.texto,
        )
    }

    @Test
    fun la_memoria_tiene_tope_y_olvida_lo_mas_viejo() {
        val muchos = (1..TOPE_DE_DEBITOS_AVISADOS + 5).map { debito("rule-$it", "2026-10") }
        val avisados = recordarDebitosAvisados(emptyList(), muchos)
        assertEquals(TOPE_DE_DEBITOS_AVISADOS, avisados.size)
        assertEquals(muchos.last().clave, avisados.last())
        assertTrue(muchos.first().clave !in avisados)
    }

    @Test
    fun tocarlo_abre_por_revisar() {
        assertEquals(com.jvillada.movi.ui.Screen.PorRevisar, destinoDeAviso(ABRIR_POR_REVISAR))
    }
}
