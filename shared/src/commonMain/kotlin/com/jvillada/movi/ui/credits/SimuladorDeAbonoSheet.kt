package com.jvillada.movi.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.simularAbonoUnico
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.components.*

/**
 * **«¿Y si le abono de más?»** — cuánto se ahorra en intereses y cuánto se acorta el plazo si hoy
 * le mete plata encima a este crédito.
 *
 * ### No escribe nada, y por eso no se parece a las otras hojas
 *
 * Las hojas vecinas —[CreditTermsSheet], [CreditBalanceSheet]— terminan en un botón oscuro que
 * guarda. Esta termina en «Cerrar»: **es una pregunta, no una operación**. Un abono de verdad se
 * registra donde se registra la plata que se mueve (Agregar → Traspaso, préstamo como destino),
 * porque tiene dos patas —sale de una cuenta y entra al crédito— y esta hoja no sabe de cuál
 * cuenta saldría. Duplicar el alta acá abriría una segunda puerta para escribir la misma deuda, que
 * es exactamente el defecto que `CreditBalanceSheet` documenta haber evitado del otro lado.
 *
 * ### La aritmética no está acá
 *
 * Está en `:core` ([simularAbonoUnico]), que llama dos veces a la misma `planDeUnaDeuda` que ya usa
 * la tarjeta de atrás. Si estuviera acá, la hoja podría contestar una fecha y la tarjeta otra.
 */
@Composable
fun SimuladorDeAbonoSheet(
    credit: CreditSummary,
    periodoActual: PeriodoFinanciero,
    onDismiss: () -> Unit,
) {
    var abono by remember { mutableStateOf<Long?>(null) }
    val saldo = credit.account.balance
    val cuota = credit.terms?.installment ?: 0L
    // Una búsqueda binaria sobre treinta y pico de proyecciones: se hace UNA vez por crédito, no
    // en cada tecla. Da `null` en la mayoría —los créditos que ya se terminan no tienen mínimo que
    // buscar— y solo cuesta algo en las dos deudas que hoy no se terminan. Viene con su fecha
    // pegada, porque el monto solo se lee más barato de lo que es. Ver [MinimoConSuFecha].
    val minimo = remember(credit.account.id, saldo) { minimoConSuFecha(credit) }
    val sugeridos = remember(saldo, cuota, minimo) { montosSugeridosDeAbono(saldo, cuota, minimo?.monto) }
    // **De quién sería el ahorro**, que no es lo mismo que de quién sale la cuota: una libranza se
    // retiene de SU sueldo. Ver [elAhorroSeriaTuyo] y [AVISO_DE_ABONO_POR_LIBRANZA].
    val elAhorroEsSuyo = credit.terms?.let { elAhorroSeriaTuyo(it) } ?: true
    val resultado = abono?.let { monto ->
        simularAbonoUnico(credit, monto)?.let { textoDeLaSimulacion(it, periodoActual, minimo, elAhorroEsSuyo) }
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
                .background(MinSurfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
        ) {
            SheetHandleWithClose(onClose = onDismiss)

            Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                SectionLabel(TITULO_DEL_SIMULADOR)
                Spacer(Modifier.height(8.dp))
                Text(credit.account.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinText)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Deuda hoy", fontSize = 13.sp, color = MinTextMute)
                    Text(
                        formatCOP(saldo),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MinText,
                    )
                }

                // **De quién es el ahorro, antes de decir cuánto es.** Cuatro de los doce créditos
                // del dueño no salen de su cuenta, y son DOS casos distintos: dos libranzas —donde
                // la plata sí es suya, retenida antes de que el sueldo llegue— y dos hipotecas que
                // gira Skandia, donde no. Ver [avisoDeQuienPagaLaCuota].
                credit.terms?.let { avisoDeQuienPagaLaCuota(it) }?.let { aviso ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        aviso.texto,
                        fontSize = 11.5.sp,
                        color = if (aviso.esAdvertencia) MinWarn else MinTextMute,
                        lineHeight = 16.sp,
                    )
                }

                if (sugeridos.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sugeridos.forEach { s ->
                            ChipDeMonto(
                                etiqueta = s.etiqueta,
                                seleccionado = abono == s.monto,
                                onClick = { abono = s.monto },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                MoneyField(
                    value = abono,
                    onValueChange = { abono = it },
                    placeholder = "Cuánto abonarías (COP)",
                )

                Spacer(Modifier.height(14.dp))
                if (resultado == null) {
                    Text(PIDE_UN_MONTO, fontSize = 12.sp, color = MinTextMute, lineHeight = 17.sp)
                } else {
                    Text(
                        resultado.titular,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (resultado.esAlerta) MinExpense else MinText,
                        lineHeight = 19.sp,
                    )
                    resultado.detalle?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp, color = MinTextMute, lineHeight = 17.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                // Los TRES supuestos, y ninguno en letra más chica que el otro: el de la proyección
                // (la tasa y la cuota de hoy) lo arrastra toda esta pantalla; el del abono (que el
                // banco acorte el plazo y no la cuota) es propio de esta hoja y además es algo que
                // él tiene que pedir; y el de la estimación —que el interés que Movi calcula se
                // queda corto contra el extracto— es el único que habla de la cifra misma y no del
                // futuro. Ver [SUPUESTO_DE_LA_ESTIMACION].
                Text(SUPUESTO_DEL_ABONO, fontSize = 11.sp, color = MinTextFaint, lineHeight = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(SUPUESTO_DE_LA_PROYECCION, fontSize = 11.sp, color = MinTextFaint, lineHeight = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(SUPUESTO_DE_LA_ESTIMACION, fontSize = 11.sp, color = MinTextFaint, lineHeight = 15.sp)
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MinSurfaceContainerLow)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cerrar", color = MinText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Un monto sugerido, con su nombre y no con su cifra. Ver [montosSugeridosDeAbono] para por qué el
 * rótulo dice «Una cuota más» y no «$1.204.064»: el número aparece en el campo apenas se toca, y
 * cuatro cifras de siete dígitos una al lado de la otra no se leen.
 */
@Composable
private fun ChipDeMonto(etiqueta: String, seleccionado: Boolean, onClick: () -> Unit) {
    Text(
        etiqueta,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (seleccionado) MinBg else MinText,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (seleccionado) MinText else MinSurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
