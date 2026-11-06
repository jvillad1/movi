package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.ExtractoDeLaTarjeta
import com.jvillada.movi.ui.quickadd.lineaDelAbonoPlaneado
import com.jvillada.movi.ui.quickadd.pastillaDelAbono
import com.jvillada.movi.shared.model.abonosPlaneados
import com.jvillada.movi.ui.credits.ROTULO_DEL_EXTRACTO
import com.jvillada.movi.ui.credits.montoDelExtracto
import com.jvillada.movi.ui.credits.loQueFaltaDelExtracto
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.categorias.ICONO_DE_CATEGORIA_RESPALDO
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.aparienciaDe
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.TonoDeSubtitulo
import androidx.compose.ui.text.withStyle
import com.jvillada.movi.shared.model.FinancialEvent
import androidx.compose.ui.platform.LocalDensity
import com.jvillada.movi.ui.components.ds.FondoDeBarra
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.Superficie
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
data class ResumenDeFacturas(
    val falta: Long,
    val pagado: Long,
    val quedan: Int,
    val de: Int,
    /** Vuelta 63: los abonos planeados que faltan ([abonosPlaneados]). No suman en [falta]: van aparte. */
    val abonos: Long = 0L,
    /**
     * Vuelta 86: «+ Tarjeta Nu sin contar» ([lineaDeLoQueNoSuma]): lo pendiente que no suma en [falta], dicho
     * debajo del total. `null` si todo suma.
     */
    val sinContar: String? = null,
) {
    val total: Long get() = falta + pagado

    /** Cuánto de la barra va lleno: lo pagado sobre el total (0 sin total). */
    val fraccionPagada: Float get() = if (total <= 0L) 0f else (pagado.toDouble() / total).toFloat()
}

/** [ResumenDeFacturas] del checklist (sin los ingresos). Pura. */
fun resumenDeFacturas(checklist: List<PagoDelPeriodo>): ResumenDeFacturas = ResumenDeFacturas(
    falta = faltaPorPagar(checklist),
    pagado = yaPagadoEnElPeriodo(checklist),
    quedan = pagosPendientes(checklist).size,
    // Vuelta 63: un abono planeado no es un pago del período (no cuenta en «quedan 4 de 7»).
    de = checklist.count { !it.esIngreso && !it.esAbono },
    abonos = abonosPlaneados(checklist),
    sinContar = lineaDeLoQueNoSuma(checklist),
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
            // Vuelta 72: una tarjeta pagada por debajo de su mínimo va con lo pendiente.
            { if (it.hecho) 2 else if (it.vencido) 0 else 1 },
            { it.diasParaVencer },
        ).thenByDescending { it.monto },
    )

/** La factura cuyo «Anotar pago» va en amarillo: la primera sin pagar del orden. `null` sin ninguna. */
fun laMasUrgente(ordenadas: List<PagoDelPeriodo>): PagoDelPeriodo? = ordenadas.firstOrNull { !it.hecho && !it.esAbono }

/**
 * Vuelta 72 · **la línea de una tarjeta pagada por debajo de su mínimo**: «Pagaste $100.000 · faltan
 * $80.000 para el mínimo», o `null` si la fila no es eso ([PagoDelPeriodo.faltaDelMinimo]). Va en tono de
 * aviso donde iría la pastilla de vencimiento. Pura.
 */
fun lineaDelMinimo(pago: PagoDelPeriodo): String? {
    val falta = pago.faltaDelMinimo ?: return null
    return "Pagaste ${formatCOP(pago.pagadoContraElMinimo)} · faltan ${formatCOP(falta)} para el mínimo"
}

/** El tag de esa línea. */
const val TAG_FALTA_DEL_MINIMO: String = "pagos:falta-del-minimo"

/** Vuelta 133 · el tag de «La de octubre fue $Y» en la fila. */
const val TAG_CUOTA_ANTERIOR_DE_LA_FILA: String = "pagos:cuota-anterior"

/**
 * Vuelta 133 · **«La de octubre fue $1.262.300»** debajo de la cuota de un crédito que cambia, sin pagar;
 * `null` en lo demás (y sin una anterior). Con «unos» delante del monto, la fila dice lo que
 * [com.jvillada.movi.shared.model.textoDeLaCuotaQueCambia]: «unos $X (la de octubre fue $Y)». Pura.
 */
fun lineaDeLaCuotaAnterior(pago: PagoDelPeriodo): String? {
    if (pago.hecho) return null
    val anterior = pago.cuotaQueCambia?.anterior?.takeIf { it.monto > 0L } ?: return null
    val mes = com.jvillada.movi.shared.model.mesDeLaCuotaPagada(anterior.periodo) ?: return null
    // Revisión de la vuelta 133: la estimación es la anterior; con la misma cifra, «Como la de octubre».
    return if (anterior.monto == pago.monto) "Como la de $mes" else "La de $mes fue ${formatCOP(anterior.monto)}"
}

/** Vuelta 126 · el tag de la cadencia de una factura no mensual («Cada 3 meses»). */
const val TAG_CADENCIA_DE_LA_FILA: String = "pagos:cadencia-de-la-fila"

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
    // Vuelta 63: un abono planeado no vence: «El día 15», neutra.
    if (pago.esAbono) return pastillaDelAbono(pago) to TonoDePildora.Neutra
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
 * **La tarjeta «Falta por pagar»** (vuelta 40): la cifra héroe de la pantalla, «de $X · quedan N de M
 * pagos», la barra en tinta y «Llevas $X pagados». Es la única cifra héroe de Pagos
 * (`UnaSolaCifraHeroePorPantallaTest`): el Disponible, más abajo, va en la de tarjeta.
 *
 * **Vuelta 79 · el amarillo deja de ser superficie**: era el fondo de la marca con la tinta encima. Ahora
 * es una [Superficie] blanca destacada (con la sombra del héroe de Inicio) y la barra en tinta sobre la
 * capa 3 ([FondoDeBarra.Destacada]). El amarillo de Pagos es la acción de la factura más urgente.
 */
@Composable
internal fun TarjetaFaltaPorPagar(
    resumen: ResumenDeFacturas,
    modifier: Modifier = Modifier,
    /** Vuelta 136: el pago más urgente con su acción (el amarillo de Pagos). `null`: nada pendiente. */
    loMasUrgente: LoMasUrgente? = null,
) {
    Superficie(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_TARJETA_FALTA_POR_PAGAR),
        destacada = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Column {
                Text(TITULO_FALTA_POR_PAGAR, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
                Cifra(resumen.falta, VarianteDeCifra.Heroe, tono = TonoDeCifra.Neutro)
                Text(lineaDeLoQueQueda(resumen), style = Movi.textos.cuerpoRegular, color = Movi.colores.textoMedio)
                // Vuelta 63: el abono planeado no es parte de lo que falta: se dice aparte.
                if (resumen.abonos > 0L) {
                    Text(lineaDelAbonoPlaneado(resumen.abonos), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                }
                // Vuelta 86: lo que está en la lista y no en la cifra, dicho bajo la cifra (sin monto: no se sabe).
                resumen.sinContar?.let {
                    Text(it, style = Movi.textos.apoyoMedio, color = Movi.colores.aviso, modifier = Modifier.testTag(TAG_SIN_CONTAR))
                }
            }
            BarraDeProgreso(
                resumen.fraccionPagada,
                modifier = Modifier.semantics { contentDescription = "${lineaDeLoPagado(resumen)} de ${formatCOP(resumen.total)}" },
                fondo = FondoDeBarra.Destacada,
            )
            Text(lineaDeLoPagado(resumen), style = Movi.textos.apoyoMedio, color = Movi.colores.textoMedio)
            loMasUrgente?.let { RenglonDeLoMasUrgente(it) }
        }
    }
}

/**
 * Vuelta 136 · **lo más urgente, con su acción**, al pie de «Falta por pagar»: «Lo más urgente: Arriendo» y,
 * **siempre en su propio renglón**, el botón (revisión, M4: al llegar un gasto que se parece a la cuota el rótulo
 * cambia de «Anotar pago» a «¿Es el de $980.000?», y en un renglón compartido el botón podía bajar: la tarjeta
 * saltaba).
 */
@Composable
private fun RenglonDeLoMasUrgente(urgente: LoMasUrgente) {
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_LO_MAS_URGENTE), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Separador(SangriaDeSeparador.Ninguna, modifier = Modifier.padding(top = Espacio.xs))
        // Un renglón, «Lo más urgente: Arriendo» (el nombre en negrita): el estado ya lo dice su fila,
        // que va primera en la lista.
        Text(
            textoDeLoMasUrgente(urgente.nombre),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Boton(
            urgente.boton,
            onClick = urgente.onClick,
            variante = if (urgente.principal) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
            tamano = TamanoDeBoton.S,
            habilitado = urgente.habilitado,
            modifier = Modifier.testTag(TAG_ACCION_DE_LO_MAS_URGENTE),
        )
    }
}

// ── Vuelta 136 · una sola fila de factura ─────────────────────────────────────────────────────────

/**
 * **Lo que dice el renglón de estado de una factura** (vuelta 136): un texto y su tono, siempre en el mismo
 * lugar de la fila. [conCheck] pone el visto antes del texto («Pagado»): el color nunca es la única pista.
 */
data class EstadoDeLaFactura(
    val texto: String,
    val tono: TonoDeSubtitulo,
    val conCheck: Boolean = false,
    /**
     * Revisión de la 136 (M2, M7) · la versión larga, solo si cabe entera en la fila («Venció hace 3 días · falta
     * el mínimo», «Vence el 22 nov · cada 3 meses»); si no, [texto]. `null`: no hay versión larga.
     */
    val siCabe: String? = null,
)

/** Lo que dice el renglón de estado de una fila mientras una escritura suya viaja. */
const val GUARDANDO_LA_FACTURA: String = "Guardando…"

/** «Abono planeado · el día 15», «Abono planeado · este mes». */
const val ABONO_PLANEADO: String = "Abono planeado"

/**
 * **El renglón de estado de una factura** (vuelta 136), en este orden —lo primero que aplica—:
 *
 * - con una escritura en vuelo, «Guardando…»;
 * - sellada con el extracto de su tarjeta todavía abierto: «Faltan $1.292.700 del extracto», en aviso (no se ve
 *   pagada), y si cabe entera la larga, [loQueFaltaDelExtracto] («Abonaste $100.000 · faltan $1.292.700 del extracto…»);
 * - **pagada** ([PagoDelPeriodo.hecho]): «Pagado · 25 oct» («Recibido» si es un ingreso, «Mínimo cubierto»
 *   si el mínimo del extracto quedó cubierto sin un pago propio), en el verde de `entra` con el visto;
 * - una tarjeta pagada por debajo de su mínimo: [lineaDelMinimo], en aviso (cortada a un renglón en la fila;
 *   la hoja la dice entera);
 * - con un movimiento que Movi propone **cerca del monto** ([candidato]; revisión de la 136, I1: uno lejano o el
 *   gasto que se parece a la cuota no tapan el vencimiento, se ven en la hoja): «¿Es el de $4.000.000?»
 *   ([enlaceDeLaPropuestaLejana]), en el azul del enlace;
 * - una fila que no suma en «Falta por pagar» (vuelta 86): «No suma: falta el mínimo» ([pildoraDeLoQueNoSuma]), en aviso;
 *   vencida, gana el vencimiento («Venció hace 3 días · falta el mínimo» si cabe; M2);
 * - un abono: «Abono planeado · el día 15» ([pastillaDelAbono]);
 * - lo demás (con «· cada 3 meses» si no es de cada mes, está por vencer y cabe; M7), la pastilla de vencimiento de siempre ([pastillaDeVencimiento]: «Vence el 17 nov», «Venció hace 3
 *   días», «Se cobra sola el 5», «¿Se cobró?»), con su tono: el aviso, o neutro.
 *
 * Todo sale de funciones que ya existían: ningún estado nuevo. Pura.
 */
fun estadoDeLaFactura(
    pago: PagoDelPeriodo,
    candidato: FinancialEvent?,
    enVuelo: Boolean = false,
    /** Vuelta 86: la fila no suma en «Falta por pagar» ([porQueNoSuma]); lo dice en aviso. */
    noSuma: PorQueNoSuma? = null,
    /** Vuelta 62 · el extracto de la tarjeta de esta fila (`extractoDeLaFila`); en una sellada, sigue abierto. */
    extracto: ExtractoDeLaTarjeta? = null,
): EstadoDeLaFactura {
    if (enVuelo) return EstadoDeLaFactura(GUARDANDO_LA_FACTURA, TonoDeSubtitulo.Normal)
    // Revisión de la 136 (C1): un abono menor que el extracto sella la fila, pero el extracto sigue abierto: la
    // fila no puede verse pagada. Dice lo que falta, en aviso (la regla de la vuelta 62).
    // Re-revisión (1): la frase larga a 360 dp cortaba justo la cifra que falta; lo corto es «Faltan $X del extracto»
    // y la larga va solo si cabe entera.
    if (pago.hecho && extracto != null) {
        return EstadoDeLaFactura(
            "Faltan ${montoDelExtracto(extracto.facturado, extracto.moneda)} del extracto",
            TonoDeSubtitulo.Aviso,
            siCabe = loQueFaltaDelExtracto(extracto),
        )
    }
    if (pago.hecho) {
        val estado = when {
            pago.esIngreso -> "Recibido"
            // Vuelta 72 (revisión): el mínimo del extracto ya está cubierto sin un pago propio.
            !pago.pagado -> MINIMO_CUBIERTO
            else -> "Pagado"
        }
        val fecha = pago.pagadoEl?.let(::fechaCortaDeFactura)?.takeIf { pago.pagado }
        return EstadoDeLaFactura(if (fecha == null) estado else "$estado · $fecha", TonoDeSubtitulo.Exito, conCheck = true)
    }
    lineaDelMinimo(pago)?.let { return EstadoDeLaFactura(it, TonoDeSubtitulo.Aviso) }
    if (candidato != null) return EstadoDeLaFactura(enlaceDeLaPropuestaLejana(candidato), TonoDeSubtitulo.Enlace)
    val (vencimiento, tonoDelVencimiento) = pastillaDeVencimiento(pago, conPropuesta = false)
    // Vuelta 86: lo que no suma lo dice la fila («No suma: falta el mínimo»); «Cargar mínimo» va en la hoja.
    // Revisión de la 136 (M2): vencida, gana el vencimiento, con «· falta el mínimo» si cabe.
    if (noSuma != null) {
        return if (pago.vencido) {
            EstadoDeLaFactura(vencimiento, TonoDeSubtitulo.Aviso, siCabe = "$vencimiento · ${pildoraDeLoQueNoSuma(noSuma).removePrefix(PREFIJO_DE_NO_SUMA)}")
        } else {
            EstadoDeLaFactura(pildoraDeLoQueNoSuma(noSuma), TonoDeSubtitulo.Aviso)
        }
    }
    if (pago.esAbono) {
        return EstadoDeLaFactura("$ABONO_PLANEADO · ${pastillaDelAbono(pago).replaceFirstChar { it.lowercase() }}", TonoDeSubtitulo.Normal)
    }
    // Revisión de la 136 (M7): por vencer, la cadencia de una factura que no es de cada mes, si cabe.
    val cadencia = cadenciaDeLaFila(pago)?.takeIf { !pago.vencido }?.replaceFirstChar { it.lowercase() }
    return EstadoDeLaFactura(
        vencimiento,
        if (tonoDelVencimiento == TonoDePildora.Neutra) TonoDeSubtitulo.Normal else TonoDeSubtitulo.Aviso,
        siCabe = cadencia?.let { "$vencimiento · $it" },
    )
}

/** Lo que se le quita a «No suma: falta el mínimo» para decirlo después del vencimiento. */
private const val PREFIJO_DE_NO_SUMA: String = "No suma: "

/**
 * **La cifra de una factura y lo que la acompaña** (vuelta 136): «unos» antes del monto de una cuota que
 * cambia sin pagar ([estimada], la regla única `laCifraDelPagoEsEstimada` de la vuelta 134, que la pinta
 * `ConUnos`), y después «saldo» si el monto de una tarjeta es su saldo o «extracto» si es lo facturado al
 * corte (vuelta 62). La misma en la fila y en la hoja. Pura.
 *
 * @property despues lo que va después del monto, o `null`.
 */
data class CifraDeLaFactura(val texto: String, val estimada: Boolean = false, val despues: String? = null)

/** Ver [CifraDeLaFactura]. Pura. */
fun cifraDeLaFactura(pago: PagoDelPeriodo, extracto: ExtractoDeLaTarjeta?): CifraDeLaFactura = when {
    // Vuelta 62: lo que pide el extracto, no el saldo entero. Solo sin pagar (C1): sellada, va lo que salió.
    extracto != null && !pago.hecho -> CifraDeLaFactura(montoDelExtracto(extracto.facturado, extracto.moneda), despues = ROTULO_DEL_EXTRACTO)
    // Vuelta 133/134: la cuota que cambia es una estimación, y se dice («unos $X»), con la regla única.
    com.jvillada.movi.shared.model.laCifraDelPagoEsEstimada(pago) -> CifraDeLaFactura(textoDelMontoDelChecklist(pago), estimada = true)
    // Una tarjeta se paga con su mínimo o su saldo: el monto dice de cuál se habla.
    !pago.hecho && pago.montoEsSaldo -> CifraDeLaFactura(textoDelMontoDelChecklist(pago), despues = "saldo")
    else -> CifraDeLaFactura(textoDelMontoDelChecklist(pago))
}

/**
 * Con la letra del sistema por encima de esto (la de la app ya es ×1,12), la fila de una factura reserva dos
 * renglones para el nombre y dos para el estado (vuelta 136). La misma regla para todas: crecen juntas.
 */
const val ESCALA_DE_LETRA_GRANDE_EN_LAS_FACTURAS: Float = 1.2f

/** Cuántos renglones ocupan el nombre y el estado de una factura con esta escala de letra. Pura. */
fun renglonesDeLaFactura(escalaDeLaLetra: Float): Int = if (escalaDeLaLetra > ESCALA_DE_LETRA_GRANDE_EN_LAS_FACTURAS) 2 else 1

/** El tag de una factura sin pagar (la pagada lleva [TAG_FILA_PAGADA]); las dos, además, [TAG_FILA_DE_FACTURA]. */
const val TAG_FILA_PENDIENTE: String = "pagos:fila-pendiente"

/** El tag de la cifra de una factura con lo que la acompaña («unos», «saldo», «extracto»). */
const val TAG_CIFRA_DE_LA_FACTURA: String = "pagos:cifra-de-la-factura"

/** Lo que dice el toque sobre una factura al lector de pantalla. */
const val ETIQUETA_ABRIR_LA_FACTURA: String = "Ver el pago"

/**
 * **Una factura del período** (vuelta 136: **una sola anatomía** para toda fila de «Tus facturas del período»
 * —vencida, por vencer, por confirmar, pagada, abono, cuota, tarjeta, débito automático—). Es la [Fila] del
 * sistema:
 *
 * - a la izquierda el ícono de su categoría ([nombreParaElIconoDeLaFactura]);
 * - en el centro **dos renglones fijos**: el nombre (uno; dos con la letra grande; si no cabe, cortado por el
 *   principio, «…Master Black 3684») y el renglón de estado ([estadoDeLaFactura]: un texto y un tono, con la
 *   fecha: «Vence el 17 nov», «Pagado · 25 oct»);
 * - a la derecha, solo la cifra ([cifraDeLaFactura], con «unos», «saldo» o «extracto»);
 * - **toda la fila se toca** y abre la hoja de esa factura ([onAbrir]), donde vive todo lo demás.
 *
 * Todas miden lo mismo con la letra normal; con la grande reservan los mismos renglones y crecen igual
 * (`reservarRenglones`). Lo vigila `UnaSolaFilaDeFacturaTest`.
 */
@Composable
internal fun FilaDeFactura(
    pago: PagoDelPeriodo,
    estado: EstadoDeLaFactura,
    extracto: ExtractoDeLaTarjeta?,
    onAbrir: (() -> Unit)?,
) {
    val renglones = renglonesDeLaFactura(LocalDensity.current.fontScale)
    Box(modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_FACTURA)) {
        Box(modifier = Modifier.fillMaxWidth().testTag(if (pago.hecho) TAG_FILA_PAGADA else TAG_FILA_PENDIENTE)) {
            Fila(
                titulo = pago.nombre,
                subtitulo = estado.texto,
                subtituloSiCabe = estado.siCabe,
                // Vuelta 40: el ícono de la categoría, o el del nombre si la categoría no dice nada.
                inicio = { IconoDeCategoria(nombreParaElIconoDeLaFactura(pago.categoria, pago.nombre)) },
                // Revisión de la 136 (I2, M1): a la derecha solo la cifra; la fecha la dice el renglón de estado.
                final = {
                    Box(modifier = Modifier.testTag(TAG_CIFRA_DE_LA_FACTURA)) {
                        CifraConRotulosDeLaFactura(cifraDeLaFactura(pago, extracto), VarianteDeCifra.Fila)
                    }
                },
                onClick = onAbrir,
                onClickLabel = ETIQUETA_ABRIR_LA_FACTURA,
                lineasDelTitulo = renglones,
                // Revisión de la 136 (I2): si no cabe, se corta por el principio («…Master Black 3684»).
                cortarElTituloPorElPrincipio = true,
                lineasDelSubtitulo = renglones,
                reservarRenglones = true,
                tonoDelSubtitulo = estado.tono,
                iconoDelSubtitulo = if (estado.conCheck) Icons.Rounded.Check else null,
                // El texto ya dice «Pagado»: el visto no lo repite al lector.
                descripcionDelIconoDelSubtitulo = null,
                chevron = false,
            )
        }
    }
}

/** El monto de [cifra] con lo que va antes y después, en la variante [variante]. Fila y hoja. */
@Composable
internal fun CifraConRotulosDeLaFactura(cifra: CifraDeLaFactura, variante: VarianteDeCifra) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
        // Vuelta 134: «unos» se arma en un solo lugar (`ConUnos`).
        com.jvillada.movi.ui.components.ds.ConUnos(estimada = cifra.estimada) {
            MontoDeFila(cifra.texto, color = Movi.colores.texto, variante = variante)
        }
        cifra.despues?.let { Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1) }
    }
}

/**
 * «$1.800.000 · 27 oct»: lo que salió y cuándo, en una factura pagada (vuelta 48; desde la vuelta 136 lo dice
 * la hoja de la factura), con lo que de verdad salió ([textoDelMontoDelChecklist]) y el día en que salió, si
 * se sabe. Pura.
 */
fun resumenDeLaFacturaPagada(pago: PagoDelPeriodo): String =
    if (!pago.pagado && pago.restoDelMinimo == 0L) {
        // Re-revisión: con lo facturado en cero no hay nada que pagar de ese extracto.
        if (pago.pagadoDesdeElCorte > 0L) "pagaste ${formatCOP(pago.pagadoDesdeElCorte)}" else "sin saldo facturado"
    }
    else listOfNotNull(textoDelMontoDelChecklist(pago), pago.pagadoEl?.let(::fechaCortaDeFactura)).joinToString(" · ")

/** Vuelta 72 (revisión) · la fila del extracto con el mínimo cubierto, sin un pago que la tilde. */
const val MINIMO_CUBIERTO: String = "Mínimo cubierto"

/** El tag de una factura pagada (vuelta 48). */
const val TAG_FILA_PAGADA: String = "pagos:fila-pagada"

// ── Vuelta 136 · lo más urgente, en la tarjeta «Falta por pagar» ─────────────────────────────────────

/** El rótulo del renglón de lo más urgente en la tarjeta «Falta por pagar». */
const val ROTULO_DE_LO_MAS_URGENTE: String = "Lo más urgente"

/** «Lo más urgente: Arriendo», con el nombre en `cuerpoFuerte` y la tinta. */
@Composable
private fun textoDeLoMasUrgente(nombre: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append("$ROTULO_DE_LO_MAS_URGENTE: ")
        withStyle(Movi.textos.cuerpoFuerte.toSpanStyle().copy(color = Movi.colores.texto)) { append(nombre) }
    }

/** El tag de ese renglón y el de su botón. */
const val TAG_LO_MAS_URGENTE: String = "pagos:lo-mas-urgente"
const val TAG_ACCION_DE_LO_MAS_URGENTE: String = "pagos:accion-de-lo-mas-urgente"

/**
 * **El renglón de lo más urgente en «Falta por pagar»** (vuelta 136): «Lo más urgente: Arriendo» y una acción. Así
 * las filas de la lista son todas iguales y el amarillo de Pagos sigue siendo el pago más urgente (vuelta 79).
 *
 * @property boton «Anotar pago» o, si Movi tiene un candidato, «¿Es el de $X?» (que abre la hoja).
 * @property principal el amarillo; ver [accionPrincipalDeLaFila].
 */
data class LoMasUrgente(
    val nombre: String,
    val boton: String,
    val principal: Boolean,
    val habilitado: Boolean,
    val onClick: () -> Unit,
)

/** Los planes de las cuotas, para la estimación de cada fila (ver [estimacionDeLaFila]). */
internal typealias PlanesDeCuotas = Map<String, PlanDelCredito>
