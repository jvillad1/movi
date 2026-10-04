package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Lo que Movi encuentra solo** (4-oct-2026). Textos sintéticos con la forma de los avisos de
 * Bancolombia y Nu; ningún número ni nombre es del dueño.
 */
class TercerosSugeridosTest {

    private val dia = 86_400_000L
    private val ahora = 1_790_000_000_000L

    private val lu = DestinoConocido(id = "dst_lu", nombre = "Luci", numero = "", deQuien = "esposa")
        .conIdentificadores(listOf(IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "55500001111")))
    private val gol = DestinoConocido(id = "dst_gol", nombre = "Cancha", numero = "", llave = "3001112222")

    private fun aviso(texto: String, monto: Long, tipo: TransactionType = TransactionType.EXPENSE, haceDias: Int = 1, moneda: String = "COP") =
        rastroDeUnAviso(texto, monto, moneda, tipo, ahora - haceDias * dia)

    private fun movimiento(id: String, descripcion: String, monto: Long, haceDias: Int = 1, raw: String? = null, tipo: TransactionType = TransactionType.EXPENSE) =
        rastroDeUnMovimiento(
            FinancialEvent(
                id = id, accountId = "a", type = tipo, amount = monto, category = "Otros",
                description = descripcion, merchant = descripcion, timestamp = ahora - haceDias * dia, rawPayload = raw,
            ),
        )

    private fun sugeridos(
        rastros: List<RastroDeTercero?>,
        destinos: List<DestinoConocido> = listOf(lu, gol),
        cuentas: List<String> = listOf("Ahorros 9999", "Fiducia 4444"),
        textos: List<String> = emptyList(),
        descartados: Set<String> = emptySet(),
    ) = destinosSugeridos(
        rastros = rastros.filterNotNull(),
        destinos = destinos,
        nombresDeCuentas = cuentas,
        colasPropias = colasPropiasEn(textos),
        nombresPropios = nombresPropiosEn(textos),
        descartados = descartados,
        ahora = ahora,
    )

    private fun transferenciaA(cuenta: String, monto: String = "\$100.000") =
        "Banco: Transferiste $monto desde tu cuenta *9999 a la cuenta *$cuenta el 01/09/2026 a las 10:00."

    private fun qr(llave: String) =
        "Banco: PEDRO PABLO PEREZ pagaste \$20.000 por codigo QR desde tu cuenta *9999 a la llave $llave el 01/09/2026 a las 12:00."

    @Test
    fun `junta lo que no es de nadie conocido, con veces, total y la ultima`() {
        val lista = sugeridos(
            listOf(
                aviso(transferenciaA("66600003333"), 100_000, haceDias = 3),
                aviso(transferenciaA("66600003333"), 250_000, haceDias = 40),
                aviso(transferenciaA("55500001111"), 90_000), // es de Lu, guardada
            ),
        )
        val s = lista.single()
        assertEquals(IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "66600003333"), s.identificador)
        assertEquals(2, s.veces)
        assertEquals(mapOf("COP" to 350_000L), s.enviado)
        assertEquals(ahora - 3 * dia, s.ultimo)
        assertEquals(TipoDeTercero.PERSONA, s.tipo)
    }

    @Test
    fun `excluye las cuentas propias, por el nombre de la cuenta o porque el banco dice que es tuya`() {
        val propioPorNombre = transferenciaA("12304444")
        val propioPorElBanco = "Banco: Retiraste \$500.000 de tu cuenta *4444 Fiducia el 2026/09/18 10:45:19, hacia la cuenta *00011119999."
        val lista = sugeridos(
            listOf(aviso(propioPorNombre, 1_000), aviso(propioPorNombre, 2_000), aviso(propioPorElBanco, 500_000)),
            cuentas = listOf("Fiducia 4444", "Ahorros"),
            textos = listOf(transferenciaA("123"), propioPorElBanco),
        )
        assertTrue(lista.isEmpty(), "ninguna es de un tercero: $lista")
    }

    @Test
    fun `excluye el nombre del dueño como lo escribe su banco`() {
        val deSuOtraCuenta = "Banco: PEDRO, recibiste una transferencia de Pedro Pablo Perez por \$300.000 en tu cuenta *9999 el 23/08/26."
        val lista = sugeridos(listOf(aviso(deSuOtraCuenta, 300_000, TransactionType.INCOME)), textos = listOf(qr("0011"), deSuOtraCuenta))
        assertTrue(lista.isEmpty(), "Pedro Pablo Perez es el dueño: $lista")
    }

    @Test
    fun `excluye lo ignorado y lo que dijo que es suyo`() {
        val id = IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "66600003333")
        val rastros = listOf(aviso(transferenciaA("66600003333"), 1_000), aviso(transferenciaA("66600003333"), 2_000))
        assertEquals(1, sugeridos(rastros).size)
        assertTrue(sugeridos(rastros, descartados = setOf(id.clave)).isEmpty())
    }

    @Test
    fun `el nombre propuesto es el del banco, en titulo caso`() {
        val texto = "Banco: PEDRO, transferiste \$40.000 a la llave marta.r@correo.com desde tu cuenta *9999 a MARTA RUIZ ORTIZ el 14/09/26 a las 21:18."
        val s = sugeridos(listOf(aviso(texto, 40_000))).single()
        assertEquals("Marta Ruiz Ortiz", s.nombrePropuesto)
        assertEquals(OrigenDelNombre.BANCO, s.origenDelNombre)
        assertEquals(IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "marta.r@correo.com"), s.identificador)
        assertEquals(
            listOf(IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "marta ruiz ortiz")),
            s.otrosIdentificadores,
            "el nombre que acompaña a la llave se guarda junto",
        )
    }

    @Test
    fun `sin nombre del banco, el que el dueño le puso al movimiento`() {
        val s = sugeridos(
            listOf(
                aviso(qr("0099887766"), 20_000, haceDias = 10),
                movimiento("e1", "Empanadas - Pago QR (llave 0099887766)", 25_000, haceDias = 5),
            ),
        ).single()
        assertEquals("Empanadas", s.nombrePropuesto)
        assertEquals(OrigenDelNombre.TUYO, s.origenDelNombre)
        assertEquals(TipoDeTercero.COMERCIO, s.tipo, "un pago por QR es un comercio")
        assertEquals(2, s.veces)
    }

    @Test
    fun `un aviso y el movimiento que se anoto con el cuentan una vez`() {
        val texto = transferenciaA("66600003333", "\$1.300.000")
        val s = sugeridos(
            listOf(
                aviso(texto, 1_300_000, haceDias = 2),
                movimiento("e1", "Transferencia a la cuenta *66600003333", 1_300_000, haceDias = 2, raw = texto),
                // El mismo pago avisado otra vez a los 5 minutos (el correo después del SMS).
                rastroDeUnAviso(texto, 1_300_000, "COP", TransactionType.EXPENSE, ahora - 2 * dia + 5 * 60_000),
            ),
        ).single()
        assertEquals(1, s.veces)
        assertEquals(mapOf("COP" to 1_300_000L), s.enviado)
        assertEquals(1, s.renombrables, "«Transferencia a la cuenta *…» es ilegible y se puede renombrar")
    }

    @Test
    fun `uno solo y viejo no entra, uno solo y reciente si`() {
        assertTrue(sugeridos(listOf(aviso(transferenciaA("66600003333"), 1_000, haceDias = 90))).isEmpty())
        assertEquals(1, sugeridos(listOf(aviso(transferenciaA("66600003333"), 1_000, haceDias = 10))).size)
    }

    @Test
    fun `lo que llego de alguien que se parece a un guardado pregunta si es esa persona`() {
        val texto = "Recibiste 200.000,00 en tu cuenta: Te llegó dinero de LUCIANA MORA DIAZ con tu llave."
        val s = sugeridos(listOf(aviso(texto, 200_000, TransactionType.INCOME))).single()
        assertEquals(IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "luciana mora diaz"), s.identificador)
        assertEquals(mapOf("COP" to 200_000L), s.recibido)
        assertEquals("dst_lu", s.pareceDe, "«Luci» es el comienzo de «Luciana»: ¿Es Luci?")
        assertEquals("Luci", s.pareceDeNombre)
    }

    @Test
    fun `parecerse a dos no es parecerse a ninguno`() {
        val luz = DestinoConocido(id = "dst_luz", nombre = "Lucia", numero = "77711112222")
        assertNull(pareceDe(listOf("Luciana Mora Diaz"), listOf(lu, luz)))
        assertNull(pareceDe(listOf("Mariana Gil"), listOf(DestinoConocido(id = "x", nombre = "Ana", numero = "1234"))))
    }

    @Test
    fun `los pagos de tarjeta y los traspasos propios no son terceros`() {
        assertNull(rastroDeUnAviso(transferenciaA("66600003333"), 1_000, "COP", TransactionType.EXPENSE, ahora, esPagoDeTarjeta = true))
        val pata = FinancialEvent(
            id = "t", accountId = "a", type = TransactionType.EXPENSE, amount = 1_000, category = "Traspaso",
            description = "Transferencia a la cuenta *66600003333", timestamp = ahora, transferId = "tr1",
        )
        assertNull(rastroDeUnMovimiento(pata))
    }

    @Test
    fun `que nombre puso el dueño y cual es ilegible`() {
        assertEquals("Arepas", nombreQueLePusoElDueno("Arepas - Pago QR (llave 0092184713)"))
        assertEquals("Marta Ruiz", nombreQueLePusoElDueno("Transferencia a Marta Ruiz"))
        assertNull(nombreQueLePusoElDueno("Pago QR · llave 0043980168"))
        assertNull(nombreQueLePusoElDueno("Transferencia a la cuenta *41279033068"))
        assertTrue(esUnNombreIlegible("Transferencia a la cuenta *41279033068"))
        assertTrue(esUnNombreIlegible("Pago QR · llave 0047142708"))
        assertTrue(!esUnNombreIlegible("Arepas - Pago QR (llave 0092184713)"), "lo escribió el dueño: es suyo")
        assertTrue(!esUnNombreIlegible("Transferencia a Caro"))
    }
}
