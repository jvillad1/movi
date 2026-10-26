package com.jvillada.movi.ui.extractos

import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.jvillada.movi.ui.LocalGoBack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone

@Composable
fun ImportDetailScreen(onNavigate: (Screen) -> Unit, importId: String) {
    var detail by remember { mutableStateOf<StatementImportDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val goBack = LocalGoBack.current
    val coroutine = rememberCoroutineScope()
    var pidiendoDeshacer by remember { mutableStateOf(false) }
    var deshaciendo by remember { mutableStateOf(false) }
    var errorDeDeshacer by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(importId) {
        loading = true
        error = null
        runCatching { Repositories.wallets.getStatementImportDetail(importId) }
            .onSuccess { detail = it; loading = false }
            .onFailure { t ->
                if (t is CancellationException) throw t
                loading = false
                error = "No pude cargar el detalle: ${t.message ?: "error"}"
            }
    }

    Column(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        // F60 · F22: encabezado único; el detalle vuelve a Documentos si no hay historial de
        // navegación. Ola B, tarea 7: era Screen.Extractos — la sección «Importaciones» que
        // abre esta pantalla ahora vive en Documentos, no en Extractos (que salió de Más).
        MinScreenHeader(
            title = "Detalle de importación",
            leading = HeaderLeading.Back(fallback = Screen.Documentos),
        )
        Spacer(Modifier.height(Espacio.s))

        when {
            loading -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla),
                color = Movi.colores.marca,
                trackColor = Movi.colores.tarjeta,
            )
            error != null -> Text(
                error!!,
                style = Movi.textos.cuerpo, color = Movi.colores.sale,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(Espacio.l),
            )
            detail != null -> {
                errorDeDeshacer?.let {
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale, modifier = Modifier.padding(horizontal = Medida.margenDePantalla))
                }
                // **Deshacer el importe entero.** Antes un extracto importado en la cuenta equivocada
                // solo se arreglaba anulando fila por fila.
                Boton(
                    "Deshacer este importe",
                    onClick = { pidiendoDeshacer = true },
                    variante = VarianteDeBoton.Peligro,
                    tamano = TamanoDeBoton.S,
                    habilitado = !deshaciendo,
                    modifier = Modifier.padding(horizontal = Medida.margenDePantalla),
                )
                ImportDetailContent(detail = detail!!)
            }
        }
    }
    if (pidiendoDeshacer) {
        ConfirmarEnHoja(
            pregunta = "¿Deshacer este importe?",
            detalle = "Se anulan los movimientos que creó este extracto. Lo que ya tenías anotado y solo se concilió se queda. No se puede deshacer.",
            textoConfirmar = "Deshacer",
            ocupado = deshaciendo,
            onConfirmar = {
                deshaciendo = true
                errorDeDeshacer = null
                coroutine.launch {
                    runCatching { Repositories.wallets.deleteStatementImport(importId) }
                        .onSuccess { pidiendoDeshacer = false; goBack(Screen.Documentos) }
                        .onFailure { errorDeDeshacer = it.toUserMessage(); pidiendoDeshacer = false }
                    deshaciendo = false
                }
            },
            onCancelar = { pidiendoDeshacer = false },
        )
    }
}

@Composable
private fun ImportDetailContent(detail: StatementImportDetail) {
    val imp = detail.import
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        item(key = "header") {
            Spacer(Modifier.height(Espacio.xxs))
            ImportSummaryHeader(imp)
        }

        if (detail.events.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No se encontraron movimientos",
                    style = Movi.textos.cuerpo, color = Movi.colores.textoMedio,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Espacio.xxl),
                )
            }
        } else {
            item(key = "section-label") {
                Seccion(titulo = "Movimientos", conteo = detail.events.size)
            }
            item(key = "events-card") {
                Superficie(relleno = RellenoDeSuperficie.Lista) {
                    detail.events.forEachIndexed { i, event ->
                        if (i > 0) Separador(SangriaDeSeparador.Margen)
                        ImportEventRow(event)
                    }
                }
            }
        }

        item(key = "bottom-spacer") { Spacer(Modifier.height(Espacio.xl)) }
    }
}

@Composable
private fun ImportSummaryHeader(imp: StatementImport) {
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Text("${imp.bankName} · ${imp.period}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Espacio.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${imp.importedCount} importadas · ${imp.reconciledCount} reconciliadas",
                style = Movi.textos.cuerpo, fontWeight = FontWeight.SemiBold, color = Movi.colores.texto,
            )
            Text(epochToShortDate(imp.importedAt), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

/** Un movimiento del importe: una [Fila] del sistema con la categoría y la fecha, y su [Cifra]. */
@Composable
private fun ImportEventRow(event: FinancialEvent) {
    val isIncome = event.type == TransactionType.INCOME
    Fila(
        titulo = event.description.ifBlank { event.merchant ?: "Sin descripción" },
        subtitulo = "${event.category} · ${epochToShortDate(event.timestamp)}",
        final = {
            // Ola 2a: el gasto en el color del texto; el signo «−» ya lo dice.
            Cifra(
                if (isIncome) event.amount else -event.amount,
                moneda = event.currency,
                tono = if (isIncome) TonoDeCifra.Ingreso else TonoDeCifra.Neutro,
            )
        },
    )
}

// «Hoy», «23 de agosto»: en palabras, como el resto de la app, y no el ISO de la fecha.
private fun epochToShortDate(millis: Long): String =
    etiquetaDeFecha(fechaDeEpoch(millis), hoyEnAppZone())
