package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FilaDelAnalisis
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.VistaDelAnalisis
import com.jvillada.movi.shared.model.analisisDelPeriodo
import com.jvillada.movi.shared.model.filasDelAnalisis
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.colorDeLaCifra
import com.jvillada.movi.ui.components.ds.estiloDeLaCifra
import com.jvillada.movi.ui.dashboard.CONTRA_EL_PERIODO_PASADO
import com.jvillada.movi.ui.dashboard.CategoriaDelPeriodo
import com.jvillada.movi.ui.dashboard.DiferenciaDeLaCategoria
import com.jvillada.movi.ui.dashboard.diferenciaDeLaCategoria
import kotlinx.datetime.LocalDate

/**
 * # El panel de Movimientos sin nada elegido: el resumen del período (vuelta de diseño 9)
 *
 * En pantalla ancha, sin un movimiento elegido, el panel de la derecha decía «Elige un movimiento» y
 * dejaba ~800 px en blanco. Ahora muestra **el período que la lista tiene a la vista**: lo que entró,
 * lo que salió y el neto, y el gasto por categoría con su diferencia contra el período pasado a la
 * misma altura —lo mismo que «En qué se va» y su widget del Inicio—, con un enlace a «En qué se va».
 * Tocar una categoría filtra la lista con [com.jvillada.movi.shared.model.FiltroDeMovimientos], igual
 * que desde «En qué se va». Al elegir un movimiento el panel vuelve a ser su detalle.
 *
 * **Ninguna cuenta nueva.** Lo que salió y las categorías son [analisisDelPeriodo] y
 * [filasDelAnalisis] (la regla de «Gastos», solo tu parte); lo que entró es la regla del chip
 * «Ingresos» ([matchesChip], con el mismo [isCashFlow] que el gasto) en pesos, en los mismos días. Todo sobre la historia que la lista ya
 * leyó: ninguna lectura más. En el teléfono no hay panel, y nada de esto se pinta.
 */

/** El tag del resumen del período en el panel de Movimientos. */
const val TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL: String = "movimientos-resumen-del-periodo"

/** El tag de cada fila de categoría del resumen. */
const val TAG_CATEGORIA_DEL_RESUMEN: String = "movimientos-categoria-del-resumen"

/** El tag de la diferencia de una categoría contra el período pasado («$120.000 más»). */
const val TAG_DIFERENCIA_DEL_RESUMEN: String = "movimientos-diferencia-del-resumen"

/** El enlace a «En qué se va». */
const val VER_EN_QUE_SE_VA: String = "Ver en qué se va"

/** El título de la lista de categorías del resumen. */
const val TITULO_DEL_GASTO_POR_CATEGORIA: String = "Gasto por categoría"

/** Lo que dice el resumen debajo de todo: que un movimiento se abre tocándolo. */
const val PISTA_PARA_ELEGIR_UN_MOVIMIENTO: String =
    "Toca un movimiento de la lista para ver su detalle, corregirlo o anularlo."

/**
 * El resumen de un período, como lo pinta el panel.
 *
 * @property entro lo que entró en pesos (regla del chip «Ingresos»).
 * @property salio lo gastado (`AnalisisDelPeriodo.total`): la suma de [categorias].
 * @property categorias las categorías de mayor a menor, con lo del anterior a la misma altura.
 */
data class ResumenDelPanel(
    val periodo: PeriodoFinanciero,
    val entro: Long,
    val salio: Long,
    val categorias: List<FilaDelAnalisis>,
) {
    /** Lo que entró menos lo que salió. */
    val neto: Long get() = entro - salio

    /** ¿Alguna categoría se compara con el período pasado? (Movi cubrió el anterior entero.) */
    val conComparacion: Boolean get() = categorias.any { it.anterior != null }
}

/**
 * **El resumen de [periodo]** sobre [dias] (la historia entera de Movimientos, sin anulados). Pura.
 *
 * @param hoy el día de hoy en la zona de la app (la «altura» de la comparación).
 * @param tiposDeCuenta el tipo de cada cuenta, por id (ver `esGastoDelPeriodo`).
 */
fun resumenDelPanel(
    dias: List<EventDay>,
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
    hoy: LocalDate,
    tiposDeCuenta: Map<String, AccountType> = emptyMap(),
): ResumenDelPanel {
    val eventos = dias.flatMap { it.items }
    val analisis = analisisDelPeriodo(eventos, periodo, settings, hoy, tiposDeCuenta)
    // Los mismos días que cuenta el análisis (por la fecha del movimiento en la zona de la app).
    val entro = eventos
        .filter { it.currency == "COP" && matchesChip(it, CHIP_INGRESOS) }
        // Lo mismo que `esGastoDelPeriodo` agrega del lado del gasto: traspasos, aperturas y ajustes
        // no son plata que entró, aunque el movimiento no traiga la bandera derivada.
        .filter { isCashFlow(tiposDeCuenta[it.accountId] ?: AccountType.CHECKING, it.type, it.category) }
        .filter { epochMillisToAppDate(it.timestamp) in analisis.desde..analisis.hasta }
        .sumOf { montoContado(it) }
    return ResumenDelPanel(
        periodo = periodo,
        entro = entro,
        salio = analisis.total,
        categorias = filasDelAnalisis(analisis, VistaDelAnalisis.CATEGORIA),
    )
}

/**
 * La diferencia de una fila contra el período pasado, dicha como en el widget «En qué se va» del
 * Inicio: la MISMA [diferenciaDeLaCategoria] («$120.000 más», «nuevo»…). `null` sin comparación.
 */
fun diferenciaDeLaFila(fila: FilaDelAnalisis): DiferenciaDeLaCategoria? =
    diferenciaDeLaCategoria(
        CategoriaDelPeriodo(nombre = fila.nombre, gastado = fila.gasto, fraccion = fila.fraccion, anterior = fila.anterior),
    )

/** El tono del neto: en `entra` si quedó plata, en `sale` si salió más de lo que entró. */
fun tonoDelNeto(neto: Long): TonoDeCifra = when {
    neto > 0 -> TonoDeCifra.Ingreso
    neto < 0 -> TonoDeCifra.Alerta
    else -> TonoDeCifra.Apagado
}

/**
 * El panel sin nada elegido: [resumen] del período, que se desplaza por su cuenta (la rueda sobre
 * el panel no llega a la lista: ver `ListaYPanelDeMovimientos`).
 */
@Composable
internal fun ResumenDelPeriodoEnElPanel(
    resumen: ResumenDelPanel,
    onCategoria: (FilaDelAnalisis) -> Unit,
    onVerEnQueSeVa: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Medida.margenDePantalla)
            .padding(top = Medida.dentroDeSeccion, bottom = Medida.entreSecciones)
            .testTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL),
    ) {
        Seccion(titulo = tituloDelPeriodo(resumen.periodo))
        Superficie(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                CifraDelResumen("Entró", resumen.entro, TonoDeCifra.Ingreso, Modifier.weight(1f))
                CifraDelResumen("Salió", resumen.salio, TonoDeCifra.Neutro, Modifier.weight(1f))
                CifraDelResumen("Neto", resumen.neto, tonoDelNeto(resumen.neto), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(Medida.entreSecciones))
        Seccion(titulo = TITULO_DEL_GASTO_POR_CATEGORIA, accion = VER_EN_QUE_SE_VA, onAccion = onVerEnQueSeVa)
        if (resumen.categorias.isEmpty()) {
            Text("No hay gastos en este período.", style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        } else {
            // Contra qué se compara, dicho una vez (cada diferencia dice la frase entera al lector).
            if (resumen.conComparacion) {
                Text(CONTRA_EL_PERIODO_PASADO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(Espacio.xs))
            }
            Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                resumen.categorias.forEachIndexed { i, fila ->
                    if (i > 0) Separador()
                    FilaDeCategoriaDelResumen(fila, onClick = { onCategoria(fila) })
                }
            }
        }

        Spacer(Modifier.height(Medida.entreSecciones))
        Text(PISTA_PARA_ELEGIR_UN_MOVIMIENTO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    }
}

/** Un rótulo («Entró») y su cifra, en un tercio de la superficie. */
@Composable
private fun CifraDelResumen(rotulo: String, monto: Long, tono: TonoDeCifra, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
        Text(rotulo, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 1)
        Cifra(monto, VarianteDeCifra.Grande, tono = tono)
    }
}

/**
 * Una categoría: su ícono, su nombre, cuánto pesa y cuántas veces; a la derecha el monto y la
 * diferencia contra el período pasado. Tocarla filtra la lista de al lado.
 */
@Composable
private fun FilaDeCategoriaDelResumen(fila: FilaDelAnalisis, onClick: () -> Unit) {
    val diferencia = diferenciaDeLaFila(fila)
    Fila(
        titulo = fila.nombre,
        subtitulo = "${(fila.fraccion * 100).toInt()} % · ${if (fila.veces == 1) "1 movimiento" else "${fila.veces} movimientos"}",
        modifier = Modifier.testTag(TAG_CATEGORIA_DEL_RESUMEN),
        inicio = { IconoDeCategoria(fila.nombre) },
        final = {
            Column(horizontalAlignment = Alignment.End) {
                Cifra(fila.gasto)
                if (diferencia != null) {
                    Text(
                        text = diferencia.texto,
                        style = estiloDeLaCifra(VarianteDeCifra.Chica, Movi.textos),
                        color = colorDeLaCifra(diferencia.tono, Movi.colores),
                        maxLines = 1,
                        modifier = Modifier
                            .testTag(TAG_DIFERENCIA_DEL_RESUMEN)
                            .semantics { contentDescription = diferencia.descripcion },
                    )
                }
            }
        },
        onClick = onClick,
        onClickLabel = "Ver los movimientos",
    )
}
