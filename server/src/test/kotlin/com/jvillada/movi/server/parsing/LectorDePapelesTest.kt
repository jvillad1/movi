package com.jvillada.movi.server.parsing

import com.jvillada.movi.server.routes.categoriaDelComprobante
import com.jvillada.movi.server.routes.cuandoDelComprobante
import com.jvillada.movi.server.routes.huellaDelPapel
import com.jvillada.movi.server.routes.montoDelComprobante
import com.jvillada.movi.server.routes.nombreDelComprobante
import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.server.routes.parsedDelComprobante
import com.jvillada.movi.server.routes.textoDelComprobante
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.identificadorDelDestinoEn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo puro de «Compartir con Movi»: cómo se lee la respuesta del modelo, cómo se escribe la propuesta
 * y cuándo un PDF ya es un extracto. Sin la API de Anthropic: las respuestas son cadenas a mano.
 */
class LectorDePapelesTest {

    // ── La respuesta del modelo ──────────────────────────────────────────────────

    @Test
    fun `un comprobante se lee aunque venga envuelto en texto`() {
        val dijo = queDiceLaRespuesta(
            """Claro: {"tipo":"COMPROBANTE","monto":138600,"moneda":"COP","movimiento":"EXPENSE","fecha":"2026-09-30","hora":"9:05","comercio":"Coomeva","categoria":"Salud","banco":"Bancolombia","cuentaPropia":"****8133","cuentaDestino":null,"llave":null,"concepto":"Pago PSE"} listo""",
        )
        val leido = assertIs<QueDiceElPapel.Comprobante>(dijo).leido
        assertEquals(138_600.0, leido.monto)
        assertEquals(TransactionType.EXPENSE, leido.tipo)
        assertEquals("2026-09-30", leido.fecha)
        assertEquals("09:05", leido.hora)
        assertEquals("8133", leido.cuentaPropia)
        assertNull(leido.cuentaDestino)
    }

    @Test
    fun `un ingreso en dolares con centavos`() {
        val leido = assertIs<QueDiceElPapel.Comprobante>(
            queDiceLaRespuesta("""{"tipo":"COMPROBANTE","monto":"20.5","moneda":"usd","movimiento":"INCOME","fecha":"no se ve","comercio":"Upwork"}"""),
        ).leido
        assertEquals(20.5, leido.monto)
        assertEquals("USD", leido.moneda)
        assertEquals(TransactionType.INCOME, leido.tipo)
        assertNull(leido.fecha, "una fecha que no es YYYY-MM-DD no se inventa")
    }

    @Test
    fun `extracto, nada y basura`() {
        assertEquals(QueDiceElPapel.Extracto, queDiceLaRespuesta("""{"tipo":"EXTRACTO"}"""))
        assertEquals(QueDiceElPapel.SinMovimiento, queDiceLaRespuesta("""{"tipo":"NADA"}"""))
        assertEquals(QueDiceElPapel.Ilegible, queDiceLaRespuesta("no sé qué es esto"))
        assertEquals(QueDiceElPapel.Ilegible, queDiceLaRespuesta("""{"tipo":"COMPROBANTE","monto":0}"""))
        assertEquals(QueDiceElPapel.Ilegible, queDiceLaRespuesta("""{"tipo":"COMPROBANTE"}"""), "sin monto no hay movimiento")
    }

    // ── La propuesta ─────────────────────────────────────────────────────────────

    private val transferenciaACaro = ComprobanteLeido(
        monto = 250_000.0,
        tipo = TransactionType.EXPENSE,
        fecha = "2026-09-30",
        hora = "18:40",
        banco = "Bancolombia",
        cuentaPropia = "8133",
        cuentaDestino = "31973270756",
        concepto = "Ref 123456789",
    )

    @Test
    fun `el texto se escribe como un aviso del banco que la app ya sabe leer`() {
        val texto = textoDelComprobante(transferenciaACaro)
        assertEquals(
            "Bancolombia: Pagaste $250.000 a la cuenta *31973270756 desde tu cuenta *8133 el 30/09/2026 a las 18:40. Ref.",
            texto,
        )
        // La cuenta de destino la reconocen las cuentas de otros…
        val destino = identificadorDelDestinoEn(texto)
        assertEquals(TipoDeIdentificador.NUMERO, destino?.tipo)
        assertEquals("31973270756", destino?.valor)
        // …y el parser de SMS lee el mismo monto y el mismo tipo (lo usa «el mismo pago» de la bandeja).
        val comoSms = parseSms(texto)
        assertEquals(250_000.0, comoSms?.amount)
        assertEquals(TransactionType.EXPENSE, comoSms?.type)
        // La referencia no se lee como una cuenta.
        assertFalse("123456789" in texto)
    }

    @Test
    fun `un ingreso dice de quien y en que cuenta`() {
        val texto = textoDelComprobante(
            ComprobanteLeido(monto = 300_000.0, tipo = TransactionType.INCOME, comercio = "Carolina Restrepo", cuentaPropia = "3333"),
        )
        assertEquals("Recibiste $300.000 de Carolina Restrepo en tu cuenta *3333.", texto)
        assertEquals(TransactionType.INCOME, parseSms(texto)?.type)
    }

    @Test
    fun `el nombre cae a la cuenta o la llave cuando no hay comercio`() {
        assertEquals("Transferencia a la cuenta *31973270756", nombreDelComprobante(transferenciaACaro))
        assertEquals("Transferencia · llave 0092184713", nombreDelComprobante(ComprobanteLeido(monto = 1.0, llave = "0092184713")))
        assertEquals("Movimiento", nombreDelComprobante(ComprobanteLeido(monto = 1.0)))
    }

    @Test
    fun `la categoria del modelo solo si es del catalogo y no reservada`() {
        assertEquals("Salud", categoriaDelComprobante(ComprobanteLeido(monto = 1.0, categoria = "salud"), "Coomeva"))
        assertEquals(CARD_PAYMENT_CATEGORY, categoriaDelComprobante(ComprobanteLeido(monto = 1.0, categoria = "Pago de tarjeta"), "AMEX"))
        assertEquals("Otros", categoriaDelComprobante(ComprobanteLeido(monto = 1.0, categoria = "Inventada"), "Zzz"))
        // Una reservada (un traspaso) no nace de un papel: es media pata sin la otra.
        assertTrue(categoriaDelComprobante(ComprobanteLeido(monto = 1.0, categoria = "Traspaso"), "Zzz") != "Traspaso")
    }

    @Test
    fun `la propuesta trae el identificador del destino`() {
        val p = parsedDelComprobante(transferenciaACaro, textoDelComprobante(transferenciaACaro))
        assertEquals("31973270756", p.identificadorDelDestino)
        assertFalse(p.identificadorEsLlave)
    }

    @Test
    fun `cuando sin hora es el mediodia y sin fecha es ahora`() {
        assertEquals("2026-09-30 12:00", cuandoDelComprobante(ComprobanteLeido(monto = 1.0, fecha = "2026-09-30"), ahora = 0))
        // 2026-10-01 15:30 UTC = 10:30 en Bogotá.
        assertEquals("2026-10-01 10:30", cuandoDelComprobante(ComprobanteLeido(monto = 1.0), ahora = 1_790_868_600_000L))
    }

    @Test
    fun `montos como los escribe el banco`() {
        assertEquals("$138.600", montoDelComprobante(138_600.0, "COP"))
        assertEquals("$1.250.000", montoDelComprobante(1_250_000.4, "COP"))
        assertEquals("USD 20,50", montoDelComprobante(20.5, "USD"))
        assertEquals("USD 99", montoDelComprobante(99.0, "USD"))
    }

    // ── El clasificador frente a créditos, facturas y desembolsos ───────────────

    @Test
    fun `el clasificador sabe que un credito sin movimientos y una factura sin pagar son NADA`() {
        val prompt = ClaudeStatementParser.promptDelPapel()
        assertTrue("CRÉDITO (hipoteca, vehículo, libre inversión, libranza)" in prompt)
        assertTrue("lo que se DEBE, no un movimiento hecho" in prompt)
        assertTrue("todavía no se ha pagado es {\"tipo\":\"NADA\"}" in prompt)
        // Las reglas van después de la definición de NADA, que es la que afinan.
        assertTrue(prompt.indexOf("CRÉDITO (hipoteca") > prompt.indexOf("devuelve: {\"tipo\":\"NADA\"}"))
    }

    @Test
    fun `un desembolso es plata que entro`() {
        assertTrue("Un desembolso de crédito es plata que ENTRÓ al titular: \"movimiento\":\"INCOME\"" in ClaudeStatementParser.promptDelPapel())
        // Y lo que el modelo conteste así se lee como ingreso, no como el gasto de siempre.
        val dijo = queDiceLaRespuesta("""{"tipo":"COMPROBANTE","monto":200000000,"moneda":"COP","movimiento":"INCOME","fecha":"2026-08-19","comercio":"Bancolombia","concepto":"Desembolso crédito hipotecario"}""")
        assertEquals(TransactionType.INCOME, assertIs<QueDiceElPapel.Comprobante>(dijo).leido.tipo)
    }

    // ── Cuándo un PDF ya es un extracto ─────────────────────────────────────────

    @Test
    fun `cuatro filas con fecha y monto son un extracto`() {
        val extracto = (1..4).joinToString("\n") { "0$it/09/2026 COMPRA EXITO 45.000" }
        assertTrue(pareceUnExtracto(extracto))
        val comprobante = "Bancolombia\nTransferencia exitosa\n30/09/2026 Valor $250.000\nCuenta destino *0756"
        assertFalse(pareceUnExtracto(comprobante))
    }

    @Test
    fun `los formatos de fecha de los extractos reales tambien son un extracto`() {
        val listadoBancolombia = (1..4).joinToString("\n") { "0$it sept 2026 COMPRA EN EXITO -45.000,00" }
        val nu = (1..4).joinToString("\n") { "2$it AGO 2026 Rappi \$ 32.900" }
        val davibank = (1..4).joinToString("\n") { "2026/09/0$it;TRANSFERENCIA;1.250.000" }
        val davivienda = (1..4).joinToString("\n") { "2026082$it PAGO PSE 120.000,00" }
        val conPunto = (1..4).joinToString("\n") { "1$it oct. 2026 UBER 18.500" }
        listOf(listadoBancolombia, nu, davibank, davivienda, conPunto).forEach { assertTrue(pareceUnExtracto(it), it) }
    }

    @Test
    fun `un comprobante con una fecha larga sigue sin ser un extracto`() {
        val comprobante = "Nu\nPago exitoso\n21 AGO 2026 14:05\nValor \$ 115.000\nReferencia 20260821"
        assertFalse(pareceUnExtracto(comprobante))
        // Un mes que no es mes no cuenta como fecha.
        assertFalse(pareceUnExtracto((1..4).joinToString("\n") { "0$it xyz 2026 COMPRA 45.000" }))
    }

    @Test
    fun `la huella es la de los bytes`() {
        assertEquals(huellaDelPapel(byteArrayOf(1, 2, 3)), huellaDelPapel(byteArrayOf(1, 2, 3)))
        assertTrue(huellaDelPapel(byteArrayOf(1, 2, 3)) != huellaDelPapel(byteArrayOf(1, 2, 4)))
        assertEquals(64, huellaDelPapel(byteArrayOf()).length)
    }

    @Test
    fun `cada motivo cabe en lo que la app muestra`() {
        // La app muestra el cuerpo de un 4xx solo si tiene 200 caracteres o menos (`toUserMessage`):
        // un motivo más largo se perdería detrás de «Algo salió mal».
        val motivos = listOf(
            com.jvillada.movi.server.routes.PAPEL_SIN_LLAVE,
            com.jvillada.movi.server.routes.PAPEL_CON_CLAVE,
            com.jvillada.movi.server.routes.PAPEL_SIN_TEXTO,
            com.jvillada.movi.server.routes.PAPEL_SIN_MOVIMIENTO,
            com.jvillada.movi.server.routes.PAPEL_ILEGIBLE,
            com.jvillada.movi.server.routes.PAPEL_DE_OTRO_FORMATO,
            com.jvillada.movi.server.routes.PAPEL_IMAGEN_NO_SOPORTADA,
            com.jvillada.movi.server.routes.LECTURA_FALLO + " El archivo quedó guardado en Documentos.",
        )
        motivos.forEach { assertTrue(it.length <= 200, "${it.length} caracteres: $it") }
    }
}
