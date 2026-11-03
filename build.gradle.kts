import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

// Every Kotlin/Native link runs inside the single Kotlin daemon
// (kotlin.daemon.jvmargs). An optimized (release) link needs several GB of heap
// on its own, and the configuration cache schedules the :core and :shared iOS
// release links concurrently — together they exhaust the daemon heap on a 16 GB
// machine, and raising kotlin.daemon.jvmargs further starves the OS instead.
// Gate the optimized links through a shared build service so at most one runs
// at a time; debug links are cheap and stay fully parallel.
abstract class KotlinNativeOptimizedLinkGate : BuildService<BuildServiceParameters.None>

val optimizedLinkGate = gradle.sharedServices.registerIfAbsent(
    "kotlinNativeOptimizedLinkGate",
    KotlinNativeOptimizedLinkGate::class,
) {
    maxParallelUsages.set(1)
}

subprojects {
    tasks.withType<KotlinNativeLink>().configureEach {
        if (binary.optimized) {
            usesService(optimizedLinkGate)
        }
    }
}

// ── Vuelta 122 · las pruebas siempre corren; lo demás sale de la caché ──────────────────────────
//
// Hasta acá CI y CLAUDE.md usaban `--rerun-tasks`, que vuelve a correr las pruebas… y también
// recompila TODO, sin mirar la caché de build. La razón de fondo era buena: este repo ya tuvo una
// prueba en verde que no se ejecutaba porque Gradle la daba por «al día» (sus entradas reales eran
// archivos de otro módulo). Así que la regla queda escrita acá, para todos los módulos y para
// cualquiera que llame una tarea de prueba: una `Test` **nunca** está al día ni sale de la caché.
// La compilación sí (es determinista y sus entradas están todas declaradas).
//
// `movi.forksDePruebas` (por defecto 1): cuántas JVM de prueba corren a la vez dentro de un módulo.
// CI pide 2 en sus máquinas de 4 núcleos; en la Mac de 16 GB queda en 1 (cada fork de Robolectric
// es otra JVM con su heap).
//
// `movi.trozoDePruebas=i/n` (opcional): corre solo el trozo i de n de las clases de prueba, para
// repartir una suite entre varias máquinas de CI. Cada clase cae en UN trozo por su nombre (el
// `hashCode` del nombre, que no cambia entre corridas), con sus clases internas (`Foo$1`), así que
// los n trozos juntos son exactamente la suite entera. Sin la propiedad, nada cambia.
val forksDePruebas = providers.gradleProperty("movi.forksDePruebas").map { it.toInt() }.orElse(1)
val trozoDePruebas = providers.gradleProperty("movi.trozoDePruebas").orNull?.let { valor ->
    val partes = valor.split("/").map { it.trim().toInt() }
    require(partes.size == 2 && partes[1] >= 1 && partes[0] in 1..partes[1]) {
        "movi.trozoDePruebas tiene que ser i/n con 1 ≤ i ≤ n, no «$valor»"
    }
    partes[0] to partes[1]
}
subprojects {
    tasks.withType<Test>().configureEach {
        outputs.upToDateWhen { false }
        outputs.doNotCacheIf("Las pruebas siempre corren (vuelta 122)") { true }
        maxParallelForks = forksDePruebas.get()
        trozoDePruebas?.let { (trozo, de) ->
            exclude { archivo ->
                if (archivo.isDirectory || !archivo.name.endsWith(".class")) return@exclude false
                val clase = archivo.relativePath.pathString.removeSuffix(".class").substringBefore('$')
                Math.floorMod(clase.hashCode(), de) != trozo - 1
            }
        }
    }
}
