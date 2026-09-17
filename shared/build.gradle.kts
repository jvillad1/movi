import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
}
