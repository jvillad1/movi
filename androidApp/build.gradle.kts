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

/**
 * Vuelta 109 · **las cuatro propiedades de la llave de subida de Play**, de `~/.gradle/gradle.properties`
 * (o `-P`) o, si no, de variables de entorno con el mismo nombre. `null` si falta alguna: entonces el
 * release sale sin firmar y el build no falla (CI y Railway no las tienen). Nunca se escriben en el repo.
 */
data class LlaveDeSubida(
    val archivo: String,
    val claveDelAlmacen: String,
    val alias: String,
    val claveDeLaLlave: String,
) {
    companion object {
        val NOMBRES = listOf(
            "MOVI_UPLOAD_STORE_FILE",
            "MOVI_UPLOAD_STORE_PASSWORD",
            "MOVI_UPLOAD_KEY_ALIAS",
            "MOVI_UPLOAD_KEY_PASSWORD",
        )

        fun leer(providers: ProviderFactory): LlaveDeSubida? {
            val valores = NOMBRES.map { nombre ->
                (providers.gradleProperty(nombre).orNull ?: providers.environmentVariable(nombre).orNull)
                    ?.takeIf { it.isNotBlank() } ?: return null
            }
            return LlaveDeSubida(valores[0], valores[1], valores[2], valores[3])
        }
    }
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
        versionCode = 93
        versionName = "1.92"
    }
    /*
     * Vuelta 109 · **dos canales, la misma app.** `directa` es el APK de siempre —el de Drive, con la
     * captura de SMS— y `play` es el que va a Google Play, **sin SMS**: Play restringe `READ_SMS` y
     * `RECEIVE_SMS` a quien tenga aprobada la excepción, y la primera revisión no puede depender de
     * eso. `src/play/AndroidManifest.xml` quita los dos permisos y el receiver; la app no pregunta
     * por un flag de build: mira su propio manifiesto (`elAparatoPuedeLeerSms` en `:shared`).
     *
     * **El mismo `applicationId` en los dos** (`com.jvillada.movi`): el dueño y su papá tienen
     * instalado el APK de Drive, y una actualización con otro id sería otra app al lado, con otra
     * base y sin sesión. Como los dos llevan el mismo id, no conviven en un teléfono: el de Play solo
     * se instala donde el de Drive no esté (o tras desinstalarlo), porque las firmas son distintas.
     */
    flavorDimensions += "canal"
    productFlavors {
        create("directa") {
            dimension = "canal"
            // El que Android Studio y `scripts/build-apk.sh` arman por defecto: el de siempre.
            isDefault = true
        }
        create("play") {
            dimension = "canal"
        }
    }
    signingConfigs {
        // La llave de subida de Play, SOLO si están las cuatro propiedades (en `~/.gradle/gradle.properties`
        // o como variables de entorno; ver `scripts/crear-llave-de-subida.sh`). Sin ellas el build no
        // falla —CI y Railway no las tienen—: el release sale sin firmar, y es `scripts/armar-para-play.sh`
        // el que exige que existan antes de armar el AAB que se sube.
        val llave = LlaveDeSubida.leer(providers)
        if (llave != null) {
            create("subida") {
                storeFile = file(llave.archivo)
                storePassword = llave.claveDelAlmacen
                keyAlias = llave.alias
                keyPassword = llave.claveDeLaLlave
            }
        }
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        getByName("release") {
            // R8 sigue apagado: no hay cómo probar un release minificado en la Mac del dueño (sin
            // emuladores), y una regla de keep que falte crashea en el teléfono, no en el build.
            // Queda como mejora pendiente en `docs/lanzamiento/README.md`.
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            signingConfig = signingConfigs.findByName("subida")
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
    // MainActivity es una FragmentActivity: la pide el BiometricPrompt de «Entrar con huella».
    implementation(libs.androidx.fragment)
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

/**
 * La verificación del dex, como tarea con sus entradas declaradas (vuelta 109). Antes vivía en un
 * `doLast` armado a mano para `Debug` y `Release`; con los dos canales son cuatro variantes más el AAB,
 * y repetir el cuerpo cuatro veces era la forma de que una quedara sin vigilar. Una clase de tarea, además,
 * es lo que la caché de configuración sabe guardar sin arrastrar el script.
 *
 * - [compilado]: los directorios de `.class` de `:core`, `:shared` y `:androidApp` para esta variante.
 * - [paquetes]: el directorio donde AGP dejó el APK (o el AAB) de ESTE build, con su `output-metadata.json`
 *   si lo hay (ver el comentario de abajo sobre las copias de `build-apk.sh`).
 * - Los dex se buscan en la raíz del zip (`classes2.dex`, APK) y en `base/dex/` (AAB).
 */
abstract class VerificaElDex : DefaultTask() {
    @get:Internal abstract val dexdump: Property<String>
    @get:Internal abstract val compilado: ListProperty<String>
    @get:Internal abstract val paquetes: Property<String>
    @get:Internal abstract val extension: Property<String>
    @get:Internal abstract val temporal: Property<String>

    init {
        // Siempre corre: lo que verifica es el archivo que acaba de salir, no una entrada que cambió.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verificar() {
        val dexdump = File(dexdump.get())
        check(dexdump.canExecute()) {
            "No encontré dexdump ejecutable en $dexdump. Sin él no puedo verificar el paquete, y " +
                "un paquete sin verificar no se entrega: instalá las build-tools o corregí la versión."
        }
        // `Lcom/jvillada/movi/...;` por cada .class compilado. Solo el paquete de la app:
        // las dependencias las mete AGP y no son lo que se pierde.
        val prefijo = "Lcom/jvillada/movi/"
        val dirs = compilado.get().map(::File)
        val esperadas = dirs.flatMap { raiz ->
            if (!raiz.isDirectory) emptyList() else raiz.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map { "L" + it.relativeTo(raiz).invariantSeparatorsPath.removeSuffix(".class") + ";" }
                .filter { it.startsWith(prefijo) }
                .toList()
        }.toSortedSet()
        check(esperadas.isNotEmpty()) {
            "No encontré ninguna clase compilada de Movi en $dirs. Sin eso contra qué comparar, la " +
                "verificación no prueba nada — y un paquete sin verificar no se entrega."
        }

        // **Solo los paquetes que ESTE build produjo, no todo lo que haya en la carpeta.**
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
        // que acaba de empaquetar. Si no está —o no se puede leer, o es un AAB, que no lo trae—
        // se cae a todos los archivos de esa extensión: más ruidoso, nunca más permisivo.
        val dir = File(paquetes.get())
        val ext = extension.get()
        val todos = dir.listFiles { f -> f.name.endsWith(".$ext") }.orEmpty()
        val declarados = File(dir, "output-metadata.json")
            .takeIf { it.isFile }
            ?.let { metadata ->
                Regex(""""outputFile"\s*:\s*"([^"]+)"""")
                    .findAll(metadata.readText())
                    .map { it.groupValues[1] }
                    .toSet()
            }
            .orEmpty()
        val archivos = todos.filter { declarados.isEmpty() || it.name in declarados }
        check(archivos.isNotEmpty()) { "No se armó ningún .$ext en $dir" }

        val esDex = Regex("""(base/dex/)?classes\d*\.dex""")
        archivos.forEach { paquete ->
            val donde = File(temporal.get()).also { it.deleteRecursively(); it.mkdirs() }
            val definidas = mutableSetOf<String>()
            ZipFile(paquete).use { zip ->
                for (entrada in zip.entries().asSequence()) {
                    if (!entrada.name.matches(esDex)) continue
                    val suelto = File(donde, entrada.name.substringAfterLast('/'))
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
                "${paquete.name} no DEFINE ${faltantes.size} de las ${esperadas.size} clases de " +
                    "Movi que este build compiló. La app va a crashear al abrir. Faltan:\n  " +
                    muestra + resto + "\nVolvé a armarlo con --no-build-cache, o desde el " +
                    "checkout principal en vez de un worktree."
            }
            logger.lifecycle("${paquete.name}: las ${esperadas.size} clases de Movi están adentro.")
        }
    }
}

/**
 * Vuelta 109 · **el manifiesto fusionado del canal `play` no pide nada de SMS ni de llamadas.**
 *
 * `src/play/AndroidManifest.xml` los quita con `tools:node="remove"`, pero el manifiesto que llega a
 * Play es el FUSIONADO: una dependencia nueva que pida `RECEIVE_SMS` lo volvería a meter sin que nadie
 * tocara el nuestro, y Play rechazaría la versión (o pediría la declaración de permisos) por algo que
 * la app ni usa. Esta tarea lee el fusionado de la variante y falla si aparece alguno. Va antes del
 * APK y del AAB de `play` (`dependsOn`): un paquete con SMS no llega ni a salir del build.
 */
abstract class VerificaElManifiestoSinSms : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val manifiesto: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verificar() {
        val texto = manifiesto.get().asFile.readText()
        val prohibidos = PERMISOS_PROHIBIDOS_EN_PLAY.filter { permiso ->
            Regex("""android:name\s*=\s*"android\.permission\.$permiso"""").containsMatchIn(texto)
        }
        val receptorDeSms = texto.contains("android.provider.Telephony.SMS_RECEIVED")
        check(prohibidos.isEmpty() && !receptorDeSms) {
            "El manifiesto fusionado de $name pide lo que Play no deja (SMS sin la excepción, o la exención de batería): " +
                (prohibidos + listOfNotNull("SMS_RECEIVED".takeIf { receptorDeSms })).joinToString() +
                ". Quítalo en androidApp/src/play/AndroidManifest.xml con tools:node=\"remove\" " +
                "(si lo trae una dependencia, también). Manifiesto: ${manifiesto.get().asFile}"
        }
        logger.lifecycle("$name: el manifiesto fusionado no pide SMS, MMS ni el registro de llamadas.")
    }

    companion object {
        val PERMISOS_PROHIBIDOS_EN_PLAY = listOf(
            "READ_SMS", "RECEIVE_SMS", "SEND_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH", "READ_CALL_LOG",
            "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS",
            // Revisión de lanzamiento (nov-2026): Play lo restringe a casos puntuales; el canal `play` lo
            // quita y abre la lista del sistema en su lugar (`pedirLaExencionDeBateria`).
            "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        )
    }
}

androidComponents {
    /*
     * Revisión de la vuelta 109 · **`playDebug` lleva otro id: `com.jvillada.movi.play`.** `playDebug` y
     * `directaDebug` firman con la MISMA llave de depuración, así que con el mismo id un `adb install -r`
     * del de Play reemplazaba en silencio la app de Drive del teléfono —con sus datos— y apagaba la
     * captura de SMS. Con el sufijo conviven. Solo en esa combinación: `playRelease` (lo que va a la
     * tienda) y las dos de `directa` siguen siendo `com.jvillada.movi`. La app no escribe su id en ningún
     * lado (usa `context.packageName`), así que nada más cambia.
     */
    onVariants(selector().withFlavor("canal" to "play").withBuildType("debug")) { variante ->
        variante.applicationId.set("com.jvillada.movi.play")
    }
    onVariants(selector().withFlavor("canal" to "play")) { variante ->
        val nombre = variante.name.replaceFirstChar { it.uppercase() }
        val verifica = tasks.register<VerificaElManifiestoSinSms>("verificaElManifiestoDe$nombre") {
            description = "Falla si el manifiesto fusionado de $nombre pide SMS, MMS o el registro de llamadas."
            manifiesto.set(variante.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
        }
        tasks.matching { it.name == "assemble$nombre" || it.name == "bundle$nombre" }
            .configureEach { dependsOn(verifica) }
    }
}

if (sdkDeEstaMaquina == null) {
    logger.info("Sin Android SDK: no se registra la verificación del dex (acá no se arman APKs).")
} else {
    val dexdumpDeEstaMaquina = File(sdkDeEstaMaquina, "build-tools/${android.buildToolsVersion}/dexdump").absolutePath
    // Las cuatro variantes (canal × tipo) y, para `playRelease`, también el AAB que va a Play.
    listOf("directa", "play").forEach { canal ->
        listOf("debug", "release").forEach { tipo ->
            val variante = canal + tipo.replaceFirstChar { it.uppercase() }
            val Variante = variante.replaceFirstChar { it.uppercase() }
            // Lo que el build compiló, por módulo: la salida de Kotlin para esta variante de Android,
            // o sea `commonMain` y `androidMain` juntos — justo el conjunto que tiene que llegar al
            // paquete. `:core` y `:shared` no tienen canales: su carpeta es la del tipo de build.
            val compilado = listOf(":core" to tipo, ":shared" to tipo, ":androidApp" to variante).map { (modulo, dir) ->
                project(modulo).layout.buildDirectory.dir("tmp/kotlin-classes/$dir").get().asFile.absolutePath
            }
            val delApk = tasks.register<VerificaElDex>("verificaElDexDe$Variante") {
                description = "Falla si al APK de $Variante le falta alguna clase que este build compiló."
                dexdump.set(dexdumpDeEstaMaquina)
                this.compilado.set(compilado)
                paquetes.set(layout.buildDirectory.dir("outputs/apk/$canal/$tipo").get().asFile.absolutePath)
                extension.set("apk")
                temporal.set(layout.buildDirectory.dir("tmp/dexDe$Variante").get().asFile.absolutePath)
            }
            tasks.matching { it.name == "assemble$Variante" }.configureEach { finalizedBy(delApk) }
            val delAab = tasks.register<VerificaElDex>("verificaElDexDelAabDe$Variante") {
                description = "Falla si al AAB de $Variante le falta alguna clase que este build compiló."
                dexdump.set(dexdumpDeEstaMaquina)
                this.compilado.set(compilado)
                paquetes.set(layout.buildDirectory.dir("outputs/bundle/$variante").get().asFile.absolutePath)
                extension.set("aab")
                temporal.set(layout.buildDirectory.dir("tmp/dexDelAabDe$Variante").get().asFile.absolutePath)
            }
            tasks.matching { it.name == "bundle$Variante" }.configureEach { finalizedBy(delAab) }
        }
    }
}
