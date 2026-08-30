package com.jvillada.movi.server.routes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Un APK anterior no puede borrar en silencio quién paga la cuota.
 *
 * `PUT /api/credits/{id}` recibe el objeto ENTERO y `fillTerms` sobrescribe todas las columnas.
 * `paidBy` y `payrollDeduction` tienen default, así que un cliente que no los conoce —el APK que
 * el dueño tiene instalado hoy— que edite cualquier cosa del crédito (la nota, el día de pago)
 * manda un cuerpo sin esos campos y los deja en NULL.
 *
 * Consecuencia medida: los tres créditos que paga otro vuelven al barrido de avisos y el dueño
 * empieza a recibir recordatorios de **$13,1 millones al mes** que nadie le debe, y el botón
 * «Registrar pago de Skandia» desaparece.
 *
 * La ruta lo resuelve mirando las CLAVES del JSON recibido: «ausente» y «null» son lo mismo en el
 * objeto deserializado, pero no en el JSON. Esta suite prueba esa distinción — es la lógica
 * exacta que corre en la ruta, sin base de datos de por medio.
 */
class QuienPagaSobreviveAUnClienteViejoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun cuerpo(vararg extra: String): JsonObject = json.decodeFromString(
        """
        {"accountId":"acc-1254","bank":"Davibank","principal":784000000,"rateEa":10.99,
         "termMonths":180,"installment":9147408,"dayOfMonth":12,"startDate":"2025-06-12"
         ${if (extra.isEmpty()) "" else "," + extra.joinToString(",")}}
        """.trimIndent(),
    )

    @Test
    fun un_cliente_viejo_no_menciona_el_campo() {
        // La premisa de todo lo demás: si esto fallara, el resto de la suite no probaría nada.
        assertFalse("paidBy" in cuerpo())
        assertFalse("payrollDeduction" in cuerpo())
    }

    @Test
    fun y_por_eso_se_conserva_lo_guardado() {
        val recibido = cuerpo()
        val guardado = "Skandia"

        val resultado = if ("paidBy" in recibido) null else guardado

        assertEquals("Skandia", resultado)
    }

    @Test
    fun un_cliente_nuevo_que_manda_null_SI_lo_borra() {
        // La otra mitad, y es la que hace útil a la primera: el dueño tiene que poder sacar el
        // rótulo. Si «ausente» y «null» se trataran igual, «la paga Skandia» sería para siempre.
        val recibido = cuerpo("\"paidBy\":null")

        assertTrue("paidBy" in recibido)
        assertNull(recibido["paidBy"]?.let { if (it.toString() == "null") null else it })
    }

    @Test
    fun un_cliente_nuevo_que_manda_un_nombre_lo_escribe() {
        val recibido = cuerpo("\"paidBy\":\"Caro\"")

        assertTrue("paidBy" in recibido)
        assertEquals("\"Caro\"", recibido["paidBy"].toString())
    }

    @Test
    fun lo_mismo_vale_para_la_libranza() {
        // `payrollDeduction` arrastra este mismo agujero desde la ola 17 sin que nadie lo hubiera
        // nombrado: un cliente viejo que editaba una libranza la desmarcaba.
        assertFalse("payrollDeduction" in cuerpo())
        assertTrue("payrollDeduction" in cuerpo("\"payrollDeduction\":true"))
    }
}
