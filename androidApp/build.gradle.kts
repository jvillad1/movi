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
        versionCode = 1
        versionName = "1.0"
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
    implementation(compose.ui)
    implementation(compose.components.uiToolingPreview)
    implementation(libs.androidx.activity.compose)
    debugImplementation(compose.uiTooling)
    testImplementation(kotlin("test"))
}
