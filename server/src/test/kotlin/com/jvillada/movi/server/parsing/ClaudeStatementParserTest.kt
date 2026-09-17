package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeStatementParserTest {

    @Test
    fun `parseJson extracts transactions from clean JSON array`() {
        val json = """[{"date":"2025-05-28","merchant":"Rappi","amount":48900,"type":"EXPENSE","category":"Restaurantes","description":"Domicilio","rawText":"COMPRA RAPPI"}]"""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(1, result.size)
        assertEquals("Rappi", result[0].merchant)
        assertEquals(48900L, result[0].amount)
        assertEquals(TransactionType.EXPENSE, result[0].type)
    }

    @Test
    fun `parseJson extracts JSON array embedded in prose`() {
        val json = """Here are the transactions: [{"date":"2025-05-25","merchant":"Globant","amount":4500000,"type":"INCOME","category":"Salario","description":"Nomina","rawText":"ABONO NOMINA"}] end."""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(1, result.size)
        assertEquals("Globant", result[0].merchant)
        assertEquals(TransactionType.INCOME, result[0].type)
    }

    @Test
    fun `parseJson returns empty list for invalid JSON`() {
        val result = ClaudeStatementParser.parseJson("no json here")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseJson assigns unique IDs to each transaction`() {
        val json = """[
          {"date":"2025-05-28","merchant":"A","amount":100,"type":"EXPENSE","category":"Otro","description":"","rawText":""},
          {"date":"2025-05-27","merchant":"B","amount":200,"type":"EXPENSE","category":"Otro","description":"","rawText":""}
        ]"""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(2, result.size)
        assertTrue(result[0].id != result[1].id)
        assertTrue(result[0].id.isNotBlank())
    }

    @Test
    fun `parseJson maps currency and defaults to COP`() {
        val json = """[
          {"date":"2026-06-04","merchant":"Anthropic","amount":100,"currency":"USD","type":"EXPENSE","category":"Tecnología","description":"","rawText":""},
          {"date":"2026-05-31","merchant":"YouTube","amount":79000,"type":"EXPENSE","category":"Entretenimiento","description":"","rawText":""}
        ]"""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(2, result.size)
        assertEquals("USD", result[0].currency)
        assertEquals("COP", result[1].currency) // absent -> default
    }

    @Test
    fun `parseJson normalizes currency casing and blanks`() {
        val json = """[
          {"date":"2026-06-04","merchant":"A","amount":100,"currency":"usd","type":"EXPENSE","category":"Tecnología","description":"","rawText":""},
          {"date":"2026-06-04","merchant":"B","amount":50,"currency":" ","type":"EXPENSE","category":"Otros","description":"","rawText":""}
        ]"""
        val r = ClaudeStatementParser.parseJson(json)
        assertEquals("USD", r[0].currency)
        assertEquals("COP", r[1].currency)
    }

    // ── Un extracto largo: el corte se detecta y el texto se trocea ──────────────
    //
    // El defecto: con `maxTokens(4096)` y sin trocear, un mes de Ahorros con ~80 movimientos
    // devolvía un array JSON **sin cerrar**. `parseJson` no encontraba el `]`, devolvía vacío, y
    // la ruta contestaba 200 con cero filas: la pantalla de revisión abría en «0 nuevas · 0
    // coincidencias» con el botón de importar apagado. Eso se lee como «este mes ya estaba
    // conciliado» — la lectura fallaba y nadie lo decía.

    @Test
    fun `una respuesta cortada por el tope de tokens se reconoce`() {
        val aMedias = """[{"date":"2026-08-01","merchant":"Exito","amount":48900,"type":"EXPENSE",""" +
            """"category":"Mercado","description":"","rawText":""},{"date":"2026-08-02","merchant":"Ra"""

        assertTrue(ClaudeStatementParser.quedoCortada(aMedias, stopReason = "max_tokens"))
        // Y también sin que el API lo diga: el array abierto alcanza como señal.
        assertTrue(ClaudeStatementParser.quedoCortada(aMedias, stopReason = null))
    }

    @Test
    fun `una respuesta entera no se reporta como cortada`() {
        val entera = """[{"date":"2026-08-01","merchant":"Exito","amount":48900,"type":"EXPENSE",""" +
            """"category":"Mercado","description":"","rawText":""}]"""

        assertTrue(!ClaudeStatementParser.quedoCortada(entera, stopReason = "end_turn"))
        assertTrue(!ClaudeStatementParser.quedoCortada("[]", stopReason = "end_turn"))
    }

    /**
     * Una respuesta que no trae JSON **no** está cortada: está vacía. Son dos fallas distintas y
     * la ruta les contesta cosas distintas — «divide el archivo» contra «acá no hay movimientos».
     */
    @Test
    fun `una respuesta sin JSON no es un corte`() {
        assertTrue(!ClaudeStatementParser.quedoCortada("No encontré movimientos en el documento.", stopReason = "end_turn"))
    }

    @Test
    fun `un extracto que entra en un pedido no se trocea`() {
        val corto = (1..10).joinToString("\n") { "01/08  COMPRA EXITO  48.900" }

        assertEquals(listOf(corto), ClaudeStatementParser.dividirEnPedazos(corto))
    }

    @Test
    fun `un extracto largo se parte por lineas, sin perder ni repetir ninguna`() {
        val lineas = (1..500).map { "01/08  COMPRA NUMERO $it  48.900" }
        val texto = lineas.joinToString("\n")

        val pedazos = ClaudeStatementParser.dividirEnPedazos(texto, maxCaracteres = 1_000)

        assertTrue(pedazos.size > 1, "un texto de ${texto.length} caracteres no entra en 1.000")
        assertTrue(pedazos.all { it.length <= 1_000 }, "ningún pedazo puede pasarse del tope")
        // Lo que no se puede perder: las mismas líneas, en el mismo orden. Una fila partida al
        // medio sería un movimiento inventado en un pedazo y otro mutilado en el siguiente.
        assertEquals(lineas, pedazos.flatMap { it.lines() })
    }

    /**
     * Una línea sola más larga que el tope entra igual, en su propio pedazo. Partirla sería
     * exactamente el daño que el troceo por líneas evita — y un extracto en PDF donde el texto
     * salió todo en un renglón es un archivo raro, no un archivo que haya que cortar mal.
     */
    @Test
    fun `una linea mas larga que el tope entra entera`() {
        val larga = "X".repeat(3_000)

        val pedazos = ClaudeStatementParser.dividirEnPedazos("corta\n$larga\ncorta", maxCaracteres = 1_000)

        assertTrue(pedazos.contains(larga), "la línea larga tiene que llegar entera: $pedazos")
    }

    /**
     * El encabezado viaja con cada pedazo porque el prompt le pide al modelo que saque de ahí el
     * AÑO cuando las fechas vienen como «15/04». Sin él, la segunda mitad de un extracto troceado
     * se importaba con el año equivocado.
     */
    @Test
    fun `el encabezado son las primeras lineas con contenido`() {
        val texto = """
            BANCOLOMBIA

            Cuenta de Ahorros ****3684
            DESDE 01/08/2026 HASTA 31/08/2026

            01/08  COMPRA EXITO  48.900
        """.trimIndent()

        val encabezado = ClaudeStatementParser.encabezadoDe(texto, lineas = 3)

        assertEquals(
            "BANCOLOMBIA\nCuenta de Ahorros ****3684\nDESDE 01/08/2026 HASTA 31/08/2026",
            encabezado,
        )
    }

    // image-branch offline tests — no network calls, exercise the mime/extension helpers only

    @Test
    fun `isImageMime detects image mime types correctly`() {
        assertTrue(ClaudeStatementParser.isImageMime("image/png"))
        assertTrue(ClaudeStatementParser.isImageMime("image/jpeg"))
        assertTrue(ClaudeStatementParser.isImageMime("image/webp"))
        assertTrue(ClaudeStatementParser.isImageMime("image/gif"))
        assertTrue(ClaudeStatementParser.isImageMime("image/heic"))
    }

    @Test
    fun `isImageMime rejects non-image types`() {
        assertTrue(!ClaudeStatementParser.isImageMime("application/pdf"))
        assertTrue(!ClaudeStatementParser.isImageMime("text/csv"))
        assertTrue(!ClaudeStatementParser.isImageMime("application/vnd.ms-excel"))
        assertTrue(!ClaudeStatementParser.isImageMime(""))
    }

    @Test
    fun `supportedImageMime maps supported types and rejects unsupported`() {
        // direct mime
        assertEquals("image/png", ClaudeStatementParser.supportedImageMime("image/png", "x.png"))
        assertEquals("image/jpeg", ClaudeStatementParser.supportedImageMime("image/jpeg", "x.jpg"))
        assertEquals("image/jpeg", ClaudeStatementParser.supportedImageMime("image/jpg", "x.jpg")) // jpg -> jpeg
        // blank mime falls back to filename extension
        assertEquals("image/png", ClaudeStatementParser.supportedImageMime("", "captura.png"))
        assertEquals("image/jpeg", ClaudeStatementParser.supportedImageMime("application/octet-stream", "foto.JPEG"))
        // unsupported (HEIC) -> null so the route can 422 instead of crashing
        assertEquals(null, ClaudeStatementParser.supportedImageMime("image/heic", "foto.heic"))
        assertEquals(null, ClaudeStatementParser.supportedImageMime("", "foto.heic"))
    }
}
