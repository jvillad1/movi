package com.jvillada.movi.notificaciones

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La regla de captura por notificaciones, sin Android de por medio.
 *
 * Lo que se fija acá es sobre todo **lo que NO sale del teléfono**: el filtro por paquete es la
 * única barrera entre la bandeja de Movi y las notificaciones personales del dueño, y una barrera
 * sin prueba es una barrera hasta el próximo refactor.
 */
class FiltroDeNotificacionesTest {

    private val soloBancolombia = AppsQueAvisan(listOf("com.todo1.mobile"))

    private fun entrante(
        paquete: String = "com.todo1.mobile",
        titulo: String = "Compra aprobada",
        texto: String = "Compra por \$28.500 en Rappi",
        esPersistente: Boolean = false,
        esResumenDeGrupo: Boolean = false,
        cuando: Long = 1_757_000_000_000L,
    ) = NotificacionEntrante(paquete, titulo, texto, esPersistente, esResumenDeGrupo, cuando)

    // ── La lista de apps ──────────────────────────────────────────────────────

    @Test
    fun `una app de la lista se captura`() {
        val decision = decidirNotificacion(entrante(), soloBancolombia)
        assertEquals(DecisionDeNotificacion.Subir("Compra aprobada: Compra por \$28.500 en Rappi"), decision)
    }

    @Test
    fun `una app fuera de la lista se descarta por el paquete, no por el contenido`() {
        // El texto es idéntico al de arriba a propósito: lo único que cambia es quién lo publicó.
        val decision = decidirNotificacion(entrante(paquete = "com.whatsapp"), soloBancolombia)
        assertEquals(DecisionDeNotificacion.Descartar(MotivoDeDescarte.APP_FUERA_DE_LA_LISTA), decision)
    }

    @Test
    fun `el paquete se compara por igualdad y no por substring`() {
        // Un `contains` daría por buena a una app llamada así justamente para colarse.
        assertFalse(FiltroDeNotificaciones.laAppEstaEnLaLista("com.malo.com.todo1.mobile", soloBancolombia))
        assertFalse(FiltroDeNotificaciones.laAppEstaEnLaLista("com.todo1.mobile.falsa", soloBancolombia))
        assertTrue(FiltroDeNotificaciones.laAppEstaEnLaLista("com.todo1.mobile", soloBancolombia))
    }

    @Test
    fun `una lista vacía no captura nada`() {
        // Es la forma de apagar este sensor desde el server, así que tiene que apagarlo de verdad.
        assertEquals(
            DecisionDeNotificacion.Descartar(MotivoDeDescarte.APP_FUERA_DE_LA_LISTA),
            decidirNotificacion(entrante(), AppsQueAvisan(emptyList())),
        )
    }

    @Test
    fun `una entrada en blanco de la lista no habilita a la app sin paquete`() {
        assertFalse(FiltroDeNotificaciones.laAppEstaEnLaLista("", AppsQueAvisan(listOf(""))))
    }

    // ── El ruido ──────────────────────────────────────────────────────────────

    @Test
    fun `una notificación persistente no es un movimiento`() {
        assertEquals(
            DecisionDeNotificacion.Descartar(MotivoDeDescarte.PERSISTENTE),
            decidirNotificacion(entrante(esPersistente = true), soloBancolombia),
        )
    }

    @Test
    fun `el resumen de un grupo no es un movimiento`() {
        assertEquals(
            DecisionDeNotificacion.Descartar(MotivoDeDescarte.RESUMEN_DE_GRUPO),
            decidirNotificacion(entrante(esResumenDeGrupo = true), soloBancolombia),
        )
    }

    @Test
    fun `sin cuerpo no hay nada que parsear, aunque haya título`() {
        assertEquals(
            DecisionDeNotificacion.Descartar(MotivoDeDescarte.SIN_TEXTO),
            decidirNotificacion(entrante(texto = "   "), soloBancolombia),
        )
    }

    // ── El texto que se sube ──────────────────────────────────────────────────

    @Test
    fun `el título se antepone porque ahí suele estar la mitad de la frase`() {
        assertEquals("Compra aprobada: \$28.500 en Rappi", textoDeLaNotificacion("Compra aprobada", "\$28.500 en Rappi"))
    }

    @Test
    fun `sin título se sube el cuerpo tal cual`() {
        assertEquals("Compra por \$28.500", textoDeLaNotificacion("  ", "Compra por \$28.500 "))
    }

    @Test
    fun `un cuerpo que ya empieza con el título no lo repite`() {
        // Repetirlo le daría al dedupe por texto del server dos cadenas para el mismo hecho.
        assertEquals("Bancolombia te informa: compra por \$1.000", textoDeLaNotificacion("Bancolombia", "Bancolombia te informa: compra por \$1.000"))
    }

    @Test
    fun `un texto enorme se recorta`() {
        val largo = "x".repeat(2_000)
        assertEquals(MAX_TEXTO_DE_NOTIFICACION, textoDeLaNotificacion("", largo).length)
    }

    @Test
    fun `la marca de origen nombra la app y cabe en la columna`() {
        assertEquals("Notificación · Bancolombia", marcaDeOrigen("Bancolombia"))
        assertTrue(marcaDeOrigen("N".repeat(300)).length <= MAX_MARCA_DE_ORIGEN)
        assertEquals("Notificación · otra app", marcaDeOrigen("   "))
    }

    // ── La memoria de lo ya subido ────────────────────────────────────────────

    @Test
    fun `la misma notificación dentro del mismo minuto tiene la misma huella`() {
        val a = huellaDeLaNotificacion("com.todo1.mobile", "Compra", "\$1.000", 1_757_000_000_000L)
        val b = huellaDeLaNotificacion("com.todo1.mobile", "Compra", "\$1.000", 1_757_000_000_000L + 25_000L)
        assertEquals(a, b)
    }

    @Test
    fun `dos cobros iguales en minutos distintos son dos huellas`() {
        // Un doble cobro real existe: colapsarlo sería perder plata en silencio.
        val a = huellaDeLaNotificacion("com.todo1.mobile", "Compra", "\$1.000", 1_757_000_000_000L)
        val b = huellaDeLaNotificacion("com.todo1.mobile", "Compra", "\$1.000", 1_757_000_000_000L + 120_000L)
        assertTrue(a != b)
    }

    @Test
    fun `una huella repetida no se vuelve a subir`() {
        val huella = "com.todo1.mobile|Compra|\$1.000|29283333"
        val despues = recordarHuella(emptyList(), huella)!!
        assertEquals(listOf(huella), despues)
        assertNull(recordarHuella(despues, huella))
    }

    @Test
    fun `la memoria se recorta por el extremo más viejo`() {
        val vistas = (1..5).map { "h$it" }
        val nueva = recordarHuella(vistas, "h6", tope = 3)!!
        assertEquals(listOf("h4", "h5", "h6"), nueva)
    }

    @Test
    fun `el id sale de la huella, es estable y no se confunde con el de un SMS`() {
        val huella = huellaDeLaNotificacion("com.todo1.mobile", "Compra", "\$1.000", 1_757_000_000_000L)
        val id = idDeNotificacion(huella)
        assertEquals(id, idDeNotificacion(huella))
        assertTrue(id.startsWith("notif_"))
        assertFalse(id.startsWith("sms_"))
        // La columna `id` de sms_messages es varchar(50).
        assertTrue(id.length <= 50)
    }

    // ── La lista que sirve el server ──────────────────────────────────────────

    @Test
    fun `la lista de apps se lee del mismo json del filtro de sms`() {
        val config = appsQueAvisan("""{"senderCodes":["85540"],"bodyKeywords":["bancolombia"],"appPackages":["com.todo1.mobile","com.glim.app"]}""")
        assertEquals(listOf("com.todo1.mobile", "com.glim.app"), config!!.paquetes)
    }

    @Test
    fun `un server viejo sin la clave devuelve null y el teléfono se queda con sus defaults`() {
        assertNull(appsQueAvisan("""{"senderCodes":["85540"],"bodyKeywords":["bancolombia"]}"""))
        assertNull(appsQueAvisan("no es json"))
    }

    @Test
    fun `una lista remota vacía SÍ se respeta — es como se apaga este sensor desde el server`() {
        // Al revés que el filtro de SMS, donde una config vacía cae a los defaults compilados.
        val config = appsQueAvisan("""{"appPackages":[]}""")
        assertEquals(emptyList(), config!!.paquetes)
    }
}
