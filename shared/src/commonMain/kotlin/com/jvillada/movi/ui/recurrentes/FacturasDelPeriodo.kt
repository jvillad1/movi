package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.ESPACIO_ANTES_DEL_FINAL_DE_FILA
import com.jvillada.movi.ui.components.ds.ESPACIO_ANTES_DEL_TEXTO_DE_FILA
import com.jvillada.movi.ui.components.ds.FondoDeBarra
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.dashboard.EstadoDeLaFila
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.yaPagadoEnElPeriodo
import com.jvillada.movi.ui.transactions.nombreDelDiaDeLaSemana
import kotlinx.datetime.LocalDate

/*
 * # Tus facturas del período (vuelta 40, la opción 3)
 *
 * La lista del período de Pagos, con la forma de la maqueta: arriba la **tarjeta amarilla «Falta por
 * pagar»** y debajo **una fila por factura** —ícono, nombre, cifra, la pastilla de vencimiento y el
 * botón «Anotar pago» a la derecha—; las pagadas dicen «Pagado» en verde y llevan el enlace al
 * movimiento que las pagó. El orden: lo vencido, lo que vence por fecha, lo pagado al final.
 *
 * **Las cifras son las de siempre** (`checklistDelPeriodo`, [faltaPorPagar], [yaPagadoEnElPeriodo]):
 * esto solo cambia cómo se ven. Y lo que la lista ya ofrecía sigue colgando de su fila: «¿Ya pagaste
 * …? Sí, fue este / No fue este», «No fue este» sobre lo emparejado, «Quitar la marca» y la
 * estimación de una cuota (ver [SeccionChecklistDelPeriodo]).
 */

/** El título de la lista de las facturas en Pagos (vuelta 40). */
const val TITULO_TUS_FACTURAS: String = "Tus facturas del período"

/** El título de la lista de los ingresos fijos del período, debajo de las facturas. */
const val TITULO_TUS_INGRESOS: String = "Tus ingresos del período"

/** Cuántas facturas se ven antes de «Ver los N pagos del período». */
const val FACTURAS_A_LA_VISTA: Int = 5

/** El tag de la tarjeta amarilla «Falta por pagar». */
const val TAG_TARJETA_FALTA_POR_PAGAR: String = "pagos:falta-por-pagar"

/** El tag de una fila de factura. */
const val TAG_FILA_DE_FACTURA: String = "pagos:fila-de-factura"

/** El tag del enlace «Movimiento del 25 oct» de una factura pagada. */
const val TAG_ENLACE_AL_MOVIMIENTO: String = "pagos:enlace-al-movimiento"

/** El tag de «Ver los N pagos del período». */
const val TAG_VER_TODAS_LAS_FACTURAS: String = "pagos:ver-todas-las-facturas"

/**
 * **Lo que dice la tarjeta amarilla.** Sale entero de las funciones de siempre.
 *
 * @property falta lo que falta pagar ([faltaPorPagar]), la cifra héroe.
 * @property pagado lo que ya pagaste ([yaPagadoEnElPeriodo]): «Llevas $X pagados».
 * @property total [falta] + [pagado]: «de $X».
 * @property quedan las facturas que faltan ([pagosPendientes]); [de], todas las del período.
 */
data class ResumenDeFacturas(val falta: Long, val pagado: Long, val quedan: Int, val de: Int) {
    val total: Long get() = falta + pagado

    /** Cuánto de la barra va lleno: lo pagado sobre el total (0 sin total). */
    val fraccionPagada: Float get() = if (total <= 0L) 0f else (pagado.toDouble() / total).toFloat()
}

/** [ResumenDeFacturas] del checklist (sin los ingresos). Pura. */
fun resumenDeFacturas(checklist: List<PagoDelPeriodo>): ResumenDeFacturas = ResumenDeFacturas(
    falta = faltaPorPagar(checklist),
    pagado = yaPagadoEnElPeriodo(checklist),
    quedan = pagosPendientes(checklist).size,
    de = checklist.count { !it.esIngreso },
)

/** «de $5.950.600 · quedan 4 de 7 pagos». */
fun lineaDeLoQueQueda(resumen: ResumenDeFacturas): String {
    val pagos = if (resumen.de == 1) "pago" else "pagos"
    val quedan = if (resumen.quedan == 1) "queda 1 de ${resumen.de} $pagos" else "quedan ${resumen.quedan} de ${resumen.de} $pagos"
    return "de ${formatCOP(resumen.total)} · $quedan"
}

/** «Llevas $1.638.600 pagados». */
fun lineaDeLoPagado(resumen: ResumenDeFacturas): String = "Llevas ${formatCOP(resumen.pagado)} pagados"

/**
 * **El orden de la lista**: lo vencido primero (lo que venció hace más, arriba), después lo que vence
 * por fecha, y lo pagado al final (también por fecha). A igual fecha, el monto más grande primero.
 * Pura.
 */
fun enOrdenDeFacturas(pagos: List<PagoDelPeriodo>): List<PagoDelPeriodo> =
    pagos.sortedWith(
        compareBy<PagoDelPeriodo>(
            { if (it.pagado) 2 else if (it.vencido) 0 else 1 },
            { it.diasParaVencer },
        ).thenByDescending { it.monto },
    )

/** La factura cuyo «Anotar pago» va en amarillo: la primera sin pagar del orden. `null` sin ninguna. */
fun laMasUrgente(ordenadas: List<PagoDelPeriodo>): PagoDelPeriodo? = ordenadas.firstOrNull { !it.pagado }

private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** «25 oct» desde un ISO; `null` si no es una fecha. */
fun fechaCortaDeFactura(iso: String): String? =
    runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()?.let { "${it.dayOfMonth} ${MESES_CORTOS[it.monthNumber - 1]}" }

/**
 * **La pastilla de vencimiento de una factura sin pagar**, con su tono: lo vencido y lo que vence en
 * tres días o menos en el tinte con el texto de aviso («Vence el martes»); lo demás, neutra («Vence
 * el 3 nov»). Lo que Movi pregunta («¿fue este?») dice «Por confirmar». Pura.
 */
fun pastillaDeVencimiento(pago: PagoDelPeriodo, conPropuesta: Boolean): Pair<String, TonoDePildora> {
    if (conPropuesta) return "Por confirmar" to TonoDePildora.Marca
    val dias = pago.diasParaVencer
    val verbo = if (pago.esIngreso) "Llega" else "Vence"
    val fecha = runCatching { LocalDate.parse(pago.vence) }.getOrNull()
    return when {
        dias < -1 -> (if (pago.esIngreso) "Debía llegar hace ${-dias} días" else "Venció hace ${-dias} días") to TonoDePildora.Aviso
        dias == -1 -> (if (pago.esIngreso) "Debía llegar ayer" else "Venció ayer") to TonoDePildora.Aviso
        dias == 0 -> "$verbo hoy" to TonoDePildora.Aviso
        dias == 1 -> "$verbo mañana" to TonoDePildora.Aviso
        dias in 2..3 && fecha != null -> "$verbo el ${nombreDelDiaDeLaSemana(fecha.dayOfWeek)}" to TonoDePildora.Aviso
        dias in 2..3 -> "$verbo en $dias días" to TonoDePildora.Aviso
        else -> (fechaCortaDeFactura(pago.vence)?.let { "$verbo el $it" } ?: "$verbo en $dias días") to TonoDePildora.Neutra
    }
}

/**
 * «Movimiento del 25 oct»: el enlace de una factura pagada al movimiento que la pagó, o `null` si no
 * hay un movimiento detrás (un sello viejo hecho a mano) o no se sabe el día.
 */
fun enlaceDelMovimiento(pago: PagoDelPeriodo): String? {
    if (!pago.pagado || pago.eventId == null) return null
    val dia = pago.pagadoEl?.let(::fechaCortaDeFactura) ?: return null
    return "Movimiento del $dia"
}

/** «Ver los 7 pagos del período». */
fun textoDeVerTodas(cuantas: Int): String = "Ver los $cuantas pagos del período"

/**
 * **La tarjeta amarilla «Falta por pagar»** (vuelta 40): el fondo de la marca con la tinta encima, la
 * cifra héroe de la pantalla, «de $X · quedan N de M pagos», la barra en tinta sobre el amarillo
 * suave y «Llevas $X pagados». Es la única cifra héroe de Pagos (`UnaSolaCifraHeroePorPantallaTest`):
 * el Disponible, más abajo, va en la de tarjeta.
 */
@Composable
internal fun TarjetaFaltaPorPagar(resumen: ResumenDeFacturas, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radio.xl))
            .background(Movi.colores.marca)
            .testTag(TAG_TARJETA_FALTA_POR_PAGAR)
            .padding(Medida.rellenoDeSuperficie),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Column {
            Text(TITULO_FALTA_POR_PAGAR, style = Movi.textos.cuerpo, color = Movi.colores.sobreMarcaMedio)
            Cifra(resumen.falta, VarianteDeCifra.Heroe, tono = TonoDeCifra.Neutro)
            Text(lineaDeLoQueQueda(resumen), style = Movi.textos.cuerpoRegular, color = Movi.colores.sobreMarcaMedio)
        }
        BarraDeProgreso(
            resumen.fraccionPagada,
            modifier = Modifier.semantics { contentDescription = "${lineaDeLoPagado(resumen)} de ${formatCOP(resumen.total)}" },
            fondo = FondoDeBarra.SobreLaMarca,
        )
        Text(lineaDeLoPagado(resumen), style = Movi.textos.apoyoMedio, color = Movi.colores.sobreMarcaMedio)
    }
}

/**
 * **Una factura del período** (vuelta 40): ícono de su categoría, nombre, cifra y debajo la pastilla
 * de vencimiento —o, pagada, el enlace al movimiento que la pagó—; a la derecha «Anotar pago» (en
 * amarillo la más urgente, [principal]) o «Pagado» en verde. Tocar el resto de la fila abre la regla,
 * como siempre ([onAbrir]). [debajo] es lo que la fila ofrece además (la propuesta, «No fue este»,
 * «Quitar la marca», la estimación de una cuota).
 */
@Composable
internal fun FilaDeFactura(
    pago: PagoDelPeriodo,
    principal: Boolean,
    enVuelo: Boolean,
    conPropuesta: Boolean,
    onAnotar: (() -> Unit)?,
    onVerMovimiento: ((String) -> Unit)?,
    onAbrir: (() -> Unit)?,
    etiquetaDeAbrir: String?,
    evidencia: String?,
    debajo: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Medida.altoDeFilaDoble)
            .then(if (onAbrir != null) Modifier.clickable(onClickLabel = etiquetaDeAbrir, onClick = onAbrir) else Modifier)
            .testTag(TAG_FILA_DE_FACTURA)
            .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(Medida.avatar), contentAlignment = Alignment.Center) {
            IconoDeCategoria(pago.categoria.ifBlank { pago.nombre })
        }
        Spacer(Modifier.width(ESPACIO_ANTES_DEL_TEXTO_DE_FILA))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
            Text(
                pago.nombre,
                style = Movi.textos.cuerpoFuerte,
                color = Movi.colores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                MontoDeFila(
                    textoDelMontoDelChecklist(pago),
                    color = if (pago.pagado) Movi.colores.textoMedio else Movi.colores.texto,
                )
                // Una tarjeta se paga con su mínimo o su saldo: el monto dice de cuál se habla.
                if (pago.montoEsSaldo && !pago.pagado) {
                    Text("saldo", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
            }
            val enlace = enlaceDelMovimiento(pago)
            when {
                enVuelo -> Text("Guardando…", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                !pago.pagado -> {
                    val (texto, tono) = pastillaDeVencimiento(pago, conPropuesta)
                    Pildora(texto, tono)
                }
                enlace != null && onVerMovimiento != null -> EnlaceAlMovimiento(enlace) { onVerMovimiento(pago.eventId!!) }
                evidencia != null -> Text(evidencia, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
        Spacer(Modifier.width(ESPACIO_ANTES_DEL_FINAL_DE_FILA))
        when {
            pago.pagado -> PildoraDePagado(pago.esIngreso)
            onAnotar != null && !conPropuesta -> Boton(
                if (pago.esIngreso) ETIQUETA_ANOTAR_INGRESO else ETIQUETA_ANOTAR,
                onClick = onAnotar,
                variante = if (principal) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
                tamano = TamanoDeBoton.S,
                habilitado = !enVuelo,
            )
        }
    }
    debajo?.let { BajoLaFila { it() } }
}

/** «Movimiento del 25 oct», en el azul del enlace; abre ese movimiento. Toque de 48. */
@Composable
private fun EnlaceAlMovimiento(texto: String, onClick: () -> Unit) {
    Text(
        texto,
        style = Movi.textos.apoyoMedio,
        color = Movi.colores.enlace,
        modifier = Modifier
            .testTag(TAG_ENLACE_AL_MOVIMIENTO)
            .clickable(role = Role.Button, onClickLabel = "Ver el movimiento", onClick = onClick),
    )
}

/**
 * «Pagado ✓» en verde (el «✓» es un ícono: la letra del lienzo de la web no trae el carácter). Dice
 * «Pagado» al lector de pantalla —o «Recibido», en un ingreso—.
 */
@Composable
private fun PildoraDePagado(esIngreso: Boolean) {
    val texto = if (esIngreso) "Recibido" else "Pagado"
    Row(
        modifier = Modifier
            .height(Medida.altoChico)
            .clip(RoundedCornerShape(Radio.pill))
            .background(Movi.colores.lavado(Movi.colores.entra))
            .padding(horizontal = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.xxs),
    ) {
        Text(texto, style = Movi.textos.cuerpoFuerte, color = Movi.colores.entra)
        Icon(
            Icons.Rounded.Check,
            contentDescription = texto,
            tint = Movi.colores.entra,
            modifier = Modifier.size(Icono.s),
        )
    }
}

/** Los planes de las cuotas, para la estimación de cada fila (ver [estimacionDeLaFila]). */
internal typealias PlanesDeCuotas = Map<String, PlanDelCredito>

/** ¿La fila de [pago] trae algo que ofrecer debajo, además de su botón? */
internal fun ofreceAlgoDebajo(pago: PagoDelPeriodo, conPropuesta: Boolean): Boolean = when (pago.estado) {
    EstadoDeLaFila.LISTO -> pago.automatica || (pago.eventId != null && !pago.derivado)
    EstadoDeLaFila.MARCADA_A_MANO -> true
    EstadoDeLaFila.CON_DUDAS -> conPropuesta
    else -> false
}
