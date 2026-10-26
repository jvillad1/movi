package com.jvillada.movi.sms

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo que el banco mandó con la sesión cerrada por el server se guarda y espera a su dueño
 * ([CapturasPorSubir]), y cómo se lee la respuesta del sync que decide eso ([resultadoDelSync]).
 */
@RunWith(RobolectricTestRunner::class)
class CapturasPorSubirTest {

    private val contexto: Context get() = ApplicationProvider.getApplicationContext()

    private fun captura(id: String, userId: String = "usr_1", notificacion: Boolean = false) =
        CapturasPorSubir.Captura(id, "85540", "Compraste \$27.500 en Las Doce", 1_792_990_000_000L, notificacion, userId)

    @After fun vaciar() {
        CapturasPorSubir.quitarSubidas(contexto, "nadie", emptySet())
    }

    @Test
    fun `una captura guardada sobrevive y se lee con todo lo suyo`() {
        CapturasPorSubir.guardar(contexto, captura("a", notificacion = true))
        assertEquals(listOf(captura("a", notificacion = true)), CapturasPorSubir.deQuien(contexto, "usr_1"))
    }

    @Test
    fun `la misma captura dos veces no se duplica`() {
        CapturasPorSubir.guardar(contexto, captura("a"))
        CapturasPorSubir.guardar(contexto, captura("a"))
        assertEquals(1, CapturasPorSubir.cuantas(contexto))
    }

    @Test
    fun `al subir se quitan las subidas y lo de otra persona`() {
        CapturasPorSubir.guardar(contexto, captura("a"))
        CapturasPorSubir.guardar(contexto, captura("b"))
        CapturasPorSubir.guardar(contexto, captura("c", userId = "usr_otro"))

        assertEquals(listOf("a", "b"), CapturasPorSubir.deQuien(contexto, "usr_1").map { it.id })

        CapturasPorSubir.quitarSubidas(contexto, "usr_1", setOf("a"))
        assertEquals(listOf("b"), CapturasPorSubir.deQuien(contexto, "usr_1").map { it.id })
        assertEquals(1, CapturasPorSubir.cuantas(contexto), "lo de usr_otro no se le sube a usr_1: se descarta")
    }

    @Test
    fun `el tope deja las mas recientes`() {
        val lista = (1..5).fold(emptyList<CapturasPorSubir.Captura>()) { acc, i -> CapturasPorSubir.agregar(acc, captura("$i"), maximo = 3) }
        assertEquals(listOf("3", "4", "5"), lista.map { it.id })
    }

    @Test
    fun `una lista ilegible es una lista vacia`() {
        assertTrue(CapturasPorSubir.decodificar("{no es json").isEmpty())
        assertTrue(CapturasPorSubir.decodificar(null).isEmpty())
    }

    @Test
    fun `sin sesion se guarda solo si la cerro el server`() {
        assertEquals("usr_1", duenoDeLaCapturaSinSesion("usr_1"))
        assertNull(duenoDeLaCapturaSinSesion(null), "salió a mano: no hay a quién esperar")
        assertNull(duenoDeLaCapturaSinSesion(""))
    }

    @Test
    fun `el 401 es sesion rechazada, el 5xx se reintenta y otro codigo se rechaza`() {
        assertIs<SmsSyncResult.Unauthorized>(resultadoDelSync(401, null))
        assertEquals(SmsSyncResult.ServerError(503), resultadoDelSync(503, null))
        assertEquals(SmsSyncResult.Rejected(413), resultadoDelSync(413, null))
        assertIs<SmsSyncResult.Success>(resultadoDelSync(200, """{"synced":1}"""))
    }
}
