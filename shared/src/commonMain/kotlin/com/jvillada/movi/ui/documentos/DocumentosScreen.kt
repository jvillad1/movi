package com.jvillada.movi.ui.documentos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.MAX_DOCUMENTO_BYTES
import com.jvillada.movi.theme.MinBg
import com.jvillada.movi.theme.MinExpense
import com.jvillada.movi.theme.MinPrimary
import com.jvillada.movi.theme.MinSurfaceContainerHigh
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextFaint
import com.jvillada.movi.theme.MinTextMute
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NewItemButton
import com.jvillada.movi.ui.extractos.rememberFilePicker
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.launch

/**
 * **«Documentos»** — los papeles del dueño, guardados en Movi.
 *
 * El pedido: *«me gustaría que guardemos en Movi extractos y documentos en algún lugar y los
 * podamos listar y acceder desde el sitio y la app»*.
 *
 * Hasta acá el importador de extractos recibía el PDF, lo parseaba y **tiraba el archivo**:
 * quedaban los movimientos y se perdía el papel del que salieron — que es justo lo que hace
 * falta el día que una cifra no cuadra con el banco. Ahora todo extracto que pasa por el
 * importador se archiva solo, y esta pantalla además deja subir lo que no es extracto: la
 * nómina, la escritura, la carta del banco.
 *
 * ### Abrir, no descargar
 *
 * Tocar una fila pide un permiso de descarga —una URL que dura cinco minutos, ver
 * `JwtConfig.makeDownloadToken`— y se la entrega al sistema: el navegador abre el visor de PDF,
 * el teléfono abre la app que corresponda. No se baja el archivo a mano ni se guarda una copia
 * local: son megas por documento y el valor está en poder mirarlo, no en tenerlo dos veces.
 *
 * Por lo mismo esta pantalla **no funciona sin señal**, y lo dice. El espejo local de Movi existe
 * para que las cifras estén sin red; listar papeles que no se van a poder abrir sería una lista
 * que miente.
 */
@Composable
fun DocumentosScreen(onNavigate: (Screen) -> Unit) {
    var documentos by remember { mutableStateOf<List<Documento>?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var subiendo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    // Borrar pide confirmación, y no es ceremonia: el server hace un `delete` duro, no hay
    // papelera, y los documentos NO se espejan en local — o sea que no queda ninguna copia. El
    // «Borrar» vive a milímetros de «Abrir» dentro de una fila que además es clickable entera: un
    // toque gordo en el teléfono se llevaba la escritura del apartamento, sin vuelta atrás.
    var aBorrar by remember { mutableStateOf<Documento?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutine = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(refreshKey) {
        cargando = true
        runCatching { Repositories.wallets.getDocuments() }
            .onSuccess { documentos = it }
            .onFailure { error = it.toUserMessage() }
        cargando = false
    }

    val elegirArchivo = rememberFilePicker { nombre, bytes, mime ->
        // El tope se comprueba ACÁ además de en el server: subir 30 MB por datos móviles para
        // que el server conteste «pesa de más» es cobrarle al dueño el error dos veces.
        if (bytes.size > MAX_DOCUMENTO_BYTES) {
            error = "«$nombre» pesa ${pesoLegible(bytes.size.toLong())} y el máximo es " +
                "${pesoLegible(MAX_DOCUMENTO_BYTES)}"
            return@rememberFilePicker
        }
        subiendo = true
        coroutine.launch {
            runCatching {
                Repositories.wallets.uploadDocument(
                    fileName = nombre,
                    bytes = bytes,
                    mimeType = mime,
                    // El tipo se adivina por el nombre. **Hoy no se puede corregir después** —no
                    // hay selector al subir ni ruta para editarlo— así que un archivo mal
                    // nombrado queda en «Otros». Se deja así a propósito en esta primera versión:
                    // el tipo solo decide bajo qué encabezado aparece, y agregar un paso a la
                    // acción más frecuente de la pantalla cuesta más que el error que evita. El
                    // día que moleste, la salida es un `PATCH`, no preguntar antes de subir.
                    tipo = tipoSugeridoPara(nombre),
                )
            }
                .onSuccess { refreshKey++ }
                .onFailure { error = it.toUserMessage() }
            subiendo = false
        }
    }

    fun abrir(doc: Documento) {
        coroutine.launch {
            runCatching { Repositories.wallets.getDocumentLink(doc.id) }
                .onSuccess { uriHandler.openUri(it.url) }
                .onFailure { error = it.toUserMessage() }
        }
    }

    fun borrar(doc: Documento) {
        coroutine.launch {
            runCatching { Repositories.wallets.deleteDocument(doc.id) }
                .onSuccess { refreshKey++; aBorrar = null }
                .onFailure { error = it.toUserMessage(); aBorrar = null }
        }
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        error = null
    }

    Box(modifier = Modifier.fillMaxSize().background(MinBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinScreenHeader(
                title = "Documentos",
                leading = HeaderLeading.Back(fallback = Screen.Mas),
                action = if (!documentos.isNullOrEmpty()) {
                    { NewItemButton(label = "Subir archivo", onClick = elegirArchivo) }
                } else null,
            )
            if (cargando || subiendo) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val lista = documentos
            when {
                // «Todavía no guardaste nada» es una afirmación sobre lo que el dueño tiene, y no
                // se hace antes de que la lectura conteste. Misma regla que el Inicio.
                lista == null -> Spacer(Modifier.height(1.dp))

                lista.isEmpty() -> Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Aquí se guardan tus extractos, nóminas, contratos y cualquier papel " +
                            "que quieras tener a mano. Los extractos que importes se archivan solos.",
                        fontSize = 13.sp,
                        color = MinTextMute,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    NewItemButton(label = "Subir archivo", onClick = elegirArchivo, full = true)
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 80.dp),
                ) {
                    porTipo(lista).forEach { (tipo, delTipo) ->
                        item(key = "encabezado-${tipo.name}") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                MinSectionHeader(
                                    title = if (tipo.name == "OTRO") "Otros" else "${nombreDeTipo(tipo)}s",
                                    count = delTipo.size,
                                )
                            }
                        }
                        items(delTipo, key = { it.id }) { doc ->
                            FilaDeDocumento(
                                doc = doc,
                                onAbrir = { abrir(doc) },
                                onBorrar = { aBorrar = doc },
                            )
                        }
                        item(key = "espacio-${tipo.name}") { Spacer(Modifier.height(18.dp)) }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )

        aBorrar?.let { doc ->
            ConfirmarBorrado(
                doc = doc,
                onCancelar = { aBorrar = null },
                onConfirmar = { borrar(doc) },
            )
        }
    }
}

/**
 * La confirmación de borrado. Dice **qué** se borra y que no hay vuelta atrás — las dos cosas que
 * uno quiere leer antes de tocar el botón rojo.
 */
@Composable
private fun ConfirmarBorrado(doc: Documento, onCancelar: () -> Unit, onConfirmar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onCancelar),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinSurfaceContainerHigh)
                .clickable(enabled = false) {}
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text("¿Borrar este documento?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MinText)
            Spacer(Modifier.height(8.dp))
            Text(doc.nombre, fontSize = 14.sp, color = MinText)
            Spacer(Modifier.height(4.dp))
            Text(
                "Se borra del todo. Movi no guarda una copia y no se puede deshacer.",
                fontSize = 12.5.sp,
                color = MinTextMute,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Cancelar",
                    fontSize = 14.sp,
                    color = MinTextMute,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onCancelar)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Borrar",
                    fontSize = 14.sp,
                    color = MinExpense,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onConfirmar)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun FilaDeDocumento(doc: Documento, onAbrir: () -> Unit, onBorrar: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onAbrir)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(doc.nombre, fontSize = 14.sp, color = MinText, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                // Peso, fecha y período en un renglón: son los tres datos con los que uno
                // reconoce cuál de tres extractos parecidos es el que busca.
                Text(
                    text = listOfNotNull(
                        pesoLegible(doc.bytes),
                        etiquetaDeFecha(fechaDeEpoch(doc.subidoEn), hoyEnAppZone()),
                        doc.periodo,
                    ).joinToString(" · "),
                    fontSize = 11.5.sp,
                    color = MinTextFaint,
                )
                doc.notas?.takeIf { it.isNotBlank() }?.let { nota ->
                    Spacer(Modifier.height(2.dp))
                    Text(nota, fontSize = 11.5.sp, color = MinTextMute)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Abrir",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MinPrimary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Borrar",
                fontSize = 12.sp,
                color = MinExpense,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBorrar)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Hairline()
    }
}
