package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.*
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.ui.fecha.hoyEnAppZone


@Composable
fun StatementReviewScreen(
    onNavigate: (Screen) -> Unit,
    result: StatementParseResult,
) {
    val goBack = LocalGoBack.current
    val coroutine = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(emptyList<Account>()) }
    // Lo que el dueño tildó o destildó con el dedo. El resto va por defecto: todo tildado, salvo un
    // cargo del banco que ya está anotado sumado en la cuenta del extracto (ver `estadoDelCargo`).
    // Es un mapa de excepciones y no un conjunto fijo porque la cuenta se resuelve DESPUÉS de abrir
    // (llegan las cuentas, o el dueño la cambia), y con ella cambia qué cargo ya está anotado.
    val elegidasAMano = remember { mutableStateMapOf<String, Boolean>() }
    // Los cargos del banco que ya se anotaron desde su bloque, con «Anotar los N».
    var cargosAnotados by remember { mutableStateOf(emptySet<String>()) }
    val reconciliations = remember { mutableStateMapOf<String, ReconciliationDecision>() }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // La cuenta que el dueño eligió con el dedo, si la eligió. Manda sobre lo que resuelva Movi.
    var cuentaElegida by remember { mutableStateOf<String?>(null) }
    var eligiendoCuenta by remember { mutableStateOf(false) }
    // Ola 2 #1: red de seguridad — si la pila de navegación vuelve a esta pantalla ya
    // importada (mismo defecto que el de SMS), no se puede reimportar el mismo extracto.
    var imported by remember { mutableStateOf(false) }

    // Load accounts to determine destination
    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getAccounts() }
            .onSuccess { accounts = it }
            .onFailure { error = it.toUserMessage() }
    }

    // Ola 15: acá había la misma cadena que en SMSScreens, respaldo peligroso incluido —
    // `accounts.firstOrNull()`, o sea la primera del abecedario, que en las cuentas del dueño
    // puede ser el «Vehículo 4083». Un extracto entero podía quedar importado contra un crédito
    // ya desembolsado. Ahora las candidatas salen del criterio de `:core`, y si no hay ninguna no
    // se resuelve nada: el botón queda apagado (ya lo estaba) y el chip pide que la elija.
    //
    // [UsoDeCuenta.CUENTA_DEL_EXTRACTO] y no el del gasto: un extracto trae las compras del mes y
    // también la nómina que entró, así que la pregunta no es de qué lado se mueve la plata sino
    // quién manda extractos.
    val destino = remember(accounts, result.bankName, cuentaElegida) {
        resolverCuentaDelBanco(
            accounts = accounts,
            uso = UsoDeCuenta.CUENTA_DEL_EXTRACTO,
            banco = result.bankName,
            elegidaAMano = cuentaElegida,
            // El número de la tarjeta o cuenta que nombra el extracto, con la misma regla que un SMS:
            // un extracto de la Master Black ya no cae por defecto en «Bancolombia Ahorros».
            textoDelMensaje = result.numerosDeCuenta.joinToString(" ") { "*$it" },
        )
    }
    val destinationAccount = destino.cuenta

    val cargos = remember(result) { result.cargosDelBanco.associateBy { it.parsedId } }
    val estadosDeLosCargos = remember(cargos, destinationAccount?.id) {
        cargos.mapValues { (_, cargo) -> estadoDelCargo(cargo, destinationAccount?.id) }
    }
    val filasPorImportar = result.newTransactions.filter { it.id !in cargosAnotados }
    val selectedIds = filasPorImportar
        .filter { elegidasAMano[it.id] ?: (estadosDeLosCargos[it.id] !is EstadoDelCargo.YaAnotado) }
        .map { it.id }
        .toSet()
    val filasDelBanco = filasPorImportar.filter { it.id in cargos }
    val filasNuevas = filasPorImportar.filter { it.id !in cargos }
    val cargosTildados = filasDelBanco.filter { it.id in selectedIds }

    val confirmedCount = reconciliations.values.count { it.confirm }
    // Las coincidencias que el dueño no tocó: no se importan ni se concilian. Antes eso pasaba en
    // silencio, y una coincidencia falsa sin revisar era una compra real que nunca entraba.
    val sinRevisar = result.matches.count { it.parsed.id !in reconciliations }
    // Las coincidencias marcadas «No son el mismo» TAMBIÉN entran (como movimientos nuevos): antes no
    // contaban, y si el dueño rechazaba todas y desmarcaba las nuevas el botón quedaba apagado con
    // filas por importar.
    val rechazadas = reconciliations.values.count { !it.confirm }
    val importCount = selectedIds.size + confirmedCount + rechazadas
    val canImport = importCount > 0 && !working && !imported && destinationAccount != null

    fun import() {
        val acct = destinationAccount ?: return
        working = true; error = null
        coroutine.launch {
            runCatching {
                val decision = ImportDecision(
                    statementId = result.statementId,
                    accountId = acct.id,
                    bankName = result.bankName,
                    period = result.period,
                    imports = filasPorImportar.filter { it.id in selectedIds },
                    reconciliations = reconciliations.values.toList(),
                    skipped = filasPorImportar.map { it.id }.filter { it !in selectedIds },
                    // El papel que la lectura archivó viaja de vuelta para que el server le
                    // cuelgue ESTA cuenta. Al subirlo todavía no se sabía cuál era —se elige
                    // acá—, así que sin este viaje de ida y vuelta todo extracto archivado se
                    // quedaba sin cuenta para siempre.
                    documentoId = result.documentoId,
                )
                Repositories.wallets.importStatement(decision)
            }.onSuccess {
                working = false
                imported = true
                // Ola 2 #1: pop, no push — coherente con SMS (evita reimportar el mismo extracto
                // si la ‹ de Transacciones vuelve acá).
                goBack(Screen.Transactions())
            }.onFailure {
                working = false
                error = it.toUserMessage()
            }
        }
    }

    /**
     * **«Anotar los N»**: los cargos y abonos del banco tildados, de un toque, sin esperar al resto
     * del extracto. Es un importe propio —con la misma cuenta y el mismo papel—, así que se deshace
     * aparte desde «Extractos importados». Lo que queda en pantalla se importa después con el botón
     * de siempre, sin estas filas.
     */
    fun anotarLosCargos() {
        val acct = destinationAccount ?: return
        val filas = cargosTildados
        if (filas.isEmpty()) return
        working = true; error = null
        coroutine.launch {
            runCatching {
                Repositories.wallets.importStatement(
                    ImportDecision(
                        statementId = result.statementId,
                        accountId = acct.id,
                        bankName = result.bankName,
                        period = result.period,
                        imports = filas,
                        reconciliations = emptyList(),
                        skipped = emptyList(),
                        documentoId = result.documentoId,
                    ),
                )
            }.onSuccess {
                working = false
                cargosAnotados = cargosAnotados + filas.map { it.id }
            }.onFailure {
                working = false
                error = it.toUserMessage()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60 · F22: encabezado único; vuelve a Documentos si no hay historial de navegación.
        // Ola B, tarea 7: era Screen.Extractos, que salió de Más — «Importar movimientos» se
        // llega desde Documentos ahora, y es ahí donde tiene sentido volver.
        MinScreenHeader(
            title = "${result.bankName} · ${result.period}",
            leading = HeaderLeading.Back(fallback = Screen.Documentos),
            subtitle = "${result.newTransactions.size} nuevas · ${result.matches.size} coincidencias",
        )
        Spacer(Modifier.height(12.dp))

        // **El destino, ahora elegible con el dedo.** Antes era un chip de solo lectura que además
        // solo se dibujaba si Movi había resuelto algo — y resolvía siempre, porque el último
        // respaldo agarraba la primera cuenta de la lista. Ahora se dibuja siempre: cuando no hay
        // candidata lo dice, en vez de dejar el botón apagado sin explicar por qué.
        val sinCuenta = destinationAccount == null
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { eligiendoCuenta = !eligiendoCuenta }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Destino:", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Text(
                    destinationAccount?.name ?: "Elige la cuenta",
                    style = Movi.textos.apoyo,
                    color = if (sinCuenta) Movi.colores.aviso else Movi.colores.marca,
                    fontWeight = FontWeight.Medium,
                    // `fill = false` y no un `Spacer` con peso: un segundo hijo pesado le
                    // recortaría el ancho al chip a la mitad de la fila, y «Bancolombia Ahorros»
                    // se partiría en dos renglones por dejar «Cambiar» pegado al borde.
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(4.dp))
                        .background((if (sinCuenta) Movi.colores.aviso else Movi.colores.marca).copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    if (eligiendoCuenta) "Cerrar" else "Cambiar",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
            }
            // En renglón propio y no al lado del chip: a 375 dp, «Destino: Bancolombia Ahorros La
            // puso Movi Cambiar» no entra en una línea, y lo primero que se recorta es justamente
            // lo que hay que leer.
            avisoDeLaCuentaDelBanco(destino.origen, queLoDijo = "el extracto")?.let { aviso ->
                Text(aviso, style = Movi.textos.apoyo, color = Movi.colores.textoApagado, modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (eligiendoCuenta) {
            MinCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                variant = MinCardVariant.Default,
                padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                ListaDeCuentasElegibles(
                    // `conservar` sostiene lo que él haya sacado del «Ver todas»: sin eso, la
                    // cuenta que acaba de elegir se escondería sola al reabrir el selector.
                    cuentas = cuentasPara(
                        accounts,
                        UsoDeCuenta.CUENTA_DEL_EXTRACTO,
                        conservar = destinationAccount?.id,
                    ),
                    uso = UsoDeCuenta.CUENTA_DEL_EXTRACTO,
                    selectedId = destinationAccount?.id,
                    onPick = { id ->
                        cuentaElegida = id
                        eligiendoCuenta = false
                    },
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            // Matches section
            if (result.matches.isNotEmpty()) {
                item {
                    Text(
                        "POSIBLES DUPLICADOS",
                        style = Movi.textos.rotulo, color = Movi.colores.aviso,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(result.matches, key = { it.parsed.id }) { match ->
                    ReconciliationCard(
                        match = match,
                        decision = reconciliations[match.parsed.id],
                        onConfirm = { dec -> reconciliations[match.parsed.id] = dec },
                        onReject = {
                            reconciliations[match.parsed.id] = ReconciliationDecision(
                                parsedId = match.parsed.id,
                                existingEventId = match.existingEventId,
                                confirm = false,
                                categorySource = FieldSource.STATEMENT,
                                descriptionSource = FieldSource.STATEMENT,
                                merchantSource = FieldSource.STATEMENT,
                                parsed = match.parsed,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Los cargos y abonos del banco, aparte y antes de las compras: son los que nadie revisa
            // uno por uno.
            if (cargos.isNotEmpty()) {
                item(key = "cargos_del_banco") {
                    BloqueDeCargosDelBanco(
                        cuantos = filasDelBanco.size,
                        tildados = cargosTildados.size,
                        anotados = cargosAnotados.size,
                        puedeAnotar = cargosTildados.isNotEmpty() && destinationAccount != null && !working && !imported,
                        onAnotar = ::anotarLosCargos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(filasDelBanco, key = { it.id }) { tx ->
                    NewTransactionRow(
                        tx = tx,
                        checked = tx.id in selectedIds,
                        onToggle = { elegidasAMano[tx.id] = tx.id !in selectedIds },
                        nota = estadosDeLosCargos[tx.id]?.let(::notaDelCargo),
                        notaEsAviso = estadosDeLosCargos[tx.id] is EstadoDelCargo.PuedeEstarSumado,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }

            // New transactions section
            if (filasNuevas.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "NUEVAS TRANSACCIONES",
                            style = Movi.textos.rotulo, color = Movi.colores.textoMedio,
                        )
                        val allSelected = filasNuevas.all { it.id in selectedIds }
                        Text(
                            if (allSelected) "Deseleccionar todas" else "Seleccionar todas",
                            style = Movi.textos.apoyo, color = Movi.colores.marca,
                            modifier = Modifier.clickable {
                                filasNuevas.forEach { elegidasAMano[it.id] = !allSelected }
                            },
                        )
                    }
                }
                items(filasNuevas, key = { it.id }) { tx ->
                    NewTransactionRow(
                        tx = tx,
                        checked = tx.id in selectedIds,
                        onToggle = { elegidasAMano[tx.id] = tx.id !in selectedIds },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // Error message
        error?.let {
            Text(
                it, style = Movi.textos.apoyo, color = Movi.colores.sale,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (imported) {
            Text(
                "Este extracto ya se importó",
                style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Sticky bottom bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Movi.colores.tarjeta)
                .padding(16.dp),
        ) {
            if (sinRevisar > 0) {
                Text(
                    if (sinRevisar == 1) "1 coincidencia sin revisar no se va a importar ni a conciliar. Si es otra compra, toca «No son el mismo»."
                    else "$sinRevisar coincidencias sin revisar no se van a importar ni a conciliar. Si son otras compras, toca «No son el mismo».",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.aviso,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Button(
                onClick = ::import,
                enabled = canImport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Movi.colores.marca),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (working) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "Importar $importCount seleccionada${if (importCount != 1) "s" else ""}",
                        style = Movi.textos.titulo, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * El encabezado del bloque «Cargos y abonos del banco (N)», con su botón para anotar los tildados de
 * un toque. Después de anotarlos, dice cuántos quedaron anotados.
 */
@Composable
private fun BloqueDeCargosDelBanco(
    cuantos: Int,
    tildados: Int,
    anotados: Int,
    puedeAnotar: Boolean,
    onAnotar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Cargos y abonos del banco ($cuantos)",
            style = Movi.textos.rotulo, color = Movi.colores.textoMedio,
        )
        Text(
            "Impuestos, comisiones e intereses que el banco cobra o abona solo. Destilda los que no quieras anotar.",
            style = Movi.textos.apoyo, color = Movi.colores.textoApagado,
        )
        if (anotados > 0) {
            Text(
                if (anotados == 1) "Anotaste 1 cargo del banco." else "Anotaste $anotados cargos y abonos del banco.",
                style = Movi.textos.apoyo, color = Movi.colores.entra, fontWeight = FontWeight.Medium,
            )
        }
        if (cuantos > 0) {
            OutlinedButton(
                onClick = onAnotar,
                enabled = puedeAnotar,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    if (tildados == 1) "Anotar 1" else "Anotar los $tildados",
                    style = Movi.textos.apoyo, color = if (puedeAnotar) Movi.colores.marca else Movi.colores.textoApagado,
                )
            }
        }
    }
}

/** Lo que se dice debajo de un cargo que ya puede estar anotado; nada si es nuevo. */
private fun notaDelCargo(estado: EstadoDelCargo): String? = when (estado) {
    EstadoDelCargo.Nuevo -> null
    is EstadoDelCargo.YaAnotado -> "Ya anotado en «${estado.en.nombre}»"
    is EstadoDelCargo.PuedeEstarSumado ->
        "Puede que ya lo hayas anotado sumado: «${estado.en.nombre}» por ${formatMoney(estado.en.monto, estado.en.moneda)}"
}

@Composable
private fun NewTransactionRow(
    tx: ParsedTransaction,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    nota: String? = null,
    notaEsAviso: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Movi.colores.tarjeta)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = if (checked) "Seleccionado" else "No seleccionado",
            tint = if (checked) Movi.colores.marca else Movi.colores.textoMedio,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.merchant, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            Text("${tx.category} · ${fechaEnPalabras(tx.date, hoyEnAppZone())}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            if (nota != null) {
                Text(
                    nota,
                    style = Movi.textos.apoyo,
                    color = if (notaEsAviso) Movi.colores.aviso else Movi.colores.textoApagado,
                )
            }
        }
        val amountColor = if (tx.type == TransactionType.INCOME) Movi.colores.entra else Movi.colores.sale
        val prefix = if (tx.type == TransactionType.INCOME) "+" else "−"
        Text(
            "$prefix${formatMoney(tx.amount, tx.currency)}",
            style = Movi.textos.monto, fontWeight = FontWeight.SemiBold, color = amountColor,
        )
    }
}

@Composable
private fun ReconciliationCard(
    match: ReconciliationMatch,
    decision: ReconciliationDecision?,
    onConfirm: (ReconciliationDecision) -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var categorySource by remember { mutableStateOf(FieldSource.STATEMENT) }
    var descriptionSource by remember { mutableStateOf(FieldSource.STATEMENT) }
    var merchantSource by remember { mutableStateOf(FieldSource.MANUAL) }

    val isDecided = decision != null
    val borderColor = if (isDecided && decision!!.confirm) Movi.colores.entra else Movi.colores.aviso

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Movi.colores.tarjeta)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Badge + amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Ola 2 #5 (F11): glifo escrito como texto → ícono Material (salía como ▯ en la web).
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Movi.colores.aviso, modifier = Modifier.size(11.dp))
                Text(
                    "POSIBLE DUPLICADO",
                    style = Movi.textos.rotulo, color = Movi.colores.aviso,
                )
            }
            val amtColor = if (match.parsed.type == TransactionType.INCOME) Movi.colores.entra else Movi.colores.sale
            val prefix = if (match.parsed.type == TransactionType.INCOME) "+" else "−"
            Text(
                "$prefix${formatMoney(match.parsed.amount, match.parsed.currency)}",
                style = Movi.textos.apoyo, fontWeight = FontWeight.Bold, color = amtColor,
            )
        }

        // Column headers
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(80.dp))
            Text(
                "MANUAL", style = Movi.textos.rotulo, color = Movi.colores.marca,
                modifier = Modifier.weight(1f),
            )
            Text(
                "EXTRACTO", style = Movi.textos.rotulo, color = Movi.colores.entreCuentas,
                modifier = Modifier.weight(1f),
            )
        }

        // Merchant row (differs always since bank names are messy)
        FieldRow(
            label = "Comercio",
            manualValue = match.existingEvent.merchant ?: match.existingEvent.description,
            statementValue = match.parsed.merchant,
            selected = merchantSource,
            onSelectManual = { merchantSource = FieldSource.MANUAL },
            onSelectStatement = { merchantSource = FieldSource.STATEMENT },
        )

        // Category row (only if different)
        if (match.parsed.category != match.existingEvent.category) {
            FieldRow(
                label = "Categoría",
                manualValue = match.existingEvent.category,
                statementValue = match.parsed.category,
                selected = categorySource,
                onSelectManual = { categorySource = FieldSource.MANUAL },
                onSelectStatement = { categorySource = FieldSource.STATEMENT },
            )
        }

        // Description row (only if extracto has one and differs)
        val existDesc = match.existingEvent.description
        val parsedDesc = match.parsed.description
        if (parsedDesc.isNotBlank() && parsedDesc != existDesc) {
            FieldRow(
                label = "Descripción",
                manualValue = existDesc.ifBlank { "—" },
                statementValue = parsedDesc,
                selected = descriptionSource,
                onSelectManual = { descriptionSource = FieldSource.MANUAL },
                onSelectStatement = { descriptionSource = FieldSource.STATEMENT },
            )
        }

        if (!isDecided) {
            Text(
                "Toca cada campo para cambiar la fuente",
                style = Movi.textos.apoyo, color = Movi.colores.textoApagado,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Action buttons
        if (!isDecided) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("No son el mismo", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
                Button(
                    onClick = {
                        onConfirm(
                            ReconciliationDecision(
                                parsedId = match.parsed.id,
                                existingEventId = match.existingEventId,
                                confirm = true,
                                categorySource = categorySource,
                                descriptionSource = descriptionSource,
                                merchantSource = merchantSource,
                                parsed = match.parsed,
                            )
                        )
                    },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Movi.colores.marca),
                ) {
                    Text("Confirmar reconciliación", style = Movi.textos.apoyo, color = Color.White)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Ola 2 #5 (F11): "✓"/"→" como texto suelto → íconos Material.
                if (decision!!.confirm) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Movi.colores.entra, modifier = Modifier.size(13.dp))
                } else {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Movi.colores.textoMedio, modifier = Modifier.size(13.dp))
                }
                Text(
                    if (decision.confirm) "Reconciliado" else "Se importará como nuevo",
                    style = Movi.textos.apoyo,
                    color = if (decision.confirm) Movi.colores.entra else Movi.colores.textoMedio,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    manualValue: String,
    statementValue: String,
    selected: FieldSource,
    onSelectManual: () -> Unit,
    onSelectStatement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.width(80.dp))
        FieldCell(
            value = manualValue,
            active = selected == FieldSource.MANUAL,
            onClick = onSelectManual,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
        )
        FieldCell(
            value = statementValue,
            active = selected == FieldSource.STATEMENT,
            onClick = onSelectStatement,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
    }
}

@Composable
private fun FieldCell(
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (active) Movi.colores.marca else Color.Transparent
    val bgColor = if (active) Movi.colores.marca.copy(alpha = 0.08f) else Movi.colores.tarjeta
    Text(
        value,
        style = Movi.textos.apoyo,
        color = if (active) Movi.colores.texto else Movi.colores.textoMedio,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
