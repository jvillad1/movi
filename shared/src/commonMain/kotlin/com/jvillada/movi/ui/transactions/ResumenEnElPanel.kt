package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FilaDelAnalisis
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.VistaDelAnalisis
import com.jvillada.movi.shared.model.analisisDelPeriodo
import com.jvillada.movi.shared.model.filasDelAnalisis
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.movimientosDelAnalisis
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.TonoDeBarra
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.TonoDePildora
import androidx.compose.foundation.layout.Box
import com.jvillada.movi.ui.components.ds.CifraConRotulo
import com.jvillada.movi.ui.components.ds.EsqueletoDeCifraConRotulo
import com.jvillada.movi.ui.components.ds.renglonTocable
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.rememberFormaComunDeLasCifras
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
 * [filasDelAnalisis] sobre [movimientosDelAnalisis]: los MISMOS movimientos que «En qué se va» (la
 * historia más tu parte de lo que pagaron otros, y solo tu parte de lo que repartiste). Lo que entró
 * es la regla del chip «Ingresos» ([matchesChip], con el mismo [isCashFlow] que el gasto) en pesos,
 * en los mismos días. Todo sobre la historia que la lista ya leyó, más la lectura de tu parte de
 * otros que ya hace «En qué se va» (la misma llamada, con su caché; solo con el panel a la vista).
 * En el teléfono no hay panel, y nada de esto se pinta.
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
    /** Vuelta 76: las categorías fijas, que no dicen «nuevo» (ver `categoriasFijas`). */
    val fijas: Set<String> = emptySet(),
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
 * @param tuParteDeOtros tu parte de los gastos que pagó otro en un espacio (`getTuParteDeOtros`, la
 *   misma lectura de «En qué se va»): suma en lo que salió y en su categoría, solo tu parte.
 */
fun resumenDelPanel(
    dias: List<EventDay>,
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
    hoy: LocalDate,
    tiposDeCuenta: Map<String, AccountType> = emptyMap(),
    tuParteDeOtros: List<FinancialEvent> = emptyList(),
): ResumenDelPanel {
    val eventos = dias.flatMap { it.items }
    // Lo MISMO que analiza «En qué se va»: la historia más tu parte de lo que pagaron otros.
    val analisis = analisisDelPeriodo(movimientosDelAnalisis(dias, tuParteDeOtros), periodo, settings, hoy, tiposDeCuenta)
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
 * # El resumen en el teléfono (vuelta de diseño 30)
 *
 * A 390 px Movimientos iba directo a la lista por día: lo que entró, lo que salió y el neto solo se
 * veían en el panel de la pantalla ancha. Ahora, en el teléfono, una franja compacta debajo del
 * selector del período los dice con [CifraConRotulo] y **el mismo [resumenDelPanel]** del panel:
 * las mismas cifras en los dos anchos. Tocarla abre «En qué se va» de ese período.
 *
 * **Es el primer ítem de la lista y se va con el scroll** (decisión del coordinador), antes de la
 * tarjeta «N por revisar»: fijos quedan solo el encabezado, el selector del período y los chips, la
 * regla de la vuelta de diseño 1 (`LoDeArribaSeVaConElScrollTest`).
 *
 * - Mientras no hay historia o perfil, el esqueleto de las tres (sin «$0»).
 * - Con «Ocultar los saldos», las cifras del sistema se tapan solas.
 * - **Con un filtro que recorta la lista** (una categoría, un comercio o una búsqueda) no se pinta:
 *   el resumen es del período entero, y encima de una lista filtrada se leería como el total de lo
 *   filtrado. Los chips de tipo («Gastos», «Ingresos», «Compartidos») no lo esconden: desde la vuelta
 *   67 la franja **suma lo que muestra la lista con el chip** ([laFranjaDeLaLista]), así que nunca
 *   contradice las filas de abajo.
 */

/**
 * **La franja sigue al chip** (vuelta 67, decisión): Entró · Salió · Neto es la suma de **la lista que
 * tiene debajo** —los días que se ven, con el chip puesto—, con las mismas reglas de la cifra de cada
 * día ([resumenDelDia]). En «Compartidos» decía $4.888.850 de todo el período mientras la lista sumaba
 * $408.050; ahora dice lo que suman sus filas. Con «Todo» es lo mismo que [resumenDelPanel] (lo fija
 * `LaFranjaSigueAlChipTest`). El panel de la pantalla ancha sigue siendo del período entero: no está
 * encima de la lista, y su título dice el período.
 */
fun laFranjaDeLaLista(resumen: ResumenDelPanel, diasALaVista: List<EventDay>, tiposDeCuenta: Map<String, AccountType>): ResumenDelPanel {
    val porDia = diasALaVista.map { resumenDelDia(it.items, tiposDeCuenta) }
    return resumen.copy(entro = porDia.sumOf { it.entro }, salio = porDia.sumOf { it.gastaste })
}

/** El tag de la franja del resumen en el teléfono. */
const val TAG_RESUMEN_EN_EL_TELEFONO: String = "movimientos-resumen-en-el-telefono"

/** ¿Se pinta la franja? Sin panel al lado, y sin un filtro que recorte la lista. Pura. */
fun muestraElResumenEnElTelefono(conPanel: Boolean, hayFiltro: Boolean, busqueda: String): Boolean =
    !conPanel && !hayFiltro && busqueda.isBlank()

/**
 * La franja: «Entró · Salió · Neto», cada uno con su [CifraConRotulo]. Vuelta de diseño 32: las tres
 * en la misma forma —enteras si caben, abreviadas las tres si una no cabe («+$850 mil · $1,8M ·
 * −$902 mil»)—; antes iban en `Compacta`, que solo abrevia del millón para arriba, y a 390 px decía
 * «+$850.000 · $1,8M · −$902.250». [resumen] `null` = todavía no se sabe: el esqueleto de las tres,
 * del mismo alto.
 */
@Composable
internal fun ResumenDelPeriodoEnElTelefono(
    resumen: ResumenDelPanel?,
    onVerEnQueSeVa: () -> Unit,
    /** Su lugar en la lista: el margen y el aire de lo que va arriba de las filas. */
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Medida.objetivoTactil)
            .renglonTocable(onClickLabel = VER_EN_QUE_SE_VA, onClick = onVerEnQueSeVa)
            .testTag(TAG_RESUMEN_EN_EL_TELEFONO),
        contentAlignment = Alignment.CenterStart,
    ) {
        val anchoDeCadaUna = anchoDeCadaCifra(maxWidth)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            if (resumen == null) {
                EsqueletoDeCifraConRotulo(Modifier.weight(1f))
                EsqueletoDeCifraConRotulo(Modifier.weight(1f))
                EsqueletoDeCifraConRotulo(Modifier.weight(1f), alFinal = true)
            } else {
                // Vuelta de diseño 32: las tres en la MISMA forma (ver [formaComunDeLasCifras]).
                val forma = rememberFormaComunDeLasCifras(cifrasDelResumen(resumen), VarianteDeCifra.Fila, anchoDeCadaUna)
                CifraConRotulo("Entró", resumen.entro, Modifier.weight(1f), tono = TonoDeCifra.Ingreso, forma = forma)
                CifraConRotulo("Salió", resumen.salio, Modifier.weight(1f), forma = forma)
                CifraConRotulo(
                    "Neto", resumen.neto, Modifier.weight(1f),
                    tono = tonoDelNeto(resumen.neto), forma = forma, alFinal = true,
                )
            }
        }
    }
}

/**
 * Vuelta 68 · **¿se pinta la franja Entró · Salió · Neto?** Con el resumen, sí; mientras carga, su
 * esqueleto; si la lectura terminó sin resumen (falló), **no**: un esqueleto que pulsa para siempre dice
 * «ya viene» de algo que no va a venir. El aviso de la lista dice qué pasó.
 */
fun laFranjaSePinta(resumen: ResumenDelPanel?, cargando: Boolean): Boolean = resumen != null || cargando

/** Entró, Salió y Neto con su tono, en el orden en que se pintan: lo que mide la forma común. */
fun cifrasDelResumen(resumen: ResumenDelPanel): List<Pair<Long, TonoDeCifra>> = listOf(
    resumen.entro to TonoDeCifra.Ingreso,
    resumen.salio to TonoDeCifra.Neutro,
    resumen.neto to tonoDelNeto(resumen.neto),
)

/** Lo que tiene cada una de [cuantas] cifras lado a lado en [ancho], con [Espacio.s] entre ellas. */
private fun anchoDeCadaCifra(ancho: Dp, cuantas: Int = 3): Dp = (ancho - Espacio.s * (cuantas - 1)) / cuantas

/**
 * La diferencia de una fila contra el período pasado, dicha como en el widget «En qué se va» del
 * Inicio: la MISMA [diferenciaDeLaCategoria] («$120.000 más», «nuevo»…). `null` sin comparación.
 */
fun diferenciaDeLaFila(fila: FilaDelAnalisis, fijas: Set<String> = emptySet()): DiferenciaDeLaCategoria? =
    diferenciaDeLaCategoria(
        CategoriaDelPeriodo(nombre = fila.nombre, gastado = fila.gasto, fraccion = fila.fraccion, anterior = fila.anterior),
        fijas,
    )

/**
 * El tono del neto (entró − salió): en `entra` si quedó plata, **en el color del texto** si salió más
 * de lo que entró, apagado en cero. La misma regla del «Neto del período» de Tus períodos.
 *
 * **Un neto negativo no es una alerta** (vuelta de diseño 28): un período en que salió más de lo que
 * entró puede haberse pagado con lo que ya había; el rojo queda para lo que pide algo.
 */
fun tonoDelNeto(neto: Long): TonoDeCifra = when {
    neto > 0 -> TonoDeCifra.Ingreso
    neto < 0 -> TonoDeCifra.Neutro
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
    /**
     * Vuelta 67 (revisión): con un chip puesto la lista de al lado muestra menos que el período; el
     * panel lo dice («Octubre 2026 · del período»): sus cifras son de todo el período.
     */
    conChip: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Medida.margenDePantalla)
            .padding(top = Medida.dentroDeSeccion, bottom = Medida.entreSecciones)
            .testTag(TAG_RESUMEN_DEL_PERIODO_EN_EL_PANEL),
    ) {
        Seccion(titulo = tituloDelPanel(resumen.periodo, conChip))
        // Vuelta 81: el panel es una columna lateral (el 40 % del ancho): Entró, Salió y Neto van uno
        // debajo del otro, como filas, y cada cifra entera —lado a lado, a 425 dp, se abreviaban—.
        Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_CIFRAS_DEL_RESUMEN), relleno = RellenoDeSuperficie.Lista) {
            FilaDeCifraDelResumen("Entró", resumen.entro, TonoDeCifra.Ingreso)
            Separador()
            FilaDeCifraDelResumen("Salió", resumen.salio, TonoDeCifra.Neutro)
            Separador()
            FilaDeCifraDelResumen("Neto", resumen.neto, tonoDelNeto(resumen.neto))
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
                // Vuelta 81: cada categoría con su barra de 4 dp relativa a la mayor, como el widget.
                val mayor = resumen.categorias.maxOfOrNull { it.gasto } ?: 0L
                resumen.categorias.forEach { fila ->
                    FilaDeCategoriaDelResumen(fila, resumen.fijas, mayor, onClick = { onCategoria(fila) })
                }
            }
        }

        Spacer(Modifier.height(Medida.entreSecciones))
        Text(PISTA_PARA_ELEGIR_UN_MOVIMIENTO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    }
}

/** El tag de las tres cifras del resumen (Entró, Salió, Neto) en el panel. */
const val TAG_CIFRAS_DEL_RESUMEN: String = "movimientos-cifras-del-resumen"

/** El tag de la barra de una categoría del resumen (vuelta 81). */
const val TAG_BARRA_DEL_RESUMEN: String = "movimientos-barra-del-resumen"

/** Un rótulo («Entró») y su cifra, en una fila (vuelta 81: el panel es una columna lateral). */
@Composable
private fun FilaDeCifraDelResumen(rotulo: String, monto: Long, tono: TonoDeCifra) {
    Fila(titulo = rotulo, final = { Cifra(monto, VarianteDeCifra.Fila, tono = tono) })
}

/**
 * Una categoría (vuelta 81, como «En qué se va» del Inicio): su ícono, su nombre y cuántas veces; a la
 * derecha el monto; debajo, una [BarraDeProgreso] de 4 dp relativa a la mayor y la diferencia contra el
 * período pasado. Tocarla filtra la lista de al lado.
 */
@Composable
private fun FilaDeCategoriaDelResumen(fila: FilaDelAnalisis, fijas: Set<String>, mayor: Long, onClick: () -> Unit) {
    val diferencia = diferenciaDeLaFila(fila, fijas)
    Column(modifier = Modifier.testTag(TAG_CATEGORIA_DEL_RESUMEN)) {
        Fila(
            titulo = fila.nombre,
            subtitulo = "${(fila.fraccion * 100).toInt()} % · ${if (fila.veces == 1) "1 movimiento" else "${fila.veces} movimientos"}",
            inicio = { IconoDeCategoria(fila.nombre) },
            final = { Cifra(fila.gasto) },
            onClick = onClick,
            onClickLabel = "Ver los movimientos",
            chevron = false,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Medida.margenDePantalla + Medida.avatar + Espacio.s, end = Medida.margenDePantalla)
                .padding(bottom = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            BarraDeProgreso(
                fraccion = fraccionDeLaBarraDelResumen(fila.gasto, mayor),
                tono = TonoDeBarra.Marca,
                modifier = Modifier.weight(1f).testTag(TAG_BARRA_DEL_RESUMEN),
            )
            if (diferencia != null) {
                Box(
                    modifier = Modifier
                        .testTag(TAG_DIFERENCIA_DEL_RESUMEN)
                        .semantics { contentDescription = diferencia.descripcion },
                ) {
                    Pildora(texto = diferencia.texto, tono = if (diferencia.tono == TonoDeCifra.Alerta) TonoDePildora.Peligro else TonoDePildora.Neutra)
                }
            }
        }
    }
}

/** Cuánto de la barra llena una categoría del resumen: relativa a la mayor (la mayor la llena). Pura. */
fun fraccionDeLaBarraDelResumen(gasto: Long, mayor: Long): Float =
    if (mayor <= 0L) 0f else (gasto.toFloat() / mayor).coerceIn(0f, 1f)

/** El título del panel: «Octubre 2026», o «Octubre 2026 · del período» con un chip puesto. Pura. */
fun tituloDelPanel(periodo: PeriodoFinanciero, conChip: Boolean): String =
    if (conChip) "${tituloDelPeriodo(periodo)} · del período" else tituloDelPeriodo(periodo)
