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

    // ── Cuánto vale la hermana cuando se corrige una pata ──────────────────────

    /**
     * Corregir la pata del DINERO de una cuota: la hermana es la de la deuda y es la que guarda lo
     * que no amortiza.
     */
    private fun alCorregirLaCuota(montoViejo: Long, montoNuevo: Long, capital: Long, noAmortiza: Long?) =
        montoDeLaHermanaAlCorregir(
            montoViejo = montoViejo,
            montoNuevo = montoNuevo,
            montoDeLaHermana = capital,
            noAmortizaDeLaHermana = noAmortiza,
            noAmortizaDeLaPataQueSeCorrige = null,
        )

    @Test
    fun `en un par simetrico corregir una pata le da a la hermana el mismo monto`() {
        // Un traspaso, un pago de tarjeta, un pago de cuota anterior a que esto se guardara: las
        // dos mitades son la misma plata. La regla nueva tiene que dar exactamente lo que daba la
        // copia de antes — si no, rompe lo que ya funcionaba.
        assertEquals(
            1_500_000L,
            alCorregirLaCuota(montoViejo = 2_000_000L, montoNuevo = 1_500_000L, capital = 2_000_000L, noAmortiza = null),
        )
    }

    @Test
    fun `en la cuota de un credito la hermana se recalcula sobre el interes guardado`() {
        // Cuota de $4.215.223 de la que $1.733.905 abonaron a capital: los otros $2.481.318 son
        // interés del mes, un hecho ya ocurrido que no cambia porque él corrija lo que pagó.
        // Copiar el monto le habría borrado al crédito $2,7 millones que sigue debiendo.
        assertEquals(
            2_018_682L,
            alCorregirLaCuota(
                montoViejo = 4_215_223L,
                montoNuevo = 4_500_000L,
                capital = 1_733_905L,
                noAmortiza = 2_481_318L,
            ),
        )
    }

    @Test
    fun `bajar la cuota por debajo del interes no le SUBE la deuda`() {
        // El piso en cero: sin él, un monto negativo habría entrado como INCOME negativo a la
        // cuenta LOAN.
        assertEquals(
            0L,
            alCorregirLaCuota(
                montoViejo = 4_215_223L,
                montoNuevo = 1_000_000L,
                capital = 1_733_905L,
                noAmortiza = 2_481_318L,
            ),
        )
    }

    @Test
    fun `corregir hacia abajo y arrepentirse devuelve el capital EXACTO`() {
        // **El defecto que el interés guardado vino a cerrar.** La versión anterior deducía el
        // interés restando las dos patas, así que después de un clampeo a cero la resta mentía y
        // la vuelta atrás dejaba la deuda más baja de lo que estaba.
        //
        // Cuota del ·9695: $1.286.548, de los que $813.843 abonan a capital ($363.905 de interés +
        // $108.800 de seguro = $472.705 que no amortizan).
        val noAmortiza = 472_705L
        val bajada = alCorregirLaCuota(
            montoViejo = 1_286_548L, montoNuevo = 400_000L, capital = 813_843L, noAmortiza = noAmortiza,
        )
        assertEquals(0L, bajada, "400.000 no cubren 472.705 de interés y seguro: nada abona a capital")

        val devuelta = alCorregirLaCuota(
            montoViejo = 400_000L, montoNuevo = 1_286_548L, capital = bajada, noAmortiza = noAmortiza,
        )
        assertEquals(
            813_843L,
            devuelta,
            "tiene que volver al capital original; la regla de la diferencia daba 886.548 y " +
                "borraba \$72.705 de deuda en silencio",
        )
    }

    @Test
    fun `corregir hacia arriba un pago que era 100 por ciento interes no borra deuda`() {
        // El caso real que el KDoc del piso invocaba y que no tenía prueba: un pago PARCIAL de
        // $3.000.000 a la libranza ·4818, cuyo interés del mes es $3.646.011 — o sea capital 0.
        // Corregido después al valor real de la cuota, $6.040.259.
        val noAmortiza = 3_646_011L
        val corregido = alCorregirLaCuota(
            montoViejo = 3_000_000L, montoNuevo = 6_040_259L, capital = 0L, noAmortiza = noAmortiza,
        )

        assertEquals(
            2_394_248L,
            corregido,
            "6.040.259 − 3.646.011; la regla de la diferencia daba 3.040.259 y borraba \$646.011",
        )
    }

    @Test
    fun `corregir la pata de la DEUDA le suma a la hermana lo que no amortiza`() {
        // La otra dirección: acá `montoNuevo` es un capital y la hermana es la plata que salió de
        // la cuenta, que tiene que ser ese capital más el interés y el seguro de ese mes.
        assertEquals(
            4_500_000L,
            montoDeLaHermanaAlCorregir(
                montoViejo = 1_733_905L,
                montoNuevo = 2_018_682L,
                montoDeLaHermana = 4_215_223L,
                noAmortizaDeLaHermana = null,
                noAmortizaDeLaPataQueSeCorrige = 2_481_318L,
            ),
        )
    }

    @Test
    fun `el aviso de la hoja dice la verdad segun que clase de par sea`() {
        // «Para que la plata que sale sea la misma que entra» dejó de ser cierto en una cuota.
        assertEquals(MONTO_DE_UN_PAR_SE_MUEVE_JUNTO, avisoDeMontoDeUnPar(TRANSFER_CATEGORY))
        assertEquals(MONTO_DE_UN_PAR_SE_MUEVE_JUNTO, avisoDeMontoDeUnPar(CARD_PAYMENT_CATEGORY))
        assertEquals(MONTO_DE_UNA_CUOTA_SE_MUEVE_JUNTO, avisoDeMontoDeUnPar(CUOTA_CATEGORY))
    }
}
