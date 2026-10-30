import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

// El server corre en `eclipse-temurin:17-jre` (Railway). Sin esto, la versión de clase sale del JDK
// que corre Gradle: con el JBR 21 de GitHub Actions (vuelta 69, el JAR se arma ahí) daba clases de
// Java 21 que el JRE 17 no carga. `-Xjdk-release=17` además compila contra la API de 17.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// **El fat JAR no lleva `sqlite-jdbc`** (vuelta 69). Llega por `:core` (el `jvm()` usa el driver
// SQLite de SQLDelight para el espejo local de los clientes), pero el server no abre ninguna base
// SQLite: producción es Postgres. Son 12,9 MB de nativos para todas las plataformas, el 13 % del
// JAR, y el JAR viaja entero en cada `railway up`. Solo sale del fat JAR: `:server:run` y las
// pruebas siguen viéndolo. Va acá y no en el workflow para que `desplegar.sh --desde-fuente` arme
// el mismo JAR.
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    dependencies {
        exclude(dependency("org.xerial:sqlite-jdbc"))
    }
}

application {
    mainClass.set("com.jvillada.movi.server.ApplicationKt")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=${project.findProperty("development") ?: "false"}")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.compression)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.logback)
    implementation(libs.anthropic.java)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.bcrypt)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.postgresql.driver)
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)
    implementation(libs.webpush)
    implementation(libs.bcprov)
    // web-push's POM declares httpasyncclient/httpcore as runtime-scope only, so PushService's
    // return type (org.apache.http.HttpResponse) isn't visible at compile time without this.
    implementation(libs.httpcore)
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.h2)
}
