package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.routes.FabricaDeModelos
import com.jvillada.movi.server.routes.cajaProyectadaDe
import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.server.routes.cargarDatosDelUsuario
import com.jvillada.movi.server.routes.buildUserContext
import kotlinx.coroutines.runBlocking
import com.jvillada.movi.server.routes.dashboardRoutes
import com.jvillada.movi.server.routes.reminderRoutes
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.IA_SIN_CREDITO
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.disponibleAlDia
import com.jvillada.movi.shared.model.faltaPorPagar
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.pesosDelCierre
import com.jvillada.movi.shared.model.plataDelDisponibleDe
import com.jvillada.movi.shared.model.ventanaDe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Las tres preguntas sugeridas se contestan sin la IA
 *
 * Por la ruta de verdad (`POST /api/ai/chat`), con una fábrica de modelos que **cuenta cada vez que
 * la llaman**: ninguna prueba toca la red, y las de las sugeridas exigen que la cuenta quede en cero.
 * Las cifras se comparan contra lo que la app calcularía con lo que le contestan sus rutas
 * (`/api/dashboard/summary`, `/api/payments/upcoming` y `/occurrences`) y las funciones de `:core`:
 * si el chat y Hoy dijeran cifras distintas, ninguno de los dos se podría creer.
 *
 * El día es el de hoy (`AppClock` no se mueve desde una prueba); los datos se arman alrededor de él.
 */
class LasPreguntasSugeridasTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var llamadasAlModelo = 0
    private var n = 0

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("preguntas_sugeridas")
        llamadasAlModelo = 0
    }

    /** Una fábrica que anota que la llamaron y falla: si una sugerida llega acá, la prueba lo ve. */
    private val fabricaQueCuenta: FabricaDeModelos = {
        llamadasAlModelo++
        object : ElModeloQueSeCorrige {
            override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo = throw AssertionError("no debía llamar al modelo")
            override fun anotarResultados(resultados: List<Pair<String, String>>) = Unit
            override fun anotarCorreccion(correccion: String) = Unit
        }
    }

    /** La cuenta de Anthropic sin saldo, como el 4-oct. */
    private val fabricaSinCredito: FabricaDeModelos = {
        llamadasAlModelo++
        object : ElModeloQueSeCorrige {
            override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo = throw FallasDeLaApiDePrueba.sinCredito()
            override fun anotarResultados(resultados: List<Pair<String, String>>) = Unit
            override fun anotarCorreccion(correccion: String) = Unit
        }
    }

    private fun anotar(uid: String, cuenta: String, fecha: LocalDate, monto: Long, categoria: String, descripcion: String = categoria, tipo: String = "EXPENSE") =
        transaction {
            Events.insert {
                it[id] = "ev_${n++}"
                it[userId] = uid
                it[accountId] = cuenta
                it[type] = tipo
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = descripcion
                it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
                it[reconciliationStatus] = "RECONCILED"
            }
        }

    /**
     * El período del dueño A (corte 1: el mes de calendario): llegó con plata hace rato —así el
     * período pasado está cubierto entero y hay contra qué comparar—, hoy le entró el sueldo, gastó en
     * Comida y tres cafés en el mismo lugar, y el arriendo vence hoy sin pagar.
     */
    private fun periodoDeA(hoy: LocalDate) {
        val inicioDelMes = hoy.withDayOfMonth(1)
        anotar(a.duenoA, a.cuentaDeA, inicioDelMes.minusDays(45), 20_000_000, OPENING_CATEGORY, "Saldo inicial", tipo = "INCOME")
        anotar(a.duenoA, a.cuentaDeA, hoy, 10_000_000, "Salario", "Sueldo", tipo = "INCOME")
        anotar(a.duenoA, a.cuentaDeA, hoy, 200_000, "Comida", "Mercado")
        repeat(3) { anotar(a.duenoA, a.cuentaDeA, hoy, 40_000, "Café", "Juan Valdez") }
        transaction {
            RecurringRules.insert {
                it[id] = "regla-arriendo"
                it[userId] = a.duenoA
                it[name] = "Arriendo"
                it[category] = "Vivienda"
                it[amount] = 1_000_000
                it[dayOfMonth] = hoy.dayOfMonth
                it[type] = "EXPENSE"
                it[accountId] = a.cuentaDeA
                it[createdAt] = appDateToEpochMillis(inicioDelMes.minusDays(60))
            }
        }
    }

    private suspend fun ApplicationTestBuilder.preguntar(
        uid: String,
        texto: String,
        sugerida: PreguntaSugerida?,
        imagen: Boolean = false,
    ): HttpResponse = client.post("/api/ai/chat") {
        with(a) { como(uid) }
        contentType(ContentType.Application.Json)
        val mensaje = ChatMessage(
            role = ChatRole.USER,
            content = texto,
            preguntaSugerida = sugerida?.id,
            // Un PNG de 1×1: lo justo para que la validación de imágenes lo deje pasar.
            imageBase64 = if (imagen) PNG_DE_UN_PIXEL else null,
            imageMime = if (imagen) "image/png" else null,
        )
        setBody(json.encodeToString(AiChatRequest.serializer(), AiChatRequest(listOf(mensaje))))
    }

    private suspend fun ApplicationTestBuilder.respuesta(uid: String, pregunta: PreguntaSugerida): String {
        val res = preguntar(uid, pregunta.texto, pregunta)
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return json.decodeFromString(AiChatResponse.serializer(), res.bodyAsText()).text
    }

    @Test
    fun `las tres contestan sin tocar la fabrica del modelo y el turno queda marcado sin modelo`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta) } } }
        periodoDeA(AppClock.today())

        PreguntaSugerida.entries.forEach { pregunta ->
            val texto = respuesta(a.duenoA, pregunta)
            assertTrue(texto.isNotBlank(), "${pregunta.id} contestó vacío")
            assertFalse(texto.contains("según mis cálculos", ignoreCase = true), texto)
        }
        assertEquals(0, llamadasAlModelo, "ninguna de las tres puede llamar al modelo")

        val turnos = transaction {
            AiTurns.selectAll().where { AiTurns.userId eq a.duenoA }.map { Triple(it[AiTurns.pregunta], it[AiTurns.modelo], it[AiTurns.consultas]) }
        }
        assertEquals(PreguntaSugerida.entries.map { it.texto }.toSet(), turnos.map { it.first }.toSet())
        assertTrue(turnos.all { it.second == MODELO_SIN_IA }, turnos.toString())
        PreguntaSugerida.entries.forEach { p -> assertTrue(turnos.any { "\"${p.id}\"" in it.third }, "falta la pregunta ${p.id} en consultas") }

        // Y la conversación recargada las trae como cualquier otra respuesta.
        val conversacion = json.decodeFromString(
            ConversacionDelAsistente.serializer(),
            client.get("/api/ai/conversacion") { with(a) { como(a.duenoA) } }.bodyAsText(),
        )
        assertEquals(6, conversacion.mensajes.size)
    }

    /**
     * Vuelta 61: la misma pregunta escrita a mano —o casi igual: «¿Cómo voy?»— se contesta sin el
     * modelo, como el chip. Era «se reconoce por el id, nunca por el texto», y sin crédito el dueño
     * veía el aviso de la IA debajo del chip que decía lo mismo.
     */
    @Test
    fun `la misma pregunta escrita a mano, sin el id, se contesta sin el modelo`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta) } } }
        periodoDeA(AppClock.today())
        listOf(PreguntaSugerida.COMO_VOY.texto, "¿Cómo voy?", "como voy", "¿Qué reviso primero?").forEach { texto ->
            val res = preguntar(a.duenoA, texto, sugerida = null)
            assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        }
        assertEquals(0, llamadasAlModelo, "lo escrito casi igual que una de las tres no llama al modelo")
        val modelos = transaction { AiTurns.selectAll().where { AiTurns.userId eq a.duenoA }.map { it[AiTurns.modelo] } }
        assertTrue(modelos.size == 4 && modelos.all { it == MODELO_SIN_IA }, modelos.toString())
    }

    @Test
    fun `una pregunta que solo empieza igual va al modelo`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta) } } }
        preguntar(a.duenoA, "¿Cómo voy con la deuda del carro?", sugerida = null)
        assertEquals(1, llamadasAlModelo, "nada de adivinar: solo lo casi idéntico se contesta sin el modelo")
    }

    @Test
    fun `con una foto adjunta va al modelo aunque traiga el id`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta) } } }
        preguntar(a.duenoA, PreguntaSugerida.COMO_VOY.texto, PreguntaSugerida.COMO_VOY, imagen = true)
        assertEquals(1, llamadasAlModelo)
    }

    @Test
    fun `como voy dice las cifras de Hoy y de Plan`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta); dashboardRoutes(); reminderRoutes() } } }
        val hoy = AppClock.today()
        periodoDeA(hoy)

        // Lo que la app tiene en la mano, pedido a las mismas rutas que pide ella.
        val resumen = json.decodeFromString(DashboardSummary.serializer(), get(a.duenoA, "/api/dashboard/summary"))
        val upcoming = json.decodeFromString(ListSerializer(UpcomingPayment.serializer()), get(a.duenoA, "/api/payments/upcoming"))
        val ocurrencias = json.decodeFromString(ListSerializer(OccurrenceState.serializer()), get(a.duenoA, "/api/payments/occurrences"))
        val ajustes = PeriodSettings()
        val periodo = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, ajustes)
        val ventana = ventanaDe(periodo, ajustes)
        val checklist = checklistDelPeriodo(upcoming, ocurrencias, periodo, ajustes)
        // `disponibleDelInicio` de la app: la misma `disponibleDelPeriodo`, con los mismos datos.
        val disponible = assertNotNull(
            disponibleAlDia(
                ingresosRecibidos = resumen.monthIncome,
                pagos = checklist,
                gastoVariablePorDia = resumen.gastoVariablePorDia.orEmpty(),
                desde = epochMillisToAppDate(ventana.first).toString(),
                hasta = epochMillisToAppDate(ventana.last).toString(),
                hoy = hoy.toString(),
                plata = plataDelDisponibleDe(resumen),
            ),
        )

        val texto = respuesta(a.duenoA, PreguntaSugerida.COMO_VOY)

        assertTrue(pesosDelCierre(disponible.periodo.teQuedan) in texto, "te quedan ${disponible.periodo.teQuedan}:\n$texto")
        assertTrue("entró ${pesosDelCierre(resumen.monthIncome)} y salió ${pesosDelCierre(resumen.monthSpent)}" in texto, texto)
        assertEquals(1_000_000L, faltaPorPagar(checklist))
        assertTrue("Te falta 1 pago del período por ${pesosDelCierre(faltaPorPagar(checklist))}" in texto, texto)
        assertTrue("Arriendo \$1.000.000 · vence hoy" in texto, texto)
        // El período pasado está cubierto (el saldo inicial es de antes): hay comparación.
        assertTrue("período pasado" in texto, texto)
        // La cifra diaria del héroe de Hoy (lo que queda entre los días que faltan), no la meta del período.
        if (disponible.diasQueQuedan > 1 && disponible.periodo.contraElRitmo <= 0L) {
            assertTrue("${pesosDelCierre(disponible.porDiaParaLoQueQueda ?: 0L)} por día" in texto, texto)
        }
        assertFalse("meta diaria" in texto, texto)
        assertEquals(0, llamadasAlModelo)
    }

    /** Lo apartado no se puede gastar: «Te quedan» baja exactamente lo apartado, y el chat lo dice igual que Hoy. */
    @Test
    fun `como voy descuenta lo apartado de lo que te queda`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta); dashboardRoutes(); reminderRoutes() } } }
        val hoy = AppClock.today()
        periodoDeA(hoy)

        suspend fun teQuedan(): Long {
            val resumen = json.decodeFromString(DashboardSummary.serializer(), get(a.duenoA, "/api/dashboard/summary"))
            val upcoming = json.decodeFromString(ListSerializer(UpcomingPayment.serializer()), get(a.duenoA, "/api/payments/upcoming"))
            val ocurrencias = json.decodeFromString(ListSerializer(OccurrenceState.serializer()), get(a.duenoA, "/api/payments/occurrences"))
            val ajustes = PeriodSettings()
            val periodo = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, ajustes)
            val ventana = ventanaDe(periodo, ajustes)
            return assertNotNull(
                disponibleAlDia(
                    ingresosRecibidos = resumen.monthIncome,
                    pagos = checklistDelPeriodo(upcoming, ocurrencias, periodo, ajustes),
                    gastoVariablePorDia = resumen.gastoVariablePorDia.orEmpty(),
                    desde = epochMillisToAppDate(ventana.first).toString(),
                    hasta = epochMillisToAppDate(ventana.last).toString(),
                    hoy = hoy.toString(),
                    plata = plataDelDisponibleDe(resumen),
                ),
            ).periodo.teQuedan
        }

        val antes = teQuedan()
        transaction {
            Apartados.insert {
                it[Apartados.id] = "apt-sugeridas"
                it[Apartados.userId] = a.duenoA
                it[accountId] = a.cuentaDeA
                it[proposito] = "Colegio"
                it[monto] = 5_000_000L
                it[desde] = 0L
                it[createdAt] = System.currentTimeMillis()
            }
        }
        val despues = teQuedan()
        assertEquals(antes - 5_000_000L, despues)

        val texto = respuesta(a.duenoA, PreguntaSugerida.COMO_VOY)
        assertTrue(pesosDelCierre(despues) in texto, "te quedan $despues:\n$texto")
        assertEquals(0, llamadasAlModelo)
    }

    /**
     * **El ahorro del período** (27-oct-2026): el dueño eligió apartar el 20 % de lo que entra. El
     * resumen del Inicio trae ese 20 % de lo que entró en pesos, «Te quedan» baja exactamente eso, y
     * «¿Cómo voy?» dice la misma cifra que el héroe. Tu plata no cambia, y el otro usuario tampoco.
     */
    @Test
    fun `como voy descuenta el ahorro del periodo igual que Hoy`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta); dashboardRoutes(); reminderRoutes() } } }
        val hoy = AppClock.today()
        periodoDeA(hoy)
        anotar(a.duenoB, a.cuentaDeB, hoy.withDayOfMonth(1).minusDays(45), 5_000_000, OPENING_CATEGORY, "Saldo inicial", tipo = "INCOME")
        anotar(a.duenoB, a.cuentaDeB, hoy, 3_000_000, "Salario", "Sueldo", tipo = "INCOME")

        suspend fun resumen(uid: String) = json.decodeFromString(DashboardSummary.serializer(), get(uid, "/api/dashboard/summary"))
        suspend fun teQuedan(uid: String): Long {
            val resumen = resumen(uid)
            val upcoming = json.decodeFromString(ListSerializer(UpcomingPayment.serializer()), get(uid, "/api/payments/upcoming"))
            val ocurrencias = json.decodeFromString(ListSerializer(OccurrenceState.serializer()), get(uid, "/api/payments/occurrences"))
            val ajustes = PeriodSettings()
            val periodo = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, ajustes)
            val ventana = ventanaDe(periodo, ajustes)
            return assertNotNull(
                disponibleAlDia(
                    ingresosRecibidos = resumen.monthIncome,
                    pagos = checklistDelPeriodo(upcoming, ocurrencias, periodo, ajustes),
                    gastoVariablePorDia = resumen.gastoVariablePorDia.orEmpty(),
                    desde = epochMillisToAppDate(ventana.first).toString(),
                    hasta = epochMillisToAppDate(ventana.last).toString(),
                    hoy = hoy.toString(),
                    plata = plataDelDisponibleDe(resumen),
                ),
            ).periodo.teQuedan
        }

        // Sin elegir nada, el resumen no trae el campo: todo da como antes.
        val sinAhorro = resumen(a.duenoA)
        assertEquals(null, sinAhorro.ahorroDelPeriodo)
        val antes = teQuedan(a.duenoA)
        val antesDeB = teQuedan(a.duenoB)

        transaction {
            Users.update({ Users.id eq a.duenoA }) {
                it[ahorroDelPeriodoTipo] = "PORCENTAJE"
                it[ahorroDelPeriodoValor] = 20L
            }
        }
        val con = resumen(a.duenoA)
        assertEquals(10_000_000L, con.monthIncome)
        assertEquals(2_000_000L, con.ahorroDelPeriodo, "el 20 % de lo que entró")
        assertEquals(sinAhorro.patrimonio, con.patrimonio, "Tu plata y el patrimonio no cambian")
        assertEquals(sinAhorro.saldoTuPlataAlInicio, con.saldoTuPlataAlInicio)
        val despues = teQuedan(a.duenoA)
        assertEquals(antes - 2_000_000L, despues)

        val texto = respuesta(a.duenoA, PreguntaSugerida.COMO_VOY)
        assertTrue(pesosDelCierre(despues) in texto, "te quedan $despues:\n$texto")
        assertFalse(pesosDelCierre(antes) in texto, "no puede decir la cifra sin el ahorro:\n$texto")
        assertEquals(0, llamadasAlModelo)

        // El ahorro es de A: B sigue igual.
        assertEquals(null, resumen(a.duenoB).ahorroDelPeriodo)
        assertEquals(antesDeB, teQuedan(a.duenoB))
    }

    /**
     * **El contexto de las preguntas escritas a mano también sabe del ahorro**: el renglón «Apartas
     * $X de ahorro cada período (20 % de lo que entra); te quedan $Y…» va en el contexto general y en
     * los hechos exactos —las dos fuentes del verificador de cifras—, con el «Te quedan» de Hoy. Sin
     * ahorro, el contexto es exactamente el de antes (ni un renglón de más).
     */
    @Test
    fun `el contexto del chat menciona el ahorro solo cuando hay ahorro`() {
        val hoy = AppClock.today()
        periodoDeA(hoy)
        val pregunta = "¿Cuánto puedo gastar este período?"

        val sinAhorro = runBlocking { buildUserContext(a.duenoA) }
        val hechosSin = runBlocking { hechosParaLaPregunta(pregunta, cargarDatosDelUsuario(a.duenoA).paraLosHechos()) }
        assertFalse("Apartas" in sinAhorro, sinAhorro)
        assertFalse("ahorro" in hechosSin.orEmpty(), hechosSin.orEmpty())

        fun elegir(tipo: String, valor: Long) = transaction {
            Users.update({ Users.id eq a.duenoA }) {
                it[ahorroDelPeriodoTipo] = tipo
                it[ahorroDelPeriodoValor] = valor
            }
        }

        elegir("PORCENTAJE", 20L)
        val teQuedan = assertNotNull(runBlocking { datosDeLasSugeridas(a.duenoA, PreguntaSugerida.COMO_VOY, hoy) }.disponible).periodo.teQuedan
        val conAhorro = runBlocking { buildUserContext(a.duenoA) }
        assertTrue(
            "- Apartas \$2000000 de ahorro cada período (20 % de lo que entra); te quedan \$$teQuedan para gastar" in conAhorro,
            conAhorro,
        )
        val hechos = assertNotNull(runBlocking { hechosParaLaPregunta(pregunta, cargarDatosDelUsuario(a.duenoA).paraLosHechos()) })
        assertTrue(
            "- Apartas ${pesos(2_000_000L)} de ahorro cada período (20 % de lo que entra); te quedan ${pesos(teQuedan)} para gastar" in hechos,
            hechos,
        )
        // El mismo «Te quedan» que el héroe: el que dice «¿Cómo voy?» (ver la prueba de arriba).
        assertTrue(pesosDelCierre(teQuedan) in respuestaSinModelo(PreguntaSugerida.COMO_VOY, runBlocking { datosDeLasSugeridas(a.duenoA, PreguntaSugerida.COMO_VOY, hoy) }))
        // Y el verificador de cifras lo respalda: está en las fuentes del turno.
        assertTrue(cifrasSinRespaldo("Te quedan ${pesos(teQuedan)} y apartas ${pesos(2_000_000L)}.", listOf(conAhorro, hechos)).isEmpty())

        // El otro usuario no lo ve.
        assertFalse("Apartas" in runBlocking { buildUserContext(a.duenoB) })

        // Volver a «Nada»: el contexto es exactamente el de antes.
        elegir("NINGUNO", 0L)
        assertEquals(sinAhorro, runBlocking { buildUserContext(a.duenoA) })
    }

    /**
     * El apartado que además es un pago pendiente (el arriendo de hoy, «Vivienda», desde la misma
     * cuenta): los fijos ya restan el $1M que falta pagar, así que «Te quedan» baja solo los otros
     * $4M — en Hoy y en el chat igual — y la caja proyectada arranca con ese $1M devuelto.
     */
    @Test
    fun `el apartado que cubre un pago pendiente no se resta dos veces`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta); dashboardRoutes(); reminderRoutes() } } }
        val hoy = AppClock.today()
        periodoDeA(hoy)

        suspend fun resumen() = json.decodeFromString(DashboardSummary.serializer(), get(a.duenoA, "/api/dashboard/summary"))
        suspend fun teQuedan(): Long {
            val resumen = resumen()
            val upcoming = json.decodeFromString(ListSerializer(UpcomingPayment.serializer()), get(a.duenoA, "/api/payments/upcoming"))
            val ocurrencias = json.decodeFromString(ListSerializer(OccurrenceState.serializer()), get(a.duenoA, "/api/payments/occurrences"))
            val ajustes = PeriodSettings()
            val periodo = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, ajustes)
            val ventana = ventanaDe(periodo, ajustes)
            return assertNotNull(
                disponibleAlDia(
                    ingresosRecibidos = resumen.monthIncome,
                    pagos = checklistDelPeriodo(upcoming, ocurrencias, periodo, ajustes),
                    gastoVariablePorDia = resumen.gastoVariablePorDia.orEmpty(),
                    desde = epochMillisToAppDate(ventana.first).toString(),
                    hasta = epochMillisToAppDate(ventana.last).toString(),
                    hoy = hoy.toString(),
                    plata = plataDelDisponibleDe(resumen),
                ),
            ).periodo.teQuedan
        }

        val antes = teQuedan()
        transaction {
            Apartados.insert {
                it[Apartados.id] = "apt-arriendo"
                it[Apartados.userId] = a.duenoA
                it[accountId] = a.cuentaDeA
                it[proposito] = "Arriendo y más"
                it[monto] = 5_000_000L
                it[categoria] = "Vivienda"
                it[desde] = appDateToEpochMillis(hoy)
                it[createdAt] = System.currentTimeMillis()
            }
        }
        val con = resumen()
        assertEquals(5_000_000L, con.patrimonio!!.apartado, "Tu plata sigue restando lo apartado entero")
        assertEquals(4_000_000L, con.apartado, "«Te quedan» resta solo lo que el arriendo pendiente no cubre")
        val despues = teQuedan()
        assertEquals(antes - 4_000_000L, despues)

        val texto = respuesta(a.duenoA, PreguntaSugerida.COMO_VOY)
        assertTrue(pesosDelCierre(despues) in texto, "te quedan $despues:\n$texto")
        assertEquals(0, llamadasAlModelo)

        val caja = assertNotNull(cajaProyectadaDe(a.duenoA, hoy).caja)
        // Vuelta 70: la caja parte de lo que hay en las cuentas (lo apartado sigue ahí) y lo libre resta
        // solo lo apartado que el arriendo pendiente no cubre: el $1M cubierto no se resta dos veces.
        assertEquals(con.patrimonio!!.tuPlata + 5_000_000L, caja.tuPlataHoy)
        assertEquals(con.patrimonio!!.tuPlata + 1_000_000L, caja.libreHoy)
    }

    @Test
    fun `en que se va dice las categorias de Hoy y los pequenos que suman`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta); dashboardRoutes() } } }
        periodoDeA(AppClock.today())
        val resumen = json.decodeFromString(DashboardSummary.serializer(), get(a.duenoA, "/api/dashboard/summary"))

        val texto = respuesta(a.duenoA, PreguntaSugerida.EN_QUE_SE_VA)

        assertTrue("salieron ${pesosDelCierre(resumen.monthSpent)}" in texto, texto)
        val comida = resumen.spentByCategory.getValue("Comida")
        val cafe = resumen.spentByCategory.getValue("Café")
        assertTrue("1. Comida ${pesosDelCierre(comida)}" in texto, texto)
        assertTrue("2. Café ${pesosDelCierre(cafe)}" in texto, texto)
        // El período pasado no tuvo gastos a esta altura: las dos suben por todo lo gastado.
        assertTrue("Comida ${pesosDelCierre(comida)} · ${pesosDelCierre(comida)} más que el período pasado" in texto, texto)
        assertTrue("3 compras en Juan Valdez: \$120.000" in texto, texto)
    }

    @Test
    fun `que revisar pone primero lo urgente y dice donde se resuelve`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta) } } }
        periodoDeA(AppClock.today())
        // Un presupuesto pasado: urgente, «te pasaste».
        transaction {
            com.jvillada.movi.server.db.Budgets.insert {
                it[userId] = a.duenoA
                it[category] = "Comida"
                it[monthlyLimit] = 150_000
            }
        }

        val texto = respuesta(a.duenoA, PreguntaSugerida.QUE_REVISAR)

        assertTrue("1. Te pasaste del presupuesto de Comida por \$50.000." in texto, texto)
        assertTrue("Presupuestos" in texto, texto)
    }

    @Test
    fun `sin credito las tres siguen contestando y una pregunta libre da el aviso`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaSinCredito) } } }
        periodoDeA(AppClock.today())

        PreguntaSugerida.entries.forEach { respuesta(a.duenoA, it) }
        assertEquals(0, llamadasAlModelo)

        val libre = preguntar(a.duenoA, "¿Cuánto gasté en Comida en agosto?", sugerida = null)
        assertEquals(HttpStatusCode.ServiceUnavailable, libre.status)
        assertEquals(IA_SIN_CREDITO, json.decodeFromString(AiChatResponse.serializer(), libre.bodyAsText()).codigo)
    }

    @Test
    fun `cada quien recibe solo lo suyo`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueCuenta) } } }
        periodoDeA(AppClock.today())

        val deB = respuesta(a.duenoB, PreguntaSugerida.EN_QUE_SE_VA)
        assertTrue("todavía no hay gastos anotados" in deB, deB)
        assertFalse("Comida" in deB || "Juan Valdez" in deB, deB)

        val comoVaB = respuesta(a.duenoB, PreguntaSugerida.COMO_VOY)
        assertFalse(pesosDelCierre(10_000_000) in comoVaB || "Arriendo" in comoVaB, comoVaB)

        val turnosDeA = transaction { AiTurns.selectAll().where { AiTurns.userId eq a.duenoA }.count() }
        assertEquals(0L, turnosDeA, "lo que preguntó B no queda en la conversación de A")
    }

    private suspend fun ApplicationTestBuilder.get(uid: String, ruta: String): String {
        val res = client.get(ruta) { with(a) { como(uid) } }
        assertEquals(HttpStatusCode.OK, res.status, "$ruta: ${res.bodyAsText()}")
        return res.bodyAsText()
    }

    private companion object {
        const val PNG_DE_UN_PIXEL =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    }
}
