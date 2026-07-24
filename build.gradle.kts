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
