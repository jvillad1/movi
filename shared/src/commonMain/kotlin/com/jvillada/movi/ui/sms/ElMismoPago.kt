package com.jvillada.movi.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.horaLegibleDeSms
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.fecha.hoyEnAppZone

/**
 * El origen de un aviso como lo diría una persona: «Notificación · Google Wallet» → «Google Wallet»,
 * «Correo · Bancolombia» → «Bancolombia». Un SMS lleva el código del remitente y se deja como está.
 */
internal fun origenLegibleDelAviso(bank: String): String =
    bank.substringAfter('·', bank).trim().ifBlank { bank.trim() }

/**
 * **La línea que avisa que dos avisos parecen el mismo pago**, antes de aprobar el segundo.
 *
 * El 25-sep el dueño aprobó dos avisos del mismo pago con la Glim —el de Google Wallet y el de la
 * app de Glim— y quedaron dos movimientos: nada le dijo que eran el mismo. El server marca el
 * parecido (`SmsMessage.parecidoA`); esto lo dice con el origen y la hora del otro, que es lo que
 * él puede reconocer. Si no se sabe cuál es el otro (no se pudo leer), se dice igual, sin detalle.
 */
internal fun avisoDelMismoPago(otro: SmsMessage?): String {
    if (otro == null) return "Parece el mismo pago que otro aviso."
    val origen = origenLegibleDelAviso(otro.bank)
    val hora = horaLegibleDeSms(otro.time)
    if (hora == null) return "Parece el mismo pago que el aviso de $origen."
    // «de la 1:15», no «de las 1:15». Y la hora ya termina en punto («a. m.»): no se le pone otro.
    val articulo = if (hora.startsWith("1:")) "la" else "las"
    val cierre = if (hora.endsWith(".")) "" else "."
    return "Parece el mismo pago que el aviso de $origen de $articulo $hora$cierre"
}

/** La línea de apoyo de [avisoDelMismoPago], con el color de aviso: es algo que revisar antes de aprobar. */
@Composable
internal fun LineaDelMismoPago(otro: SmsMessage?, modifier: Modifier = Modifier) {
    Text(
        avisoDelMismoPago(otro),
        style = Movi.textos.apoyo,
        color = Movi.colores.aviso,
        modifier = modifier,
    )
}

// ── Un pago, una tarjeta (4-oct-2026) ───────────────────────────────────────────────────────────

/**
 * **El canal de un aviso, como se nombra en la tarjeta del mismo pago**: «85540» → «SMS»,
 * «Notificación · Google Wallet» → «Google Wallet», «Correo · Bancolombia» → «Correo». Así la línea
 * «SMS · Bancolombia · Google Wallet» dice por dónde llegó cada aviso, que es lo que distingue a
 * uno de otro (el monto es el mismo en todos).
 */
internal fun canalLegibleDelAviso(bank: String): String {
    val limpio = bank.replace(' ', ' ').trim()
    return when {
        limpio.isEmpty() || limpio.equals("SMS", ignoreCase = true) || limpio.all { it.isDigit() } -> "SMS"
        limpio.startsWith("Correo", ignoreCase = true) -> "Correo"
        else -> origenLegibleDelAviso(limpio)
    }
}

/**
 * «Avisado 3 veces»: la píldora de un pago que llegó por varios canales. Antes era el título de la
 * tarjeta; desde la vuelta de diseño 2 el título es el comercio y esto dice lo que tiene de raro.
 */
internal fun pildoraDelMismoPago(cuantos: Int): String = "Avisado $cuantos veces"

/** «SMS · Bancolombia · Google Wallet», en el orden en que llegaron. */
internal fun canalesDelMismoPago(miembros: List<SmsMessage>): String =
    miembros.map { canalLegibleDelAviso(it.bank) }.distinct().joinToString(" · ")

/** «Ya lo anotaste con el aviso de Google Wallet de las 9:15 a. m.» */
internal fun avisoYaAnotado(otro: SmsMessage?): String {
    if (otro == null) return "Ya lo anotaste con otro aviso de este pago."
    val canal = canalLegibleDelAviso(otro.bank)
    val deQuien = if (canal == "SMS") "el SMS" else if (canal == "Correo") "el correo" else "el aviso de $canal"
    val hora = horaLegibleDeSms(otro.time) ?: return "Ya lo anotaste con $deQuien."
    val articulo = if (hora.startsWith("1:")) "la" else "las"
    val cierre = if (hora.endsWith(".")) "" else "."
    return "Ya lo anotaste con $deQuien de $articulo $hora$cierre"
}

/**
 * **Los avisos de un pago, en el orden en que llegaron**, sacados de la lista que ya se tiene a mano
 * con [SmsMessage.miembrosDelGrupo]. Los que no estén en la lista (no debería pasar) se omiten.
 */
internal fun avisosDelPago(cabeza: SmsMessage, mensajes: List<SmsMessage>): List<SmsMessage> {
    val porId = mensajes.associateBy { it.id }
    return cabeza.miembrosDelGrupo.mapNotNull { porId[it] }.ifEmpty { listOf(cabeza) }
}

/**
 * **La bandeja con lo que el dueño ya resolvió acá**, mientras la relectura no llega (o falla):
 * los avisos de [resueltos] toman ese estado, y los de [separados] dejan de ser parte de un pago.
 * Un pago que se queda con un solo aviso deja de serlo también. Sin esto, «No son el mismo pago»
 * no cambiaría nada a la vista hasta la próxima lectura.
 */
fun conLoResueltoEnLaBandeja(
    mensajes: List<SmsMessage>,
    resueltos: Map<String, String>,
    separados: Set<String>,
): List<SmsMessage> {
    if (resueltos.isEmpty() && separados.isEmpty()) return mensajes
    fun suelto(sms: SmsMessage) = sms.copy(grupoId = null, miembrosDelGrupo = emptyList(), parecidoA = null, yaAnotadoCon = null)
    val conEstado = mensajes.map { sms -> resueltos[sms.id]?.let { sms.copy(state = it) } ?: sms }
    return conEstado.map { sms ->
        if (sms.grupoId == null) return@map sms
        if (sms.id in separados) return@map suelto(sms)
        val quedan = sms.miembrosDelGrupo.filter { it !in separados }
        if (quedan.size < 2) suelto(sms) else sms.copy(miembrosDelGrupo = quedan)
    }
}

/**
 * **La cuenta de un pago avisado varias veces**: se resuelve con cada aviso y gana la mejor
 * lectura —la elegida a mano, después la que salió del número escrito, después la del banco, y
 * recién al final la de por defecto—. Así un pago por QR toma la cuenta *8133 del SMS aunque la
 * propuesta (el comercio) salga de Google Wallet. A igualdad, el primero de [avisos] (la propuesta).
 */
internal fun resolverCuentaDelPago(
    accounts: List<com.jvillada.movi.shared.model.Account>,
    uso: com.jvillada.movi.shared.model.UsoDeCuenta,
    avisos: List<SmsMessage>,
    elegidaAMano: String?,
): com.jvillada.movi.ui.components.CuentaDelBanco {
    val lecturas = avisos.map { aviso ->
        com.jvillada.movi.ui.components.resolverCuentaDelBanco(
            accounts = accounts, uso = uso, banco = aviso.bank, elegidaAMano = elegidaAMano, textoDelMensaje = aviso.text,
        )
    }
    val orden = listOf(
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.A_MANO,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.POR_EL_NUMERO,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.POR_EL_BANCO,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.SUGERIDA_POR_MOVI,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.POR_DEFECTO,
        com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.NINGUNA,
    )
    return lecturas.minByOrNull { orden.indexOf(it.origen).let { i -> if (i < 0) orden.size else i } }
        ?: com.jvillada.movi.ui.components.CuentaDelBanco(null, com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco.NINGUNA)
}

/** Los textos de la tarjeta del mismo pago, para las pruebas. */
const val NO_SON_EL_MISMO_PAGO: String = "No son el mismo pago"
const val ESTE_ES_OTRO_PAGO: String = "Este es otro pago"
const val TAG_TARJETA_DEL_MISMO_PAGO: String = "tarjeta-del-mismo-pago"

/**
 * **Un pago avisado varias veces, como una sola tarjeta** de «Por revisar»: cuántas veces y por
 * dónde llegó, los textos plegados (se abren con «Ver los N avisos»), y las acciones del pago
 * entero —«Revisar», «Ignorar», «No son el mismo pago»—. Si uno de los avisos ya se confirmó, el
 * pago ya tiene su movimiento: lo dice, y en vez de «Revisar» ofrece «Cerrar».
 */
@Composable
internal fun TarjetaDelMismoPago(
    avisos: List<SmsMessage>,
    onRevisar: () -> Unit,
    onIgnorar: () -> Unit,
    onNoSonElMismoPago: () -> Unit,
    onCerrar: () -> Unit,
    trabajando: Boolean = false,
    /**
     * La bandeja que se vacía sola: lo que Movi va a anotar («Comida · Bancolombia Ahorros ·
     * $46.000») cuando ya entendió el pago. Con esto y [onConfirmar] la tarjeta ofrece «Confirmar»
     * directo; sin esto, el botón principal sigue siendo «Revisar».
     */
    entendido: String? = null,
    onConfirmar: (() -> Unit)? = null,
    /** Lo que Movi leyó del pago (la propuesta de la bandeja): el comercio y el monto de la cabeza. */
    leido: ParsedSms? = null,
    sinLeer: AvisoSinLeer? = null,
    /** «Ojo: el aviso es de … y la cuenta es de …» (ver [advertenciaDeBancos]), o `null`. */
    advertencia: String? = null,
    /**
     * Vuelta 44 · **lo que Movi entendió, arriba**: «Transporte · Bancolombia Ahorros» en el lugar del
     * subtítulo, entendido o no (ver `LoQueMoviEntendio.linea`). Con esto, el botón que abre Revisar
     * aviso junto a «Confirmar» dice «Cambiar». Solo la bandeja lo pasa.
     */
    loEntendido: String? = null,
    modifier: Modifier = Modifier,
    /** Vuelta 74: «¿Ya lo anotaste?» encontró el movimiento del pago (ver [YaAnotadoEnLaFila]). */
    yaAnotado: YaAnotadoEnLaFila? = null,
) {
    var abiertos by remember { mutableStateOf(false) }
    val yaAnotadoCon = avisos.firstNotNullOfOrNull { it.yaAnotadoCon }?.let { id -> avisos.firstOrNull { it.id == id } }
    val anotado = yaAnotadoCon != null || avisos.any { it.state == SMS_STATE_CONFIRMED }
    val primero = avisos.first()
    val confirmable = !anotado && entendido != null && onConfirmar != null
    val cabeza = remember(avisos, leido, sinLeer) { cabezaDelAviso(avisos, leido, hoyEnAppZone(), sinLeer) }
    val conLoEntendido = loEntendido != null && !anotado
    TarjetaDeAviso(
        titulo = cabeza.titulo,
        subtitulo = if (conLoEntendido) loEntendido else cabeza.subtitulo,
        tagDelSubtitulo = if (conLoEntendido) TAG_LINEA_DE_LO_ENTENDIDO else null,
        modifier = modifier.testTag(TAG_TARJETA_DEL_MISMO_PAGO),
        inicio = { AvatarDeLaCabeza(cabeza) },
        cifra = cabeza.monto?.let { { CifraDeLaCabeza(cabeza) } },
        // Sin «Pendiente»: en la bandeja todo lo está. Lo que sí dice algo: que ya se anotó, o que
        // llegó varias veces.
        estado = if (anotado) "Ya anotado" to TonoDePildora.Exito else pildoraDelMismoPago(avisos.size) to TonoDePildora.Info,
        acciones = {
            if (anotado) {
                PastillaDelPago("Cerrar", principal = true, habilitada = !trabajando, onClick = onCerrar)
            } else if (yaAnotado != null) {
                // Vuelta 74: ya está anotado a mano; «Es este» lo cierra sin crear otro.
                AccionesDelYaAnotado(yaAnotado, trabajando, onRevisar, onIgnorar)
            } else if (confirmable) {
                PastillaDelPago(
                    CONFIRMAR_EN_LA_FILA, principal = true, habilitada = !trabajando, onClick = onConfirmar!!,
                    modifier = Modifier.testTag(TAG_CONFIRMAR_EN_LA_FILA),
                )
                PastillaDelPago(if (conLoEntendido) CAMBIAR_EN_LA_FILA else "Revisar", habilitada = !trabajando, onClick = onRevisar)
                PastillaDelPago(IGNORAR_EN_LA_FILA, habilitada = !trabajando, onClick = onIgnorar)
            } else {
                PastillaDelPago("Revisar", principal = true, habilitada = !trabajando, onClick = onRevisar)
                PastillaDelPago(IGNORAR_EN_LA_FILA, habilitada = !trabajando, onClick = onIgnorar)
            }
            PastillaDelPago(NO_SON_EL_MISMO_PAGO, habilitada = !trabajando, onClick = onNoSonElMismoPago)
        },
    ) {
        if (anotado) Text(avisoYaAnotado(yaAnotadoCon), style = Movi.textos.apoyo, color = Movi.colores.aviso)
        if (!anotado && yaAnotado != null) LineaDelYaAnotado(lineaDelYaAnotado(yaAnotado.coincidencias, hoyEnAppZone()))
        if (!anotado) advertencia?.let { LineaDeAdvertenciaDeBancos(it) }
        // El texto del primero siempre; los demás, plegados: dicen lo mismo con otras palabras.
        TextoDelAviso(primero, plegable = true)
        if (abiertos) avisos.drop(1).forEach { aviso -> TextoDelAviso(aviso, plegable = true) }
        Boton(
            texto = if (abiertos) "Ocultar los otros avisos" else "Ver los ${avisos.size} avisos",
            onClick = { abiertos = !abiertos },
            variante = VarianteDeBoton.Fantasma,
            tamano = TamanoDeBoton.S,
        )
        if (confirmable && !conLoEntendido) LineaDeLoEntendido(entendido!!)
    }
}

/**
 * **Un aviso del banco que espera una decisión, con la forma de una propuesta de Movi** (ola R4):
 * la superficie de la propuesta ([NivelDeSuperficie.Propuesta]) cuando pide algo, la capa 1 cuando
 * ya es historial; en el medio lo que dice el banco; abajo las acciones en botones chicos. La usan
 * la bandeja «Por revisar», «Captura del banco» y Reconciliar, para que un aviso se vea igual en
 * todas partes —y como las propuestas del Inicio—.
 *
 * **La cabeza se lee primero** (vuelta de diseño 2, como el detalle de un movimiento): el inicio (la
 * marca del comercio, o el ícono), el título (el comercio, en hasta dos renglones) con el subtítulo
 * (banco · hora) debajo, y a la derecha la [cifra] y, debajo de ella, el [estado] —solo si dice algo:
 * «Pendiente» en la bandeja sobra, porque ahí todo lo está—.
 *
 * **Vuelta 42: [destacada].** En la bandeja el aviso es la propuesta (con sombra); en Reconciliar es
 * el papel que se está revisando, y la propuesta es «Movi sugiere», justo debajo: dos tarjetas con
 * sombra seguidas no destacaban ninguna. Ahí va plano (`destacada = false`, la capa 1 sin sombra).
 */
@Composable
internal fun TarjetaDeAviso(
    titulo: String,
    subtitulo: String?,
    modifier: Modifier = Modifier,
    inicio: (@Composable () -> Unit)? = null,
    estado: Pair<String, TonoDePildora>? = null,
    pideDecision: Boolean = true,
    /** Con la sombra de una propuesta. Por defecto, si [pideDecision]; Reconciliar la pide plana. */
    destacada: Boolean = pideDecision,
    /** El monto, a la derecha de la cabeza: una [com.jvillada.movi.ui.components.ds.Cifra]. */
    cifra: (@Composable () -> Unit)? = null,
    acciones: (@Composable RowScope.() -> Unit)? = null,
    pie: (@Composable () -> Unit)? = null,
    /** El `testTag` del subtítulo, cuando dice algo que una prueba busca (lo que Movi entendió). */
    tagDelSubtitulo: String? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Superficie(
        modifier = modifier.fillMaxWidth().testTag(TAG_TARJETA_DE_AVISO),
        nivel = if (destacada) NivelDeSuperficie.Propuesta else NivelDeSuperficie.Capa1,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            inicio?.invoke() ?: IconoEnCirculo(Icons.Rounded.Inbox, tono = if (pideDecision) TonoDeIcono.Aviso else TonoDeIcono.Neutro)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(TAG_TITULO_DE_LA_TARJETA_DE_AVISO),
                )
                if (subtitulo != null) {
                    Text(
                        subtitulo,
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (tagDelSubtitulo != null) Modifier.testTag(tagDelSubtitulo) else Modifier,
                    )
                }
            }
            if (cifra != null || estado != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
                ) {
                    cifra?.invoke()
                    if (estado != null) Pildora(estado.first, estado.second)
                }
            }
        }
        Column(
            modifier = Modifier.padding(top = Medida.dentroDeSeccion),
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            content = contenido,
        )
        if (acciones != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Medida.dentroDeSeccion),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                verticalAlignment = Alignment.CenterVertically,
                content = acciones,
            )
        }
        if (pie != null) {
            Separador(SangriaDeSeparador.Ninguna, Modifier.padding(vertical = Medida.dentroDeSeccion))
            pie()
        }
    }
}

/**
 * Un aviso dentro de la tarjeta: por dónde y cuándo llegó, y su texto con lo importante en negrita.
 * [plegable]: el texto en dos renglones, que se abren al tocarlo (ver [LoQueDiceElBanco]).
 */
@Composable
internal fun TextoDelAviso(aviso: SmsMessage, plegable: Boolean = false, accion: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${canalLegibleDelAviso(aviso.bank)} · ${horaLegibleDeSms(aviso.time) ?: aviso.time}",
                style = Movi.textos.apoyoMedio,
                color = Movi.colores.textoMedio,
                modifier = Modifier.weight(1f),
            )
            accion?.invoke()
        }
        LoQueDiceElBanco(aviso.text, plegable = plegable)
    }
}

/** Cuántos renglones del texto del banco se ven en una tarjeta antes de tocarlo. */
const val RENGLONES_DEL_TEXTO_PLEGADO: Int = 2

/** El tag del texto del banco dentro de una tarjeta. */
const val TAG_LO_QUE_DICE_EL_BANCO: String = "lo-que-dice-el-banco"

/** El tag del título de una [TarjetaDeAviso]. */
const val TAG_TITULO_DE_LA_TARJETA_DE_AVISO: String = "titulo-de-la-tarjeta-de-aviso"

/** La [TarjetaDeAviso] entera: la mide `ElTextoAl130NoSeCortaTest` (vuelta 55). */
const val TAG_TARJETA_DE_AVISO: String = "tarjeta-de-aviso"

/**
 * **Lo que dijo el banco, tal cual**, con lo importante en negrita ([resaltadoDelTextoDelBanco]) y
 * una raya a la izquierda que lo marca como cita. En la letra monoespaciada: es un dato crudo.
 *
 * [plegable] (las tarjetas de «Por revisar», vuelta de diseño 2): es la evidencia, no el titular —el
 * comercio y el monto ya están arriba—, así que va en [RENGLONES_DEL_TEXTO_PLEGADO] renglones con
 * «…» y se abre entero al tocarlo (solo si de verdad se cortó). Reconciliar lo muestra entero.
 */
@Composable
internal fun LoQueDiceElBanco(
    texto: String,
    modifier: Modifier = Modifier,
    plegable: Boolean = false,
    /** Cuántos renglones se ven plegado; la bandeja (vuelta 44) lo deja en uno: es la evidencia. */
    renglones: Int = RENGLONES_DEL_TEXTO_PLEGADO,
) {
    var abierto by remember(texto) { mutableStateOf(false) }
    var seCorto by remember(texto) { mutableStateOf(false) }
    val tocable = plegable && (seCorto || abierto)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(
                if (tocable) {
                    Modifier.clickable(onClickLabel = if (abierto) "Plegar el mensaje" else "Ver el mensaje entero") { abierto = !abierto }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(modifier = Modifier.width(Medida.trazo).fillMaxHeight().background(Movi.colores.hilo))
        Text(
            resaltadoDelTextoDelBanco(texto, Movi.textos.enfasis),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            fontFamily = FontFamily.Monospace,
            maxLines = if (plegable && !abierto) renglones else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (plegable && !abierto) seCorto = it.hasVisualOverflow },
            modifier = Modifier.padding(start = Espacio.s).testTag(TAG_LO_QUE_DICE_EL_BANCO),
        )
    }
}

/**
 * Vuelta 82 · **un amarillo por vista en Por revisar**: si la acción esperada de cada tarjeta de aviso va
 * en amarillo. `true` por defecto (Captura del banco, una tarjeta sola); Por revisar lo apaga cuando hay
 * más de una tarjeta o el lote «Confirmar N» está a la vista ([laTarjetaLlevaElAmarillo]): entonces el
 * único amarillo es el lote, y «Confirmar», «Es este» o «Sí, se cobró» de cada tarjeta van secundarios.
 */
val LocalLaTarjetaLlevaElAmarillo = compositionLocalOf { true }

/**
 * ¿La acción de cada tarjeta de Por revisar va en amarillo? Solo si es **la única tarjeta** con acciones
 * ([tarjetas]) y el lote no está a la vista ([conLote]): con dos «Confirmar» amarillos (o el lote y uno
 * más) la vista ya no dice cuál es su acción. Pura.
 */
fun laTarjetaLlevaElAmarillo(tarjetas: Int, conLote: Boolean): Boolean = !conLote && tarjetas <= 1

/**
 * Un botón de una tarjeta de aviso: un [Boton] chico del sistema. [principal] es la acción que se
 * espera («Confirmar», «Revisar»); el resto va en la capa 2. Vuelta 82: la principal va en amarillo solo
 * si [LocalLaTarjetaLlevaElAmarillo] lo deja.
 */
@Composable
internal fun PastillaDelPago(
    texto: String,
    habilitada: Boolean,
    principal: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val enAmarillo = principal && LocalLaTarjetaLlevaElAmarillo.current
    Boton(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        variante = if (enAmarillo) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
        tamano = TamanoDeBoton.S,
        habilitado = habilitada,
    )
}

// ── La bandeja que se vacía sola ───────────────────────────────────────────────────────────────

/** El botón de la fila que anota lo que Movi entendió, sin pasar por Reconciliar. */
const val CONFIRMAR_EN_LA_FILA: String = "Confirmar"

/**
 * Vuelta 44: el botón que abre Revisar aviso junto a «Confirmar», cuando la tarjeta ya dice lo que
 * Movi entendió —para cambiar la categoría, la cuenta o el comercio—. Sin «Confirmar», es «Revisar».
 */
const val CAMBIAR_EN_LA_FILA: String = "Cambiar"

/** El botón de la fila que ignora el aviso (o todos los del pago). */
const val IGNORAR_EN_LA_FILA: String = "Ignorar"

/** El tag de «Confirmar» en una tarjeta de «Por revisar». */
const val TAG_CONFIRMAR_EN_LA_FILA: String = "por-revisar:confirmar-en-la-fila"

/** El tag de la línea «Comida · Bancolombia Ahorros · $46.000» de una tarjeta de «Por revisar». */
const val TAG_LINEA_DE_LO_ENTENDIDO: String = "por-revisar:linea-de-lo-entendido"

/** «Se anota: Comida · Bancolombia Ahorros · $46.000», justo encima de «Confirmar». */
@Composable
internal fun LineaDeLoEntendido(linea: String) {
    Text(
        "Se anota: $linea",
        style = Movi.textos.apoyoMedio,
        color = Movi.colores.texto,
        modifier = Modifier.testTag(TAG_LINEA_DE_LO_ENTENDIDO),
    )
}
