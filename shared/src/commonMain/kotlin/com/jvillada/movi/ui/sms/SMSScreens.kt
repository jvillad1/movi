package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.nombreParaMostrar
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QuestionMark
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.TonoDeSubtitulo
import com.jvillada.movi.ui.components.ds.AyudaDeCampo
import androidx.compose.material.icons.rounded.Inbox
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
import com.jvillada.movi.shared.model.loApartoMovi
import com.jvillada.movi.shared.model.textoDelApartado
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
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.comoLectura
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.motivoDeApartado
import com.jvillada.movi.shared.model.GrupoDeAvisos
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.avisoDeCaptura
import com.jvillada.movi.shared.model.capturaDeSms
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.ofreceVincularDeuda
import com.jvillada.movi.shared.model.identificador
import com.jvillada.movi.shared.model.textoConElNombreDelTercero
import com.jvillada.movi.ui.destinos.FilaGuardarElDestino
import com.jvillada.movi.ui.destinos.nombreParaLaFila
import com.jvillada.movi.ui.destinos.rememberDestinosParaGuardar
import com.jvillada.movi.ui.transactions.SelectorDeCuentaDeDeuda
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.quickadd.simboloDeMoneda
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
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
    val captura = smsItems?.let { capturaDeSms(soloLoQueLlegoSolo(it).map { sms -> sms.time }) }
    val aviso = captura?.let(::avisoDeCaptura)
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60: encabezado único — se abre desde Ajustes (flecha, F22); «Actualizar» es la acción
        // propia. Ola C: ya no lleva «N por confirmar» de subtítulo — lo pendiente se revisa en
        // «Por revisar», y esta pantalla es el historial.
        MinScreenHeader(
            title = tituloDeCapturaDelBanco,
            leading = HeaderLeading.Back(fallback = Screen.Mas),
            action = { BotonDeIcono(Icons.Rounded.Refresh, "Actualizar", onClick = { refreshKey++ }) },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Medida.margenDePantalla,
                end = Medida.margenDePantalla,
                top = Medida.dentroDeSeccion,
                bottom = Medida.botonFlotante + Medida.entreSecciones,
            ),
        ) {
            item {
                Superficie(
                    modifier = Modifier.fillMaxWidth(),
                    relleno = RellenoDeSuperficie.Lista,
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
                    // Vuelta de diseño 8: una fila y una línea de ayuda; los dos párrafos de antes
                    // (qué llegó y por qué este aparato no lee mensajes) se abren con «¿Qué es
                    // esto?» — se pliegan, no se pierden. Ver `elUltimoMensaje`.
                    captura?.let { c ->
                        val ultimo = elUltimoMensaje(c, hoyEnAppZone(), enAndroid = isAndroid)
                        Fila(
                            titulo = ultimo.titulo,
                            subtitulo = ultimo.subtitulo,
                            tonoDelSubtitulo = if (ultimo.esAlerta) TonoDeSubtitulo.Aviso else TonoDeSubtitulo.Normal,
                            inicio = {
                                IconoEnCirculo(
                                    Icons.Rounded.Inbox,
                                    tono = if (ultimo.esAlerta) TonoDeIcono.Aviso else TonoDeIcono.Marca,
                                )
                            },
                            modifier = Modifier.testTag(TAG_ULTIMO_MENSAJE),
                        )
                        AyudaDeCampo(
                            ayuda = ultimo.ayuda,
                            masAyuda = ultimo.masAyuda,
                            modifier = Modifier.padding(
                                start = Medida.margenDePantalla,
                                end = Medida.margenDePantalla,
                                bottom = Espacio.s,
                            ),
                        )
                    }
                    // Y recién después, lo que depende de dónde estés parado. «Este dispositivo
                    // no puede leer SMS» y «nunca llegó nada» son dos afirmaciones distintas: la
                    // primera es normal en la web y en iOS (y la dice la ayuda de arriba), la
                    // segunda no lo es en ninguna parte.
                    //
                    // En Android solo se nombra lo que FALTA. Que el permiso esté dado no prueba
                    // que la captura corra, así que no se dice nada cuando está dado.
                    if (isAndroid && !rememberSmsCaptureReady()) {
                        Text(
                            "En este teléfono falta el permiso de mensajes: se otorga en la sección de abajo.",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.aviso,
                            modifier = Modifier.padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xxs),
                        )
                    }

                    // El freno al ruido crónico, y vive acá y no en el Inicio a propósito: para
                    // callar el recordatorio hay que estar viendo lo que se calla. Solo aparece
                    // en el caso que genera la alerta — si ya llegó algo, no hay nada que callar.
                    if (aviso?.esAlerta == true) {
                        silenciada?.let { silencioActual ->
                            Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla).padding(bottom = Espacio.s)) {
                                Separador(SangriaDeSeparador.Ninguna)
                                Spacer(Modifier.height(Espacio.s))
                                if (silencioActual) {
                                    Text(
                                        "Este aviso no se muestra en Hoy ni en Por revisar.",
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoMedio,
                                    )
                                    Spacer(Modifier.height(Espacio.xs))
                                }
                                Boton(
                                    if (silencioActual) VOLVER_A_AVISARME else SILENCIAR_ESTE_AVISO,
                                    variante = VarianteDeBoton.Secundario,
                                    tamano = TamanoDeBoton.S,
                                    onClick = {
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
                    Spacer(Modifier.height(Medida.entreSecciones))
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

                Spacer(Modifier.height(Medida.entreSecciones))
                Seccion(titulo = "Historial", conteo = if (smsItems == null) null else mensajes.size)
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
                        // «Era un movimiento»: lo que Movi apartó vuelve a «Por revisar». Se ve
                        // devuelto en el acto y se relee la lista cuando el server contesta.
                        onEraUnMovimiento = {
                            coroutine.launch {
                                intentar { Repositories.wallets.devolverSmsALaBandeja(sms.id) }
                                    .onSuccess {
                                        smsItems = smsItems?.map {
                                            if (it.id == sms.id) it.copy(state = SMS_STATE_PENDING, apartadoPor = null) else it
                                        }
                                        refreshKey++
                                    }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Calla el aviso de «nunca llegó nada» en Hoy y en Por revisar. */
const val SILENCIAR_ESTE_AVISO: String = "Silenciar este aviso"

/** Lo contrario de [SILENCIAR_ESTE_AVISO]. */
const val VOLVER_A_AVISARME: String = "Volver a avisarme"

/** El botón que devuelve a «Por revisar» lo que Movi apartó solo. */
const val ERA_UN_MOVIMIENTO: String = "Era un movimiento"

/** El tag de la línea «Movi lo apartó: …» de una tarjeta del historial. */
const val TAG_MOTIVO_DEL_APARTADO: String = "motivo-del-apartado"

/** El tag de la fila «Último mensaje · …» de Captura del banco / Mensajes del banco. */
const val TAG_ULTIMO_MENSAJE: String = "ultimo-mensaje-del-banco"

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
    Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_AJUSTE_DE_BANCO_MUDO)) {
        Text("Avisarme si un banco se calla", style = Movi.textos.titulo, color = Movi.colores.texto)
        mudos.forEach { mudo ->
            Spacer(Modifier.height(Espacio.xs))
            Text(conPuntoFinalDelAviso(textoDeOrigenMudo(mudo)), style = Movi.textos.cuerpo, color = Movi.colores.aviso)
        }
        // Vuelta de diseño 8: una línea; la explicación entera, detrás de «¿Qué es esto?».
        val (linea, explicacion) = ayudaDelBancoQueSeCalla(dias)
        AyudaDeCampo(ayuda = linea, masAyuda = explicacion)
        Spacer(Modifier.height(Espacio.s))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            BotonDeDias("−", habilitado = dias > 0) { onCambio(dias - 1) }
            Text(
                textoDeDiasDeBancoMudo(dias),
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                modifier = Modifier.widthIn(min = Medida.altoDeFilaDoble),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            BotonDeDias("+", habilitado = dias < MAX_DIAS_PARA_BANCO_MUDO) { onCambio(dias + 1) }
        }
    }
}

/** El texto de la regla termina sin punto («… Revisa la captura»); en una tarjeta va con él. */
private fun conPuntoFinalDelAviso(texto: String): String = if (texto.endsWith(".")) texto else "$texto."

/** «−» y «+» de los días: el [Boton] chico secundario del sistema (su toque llega a 48). */
@Composable
private fun BotonDeDias(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    Boton(texto, onClick = onClick, variante = VarianteDeBoton.Secundario, tamano = TamanoDeBoton.S, habilitado = habilitado)
}

/** «NUNCA HA LLEGADO UN MENSAJE» → «Nunca ha llegado un mensaje»: un rótulo, en frase (ola R4). */
internal fun enFrase(texto: String): String = texto.lowercase().replaceFirstChar { it.uppercase() }.replace("movi", "Movi")

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
 *
 * La negrita es [enfasis] (`Movi.textos.enfasis`, vuelta de diseño 21), que la pantalla pasa: esto
 * es puro y no lee el tema.
 */
internal fun resaltadoDelTextoDelBanco(texto: String, enfasis: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(texto)
    fun negrita(rango: IntRange) = addStyle(enfasis, rango.first, rango.last + 1)

    // El banco/entidad: la palabra o frase antes de los primeros dos puntos, si los hay.
    val dosPuntos = texto.indexOf(':')
    if (dosPuntos > 0 && texto.substring(0, dosPuntos).isNotBlank()) negrita(0 until dosPuntos)

    MONTO_EN_PESOS.findAll(texto).forEach { negrita(it.range) }
    CUENTA_O_TARJETA.findAll(texto).forEach { negrita(it.range) }
}

/** Lo que va debajo del texto del banco en [TarjetaDeMensajeDelBanco], si va algo. */
internal sealed interface BloqueDeAbajo {
    /** «Se anota: Comida · Bancolombia Ahorros · $46.000». */
    data class LoEntendido(val linea: String) : BloqueDeAbajo

    /** El detalle que guardó el server ([SmsMessage.det]). */
    data class Detalle(val texto: String) : BloqueDeAbajo
}

/**
 * **El último bloque de la tarjeta de un aviso, o ninguno.** «Se anota: …» cuando Movi ya entendió
 * el aviso; si no, el detalle del server, pero **solo si dice algo**: un detalle en blanco no se pinta,
 * porque un `Text("")` ocupa un renglón y deja un hueco entre el mensaje y los botones (vuelta de
 * diseño 7). El espacio entre bloques lo pone la tarjeta (`Espacio.xs`), así que sin bloque la
 * distancia al último texto es la misma que con él.
 */
internal fun bloqueDeAbajo(confirmable: Boolean, entendido: String?, det: String): BloqueDeAbajo? = when {
    confirmable && entendido != null -> BloqueDeAbajo.LoEntendido(entendido)
    det.isNotBlank() -> BloqueDeAbajo.Detalle(det)
    else -> null
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
    /**
     * «Era un movimiento», para lo que Movi apartó solo ([loApartoMovi]): el historial de «Captura
     * del banco» lo pasa; la bandeja no, porque ahí nunca llega un apartado.
     */
    onEraUnMovimiento: (() -> Unit)? = null,
    /**
     * **La bandeja que se vacía sola**: lo que Movi va a anotar («Comida · Bancolombia Ahorros ·
     * $46.000») cuando ya entendió el aviso. Con esto y [onConfirmar] la tarjeta ofrece «Confirmar»
     * directo; sin esto el botón principal sigue siendo «Revisar». Solo la bandeja lo pasa.
     */
    entendido: String? = null,
    onConfirmar: (() -> Unit)? = null,
    /** «Ignorar» en la fila: la bandeja lo pasa siempre; el historial no. */
    onIgnorar: (() -> Unit)? = null,
    /** Una acción de esta tarjeta está en curso: los botones se apagan. */
    trabajando: Boolean = false,
    /**
     * Lo que Movi leyó del aviso (la propuesta de la bandeja): el comercio y el monto de la cabeza.
     * El historial de «Captura del banco» no lo tiene; ahí la cabeza sale del texto.
     */
    leido: ParsedSms? = null,
    sinLeer: AvisoSinLeer? = null,
    /**
     * Si un aviso pendiente lleva la píldora «Pendiente». En el historial sí (hay de todo); en la
     * bandeja no: ahí todo está pendiente y la píldora no dice nada.
     */
    conPildoraDePendiente: Boolean = true,
    /** «Ojo: el aviso es de … y la cuenta es de …» (ver [advertenciaDeBancos]), o `null`. */
    advertencia: String? = null,
) {
    val apartado = loApartoMovi(sms)
    val pendiente = sms.state == SMS_STATE_PENDING
    val estado = when {
        apartado -> "Apartado" to TonoDePildora.Neutra
        else -> when (sms.state) {
            SMS_STATE_PENDING -> if (conPildoraDePendiente) "Pendiente" to TonoDePildora.Aviso else null
            SMS_STATE_CONFIRMED -> "Confirmado" to TonoDePildora.Exito
            SMS_STATE_IGNORED -> "Ignorado" to TonoDePildora.Neutra
            else -> sms.state.lowercase().replaceFirstChar { it.uppercase() } to TonoDePildora.Neutra
        }
    }
    val confirmable = pendiente && entendido != null && onConfirmar != null
    // Vuelta de diseño 2: la cabeza se lee primero —el comercio (o lo que Movi entendió) con su
    // marca, el monto a la derecha y «banco · hora» debajo—; el texto del banco va después, apagado.
    // Sin comercio, el banco y su marca (o el ícono de los avisos). Ver [cabezaDelAviso].
    val cabeza = remember(sms, leido, sinLeer) { cabezaDelAviso(listOf(sms), leido, hoyEnAppZone(), sinLeer) }
    TarjetaDeAviso(
        titulo = cabeza.titulo,
        subtitulo = cabeza.subtitulo,
        modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
        inicio = { AvatarDeLaCabeza(cabeza, pideDecision = pendiente) },
        cifra = cabeza.monto?.let { { CifraDeLaCabeza(cabeza) } },
        estado = estado,
        pideDecision = pendiente,
        acciones = when {
            // La bandeja que se vacía sola: «Confirmar» solo cuando Movi ya tiene todo (ver
            // `loQueMoviEntendio`).
            confirmable -> {
                {
                    PastillaDelPago(
                        CONFIRMAR_EN_LA_FILA, principal = true, habilitada = !trabajando, onClick = onConfirmar!!,
                        modifier = Modifier.testTag(TAG_CONFIRMAR_EN_LA_FILA),
                    )
                    PastillaDelPago("Revisar", habilitada = !trabajando, onClick = onRevisar)
                    onIgnorar?.let { PastillaDelPago(IGNORAR_EN_LA_FILA, habilitada = !trabajando, onClick = it) }
                }
            }
            // Con dudas, «Revisar» es el principal; «Ignorar» va siempre en la bandeja.
            pendiente -> {
                {
                    PastillaDelPago("Revisar", principal = true, habilitada = !trabajando, onClick = onRevisar)
                    onIgnorar?.let { PastillaDelPago(IGNORAR_EN_LA_FILA, habilitada = !trabajando, onClick = it) }
                }
            }
            apartado && onEraUnMovimiento != null -> {
                { PastillaDelPago(ERA_UN_MOVIMIENTO, habilitada = true, onClick = onEraUnMovimiento) }
            }
            else -> null
        },
        pie = pie,
    ) {
        // Antes de aprobar, que se sepa que otro aviso parece el mismo pago: dos avisos de un pago
        // aprobados por separado son dos movimientos.
        if (pendiente && sms.parecidoA != null) LineaDelMismoPago(parecidoA)
        // La cuenta que propone Movi es de otro banco que el del aviso: se dice antes de confirmar.
        if (pendiente) advertencia?.let { LineaDeAdvertenciaDeBancos(it) }
        // Por qué no está en «Por revisar»: lo apartó Movi, no él.
        textoDelApartado(sms)?.let { porQue ->
            Text(
                porQue,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.testTag(TAG_MOTIVO_DEL_APARTADO),
            )
        }
        LoQueDiceElBanco(sms.text, plegable = true)
        // Vuelta de diseño 7: un bloque que falta no deja su lugar. El teléfono, el correo y los
        // comprobantes suben `det` vacío, y ese `Text("")` era un renglón en blanco más su espacio:
        // ~30 px de hueco entre el mensaje y los botones del aviso de Rappi. Ver [bloqueDeAbajo].
        when (val abajo = bloqueDeAbajo(confirmable, entendido, sms.det)) {
            is BloqueDeAbajo.LoEntendido -> LineaDeLoEntendido(abajo.linea)
            is BloqueDeAbajo.Detalle -> Text(abajo.texto, style = Movi.textos.cuerpo, color = Movi.colores.texto)
            null -> Unit
        }
    }
}

/** El tag de la tarjeta con los avisos del mismo pago en Reconciliar. */
const val TAG_AVISOS_DEL_PAGO_EN_RECONCILIAR: String = "avisos-del-pago-en-reconciliar"

@Composable
fun SMSReconcileScreen(
    onNavigate: (Screen) -> Unit,
    smsId: String,
    /** Ola 5 · «Otra categoría» desde la notificación: el selector se abre solo cuando llega la lectura. */
    abrirCategoria: Boolean = false,
) {
    val goBack = LocalGoBack.current
    // Confirmar el aviso vibra (ver [Vibrador]): quedó anotado. Ignorarlo no.
    val vibrador = rememberVibrador()
    val coroutine = rememberCoroutineScope()
    var sms by remember { mutableStateOf<SmsMessage?>(null) }
    var parsed by remember { mutableStateOf<ParsedSms?>(null) }
    /**
     * Por qué Movi no pudo leer este mensaje, cuando no pudo. Sin esto, un parseo fallido dejaba
     * «Parseando…» para siempre en la tarjeta de la sugerencia y el motivo abajo, lejos, como si
     * todavía estuviera trabajando.
     */
    var noSePudoLeer by remember { mutableStateOf<String?>(null) }
    /**
     * «Anotarlo igual»: lo que trae un aviso que Movi no entendió (el monto, la moneda, la fecha), si
     * el server lo ofrece. Ver `AnotarloIgualEnReconciliar.kt`.
     */
    var sinLeer by remember { mutableStateOf<AvisoSinLeer?>(null) }
    /** El dueño tocó «Anotarlo igual»: [parsed] es la lectura armada con [sinLeer], y se edita. */
    var anotandoIgual by remember { mutableStateOf(false) }
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
    // Ola 5: se abre una sola vez, cuando la lectura llegó (antes la fila «Categoría» no tiene con qué).
    var yaSeAbrioLaCategoria by remember { mutableStateOf(false) }
    LaunchedEffect(parsed != null) {
        if (abrirCategoria && parsed != null && !yaSeAbrioLaCategoria) {
            yaSeAbrioLaCategoria = true
            eligiendoCategoria = true
        }
    }
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
     * Arreglo 1 de la ingesta: la cuenta suya a la que fue la plata en un traspaso, o a la que entró
     * un avance, elegida con el dedo. `null` = la que propone Movi ([destinoPropuestoDelAviso]).
     */
    var destinoElegidoId by remember { mutableStateOf<String?>(null) }
    var eligiendoDestino by remember { mutableStateOf(false) }
    /**
     * El traspaso que entra: la cuenta suya de la que vino la plata, elegida con el dedo. `null` = la
     * que propone Movi ([origenPropuestoDelAviso]). Comparte con el destino el selector de la tarjeta.
     */
    var origenElegidoId by remember { mutableStateOf<String?>(null) }
    /**
     * Pagar una tarjeta en otra moneda: lo que el dueño escribió en «¿Cuánto bajó la deuda en USD?».
     * `null` = vacío, y el server convierte con la tasa del día (sin ella contesta 422 y lo pide).
     */
    var montoEnLaDeuda by remember { mutableStateOf<Long?>(null) }

    /**
     * Ola 2: la propuesta salió de un comprobante que el dueño compartió (ver `Papeles.kt` en
     * :core). Se revisa igual que un SMS; cambia el rótulo, el origen del movimiento (`OCR`) y que
     * al confirmar se le dice al server con qué movimiento, para colgar el papel de su cuenta.
     */
    val esComprobante = esIdDeComprobante(smsId)

    /**
     * «Un pago, una tarjeta» (4-oct-2026): los avisos del mismo pago, si este es uno de varios. La
     * propuesta sale del que más dice ([GrupoDeAvisos.propuestaDe]), confirmar crea UN movimiento y
     * cierra todos, e ignorar los ignora a todos. `null` es un aviso suelto: el camino de siempre.
     */
    var pago by remember { mutableStateOf<GrupoDeAvisos?>(null) }
    /** Sube cuando el dueño saca un aviso del pago («Este es otro pago»): se vuelve a leer todo. */
    var relecturaDelPago by remember { mutableStateOf(0) }
    val pagoActual = pago?.takeIf { it.miembros.size > 1 }
    val idsDelPago = pagoActual?.miembros?.map { it.id }.orEmpty()
    /** El aviso del pago que ya se confirmó: el pago ya tiene su movimiento y solo falta cerrar los demás. */
    val yaAnotadoCon = pagoActual?.yaAnotadoCon?.let { id -> pagoActual.miembros.firstOrNull { it.id == id } }
    /** De qué aviso sale la propuesta: el que más dice del pago, o este mismo. */
    val idDeLaPropuesta = pagoActual?.propuestaDe ?: smsId
    val avisoDeLaPropuesta = pagoActual?.miembros?.firstOrNull { it.id == idDeLaPropuesta }

    /**
     * Confirma el aviso diciendo con qué movimiento quedó: el server lo guarda en el aviso, y si es
     * un comprobante además cuelga el papel de la cuenta de ese movimiento.
     */
    suspend fun confirmarElAviso(eventoId: String) {
        val elPago = pagoActual
        when {
            elPago != null -> Repositories.wallets.confirmarElMismoPago(
                elPago.grupoId, ConfirmarElMismoPago(idsDelPago, eventoExistenteId = eventoId),
            )
            esComprobante -> Repositories.wallets.confirmarComprobante(smsId, eventoId)
            else -> Repositories.wallets.confirmSmsCon(smsId, eventoId)
        }
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
                    vibrador.confirmar()
                    goBack(Screen.PorRevisar)
                }
                .onFailure { working = false; error = it.toUserMessage() }
        }
    }

    LaunchedEffect(smsId, relecturaDelPago) {
        intentar { Repositories.wallets.getSms(smsId) }.onSuccess { sms = it }
            .onFailure { error = "No pude cargar el SMS" }
        // Los avisos del mismo pago. Si no se pueden leer, se revisa este solo, como siempre.
        pago = sms?.grupoId?.let { grupoId -> intentar { Repositories.wallets.getAvisosDelMismoPago(grupoId) }.getOrNull() }
        // El otro aviso del mismo pago, para poder decir cuál es. Si no se lee, la línea se dice igual.
        if (pago == null) {
            sms?.parecidoA?.let { id -> intentar { Repositories.wallets.getSms(id) }.onSuccess { otroAviso = it } }
        }
        intentar { Repositories.wallets.leerParaReconciliar(pago?.takeIf { it.miembros.size > 1 }?.propuestaDe ?: smsId) }
            .onSuccess { lectura ->
                val leido = lectura.leido
                if (leido != null) {
                    parsed = leido
                    if (selectedCategory == null) selectedCategory = leido.category
                } else {
                    // No se entendió: el motivo, y si trae un monto, «Anotarlo igual».
                    noSePudoLeer = lectura.noSePudoLeer
                    // Solo mientras el aviso espera una decisión: lo apartado, ignorado o confirmado no.
                    sinLeer = lectura.sinLeer?.takeIf { sms?.state == SMS_STATE_PENDING }
                }
            }
            // El server explica por qué (un aviso que no es un movimiento, por ejemplo), y se
            // dice donde se estaba esperando la sugerencia.
            .onFailure { noSePudoLeer = it.toUserMessage() }
        intentar { Repositories.wallets.getAccounts() }.onSuccess { accounts = it }
    }
    // Si ya está anotado, se ofrece antes de crear otro: confirmar siempre creaba uno nuevo. Va en
    // su propio efecto —en paralelo con las demás lecturas y no detrás de ellas— y con reintento:
    // hasta que conteste, anotar está apagado.
    LaunchedEffect(idDeLaPropuesta, reintentoDeRevision) {
        coincidencias = null
        noSePudoRevisar = false
        anotarSinRevisar = false
        intentar { Repositories.wallets.getSmsCoincidencias(idDeLaPropuesta) }
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
    val cuentaDelSms = conLaCuentaQueSugiereMovi(
        resuelta = if (pagoActual != null) {
            // Un pago avisado varias veces: la mejor lectura de todos sus avisos (el número del SMS
            // aunque el comercio salga de Google Wallet). Ver [resolverCuentaDelPago].
            resolverCuentaDelPago(accounts, usoDeCuenta, pagoActual.miembros, cuentaElegida)
        } else resolverCuentaDelBanco(
            accounts = accounts,
            uso = usoDeCuenta,
            banco = currentSms?.bank.orEmpty(),
            elegidaAMano = cuentaElegida,
            // El SMS entero: adentro está el número de cuenta que el banco escribió, y ese es el único
            // dato duro de toda esta pantalla sobre a qué cuenta va el movimiento.
            textoDelMensaje = currentSms?.text.orEmpty(),
        ),
        // El correo de PSE no dice la cuenta: si el texto no la nombra, vale la que sabe Movi
        // (pagos anteriores a esa deuda, la regla recurrente, la última vez). Ver `ElPagoPorPse.kt`.
        accounts = accounts,
        sugeridaId = parsed?.cuentaSugeridaId,
    )
    val resolvedAccount = cuentaDelSms.cuenta
    // Vuelta de diseño 23: la cuenta que Movi propone es de otro banco que el del aviso. Solo se avisa;
    // la cuenta no cambia. Si la eligió el dueño, ya decidió.
    val advertenciaDeLaCuenta = if (cuentaDelSms.origen == OrigenDeLaCuentaDelBanco.A_MANO) null else advertenciaDeBancos(
        pagoActual?.miembros?.map { it.bank } ?: listOfNotNull(currentSms?.bank),
        resolvedAccount,
    )

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
    // La deuda que se paga queda elegida, en este orden: la que el mensaje NOMBRA por su número
    // («Pagaste $X en la tarjeta de credito *9208 desde la cuenta *8133», ver
    // [deudaQueNombraElMensaje]), y si no nombra ninguna, la que Movi reconoció en el aviso (el correo
    // de PSE: Banco de Occidente ↔ «Vehículo 8761», [ParsedSms.deudaSugeridaId]). Confirmar arma el
    // traspaso de dos patas que marca la cuota del período. Solo hasta que él toque el selector: si
    // la quita o elige otra, Movi no se la vuelve a poner.
    val deudaPropuesta = deudaPropuestaDelAviso(cuentaDelSms, parsed?.deudaSugeridaId, accounts)
    var deudaTocadaAMano by remember { mutableStateOf(false) }
    LaunchedEffect(ofreceVinculo, deudaPropuesta?.id) {
        if (ofreceVinculo && !deudaTocadaAMano && cuentaDeDeudaElegida == null && deudaPropuesta != null) {
            cuentaDeDeudaElegida = deudaPropuesta
        }
    }
    // ── Las dos patas (arreglo 1 de la auditoría de la ingesta) ─────────────────────────────────
    // Un aviso con destino propio —la tarjeta o el crédito que se paga, la cuenta a la que fue un
    // traspaso o a la que entró un avance— se confirma con sus dos patas, y la tarjeta de resumen lo
    // dice antes: «Sale de … · entra a …». Ver `LasDosPatasEnReconciliar.kt`.
    val categoriaActual = selectedCategory ?: parsed?.category
    val destinoNombrado = destinoPropuestoDelAviso(parsed, cuentaDelSms, accounts)
    // «Retiraste … de tu cuenta *9586 Fiducuenta hacia la cuenta *02955068133»: si el destino es una
    // cuenta suya, es un traspaso. Se propone una vez; si él elige otra categoría, manda la suya.
    LaunchedEffect(parsed, destinoNombrado?.id) {
        val p = parsed ?: return@LaunchedEffect
        if (p.type == TransactionType.EXPENSE && destinoNombrado != null && selectedCategory == p.category &&
            p.category != CARD_PAYMENT_CATEGORY && p.category != CUOTA_CATEGORY
        ) {
            selectedCategory = TRANSFER_CATEGORY
        }
    }
    // «Recibiste $X de tu cuenta *9586 en tu cuenta *8133»: si el origen es una cuenta suya, es un
    // traspaso que entra. Se propone una vez, igual que el de arriba; si él elige otra categoría, manda la suya.
    val origenNombrado = origenPropuestoDelAviso(parsed, cuentaDelSms, accounts)
    LaunchedEffect(parsed, origenNombrado?.id) {
        val p = parsed ?: return@LaunchedEffect
        if (p.type == TransactionType.INCOME && origenNombrado != null && selectedCategory == p.category) {
            selectedCategory = TRANSFER_CATEGORY
        }
    }
    val pideDestino = pideLaCuentaDeDestino(parsed, categoriaActual, resolvedAccount)
    val destinoDeLasPatas = if (!pideDestino) null else {
        destinoElegidoId?.let { id -> accounts.firstOrNull { it.id == id } } ?: destinoNombrado
    }
    val pideOrigen = pideLaCuentaDeOrigen(parsed, categoriaActual, resolvedAccount)
    val origenDeLasPatas = if (!pideOrigen) null else {
        origenElegidoId?.let { id -> accounts.firstOrNull { it.id == id } } ?: origenNombrado
    }
    val lasDosPatas = dosPatasPropuestas(
        parsed, categoriaActual, resolvedAccount, cuentaDeDeudaElegida, destinoDeLasPatas, origenElegido = origenDeLasPatas,
    )
    /**
     * Un traspaso o un avance sin la cuenta a la que entró —o un traspaso que entra sin la cuenta de la
     * que vino— no se confirma: el avance bajaría la deuda de la tarjeta, y un «Traspaso» suelto no existe.
     */
    val faltaElDestino = (pideDestino || pideOrigen) && lasDosPatas == null

    fun confirm() {
        if (working) return
        // Ya confirmado (un segundo toque que llegó tarde): nada más que hacer.
        if (sms?.state.let { it != null && it != SMS_STATE_PENDING }) return
        // La revisión de «¿ya lo anotaste?» tiene que haber contestado. Si falló, el primer toque
        // solo arma «Anotar de todas formas»: anotar sin revisar es una decisión explícita.
        if (coincidencias == null) {
            if (!noSePudoRevisar) return
            if (!anotarSinRevisar) {
                anotarSinRevisar = true
                return
            }
        }
        val cat = (selectedCategory ?: parsed?.category)?.takeIf { it.isNotBlank() } ?: return
        val acct = resolvedAccount ?: return
        val p = parsed ?: return
        // Un aviso que se anota igual: sin monto no hay nada que anotar.
        if (p.amount <= 0.0) return
        // Se lee ACÁ, antes del `coroutine.launch`: si el dueño toca dos veces rápido, la segunda
        // pasada no puede ver un estado que la primera ya limpió a mitad de camino.
        val dosPatas = lasDosPatas
        val enLaDeuda = montoEnLaDeuda
        // Un traspaso o un avance sin la cuenta a la que entró: el botón ya está apagado.
        if (faltaElDestino) return
        working = true
        error = null
        coroutine.launch {
            // Si el movimiento se crea y marcar el aviso falla, el aviso sigue pendiente: sin volver
            // a revisar, el siguiente «Confirmar» crearía un segundo movimiento en silencio.
            var movimientoCreado = false
            intentar {
                // Cuando llegó el mensaje, no cuando se confirma: ver [momentoDelMovimientoDelAviso].
                val momento = momentoDelMovimientoDelAviso(sms, pagoActual, p, ahora = Clock.System.now().toEpochMilliseconds())
                if (dosPatas != null) {
                    // Las dos patas en una transacción del server, con este aviso (o todos los del
                    // pago) enlazados a la del dinero. Un doble toque no crea otras: el server ve el
                    // aviso ya anotado y contesta con las mismas.
                    val pedido = pedidoDeDosPatas(dosPatas, p, momento, montoEnLaMonedaDeLaDeuda = enLaDeuda, nuevoId = ::newId)
                    val elPago = pagoActual
                    if (elPago != null) {
                        Repositories.wallets.confirmarElMismoPago(elPago.grupoId, ConfirmarElMismoPago(idsDelPago, patas = pedido))
                    } else {
                        Repositories.wallets.confirmarConLasDosPatas(smsId, pedido)
                    }
                    return@intentar
                }
                // Un movimiento suelto: el MISMO camino que la fila y el lote de «Por revisar» (ver
                // `AnotarElAviso.kt`). Con una deuda elegida, las dos patas ya salieron arriba.
                val event = movimientoDelAviso(
                    leido = p,
                    cuentaId = acct.id,
                    categoria = cat,
                    comercio = comercio,
                    momento = momento,
                    textoDelAviso = (avisoDeLaPropuesta ?: sms)?.text.orEmpty(),
                    esComprobante = esComprobante,
                )
                anotarElMovimientoDelAviso(smsId, pagoActual, event, esComprobante, alCrearElMovimiento = { movimientoCreado = true })
            }.onSuccess {
                working = false
                sms = sms?.copy(state = SMS_STATE_CONFIRMED)
                vibrador.confirmar()
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

    // ── Repartir un envío entre varios pagos fijos (ver `RepartirEnReconciliar.kt`) ──────────────
    // Solo para lo que salió: el server dice qué pagos del período puede saldar y si la suma es exacta.
    var reparto by remember { mutableStateOf<com.jvillada.movi.shared.model.PropuestaDeReparto?>(null) }
    LaunchedEffect(idDeLaPropuesta, parsed?.type) {
        reparto = if (parsed?.type == TransactionType.EXPENSE) {
            intentar { Repositories.wallets.getRepartoDelAviso(idDeLaPropuesta) }.getOrNull()
        } else null
    }

    /** Un movimiento por pago, en una transacción del server: el mismo `…/confirmar` de siempre, con `reparto`. */
    fun repartir(elegidos: List<Pair<com.jvillada.movi.shared.model.PagoParaRepartir, Long>>, recordar: Boolean) {
        if (working) return
        if (sms?.state.let { it != null && it != SMS_STATE_PENDING }) return
        val p = parsed ?: return
        val acct = resolvedAccount ?: return
        val destinoARecordar = reparto?.destinoId?.takeIf { recordar }
        working = true
        error = null
        coroutine.launch {
            intentar {
                val momento = momentoDelMovimientoDelAviso(sms, pagoActual, p, ahora = Clock.System.now().toEpochMilliseconds())
                val partes = partesDelReparto(
                    elegidos = elegidos,
                    cuenta = acct,
                    leido = p,
                    momento = momento,
                    textoDelAviso = (avisoDeLaPropuesta ?: sms)?.text.orEmpty(),
                    esComprobante = esComprobante,
                    nuevoId = ::newId,
                )
                // Un aviso suelto va por la misma ruta con un solo miembro: el server lo bloquea y lo
                // marca igual que a un pago avisado varias veces.
                val elPago = pagoActual
                Repositories.wallets.confirmarElMismoPago(
                    elPago?.grupoId ?: smsId,
                    ConfirmarElMismoPago(
                        miembros = if (elPago != null) idsDelPago else listOf(smsId),
                        reparto = partes,
                        recordarDestinoId = destinoARecordar,
                    ),
                )
            }.onSuccess {
                working = false
                sms = sms?.copy(state = SMS_STATE_CONFIRMED)
                vibrador.confirmar()
                goBack(Screen.PorRevisar)
            }.onFailure {
                working = false
                error = it.toUserMessage()
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
            val elPago = pagoActual
            val result = intentar {
                if (elPago != null) Repositories.wallets.ignorarElMismoPago(elPago.grupoId, idsDelPago)
                else Repositories.wallets.ignoreSms(smsId)
            }
            working = false
            result.onSuccess {
                sms = sms?.copy(state = SMS_STATE_IGNORED)
                goBack(Screen.PorRevisar)
            }
                .onFailure { error = it.toUserMessage() }
        }
    }

    /** «Cerrar»: el pago ya tiene su movimiento (otro aviso se confirmó antes); se cierran los demás sin crear nada. */
    fun cerrarElPago() {
        val elPago = pagoActual ?: return
        if (working) return
        working = true
        error = null
        coroutine.launch {
            intentar { Repositories.wallets.confirmarElMismoPago(elPago.grupoId, ConfirmarElMismoPago(idsDelPago)) }
                .onSuccess {
                    working = false
                    sms = sms?.copy(state = SMS_STATE_CONFIRMED)
                    vibrador.confirmar()
                    goBack(Screen.PorRevisar)
                }
                .onFailure { working = false; error = it.toUserMessage() }
        }
    }

    /** «Este es otro pago»: ese aviso sale del pago para siempre, y se vuelve a leer lo que queda. */
    fun esOtroPago(id: String) {
        if (working) return
        working = true
        error = null
        coroutine.launch {
            intentar { Repositories.wallets.esteEsOtroPago(id) }
                .onSuccess { working = false; relecturaDelPago++ }
                .onFailure { working = false; error = it.toUserMessage() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60 · F22: el detalle vuelve a la bandeja si no hay historial (su padre lógico). Ola C:
        // esa bandeja es «Por revisar».
        MinScreenHeader(
            title = "Revisar aviso",
            leading = HeaderLeading.Back(fallback = Screen.PorRevisar),
        )

        // Ola R4: de lo más importante a lo menos —el aviso, lo que hay que saber antes (de antes de
        // la cuenta, ya anotado), lo que Movi propone y los datos para ajustar—, cada bloque con su
        // [Seccion] y [Medida.entreSecciones] entre uno y otro.
        val pendiente = currentSms == null || currentSms.state == SMS_STATE_PENDING
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Medida.margenDePantalla,
                end = Medida.margenDePantalla,
                top = Medida.dentroDeSeccion,
                bottom = Medida.margenDePantalla,
            ),
            verticalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
        ) {
            // ── El aviso, con la misma tarjeta que en «Por revisar» ──────────────────────────
            item(key = "aviso") {
                if (pagoActual != null) {
                    // «Un pago, una tarjeta»: los avisos del mismo pago, cada uno con «Este es otro pago».
                    TarjetaDeAviso(
                        titulo = "El mismo pago, avisado ${pagoActual.miembros.size} veces",
                        subtitulo = canalesDelMismoPago(pagoActual.miembros),
                        modifier = Modifier.testTag(TAG_AVISOS_DEL_PAGO_EN_RECONCILIAR),
                        pideDecision = pendiente,
                    ) {
                        if (yaAnotadoCon != null) {
                            Text(avisoYaAnotado(yaAnotadoCon), style = Movi.textos.cuerpo, color = Movi.colores.aviso)
                        }
                        pagoActual.miembros.forEachIndexed { i, aviso ->
                            if (i > 0) Separador(SangriaDeSeparador.Ninguna)
                            TextoDelAviso(aviso) {
                                // El confirmado ya es el movimiento: no se saca de acá.
                                if (aviso.state == SMS_STATE_PENDING) {
                                    Boton(
                                        texto = ESTE_ES_OTRO_PAGO,
                                        onClick = { esOtroPago(aviso.id) },
                                        variante = VarianteDeBoton.Fantasma,
                                        tamano = TamanoDeBoton.S,
                                        habilitado = !working,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val elSms = sms
                    TarjetaDeAviso(
                        // El origen en su renglón: «Comprobante · transferencia.png» (Ola 2) no entraba
                        // al lado del tipo y la fecha.
                        titulo = elSms?.bank ?: if (esComprobante) "Lo que Movi leyó del papel" else "Aviso del banco",
                        subtitulo = elSms?.let { "${if (esComprobante) "Comprobante" else "SMS"} · ${fechaLegibleDeSms(it.time)}" },
                        pideDecision = pendiente,
                    ) {
                        if (elSms == null) {
                            Text("Cargando…", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                        } else {
                            // Antes de mirar el resto, que sepa que otro aviso parece este mismo pago.
                            if (elSms.state == SMS_STATE_PENDING && elSms.parecidoA != null) LineaDelMismoPago(otroAviso)
                            LoQueDiceElBanco(elSms.text)
                        }
                    }
                }
            }

            // ── De antes de que la cuenta arrancara en Movi: confirmarlo lo contaría dos veces ──
            antesDeLaCuenta?.let { inicio ->
                item(key = "antes-de-la-cuenta") {
                    TarjetaDePropuesta(
                        icono = Icons.Rounded.History,
                        tono = TonoDeIcono.Aviso,
                        titulo = "Este mensaje es de antes de que empezaras a llevar «${resolvedAccount?.name}» en Movi (desde el ${fechaEnPalabras(inicio, hoyEnAppZone())}).",
                        cuerpo = "Esa plata ya está dentro del saldo con el que arrancó la cuenta. Si lo confirmas, se cuenta dos veces.",
                        accion = "Ignorar este mensaje",
                        onAccion = { if (!working) ignore() },
                    )
                }
            }

            // ── ¿Ya lo anotaste? ───────────────────────────────────────────────────────────
            val yaAnotados = coincidencias.orEmpty()
            if (yaAnotados.isNotEmpty()) {
                item(key = "ya-anotado") {
                    Column {
                        Seccion(titulo = "¿Ya lo anotaste?")
                        Text(
                            if (yaAnotados.size == 1) "Encontramos un movimiento igual. Si es este, no se crea otro."
                            else "Encontramos movimientos iguales. Si es uno de estos, no se crea otro.",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.padding(bottom = Medida.dentroDeSeccion),
                        )
                        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                            yaAnotados.forEachIndexed { i, ev ->
                                if (i > 0) Separador(SangriaDeSeparador.Margen)
                                Fila(
                                    titulo = ev.description,
                                    subtitulo = "${etiquetaDeFecha(fechaDeEpoch(ev.timestamp), hoyEnAppZone())} · ${formatMoney(ev.amount, ev.currency)}",
                                    final = {
                                        Boton(
                                            texto = "Es este",
                                            onClick = { esElQueYaEstaba(ev.id) },
                                            variante = VarianteDeBoton.Secundario,
                                            tamano = TamanoDeBoton.S,
                                            habilitado = !working,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // «Son 2 pagos: Hipoteca Papá $2.228.152 + Libranza Papá $2.051.848», o repartir a mano.
            val elReparto = reparto
            if (elReparto != null && yaAnotadoCon == null && pendiente) {
                item(key = "reparto") {
                    TarjetaDelReparto(
                        propuesta = elReparto,
                        habilitado = !working && resolvedAccount != null,
                        onRepartir = { elegidos, recordar -> repartir(elegidos, recordar) },
                    )
                }
            }

            // ── Lo que Movi propone anotar ───────────────────────────────────────────────────
            item(key = "sugiere") {
                Column {
                    Seccion(titulo = if (yaAnotados.isNotEmpty()) "O anótalo como nuevo" else "Movi sugiere")
                    Superficie(modifier = Modifier.fillMaxWidth(), nivel = NivelDeSuperficie.Propuesta) {
                        val loQueTrae = sinLeer
                        if (parsed == null && loQueTrae != null) {
                            Text(text = textoDeAnotarloIgual(loQueTrae), style = Movi.textos.cuerpo, color = Movi.colores.texto)
                        } else if (parsed == null) {
                            Text(text = noSePudoLeer ?: "Leyendo el mensaje…", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                        } else {
                            val p = parsed!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(nombreParaMostrar(comercio ?: p.merchant), style = Movi.textos.titulo, color = Movi.colores.texto)
                                    Text(
                                        "${(selectedCategory ?: p.category).ifBlank { "Elige la categoría" }} · ${resolvedAccount?.name ?: "Elige la cuenta"}",
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoMedio,
                                    )
                                }
                                // Ola 2a: el gasto en el color del texto; el ingreso en verde y con «+».
                                Cifra(
                                    monto = if (p.type == TransactionType.EXPENSE) -p.amount.roundToLong() else p.amount.roundToLong(),
                                    tono = if (p.type == TransactionType.EXPENSE) TonoDeCifra.Neutro else TonoDeCifra.Ingreso,
                                    moneda = p.currency,
                                )
                            }
                            // Arreglo 1 de la ingesta: lo que se va a crear, antes de confirmar.
                            LasDosPatasEnElResumen(
                                propuestas = lasDosPatas,
                                pideDestino = pideDestino || pideOrigen,
                                destino = if (pideOrigen) origenDeLasPatas else destinoDeLasPatas,
                                esAvance = esUnAvanceDeLaTarjeta(p, categoriaActual, resolvedAccount),
                                eligiendo = eligiendoDestino,
                                habilitado = !working && pendiente,
                                onCambiar = { eligiendoDestino = !eligiendoDestino },
                                elegibles = destinosElegibles(accounts, resolvedAccount),
                                onElegir = { cuenta ->
                                    if (pideOrigen) origenElegidoId = cuenta.id else destinoElegidoId = cuenta.id
                                    eligiendoDestino = false
                                },
                                entrante = pideOrigen,
                            )
                        }
                    }
                    // «¿De quién es la cuenta ·0756? Guardar como…» — solo con un identificador que
                    // ninguna cuenta guardada conoce, y solo mientras el aviso espera una decisión.
                    val leidoParaGuardar = parsed
                    if (identificador != null && leidoParaGuardar != null && pendiente &&
                        destinosParaGuardar.ofrece(identificador, accounts)
                    ) {
                        Superficie(modifier = Modifier.fillMaxWidth().padding(top = Medida.dentroDeSeccion)) {
                            FilaGuardarElDestino(
                                identificador = identificador,
                                nombreSugerido = nombreParaLaFila(identificador, leidoParaGuardar.merchant),
                                textoDelAviso = sms?.text,
                                destinos = destinosParaGuardar.guardados.orEmpty(),
                                onGuardado = { guardado ->
                                    destinosParaGuardar.alGuardar(guardado)
                                    // El movimiento se propone con ese nombre: «Transferencia a Caro»
                                    // (o «de Caro»; un comercio, a secas). Lo que él ya escribió en
                                    // «Comercio» es suyo y no se pisa (la regla de `NombreDelTercero.kt`).
                                    if (comercioEditado.isNullOrBlank()) {
                                        textoConElNombreDelTercero(leidoParaGuardar.merchant, guardado, leidoParaGuardar.type)
                                            ?.let { comercioEditado = it }
                                    }
                                },
                            )
                        }
                    }
                    // «Anotarlo igual»: el monto y gasto o ingreso. Lo demás son las filas de siempre.
                    val leidaIgual = parsed
                    val trae = sinLeer
                    if (anotandoIgual && leidaIgual != null && trae != null) {
                        Superficie(modifier = Modifier.fillMaxWidth().padding(top = Medida.dentroDeSeccion)) {
                            FormularioDeAnotarloIgual(
                                monto = leidaIgual.amount.roundToLong().takeIf { it > 0 },
                                moneda = leidaIgual.currency,
                                tipo = leidaIgual.type,
                                habilitado = !working && pendiente,
                                onMonto = { nuevo -> parsed = leidaIgual.copy(amount = (nuevo ?: 0L).toDouble()) },
                                onTipo = { nuevo ->
                                    if (nuevo != leidaIgual.type) {
                                        parsed = leidaIgual.copy(type = nuevo)
                                        // La categoría de un gasto no es la de un ingreso: vuelve la
                                        // propuesta si es de ese lado, y si no, a elegir.
                                        selectedCategory = trae.categoria?.takeIf { nuevo == trae.tipo }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── Confirma o ajusta ────────────────────────────────────────────────────────────
            item(key = "ajusta") {
                Column {
                    Seccion(titulo = "Confirma o ajusta")
                    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                        val leido = parsed
                        if (leido == null) {
                            Detail(ok = false, label = "Comercio", value = "—")
                        } else {
                            ComercioQueSeEdita(
                                leido = leido.merchant,
                                valor = comercioEditado ?: leido.merchant,
                                enabled = !working && pendiente,
                                onCambio = { comercioEditado = it },
                            )
                        }
                        Separador()
                        Detail(
                            ok = resolvedAccount != null,
                            label = "Cuenta",
                            value = resolvedAccount?.name ?: "Sin cuenta elegida",
                            // Lo que Movi haya adivinado se dice acá, antes de guardar: un SMS no trae
                            // la cuenta escrita, la pone la app, y eso hay que confesarlo.
                            hint = advertenciaDeLaCuenta ?: if (cuentaDelSms.origen == OrigenDeLaCuentaDelBanco.SUGERIDA_POR_MOVI) {
                                parsed?.cuentaSugeridaPor ?: avisoDeLaCuentaDelBanco(cuentaDelSms.origen)
                            } else avisoDeLaCuentaDelBanco(cuentaDelSms.origen),
                            hintEsAviso = advertenciaDeLaCuenta != null,
                            action = if (eligiendoCuenta) "Cerrar" else "Cambiar",
                            onClick = { eligiendoCuenta = !eligiendoCuenta },
                        )
                        // El selector, en el mismo lugar donde antes había un dato de solo lectura:
                        // cuando ninguna cuenta califica —o Movi eligió mal— este es el camino.
                        if (eligiendoCuenta) {
                            ListaDeCuentasElegibles(
                                // `conservar` sostiene una elección hecha desde «Ver todas».
                                cuentas = cuentasPara(accounts, usoDeCuenta, conservar = resolvedAccount?.id),
                                uso = usoDeCuenta,
                                selectedId = resolvedAccount?.id,
                                onPick = { id ->
                                    cuentaElegida = id
                                    eligiendoCuenta = false
                                },
                                modifier = Modifier.padding(horizontal = Medida.margenDePantalla),
                            )
                        }
                        Separador()
                        if (anotandoIgual && currentSms != null) {
                            // La fecha del aviso: es la del movimiento, como en cualquier confirmación.
                            Detail(ok = true, label = "Fecha", value = fechaLegibleDeSms(currentSms.time))
                            Separador()
                        }
                        Detail(
                            ok = selectedCategory != null,
                            label = "Categoría",
                            value = selectedCategory ?: "—",
                            // De dónde salió la propuesta: «Así lo anotaste 4 veces». Se calla en
                            // cuanto él elige otra — ya no está explicando lo que se ve.
                            hint = parsed?.aprendidoDe?.takeIf { selectedCategory == parsed?.category },
                            action = if (parsed == null) null else if (eligiendoCategoria) "Cerrar" else "Cambiar",
                            onClick = if (parsed == null) null else {
                                {
                                    pedidosDeFoco = 0
                                    eligiendoCategoria = !eligiendoCategoria
                                }
                            },
                        )
                    }

                    if (eligiendoCategoria) {
                        // Se dispara una sola vez al abrirse (la clave es el booleano): sin esto, cada
                        // letra de la búsqueda recompondría este bloque y saltaría el scroll.
                        LaunchedEffect(eligiendoCategoria) {
                            categoriaBringIntoViewRequester.bringIntoView()
                        }
                        Superficie(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Medida.dentroDeSeccion)
                                .bringIntoViewRequester(categoriaBringIntoViewRequester),
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

                    // Sin la lectura no se sabe de qué lado es el movimiento (gasto o ingreso): las
                    // pastillas, «Cambiar» y «+ Nueva» esperan a que llegue.
                    if (parsed != null && categoryOptions.isNotEmpty()) {
                        // **Rueda en horizontal, no se parte ni se corta**: son hasta doce y a 375 dp
                        // no entran. Los filtros del sistema, como en Movimientos y Categorías.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Medida.dentroDeSeccion)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                        ) {
                            // «+ Nueva», PRIMERA de la fila: detrás de doce pastillas nadie la veía.
                            // Abre el selector con el cursor en la búsqueda, donde «Crear "…"» crea.
                            PildoraDeFiltro(
                                texto = "Nueva",
                                seleccionada = false,
                                icono = Icons.Rounded.Add,
                                onClick = {
                                    pedidosDeFoco += 1
                                    eligiendoCategoria = true
                                },
                                modifier = Modifier.testTag(TAG_PASTILLA_NUEVA_CATEGORIA),
                            )
                            categoryOptions.forEach { opt ->
                                PildoraDeFiltro(
                                    texto = opt,
                                    seleccionada = opt == selectedCategory,
                                    onClick = { selectedCategory = opt },
                                    modifier = Modifier.testTag(tagDePastillaDeCategoriaDelSms(opt)),
                                )
                            }
                        }
                    }

                    // Un traspaso sin la cuenta a la que fue la plata: por qué todavía no se puede.
                    if (categoriaActual == TRANSFER_CATEGORY && parsed != null && lasDosPatas == null) {
                        Text(
                            if (pideOrigen) AVISO_DEL_TRASPASO_QUE_ENTRA else avisoDelTraspaso(null, resolvedAccount),
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.padding(top = Medida.dentroDeSeccion),
                        )
                    }

                    // Ola Y: opcional, y solo con esas dos categorías sobre un gasto — ver
                    // `ofreceVincularDeuda`. Debajo de las pastillas, porque depende de cuál quedó.
                    if (ofreceVinculo) {
                        Box(Modifier.padding(top = Medida.dentroDeSeccion)) {
                            SelectorDeCuentaDeDeuda(
                                cuentas = accounts,
                                seleccionada = cuentaDeDeudaElegida,
                                onSeleccionar = { deudaTocadaAMano = true; cuentaDeDeudaElegida = it },
                            )
                        }
                    }

                    // Pagar una tarjeta en otra moneda (hueco de #439): cuánto bajó la deuda lo dice
                    // el banco. Sin la tasa del día el server no lo inventa y lo pide.
                    val pagoEntreMonedas = lasDosPatas?.takeIf { pideElMontoEnLaMonedaDeLaDeuda(it) }
                    if (pagoEntreMonedas != null) {
                        Column(
                            modifier = Modifier.padding(top = Medida.dentroDeSeccion),
                            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
                        ) {
                            MoneyField(
                                value = montoEnLaDeuda,
                                onValueChange = { montoEnLaDeuda = it },
                                label = rotuloDelMontoEnLaDeuda(pagoEntreMonedas.destino.currency),
                                placeholder = simboloDeMoneda(pagoEntreMonedas.destino.currency) + " 0",
                                prefix = simboloDeMoneda(pagoEntreMonedas.destino.currency),
                                modifier = Modifier.testTag(TAG_MONTO_EN_LA_MONEDA_DE_LA_DEUDA),
                            )
                            Text(AYUDA_DEL_MONTO_EN_LA_DEUDA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                        }
                    }

                    if (error != null) {
                        Text(error!!, style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.padding(top = Medida.dentroDeSeccion))
                    }

                    // Ola 2 #1: red de seguridad — si la pila de navegación rota trae de vuelta a
                    // este detalle ya resuelto (confirmado o ignorado), no se puede reconfirmar.
                    if (currentSms != null && currentSms.state != SMS_STATE_PENDING) {
                        Text(
                            textoDelAvisoYaResuelto(currentSms),
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.padding(top = Medida.dentroDeSeccion),
                        )
                    }
                }
            }
        }

        val alreadyResolved = currentSms != null && currentSms.state != SMS_STATE_PENDING
        // Junto al botón, que es donde se mira cuando no prende: por qué todavía no, o que la
        // revisión falló y se puede reintentar.
        val alrededorDeLosBotones = Modifier.padding(horizontal = Medida.margenDePantalla).padding(top = Espacio.xs)
        if (!alreadyResolved && yaAnotadoCon == null) {
            if (coincidencias == null && !noSePudoRevisar) {
                Text(
                    "Revisando si ya está anotado…",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    modifier = alrededorDeLosBotones,
                )
            } else if (noSePudoRevisar) {
                Row(
                    modifier = alrededorDeLosBotones.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Espacio.s),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla, vertical = Espacio.s),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            Boton(
                texto = "Ignorar",
                onClick = { ignore() },
                modifier = Modifier.weight(1f),
                variante = VarianteDeBoton.Secundario,
                habilitado = !working && !alreadyResolved,
                anchoCompleto = true,
            )
            // Hasta que la revisión de «¿ya lo anotaste?» conteste, no se anota. Si falló, se puede,
            // pero con un segundo toque que dice lo que hace.
            val revisionContesto = coincidencias != null || noSePudoRevisar
            // Ya anotado con otro aviso del pago: lo único que falta es cerrar los demás.
            val soloCerrar = yaAnotadoCon != null
            // Movi no entendió el aviso pero trae un monto: el botón principal abre el formulario.
            val ofreceAnotarloIgual = !soloCerrar && !alreadyResolved && parsed == null && sinLeer != null && !anotandoIgual
            // Con el formulario abierto, además, hace falta un monto y una categoría elegida.
            val categoriaParaAnotar = (selectedCategory ?: parsed?.category)?.takeIf { it.isNotBlank() }
            val canConfirm = !working && !alreadyResolved && (
                ofreceAnotarloIgual || soloCerrar || (
                    parsed != null && resolvedAccount != null && revisionContesto && !faltaElDestino &&
                        categoriaParaAnotar != null && (parsed?.amount ?: 0.0) > 0.0
                    )
                )
            Boton(
                texto = when {
                    working -> "Guardando…"
                    soloCerrar -> "Cerrar los avisos"
                    ofreceAnotarloIgual -> ANOTARLO_IGUAL
                    anotarSinRevisar -> "Anotar de todas formas"
                    else -> "Confirmar"
                },
                onClick = {
                    when {
                        ofreceAnotarloIgual -> sinLeer?.let { trae ->
                            parsed = trae.comoLectura()
                            selectedCategory = trae.categoria
                            anotandoIgual = true
                        }
                        soloCerrar -> cerrarElPago()
                        else -> confirm()
                    }
                },
                modifier = Modifier.weight(1.7f),
                habilitado = canConfirm,
                anchoCompleto = true,
            )
        }
    }
}

/**
 * **La fila «Comercio», editable.** El nombre lo leyó Movi del aviso —«TOSTAO CAFE Y PAN VISC»,
 * o «Movimiento» cuando no supo leerlo— y hasta acá solo se podía cambiar después, desde
 * Movimientos. Lo que quede escrito es el concepto y el comercio del movimiento; vacío vuelve al
 * leído, que se muestra de guía mientras tanto.
 *
 * Tiene la anatomía de una [Fila] (el estado al inicio, el rótulo y el valor, «Editar» al final),
 * con el campo en el lugar del valor: el mismo del concepto en la hoja del movimiento
 * ([rememberCampoConSeleccion], con ⌘A en la web), con el tope de la columna.
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
            .heightIn(min = Medida.altoDeFilaDoble)
            .clickable(enabled = enabled) { foco.requestFocus() }
            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        IconoEnCirculo(Icons.Rounded.Check, tono = TonoDeIcono.Exito)
        Column(modifier = Modifier.weight(1f)) {
            Text("Comercio", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            val campo = rememberCampoConSeleccion(valor) { onCambio(it.take(MAX_CONCEPTO_LENGTH)) }
            BasicTextField(
                value = campo.valor,
                onValueChange = campo::alCambiar,
                enabled = enabled,
                cursorBrush = SolidColor(Movi.colores.texto),
                textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(foco)
                    .onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                decorationBox = { inner ->
                    if (valor.isEmpty()) {
                        Text(leido, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                    }
                    inner()
                },
            )
        }
        Text("Editar", style = Movi.textos.cuerpo, color = Movi.colores.marca)
    }
}

/**
 * Una fila del resumen «Confirma o ajusta»: una [Fila] del sistema con el estado al inicio (listo, o
 * falta decidir), el valor de título y el rótulo de subtítulo —con el porqué cuando lo puso Movi—.
 *
 * @param hint por qué ese valor está puesto, cuando lo puso Movi.
 * @param action el texto de la derecha que invita a tocar («Cambiar» / «Cerrar»). Va junto con
 *   [onClick]: una fila que se puede tocar y no lo dice es una fila que nadie toca.
 */
@Composable
private fun Detail(
    ok: Boolean,
    label: String,
    value: String,
    hint: String? = null,
    /** El [hint] pide atención («Ojo: el aviso es de Falabella…»): en el tono de aviso y en dos renglones. */
    hintEsAviso: Boolean = false,
    action: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Fila(
        titulo = value,
        // El porqué, cuando lo puso Movi («La puso Movi», «Así lo anotaste 4 veces»); si no, qué es.
        subtitulo = hint ?: label,
        tonoDelSubtitulo = if (hintEsAviso) TonoDeSubtitulo.Aviso else TonoDeSubtitulo.Normal,
        lineasDelSubtitulo = if (hintEsAviso) 2 else 1,
        modifier = Modifier.testTag(tagDeFilaDelSms(label)),
        inicio = {
            if (ok) IconoEnCirculo(Icons.Rounded.Check, tono = TonoDeIcono.Exito)
            else IconoEnCirculo(Icons.Rounded.QuestionMark, tono = TonoDeIcono.Aviso)
        },
        final = action?.let { texto -> { Text(texto, style = Movi.textos.cuerpo, color = Movi.colores.marca) } },
        onClick = onClick,
    )
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
    // Con lo que el aviso dijo de para qué fue, si lo dijo (la «Descripción» de PSE).
    description = descripcionConLaNota(leido.merchant, leido.nota),
    merchant = leido.merchant,
    source = origen,
    rawPayload = textoDelSms.ifBlank { null },
    reconciliationStatus = ReconciliationStatus.RECONCILED,
    timestamp = momento,
)

/**
 * **Qué pasó con un aviso que ya no está pendiente**, dicho como fue. Antes decía siempre «Este
 * mensaje ya se confirmó», también para lo que Movi apartó solo (una promoción, un recordatorio de
 * pago) y para lo que el dueño ignoró: le decía que se había anotado algo que no se anotó.
 */
internal fun textoDelAvisoYaResuelto(sms: SmsMessage): String = when {
    sms.state == SMS_STATE_CONFIRMED -> "Este mensaje ya se confirmó"
    sms.state == SMS_STATE_IGNORED -> motivoDeApartado(sms.apartadoPor)
        ?.let { "Movi lo apartó: ${it.texto}" }
        ?: "Ignoraste este mensaje"
    else -> "Este mensaje ya se resolvió"
}
