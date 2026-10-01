package com.jvillada.movi.sms

import com.jvillada.movi.shared.model.TransactionType
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lo que el teléfono lee de la respuesta de `POST /api/sms/sync` para avisar (Ola 1). Robolectric
 * por el `org.json` de `parseSyncedCount`, que en la JVM pelada es un stub que lanza.
 */
@RunWith(RobolectricTestRunner::class)
class RespuestaDelSyncTest {

    @Test
    fun `lee lo que quedo por revisar`() {
        val cuerpo = """
            {
              "synced": 2,
              "porRevisar": [
                {"id": "sms_rt_1", "origen": "85540", "monto": 180000.0, "moneda": "COP",
                 "descripcion": "Transferencia a Caro", "tipo": "EXPENSE"}
              ]
            }
        """.trimIndent()
        assertEquals(2, parseSyncedCount(cuerpo))
        val aviso = parsePorRevisar(cuerpo).single()
        assertEquals("sms_rt_1", aviso.id)
        assertEquals(180_000.0, aviso.monto)
        assertEquals("Transferencia a Caro", aviso.descripcion)
        assertEquals(TransactionType.EXPENSE, aviso.tipo)
    }

    @Test
    fun `un server anterior al campo no rompe nada`() {
        assertEquals(1, parseSyncedCount("""{"synced": 1}"""))
        assertEquals(emptyList(), parsePorRevisar("""{"synced": 1}"""))
    }

    @Test
    fun `un cuerpo ilegible tampoco`() {
        assertEquals(emptyList(), parsePorRevisar("<html>502</html>"))
        assertEquals(emptyList(), parsePorRevisar(null))
    }
}
