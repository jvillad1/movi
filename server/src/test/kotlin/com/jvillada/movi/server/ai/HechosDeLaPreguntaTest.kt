package com.jvillada.movi.server.ai

import com.jvillada.movi.server.routes.DatosDelUsuario
import com.jvillada.movi.server.routes.comoContexto
import com.jvillada.movi.server.routes.toMessageParam
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_INMUEBLE
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.patrimonioDe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Los datos exactos para ESTA pregunta, con los nombres reales del dueño
 *
 * El enlazado se prueba con los nombres que el dueño de verdad usa —«Hipotecario 2334», «Skandia
 * pensión voluntaria», «Mercado extra», «Casa Almendros de Zúñiga»— porque es contra esos que tiene
 * que acertar: un enlace equivocado le pone al modelo los datos de otra cosa como si fueran «los
 * exactos», que es peor que no ponerle nada.
 *
 * Y cierra con **la regresión del 23-sep**: el turno completo (contexto + hechos) de «¿Por qué
 * Hipotecario 2334 no baja aunque pago la cuota?» y la respuesta que dio el modelo esa mañana.
 */
class HechosDeLaPreguntaTest {

    // ── Los datos del dueño, en memoria ──────────────────────────────────────────

    private val h2334 = CreditoParaContexto(
        accountId = "h2334", cuenta = "Hipotecario 2334", banco = "Davibank", tasaEa = 15.24,
        cuota = 2_613_714L, plazoMeses = 180, dia = 5, seguroMensual = 209_219L, porNomina = false,
        loPaga = "Skandia", loPagaCuentaPropia = "Skandia pensión voluntaria", saldo = 204_183_376L,
    )
    private val h1254 = CreditoParaContexto(
        accountId = "h1254", cuenta = "Hipoteca 1254", banco = "Davibank", tasaEa = 11.0,
        cuota = 9_147_408L, plazoMeses = 180, dia = 5, seguroMensual = null, porNomina = false,
        loPaga = "Skandia", loPagaCuentaPropia = "Skandia pensión voluntaria", saldo = 400_000_000L,
    )
    private val v8761 = CreditoParaContexto(
        accountId = "v8761", cuenta = "Vehículo 8761", banco = "Occidente", tasaEa = 18.5,
        cuota = 1_500_000L, plazoMeses = 60, dia = 12, seguroMensual = null, porNomina = false,
        loPaga = null, otrosCargosMensuales = 25_000L, saldo = 40_000_000L,
    )

    private val cuentas = listOf(
        Account(id = "bc", name = "Bancolombia Ahorros", type = AccountType.SAVINGS, balance = 3_200_000L),
        Account(
            id = "skandia", name = "Skandia pensión voluntaria", type = AccountType.INVESTMENT,
            balance = 106_000_000L, condicionadaA = "pensión voluntaria",
        ),
        Account(id = "mb", name = "Master Black 3684", type = AccountType.CREDIT_CARD, balance = 3_500_000L),
        Account(id = "h2334", name = "Hipotecario 2334", type = AccountType.LOAN, balance = 204_183_376L),
        Account(id = "h1254", name = "Hipoteca 1254", type = AccountType.LOAN, balance = 400_000_000L),
        Account(id = "v8761", name = "Vehículo 8761", type = AccountType.LOAN, balance = 40_000_000L),
        Account(
            id = "casa", name = "Casa Almendros de Zúñiga", type = AccountType.INVESTMENT, balance = 0L,
            bien = Bien(clase = CLASE_DE_BIEN_INMUEBLE, valor = 1_411_903_920L, valorAl = "2026-08-28", deudaId = "h1254"),
        ),
    )

    private val periodo = ContextoDelPeriodo(
        rango = "25 de agosto al 24 de septiembre",
        diasQueQuedan = 1,
        corte = 25,
        ingresos = 14_000_000L,
        gastoPorCategoria = mapOf(
            "Comida" to 1_161_535L, "Fútbol" to 963_456L, "Mercado" to 600_000L, "Mercado extra" to 250_000L,
        ),
        recurrentes = listOf(
            RecurrenteParaContexto("Cancha semanal", "Fútbol", 120_000L, 20, esIngreso = false, yaOcurrioEnElPeriodo = false),
        ),
        creditos = listOf(h2334, h1254, v8761),
        suscripciones = emptyList(),
        metas = emptyList(),
        smsPorConfirmar = 0,
        movimientosPorConfirmar = 0,
        gastos = listOf(
            GastoDelPeriodo("2026-09-02", "Éxito", "Comida", "Master Black 3684", 410_000L),
            GastoDelPeriodo("2026-09-10", "Rappi", "Comida", "Bancolombia Ahorros", 95_000L),
            GastoDelPeriodo("2026-09-14", "Carulla", "Comida", "Master Black 3684", 380_000L),
            GastoDelPeriodo("2026-09-18", "Crepes", "Comida", "Bancolombia Ahorros", 276_535L),
            GastoDelPeriodo("2026-09-06", "Torneo", "Fútbol", "Bancolombia Ahorros", 700_000L),
            GastoDelPeriodo("2026-09-13", "Guayos", "Fútbol", "Master Black 3684", 263_456L),
        ),
    )

    private val presupuestos = listOf("Fútbol" to 400_000L, "Comida" to 1_500_000L)

    private val datos = DatosDelUsuario(
        cuentas = cuentas,
        patrimonio = patrimonioDe(cuentas),
        ingresos = 14_000_000L,
        egresos = 2_975_000L,
        periodo = periodo,
        presupuestos = presupuestos,
        cuantosDocumentos = 0,
    )

    private fun hechos(pregunta: String) = hechosParaLaPregunta(pregunta, datos.paraLosHechos())

    // ── El enlazado ───────────────────────────────────────────────────────────

    private val nombresDelDueno = listOf(
        "Hipotecario 2334", "Hipoteca 1254", "Vehículo 8761", "Master Black 3684", "Skandia pensión voluntaria",
        "Bancolombia Ahorros", "Comida", "Fútbol", "Mercado", "Mercado extra", "Casa Almendros de Zúñiga",
    )

    private fun enlaza(pregunta: String) = queNombraLaPregunta(pregunta, nombresDelDueno)

    @Test
    fun `el nombre entero enlaza sin importar tildes ni mayusculas`() {
        assertEquals(listOf("Hipotecario 2334"), enlaza("¿Por qué Hipotecario 2334 no baja aunque pago la cuota?"))
        assertEquals(listOf("Hipotecario 2334"), enlaza("HIPOTECARIO 2334"))
        assertEquals(listOf("Fútbol"), enlaza("¿y cuánto llevo en futbol?"))
        assertEquals(listOf("Comida"), enlaza("¿Cuánto gasté en COMIDA?"))
        assertEquals(listOf("Casa Almendros de Zúñiga"), enlaza("¿cuánto vale la casa almendros de zuniga?"))
        assertEquals(listOf("Master Black 3684"), enlaza("¿cuánto debo en la Master Black 3684?"))
    }

    @Test
    fun `un numero de la cuenta o una palabra que solo ese nombre tiene tambien enlaza`() {
        assertEquals(listOf("Hipotecario 2334"), enlaza("¿cuánto debo del 2334?"))
        assertEquals(listOf("Master Black 3684"), enlaza("¿qué compré con la tarjeta 3684?"))
        assertEquals(listOf("Skandia pensión voluntaria"), enlaza("¿cuánto tengo en Skandia?"))
        assertEquals(listOf("Casa Almendros de Zúñiga"), enlaza("¿cuánto vale la casa de Almendros?"))
        assertEquals(listOf("Vehículo 8761"), enlaza("¿cuánto me falta del vehiculo?"))
    }

    @Test
    fun `el nombre mas largo gana sobre el que lleva adentro`() {
        assertEquals(listOf("Mercado extra"), enlaza("¿cuánto llevo en Mercado extra?"))
        assertEquals(listOf("Mercado"), enlaza("¿cuánto llevo en mercado?"))
    }

    @Test
    fun `varias cosas nombradas salen en el orden de la pregunta`() {
        assertEquals(listOf("Hipoteca 1254", "Hipotecario 2334"), enlaza("¿abono al 1254 o al 2334?"))
    }

    /** Adivinar sería peor que no enlazar: el contexto general ya trae todo. */
    @Test
    fun `lo que no se nombra no se adivina`() {
        assertEquals(emptyList(), enlaza("¿qué hipoteca pago primero?"))
        assertEquals(emptyList(), enlaza("la hipoteca grande"))
        assertEquals(emptyList(), enlaza("¿qué tarjeta uso?"))
        assertEquals(emptyList(), enlaza("hola"))
        assertEquals(emptyList(), enlaza("¿cuánto gasté en comidas rápidas?"), "«comidas» no es «Comida»: nada de raíces")
    }

    // ── Los bloques ───────────────────────────────────────────────────────────

    @Test
    fun `sin nada nombrado no se agrega ni una ficha`() {
        assertNull(hechos("hola, ¿cómo estás?"))
        assertNull(hechos("¿qué hipoteca pago primero?"))
    }

    @Test
    fun `una categoria trae lo gastado, el presupuesto con la resta hecha y los tres gastos mas grandes`() {
        val texto = assertNotNull(hechos("¿Cómo voy en Fútbol?"))

        assertTrue("Categoría «Fútbol»" in texto, texto)
        assertTrue("- Gastado: \$963.456" in texto, texto)
        assertTrue("SE PASÓ por \$563.456" in texto, texto)
        assertTrue("va en el 241 % del límite" in texto, texto)
        assertTrue("2026-09-06 · Torneo (Fútbol, Bancolombia Ahorros): \$700.000" in texto, texto)
        assertTrue("Cancha semanal \$120.000, sale el día 20 — TODAVÍA no ocurrió" in texto, texto)

        val comida = assertNotNull(hechos("¿cuánto gasté en comida?"))
        assertTrue("le quedan \$338.465" in comida, comida)
        val grandes = comida.lineSequence().first { it.startsWith("- Los gastos más grandes") }
        assertTrue(grandes.indexOf("Éxito") < grandes.indexOf("Carulla") && grandes.indexOf("Carulla") < grandes.indexOf("Crepes"), grandes)
        assertFalse("Rappi" in grandes, "son los TRES más grandes: $grandes")
    }

    @Test
    fun `una tarjeta trae lo que debe y lo gastado con ella en el periodo`() {
        val texto = assertNotNull(hechos("¿cuánto gasté con la Master Black 3684?"))
        assertTrue("Tarjeta «Master Black 3684»" in texto, texto)
        assertTrue("- Debe hoy: \$3.500.000" in texto, texto)
        assertTrue("en este período (25 de agosto al 24 de septiembre): \$1.053.456" in texto, texto)
    }

    /** Skandia es plata suya con destino, y de ahí salen dos cuotas: las dos cosas, dichas. */
    @Test
    fun `una cuenta propia que paga cuotas lo dice`() {
        val texto = assertNotNull(hechos("¿cuánto tengo en Skandia?"))
        assertTrue("- Saldo hoy: \$106.000.000" in texto, texto)
        assertTrue("NO es plata disponible: solo se puede usar para pensión voluntaria" in texto, texto)
        assertTrue("De esta cuenta sale la cuota de: «Hipotecario 2334» (\$2.613.714 al mes); «Hipoteca 1254» (\$9.147.408 al mes)" in texto, texto)
    }

    @Test
    fun `un bien trae su valor, la deuda que lo financia y lo suyo de verdad`() {
        val texto = assertNotNull(hechos("¿cuánto vale la Casa Almendros de Zúñiga?"))
        assertTrue("- Vale \$1.411.903.920 según el avalúo del 2026-08-28" in texto, texto)
        assertTrue("Lo financia «Hipoteca 1254», que debe \$400.000.000: lo suyo de verdad son \$1.011.903.920" in texto, texto)
    }

    @Test
    fun `un credito con otros cargos los resta antes de decir lo que baja`() {
        val texto = assertNotNull(hechos("¿cuánto baja el Vehículo 8761?"))
        val plan = assertNotNull(v8761.plan)
        assertTrue("Otros cargos dentro de la cuota: \$25.000" in texto, texto)
        assertTrue("le quedan \$1.475.000 para interés y capital" in texto, texto)
        assertTrue("la BAJA ${pesos(plan.capital)} este mes" in texto, texto)
        assertTrue("- Quién paga la cuota: sale de su bolsillo" in texto, texto)
    }

    @Test
    fun `el periodo, el patrimonio y los intereses se piden por su nombre`() {
        val periodo = assertNotNull(hechos("¿me alcanza este período?"))
        // 14.000.000 − (1.161.535 + 963.456 + 600.000 + 250.000)
        assertTrue("flujo (ingresos − gastos): \$11.025.009" in periodo, periodo)
        assertTrue("Gastos recurrentes que todavía no ocurrieron: \$120.000" in periodo, periodo)
        assertTrue("el flujo del período quedaría en \$10.905.009" in periodo, periodo)

        val patrimonio = assertNotNull(hechos("¿cuál es mi patrimonio?"))
        assertTrue("- Patrimonio neto: ${pesos(datos.patrimonio.neto)}" in patrimonio, patrimonio)

        val intereses = assertNotNull(hechos("¿cuánto pago de intereses?"))
        assertTrue(intereses.indexOf("«Hipoteca 1254»") < intereses.indexOf("«Vehículo 8761»"), "del más caro al más barato:\n$intereses")
    }

    // ── La regresión del 23-sep ───────────────────────────────────────────────

    /**
     * **El turno completo de la mañana del 23-sep**, con los datos reales del 2334, y la respuesta
     * que dio el modelo.
     *
     * Lo que se fija:
     * 1. El bloque de hechos trae la cuenta bien hecha: lo que queda de la cuota después de seguros
     *    ($2.404.495), el interés del mes ($2.427.883) y que la deuda CRECE ($23.388 al mes).
     * 2. Quién paga es SU cuenta, no «tu seguro».
     * 3. El verificador **marca** los $185.831 del «lo que baja la deuda».
     *
     * ### El límite conocido, escrito
     *
     * $185.831 **es** una resta de dos números conocidos: cuota ($2.613.714) − interés
     * ($2.427.883), los dos en el mismo renglón. Por números solos, el verificador la acepta (ver
     * la última aserción: sin trampas, pasa). Una cuenta válida con el significado equivocado no se
     * detecta por números; se detecta aquí porque [cifrasTrampa] declara de antemano esa resta como
     * la lectura equivocada de un crédito con seguros, y porque el bloque de hechos ya trae la
     * cuenta correcta con su nombre. Cualquier otra resta bien hecha y mal leída sigue pasando.
     */
    @Test
    fun `la respuesta del 23-sep sobre el Hipotecario 2334 queda marcada`() {
        val pregunta = "¿Por qué Hipotecario 2334 no baja aunque pago la cuota?"
        val contexto = datos.comoContexto()
        val hechos = assertNotNull(hechos(pregunta))
        val plan = assertNotNull(h2334.plan)

        // Los números del caso, fijados: si cambia la cuenta de la pantalla, esto tiene que saberse.
        assertEquals(2_427_883L, plan.interes)
        assertEquals(-23_388L, plan.capital)
        assertEquals(185_831L, 2_613_714L - plan.interes, "cuota − interés: la resta que hizo el modelo")

        // 1. Los hechos traen la cuenta hecha.
        assertTrue(hechos.startsWith(TITULO_DE_LOS_HECHOS), hechos)
        assertTrue("le quedan \$2.404.495" in hechos, hechos)
        assertTrue("Interés de este mes: \$2.427.883" in hechos, hechos)
        assertTrue("la deuda CRECE unos \$23.388 al mes aunque pague" in hechos, hechos)
        assertTrue("Tasa: 15,24 % EA" in hechos, hechos)
        // 2. Skandia es su cuenta.
        assertTrue("«Skandia pensión voluntaria» (no es un seguro ni un tercero)" in hechos, hechos)
        assertFalse("185.831" in hechos || "185831" in hechos, "la cifra equivocada no puede estar en los datos")

        // 3. La respuesta de esa mañana.
        val respuestaDel23 = """
            El Hipotecario 2334 no baja porque la cuota que pagas no alcanza a cubrir los intereses.
            Tu cuota es de ${'$'}2.613.714 y los intereses del mes son unos ${'$'}2.427.883: la diferencia es apenas ${'$'}185.831 al mes, que es lo que baja la deuda.
            Esa cuota la paga Skandia (tu seguro), así que no estás aportando nada extra de tu plata.
        """.trimIndent()
        val fuentes = listOf(contexto, hechos, pregunta)
        val trampas = cifrasTrampa(periodo.creditos)

        assertEquals(listOf("\$185.831"), cifrasSinRespaldo(respuestaDel23, fuentes, trampas))

        // La respuesta corregida, con las cifras de los hechos, pasa limpia.
        val corregida = """
            Tu cuota es de ${'$'}2.613.714, pero ${'$'}209.219 son seguros: te quedan ${'$'}2.404.495 para intereses y capital.
            Los intereses de este mes son ${'$'}2.427.883, así que la deuda crece unos ${'$'}23.388 al mes aunque pagues.
            La cuota sale de tu cuenta Skandia pensión voluntaria.
        """.trimIndent()
        assertEquals(emptyList(), cifrasSinRespaldo(corregida, fuentes, trampas))

        // El límite conocido: sin la trampa, la resta válida-pero-mal-leída pasa.
        assertEquals(emptyList(), cifrasSinRespaldo(respuestaDel23, fuentes), "límite conocido: ver el KDoc")
    }

    @Test
    fun `solo los creditos con cargos dentro de la cuota tienen trampa`() {
        val trampas = cifrasTrampa(listOf(h2334, h1254, v8761))
        assertTrue(185_831L in trampas, trampas.toString())
        assertFalse(trampas.values.any { "Hipoteca 1254" in it }, "sin seguros, cuota − interés sí es lo que baja")
        assertTrue(trampas.values.any { "Vehículo 8761" in it }, "otros cargos también cuentan")
    }

    /**
     * **Los hechos van en el mensaje del turno, después de la pregunta.** Así la PERSONA y el
     * contexto —que van cacheados en el sistema— no cambian de una pregunta a otra.
     */
    @Test
    fun `los hechos viajan pegados a la pregunta y no en el sistema`() {
        val pregunta = "¿Por qué Hipotecario 2334 no baja aunque pago la cuota?"
        val hechos = assertNotNull(hechos(pregunta))

        val mensaje = toMessageParam(
            ChatMessage(ChatRole.USER, pregunta),
            anexo = hechos,
        )
        val textos = mensaje.content().asBlockParams().map { it.asText().text() }
        assertEquals(listOf(pregunta, hechos), textos)

        val sinHechos = toMessageParam(
            ChatMessage(ChatRole.USER, "hola"),
        )
        assertEquals("hola", sinHechos.content().asString(), "sin hechos, el mensaje es el de siempre")
    }

    @Test
    fun `los pesos van con el formato de la app`() {
        assertEquals("\$2.404.495", pesos(2_404_495L))
        assertEquals("-\$23.388", pesos(-23_388L))
        assertEquals("\$0", pesos(0L))
        assertEquals("\$950", pesos(950L))
    }
}
