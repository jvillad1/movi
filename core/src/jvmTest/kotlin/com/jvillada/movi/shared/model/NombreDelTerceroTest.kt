package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El nombre de un tercero en sus movimientos, una sola regla** (20-oct-2026). Cada caso es uno de
 * los que pidió el dueño o uno de los que nunca se pueden tocar. Ver `NombreDelTercero.kt`.
 */
class NombreDelTerceroTest {

    private val daniel = DestinoConocido(id = "dst_daniel", nombre = "Daniel Leonett", numero = "43087514791", creadoEn = 2L)
    private val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "", tipo = TipoDeTercero.PERSONA, creadoEn = 1L)
        .conIdentificadores(
            listOf(
                IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "31973270756"),
                IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "carolina restrepo salazar"),
            ),
        )
    private val parqueadero = DestinoConocido(
        id = "dst_parq", nombre = "Parqueadero Corpaul Lleras", numero = "", llave = "0088044519", tipo = TipoDeTercero.COMERCIO,
    ).conIdentificadores(listOf(IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "0088044519")))

    private fun ev(
        descripcion: String,
        tipo: TransactionType = TransactionType.EXPENSE,
        merchant: String? = descripcion,
        crudo: String? = null,
        id: String = "ev",
        transferId: String? = null,
        categoria: String = "Otros",
    ) = FinancialEvent(
        id = id, accountId = "acc", type = tipo, amount = 100_000L, category = categoria,
        description = descripcion, merchant = merchant, timestamp = 1_790_000_000_000L,
        rawPayload = crudo, transferId = transferId,
    )

    // ── Lo que se reescribe ─────────────────────────────────────────────────────

    @Test
    fun `el nombre del banco y la cuenta ilegible pasan al nombre del tercero`() {
        assertEquals("Transferencia a Daniel Leonett", nombreDelMovimientoParaElDestino(ev("DANIEL LEONETT"), daniel))
        assertEquals(
            "Transferencia a Daniel Leonett",
            nombreDelMovimientoParaElDestino(ev("Transferencia a la cuenta *43087514791"), daniel),
        )
    }

    @Test
    fun `lo que llego con el nombre que escribe el banco pasa a Transferencia de`() {
        val llego = ev("CAROLINA RESTREPO SALAZAR", tipo = TransactionType.INCOME)
        assertEquals("Transferencia de Caro", nombreDelMovimientoParaElDestino(llego, caro))
    }

    /**
     * «Recibido de Carolina»: «Carolina» es la primera palabra de «CAROLINA RESTREPO SALAZAR», el
     * nombre con que el banco nombra a Caro, y va con un prefijo de Movi. Se reescribe, y el
     * movimiento —sin texto del banco— es de Caro por su nombre. Si otro tercero también se llamara
     * Carolina, no es de ninguno.
     */
    @Test
    fun `Recibido de Carolina pasa a Transferencia de Caro, salvo que otra Carolina lo reclame`() {
        val recibido = ev("Recibido de Carolina", tipo = TransactionType.INCOME, merchant = null)
        assertEquals("Transferencia de Caro", nombreDelMovimientoParaElDestino(recibido, caro))
        assertEquals(caro, terceroDelMovimiento(recibido, listOf(caro, daniel)))
        val otra = DestinoConocido(id = "dst_otra", nombre = "Carolina Pérez", numero = "55500002222")
        assertNull(terceroDelMovimiento(recibido, listOf(caro, daniel, otra)), "dos lo reclaman: ninguno")
    }

    @Test
    fun `un prefijo repetido o con basura del mismo prefijo es de Movi, y un comercio va a secas`() {
        assertEquals(
            "Parqueadero Corpaul Lleras",
            nombreDelMovimientoParaElDestino(ev("TrTransferencia a Parqueadero Corpaul Lleras"), parqueadero),
        )
        assertEquals(
            "Parqueadero Corpaul Lleras",
            nombreDelMovimientoParaElDestino(ev("Transferencia a Transferencia a Parqueadero Corpaul Lleras"), parqueadero),
        )
        assertEquals("Parqueadero Corpaul Lleras", nombreDelMovimientoParaElDestino(ev("Pago QR · llave 0088044519"), parqueadero))
        assertNull(nombreDelMovimientoParaElDestino(ev("Parqueadero Corpaul Lleras"), parqueadero), "ya dice lo que tiene que decir")
    }

    @Test
    fun `la nota que escribio el dueño despues del punto se conserva tal cual`() {
        assertEquals(
            "Transferencia a Daniel Leonett · arreglos Gardenera",
            nombreDelMovimientoParaElDestino(ev("DANIEL LEONETT · arreglos Gardenera"), daniel),
        )
        assertEquals(
            "Transferencia a Caro · arreglos Gardenera",
            nombreDelMovimientoParaElDestino(ev("Transferencia a la cuenta *31973270756 · arreglos Gardenera"), caro),
        )
    }

    @Test
    fun `el nombre del propio tercero en otra forma se pone en la forma de siempre`() {
        assertEquals("Transferencia a Caro", nombreDelMovimientoParaElDestino(ev("CARO"), caro))
        assertEquals("Transferencia a Caro", nombreDelMovimientoParaElDestino(ev("transferencia a caro"), caro))
        assertEquals("Transferencia de Caro", nombreDelMovimientoParaElDestino(ev("Transferencia a Caro", tipo = TransactionType.INCOME), caro))
        assertNull(nombreDelMovimientoParaElDestino(ev("Transferencia a Caro"), caro))
    }

    @Test
    fun `renombrar al tercero reescribe Transferencia a NombreViejo`() {
        val renombrada = caro.copy(nombre = "Carolina", nombresAnteriores = listOf("Caro"))
        val viejo = ev("Transferencia a Caro", merchant = null)
        assertEquals("Transferencia a Carolina", nombreDelMovimientoParaElDestino(viejo, renombrada))
        // Sin texto del banco, el movimiento sigue siendo suyo por el nombre viejo.
        assertEquals(
            listOf(NombreNuevoDelMovimiento("ev", "Transferencia a Carolina", null)),
            movimientosParaElNombreDelTercero(renombrada, listOf(viejo), listOf(renombrada, daniel)),
        )
    }

    // ── Lo que nunca se toca ────────────────────────────────────────────────────

    @Test
    fun `lo que escribio el dueño no se toca`() {
        assertNull(nombreDelMovimientoParaElDestino(ev("Abono de Caro a la AMEX · Wompi-PSE"), caro))
        assertNull(nombreDelMovimientoParaElDestino(ev("Partido DIM y arreglo de llanta Caro"), caro))
        assertNull(nombreDelMovimientoParaElDestino(ev("Mercado"), caro))
        // Un nombre a medias sin prefijo pudo escribirlo él; uno corto con prefijo es demasiado común.
        assertNull(nombreDelMovimientoParaElDestino(ev("Carolina"), caro))
        val juan = DestinoConocido(id = "dst_juan", nombre = "Papá", numero = "", llave = "juan guillermo prueba")
        assertNull(nombreDelMovimientoParaElDestino(ev("Pago a Juan"), juan), "«Juan» tiene menos de $MIN_LETRAS_DE_UN_NOMBRE_PARCIAL letras")
    }

    @Test
    fun `las patas de traspaso y de cuota, y los anulados, no se tocan`() {
        val pata = ev("Transferencia a la cuenta *31973270756", transferId = "tr-1")
        assertNull(nombreDelMovimientoParaElDestino(pata, caro))
        assertNull(terceroDelMovimiento(pata, listOf(caro)))
        assertNull(nombreDelMovimientoParaElDestino(ev("Transferencia a la cuenta *31973270756", categoria = TRANSFER_CATEGORY), caro))
        val anulado = ev("Transferencia a la cuenta *31973270756", id = "anulado")
        val vivo = ev("Transferencia a la cuenta *31973270756", id = "vivo")
        val cambios = movimientosParaElNombreDelTercero(caro, listOf(pata, anulado, vivo), listOf(caro), anulados = setOf("anulado"))
        assertEquals(listOf("vivo"), cambios.map { it.id })
    }

    @Test
    fun `el nombre del banco cambia, salvo un numero o una llave, que son el rastro del banco`() {
        val conNumero = ev("Transferencia a la cuenta *31973270756")
        assertNull(merchantDelMovimientoParaElDestino(conNumero, caro), "el número se queda: es la huella de la memoria")
        val conNombre = ev("DANIEL LEONETT")
        assertEquals("Transferencia a Daniel Leonett", merchantDelMovimientoParaElDestino(conNombre, daniel))
        // Y la memoria sigue viendo la misma huella antes y después.
        assertEquals(huellaDeUnMovimiento("DANIEL LEONETT"), huellaDeUnMovimiento("Transferencia a Daniel Leonett"))
        assertEquals(huellaDeUnMovimiento("Transferencia a Caro"), huellaDeUnMovimiento("Transferencia de Caro"))
    }

    @Test
    fun `un movimiento que el banco liga a dos terceros no es de ninguno`() {
        val ambiguo = ev(
            "Transferencia",
            crudo = "Transferiste \$1 desde tu cuenta *9999 a la cuenta *31973270756 y a la cuenta *43087514791",
        )
        assertNull(terceroDelMovimiento(ambiguo, listOf(caro, daniel)))
        assertTrue(movimientosParaElNombreDelTercero(caro, listOf(ambiguo), listOf(caro, daniel)).isEmpty())
    }

    @Test
    fun `la señal del banco gana sobre el nombre`() {
        // El texto del banco dice la cuenta de Caro; el concepto nombra a Daniel. Es de Caro, y como
        // «Transferencia a Daniel Leonett» no es un nombre de Caro, no se toca.
        val e = ev("Transferencia a Daniel Leonett", crudo = "Transferiste \$1 desde tu cuenta *9999 a la cuenta *31973270756")
        assertEquals(caro, terceroDelMovimiento(e, listOf(caro, daniel)))
        assertTrue(movimientosParaElNombreDelTercero(caro, listOf(e), listOf(caro, daniel)).isEmpty())
    }

    // ── Avisos nuevos: la misma regla ───────────────────────────────────────────

    @Test
    fun `un aviso nuevo usa la misma regla, y un pago por QR a un tercero sin tipo va a secas`() {
        val sinTipo = parqueadero.copy(tipo = null)
        val texto = "Bancolombia: pagaste \$9.000 por codigo QR desde tu cuenta *9999 a la llave 0088044519."
        val leido = ParsedSms(9_000.0, "Pago QR · llave 0088044519", TransactionType.EXPENSE, "Otros")
        assertEquals("Parqueadero Corpaul Lleras", conElDestinoConocido(leido, texto, listOf(sinTipo)).merchant)

        val breB = "JUAN, transferiste \$30.000 a la llave @dl desde tu cuenta *9999 a DANIEL LEONETT el 01/10/26 a las 10:00."
        val conLlave = daniel.conIdentificadores(daniel.todosLosIdentificadores() + IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "@dl"))
        val delBanco = ParsedSms(30_000.0, "DANIEL LEONETT", TransactionType.EXPENSE, "Otros")
        assertEquals("Transferencia a Daniel Leonett", conElDestinoConocido(delBanco, breB, listOf(conLlave)).merchant)
        assertEquals("Mercado", conElDestinoConocido(delBanco.copy(merchant = "Mercado"), breB, listOf(conLlave)).merchant)
    }

    // ── Los nombres anteriores ──────────────────────────────────────────────────

    @Test
    fun `los nombres anteriores no se repiten, no incluyen el de hoy y tienen tope`() {
        assertEquals(listOf("Caro"), nombresAnterioresCon(emptyList(), listOf("Caro"), nombreDeHoy = "Carolina"))
        assertEquals(listOf("CARO"), nombresAnterioresCon(listOf("Caro"), listOf("CARO"), nombreDeHoy = "Carolina"), "uno solo, el más nuevo")
        assertEquals(emptyList(), nombresAnterioresCon(listOf("Carolina"), emptyList(), nombreDeHoy = "carolina"))
        val muchos = (1..15).map { "Nombre $it" }
        assertEquals(MAX_NOMBRES_ANTERIORES, nombresAnterioresCon(muchos, emptyList(), "Hoy").size)
        assertEquals("Nombre 15", nombresAnterioresCon(muchos, emptyList(), "Hoy").last(), "se quedan los más nuevos")
    }

    @Test
    fun `unir deja el nombre de la que se va entre los anteriores de la que queda`() {
        val otra = DestinoConocido(id = "dst_otra", nombre = "Carolina Restrepo Salazar", numero = "", llave = "carolina restrepo salazar")
        val unida = unirTerceros(seVa = otra, queda = caro.copy(llave = null).conIdentificadores(listOf(IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "31973270756"))))
        assertEquals(listOf("Carolina Restrepo Salazar"), unida.nombresAnteriores)
        assertEquals("Transferencia de Caro", nombreDelMovimientoParaElDestino(ev("Transferencia de Carolina Restrepo Salazar", tipo = TransactionType.INCOME), unida))
    }
}
