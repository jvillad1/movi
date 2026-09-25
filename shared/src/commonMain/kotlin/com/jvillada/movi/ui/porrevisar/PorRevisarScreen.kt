package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.capturaDeSms
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.FilaDeListaEsqueleto
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.RotuloDeSeccionEsqueleto
import com.jvillada.movi.ui.components.VacioQueEnsena
import com.jvillada.movi.ui.sms.TarjetaDeMensajeDelBanco
import com.jvillada.movi.ui.transactions.CardPaymentCandidatesSheet
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import com.jvillada.movi.ui.transactions.MovementRow
import com.jvillada.movi.ui.transactions.MovementSingleRow
import com.jvillada.movi.ui.transactions.TransferRow

/** El título de la bandeja, y el rótulo con que la nombran los demás. */
const val TITULO_DE_POR_REVISAR: String = "Por revisar"

/** Lo que dice la bandeja cuando las tres lecturas contestaron y ninguna trajo nada. */
const val TODO_AL_DIA: String = "Todo al día"

/** El tag del renglón «N por revisar» de Movimientos. */
const val TAG_RENGLON_POR_REVISAR: String = "renglon-por-revisar"

/** El tag del esqueleto de la bandeja, para verificar que está mientras nada contestó. */
const val TAG_ESQUELETO_DE_POR_REVISAR: String = "esqueleto-de-por-revisar"

/** El tag del renglón «la captura dejó de andar» de la bandeja. */
const val TAG_AVISO_DE_CAPTURA_EN_LA_BANDEJA: String = "aviso-de-captura-en-la-bandeja"

/**
 * **El renglón de Movimientos que abre la bandeja**: «N por revisar», con la suma de las tres
 * fuentes (ver [cuantosPorRevisar]). Reemplaza a los avisos sueltos que había antes —uno por los
 * movimientos que entraron solos, otro por los pagos de tarjeta— y tiene la misma forma que ellos:
 * una tarjeta baja con el texto y la flecha.
 */
@Composable
fun RenglonPorRevisar(cuantos: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilaQueLleva(
        texto = textoDePorRevisar(cuantos),
        onClick = onClick,
        modifier = modifier.testTag(TAG_RENGLON_POR_REVISAR),
    )
}

/**
 * **«Por revisar»** — ola C, tarea 5: una sola bandeja para lo que entró solo y espera que el dueño
 * decida. Tres secciones, cada una con su propia acción de siempre:
 *
 * 1. **Mensajes del banco** por confirmar: la misma tarjeta que el historial, y «Revisar» abre
 *    [Screen.SMSReconcile] como siempre.
 * 2. **Entraron solos**: los movimientos sin confirmar, con el mismo renglón de Movimientos; tocarlo
 *    abre [HojaDelMovimiento], que es donde vive «Confirmar».
 * 3. **Pagos de tarjeta** sin marcar: abre [CardPaymentCandidatesSheet], uno por uno.
 *
 * Arriba de todo, solo si la captura dejó de andar —la misma condición que el aviso del Hoy, ver
 * [avisoDeCapturaEnLaBandeja]— un renglón que lleva a «Captura del banco». La configuración de la
 * captura no vive acá: esto es una bandeja, no un panel de ajustes.
 *
 * **Nada se afirma antes de leer.** Mientras las lecturas no contestaron hay un esqueleto; una
 * lectura caída dice «No pudimos cargar…» con su «Reintentar» en el lugar de su sección; y
 * [TODO_AL_DIA] sale solo cuando las tres contestaron bien y vacías (ver [bandejaAlDia]).
 */
@Composable
fun PorRevisarScreen(onNavigate: (Screen) -> Unit) {
    var recarga by remember { mutableStateOf(0) }
    val refreshTick = LocalRefreshTick.current
    val lecturas = rememberLecturasPorRevisar(recarga)

    /** `null` hasta que `getEventsByDay` contesta bien; un reintento que falla conserva lo último. */
    var diasLeidos by remember { mutableStateOf<List<EventDay>?>(null) }
    var leyendoDias by remember { mutableStateOf(true) }
    LaunchedEffect(recarga, refreshTick) {
        leyendoDias = true
        intentar { Repositories.wallets.getEventsByDay() }.onSuccess { diasLeidos = it }
        leyendoDias = false
    }
    // Secundarias: sin las cuentas los renglones dicen solo la categoría, y sin el perfil no se
    // sabe si el dueño silenció el aviso de la captura, así que ese renglón no se pinta.
    var cuentas by remember { mutableStateOf<List<Account>>(emptyList()) }
    LaunchedEffect(recarga, refreshTick) {
        intentar { Repositories.wallets.getAccounts() }.onSuccess { cuentas = it }
    }
    var capturaSilenciada by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(recarga) {
        intentar { Repositories.wallets.getUserProfile() }.onSuccess { capturaSilenciada = it.smsAlertMuted }
    }

    // Los candidatos que el dueño ya resolvió acá —«Marcar» o «No es»—. Se descuentan de la lista
    // porque el refetch puede fallar y dejarla vieja: sin esto, un pago recién resuelto volvía a
    // aparecer con sus botones activos, como si la acción no se hubiera guardado.
    var candidatosResueltos by remember { mutableStateOf(emptySet<String>()) }
    // Lo mismo para los movimientos que se confirmaron o anularon desde su hoja: la llave es el
    // traspaso cuando lo hay, porque confirmar una pata confirma el par (y el renglón es uno).
    var movimientosResueltos by remember { mutableStateOf(emptySet<String>()) }
    var viendoCandidatos by remember { mutableStateOf(false) }
    var movimientoAbierto by remember { mutableStateOf<FinancialEvent?>(null) }

    val mensajes = lecturas.mensajes
    val dias = diasLeidos?.map { dia ->
        dia.copy(items = dia.items.filterNot { (it.transferId ?: it.id) in movimientosResueltos })
    }
    val candidatos = lecturas.candidatos?.filterNot { it.id in candidatosResueltos }
    val nombresDeCuentas = remember(cuentas) { cuentas.associate { it.id to it.name } }
    val tiposDeCuentas = remember(cuentas) { cuentas.associate { it.id to it.type } }

    // La primera carga: alguna de las tres fuentes todavía no dijo nada. Con lo de antes en
    // pantalla (un reintento) no se vuelve al esqueleto.
    val cargando = (mensajes == null && lecturas.leyendoMensajes) ||
        (dias == null && leyendoDias) ||
        (lecturas.candidatos == null && lecturas.leyendoCandidatos)

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        MinScreenHeader(
            title = TITULO_DE_POR_REVISAR,
            leading = HeaderLeading.Back(fallback = Screen.Transactions()),
            action = {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Actualizar",
                    tint = Movi.colores.textoMedio,
                    modifier = Modifier.size(20.dp).clickable { recarga++ },
                )
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
        ) {
            if (cargando) {
                item { EsqueletoDePorRevisar() }
                return@LazyColumn
            }

            avisoDeCapturaEnLaBandeja(mensajes, capturaSilenciada)?.let { aviso ->
                item {
                    FilaQueLleva(
                        texto = aviso,
                        detalle = "Toca para ver cómo está la captura.",
                        esAlerta = true,
                        onClick = { onNavigate(Screen.CapturaDelBanco) },
                        modifier = Modifier.padding(bottom = 16.dp).testTag(TAG_AVISO_DE_CAPTURA_EN_LA_BANDEJA),
                    )
                }
            }

            if (bandejaAlDia(mensajes, dias, candidatos)) {
                item { TodoAlDia() }
                // Ola D, Task 2: `mensajes` no es `null` acá —[bandejaAlDia] lo exige— y contestó
                // vacía de verdad: nunca llegó un mensaje del banco a esta cuenta. Es la misma
                // condición que [avisoDeCapturaEnLaBandeja] usa arriba, pero SIN mirar si el dueño
                // la silenció: ese renglón es un reclamo que se puede apagar, este es una
                // invitación a configurar algo que todavía no existe, y silenciar el reclamo no
                // debería apagar también la invitación.
                if (mensajes != null && capturaDeSms(mensajes.map { it.time }).nuncaLlegoNada) {
                    item { VacioDeLaCapturaEnLaBandeja(onNavigate) }
                }
                return@LazyColumn
            }

            // ── Mensajes del banco ────────────────────────────────────────────────
            if (mensajes == null) {
                item { SeccionQueNoSeLeyo("No pudimos cargar tus mensajes del banco") { recarga++ } }
            } else {
                val pendientes = mensajesPorRevisar(mensajes)
                if (pendientes.isNotEmpty()) {
                    item { MinSectionHeader(title = "Mensajes del banco", count = pendientes.size) }
                    pendientes.forEach { sms ->
                        item(key = "sms-${sms.id}") {
                            TarjetaDeMensajeDelBanco(sms, onRevisar = { onNavigate(Screen.SMSReconcile(sms.id)) })
                        }
                    }
                    item { Spacer(Modifier.height(14.dp)) }
                }
            }

            // ── Entraron solos ────────────────────────────────────────────────────
            if (dias == null) {
                item { SeccionQueNoSeLeyo("No pudimos cargar tus movimientos") { recarga++ } }
            } else {
                // Se cuentan los renglones, no los eventos: un traspaso es uno (ver
                // [renglonesQueEntraronSolos]), el mismo número que el renglón de Movimientos.
                val renglones = renglonesQueEntraronSolos(dias)
                if (renglones.isNotEmpty()) {
                    item {
                        MinSectionHeader(title = "Entraron solos", count = renglones.size)
                        Text(
                            "Llegaron por SMS, por un extracto o por una foto. Hasta confirmarlos no cuentan en " +
                                "tus gastos ni ingresos: tócalos para revisar el monto y la categoría.",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.padding(horizontal = Movi.espacios.corto).padding(bottom = 10.dp),
                        )
                        MinCard(
                            modifier = Modifier.fillMaxWidth(),
                            variant = MinCardVariant.Elevated,
                            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                        ) {
                            renglones.forEachIndexed { i, renglon ->
                                when (renglon) {
                                    is MovementRow.Transfer -> TransferRow(
                                        row = renglon,
                                        accountNames = nombresDeCuentas,
                                        accountTypes = tiposDeCuentas,
                                        onClick = { movimientoAbierto = renglon.out },
                                    )
                                    is MovementRow.Single -> MovementSingleRow(
                                        tx = renglon.event,
                                        accountNames = nombresDeCuentas,
                                        onClick = { movimientoAbierto = renglon.event },
                                    )
                                    // `collapseTransfers` no agrupa ajustes (eso lo hace
                                    // `agruparAjustesDeSaldo`, que acá no se llama); la rama está
                                    // para que el `when` siga siendo exhaustivo.
                                    is MovementRow.Ajustes -> renglon.events.forEach { ajuste ->
                                        MovementSingleRow(
                                            tx = ajuste,
                                            accountNames = nombresDeCuentas,
                                            onClick = { movimientoAbierto = ajuste },
                                        )
                                    }
                                }
                                if (i < renglones.size - 1) Hairline()
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // ── Pagos de tarjeta ──────────────────────────────────────────────────
            if (candidatos == null) {
                item { SeccionQueNoSeLeyo("No pudimos cargar los pagos de tarjeta") { recarga++ } }
            } else if (candidatos.isNotEmpty()) {
                item {
                    MinSectionHeader(title = "Pagos de tarjeta", count = candidatos.size)
                    // Abre una lista donde cada uno se confirma por separado (ver
                    // CardPaymentCandidatesSheet) — nunca se marcan todos de una.
                    FilaQueLleva(
                        texto = if (candidatos.size == 1) "1 pago de tarjeta sin marcar"
                        else "${candidatos.size} pagos de tarjeta sin marcar",
                        detalle = "Marcarlos evita contar la misma plata dos veces.",
                        onClick = { viendoCandidatos = true },
                    )
                }
            }
        }
    }

    movimientoAbierto?.let { evento ->
        // El mismo juego de hojas que Movimientos: ahí vive «Confirmar» (ver `PorConfirmar` en
        // CategorySheets.kt), junto con corregir el monto, la categoría o la cuenta antes.
        HojaDelMovimiento(
            event = evento,
            cuentas = cuentas,
            onDismiss = { movimientoAbierto = null },
            onCambiado = { movimientoAbierto = null; recarga++ },
            onResuelto = { resuelto -> movimientosResueltos = movimientosResueltos + (resuelto.transferId ?: resuelto.id) },
            onVerCuenta = tiposDeCuentas[evento.accountId]?.let { tipo ->
                { onNavigate(Screen.AccountDetail(evento.accountId, tipo.group)) }
            },
        )
    }

    if (viendoCandidatos && candidatos != null) {
        CardPaymentCandidatesSheet(
            candidates = candidatos,
            onDismiss = { viendoCandidatos = false },
            onConfirmed = { id -> candidatosResueltos = candidatosResueltos + id; recarga++ },
            onDismissedCandidate = { id -> candidatosResueltos = candidatosResueltos + id; recarga++ },
        )
    }
}

/**
 * Una tarjeta baja que lleva a otro lado: el texto, opcionalmente una línea de apoyo, y la flecha.
 * Es la forma de los avisos que Movimientos tenía arriba de su lista, y la usan el renglón «N por
 * revisar», el aviso de la captura y la entrada a los pagos de tarjeta.
 */
@Composable
private fun FilaQueLleva(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    esAlerta: Boolean = false,
) {
    MinCard(
        modifier = modifier.fillMaxWidth(),
        variant = MinCardVariant.Default,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = texto,
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    color = if (esAlerta) Movi.colores.aviso else Movi.colores.texto,
                )
                detalle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(text = it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
            }
            ChevronRight()
        }
    }
}

/** Una fuente que no contestó: se dice en el lugar de su sección, con su «Reintentar». */
@Composable
private fun SeccionQueNoSeLeyo(texto: String, onReintentar: () -> Unit) {
    NoSePudoLeer(texto, onReintentar = onReintentar, modifier = Modifier.padding(bottom = 16.dp))
}

/**
 * El vacío de verdad: las tres fuentes contestaron y no hay nada. No ofrece ninguna acción —no hay
 * nada que hacer, que es la buena noticia— y explica qué caería acá, para que la bandeja vacía no
 * sea un misterio.
 */
@Composable
private fun TodoAlDia() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(TODO_AL_DIA, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
        Text(
            "Aquí caen los mensajes del banco, los movimientos que entran solos y los pagos de tarjeta " +
                "que Movi reconoce, hasta que los revises.",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Ola D, Task 2: debajo de [TodoAlDia], cuando además nunca llegó un mensaje del banco —ver el
 * llamador—, invita a configurar la captura en vez de dejar la bandeja vacía sin explicar por qué
 * ese mensaje nunca va a entrar solo.
 *
 * **El texto cambia por plataforma, con la MISMA señal que [tituloDeCapturaDelBanco]**: en Android
 * la captura lee SMS y notificaciones del banco; en la web y en iOS —que no leen nada del
 * teléfono— lo único que llega son los correos que reenvía el banco (ver
 * `CorreoEntranteRoutes.kt`), así que prometer «mensajes» ahí sería prometer algo que ese aparato
 * no hace.
 */
@Composable
private fun VacioDeLaCapturaEnLaBandeja(onNavigate: (Screen) -> Unit) {
    VacioQueEnsena(
        titulo = "Que tus movimientos entren solos",
        detalle = if (isAndroid) {
            "Movi puede leer los mensajes y avisos de tu banco y dejarlos aquí para que los revises. " +
                "Así no tienes que anotar cada compra."
        } else {
            "Movi puede leer los correos de tu banco y dejarlos aquí para que los revises. Así no " +
                "tienes que anotar cada compra."
        },
        accion = "Configurar la captura",
        onAccion = { onNavigate(Screen.CapturaDelBanco) },
        modifier = Modifier.padding(top = 16.dp),
    )
}

/**
 * La bandeja mientras nada contestó: un rótulo de sección y una tarjeta con tres renglones, la
 * forma de «Entraron solos» — la sección de renglones, que es la que más se repite. No dice ni
 * cuántos hay ni de qué fuente: nada de eso se sabe todavía.
 */
@Composable
private fun EsqueletoDePorRevisar() {
    Column(modifier = Modifier.testTag(TAG_ESQUELETO_DE_POR_REVISAR)) {
        RotuloDeSeccionEsqueleto()
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            repeat(3) { i ->
                FilaDeListaEsqueleto(
                    isLast = i == 2,
                    diametroIconoAlFrente = TamanoDeIconoDeCategoria.Normal.circulo,
                )
            }
        }
    }
}
