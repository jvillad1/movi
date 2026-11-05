package com.jvillada.movi.shared.model

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Vuelta 129 · la huella SHA-256 escrita a mano en `:core` da lo mismo que `MessageDigest` (lo que usa
 * el server, `huellaDelPapel`): si no, la hoja de subida preguntaría por un archivo que el server
 * nunca reconocería.
 */
class HuellaDeLosBytesTest {

    private fun deJava(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `los vectores conocidos`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", huellaSha256(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", huellaSha256("abc".encodeToByteArray()))
    }

    @Test
    fun `da lo mismo que MessageDigest en los bordes del bloque y en un archivo grande`() {
        val azar = Random(129)
        for (largo in listOf(1, 55, 56, 57, 63, 64, 65, 119, 120, 127, 128, 129, 1000)) {
            val bytes = azar.nextBytes(largo)
            assertEquals(deJava(bytes), huellaSha256(bytes), "largo $largo")
        }
        val pdf = azar.nextBytes(3 * 1024 * 1024 + 7)
        assertEquals(deJava(pdf), huellaSha256(pdf))
    }
}
