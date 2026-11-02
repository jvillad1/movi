package com.jvillada.movi.ui

import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **El proyecto de iPhone está listo para la App Store** (vuelta 112): el manifiesto de privacidad
 * dice lo mismo que la tabla de `docs/lanzamiento/app-store.md` §2, va dentro de la app, y el
 * proyecto declara el cifrado exento, solo iPhone en vertical y el framework de release en Release.
 * Como `ElIconoDeMoviTest`, lee los archivos del repo: Xcode no corre en las pruebas.
 */
class ElProyectoDeIphoneTest {

    private fun archivoEn(ruta: String): File =
        listOf("../$ruta", ruta).map(::File).firstOrNull { it.exists() }
            ?: fail("No encuentro $ruta desde ${File(".").absolutePath}")

    private fun archivo(ruta: String): String = archivoEn(ruta).readText()

    private val proyecto by lazy { archivo("iosApp/iosApp.xcodeproj/project.pbxproj") }

    /** El plist del manifiesto como un mapa (`dict` → Map, `array` → List, `true`/`false` → Boolean). */
    private val manifiesto: Map<*, *> by lazy {
        val fabrica = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false; isValidating = false }
        val lector = fabrica.newDocumentBuilder().apply {
            // El DOCTYPE apunta al DTD de Apple: no se baja nada de la red.
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        val doc = lector.parse(archivoEn("iosApp/iosApp/PrivacyInfo.xcprivacy"))
        val raiz = doc.documentElement
        assertEquals("plist", raiz.tagName)
        valor(hijos(raiz).single()) as? Map<*, *> ?: fail("la raíz del plist no es un dict")
    }

    private fun hijos(e: Element): List<Element> =
        (0 until e.childNodes.length).map { e.childNodes.item(it) }.filterIsInstance<Element>()

    private fun valor(e: Element): Any = when (e.tagName) {
        "dict" -> hijos(e).chunked(2).associate { (k, v) ->
            assertEquals("key", k.tagName, "un dict alterna key y valor")
            k.textContent to valor(v)
        }
        "array" -> hijos(e).map(::valor)
        "string" -> e.textContent
        "true" -> true
        "false" -> false
        "integer" -> e.textContent.toLong()
        else -> fail("tipo de plist inesperado: <${e.tagName}>")
    }

    /** Cada tipo de la tabla de App Store Connect (§2), con su identificador oficial del manifiesto. */
    private val tipoDelManifiesto = mapOf(
        "Nombre" to "NSPrivacyCollectedDataTypeName",
        "Correo electrónico" to "NSPrivacyCollectedDataTypeEmailAddress",
        "Otra información financiera" to "NSPrivacyCollectedDataTypeOtherFinancialInfo",
        "Correos electrónicos o mensajes de texto" to "NSPrivacyCollectedDataTypeEmailsOrTextMessages",
        "Fotos o videos" to "NSPrivacyCollectedDataTypePhotosorVideos",
        "Otro contenido del usuario" to "NSPrivacyCollectedDataTypeOtherUserContent",
        "ID de usuario" to "NSPrivacyCollectedDataTypeUserID",
        "Interacción con el producto" to "NSPrivacyCollectedDataTypeProductInteraction",
        "Datos de fallos" to "NSPrivacyCollectedDataTypeCrashData",
        "Otros datos de diagnóstico" to "NSPrivacyCollectedDataTypeOtherDiagnosticData",
    )

    private val propositoDelManifiesto = mapOf(
        "Funcionalidad de la app" to "NSPrivacyCollectedDataTypePurposeAppFunctionality",
        "Análisis" to "NSPrivacyCollectedDataTypePurposeAnalytics",
        "Personalización" to "NSPrivacyCollectedDataTypePurposeProductPersonalization",
    )

    /** Las filas «Sí» de la tabla de la §2: tipo (en negrita) → propósitos (sin lo que va entre paréntesis). */
    private val laTablaDeLaSeccion2: Map<String, Set<String>> by lazy {
        val doc = archivo("docs/lanzamiento/app-store.md")
        val seccion = doc.substringAfter("## 2.").substringBefore("## 3.")
        seccion.lines().filter { it.startsWith("|") }.mapNotNull { fila ->
            val celdas = fila.trim('|').split("|").map { it.trim() }
            if (celdas.size != 3 || celdas[1] != "Sí") return@mapNotNull null
            val propositos = celdas[2].split(";").map { it.substringBefore("(").trim() }.toSet()
            Regex("""\*\*([^*]+)\*\*""").findAll(celdas[0]).map { it.groupValues[1] to propositos }.toList()
        }.flatten().toMap()
    }

    @Test
    fun `el manifiesto no rastrea y declara exactamente lo de la tabla de la seccion 2`() {
        assertEquals(false, manifiesto["NSPrivacyTracking"], "Movi no rastrea")
        assertEquals(emptyList<Any>(), manifiesto["NSPrivacyTrackingDomains"])

        assertEquals(tipoDelManifiesto.keys, laTablaDeLaSeccion2.keys, "los tipos de la §2 de app-store.md")
        val declarados = (manifiesto["NSPrivacyCollectedDataTypes"] as List<*>).map { it as Map<*, *> }
        val porTipo = declarados.associateBy { it["NSPrivacyCollectedDataType"] as String }
        assertEquals(declarados.size, porTipo.size, "ningún tipo repetido")
        assertEquals(tipoDelManifiesto.values.toSet(), porTipo.keys, "ni uno de más ni uno de menos")

        for ((nombre, propositos) in laTablaDeLaSeccion2) {
            val tipo = porTipo.getValue(tipoDelManifiesto.getValue(nombre))
            assertEquals(true, tipo["NSPrivacyCollectedDataTypeLinked"], "$nombre: vinculado a la cuenta")
            assertEquals(false, tipo["NSPrivacyCollectedDataTypeTracking"], "$nombre: sin rastreo")
            val esperados = propositos.map { propositoDelManifiesto[it] ?: fail("$nombre: propósito «$it» sin equivalente") }.toSet()
            assertEquals(esperados, (tipo["NSPrivacyCollectedDataTypePurposes"] as List<*>).toSet(), "$nombre: propósitos")
        }
    }

    @Test
    fun `el manifiesto declara las APIs de razon requerida con razones de la app`() {
        val apis = (manifiesto["NSPrivacyAccessedAPITypes"] as List<*>).map { it as Map<*, *> }
            .associate { it["NSPrivacyAccessedAPIType"] as String to (it["NSPrivacyAccessedAPITypeReasons"] as List<*>).toSet() }
        // Medido con `nm -u` sobre el framework (vuelta 112): NSUserDefaults (la app y
        // multiplatform-settings), stat/fstat (Skia e ICU sobre archivos del propio paquete) y
        // mach_absolute_time (el temporizador del SDK de DNG de Skia).
        assertEquals(setOf("CA92.1"), apis["NSPrivacyAccessedAPICategoryUserDefaults"], "UserDefaults propios")
        assertEquals(setOf("C617.1"), apis["NSPrivacyAccessedAPICategoryFileTimestamp"], "archivos del contenedor")
        assertEquals(setOf("35F9.1"), apis["NSPrivacyAccessedAPICategorySystemBootTime"], "tiempo entre eventos")
    }

    @Test
    fun `el manifiesto va en los recursos de la app`() {
        assertTrue("/* PrivacyInfo.xcprivacy in Resources */ = {isa = PBXBuildFile;" in proyecto, "su PBXBuildFile")
        val recursos = proyecto.substringAfter("/* Begin PBXResourcesBuildPhase section */").substringBefore("/* End PBXResourcesBuildPhase section */")
        assertTrue("PrivacyInfo.xcprivacy in Resources" in recursos, "en la fase Resources")
        assertTrue("path = PrivacyInfo.xcprivacy;" in proyecto, "su referencia al archivo")
    }

    @Test
    fun `cifrado exento, solo iPhone en vertical y el framework de cada configuracion`() {
        fun cuenta(texto: String) = Regex(Regex.escape(texto)).findAll(proyecto).count()
        assertEquals(2, cuenta("INFOPLIST_KEY_ITSAppUsesNonExemptEncryption = NO;"), "Debug y Release: solo HTTPS del sistema")
        assertEquals(2, cuenta("TARGETED_DEVICE_FAMILY = 1;"), "solo iPhone")
        assertEquals(0, cuenta("TARGETED_DEVICE_FAMILY = \"1,2\""), "sin iPad")
        assertEquals(0, cuenta("_iPad"), "sin orientaciones de iPad")
        assertEquals(2, cuenta("INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone = UIInterfaceOrientationPortrait;"), "solo vertical")
        assertEquals(0, cuenta("Landscape"), "sin horizontal")

        val configuraciones = Regex("""/\* (Debug|Release) \*/ = \{\s*isa = XCBuildConfiguration;(.*?)name = (\w+);""", RegexOption.DOT_MATCHES_ALL)
            .findAll(proyecto).map { it.groupValues[3] to it.groupValues[2] }
            .filter { (_, ajustes) -> "MOVI_XCFRAMEWORK_DIR" in ajustes }.toList()
        assertEquals(listOf("Debug", "Release"), configuraciones.map { it.first }.sorted(), "el target define la carpeta en las dos")
        for ((nombre, ajustes) in configuraciones) {
            val esperado = if (nombre == "Debug") "debug" else "release"
            assertTrue("""MOVI_XCFRAMEWORK_DIR = "$(SRCROOT)/../shared/build/XCFrameworks/$esperado";""" in ajustes, "$nombre usa XCFrameworks/$esperado")
        }
        assertTrue("path = ComposeApp.xcframework; sourceTree = MOVI_XCFRAMEWORK_DIR;" in proyecto, "el framework se resuelve por configuración")
        assertEquals(0, cuenta("XCFrameworks/debug/ComposeApp.xcframework"), "nada fija el de depuración")
    }
}
