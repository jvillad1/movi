package com.jvillada.movi.shared.model

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/*
 * # Vuelta 93 · el filtro de Movi para Gmail
 *
 * Que cualquier persona le mande a Movi «todo lo financiero útil» de su correo **sin escribir su propio
 * filtro**: Movi arma la búsqueda con los remitentes financieros de Colombia y la persona la pega (o
 * importa el XML) en Gmail, que reenvía todo eso a su dirección de Movi (`alertas+<código>@…`).
 *
 * Todo lo de este archivo es puro: la lista, la partición de la búsqueda en filtros que quepan en Gmail
 * y el XML de importación. Lo sirve `GET /api/correo-entrante/filtro` ([FiltroDeGmail]).
 */

/**
 * Un remitente financiero: el dominio desde el que un banco, billetera o pasarela manda correo.
 *
 * [verificado] es `true` **solo** si el dominio sale de algo que el repo confirma: un remitente real en
 * un fixture o en el parser de correos. Los demás salen de la documentación pública de cada entidad (el
 * dominio existe y recibe correo) y van como no verificados: **no se sabe** si ese banco manda hoy
 * alertas desde ahí, ni con qué subdominio. Incluirlos no cuesta nada (un dominio que no escribe no
 * trae correos); por eso la lista no los esconde, solo los marca.
 */
@Serializable
data class RemitenteFinanciero(
    val dominio: String,
    val nombre: String,
    val verificado: Boolean = false,
)

/**
 * Los remitentes financieros de Colombia, **en un solo lugar**. El orden es el de la búsqueda: primero
 * los verificados. Agregar uno es agregar una línea (`RemitentesFinancierosTest` cuida que no se repita
 * un dominio y que ninguno traiga espacios, `@` ni mayúsculas).
 *
 * Verificados (remitente real en `AlertasDeEjemplo.kt` y en `ElCorreoDePse.kt`):
 * `notificacionesbancolombia.com` (`alertasynotificaciones@…`) y `achcolombia.com.co`
 * (`serviciopse@…`, el correo de cada pago por PSE).
 *
 * **Afuera a propósito (revisión de la vuelta 93): `bold.co` e `itau.co`.** Son dominios cortos y sin verificar:
 * `from:bold.co` / `from:itau.co` pueden atrapar remitentes ajenos (cualquier dirección que contenga esas
 * letras), y el filtro reenvía **todo** lo que atrapa. Vuelven solo con un subdominio de alertas que se pueda
 * sostener (algo como `alertas.banco.co`), no con el dominio pelado.
 *
 * Los de Nu y Davibank llegan del dueño (sus alertas) pero el repo no tiene un correo de ninguno de los
 * dos: por eso `verificado = false`. Rappi Pay no está: no hubo un dominio que sostener.
 */
val REMITENTES_FINANCIEROS: List<RemitenteFinanciero> = listOf(
    RemitenteFinanciero("notificacionesbancolombia.com", "Bancolombia", verificado = true),
    RemitenteFinanciero("achcolombia.com.co", "PSE", verificado = true),
    RemitenteFinanciero("nu.com.co", "Nu"),
    RemitenteFinanciero("davibank.com", "Davibank"),
    RemitenteFinanciero("davivienda.com", "Davivienda"),
    RemitenteFinanciero("daviplata.com", "Daviplata"),
    RemitenteFinanciero("bbva.com.co", "BBVA"),
    RemitenteFinanciero("bancodebogota.com.co", "Banco de Bogotá"),
    RemitenteFinanciero("bancodeoccidente.com.co", "Banco de Occidente"),
    RemitenteFinanciero("avvillas.com.co", "AV Villas"),
    RemitenteFinanciero("bancopopular.com.co", "Banco Popular"),
    RemitenteFinanciero("scotiabankcolpatria.com", "Scotiabank Colpatria"),
    RemitenteFinanciero("bancofalabella.com.co", "Banco Falabella"),
    RemitenteFinanciero("lulobank.com", "Lulo Bank"),
    RemitenteFinanciero("nequi.com.co", "Nequi"),
    RemitenteFinanciero("pse.com.co", "PSE"),
    RemitenteFinanciero("mercadopago.com", "Mercado Pago"),
    RemitenteFinanciero("skandia.com.co", "Skandia"),
)

/**
 * El largo más alto que Movi le pone a una búsqueda de Gmail. Gmail no publica el límite del campo «Incluye
 * las palabras» de un filtro; esta cifra es **elegida, no medida**, y deja mucho margen: los 20 dominios de
 * hoy caben juntos en menos de 600 caracteres. Pasarla parte la búsqueda en dos (o más) filtros.
 */
const val LARGO_MAXIMO_DE_LA_BUSQUEDA_DE_GMAIL: Int = 1_500

/** `from:(a OR b OR …)`: el trozo que se repite. */
private const val PREFIJO_DE_LA_BUSQUEDA = "from:("
private const val SEPARADOR_DE_LA_BUSQUEDA = " OR "

/** Los dominios de [remitentes], sin repetir (sin importar las mayúsculas) y en su orden. */
fun dominiosUnicos(remitentes: List<RemitenteFinanciero>): List<String> =
    remitentes.map { it.dominio.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

/**
 * **La búsqueda de Gmail**: `from:(a.com OR b.com …)`, con los dominios únicos de [remitentes]. Si todo no
 * cabe en [largoMaximo] caracteres se parte en varias —cada una completa, `from:(…)`—, **sin partir un
 * dominio ni repetir ninguno**. Sin dominios, ninguna búsqueda.
 */
fun busquedasDeGmail(
    remitentes: List<RemitenteFinanciero> = REMITENTES_FINANCIEROS,
    largoMaximo: Int = LARGO_MAXIMO_DE_LA_BUSQUEDA_DE_GMAIL,
): List<String> {
    val busquedas = mutableListOf<String>()
    var actual = mutableListOf<String>()
    fun largoCon(dominios: List<String>): Int =
        PREFIJO_DE_LA_BUSQUEDA.length + dominios.sumOf { it.length } +
            SEPARADOR_DE_LA_BUSQUEDA.length * (dominios.size - 1).coerceAtLeast(0) + 1
    for (dominio in dominiosUnicos(remitentes)) {
        // Un dominio solo que ya pasa del límite igual va (no se puede partir): lo corto es no perderlo.
        if (actual.isNotEmpty() && largoCon(actual + dominio) > largoMaximo) {
            busquedas += armarLaBusqueda(actual)
            actual = mutableListOf()
        }
        actual += dominio
    }
    if (actual.isNotEmpty()) busquedas += armarLaBusqueda(actual)
    return busquedas
}

private fun armarLaBusqueda(dominios: List<String>): String =
    dominios.joinToString(SEPARADOR_DE_LA_BUSQUEDA, PREFIJO_DE_LA_BUSQUEDA, ")")

/**
 * **El XML de importación de filtros de Gmail** (Configuración → Filtros y direcciones bloqueadas →
 * «Importar filtros»): un feed Atom con una `entry` por búsqueda, con la búsqueda en `hasTheWord` y
 * `forwardTo` = [direccion].
 *
 * La forma (`<feed>` con `xmlns:apps`, `<category term='filter'>`, `<title>Mail Filter</title>`,
 * `<apps:property name='…' value='…'/>`, y `hasTheWord` con un operador de búsqueda) sale de filtros
 * exportados por Gmail que otras personas publican; **Google no documenta el formato**. Lo que sí se sabe
 * de Gmail es que reenviar desde un filtro exige que la dirección ya esté verificada como dirección de
 * reenvío: por eso la guía pone la confirmación antes del filtro.
 *
 * Como en lo que exporta Gmail, el feed y cada `entry` llevan `<id>` (`tag:mail.google.com,2008:…`, aquí
 * `movi1`, `movi2`…) y `<updated>` ([actualizadoEn]), y la `entry` un `<content>` vacío; no se verificó que
 * Gmail los use al importar, pero así el archivo se parece al que él mismo exporta. Todo valor se escapa (`& < > ' "`). Lanza [IllegalArgumentException] si [direccion] no parece un
 * correo (sin `@`, con espacios o caracteres de control): un XML con una dirección rota no se entrega.
 */
fun filtroDeGmailXml(
    busquedas: List<String>,
    direccion: String,
    actualizadoEn: String = ahoraParaGmail(),
): String {
    require(direccionParecidaAUnCorreo(direccion)) { "La dirección de reenvío no parece un correo" }
    val fecha = escaparXml(actualizadoEn)
    val sb = StringBuilder()
    sb.append("<?xml version='1.0' encoding='UTF-8'?>\n")
    sb.append("<feed xmlns='http://www.w3.org/2005/Atom' xmlns:apps='http://schemas.google.com/apps/2006'>\n")
    sb.append("\t<title>Mail Filters</title>\n")
    sb.append("\t<id>tag:mail.google.com,2008:filters:").append(busquedas.indices.joinToString(",") { "movi${it + 1}" }).append("</id>\n")
    sb.append("\t<updated>").append(fecha).append("</updated>\n")
    busquedas.forEachIndexed { i, busqueda ->
        sb.append("\t<entry>\n")
        sb.append("\t\t<category term='filter'></category>\n")
        sb.append("\t\t<title>Mail Filter</title>\n")
        sb.append("\t\t<id>tag:mail.google.com,2008:filter:movi").append(i + 1).append("</id>\n")
        sb.append("\t\t<updated>").append(fecha).append("</updated>\n")
        sb.append("\t\t<content></content>\n")
        sb.append("\t\t<apps:property name='hasTheWord' value='").append(escaparXml(busqueda)).append("'/>\n")
        sb.append("\t\t<apps:property name='forwardTo' value='").append(escaparXml(direccion)).append("'/>\n")
        sb.append("\t</entry>\n")
    }
    sb.append("</feed>\n")
    return sb.toString()
}

/** «2026-11-01T12:34:56Z»: el instante de ahora, a los segundos, como lo escribe Gmail en `<updated>`. */
private fun ahoraParaGmail(): String = Clock.System.now().toString().substringBefore('.').removeSuffix("Z") + "Z"

private fun direccionParecidaAUnCorreo(direccion: String): Boolean {
    val arroba = direccion.indexOf('@')
    return arroba > 0 && arroba < direccion.length - 1 &&
        direccion.none { it.isWhitespace() || it.isISOControl() || it == '<' || it == '>' }
}

/** Lo mínimo para que un valor viaje dentro de un atributo XML entre comillas simples. */
internal fun escaparXml(texto: String): String = buildString(texto.length + 8) {
    for (c in texto) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '\'' -> append("&apos;")
        '"' -> append("&quot;")
        else -> append(c)
    }
}

/** El nombre del archivo que se descarga en la web. */
const val NOMBRE_DEL_ARCHIVO_DEL_FILTRO: String = "filtro-de-movi-para-gmail.xml"

/**
 * `GET /api/correo-entrante/filtro`: lo que la guía necesita para que alguien arme el reenvío sin
 * escribir nada.
 *
 * - [busquedas]: una o más búsquedas (`from:(…)`) para pegar en Gmail. Vacía solo si no hay remitentes.
 * - [xml]: el archivo para «Importar filtros», o `null` si el server no tiene dirección de Movi (sin ella
 *   no hay a dónde reenviar).
 * - [remitentes]: la lista, para decir cuántos bancos cubre.
 * - [codigoDeConfirmacion]: el código que Gmail mandó a la dirección de Movi al agregarla como dirección
 *   de reenvío (el correo apartado como `CONFIRMACION_DE_REENVIO`), si llegó en los últimos días.
 *
 * Todo con valor por defecto: un APK viejo no lo lee y uno nuevo contra un server viejo no lo recibe.
 */
@Serializable
data class FiltroDeGmail(
    val configurado: Boolean = false,
    val direccion: String? = null,
    val busquedas: List<String> = emptyList(),
    val xml: String? = null,
    val remitentes: List<RemitenteFinanciero> = emptyList(),
    val codigoDeConfirmacion: String? = null,
)

/** Arma la respuesta de [FiltroDeGmail] con la lista y los límites de siempre. */
fun filtroDeGmailPara(
    direccion: String?,
    configurado: Boolean,
    codigoDeConfirmacion: String? = null,
    remitentes: List<RemitenteFinanciero> = REMITENTES_FINANCIEROS,
): FiltroDeGmail {
    val busquedas = busquedasDeGmail(remitentes)
    return FiltroDeGmail(
        configurado = configurado,
        direccion = direccion,
        busquedas = busquedas,
        xml = direccion?.takeIf { busquedas.isNotEmpty() && direccionParecidaAUnCorreo(it) }
            ?.let { filtroDeGmailXml(busquedas, it) },
        remitentes = remitentes,
        codigoDeConfirmacion = codigoDeConfirmacion,
    )
}

private val ELCODIGO_DE_CONFIRMACION = Regex(
    """(?:confirmation\s+code|c[oó]digo\s+de\s+confirmaci[oó]n)\s*:?\s*(\d{4,12})""",
    RegexOption.IGNORE_CASE,
)
private val ELCODIGO_EN_EL_ASUNTO = Regex("""\(#(\d{4,12})\)""")

/**
 * **El código de la confirmación de reenvío de Gmail** dentro del texto que Movi guarda del correo (asunto
 * + cuerpo): «Confirmation code: 987654321» / «Código de confirmación: …», o el «(#987654321)» del asunto.
 * `null` si no hay.
 */
fun codigoDeConfirmacionDeGmail(texto: String): String? =
    ELCODIGO_DE_CONFIRMACION.find(texto)?.groupValues?.get(1)
        ?: ELCODIGO_EN_EL_ASUNTO.find(texto)?.groupValues?.get(1)
