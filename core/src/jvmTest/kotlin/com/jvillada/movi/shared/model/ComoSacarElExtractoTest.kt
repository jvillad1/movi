package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        // Solo los «Movimientos» de la cuenta y de la tarjeta de Bancolombia se probaron en el portal (vuelta
        // 107). Revisión de la vuelta 130: el crédito los heredaba con `copy`; por producto, no por banco.
        assertEquals(
            setOf("bancolombia" to ProductoDelExtracto.CUENTA, "bancolombia" to ProductoDelExtracto.TARJETA),
            GUIAS_DEL_EXTRACTO.filter { it.fuente == FuenteDeLaGuia.PROBADA_EN_EL_BANCO }
                .flatMap { g -> g.productos.map { g.bancoId to it } }.toSet(),
        )
        // Nequi: el paso a paso completo es de Infobae (secundaria); solo va lo del perfil.
        val nequi = guiaDelExtracto("nequi", ProductoDelExtracto.CUENTA)
        assertTrue(nequi.pasos.none { "Ir a web de Nequi" in it || "Generar" in it }, "${nequi.pasos}")
        assertTrue(nequi.pasos.first().contains("«Documentos y certificados»"))
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
            setOf("bancolombia" to ProductoDelExtracto.CREDITO, "lulo" to ProductoDelExtracto.CUENTA),
            GUIAS_DEL_EXTRACTO.filter { it.clave != null }.flatMap { g -> g.productos.map { g.bancoId to it } }.toSet(),
        )
        // La de Bancolombia es la del extracto mensual, no la del PDF de «Movimientos».
        assertNull(GUIA_DE_BANCOLOMBIA.clave)
        assertNull(GUIA_DE_BANCOLOMBIA_TARJETA.clave)
        assertTrue(GUIA_DE_BANCOLOMBIA.otraForma!!.clave!!.contains("cédula"))
    }

    @Test
    fun `Falabella no dice nada del correo, solo del SMS`() {
        val f = guiaDelExtracto("banco_falabella", null)
        assertNull(f.porCorreo)
        assertTrue(f.porSms!!.contains("SMS"))
        assertTrue(textosDelCatalogo().none { "Por correo no" in it })
    }

    @Test
    fun `Bancolombia recomienda los Movimientos o el Excel antes que el extracto mensual`() {
        val g = guiaDelExtracto("bancolombia", ProductoDelExtracto.CUENTA)
        assertSame(GUIA_DE_BANCOLOMBIA, g)
        assertEquals(LecturaDelExtracto.AL_INSTANTE, g.lectura)
        assertTrue("«Movimientos»" in g.queSubir && "Excel" in g.queSubir, g.queSubir)
        // El PDF primero; el Excel de segundo y sin «al instante» (nadie subió uno real).
        assertTrue(g.queSubir.indexOf("PDF") < g.queSubir.indexOf("Excel"), g.queSubir)
        assertTrue("al instante" !in g.queSubir.substring(g.queSubir.indexOf("Excel")), g.queSubir)
        assertTrue(g.pasos.none { "Excel" in it }, "${g.pasos}")
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
        fun con(l: LecturaDelExtracto) = GUIAS_DEL_EXTRACTO.filter { it.lectura == l }
            .flatMap { g -> g.productos.map { g.bancoId to it } }.toSet()
        // Los movimientos: `MovimientosDeBancolombia.kt` (cuenta, tarjeta, fondo) y `ExtractoDeNu.kt`.
        assertEquals(
            setOf(
                "bancolombia" to ProductoDelExtracto.CUENTA,
                "bancolombia" to ProductoDelExtracto.TARJETA,
                "bancolombia" to ProductoDelExtracto.FONDO,
                "nu" to ProductoDelExtracto.TARJETA,
            ),
            con(LecturaDelExtracto.AL_INSTANTE),
        )
        // Solo el saldo, sin movimientos: `ExtractoAfcDavibank.kt` y `ExtractoDeSkandia.kt`.
        assertEquals(setOf("davibank" to ProductoDelExtracto.CUENTA, "skandia" to ProductoDelExtracto.FONDO), con(LecturaDelExtracto.SOLO_EL_SALDO))
        assertEquals("Movi toma el saldo al instante", textoDeLaLectura(LecturaDelExtracto.SOLO_EL_SALDO))
        // El crédito de Bancolombia: solo la libre inversión y la libranza (el server lo vigila con el lector,
        // `ExtractoDeCreditoRoutesTest`).
        assertEquals(setOf("bancolombia" to ProductoDelExtracto.CREDITO), con(LecturaDelExtracto.SOLO_ALGUNOS_CREDITOS))
        assertEquals(LecturaDelExtracto.CON_IA, guiaDelExtracto("nu", ProductoDelExtracto.CUENTA).lectura)
    }

    @Test
    fun `un credito sin lector no promete nada ni ofrece subir`() {
        // Lulo: su guía de crédito se fue (la IA descarta un extracto sin movimientos).
        listOf("lulo", "bbva", "davivienda").forEach { banco ->
            val g = guiaDelExtracto(banco, ProductoDelExtracto.CREDITO)
            assertEquals(LecturaDelExtracto.NO_SE_IMPORTA, g.lectura, banco)
            assertEquals(CREDITO_QUE_NO_SE_IMPORTA, g.queSubir)
            assertFalse(seOfreceSubir(g))
        }
        assertTrue(seOfreceSubir(GUIA_DE_BANCOLOMBIA_CREDITO))
        assertTrue(GUIAS_DEL_EXTRACTO.none { it.bancoId == "lulo" && it.esDe(ProductoDelExtracto.CREDITO) })
    }

    @Test
    fun `la Fiducuenta abre el fondo de Bancolombia`() {
        assertSame(GUIA_DE_BANCOLOMBIA_FONDO, guiaDelExtractoDeLaCuenta(cuenta("Fiducuenta Bancolombia", AccountType.INVESTMENT)))
        assertSame(GUIA_DE_BANCOLOMBIA_FONDO, guiaDelExtractoPara("Fiducuenta"))
    }

    @Test
    fun `los papeles que no se leyeron por el archivo piden la guia, y la IA caida no`() {
        listOf(
            FRASE_EXTRACTO_INCOMPLETO, FRASE_EXTRACTO_SIN_MOVIMIENTOS, FRASE_LECTURA_FALLO + " El archivo quedó guardado en Documentos.",
            FRASE_PAPEL_SIN_MOVIMIENTO, FRASE_PAPEL_SIN_TEXTO, EL_PDF_TIENE_CLAVE, PDF_CON_CLAVE,
        ).forEach { assertTrue(laFallaPideLaGuia(it), it) }
        listOf("La lectura con IA no está disponible ahora. Inténtalo más tarde.", "", null).forEach { assertFalse(laFallaPideLaGuia(it)) }
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
        assertEquals(4, guiasDelBanco("bancolombia").size)
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
            listOf(g.banco, g.donde, g.queSubir, g.siNoLoEncuentras, tituloDeLaGuiaDelExtracto(g), rotuloDelProducto(g), textoDeLaLectura(g.lectura)) +
                g.pasos + listOfNotNull(g.clave, g.porCorreo, g.porSms, g.aviso, g.limite, notaDeLaFuente(g)) +
                g.otraForma?.let { listOf(it.titulo, it.queSubir) + it.pasos + listOfNotNull(it.limite, it.clave) }.orEmpty()
        } + LecturaDelExtracto.entries.map(::textoDeLaLectura) + guiaGenerica(null, null, ProductoDelExtracto.CREDITO).queSubir + listOf(
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
