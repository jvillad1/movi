package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.inicioDeLaCuentaSiElSmsEsAnterior
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlin.math.roundToLong
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.jvillada.movi.shared.model.MAX_CONCEPTO_LENGTH
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.UsedCategoriesCache
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.esIdDeComprobante
import com.jvillada.movi.shared.model.soloLoQueLlegoSolo
import com.jvillada.movi.shared.model.Captura
import com.jvillada.movi.shared.model.MAX_DIAS_PARA_BANCO_MUDO
import com.jvillada.movi.shared.model.OrigenMudo
import com.jvillada.movi.shared.model.origenesMudos
import com.jvillada.movi.shared.model.textoDeOrigenMudo
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.avisoDeCaptura
import com.jvillada.movi.shared.model.capturaDeSms
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.ofreceVincularDeuda
import com.jvillada.movi.shared.model.identificador
import com.jvillada.movi.shared.model.nombreDelMovimientoConElDestino
import com.jvillada.movi.ui.destinos.FilaGuardarElDestino
import com.jvillada.movi.ui.destinos.nombreParaLaFila
import com.jvillada.movi.ui.destinos.rememberDestinosParaGuardar
import com.jvillada.movi.shared.model.VincularPagoDeDeudaRequest
import com.jvillada.movi.ui.transactions.SelectorDeCuentaDeDeuda
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import com.jvillada.movi.shared.model.fechaCortaDeSms
import com.jvillada.movi.shared.model.fechaLegibleDeSms
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone

/**
 * **La bandeja, del más nuevo al más viejo.**
 *
 * El dueño, con 96 mensajes adentro: *«al importar SMS del teléfono el último mensaje recibido
 * queda de último en la lista, debe ser el primero»*. La causa estaba en el server —`GET /api/sms`
 * no tenía `ORDER BY`, así que Postgres devolvía las filas en el orden que le conviniera— y se
 * arregló allá. Esto es lo mismo de este lado, y no sobra: el orden de una lista que el dueño lee
 * de arriba abajo no debería depender de que un endpoint se acuerde.
 *
 * **Se ordena por el texto de `time`, y está bien.** Lo escribe el teléfono con
 * `SimpleDateFormat("yyyy-MM-dd HH:mm")` (ver `SmsSync`): en ese formato el orden alfabético **es**
 * el cronológico, porque cada campo va de más significativo a menos y con ancho fijo. Un `time`
 * con otra forma no rompe nada — queda ordenado entre los suyos, no descarta la lista.
 *
 * `sortedByDescending` es **estable**: dos mensajes del mismo minuto conservan el orden en que
 * llegaron, en vez de bailar entre lecturas.
 */
fun mensajesMasRecientesPrimero(mensajes: List<SmsMessage>): List<SmsMessage> =
    mensajes.sortedByDescending { it.time }

/**
 * El nombre de la ficha de Ajustes y el título de su pantalla ([CapturaDelBancoScreen]).
 *
 * En Android es **«Captura del banco»**: ahí se configura lo que la captura necesita (el permiso de
 * SMS, el acceso a notificaciones, la hibernación). En la web y en iOS no hay nada que configurar
 * —esos aparatos no leen mensajes— pero el historial igual existe (los correos del banco también
 * llegan ahí), y «Captura» sería prometer algo que ese aparato no hace: se llama **«Mensajes del
 * banco»**, que es lo que se ve.
 */
val tituloDeCapturaDelBanco: String get() = if (isAndroid) "Captura del banco" else "Mensajes del banco"

/**
 * **«Captura del banco»** — ola C: lo que quedó de la bandeja de SMS cuando lo pendiente se mudó a
 * «Por revisar» (ver `PorRevisarScreen`). Arriba, qué se sabe de la captura y lo que la configura;
 * abajo, el **historial** de todos los mensajes que llegaron —confirmados, ignorados y los que
 * todavía esperan— para consultar. Es una ficha de Ajustes: esto se toca de vez en cuando.
 */
@Composable
fun CapturaDelBancoScreen(onNavigate: (Screen) -> Unit) {
    val coroutine = rememberCoroutineScope()
    /**
     * `null` = la bandeja todavía no contestó (o su lectura falló); lista vacía = contestó y no
     * hay nada. La distinción no era necesaria mientras esta lista solo pintaba filas, y pasó a
     * serlo cuando de su vacío se deduce «Movi nunca ha recibido un mensaje de tu banco»: con un
     * `emptyList()` por defecto, quedarse sin señal un segundo bastaba para afirmarle eso a
     * alguien cuya captura anda perfecto. Misma disciplina que `DashboardData.accounts`.
     */
    var smsItems by remember { mutableStateOf<List<SmsMessage>?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    /** `null` = el perfil todavía no contestó; hasta entonces no se ofrece silenciar ni no. */
    var silenciada by remember { mutableStateOf<Boolean?>(null) }
    /** Ola 2 · banco mudo: los días que eligió; `null` hasta que el perfil contesta. */
    var diasParaBancoMudo by remember { mutableStateOf<Int?>(null) }

    var leyendo by remember { mutableStateOf(true) }
    // `intentar` y no `runCatching`: una lectura cancelada no puede quedar contada como contestada.
    LaunchedEffect(refreshKey) {
        leyendo = true
        intentar { Repositories.wallets.getSmsMessages() }
            .onSuccess { smsItems = it }
        leyendo = false
    }
    LaunchedEffect(Unit) {
        intentar { Repositories.wallets.getUserProfile() }
            .onSuccess {
                silenciada = it.smsAlertMuted
                diasParaBancoMudo = it.diasParaBancoMudo
            }
    }
    val mensajes = mensajesMasRecientesPrimero(smsItems.orEmpty())
    // El estado de la captura sale de la MISMA función que usa el server para el Inicio
    // (`capturaDeSms`, en :core) — acá sin un viaje extra, porque la lista ya está bajada.
    // Sin los comprobantes compartidos (Ola 2): esos no prueban que la captura ande.
    val aviso = smsItems?.let { avisoDeCaptura(capturaDeSms(soloLoQueLlegoSolo(it).map { sms -> sms.time })) }
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60: encabezado único — se abre desde Ajustes (flecha, F22); «Actualizar» es la acción
        // propia. Ola C: ya no lleva «N por confirmar» de subtítulo — lo pendiente se revisa en
        // «Por revisar», y esta pantalla es el historial.
        MinScreenHeader(
            title = tituloDeCapturaDelBanco,
            leading = HeaderLeading.Back(fallback = Screen.Mas),
            action = {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Actualizar",
                    tint = Movi.colores.textoMedio,
                    modifier = Modifier.size(20.dp).clickableSimple { refreshKey++ },
                )
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
        ) {
            item {
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(18.dp),
                ) {
                    // **El hecho primero, y en todas las plataformas.**
                    //
                    // Acá decía "AUTO-LECTURA ACTIVA" en cuanto el permiso estuviera concedido,
                    // y solo en Android; la web no pintaba nada. Las dos cosas fallaron a la vez:
                    // el permiso concedido con el receiver muerto es exactamente el estado en el
                    // que estuvo el dueño —semanas sin que llegara un solo mensaje— y él usa la
                    // web, donde ni siquiera había un rótulo que pudiera mentirle.
                    //
                    // Ahora se dice lo observado, que no depende de la plataforma: qué llegó y
                    // cuándo llegó lo último. Ver `CapturaDeSms` en :core.
                    //
                    // Mientras la bandeja no conteste no se dice nada: un `null` no es «nunca
                    // llegó nada», y afirmarlo por una lectura caída sería el mismo error al
                    // revés.
                    aviso?.let { hecho ->
                        Text(
                            hecho.rotulo,
                            style = Movi.textos.apoyo,
                            color = if (hecho.esAlerta) Movi.colores.aviso else Movi.colores.textoMedio,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            hecho.detalle,
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.texto,
                            lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    // Y recién después, lo que depende de dónde estés parado. «Este dispositivo
                    // no puede leer SMS» y «nunca llegó nada» son dos afirmaciones distintas: la
                    // primera es normal en la web y en iOS, la segunda no lo es en ninguna parte.
                    if (isAndroid) {
                        // Solo se nombra lo que FALTA. Que el permiso esté dado no prueba que la
                        // captura corra, así que no se dice nada cuando está dado.
                        if (!rememberSmsCaptureReady()) {
                            Text(
                                "En este teléfono falta el permiso de mensajes: se otorga en la sección de abajo.",
                                style = Movi.textos.apoyo,
                                color = Movi.colores.aviso,
                                lineHeight = 18.sp,
                            )
                        }
                    } else {
                        Text(
                            "Este dispositivo no puede leer mensajes: eso lo hace un teléfono Android con Movi instalado. Aquí queda el historial de lo que llegó; lo pendiente se revisa en «Por revisar», desde Movimientos.",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            lineHeight = 18.sp,
                        )
                    }

                    // El freno al ruido crónico, y vive acá y no en el Inicio a propósito: para
                    // callar el recordatorio hay que estar viendo lo que se calla. Solo aparece
                    // en el caso que genera la alerta — si ya llegó algo, no hay nada que callar.
                    if (aviso?.esAlerta == true) {
                        silenciada?.let { silencioActual ->
                            Spacer(Modifier.height(12.dp))
                            Hairline()
                            Spacer(Modifier.height(12.dp))
                            if (silencioActual) {
                                Text(
                                    "Este aviso no se muestra en Hoy ni en Por revisar.",
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoMedio,
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(
                                if (silencioActual) "Volver a avisarme en Hoy y en Por revisar" else "No me avises de esto en Hoy ni en Por revisar",
                                style = Movi.textos.apoyo,
                                color = Movi.colores.texto,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickableSimple {
                                    val nuevo = !silencioActual
                                    silenciada = nuevo
                                    coroutine.launch {
                                        runCatching {
                                            Repositories.wallets.updateUserProfile(
                                                UpdateProfileRequest(smsAlertMuted = nuevo),
                                            )
                                        }.onFailure {
                                            // Sin snackbar en esta pantalla: se revierte para no
                                            // afirmar un silencio que el server no guardó.
                                            silenciada = silencioActual
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                // Ola 2 · «banco mudo»: qué origen se calló (la misma regla del server, sobre la
                // lista que ya está bajada) y cuántos días de silencio avisan.
                diasParaBancoMudo?.let { dias ->
                    val mudos = smsItems?.let { lista ->
                        val ahora = Clock.System.now().toEpochMilliseconds()
                        origenesMudos(
                            soloLoQueLlegoSolo(lista).mapNotNull { sms ->
                                momentoDelSms(sms.time, ahora = Long.MAX_VALUE).takeIf { it != Long.MAX_VALUE }
                                    ?.let { Captura(sms.bank, it) }
                            },
                            ahora,
                            dias,
                        )
                    }.orEmpty()
                    Spacer(Modifier.height(14.dp))
                    AjusteDeBancoMudo(
                        dias = dias,
                        mudos = mudos,
                        onCambio = { nuevo ->
                            val antes = dias
                            diasParaBancoMudo = nuevo
                            coroutine.launch {
                                intentar { Repositories.wallets.updateUserProfile(UpdateProfileRequest(diasParaBancoMudo = nuevo)) }
                                    // Sin snackbar acá, como el silencio de arriba: se revierte.
                                    .onFailure { diasParaBancoMudo = antes }
                            }
                        },
                    )
                }
                // Solo Android pinta algo acá: la configuración de la captura de SMS (permisos,
                // hibernación, historial) que antes vivía en la pantalla del APK sensor.
                // Reemplaza también a la vieja tarjeta "Sincronizar SMS del teléfono", que subía
                // el inbox SIN el filtro bancario — el historial de la sección sí lo aplica, así
                // que lo que no matchea nunca sale del teléfono.
                SmsSensorSetupSection(onSynced = { refreshKey++ })

                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Historial", count = if (smsItems == null) null else mensajes.size)
                if (smsItems == null && !leyendo) {
                    NoSePudoLeer("No pudimos cargar tus mensajes", onReintentar = { refreshKey++ })
                }
            }

            mensajes.forEach { sms ->
                item {
                    TarjetaDeMensajeDelBanco(
                        sms,
                        onRevisar = { onNavigate(Screen.SMSReconcile(sms.id)) },
                        parecidoA = sms.parecidoA?.let { id -> mensajes.firstOrNull { it.id == id } },
                    )
                }
            }
        }
    }
}

/** El tag de la tarjeta del aviso de banco mudo en Captura del banco. */
const val TAG_AJUSTE_DE_BANCO_MUDO: String = "ajuste-de-banco-mudo"

/** «3 días», «1 día», «Nunca». */
internal fun textoDeDiasDeBancoMudo(dias: Int): String = when {
    dias <= 0 -> "Nunca"
    dias == 1 -> "1 día"
    else -> "$dias días"
}

/**
 * **«Avisarme si un banco se calla»** (Ola 2): cuántos días sin capturas de un origen que llegaba
 * siempre cuentan como silencio, con un − y un +; `0` es «Nunca». Encima, si ya hay uno callado, lo
 * dice. Vale para la cuenta (web y teléfono), por eso vive en el server y no en el aparato.
 */
@Composable
private fun AjusteDeBancoMudo(dias: Int, mudos: List<OrigenMudo>, onCambio: (Int) -> Unit) {
    MinCard(
        modifier = Modifier.fillMaxWidth().testTag(TAG_AJUSTE_DE_BANCO_MUDO),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Text(
            "AVISARME SI UN BANCO SE CALLA",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        mudos.forEach { mudo ->
            Text(conPuntoFinalDelAviso(textoDeOrigenMudo(mudo)), style = Movi.textos.cuerpo, color = Movi.colores.aviso)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            if (dias <= 0) "Movi no te avisa si un banco deja de mandar mensajes, avisos o correos."
            else "Si un banco que te avisaba siempre lleva ${textoDeDiasDeBancoMudo(dias)} sin mandar nada, Movi te lo dice en Hoy.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonDeDias("−", habilitado = dias > 0) { onCambio(dias - 1) }
            Text(
                textoDeDiasDeBancoMudo(dias),
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
                modifier = Modifier.widthIn(min = 64.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            BotonDeDias("+", habilitado = dias < MAX_DIAS_PARA_BANCO_MUDO) { onCambio(dias + 1) }
        }
    }
}

/** El texto de la regla termina sin punto («… Revisa la captura»); en una tarjeta va con él. */
private fun conPuntoFinalDelAviso(texto: String): String = if (texto.endsWith(".")) texto else "$texto."

@Composable
private fun BotonDeDias(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, Movi.colores.borde, CircleShape)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = Movi.textos.titulo, color = if (habilitado) Movi.colores.texto else Movi.colores.textoApagado)
    }
}

/** «$386.902», «$1,300,000», «$4.000.000»: el mismo par de formatos que ya reconoce `amountRegex`
 * de `SmsRoutes.kt` (punto O coma como separador de miles), pero anclado al `$` porque acá solo
 * resaltamos, no parseamos — un COP/USD sin `$` no es tan visible como para pelear por él. */
private val MONTO_EN_PESOS = Regex("""\$[0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]+)?""")

/** «*3684», «*8133», «*10272432504»: el mismo patrón que `CUENTA` en `MemoriaDeCategorias.kt` y
 * `numerosQueNombraElMensaje` en `CuentaDelBanco.kt` — no es pública ninguna de las dos, así que se
 * repite acá para no acoplar un módulo de UI a uno de lógica de negocio por una regex. */
private val CUENTA_O_TARJETA = Regex("""\*\s?(\d{4,})""")

/**
 * **Lo importante de un SMS del banco, en negrita.** El dueño lo pidió mirando la bandeja «Por
 * revisar»: el texto citado tal cual se ve todo con el mismo peso, y lo que quiere escanear de un
 * vistazo —cuánto, en qué cuenta, de qué banco— no salta a la vista.
 *
 * No toca [texto] en sí (sigue siendo el dato crudo que llegó del banco): solo decide qué rangos
 * pintar en negrita al mostrarlo. Sin ninguno de los patrones, devuelve el texto tal cual, sin
 * negrita y sin lanzar — un SMS raro sin monto no es un error.
 */
internal fun resaltadoDelTextoDelBanco(texto: String): AnnotatedString = buildAnnotatedString {
    append(texto)
    fun negrita(rango: IntRange) = addStyle(SpanStyle(fontWeight = FontWeight.Bold), rango.first, rango.last + 1)

    // El banco/entidad: la palabra o frase antes de los primeros dos puntos, si los hay.
    val dosPuntos = texto.indexOf(':')
    if (dosPuntos > 0 && texto.substring(0, dosPuntos).isNotBlank()) negrita(0 until dosPuntos)

    MONTO_EN_PESOS.findAll(texto).forEach { negrita(it.range) }
    CUENTA_O_TARJETA.findAll(texto).forEach { negrita(it.range) }
}

/**
 * **Un mensaje del banco**, como se lee en todas partes: el banco, cuándo llegó y en qué estado
 * está; el texto tal cual; y lo que Movi entendió, con «Revisar» si todavía espera una decisión.
 *
 * Ola C: la usan la bandeja «Por revisar» (los pendientes) y el historial de «Captura del banco»
 * (todos). Una sola tarjeta para las dos, para que un mensaje no se vea distinto según por dónde
 * se lo mire.
 */
@Composable
internal fun TarjetaDeMensajeDelBanco(
    sms: SmsMessage,
    onRevisar: () -> Unit,
    /**
     * El aviso al que este se parece ([SmsMessage.parecidoA]), si está en la lista que se tiene a
     * mano. Solo se usa para decir su origen y su hora: la marca la decide el server.
     */
    parecidoA: SmsMessage? = null,
    /**
     * Lo que va al pie de la tarjeta, debajo de «Revisar»: la bandeja pone acá «¿De quién es la
     * cuenta ·0756?» cuando el mensaje nombra una cuenta que no está guardada. El historial de
     * «Captura del banco» no lo usa.
     */
    pie: (@Composable () -> Unit)? = null,
) {
    MinCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(sms.bank, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            StatusDot(Movi.colores.textoApagado, 2.dp)
            // La fecha se lleva lo que sobra: el banco y el estado son lo que se busca con la
            // vista. En palabras («Hoy, 8:10 a. m.») y no el «2026-09-29 08:10» guardado
            // (revisión del 29-sep); si no entra en un renglón, baja a un segundo en vez de
            // cortarse justo en la hora.
            Text(
                fechaCortaDeSms(sms.time, hoyEnAppZone()),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val (label, color) = when (sms.state) {
                SMS_STATE_PENDING -> "PENDIENTE" to Movi.colores.aviso
                SMS_STATE_CONFIRMED -> "CONFIRMADO" to Movi.colores.entra
                SMS_STATE_IGNORED -> "IGNORADO" to Movi.colores.textoMedio
                else -> sms.state.uppercase() to Movi.colores.textoMedio
            }
            // Un renglón siempre. Con el espaciado completo de `rotulo` (1,7 sp),
            // «CONFIRMADO» no entraba al lado de la fecha: primero se partía en
            // «CONFIRMAD» con la «O» abajo, y sin partirse le comía los minutos a la
            // hora. Visto en la web a 390 dp. A 0,8 sp entran los dos; en un teléfono más
            // angosto la fecha es la que cede.
            Text(
                label,
                style = Movi.textos.rotulo.copy(letterSpacing = 0.8.sp),
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
        // Antes de aprobar, que se sepa que otro aviso parece el mismo pago: dos avisos de un pago
        // aprobados por separado son dos movimientos.
        if (sms.state == SMS_STATE_PENDING && sms.parecidoA != null) {
            Spacer(Modifier.height(8.dp))
            LineaDelMismoPago(parecidoA)
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Movi.colores.hilo))
            Text(resaltadoDelTextoDelBanco(sms.text), style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontFamily = FontFamily.Monospace, lineHeight = 17.sp, modifier = Modifier.padding(start = 12.dp))
        }
        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sms.det, style = Movi.textos.cuerpo, color = Movi.colores.texto, letterSpacing = (-0.1).sp, modifier = Modifier.weight(1f))
            if (sms.state == SMS_STATE_PENDING) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp))
                        .clickable(onClick = onRevisar)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Revisar", style = Movi.textos.apoyo, color = Movi.colores.texto, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (pie != null) {
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            pie()
        }
    }
}

@Composable
fun SMSReconcileScreen(onNavigate: (Screen) -> Unit, smsId: String) {
    val goBack = LocalGoBack.current
    val coroutine = rememberCoroutineScope()
    var sms by remember { mutableStateOf<SmsMessage?>(null) }
    var parsed by remember { mutableStateOf<ParsedSms?>(null) }
    /**
     * Por qué Movi no pudo leer este mensaje, cuando no pudo. Sin esto, un parseo fallido dejaba
     * «Parseando…» para siempre en la tarjeta de la sugerencia y el motivo abajo, lejos, como si
     * todavía estuviera trabajando.
     */
    var noSePudoLeer by remember { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    // La cuenta que el dueño eligió con el dedo, si la eligió. Manda sobre lo que resuelva Movi
    // —ver [resolverCuentaDelBanco]— y por eso vive acá y no adentro del cálculo.
    var cuentaElegida by remember { mutableStateOf<String?>(null) }
    var eligiendoCuenta by remember { mutableStateOf(false) }
    // Ola L: «Cambiar» en la fila Categoría abre el mismo selector de todas las pantallas
    // ([SelectorDeCategoria]). `pedidosDeFoco` distingue quién lo abrió: «+ Nueva» viene a
    // escribir un nombre y lo abre con el cursor en la búsqueda (y cada toque suma uno, así que un
    // segundo toque vuelve a enfocar aunque el teclado ya esté abajo); «Cambiar», a mirar.
    var eligiendoCategoria by remember { mutableStateOf(false) }
    var pedidosDeFoco by remember { mutableStateOf(0) }
    // Ola Q: el dueño tocaba «Cambiar» y el selector se abría debajo de la fila, empujando
    // «Ignorar»/«Confirmar» fuera de lo visible sin que nada le avisara que había más abajo.
    // Este `BringIntoViewRequester` se ancla a la tarjeta del selector (ver más abajo) y el
    // `LaunchedEffect` de [eligiendoCategoria] lo dispara SOLO al abrirse — nunca al escribir en
    // la búsqueda ni en cada recomposición, porque la clave es el booleano, no algo que cambie con
    // cada letra.
    val categoriaBringIntoViewRequester = remember { BringIntoViewRequester() }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /**
     * Movimientos ya anotados que parecen ser este SMS (mismo monto, moneda y tipo, días cercanos).
     *
     * `null` = la revisión todavía no contestó, y mientras tanto **no se puede anotar**: el 25-sep el
     * dueño aprobó dos avisos del mismo pago seis segundos aparte, y el «¿Ya lo anotaste?» del
     * segundo no alcanzó a aparecer porque esta lectura llegaba después de que el botón ya estaba
     * prendido. Lista vacía = revisó y no hay nada.
     */
    var coincidencias by remember { mutableStateOf<List<FinancialEvent>?>(null) }
    /** La revisión de coincidencias falló: se dice, se ofrece reintentar, y anotar pide dos toques. */
    var noSePudoRevisar by remember { mutableStateOf(false) }
    /** Con la revisión caída, el primer toque de «Confirmar» arma este; el segundo anota. */
    var anotarSinRevisar by remember { mutableStateOf(false) }
    var reintentoDeRevision by remember { mutableStateOf(0) }
    /**
     * El otro aviso que parece ser este mismo pago ([SmsMessage.parecidoA]), leído para poder decir
     * su origen y su hora. `null` también si no se pudo leer: la línea se dice igual, sin detalle.
     */
    var otroAviso by remember { mutableStateOf<SmsMessage?>(null) }
    /**
     * El comercio como lo dejó el dueño. `null` = no lo tocó y vale el leído; vacío también vuelve
     * al leído — un movimiento sin nombre no se puede buscar después.
     */
    var comercioEditado by remember { mutableStateOf<String?>(null) }
    /**
     * Ola Y: la cuenta de deuda que el dueño eligió en «¿A cuál crédito o tarjeta corresponde?».
     * `null` = no eligió ninguna, que es el camino de siempre (gasto suelto). Se limpia si cambia
     * de categoría hacia una que ya no ofrece el paso, para no vincular con una elección vieja que
     * quedó pegada de una categoría anterior.
     */
    var cuentaDeDeudaElegida by remember { mutableStateOf<Account?>(null) }

    /**
     * Ola 2: la propuesta salió de un comprobante que el dueño compartió (ver `Papeles.kt` en
     * :core). Se revisa igual que un SMS; cambia el rótulo, el origen del movimiento (`OCR`) y que
     * al confirmar se le dice al server con qué movimiento, para colgar el papel de su cuenta.
     */
    val esComprobante = esIdDeComprobante(smsId)

    /** Confirma el aviso diciendo, si es un comprobante, con qué movimiento quedó. */
    suspend fun confirmarElAviso(eventoId: String) {
        if (esComprobante) Repositories.wallets.confirmarComprobante(smsId, eventoId)
        else Repositories.wallets.confirmSms(smsId)
    }

    /** «Es este»: el SMS queda confirmado sin crear nada, porque el movimiento ya existía. */
    fun esElQueYaEstaba(eventoId: String) {
        if (working) return
        working = true
        error = null
        coroutine.launch {
            intentar { confirmarElAviso(eventoId) }
                .onSuccess {
                    working = false
                    sms = sms?.copy(state = SMS_STATE_CONFIRMED)
                    goBack(Screen.PorRevisar)
                }
                .onFailure { working = false; error = it.toUserMessage() }
        }
    }

    LaunchedEffect(smsId) {
        intentar { Repositories.wallets.getSms(smsId) }.onSuccess { sms = it }
            .onFailure { error = "No pude cargar el SMS" }
        // El otro aviso del mismo pago, para poder decir cuál es. Si no se lee, la línea se dice igual.
        sms?.parecidoA?.let { id -> intentar { Repositories.wallets.getSms(id) }.onSuccess { otroAviso = it } }
        intentar { Repositories.wallets.parseSms(smsId) }
            .onSuccess { parsed = it; selectedCategory = it.category }
            // El server explica por qué (un aviso que no es un movimiento, por ejemplo), y se
            // dice donde se estaba esperando la sugerencia.
            .onFailure { noSePudoLeer = it.toUserMessage() }
        intentar { Repositories.wallets.getAccounts() }.onSuccess { accounts = it }
    }
    // Si ya está anotado, se ofrece antes de crear otro: confirmar siempre creaba uno nuevo. Va en
    // su propio efecto —en paralelo con las demás lecturas y no detrás de ellas— y con reintento:
    // hasta que conteste, anotar está apagado.
    LaunchedEffect(smsId, reintentoDeRevision) {
        coincidencias = null
        noSePudoRevisar = false
        anotarSinRevisar = false
        intentar { Repositories.wallets.getSmsCoincidencias(smsId) }
            .onSuccess { coincidencias = it }
            .onFailure { noSePudoRevisar = true }
    }

    val currentSms = sms

    /**
     * **Para qué se elige la cuenta acá**: este SMS termina siendo un `FinancialEvent` con el tipo
     * que salió del parseo, o sea exactamente lo mismo que anotar un gasto o un ingreso a mano. El
     * criterio, entonces, es el mismo de la hoja de «Agregar» — de un gasto la plata sale (banco,
     * efectivo, tarjeta), a un ingreso entra (banco, efectivo, inversión).
     *
     * Mientras el parseo no llegue vale el del gasto, que es el caso común; cuando llega, todo
     * esto se recalcula solo y con él la cuenta resuelta. Confirmar sigue exigiendo `parsed`, así
     * que ese rato no se puede guardar nada.
     */
    val usoDeCuenta = if (parsed?.type == TransactionType.INCOME) {
        UsoDeCuenta.DESTINO_DE_INGRESO
    } else {
        UsoDeCuenta.ORIGEN_DE_GASTO
    }
    // Ola 15: acá había una cadena que terminaba en `accounts.firstOrNull()` — la primera del
    // abecedario, que en las cuentas del dueño puede ser el «Vehículo 4083». Ahora las candidatas
    // salen del criterio de `:core`, y si no hay ninguna no se resuelve nada: el botón queda
    // apagado (ya lo estaba) y la fila «Cuenta» pide que la elija.
    val cuentaDelSms = resolverCuentaDelBanco(
        accounts = accounts,
        uso = usoDeCuenta,
        banco = currentSms?.bank.orEmpty(),
        elegidaAMano = cuentaElegida,
        // El SMS entero: adentro está el número de cuenta que el banco escribió, y ese es el único
        // dato duro de toda esta pantalla sobre a qué cuenta va el movimiento.
        textoDelMensaje = currentSms?.text.orEmpty(),
    )
    val resolvedAccount = cuentaDelSms.cuenta

    /**
     * **Las categorías que él usa, no una lista escrita a mano.**
     *
     * Acá había ocho nombres fijos —«Restaurantes», «Mercado», «Suscripción», «Hogar»— de los
     * cuales el dueño no usa ninguno: sus movimientos están en «Comida», «Fútbol», «Hija»,
     * «Gardenera». Para poner una de esas tenía que salir de esta pantalla. Ahora salen de la misma
     * regla que el resto de la app ([categoriasParaPastillas]: la propuesta de Movi primero, luego
     * las que él más usa, luego el resto de las suyas).
     *
     * Ola L: el orden ya no es alfabético —con `take(10)`, «Hija» y «Fútbol» quedaban fuera del
     * corte— y lo que no cabe en la fila está en «Cambiar» (el selector completo, que también crea).
     */
    val categoryOptions: List<String> = categoriasParaElegirEnElSms(
        propuesta = parsed?.category,
        elegida = selectedCategory,
        tipo = parsed?.type,
        usadas = UsedCategoriesCache.used,
        prefs = UsedCategoriesCache.prefs,
        usos = UsedCategoriesCache.usosRecientes,
        cuantas = CATEGORIAS_EN_LAS_PASTILLAS_DEL_SMS,
    )

    /**
     * El comercio que se guarda: el que dejó escrito el dueño, o el leído si lo dejó vacío. Monto y
     * fecha no se editan acá — los dice el banco.
     */
    val comercio: String? = parsed?.let { p -> comercioEditado?.trim()?.takeIf { it.isNotEmpty() } ?: p.merchant }

    /**
     * **A quién fue (o de quién vino) la plata**, según lo leyó el server ([ParsedSms.identificador]).
     * Si ninguna cuenta guardada lo conoce —y no es una cuenta suya— se ofrece guardarla acá mismo
     * («¿De quién es la cuenta ·0756?»), en vez de mandarlo a Ajustes a copiar el número a mano.
     * Los destinos se leen solo cuando el mensaje trae un identificador.
     */
    val identificador = parsed?.identificador()
    val destinosParaGuardar = rememberDestinosParaGuardar(hacenFalta = identificador != null)

    // Ola Y: ¿corresponde ofrecer «¿A cuál crédito o tarjeta corresponde?» para lo que se va a
    // confirmar? Un SMS recién confirmado nunca es ya la mitad de un traspaso, así que las tres
    // puertas de `ofreceVincularDeuda` se reducen acá a la categoría y el tipo.
    val ofreceVinculo = parsed != null &&
        ofreceVincularDeuda(parsed!!.type, selectedCategory ?: parsed!!.category, transferId = null)
    LaunchedEffect(ofreceVinculo) {
        if (!ofreceVinculo) cuentaDeDeudaElegida = null
    }

    fun confirm() {
        if (working) return
        // La revisión de «¿ya lo anotaste?» tiene que haber contestado. Si falló, el primer toque
        // solo arma «Anotar de todas formas»: anotar sin revisar es una decisión explícita.
        if (coincidencias == null) {
            if (!noSePudoRevisar) return
            if (!anotarSinRevisar) {
                anotarSinRevisar = true
                return
            }
        }
        val cat = selectedCategory ?: parsed?.category ?: return
        val acct = resolvedAccount ?: return
        val p = parsed ?: return
        working = true
        error = null
        // Se lee ACÁ, antes del `coroutine.launch`: si el dueño toca dos veces rápido, la segunda
        // pasada no puede ver un estado que la primera ya limpió a mitad de camino.
        val cuentaDeDeuda = cuentaDeDeudaElegida
        coroutine.launch {
            // Si el movimiento se crea y marcar el aviso falla, el aviso sigue pendiente: sin volver
            // a revisar, el siguiente «Confirmar» crearía un segundo movimiento en silencio.
            var movimientoCreado = false
            intentar {
                val event = movimientoConfirmadoDelSms(
                    // Mismo motivo que en QuickAddScreen — ver newId().
                    id = newId("ev"),
                    cuentaId = acct.id,
                    leido = p.copy(merchant = comercio ?: p.merchant),
                    categoria = cat,
                    // Cuando llegó el mensaje, no cuando se confirma: ver [momentoDelSms].
                    momento = momentoDelSms(sms?.time.orEmpty(), ahora = Clock.System.now().toEpochMilliseconds()),
                    textoDelSms = sms?.text.orEmpty(),
                    origen = if (esComprobante) EventSource.OCR else EventSource.SMS,
                )
                Repositories.wallets.postEvent(event)
                movimientoCreado = true
                // Una categoría creada acá tiene que ser conocida en el siguiente aviso: sin esto
                // la bandeja no recarga el caché y «Colegio» se volvía a ofrecer como «Crear».
                UsedCategoriesCache.record(cat, p.type)
                // Ola Y: si eligió una deuda, arma el traspaso completo. Es best-effort a propósito
                // — el movimiento YA quedó guardado como gasto suelto en la línea de arriba, así
                // que si esto falla no se pierde nada: queda exactamente como si no hubiera elegido
                // ninguna cuenta, y se puede completar después desde el editor del movimiento.
                cuentaDeDeuda?.let { deuda ->
                    runCatching {
                        Repositories.wallets.vincularPagoDeDeuda(
                            eventId = event.id,
                            request = VincularPagoDeDeudaRequest(
                                debtAccountId = deuda.id,
                                transferId = newId("tr"),
                                toEventId = newId("ev"),
                            ),
                        )
                    }
                }
                confirmarElAviso(event.id)
            }.onSuccess {
                working = false
                sms = sms?.copy(state = SMS_STATE_CONFIRMED)
                // Ola 2 #1: pop, no push — un segundo tap en ‹ desde la bandeja no debe volver
                // a este detalle ya confirmado (evita duplicar el movimiento).
                goBack(Screen.PorRevisar)
            }.onFailure {
                working = false
                error = it.toUserMessage()
                // Se vuelve a revisar «¿Ya lo anotaste?»: el que se acaba de crear aparece ahí con
                // «Es este», que marca el aviso sin crear otro.
                if (movimientoCreado) reintentoDeRevision++
            }
        }
    }

    // ¿El SMS es de antes de que la cuenta elegida arrancara en Movi? Se recalcula al cambiar de cuenta.
    var antesDeLaCuenta by remember { mutableStateOf<kotlinx.datetime.LocalDate?>(null) }
    LaunchedEffect(resolvedAccount?.id, currentSms?.time) {
        val cuenta = resolvedAccount ?: run { antesDeLaCuenta = null; return@LaunchedEffect }
        val cuando = currentSms?.time ?: return@LaunchedEffect
        antesDeLaCuenta = intentar { Repositories.wallets.getEvents(cuenta.id) }
            .map { inicioDeLaCuentaSiElSmsEsAnterior(momentoDelSms(cuando, ahora = Clock.System.now().toEpochMilliseconds()), it) }
            .getOrNull()
    }

    fun ignore() {
        working = true
        error = null
        coroutine.launch {
            val result = intentar { Repositories.wallets.ignoreSms(smsId) }
            working = false
            result.onSuccess {
                sms = sms?.copy(state = SMS_STATE_IGNORED)
                goBack(Screen.PorRevisar)
            }
                .onFailure { error = it.toUserMessage() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60 · F22: el detalle vuelve a la bandeja si no hay historial (su padre lógico). Ola C:
        // esa bandeja es «Por revisar».
        MinScreenHeader(
            title = "Reconciliar movimiento",
            leading = HeaderLeading.Back(fallback = Screen.PorRevisar),
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            item {
                // Arriba de todo: antes de mirar el resto, que sepa que otro aviso parece este mismo pago.
                if (currentSms?.state == SMS_STATE_PENDING && currentSms.parecidoA != null) {
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        LineaDelMismoPago(otroAviso)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                MinSectionHeader(title = if (esComprobante) "Lo que Movi leyó del papel" else "SMS recibido")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Default,
                    padding = PaddingValues(18.dp),
                ) {
                    if (sms == null) {
                        Text("Cargando…", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(sms!!.bank, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                            StatusDot(Movi.colores.textoApagado, 2.dp)
                            Text(if (esComprobante) "COMPROBANTE" else "SMS", color = Movi.colores.textoMedio, style = Movi.textos.rotulo)
                            StatusDot(Movi.colores.textoApagado, 2.dp)
                            Text(fechaLegibleDeSms(sms!!.time), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Movi.colores.hilo))
                            Text(
                                "\"${sms!!.text}\"",
                                color = Movi.colores.textoMedio,
                                style = Movi.textos.cuerpo,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                antesDeLaCuenta?.let { inicio ->
                    Spacer(Modifier.height(14.dp))
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(18.dp),
                    ) {
                        Text(
                            "Este mensaje es de antes de que empezaras a llevar «${resolvedAccount?.name}» en Movi (desde el ${fechaEnPalabras(inicio, hoyEnAppZone())}).",
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.aviso,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Esa plata ya está dentro del saldo con el que arrancó la cuenta. Si lo confirmas, se cuenta dos veces.",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Ignorar este mensaje",
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.marca,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Movi.colores.marca.copy(alpha = 0.16f))
                                .clickable(enabled = !working) { ignore() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

                val yaAnotados = coincidencias.orEmpty()
                if (yaAnotados.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    MinSectionHeader(title = "¿Ya lo anotaste?")
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Elevated,
                        padding = PaddingValues(18.dp),
                    ) {
                        Text(
                            if (yaAnotados.size == 1) "Encontramos un movimiento igual. Si es este, no se crea otro."
                            else "Encontramos movimientos iguales. Si es uno de estos, no se crea otro.",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            lineHeight = 17.sp,
                        )
                        yaAnotados.forEach { ev ->
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ev.description, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                                    Text(
                                        "${etiquetaDeFecha(fechaDeEpoch(ev.timestamp), hoyEnAppZone())} · ${formatMoney(ev.amount, ev.currency)}",
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoMedio,
                                    )
                                }
                                Text(
                                    "Es este",
                                    style = Movi.textos.cuerpo,
                                    fontWeight = FontWeight.Medium,
                                    color = Movi.colores.marca,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Movi.colores.marca.copy(alpha = 0.16f))
                                        .clickable(enabled = !working) { esElQueYaEstaba(ev.id) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = if (yaAnotados.isNotEmpty()) "O anótalo como nuevo" else "Movi sugiere")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(20.dp),
                ) {
                    if (parsed == null) {
                        Text(
                            text = noSePudoLeer ?: "Leyendo el mensaje…",
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.textoMedio,
                        )
                    } else {
                        val p = parsed!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(comercio ?: p.merchant, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto, letterSpacing = (-0.2).sp)
                                Text(
                                    "${selectedCategory ?: p.category} · ${resolvedAccount?.name ?: "Elige la cuenta"}",
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoMedio,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            val sign = if (p.type == TransactionType.EXPENSE) "−" else "+"
                            val color = if (p.type == TransactionType.EXPENSE) Movi.colores.sale else Movi.colores.entra
                            Text(
                                "$sign${formatMoney(p.amount.roundToLong(), p.currency)}",
                                style = Movi.textos.monto,
                                fontWeight = FontWeight.Medium,
                                color = color,
                                letterSpacing = (-0.4).sp,
                            )
                        }
                    }
                }

                // «¿De quién es la cuenta ·0756? Guardar como…» — solo con un identificador que
                // ninguna cuenta guardada conoce, y solo mientras el aviso espera una decisión.
                val leidoParaGuardar = parsed
                if (identificador != null && leidoParaGuardar != null &&
                    (currentSms == null || currentSms.state == SMS_STATE_PENDING) &&
                    destinosParaGuardar.ofrece(identificador, accounts)
                ) {
                    Spacer(Modifier.height(8.dp))
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Default,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        FilaGuardarElDestino(
                            identificador = identificador,
                            nombreSugerido = nombreParaLaFila(identificador, leidoParaGuardar.merchant),
                            destinos = destinosParaGuardar.guardados.orEmpty(),
                            onGuardado = { guardado ->
                                destinosParaGuardar.alGuardar(guardado)
                                // El movimiento se propone con ese nombre: «Transferencia a Caro»
                                // (o «de Caro», si la plata llegó). Lo que él ya haya escrito en
                                // «Comercio» es suyo y no se pisa.
                                if (comercioEditado.isNullOrBlank()) {
                                    comercioEditado = nombreDelMovimientoConElDestino(guardado, leidoParaGuardar.type)
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Confirma o ajusta")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    val leido = parsed
                    if (leido == null) {
                        Detail(ok = false, label = "Comercio", value = "—")
                    } else {
                        ComercioQueSeEdita(
                            leido = leido.merchant,
                            valor = comercioEditado ?: leido.merchant,
                            enabled = !working && (currentSms == null || currentSms.state == SMS_STATE_PENDING),
                            onCambio = { comercioEditado = it },
                        )
                    }
                    Hairline()
                    Detail(
                        ok = resolvedAccount != null,
                        label = "Cuenta",
                        value = resolvedAccount?.name ?: "Sin cuenta elegida",
                        // Lo que Movi haya adivinado se dice acá, antes de guardar. Un SMS no
                        // trae la cuenta escrita: la pone la app, y eso hay que confesarlo en la
                        // pantalla y no descubrirlo después en Movimientos.
                        hint = avisoDeLaCuentaDelBanco(cuentaDelSms.origen),
                        action = if (eligiendoCuenta) "Cerrar" else "Cambiar",
                        onClick = { eligiendoCuenta = !eligiendoCuenta },
                    )
                    Hairline()
                    Detail(
                        ok = selectedCategory != null,
                        label = "Categoría",
                        value = selectedCategory ?: "—",
                        // De dónde salió la propuesta: «Así lo anotaste 4 veces». Una sugerencia
                        // que se explica se puede rechazar; una que no, solo se obedece. Se calla
                        // en cuanto él elige otra — ya no está explicando lo que se ve.
                        hint = parsed?.aprendidoDe?.takeIf { selectedCategory == parsed?.category },
                        // Ola L: antes esta fila era de solo lectura y las pastillas de abajo eran
                        // el único camino — sin forma de ver todas ni de crear una nueva.
                        action = if (parsed == null) null else if (eligiendoCategoria) "Cerrar" else "Cambiar",
                        onClick = if (parsed == null) null else {
                            {
                                pedidosDeFoco = 0
                                eligiendoCategoria = !eligiendoCategoria
                            }
                        },
                        isLast = true,
                    )
                }

                if (eligiendoCategoria) {
                    // Se dispara una sola vez al abrirse (la clave es el booleano): sin esto, cada
                    // letra tecleada en la búsqueda de adentro recompondría este bloque y saltaría
                    // el scroll una y otra vez debajo del dedo.
                    LaunchedEffect(eligiendoCategoria) {
                        categoriaBringIntoViewRequester.bringIntoView()
                    }
                    Spacer(Modifier.height(8.dp))
                    MinCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(categoriaBringIntoViewRequester),
                        variant = MinCardVariant.Default,
                        padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        SelectorDeCategoria(
                            elegida = selectedCategory ?: "",
                            onElegir = {
                                selectedCategory = it
                                eligiendoCategoria = false
                            },
                            tipo = parsed?.type,
                            usadas = UsedCategoriesCache.used,
                            prefs = UsedCategoriesCache.prefs,
                            usos = UsedCategoriesCache.usosRecientes,
                            pedidosDeFoco = pedidosDeFoco,
                        )
                    }
                }

                // El selector, en el mismo lugar donde antes había un dato de solo lectura. Es lo
                // que hace que el criterio nuevo no sea un callejón: cuando ninguna cuenta califica
                // —o cuando Movi eligió mal— este es el camino hacia adelante.
                if (eligiendoCuenta) {
                    Spacer(Modifier.height(8.dp))
                    MinCard(
                        modifier = Modifier.fillMaxWidth(),
                        variant = MinCardVariant.Default,
                        padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                    ) {
                        ListaDeCuentasElegibles(
                            // `conservar` es lo que sostiene una elección hecha desde el «Ver
                            // todas»: sin él, la cuenta que el dueño acaba de sacar de ahí se
                            // escondería sola al reabrir el selector.
                            cuentas = cuentasPara(accounts, usoDeCuenta, conservar = resolvedAccount?.id),
                            uso = usoDeCuenta,
                            selectedId = resolvedAccount?.id,
                            onPick = { id ->
                                cuentaElegida = id
                                eligiendoCuenta = false
                            },
                        )
                    }
                }

                // Sin la lectura no se sabe de qué lado es el movimiento (gasto o ingreso): las
                // pastillas, «Cambiar» y «+ Nueva» esperan a que llegue.
                if (parsed != null && categoryOptions.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    // **Rueda en horizontal, no se parte ni se corta.** Son hasta doce pastillas, y a
                    // 375 dp —el ancho con el que se usa la PWA desde el teléfono— no entran: en un
                    // `Row` común la última quedaba cortada contra el margen, ilegible y sin forma de
                    // tocarla. Las pastillas son el atajo (la propuesta de Movi, lo que él más usa);
                    // la fila «Categoría» de arriba tiene «Cambiar» para el selector completo y
                    // «+ Nueva», el primero de la fila, para crear una.
                    //
                    // `horizontalScroll` y no un `FlowRow` porque es el patrón que este repo ya
                    // tiene para una fila de pastillas —Movimientos y Categorías hacen exactamente
                    // esto— y una tercera forma de resolver el mismo problema es justo lo que esta
                    // ola vino a sacar del código.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // «+ Nueva», PRIMERA de la fila y no al final: detrás de doce pastillas
                        // quedaba varias pantallas a la derecha y nadie la veía. Es la puerta a
                        // crear una categoría: abre el selector de siempre con el cursor en la
                        // búsqueda, donde «Crear "…"» hace el resto.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .border(1.dp, Movi.colores.marca, RoundedCornerShape(999.dp))
                                .clickable(role = Role.Button) {
                                    pedidosDeFoco += 1
                                    eligiendoCategoria = true
                                }
                                .testTag(TAG_PASTILLA_NUEVA_CATEGORIA)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("+ Nueva", style = Movi.textos.apoyo, color = Movi.colores.marca, fontWeight = FontWeight.Medium)
                        }
                        categoryOptions.forEach { opt ->
                            val on = opt == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (on) Movi.colores.texto else Color.Transparent)
                                    .then(if (!on) Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp)) else Modifier)
                                    .clickable(role = Role.Button) { selectedCategory = opt }
                                    .testTag(tagDePastillaDeCategoriaDelSms(opt))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(opt, style = Movi.textos.apoyo, color = if (on) Movi.colores.fondo else Movi.colores.texto, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // Ola Y: opcional, y solo con esas dos categorías sobre un gasto — ver
                // `ofreceVincularDeuda`. Debajo de las pastillas de categoría, porque depende de
                // cuál quedó elegida.
                if (ofreceVinculo) {
                    Spacer(Modifier.height(14.dp))
                    SelectorDeCuentaDeDeuda(
                        cuentas = accounts,
                        seleccionada = cuentaDeDeudaElegida,
                        onSeleccionar = { cuentaDeDeudaElegida = it },
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
                }

                // Ola 2 #1: red de seguridad — si la pila de navegación rota trae de vuelta a
                // este detalle ya resuelto (confirmado o ignorado), no se puede reconfirmar.
                if (currentSms != null && currentSms.state != SMS_STATE_PENDING) {
                    Spacer(Modifier.height(10.dp))
                    Text("Este mensaje ya se confirmó", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
            }
        }

        val alreadyResolved = currentSms != null && currentSms.state != SMS_STATE_PENDING
        // Junto al botón, que es donde se mira cuando no prende: por qué todavía no, o que la
        // revisión falló y se puede reintentar.
        if (!alreadyResolved) {
            if (coincidencias == null && !noSePudoRevisar) {
                Text(
                    "Revisando si ya está anotado…",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            } else if (noSePudoRevisar) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "No pudimos revisar si ya estaba anotado",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.aviso,
                        modifier = Modifier.weight(1f),
                    )
                    BotonReintentar(onReintentar = { reintentoDeRevision++ })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Movi.colores.borde, RoundedCornerShape(14.dp))
                    .clickable(enabled = !working && !alreadyResolved) { ignore() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Ignorar", style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            }
            // Hasta que la revisión de «¿ya lo anotaste?» conteste, no se anota. Si falló, se puede,
            // pero con un segundo toque que dice lo que hace.
            val revisionContesto = coincidencias != null || noSePudoRevisar
            val canConfirm = parsed != null && resolvedAccount != null && !working && !alreadyResolved && revisionContesto
            Box(
                modifier = Modifier
                    .weight(1.7f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canConfirm) Movi.colores.texto else Movi.colores.tarjeta)
                    .clickable(enabled = canConfirm) { confirm() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        working -> "Guardando…"
                        anotarSinRevisar -> "Anotar de todas formas"
                        else -> "Confirmar"
                    },
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    color = if (canConfirm) Movi.colores.fondo else Movi.colores.textoApagado,
                )
            }
        }
    }
}

/**
 * **La fila «Comercio», editable.** El nombre lo leyó Movi del aviso —«TOSTAO CAFE Y PAN VISC»,
 * o «Movimiento» cuando no supo leerlo— y hasta acá solo se podía cambiar después, desde
 * Movimientos. Lo que quede escrito es el concepto y el comercio del movimiento; vacío vuelve al
 * leído, que se muestra de guía mientras tanto.
 *
 * El campo es el mismo del concepto en la hoja del movimiento ([rememberCampoConSeleccion], con
 * ⌘A en la web), con el tope de la columna.
 */
@Composable
private fun ComercioQueSeEdita(
    leido: String,
    valor: String,
    enabled: Boolean,
    onCambio: (String) -> Unit,
) {
    // Toda la fila lleva al campo, como las demás filas de este resumen llevan a lo suyo.
    val foco = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { foco.requestFocus() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Movi.colores.entra.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.entra, modifier = Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("COMERCIO", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
            val campo = rememberCampoConSeleccion(valor) { onCambio(it.take(MAX_CONCEPTO_LENGTH)) }
            BasicTextField(
                value = campo.valor,
                onValueChange = campo::alCambiar,
                enabled = enabled,
                cursorBrush = SolidColor(Movi.colores.texto),
                textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto, letterSpacing = (-0.1).sp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .focusRequester(foco)
                    .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                decorationBox = { inner ->
                    if (valor.isEmpty()) {
                        Text(leido, style = Movi.textos.cuerpo, color = Movi.colores.textoApagado)
                    }
                    inner()
                },
            )
        }
        Text("Editar", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    }
}

/**
 * Una fila del resumen «Confirma o ajusta».
 *
 * @param hint el renglón chiquito de abajo: por qué ese valor está puesto, cuando lo puso Movi.
 * @param action el texto de la derecha que invita a tocar («Cambiar» / «Cerrar»). Va junto con
 *   [onClick]: una fila que se puede tocar y no lo dice es una fila que nadie toca.
 */
@Composable
private fun Detail(
    ok: Boolean,
    label: String,
    value: String,
    isLast: Boolean = false,
    hint: String? = null,
    action: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tagDeFilaDelSms(label))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (ok) Movi.colores.entra.copy(alpha = 0.16f) else Movi.colores.aviso.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            if (ok) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.entra, modifier = Modifier.size(12.dp))
            } else {
                // Sin estilo a propósito: el «?» hace de ícono (como el ✓ de 12 dp) dentro del círculo de 18 dp.
                Text("?", fontSize = 10.sp, color = Movi.colores.aviso, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(), style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
            Text(value, style = Movi.textos.cuerpo, color = Movi.colores.texto, letterSpacing = (-0.1).sp, modifier = Modifier.padding(top = 2.dp))
            if (hint != null) {
                Text(hint, style = Movi.textos.apoyo, color = Movi.colores.textoApagado, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (action != null) {
            Text(action, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

private fun formatThousands(amount: Long): String {
    val digits = amount.toString()
    val sb = StringBuilder()
    for ((i, c) in digits.reversed().withIndex()) {
        if (i > 0 && i % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.reverse().toString()
}

private fun Modifier.clickableSimple(onClick: () -> Unit) = this.then(
    Modifier.clickable(onClick = onClick),
)

/**
 * El movimiento que nace de **confirmar un SMS en la bandeja**.
 *
 * Nace `RECONCILED`: el dueño leyó el aviso del banco y tocó «Confirmar», así que no vuelve a
 * esperar en «Por confirmar» — que lo dejaba fuera de «Gastos» e «Ingresos» justo después de
 * confirmarlo. El server lo corrige igual (`ORIGENES_YA_REVISADOS` en `EventRoutes`), pero el
 * teléfono lo guarda local antes de subirlo, y sin señal esa fila es la que se ve.
 *
 * El monto va redondeado y no truncado (US$15,44 → 15), y en la moneda que dice el SMS: una
 * compra en dólares no se anota como pesos.
 */
/**
 * **Las pastillas de categoría del detalle de un SMS**: lo que Movi propone primero, y detrás las
 * categorías que el dueño usa de verdad.
 *
 * Ver el KDoc de `categoryOptions` en la pantalla para el porqué. Vive afuera del `@Composable`
 * para poder probarse sin pintar nada.
 */
internal fun categoriasParaElegirEnElSms(
    propuesta: String?,
    tipo: TransactionType?,
    usadas: Map<String, Set<TransactionType>>,
    prefs: Map<String, CategoryPref>,
    usos: Map<String, Int> = emptyMap(),
    cuantas: Int = 10,
    /** Lo que el dueño ya eligió, si eligió: va detrás de la propuesta, que no se pierde. */
    elegida: String? = null,
): List<String> = categoriasParaPastillas(
    primeras = listOf(propuesta, elegida),
    tipo = tipo,
    usadas = usadas,
    prefs = prefs,
    usos = usos,
    cuantas = cuantas,
)

/** Cuántas pastillas rueda la fila del detalle de un SMS: el resto está en «Cambiar». */
internal const val CATEGORIAS_EN_LAS_PASTILLAS_DEL_SMS: Int = 12

/** El `testTag` de la pastilla de una categoría del detalle de un SMS. */
fun tagDePastillaDeCategoriaDelSms(nombre: String): String = "sms:pastilla:$nombre"

/** El `testTag` de una fila del resumen «Confirma o ajusta» que se puede tocar: «Cuenta», «Categoría». */
fun tagDeFilaDelSms(rotulo: String): String = "sms:fila:$rotulo"

/** La pastilla «+ Nueva» del detalle de un SMS. */
const val TAG_PASTILLA_NUEVA_CATEGORIA: String = "sms:pastilla-nueva-categoria"

internal fun movimientoConfirmadoDelSms(
    id: String,
    cuentaId: String,
    leido: ParsedSms,
    categoria: String,
    momento: Long,
    /**
     * **El SMS completo, guardado con el movimiento.**
     *
     * Es el mismo papel que ya cumple `rawPayload` en una fila importada de un extracto (ver
     * `StatementRoutes`): el texto del banco del que salió este movimiento. Hasta acá el camino del
     * SMS lo tiraba, y eso costaba algo concreto: el número de cuenta que el banco escribió es el
     * ÚNICO dato que sobrevive a que el dueño le cambie el nombre al movimiento, y sin él «lo que
     * le mandé a Caro» dejaba de encontrar un envío apenas él lo renombraba a «Mercado» — que es
     * exactamente lo que hizo con los tres que ya tiene. Ver `vaHaciaElDestino` en `:core`.
     *
     * Vacío por defecto no: es obligatorio a propósito. Un default lo habría dejado pasar en
     * silencio en cualquier call site nuevo, y este dato es el que hace auditable una cifra.
     */
    textoDelSms: String,
    /** `OCR` cuando la propuesta salió de un comprobante compartido (Ola 2); `SMS` en lo demás. */
    origen: EventSource = EventSource.SMS,
): FinancialEvent = FinancialEvent(
    id = id,
    accountId = cuentaId,
    type = leido.type,
    amount = leido.amount.roundToLong(),
    currency = leido.currency,
    category = categoria,
    description = leido.merchant,
    merchant = leido.merchant,
    source = origen,
    rawPayload = textoDelSms.ifBlank { null },
    reconciliationStatus = ReconciliationStatus.RECONCILED,
    timestamp = momento,
)
