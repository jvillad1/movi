import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
            }
        }
    }
    jvm()
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }
    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // SQLDelight has no wasmJs artifact. Insert a "nonWasm" intermediate
    // between commonMain and every non-wasm target. Nesting the iOS group
    // inside nonWasm makes iosMain (and androidMain, jvmMain) inherit from
    // nonWasmMain — required by Kotlin 2.1.21 metadata resolution.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonWasm") {
                withAndroidTarget()
                withJvm()
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
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

// SQLDelight 2.0.2 has no wasmJs artifact. Exclude its group from every
// wasmJs configuration so Gradle dependency resolution does not fail.
configurations.configureEach {
    if (name.startsWith("wasmJs")) {
        exclude(group = "app.cash.sqldelight")
    }
}

// SQLDelight generates its Kotlin under commonMain, so wasmJs (which inherits
// from commonMain) tries to compile it and fails because the SQLDelight runtime
// is excluded above. Move the generated dir from commonMain to nonWasmMain so
// only android/ios/jvm see it. Provider-based srcDir keeps the task-dependency
// wiring intact: nonWasm compile tasks transitively dependsOn the generate task.
afterEvaluate {
    val generateTask = tasks.named("generateCommonMainMoviDatabaseInterface")
    val generatedDirProvider = generateTask.map {
        layout.buildDirectory.dir("generated/sqldelight/code/MoviDatabase/commonMain").get()
    }

    val commonMain = kotlin.sourceSets.getByName("commonMain")
    val nonWasmMain = kotlin.sourceSets.getByName("nonWasmMain")

    // Detach generated SQLDelight dir from commonMain.
    val toRemove = commonMain.kotlin.srcDirs.filter { it.path.contains("generated/sqldelight") }.toSet()
    if (toRemove.isNotEmpty()) {
        commonMain.kotlin.setSrcDirs(commonMain.kotlin.srcDirs - toRemove)
    }
    // Reattach to nonWasmMain — Provider preserves task dependency wiring.
    nonWasmMain.kotlin.srcDir(generatedDirProvider)
}

android {
    namespace = "com.jvillada.movi.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sqldelight {
    databases {
        create("MoviDatabase") {
            packageName.set("com.jvillada.movi.shared.db")
        }
    }
}
