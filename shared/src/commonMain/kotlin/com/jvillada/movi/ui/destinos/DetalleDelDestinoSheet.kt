package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.loQueSeLeMandoPorPeriodo
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.SheetHandleWithClose
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.hoyEnAppZone

/**
 * **Lo que le mandaste a esta persona**: el total, el total período por período, y los movimientos.
 *
 * Es la mitad del pedido del dueño —*«poder ver los movimientos hacia esa cuenta»*— y vive en la
 * hoja del propio destino por lo que explica el KDoc de [DestinosScreen]: es la única de las tres
 * puertas posibles que no agrega una pantalla ni un chip por persona registrada.
 *
 * ### Por período del DUEÑO, no por mes de calendario
 *
 * Su corte es 25, así que una transferencia del 26 de agosto pertenece a «septiembre» — igual que su
 * salario, y igual que lo que dicen Movimientos, Presupuestos y el Inicio. Ver
 * [loQueSeLeMandoPorPeriodo].
 *
 * ### Y dice de dónde salió cada renglón
 *
 * Abajo, en letra chica, se explica **cómo** Movi decidió que un movimiento fue para allá: porque el
 * banco nombró el número, o porque el concepto dice el nombre. Sin eso, una lista de plata que
 * aparece sola no se puede verificar — y este repo ya pagó por cifras que nadie podía auditar.
 */
@Composable
fun DetalleDelDestinoSheet(
    destino: DestinoConocido,
    ajustes: PeriodSettings,
    onDismiss: () -> Unit,
    onEditar: () -> Unit,
) {
    var movimientos by remember(destino.id) { mutableStateOf<List<FinancialEvent>?>(null) }
    var totales by remember(destino.id) { mutableStateOf(destino.totales) }
    var error by remember(destino.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(destino.id) {
        runCatching { Repositories.wallets.getMovimientosDelDestino(destino.id) }
            .onSuccess { movimientos = it.movimientos; totales = it.destino.totales }
            .onFailure { error = it.toUserMessage() }
    }

    val hoy = remember { hoyEnAppZone() }
    val porPeriodo = remember(movimientos, ajustes) {
        movimientos?.let { loQueSeLeMandoPorPeriodo(it, ajustes) }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
    ) {
        Box(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Movi.colores.tarjeta)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        destino.nombre,
                        style = Movi.textos.titulo,
                        color = Movi.colores.texto,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Editar",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.marca,
                        modifier = Modifier.clickable(onClick = onEditar),
                    )
                }
                Text(
                    subtituloDelDestino(destino),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                )
                Text(
                    "No es una cuenta tuya: no entra en tu plata ni en tu patrimonio.",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoApagado,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(18.dp))

                Text("LE HAS ENVIADO", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(6.dp))
                if (totales.isEmpty()) {
                    Text("Nada todavía", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                } else {
                    TotalesEnColumna(totales, alineadoAlFinal = false)
                }

                if (porPeriodo.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text("POR PERÍODO", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        porPeriodo.forEach { p ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    nombreDe(p.periodo),
                                    style = Movi.textos.cuerpo,
                                    color = Movi.colores.textoMedio,
                                    modifier = Modifier.weight(1f),
                                )
                                TotalesEnColumna(p.totales, alineadoAlFinal = true)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))

                Text("MOVIMIENTOS", style = Movi.textos.rotulo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(8.dp))
                when {
                    error != null -> Text(error!!, style = Movi.textos.apoyo, color = Movi.colores.sale)
                    movimientos == null -> Text(
                        "Cargando…",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                    )
                    movimientos!!.isEmpty() -> Text(
                        NADA_TODAVIA,
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        movimientos!!.forEach { ev ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ev.description, style = Movi.textos.cuerpo, color = Movi.colores.texto)
                                    Text(
                                        etiquetaDeFecha(fechaDeEpoch(ev.timestamp), hoy),
                                        style = Movi.textos.apoyo,
                                        color = Movi.colores.textoMedio,
                                    )
                                }
                                Text(
                                    formatMoney(ev.amount, ev.currency),
                                    style = Movi.textos.monto,
                                    fontWeight = FontWeight.Medium,
                                    color = Movi.colores.texto,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(COMO_SE_CUENTAN, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** Lo que se dice cuando el destino está guardado pero todavía no se le reconoció ningún envío. */
internal const val NADA_TODAVIA: String =
    "Todavía no hay ninguno. Aparecen aquí en cuanto el banco te avise de una transferencia a ese " +
        "número, o si el concepto de un movimiento dice el nombre que le pusiste."

/**
 * **De dónde sale cada renglón**, dicho en la pantalla. Ver `vaHaciaElDestino` en `:core` para las
 * dos señales; esto es la misma regla en palabras del dueño, para que la lista se pueda auditar.
 */
internal const val COMO_SE_CUENTAN: String =
    "Se cuentan los gastos en los que el banco nombró ese número —aunque después les hayas " +
        "cambiado el nombre— y los que digan el nombre que le pusiste. Lo que recibes de esa " +
        "cuenta no se cuenta aquí: esto es lo que enviaste."
