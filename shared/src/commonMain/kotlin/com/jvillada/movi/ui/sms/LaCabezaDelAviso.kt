package com.jvillada.movi.ui.sms

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.COMERCIO_DE_UN_AVISO_SIN_LEER
import com.jvillada.movi.shared.model.Marca
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.RubroDeMarca
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.horaLegibleDeSms
import com.jvillada.movi.shared.model.marcaDeLaCuenta
import com.jvillada.movi.shared.model.marcaDelBanco
import com.jvillada.movi.shared.model.marcaDelTexto
import com.jvillada.movi.shared.model.nombreParaMostrar
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.CirculoDeMarca
import com.jvillada.movi.ui.components.TamanoDeAvatar
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlin.math.roundToLong

/*
 * # La cabeza de una tarjeta de aviso: primero lo que pasó (vuelta de diseño 2)
 *
 * En producción la tarjeta de «Por revisar» se leía al revés: se titulaba con el BANCO («Falabella»)
 * y llevaba la marca del COMERCIO (la R de Rappi), y el monto quedaba enterrado en el texto crudo del
 * SMS, en letra chica. Ahora la cabeza se lee como el detalle de un movimiento: el comercio —o lo que
 * Movi entendió— con su marca, el monto a la derecha y, abajo, el banco y la hora. El texto del banco
 * va después, apagado: es la evidencia, no el titular.
 */

/**
 * **Lo que la cabeza de una tarjeta de aviso dice antes que nada.**
 *
 * @property titulo el comercio o lo que Movi entendió («Rappi», «Éxito Suba»); el banco si no entendió.
 * @property subtitulo «Falabella · 11:02 a. m.» o «Falabella · hace 3 días»: por dónde y cuándo fue.
 * @property marca la del comercio (o la del banco, si el título es el banco); `null` si no hay.
 * @property categoria la categoría que leyó Movi, para el ícono cuando el comercio no tiene marca.
 * @property monto el monto que leyó Movi, sin signo; `null` si no leyó ninguno.
 * @property tono gasto neutro, ingreso en `entra`, un pago entre cuentas suyas en `entreCuentas`.
 */
data class CabezaDelAviso(
    val titulo: String,
    val subtitulo: String,
    val marca: Marca? = null,
    val categoria: String? = null,
    val monto: Long? = null,
    val moneda: String = "COP",
    val tono: TonoDeCifra = TonoDeCifra.Neutro,
) {
    /** El monto con el signo que la [Cifra] pinta: negativo solo para un gasto. */
    val montoConSigno: Long? get() = monto?.let { if (tono == TonoDeCifra.Neutro) -it else it }
}

/**
 * **El banco que mandó el aviso, como se nombra en una línea**: «Notificación · Nu» → «Nu», «Correo ·
 * Bancolombia» → «Bancolombia», «Comprobante · transferencia.png» → «Comprobante», y un SMS que llega
 * con el código del remitente («85540») → «SMS», que es lo único que ese número dice.
 */
fun bancoDelAviso(bank: String): String {
    val limpio = bank.replace(' ', ' ').trim()
    if (limpio.startsWith("Comprobante", ignoreCase = true)) return "Comprobante"
    val origen = origenLegibleDelAviso(limpio)
    return if (origen.isEmpty() || origen.all { it.isDigit() }) "SMS" else origen
}

/** El título de un aviso que no nombra comercio y llega con el código del remitente («85540»). */
const val TITULO_DE_UN_AVISO_SIN_BANCO: String = "Mensaje del banco"

/**
 * **Cuándo fue el movimiento de un aviso, dicho corto** (vuelta de diseño 23). Antes la tarjeta decía
 * la hora de llegada aunque el aviso fuera de hace dos meses —«Rappi · Falabella · 11:02» para un SMS
 * del 2 de septiembre—, y se leía como algo de hoy. Ahora: la hora si fue hoy; «Ayer»; «hace 3 días»
 * en la semana; y de ahí para atrás la fecha, «2 sep» (con el año si es de otro).
 *
 * El día es el del movimiento que entendió Movi: [fechaLeida] (`ParsedSms.fecha`, el día que dice el
 * correo de PSE) si la hay; si no, el de llegada ([time]); y si la hora de llegada no se puede leer,
 * la fecha que el banco escribió en el texto («el 02/09/2026»). Sin ninguna, el `time` tal cual.
 */
fun cuandoFueElAviso(time: String, texto: String, fechaLeida: String?, hoy: LocalDate): String {
    val llegada = runCatching { LocalDateTime.parse(completarHora(time)) }.getOrNull()
    val leida = fechaLeida?.let { f -> runCatching { LocalDate.parse(f.trim().take(10)) }.getOrNull() }
    return when {
        leida != null && leida != llegada?.date -> fechaRelativaDelAviso(leida, hoy, hora = null)
        llegada != null -> fechaRelativaDelAviso(llegada.date, hoy, hora = horaLegibleDeSms(time))
        else -> fechaEnElTexto(texto)?.let { fechaRelativaDelAviso(it, hoy, hora = null) } ?: time.trim()
    }
}

/** El `time` guardado con segundos, para `LocalDateTime.parse` («2026-10-26 11:02» → «2026-10-26T11:02:00»). */
private fun completarHora(time: String): String {
    val t = time.trim().replace(' ', 'T')
    return if (t.length == 16) "$t:00" else t
}

/**
 * «11:02 a. m.» (o «Hoy» sin [hora]) si [dia] es [hoy]; «Ayer»; «hace N días» hasta
 * [DIAS_QUE_SE_DICEN_EN_RELATIVO]; y si no, «2 sep», o «2 sep 2025» si es de otro año.
 */
fun fechaRelativaDelAviso(dia: LocalDate, hoy: LocalDate, hora: String?): String {
    val dias = dia.daysUntil(hoy)
    return when {
        dias == 0 -> hora ?: "Hoy"
        dias == 1 -> "Ayer"
        dias in 2..DIAS_QUE_SE_DICEN_EN_RELATIVO -> "hace $dias días"
        dia.year != hoy.year -> "${fechaCortaDelAviso(dia)} ${dia.year}"
        else -> fechaCortaDelAviso(dia)
    }
}

/** Hasta cuántos días atrás se dice «hace N días»; de ahí en adelante, la fecha. */
const val DIAS_QUE_SE_DICEN_EN_RELATIVO: Int = 6

/** «2 sep». */
private fun fechaCortaDelAviso(dia: LocalDate): String = "${dia.dayOfMonth} ${MESES_DEL_ANIO[dia.monthNumber - 1].take(3)}"

private val FECHA_ESCRITA = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4}|\d{2})\b""")

/** La fecha que escribe el banco en el texto («el 02/09/2026», día/mes como en Colombia), o `null`. */
internal fun fechaEnElTexto(texto: String): LocalDate? {
    val m = FECHA_ESCRITA.find(texto) ?: return null
    val (d, mes, a) = m.destructured
    val anio = a.toInt().let { if (a.length == 2) 2000 + it else it }
    return runCatching { LocalDate(anio, mes.toInt(), d.toInt()) }.getOrNull()
}

/**
 * **«Ojo: el aviso es de Falabella y la cuenta es de Bancolombia»** (vuelta de diseño 23), cuando la
 * cuenta que propone Movi no es del banco que mandó el aviso. Se compara la marca del banco de cada
 * aviso ([marcaDelBanco] sobre su origen) con la de la cuenta ([marcaDeLaCuenta]); **no cambia qué
 * cuenta propone Movi**, solo lo avisa.
 *
 * Calla cuando no hay con qué comparar —un SMS que llega con el código del remitente, un comprobante,
 * una cuenta cuyo nombre no dice el banco—, con una red de tarjeta (Visa, Mastercard: «Bancolombia
 * Visa» es Bancolombia) y entre marcas del mismo banco ([GRUPOS_DE_BANCOS]: la AMEX y la Fiducuenta
 * son de Bancolombia). En un pago avisado varias veces basta con que un aviso sea del banco de la cuenta.
 */
fun advertenciaDeBancos(origenes: List<String>, cuenta: Account?): String? {
    val deLaCuenta = cuenta?.let(::marcaDeLaCuenta)?.takeIf(::esUnBanco) ?: return null
    val delAviso = origenes.mapNotNull { origen -> marcaDelBanco(origen)?.takeIf(::esUnBanco)?.let { origen to it } }
    if (delAviso.isEmpty() || delAviso.any { (_, marca) -> grupoDelBanco(marca.id) == grupoDelBanco(deLaCuenta.id) }) return null
    val (origen, _) = delAviso.first()
    return "Ojo: el aviso es de ${bancoDelAviso(origen)} y la cuenta es de ${deLaCuenta.nombre}"
}

/** Un banco o una billetera, y no una red de tarjeta. */
private fun esUnBanco(marca: Marca): Boolean =
    marca.rubro == RubroDeMarca.BANCOS_Y_BILLETERAS && marca.id !in REDES_DE_TARJETA

private val REDES_DE_TARJETA = setOf("visa", "mastercard")

/** Las marcas que son del mismo banco: la tarjeta o la fiducia que el banco emite con otro nombre. */
private val GRUPOS_DE_BANCOS: Map<String, String> = mapOf(
    "amex" to "bancolombia",
    "fiducuenta" to "bancolombia",
    "nequi" to "bancolombia",
    "daviplata" to "davivienda",
    "davibank" to "davivienda",
)

private fun grupoDelBanco(id: String): String = GRUPOS_DE_BANCOS[id] ?: id

/** Un comercio que no dice nada: vacío, o el «Movimiento» que Movi pone cuando el aviso no nombra a nadie. */
private fun comercioQueDiceAlgo(comercio: String?): String? =
    comercio?.trim()?.takeIf { it.isNotEmpty() && !it.equals(COMERCIO_DE_UN_AVISO_SIN_LEER, ignoreCase = true) }

/** La primera marca de comercio que nombra el texto de algún aviso. Un banco no es un comercio. */
private fun marcaDeComercioEn(avisos: List<SmsMessage>): Marca? =
    avisos.firstNotNullOfOrNull { aviso -> marcaDelTexto(aviso.text)?.takeIf { it.rubro != RubroDeMarca.BANCOS_Y_BILLETERAS } }

/**
 * **La cabeza de la tarjeta de [avisos]** (uno suelto, o los de un mismo pago con el que propone
 * primero), con lo que Movi leyó: [leido] (la propuesta de la bandeja) o, si no lo pudo leer,
 * [sinLeer] (el monto que se puede anotar igual).
 *
 * - El **título** es el comercio que leyó Movi; si no leyó ninguno, la marca de comercio que nombra
 *   el texto; y si tampoco, el banco ([bancoDelAviso]). El comercio va como se lee
 *   ([nombreParaMostrar]: «RAPPI» → «Rappi»), no como lo grita el banco.
 * - La **marca** sigue al título: la del comercio cuando el título es el comercio (o, si el comercio
 *   no tiene marca, el ícono de su [CabezaDelAviso.categoria]), la del banco cuando el título es el
 *   banco. Nunca la del comercio al lado del nombre del banco, que era lo que confundía.
 * - El **monto** es el que leyó Movi; sin lectura, no hay monto (el texto crudo lo sigue diciendo).
 * - El **subtítulo** es «banco · cuándo» ([cuandoFueElAviso]); en un pago avisado varias veces, por
 *   dónde llegó cada aviso.
 */
fun cabezaDelAviso(
    avisos: List<SmsMessage>,
    leido: ParsedSms?,
    hoy: LocalDate,
    sinLeer: AvisoSinLeer? = null,
): CabezaDelAviso {
    val primero = avisos.first()
    val cuando = cuandoFueElAviso(primero.time, primero.text, leido?.fecha, hoy)
    val subtitulo = if (avisos.size > 1) {
        "${canalesDelMismoPago(avisos)} · $cuando"
    } else {
        "${bancoDelAviso(primero.bank)} · $cuando"
    }
    val enElTexto = marcaDeComercioEn(avisos)
    val comercio = comercioQueDiceAlgo(leido?.merchant) ?: comercioQueDiceAlgo(sinLeer?.comercio) ?: enElTexto?.nombre
    val monto = leido?.amount?.roundToLong()?.takeIf { it > 0 } ?: sinLeer?.monto?.roundToLong()?.takeIf { it > 0 }
    val tipo = leido?.type ?: sinLeer?.tipo
    val entreCuentas = leido != null &&
        (leido.traspasoHaciaId != null || leido.traspasoDesdeId != null || leido.category == CARD_PAYMENT_CATEGORY)
    val tono = when {
        entreCuentas -> TonoDeCifra.EntreCuentas
        tipo == TransactionType.INCOME -> TonoDeCifra.Ingreso
        else -> TonoDeCifra.Neutro
    }
    val moneda = leido?.currency ?: sinLeer?.moneda ?: "COP"
    if (comercio == null) {
        val banco = bancoDelAviso(primero.bank)
        return CabezaDelAviso(
            titulo = when (banco) {
                "Comprobante" -> primero.bank.trim() // «Comprobante · transferencia.png»
                "SMS" -> TITULO_DE_UN_AVISO_SIN_BANCO
                else -> banco
            },
            // El banco ya es el título: abajo queda la hora (o, en un pago, por dónde llegó cada aviso).
            subtitulo = if (avisos.size > 1) subtitulo else cuando,
            marca = marcaDelBanco(primero.bank),
            monto = monto,
            moneda = moneda,
            tono = tono,
        )
    }
    return CabezaDelAviso(
        // «RAPPI» → «Rappi», «EXITO SUBA» → «Éxito Suba» (vuelta de diseño 4). Solo el título: la
        // marca se busca en el texto crudo, y lo que se guarda sigue siendo lo que leyó Movi.
        titulo = nombreParaMostrar(comercio),
        subtitulo = subtitulo,
        marca = marcaDelTexto(comercio) ?: enElTexto,
        categoria = (leido?.category ?: sinLeer?.categoria)?.takeIf { it.isNotBlank() },
        monto = monto,
        moneda = moneda,
        tono = tono,
    )
}

/**
 * **El avatar de la cabeza**: la marca, o el ícono de la categoría que leyó Movi (como la fila de un
 * movimiento, `AvatarDelComercio`), o el ícono de los avisos.
 */
@Composable
internal fun AvatarDeLaCabeza(cabeza: CabezaDelAviso, pideDecision: Boolean = true) {
    val marca = cabeza.marca
    val categoria = cabeza.categoria
    when {
        marca != null -> CirculoDeMarca(marca, tamano = TamanoDeAvatar.Fila)
        categoria != null -> IconoDeCategoria(categoria)
        else -> IconoEnCirculo(Icons.Rounded.Inbox, tono = if (pideDecision) TonoDeIcono.Aviso else TonoDeIcono.Neutro)
    }
}

/** El `testTag` de la línea «Ojo: el aviso es de … y la cuenta es de …». */
const val TAG_ADVERTENCIA_DE_BANCOS: String = "sms:advertencia-de-bancos"

/** **La advertencia de bancos distintos** ([advertenciaDeBancos]), en el tono de aviso. */
@Composable
internal fun LineaDeAdvertenciaDeBancos(texto: String) {
    Text(
        texto,
        style = Movi.textos.apoyo,
        color = Movi.colores.aviso,
        modifier = Modifier.testTag(TAG_ADVERTENCIA_DE_BANCOS),
    )
}

/** **El monto de la cabeza**, con la [Cifra] del sistema; nada si Movi no leyó ninguno. */
@Composable
internal fun CifraDeLaCabeza(cabeza: CabezaDelAviso) {
    val monto = cabeza.montoConSigno ?: return
    Cifra(monto, tono = cabeza.tono, moneda = cabeza.moneda)
}
