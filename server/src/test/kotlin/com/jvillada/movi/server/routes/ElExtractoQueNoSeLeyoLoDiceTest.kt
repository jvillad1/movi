package com.jvillada.movi.server.routes

import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Una lectura que no trajo movimientos lo dice, y dice por qué
 *
 * Las tres maneras de no traer nada se contestaban con la misma `emptyList()`, y la ruta las
 * despachaba con un **200 y cero filas**: la pantalla de revisión abría en «0 nuevas · 0
 * coincidencias» con el botón de importar apagado, que se lee como «este mes ya estaba
 * conciliado». El dueño subía su extracto de Ahorros con ~80 movimientos y concluía que ya lo
 * había importado.
 *
 * Acá se fija el mapeo entero: cada falla con su 422 y su frase, y —lo que hace que la prueba
 * valga— que las frases sean **distintas entre sí**. Un mapeo que conteste lo mismo a las tres
 * vuelve al defecto original con otro código de estado.
 */
class ElExtractoQueNoSeLeyoLoDiceTest {

    private val unMovimiento = ParsedTransaction(
        id = "p1",
        date = "2026-08-01",
        merchant = "Éxito",
        amount = 48_900,
        currency = "COP",
        type = TransactionType.EXPENSE,
        category = "Mercado",
        description = "Mercado",
        rawText = "01/08 COMPRA EXITO 48.900",
    )

    @Test
    fun sin_clave_de_anthropic_no_es_culpa_del_archivo() {
        val msg = fallaDeLaLectura(ClaudeStatementParser.Lectura.SinLlave, esImagen = false)

        assertEquals(LECTURA_SIN_LLAVE, msg)
        assertTrue(msg!!.contains("Tu archivo no tiene nada malo"), msg)
    }

    @Test
    fun una_lectura_cortada_le_pide_partir_el_archivo() {
        assertEquals(
            EXTRACTO_INCOMPLETO,
            fallaDeLaLectura(ClaudeStatementParser.Lectura.Incompleta, esImagen = false),
        )
    }

    /**
     * Una imagen no se puede partir en quincenas: el consejo tiene que ser otro. Es el único
     * lugar donde los dos caminos contestan distinto, y por eso está probado.
     */
    @Test
    fun una_imagen_cortada_le_pide_otra_cosa() {
        assertEquals(
            IMAGEN_INCOMPLETA,
            fallaDeLaLectura(ClaudeStatementParser.Lectura.Incompleta, esImagen = true),
        )
    }

    @Test
    fun una_lectura_que_salio_bien_pero_vacia_tambien_se_dice() {
        assertEquals(
            EXTRACTO_SIN_MOVIMIENTOS,
            fallaDeLaLectura(ClaudeStatementParser.Lectura.Ok(emptyList()), esImagen = false),
        )
    }

    @Test
    fun una_lectura_con_movimientos_no_falla() {
        assertNull(fallaDeLaLectura(ClaudeStatementParser.Lectura.Ok(listOf(unMovimiento)), esImagen = false))
    }

    /**
     * **Las tres frases distintas.** Es la afirmación que impide volver al defecto: si las tres
     * dijeran lo mismo, el dueño seguiría sin saber si tiene que partir el archivo, avisar que
     * falta una clave, o buscar otro PDF.
     */
    @Test
    fun cada_falla_dice_algo_distinto() {
        val frases = listOf(LECTURA_SIN_LLAVE, EXTRACTO_INCOMPLETO, IMAGEN_INCOMPLETA, EXTRACTO_SIN_MOVIMIENTOS)

        assertEquals(frases.size, frases.toSet().size, "dos fallas con la misma frase no se distinguen")
        assertTrue(frases.all { it.isNotBlank() })
    }

    /**
     * **Y ninguna se pasa de 200 caracteres.**
     *
     * No es una regla de estilo: `Throwable.toUserMessage()` (en `:shared`) descarta el cuerpo del
     * server cuando pasa de 200 y cae a un texto genérico. Una frase de 210 caracteres llega a la
     * pantalla convertida en «Algo salió mal» — o sea, exactamente el defecto que estas constantes
     * vinieron a arreglar, con un rodeo más largo.
     */
    @Test
    fun ninguna_frase_se_pasa_del_largo_que_el_cliente_muestra() {
        listOf(LECTURA_SIN_LLAVE, EXTRACTO_INCOMPLETO, IMAGEN_INCOMPLETA, EXTRACTO_SIN_MOVIMIENTOS).forEach {
            assertTrue(it.length <= 200, "«$it» tiene ${it.length} caracteres y el cliente la descarta")
        }
    }
}
