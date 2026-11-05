package com.jvillada.movi.server.parsing

import com.jvillada.movi.server.parsing.ClaudeStatementParser.DiagnosticoDeLaLectura
import com.jvillada.movi.server.parsing.ClaudeStatementParser.Lectura
import com.jvillada.movi.server.parsing.ClaudeStatementParser.RespuestaDelPedazo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * # Un extracto largo no se cae: se parte solo (vuelta 128)
 *
 * Caro subió desde el iPhone el extracto de su cuenta de ahorros (128 KB, ~60 caracteres por fila)
 * y el server contestó a los 84 s «El extracto es muy largo… Divide el archivo en dos». Un pedazo
 * de 12.000 caracteres eran ~200 filas y su JSON no cabía en las 16.000 fichas de salida: volvía
 * cortado, y un solo pedazo cortado tumbaba la lectura entera.
 *
 * Ninguna prueba llama a la API: el modelo es [modeloFalso], que lee las filas del cuerpo del
 * pedido y **vuelve cortado** si le piden más de [capacidad] filas, como el de verdad con su tope.
 */
class ElExtractoLargoSeParteSoloTest {

    private val encabezado = listOf(
        "BANCOLOMBIA",
        "Cuenta de Ahorros ****0756",
        "DESDE 2026/09/25 HASTA 2026/10/24",
        "FECHA DESCRIPCION VALOR",
    )

    /** Una fila densa de ahorros: 30 caracteres, con su número para seguirle el orden. */
    private fun fila(n: Int) = "%02d/10 MOV %04d PAGO PSE 12.345".format(1 + n % 28, n)

    private fun extracto(filas: Int, separador: String = "\n") =
        encabezado.joinToString("\n") + "\n" + (1..filas).joinToString(separador) { fila(it) }

    private val FILA = Regex("""MOV (\d{4})""")

    private fun json(numeros: List<String>) = numeros.joinToString(",", "[", "]") {
        """{"date":"2026-10-01","merchant":"MOV $it","amount":12345,"type":"EXPENSE","category":"Otros","description":"","rawText":""}"""
    }

    /**
     * El modelo falso: lee **todas** las filas del cuerpo —también las que viajen en el encabezado
     * de contexto: un modelo de verdad puede leerlas, y por eso el encabezado no debe traerlas— y,
     * con más de [capacidad], vuelve cortado. Con [cortadoConFilasValidas], el corte trae un JSON
     * que sí cerró —las primeras [capacidad] filas, como un modelo que se quedó sin fichas justo
     * después de cerrar el array— pero con `stop_reason = max_tokens`.
     */
    private fun modeloFalso(
        capacidad: Int,
        cuerpos: MutableList<String> = Collections.synchronizedList(mutableListOf()),
        cortadoConFilasValidas: Boolean = false,
        antes: suspend (String) -> Unit = {},
    ): suspend (String, Long) -> RespuestaDelPedazo = { cuerpo, _ ->
        cuerpos += cuerpo
        antes(cuerpo)
        val numeros = FILA.findAll(cuerpo).map { it.groupValues[1] }.toList()
        when {
            numeros.size <= capacidad -> RespuestaDelPedazo(json(numeros), "end_turn", numeros.size * 90L)
            cortadoConFilasValidas -> RespuestaDelPedazo(json(numeros.take(capacidad)), "max_tokens", 16_000)
            else -> RespuestaDelPedazo("""[{"date":"2026-10-01","merchant":"MO""", "max_tokens", 16_000)
        }
    }

    /** Los números de las filas leídas; exige que ninguna se repita. */
    private fun numerosDe(lectura: Lectura): List<Int> {
        val numeros = assertIs<Lectura.Ok>(lectura).movimientos.map { it.merchant.removePrefix("MOV ").toInt() }
        assertEquals(numeros.size, numeros.toSet().size, "filas repetidas: ${numeros.groupBy { it }.filter { it.value.size > 1 }.keys}")
        return numeros
    }

    // ── 1. El pedazo cortado se parte y las filas salen completas y en orden ──────────

    /**
     * El caso de Caro, en chico: 300 filas que entran en un pedazo de 12.000 caracteres, y un
     * modelo que solo alcanza a devolver 100. Antes: [Lectura.Incompleta]. Ahora: las 300, en orden.
     */
    @Test
    fun un_pedazo_que_vuelve_cortado_se_parte_y_las_filas_salen_completas_y_en_orden() = runBlocking {
        var diagnostico: DiagnosticoDeLaLectura? = null
        val texto = extracto(300)
        assertTrue(texto.length < ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO, "tiene que arrancar en un solo pedazo")

        val lectura = ClaudeStatementParser.leerPorPedazos(
            texto,
            maxCaracteres = ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO,
            anotar = { diagnostico = it },
            pedir = modeloFalso(capacidad = 100),
        )

        assertEquals((1..300).toList(), numerosDe(lectura))
        val d = diagnostico!!
        assertEquals("ok", d.resultado)
        assertEquals(1, d.pedazosIniciales)
        assertTrue(d.cortes >= 1, "el primer pedido volvió cortado: $d")
    }

    /** Varios pedazos iniciales, y uno del medio se parte: el orden del documento no cambia. */
    @Test
    fun un_pedazo_del_medio_que_se_parte_no_desordena_los_demas() = runBlocking {
        val lectura = ClaudeStatementParser.leerPorPedazos(
            extracto(400),
            maxCaracteres = 6_000, // ~190 filas por pedazo: cada uno vuelve cortado y se parte en dos de ~95
            anotar = {},
            pedir = modeloFalso(capacidad = 100),
        )

        assertEquals((1..400).toList(), numerosDe(lectura))
    }

    // ── 2. El tope de la recursión ────────────────────────────────────────────────

    /**
     * Un modelo que nunca alcanza: se parte hasta el tope y ahí sí, [Lectura.Incompleta] — nunca
     * una lista a medias. Y con un techo de pedidos: 12.000 caracteres se parten a lo sumo hasta
     * mitades de ~1.500, no al infinito.
     */
    @Test
    fun si_ni_el_pedazo_minimo_cabe_la_lectura_es_incompleta_y_no_sigue_partiendo() = runBlocking {
        val pedidos = AtomicInteger()
        var diagnostico: DiagnosticoDeLaLectura? = null
        val siempreCortado = modeloFalso(capacidad = 0)

        val lectura = ClaudeStatementParser.leerPorPedazos(
            extracto(300),
            maxCaracteres = ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO,
            aLaVez = 1,
            anotar = { diagnostico = it },
            pedir = { c, q -> pedidos.incrementAndGet(); siempreCortado(c, q) },
        )

        assertEquals(Lectura.Incompleta, lectura)
        assertEquals("incompleta", diagnostico!!.resultado)
        // Un pedazo: el tope de la lectura es 1 × 3 + 4 = 7 pedidos (sin él, 15: 1 + 2 + 4 + 8).
        assertTrue(pedidos.get() in 2..7, "pedidos: ${pedidos.get()}")
    }

    /** Un pedazo de menos del doble del mínimo ya no se parte: un corte ahí es incompleta. */
    @Test
    fun un_pedazo_chico_que_vuelve_cortado_no_se_parte() = runBlocking {
        val pedidos = AtomicInteger()
        val texto = extracto(20)
        assertTrue(texto.length < 2 * ClaudeStatementParser.MINIMO_DE_UN_PEDAZO)

        val lectura = ClaudeStatementParser.leerPorPedazos(
            texto,
            anotar = {},
            pedir = { c, q -> pedidos.incrementAndGet(); modeloFalso(capacidad = 5)(c, q) },
        )

        assertEquals(Lectura.Incompleta, lectura)
        assertEquals(1, pedidos.get())
    }

    // ── 3. Una línea gigante se parte antes de una fecha ─────────────────────────────

    /**
     * Un PDF cuyo texto salió todo en un renglón: antes iba entero en su pedazo y, si volvía
     * cortado, no había por dónde partirlo. Ahora se corta antes de cada fecha y ninguna fila se
     * rompe.
     */
    @Test
    fun una_linea_gigante_se_parte_antes_de_una_fecha() {
        val filas = (1..200).map { fila(it) }
        val linea = filas.joinToString(" ")

        val trozos = ClaudeStatementParser.partirLineaLarga(linea, max = 500)

        assertTrue(trozos.size > 1)
        assertEquals(linea, trozos.joinToString(""), "los trozos, seguidos, son la línea entera")
        assertTrue(trozos.all { it.length <= 500 }, "ninguno se pasa del tope")
        assertTrue(trozos.drop(1).all { Regex("""^\d\d/10 MOV \d{4}""").containsMatchIn(it) }, "cada trozo arranca en una fecha")
        // Ninguna fila partida: cada una está entera en algún trozo.
        assertTrue(filas.all { f -> trozos.any { it.contains(f) } })
    }

    /** Sin fechas, antes de una palabra; sin espacios tampoco, entera (no hay corte seguro). */
    @Test
    fun sin_fecha_se_parte_antes_de_una_palabra_y_sin_espacios_va_entera() {
        val palabras = (1..300).joinToString(" ") { "PALABRA$it" }
        val trozos = ClaudeStatementParser.partirLineaLarga(palabras, max = 200)
        assertEquals(palabras, trozos.joinToString(""))
        assertTrue(trozos.all { it.length <= 200 })
        assertTrue(trozos.drop(1).all { it.startsWith("PALABRA") }, "ninguna palabra partida: $trozos")

        val sinEspacios = "X".repeat(3_000)
        assertEquals(listOf(sinEspacios), ClaudeStatementParser.partirLineaLarga(sinEspacios, max = 1_000))
    }

    /** «1 MARCA» no es una fecha; «04 OCT» y «2026-10-04» sí. */
    @Test
    fun las_fechas_en_letras_y_en_iso_tambien_son_un_corte() {
        val linea = "04 OCT COMPRA 1 MARCA 10.000 2026-10-05 RAPPI 20.000 6 oct. EXITO 30.000"
        val trozos = ClaudeStatementParser.partirLineaLarga(linea, max = 30)
        assertEquals(listOf("04 OCT COMPRA 1 MARCA 10.000 ", "2026-10-05 RAPPI 20.000 ", "6 oct. EXITO 30.000"), trozos)
    }

    /** Y de punta a punta: el extracto de un solo renglón se lee entero. */
    @Test
    fun un_extracto_en_un_solo_renglon_se_lee_entero() = runBlocking {
        val lectura = ClaudeStatementParser.leerPorPedazos(
            extracto(300, separador = " "),
            maxCaracteres = ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO,
            anotar = {},
            pedir = modeloFalso(capacidad = 100),
        )

        assertEquals((1..300).toList(), numerosDe(lectura))
    }

    // ── 4. El encabezado viaja en cada sub-pedazo ────────────────────────────────────

    /**
     * El año sale del encabezado. Un pedazo partido en mitades tiene que mandarlo en todas las que
     * no arrancan el documento —también en la segunda mitad del PRIMER pedazo, que antes iba sin
     * nada porque el primer pedazo nunca se partía—.
     */
    @Test
    fun el_encabezado_viaja_en_cada_sub_pedazo() = runBlocking {
        val cuerpos = Collections.synchronizedList(mutableListOf<String>())

        ClaudeStatementParser.leerPorPedazos(
            extracto(300),
            maxCaracteres = ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO,
            aLaVez = 1,
            anotar = {},
            pedir = modeloFalso(capacidad = 80, cuerpos = cuerpos),
        )

        // 300 → 150 + 150 → cuatro cuartos de 75, que sí caben.
        val conRespuesta = cuerpos.filter { FILA.findAll(it).count() <= 80 }
        assertEquals(4, conRespuesta.size, "los cuatro cuartos")
        // El que arranca el documento ya trae el encabezado en su texto; todos los demás lo llevan.
        val queArrancan = conRespuesta.filter { it.startsWith("BANCOLOMBIA") }
        assertEquals(1, queArrancan.size)
        (conRespuesta - queArrancan.toSet()).forEach { cuerpo ->
            assertTrue(cuerpo.startsWith("ENCABEZADO DEL DOCUMENTO"), cuerpo.take(80))
            assertTrue(cuerpo.contains("DESDE 2026/09/25 HASTA 2026/10/24"))
            assertTrue(Regex("""PARTE 1(\.\d)+ DE 1 DEL EXTRACTO""").containsMatchIn(cuerpo), cuerpo.take(300))
        }
    }

    // ── 5. En paralelo, el orden se conserva ─────────────────────────────────────────

    /**
     * Los pedidos van de a tres y terminan en desorden (el último, primero). Las filas salen igual
     * en el orden del documento, y nunca hay más de tres pedidos a la vez.
     */
    @Test
    fun en_paralelo_las_filas_salen_en_orden_y_nunca_mas_de_tres_a_la_vez() = runBlocking {
        val enVuelo = AtomicInteger()
        val maximo = AtomicInteger()
        val lectura = ClaudeStatementParser.leerPorPedazos(
            extracto(400),
            maxCaracteres = 2_000,
            aLaVez = 3,
            anotar = {},
            pedir = modeloFalso(capacidad = 1_000) { cuerpo ->
                maximo.accumulateAndGet(enVuelo.incrementAndGet(), ::maxOf)
                // El que trae las filas más altas contesta más rápido.
                val primera = FILA.find(cuerpo)?.groupValues?.get(1)?.toInt() ?: 0
                delay((400 - primera).toLong() / 8)
                enVuelo.decrementAndGet()
            },
        )

        assertEquals((1..400).toList(), numerosDe(lectura))
        assertTrue(maximo.get() in 2..3, "a la vez: ${maximo.get()}")
    }

    // ── 6. El tamaño de arranque y el diagnóstico ────────────────────────────────────

    /** Un extracto denso arranca en pedazos más chicos; uno de tarjeta, en los 12.000 de siempre. */
    @Test
    fun el_tamano_inicial_sale_de_las_filas_probables() {
        val denso = extracto(600) // 31 caracteres por fila
        assertTrue(ClaudeStatementParser.tamanoInicialDelPedazo(denso) < 6_000)

        val holgado = encabezado.joinToString("\n") + "\n" +
            (1..100).joinToString("\n") { "04 OCT ${"COMPRA EN UN COMERCIO CON NOMBRE LARGO ".repeat(4)} 1 de 12 \$ 120.000,00" }
        assertEquals(ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO, ClaudeStatementParser.tamanoInicialDelPedazo(holgado))

        assertEquals(ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO, ClaudeStatementParser.tamanoInicialDelPedazo("sin fechas\nni filas"))
    }

    /** Una línea de log con los números de la lectura y ni una letra del extracto. */
    @Test
    fun el_diagnostico_no_lleva_texto_del_extracto() = runBlocking {
        var diagnostico: DiagnosticoDeLaLectura? = null
        val texto = extracto(300).replace("PAGO PSE", "SECRETO DE CARO")

        ClaudeStatementParser.leerPorPedazos(
            texto,
            maxCaracteres = ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO,
            anotar = { diagnostico = it },
            pedir = modeloFalso(capacidad = 100),
        )

        val linea = diagnostico!!.lineaDelLog()
        assertFalse(linea.contains("SECRETO"), linea)
        assertFalse(linea.contains("BANCOLOMBIA"), linea)
        assertFalse(linea.contains("MOV "), linea)
        assertTrue(linea.contains("caracteres=${texto.length}"), linea)
        assertTrue(linea.contains("lineas=${texto.lines().size}"), linea)
        assertTrue(linea.contains("cortes=${diagnostico!!.cortes}"), linea)
        assertTrue(linea.contains("16000*"), "el pedido cortado se marca: $linea")
    }

    // ── 7. Ronda de arreglos: plazo, tope, encabezado, filas de dos renglones, cuotas y errores ──

    /**
     * **El plazo.** La app deja de esperar a los 120 s; una lectura que pasa de 100 s es
     * incompleta, contesta a tiempo aunque haya un pedido bloqueado en vuelo, y no lanza más.
     */
    @Test
    fun cumplido_el_plazo_la_lectura_es_incompleta_a_tiempo_y_no_lanza_mas_pedidos() = runBlocking {
        val pedidos = AtomicInteger()
        var diagnostico: DiagnosticoDeLaLectura? = null
        val desde = System.nanoTime()

        val lectura = ClaudeStatementParser.leerPorPedazos(
            extracto(300),
            maxCaracteres = 3_000,
            aLaVez = 1,
            plazoMs = 300,
            anotar = { diagnostico = it },
            pedir = { _, _ ->
                pedidos.incrementAndGet()
                // Como el pedido de verdad: bloqueante, no se deja cancelar.
                withContext(Dispatchers.IO) { Thread.sleep(1_000) }
                RespuestaDelPedazo("[]", "end_turn", 10)
            },
        )
        val tardo = (System.nanoTime() - desde) / 1_000_000

        assertEquals(Lectura.Incompleta, lectura)
        assertTrue(tardo < 900, "contestó a los $tardo ms: esperó al pedido en vuelo")
        assertEquals(ClaudeStatementParser.MOTIVO_PLAZO, diagnostico!!.motivo)
        assertTrue(diagnostico!!.lineaDelLog().contains("resultado=incompleta(plazo)"))
        Thread.sleep(1_500)
        assertEquals(1, pedidos.get(), "después del plazo no sale ningún pedido más")
    }

    /**
     * **El tope de pedidos.** Dos pedazos que necesitan partirse dos veces cada uno son 14 pedidos;
     * el tope de dos pedazos es 2 × 3 + 4 = 10. Sin el tope la lectura saldría bien, tarde y cara.
     */
    @Test
    fun al_llegar_al_tope_de_pedidos_la_lectura_es_incompleta() = runBlocking {
        val pedidos = AtomicInteger()
        var diagnostico: DiagnosticoDeLaLectura? = null
        val texto = extracto(760)
        assertEquals(2, ClaudeStatementParser.dividirEnPedazos(texto, ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO).size)
        val modelo = modeloFalso(capacidad = 100)

        val lectura = ClaudeStatementParser.leerPorPedazos(
            texto,
            maxCaracteres = ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO,
            anotar = { diagnostico = it },
            pedir = { c, q -> pedidos.incrementAndGet(); modelo(c, q) },
        )

        assertEquals(Lectura.Incompleta, lectura)
        assertEquals(ClaudeStatementParser.MOTIVO_TOPE, diagnostico!!.motivo)
        assertTrue(pedidos.get() <= ClaudeStatementParser.topeDePedidos(2), "pedidos: ${pedidos.get()}")
    }

    /**
     * **El encabezado no trae filas.** En un extracto denso las 15 primeras líneas ya son
     * movimientos; el encabezado termina antes del primer renglón con fecha.
     */
    @Test
    fun el_encabezado_termina_antes_de_la_primera_fila() {
        val texto = extracto(50)
        assertEquals(encabezado.joinToString("\n"), ClaudeStatementParser.encabezadoDe(texto))
    }

    /** Una fila de dos renglones en la frontera: el corte va antes del renglón con fecha. */
    @Test
    fun partir_en_dos_no_parte_una_fila_de_dos_renglones() {
        val pedazo = listOf(
            "01/10 MOV 0001",
            "  REF 0000000001",
            "02/10 MOV 0002 COMPRA EN UN COMERCIO CON NOMBRE LARGO",
            "  REF 0000000002",
        ).joinToString("\n")
        // Por caracteres, la mitad cae entre «02/10 MOV 0002…» y su referencia.

        val (primera, segunda) = ClaudeStatementParser.partirEnDos(pedazo)!!

        assertEquals("01/10 MOV 0001\n  REF 0000000001", primera)
        assertTrue(segunda.startsWith("02/10 MOV 0002") && segunda.endsWith("REF 0000000002"), segunda)
    }

    /** «1/36» es la cuota 1 de 36, no una fecha; día y mes tienen que existir. */
    @Test
    fun una_cuota_no_es_una_fecha() {
        assertFalse(ClaudeStatementParser.empiezaConFecha("1/36 CUOTA COMPRA EXITO"))
        assertFalse(ClaudeStatementParser.empiezaConFecha("32/10 X"))
        assertFalse(ClaudeStatementParser.empiezaConFecha("01/13 X"))
        assertFalse(ClaudeStatementParser.empiezaConFecha("2026-13-01 X"))
        assertTrue(ClaudeStatementParser.empiezaConFecha("31/12 X"))
        assertTrue(ClaudeStatementParser.empiezaConFecha("1/8/26 X"))
        assertTrue(ClaudeStatementParser.empiezaConFecha("04 OCT X"))
        assertTrue(ClaudeStatementParser.empiezaConFecha("2026-10-04 X"))
        // Y no es un lugar para cortar una línea larga: sin otra fecha a mano, se corta antes de
        // una palabra, nunca antes de «1/36» como si ahí empezara una fila.
        val linea = "01/10 COMPRA EXITO CUOTA 1/36 VALOR 120.000 02/10 RAPPI 20.000"
        val trozos = ClaudeStatementParser.partirLineaLarga(linea, max = 40)
        assertEquals(linea, trozos.joinToString(""))
        assertTrue(trozos.none { it.startsWith("1/36") }, "$trozos")
    }

    private class ErrorDeLaApi : RuntimeException("401 de mentira")

    /**
     * **Un error de la API gana sobre la incompleta.** El primer pedazo da incompleta al instante;
     * el segundo estaba en vuelo (no se deja cancelar) y termina con un error de la API. Sale el
     * error —la ruta contesta su 503 de siempre—, no «extracto incompleto».
     */
    @Test
    fun un_error_de_la_api_en_paralelo_gana_sobre_la_incompleta() {
        val texto = extracto(60)
        assertEquals(2, ClaudeStatementParser.dividirEnPedazos(texto, 1_200).size)

        assertFailsWith<ErrorDeLaApi> {
            runBlocking {
                ClaudeStatementParser.leerPorPedazos(texto, maxCaracteres = 1_200, anotar = {}) { cuerpo, _ ->
                    if (cuerpo.contains("MOV 0001")) {
                        delay(50) // el otro pedido ya está en vuelo
                        RespuestaDelPedazo("[{", "max_tokens", 16_000)
                    } else {
                        withContext(NonCancellable) { delay(150) }
                        throw ErrorDeLaApi()
                    }
                }
            }
        }
    }

    /**
     * **Un corte con filas válidas.** El JSON cerró —con las primeras 100 filas de 300— pero
     * `stop_reason` dice `max_tokens`: no se confía en esas filas, se parte, y salen las 300, una
     * vez cada una. Confiar en ellas daría 100 filas (o repetidas) y otro número de pedidos.
     */
    @Test
    fun un_corte_con_filas_validas_se_parte_igual_y_no_repite_filas() = runBlocking {
        val pedidos = AtomicInteger()
        val modelo = modeloFalso(capacidad = 100, cortadoConFilasValidas = true)

        val lectura = ClaudeStatementParser.leerPorPedazos(
            extracto(300),
            maxCaracteres = ClaudeStatementParser.MAX_CARACTERES_POR_PEDAZO,
            aLaVez = 1,
            anotar = {},
            pedir = { c, q -> pedidos.incrementAndGet(); modelo(c, q) },
        )

        assertEquals((1..300).toList(), numerosDe(lectura))
        // 300 → dos de 150 (cortados) → cuatro de 75: 1 + 2 + 4.
        assertEquals(7, pedidos.get())
    }

    // ── 8. Última ronda: el año del período, el tiempo de cada pedido y las mitades parejas ──

    /**
     * **El período con fecha se queda en el encabezado.** Un renglón con fecha y sin monto (el
     * período, la línea del navegador de un PDF impreso) es contexto; la primera fila —fecha y
     * monto— ya no.
     */
    @Test
    fun el_periodo_con_fecha_se_queda_en_el_encabezado_y_la_primera_fila_no() {
        val texto = listOf(
            "4/11/26, 10:32 Sucursal Virtual Personas",
            "25/09/2026 - 24/10/2026",
            "BANCOLOMBIA",
            fila(1),
            fila(2),
        ).joinToString("\n")

        val encabezado = ClaudeStatementParser.encabezadoDe(texto)

        assertEquals("4/11/26, 10:32 Sucursal Virtual Personas\n25/09/2026 - 24/10/2026\nBANCOLOMBIA", encabezado)
        assertFalse(encabezado.contains("MOV 0001"))
    }

    /** Una fecha que deja una mitad casi vacía no gana: se corta en el renglón más cerca de la mitad. */
    @Test
    fun partir_en_dos_no_deja_una_mitad_casi_vacia_por_una_fecha() {
        val pedazo = (listOf("ENCABEZADO X", "01/10 MOV 0001 PAGO 12.345") +
            (1..9).map { "  DETALLE DE LA OPERACION NUMERO $it" }).joinToString("\n")

        val (primera, segunda) = ClaudeStatementParser.partirEnDos(pedazo)!!

        assertEquals(pedazo, primera + "\n" + segunda)
        assertTrue(primera.length >= pedazo.length / 4 && segunda.length >= pedazo.length / 4, "«$primera» | «$segunda»")
    }

    /** Cada pedido recibe lo que le queda al plazo de la lectura: es su `timeout`. */
    @Test
    fun cada_pedido_lleva_lo_que_le_queda_al_plazo() = runBlocking {
        val quedan = Collections.synchronizedList(mutableListOf<Long>())
        val modelo = modeloFalso(capacidad = 1_000)

        ClaudeStatementParser.leerPorPedazos(
            extracto(300),
            maxCaracteres = 3_000,
            aLaVez = 1,
            plazoMs = 5_000,
            anotar = {},
            pedir = { c, q -> quedan += q; delay(20); modelo(c, q) },
        )

        assertTrue(quedan.size >= 3)
        assertTrue(quedan.all { it in 1..5_000 }, "$quedan")
        assertTrue(quedan.zipWithNext().all { (a, b) -> b <= a } && quedan.last() < quedan.first(), "lo que queda baja: $quedan")
    }

    /** Una llamada bloqueante no retiene a quien la espera: cancelada la espera, contesta en el acto. */
    @Test
    fun una_llamada_bloqueante_no_retiene_la_cancelacion() = runBlocking {
        val desde = System.nanoTime()

        val r = withTimeoutOrNull(100) { ClaudeStatementParser.sinEsperarAlCancelar { Thread.sleep(2_000); "tarde" } }

        assertEquals(null, r)
        assertTrue((System.nanoTime() - desde) / 1_000_000 < 1_000)
    }

    /**
     * **Una incompleta contesta sin esperar el plazo completo**, aunque otro pedido siga bloqueado en
     * su llamada (como el de verdad: [ClaudeStatementParser.sinEsperarAlCancelar]).
     */
    @Test
    fun una_incompleta_no_espera_al_pedido_bloqueado_en_vuelo() = runBlocking {
        val texto = extracto(60)
        val desde = System.nanoTime()

        val lectura = ClaudeStatementParser.leerPorPedazos(texto, maxCaracteres = 1_200, anotar = {}) { cuerpo, _ ->
            if (cuerpo.contains("MOV 0001")) {
                delay(50)
                RespuestaDelPedazo("[{", "max_tokens", 16_000)
            } else {
                ClaudeStatementParser.sinEsperarAlCancelar { Thread.sleep(3_000) }
                RespuestaDelPedazo("[]", "end_turn", 10)
            }
        }

        assertEquals(Lectura.Incompleta, lectura)
        assertTrue((System.nanoTime() - desde) / 1_000_000 < 1_500, "esperó al pedido bloqueado")
    }

    /** Qué se reintenta: un 429 o un 5xx, dos veces, y solo si queda plazo. */
    @Test
    fun solo_se_reintenta_un_429_o_un_5xx_con_plazo() {
        assertEquals(2_000L, ClaudeStatementParser.esperaParaReintentar(429, vez = 0, quedaMs = 60_000))
        assertEquals(4_000L, ClaudeStatementParser.esperaParaReintentar(529, vez = 1, quedaMs = 60_000))
        assertEquals(null, ClaudeStatementParser.esperaParaReintentar(503, vez = 2, quedaMs = 60_000))
        assertEquals(null, ClaudeStatementParser.esperaParaReintentar(401, vez = 0, quedaMs = 60_000))
        assertEquals(null, ClaudeStatementParser.esperaParaReintentar(400, vez = 0, quedaMs = 60_000))
        assertEquals(null, ClaudeStatementParser.esperaParaReintentar(null, vez = 0, quedaMs = 60_000), "un corte por tiempo no")
        assertEquals(null, ClaudeStatementParser.esperaParaReintentar(429, vez = 0, quedaMs = 10_000), "sin plazo no")
    }

    private class ConStatus(val status: Int) : RuntimeException("status $status")

    /** Cancelada la lectura durante la espera, no sale ningún reintento más. */
    @Test
    fun una_lectura_cancelada_no_reintenta() = runBlocking {
        val intentos = AtomicInteger()

        val r = withTimeoutOrNull(300) {
            ClaudeStatementParser.conReintentos(60_000, statusDe = { (it as? ConStatus)?.status }) {
                intentos.incrementAndGet()
                throw ConStatus(429)
            }
        }
        delay(2_500)

        assertEquals(null, r)
        assertEquals(1, intentos.get())
    }

    /** Y con plazo, un 429 se reintenta hasta salir. */
    @Test
    fun un_429_se_reintenta() = runBlocking {
        val intentos = AtomicInteger()

        val r = ClaudeStatementParser.conReintentos(60_000, statusDe = { (it as? ConStatus)?.status }) {
            if (intentos.incrementAndGet() == 1) throw ConStatus(429)
            "listo"
        }

        assertEquals("listo", r)
        assertEquals(2, intentos.get())
    }
}
