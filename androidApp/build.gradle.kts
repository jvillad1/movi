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
        // 1.16: primer APK con las olas #124-#135 adentro — la plata condicionada, el criterio
        // del picker de cuentas, editar un movimiento, y la cuota que baja la deuda solo por
        // capital. El 1.15 se armó de apuro para reemplazar al 1.14 roto y quedó en #127.
        // 1.17: #136-#141 — ⌘A y el triple clic reemplazan en los 16 campos (el umbral del
        // multi-clic era el de Android, 300 ms, contra los 500 del sistema), y producción
        // dice qué commit corre. Ninguno toca androidMain; el APK se arma porque el dueño
        // lo pidió con todo adentro.
        // 1.18: la ola más grande hasta hoy — #143-#172, y **43 commits** desde el 1.14 que
        // todavía está en Drive. Movimientos con color por renglón y días plegables, Explora
        // borrada, Recurrentes disuelta adentro de Movimientos, presupuestos sobre el período
        // y no el mes civil, las cuotas dentro del flujo libre, suscripciones anuales y con
        // tarjeta, el aviso de que nunca llegó un SMS, y que una cuota ya pagada deje de
        // decir «vencida». Ninguno toca androidMain: el APK se arma porque el dueño lo pidió,
        // y porque el 1.14 que tiene en el teléfono es de antes de todo esto.
        // 1.19: el 1.18 crasheaba al abrir, otra vez por empaquetado y no por código —
        // esta vez faltaba `BackHandlerEffect`, del mismo `androidMain` de `:shared` que se
        // perdió entero en el 1.14. La guarda de abajo lo dejó pasar porque miraba UNA clase
        // canaria, y esa sí estaba. Ahora compara el paquete contra TODO lo compilado.
        // Adentro van además #175-#179: Movi AI leyendo tus documentos, la cuota que no es ni
        // interés ni seguro ni capital, qué pasa si abonas de más, y los mínimos de tarjeta
        // descontados del flujo libre.
        versionCode = 20
        versionName = "1.19"
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
 * ## Por qué una canaria no alcanzó
 *
 * La primera versión de esta verificación buscaba **una sola clase**, `SmsFilterConfigStore`,
 * elegida porque vive en el source set que se había perdido. El APK 1.19 existe porque **el 1.18
 * pasó esa verificación y crasheó igual**:
 *
 * ```
 * java.lang.ClassNotFoundException: Didn't find class "com.jvillada.movi.BackHandler_androidKt"
 *     ... durante la primera composición
 * ```
 *
 * `SmsFilterConfigStore` sí estaba definida en `classes3.dex`. `BackHandler_androidKt` —del mismo
 * `androidMain`, compilada y presente en `shared/build/tmp/kotlin-classes/release/`— no estaba
 * definida en ningún dex. Una canaria no mide si el paquete está completo: mide si esa clase está.
 * Con el empaquetado partiéndose de a pedazos, eso es una lotería.
 *
 * Así que ahora no hay canaria. **Se compara el APK contra todo lo que el build compiló**: cada
 * `.class` del paquete de Movi que salga de `:core`, `:shared` y `:androidApp` tiene que estar
 * *definido* en algún dex. Lo que falte se lista con nombre y apellido.
 *
 * ## Por qué `dexdump` y no buscar el nombre como texto
 *
 * Buscar el nombre como substring en los dex **no sirve, y se comprobó dos veces:** un dex guarda
 * ese nombre tanto donde la clase está *definida* como donde alguien la *llama*. En el APK 1.18
 * roto, `BackHandler_androidKt` aparecía como texto en dos dexes —los que la llaman— y no estaba
 * definida en ninguno. Exactamente el caso que hay que atrapar.
 *
 * `dexdump` sí distingue: imprime una línea `Class descriptor` por cada *class_def*.
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
    val dexdump = File(sdkDeEstaMaquina, "build-tools/${android.buildToolsVersion}/dexdump")
    val salidaDeLaVariante = layout.buildDirectory.dir("outputs/apk/${variante.lowercase()}")
    val temporal = layout.buildDirectory.dir("tmp/dexDe$variante")
    // Lo que el build compiló, por módulo: la salida de Kotlin para esta variante de Android,
    // o sea `commonMain` y `androidMain` juntos — justo el conjunto que tiene que llegar al APK.
    val compilado = listOf(":core", ":shared", ":androidApp").map { modulo ->
        project(modulo).layout.buildDirectory.dir("tmp/kotlin-classes/${variante.lowercase()}")
    }

    val verifica = tasks.register("verificaElDexDe$variante") {
        description = "Falla si al APK de $variante le falta alguna clase que este build compiló."
        doLast {
            check(dexdump.canExecute()) {
                "No encontré dexdump ejecutable en $dexdump. Sin él no puedo verificar el APK, y " +
                    "un APK sin verificar no se entrega: instalá las build-tools o corregí la versión."
            }
            // `Lcom/jvillada/movi/...;` por cada .class compilado. Solo el paquete de la app:
            // las dependencias las mete AGP y no son lo que se pierde.
            val prefijo = "Lcom/jvillada/movi/"
            val esperadas = compilado.flatMap { dir ->
                val raiz = dir.get().asFile
                if (!raiz.isDirectory) emptyList() else raiz.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .map { "L" + it.relativeTo(raiz).invariantSeparatorsPath.removeSuffix(".class") + ";" }
                    .filter { it.startsWith(prefijo) }
                    .toList()
            }.toSortedSet()
            check(esperadas.isNotEmpty()) {
                "No encontré ninguna clase compilada de Movi en ${compilado.map { it.get().asFile }}. " +
                    "Sin eso contra qué comparar, la verificación no prueba nada — y un APK sin " +
                    "verificar no se entrega."
            }

            // **Solo los APK que ESTE build produjo, no todo lo que haya en la carpeta.**
            //
            // `scripts/build-apk.sh` deja al lado una copia con nombre trazable
            // (`movi-debug-1.19.apk`), y esa copia sobrevive a los builds siguientes. Tomar todos
            // los `.apk` del directorio hacía que la verificación midiera **un paquete de otro
            // build**: al primer cambio de código, las clases nuevas «faltaban» en la copia vieja
            // y el build fallaba con una lista de faltantes perfectamente correcta sobre un
            // archivo que a nadie le importa. Es la misma clase de mentira que esta tarea existe
            // para matar, con el signo cambiado: en vez de dejar pasar un APK roto, condena uno
            // sano.
            //
            // `output-metadata.json` lo escribe AGP en el mismo directorio y nombra exactamente lo
            // que acaba de empaquetar. Si no está —o no se puede leer— se cae a todos los `.apk`,
            // que es el comportamiento anterior: más ruidoso, nunca más permisivo.
            val dir = salidaDeLaVariante.get().asFile
            val todos = dir.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
            val declarados = File(dir, "output-metadata.json")
                .takeIf { it.isFile }
                ?.let { metadata ->
                    Regex(""""outputFile"\s*:\s*"([^"]+)"""")
                        .findAll(metadata.readText())
                        .map { it.groupValues[1] }
                        .toSet()
                }
                .orEmpty()
            val apks = todos.filter { declarados.isEmpty() || it.name in declarados }
            check(apks.isNotEmpty()) { "No se armó ningún APK en $dir" }

            apks.forEach { apk ->
                val donde = temporal.get().asFile.also { it.deleteRecursively(); it.mkdirs() }
                val definidas = mutableSetOf<String>()
                ZipFile(apk).use { zip ->
                    for (entrada in zip.entries().asSequence()) {
                        if (!entrada.name.matches(Regex("""classes\d*\.dex"""))) continue
                        val suelto = File(donde, entrada.name)
                        zip.getInputStream(entrada).use { e -> suelto.outputStream().use { s -> e.copyTo(s) } }
                        val proceso = ProcessBuilder(dexdump.absolutePath, suelto.absolutePath)
                            .redirectErrorStream(true).start()
                        // Se lee la salida ENTERA: cortar antes deja al proceso escribiendo en
                        // una tubería llena y el build se cuelga.
                        proceso.inputStream.bufferedReader().forEachLine { linea ->
                            if (linea.contains("Class descriptor")) {
                                val d = linea.substringAfter('\'', "").substringBeforeLast('\'', "")
                                if (d.startsWith(prefijo)) definidas += d
                            }
                        }
                        proceso.waitFor()
                        suelto.delete()
                    }
                }
                donde.deleteRecursively()

                val faltantes = esperadas - definidas
                check(faltantes.isEmpty()) {
                    val muestra = faltantes.take(20).joinToString("\n  ")
                    val resto = if (faltantes.size > 20) "\n  ...y ${faltantes.size - 20} más" else ""
                    "${apk.name} no DEFINE ${faltantes.size} de las ${esperadas.size} clases de " +
                        "Movi que este build compiló. La app va a crashear al abrir. Faltan:\n  " +
                        muestra + resto + "\nVolvé a armarlo con --no-build-cache, o desde el " +
                        "checkout principal en vez de un worktree."
                }
                logger.lifecycle("${apk.name}: las ${esperadas.size} clases de Movi están adentro.")
            }
        }
    }
    tasks.matching { it.name == "assemble$variante" }.configureEach { finalizedBy(verifica) }
}
