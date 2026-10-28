package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Los campos de texto son los del sistema
 *
 * Vuelta de diseño 6. Las hojas y pantallas que piden datos armaban cada una su caja de texto
 * —`BasicTextField` con el radio, el relleno, el borde y el texto guía que le tocara—, y por eso no
 * había dos iguales: rótulos en mayúsculas, el nombre del campo metido en el texto guía (que se
 * borra al escribir), la plata con la guía en la letra de las cifras. Ahora el campo es uno:
 * [Campo] y [CampoDeMonto], en `ui/components/ds/Campo.kt`.
 *
 * Esta prueba falla si aparece un `BasicTextField`, `TextField` u `OutlinedTextField` en `ui/` fuera
 * de `ui/components/` y de la lista corta de [EXCEPCIONES]. **Si te falló**: usa `Campo` o
 * `CampoDeMonto` (rótulo, guía, ayuda de una línea con «¿Qué es esto?», error, tag), o
 * `CampoDeBusqueda` si lo que se escribe filtra una lista. Si de verdad
 * hace falta otra cosa, agrégala al sistema (y a `docs/diseno/sistema.md`) antes que a esta lista.
 */
class LosCamposSonDelSistemaTest {

    private val campoSuelto = Regex("""(?<![\w.])(?:Basic|Outlined)?(?:Secure)?TextField\s*\(""")

    /**
     * Lo que se queda con su campo propio, y por qué. Rutas relativas a `ui/`.
     *
     * Agregar (`quickadd/QuickAddScreen.kt`) salió en la vuelta de diseño 13: su nota es el `Campo`
     * del sistema. Lo que le queda propio —el teclado numérico y el monto grande— no es un campo de
     * texto (son teclas y un `Text`), así que no hay nada que exceptuar.
     *
     * Buscar salió en la vuelta 35: su caja es el `CampoDeBusqueda` del sistema
     * (`ui/components/ds/CampoDeBusqueda.kt`), el mismo de Categorías, del selector de categoría y
     * de Personas y comercios.
     */
    private val EXCEPCIONES = mapOf(
        "ai/AIChatScreen.kt" to "el compositor del chat de Movi AI",
        "sdui/editor/ScreenEditorScreen.kt" to "el Editor de pantallas (admin)",
        "primeruso/PrimerUsoScreen.kt" to "la guía de primer uso (otra rama la está tocando)",
        "sms/SMSScreens.kt" to "la fila «Comercio» de Reconciliar, que se edita en su lugar: es una Fila",
    )

    private fun dondeHayCampos(): Map<String, List<String>> {
        val ui = LecturaDelCodigo.archivos(LecturaDelCodigo.UI, "${LecturaDelCodigo.UI}/components")
        return LecturaDelCodigo.dondeAparece(ui, campoSuelto)
            .groupBy { it.substringBeforeLast(':').substringAfter("${LecturaDelCodigo.UI}/") }
    }

    @Test
    fun `no hay campos de texto sueltos fuera del sistema`() {
        val fuera = dondeHayCampos().filterKeys { it !in EXCEPCIONES }
        assertTrue(
            fuera.isEmpty(),
            "Campos de texto armados a mano fuera de ui/components/ (usa Campo o CampoDeMonto de " +
                "ui/components/ds/Campo.kt):\n" + fuera.values.flatten().joinToString("\n") { "  $it" },
        )
    }

    /** Una excepción que ya no tiene campo es una puerta abierta para el siguiente: se cierra. */
    @Test
    fun `cada excepcion todavia tiene su campo`() {
        val hay = dondeHayCampos().keys
        val sobran = EXCEPCIONES.keys - hay
        assertTrue(sobran.isEmpty(), "Estas excepciones ya no tienen campo propio; sácalas de la lista: $sobran")
    }

    @Test
    fun `el detector ve los tres campos y no ve los comentarios ni los del sistema`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            BasicTextField(value = v, onValueChange = {})
            OutlinedTextField(value = v, onValueChange = {})
            TextField(value = v, onValueChange = {})
            // BasicTextField( en un comentario no cuenta
            Campo(valor, { valor = it }, rotulo = "Nombre")
            CampoDeMonto(monto, { monto = it })
            val texto = "un TextField( entre comillas"
            """.trimIndent(),
        )
        val vistos = LecturaDelCodigo.dondeAparece(mapOf("Prueba.kt" to codigo), campoSuelto)
        // El de entre comillas también cuenta: el detector es por texto, como los demás.
        assertEquals(4, vistos.size, vistos.toString())
    }
}
