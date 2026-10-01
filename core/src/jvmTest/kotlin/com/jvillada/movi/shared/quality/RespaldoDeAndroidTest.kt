package com.jvillada.movi.shared.quality

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # El respaldo de Android no se lleva la sesión ni la plata
 *
 * `android:allowBackup` está prendido (sirve para que la persona no pierda el tema ni su correo
 * recordado al cambiar de teléfono), así que todo archivo que no se excluya a mano viaja a la nube
 * de Google y a la transferencia entre teléfonos. Hasta Ola 0 solo se excluía `movi_sms_filter`: el
 * token de sesión (`Settings()` → `com.jvillada.movi_preferences.xml`), la instantánea del Inicio
 * y la base `movi.db` se iban enteros.
 *
 * Los nombres no se escriben a mano en la prueba: salen del código que los crea —el
 * `applicationId` del build y el `createDatabase("…")` de Android—, así que si alguien renombra
 * la base o cambia el paquete, la regla vieja deja de excluir algo y esto se pone rojo.
 *
 * Está en `:core:jvmTest` (como `VoseoScanTest`) porque `:androidApp` no tiene pruebas en CI.
 */
class RespaldoDeAndroidTest {

    private val raiz = buscarRaiz()

    private fun buscarRaiz(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (dir != null && File(dir, "androidApp/src/main/res/xml").isDirectory) return dir!!
            dir = dir?.parentFile
        }
        fail("No se encontró androidApp/src/main/res/xml subiendo desde ${System.getProperty("user.dir")}")
    }

    private fun texto(ruta: String) = File(raiz, ruta).readText()

    /** El `applicationId` del build: de ahí sale el nombre del archivo de `Settings()`. */
    private val paquete: String = Regex("""applicationId\s*=\s*"([^"]+)"""")
        .find(texto("androidApp/build.gradle.kts"))?.groupValues?.get(1)
        ?: fail("No se encontró applicationId en androidApp/build.gradle.kts")

    /** El nombre de la base de Android, tal como lo abre `createRepository()`. */
    private val base: String = Regex("""createDatabase\("([^"]+)"\)""")
        .find(texto("shared/src/androidMain/kotlin/com/jvillada/movi/data/Platform.android.kt"))?.groupValues?.get(1)
        ?: fail("No se encontró createDatabase(\"…\") en Platform.android.kt")

    private val obligatorias: Set<Pair<String, String>> get() = setOf(
        "sharedpref" to "movi_sms_filter.xml",
        "sharedpref" to "${paquete}_preferences.xml",
        "database" to base,
        "database" to "$base-journal",
        "database" to "$base-wal",
        "database" to "$base-shm",
    )

    /** (domain, path) de cada `<exclude>` debajo de [padre] (o de todo el documento). */
    private fun exclusiones(archivo: String, padre: String? = null): Set<Pair<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(raiz, archivo))
        val raizXml = if (padre == null) doc.documentElement
        else doc.getElementsByTagName(padre).item(0) as? Element ?: fail("$archivo no tiene <$padre>")
        val nodos = raizXml.getElementsByTagName("exclude")
        return (0 until nodos.length).map { i ->
            val e = nodos.item(i) as Element
            e.getAttribute("domain") to e.getAttribute("path")
        }.toSet()
    }

    @Test
    fun `el respaldo de Android 11 excluye la sesion y la base`() {
        val faltan = obligatorias - exclusiones("androidApp/src/main/res/xml/backup_rules.xml")
        assertTrue(faltan.isEmpty(), "backup_rules.xml no excluye: $faltan")
    }

    @Test
    fun `la nube y la transferencia de Android 12 excluyen lo mismo`() {
        val reglas = "androidApp/src/main/res/xml/data_extraction_rules.xml"
        for (camino in listOf("cloud-backup", "device-transfer")) {
            val faltan = obligatorias - exclusiones(reglas, camino)
            assertTrue(faltan.isEmpty(), "<$camino> no excluye: $faltan")
        }
    }

    @Test
    fun `las dos listas dicen lo mismo`() {
        assertEquals(
            exclusiones("androidApp/src/main/res/xml/backup_rules.xml"),
            exclusiones("androidApp/src/main/res/xml/data_extraction_rules.xml", "cloud-backup"),
        )
    }

    @Test
    fun `el manifiesto apunta a las dos listas`() {
        val manifiesto = texto("androidApp/src/main/AndroidManifest.xml")
        assertTrue("android:fullBackupContent=\"@xml/backup_rules\"" in manifiesto)
        assertTrue("android:dataExtractionRules=\"@xml/data_extraction_rules\"" in manifiesto)
    }
}
