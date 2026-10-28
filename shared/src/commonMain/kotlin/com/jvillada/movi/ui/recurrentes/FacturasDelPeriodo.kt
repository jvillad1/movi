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
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.ui.components.ds.BotonDeIcono
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
import com.jvillada.movi.ui.categorias.ICONO_DE_CATEGORIA_RESPALDO
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.aparienciaDe
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
 * botón «Anotar pago» a la derecha—; las pagadas son un renglón, «Pagado ✓ · $1.800.000 · 27 oct»,
 * que al tocarlo abre el movimiento que las pagó, con «Quitar la marca» / «No fue este» en su «⋯»
 * (vuelta 48). El orden: lo vencido, lo que vence por fecha, lo pagado al final.
 *
 * **Las cifras son las de siempre** (`checklistDelPeriodo`, [faltaPorPagar], [yaPagadoEnElPeriodo]):
 * esto solo cambia cómo se ven. Y lo que la lista ya ofrecía sigue en su fila: «¿Ya pagaste …? Sí,
 * fue este / No fue este» y la estimación de una cuota colgando de la pendiente; «No fue este» sobre
 * lo emparejado y «Quitar la marca», en el «⋯» de la pagada (ver [SeccionChecklistDelPeriodo]).
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

/**
 * «Llevas $1.638.600 pagados»; en cero, «Aún no pagas nada de esto» (la frase de la vuelta 28: un
 * «$0» no dice nada que haya que mirar).
 */
fun lineaDeLoPagado(resumen: ResumenDeFacturas): String =
    if (resumen.pagado <= 0L) AUN_NO_PAGAS_NADA else "Llevas ${formatCOP(resumen.pagado)} pagados"

/** Lo que dice la tarjeta amarilla cuando todavía no se ha pagado nada del período. */
const val AUN_NO_PAGAS_NADA: String = "Aún no pagas nada de esto"

/**
 * **Con qué nombre se elige el ícono de una factura**: el de su categoría, salvo que la categoría no
 * diga nada (vacía, u «Otros» y cualquiera que caiga en el ícono genérico): ahí el de su nombre. El
 * arriendo del usuario demo está en «Otros» y salía con el ícono genérico en vez de la casa. Pura.
 */
internal fun nombreParaElIconoDeLaFactura(categoria: String, nombre: String): String =
    if (categoria.isBlank() || aparienciaDe(categoria, null).icono == ICONO_DE_CATEGORIA_RESPALDO) nombre else categoria

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
 * el 3 nov»). Lo que Movi pregunta («¿fue este?») dice «Por confirmar», también en el tinte. Pura.
 */
fun pastillaDeVencimiento(pago: PagoDelPeriodo, conPropuesta: Boolean): Pair<String, TonoDePildora> {
    // Una píldora de estado nunca va llena de amarillo: eso es de la acción principal (revisión).
    if (conPropuesta) return "Por confirmar" to TonoDePildora.Aviso
    val dias = pago.diasParaVencer
    // Vuelta 49: una cuota que el banco cobra sola no «vence»: se cobra sola, y después se pregunta.
    if (pago.seCobraSola) pastillaDelDebito(pago)?.let { return it }
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

/** La pastilla de una cuota que el banco cobra sola, ya vencida: la pregunta, no un «venció». */
const val PILDORA_SE_COBRO: String = "¿Se cobró?"

/**
 * **La pastilla de lo que el banco cobra solo** (vuelta 49): «Se cobra sola el 5» mientras no llega su
 * día —en el tinte si es en tres días o menos—, y «¿Se cobró?» desde ese día. `null` si la fecha no se
 * entiende (queda la pastilla de siempre). Pura.
 */
fun pastillaDelDebito(pago: PagoDelPeriodo): Pair<String, TonoDePildora>? {
    val dias = pago.diasParaVencer
    if (dias <= 0) return PILDORA_SE_COBRO to TonoDePildora.Aviso
    val fecha = runCatching { LocalDate.parse(pago.vence) }.getOrNull() ?: return null
    return "Se cobra sola el ${fecha.dayOfMonth}" to (if (dias <= 3) TonoDePildora.Aviso else TonoDePildora.Neutra)
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
 * de vencimiento; a la derecha «Anotar pago» (en amarillo la más urgente, [principal]). Tocar el resto
 * de la fila abre la regla, como siempre ([onAbrir]). [debajo] es lo que la fila ofrece además (la
 * propuesta, la estimación de una cuota). **Pagada, es compacta** ([FilaDeFacturaPagada], vuelta 48):
 * tocarla abre el movimiento ([onVerMovimiento]) y lo demás va en su «⋯» ([accionesDelMenu]).
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
    /**
     * Vuelta 48 · lo que la fila pagada guarda en su «⋯» («Quitar la marca», «No fue este»), con su
     * acción. Solo se mira en una fila pagada: ver [FilaDeFacturaPagada].
     */
    accionesDelMenu: List<Pair<String, () -> Unit>> = emptyList(),
) {
    if (pago.pagado) {
        FilaDeFacturaPagada(
            pago = pago,
            enVuelo = enVuelo,
            onTocar = pago.eventId?.let { id -> onVerMovimiento?.let { ver -> { ver(id) } } } ?: onAbrir,
            etiquetaDeTocar = if (pago.eventId != null && onVerMovimiento != null) "Ver el movimiento" else etiquetaDeAbrir,
            accionesDelMenu = accionesDelMenu,
            // Lo que Movi emparejó solo y el sello viejo sin movimiento dicen de dónde salió el
            // «Pagado» arriba de su «⋯»: es justo lo que «No fue este» y «Quitar la marca» discuten.
            notaDelMenu = evidencia.takeIf { pago.automatica || pago.eventId == null },
        )
        return
    }
    // Lo que se toca para abrir la regla es el ícono con los textos, no la fila entera: el botón de la
    // derecha es otro control, y un botón adentro de otro tocable se lee mal (y se toca mal).
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_FACTURA)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Medida.altoDeFila)
                .padding(end = Medida.margenDePantalla),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(if (onAbrir != null) Modifier.clickable(onClickLabel = etiquetaDeAbrir, onClick = onAbrir) else Modifier)
                    .padding(start = Medida.margenDePantalla, top = Espacio.s, bottom = Espacio.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(Medida.avatar), contentAlignment = Alignment.Center) {
                    // Vuelta 40: el ícono de la categoría, o el del nombre si la categoría no dice nada
                    // («Arriendo» anotado en «Otros» lleva la casa, no el ícono genérico).
                    IconoDeCategoria(nombreParaElIconoDeLaFactura(pago.categoria, pago.nombre))
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
                        MontoDeFila(textoDelMontoDelChecklist(pago), color = Movi.colores.texto)
                        // Una tarjeta se paga con su mínimo o su saldo: el monto dice de cuál se habla.
                        if (pago.montoEsSaldo) {
                            Text("saldo", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                        }
                    }
                }
            }
            Spacer(Modifier.width(ESPACIO_ANTES_DEL_FINAL_DE_FILA))
            if (onAnotar != null && !conPropuesta) {
                Boton(
                    if (pago.esIngreso) ETIQUETA_ANOTAR_INGRESO else ETIQUETA_ANOTAR,
                    onClick = onAnotar,
                    variante = if (principal) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                    habilitado = !enVuelo,
                )
            }
        }
        // **La pastilla va en su renglón**, a todo el ancho desde el texto (vuelta 40, revisión): al
        // lado de «Anotar pago», a 360 dp, «Venció hace 23 días» se cortaba en «Venció hace 23». Lo
        // mide `LaPastillaCabeEnteraTest`. El renglón también abre la regla (es parte de lo que se lee
        // de la fila).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onAbrir != null) Modifier.clickable(onClickLabel = etiquetaDeAbrir, onClick = onAbrir) else Modifier)
                .padding(start = SANGRIA_DEL_TEXTO_DE_LA_FILA, end = Medida.margenDePantalla, bottom = Espacio.s),
            verticalArrangement = Arrangement.spacedBy(Espacio.xxs),
        ) {
            when {
                enVuelo -> Text("Guardando…", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                else -> {
                    val (texto, tono) = pastillaDeVencimiento(pago, conPropuesta)
                    Pildora(texto, tono)
                }
            }
        }
    }
    debajo?.let { BajoLaFila { it() } }
}

/**
 * «$1.800.000 · 27 oct»: lo que va después de «Pagado ✓» en una factura pagada (vuelta 48), con lo que de
 * verdad salió ([textoDelMontoDelChecklist]) y el día en que salió, si se sabe. Pura.
 */
fun resumenDeLaFacturaPagada(pago: PagoDelPeriodo): String =
    listOfNotNull(textoDelMontoDelChecklist(pago), pago.pagadoEl?.let(::fechaCortaDeFactura)).joinToString(" · ")

/** El tag de una factura pagada (vuelta 48) y el de su «⋯». */
const val TAG_FILA_PAGADA: String = "pagos:fila-pagada"
const val TAG_MENU_DE_LA_FILA_PAGADA: String = "pagos:menu-de-la-fila-pagada"

/** Lo que dice el «⋯» de una factura pagada al lector de pantalla. */
fun descripcionDelMenuDeLaFila(nombre: String): String = "Más opciones de $nombre"

/**
 * **Una factura pagada, compacta** (vuelta 48): ícono, nombre y «Pagado ✓ · $1.800.000 · 27 oct» en un
 * renglón. Tocarla abre el movimiento que la pagó ([onTocar]; sin movimiento, la regla). Lo que se
 * puede hacer con ella —«Quitar la marca», «No fue este» en lo que Movi emparejó solo— va en el «⋯» de
 * la derecha, no en dos botones con borde apilados: una lista de cosas resueltas no se llena de
 * botones. **Nunca mide más que una pendiente** (lo mide `LaFacturaPagadaEsCompactaTest`).
 */
@Composable
private fun FilaDeFacturaPagada(
    pago: PagoDelPeriodo,
    enVuelo: Boolean,
    onTocar: (() -> Unit)?,
    etiquetaDeTocar: String?,
    accionesDelMenu: List<Pair<String, () -> Unit>>,
    notaDelMenu: String? = null,
) {
    Box(modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_FACTURA)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Medida.altoDeFila)
            .testTag(TAG_FILA_PAGADA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (onTocar != null) Modifier.clickable(onClickLabel = etiquetaDeTocar, onClick = onTocar) else Modifier)
                .padding(start = Medida.margenDePantalla, top = Espacio.s, bottom = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(Medida.avatar), contentAlignment = Alignment.Center) {
                IconoDeCategoria(nombreParaElIconoDeLaFactura(pago.categoria, pago.nombre))
            }
            Spacer(Modifier.width(ESPACIO_ANTES_DEL_TEXTO_DE_FILA))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                Text(
                    pago.nombre,
                    style = Movi.textos.cuerpoFuerte,
                    color = Movi.colores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (enVuelo) {
                    Text("Guardando…", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val estado = if (pago.esIngreso) "Recibido" else "Pagado"
                        Text(estado, style = Movi.textos.apoyoMedio, color = Movi.colores.entra)
                        Icon(
                            Icons.Rounded.Check,
                            // Como la píldora de antes: el check dice «Pagado» (lo buscan las pruebas).
                            contentDescription = estado,
                            tint = Movi.colores.entra,
                            modifier = Modifier.padding(start = Espacio.xxs).size(Icono.s),
                        )
                        Text(
                            " · ${resumenDeLaFacturaPagada(pago)}",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (accionesDelMenu.isNotEmpty()) {
            Box(modifier = Modifier.testTag(TAG_MENU_DE_LA_FILA_PAGADA)) {
                var abierto by remember { mutableStateOf(false) }
                BotonDeIcono(
                    icono = Icons.Rounded.MoreHoriz,
                    descripcion = descripcionDelMenuDeLaFila(pago.nombre),
                    onClick = { abierto = true },
                    habilitado = !enVuelo,
                )
                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }, containerColor = Movi.colores.tarjeta) {
                    if (notaDelMenu != null) {
                        Text(
                            notaDelMenu,
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.xs),
                        )
                    }
                    accionesDelMenu.forEach { (texto, accion) ->
                        DropdownMenuItem(
                            text = { Text(texto, style = Movi.textos.cuerpo, color = Movi.colores.texto) },
                            onClick = { abierto = false; accion() },
                        )
                    }
                }
            }
            Spacer(Modifier.width(Espacio.xs))
        } else {
            Spacer(Modifier.width(Medida.margenDePantalla))
        }
    }
    }
}

/** Los planes de las cuotas, para la estimación de cada fila (ver [estimacionDeLaFila]). */
internal typealias PlanesDeCuotas = Map<String, PlanDelCredito>

/** ¿La fila de [pago] trae algo que ofrecer debajo, además de su botón? */
internal fun ofreceAlgoDebajo(pago: PagoDelPeriodo, conPropuesta: Boolean): Boolean = when (pago.estado) {
    EstadoDeLaFila.LISTO -> pago.automatica || (pago.eventId != null && !pago.derivado)
    EstadoDeLaFila.MARCADA_A_MANO -> true
    // Con o sin propuesta: o pregunta «¿fue este?», o dice que Movi no encontró el movimiento.
    EstadoDeLaFila.CON_DUDAS -> true
    EstadoDeLaFila.SIN_MOVIMIENTO -> true
    else -> false
}
