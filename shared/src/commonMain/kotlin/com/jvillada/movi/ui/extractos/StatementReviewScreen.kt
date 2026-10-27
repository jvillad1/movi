package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.*
import com.jvillada.movi.theme.*
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Casilla
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.ui.fecha.hoyEnAppZone


@Composable
fun StatementReviewScreen(
    onNavigate: (Screen) -> Unit,
    result: StatementParseResult,
) {
    // El extracto de un crédito no trae movimientos: trae cómo va la deuda, y su revisión es otra.
    result.extractoDeCredito?.let { propuesta ->
        RevisionDelExtractoDeCredito(propuesta, titulo = listOf(result.bankName, result.period).filter { it.isNotBlank() }.joinToString(" · "))
        return
    }
    // El extracto que solo dice cuánto hay (Skandia, la AFC): la propuesta de poner ese saldo.
    result.extractoDeSaldo?.let { propuesta ->
        RevisionDelExtractoDeSaldo(propuesta, titulo = listOf(result.bankName, result.period).filter { it.isNotBlank() }.joinToString(" · "))
        return
    }
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
        Spacer(Modifier.height(Espacio.s))

        // **El destino, ahora elegible con el dedo.** Antes era un chip de solo lectura que además
        // solo se dibujaba si Movi había resuelto algo — y resolvía siempre, porque el último
        // respaldo agarraba la primera cuenta de la lista. Ahora se dibuja siempre: cuando no hay
        // candidata lo dice, en vez de dejar el botón apagado sin explicar por qué.
        val sinCuenta = destinationAccount == null
        Column(modifier = Modifier.padding(horizontal = Espacio.m).padding(bottom = Espacio.xs)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { eligiendoCuenta = !eligiendoCuenta }
                    .padding(vertical = Espacio.xxs),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Destino:", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                // Vuelta de diseño 20: la `Pildora` del sistema (el lavado de su tono y el texto en
                // ese tono, lo mismo que este chip hacía a mano). `fill = false` y no un `Spacer`
                // con peso: un segundo hijo pesado le recortaría el ancho a la mitad de la fila.
                Pildora(
                    destinationAccount?.name ?: "Elige la cuenta",
                    tono = if (sinCuenta) TonoDePildora.Aviso else TonoDePildora.Marca,
                    modifier = Modifier.weight(1f, fill = false),
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
                Text(aviso, style = Movi.textos.apoyo, color = Movi.colores.textoApagado, modifier = Modifier.padding(top = Espacio.xxs))
            }
        }
        if (eligiendoCuenta) {
            MinCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Espacio.m)
                    .padding(bottom = Espacio.xs),
                variant = MinCardVariant.Default,
                padding = PaddingValues(horizontal = Espacio.m, vertical = Espacio.xxs),
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
                    Seccion(
                        "Posibles duplicados",
                        conteo = result.matches.size,
                        modifier = Modifier.padding(horizontal = Espacio.m).padding(top = Espacio.s),
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
                        modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.xxs),
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
                            .padding(horizontal = Espacio.m)
                            .padding(top = Espacio.s),
                    )
                }
                items(filasDelBanco, key = { it.id }) { tx ->
                    NewTransactionRow(
                        tx = tx,
                        checked = tx.id in selectedIds,
                        onToggle = { elegidasAMano[tx.id] = tx.id !in selectedIds },
                        nota = estadosDeLosCargos[tx.id]?.let(::notaDelCargo),
                        notaEsAviso = estadosDeLosCargos[tx.id] is EstadoDelCargo.PuedeEstarSumado,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // New transactions section
            if (filasNuevas.isNotEmpty()) {
                item {
                    val allSelected = filasNuevas.all { it.id in selectedIds }
                    Seccion(
                        "Nuevas transacciones",
                        conteo = filasNuevas.size,
                        accion = if (allSelected) "Deseleccionar todas" else "Seleccionar todas",
                        onAccion = { filasNuevas.forEach { elegidasAMano[it.id] = !allSelected } },
                        modifier = Modifier.padding(horizontal = Espacio.m).padding(top = Espacio.s),
                    )
                }
                items(filasNuevas, key = { it.id }) { tx ->
                    NewTransactionRow(
                        tx = tx,
                        checked = tx.id in selectedIds,
                        onToggle = { elegidasAMano[tx.id] = tx.id !in selectedIds },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { Spacer(Modifier.height(Medida.botonFlotante + Medida.entreSecciones)) }
        }

        // Error message
        error?.let {
            Text(
                it, style = Movi.textos.apoyo, color = Movi.colores.sale,
                modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.xxs),
            )
        }
        if (imported) {
            Text(
                "Este extracto ya se importó",
                style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
                modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.xxs),
            )
        }

        // Sticky bottom bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Movi.colores.tarjeta)
                .padding(Espacio.m),
        ) {
            if (sinRevisar > 0) {
                Text(
                    if (sinRevisar == 1) "1 coincidencia sin revisar no se va a importar ni a conciliar. Si es otra compra, toca «No son el mismo»."
                    else "$sinRevisar coincidencias sin revisar no se van a importar ni a conciliar. Si son otras compras, toca «No son el mismo».",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.aviso,
                    modifier = Modifier.padding(bottom = Espacio.s),
                )
            }
            // Vuelta de diseño 20: el `Boton` principal del sistema. Era un `Button` de Material con el
            // texto y la ruedita en `Color.White`, que en el tema oscuro (la marca es lavanda) no se
            // leía; el `Boton` pone encima `sobreMarca`, y la ruedita no le cambia el ancho.
            Boton(
                "Importar $importCount seleccionada${if (importCount != 1) "s" else ""}",
                onClick = ::import,
                habilitado = canImport,
                cargando = working,
                anchoCompleto = true,
            )
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
    // `Seccion` ya trae su espacio de abajo: el resto del bloque va separado por [Espacio.xs].
    Column(modifier = modifier) {
        Seccion("Cargos y abonos del banco", conteo = cuantos)
        Text(
            "Impuestos, comisiones e intereses que el banco cobra o abona solo. Destilda los que no quieras anotar.",
            style = Movi.textos.apoyo, color = Movi.colores.textoMedio,
        )
        if (anotados > 0) {
            Text(
                if (anotados == 1) "Anotaste 1 cargo del banco." else "Anotaste $anotados cargos y abonos del banco.",
                style = Movi.textos.apoyo, color = Movi.colores.entra,
                modifier = Modifier.padding(top = Espacio.xs),
            )
        }
        if (cuantos > 0) {
            // Vuelta de diseño 20: un `Boton` secundario chico (era un `OutlinedButton` de Material).
            Boton(
                if (tildados == 1) "Anotar 1" else "Anotar los $tildados",
                onClick = onAnotar,
                variante = VarianteDeBoton.Secundario,
                tamano = TamanoDeBoton.S,
                habilitado = puedeAnotar,
                modifier = Modifier.padding(top = Espacio.xs),
            )
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

/**
 * **Una fila del extracto, para tildar o destildar** (vuelta de diseño 20): la `Fila` del sistema con
 * la `Cifra` y la `Casilla` al final, y la fila entera es el toque (`toggleable`, de borde a borde).
 * Era una tarjeta con un ícono de casilla de Material y el monto en un `Text` suelto. La [nota] —«Ya
 * anotado en…»— cuelga debajo, alineada con el título.
 */
@Composable
private fun NewTransactionRow(
    tx: ParsedTransaction,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    nota: String? = null,
    notaEsAviso: Boolean = false,
) {
    val entra = tx.type == TransactionType.INCOME
    Column(
        modifier = modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Fila(
            titulo = tx.merchant,
            subtitulo = "${tx.category} · ${fechaEnPalabras(tx.date, hoyEnAppZone())}",
            final = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    // Ola 2a: el gasto en el color del texto; el signo «−» ya lo dice.
                    Cifra(
                        if (entra) tx.amount else -tx.amount,
                        tono = if (entra) TonoDeCifra.Ingreso else TonoDeCifra.Neutro,
                        moneda = tx.currency,
                    )
                    Casilla(checked)
                }
            },
        )
        if (nota != null) {
            Text(
                nota,
                style = Movi.textos.apoyo,
                color = if (notaEsAviso) Movi.colores.aviso else Movi.colores.textoMedio,
                modifier = Modifier.padding(horizontal = Medida.margenDePantalla).padding(bottom = Espacio.xs),
            )
        }
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

    // Vuelta de diseño 20: una `Superficie` sin borde. El estado lo decía el color del borde (aviso o
    // entra, con un `copy(alpha = 0.5f)` a mano); ahora lo dice la `Pildora` de arriba, en palabras.
    Superficie(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    decision == null -> Pildora("Posible duplicado", TonoDePildora.Aviso)
                    decision.confirm -> Pildora("Reconciliado", TonoDePildora.Exito)
                    else -> Pildora("Se importará como nuevo", TonoDePildora.Neutra)
                }
                val entra = match.parsed.type == TransactionType.INCOME
                Cifra(
                    if (entra) match.parsed.amount else -match.parsed.amount,
                    tono = if (entra) TonoDeCifra.Ingreso else TonoDeCifra.Neutro,
                    moneda = match.parsed.currency,
                )
            }

            // Column headers
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(ANCHO_DEL_ROTULO))
                Text("Manual", style = Movi.textos.apoyo, color = Movi.colores.marca, modifier = Modifier.weight(1f))
                Text("Extracto", style = Movi.textos.apoyo, color = Movi.colores.entreCuentas, modifier = Modifier.weight(1f))
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
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                ) {
                    // Vuelta de diseño 20: dos `Boton` chicos (eran `OutlinedButton` y `Button` de Material,
                    // con el texto en `Color.White`, que sobre la lavanda del tema oscuro no se leía).
                    Boton(
                        "No son el mismo",
                        onClick = onReject,
                        variante = VarianteDeBoton.Secundario,
                        tamano = TamanoDeBoton.S,
                        modifier = Modifier.weight(1f),
                    )
                    Boton(
                        "Sí, es el mismo",
                        tamano = TamanoDeBoton.S,
                        modifier = Modifier.weight(1f),
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
                    )
                }
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
        Text(label, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.width(ANCHO_DEL_ROTULO))
        FieldCell(
            value = manualValue,
            active = selected == FieldSource.MANUAL,
            onClick = onSelectManual,
            modifier = Modifier.weight(1f).padding(end = Espacio.xxs),
        )
        FieldCell(
            value = statementValue,
            active = selected == FieldSource.STATEMENT,
            onClick = onSelectStatement,
            modifier = Modifier.weight(1f).padding(start = Espacio.xxs),
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
    // Sin elegir, en la capa 2: la celda va sobre la `Superficie` (capa 1) y tiene que verse tocable.
    val bgColor = if (active) Movi.colores.lavado(Movi.colores.marca) else Movi.colores.capa2
    Text(
        value,
        style = Movi.textos.apoyo,
        color = if (active) Movi.colores.texto else Movi.colores.textoMedio,
        modifier = modifier
            .clip(RoundedCornerShape(Radio.s))
            .background(bgColor)
            .border(Medida.trazo, borderColor, RoundedCornerShape(Radio.s))
            .clickable(onClick = onClick)
            .padding(horizontal = Espacio.xs, vertical = Espacio.xs),
    )
}

/** El ancho de la columna de rótulos de la comparación («Movi», «Extracto»), para que las cifras queden alineadas. */
private val ANCHO_DEL_ROTULO = Medida.avatar * 2
