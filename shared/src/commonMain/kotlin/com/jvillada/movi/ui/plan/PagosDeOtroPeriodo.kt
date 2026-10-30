package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.LoQueViene
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.loQueSaleDe
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.DIAS_DE_LO_QUE_VIENE
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.NoSePudoLeerEnLaPantalla
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.periodos.PagosFijos
import com.jvillada.movi.ui.recurrentes.FilaDePagoDelPeriodo
import com.jvillada.movi.ui.recurrentes.TITULO_CHECKLIST_DEL_PERIODO
import com.jvillada.movi.ui.recurrentes.estadoVisualDe
import com.jvillada.movi.ui.recurrentes.fechaDeLaFila
import com.jvillada.movi.ui.recurrentes.textoDelMontoDelChecklist
import kotlinx.datetime.Clock

/*
 * # Vuelta 70 · Pagos mira el período anterior y el siguiente
 *
 * La revisión 4 (`r4-pagos-390-a.png`): el 29-oct, con el período cerrando el 31, Pagos decía «Falta
 * por pagar $9.000» y nada de lo que vencía el 5-nov (arriendo, cuota, Nu), aunque
 * `/api/payments/upcoming` lo traía. Pagos no tenía ‹ › como Actividad.
 *
 * - **‹ ›** ([NavegadorDelPeriodoDePagos]) mueven el período de «Facturas»: el anterior se lee como en
 *   «Tus períodos» ([PagosFijos] sobre `GET /api/periods/{id}`, cerrado); el siguiente, solo lectura:
 *   lo que vencerá, con las filas de `checklistDelPeriodo` del período siguiente sobre las MISMAS
 *   respuestas del tablero. Más allá del siguiente no se va: no hay respuesta que hable de eso.
 * - **«Lo que viene · próximos 15 días»** ([SeccionLoQueViene]): cuando quedan 7 días o menos del
 *   período, debajo de la lista, las filas del período siguiente que vencen en esos días, con su total
 *   (`loQueViene` en `:core`). No cuenta en «Falta por pagar», que sigue siendo del período.
 */

/** El navegador del período de Pagos: la línea del rango con ‹ y ›. */
const val TAG_NAVEGADOR_DEL_PERIODO_DE_PAGOS: String = "navegador-del-periodo-de-pagos"

/** Lo que se ve en «Facturas» con otro período a la vista (el anterior o el siguiente). */
const val TAG_PAGOS_DE_OTRO_PERIODO: String = "pagos-de-otro-periodo"

/** En dos columnas, mirando otro período: el rótulo de la columna izquierda («Período en curso · octubre»). */
const val TAG_PERIODO_EN_CURSO: String = "periodo-en-curso"

/** El comienzo del rótulo de la columna izquierda. */
const val PERIODO_EN_CURSO: String = "Período en curso"

/** Lo atenuado de la columna del período en curso mientras se mira otro. */
const val OPACIDAD_DE_LO_ATENUADO: Float = 0.5f

/** El bloque «Lo que viene». */
const val TAG_LO_QUE_VIENE: String = "lo-que-viene"

/** El título del bloque de los próximos días. */
val TITULO_LO_QUE_VIENE: String = "Lo que viene · próximos $DIAS_DE_LO_QUE_VIENE días"

/** El título de la lista del período siguiente. */
const val TITULO_LO_QUE_VENCERA: String = "Lo que vencerá"

/** Por qué el período siguiente puede no tener todo: lo de este período que falta todavía no rodó. */
const val NOTA_DEL_PERIODO_SIGUIENTE: String =
    "Solo lectura: se paga cuando llegue su período. Una tarjeta suma su pago mínimo."

/** Lo que dice el bloque de abajo: que no suma en la cifra del período. */
const val NOTA_DE_LO_QUE_VIENE: String = "Es del período siguiente: no suma en «Falta por pagar»."

/** ¿[a] es anterior a [b]? */
internal fun esAnterior(a: PeriodoFinanciero, b: PeriodoFinanciero): Boolean = a.year * 12 + a.month < b.year * 12 + b.month

/** ¿Se puede ir al siguiente de [visible]? Solo hasta el siguiente de hoy. */
internal fun puedeIrAlSiguiente(visible: PeriodoFinanciero, deHoy: PeriodoFinanciero): Boolean =
    esAnterior(visible, periodoSiguiente(deHoy))

/**
 * **La línea del período de Pagos con ‹ y ›.** La línea es la de siempre («Del 25 de octubre al 24 de
 * noviembre», [TAG_LINEA_DEL_PERIODO_DE_PLAN]); las flechas están desde el primer cuadro, también
 * cargando, para que nada salte al llegar el perfil. Apagadas fuera de «Facturas» (los presupuestos
 * son del período en curso) y la de adelante, en el siguiente.
 */
@Composable
internal fun NavegadorDelPeriodoDePagos(
    texto: String?,
    reservar: Boolean,
    habilitado: Boolean,
    puedeAdelante: Boolean,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_NAVEGADOR_DEL_PERIODO_DE_PAGOS)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Medida.margenDePantalla - (Medida.objetivoTactil - Icono.l) / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BotonDeIcono(Icons.Rounded.KeyboardArrowLeft, "Período anterior", onClick = onAnterior, habilitado = habilitado)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when {
                    texto != null -> Text(
                        text = texto,
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.testTag(TAG_LINEA_DEL_PERIODO_DE_PLAN),
                    )
                    reservar -> LineaEsqueleto(
                        fraccionDelAncho = 0.7f,
                        estilo = Movi.textos.apoyo,
                        modifier = Modifier.testTag(TAG_LINEA_DEL_PERIODO_DE_PLAN_ESQUELETO),
                    )
                }
            }
            BotonDeIcono(
                Icons.Rounded.KeyboardArrowRight,
                "Período siguiente",
                onClick = onSiguiente,
                habilitado = habilitado && puedeAdelante,
            )
        }
        Spacer(Modifier.height(Espacio.xs))
    }
}

/**
 * Lo que va debajo del selector en «Facturas» con [periodo] a la vista, si no es el de hoy: el
 * anterior, cerrado y leído del server; el siguiente, lo que vencerá.
 */
internal fun LazyListScope.pagosDeOtroPeriodo(
    periodo: PeriodoFinanciero,
    periodoDeHoy: PeriodoFinanciero,
    ajustes: PeriodSettings,
    estado: EstadoDelTableroDeRecurrentes,
) {
    item(key = "pagos-de-otro-periodo-${periodo.prefijo}") {
        Column(
            modifier = Modifier
                .padding(horizontal = Medida.margenDePantalla)
                .padding(bottom = Medida.entreSecciones)
                .testTag(TAG_PAGOS_DE_OTRO_PERIODO),
        ) {
            if (esAnterior(periodo, periodoDeHoy)) PagosDeUnPeriodoAnterior(periodo, periodoDeHoy)
            else PagosDelPeriodoSiguiente(estado, periodo, ajustes)
        }
    }
}

/** Un período cerrado: sus pagos fijos como en «Tus períodos» (lo pagado, lo que no se pagó). */
@Composable
private fun PagosDeUnPeriodoAnterior(periodo: PeriodoFinanciero, periodoDeHoy: PeriodoFinanciero) {
    var reintento by remember { mutableStateOf(0) }
    val detalle = rememberLectura(ClaveDeLectura.DetalleDePeriodo(periodo.prefijo), reintento, periodoDeHoy.prefijo) {
        Repositories.wallets.getDetalleDePeriodo(periodo.prefijo)
    }
    val hoy = remember { epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()) }
    val leido = detalle.valor
    when {
        leido != null -> PagosFijos(leido.pagosFijos, enCurso = false, hoy = hoy, onAgregar = null)
        detalle.fallo -> NoSePudoLeerEnLaPantalla(
            clave = "pagos-de-otro-periodo",
            texto = "No pudimos leer los pagos de ese período",
            onReintentar = { reintento++ },
            contenidoSuelto = { Seccion(titulo = TITULO_CHECKLIST_DEL_PERIODO) },
        )
        else -> EsqueletoDeLaLista(TITULO_CHECKLIST_DEL_PERIODO)
    }
}

/**
 * **El período siguiente, solo lectura**: lo que vencerá, con las filas de [checklistDelPeriodo] sobre
 * las MISMAS respuestas que el tablero ya trajo, con el vencimiento siguiente de lo que en este período
 * sigue pendiente (`conElSiguienteVencimiento`). Su total es lo que va a salir de las cuentas, con la
 * regla de la caja ([loQueSaleDe]): una tarjeta suma su pago mínimo; un ingreso, nada.
 */
@Composable
private fun PagosDelPeriodoSiguiente(estado: EstadoDelTableroDeRecurrentes, periodo: PeriodoFinanciero, ajustes: PeriodSettings) {
    when {
        estado.recurrentesNoSePudieronLeer -> NoSePudoLeerEnLaPantalla(
            clave = "pagos-de-otro-periodo",
            texto = "No pudimos leer los pagos de ese período",
            onReintentar = { estado.recargar() },
            contenidoSuelto = { Seccion(titulo = TITULO_LO_QUE_VENCERA) },
        )
        !(estado.vencimientosOk && estado.ocurrenciasOk) -> EsqueletoDeLaLista(TITULO_LO_QUE_VENCERA)
        else -> {
            val filas = remember(estado.upcomingRecurrentes, estado.ocurrencias, periodo, ajustes) {
                checklistDelPeriodo(estado.upcomingRecurrentes, estado.ocurrencias, periodo, ajustes, conElSiguienteVencimiento = true)
            }
            Seccion(titulo = TITULO_LO_QUE_VENCERA)
            Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                if (filas.isEmpty()) {
                    Text(
                        text = "Todavía no vence nada anotado en este período",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                        modifier = Modifier.padding(Medida.rellenoDeSuperficie),
                    )
                } else {
                    TotalDeLaLista("Vence en este período", loQueSaleDe(filas))
                    filas.forEach { pago ->
                        Separador(SangriaDeSeparador.ConInicio)
                        FilaDeLoQueVence(pago)
                    }
                }
            }
            Spacer(Modifier.height(Espacio.xs))
            Text(NOTA_DEL_PERIODO_SIGUIENTE, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        }
    }
}

/**
 * **«Lo que viene · próximos 15 días»**, debajo de la lista del período cuando le quedan 7 días o
 * menos ([com.jvillada.movi.shared.model.loQueViene]).
 */
@Composable
internal fun SeccionLoQueViene(viene: LoQueViene, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag(TAG_LO_QUE_VIENE)) {
        Seccion(titulo = TITULO_LO_QUE_VIENE)
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            TotalDeLaLista("Vence en los próximos $DIAS_DE_LO_QUE_VIENE días", viene.total)
            viene.filas.forEach { pago ->
                Separador(SangriaDeSeparador.ConInicio)
                FilaDeLoQueVence(pago)
            }
        }
        Spacer(Modifier.height(Espacio.xs))
        Text(NOTA_DE_LO_QUE_VIENE, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
    }
}

/** El renglón del total de una lista de solo lectura: el rótulo y la cifra. */
@Composable
private fun TotalDeLaLista(rotulo: String, total: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla)
            .padding(top = Espacio.s, bottom = Espacio.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Text(rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.weight(1f))
        Cifra(total, VarianteDeCifra.Chica)
    }
}

/**
 * Una fila de lo que vence, sin acciones: la MISMA fila de la lista del período ([FilaDePagoDelPeriodo])
 * con su fecha y su monto. Revisión de la 70: una tarjeta dice como monto **lo que va a salir** —«mínimo
 * $180.000», lo que suma el total y lo que resta la caja— y la deuda como dato secundario; sin el mínimo
 * cargado, la deuda y que no suma.
 */
@Composable
private fun FilaDeLoQueVence(pago: PagoDelPeriodo) {
    val (monto, evidencia) = montoDeLoQueVence(pago)
    FilaDePagoDelPeriodo(
        estado = estadoVisualDe(pago),
        esIngreso = pago.esIngreso,
        nombre = pago.nombre,
        fecha = fechaDeLaFila(pago),
        monto = monto,
        colorDelMonto = if (pago.esIngreso) Movi.colores.entra else Movi.colores.texto,
        evidencia = evidencia,
    )
}

/** El monto de una fila de lo que vence y su dato secundario (puro, para las pruebas). */
internal fun montoDeLoQueVence(pago: PagoDelPeriodo): Pair<String, String?> {
    if (!pago.montoEsSaldo) return textoDelMontoDelChecklist(pago) to null
    val deuda = "Deuda ${textoDelMontoDelChecklist(pago)}"
    val minimo = pago.pagoMinimo ?: return textoDelMontoDelChecklist(pago) to "Falta el pago mínimo: no suma"
    return "mínimo ${formatCOP(minimo)}" to deuda
}

/** El título y dos renglones esqueleto, mientras viaja la lista. */
@Composable
private fun EsqueletoDeLaLista(titulo: String) {
    Seccion(titulo = titulo)
    Superficie(modifier = Modifier.fillMaxWidth()) {
        LineaEsqueleto(fraccionDelAncho = 0.8f, estilo = Movi.textos.cuerpo)
        Spacer(Modifier.height(Movi.espacios.medio))
        LineaEsqueleto(fraccionDelAncho = 0.6f, estilo = Movi.textos.cuerpo)
    }
}
