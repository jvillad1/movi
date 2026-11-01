package com.jvillada.movi.ui.papeles

import com.jvillada.movi.ui.transactions.tituloDelMovimiento
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.jvillada.movi.shared.model.ABRIR_EL_PDF
import com.jvillada.movi.shared.model.EL_PDF_TIENE_CLAVE
import com.jvillada.movi.shared.model.MAX_LARGO_DE_LA_CLAVE_DEL_PDF
import com.jvillada.movi.shared.model.PISTA_DE_LA_CLAVE_DEL_PDF
import com.jvillada.movi.shared.model.RECORDAR_LA_CLAVE_DEL_PDF
import com.jvillada.movi.shared.model.ROTULO_DE_LA_CLAVE_DEL_PDF
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.Casilla
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.fechaLegibleDeSms
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.AtrasCierraEstaHoja
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.theme.Radio

/** El tag de la hoja, para las pruebas y las capturas. */
const val TAG_HOJA_LEYENDO_EL_PAPEL: String = "hoja-leyendo-el-papel"

/**
 * **«Movi está leyendo tu comprobante…»** — la hoja que abre lo compartido (Ola 2).
 *
 * Lee los [archivos] uno por uno ([leerUnPapel]: Documentos primero, después la lectura) y dice,
 * para cada uno, qué pasó y a dónde ir: «Revisar» abre la misma pantalla de un mensaje del banco;
 * un extracto abre su pantalla de revisión de siempre; «Ya lo tienes anotado» ofrece anotarlo igual.
 * Mientras lee se puede cerrar: lo que ya se subió queda en Documentos y en Por revisar.
 *
 * [onCambio] avisa cuando terminó algo que cambia otras pantallas (una propuesta nueva en la
 * bandeja), para que la de atrás se vuelva a leer.
 */
@Composable
fun HojaLeyendoElPapel(
    archivos: List<ArchivoCompartido>,
    onCerrar: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onCambio: () -> Unit,
) {
    val resultados = remember(archivos) {
        mutableStateListOf<ResultadoDelPapel>().apply { addAll(archivos.map { ResultadoDelPapel.Leyendo(it.nombre) }) }
    }
    val coroutine = rememberCoroutineScope()

    // Vuelta 103: cada resultado también queda en [PapelesCompartidos.resultados], para que la fila de
    // la guía de primer uso (que está debajo de esta hoja) diga lo mismo que la hoja.
    fun poner(i: Int, resultado: ResultadoDelPapel) {
        resultados[i] = resultado
        PapelesCompartidos.anotar(resultado)
    }

    LaunchedEffect(archivos) {
        archivos.forEachIndexed { i, archivo ->
            PapelesCompartidos.anotar(resultados[i])
            poner(i, leerUnPapel(Repositories.wallets, archivo))
        }
        onCambio()
    }

    // ¿Se puede ofrecer «Recordarla»? Solo se pregunta si algún PDF pidió su clave. Si la pregunta
    // falla, no se ofrece: es lo seguro.
    val pideClave = resultados.any { it is ResultadoDelPapel.ConClave }
    var sePuedeRecordar by remember { mutableStateOf(false) }
    LaunchedEffect(pideClave) {
        if (pideClave) {
            sePuedeRecordar = intentar { Repositories.wallets.estadoDeLaClaveDeExtractos() }.getOrNull()?.sePuedeRecordar == true
        }
    }

    fun anotarIgual(i: Int, resultado: ResultadoDelPapel.YaAnotado) {
        poner(i, ResultadoDelPapel.Leyendo(resultado.nombre))
        coroutine.launch {
            poner(
                i,
                intentar { Repositories.wallets.leerPapel(resultado.lectura.documentoId, anotarAunqueEsteAnotado = true) }
                    .fold(
                        onSuccess = { resultadoDe(resultado.nombre, it) },
                        onFailure = { resultadoDeLaFalla(resultado.nombre, resultado.lectura.documentoId, it) },
                    ),
            )
            onCambio()
        }
    }

    /** «Abrir» con la clave escrita: sobre el documento ya guardado, sin volver a subirlo. */
    fun abrir(i: Int, pendiente: ResultadoDelPapel.ConClave, clave: String, recordar: Boolean) {
        poner(i, ResultadoDelPapel.Leyendo(pendiente.nombre))
        coroutine.launch {
            poner(i, abrirConLaClave(Repositories.wallets, pendiente, clave, recordar))
            onCambio()
        }
    }

    /** «Intentar de nuevo»: la lectura del documento ya guardado, otra vez. */
    fun reintentar(i: Int, resultado: ResultadoDelPapel.NoSePudo) {
        val documentoId = resultado.documentoId ?: return
        poner(i, ResultadoDelPapel.Leyendo(resultado.nombre))
        coroutine.launch {
            poner(i, leerUnPapel(Repositories.wallets, ArchivoCompartido(resultado.nombre, ByteArray(0), "", documentoId = documentoId)))
            onCambio()
        }
    }

    fun ir(destino: Screen) {
        onCerrar()
        onNavigate(destino)
    }

    AtrasCierraEstaHoja(onCerrar)
    MarcoDeHoja(onDismiss = onCerrar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Espacio.xl)
                .testTag(TAG_HOJA_LEYENDO_EL_PAPEL),
        ) {
            EncabezadoDeHoja(
                tituloDeLaHoja(resultados),
                subtitulo = "Lo guardamos en Documentos y lo que leamos queda en Por revisar para que lo confirmes.",
            )
            resultados.forEachIndexed { i, resultado ->
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                TarjetaDelResultado(
                    resultado = resultado,
                    onRevisar = { id -> ir(Screen.SMSReconcile(id)) },
                    onVerExtracto = { lectura ->
                        lectura.extracto?.let { ir(Screen.StatementReview(Json.encodeToString(it))) }
                    },
                    onAnotarIgual = { if (resultado is ResultadoDelPapel.YaAnotado) anotarIgual(i, resultado) },
                    onDocumentos = { ir(Screen.Documentos) },
                    sePuedeRecordar = sePuedeRecordar,
                    onAbrir = { clave, recordar -> if (resultado is ResultadoDelPapel.ConClave) abrir(i, resultado, clave, recordar) },
                    onReintentar = { if (resultado is ResultadoDelPapel.NoSePudo) reintentar(i, resultado) },
                )
            }
        }
    }
}

@Composable
private fun TarjetaDelResultado(
    resultado: ResultadoDelPapel,
    onRevisar: (String) -> Unit,
    onVerExtracto: (com.jvillada.movi.shared.model.LecturaDelPapel) -> Unit,
    onAnotarIgual: () -> Unit,
    onDocumentos: () -> Unit,
    sePuedeRecordar: Boolean,
    onAbrir: (clave: String, recordar: Boolean) -> Unit,
    onReintentar: () -> Unit,
) {
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Text(
            resultado.nombre,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Espacio.xxs))
        // Vuelta 103: un fallo (o un PDF que espera su clave) se ve como aviso, con su ícono: nunca
        // como «Listo».
        val esAlerta = esAvisoDelPapel(resultado)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (esAlerta) {
                Icon(
                    imageVector = if (resultado is ResultadoDelPapel.ConClave) Icons.Rounded.Lock else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = Movi.colores.aviso,
                    modifier = Modifier.size(Icono.s).testTag(TAG_AVISO_DEL_PAPEL),
                )
                Spacer(Modifier.width(Espacio.xs))
            }
            Text(
                tituloDelResultado(resultado),
                style = Movi.textos.cuerpo,
                color = if (esAlerta) Movi.colores.aviso else Movi.colores.texto,
            )
        }
        when (resultado) {
            is ResultadoDelPapel.Leyendo -> {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Movi.colores.enlace,
                    trackColor = Movi.colores.hilo,
                )
            }
            is ResultadoDelPapel.EnLaBandeja -> {
                LineaDeLoLeido(resultado.lectura)
                val id = resultado.lectura.porRevisarId
                if (id != null) {
                    Spacer(Modifier.height(Espacio.s))
                    Pastilla(
                        if (resultado.lectura.estadoEnLaBandeja == null || resultado.lectura.estadoEnLaBandeja == SMS_STATE_PENDING) "Revisar" else "Ver",
                        principal = true,
                    ) { onRevisar(id) }
                }
            }
            is ResultadoDelPapel.YaAnotado -> {
                LineaDeLoLeido(resultado.lectura)
                Spacer(Modifier.height(Espacio.xs))
                val hoy = hoyEnAppZone()
                resultado.lectura.yaAnotado.forEach { ev ->
                    Text(
                        "${tituloDelMovimiento(ev)} · ${etiquetaDeFecha(fechaDeEpoch(ev.timestamp), hoy)} · ${formatMoney(ev.amount, ev.currency)}",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                }
                Spacer(Modifier.height(Espacio.s))
                Pastilla("Anotarlo de todas formas", principal = false, onClick = onAnotarIgual)
            }
            is ResultadoDelPapel.Extracto -> {
                Spacer(Modifier.height(Espacio.s))
                Pastilla("Revisar el extracto", principal = true) { onVerExtracto(resultado.lectura) }
            }
            is ResultadoDelPapel.NoSePudo -> {
                Spacer(Modifier.height(Espacio.xxs))
                Text(resultado.motivo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                if (resultado.reintentable && resultado.documentoId != null) {
                    Spacer(Modifier.height(Espacio.s))
                    Pastilla("Intentar de nuevo", principal = true, onClick = onReintentar)
                }
                if (resultado.guardado) {
                    Spacer(Modifier.height(Espacio.s))
                    Pastilla("Ver en Documentos", principal = false, onClick = onDocumentos)
                }
            }
            is ResultadoDelPapel.ConClave -> PedirLaClave(resultado, sePuedeRecordar, onAbrir)
        }
    }
}

/** Tags de la clave en línea, para las pruebas. */
const val TAG_AVISO_DEL_PAPEL: String = "papel-aviso"
const val TAG_CLAVE_DEL_PDF: String = "papel-clave-del-pdf"
const val TAG_RECORDAR_LA_CLAVE: String = "papel-recordar-la-clave"
const val TAG_ABRIR_EL_PDF: String = "papel-abrir-el-pdf"

/**
 * Vuelta 103 · **pedir un secreto en línea** (ver `docs/diseno/sistema.md`): el campo «Clave del PDF»
 * con su pista, la casilla «Recordarla…» —apagada siempre al empezar, y solo si el server puede
 * guardarla cifrada— y «Abrir». La clave vive solo en este estado y en el cuerpo del pedido.
 */
@Composable
private fun PedirLaClave(
    resultado: ResultadoDelPapel.ConClave,
    sePuedeRecordar: Boolean,
    onAbrir: (clave: String, recordar: Boolean) -> Unit,
) {
    var clave by remember(resultado.documentoId) { mutableStateOf("") }
    var recordar by remember(resultado.documentoId) { mutableStateOf(false) }
    Spacer(Modifier.height(Espacio.xxs))
    Text(EL_PDF_TIENE_CLAVE, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    Spacer(Modifier.height(Espacio.s))
    Campo(
        valor = clave,
        alCambiar = { clave = it.take(MAX_LARGO_DE_LA_CLAVE_DEL_PDF) },
        rotulo = ROTULO_DE_LA_CLAVE_DEL_PDF,
        ayuda = PISTA_DE_LA_CLAVE_DEL_PDF,
        error = resultado.error,
        secreto = true,
        teclado = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        acciones = KeyboardActions(onDone = { if (clave.isNotBlank()) onAbrir(clave, recordar && sePuedeRecordar) }),
        tag = TAG_CLAVE_DEL_PDF,
    )
    if (sePuedeRecordar) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().testTag(TAG_RECORDAR_LA_CLAVE),
        ) {
            Casilla(marcada = recordar, onCambiar = { recordar = it })
            Text(RECORDAR_LA_CLAVE_DEL_PDF, style = Movi.textos.apoyo, color = Movi.colores.texto)
        }
    } else {
        Spacer(Modifier.height(Espacio.s))
    }
    Boton(
        ABRIR_EL_PDF,
        onClick = { onAbrir(clave, recordar && sePuedeRecordar) },
        habilitado = clave.isNotBlank(),
        modifier = Modifier.testTag(TAG_ABRIR_EL_PDF),
    )
}

/** «−$138.600 · Coomeva · 30 de septiembre a las 2:05 p. m.» */
@Composable
private fun LineaDeLoLeido(lectura: com.jvillada.movi.shared.model.LecturaDelPapel) {
    val leido = lectura.leido ?: return
    val signo = if (leido.type == TransactionType.INCOME) "+" else "−"
    val cuando = lectura.cuando?.let { " · ${fechaLegibleDeSms(it)}" }.orEmpty()
    Spacer(Modifier.height(Espacio.xxs))
    Text(
        "$signo${formatMoney(leido.amount.roundToLong(), leido.currency)} · ${leido.merchant}$cuando",
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
    )
}

@Composable
private fun Pastilla(texto: String, principal: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radio.pill))
            .then(
                if (principal) Modifier.background(Movi.colores.marca)
                else Modifier.border(Medida.trazo, Movi.colores.borde, RoundedCornerShape(Radio.pill)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Espacio.m, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            texto,
            style = Movi.textos.cuerpo,
            color = if (principal) Movi.colores.sobreMarca else Movi.colores.texto,
        )
    }
}
