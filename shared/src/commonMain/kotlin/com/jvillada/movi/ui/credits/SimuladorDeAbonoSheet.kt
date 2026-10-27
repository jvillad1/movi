package com.jvillada.movi.ui.credits

import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.theme.Radio
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

    MarcoDeHoja(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            Text(TITULO_DEL_SIMULADOR, style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = Movi.colores.textoMedio)
            Spacer(Modifier.height(Espacio.xs))
            Text(credit.account.name, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            Spacer(Modifier.height(Espacio.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Deuda hoy", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                Text(
                    formatCOP(saldo),
                    style = Movi.textos.monto,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
            }

            // **De quién es el ahorro, antes de decir cuánto es.** Cuatro de los doce créditos
            // del dueño no salen de su cuenta, y son DOS casos distintos: dos libranzas —donde
            // la plata sí es suya, retenida antes de que el sueldo llegue— y dos hipotecas que
            // gira Skandia, donde no. Ver [avisoDeQuienPagaLaCuota].
            credit.terms?.let { avisoDeQuienPagaLaCuota(it) }?.let { aviso ->
                Spacer(Modifier.height(Espacio.s))
                Text(
                    aviso.texto,
                    style = Movi.textos.apoyo,
                    color = if (aviso.esAdvertencia) Movi.colores.aviso else Movi.colores.textoMedio,
                )
            }

            if (sugeridos.isNotEmpty()) {
                Spacer(Modifier.height(Espacio.s))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
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

            Spacer(Modifier.height(Espacio.s))
            MoneyField(
                value = abono,
                onValueChange = { abono = it },
                placeholder = "Cuánto abonarías (COP)",
            )

            Spacer(Modifier.height(Espacio.s))
            if (resultado == null) {
                Text(PIDE_UN_MONTO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            } else {
                Text(
                    resultado.titular,
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    color = if (resultado.esAlerta) Movi.colores.sale else Movi.colores.texto,
                )
                resultado.detalle?.let {
                    Spacer(Modifier.height(Espacio.xs))
                    Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
            }

            Spacer(Modifier.height(Espacio.m))
            Hairline()
            Spacer(Modifier.height(Espacio.s))
            // Los TRES supuestos, y ninguno en letra más chica que el otro: el de la proyección
            // (la tasa y la cuota de hoy) lo arrastra toda esta pantalla; el del abono (que el
            // banco acorte el plazo y no la cuota) es propio de esta hoja y además es algo que
            // él tiene que pedir; y el de la estimación —que el interés que Movi calcula se
            // queda corto contra el extracto— es el único que habla de la cifra misma y no del
            // futuro. Ver [SUPUESTO_DE_LA_ESTIMACION].
            Text(SUPUESTO_DEL_ABONO, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            Spacer(Modifier.height(Espacio.xs))
            Text(SUPUESTO_DE_LA_PROYECCION, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            Spacer(Modifier.height(Espacio.xs))
            Text(SUPUESTO_DE_LA_ESTIMACION, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        }

        Spacer(Modifier.height(Espacio.m))
        Boton(
            texto = "Cerrar",
            onClick = onDismiss,
            variante = VarianteDeBoton.Secundario,
            anchoCompleto = true,
        )
        Spacer(Modifier.height(Espacio.l))
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
        style = Movi.textos.apoyo,
        fontWeight = FontWeight.Medium,
        color = if (seleccionado) Movi.colores.fondo else Movi.colores.texto,
        modifier = Modifier
            .clip(RoundedCornerShape(Radio.m))
            .background(if (seleccionado) Movi.colores.texto else Movi.colores.tarjeta)
            .clickable(onClick = onClick)
            .padding(horizontal = Espacio.s, vertical = Espacio.xs),
    )
}
