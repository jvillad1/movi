package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 104 · todo componente tocable de `ds/` se registra solo
 *
 * La autocaptura de toques vive en los tres gestos de `ds/AlPresionar.kt` (`alPresionar`, `alAlternar`,
 * `toqueSinRipple`): piden un `ToqueDeUso` **obligatorio** y lo anotan en `Uso.toque` antes del
 * `onClick`. `ElToqueEsDelSistemaTest` ya prohíbe en `ds/` un `clickable` suelto; esta guarda cierra el
 * resto, **en cero**:
 *
 * 1. los tres gestos tienen el parámetro `toque: ToqueDeUso` sin valor por defecto y llaman a `Uso.toque`;
 * 2. cada uso de los tres en `ds/`, la barra de pestañas, el rail y el «+» dice qué se toca (`toque =`);
 * 3. todo `BasicTextField` de `ds/` va con `observarLaEscritura` (empezó y terminó de escribir);
 * 4. un componente público de `ds/` que recibe un `on…` (algo que se toca) pasa por un gesto del
 *    sistema o por otro componente de `ds/` que lo hace.
 *
 * Así un componente tocable nuevo no puede saltarse el registro sin que esto falle.
 */
class LosToquesDelSistemaSeRegistranTest {

    private val gestos = listOf("alPresionar", "alAlternar", "toqueSinRipple")

    private val vecinos = listOf("MinBottomNav.kt", "MinNavRail.kt", "MenuDeAgregar.kt").map { "${LecturaDelCodigo.UI}/components/$it" }

    private fun ds(): Map<String, String> = LecturaDelCodigo.archivos(LecturaDelCodigo.DS)

    private fun conVecinos(): Map<String, String> {
        val raiz = LecturaDelCodigo.raiz()
        return ds() + vecinos.associateWith { LecturaDelCodigo.sinComentarios(raiz.resolve(it).readText()) }
    }

    /** Los argumentos de cada llamada a [funcion] en [codigo] (hasta su paréntesis de cierre). */
    private fun llamadas(codigo: String, funcion: String): List<String> {
        val salida = mutableListOf<String>()
        var desde = 0
        while (true) {
            val i = codigo.indexOf(".$funcion(", desde).takeIf { it >= 0 } ?: codigo.indexOf(" $funcion(", desde).takeIf { it >= 0 } ?: break
            val abre = codigo.indexOf('(', i)
            var prof = 0
            var j = abre
            while (j < codigo.length) {
                if (codigo[j] == '(') prof++
                if (codigo[j] == ')') { prof--; if (prof == 0) break }
                j++
            }
            salida += codigo.substring(abre, minOf(j + 1, codigo.length))
            desde = j + 1
        }
        return salida
    }

    @Test
    fun `los tres gestos piden que se diga que se toca y lo anotan`() {
        val toque = ds().entries.single { it.key.endsWith("/AlPresionar.kt") }.value
        for (g in gestos) {
            val firma = Regex("""fun Modifier\.$g\((.*?)\n\): Modifier""", RegexOption.DOT_MATCHES_ALL).find(toque)?.groupValues?.get(1)
                ?: error("no encontré la firma de $g")
            assertTrue(Regex("""\btoque: ToqueDeUso,""").containsMatchIn(firma), "$g tiene que pedir `toque: ToqueDeUso` sin valor por defecto")
        }
        assertEquals(gestos.size, Regex("""Uso\.toque\(""").findAll(toque).count(), "cada gesto llama a Uso.toque una vez")
    }

    @Test
    fun `cada uso de un gesto dice que se toca`() {
        val sinDecir = mutableListOf<String>()
        conVecinos().forEach { (archivo, codigo) ->
            if (archivo.endsWith("/AlPresionar.kt")) return@forEach
            for (g in gestos) llamadas(codigo, g).filter { "toque =" !in it }.forEach { sinDecir += "${archivo.substringAfterLast('/')}: .$g${it.take(60)}" }
        }
        assertTrue(sinDecir.isEmpty(), "Un toque sin su ToqueDeUso (ver observabilidad/Toques.kt):\n" + sinDecir.joinToString("\n") { "  $it" })
    }

    @Test
    fun `todo campo de texto anota que se empezo y se termino`() {
        ds().forEach { (archivo, codigo) ->
            val campos = Regex("""\bBasicTextField\(""").findAll(codigo).count()
            val observados = Regex("""\.observarLaEscritura\(""").findAll(codigo).count()
            assertEquals(campos, observados, "${archivo.substringAfterLast('/')}: un BasicTextField sin observarLaEscritura")
        }
    }

    @Test
    fun `un componente que se toca pasa por el registro`() {
        val conRegistro = Regex("""\.(?:alPresionar|alAlternar|toqueSinRipple|renglonTocable)\(|\b(?:Boton|BotonDeIcono|AccionRapida|Fila|PildoraDeAccion|AccionDelEncabezado|AccionDelEncabezadoSoloIcono|Superficie|VacioQueEnsena)\(""")
        val tocable = Regex("""@Composable\s+fun\s+\w+\(([^{]*?)\)\s*\{""", RegexOption.DOT_MATCHES_ALL)
        val sinRegistro = mutableListOf<String>()
        ds().forEach { (archivo, codigo) ->
            val componentesTocables = tocable.findAll(codigo).filter { Regex("""\bon[A-Z]\w*\s*:""").containsMatchIn(it.groupValues[1]) }.toList()
            if (componentesTocables.isNotEmpty() && !conRegistro.containsMatchIn(codigo)) sinRegistro += archivo.substringAfterLast('/')
        }
        assertTrue(sinRegistro.isEmpty(), "Componentes de ds/ que se tocan sin pasar por el registro: $sinRegistro")
    }

    @Test
    fun `el detector ve un gesto sin decir que se toca`() {
        val codigo = """
            Modifier.alPresionar(onClick = f, toque = t)
            Modifier.alPresionar(onClick = f, rol = Role.Button)
            Modifier.alAlternar(valor = v, onCambiar = g, toque = ToqueDeUso("x"))
        """.trimIndent()
        assertEquals(2, llamadas(codigo, "alPresionar").size)
        assertEquals(1, llamadas(codigo, "alPresionar").count { "toque =" !in it })
        assertEquals(0, llamadas(codigo, "alAlternar").count { "toque =" !in it })
    }
}
