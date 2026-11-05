package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # Vuelta 130 · el catálogo «Cómo bajar tu extracto», banco por banco
 *
 * (La vuelta 107 tenía dos guías, Bancolombia y una genérica de Nu; esta prueba reemplaza la suya.)
 */
class ComoSacarElExtractoTest {

    // ── El catálogo ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `toda entrada tiene banco, producto, donde, pasos, que subir y si no lo encuentras`() {
        assertTrue(GUIAS_DEL_EXTRACTO.size >= 15, "el catálogo cubre los bancos de la investigación")
        GUIAS_DEL_EXTRACTO.forEach { g ->
            assertTrue(g.bancoId.isNotBlank() && g.banco.isNotBlank(), "sin banco: $g")
            assertTrue(g.productos.isNotEmpty(), "sin producto: ${g.banco}")
            assertTrue(g.donde.isNotBlank(), "sin dónde: ${g.banco}")
            assertTrue(g.pasos.isNotEmpty(), "sin pasos: ${g.banco}")
            assertTrue(g.queSubir.isNotBlank(), "sin qué subir: ${g.banco}")
            assertTrue(g.siNoLoEncuentras.isNotBlank(), "sin «si no lo encuentras»: ${g.banco}")
        }
        // Un banco y un producto, una sola guía.
        val pares = GUIAS_DEL_EXTRACTO.flatMap { g -> g.productos.map { g.bancoId to it } }
        assertEquals(pares.size, pares.toSet().size, "un producto con dos guías del mismo banco")
    }

    @Test
    fun `los bancos se llaman como en el resto de la app`() {
        GUIAS_DEL_EXTRACTO.forEach { g ->
            val marca = BANCOS_CONOCIDOS.firstOrNull { it.id == g.bancoId }
            assertNotNull(marca, "${g.bancoId} no es un banco de MarcasConocidas")
            assertEquals(marca.nombre, g.banco)
            assertTrue(bancosParaElegir(paraTarjetas = true).any { it.id == g.bancoId }, "${g.banco} no se puede elegir")
        }
    }

    /**
     * La investigación dice «[no encontré]» los pasos de Davivienda (la oficial no los da; los de Rankia
     * son secundarios), Davibank y el extracto del fondo de Skandia: llevan **exactamente** los genéricos.
     * Y al revés: una guía con pasos genéricos lo dice en su fuente, y una con pasos del banco no los copia.
     */
    @Test
    fun `ninguna entrada sin fuente trae pasos inventados`() {
        val sinPasos = setOf("davivienda", "davibank", "skandia")
        GUIAS_DEL_EXTRACTO.filter { it.bancoId in sinPasos }.forEach { g ->
            assertEquals(PASOS_GENERICOS, g.pasos, "${g.banco} · ${g.producto} no tiene fuente para sus pasos")
            assertEquals(FuenteDeLaGuia.SIN_PASOS_DEL_BANCO, g.fuente)
        }
        GUIAS_DEL_EXTRACTO.forEach { g ->
            assertEquals(g.fuente == FuenteDeLaGuia.SIN_PASOS_DEL_BANCO, g.conPasosGenericos, "${g.banco} · ${g.producto}")
        }
        // Solo los «Movimientos» de Bancolombia se probaron en el banco.
        assertEquals(
            setOf("bancolombia"),
            GUIAS_DEL_EXTRACTO.filter { it.fuente == FuenteDeLaGuia.PROBADA_EN_EL_BANCO }.map { it.bancoId }.toSet(),
        )
        // Un banco que no está en el catálogo, o un producto que su guía no cubre: lo genérico.
        val tarjetaBbva = guiaDelExtracto("bbva", ProductoDelExtracto.TARJETA)
        assertEquals(PASOS_GENERICOS, tarjetaBbva.pasos)
        assertEquals("BBVA", tarjetaBbva.banco)
        val itau = guiaDelExtracto("itau", ProductoDelExtracto.CUENTA, "Itaú")
        assertEquals(PASOS_GENERICOS, itau.pasos)
        assertEquals("Itaú", itau.banco)
        assertEquals(SI_NO_LO_ENCUENTRAS_GENERICO, itau.siNoLoEncuentras)
        assertEquals(TU_BANCO, guiaGenerica(null, null, null).banco)
    }

    @Test
    fun `la clave solo donde la fuente la confirma`() {
        assertEquals(
            setOf("bancolombia", "lulo"),
            GUIAS_DEL_EXTRACTO.filter { it.clave != null }.map { it.bancoId }.toSet(),
        )
        assertTrue(GUIA_DE_BANCOLOMBIA.clave!!.contains("cédula"))
    }

    @Test
    fun `Bancolombia recomienda los Movimientos o el Excel antes que el extracto mensual`() {
        val g = guiaDelExtracto("bancolombia", ProductoDelExtracto.CUENTA)
        assertSame(GUIA_DE_BANCOLOMBIA, g)
        assertEquals(LecturaDelExtracto.AL_INSTANTE, g.lectura)
        assertTrue("«Movimientos»" in g.queSubir && "Excel" in g.queSubir, g.queSubir)
        assertTrue(g.pasos.any { "pestaña «Movimientos»" in it })
        // El mensual va después, como segunda opción, y con IA.
        val mensual = assertNotNull(g.otraForma)
        assertEquals(LecturaDelExtracto.CON_IA, mensual.lectura)
        assertTrue(mensual.pasos.first().contains("«Documentos» → «Extractos»"), mensual.pasos.first())
        assertTrue(mensual.pasos.any { "clave dinámica" in it })
        assertEquals("Deja bajar extractos de hasta 2 años atrás.", mensual.limite)
        // La web, no la app; y el aviso del portal.
        assertTrue("Sucursal Virtual Personas" in g.donde && "2 meses" in g.donde, g.donde)
        assertTrue(g.aviso!!.contains("dirección"))
        // La tarjeta: los «Movimientos» en PDF (el Excel de la tarjeta no tiene lector propio).
        val tarjeta = guiaDelExtracto("bancolombia", ProductoDelExtracto.TARJETA)
        assertTrue(tarjeta.pasos.any { "Tarjetas de crédito" in it })
        assertTrue("«Movimientos»" in tarjeta.queSubir && "Excel" !in tarjeta.queSubir)
        assertEquals(LecturaDelExtracto.AL_INSTANTE, tarjeta.lectura)
        // Sin el lector de Excel, la cuenta solo ofrece el PDF.
        assertEquals("Los «Movimientos» en PDF: Movi los lee al instante.", queSubirDe(g, lectorDeExcel = false))
    }

    @Test
    fun `lo que Movi lee al instante es lo que leen los lectores sin IA del server`() {
        assertEquals(
            setOf(
                "bancolombia" to ProductoDelExtracto.CUENTA,
                "bancolombia" to ProductoDelExtracto.TARJETA,
                "bancolombia" to ProductoDelExtracto.CREDITO,
                "nu" to ProductoDelExtracto.TARJETA,
                "davibank" to ProductoDelExtracto.CUENTA,
                "skandia" to ProductoDelExtracto.FONDO,
            ),
            GUIAS_DEL_EXTRACTO.filter { it.lectura == LecturaDelExtracto.AL_INSTANTE }
                .flatMap { g -> g.productos.map { g.bancoId to it } }.toSet(),
        )
        assertEquals(LecturaDelExtracto.CON_IA, guiaDelExtracto("nu", ProductoDelExtracto.CUENTA).lectura)
    }

    // ── Elegir la guía ───────────────────────────────────────────────────────────────────────────

    private fun cuenta(nombre: String, tipo: AccountType) = Account("a", nombre, tipo, 0)

    @Test
    fun `la cuenta dice el banco y el producto`() {
        assertSame(GUIA_DE_NU_CUENTA, guiaDelExtractoDeLaCuenta(cuenta("Nu Ahorros", AccountType.SAVINGS)))
        assertSame(GUIA_DE_NU, guiaDelExtractoDeLaCuenta(cuenta("Nu Tarjeta", AccountType.CREDIT_CARD)))
        assertSame(GUIA_DE_BANCOLOMBIA, guiaDelExtractoDeLaCuenta(cuenta("Bancolombia Ahorros 8133 · 4057", AccountType.SAVINGS)))
        // La tarjeta no dice el banco en el nombre: lo dicen sus condiciones.
        assertSame(GUIA_DE_BANCOLOMBIA_TARJETA, guiaDelExtractoDeLaCuenta(cuenta("Master Black 3684", AccountType.CREDIT_CARD), "Bancolombia"))
        // La AMEX es de Bancolombia; Scotiabank Colpatria es Davibank.
        assertSame(GUIA_DE_BANCOLOMBIA_TARJETA, guiaDelExtractoDeLaCuenta(cuenta("AMEX", AccountType.CREDIT_CARD)))
        assertEquals("Davibank", guiaDelExtractoDeLaCuenta(cuenta("Scotiabank Colpatria", AccountType.SAVINGS))!!.banco)
        assertSame(GUIA_DE_BANCOLOMBIA_CREDITO, guiaDelExtractoDeLaCuenta(cuenta("Libre inversión Bancolombia", AccountType.LOAN)))
        // Sin banco, sin extracto o con solo la red de la tarjeta: nada (la hoja ofrece la lista).
        assertNull(guiaDelExtractoDeLaCuenta(cuenta("Master Black 3684", AccountType.CREDIT_CARD)))
        assertNull(guiaDelExtractoDeLaCuenta(cuenta("Visa Oro", AccountType.CREDIT_CARD)))
        assertNull(guiaDelExtractoDeLaCuenta(cuenta("Efectivo Nu", AccountType.CASH)))
        assertNull(guiaDelExtractoDeLaCuenta(cuenta("Ahorros", AccountType.SAVINGS)))
    }

    @Test
    fun `el texto del banco de un extracto o de un aviso abre su guia`() {
        assertSame(GUIA_DE_NU, guiaDelExtractoPara("Notificación · Nu"))
        assertSame(GUIA_DE_NU_CUENTA, guiaDelExtractoPara("Nu", ProductoDelExtracto.CUENTA))
        assertSame(GUIA_DE_BANCOLOMBIA_TARJETA, guiaDelExtractoParaAlguno(null, "Bancolombia", producto = ProductoDelExtracto.TARJETA))
        assertEquals("Davivienda", guiaDelExtractoPara("Davivienda 9418")!!.banco)
        assertNull(guiaDelExtractoPara("Número nuevo"))
        assertNull(guiaDelExtractoPara(""))
        assertNull(guiaDelExtractoPara(null))
        // Un comercio no es un banco.
        assertNull(guiaDelExtractoPara("Éxito"))
    }

    @Test
    fun `el selector de producto nombra lo que cubre cada guia`() {
        assertEquals("Cuenta", rotuloDelProducto(GUIA_DE_BANCOLOMBIA))
        assertEquals("Tarjeta de crédito", rotuloDelProducto(GUIA_DE_NU))
        assertEquals("Cuenta o tarjeta de crédito", rotuloDelProducto(guiaDelExtracto("davivienda", null)))
        assertEquals(3, guiasDelBanco("bancolombia").size)
        assertEquals(guiasDelBanco("bancolombia"), guiasDelBanco("amex"))
    }

    // ── Lo que dice la hoja ──────────────────────────────────────────────────────────────────────

    @Test
    fun `la pastilla, el titulo y la nota de la fuente`() {
        assertEquals("Movi lo lee al instante", textoDeLaLectura(LecturaDelExtracto.AL_INSTANTE))
        assertEquals("Con IA · puede tardar un poco", textoDeLaLectura(LecturaDelExtracto.CON_IA))
        assertEquals("Cómo bajar tu extracto de Bancolombia", tituloDeLaGuiaDelExtracto(GUIA_DE_BANCOLOMBIA))
        assertNull(notaDeLaFuente(GUIA_DE_BANCOLOMBIA))
        assertEquals(NOTA_DE_LA_GUIA_SIN_VERIFICAR, notaDeLaFuente(GUIA_DE_NU))
        assertEquals(NOTA_DE_LA_GUIA_GENERICA, notaDeLaFuente(guiaDelExtracto("davivienda", null)))
    }

    @Test
    fun `el rango ideal va desde el ultimo corte hasta hoy`() {
        assertEquals(
            "Rango ideal: desde tu último corte, el 23 de octubre, hasta hoy.",
            rangoIdealDelExtracto(LocalDate(2026, 10, 23), LocalDate(2026, 11, 2)),
        )
        assertEquals("Rango ideal: hoy.", rangoIdealDelExtracto(LocalDate(2026, 11, 2), LocalDate(2026, 11, 2)))
    }

    // ── Las guardas del texto (el catálogo vive en :core, fuera del barrido de `ui/`) ────────────

    private fun textosDelCatalogo(): List<String> =
        (GUIAS_DEL_EXTRACTO + guiaGenerica(null, null, null)).flatMap { g ->
            listOf(g.banco, g.donde, g.queSubir, g.siNoLoEncuentras, tituloDeLaGuiaDelExtracto(g), rotuloDelProducto(g)) +
                g.pasos + listOfNotNull(g.clave, g.porCorreo, g.aviso, g.limite, notaDeLaFuente(g)) +
                g.otraForma?.let { listOf(it.titulo, it.queSubir) + it.pasos + listOfNotNull(it.limite) }.orEmpty()
        } + listOf(
            MOVI_LO_LEE_AL_INSTANTE, CON_IA_PUEDE_TARDAR, COMO_BAJARLO, COMO_BAJO_MI_EXTRACTO,
            COMO_BAJAR_TUS_EXTRACTOS, COMO_BAJAR_EL_ARCHIVO_QUE_MOVI_LEE_MEJOR, SUBIR_EL_EXTRACTO_DE_LA_GUIA,
        )

    @Test
    fun `los textos citan con comillas latinas y solo usan glifos que Figtree tiene`() {
        System.setProperty("java.awt.headless", "true")
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "shared/src/commonMain/composeResources/font/figtree_regular.ttf").isFile) dir = dir.parentFile
        val ttf = File(dir ?: fail("no se encontró la fuente"), "shared/src/commonMain/composeResources/font/figtree_regular.ttf")
        val figtree = ttf.inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }
        textosDelCatalogo().forEach { t ->
            assertTrue('"' !in t && '\'' !in t, "comillas rectas en «$t»")
            t.codePoints().filter { it > 127 && !figtree.canDisplay(it) }.forEach { cp ->
                fail("«${String(Character.toChars(cp))}» no está en Figtree: «$t»")
            }
        }
    }

    @Test
    fun `los textos tutean, sin voseo`() {
        val voseo = Regex("""(?i)(?<![\p{L}])(vos|sos|tenés|podés|querés|bajá|subí|elegí|buscá|tocá|abrí|entrá|escribí|pedí|revisá|marcá)(?![\p{L}])""")
        textosDelCatalogo().forEach { t -> assertNull(voseo.find(t), "voseo en «$t»") }
    }
}
