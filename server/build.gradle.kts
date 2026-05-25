plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
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
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test)
}
