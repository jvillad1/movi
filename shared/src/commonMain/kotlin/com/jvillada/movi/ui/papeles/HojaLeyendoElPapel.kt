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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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

    LaunchedEffect(archivos) {
        archivos.forEachIndexed { i, archivo ->
            resultados[i] = leerUnPapel(Repositories.wallets, archivo)
        }
        onCambio()
    }

    fun anotarIgual(i: Int, resultado: ResultadoDelPapel.YaAnotado) {
        resultados[i] = ResultadoDelPapel.Leyendo(resultado.nombre)
        coroutine.launch {
            resultados[i] = intentar { Repositories.wallets.leerPapel(resultado.lectura.documentoId, anotarAunqueEsteAnotado = true) }
                .fold(
                    onSuccess = { resultadoDe(resultado.nombre, it) },
                    onFailure = { ResultadoDelPapel.NoSePudo(resultado.nombre, motivoAlLeerElPapel(it), guardado = true) },
                )
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
        val esAlerta = resultado is ResultadoDelPapel.NoSePudo
        Text(
            tituloDelResultado(resultado),
            style = Movi.textos.cuerpo,
            color = if (esAlerta) Movi.colores.aviso else Movi.colores.texto,
        )
        when (resultado) {
            is ResultadoDelPapel.Leyendo -> {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Movi.colores.marca,
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
                if (resultado.guardado) {
                    Spacer(Modifier.height(Espacio.s))
                    Pastilla("Ver en Documentos", principal = false, onClick = onDocumentos)
                }
            }
        }
    }
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
