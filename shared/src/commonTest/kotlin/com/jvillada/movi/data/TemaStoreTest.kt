package com.jvillada.movi.data

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lo único que hay que sostener del interruptor de tema: **que arranque en oscuro**.
 *
 * No es un detalle de implementación. Oscuro es el único tema que Movi tuvo desde siempre, y el
 * día que alguien abra la app después de este cambio no puede encontrarse con otra cosa. Un
 * default distinto no es una preferencia: es una sorpresa.
 *
 * No se prueba la persistencia: `Settings()` no existe en una JVM pelada, y por eso mismo el store
 * envuelve cada lectura y escritura en `runCatching`. Lo que sí se prueba es que **sin lugar donde
 * guardar el interruptor sigue funcionando**, que es la garantía que importa — un almacenamiento
 * bloqueado no puede dejar la app sin tema.
 */
class TemaStoreTest {

    @AfterTest
    fun limpiar() = TemaStore.clear()

    @Test
    fun `arranca en oscuro`() {
        TemaStore.clear()
        assertTrue(TemaStore.oscuro, "El default tiene que ser oscuro: es el único tema que Movi tuvo")
    }

    @Test
    fun `el interruptor funciona aunque no haya donde guardar`() {
        TemaStore.clear()
        TemaStore.poner(false)
        assertEquals(false, TemaStore.oscuro, "Poner el claro tiene que verse en el acto")
        TemaStore.alternar()
        assertEquals(true, TemaStore.oscuro, "Alternar tiene que volver al oscuro")
    }
}
