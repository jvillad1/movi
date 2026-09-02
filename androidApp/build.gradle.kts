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
        // Ver la tarea `verificaQueElDexEsteCompleto` de abajo: el bug no estaba en el
        // código sino en el empaquetado, y el build decía BUILD SUCCESSFUL igual.
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
 *
 * **Y el build decía `BUILD SUCCESSFUL`.** Se reprodujo dos veces armando desde un worktree de git
 * con la caché de Gradle compartida: `dexBuilderDebug` y `mergeProjectDexDebug` resuelven
 * `FROM-CACHE` contra entradas del checkout principal y devuelven un juego de dex incompleto.
 * Con `--no-build-cache`, o armando desde el checkout principal, sale bien.
 *
 * La regla del proyecto es que el APK se arma pocas veces y se entrega a mano, así que un defecto
 * de empaquetado se descubre cuando el dueño no puede abrir la app. Esta tarea lo mueve al build:
 * si la clase canaria no está en ningún dex, el build falla y ese APK no sale de acá.
 *
 * La canaria es `SmsFilterConfigStore` a propósito: vive en el `androidMain` de `:shared` —el source
 * set que se perdió— y es la primera que toca `MainActivity`, así que si falta, la app no abre.
 */
/**
 * Cada variante verifica **su propio** APK, no todo lo que haya quedado en `outputs/apk`. La
 * primera versión de esta tarea escaneaba el árbol entero y falló contra un `release` viejo de otro
 * build mientras se armaba el `debug` — un guardián que grita por un artefacto que nadie va a
 * instalar se termina desactivando, y entonces no guarda nada.
 */
listOf("Debug", "Release").forEach { variante ->
    val verifica = tasks.register("verificaElDexDe$variante") {
        description = "Falla si al APK de $variante le falta el dex del androidMain de :shared."
        val dir = layout.buildDirectory.dir("outputs/apk/${variante.lowercase()}")
        doLast {
            val canaria = "com/jvillada/movi/sms/SmsFilterConfigStore"
            val apks = dir.get().asFile.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
            check(apks.isNotEmpty()) { "No se armó ningún APK en ${dir.get().asFile}" }
            apks.forEach { apk ->
                val presente = ZipFile(apk).use { zip ->
                    zip.entries().asSequence()
                        .filter { it.name.matches(Regex("""classes\d*\.dex""")) }
                        .any { entrada ->
                            // El nombre de la clase viaja como texto plano en el pool de strings
                            // del dex: alcanza con buscarlo, sin traer una librería que lo parsee.
                            zip.getInputStream(entrada).readBytes()
                                .toString(Charsets.ISO_8859_1).contains(canaria)
                        }
                }
                check(presente) {
                    "${apk.name} no contiene $canaria: le falta el dex del androidMain de :shared " +
                        "y la app va a crashear al abrir. Volvé a armarlo con --no-build-cache, o " +
                        "desde el checkout principal en vez de un worktree."
                }
            }
        }
    }
    tasks.matching { it.name == "assemble$variante" }.configureEach { finalizedBy(verifica) }
}

