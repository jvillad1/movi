package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El amarillo lleno es de la acción (vuelta 42)
 *
 * La opción 3 pinta de amarillo lleno (`marca`) **solo la acción principal**: el `Boton` `Principal`,
 * la acción destacada de Inicio, la píldora de la pestaña activa. La revisión del 28-oct encontró el
 * amarillo lleno usado como identidad o estado: el avatar de «Tú» en los gastos de un grupo (cinco
 * círculos amarillos), la píldora «En curso» de Tus períodos, y el ícono de las filas que llevan a otra
 * pantalla en Créditos, Pagos, Por revisar y Captura (mientras en Tú iba en gris).
 *
 * Esta guarda, sobre el código de `ui/` sin comentarios, falla si vuelve:
 *
 * - **una píldora amarilla**: `TonoDePildora.Marca` ya no existe; nadie puede nombrarlo.
 * - **una persona en amarillo lleno**: ningún `AvatarDePersona(` recibe `TonoDeIcono.Marca`, y su tono
 *   por defecto no es la marca. El amarillo **suave** sí se permite: «Tú» va en `TonoDeIcono.Propio`
 *   (el `tinte` con la letra en `aviso`); lo prohibido es el **lleno** (`marca`).
 * - **un círculo amarillo en una fila**: `IconoEnCirculo` es neutro por defecto, y `TonoDeIcono.Marca`
 *   solo aparece fuera de `ds/` en **las dos únicas excepciones**, las propuestas que la maqueta pinta
 *   así: la campana de la tarjeta de los avisos del banco en Actividad y el ícono de «Movi propone hoy»
 *   en Inicio. Están con nombre en [PROPUESTAS], y la guarda falla si sobra o falta alguna.
 * - **una fila de navegación armada a mano**: las cinco que había usan `FilaDeNavegacion`.
 */
class ElAmarilloEsDeLaAccionTest {

    /** Los únicos archivos de `ui/` (fuera de `ds/`) que pueden pintar un ícono en el círculo amarillo. */
    private val PROPUESTAS = setOf(
        "transactions/TarjetaDeAvisosDelBanco.kt",
        "sdui/SeccionesDelInicio.kt",
    )

    private val ui by lazy { LecturaDelCodigo.archivos(LecturaDelCodigo.UI, LecturaDelCodigo.DS) }
    private val ds by lazy { LecturaDelCodigo.archivos(LecturaDelCodigo.DS) }

    private fun nombre(ruta: String) = ruta.substringAfter("/ui/")

    @Test
    fun `ninguna pildora es amarilla`() {
        val todo = LecturaDelCodigo.archivos(LecturaDelCodigo.COMMON_MAIN)
        val donde = LecturaDelCodigo.dondeAparece(todo, Regex("""TonoDePildora\.Marca\b"""))
        assertTrue(donde.isEmpty(), "Una píldora en amarillo lleno (es un estado: va en Aviso):\n" + donde.joinToString("\n"))
        val pildora = ds.entries.single { it.key.endsWith("/Pildora.kt") }.value
        val enum = Regex("""enum class TonoDePildora\s*\{([^}]*)\}""").find(pildora)?.groupValues?.get(1).orEmpty()
        assertTrue(enum.isNotBlank(), "No encontré el enum TonoDePildora")
        assertTrue("Marca" !in enum, "TonoDePildora volvió a tener un tono Marca: $enum")
    }

    @Test
    fun `ninguna persona va en amarillo`() {
        val donde = ui.flatMap { (ruta, codigo) ->
            codigo.lines().mapIndexedNotNull { n, linea ->
                if ("AvatarDePersona(" in linea && "TonoDeIcono.Marca" in linea) "${nombre(ruta)}:${n + 1}" else null
            }
        }
        assertTrue(donde.isEmpty(), "Un avatar de persona en amarillo lleno («Tú» va en Propio, el tinte):\n" + donde.joinToString("\n"))
    }

    @Test
    fun `los circulos de ds no son amarillos por defecto`() {
        // Vuelta 80: `AvatarDePersona` vive en su archivo (`ds/AvatarDePersona.kt`); se busca en todo `ds/`.
        val todoDs = ds.values.joinToString("\n")
        for (componente in listOf("IconoEnCirculo", "AvatarDePersona")) {
            val firma = Regex("""fun $componente\(([^)]*)\)""").find(todoDs)?.groupValues?.get(1)
                ?: error("No encontré $componente en ds/")
            assertTrue("tono: TonoDeIcono = TonoDeIcono.Neutro" in firma, "$componente tiene que ser neutro por defecto: $firma")
        }
    }

    @Test
    fun `las dos excepciones siguen siendo amarillas, y son solo dos`() {
        val conMarca = ui.filterValues { Regex("""TonoDeIcono\.Marca\b""").containsMatchIn(it) }.keys.map(::nombre).toSet()
        assertEquals(PROPUESTAS, conMarca, "las únicas que pintan el círculo amarillo lleno fuera de ds/")
    }

    @Test
    fun `el amarillo suave se permite en una persona`() {
        val fila = ds.entries.single { it.key.endsWith("/Fila.kt") }.value
        assertTrue("TonoDeIcono.Propio -> colores.tinte" in fila, "«Tú» va en el tinte, el amarillo suave")
    }

    @Test
    fun `el circulo amarillo solo va en una propuesta de Movi`() {
        val donde = ui.filterKeys { nombre(it) !in PROPUESTAS }
            .let { LecturaDelCodigo.dondeAparece(it, Regex("""TonoDeIcono\.Marca\b""")) }
        assertTrue(
            donde.isEmpty(),
            "Un ícono en el círculo amarillo fuera de una propuesta. Una fila que lleva a otro lado va en " +
                "FilaDeNavegacion (el círculo neutro); una que avisa, en TonoDeIcono.Aviso:\n" + donde.joinToString("\n"),
        )
    }

    @Test
    fun `las filas que llevan a otra pantalla son la del sistema`() {
        val esperadas = mapOf(
            "credits/CreditosScreen.kt" to "TITULO_SALIDA_DE_DEUDAS",
            "plan/PlanScreen.kt" to "\"Tus períodos\"",
            "porrevisar/PorRevisarScreen.kt" to "FilaQueLleva",
            // Vuelta 73: «Personas y comercios» es una fila más de la lista de «Lo tuyo» (una [Fila] con su
            // círculo neutro dentro de la superficie de la sección), ya no una tarjeta suelta.
        )
        esperadas.forEach { (archivo, donde) ->
            val codigo = ui.entries.single { nombre(it.key) == archivo }.value
            assertTrue("FilaDeNavegacion(" in codigo, "$archivo ($donde) tiene que usar FilaDeNavegacion")
        }
    }

    /**
     * Los únicos archivos de `ui/` (fuera de `ds/`) con un círculo amarillo lleno hecho a mano: el «+»
     * flotante del rail compacto, que es la acción de agregar.
     */
    private val CIRCULOS_DE_ACCION = setOf("components/MinNavRail.kt")

    /**
     * Un `colores.marca` (no `marcaSuave`) con un `CircleShape` a dos renglones o menos: un círculo
     * amarillo lleno armado con `background`/`clip`, que no pasa por `TonoDeIcono.Marca`.
     */
    private fun circulosAmarillosAMano(archivos: Map<String, String>): List<String> =
        archivos.flatMap { (ruta, codigo) ->
            val renglones = codigo.lines()
            renglones.indices.mapNotNull { n ->
                if (!Regex("""colores\.marca\b""").containsMatchIn(renglones[n])) return@mapNotNull null
                val cerca = renglones.subList(maxOf(0, n - 2), minOf(renglones.size, n + 3))
                if (cerca.any { "CircleShape" in it }) "${nombre(ruta)}:${n + 1}" else null
            }
        }

    /**
     * Vuelta 61: el vacío de «Lo que Movi sabe de ti» pintaba su ícono en un círculo amarillo lleno
     * —`VacioQueEnsena` lo armaba con `background(Movi.colores.marca, CircleShape)`, en todo vacío con
     * ícono— y las guardas de arriba no lo veían, porque solo buscan `TonoDeIcono.Marca`.
     */
    @Test
    fun `ningun circulo amarillo armado a mano fuera de la accion`() {
        val donde = circulosAmarillosAMano(ui).filter { it.substringBefore(":") !in CIRCULOS_DE_ACCION }
        assertTrue(
            donde.isEmpty(),
            "Un círculo amarillo lleno armado a mano. Un ícono que no es la acción va en el círculo neutro " +
                "(IconoEnCirculo, fondoDelTonoDeIcono(TonoDeIcono.Neutro)):\n" + donde.joinToString("\n"),
        )
    }

    @Test
    fun `el detector de circulos ve uno amarillo y deja pasar el suave y el de otra forma`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            Box(Modifier.background(Movi.colores.marca, CircleShape))
            Box(Modifier.background(Movi.colores.marcaSuave, CircleShape))



            Box(Modifier.clip(RoundedCornerShape(Radio.xl)).background(Movi.colores.marca))
            """.trimIndent(),
        )
        assertEquals(listOf("x.kt:1"), circulosAmarillosAMano(mapOf("/ui/x.kt" to codigo)))
    }

    @Test
    fun `el detector ve un amarillo y deja pasar los demas tonos`() {
        val codigo = LecturaDelCodigo.sinComentarios(
            """
            IconoEnCirculo(Icons.Rounded.Inbox, tono = TonoDeIcono.Marca)
            IconoEnCirculo(Icons.Rounded.Inbox, tono = TonoDeIcono.Neutro)
            // IconoEnCirculo(Icons.Rounded.Inbox, tono = TonoDeIcono.Marca) en un comentario no cuenta
            """.trimIndent(),
        )
        val donde = LecturaDelCodigo.dondeAparece(mapOf("x.kt" to codigo), Regex("""TonoDeIcono\.Marca\b"""))
        assertEquals(listOf("x.kt:1"), donde)
    }
}
