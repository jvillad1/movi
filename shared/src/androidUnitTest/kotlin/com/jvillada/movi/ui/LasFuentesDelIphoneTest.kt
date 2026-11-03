package com.jvillada.movi.ui

import com.jvillada.movi.theme.ARCHIVOS_DE_FIGTREE
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **Figtree en el iPhone** (vuelta 115). El framework es estático y no trae los recursos de Compose:
 * una fase del proyecto de Xcode los copia a `<app>/compose-resources`, donde Compose los busca. Si
 * la fase falta y el iPhone dice que trae las fuentes, `Font(recurso)` no las encuentra y la app se
 * cae al abrir; por eso **la fase y `laPlataformaTraeLasFuentes` van de la mano**. Como
 * `ElProyectoDeIphoneTest`, lee los archivos del repo: Xcode no corre en las pruebas.
 */
class LasFuentesDelIphoneTest {

    private fun archivoEn(ruta: String): File =
        listOf("../$ruta", ruta).map(::File).firstOrNull { it.exists() }
            ?: fail("No encuentro $ruta desde ${File(".").absolutePath}")

    private fun archivo(ruta: String): String = archivoEn(ruta).readText()

    private val proyecto by lazy { archivo("iosApp/iosApp.xcodeproj/project.pbxproj") }
    private val letraDelIphone by lazy { archivo("shared/src/iosMain/kotlin/com/jvillada/movi/theme/LetraPropia.ios.kt") }
    private val gradle by lazy { archivo("shared/build.gradle.kts") }

    private val NOMBRE_DE_LA_FASE = "Copiar las fuentes de Movi"

    /** El `shellScript` de la fase, sin el escape del pbxproj; `null` si la fase no está. */
    private val guion: String? by lazy {
        val fases = proyecto.substringAfter("/* Begin PBXShellScriptBuildPhase section */", "")
            .substringBefore("/* End PBXShellScriptBuildPhase section */")
        if ("name = \"$NOMBRE_DE_LA_FASE\";" !in fases) return@lazy null
        val crudo = Regex("""shellScript = "((?:[^"\\]|\\.)*)";""").find(fases)?.groupValues?.get(1)
            ?: fail("la fase no tiene shellScript")
        crudo.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private val elIphoneDiceQueTraeLasFuentes: Boolean by lazy {
        val actual = Regex("""internal actual val laPlataformaTraeLasFuentes: Boolean\s*(=\s*(\w+)|by lazy)""")
            .find(letraDelIphone) ?: fail("no encuentro el actual de iOS")
        actual.groupValues[2] != "false"
    }

    @Test
    fun `la fase y laPlataformaTraeLasFuentes van de la mano`() {
        assertEquals(
            guion != null, elIphoneDiceQueTraeLasFuentes,
            "sin la fase que copia las fuentes, el iPhone no puede decir que las trae (y con ella, debe)",
        )
        assertTrue(guion != null, "esta vuelta prende Figtree en el iPhone")
    }

    @Test
    fun `la fase corre en el target, despues de Resources`() {
        val fasesDelTarget = proyecto.substringAfter("/* Begin PBXNativeTarget section */")
            .substringAfter("buildPhases = (").substringBefore(");")
            .lines().map { it.trim().trimEnd(',') }.filter { it.isNotEmpty() }
            .map { it.substringAfter("/* ").substringBefore(" */") }
        assertEquals(listOf("Sources", "Frameworks", "Resources", NOMBRE_DE_LA_FASE, "Embed Frameworks"), fasesDelTarget)
        // Corre en cada build (es copiar 200 KB) y fuera del sandbox de los guiones: lee una carpeta
        // fuera del proyecto de Xcode (shared/build).
        assertTrue("alwaysOutOfDate = 1;" in proyecto.substringAfter("/* $NOMBRE_DE_LA_FASE */ = {").substringBefore("};"))
        assertEquals(2, Regex(Regex.escape("ENABLE_USER_SCRIPT_SANDBOXING = NO;")).findAll(proyecto).count(), "Debug y Release")
    }

    @Test
    fun `copia de la carpeta del XCFramework a donde Compose busca, y sin ella el build falla`() {
        val g = guion ?: fail("falta la fase «$NOMBRE_DE_LA_FASE»")
        assertTrue("set -eu" in g)
        assertTrue("""ORIGEN="${'$'}{MOVI_XCFRAMEWORK_DIR}/compose-resources"""" in g, "el de la configuración: $g")
        // Compose 1.8 (ResourceReader.ios.kt): NSBundle.mainBundle.resourcePath + "/compose-resources".
        // En una app de iOS, ese resourcePath es BUILT_PRODUCTS_DIR/UNLOCALIZED_RESOURCES_FOLDER_PATH.
        assertTrue("""DESTINO="${'$'}{BUILT_PRODUCTS_DIR}/${'$'}{UNLOCALIZED_RESOURCES_FOLDER_PATH}/compose-resources"""" in g, g)
        assertTrue("""cp -R "${'$'}{ORIGEN}/" "${'$'}{DESTINO}/"""" in g, g)
        val siFalta = g.substringAfter("if [ ! -d \"\${ORIGEN}/composeResources\" ]; then", "").substringBefore("fi")
        assertTrue("echo \"error:" in siFalta && "exit 1" in siFalta, "sin las fuentes el build falla y dice qué correr: $g")
        assertTrue("recursosDeComposeParaIphone\${CONFIGURATION}" in siFalta, "nombra la tarea de Gradle de la configuración")
    }

    @Test
    fun `Gradle deja las fuentes junto al XCFramework de cada configuracion`() {
        assertTrue("""listOf("Debug", "Release").forEach { configuracion ->""" in gradle)
        assertTrue("""tasks.register<Sync>("recursosDeComposeParaIphone${'$'}configuracion")""" in gradle)
        assertTrue("""into(layout.buildDirectory.dir("XCFrameworks/${'$'}{configuracion.lowercase()}/compose-resources"))""" in gradle)
        assertTrue("""tasks.matching { it.name == "assembleComposeApp${'$'}{configuracion}XCFramework" }""" in gradle)
        assertTrue("finalizedBy(recursos)" in gradle, "armar el XCFramework las deja solas")
        // La ruta del paquete de `Res` es la carpeta que va adentro de composeResources.
        assertTrue("""packageOfResClass = "com.jvillada.movi.resources"""" in gradle)
    }

    @Test
    fun `el iPhone pregunta por las cinco Figtree con la ruta de Compose y si falta una no se cae`() {
        val fuentes = archivoEn("shared/src/commonMain/composeResources/font").list()!!.filter { it.endsWith(".ttf") }.toSet()
        assertEquals(fuentes, ARCHIVOS_DE_FIGTREE.toSet(), "la lista del iPhone es la carpeta de fuentes")
        assertEquals(5, ARCHIVOS_DE_FIGTREE.size)
        assertTrue("ARCHIVOS_DE_FIGTREE.filterNot" in letraDelIphone)
        assertTrue("Res.getUri(\"font/\$archivo\")" in letraDelIphone, "la misma ruta que arma Compose al leerla")
        assertTrue("fileExistsAtPath" in letraDelIphone)
        assertTrue("reportarAtrapado(" in letraDelIphone, "lo que falta va a los fallos atrapados")

        val tipografia = archivo("shared/src/commonMain/kotlin/com/jvillada/movi/theme/Tipografia.kt")
        val usadas = Regex("""Res\.font\.(figtree_\w+)""").findAll(tipografia).map { "${it.groupValues[1]}.ttf" }.toSet()
        assertEquals(fuentes, usadas, "la familia usa las mismas cinco")
    }

    @Test
    fun `el archivo de la App Store sale con las fuentes`() {
        val script = archivo("scripts/armar-para-app-store.sh")
        assertTrue(":shared:recursosDeComposeParaIphoneRelease" in script)
        assertTrue("\$APP/compose-resources/composeResources/com.jvillada.movi.resources/font/figtree_\$PESO.ttf" in script)
        assertTrue("for PESO in regular medium semibold bold extrabold; do" in script)
    }
}
