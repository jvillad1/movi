package com.jvillada.movi.server.parsing

import com.jvillada.movi.server.parsing.ClaudeStatementParser.DiagnosticoDeLaLectura
import com.jvillada.movi.server.parsing.ClaudeStatementParser.Lectura
import com.jvillada.movi.server.parsing.ClaudeStatementParser.RespuestaDelPedazo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /** Lo que el pedido trae para leer: sin el encabezado de contexto, que «no tiene movimientos». */
    private fun loQueSeLee(cuerpo: String): String =
        if (cuerpo.startsWith("ENCABEZADO DEL DOCUMENTO")) cuerpo.substringAfter(" DEL EXTRACTO:\n") else cuerpo

    private fun json(numeros: List<String>) = numeros.joinToString(",", "[", "]") {
        """{"date":"2026-10-01","merchant":"MOV $it","amount":12345,"type":"EXPENSE","category":"Otros","description":"","rawText":""}"""
    }

    /** El modelo falso: más de [capacidad] filas en un pedido → respuesta cortada. */
    private fun modeloFalso(
        capacidad: Int,
        cuerpos: MutableList<String> = Collections.synchronizedList(mutableListOf()),
        antes: suspend (String) -> Unit = {},
    ): suspend (String) -> RespuestaDelPedazo = { cuerpo ->
        cuerpos += cuerpo
        antes(cuerpo)
        val numeros = FILA.findAll(loQueSeLee(cuerpo)).map { it.groupValues[1] }.toList()
        if (numeros.size > capacidad) {
            RespuestaDelPedazo("""[{"date":"2026-10-01","merchant":"MO""", "max_tokens", 16_000)
        } else {
            RespuestaDelPedazo(json(numeros), "end_turn", numeros.size * 90L)
        }
    }

    private fun numerosDe(lectura: Lectura): List<Int> =
        assertIs<Lectura.Ok>(lectura).movimientos.map { it.merchant.removePrefix("MOV ").toInt() }

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
            pedir = { pedidos.incrementAndGet(); siempreCortado(it) },
        )

        assertEquals(Lectura.Incompleta, lectura)
        assertEquals("incompleta", diagnostico!!.resultado)
        // 1 + 2 + 4 + 8 + 16 si se partiera a profundidad 4 sin cancelar: nunca más que eso.
        assertTrue(pedidos.get() in 2..31, "pedidos: ${pedidos.get()}")
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
            pedir = { pedidos.incrementAndGet(); modeloFalso(capacidad = 5)(it) },
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
        val conRespuesta = cuerpos.filter { FILA.findAll(loQueSeLee(it)).count() <= 80 }
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
                val primera = FILA.find(loQueSeLee(cuerpo))?.groupValues?.get(1)?.toInt() ?: 0
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
}
