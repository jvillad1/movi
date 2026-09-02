import org.jetbrains.kotlin.gradle.dsl.JvmTarget
// Importado y no escrito como `java.util.zip.ZipFile`: dentro de un build script de Gradle
// `java` es la extensión del Java plugin, así que la ruta completa no resuelve.
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.jvillada.movi.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.jvillada.movi"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // El APK es de instalación única pero sideloaded: sin bump, un instalador
        // consciente de versiones rechaza la actualización por "misma versión".
        // 1.3: la app deja de ser solo el sensor — MainActivity monta la app completa.
        // 1.15: el APK 1.14 crasheaba al abrir con NoClassDefFoundError sobre
        // SmsFilterConfigStore — el paquete salió SIN el dex de androidMain de :shared.
        // Ver `verificaElDexDe{Debug,Release}` abajo: el bug no estaba en el código sino
        // en el empaquetado, y el build decía BUILD SUCCESSFUL igual.
        versionCode = 16
        versionName = "1.15"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        getByName("release") {
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        // The NullSafeMutableLiveData detector crashes lint analysis ("Unexpected
        // failure during lint analysis") with AGP/lint 8.7.x on this codebase.
        // The project doesn't use LiveData, so disabling it loses no coverage.
        disable += "NullSafeMutableLiveData"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)
}

/**
 * # Que un APK incompleto falle el build en vez de llegar al teléfono
 *
 * El APK 1.14 se entregó crasheando en loop («Movi keeps stopping»). No era un bug del código:
 *
 * ```
 * java.lang.NoClassDefFoundError: Failed resolution of: Lcom/jvillada/movi/sms/SmsFilterConfigStore;
 *     at com.jvillada.movi.MainActivity.onCreate(MainActivity.kt:31)
 * ```
 *
 * Al paquete le faltaba el dex con el `androidMain` de `:shared`. `MainActivity` arranca la captura
 * de SMS en su primera línea, la clase no estaba, y Android mataba el proceso antes de dibujar nada.
 * **Y el build decía `BUILD SUCCESSFUL`.** El APK se arma pocas veces y se entrega a mano, así que
 * un defecto de empaquetado se descubre cuando el dueño no puede abrir la app.
 *
 * ## Por qué `dexdump` y no buscar el nombre como texto
 *
 * La primera versión de esta tarea buscaba `com/jvillada/movi/sms/SmsFilterConfigStore` como
 * substring en los dex. **No sirve, y se comprobó:** un dex guarda ese nombre tanto donde la clase
 * está *definida* como donde alguien la *llama*, y `MainActivity` la llama. O sea que el nombre
 * sigue apareciendo aunque la definición se haya perdido — exactamente el caso que hay que atrapar.
 * Medido sobre un APK bueno: la definición está en `classes3.dex`, y `classes5/16/17` la nombran sin
 * definirla.
 *
 * `dexdump` sí distingue: imprime una línea `Class descriptor` por cada *class_def*. Tarda ~4 s en
 * encontrarla, una vez por `assemble`.
 *
 * La canaria es `SmsFilterConfigStore` a propósito: vive en el `androidMain` de `:shared` —el source
 * set que se perdió— y es la primera clase que toca `MainActivity`, así que si falta, la app no abre.
 */
/**
 * **Dónde está el SDK, o `null` si esta máquina no tiene.**
 *
 * `android.sdkDirectory` se resuelve en **configuración**, y Gradle configura TODOS los módulos
 * aunque solo se le pida `:webApp:wasmJsBrowserDistribution`. La imagen de Railway no tiene Android
 * SDK —ni lo necesita: ahí solo se arman el wasm y el fat JAR—, así que esa línea tiraba «SDK
 * location not found» y **se caía el despliegue entero**.
 *
 * No fue un susto teórico: producción quedó tres merges atrás (corriendo #130 mientras master iba
 * por #133) sin que nadie se enterara, porque el build falla y Railway deja sirviendo la versión
 * vieja. Un `BUILD SUCCESSFUL` que no despliega es la misma clase de mentira que esta verificación
 * vino a matar, una capa más arriba.
 *
 * Saltar el registro no afloja la garantía. Esto existe para que no salga un APK incompleto, y una
 * máquina sin SDK **no puede armar un APK**: no hay nada que dejar pasar. Donde sí se arma —esta
 * máquina, y cualquiera con las build-tools— se registra igual y el build sigue fallando si al
 * paquete le falta el dex.
 */
val sdkDeEstaMaquina: File? = runCatching { android.sdkDirectory }.getOrNull()

if (sdkDeEstaMaquina == null) {
    logger.info("Sin Android SDK: no se registra la verificación del dex (acá no se arman APKs).")
} else listOf("Debug", "Release").forEach { variante ->
    // Todo local, nada de propiedades del script: la caché de configuración no serializa
    // referencias a objetos del build script, y `doLast` captura lo que nombra. Y `android` no se
    // puede tocar dentro de `doLast`, así que su ruta se resuelve acá.
    val canaria = "Lcom/jvillada/movi/sms/SmsFilterConfigStore;"
    val dexdump = File(sdkDeEstaMaquina, "build-tools/${android.buildToolsVersion}/dexdump")
    val salidaDeLaVariante = layout.buildDirectory.dir("outputs/apk/${variante.lowercase()}")
    val temporal = layout.buildDirectory.dir("tmp/dexDe$variante")

    val verifica = tasks.register("verificaElDexDe$variante") {
        description = "Falla si al APK de $variante le falta el dex del androidMain de :shared."
        doLast {
            check(dexdump.canExecute()) {
                "No encontré dexdump ejecutable en $dexdump. Sin él no puedo verificar el APK, y " +
                    "un APK sin verificar no se entrega: instalá las build-tools o corregí la versión."
            }
            val dir = salidaDeLaVariante.get().asFile
            val apks = dir.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
            check(apks.isNotEmpty()) { "No se armó ningún APK en $dir" }

            apks.forEach { apk ->
                val donde = temporal.get().asFile.also { it.deleteRecursively(); it.mkdirs() }
                var definida = false
                ZipFile(apk).use { zip ->
                    for (entrada in zip.entries().asSequence()) {
                        if (definida) break
                        if (!entrada.name.matches(Regex("""classes\d*\.dex"""))) continue
                        val suelto = File(donde, entrada.name)
                        zip.getInputStream(entrada).use { e -> suelto.outputStream().use { s -> e.copyTo(s) } }
                        val proceso = ProcessBuilder(dexdump.absolutePath, suelto.absolutePath)
                            .redirectErrorStream(true).start()
                        definida = proceso.inputStream.bufferedReader().useLines { lineas ->
                            lineas.any { it.contains("Class descriptor") && it.contains(canaria) }
                        }
                        // `destroy` y no `waitFor`: `any` corta apenas encuentra, y esperar a un
                        // proceso con salida sin leer se cuelga.
                        proceso.destroy()
                        suelto.delete()
                    }
                }
                donde.deleteRecursively()
                check(definida) {
                    "${apk.name} no DEFINE $canaria en ningún dex: le falta el androidMain " +
                        "de :shared y la app va a crashear al abrir. Volvé a armarlo con " +
                        "--no-build-cache, o desde el checkout principal en vez de un worktree."
                }
            }
        }
    }
    tasks.matching { it.name == "assemble$variante" }.configureEach { finalizedBy(verifica) }
}
