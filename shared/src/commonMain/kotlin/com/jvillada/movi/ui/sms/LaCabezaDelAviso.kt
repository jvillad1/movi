package com.jvillada.movi.ui.sms

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.runtime.Composable
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.COMERCIO_DE_UN_AVISO_SIN_LEER
import com.jvillada.movi.shared.model.Marca
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.RubroDeMarca
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.fechaCortaDeSms
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
import kotlinx.datetime.LocalDate
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
 * @property subtitulo «Falabella · 11:02 a. m.»: por dónde y cuándo llegó.
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

/** «11:02 a. m.» si llegó hoy; «Ayer, 9:15 a. m.» o «20 de octubre, 7:40 p. m.» si no. */
fun cuandoLlegoElAviso(time: String, hoy: LocalDate): String =
    fechaCortaDeSms(time, hoy).removePrefix("Hoy, ")

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
 * - El **subtítulo** es «banco · hora»; en un pago avisado varias veces, por dónde llegó cada aviso.
 */
fun cabezaDelAviso(
    avisos: List<SmsMessage>,
    leido: ParsedSms?,
    hoy: LocalDate,
    sinLeer: AvisoSinLeer? = null,
): CabezaDelAviso {
    val primero = avisos.first()
    val subtitulo = if (avisos.size > 1) {
        "${canalesDelMismoPago(avisos)} · ${cuandoLlegoElAviso(primero.time, hoy)}"
    } else {
        "${bancoDelAviso(primero.bank)} · ${cuandoLlegoElAviso(primero.time, hoy)}"
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
            subtitulo = if (avisos.size > 1) subtitulo else cuandoLlegoElAviso(primero.time, hoy),
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
        else -> IconoEnCirculo(Icons.Rounded.Inbox, tono = if (pideDecision) TonoDeIcono.Marca else TonoDeIcono.Neutro)
    }
}

/** **El monto de la cabeza**, con la [Cifra] del sistema; nada si Movi no leyó ninguno. */
@Composable
internal fun CifraDeLaCabeza(cabeza: CabezaDelAviso) {
    val monto = cabeza.montoConSigno ?: return
    Cifra(monto, tono = cabeza.tono, moneda = cabeza.moneda)
}
