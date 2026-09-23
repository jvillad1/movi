package com.jvillada.movi.server.compartir

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Las reglas puras de la página compartida: qué NO se muestra, cómo se escribe la plata, y que los
 * colores sean los de la app.
 */
class PaginaCompartidaTest {

    // ── Números de cuenta ───────────────────────────────────────────────────

    @Test
    fun `un numero de cinco digitos o mas queda en sus ultimos cuatro`() {
        assertEquals("Ahorros ••5678", sinNumerosCompletos("Ahorros 0012345678"))
        assertEquals("Visa ••5678", sinNumerosCompletos("Visa 4513 2200 1234 5678"))
        assertEquals("Cuenta ••9012", sinNumerosCompletos("Cuenta 123-456-789-012"))
        assertEquals("Crédito ••4321", sinNumerosCompletos("Crédito 987654321"))
    }

    @Test
    fun `lo que ya es una cola de cuatro, o menos, no se toca`() {
        assertEquals("Hipoteca 1254", sinNumerosCompletos("Hipoteca 1254"))
        assertEquals("Vehículo 8761", sinNumerosCompletos("Vehículo 8761"))
        assertEquals("Crediexpress", sinNumerosCompletos("Crediexpress"))
        assertEquals("Techo 2", sinNumerosCompletos("Techo 2"))
    }

    // ── Formatos ────────────────────────────────────────────────────────────

    @Test
    fun `la plata se escribe como en la app`() {
        assertEquals("$558.350", PaginaCompartida.dinero(558_350))
        assertEquals("−$2.191.000.000", PaginaCompartida.dinero(-2_191_000_000))
        assertEquals("$0", PaginaCompartida.dinero(0))
        assertEquals("$999.999", PaginaCompartida.compacto(999_999))
        assertEquals("$22,2M", PaginaCompartida.compacto(22_200_000))
        assertEquals("−$11,7M", PaginaCompartida.compacto(-11_700_000))
        assertEquals("$2.191M", PaginaCompartida.compacto(2_191_000_000))
    }

    @Test
    fun `el texto de la base se escapa`() {
        val r = resumen(gasto = mapOf("<script>alert(1)</script>" to 10_000L), quien = "Juan & \"Caro\"")
        val html = PaginaCompartida.contenido(r)
        assertFalse("<script>" in html)
        assertTrue("&lt;script&gt;" in html)
        assertTrue("Juan &amp; &quot;Caro&quot;" in html)
    }

    @Test
    fun `el veredicto sale del flujo y no se contradice`() {
        assertEquals(
            "Este período salieron $1.000.000 más de los que entraron.",
            PaginaCompartida.veredicto(resumen(ingresos = 2_000_000, gasto = mapOf("Mercado" to 3_000_000L))),
        )
        assertEquals(
            "Este período entró $500.000 más de lo que salió.",
            PaginaCompartida.veredicto(resumen(ingresos = 1_500_000, gasto = mapOf("Mercado" to 1_000_000L))),
        )
    }

    @Test
    fun `mas de siete categorias se juntan en Otras`() {
        val gasto = (1..9).associate { "Cat $it" to it * 1_000L }
        val vista = PaginaCompartida.categoriasALaVista(gasto)
        assertEquals(7, vista.size)
        assertEquals("Cat 9", vista.first().first)
        assertEquals("Otras" to 6_000L, vista.last())
    }

    @Test
    fun `sin bienes en el modelo no se dibuja un renglon de bienes en cero`() {
        assertFalse(">Bienes<" in PaginaCompartida.contenido(resumen(bienes = null)))
        assertTrue(">Bienes<" in PaginaCompartida.contenido(resumen(bienes = 1_411_903_920)))
    }

    @Test
    fun `una deuda sin tasa ni cuota igual dice algo`() {
        val d = DeudaCompartida("Préstamo", esTarjeta = false, banco = null, saldo = 1, cuota = null, tasaEa = null, sinIntereses = false)
        assertEquals("Sin detalle", PaginaCompartida.detalleDeLaDeuda(d))
        assertEquals("Sin intereses", PaginaCompartida.detalleDeLaDeuda(d.copy(sinIntereses = true, tasaEa = 10.0)))
    }

    // ── Los colores son los de Tokens.kt ────────────────────────────────────

    /**
     * La página no puede importar `Tokens.kt` (es Compose), así que copia los valores. Esta prueba
     * es lo que impide que la copia se quede vieja: si alguien cambia un color de la app, la página
     * tiene que cambiar con él.
     */
    @Test
    fun `los colores de la pagina son los de Tokens kt, en los dos temas`() {
        val tokens = File("../shared/src/commonMain/kotlin/com/jvillada/movi/theme/Tokens.kt").readText()
        val cascara = PaginaCompartida.cascara
        val oscuro = cascara.substringBefore("@media (prefers-color-scheme: light)")
        val claro = cascara.substringAfter("@media (prefers-color-scheme: light)").substringBefore("color-scheme: light")

        val roles = mapOf(
            "fondo" to "--fondo", "tarjeta" to "--tarjeta", "borde" to "--borde", "hilo" to "--hilo",
            "texto" to "--texto", "textoMedio" to "--texto-medio", "textoApagado" to "--texto-apagado",
            "marca" to "--marca", "entra" to "--entra", "sale" to "--sale", "neutro" to "--neutro",
        )
        listOf("COLORES_OSCUROS" to oscuro, "COLORES_CLAROS" to claro).forEach { (tema, css) ->
            val bloque = tokens.substringAfter("val $tema = ColoresDeMovi(").substringBefore("\n)")
            roles.forEach { (rol, variable) ->
                val hex = Regex("""\b$rol\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)""").find(bloque)?.groupValues?.get(1)
                    ?: error("no encontré $rol en $tema")
                val enLaPagina = Regex("""$variable:\s*#([0-9A-Fa-f]{6})""").find(css)?.groupValues?.get(1)
                    ?: error("la página no define $variable para $tema")
                assertEquals(hex.uppercase(), enLaPagina.uppercase(), "$variable en $tema")
            }
        }
    }

    private fun resumen(
        ingresos: Long = 0,
        gasto: Map<String, Long> = emptyMap(),
        quien: String = "Juan",
        bienes: Long? = null,
    ) = ResumenCompartido(
        quien = quien,
        generadoEn = 1_790_000_000_000,
        venceEn = 1_790_600_000_000,
        tuPlata = 558_350,
        condicionado = 0,
        condicionadoA = null,
        bienes = bienes,
        deudas = 0,
        patrimonio = 558_350,
        rangoDelPeriodo = "25 de agosto al 24 de septiembre",
        ingresos = ingresos,
        gastoPorCategoria = gasto,
        deudasConSaldo = emptyList(),
    )
}
