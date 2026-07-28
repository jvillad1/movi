import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        versionCode = 2
        versionName = "1.1"
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
    testImplementation(kotlin("test"))
    // JVM unit tests only (not shipped in the APK): AGP's mockable android.jar stubs
    // org.json to throw at runtime, so real parsing needs the real implementation here.
    testImplementation(libs.org.json)
}
