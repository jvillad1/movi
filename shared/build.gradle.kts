import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // La instantánea del Inicio (`ui/dashboard/InstantaneaDelInicio.kt`) guarda `DashboardData` en
    // el aparato como JSON, y eso pide `@Serializable` en clases de este módulo. Hasta acá :shared
    // solo usaba los serializadores que ya venían compilados de :core.
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    // Compose UI framework consumed by iosApp. baseName stays "ComposeApp" so the
    // Swift `import ComposeApp` and the generated .xcframework name are unchanged.
    val xcf = XCFramework("ComposeApp")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            xcf.add(this)
        }
    }

    // wasmJs is a library target here; the browser executable lives in :webApp.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // Mirror :core module's nested nonWasm hierarchy so consumer-side metadata
    // resolution lines up with the producer.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonWasm") {
                withAndroidTarget()
                group("apple") {
                    group("ios") {
                        withIosArm64()
                        withIosX64()
                        withIosSimulatorArm64()
                    }
                }
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.android)
            implementation(libs.koin.android)
            // «Entrar con huella» (platform/HuellaDelAparato.android.kt). `fragment` va
            // explícito: `biometric` pide FragmentActivity y arrastra un fragment de 2020.
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
        }
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.kotlinx.datetime)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Tests JVM de los `actual` de Android (p.ej. el reloj del backfill de SMS en
        // platform/SmsReader.android.kt y el subsistema del sensor en sms/ y sensor/).
        // Corren con ./gradlew :shared:testDebugUnitTest.
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            // Solo para los tests (no viaja en el AAR): el android.jar mockeable de AGP
            // stubbea org.json para tirar en runtime, así que parsear de verdad
            // (SmsSync/SmsFilterConfigStore) necesita la implementación real.
            implementation(libs.org.json)
            // Pruebas de GEOMETRÍA de la interfaz, en la JVM. Ver el KDoc de
            // `HojaAgregarGeometriaTest` para qué cubren y —sobre todo— qué NO.
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test.junit4)
            // Trae el `<activity android:name="ComponentActivity">` que `createComposeRule()`
            // lanza. Sin él, Robolectric muere con «Unable to find explicit activity class».
            implementation(libs.androidx.compose.ui.test.manifest)
            // Vuelta 78: el contador de peticiones del Inicio, con el cliente HTTP de verdad
            // (`WalletRepositoryImpl`) sobre un motor falso. Ver `ElInicioNoRepitePeticionesTest`.
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "com.jvillada.movi"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            // Robolectric necesita el AndroidManifest fusionado y los recursos empaquetados
            // para arrancar; sin esto, `createComposeRule()` no encuentra la actividad.
            isIncludeAndroidResources = true
        }
    }
    lint {
        // The NullSafeMutableLiveData detector crashes lint analysis ("Unexpected
        // failure during lint analysis") with AGP/lint 8.7.x on this codebase.
        // The project doesn't use LiveData, so disabling it loses no coverage.
        disable += "NullSafeMutableLiveData"
    }
}

// Las tipografías de Movi viven en commonMain/composeResources/font. El paquete de `Res` se fija a
// mano: el que Compose arma solo sale del nombre del módulo, y si algún día el módulo se renombra
// —ya pasó una vez, de `shared` a `core`— cambiarían todos los imports sin que nadie lo pidiera.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.jvillada.movi.resources"
    generateResClass = always
}

// `ElIngresoHablaLosTokensTest` lee el index.html del webApp para comparar sus colores con
// `Tokens.kt`. Sin declararlo como entrada, un cambio que toque SOLO ese archivo dejaba la prueba
// «al día» y Gradle no la corría: pasaba en verde sin mirar.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("webApp/src/wasmJsMain/resources/index.html"))
        .withPropertyName("indexDelWebApp")
    // Y `ElWidgetHablaLosTokensTest`, los colores de la vista previa del widget de Android.
    inputs.file(rootProject.file("androidApp/src/main/res/values/colors.xml"))
        .withPropertyName("coloresDelWidget")
    inputs.file(rootProject.file("androidApp/src/main/res/layout/widget_te_quedan.xml"))
        .withPropertyName("layoutDelWidget")
    // Y `ElIconoDeMoviTest` (vuelta 65), el ícono de Android, los avisos y los íconos de la PWA.
    inputs.dir(rootProject.file("androidApp/src/main/res/drawable")).withPropertyName("drawablesDeLaApp")
    inputs.dir(rootProject.file("androidApp/src/main/res/mipmap-anydpi-v26")).withPropertyName("iconoAdaptable")
    inputs.dir(rootProject.file("androidApp/src/main/kotlin")).withPropertyName("fuentesDeLaApp")
    inputs.dir(rootProject.file("webApp/src/wasmJsMain/resources")).withPropertyName("recursosDelWebApp")
}

// ── Vuelta 104 · los rótulos de la app, para la autocaptura de toques ─────────────────────────
//
// Un toque en `ui/components/ds/` se nombra con el texto del botón **solo si ese texto es un rótulo de
// la app**: un literal del código pasado como rótulo de un componente (`Boton("Guardar", …)`,
// `titulo = "Inicio del mes"`, `texto = TEXTO_DE_GUARDAR`), nunca algo que llegó del usuario o del
// banco. Esta tarea lee las fuentes de `commonMain` (de `:shared`, y las constantes de `:core`) sin
// los comentarios, junta esos literales y los `testTag` del código, y genera
// `RotulosDeUsoGenerados.kt`. Siempre al día: corre antes de compilar, y si un rótulo nuevo no
// estuviera, el toque solo se nombraría por su tag o su tipo de componente (el lado seguro).
// Ver `observabilidad/Toques.kt`.
abstract class GenerarRotulosDeUso : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fuentes: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val constantes: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val salida: DirectoryProperty

    private class Tok(val tipo: Char, val texto: String, val partes: List<Pair<Boolean, String>> = emptyList()) {
        /** Un literal sin `$`: su texto. */
        val literal: String? get() = if (tipo == 'S' && partes.none { it.first }) partes.joinToString("") { it.second } else null
    }

    private fun lexico(fuente: String): List<Tok> {
        val toks = ArrayList<Tok>()
        var i = 0
        val n = fuente.length
        fun cadena(desde: Int): Pair<Tok, Int> {
            // desde apunta a la primera comilla
            if (fuente.startsWith("\"\"\"", desde)) {
                val fin = fuente.indexOf("\"\"\"", desde + 3).let { if (it < 0) n else it + 3 }
                return Tok('R', "") to fin
            }
            var j = desde + 1
            val partes = ArrayList<Pair<Boolean, String>>()
            val actual = StringBuilder()
            var rara = false
            while (j < n && fuente[j] != '"') {
                val c = fuente[j]
                when {
                    c == '\\' && j + 1 < n -> {
                        when (val e = fuente[j + 1]) {
                            '"', '\\', '$', '\'' -> actual.append(e)
                            'u' -> {
                                val hex = fuente.substring(j + 2, minOf(n, j + 6))
                                hex.toIntOrNull(16)?.let { actual.append(it.toChar()) } ?: run { rara = true }
                                j += 4
                            }
                            else -> rara = true
                        }
                        j += 2
                    }
                    c == '$' && j + 1 < n && fuente[j + 1] == '{' -> {
                        if (actual.isNotEmpty()) { partes += false to actual.toString(); actual.clear() }
                        var prof = 1
                        var k = j + 2
                        val dentro = StringBuilder()
                        while (k < n && prof > 0) {
                            when (fuente[k]) {
                                '{' -> prof++
                                '}' -> prof--
                                '"' -> { val (_, fin) = cadena(k); dentro.append(fuente, k, fin); k = fin; continue }
                            }
                            if (prof > 0) dentro.append(fuente[k])
                            k++
                        }
                        partes += true to dentro.toString().trim()
                        j = k
                    }
                    c == '$' && j + 1 < n && (fuente[j + 1].isLetter() || fuente[j + 1] == '_') -> {
                        if (actual.isNotEmpty()) { partes += false to actual.toString(); actual.clear() }
                        var k = j + 1
                        while (k < n && (fuente[k].isLetterOrDigit() || fuente[k] == '_')) k++
                        partes += true to fuente.substring(j + 1, k)
                        j = k
                    }
                    c == '\n' -> { rara = true; j++ }
                    else -> { actual.append(c); j++ }
                }
            }
            if (actual.isNotEmpty()) partes += false to actual.toString()
            return (if (rara) Tok('R', "") else Tok('S', "", partes)) to minOf(n, j + 1)
        }
        while (i < n) {
            val c = fuente[i]
            when {
                c.isWhitespace() -> i++
                fuente.startsWith("//", i) -> { i = fuente.indexOf('\n', i).let { if (it < 0) n else it } }
                fuente.startsWith("/*", i) -> {
                    var prof = 1
                    i += 2
                    while (i < n && prof > 0) {
                        if (fuente.startsWith("/*", i)) { prof++; i += 2 }
                        else if (fuente.startsWith("*/", i)) { prof--; i += 2 }
                        else i++
                    }
                }
                c == '"' -> { val (t, fin) = cadena(i); toks += t; i = fin }
                c == '\'' -> {
                    var j = i + 1
                    while (j < n && fuente[j] != '\'') { if (fuente[j] == '\\') j++; j++ }
                    i = j + 1
                }
                c == '`' -> { val fin = fuente.indexOf('`', i + 1).let { if (it < 0) n else it }; toks += Tok('I', fuente.substring(i + 1, fin)); i = fin + 1 }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < n && (fuente[j].isLetterOrDigit() || fuente[j] == '_')) j++
                    toks += Tok('I', fuente.substring(i, j)); i = j
                }
                c.isDigit() -> { var j = i; while (j < n && (fuente[j].isLetterOrDigit() || fuente[j] == '.' || fuente[j] == '_')) j++; toks += Tok('N', fuente.substring(i, j)); i = j }
                else -> { toks += Tok('P', c.toString()); i++ }
            }
        }
        return toks
    }

    /** Desde [desde], hasta la `,` o el cierre de nivel 0. Devuelve el índice del separador. */
    private fun finDeLaExpresion(t: List<Tok>, desde: Int): Int {
        var prof = 0
        var i = desde
        while (i < t.size) {
            val x = t[i]
            if (x.tipo == 'P') when (x.texto) {
                "(", "[", "{" -> prof++
                ")", "]", "}" -> { if (prof == 0) return i; prof-- }
                "," -> if (prof == 0) return i
            }
            i++
        }
        return i
    }

    @TaskAction
    fun generar() {
        val parametrosDeRotulo = setOf(
            "texto", "titulo", "rotulo", "rotuloCorto", "descripcion", "accion", "accionSecundaria", "accionTerciaria",
            "etiqueta", "label", "labels", "title", "onClickLabel", "contentDescription", "guia", "secundaria", "textoDeLaAccion",
        )
        val parametrosDeTag = setOf("tag", "tagDeLaGuia")
        // Componente → las posiciones de sus argumentos que son rótulos cuando van sin nombre.
        val posicionales = mapOf(
            "Boton" to listOf(0), "Fila" to listOf(0), "PildoraDeFiltro" to listOf(0), "PildoraDeAccion" to listOf(0),
            "Pildora" to listOf(0), "AccionRapida" to listOf(0), "Accion" to listOf(0), "AccionDeEncabezado" to listOf(0),
            "Seccion" to listOf(0), "Encabezado" to listOf(0), "EncabezadoDeHoja" to listOf(0), "BotonDeIcono" to listOf(1),
            "TarjetaDePropuesta" to listOf(1, 2), "Vacio" to listOf(0), "RotuloDeCampo" to listOf(0), "PieDeHoja" to listOf(0),
            "MinScreenHeader" to listOf(0), "GrupoDeCampos" to listOf(0), "Text" to emptyList(),
        )
        val todos = (fuentes.files + constantes.files).filter { it.isFile && it.extension == "kt" }
        val lexicos = todos.associateWith { lexico(it.readText()) }
        // 1. Las constantes de texto: `const val X = "…"` y `val X_MAYUSCULAS = "…"`.
        val consts = HashMap<String, MutableSet<String>>()
        val constsConPrefijo = HashMap<String, MutableSet<String>>()
        lexicos.values.forEach { t ->
            for (i in 0 until t.size - 3) {
                if (t[i].tipo != 'I' || t[i].texto != "val") continue
                val nombre = t[i + 1]
                if (nombre.tipo != 'I') continue
                val esConst = i > 0 && t[i - 1].tipo == 'I' && t[i - 1].texto == "const"
                if (!esConst && !nombre.texto.matches(Regex("[A-Z][A-Z0-9_]+"))) continue
                var j = i + 2
                if (t.getOrNull(j)?.texto == ":") j += 2
                if (t.getOrNull(j)?.texto != "=") continue
                val valor = t.getOrNull(j + 1) ?: continue
                if (t.getOrNull(j + 2)?.texto == "+") continue
                valor.literal?.let { consts.getOrPut(nombre.texto) { HashSet() } += it }
                if (valor.tipo == 'S' && valor.literal == null) {
                    val pre = valor.partes.takeWhile { !it.first }.joinToString("") { it.second }
                    if (pre.isNotEmpty()) constsConPrefijo.getOrPut(nombre.texto) { HashSet() } += pre
                }
            }
        }
        fun prefijoResuelto(x: Tok): String? {
            if (x.tipo != 'S') return null
            val b = StringBuilder()
            for ((esRef, s) in x.partes) {
                if (!esRef) { b.append(s); continue }
                val unico = consts[s.substringAfterLast('.')]?.singleOrNull() ?: break
                b.append(unico)
            }
            return b.toString().takeIf { it.isNotEmpty() }
        }
        val rotulos = sortedSetOf<String>()
        val tags = sortedSetOf<String>()
        fun juntarRotulos(t: List<Tok>, desde: Int, hasta: Int) {
            for (k in desde until hasta) {
                val x = t[k]
                x.literal?.let { rotulos += it }
                if (x.tipo == 'I') consts[x.texto]?.let { rotulos += it }
            }
        }
        fun juntarTags(t: List<Tok>, desde: Int, hasta: Int) {
            for (k in desde until hasta) {
                val x = t[k]
                x.literal?.let { tags += it } ?: prefijoResuelto(x)?.let { tags += it }
                if (x.tipo == 'I') {
                    consts[x.texto]?.let { tags += it }
                    constsConPrefijo[x.texto]?.let { tags += it }
                }
            }
        }
        // Las constantes TAG_* y lo que devuelve una `fun tag…` son tags.
        consts.forEach { (nombre, valores) -> if (nombre.startsWith("TAG_")) tags += valores }
        constsConPrefijo.forEach { (nombre, valores) -> if (nombre.startsWith("TAG_")) tags += valores }
        fuentes.files.filter { it.isFile && it.extension == "kt" }.forEach { archivo ->
            val t = lexicos.getValue(archivo)
            var i = 0
            while (i < t.size) {
                val x = t[i]
                if (x.tipo == 'I') {
                    val anterior = t.getOrNull(i - 1)?.texto
                    val siguiente = t.getOrNull(i + 1)?.texto
                    val nombrado = siguiente == "=" && t.getOrNull(i + 2)?.texto != "=" && (anterior == "(" || anterior == ",")
                    when {
                        nombrado && x.texto in parametrosDeRotulo -> juntarRotulos(t, i + 2, finDeLaExpresion(t, i + 2))
                        nombrado && x.texto in parametrosDeTag -> juntarTags(t, i + 2, finDeLaExpresion(t, i + 2))
                        x.texto == "testTag" && siguiente == "(" -> juntarTags(t, i + 2, finDeLaExpresion(t, i + 2))
                        x.texto == "fun" && t.getOrNull(i + 1)?.texto?.startsWith("tag") == true ->
                            (i + 2 until minOf(t.size, i + 40)).firstOrNull { t[it].tipo == 'S' }?.let { juntarTags(t, it, it + 1) }
                        siguiente == "(" && x.texto in posicionales -> {
                            val posiciones = posicionales.getValue(x.texto)
                            var desde = i + 2
                            var indice = 0
                            while (desde < t.size && posiciones.isNotEmpty() && indice <= posiciones.max()) {
                                val fin = finDeLaExpresion(t, desde)
                                val conNombre = t.getOrNull(desde)?.tipo == 'I' && t.getOrNull(desde + 1)?.texto == "=" && t.getOrNull(desde + 2)?.texto != "="
                                if (!conNombre && indice in posiciones) juntarRotulos(t, desde, fin)
                                if (fin >= t.size || t[fin].texto != ",") break
                                desde = fin + 1
                                indice++
                            }
                        }
                    }
                }
                i++
            }
        }
        // Un rótulo: sin cifras ni «$» (nunca un monto), de un renglón, corto. Un tag: con la forma de un tag.
        val rotulosBuenos = rotulos.map { it.trim() }.filter { r ->
            r.length in 2..60 && r.none { it.isDigit() || it == '$' || it == '\n' || it == '@' } && r.any { it.isLetter() }
        }.toSortedSet()
        val tagsBuenos = tags.map { it.trim() }.filter { it.matches(Regex("[A-Za-z][A-Za-z0-9:_.-]{0,79}")) }.toSortedSet()
        fun comoKotlin(nombre: String, valores: Collection<String>): String {
            val unido = valores.joinToString("\u0001")
            val trozos = unido.chunked(12_000).joinToString(",\n        ") { trozo ->
                "\"" + trozo.flatMap { ch ->
                    when (ch) {
                        '\\' -> "\\\\".toList(); '"' -> "\\\"".toList(); '$' -> "\\$".toList()
                        '\u0001' -> "\\u0001".toList()
                        else -> if (ch.code < 32) "\\u%04x".format(ch.code).toList() else listOf(ch)
                    }
                }.joinToString("") + "\""
            }
            return "    private val ${nombre}_EN_TROZOS = arrayOf(\n        $trozos,\n    )\n"
        }
        val destino = salida.get().asFile.resolve("com/jvillada/movi/observabilidad/RotulosDeUsoGenerados.kt")
        destino.parentFile.mkdirs()
        destino.writeText(
            "// GENERADO por la tarea `generarRotulosDeUso` de shared/build.gradle.kts. No editar.\n" +
                "package com.jvillada.movi.observabilidad\n\n" +
                "/** Los rótulos y los tags del código de la app (vuelta 104). Ver `Toques.kt`. */\n" +
                "internal object RotulosDeUsoGenerados {\n" +
                "    /** ${rotulosBuenos.size} rótulos: literales pasados como rótulo de un componente. */\n" +
                "    val rotulos: Set<String> by lazy { ROTULOS_EN_TROZOS.joinToString(\"\").split('\\u0001').toHashSet() }\n\n" +
                "    /** ${tagsBuenos.size} tags (y prefijos de tags armados con `\$`). */\n" +
                "    val tags: Set<String> by lazy { TAGS_EN_TROZOS.joinToString(\"\").split('\\u0001').toHashSet() }\n\n" +
                comoKotlin("ROTULOS", rotulosBuenos) + "\n" + comoKotlin("TAGS", tagsBuenos) +
                "}\n",
        )
    }
}

val generarRotulosDeUso = tasks.register<GenerarRotulosDeUso>("generarRotulosDeUso") {
    fuentes.from(fileTree("src/commonMain/kotlin") { include("**/*.kt") })
    constantes.from(fileTree(rootProject.file("core/src/commonMain/kotlin")) { include("**/*.kt") })
    salida.set(layout.buildDirectory.dir("generated/rotulosDeUso/kotlin"))
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generarRotulosDeUso.flatMap { it.salida })
}
