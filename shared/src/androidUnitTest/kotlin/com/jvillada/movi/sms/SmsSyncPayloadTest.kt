package com.jvillada.movi.sms

import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import org.json.JSONArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmsSyncPayloadTest {

    @Test
    fun `el payload de una captura conserva la forma que espera el server`() {
        val payload = buildSmsSyncPayload(listOf(captureItem("sms_rt_abc", "85540", "Compra por \$1.000", 0L)))
        val obj = JSONArray(payload).getJSONObject(0)
        assertEquals("sms_rt_abc", obj.getString("id"))
        assertEquals("85540", obj.getString("bank"))
        assertEquals("Compra por \$1.000", obj.getString("text"))
        // El server pisa el `state` que venga (es el dueño del estado), pero el payload manda el
        // mismo nombre que usa todo el sistema: nada del lado del cliente habla de "new".
        assertEquals(SMS_STATE_PENDING, obj.getString("state"))
        assertEquals("", obj.getString("det"))
    }

    @Test
    fun `remitente en blanco se normaliza a SMS en los dos caminos`() {
        // El inbox deja "" y el broadcast puede traer null: si divergen, el mismo SMS
        // físico entra al server con dos formas distintas.
        val fromCapture = JSONArray(buildSmsSyncPayload(listOf(captureItem("a", "", "hola", 0L))))
        val fromInbox = JSONArray(buildSmsSyncPayload(listOf(SmsSyncItem("b", "2026-08-01 10:00", "", "hola"))))
        assertEquals("SMS", fromCapture.getJSONObject(0).getString("bank"))
        assertEquals("SMS", fromInbox.getJSONObject(0).getString("bank"))
    }

    @Test
    fun `el lote del backfill viaja en un solo array`() {
        val items = (1..3).map { SmsSyncItem("id$it", "2026-08-01 10:0$it", "85540", "compra $it") }
        assertEquals(3, JSONArray(buildSmsSyncPayload(items)).length())
    }

    @Test
    fun `el conteo del server se lee de la respuesta`() {
        assertEquals(7, parseSyncedCount("""{"synced":7}"""))
    }

    @Test
    fun `una respuesta ausente o rara no rompe el reporte`() {
        assertNull(parseSyncedCount(null))
        assertNull(parseSyncedCount(""))
        assertNull(parseSyncedCount("not json"))
        assertNull(parseSyncedCount("""{"otra":1}"""))
    }
}
