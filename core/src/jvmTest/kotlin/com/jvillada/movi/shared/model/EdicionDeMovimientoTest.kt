package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Las guardas de **corregir un movimiento ya anotado**, que es la única definición que tienen: el
 * server (`PUT /api/events/{id}`) y el espejo local (`LocalRepository.updateEvent`) las llaman a
 * las dos y no reimplementan ninguna. Si estas pruebas se caen, se cae el rechazo en las dos
 * caras a la vez, que es exactamente lo que se busca.
 */
class EdicionDeMovimientoTest {

    private val ahorros = Account("acc-ahorros", "Bancolombia", AccountType.SAVINGS, 0L, "COP")
    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 0L, "COP")
    private val enDolares = Account("acc-usd", "Ahorros USD", AccountType.SAVINGS, 0L, "USD")
    private val tarjeta = Account("acc-amex", "AMEX", AccountType.CREDIT_CARD, 0L, "COP")
    private val credito = Account("acc-carro", "Vehículo", AccountType.LOAN, 0L, "COP")

    private fun evento(
        amount: Long = 1_000_000L,
        accountId: String = ahorros.id,
        category: String = "Otros",
        type: TransactionType = TransactionType.EXPENSE,
        transferId: String? = null,
    ) = FinancialEvent(
        id = "ev-hija",
        accountId = accountId,
        type = type,
        amount = amount,
        currency = "COP",
        category = category,
        description = "Hija",
        timestamp = 1_788_000_000_000L,
        transferId = transferId,
    )

    private fun validar(
        cambios: EdicionDeMovimiento,
        esPataDeUnPar: Boolean = false,
        cuentaActualId: String = ahorros.id,
        moneda: String = "COP",
        cuentaNueva: Account? = null,
    ) = validarEdicionDeMovimiento(cambios, esPataDeUnPar, cuentaActualId, moneda, cuentaNueva)

    // ── Lo que el dueño pidió: cambiar el monto y la cuenta ────────────────────

    @Test
    fun `cambiar el monto y la cuenta a otra propia en la misma moneda se acepta`() {
        val rechazo = validar(
            EdicionDeMovimiento(amount = 3_000_000L, accountId = nu.id, description = "Hija"),
            cuentaNueva = nu,
        )
        assertNull(rechazo, "es exactamente el caso que el dueño pidió")
    }

    @Test
    fun `mandar la MISMA cuenta no cuenta como cambiar de cuenta`() {
        // La hoja manda los tres campos siempre. Sin esta distinción, corregirle el monto a una
        // pata rebotaría con PATA_NO_CAMBIA_DE_CUENTA por una cuenta que nadie tocó.
        val rechazo = validar(
            EdicionDeMovimiento(amount = 3_000_000L, accountId = ahorros.id),
            esPataDeUnPar = true,
            cuentaNueva = ahorros,
        )
        assertNull(rechazo)
    }

    // ── Los rechazos ──────────────────────────────────────────────────────────

    @Test
    fun `un monto de cero o negativo no es plata`() {
        assertEquals(RechazoDeEdicion(400, MONTO_INVALIDO), validar(EdicionDeMovimiento(amount = 0L)))
        assertEquals(RechazoDeEdicion(400, MONTO_INVALIDO), validar(EdicionDeMovimiento(amount = -5L)))
    }

    @Test
    fun `un monto absurdo se rechaza en vez de guardarse`() {
        assertEquals(
            RechazoDeEdicion(400, MONTO_DEMASIADO_GRANDE),
            validar(EdicionDeMovimiento(amount = MONTO_MAXIMO + 1)),
        )
        assertNull(validar(EdicionDeMovimiento(amount = MONTO_MAXIMO)), "el tope es inclusivo")
    }

    @Test
    fun `un concepto en blanco deja el renglon sin nada que decir`() {
        assertEquals(RechazoDeEdicion(400, CONCEPTO_VACIO), validar(EdicionDeMovimiento(description = "   ")))
    }

    @Test
    fun `un concepto mas largo que la columna se rechaza antes de llegar a la base`() {
        val largo = "a".repeat(MAX_CONCEPTO_LENGTH + 1)
        assertEquals(
            RechazoDeEdicion(400, CONCEPTO_DEMASIADO_LARGO),
            validar(EdicionDeMovimiento(description = largo)),
        )
        assertNull(validar(EdicionDeMovimiento(description = "a".repeat(MAX_CONCEPTO_LENGTH))))
    }

    @Test
    fun `una pata de un par no puede cambiar de cuenta`() {
        val rechazo = validar(
            EdicionDeMovimiento(accountId = nu.id),
            esPataDeUnPar = true,
            cuentaNueva = nu,
        )
        assertEquals(422, rechazo?.status)
        assertEquals(PATA_NO_CAMBIA_DE_CUENTA, rechazo?.mensaje)
    }

    @Test
    fun `una cuenta que no es del dueno responde que no existe, no que no puede`() {
        // El aislamiento entre usuarios se dice como «no existe» en todo este server: contestar
        // «no puedes» confirmaría que esa cuenta existe y es de alguien.
        val rechazo = validar(EdicionDeMovimiento(accountId = "acc-de-otro"), cuentaNueva = null)
        assertEquals(RechazoDeEdicion(404, CUENTA_NO_ENCONTRADA), rechazo)
    }

    @Test
    fun `un movimiento en pesos no se muda a una cuenta en dolares`() {
        val rechazo = validar(EdicionDeMovimiento(accountId = enDolares.id), cuentaNueva = enDolares)
        assertEquals(422, rechazo?.status)
        assertEquals(mensajeDeMonedaDistinta("USD", "COP"), rechazo?.mensaje)
    }

    // ── soloLoQueCambia: guardar sin cambiar nada no escribe nada ──────────────

    @Test
    fun `lo que llega igual a lo guardado se descarta campo por campo`() {
        val evento = evento(amount = 1_000_000L)
        val cambios = soloLoQueCambia(
            evento,
            EdicionDeMovimiento(amount = 1_000_000L, accountId = ahorros.id, description = "Hija"),
        )
        assertNull(cambios.amount)
        assertNull(cambios.accountId)
        assertNull(cambios.description)
        assertTrue(!cambios.tieneCambios())
    }

    @Test
    fun `el concepto se compara ya recortado, asi un espacio de mas no es un cambio`() {
        val cambios = soloLoQueCambia(evento(), EdicionDeMovimiento(description = "  Hija  "))
        assertNull(cambios.description)
    }

    @Test
    fun `lo que si cambia sobrevive, y el concepto queda recortado`() {
        val cambios = soloLoQueCambia(
            evento(amount = 1_000_000L),
            EdicionDeMovimiento(amount = 3_000_000L, accountId = nu.id, description = "  Hija Nu  "),
        )
        assertEquals(3_000_000L, cambios.amount)
        assertEquals(nu.id, cambios.accountId)
        assertEquals("Hija Nu", cambios.description)
        assertTrue(cambios.tieneCambios())
    }

    // ── El aviso de que la cuenta nueva cambia si el movimiento cuenta en el mes ──

    @Test
    fun `mover un gasto a un credito lo saca de los gastos del mes, y se avisa`() {
        val aviso = avisoDeCambioDeCuenta(evento(), AccountType.SAVINGS, AccountType.LOAN)
        assertTrue(aviso != null && "deja de contar" in aviso, "aviso real: $aviso")
        assertTrue(aviso!!.contains("gastos"), "un EXPENSE habla de gastos: $aviso")
    }

    @Test
    fun `mover un ingreso a un credito habla de ingresos, no de gastos`() {
        val ingreso = evento(type = TransactionType.INCOME, category = "Salario")
        val aviso = avisoDeCambioDeCuenta(ingreso, AccountType.SAVINGS, AccountType.LOAN)
        assertTrue(aviso != null && "ingresos" in aviso, "aviso real: $aviso")
    }

    @Test
    fun `traer un gasto de vuelta desde un credito lo devuelve al mes, y tambien se avisa`() {
        val aviso = avisoDeCambioDeCuenta(evento(accountId = credito.id), AccountType.LOAN, AccountType.SAVINGS)
        assertTrue(aviso != null && "pasa a contar" in aviso, "aviso real: $aviso")
    }

    @Test
    fun `mover un gasto de una cuenta de ahorros a una tarjeta NO cambia si cuenta`() {
        // En CREDIT_CARD la compra sigue siendo gasto del mes (ver isCashFlow), así que no hay
        // nada que avisar: un aviso de más sobre algo que no cambia enseña a ignorarlos.
        assertNull(avisoDeCambioDeCuenta(evento(), AccountType.SAVINGS, AccountType.CREDIT_CARD))
    }

    @Test
    fun `sin tipo de cuenta conocido no se inventa ningun aviso`() {
        assertNull(avisoDeCambioDeCuenta(evento(), null, AccountType.LOAN))
        assertNull(avisoDeCambioDeCuenta(evento(), AccountType.SAVINGS, null))
    }

    @Test
    fun `una categoria reservada ya estaba fuera del mes, asi que mudarla no cambia nada`() {
        // «Pago de tarjeta» queda fuera por NOMBRE, sin importar el tipo de cuenta: los dos lados
        // dan false y no hay aviso. Sin este caso, la comparación por tipo de cuenta habría
        // anunciado un cambio que no ocurre.
        val pago = evento(category = CARD_PAYMENT_CATEGORY)
        assertNull(avisoDeCambioDeCuenta(pago, AccountType.SAVINGS, AccountType.LOAN))
    }

    @Test
    fun `la tarjeta no aparece en este test por casualidad`() {
        // Guarda de cordura del fixture: si `tarjeta` dejara de ser CREDIT_CARD, el test de
        // arriba pasaría por el motivo equivocado.
        assertEquals(AccountType.CREDIT_CARD, tarjeta.type)
    }
}
