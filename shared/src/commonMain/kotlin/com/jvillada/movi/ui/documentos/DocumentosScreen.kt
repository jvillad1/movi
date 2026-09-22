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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.MAX_DOCUMENTO_BYTES
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.cuentasPara
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NewItemButton
import com.jvillada.movi.ui.extractos.TiposDeArchivo
import com.jvillada.movi.ui.extractos.rememberFilePicker
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.components.toUserMessage
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.jvillada.movi.shared.model.EdicionDeDocumento
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.ui.components.ListaDeCuentasElegibles
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.rememberCampoConSeleccion
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
    var cargando by remember { mutableStateOf(true) }  // true de entrada: antes de la primera lectura no se afirma ni vacío ni error
    var subiendo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    // Borrar pide confirmación, y no es ceremonia: el server hace un `delete` duro, no hay
    // papelera, y los documentos NO se espejan en local — o sea que no queda ninguna copia. El
    // «Borrar» vive a milímetros de «Abrir» dentro de una fila que además es clickable entera: un
    // toque gordo en el teléfono se llevaba la escritura del apartamento, sin vuelta atrás.
    var aBorrar by remember { mutableStateOf<Documento?>(null) }
    // Un segundo toque en «Borrar» mientras el primero está en vuelo mandaba otro DELETE, que
    // volvía 404 y mostraba «no encontrado» sobre un borrado que sí se hizo.
    var borrando by remember { mutableStateOf(false) }
    var aEditar by remember { mutableStateOf<Documento?>(null) }
    // El archivo elegido, todavía sin subir: la hoja de subida pregunta qué es y de qué cuenta.
    var aSubir by remember { mutableStateOf<ArchivoElegido?>(null) }
    // Las cuentas, para los dos selectores. Si la lectura falla la pantalla sigue andando: sin
    // cuentas el selector dice «No tienes cuentas todavía» y el papel se sube sin cuenta, que es
    // exactamente lo que pasaba antes de que esto existiera.
    var cuentas by remember { mutableStateOf(emptyList<Account>()) }
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

    LaunchedEffect(Unit) {
        runCatching { Repositories.wallets.getAccounts() }.onSuccess { cuentas = it }
    }

    val elegirArchivo = rememberFilePicker(TiposDeArchivo.TODOS) { nombre, bytes, mime ->
        // El tope se comprueba ACÁ además de en el server: subir 30 MB por datos móviles para
        // que el server conteste «pesa de más» es cobrarle al dueño el error dos veces.
        if (bytes.size > MAX_DOCUMENTO_BYTES) {
            error = "«$nombre» pesa ${pesoLegible(bytes.size.toLong())} y el máximo es " +
                "${pesoLegible(MAX_DOCUMENTO_BYTES)}"
            return@rememberFilePicker
        }
        aSubir = ArchivoElegido(nombre = nombre, bytes = bytes, mimeType = mime)
    }

    fun subir(archivo: ArchivoElegido, tipo: TipoDeDocumento, cuentaId: String?) {
        aSubir = null
        subiendo = true
        coroutine.launch {
            runCatching {
                Repositories.wallets.uploadDocument(
                    fileName = archivo.nombre,
                    bytes = archivo.bytes,
                    mimeType = archivo.mimeType,
                    tipo = tipo,
                    accountId = cuentaId,
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
        if (borrando) return
        borrando = true
        coroutine.launch {
            runCatching { Repositories.wallets.deleteDocument(doc.id) }
                .onSuccess { refreshKey++; aBorrar = null }
                .onFailure { error = it.toUserMessage(); aBorrar = null }
            borrando = false
        }
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        error = null
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
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
                // Mientras lee, nada. Si ya terminó y sigue sin lista, la lectura falló: se dice eso
                // con un reintento, en vez de dejar la pantalla en blanco sin salida (el snackbar
                // del error se va solo). Ver [NoSePudoLeer].
                lista == null && cargando -> Spacer(Modifier.height(1.dp))
                lista == null -> NoSePudoLeer(
                    "No pudimos cargar tus documentos",
                    onReintentar = { refreshKey++ },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp),
                )

                lista.isEmpty() -> Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Aquí se guardan tus extractos, nóminas, contratos y cualquier papel " +
                            "que quieras tener a mano. Los extractos que importes se archivan solos.",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
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
                                cuenta = doc.accountId?.let { id -> cuentas.firstOrNull { it.id == id }?.name },
                                onAbrir = { abrir(doc) },
                                onBorrar = { aBorrar = doc },
                                onEditar = { aEditar = doc },
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

        aSubir?.let { archivo ->
            SubirDocumentoSheet(
                archivo = archivo,
                cuentas = cuentas,
                onDismiss = { aSubir = null },
                onSubir = { tipo, cuentaId -> subir(archivo, tipo, cuentaId) },
            )
        }

        aEditar?.let { doc ->
            EditarDocumentoSheet(
                doc = doc,
                cuentas = cuentas,
                onDismiss = { aEditar = null },
                onGuardado = { aEditar = null; refreshKey++ },
            )
        }

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
                .background(Movi.colores.tarjeta)
                .clickable(enabled = false) {}
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text("¿Borrar este documento?", style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            Spacer(Modifier.height(8.dp))
            Text(nombreQueSePartePorSusSeparadores(doc.nombre), style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Spacer(Modifier.height(4.dp))
            Text(
                "Se borra del todo. Movi no guarda una copia y no se puede deshacer.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Cancelar",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onCancelar)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Borrar",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.sale,
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
private fun FilaDeDocumento(
    doc: Documento,
    /** El nombre de la cuenta a la que está colgado, o `null` si no cuelga de ninguna. */
    cuenta: String?,
    onAbrir: () -> Unit,
    onBorrar: () -> Unit,
    onEditar: () -> Unit,
) {
    // El texto arriba a todo el ancho y las acciones en un renglón DEBAJO. Con las tres acciones a
    // la derecha, en un teléfono de ~390 dp se comían un tercio de la fila y el nombre del archivo
    // se partía a mitad de palabra: «Portal_Beneficios_3037_movimi / entos_09_2026.jpeg».
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onAbrir)
                .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
        ) {
            // Con cortes invisibles después de `_`, `-` y `.`: si no entra, parte en un separador.
            // Solo lo que se pinta; `doc.nombre` sigue intacto para editar y para «Abrir».
            Text(
                nombreQueSePartePorSusSeparadores(doc.nombre),
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            // Cuenta, peso, fecha y período en un renglón: son los datos con los que uno
            // reconoce cuál de tres extractos parecidos es el que busca. La cuenta va PRIMERO
            // y no al final: entre «Extracto_08_2026.pdf» repetidos, lo que los distingue es
            // de qué cuenta son, y es lo primero que se corta si la fila no entra.
            Text(
                text = listOfNotNull(
                    cuenta,
                    pesoLegible(doc.bytes),
                    etiquetaDeFecha(fechaDeEpoch(doc.subidoEn), hoyEnAppZone()),
                    doc.periodo,
                ).joinToString(" · "),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
            )
            doc.notas?.takeIf { it.isNotBlank() }?.let { nota ->
                Spacer(Modifier.height(2.dp))
                Text(nota, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            Spacer(Modifier.height(4.dp))
            // Corrido 8 dp a la izquierda para que la PALABRA «Abrir» —no su zona de toque— quede
            // alineada con el nombre de arriba.
            Row(
                modifier = Modifier.offset(x = (-8).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AccionDeFila("Abrir", Movi.colores.marca, FontWeight.Medium, onAbrir)
                AccionDeFila("Editar", Movi.colores.textoMedio, null, onEditar)
                // «Borrar» no borra: abre la confirmación (ver `aBorrar` en la pantalla).
                AccionDeFila("Borrar", Movi.colores.sale, null, onBorrar)
            }
        }
        Hairline()
    }
}

@Composable
private fun AccionDeFila(texto: String, color: Color, peso: FontWeight?, onClick: () -> Unit) {
    Text(
        texto,
        style = Movi.textos.apoyo,
        fontWeight = peso,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Corregir un documento ya subido: cómo se llama, qué es, **de qué cuenta**, de qué período y qué
 * anotaste.
 *
 * Nace de un hueco que la revisión encontró: el tipo se adivinaba por el nombre del archivo y un
 * comentario prometía que «se puede corregir después», pero no existía forma de hacerlo. Un
 * `IMG_4821.jpg` que es la escritura del apartamento quedaba en «Otros» para siempre.
 *
 * La CUENTA llegó por el mismo camino, una revisión después: el campo existía en el modelo desde
 * el primer día y **no había forma de llenarlo**, así que todo papel caía en «Sin cuenta asociada»
 * y preguntarle a Movi «¿qué tienes guardado de la cuenta 2334?» devolvía la lista entera.
 *
 * **No cambia los bytes.** Para reemplazar el archivo se sube otro y se borra este — dejar que un
 * documento cambie de contenido conservando su id es justamente lo que uno no quiere de un
 * archivo que existe para ser prueba de algo.
 */
@Composable
internal fun EditarDocumentoSheet(
    doc: Documento,
    cuentas: List<Account>,
    onDismiss: () -> Unit,
    onGuardado: () -> Unit,
) {
    val coroutine = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(doc.nombre) }
    var tipo by remember { mutableStateOf(doc.tipo) }
    var cuenta by remember { mutableStateOf(doc.accountId) }
    var periodo by remember { mutableStateOf(doc.periodo ?: "") }
    var notas by remember { mutableStateOf(doc.notas ?: "") }
    var guardando by remember { mutableStateOf(false) }
    // El error se pinta ADENTRO de la hoja, no en el snackbar de la pantalla.
    //
    // La primera versión lo mandaba al padre, y la hoja lo tapaba: el `SnackbarHost` se dibuja
    // antes que la hoja dentro del mismo `Box`, y la hoja trae un scrim negro a pantalla completa
    // más un panel opaco. El snackbar vive a 16 dp del borde inferior, o sea justo debajo. Se caía
    // la red, el botón volvía de «Guardando…» a «Guardar», y **no pasaba nada visible** — el dueño
    // no sabía si había guardado. Y `LaunchedEffect(error)` lo limpiaba a los pocos segundos, así
    // que cerrar la hoja después tampoco lo mostraba.
    //
    // Es la convención del resto de las hojas de la app (ver `EditProfileSheet`), no una excepción.
    var error by remember { mutableStateOf<String?>(null) }

    fun guardar() {
        if (guardando || nombre.isBlank()) return
        guardando = true
        error = null
        coroutine.launch {
            runCatching {
                Repositories.wallets.updateDocument(
                    doc.id,
                    EdicionDeDocumento(
                        nombre = nombre.trim(),
                        tipo = tipo,
                        // Igual que el período y las notas: la cadena vacía DESCUELGA el papel de
                        // su cuenta, que es la única forma de deshacer una elección equivocada.
                        accountId = cuenta.orEmpty(),
                        // La cadena vacía BORRA, y es a propósito: es la única forma de sacar una
                        // nota escrita por error. `null` querría decir «no lo toques», que acá
                        // nunca es lo que el dueño quiso al vaciar el campo a mano.
                        periodo = periodo.trim(),
                        notas = notas.trim(),
                    ),
                )
            }
                .onSuccess { onGuardado() }
                .onFailure { guardando = false; error = it.toUserMessage() }
        }
    }

    HojaDeDocumento(onDismiss = onDismiss, cerrarHabilitado = !guardando) {
        CampoDeTexto("NOMBRE", nombre, { nombre = it.take(255) }, "Extracto agosto.pdf")
        Spacer(Modifier.height(14.dp))

        SelectorDeTipo(tipo, enabled = !guardando) { tipo = it }

        Spacer(Modifier.height(14.dp))
        SelectorDeCuenta(cuentas, cuenta, enabled = !guardando) { cuenta = it }

        Spacer(Modifier.height(14.dp))
        CampoDeTexto("PERÍODO", periodo, { periodo = it.take(50) }, "agosto 2026")
        Spacer(Modifier.height(14.dp))
        CampoDeTexto("NOTAS", notas, { notas = it.take(500) }, "Para qué lo guardaste")

        Spacer(Modifier.height(20.dp))
        BotonDeLaHoja(
            texto = if (guardando) "Guardando…" else "Guardar",
            habilitado = !guardando && nombre.isNotBlank(),
        ) { guardar() }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.sale)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CampoDeTexto(
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    marcador: String,
) {
    Text(etiqueta, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Movi.colores.tarjeta)
            .border(1.dp, Movi.colores.borde, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (valor.isEmpty()) {
            Text(marcador, style = Movi.textos.cuerpo, color = Movi.colores.textoApagado, maxLines = 1)
        }
        // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver [esAtajoDeSeleccionarTodo].
        val campo = rememberCampoConSeleccion(valor, onCambio)
        BasicTextField(
            value = campo.valor,
            onValueChange = campo::alCambiar,
            textStyle = Movi.textos.cuerpo.copy(color = Movi.colores.texto),
            cursorBrush = SolidColor(Movi.colores.marca),
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
        )
    }
}

/**
 * Un archivo ya elegido del disco y todavía **sin subir**.
 *
 * Existe porque la subida dejó de ser un solo toque: entre elegir el archivo y mandarlo hay una
 * hoja que pregunta qué es y de qué cuenta (ver [SubirDocumentoSheet]), y los bytes tienen que
 * esperar ahí.
 */
internal class ArchivoElegido(
    val nombre: String,
    val bytes: ByteArray,
    val mimeType: String,
)

/**
 * **Qué es este papel y de qué cuenta**, antes de subirlo.
 *
 * Hasta acá la subida no preguntaba nada: se elegía el archivo y se iba, con el tipo adivinado por
 * el nombre y sin cuenta. El argumento escrito entonces era que agregar un paso a la acción más
 * frecuente costaba más que el error que evitaba, «y el error solo cambia bajo qué encabezado
 * aparece el archivo».
 *
 * Eso dejó de ser cierto cuando el asistente empezó a leer los documentos guardados: la cuenta no
 * es un encabezado, es **lo que hace contestable** «¿qué tienes guardado de la cuenta 2334?». Y
 * nadie la iba a poner después, en una pantalla que solo se abre cuando algo no cuadra.
 *
 * El paso se cobra barato a propósito: todo llega elegido —el tipo adivinado, «Sin cuenta»
 * puesta— así que quien no quiera decidir nada toca «Subir» y listo.
 */
@Composable
internal fun SubirDocumentoSheet(
    archivo: ArchivoElegido,
    cuentas: List<Account>,
    onDismiss: () -> Unit,
    onSubir: (TipoDeDocumento, String?) -> Unit,
) {
    var tipo by remember { mutableStateOf(tipoSugeridoPara(archivo.nombre)) }
    var cuenta by remember { mutableStateOf<String?>(null) }

    HojaDeDocumento(onDismiss = onDismiss, cerrarHabilitado = true) {
        // El nombre y el peso, de solo lectura: acá no se renombra —eso es «Editar»— pero sí hace
        // falta ver QUÉ se está subiendo, porque el selector del sistema ya se cerró.
        Text(archivo.nombre, style = Movi.textos.cuerpo, color = Movi.colores.texto, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(
            pesoLegible(archivo.bytes.size.toLong()),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
        )

        Spacer(Modifier.height(16.dp))
        SelectorDeTipo(tipo, enabled = true) { tipo = it }

        Spacer(Modifier.height(14.dp))
        SelectorDeCuenta(cuentas, cuenta, enabled = true) { cuenta = it }

        Spacer(Modifier.height(20.dp))
        BotonDeLaHoja(texto = "Subir", habilitado = true) { onSubir(tipo, cuenta) }
        Spacer(Modifier.height(20.dp))
    }
}

/** Las cuatro opciones de tipo, una por fila. Compartidas por las dos hojas. */
@Composable
private fun SelectorDeTipo(
    elegido: TipoDeDocumento,
    enabled: Boolean,
    onElegir: (TipoDeDocumento) -> Unit,
) {
    EtiquetaDeCampo("TIPO")
    Spacer(Modifier.height(8.dp))
    TipoDeDocumento.entries.forEach { t ->
        FilaElegible(nombreDeTipo(t), elegida = t == elegido, enabled = enabled) { onElegir(t) }
    }
}

/**
 * **De qué cuenta es el papel**, con «Sin cuenta» como primera opción.
 *
 * Reusa [ListaDeCuentasElegibles] —la misma lista, con el mismo «Ver todas las cuentas», que ya
 * usan el SMS y la revisión de extractos— en vez de escribir un selector propio: este repo ya se
 * comió dos veces el defecto de copiar una regla de cuentas en vez de compartirla.
 *
 * «Sin cuenta» no es un hueco sino una respuesta: la póliza de vida y la escritura del apartamento
 * no cuelgan de ninguna cuenta, y es además la única forma de descolgar un papel mal asignado.
 */
@Composable
private fun SelectorDeCuenta(
    cuentas: List<Account>,
    elegida: String?,
    enabled: Boolean,
    onElegir: (String?) -> Unit,
) {
    EtiquetaDeCampo("CUENTA")
    Spacer(Modifier.height(8.dp))
    FilaElegible("Sin cuenta", elegida = elegida == null, enabled = enabled) { onElegir(null) }
    ListaDeCuentasElegibles(
        // `conservar` sostiene la cuenta ya puesta aunque hoy no se ofreciera —el papel puede ser
        // de una cuenta que después cambió de tipo—: sin eso, abrir la hoja la escondería.
        cuentas = cuentasPara(cuentas, UsoDeCuenta.PAPEL_GUARDADO, conservar = elegida),
        uso = UsoDeCuenta.PAPEL_GUARDADO,
        selectedId = elegida,
        onPick = { if (enabled) onElegir(it) },
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** El rótulo en versalitas de un campo de las hojas de documentos. */
@Composable
private fun EtiquetaDeCampo(texto: String) {
    Text(
        texto,
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    )
}

/** Una opción de una lista de una sola elección, con su tilde cuando está puesta. */
@Composable
private fun FilaElegible(
    texto: String,
    elegida: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            texto,
            style = Movi.textos.cuerpo,
            color = if (elegida) Movi.colores.texto else Movi.colores.textoMedio,
            fontWeight = if (elegida) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        // Mismo motivo que en `CreditTermsSheet`: la fuente del navegador no trae U+2713 y el
        // carácter sale como un rectángulo vacío.
        if (elegida) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Movi.colores.marca,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** El botón ancho del pie de una hoja. */
@Composable
private fun BotonDeLaHoja(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (habilitado) Movi.colores.texto else Movi.colores.textoApagado)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = Movi.colores.fondo, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium)
    }
}

/**
 * El armazón de las dos hojas de documentos: el velo, el panel de abajo, el asa con su «cerrar» y
 * el scroll.
 *
 * El scroll no es decoración: con el teclado abierto ninguna de las dos cabe en un teléfono chico,
 * que es la misma lección de las siete hojas que nacieron sin él.
 */
@Composable
private fun HojaDeDocumento(
    onDismiss: () -> Unit,
    cerrarHabilitado: Boolean,
    contenido: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = cerrarHabilitado, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Movi.colores.tarjeta)
                .clickable(enabled = false) {}
                .padding(horizontal = 20.dp),
        ) {
            SheetHandleWithClose(onClose = onDismiss, enabled = cerrarHabilitado)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
            ) {
                contenido()
            }
        }
    }
}
