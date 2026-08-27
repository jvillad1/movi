package com.jvillada.movi.data

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ola 11 — la memoria de la última cuenta usada.
 *
 * **Este test corre en una JVM donde `Settings()` no se puede construir** (no hay contexto de
 * Android ni almacenamiento del navegador), así que además de probar la lógica prueba lo que la
 * ola pasada aprendió a los golpes: que tocar este `object` **no tumba nada**. Si la lectura del
 * inicializador no estuviera envuelta en `runCatching`, todos los tests de este archivo —y la
 * suite entera, por el `ExceptionInInitializerError`— se caerían acá.
 */
class LastAccountStoreTest {

    @BeforeTest fun limpiarAntes() = LastAccountStore.clear()
    @AfterTest fun limpiarDespues() = LastAccountStore.clear()

    @Test
    fun `arranca sin memoria y no explota aunque no haya donde guardar`() {
        assertNull(LastAccountStore.lastAccountId)
        assertNull(LastAccountStore.lastTransferFromId)
        assertNull(LastAccountStore.lastTransferToId)
    }

    @Test
    fun `recuerda la ultima cuenta de un movimiento`() {
        LastAccountStore.recordAccount("acc_n")
        assertEquals("acc_n", LastAccountStore.lastAccountId)
    }

    @Test
    fun `un id vacio o nulo no borra lo que ya se sabia`() {
        LastAccountStore.recordAccount("acc_n")
        LastAccountStore.recordAccount(null)
        LastAccountStore.recordAccount("   ")
        assertEquals("acc_n", LastAccountStore.lastAccountId)
    }

    @Test
    fun `un traspaso guarda su par y NO toca la cuenta de los movimientos`() {
        LastAccountStore.recordAccount("acc_n")
        LastAccountStore.recordTransfer("acc_b", "acc_e")

        assertEquals("acc_b", LastAccountStore.lastTransferFromId)
        assertEquals("acc_e", LastAccountStore.lastTransferToId)
        // Lo importante: un traspaso mueve plata entre DOS cuentas, así que no puede decidir en
        // cuál se anota el próximo gasto.
        assertEquals("acc_n", LastAccountStore.lastAccountId)
    }

    @Test
    fun `un traspaso a medias no se guarda`() {
        LastAccountStore.recordTransfer("acc_b", null)
        assertNull(LastAccountStore.lastTransferFromId)
        assertNull(LastAccountStore.lastTransferToId)
    }

    @Test
    fun `cerrar sesion borra la memoria del usuario que se va`() {
        LastAccountStore.recordAccount("acc_n")
        LastAccountStore.recordTransfer("acc_b", "acc_e")

        LastAccountStore.clear()

        assertNull(LastAccountStore.lastAccountId)
        assertNull(LastAccountStore.lastTransferFromId)
        assertNull(LastAccountStore.lastTransferToId)
    }
}
