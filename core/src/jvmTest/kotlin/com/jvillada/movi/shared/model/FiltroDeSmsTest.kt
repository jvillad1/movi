package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 113 · **un SMS personal nunca sale del teléfono** ([seSubeElSms]) **ni se guarda desde un atajo
 * del iPhone** ([seGuardaElSmsDelAtajo]).
 */
class FiltroDeSmsTest {

    /** Los códigos de siempre (el piso del APK y lo que sirve el server). */
    private val codigos = listOf("85540", "891333", "87400")

    /** Copia de `PALABRAS_DE_BANCOS` del server (`SmsFilterConfigRoutes.kt`), que `:core` no ve. */
    private val palabras = listOf(
        "bancolombia", "nubank", "davivienda", "davibank", "bbva",
        "banco de bogota", "banco de bogotá", "av villas", "banco de occidente",
        "colpatria", "scotiabank", "itau", "itaú", "falabella", "lulo bank", "rappipay",
    )

    private fun sube(remitente: String?, cuerpo: String) = seSubeElSms(remitente, cuerpo, codigos, palabras)
    private fun atajo(remitente: String?, cuerpo: String) = seGuardaElSmsDelAtajo(remitente, cuerpo, codigos, palabras)

    private val conPalabra = "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133, el 10/10/2026 a las 09:12."
    private val sinPalabra = "Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133."

    @Test
    fun `la tabla de la regla del telefono`() {
        val casos = listOf(
            Triple("85540", sinPalabra, true), // código conocido sin palabra
            Triple("85540", conPalabra, true), // código conocido
            Triple("87100", conPalabra, true), // código corto desconocido + palabra
            Triple("8710", conPalabra, true), // de 4 dígitos
            Triple("Bancolombia", conPalabra, true), // alfanumérico + palabra
            Triple("DAVIVIENDA", "DAVIVIENDA Descuento en internet, \$64,900,Cta de Ahorros *0136", true),
            Triple("3001234567", conPalabra, false), // celular colombiano
            Triple("+57 300 123 4567", conPalabra, false),
            Triple("573001234567", conPalabra, false),
            Triple("+57 (300) 123-4567", conPalabra, false),
            Triple("6014567890", conPalabra, false), // fijo con indicativo
            Triple("+1 415 555 0100", conPalabra, false), // internacional largo
            Triple("+447911123456", conPalabra, false),
            Triple(null, conPalabra, false), // sin remitente: no pasa por la palabra
            Triple("", conPalabra, false),
            Triple("   ", conPalabra, false),
            Triple("3001234567", "hola, nos vemos a las 7", false), // personal sin palabra
            Triple("87100", "hola papá, ya llegué a la casa", false), // código desconocido sin palabra
            Triple("Claro", "Tu factura de Claro está disponible por \$63.900", false),
            Triple("3008554012", sinPalabra, false), // un celular que contiene «85540» no es el código
            Triple("+5787400", "aviso", true), // contiene 87400 y no es personal (7 dígitos)
            Triple(" 891 333 ", sinPalabra, true),
        )
        for ((remitente, cuerpo, esperado) in casos) {
            assertEquals(esperado, sube(remitente, cuerpo), "remitente «$remitente», cuerpo «$cuerpo»")
        }
    }

    @Test
    fun `que es un remitente personal`() {
        listOf("3001234567", "+57 300 123 4567", "573001234567", "(601) 456-7890", "+1 415 555 0100", "0034612345678")
            .forEach { assertTrue(esUnRemitentePersonal(it), it) }
        listOf("85540", "890077", "8710", "871", "123456789", "Bancolombia", "DAVIVIENDA", "InfoSMS", "Mamá", "", "  ")
            .forEach { assertFalse(esUnRemitentePersonal(it), it) }
        assertFalse(esUnRemitentePersonal(null))
    }

    /**
     * **La regresión de producción** (medida el 2-nov-2026, solo lectura): los 359 SMS de `sms_messages`
     * venían de estos 16 remitentes, todos códigos cortos. Con un texto que nombra un banco, pasan todos:
     * la regla nueva no habría perdido ninguno.
     */
    @Test
    fun `los 16 remitentes de produccion siguen pasando`() {
        val deProduccion = listOf(
            "85540", "85784", "87188", "890077", "890404", "85630", "87998", "85697",
            "87701", "899979", "890704", "891060", "891234", "890059", "891069", "899938",
        )
        assertEquals(16, deProduccion.toSet().size)
        for (remitente in deProduccion) {
            assertFalse(esUnRemitentePersonal(remitente), remitente)
            assertTrue(sube(remitente, conPalabra), "el teléfono dejaría de subir lo de $remitente")
            assertTrue(sube(remitente, "DAVIVIENDA Abono Transferencia ACH, \$419,745,Cta de Ahorros *0136"), remitente)
            assertTrue(atajo(remitente, conPalabra), "el atajo dejaría de guardar lo de $remitente")
        }
    }

    @Test
    fun `el atajo sin remitente guarda el aviso que empieza por el banco y trae un monto`() {
        assertTrue(atajo(null, conPalabra))
        assertTrue(atajo("", "  Bancolombia: Recibiste \$500.000 de tu cuenta *9586 en tu cuenta *8133"))
        assertFalse(atajo(null, "Hola, te pagué por Bancolombia")) // no empieza por el banco, sin monto
        assertFalse(atajo(null, "Hola, te pagué \$25.000 por Bancolombia")) // con monto pero no empieza por el banco
        assertFalse(atajo(null, "Bancolombia: tu clave fue actualizada.")) // empieza por el banco, sin monto
        assertFalse(atajo(null, "Bancolombia: Tu clave dinamica es 482913. Es personal e intransferible."))
        assertFalse(atajo(null, "Tu código de Uber es 1234"))
        assertFalse(atajo(null, "\$50.000 por Bancolombia, ¿listo?")) // empieza por el monto, no por el banco
    }

    @Test
    fun `empieza por el banco tolera un Banco o Banco de inicial`() {
        assertTrue(atajo(null, "Banco Falabella: Compraste \$120.000 en FALABELLA con tu tarjeta *1234"))
        assertTrue(atajo(null, "Banco AV Villas informa: retiro por \$200.000 de su cuenta *5678"))
        assertTrue(atajo(null, "Banco de Bogotá: Transferiste \$50.000 a la cuenta *4321"))
        assertTrue(atajo(null, "BANCO DAVIVIENDA Descuento en internet, \$64,900"))
        assertFalse(atajo(null, "Banco: Compraste \$5.000")) // «Banco» solo no es un banco de la lista
        assertFalse(atajo(null, "Banco del barrio, te debo \$5.000 de Bancolombia"))
        assertTrue(empiezaPorUnBanco("¡Banco Falabella te informa!", palabras))
    }

    @Test
    fun `el atajo con remitente`() {
        assertTrue(atajo("85540", conPalabra))
        assertTrue(atajo("85540", sinPalabra)) // el código basta, como en el teléfono
        assertTrue(atajo("87100", "Movimiento en tu cuenta de bancolombia por \$5.000")) // código corto + palabra
        assertFalse(atajo("3001234567", conPalabra)) // un celular: solo se cuenta
        assertFalse(atajo("+57 300 123 4567", conPalabra))
        // Un remitente con letras no prueba nada en el iPhone (puede ser un contacto): empieza por el banco + monto.
        assertFalse(atajo("Bancolombia", "Compraste \$5.000 en EXITO. bancolombia.com"))
        assertTrue(atajo("Bancolombia", conPalabra))
        assertFalse(atajo("Asesora Bancolombia", "Hola, soy tu asesora de Bancolombia, llámame"))
        assertFalse(atajo("Asesora Bancolombia", "Bancolombia: llámame, tengo una oferta para ti"))
        // Un contacto («Mamá»): el iPhone da su nombre, que no dice si es una persona. Se lee como sin remitente.
        assertTrue(atajo("Mamá", "DAVIVIENDA Descuento en internet, \$64,900,Cta de Ahorros *0136"))
        assertFalse(atajo("Mamá", "Ya te pagué por Bancolombia, \$25.000"))
        assertFalse(atajo("Tía Rosa", "Movimiento en tu cuenta nubank de \$5.000"))
    }

    /**
     * **Los avisos reales del repo empiezan por la marca.** Son los SMS de los fixtures (los de Bancolombia de
     * las pruebas del parser y de las vueltas de captura, y los tres de Davivienda del teléfono del papá del
     * dueño): todos los que son un movimiento empiezan por «Bancolombia:» o «DAVIVIENDA» y traen un monto,
     * así que un atajo sin remitente los guarda. Los correos («Notificación Transaccional Bancolombia: …»)
     * no pasan por acá: llegan por `POST /api/correo-entrante`.
     */
    @Test
    fun `los SMS reales de los fixtures entran por el atajo sin remitente`() {
        val reales = listOf(
            "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133, el 10/10/2026 a las 09:12.",
            "Bancolombia: Compraste \$80.000,00 en EXITO con tu T.Deb *8133, el 10/10/2026 a las 10:00.",
            "Bancolombia: Retiraste \$4.200.000 de tu cuenta *9586 Fiducuenta hacia la cuenta *029550681",
            "Bancolombia: Recibiste \$500.000 de tu cuenta *9586 en tu cuenta *8133 el 04/10/2026 a las 10:00",
            "Bancolombia: Pagaste \$386.902 en la tarjeta de credito *3684 desde la cuenta *8133 el 10/10/2026",
            "Bancolombia: Pagaste \$138,600.00 a Salud Total S A desde tu producto 3333 el 05/09/2026 17:00",
            "Bancolombia: Pagaste \$1,500,000.00 a NU Compania de Financiamiento desde tu producto 8133 el 31/10/2026",
            "Bancolombia: Hiciste un avance de \$6,200,000 en tu SUC VIRTUAL desde tu T.Credito *9208",
            "Bancolombia: ANA PEREZ pagaste \$18,500.00 por codigo QR desde tu cuenta *3333 a la llave 0092184713",
            "Bancolombia: Transferiste \$50,000.00 desde tu cuenta *8133 a la cuenta *31973270756 el 10/10/2026",
            "Bancolombia: Ups! Tu compra con Tarjeta Debito *4057 de \$ 116,600 fue rechazada por saldo insuficiente",
            "Bancolombia: Compraste COP86.900,00 en TIENDA LA 14 con tu T.Cred *3684, el 30/10/2026",
            "DAVIVIENDA Descuento en internet, \$64,900,Cta de Ahorros *0136,Hora 16:49,Lugar PSE MERCADOPAGO COLOMBIA.",
            "DAVIVIENDA Abono Transferencia ACH, \$419,745,Cta de Ahorros *0136,Hora 17:18,Lugar 0000000000000000000000000.",
            "DAVIVIENDA . Retiro . \$320,000,Cta de Ahorros *0136,Hora 12:04,Lugar OVIEDO.",
        )
        for (texto in reales) {
            assertTrue(empiezaPorUnBanco(texto, palabras), texto)
            assertTrue(traeUnMontoDelAviso(texto), texto)
            assertTrue(atajo(null, texto), texto)
        }
    }

    @Test
    fun `un codigo o una palabra vacia en la configuracion no abre la puerta`() {
        assertFalse(seSubeElSms("87100", "hola", listOf(""), listOf("")))
        assertFalse(seGuardaElSmsDelAtajo(null, "hola \$5.000", listOf(""), listOf("")))
    }

    /**
     * Vuelta 114 · lo que se descarta se cuenta con su motivo: lo que nombra un banco sin la forma de un aviso
     * (una promoción, una clave dinámica) no es «un mensaje que no era de un banco».
     */
    @Test
    fun `el motivo separa lo que nombra un banco sin forma de aviso de lo que no es de un banco`() {
        fun motivo(remitente: String?, texto: String) = porQueNoSeGuardaElSmsDelAtajo(remitente, texto, codigos, palabras)
        // Se guarda: sin motivo.
        assertNull(motivo(null, "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133"))
        assertNull(motivo("85540", "Transferiste \$50.000 desde tu cuenta *1234"))
        // Nombra un banco pero no tiene la forma de un aviso: una promoción sin monto, una clave, o un texto que
        // no empieza por el banco.
        assertEquals(MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO, motivo(null, "Bancolombia te invita a conocer tu nueva tarjeta. Pídela ya"))
        assertEquals(MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO, motivo(null, "Bancolombia: tu clave dinámica es 123456"))
        assertEquals(MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO, motivo(null, "Hola, ya te pagué por Bancolombia \$50.000"))
        assertEquals(MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO, motivo("Asesora Bancolombia", "Recuerda que Bancolombia te regala \$20.000"))
        // No nombra ningún banco, o lo manda una persona: no era de un banco.
        assertEquals(MotivoDelAtajoDescartado.NO_ES_DEL_BANCO, motivo(null, "Hola, ya llegué"))
        assertEquals(MotivoDelAtajoDescartado.NO_ES_DEL_BANCO, motivo("Mamá", "¿Vienes a comer?"))
        assertEquals(MotivoDelAtajoDescartado.NO_ES_DEL_BANCO, motivo("3001234567", "Bancolombia: Compraste \$5.000 en X"))
        assertEquals(MotivoDelAtajoDescartado.NO_ES_DEL_BANCO, motivo("12345", "Tu código de Uber es 1234"))
        // La regla de siempre no cambió: hay motivo exactamente cuando no se guarda.
        listOf(
            null to "Bancolombia: Compraste \$1.000,00 en PRUEBA",
            null to "Bancolombia te invita",
            "Mamá" to "hola",
            "891333" to "Transferiste \$5.000",
        ).forEach { (r, t) -> assertEquals(atajo(r, t), motivo(r, t) == null, t) }
    }
}
