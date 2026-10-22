package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PreferenciasDelInicio
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.SECCIONES_CONFIGURABLES
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.ui.ai.PREGUNTAS_PARA_TOCAR_DE_RESPALDO
import com.jvillada.movi.ui.ai.SenalesParaPreguntar
import com.jvillada.movi.ui.ai.preguntasParaTocar
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ola 3 · Hoy en una mirada, sin pantalla: el veredicto corto del héroe, los chips cortos de
 * «Pregúntale a Movi» y las reglas de «Editar inicio».
 */
class HoyEnUnaMiradaTest {

    // ── El veredicto corto ───────────────────────────────────────────────────

    /** El ejemplo de la revisión del 22-oct: la frase larga pasa a una línea con cifras. */
    @Test
    fun `el veredicto corto con la categoria que mas peso`() {
        assertEquals(
            "−\$893.300 este período · Mercado \$392.100",
            veredictoCortoDelPeriodo(ingresos = 4_000_000, egresos = 4_893_300, mayorGasto = "Mercado" to 392_100L),
        )
        // La larga sigue igual: es la que oye el lector de pantalla.
        assertEquals(
            "Este período salieron \$893.300 más de los que entraron — lo que más pesó fue Mercado: \$392.100",
            veredictoDelPeriodo(ingresos = 4_000_000, egresos = 4_893_300, mayorGasto = "Mercado" to 392_100L)?.frase,
        )
    }

    @Test
    fun `con cuotas de credito las nombra antes que cualquier categoria`() {
        assertEquals(
            "−\$11,7M este período · Cuotas \$12,9M",
            veredictoCortoDelPeriodo(22_152_488, 33_882_000, cuotasDeCredito = 12_915_000, mayorGasto = "Vivienda" to 14_000_000L),
        )
    }

    @Test
    fun `a favor, parejo, sin datos y con el disponible en rojo`() {
        assertEquals("+\$3M este período", veredictoCortoDelPeriodo(10_000_000, 7_000_000))
        assertEquals("\$0 este período · entró lo mismo que salió", veredictoCortoDelPeriodo(2_000_000, 2_000_000))
        assertNull(veredictoCortoDelPeriodo(0, 0))
        assertEquals(
            "+\$3M este período · te pasaste del disponible por \$563.456",
            veredictoCortoDelPeriodo(10_000_000, 7_000_000, avisoDelDisponible = "te pasaste del disponible por \$563.456"),
        )
    }

    @Test
    fun `sale de lo que ya cargo el Inicio, como el largo`() {
        val hoy = LocalDate(2026, 9, 21)
        val data = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 22_152_488, egresos = 33_882_000),
            spentByCategory = mapOf(CUOTA_CATEGORY to 12_915_000L, "Vivienda" to 14_000_000L),
        )
        assertEquals("−\$11,7M este período · Cuotas \$12,9M", veredictoCortoDelInicio(data, hoy))
        assertNull(veredictoCortoDelInicio(DashboardData(), hoy))
        assertNull(veredictoDelInicio(DashboardData(), hoy), "los dos aparecen y desaparecen juntos")
    }

    // ── Los chips de «Pregúntale a Movi» ─────────────────────────────────────

    @Test
    fun `el chip dice la version corta y se manda la completa con su id`() {
        val respaldo = PREGUNTAS_PARA_TOCAR_DE_RESPALDO
        assertEquals(listOf("¿Cómo voy?", "¿En qué se me va la plata?", "¿Qué reviso primero?"), respaldo.map { it.corta })
        // Lo que viaja no cambia: el texto completo y el id por el que el server la reconoce.
        assertEquals(PreguntaSugerida.entries.map { it.texto }, respaldo.map { it.texto })
        assertEquals(PreguntaSugerida.entries.toList(), respaldo.map { it.sugerida })

        val conDatos = preguntasParaTocar(SenalesParaPreguntar(ingresosDelPeriodo = 22_152_488, gastosDelPeriodo = 33_882_000))
        val primera = conDatos.first()
        assertEquals("¿Por qué este período salieron \$11,7M más de los que entraron?", primera.texto)
        assertEquals("¿Por qué salieron \$11,7M de más?", primera.corta)
        assertNull(primera.sugerida)
        assertTrue(conDatos.all { it.corta.length <= it.texto.length }, "la corta nunca es más larga")
    }

    // ── «Editar inicio» ──────────────────────────────────────────────────────

    private val deMovi = PreferenciasDelInicio(ordenDeMovi = SECCIONES_CONFIGURABLES.keys.toList())

    @Test
    fun `la lista del editor sigue el orden elegido y marca las ocultas`() {
        assertEquals(SECCIONES_CONFIGURABLES.keys.toList(), seccionesDelEditor(deMovi).map { it.tipo })
        assertTrue(seccionesDelEditor(deMovi).all { it.visible })
        assertEquals("Disponible", seccionesDelEditor(deMovi).first { it.tipo == "DISPONIBLE_DEL_PERIODO" }.nombre)

        val oculta = deMovi.mostrando("DISPONIBLE_DEL_PERIODO", false)
        assertEquals(listOf("DISPONIBLE_DEL_PERIODO"), oculta.ocultas)
        assertEquals(false, seccionesDelEditor(oculta).first { it.tipo == "DISPONIBLE_DEL_PERIODO" }.visible)
        assertEquals(emptyList(), oculta.mostrando("DISPONIBLE_DEL_PERIODO", true).ocultas)
    }

    @Test
    fun `subir y bajar, sin efecto en los bordes, y volver a mano es el orden de Movi`() {
        val subida = deMovi.moviendo("GASTO_POR_CATEGORIA", -1)
        assertEquals(
            listOf("PREGUNTALE_A_MOVI", "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA", "DISPONIBLE_DEL_PERIODO", "PATRIMONIO", "ALERTS"),
            seccionesDelEditor(subida).map { it.tipo },
        )
        assertEquals(deMovi, deMovi.moviendo("PREGUNTALE_A_MOVI", -1), "la primera no sube")
        assertEquals(deMovi, deMovi.moviendo("ALERTS", +1), "la última no baja")
        // Bajarla de nuevo deja el orden de Movi: la preferencia deja de nombrarlo.
        assertEquals(emptyList(), subida.moviendo("GASTO_POR_CATEGORIA", +1).orden)

        val todo = subida.mostrando("PATRIMONIO", false)
        assertEquals(deMovi, todo.deMovi())
    }
}
