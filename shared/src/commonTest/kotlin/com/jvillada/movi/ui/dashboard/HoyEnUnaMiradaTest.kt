package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PreferenciasDelInicio
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.seccionesConfigurablesDe
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

    /**
     * Vuelta de diseño 22: la línea de respaldo dice el flujo con su verbo. La Ola 3 la había dejado en
     * «−$893.300 este período · Mercado $392.100», y no se entendía si era neto, gasto o déficit.
     */
    @Test
    fun `el veredicto corto con la categoria que mas peso`() {
        assertEquals(
            "Salió \$893.300 más de lo que entró · lo que más: Mercado \$392.100",
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
            "Salió \$11,7M más de lo que entró · cuotas de crédito: \$12,9M",
            veredictoCortoDelPeriodo(22_152_488, 33_882_000, cuotasDeCredito = 12_915_000, mayorGasto = "Vivienda" to 14_000_000L),
        )
    }

    @Test
    fun `a favor, parejo, sin datos y con el disponible en rojo`() {
        assertEquals("Entró \$3M más de lo que salió", veredictoCortoDelPeriodo(10_000_000, 7_000_000))
        assertEquals("Entró lo mismo que salió", veredictoCortoDelPeriodo(2_000_000, 2_000_000))
        assertNull(veredictoCortoDelPeriodo(0, 0))
        assertEquals(
            "Entró \$3M más de lo que salió · te pasaste del disponible por \$563.456",
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
        assertEquals("Salió \$11,7M más de lo que entró · cuotas de crédito: \$12,9M", veredictoCortoDelInicio(data, hoy))
        // Sin el Disponible (faltan sus lecturas) la frase del héroe es esta misma, con la larga al lado.
        assertEquals(veredictoCortoDelInicio(data, hoy), fraseDelHeroe(data, hoy)?.corta)
        assertEquals(veredictoDelInicio(data, hoy)?.frase, fraseDelHeroe(data, hoy)?.larga)
        assertNull(veredictoCortoDelInicio(DashboardData(), hoy))
        assertNull(veredictoDelInicio(DashboardData(), hoy), "los dos aparecen y desaparecen juntos")
        assertNull(fraseDelHeroe(DashboardData(), hoy))
    }

    // ── La frase del héroe usa la cifra de Plan (vuelta de diseño 22) ────────

    /** Octubre (mes de calendario), hoy 27: $1,7M de gasto del día a día, como dice la columna de Plan. */
    private val hoyDeOctubre = LocalDate(2026, 10, 27)
    private val conDisponible = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 5_893_300),
        spentByCategory = mapOf("Mercado" to 316_050L, "Vivienda" to 1_800_000L, "Comida" to 210_000L),
        periodoActual = com.jvillada.movi.shared.model.PeriodoFinanciero(2026, 10),
        ajustesDePeriodo = com.jvillada.movi.shared.model.PeriodSettings(),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        gastoVariablePorDia = mapOf("2026-10-05" to 1_000_000L, "2026-10-20" to 700_000L),
    )

    @Test
    fun `la frase del heroe dice lo que gastaste con la misma cifra que Plan`() {
        val gastadoEnPlan = disponibleDelInicio(conDisponible, hoyDeOctubre)!!.periodo.gastado
        val frase = fraseDelHeroe(conDisponible, hoyDeOctubre)!!
        // La columna «Período» del Disponible de Plan escribe esta cifra con `formatMoneyCompact`.
        assertTrue(com.jvillada.movi.ui.components.formatMoneyCompact(gastadoEnPlan) in frase.corta, frase.corta)
        // «Lo que más» es la categoría más grande que cabe en lo gastado: la Vivienda de $1,8M es un
        // fijo, no gasto del día a día, y no puede ser más grande que el todo.
        assertEquals("Gastaste \$1,7M · lo que más: Mercado \$316.050", frase.corta)
        assertEquals("Este período gastaste \$1,7M en el día a día; lo que más pesó fue Mercado, con \$316.050", frase.larga)
    }

    @Test
    fun `sin gasto del dia a dia lo dice, sin cifra inventada`() {
        val sinGasto = conDisponible.copy(gastoVariablePorDia = emptyMap())
        assertEquals(SIN_GASTO_EN_EL_PERIODO, fraseDelHeroe(sinGasto, hoyDeOctubre)?.corta)
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

    /** Lo que manda el server: las configurables de la definición de Movi (generación 9). */
    private val deMovi = PreferenciasDelInicio(ordenDeMovi = seccionesConfigurablesDe(defaultDashboardDefinition()))

    @Test
    fun `la lista del editor sigue el orden elegido, marca las ocultas y apaga las opcionales`() {
        // Ola R1: las del Inicio de Movi, y al final las opcionales que no trae, apagadas.
        assertEquals(
            listOf("CUENTAS", "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA", "DISPONIBLE_DEL_PERIODO", "PATRIMONIO"),
            seccionesDelEditor(deMovi).map { it.tipo },
        )
        assertEquals(listOf(true, true, true, false, false), seccionesDelEditor(deMovi).map { it.visible })
        assertEquals("Disponible", seccionesDelEditor(deMovi).first { it.tipo == "DISPONIBLE_DEL_PERIODO" }.nombre)

        val oculta = deMovi.mostrando("CUENTAS", false)
        assertEquals(listOf("CUENTAS"), oculta.ocultas)
        assertEquals(false, seccionesDelEditor(oculta).first { it.tipo == "CUENTAS" }.visible)
        assertEquals(emptyList(), oculta.mostrando("CUENTAS", true).ocultas)

        // Prender una opcional la anota en `visibles`, no en `ocultas`.
        val conDisponible = deMovi.mostrando("DISPONIBLE_DEL_PERIODO", true)
        assertEquals(listOf("DISPONIBLE_DEL_PERIODO"), conDisponible.visibles)
        assertEquals(emptyList(), conDisponible.ocultas)
        assertEquals(true, seccionesDelEditor(conDisponible).first { it.tipo == "DISPONIBLE_DEL_PERIODO" }.visible)
        assertEquals(emptyList(), conDisponible.mostrando("DISPONIBLE_DEL_PERIODO", false).visibles)
    }

    /**
     * Si el Editor de pantallas puso una opcional en el Inicio de todos (viene en `ordenDeMovi`), se
     * esconde como cualquier otra: en `ocultas`.
     */
    @Test
    fun `una opcional que ya viene en el Inicio de Movi se esconde como las demas`() {
        val conPatrimonioGlobal = PreferenciasDelInicio(ordenDeMovi = deMovi.ordenDeMovi + "PATRIMONIO")
        assertEquals(true, seccionesDelEditor(conPatrimonioGlobal).first { it.tipo == "PATRIMONIO" }.visible)
        assertEquals(listOf("PATRIMONIO"), conPatrimonioGlobal.mostrando("PATRIMONIO", false).ocultas)
    }

    @Test
    fun `subir y bajar, sin efecto en los bordes, y volver a mano es el orden de Movi`() {
        val subida = deMovi.moviendo("GASTO_POR_CATEGORIA", -1)
        assertEquals(
            listOf("CUENTAS", "GASTO_POR_CATEGORIA", "CHECKLIST_DEL_PERIODO", "DISPONIBLE_DEL_PERIODO", "PATRIMONIO"),
            seccionesDelEditor(subida).map { it.tipo },
        )
        assertEquals(deMovi, deMovi.moviendo("CUENTAS", -1), "la primera no sube")
        assertEquals(deMovi, deMovi.moviendo("PATRIMONIO", +1), "la última no baja")
        // Bajarla de nuevo deja el orden de Movi: la preferencia deja de nombrarlo.
        assertEquals(emptyList(), subida.moviendo("GASTO_POR_CATEGORIA", +1).orden)

        val todo = subida.mostrando("PATRIMONIO", true).mostrando("CUENTAS", false)
        assertEquals(deMovi, todo.deMovi())
    }

    /**
     * Vuelta 66 · el caso de la revisión: «Disponible» quedó en `ocultas` de cuando venía en Hoy. Al
     * prenderla, el editor la saca de ahí (antes solo la agregaba a `visibles` y ganaba la oculta).
     */
    @Test
    fun `prender una opcional escondida de antes la saca de ocultas`() {
        val deAntes = deMovi.copy(ocultas = listOf("DISPONIBLE_DEL_PERIODO"))
        val prendida = deAntes.mostrando("DISPONIBLE_DEL_PERIODO", true)
        assertEquals(listOf("DISPONIBLE_DEL_PERIODO"), prendida.visibles)
        assertEquals(emptyList(), prendida.ocultas)
        // La preferencia ya guardada con las dos: el editor dice lo mismo que Hoy (prendida).
        val guardada = deMovi.copy(ocultas = listOf("DISPONIBLE_DEL_PERIODO"), visibles = listOf("DISPONIBLE_DEL_PERIODO"))
        assertEquals(true, seccionesDelEditor(guardada).first { it.tipo == "DISPONIBLE_DEL_PERIODO" }.visible)
    }
}
