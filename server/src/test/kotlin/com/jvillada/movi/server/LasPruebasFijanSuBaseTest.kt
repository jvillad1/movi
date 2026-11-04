package com.jvillada.movi.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ninguna prueba del server conecta su base con `Database.connect` a secas: todas pasan por
 * [conectarBaseDePrueba], que además la fija como la de por defecto (ver su comentario).
 */
class LasPruebasFijanSuBaseTest {
    @Test
    fun `toda base de prueba se conecta con conectarBaseDePrueba`() {
        val raiz = File("src/test/kotlin")
        assertTrue(raiz.isDirectory, "no encontré ${raiz.absolutePath}")
        val directas = raiz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "BaseDePrueba.kt" && it.name != "LasPruebasFijanSuBaseTest.kt" }
            .filter { "Database.connect(" in it.readText() }
            .map { it.relativeTo(raiz).path }
            .toList()
        assertTrue(directas.isEmpty(), "Usa conectarBaseDePrueba(...) en vez de Database.connect(...): $directas")
    }
}
