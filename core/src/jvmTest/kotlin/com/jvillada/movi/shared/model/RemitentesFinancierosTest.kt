package com.jvillada.movi.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 93 · el filtro de Movi para Gmail: la lista, la búsqueda (largo, únicos, partición), el XML
 * (válido, escapado, con la dirección del usuario), el código de confirmación y el wire.
 */
class RemitentesFinancierosTest {

    @Test
    fun `la lista no repite un dominio ni trae algo que no sea un dominio`() {
        val dominios = REMITENTES_FINANCIEROS.map { it.dominio }
        assertEquals(dominios.size, dominios.toSet().size, "un dominio, una vez")
        for (d in dominios) {
            assertEquals(d, d.trim().lowercase(), "$d: sin espacios ni mayúsculas")
            assertTrue('@' !in d && ' ' !in d && '.' in d, "$d: un dominio, no una dirección")
        }
        assertTrue(REMITENTES_FINANCIEROS.all { it.nombre.isNotBlank() })
    }

    @Test
    fun `arranca de los del dueno y los verificados van primero`() {
        val porDominio = REMITENTES_FINANCIEROS.associateBy { it.dominio }
        for (d in listOf("notificacionesbancolombia.com", "nu.com.co", "davibank.com", "achcolombia.com.co")) {
            assertTrue(d in porDominio, "$d es del dueño")
        }
        assertTrue(porDominio.getValue("notificacionesbancolombia.com").verificado)
        assertTrue(porDominio.getValue("achcolombia.com.co").verificado)
        assertFalse(porDominio.getValue("nu.com.co").verificado, "el repo no tiene un correo de Nu")
        val primerNoVerificado = REMITENTES_FINANCIEROS.indexOfFirst { !it.verificado }
        assertTrue(REMITENTES_FINANCIEROS.drop(primerNoVerificado).none { it.verificado })
    }

    @Test
    fun `los dominios cortos sin verificar que atrapan a otros se quedan afuera`() {
        val dominios = REMITENTES_FINANCIEROS.map { it.dominio }
        assertFalse("bold.co" in dominios)
        assertFalse("itau.co" in dominios)
        assertFalse("bold.co" in busquedasDeGmail().joinToString(" ") || "itau.co" in busquedasDeGmail().joinToString(" "))
        // La regla: un dominio sin verificar con el nombre pegado a un sufijo de dos letras (`x.co`) es de
        // los que pueden atrapar remitentes ajenos y no entra sin un subdominio de alertas.
        val atrapones = REMITENTES_FINANCIEROS.filter { !it.verificado && Regex("""^[a-z0-9]{1,6}\.[a-z]{2}$""").matches(it.dominio) }
        assertEquals(emptyList(), atrapones.map { it.dominio })
    }

    @Test
    fun `la busqueda de hoy cabe en una sola y trae cada dominio una vez`() {
        val busquedas = busquedasDeGmail()
        assertEquals(1, busquedas.size)
        val b = busquedas.single()
        assertTrue(b.length <= LARGO_MAXIMO_DE_LA_BUSQUEDA_DE_GMAIL)
        assertTrue(b.startsWith("from:(") && b.endsWith(")"))
        val dominios = b.removePrefix("from:(").removeSuffix(")").split(" OR ")
        assertEquals(REMITENTES_FINANCIEROS.map { it.dominio }, dominios)
    }

    @Test
    fun `un dominio repetido, con otras mayusculas o con espacios, entra una sola vez`() {
        val b = busquedasDeGmail(
            listOf(
                RemitenteFinanciero("A.com", "A"),
                RemitenteFinanciero("a.com ", "A otra vez"),
                RemitenteFinanciero("b.com", "B"),
                RemitenteFinanciero("", "vacío"),
            ),
        )
        assertEquals(listOf("from:(a.com OR b.com)"), b)
    }

    @Test
    fun `sin remitentes no hay busqueda`() {
        assertEquals(emptyList(), busquedasDeGmail(emptyList()))
    }

    @Test
    fun `si no cabe se parte en filtros que caben, completos y sin repetir ni perder un dominio`() {
        val remitentes = (1..40).map { RemitenteFinanciero("banco-numero-$it.com.co", "B$it") }
        val limite = 200
        val busquedas = busquedasDeGmail(remitentes, limite)
        assertTrue(busquedas.size > 1, "con 40 dominios y 200 caracteres no cabe en una")
        for (b in busquedas) {
            assertTrue(b.length <= limite, "cada una dentro del límite: ${b.length}")
            assertTrue(b.startsWith("from:(") && b.endsWith(")"))
        }
        val todos = busquedas.flatMap { it.removePrefix("from:(").removeSuffix(")").split(" OR ") }
        assertEquals(remitentes.map { it.dominio }, todos, "ninguno se pierde, ninguno se repite, en orden")
    }

    @Test
    fun `una busqueda llena justo hasta el limite no se parte`() {
        val remitentes = listOf(RemitenteFinanciero("aa.com", "A"), RemitenteFinanciero("bb.com", "B"))
        val justo = "from:(aa.com OR bb.com)".length
        assertEquals(1, busquedasDeGmail(remitentes, justo).size)
        assertEquals(2, busquedasDeGmail(remitentes, justo - 1).size)
    }

    @Test
    fun `un dominio que solo ya pasa del limite igual va`() {
        val b = busquedasDeGmail(listOf(RemitenteFinanciero("dominio-larguisimo.com.co", "X")), largoMaximo = 5)
        assertEquals(listOf("from:(dominio-larguisimo.com.co)"), b)
    }

    @Test
    fun `el XML es un feed de Atom con una entrada por busqueda y la direccion del usuario`() {
        val xml = filtroDeGmailXml(listOf("from:(a.com OR b.com)", "from:(c.com)"), "alertas+abc123@x.resend.app")
        assertTrue(xml.startsWith("<?xml version='1.0' encoding='UTF-8'?>"))
        assertTrue("<feed xmlns='http://www.w3.org/2005/Atom' xmlns:apps='http://schemas.google.com/apps/2006'>" in xml)
        assertEquals(2, Regex("<entry>").findAll(xml).count())
        assertEquals(2, Regex("</entry>").findAll(xml).count())
        assertEquals(2, Regex("<category term='filter'></category>").findAll(xml).count())
        assertTrue("<apps:property name='hasTheWord' value='from:(a.com OR b.com)'/>" in xml)
        assertEquals(2, Regex("<apps:property name='forwardTo' value='alertas\\+abc123@x\\.resend\\.app'/>").findAll(xml).count())
        assertTrue(xml.trimEnd().endsWith("</feed>"))
    }

    @Test
    fun `el XML lleva id y updated en el feed y en cada entrada, como el que exporta Gmail`() {
        val xml = filtroDeGmailXml(listOf("from:(a.com)", "from:(b.com)"), "a@b.com", actualizadoEn = "2026-11-01T12:00:00Z")
        assertTrue("\t<id>tag:mail.google.com,2008:filters:movi1,movi2</id>" in xml)
        assertEquals(1, Regex("\n\t<updated>2026-11-01T12:00:00Z</updated>").findAll(xml).count(), "el del feed")
        assertEquals(2, Regex("<id>tag:mail.google.com,2008:filter:movi\\d</id>").findAll(xml).count(), "uno por entrada")
        assertEquals(2, Regex("\n\t\t<updated>2026-11-01T12:00:00Z</updated>").findAll(xml).count(), "uno por entrada")
        assertEquals(2, Regex("<content></content>").findAll(xml).count())
        // Con la hora de ahora por defecto también: «AAAA-MM-DDThh:mm:ssZ».
        assertTrue(Regex("<updated>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z</updated>").containsMatchIn(filtroDeGmailXml(listOf("from:(a.com)"), "a@b.com")))
    }

    @Test
    fun `el XML se puede leer con un parser de verdad`() {
        val xml = filtroDeGmailXml(busquedasDeGmail(), "alertas+abc@x.resend.app")
        val fabrica = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = fabrica.newDocumentBuilder().parse(xml.byteInputStream())
        assertEquals("feed", doc.documentElement.localName)
        val props = doc.getElementsByTagNameNS("http://schemas.google.com/apps/2006", "property")
        assertEquals(2, props.length)
        assertEquals("hasTheWord", (props.item(0) as org.w3c.dom.Element).getAttribute("name"))
        assertEquals(busquedasDeGmail().single(), (props.item(0) as org.w3c.dom.Element).getAttribute("value"))
        assertEquals("alertas+abc@x.resend.app", (props.item(1) as org.w3c.dom.Element).getAttribute("value"))
    }

    @Test
    fun `el XML escapa lo que rompe un atributo`() {
        val xml = filtroDeGmailXml(listOf("from:(a&b.com OR \"c'd\".com <x>)"), "o'brien&co@x.com")
        assertTrue("&amp;" in xml && "&apos;" in xml && "&quot;" in xml && "&lt;" in xml && "&gt;" in xml)
        assertFalse("a&b" in xml, "ningún & suelto")
        val fabrica = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val doc = fabrica.newDocumentBuilder().parse(xml.byteInputStream())
        val p = doc.getElementsByTagName("apps:property").item(0) as org.w3c.dom.Element
        assertEquals("from:(a&b.com OR \"c'd\".com <x>)", p.getAttribute("value"), "al leerlo vuelve igual")
    }

    @Test
    fun `una direccion rota no se entrega`() {
        for (mala in listOf("", "sin-arroba", "@x.com", "a@", "con espacio@x.com", "a@x.com\n<evil/>", "a<b>@x.com")) {
            assertFailsWith<IllegalArgumentException>("«$mala»") { filtroDeGmailXml(listOf("from:(a.com)"), mala) }
        }
    }

    @Test
    fun `el filtro armado lleva todo, y sin direccion no hay XML`() {
        val con = filtroDeGmailPara("alertas+abc@x.resend.app", configurado = true, codigoDeConfirmacion = "123456789")
        assertEquals("123456789", con.codigoDeConfirmacion)
        assertEquals(REMITENTES_FINANCIEROS, con.remitentes)
        assertTrue(con.xml!!.contains("alertas+abc@x.resend.app"))
        val sin = filtroDeGmailPara(null, configurado = false)
        assertNull(sin.xml)
        assertTrue(sin.busquedas.isNotEmpty(), "la búsqueda sirve aunque no haya dirección")
    }

    @Test
    fun `el wire viejo sigue deserializando y el nuevo ignora lo que no conoce`() {
        val json = Json { ignoreUnknownKeys = true }
        // Un server viejo (sin /filtro) no manda nada de esto: todo tiene valor por defecto.
        assertEquals(FiltroDeGmail(), json.decodeFromString<FiltroDeGmail>("{}"))
        // Un RemitenteFinanciero sin `verificado` (una versión anterior) no es verificado.
        assertFalse(json.decodeFromString<RemitenteFinanciero>("""{"dominio":"a.com","nombre":"A"}""").verificado)
        // Y la respuesta de /direccion, que no cambió, sigue siendo la de siempre.
        val d = json.decodeFromString<com.jvillada.movi.shared.model.DireccionDeCorreo>(
            """{"token":"t","direccion":"a@b.com","configurado":true}""",
        )
        assertEquals("a@b.com", d.direccion)
        val completo = filtroDeGmailPara("a@b.com", true, "123456")
        assertEquals(completo, json.decodeFromString<FiltroDeGmail>(json.encodeToString(FiltroDeGmail.serializer(), completo)))
    }

    @Test
    fun `el codigo se lee del cuerpo o, si no, del asunto`() {
        assertEquals("987654321", codigoDeConfirmacionDeGmail("a ... Confirmation code: 987654321 ..."))
        assertEquals("987654321", codigoDeConfirmacionDeGmail("Código de confirmación: 987654321"))
        assertEquals("987654321", codigoDeConfirmacionDeGmail("(#987654321) Gmail Forwarding Confirmation - Receive Mail"))
        assertNull(codigoDeConfirmacionDeGmail("Compra aprobada por $21.640"))
        assertNull(codigoDeConfirmacionDeGmail("Confirmation code: 12"))
    }
}
