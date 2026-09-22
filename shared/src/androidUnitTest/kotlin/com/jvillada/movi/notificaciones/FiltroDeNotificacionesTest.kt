package com.jvillada.movi.notificaciones

import android.app.Notification
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

    // ── De dónde sale el cuerpo ────────────────────────────────────

    /**
     * La alerta de compra tal como la escribe Bancolombia: larga, con el comercio, la tarjeta y la
     * fecha. Es la que el 21-sep se probó de punta a punta contra el teléfono del dueño.
     */
    private val COMPRA_REAL =
        "Bancolombia: Compraste \$12.345,00 en PRUEBA DE MOVI con tu T.Deb *4057, el 21/09/2026 a las 14:00."

    @Test
    fun `el texto grande le gana al corto, que es el recortado`() {
        // El caso de todos los días: la misma alerta, entera en bigText y mocha en text.
        val cuerpo = cuerpoDeLaNotificacion(
            mapOf(
                LlavesDeNotificacion.TEXTO_GRANDE to COMPRA_REAL,
                LlavesDeNotificacion.TEXTO to "Compraste \$12.345,00 en PRUEBA…",
            )
        )
        assertEquals(COMPRA_REAL, cuerpo)
    }

    @Test
    fun `solo texto grande`() {
        assertEquals(COMPRA_REAL, cuerpoDeLaNotificacion(mapOf(LlavesDeNotificacion.TEXTO_GRANDE to COMPRA_REAL)))
    }

    @Test
    fun `solo texto corto`() {
        assertEquals(COMPRA_REAL, cuerpoDeLaNotificacion(mapOf(LlavesDeNotificacion.TEXTO to COMPRA_REAL)))
    }

    @Test
    fun `un texto grande en blanco cae al corto en vez de tirar el movimiento`() {
        // Un `?:` sobre nulos se quedaba con el vacío: la llave ESTÁ, solo que no dice nada.
        assertEquals(
            COMPRA_REAL,
            cuerpoDeLaNotificacion(
                mapOf(LlavesDeNotificacion.TEXTO_GRANDE to "   ", LlavesDeNotificacion.TEXTO to COMPRA_REAL)
            ),
        )
    }

    @Test
    fun `las líneas de una bandeja se juntan en una sola línea`() {
        // `InboxStyle`: la app agrupa varios avisos y el cuerpo vive en un arreglo, no en un texto.
        val cuerpo = cuerpoDeLaNotificacion(
            mapOf(
                LlavesDeNotificacion.LINEAS to arrayOf<CharSequence>(
                    "Compra por \$10.000 en TIENDA",
                    "   ",
                    "Compra por \$20.000 en OTRA",
                )
            )
        )
        assertEquals("Compra por \$10.000 en TIENDA Compra por \$20.000 en OTRA", cuerpo)
    }

    @Test
    fun `el resumen es el último recurso, pero es mejor que nada`() {
        assertEquals(
            "Movimiento por \$5.000",
            cuerpoDeLaNotificacion(mapOf(LlavesDeNotificacion.RESUMEN to "Movimiento por \$5.000")),
        )
    }

    @Test
    fun `sin ninguna llave con texto el cuerpo es vacío`() {
        assertEquals("", cuerpoDeLaNotificacion(emptyMap()))
        assertEquals(
            "",
            cuerpoDeLaNotificacion(
                mapOf(
                    LlavesDeNotificacion.TEXTO_GRANDE to "",
                    LlavesDeNotificacion.TEXTO to "  ",
                    LlavesDeNotificacion.LINEAS to arrayOf<CharSequence>(" "),
                    LlavesDeNotificacion.RESUMEN to null,
                )
            ),
        )
    }

    @Test
    fun `una notificación con título y sin cuerpo se descarta, no se sube`() {
        // La fila «Bancolombia:» a secas no la puede parsear nadie y solo se saca a mano.
        val extras = mapOf<String, Any?>(LlavesDeNotificacion.TITULO to "Bancolombia")
        assertEquals("", cuerpoDeLaNotificacion(extras))
        assertEquals(
            DecisionDeNotificacion.Descartar(MotivoDeDescarte.SIN_TEXTO),
            decidirNotificacion(
                entrante(titulo = tituloDeLaNotificacion(extras), texto = cuerpoDeLaNotificacion(extras)),
                soloBancolombia,
            ),
        )
    }

    @Test
    fun `el título cae al título grande cuando la alerta viene expandida`() {
        assertEquals("Bancolombia", tituloDeLaNotificacion(mapOf(LlavesDeNotificacion.TITULO_GRANDE to "Bancolombia")))
        assertEquals(
            "Bancolombia",
            tituloDeLaNotificacion(
                mapOf(LlavesDeNotificacion.TITULO to " ", LlavesDeNotificacion.TITULO_GRANDE to "Bancolombia")
            ),
        )
        assertEquals("", tituloDeLaNotificacion(emptyMap()))
    }

    @Test
    fun `la compra real del teléfono sube entera, no solo el título`() {
        // La medición del 21-sep: título «Bancolombia» y la compra larga en el texto grande.
        val extras = mapOf<String, Any?>(
            LlavesDeNotificacion.TITULO to "Bancolombia",
            LlavesDeNotificacion.TEXTO_GRANDE to COMPRA_REAL,
        )
        val decision = decidirNotificacion(
            entrante(titulo = tituloDeLaNotificacion(extras), texto = cuerpoDeLaNotificacion(extras)),
            soloBancolombia,
        )
        // El cuerpo ya empieza con el título, así que no se repite: sube la frase del banco tal cual.
        assertEquals(DecisionDeNotificacion.Subir(COMPRA_REAL), decision)
        // Y lo que sube es lo que `parseSms` sabe leer —el monto, el comercio y la tarjeta siguen
        // ahí—; el server lo fija en `ElTextoDeUnaNotificacionSeParseaTest`.
        assertTrue(COMPRA_REAL.contains("\$12.345,00") && COMPRA_REAL.contains("PRUEBA DE MOVI"))
    }

    @Test
    fun `las llaves son las de Android, no una copia que se desincronizó`() {
        // Si Android renombrara un extra —o si alguien tipeara mal un literal— el cuerpo volvería a
        // llegar vacío en silencio. Son constantes de compilación: acá no se toca android.jar.
        assertEquals(Notification.EXTRA_TITLE, LlavesDeNotificacion.TITULO)
        assertEquals(Notification.EXTRA_TITLE_BIG, LlavesDeNotificacion.TITULO_GRANDE)
        assertEquals(Notification.EXTRA_TEXT, LlavesDeNotificacion.TEXTO)
        assertEquals(Notification.EXTRA_BIG_TEXT, LlavesDeNotificacion.TEXTO_GRANDE)
        assertEquals(Notification.EXTRA_TEXT_LINES, LlavesDeNotificacion.LINEAS)
        assertEquals(Notification.EXTRA_SUMMARY_TEXT, LlavesDeNotificacion.RESUMEN)
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

    // ── Nu Colombia ───────────────────────────────────────────────────────────

    /** La lista que sirve el server desde este cambio (`SmsFilterConfigRoutes.CURRENT_FILTER`). */
    private val deLaConfigDelServer = appsQueAvisan(
        """{"senderCodes":["85540"],"bodyKeywords":["bancolombia","nubank"],"appPackages":[""" +
            """"co.com.bancolombia.personas.superapp","com.app.prontomas",""" +
            """"com.google.android.apps.walletnfcrel","com.nu.production"]}""",
    )!!

    @Test
    fun `Nu está en la lista del server y en la de respaldo`() {
        assertTrue(FiltroDeNotificaciones.laAppEstaEnLaLista("com.nu.production", deLaConfigDelServer))
        assertTrue(FiltroDeNotificaciones.laAppEstaEnLaLista("com.nu.production", FiltroDeNotificaciones.DEFAULTS))
        // Igualdad exacta, también para Nu.
        assertFalse(FiltroDeNotificaciones.laAppEstaEnLaLista("com.nu.production.falsa", deLaConfigDelServer))
    }

    @Test
    fun `Bancolombia y Glim siguen en las dos listas`() {
        listOf("co.com.bancolombia.personas.superapp", "com.app.prontomas").forEach { paquete ->
            assertTrue(FiltroDeNotificaciones.laAppEstaEnLaLista(paquete, deLaConfigDelServer), paquete)
            assertTrue(FiltroDeNotificaciones.laAppEstaEnLaLista(paquete, FiltroDeNotificaciones.DEFAULTS), paquete)
        }
    }

    /**
     * **Las dos notificaciones reales de Nu**, con el espacio del final que trae su cuerpo. Lo que
     * sube es título + «: » + cuerpo recortado — la misma cadena que el server fija en
     * `ElTextoDeUnaNotificacionSeParseaTest`.
     */
    @Test
    fun `las compras de Nu suben con título y cuerpo en una línea`() {
        val crepes = decidirNotificacion(
            entrante(
                paquete = "com.nu.production",
                titulo = "Compra aprobada por \$130.200,00",
                texto = "Tu compra en CREPES Y WAFFLES LEMON por \$130.200,00 con tu tarjeta terminada en 1336 ha sido APROBADA. ",
            ),
            deLaConfigDelServer,
        )
        assertEquals(
            DecisionDeNotificacion.Subir(
                "Compra aprobada por \$130.200,00: Tu compra en CREPES Y WAFFLES LEMON por \$130.200,00 " +
                    "con tu tarjeta terminada en 1336 ha sido APROBADA.",
            ),
            crepes,
        )

        val google = decidirNotificacion(
            entrante(
                paquete = "com.nu.production",
                titulo = "Compra aprobada por \$39.920,00",
                texto = "Tu compra en GOOGLE *MINTROCKET por \$39.920,00 con tu tarjeta terminada en 1336 ha sido APROBADA. ",
            ),
            deLaConfigDelServer,
        )
        assertEquals(
            DecisionDeNotificacion.Subir(
                "Compra aprobada por \$39.920,00: Tu compra en GOOGLE *MINTROCKET por \$39.920,00 " +
                    "con tu tarjeta terminada en 1336 ha sido APROBADA.",
            ),
            google,
        )
    }
}
