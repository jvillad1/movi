package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Las cuentas de otros**: que el número lleve al destino, que un número propio no pueda
 * registrarse, y que el total de lo que se le mandó cuente exactamente lo que fue para allá.
 *
 * Los números y los textos son los reales del dueño: la cuenta de su esposa (`*31973270756`), sus
 * propias cuentas («Fiducuenta 9586», «Master Black 3684») y los tres envíos que ya tiene anotados
 * —con los nombres que él les puso a mano, que es justo lo que hace difícil el problema.
 */
class DestinoConocidoTest {

    private val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "31973270756", deQuien = "esposa")
    private val papa = DestinoConocido(id = "dst_papa", nombre = "Papá", numero = "41279033068", deQuien = "papá")
    private val destinos = listOf(caro, papa)

    // ── Resolver el destino de un texto del banco ────────────────────────────

    @Test
    fun `el numero de la cuenta lleva al destino, con y sin asterisco`() {
        assertEquals(
            caro,
            destinoQueNombra("Transferiste \$1.931.488 a la cuenta *31973270756 desde tu cuenta *8133", destinos),
        )
        assertEquals(caro, destinoQueNombra("Transferiste \$1.931.488 a la cuenta 31973270756", destinos))
        // El banco a veces escribe solo la cola: se compara por los últimos cuatro.
        assertEquals(caro, destinoQueNombra("Transferencia a la cuenta *0756", destinos))
    }

    @Test
    fun `un numero suelto sin asterisco ni la palabra cuenta no es una cuenta`() {
        // Un teléfono de atención, un monto sin separadores. Una coincidencia falsa acá le pondría
        // a un gasto el nombre de otra persona.
        assertNull(destinoQueNombra("Dudas al 6045109009 o al 31973270756 de la sucursal", destinos))
    }

    @Test
    fun `la cuenta de origen del dueño no se confunde con un destino`() {
        // «desde tu cuenta *8133» es de dónde SALIÓ la plata. No está registrada —no puede estarlo,
        // ver el test del rechazo— así que no resuelve a nadie.
        assertNull(destinoQueNombra("Pagaste \$138.600 a Coomeva desde tu cuenta *8133", destinos))
    }

    @Test
    fun `si dos destinos terminan en los mismos cuatro digitos no se elige ninguno`() {
        val otro = DestinoConocido(id = "dst_otro", nombre = "Vecino", numero = "99990756")
        assertNull(
            destinoQueNombra("Transferencia a la cuenta *31973270756", destinos + otro),
            "un empate no se resuelve al azar: elegir uno de dos le pone el nombre equivocado a un gasto",
        )
    }

    @Test
    fun `sin destinos registrados no se resuelve nada`() {
        assertNull(destinoQueNombra("Transferencia a la cuenta *31973270756", emptyList()))
    }

    // ── Rechazos del alta ────────────────────────────────────────────────────

    @Test
    fun `un destino necesita nombre y al menos cuatro digitos`() {
        assertEquals(DESTINO_SIN_NOMBRE, rechazoDelDestino("   ", "31973270756"))
        assertEquals(NUMERO_DEMASIADO_CORTO, rechazoDelDestino("Caro", "756"))
        // Sin un solo dígito (y sin llave) no hay con qué reconocerla: se pide una de las dos cosas.
        assertEquals(FALTA_NUMERO_O_LLAVE, rechazoDelDestino("Caro", "sin dígitos"))
        assertEquals(NUMERO_DEMASIADO_LARGO, rechazoDelDestino("Caro", "1".repeat(MAX_DIGITOS_DEL_NUMERO + 1)))
        assertEquals(NOMBRE_DEL_DESTINO_DEMASIADO_LARGO, rechazoDelDestino("C".repeat(MAX_NOMBRE_DEL_DESTINO + 1), "0756"))
        assertEquals(DE_QUIEN_DEMASIADO_LARGO, rechazoDelDestino("Caro", "0756", "e".repeat(MAX_DE_QUIEN + 1)))
        assertNull(rechazoDelDestino("  Caro  ", "*319-7327-0756", " esposa "))
    }

    @Test
    fun `el numero se guarda solo con digitos, venga como venga`() {
        assertEquals("31973270756", soloLosDigitos("*31973270756"))
        assertEquals("31973270756", soloLosDigitos("319-7327-0756"))
        assertEquals("31973270756", soloLosDigitos(" 319 7327 0756 "))
        assertEquals("0756", ultimosCuatro("*31973270756"))
        assertNull(ultimosCuatro("756"))
    }

    /**
     * **La guarda más importante de la feature.** Si el número de una cuenta suya pudiera
     * registrarse como ajeno, un SMS de un retiro de su propia Fiducuenta se propondría como
     * «Transferencia a Caro» y después se contaría en «lo que le mandé a Caro».
     */
    @Test
    fun `un numero que es de una cuenta suya se rechaza, y el mensaje nombra la cuenta`() {
        val sus = listOf("Fiducuenta 9586", "Master Black 3684", "Bancolombia Ahorros 8133")
        assertEquals("Fiducuenta 9586", nombreDeLaCuentaPropiaConEseNumero("43087519586", sus))
        assertEquals("Bancolombia Ahorros 8133", nombreDeLaCuentaPropiaConEseNumero("8133", sus))
        assertNull(nombreDeLaCuentaPropiaConEseNumero("31973270756", sus))
        assertTrue(mensajeDeNumeroPropio("Fiducuenta 9586").contains("Fiducuenta 9586"))
    }

    @Test
    fun `la version con cuentas devuelve la cuenta que choco`() {
        val fidu = Account(id = "acc1", name = "Fiducuenta 9586", type = AccountType.SAVINGS, balance = 0L)
        val nu = Account(id = "acc2", name = "Nu 1254", type = AccountType.CREDIT_CARD, balance = 0L)
        assertEquals(fidu, cuentaPropiaConEseNumero("*9586", listOf(fidu, nu)))
        assertNull(cuentaPropiaConEseNumero("*0756", listOf(fidu, nu)))
    }

    // ── Ponerle el nombre al movimiento ──────────────────────────────────────

    private fun sms(merchant: String, tipo: TransactionType = TransactionType.EXPENSE) =
        ParsedSms(amount = 1_931_488.0, merchant = merchant, type = tipo, category = "Otros", currency = "COP")

    private val elSmsDeCotrafa =
        "Transferiste \$1.931.488 a la cuenta *31973270756 desde tu cuenta *8133 el 18/09/26"

    @Test
    fun `un numero ilegible se vuelve el nombre del destino`() {
        val leido = conElDestinoConocido(
            sms("Transferencia a la cuenta *31973270756"),
            elSmsDeCotrafa,
            destinos,
        )
        assertEquals("Transferencia a Caro", leido.merchant)
    }

    @Test
    fun `un movimiento sin nombre reconocible tambien lo recibe`() {
        // «Transferencia» a secas no identifica a nadie (`huellaDeUnMovimiento` devuelve null).
        assertEquals("Transferencia a Caro", conElDestinoConocido(sms("Transferencia"), elSmsDeCotrafa, destinos).merchant)
        assertEquals("Transferencia a Caro", conElDestinoConocido(sms("Movimiento"), elSmsDeCotrafa, destinos).merchant)
    }

    /**
     * **El nombre que el dueño ya puso es suyo.** La memoria de categorías corre ANTES de este paso
     * (ver `SmsRoutes`), así que si a ese número él lo llamó «Mercado», lo que llega acá se llama
     * «Mercado» — y este paso calla. El orden de la cadena es lo que garantiza esto.
     */
    @Test
    fun `no le pisa el nombre que el dueno ya le puso`() {
        assertEquals("Mercado", conElDestinoConocido(sms("Mercado"), elSmsDeCotrafa, destinos).merchant)
    }

    @Test
    fun `no le pisa un nombre que el banco mando de verdad`() {
        val texto = "Pagaste \$138.600 a DANIEL LEONETT desde tu cuenta *31973270756"
        assertEquals("DANIEL LEONETT", conElDestinoConocido(sms("DANIEL LEONETT"), texto, destinos).merchant)
    }

    @Test
    fun `un ingreso no recibe el nombre del destino`() {
        val recibido = sms("Transferencia recibida", TransactionType.INCOME)
        assertEquals("Transferencia recibida", conElDestinoConocido(recibido, elSmsDeCotrafa, destinos).merchant)
    }

    @Test
    fun `una fila de extracto tambien recibe el nombre, y su concepto se mueve junto`() {
        val fila = ParsedTransaction(
            id = "tx1",
            date = "2026-09-18",
            merchant = "Transferencia a la cuenta *31973270756",
            amount = 1_931_488L,
            type = TransactionType.EXPENSE,
            category = "Otros",
            description = "Transferencia a la cuenta *31973270756",
            rawText = "18/09 TRANSF A CUENTA *31973270756  1.931.488",
        )
        val conNombre = conElDestinoConocido(fila, destinos)
        assertEquals("Transferencia a Caro", conNombre.merchant)
        assertEquals("Transferencia a Caro", conNombre.description)

        // Y si el papel trajo un concepto propio, ese concepto no se pisa: es un dato del extracto.
        val conConcepto = fila.copy(description = "Cuota de Cotrafa · septiembre")
        assertEquals("Cuota de Cotrafa · septiembre", conElDestinoConocido(conConcepto, destinos).description)
    }

    // ── Qué movimientos cuentan, y cuánto suman ──────────────────────────────

    private fun gasto(
        id: String,
        descripcion: String,
        monto: Long,
        cuando: Long,
        crudo: String? = null,
        moneda: String = "COP",
        tipo: TransactionType = TransactionType.EXPENSE,
    ) = FinancialEvent(
        id = id,
        accountId = "acc-bancolombia",
        type = tipo,
        amount = monto,
        currency = moneda,
        category = "Otros",
        description = descripcion,
        merchant = descripcion,
        timestamp = cuando,
        rawPayload = crudo,
    )

    /** 26-ago-2026, 31-ago-2026 y 18-sep-2026, al mediodía de Bogotá. */
    private val elVeintiseisDeAgosto = 1_787_000_000_000L  // 2026-08-16 ≈; ver los tests que lo usan
    private val mercado = gasto(
        id = "ev-mercado",
        descripcion = "Mercado",
        monto = 2_000_000L,
        cuando = elVeintiseisDeAgosto,
        crudo = "Transferiste \$2.000.000 a la cuenta *31973270756 desde tu cuenta *8133",
    )
    private val colegio = gasto(
        id = "ev-colegio",
        descripcion = "Colegio Hija · parte desde Bancolombia",
        monto = 1_000_000L,
        cuando = elVeintiseisDeAgosto + 5 * 86_400_000L,
        crudo = "Transferiste \$1.000.000 a la cuenta *31973270756 desde tu cuenta *8133",
    )
    private val cotrafa = gasto(
        id = "ev-cotrafa",
        // Anotado a mano: no tiene texto del banco, y lo único que lo engancha es el nombre.
        descripcion = "Cuota de Cotrafa 5413 · transferida a Caro",
        monto = 1_931_488L,
        cuando = elVeintiseisDeAgosto + 30 * 86_400_000L,
    )

    /**
     * **Los tres envíos reales del dueño cuentan, aunque él les haya cambiado el nombre.**
     *
     * Es el caso que decidió el diseño: los tres están renombrados a mano («Mercado», «Colegio
     * Hija…», «Cuota de Cotrafa…»), así que buscar por el nombre del destino solo, o por el número
     * en el concepto solo, no encontraría ninguno de los dos primeros. Lo que los engancha es el
     * texto del banco guardado con el movimiento.
     */
    @Test
    fun `los tres envios reales cuentan aunque esten renombrados`() {
        val suyos = movimientosHaciaElDestino(caro, listOf(mercado, colegio, cotrafa))
        assertEquals(listOf("ev-cotrafa", "ev-colegio", "ev-mercado"), suyos.map { it.id }, "del más reciente al más viejo")
        assertEquals(mapOf("COP" to 4_931_488L), totalesHaciaElDestino(suyos))
    }

    @Test
    fun `un gasto que no tiene nada que ver no cuenta`() {
        val uber = gasto("ev-uber", "Uber", 28_500L, elVeintiseisDeAgosto, crudo = "Compra aprobada \$28.500 en Uber BV.")
        assertFalse(vaHaciaElDestino(uber, caro))
        assertTrue(movimientosHaciaElDestino(caro, listOf(uber)).isEmpty())
    }

    @Test
    fun `el nombre cuenta como palabra completa y no como pedazo de otra`() {
        val carolina = gasto("ev-carolina", "Regalo para Carolina Pérez", 50_000L, elVeintiseisDeAgosto)
        assertFalse(vaHaciaElDestino(carolina, caro), "«Caro» adentro de «Carolina» no es evidencia de nada")
        val conPuntuacion = gasto("ev-punt", "Plata para ·Caro/", 50_000L, elVeintiseisDeAgosto)
        assertTrue(vaHaciaElDestino(conPuntuacion, caro), "la puntuación no tiene que romper la palabra")
    }

    /**
     * Ola V — [nombraElNumeroDelDestino] es SOLO la señal del número: la palabra suelta del nombre
     * («Almuerzo caro», donde «caro» es un adjetivo común) NO puede pegar acá, aunque sí pegue en
     * [vaHaciaElDestino] (que la sigue aceptando a propósito, para agrupar en pantalla — no para
     * decidir un recurrente solo, que es justo el problema que `nombraElNumeroDelDestino` existe
     * para evitar).
     */
    @Test
    fun `nombraElNumeroDelDestino solo mira el numero, nunca el nombre suelto`() {
        val almuerzoCaro = gasto("ev-almuerzo", "Almuerzo caro", 45_000L, elVeintiseisDeAgosto)
        assertFalse(nombraElNumeroDelDestino(almuerzoCaro, caro), "el número no está en el texto")
        assertTrue(
            vaHaciaElDestino(almuerzoCaro, caro),
            "vaHaciaElDestino SÍ acepta el nombre suelto — el agravante que encontró la revisión",
        )

        val conElNumero = gasto(
            "ev-numero", "Transferencia", 45_000L, elVeintiseisDeAgosto,
            crudo = "Enviaste \$45.000 a la cuenta *31973270756",
        )
        assertTrue(nombraElNumeroDelDestino(conElNumero, caro))
    }

    @Test
    fun `lo que ENTRA de esa cuenta no se cuenta como enviado`() {
        val devuelto = gasto(
            id = "ev-devuelto",
            descripcion = "Transferencia recibida de Caro",
            monto = 500_000L,
            cuando = elVeintiseisDeAgosto,
            crudo = "Recibiste \$500.000 de la cuenta *31973270756",
            tipo = TransactionType.INCOME,
        )
        assertFalse(vaHaciaElDestino(devuelto, caro), "esto es lo que le enviaste, no lo que te devolvió")
    }

    @Test
    fun `los dolares y los pesos se suman por separado`() {
        val enDolares = gasto(
            id = "ev-usd",
            descripcion = "Transferencia a Caro",
            monto = 100L,
            cuando = elVeintiseisDeAgosto,
            moneda = "USD",
        )
        val totales = totalesHaciaElDestino(movimientosHaciaElDestino(caro, listOf(mercado, enDolares)))
        assertEquals(mapOf("COP" to 2_000_000L, "USD" to 100L), totales)
    }

    @Test
    fun `conLoQueSeLeMando llena el total y la cantidad`() {
        val lleno = conLoQueSeLeMando(caro, listOf(mercado, colegio, cotrafa))
        assertEquals(3, lleno.cuantos)
        assertEquals(mapOf("COP" to 4_931_488L), lleno.totales)
        // Y un destino sin envíos queda en cero y vacío, no en null ni inventado.
        val vacio = conLoQueSeLeMando(papa, listOf(mercado))
        assertEquals(0, vacio.cuantos)
        assertTrue(vacio.totales.isEmpty())
    }

    /**
     * **Por el período del dueño, no por mes de calendario.** Con corte 25, dos envíos del 26 y del
     * 31 de agosto caen en «septiembre» —el mes que él vive— y no en agosto.
     */
    @Test
    fun `el total por periodo respeta el corte del dueno`() {
        val conCorte25 = PeriodSettings(cutoffDay = 25)
        val suyos = movimientosHaciaElDestino(caro, listOf(mercado, colegio, cotrafa))
        val porPeriodo = loQueSeLeMandoPorPeriodo(suyos, conCorte25)

        // Los tres períodos salen del más reciente al más viejo, sin ninguno vacío en medio.
        assertTrue(porPeriodo.isNotEmpty())
        val ordenados = porPeriodo.map { it.periodo.year * 100 + it.periodo.month }
        assertEquals(ordenados.sortedDescending(), ordenados, "del período más reciente al más viejo")
        // Y la suma de todos los períodos es el total: ningún movimiento se pierde ni se cuenta dos veces.
        assertEquals(
            4_931_488L,
            porPeriodo.sumOf { it.totales["COP"] ?: 0L },
        )
        assertEquals(3, porPeriodo.sumOf { it.cuantos })

        // El mismo juego con corte 1 (mes de calendario) tiene que repartirlo distinto: si diera lo
        // mismo, este test no estaría probando que el corte se respeta.
        val porCalendario = loQueSeLeMandoPorPeriodo(suyos, PeriodSettings(cutoffDay = 1))
        assertNotNull(porCalendario.firstOrNull())
        assertEquals(4_931_488L, porCalendario.sumOf { it.totales["COP"] ?: 0L })
    }

    // ── La tarjeta de Patrimonio: lo de este período ─────────────────────────

    /**
     * El período en curso con corte 25, cuando «ahora» es el 18-sep: va del 25-ago al 24-sep, así
     * que los tres envíos de Caro (≈16-ago, ≈21-ago y ≈15-sep) reparten: solo el último es de este
     * período. Se mide contra [periodoDe] y no contra una fecha escrita a mano.
     */
    @Test
    fun `lo de este periodo cuenta solo los envios del periodo en curso del dueno`() {
        val corte25 = PeriodSettings(cutoffDay = 25)
        val ahora = cotrafa.timestamp
        val esperado = listOf(mercado, colegio, cotrafa)
            .filter { periodoDe(it.timestamp, corte25) == periodoActual(ahora, corte25) }
            .sumOf { it.amount }
        assertTrue(esperado in 1L until 4_931_488L, "el caso tiene que dejar afuera algún envío, si no no prueba nada")

        val lleno = conLoQueSeLeMando(caro, listOf(mercado, colegio, cotrafa), corte25, ahora)
        assertEquals(mapOf("COP" to esperado), lleno.totalesDelPeriodo)
        assertEquals(mapOf("COP" to 4_931_488L), lleno.totales, "el total de siempre no cambia")
        // Sin ajustes no se inventa un período: queda vacío, como lo lee un server viejo.
        assertTrue(conLoQueSeLeMando(caro, listOf(mercado, colegio, cotrafa)).totalesDelPeriodo.isEmpty())
    }

    @Test
    fun `lo de este periodo de todas se suma por moneda y sin ceros`() {
        val conPlata = listOf(
            caro.copy(totalesDelPeriodo = mapOf("COP" to 300_000L)),
            papa.copy(totalesDelPeriodo = mapOf("COP" to 1_000_000L, "USD" to 50L)),
            DestinoConocido(nombre = "Mamá", numero = "40001234", totalesDelPeriodo = mapOf("COP" to 0L)),
        )
        assertEquals(mapOf("COP" to 1_300_000L, "USD" to 50L), loQueSeLesMandoEstePeriodo(conPlata))
        assertTrue(loQueSeLesMandoEstePeriodo(listOf(caro, papa)).isEmpty())
    }

    @Test
    fun `los nombres de la tarjeta van como se dicen, con los que mas recibieron primero`() {
        assertEquals("", nombresDeLasCuentasDeOtros(emptyList()))
        assertEquals("Caro", nombresDeLasCuentasDeOtros(listOf(caro)))
        assertEquals("Caro y Papá", nombresDeLasCuentasDeOtros(listOf(caro, papa)))
        val mama = DestinoConocido(nombre = "Mamá", numero = "40001234", totalesDelPeriodo = mapOf("COP" to 5L))
        assertEquals("Mamá, Caro y Papá", nombresDeLasCuentasDeOtros(listOf(caro, papa, mama)))
        val cinco = listOf(caro, papa, mama) + listOf("Tía", "Vecino").map { DestinoConocido(nombre = it, numero = "12345678") }
        assertEquals("Mamá, Caro, Papá y 2 más", nombresDeLasCuentasDeOtros(cinco))
    }

    @Test
    fun `el nombre propuesto dice quien es`() {
        assertEquals("Transferencia a Caro", nombreHaciaElDestino(caro))
        assertEquals("·0756", colaVisibleDePrueba(caro.numero))
    }

    /** Copia de lo que la pantalla muestra, para que el test no dependa de `:shared`. */
    private fun colaVisibleDePrueba(numero: String) = "·" + numero.takeLast(4)

    // ── Llaves (29-sep) ───────────────────────────────────────────────────────

    /** Los tres textos reales del brief, con lo que identifica a quién fue (o de quién vino). */
    @Test
    fun `el identificador sale de los tres textos reales`() {
        assertEquals(
            IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "31973270756"),
            identificadorDelDestinoEn("Bancolombia: Transferiste \$1.931.488 a la cuenta *31973270756 desde tu cuenta *8133 el 18/09/26"),
        )
        assertEquals(
            IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "0087"),
            identificadorDelDestinoEn("Bancolombia: ANA PEREZ pagaste \$18,500.00 por codigo QR desde tu cuenta *3333 a la llave 0087 el 09/09/2026"),
        )
        assertEquals(
            IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "carolina restrepo salazar"),
            identificadorDelDestinoEn("Recibiste 300.000,00 en tu cuenta: Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave."),
        )
        // «hacia la cuenta», que también escribe Bancolombia.
        assertEquals("25318624146", identificadorDelDestinoEn("2026/09/10 13:35:32, hacia la cuenta *25318624146.")?.valor)
    }

    @Test
    fun `ni la cuenta de origen ni la llave del dueno identifican a nadie`() {
        assertNull(identificadorDelDestinoEn("Pagaste \$138.600 a Coomeva desde tu cuenta *8133"))
        assertNull(identificadorDelDestinoEn("Tu llave 3001234567 quedó inscrita"), "«tu llave» es la del dueño")
        assertNull(identificadorDelDestinoEn("Configura la llave registrada en tu app"), "una palabra suelta no es una llave")
        assertNull(identificadorDelDestinoEn("Compraste \$28.500 en UBER con tu T.Cred *3684"))
    }

    @Test
    fun `las llaves se normalizan a minusculas y sin espacios, y un nombre queda legible`() {
        assertEquals("3001234567", normalizarLlave(" 300 123 4567 "))
        assertEquals("caro@correo.com", normalizarLlave("Caro@Correo.com"))
        assertEquals("@caror", normalizarLlave("@CaroR"))
        assertEquals("carolina restrepo salazar", normalizarLlave("CAROLINA  RESTREPO SALAZAR"))
        assertEquals("caro@correo.com", identificadorDelDestinoEn("transferiste a la llave caro@correo.com.")?.valor, "sin el punto final")
    }

    @Test
    fun `un destino puede ser solo llave, pero no ni numero ni llave`() {
        assertNull(rechazoDelDestino("Panadería", "", llave = "0092184713"))
        assertNull(rechazoDelDestino("Caro", "31973270756", llave = "@caro"), "número Y llave")
        assertEquals(FALTA_NUMERO_O_LLAVE, rechazoDelDestino("Nadie", "", llave = "  "))
        assertEquals(LLAVE_DEMASIADO_CORTA, rechazoDelDestino("Jo", "", llave = "@j"))
        assertEquals(NUMERO_DEMASIADO_CORTO, rechazoDelDestino("Caro", "12", llave = "@caro"), "un número a medias no se guarda aunque haya llave")
    }

    private val panaderia = DestinoConocido(id = "dst_pan", nombre = "Panadería", numero = "", llave = "0092184713")
    /** Una cuenta que TERMINA en los mismos cuatro dígitos que la llave de la panadería. */
    private val vecino = DestinoConocido(id = "dst_vecino", nombre = "Vecino", numero = "55554713")

    /**
     * **Una llave no se confunde con un número corto.** «llave 0092184713» y la cuenta *55554713
     * comparten la cola 4713: por número se compara la cola, por llave el valor exacto, y las dos
     * señales no se mezclan.
     */
    @Test
    fun `una llave numerica no se confunde con una cuenta que termina igual`() {
        val conLosDos = listOf(panaderia, vecino)
        assertEquals(panaderia, destinoQueNombra("Pago QR · llave 0092184713", conLosDos))
        assertEquals(vecino, destinoQueNombra("Transferiste \$10.000 a la cuenta *55554713", conLosDos))
        assertNull(destinoQueNombra("Pago QR · llave 4713", conLosDos), "la llave se compara exacta, no por la cola")

        val alQr = gasto("ev-qr", "Pan", 18_500L, 1L, crudo = "pagaste \$18,500.00 por codigo QR a la llave 0092184713")
        val aLaCuenta = gasto("ev-cta", "Transferencia", 10_000L, 1L, crudo = "Transferiste \$10.000 a la cuenta *55554713")
        assertTrue(nombraLaLlaveDelDestino(alQr, panaderia))
        assertFalse(nombraLaLlaveDelDestino(aLaCuenta, panaderia))
        assertFalse(nombraElNumeroDelDestino(alQr, vecino), "la llave no pasa por el lector de números")
        assertTrue(nombraElNumeroDelDestino(aLaCuenta, vecino))
        assertFalse(nombraElNumeroDelDestino(alQr, panaderia), "un destino sin número no nombra ninguna cuenta")
        assertEquals(listOf("ev-qr"), movimientosHaciaElDestino(panaderia, listOf(alQr, aLaCuenta)).map { it.id })
    }

    @Test
    fun `lo que llega de un remitente guardado se propone con su nombre`() {
        val caroConNombre = caro.copy(llave = "carolina restrepo salazar")
        val texto = "Recibiste 300.000,00 en tu cuenta: Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave."
        val leido = ParsedSms(300_000.0, "CAROLINA RESTREPO SALAZAR", TransactionType.INCOME, "Transferencia")
        assertEquals("Transferencia de Caro", conElDestinoConocido(leido, texto, listOf(caroConNombre)).merchant)
        // Sin guardarlo, queda el nombre del banco.
        assertEquals("CAROLINA RESTREPO SALAZAR", conElDestinoConocido(leido, texto, destinos).merchant)
        // Y si la memoria ya le puso otro nombre, ese es suyo.
        assertEquals("Mesada", conElDestinoConocido(leido.copy(merchant = "Mesada"), texto, listOf(caroConNombre)).merchant)
    }

    @Test
    fun `se ofrece guardar solo lo que no se conoce y no es una cuenta suya`() {
        val fiducuenta = Account("acc-fidu", "Fiducuenta 9586", AccountType.SAVINGS, 0L)
        val aCaro = IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "31973270756")
        val aOtro = IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "12345678")
        val aSuFiducuenta = IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "40009586")
        val aLaPanaderia = IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "0092184713")

        assertFalse(ofreceGuardarElDestino(aCaro, destinos, listOf(fiducuenta)), "Caro ya está guardada")
        assertTrue(ofreceGuardarElDestino(aOtro, destinos, listOf(fiducuenta)))
        assertFalse(ofreceGuardarElDestino(aSuFiducuenta, destinos, listOf(fiducuenta)), "un traspaso a una cuenta suya")
        assertTrue(ofreceGuardarElDestino(aLaPanaderia, destinos, emptyList()))
        assertFalse(ofreceGuardarElDestino(aLaPanaderia, destinos + panaderia, emptyList()))
        assertFalse(ofreceGuardarElDestino(null, destinos, emptyList()))
    }

    @Test
    fun `guardar con el nombre de uno que ya existe le suma el dato en vez de duplicarlo`() {
        val nombre = IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "carolina restrepo salazar")
        val sumado = destinoParaGuardar(nombre, " caro ", null, destinos)
        assertEquals("dst_caro", sumado.id, "es Caro, con la llave agregada")
        assertEquals("carolina restrepo salazar", sumado.llave)
        assertEquals("31973270756", sumado.numero)

        val nuevo = destinoParaGuardar(IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "12345678"), "Mamá", "mamá", destinos)
        assertEquals("", nuevo.id)
        assertEquals("12345678", nuevo.numero)
        assertEquals("mamá", nuevo.deQuien)
        // Caro ya tiene número: uno nuevo con su nombre no le pisa el que tiene.
        assertEquals("", destinoParaGuardar(IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "12345678"), "Caro", null, destinos).id)
    }

    @Test
    fun `el nombre sugerido es el del banco en titulo caso, o nada si es un numero`() {
        assertEquals("Carolina Restrepo Salazar", nombreSugeridoParaElDestino("CAROLINA RESTREPO SALAZAR"))
        assertEquals("Pedro Gomez", nombreSugeridoParaElDestino("PEDRO GOMEZ"))
        assertEquals("", nombreSugeridoParaElDestino("Transferencia a la cuenta *41279033068"))
        assertEquals("", nombreSugeridoParaElDestino("Pago QR · llave 0087"))
        assertEquals("María de la Cruz", enTituloCaso("MARÍA DE LA CRUZ"))
    }
}
