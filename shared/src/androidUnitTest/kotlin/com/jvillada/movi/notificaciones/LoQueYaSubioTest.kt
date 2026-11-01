package com.jvillada.movi.notificaciones

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 92 · **Lo que ya subió no vuelve a subir; lo nuevo sí.** El barrido de la bandeja al
 * reconectarse, las notificaciones que el banco actualiza y los resúmenes de grupo, todo por el
 * mismo camino (`procesarNotificaciones`).
 */
class LoQueYaSubioTest {

    private val bancolombia = "co.com.bancolombia.personas.superapp"
    private val apps = AppsQueAvisan(listOf(bancolombia))
    private val t0 = 1_761_000_000_000L
    private val minuto = 60_000L

    private fun entrante(
        texto: String = "Compraste \$28.500 en RAPPI con tu T.Deb *4057",
        cuando: Long = t0,
        llave: String = "0|$bancolombia|7|null|10123",
        titulo: String = "Bancolombia",
        resumen: Boolean = false,
        paquete: String = bancolombia,
    ) = NotificacionEntrante(paquete, titulo, texto, esPersistente = false, esResumenDeGrupo = resumen, cuando = cuando, llave = llave)

    // ── El barrido de la bandeja ─────────────────────────────────────────────

    @Test
    fun `el barrido no duplica lo ya subido y deja pasar lo nuevo`() {
        val yaSubida = entrante()
        val antes = procesarNotificaciones(listOf(yaSubida), apps, MemoriaDeLoSubido())
        assertEquals(1, antes.subidas.size)

        // El listener se cayó; al volver, la bandeja tiene la que ya subió (misma hora de publicación)
        // y una que llegó mientras tanto.
        val nueva = entrante(texto = "Pagaste \$120.000 a Caro", cuando = t0 + 90 * minuto, llave = "0|$bancolombia|8|null|10123")
        val barrido = procesarNotificaciones(listOf(nueva, yaSubida), apps, antes.memoria)

        assertEquals(listOf("Bancolombia: Pagaste \$120.000 a Caro"), barrido.subidas.map { it.texto })
        // El id es el mismo que habría tenido llegando en vivo: el server tampoco la duplicaría.
        assertEquals(idDeNotificacion(huellaDeLaNotificacion(bancolombia, "Bancolombia", "Pagaste \$120.000 a Caro", t0 + 90 * minuto)), barrido.subidas.single().id)
    }

    @Test
    fun `el barrido pasa por la misma decision - una app fuera de la lista no sube`() {
        val ajena = entrante(paquete = "com.whatsapp", llave = "0|com.whatsapp|1|null|10200")
        assertTrue(procesarNotificaciones(listOf(ajena), apps, MemoriaDeLoSubido()).subidas.isEmpty())
    }

    @Test
    fun `un barrido repetido no sube nada`() {
        val bandeja = listOf(entrante(), entrante(texto = "Pagaste \$9.000", cuando = t0 + 5 * minuto, llave = "otra"))
        val primero = procesarNotificaciones(bandeja, apps, MemoriaDeLoSubido())
        assertEquals(2, primero.subidas.size)
        assertTrue(procesarNotificaciones(bandeja, apps, primero.memoria).subidas.isEmpty())
    }

    // ── Actualizaciones y grupos ─────────────────────────────────────────────

    @Test
    fun `el resumen del grupo se ignora`() {
        val resumen = entrante(texto = "2 movimientos nuevos", resumen = true)
        assertTrue(procesarNotificaciones(listOf(resumen), apps, MemoriaDeLoSubido()).subidas.isEmpty())
    }

    @Test
    fun `el texto grande le gana al corto recortado`() {
        val extras = mapOf(
            LlavesDeNotificacion.TEXTO to "Compraste \$28.500 en RAPPI…",
            LlavesDeNotificacion.TEXTO_GRANDE to "Compraste \$28.500 en RAPPI con tu T.Deb *4057, el 21/10/2026 a las 14:00.",
        )
        val resultado = procesarNotificaciones(listOf(entrante(texto = cuerpoDeLaNotificacion(extras))), apps, MemoriaDeLoSubido())
        assertEquals("Bancolombia: Compraste \$28.500 en RAPPI con tu T.Deb *4057, el 21/10/2026 a las 14:00.", resultado.subidas.single().texto)
    }

    @Test
    fun `una actualizacion con el mismo texto en otro minuto no duplica`() {
        val primera = procesarNotificaciones(listOf(entrante()), apps, MemoriaDeLoSubido())
        // El banco la repone al desbloquear el teléfono: misma llave, mismo texto, otra hora.
        val repuesta = procesarNotificaciones(listOf(entrante(cuando = t0 + 8 * minuto)), apps, primera.memoria)
        assertTrue(repuesta.subidas.isEmpty())
    }

    @Test
    fun `dos cobros identicos del mismo dia con un id fijo no se pierden`() {
        // Revisión de la vuelta 92: la ventana es de 10 min, no de horas. El mismo café dos veces el
        // mismo día, bajo la misma llave y con el mismo texto, son dos movimientos.
        val primera = procesarNotificaciones(listOf(entrante()), apps, MemoriaDeLoSubido())
        val segundo = procesarNotificaciones(listOf(entrante(cuando = t0 + 2 * 60 * minuto)), apps, primera.memoria)
        assertEquals(1, segundo.subidas.size)
        assertEquals(10 * minuto, VENTANA_DE_LA_MISMA_LLAVE_MS)
    }

    // ── El barrido: hasta 48 h y en silencio ─────────────────────────────────

    @Test
    fun `el barrido ignora lo publicado hace mas de 48 horas`() {
        val ahora = t0 + 50 * 60 * minuto
        val vieja = entrante(texto = "Compraste \$1.000 en VIEJA", cuando = t0, llave = "a")
        val reciente = entrante(texto = "Compraste \$2.000 en NUEVA", cuando = ahora - 60 * minuto, llave = "b")
        val barrido = procesarNotificaciones(listOf(vieja, reciente), apps, MemoriaDeLoSubido(), barridoHecho = ahora)
        assertEquals(listOf("Bancolombia: Compraste \$2.000 en NUEVA"), barrido.subidas.map { it.texto })
    }

    @Test
    fun `lo que sube del barrido va silencioso y lo que llega en vivo no`() {
        val enVivo = procesarNotificaciones(listOf(entrante()), apps, MemoriaDeLoSubido())
        assertEquals(listOf(false), enVivo.subidas.map { it.silenciosa })
        val barrido = procesarNotificaciones(listOf(entrante()), apps, MemoriaDeLoSubido(), barridoHecho = t0 + 60 * minuto)
        assertEquals(listOf(true), barrido.subidas.map { it.silenciosa })
    }

    @Test
    fun `una actualizacion con texto nuevo bajo la misma llave si sube`() {
        // Hay bancos con un id fijo para todos sus avisos: cada movimiento llega como «actualización».
        val primera = procesarNotificaciones(listOf(entrante()), apps, MemoriaDeLoSubido())
        val otra = procesarNotificaciones(listOf(entrante(texto = "Compraste \$4.000 en TIENDA", cuando = t0 + 3 * minuto)), apps, primera.memoria)
        assertEquals(1, otra.subidas.size)
    }

    @Test
    fun `el mismo texto con otra llave en otro minuto son dos cobros`() {
        val primera = procesarNotificaciones(listOf(entrante()), apps, MemoriaDeLoSubido())
        val doble = procesarNotificaciones(listOf(entrante(cuando = t0 + 2 * minuto, llave = "0|$bancolombia|9|null|10123")), apps, primera.memoria)
        assertEquals(1, doble.subidas.size)
    }

    @Test
    fun `pasada la ventana, la misma llave con el mismo texto vuelve a contar`() {
        val primera = procesarNotificaciones(listOf(entrante()), apps, MemoriaDeLoSubido())
        val manana = procesarNotificaciones(listOf(entrante(cuando = t0 + VENTANA_DE_LA_MISMA_LLAVE_MS + minuto)), apps, primera.memoria)
        assertEquals(1, manana.subidas.size)
    }

    @Test
    fun `sin llave solo cuenta la huella del minuto`() {
        val vista = VistaPorLlave("", contenidoDeLaNotificacion(bancolombia, "B", "x"), t0)
        val memoria = anotarSiEsNueva(MemoriaDeLoSubido(), "h1", vista)!!
        assertTrue(memoria.porLlave.isEmpty())
        assertNull(anotarSiEsNueva(memoria, "h1", vista))
        assertEquals(listOf("h1", "h2"), anotarSiEsNueva(memoria, "h2", vista.copy(cuando = t0 + minuto))!!.huellas)
    }

    @Test
    fun `la memoria por llave se recorta por el extremo mas viejo`() {
        var memoria = MemoriaDeLoSubido()
        (1..5).forEach { i -> memoria = anotarSiEsNueva(memoria, "h$i", VistaPorLlave("k$i", "c$i", t0 + i), tope = 3)!! }
        assertEquals(listOf("k3", "k4", "k5"), memoria.porLlave.map { it.llave })
        assertEquals(listOf("h3", "h4", "h5"), memoria.huellas)
    }
}
