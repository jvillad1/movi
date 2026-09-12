package com.jvillada.movi.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.EventSource
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
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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

@Composable
fun SMSInboxScreen(onNavigate: (Screen) -> Unit) {
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

    LaunchedEffect(refreshKey) {
        runCatching { Repositories.wallets.getSmsMessages() }
            .onSuccess { smsItems = it }
    }
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getUserProfile() }
            .onSuccess { silenciada = it.smsAlertMuted }
    }
    val mensajes = mensajesMasRecientesPrimero(smsItems.orEmpty())
    val pendingCount = mensajes.count { it.state == SMS_STATE_PENDING }
    // El estado de la captura sale de la MISMA función que usa el server para el Inicio
    // (`capturaDeSms`, en :core) — acá sin un viaje extra, porque la lista ya está bajada.
    val aviso = smsItems?.let { avisoDeCaptura(capturaDeSms(it.map { sms -> sms.time })) }
    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60: encabezado único — se abre desde Más (flecha, F22); la cuenta de pendientes
        // va como subtítulo y «Actualizar» es la acción propia.
        MinScreenHeader(
            title = "Mensajes del banco",
            leading = HeaderLeading.Back(fallback = Screen.Mas),
            subtitle = "$pendingCount por confirmar",
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
                            fontSize = 11.sp,
                            color = if (hecho.esAlerta) Movi.colores.aviso else Movi.colores.textoMedio,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            hecho.detalle,
                            fontSize = 13.5.sp,
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
                                fontSize = 12.5.sp,
                                color = Movi.colores.aviso,
                                lineHeight = 18.sp,
                            )
                        }
                    } else {
                        Text(
                            "Este dispositivo no puede leer mensajes: eso lo hace un teléfono Android con Movi instalado. Aquí los revisas antes de que cuenten.",
                            fontSize = 12.5.sp,
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
                                    "Este aviso no se muestra en Inicio.",
                                    fontSize = 12.5.sp,
                                    color = Movi.colores.textoMedio,
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(
                                if (silencioActual) "Volver a avisarme en Inicio" else "No me avises de esto en Inicio",
                                fontSize = 12.5.sp,
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
                // Solo Android pinta algo acá: la configuración de la captura de SMS (permisos,
                // hibernación, historial) que antes vivía en la pantalla del APK sensor.
                // Reemplaza también a la vieja tarjeta "Sincronizar SMS del teléfono", que subía
                // el inbox SIN el filtro bancario — el historial de la sección sí lo aplica, así
                // que lo que no matchea nunca sale del teléfono.
                SmsSensorSetupSection(onSynced = { refreshKey++ })

                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Bandeja", count = mensajes.size)
            }

            mensajes.forEach { sms ->
                item {
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
                            Text(sms.bank, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                            StatusDot(Movi.colores.textoApagado, 2.dp)
                            Text(sms.time, fontSize = 11.5.sp, color = Movi.colores.textoMedio)
                            Spacer(Modifier.weight(1f))
                            val (label, color) = when (sms.state) {
                                SMS_STATE_PENDING -> "PENDIENTE" to Movi.colores.aviso
                                SMS_STATE_CONFIRMED -> "CONFIRMADO" to Movi.colores.entra
                                SMS_STATE_IGNORED -> "IGNORADO" to Movi.colores.textoMedio
                                else -> sms.state.uppercase() to Movi.colores.textoMedio
                            }
                            Text(label, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = color, letterSpacing = 0.4.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Movi.colores.hilo))
                            Text(sms.text, fontSize = 12.sp, color = Movi.colores.textoMedio, fontFamily = FontFamily.Monospace, lineHeight = 17.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Hairline()
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sms.det, fontSize = 13.sp, color = Movi.colores.texto, letterSpacing = (-0.1).sp, modifier = Modifier.weight(1f))
                            if (sms.state == SMS_STATE_PENDING) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp))
                                        .clickable { onNavigate(Screen.SMSReconcile(sms.id)) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text("Revisar", fontSize = 12.5.sp, color = Movi.colores.texto, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SMSReconcileScreen(onNavigate: (Screen) -> Unit, smsId: String) {
    val goBack = LocalGoBack.current
    val coroutine = rememberCoroutineScope()
    var sms by remember { mutableStateOf<SmsMessage?>(null) }
    var parsed by remember { mutableStateOf<ParsedSms?>(null) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    // La cuenta que el dueño eligió con el dedo, si la eligió. Manda sobre lo que resuelva Movi
    // —ver [resolverCuentaDelBanco]— y por eso vive acá y no adentro del cálculo.
    var cuentaElegida by remember { mutableStateOf<String?>(null) }
    var eligiendoCuenta by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(smsId) {
        runCatching { Repositories.wallets.getSms(smsId) }.onSuccess { sms = it }
            .onFailure { error = "No pude cargar el SMS" }
        runCatching { Repositories.wallets.parseSms(smsId) }
            .onSuccess { parsed = it; selectedCategory = it.category }
            .onFailure { error = "No pude parsear el SMS" }
        runCatching { Repositories.wallets.getAccounts() }.onSuccess { accounts = it }
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
    )
    val resolvedAccount = cuentaDelSms.cuenta

    val categoryOptions: List<String> = run {
        val base = parsed?.category
        val alts = if (parsed?.type == TransactionType.INCOME) {
            listOf("Nómina", "Transferencia", "Reembolso")
        } else {
            listOf("Restaurantes", "Mercado", "Transporte", "Salud", "Suscripción", "Servicios", "Hogar", "Otro")
        }
        (listOfNotNull(base) + alts).distinct().take(4)
    }

    fun confirm() {
        val cat = selectedCategory ?: parsed?.category ?: return
        val acct = resolvedAccount ?: return
        val p = parsed ?: return
        working = true
        error = null
        coroutine.launch {
            runCatching {
                val event = FinancialEvent(
                    // Mismo motivo que en QuickAddScreen — ver newId().
                    id = newId("ev"),
                    accountId = acct.id,
                    type = p.type,
                    amount = p.amount.toLong(),
                    category = cat,
                    description = p.merchant,
                    merchant = p.merchant,
                    source = EventSource.SMS,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
                Repositories.wallets.postEvent(event)
                Repositories.wallets.confirmSms(smsId)
            }.onSuccess {
                working = false
                sms = sms?.copy(state = SMS_STATE_CONFIRMED)
                // Ola 2 #1: pop, no push — un segundo tap en ‹ desde la bandeja no debe volver
                // a este detalle ya confirmado (evita duplicar el movimiento).
                goBack(Screen.SMSInbox)
            }.onFailure {
                working = false
                error = it.toUserMessage()
            }
        }
    }

    fun ignore() {
        working = true
        error = null
        coroutine.launch {
            val result = runCatching { Repositories.wallets.ignoreSms(smsId) }
            working = false
            result.onSuccess {
                sms = sms?.copy(state = SMS_STATE_IGNORED)
                goBack(Screen.SMSInbox)
            }
                .onFailure { error = it.toUserMessage() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60 · F22: el detalle vuelve a la bandeja si no hay historial (su padre lógico).
        MinScreenHeader(
            title = "Reconciliar movimiento",
            leading = HeaderLeading.Back(fallback = Screen.SMSInbox),
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            item {
                MinSectionHeader(title = "SMS recibido")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Default,
                    padding = PaddingValues(18.dp),
                ) {
                    if (sms == null) {
                        Text("Cargando…", fontSize = 13.sp, color = Movi.colores.textoMedio)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(sms!!.bank, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
                            StatusDot(Movi.colores.textoApagado, 2.dp)
                            Text("SMS", fontSize = 11.sp, color = Movi.colores.textoMedio, fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp)
                            StatusDot(Movi.colores.textoApagado, 2.dp)
                            Text(sms!!.time, fontSize = 11.sp, color = Movi.colores.textoMedio)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Movi.colores.hilo))
                            Text(
                                "\"${sms!!.text}\"",
                                fontSize = 13.sp,
                                color = Movi.colores.textoMedio,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Movi sugiere")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(20.dp),
                ) {
                    if (parsed == null) {
                        Text("Parseando…", fontSize = 13.sp, color = Movi.colores.textoMedio)
                    } else {
                        val p = parsed!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.merchant, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Movi.colores.texto, letterSpacing = (-0.2).sp)
                                Text(
                                    "${selectedCategory ?: p.category} · ${resolvedAccount?.name ?: "Elige la cuenta"}",
                                    fontSize = 12.sp,
                                    color = Movi.colores.textoMedio,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            val sign = if (p.type == TransactionType.EXPENSE) "−" else "+"
                            val color = if (p.type == TransactionType.EXPENSE) Movi.colores.sale else Movi.colores.entra
                            Text(
                                "$sign\$${formatThousands(p.amount.toLong())}",
                                fontSize = 17.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = color,
                                letterSpacing = (-0.4).sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                MinSectionHeader(title = "Confirma o ajusta")
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    Detail(
                        ok = parsed != null,
                        label = "Comercio",
                        value = parsed?.merchant ?: "—",
                    )
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
                        isLast = true,
                    )
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

                if (categoryOptions.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    // **Se desbordaban por el borde derecho.** `categoryOptions` trae hasta cuatro
                    // (`take(4)`), y a 375 dp —el ancho con el que se usa la PWA desde el teléfono—
                    // «Restaurantes · Mercado · Transporte · Salud» no entra: la cuarta quedaba
                    // cortada contra el margen, con las letras apiladas en vertical, ilegible y sin
                    // forma de tocarla. Y estas pastillas no son un atajo: la fila «Categoría» de
                    // arriba es de solo lectura, así que son el único modo de cambiarla acá.
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
                        categoryOptions.forEach { opt ->
                            val on = opt == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (on) Movi.colores.texto else Color.Transparent)
                                    .then(if (!on) Modifier.border(1.dp, Movi.colores.borde, RoundedCornerShape(999.dp)) else Modifier)
                                    .clickable { selectedCategory = opt }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(opt, fontSize = 12.5.sp, color = if (on) Movi.colores.fondo else Movi.colores.texto, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error!!, fontSize = 12.sp, color = Movi.colores.sale)
                }

                // Ola 2 #1: red de seguridad — si la pila de navegación rota trae de vuelta a
                // este detalle ya resuelto (confirmado o ignorado), no se puede reconfirmar.
                if (currentSms != null && currentSms.state != SMS_STATE_PENDING) {
                    Spacer(Modifier.height(10.dp))
                    Text("Este mensaje ya se confirmó", fontSize = 12.sp, color = Movi.colores.textoMedio)
                }
            }
        }

        val alreadyResolved = currentSms != null && currentSms.state != SMS_STATE_PENDING
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
                Text("Ignorar", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            }
            val canConfirm = parsed != null && resolvedAccount != null && !working && !alreadyResolved
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
                    if (working) "Guardando…" else "Confirmar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canConfirm) Movi.colores.fondo else Movi.colores.textoApagado,
                )
            }
        }
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
                Text("?", fontSize = 10.sp, color = Movi.colores.aviso, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(), fontSize = 11.sp, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
            Text(value, fontSize = 13.5.sp, color = Movi.colores.texto, letterSpacing = (-0.1).sp, modifier = Modifier.padding(top = 2.dp))
            if (hint != null) {
                Text(hint, fontSize = 11.sp, color = Movi.colores.textoApagado, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (action != null) {
            Text(action, fontSize = 12.sp, color = Movi.colores.textoMedio)
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
