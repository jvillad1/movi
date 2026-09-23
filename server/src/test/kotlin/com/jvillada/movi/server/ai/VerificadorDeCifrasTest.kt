package com.jvillada.movi.server.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El verificador de cifras
 *
 * Dos errores posibles, con costos distintos, y las pruebas cuidan los dos:
 *
 * - **Dejar pasar una cifra inventada** es el error que esto vino a medir (los $185.831 del 23-sep).
 * - **Marcar una cifra buena** cuesta un reintento —plata— y, si el reintento tampoco la «arregla»,
 *   una línea que le dice al dueño que no se pudo verificar algo que estaba bien. Por eso la mitad de
 *   estas pruebas son de lo que NO se tiene que marcar: los formatos de la app, los redondeos, los
 *   años, los días, las cuotas y las colas de cuenta.
 */
class VerificadorDeCifrasTest {

    /** Los datos como los ve el modelo: el contexto dice los montos sin puntos, como `$204183376`. */
    private val datos = listOf(
        """
        == Créditos ==
        - Hipotecario 2334 (Davibank): debe ${'$'}204183376; cuota ${'$'}2613714 el día 5, tasa 15.24 % EA, plazo 180 meses, incluye seguros por ${'$'}209219 al mes.

        == Presupuestos de este período ==
        - Fútbol: límite ${'$'}400000, gastado ${'$'}963456 — SE PASÓ por ${'$'}563456

        == Patrimonio ==
        - Bienes (inmuebles, vehículos; no es plata): ${'$'}2191000000

        == Suscripciones activas ==
        - Claude: ${'$'}284000 al mes, el día 3 (el cobro real es USD ${'$'}71 al mes, convertido a la TRM de hoy)
        """.trimIndent(),
    )

    private fun sinRespaldo(respuesta: String, trampas: Map<Long, String> = emptyMap()) =
        cifrasSinRespaldo(respuesta, datos, trampas)

    // ── Lo que tiene respaldo ──────────────────────────────────────────────────

    @Test
    fun `una cifra con puntos de miles se encuentra en un dato escrito sin puntos`() {
        assertEquals(emptyList(), sinRespaldo("Debes \$204.183.376 y la cuota es de \$2.613.714."))
    }

    @Test
    fun `los formatos compactos de la app se leen con su propio redondeo`() {
        assertEquals(emptyList(), sinRespaldo("Debes unos \$204,2M; la cuota ronda \$2,6M."))
        assertEquals(emptyList(), sinRespaldo("Tus bienes suman \$2.191M."), "millones con punto de miles, como la pantalla")
        assertEquals(emptyList(), sinRespaldo("Unos 204 millones de pesos."))
        assertEquals(emptyList(), sinRespaldo("El seguro son \$209 mil al mes."))
    }

    @Test
    fun `los porcentajes y los dolares se reconocen en los formatos de la app`() {
        assertEquals(emptyList(), sinRespaldo("La tasa es 15,24 % EA."))
        assertEquals(emptyList(), sinRespaldo("La tasa es 15.24% EA."))
        assertEquals(emptyList(), sinRespaldo("Unos 15,2 % al año."), "redondeado a un decimal sigue siendo el dato")
        assertEquals(emptyList(), sinRespaldo("Claude te cobra US\$71 al mes."))
        assertEquals(emptyList(), sinRespaldo("Claude te cobra USD 71 al mes."))
    }

    @Test
    fun `los miles con coma a la inglesa tambien son el dato`() {
        assertEquals(emptyList(), sinRespaldo("Tu cuota es de \$2,613,714."))
    }

    @Test
    fun `un redondeo con ceros al final se acepta, pero no se estira mas del uno por ciento`() {
        assertEquals(emptyList(), sinRespaldo("Unos \$2.613.700 de cuota."))
        assertEquals(emptyList(), sinRespaldo("Unos \$2.600.000 de cuota."))
        assertEquals(listOf("\$2.500.000"), sinRespaldo("Unos \$2.500.000 de cuota."), "4 % de distancia ya es otra cifra")
    }

    /** Lo que un asesor dice legítimamente: una resta de dos datos del mismo renglón. */
    @Test
    fun `la resta de dos datos del mismo renglon tiene respaldo`() {
        // cuota − seguro = lo que queda de la cuota
        assertEquals(emptyList(), sinRespaldo("Después del seguro te quedan \$2.404.495 de la cuota."))
        // límite − gastado, ya hecho en el renglón, y también su suma
        assertEquals(emptyList(), sinRespaldo("Te pasaste por \$563.456."))
        assertEquals(emptyList(), sinRespaldo("Entre límite y gasto van \$1.363.456."))
    }

    /** Una cuenta hecha a la vista con dos cifras respaldadas se puede comprobar aunque sean de bloques distintos. */
    @Test
    fun `una operacion escrita con dos cifras respaldadas se acepta aunque sean de bloques distintos`() {
        val respuesta = "Tu cuota (\$2.613.714) menos lo que te pasaste en Fútbol (\$563.456) da \$2.050.258."
        assertEquals(emptyList(), sinRespaldo(respuesta))
    }

    @Test
    fun `sin la operacion a la vista, dos datos de bloques distintos no se suman solos`() {
        assertEquals(listOf("\$2.050.258"), sinRespaldo("Te quedarían \$2.050.258."))
    }

    @Test
    fun `las cifras que dijo el usuario tambien son datos`() {
        val fuentes = datos + "¿Y si abono \$10.000.000 al Hipotecario 2334?"
        assertEquals(
            emptyList(),
            cifrasSinRespaldo("Si abonas \$10.000.000, la deuda pasa de \$204.183.376 a \$194.183.376.", fuentes),
            "la resta está a la vista: los dos operandos están citados y respaldados",
        )
    }

    // ── Lo que NO es plata ─────────────────────────────────────────────────────

    @Test
    fun `anos, dias, cuotas y colas de cuenta no se confunden con plata`() {
        val respuesta = "El Hipotecario 2334 vence el día 5; en 2026 le quedan 180 cuotas y lo revisamos el 25 de octubre."
        assertEquals(emptyList(), cifrasDe(respuesta), "nada de eso lleva marca de plata ni de porcentaje")
        assertEquals(emptyList(), sinRespaldo(respuesta))
    }

    @Test
    fun `un porcentaje solo lo respalda un porcentaje de los datos, no un plazo`() {
        assertEquals(listOf("180 %"), sinRespaldo("Eso es un 180 % de lo normal."), "180 son los meses del plazo, no un porcentaje")
    }

    @Test
    fun `cero pesos no necesita respaldo`() {
        assertEquals(emptyList(), sinRespaldo("Este mes no gastaste nada: \$0."))
    }

    // ── Lo que no tiene respaldo ───────────────────────────────────────────────

    @Test
    fun `una cifra inventada se marca tal como la escribio el modelo`() {
        assertEquals(listOf("\$1.234.567"), sinRespaldo("Te sobran \$1.234.567 este mes."))
        assertEquals(listOf("\$3,7M"), sinRespaldo("Te sobran \$3,7M este mes."))
        assertEquals(listOf("12 %"), sinRespaldo("Eso es un 12 % de tu sueldo."))
    }

    @Test
    fun `una cifra repetida se marca una sola vez`() {
        assertEquals(listOf("\$1.234.567"), sinRespaldo("Son \$1.234.567. Repito: \$1.234.567."))
    }

    /**
     * **La trampa: una resta válida con el significado equivocado.** `cuota − interés` son dos datos
     * del mismo renglón, así que por números solos pasa; declarada como trampa, se marca igual.
     */
    @Test
    fun `una cifra trampa se marca aunque sea la resta de dos datos`() {
        val fuentes = datos + "Interés de este mes: \$2.427.883"
        val respuesta = "La diferencia es apenas \$185.831 al mes, que es lo que baja la deuda."
        assertEquals(emptyList(), cifrasSinRespaldo(respuesta, fuentes + "\$2613714 \$2427883"), "sin trampa, es una resta legítima")
        assertEquals(
            listOf("\$185.831"),
            cifrasSinRespaldo(respuesta, fuentes, trampas = mapOf(185_831L to "cuota − interés sin seguros")),
        )
    }

    @Test
    fun `una trampa no marca una cifra que esta literalmente en los datos`() {
        assertEquals(emptyList(), sinRespaldo("El seguro es \$209.219.", trampas = mapOf(209_219L to "x")))
    }

    // ── La línea honesta ───────────────────────────────────────────────────────

    @Test
    fun `la linea honesta se agrega una vez y verificar dos veces da lo mismo`() {
        val respuesta = "Te sobran \$1.234.567 este mes."
        val marcadas = sinRespaldo(respuesta)
        val conLinea = conLineaHonesta(respuesta, marcadas)

        assertTrue(conLinea.endsWith("$NO_PUDE_VERIFICAR \$1.234.567."), conLinea)
        assertEquals(conLinea, conLineaHonesta(conLinea, marcadas), "la línea no se duplica")
        assertEquals(marcadas, sinRespaldo(conLinea), "la línea no se verifica a sí misma")
        assertEquals(respuesta, conLineaHonesta(respuesta, emptyList()), "sin cifras marcadas no se toca nada")
    }

    /** El teléfono reenvía la respuesta anterior: su línea honesta no puede blanquear sus propias cifras. */
    @Test
    fun `una linea honesta de un turno anterior no respalda sus cifras`() {
        val turnoAnterior = conLineaHonesta("Tu cuota es \$2.613.714 y te sobran \$1.234.567 este mes.", listOf("\$1.234.567"))
        assertEquals(
            listOf("\$1.234.567"),
            cifrasSinRespaldo("Como te dije, te sobran \$1.234.567.", datos + turnoAnterior),
        )
        assertEquals(
            emptyList(),
            cifrasSinRespaldo("Tu cuota sigue en \$2.613.714.", listOf(turnoAnterior)),
            "lo que no se le marcó a esa respuesta sigue valiendo",
        )
    }

    @Test
    fun `el mensaje de correccion nombra las cifras y deja decir que no se sabe`() {
        val mensaje = mensajeDeCorreccion(listOf("\$185.831"))
        assertTrue("\$185.831" in mensaje)
        assertTrue("di que no la sabes" in mensaje)
        assertTrue("No menciones esta revisión" in mensaje)
    }

    // ── La lectura de números ──────────────────────────────────────────────────

    @Test
    fun `lee los numeros como los escribe una persona en Colombia`() {
        assertEquals(2_613_714.0 to 0, leerNumero("2.613.714"))
        assertEquals(15.24 to 2, leerNumero("15,24"))
        assertEquals(15.24 to 2, leerNumero("15.24"))
        assertEquals(2.6 to 1, leerNumero("2,6", conMultiplicador = true))
        assertEquals(2_191.0 to 0, leerNumero("2.191", conMultiplicador = true), "«\$2.191M» son dos mil ciento noventa y un millones")
        assertEquals(2_613_714.0 to 0, leerNumero("2,613,714"))
    }

    // ── El total de varias cifras citadas (caso real del 23-sep) ─────────────────

    private val periodo = listOf(
        """
        == En qué se fue la plata del período ==
        - Cuota de crédito: ${'$'}12920200
        - Gardenera: ${'$'}9964910
        - Hija: ${'$'}4362300
        - Mercado: ${'$'}2000000
        - Comida: ${'$'}1232430
        """.trimIndent(),
    )

    /**
     * «¿Por qué este período salieron $11,7M más de los que entraron?»: el borrador enumeró cuatro
     * categorías y las totalizó bien ($29.247.410). Solo se aceptaban cuentas de DOS números, así que
     * se marcó y se pagó un reintento con el modelo de consejos para borrar una cifra correcta.
     */
    @Test
    fun `el total exacto de cuatro cifras citadas y respaldadas tiene respaldo`() {
        val respuesta = "Cuota de crédito \$12.920.200, Gardenera \$9.964.910, Hija \$4.362.300 y " +
            "Mercado \$2.000.000: esas cuatro suman \$29.247.410."
        assertEquals(emptyList(), cifrasSinRespaldo(respuesta, periodo))
    }

    /** Lo que NO se acepta: un total de cifras que la respuesta no citó, aunque estén en los datos. */
    @Test
    fun `un total de cifras que la respuesta no dijo sigue sin respaldo`() {
        // 12.920.200 + 9.964.910 + 4.362.300 = 27.247.410, pero la respuesta no nombró ninguna.
        val respuesta = "Tus tres gastos más grandes suman \$27.247.410."
        assertEquals(listOf("\$27.247.410"), cifrasSinRespaldo(respuesta, periodo))
    }

    /** Ni un total que no cuadra con ningún subconjunto de lo citado. */
    @Test
    fun `un total que no cuadra con lo citado sigue sin respaldo`() {
        val respuesta = "Cuota de crédito \$12.920.200, Gardenera \$9.964.910 y Hija \$4.362.300 suman \$30.000.000."
        assertEquals(listOf("\$30.000.000"), cifrasSinRespaldo(respuesta, periodo))
    }
}
