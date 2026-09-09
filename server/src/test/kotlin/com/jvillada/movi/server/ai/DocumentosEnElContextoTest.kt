package com.jvillada.movi.server.ai

import com.jvillada.movi.shared.model.TipoDeDocumento
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El armado del bloque «Documentos guardados», sin base de datos.
 *
 * Lo que se fija acá es la FORMA del bloque: cómo agrupa, qué pasa cuando no entra todo y qué
 * pasa cuando no hay nada. El camino que va de la tabla al contexto —y sobre todo que la
 * consulta no pida los bytes— se prueba en [DocumentosEnElContextoDeMoviAiTest].
 */
class DocumentosEnElContextoTest {

    private fun doc(
        nombre: String,
        notas: String? = "una nota cualquiera",
        cuenta: String? = null,
        periodo: String? = null,
        subidoEn: Long = 1_000L,
        tipo: TipoDeDocumento = TipoDeDocumento.EXTRACTO,
    ) = DocumentoParaContexto(
        nombre = nombre,
        tipo = tipo,
        accountId = cuenta,
        periodo = periodo,
        notas = notas,
        subidoEn = subidoEn,
    )

    @Test
    fun `sin documentos el bloque no existe`() {
        // No es «(sin documentos)»: un renglón para avisar que no hay nada que mirar cuesta
        // tokens en cada mensaje del chat y no cambia ninguna respuesta.
        assertEquals("", renderizarDocumentos(emptyList(), emptyMap()))
    }

    @Test
    fun `cada documento queda bajo el encabezado de SU cuenta`() {
        val bloque = renderizarDocumentos(
            listOf(
                doc("Extracto_2334_08_2026.pdf", cuenta = "acc-crediagil", subidoEn = 300),
                doc("TC_Master_3684_09_2026.pdf", cuenta = "acc-master", subidoEn = 200),
                doc("Extracto_2334_07_2026.pdf", cuenta = "acc-crediagil", subidoEn = 100),
            ),
            mapOf("acc-crediagil" to "Crediágil 2334", "acc-master" to "Mastercard 3684"),
        )

        // No alcanza con que los nombres aparezcan: tienen que caer DENTRO del grupo correcto.
        // Si el agrupamiento se rompe (o se aplana la lista), estos órdenes se cruzan.
        val crediagil = bloque.indexOf("[Crediágil 2334]")
        val master = bloque.indexOf("[Mastercard 3684]")
        assertTrue(crediagil in 0..<master, "los dos encabezados van, y Crediágil primero:\n$bloque")
        assertTrue(bloque.indexOf("Extracto_2334_08_2026.pdf") in crediagil..<master, bloque)
        assertTrue(bloque.indexOf("Extracto_2334_07_2026.pdf") in crediagil..<master, bloque)
        assertTrue(bloque.indexOf("TC_Master_3684_09_2026.pdf") > master, bloque)
    }

    @Test
    fun `los que no cuelgan de ninguna cuenta van juntos y al final`() {
        // Dos documentos sin cuenta es el caso REAL del dueño (un crédito saldado y una cuenta
        // con $61), y el más viejo de todos: sin la regla de dejarlos al final, el grupo se
        // colaría en el medio nada más que por la fecha.
        val bloque = renderizarDocumentos(
            listOf(
                doc("poliza_hdi.pdf", cuenta = null, subidoEn = 900),
                doc("Extracto_2334_08_2026.pdf", cuenta = "acc-crediagil", subidoEn = 300),
            ),
            mapOf("acc-crediagil" to "Crediágil 2334"),
        )

        val sinCuenta = bloque.indexOf("[$SIN_CUENTA]")
        assertTrue(sinCuenta > bloque.indexOf("[Crediágil 2334]"), "va al final:\n$bloque")
        assertTrue(bloque.indexOf("poliza_hdi.pdf") > sinCuenta, bloque)
    }

    @Test
    fun `un documento de una cuenta que ya no existe no inventa un encabezado con el id`() {
        val bloque = renderizarDocumentos(
            listOf(doc("carta_del_banco.pdf", cuenta = "acc-borrada")),
            emptyMap(),
        )

        assertFalse(bloque.contains("acc-borrada"), "el id crudo no le dice nada al modelo:\n$bloque")
        assertTrue(bloque.contains("[$SIN_CUENTA]"), bloque)
        assertTrue(bloque.contains("carta_del_banco.pdf"), bloque)
    }

    @Test
    fun `el nombre, el tipo, el periodo y las notas viajan enteros`() {
        val bloque = renderizarDocumentos(
            listOf(
                doc(
                    nombre = "Poliza_vida_deudor.pdf",
                    tipo = TipoDeDocumento.CONTRATO,
                    periodo = "2026",
                    notas = "Poliza de vida deudor HDI 625574 cert. 69193, prima 835.200 al ano = 69.600 AL MES.",
                ),
            ),
            emptyMap(),
        )

        assertTrue(bloque.contains("CONTRATO"), bloque)
        assertTrue(bloque.contains("\"Poliza_vida_deudor.pdf\""), bloque)
        assertTrue(bloque.contains("(período 2026)"), bloque)
        // La nota es EL dato: es de donde sale «el 2334 paga 69.600 al mes de seguro».
        assertTrue(bloque.contains("prima 835.200 al ano = 69.600 AL MES."), bloque)
    }

    @Test
    fun `una nota escrita en varias lineas no parte el renglon en dos`() {
        val bloque = renderizarDocumentos(
            listOf(doc("extracto.pdf", notas = "Saldo 507.553.\nTasa 29,64 EA.\n\nComisión 21.640.")),
            emptyMap(),
        )

        val renglonesDeDocumento = bloque.lines().count { it.startsWith("- ") }
        assertEquals(1, renglonesDeDocumento, "un documento, un renglón:\n$bloque")
        assertTrue(bloque.contains("Saldo 507.553. Tasa 29,64 EA. Comisión 21.640."), bloque)
    }

    @Test
    fun `sin notas lo dice, en vez de mostrar un renglon a medias`() {
        val bloque = renderizarDocumentos(listOf(doc("recibo.jpg", notas = null)), emptyMap())
        assertTrue(bloque.contains("(sin notas)"), bloque)
    }

    @Test
    fun `los 30 documentos del dueño entran enteros en el presupuesto`() {
        // **Esta es la medición.** Las notas son las de verdad (largo y todo), repetidas hasta
        // llegar a los 30 documentos que hay guardados hoy. Si mañana alguien baja el
        // presupuesto sin mirar, esta prueba avisa que empezó a recortar datos reales.
        val notasReales = listOf(
            "Extracto del Crediagil. Tasa 29,64 EA, seguro 1.960, saldo 507.553. Trae la comision de 21.640 + IVA al mes.",
            "Movimientos al 7-sep. Aca esta la ABONO AMPLIACION DE PLAZO del 19-ago que borro el saldo entero, y la columna Cuotas que muestra el 36 por defecto.",
            "Poliza de vida deudor HDI 625574 cert. 69193, prima 835.200 al ano = 69.600 AL MES.",
        )
        val treinta = (1..30).map { i ->
            doc(
                nombre = "Extracto_Bancolombia_9695_0${i % 9 + 1}_2026.pdf",
                notas = notasReales[i % notasReales.size],
                cuenta = "acc-${i % 6}",
                periodo = "2026-0${i % 9 + 1}",
                subidoEn = i.toLong(),
            )
        }
        val nombres = (0..5).associate { "acc-$it" to "Cuenta número $it" }

        val bloque = renderizarDocumentos(treinta, nombres)

        assertTrue(bloque.length < PRESUPUESTO_DE_DOCUMENTOS, "midió ${bloque.length} caracteres")
        assertTrue(bloque.contains("== Documentos guardados (30) =="), bloque)
        assertFalse(bloque.contains("AVISO"), "no recortó nada, así que no avisa nada:\n$bloque")
    }

    @Test
    fun `cuando el presupuesto muerde, el bloque LO DICE y conserva lo más reciente`() {
        // El punto no es el recorte: es que un asistente que ve una lista recortada sin saberlo
        // contesta «no tienes ninguna póliza guardada», que es peor que no contestar.
        val muchos = (1..40).map { i ->
            doc(nombre = "documento_$i.pdf", notas = "n".repeat(400), subidoEn = i.toLong())
        }

        val bloque = renderizarDocumentos(muchos, emptyMap(), presupuesto = 1_200)

        assertTrue(bloque.contains("AVISO"), bloque)
        assertTrue(bloque.contains("hay 40 documentos guardados"), bloque)
        assertTrue(bloque.contains("NO digas que no existe"), bloque)
        // Y lo que quedó es lo NUEVO, no lo primero que salió de la lista.
        assertTrue(bloque.contains("documento_40.pdf"), bloque)
        assertFalse(bloque.contains("documento_1.pdf"), bloque)
    }

    @Test
    fun `un solo documento mas largo que todo el presupuesto entra igual`() {
        // Un bloque que dijera «0 de 1» sería peor que el bloque que no existe.
        val bloque = renderizarDocumentos(
            listOf(doc("gigante.pdf", notas = "x".repeat(500))),
            emptyMap(),
            presupuesto = 10,
        )

        assertTrue(bloque.contains("gigante.pdf"), bloque)
        assertTrue(bloque.contains("== Documentos guardados (1) =="), bloque)
        assertFalse(bloque.contains("AVISO"), "entró todo lo que había:\n$bloque")
    }

    @Test
    fun `el bloque le pide al asistente que nombre el documento del que saca la cifra`() {
        val bloque = renderizarDocumentos(listOf(doc("TC_Master_3684_09_2026.pdf")), emptyMap())
        assertTrue(
            bloque.contains("di de qué documento sale"),
            "sin esto el modelo afirma cifras sin respaldo:\n$bloque",
        )
    }
}
