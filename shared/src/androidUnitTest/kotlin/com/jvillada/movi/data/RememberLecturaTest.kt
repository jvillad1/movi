package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ResumenDePeriodo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [rememberLectura] montado de verdad: lo que [CacheDeLecturasTest] prueba con la generación y el
 * período pasados a mano, acá tiene que salir del cableado del composable.
 */
@RunWith(RobolectricTestRunner::class)
class RememberLecturaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun cuenta(saldo: Long) = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = saldo))

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
    }

    private fun ahora() = Clock.System.now().toEpochMilliseconds()

    @Test
    fun `una lectura que se cruzo con una escritura no se muestra como confirmada ni se guarda, se lee otra vez`() {
        val puerta = CompletableDeferred<List<Account>>()
        var pedidos = 0
        lateinit var lectura: Lectura<List<Account>>
        composeRule.setContent {
            lectura = rememberLectura(ClaveDeLectura.Cuentas, reintento = 0) {
                pedidos++
                if (pedidos == 1) puerta.await() else cuenta(200)
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, pedidos)

        CacheDeLecturas.borrarTodo() // una escritura mientras la primera lectura viajaba
        puerta.complete(cuenta(100)) // lo que el server tenía ANTES de la escritura
        composeRule.waitForIdle()

        assertEquals(2, pedidos, "lo que volvió de antes de la escritura se descarta y se lee de nuevo")
        assertEquals(cuenta(200), lectura.valor)
        assertEquals(cuenta(200), CacheDeLecturas.ultima(ClaveDeLectura.Cuentas, ahora()))
    }

    @Test
    fun `si cada vuelta se cruza con una escritura, se muestra lo ultimo pero no se guarda`() {
        var pedidos = 0
        lateinit var lectura: Lectura<List<Account>>
        composeRule.setContent {
            lectura = rememberLectura(ClaveDeLectura.Cuentas, reintento = 0) {
                pedidos++
                CacheDeLecturas.borrarTodo()
                cuenta(pedidos.toLong())
            }
        }
        composeRule.waitForIdle()

        assertEquals(REINTENTOS_POR_ESCRITURAS, pedidos)
        assertEquals(cuenta(REINTENTOS_POR_ESCRITURAS.toLong()), lectura.valor)
        assertTrue(!lectura.actualizando)
        assertNull(CacheDeLecturas.ultima(ClaveDeLectura.Cuentas, ahora()))
    }

    @Test
    fun `una clave que depende del periodo no lee ni muestra nada hasta saber el periodo`() {
        val periodos = listOf(ResumenDePeriodo(id = "2026-09", nombre = "septiembre", desde = "2026-08-25", hasta = "2026-09-24", enCurso = true))
        // Algo guardado de ese período, para que se vea que ni eso se muestra sin período.
        CacheDeLecturas.guardar(ClaveDeLectura.Periodos, periodos, "u1", ahora(), periodo = "2026-09")
        var periodo by mutableStateOf<String?>(null)
        var pedidos = 0
        lateinit var lectura: Lectura<List<ResumenDePeriodo>>
        composeRule.setContent {
            lectura = rememberLectura(ClaveDeLectura.Periodos, reintento = 0, periodoVigente = periodo) {
                pedidos++
                periodos
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, pedidos)
        assertNull(lectura.valor)
        assertTrue(lectura.actualizando, "sin período la pantalla sigue en su esqueleto")

        periodo = "2026-09"
        composeRule.waitForIdle()

        assertEquals(1, pedidos)
        assertEquals(periodos, lectura.valor)
    }
}
