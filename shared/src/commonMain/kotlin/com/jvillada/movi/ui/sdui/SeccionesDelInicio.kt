package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.marcaDelBanco
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.BloqueEsqueleto
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.conCifrasOcultas
import com.jvillada.movi.ui.components.ds.Accion
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.CuadriculaDeAcciones
import com.jvillada.movi.ui.components.ds.FRACCION_DE_TARJETA_DE_CUENTA
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.ParDeWidgets
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TarjetaDeCuenta
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.Widget
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.components.iconoDelTipoDeCuenta
import com.jvillada.movi.ui.dashboard.AccionDeLaPropuesta
import com.jvillada.movi.ui.dashboard.AccionDelInicio
import com.jvillada.movi.ui.dashboard.AnomaliasDescartadasEnLaSesion
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.LocalCargandoElInicio
import com.jvillada.movi.ui.dashboard.NADA_POR_DECIDIR
import com.jvillada.movi.ui.dashboard.PropuestaDelInicio
import com.jvillada.movi.ui.dashboard.TOPE_DE_PROPUESTAS
import com.jvillada.movi.ui.dashboard.TipoDePropuesta
import com.jvillada.movi.ui.dashboard.accionesDelInicio
import com.jvillada.movi.ui.dashboard.avanceDelChecklist
import com.jvillada.movi.ui.dashboard.categoriasDelPeriodo
import com.jvillada.movi.ui.dashboard.checklistDelPeriodoDe
import com.jvillada.movi.ui.dashboard.cuentasDelCarrusel
import com.jvillada.movi.ui.dashboard.destinoDeLaAccion
import com.jvillada.movi.ui.espacios.HojasDeRepartir
import com.jvillada.movi.ui.espacios.rememberEstadoDeRepartir
import com.jvillada.movi.ui.dashboard.CONTRA_EL_PERIODO_PASADO
import com.jvillada.movi.ui.dashboard.DiferenciaDeLaCategoria
import com.jvillada.movi.ui.dashboard.diferenciaDeLaCategoria
import com.jvillada.movi.ui.components.ds.colorDeLaCifra
import com.jvillada.movi.ui.components.ds.estiloDeLaCifra
import com.jvillada.movi.ui.dashboard.dueLabel
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.pendientesPorRevisar
import com.jvillada.movi.ui.dashboard.propuestasALaVista
import com.jvillada.movi.ui.dashboard.propuestasDelInicio
import kotlinx.coroutines.launch

/**
 * # Las secciones del Inicio del rediseño (ola R1, generación 9)
 *
 * La cuadrícula de acciones, «Movi propone hoy», el carrusel de cuentas y los dos widgets. Todo con los
 * componentes de `ui/components/ds/`; lo que deciden sale de `InicioDelRediseno.kt` (puro y probado).
 * Acá no hay ni un número suelto ni una cifra calculada.
 */

// ── Las etiquetas de las pruebas ─────────────────────────────────────────────

/** La sección «Movi propone hoy». */
const val TAG_PROPUESTAS_DEL_INICIO: String = "inicio-propuestas"

/** La línea tranquila «Nada por decidir hoy». */
const val TAG_NADA_POR_DECIDIR: String = "inicio-nada-por-decidir"

/** El esqueleto de una propuesta, mientras las lecturas no llegan. */
const val TAG_ESQUELETO_DE_PROPUESTA: String = "inicio-esqueleto-de-propuesta"

/** El carrusel «Tus cuentas». */
const val TAG_CARRUSEL_DE_CUENTAS: String = "inicio-carrusel-de-cuentas"

/** El esqueleto del carrusel. */
const val TAG_ESQUELETO_DEL_CARRUSEL: String = "inicio-esqueleto-del-carrusel"

/** El widget «Falta por pagar». */
const val TAG_WIDGET_FALTA_POR_PAGAR: String = "inicio-widget-falta-por-pagar"

/** El título de la fila de los widgets. */
const val TITULO_DE_LOS_WIDGETS: String = "Tus widgets"

// ── La cuadrícula de acciones ────────────────────────────────────────────────

/** El ícono de cada acción de la cuadrícula. */
private fun iconoDe(accion: AccionDelInicio): ImageVector = when (accion) {
    AccionDelInicio.ANOTAR_GASTO -> Icons.Rounded.Edit
    AccionDelInicio.ANOTAR_INGRESO -> Icons.Rounded.ArrowDownward
    AccionDelInicio.TRASPASAR -> Icons.Rounded.SwapHoriz
    AccionDelInicio.PAGAR_TARJETA -> Icons.Rounded.CreditCard
    AccionDelInicio.REPARTIR -> Icons.Rounded.Groups
    AccionDelInicio.SUBIR_COMPROBANTE -> Icons.Rounded.UploadFile
    AccionDelInicio.REVISAR_AVISOS -> Icons.Rounded.Inbox
    AccionDelInicio.PREGUNTAR_A_MOVI -> Icons.Rounded.AutoAwesome
}

/**
 * **La cuadrícula de acciones** (camino 3): cuatro columnas, dos filas. «Anotar gasto» es la destacada
 * —lo que más se hace— y «Revisar avisos» lleva la insignia de lo que espera en Por revisar. Cada una
 * abre lo que ya existe (ver `destinoDeLaAccion`); «Subir comprobante» elige el archivo, y «Repartir»
 * abre la hoja del gasto compartido (`RepartirDesdeElInicio.kt`).
 */
@Composable
internal fun AccionesDelInicioSection(
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    onSubirComprobante: () -> Unit,
) {
    val insignia = pendientesPorRevisar(data)
    // Vuelta de diseño 7: «Repartir» abre directo la hoja de anotar el gasto compartido (o elige el
    // espacio, o lo crea) en vez de solo cambiar de pestaña. Ver `RepartirDesdeElInicio.kt`.
    val repartir = rememberEstadoDeRepartir(onNavigate)
    HojasDeRepartir(repartir)
    CuadriculaDeAcciones(
        acciones = accionesDelInicio().map { accion ->
            Accion(
                rotulo = accion.rotulo,
                icono = iconoDe(accion),
                onClick = {
                    if (accion == AccionDelInicio.REPARTIR) repartir.tocar()
                    else destinoDeLaAccion(accion)?.let(onNavigate) ?: onSubirComprobante()
                },
                destacada = accion == AccionDelInicio.ANOTAR_GASTO,
                insignia = if (accion == AccionDelInicio.REVISAR_AVISOS) insignia else null,
                descripcion = accion.descripcion,
            )
        },
        modifier = Modifier.padding(horizontal = Medida.margenDePantalla),
    )
}

// ── Movi propone hoy ─────────────────────────────────────────────────────────

private fun iconoDe(tipo: TipoDePropuesta): ImageVector = when (tipo) {
    TipoDePropuesta.PAGO -> Icons.Rounded.Schedule
    TipoDePropuesta.DEBITO -> Icons.Rounded.AccountBalance
    TipoDePropuesta.AVISOS -> Icons.Rounded.Inbox
    TipoDePropuesta.CUADRE -> Icons.Rounded.Tune
    TipoDePropuesta.ANOMALIA -> Icons.AutoMirrored.Rounded.TrendingUp
    TipoDePropuesta.CIERRE -> Icons.AutoMirrored.Rounded.EventNote
}

/**
 * **«Movi propone hoy»** (camino 5): una [TarjetaDePropuesta] por cosa que pide decisión, de la más
 * urgente a la menos, hasta [TOPE_DE_PROPUESTAS]. «Ver todo» lleva a Por revisar. Sin nada pendiente,
 * una línea tranquila — y solo si las lecturas alcanzan para afirmarlo (`puedeAfirmarVacio`):
 * mientras cargan va el esqueleto de una tarjeta, nunca un «nada» que todavía no se sabe.
 */
@Composable
internal fun PropuestasDelInicioSection(
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    onDescartarCierre: (String) -> Unit,
) {
    val todas = propuestasDelInicio(data)
    val aLaVista = propuestasALaVista(todas)
    val cargando = LocalCargandoElInicio.current
    val alcance = rememberCoroutineScope()
    val hacer: (AccionDeLaPropuesta) -> Unit = { accion ->
        when (accion) {
            is AccionDeLaPropuesta.Ir -> onNavigate(accion.pantalla)
            is AccionDeLaPropuesta.DescartarCierre -> onDescartarCierre(accion.periodo)
            is AccionDeLaPropuesta.DescartarAnomalia -> {
                // «Está bien»: se va en el mismo toque; si el server no lo guarda, vuelve.
                AnomaliasDescartadasEnLaSesion.descartar(accion.huella)
                alcance.launch {
                    runCatching { Repositories.wallets.descartarAnomalia(accion.huella) }
                        .onFailure { AnomaliasDescartadasEnLaSesion.deshacer(accion.huella) }
                }
            }
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = Medida.margenDePantalla).testTag(TAG_PROPUESTAS_DEL_INICIO),
    ) {
        Seccion(
            titulo = "Movi propone hoy",
            conteo = todas.size.takeIf { it > 0 },
            accion = "Ver todo",
            onAccion = { onNavigate(Screen.PorRevisar) },
        )
        when {
            aLaVista.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                aLaVista.forEach { propuesta -> TarjetaDelFeed(propuesta, hacer) }
            }
            data.puedeAfirmarVacio -> Text(
                text = NADA_POR_DECIDIR,
                style = Movi.textos.cuerpo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.testTag(TAG_NADA_POR_DECIDIR),
            )
            cargando -> EsqueletoDePropuesta()
            else -> Unit
        }
    }
}

@Composable
private fun TarjetaDelFeed(propuesta: PropuestaDelInicio, hacer: (AccionDeLaPropuesta) -> Unit) {
    val secundaria = propuesta.alTocarSecundaria
    TarjetaDePropuesta(
        icono = iconoDe(propuesta.tipo),
        // El título y el cuerpo pueden traer cifras escritas (las anomalías, el cierre): se tapan con
        // «Ocultar los saldos» igual que la [Cifra].
        titulo = conCifrasOcultas(propuesta.titulo),
        cifra = propuesta.cifra,
        moneda = propuesta.moneda,
        cuerpo = propuesta.cuerpo?.let { conCifrasOcultas(it) },
        tono = if (propuesta.urgente) TonoDeIcono.Aviso else TonoDeIcono.Marca,
        accion = propuesta.accion,
        onAccion = { hacer(propuesta.alTocar) },
        accionSecundaria = propuesta.accionSecundaria,
        onAccionSecundaria = secundaria?.let { { hacer(it) } },
    )
}

/** Una propuesta que todavía no llegó: la superficie de la propuesta con dos renglones. */
@Composable
private fun EsqueletoDePropuesta() {
    Superficie(
        modifier = Modifier.fillMaxWidth().testTag(TAG_ESQUELETO_DE_PROPUESTA),
        nivel = NivelDeSuperficie.Propuesta,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            LineaEsqueleto(fraccionDelAncho = 0.7f, estilo = Movi.textos.titulo)
            LineaEsqueleto(fraccionDelAncho = 0.45f, estilo = Movi.textos.cuerpo)
            BloqueEsqueleto(alto = Medida.altoChico, ancho = Medida.avatarGrande * 2)
        }
    }
}

// ── Tus cuentas ──────────────────────────────────────────────────────────────

/**
 * **«Tus cuentas»**: el carrusel de tarjetas con el color y la marca de cada banco (camino 1), la
 * siguiente asomando. Tocar una abre su detalle; «Ver todo» lleva a Patrimonio. Mientras las cuentas
 * no contestan, una tarjeta esqueleto del mismo alto.
 */
@Composable
internal fun CuentasDelInicioSection(data: DashboardData, onNavigate: (Screen) -> Unit) {
    val cuentas = cuentasDelCarrusel(data.accounts)
    val cargando = LocalCargandoElInicio.current
    if (cuentas == null && !cargando) return
    if (cuentas != null && cuentas.isEmpty()) return
    Column {
        Seccion(
            titulo = "Tus cuentas",
            conteo = cuentas?.size,
            accion = "Ver todo",
            onAccion = { onNavigate(Screen.Accounts) },
            modifier = Modifier.padding(horizontal = Medida.margenDePantalla),
        )
        if (cuentas == null) {
            BloqueEsqueleto(
                alto = Medida.altoDeTarjetaDeCuenta,
                modifier = Modifier.padding(horizontal = Medida.margenDePantalla).testTag(TAG_ESQUELETO_DEL_CARRUSEL),
            )
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().testTag(TAG_CARRUSEL_DE_CUENTAS),
            contentPadding = PaddingValues(horizontal = Medida.margenDePantalla),
            horizontalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
        ) {
            items(cuentas, key = { it.cuenta.id }) { c ->
                TarjetaDeCuenta(
                    nombre = c.cuenta.name,
                    tipo = c.tipo,
                    rotuloDelSaldo = c.rotuloDelSaldo,
                    saldo = c.saldo,
                    moneda = c.moneda,
                    marca = marcaDelBanco(c.cuenta.name),
                    icono = iconoDelTipoDeCuenta(c.cuenta.type).first,
                    onClick = { onNavigate(c.destino) },
                    // Con una sola cuenta no hay siguiente que asome: la tarjeta va a todo el ancho.
                    modifier = Modifier.fillParentMaxWidth(if (cuentas.size == 1) 1f else FRACCION_DE_TARJETA_DE_CUENTA),
                )
            }
        }
    }
}

// ── Los widgets: falta por pagar y en qué se va ──────────────────────────────

/**
 * **Los widgets del Inicio** —«Falta por pagar» y «En qué se va», los que el dueño elige en «Editar
 * inicio»—, lado a lado cuando llegan los dos ([ParDeWidgets]) o a todo el ancho si llega uno. Cada uno
 * **resume** y lleva a su casa: Plan y «En qué se va».
 */
@Composable
internal fun WidgetsDelInicio(
    tipos: List<String>,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
) {
    @Composable
    fun Uno(tipo: String, modifier: Modifier) = when (tipo) {
        "CHECKLIST_DEL_PERIODO" -> WidgetFaltaPorPagar(data, onNavigate, modifier)
        else -> WidgetEnQueSeVa(data, onNavigate, modifier)
    }
    Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla)) {
        Seccion(
            titulo = TITULO_DE_LOS_WIDGETS,
            accion = "Editar",
            onAccion = { onNavigate(Screen.EditarInicio) },
        )
        if (tipos.size >= 2) {
            ParDeWidgets(izquierdo = { Uno(tipos[0], it) }, derecho = { Uno(tipos[1], it) })
        } else {
            Uno(tipos.first(), Modifier.fillMaxWidth())
        }
    }
}

/** Lo que dice el widget de «Falta por pagar» cuando ya salió todo. */
const val TODO_PAGADO: String = "Ya salió todo lo del período"

/** «Faltan 2 de 3 pagos», «Falta 1 de 3 pagos»: lo que lista el widget es lo pendiente. */
internal fun lineaCortaDeLoQueFalta(pendientes: Int, total: Int): String =
    if (pendientes == 1) "Falta 1 de $total pagos" else "Faltan $pendientes de $total pagos"

/** Cuántos pagos lista el widget de «Falta por pagar»: los dos que vencen primero. */
private const val PAGOS_EN_EL_WIDGET = 2

/**
 * **«Falta por pagar»** compacto: cuánto falta (`faltaPorPagar`, la misma de Plan), de cuántos pagos es
 * eso y los dos que vencen primero. Lleva a Plan · Pagos del mes.
 */
@Composable
internal fun WidgetFaltaPorPagar(data: DashboardData, onNavigate: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val checklist = checklistDelPeriodoDe(data)
    val total = avanceDelChecklist(checklist).second
    val pendientes = pagosPendientes(checklist).sortedBy { it.diasParaVencer }
    Widget(
        titulo = "Falta por pagar",
        onClick = { onNavigate(Screen.Plan(com.jvillada.movi.ui.plan.SEGMENTO_PAGOS)) },
        modifier = modifier.testTag(TAG_WIDGET_FALTA_POR_PAGAR),
    ) {
        if (pendientes.isEmpty()) {
            Text(TODO_PAGADO, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
            return@Widget
        }
        Cifra(faltaPorPagar(checklist), VarianteDeCifra.Grande)
        // Dice que lo de abajo es lo que FALTA, y de cuántos pagos del período (el reclamo del dueño
        // sobre la tarjeta vieja: «solo muestra los faltantes; debería indicarlo»).
        Text(lineaCortaDeLoQueFalta(pendientes.size, total), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        pendientes.take(PAGOS_EN_EL_WIDGET).forEach { pago ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    Text(
                        text = pago.nombre,
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Una tarjeta no tiene cuota: su monto es el saldo, y no se muestra como un pago.
                    val monto = if (pago.montoEsSaldo) pago.pagoMinimo else pago.monto
                    if (monto != null) Cifra(monto, VarianteDeCifra.Chica, moneda = pago.moneda)
                }
                Text(
                    text = dueLabel(pago.diasParaVencer),
                    style = Movi.textos.apoyo,
                    color = if (pago.diasParaVencer <= 2) Movi.colores.aviso else Movi.colores.textoMedio,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Cuántas categorías lleva el widget de «En qué se va»: las tres que más pesan. Es un resumen de media
 * pantalla; la lista entera (y «Otras N categorías») está en «En qué se va», a un toque.
 */
private const val CATEGORIAS_EN_EL_WIDGET = 3

/**
 * **«En qué se va»** compacto: las categorías que más pesan del período (`categoriasDelPeriodo`, la
 * misma del análisis), cada una con su monto y **cuánto más o menos que el período pasado a la misma
 * altura** (`diferenciaDeLaCategoria`). Lleva a «En qué se va».
 *
 * Vuelta de diseño 7: contesta «¿en qué gasto más que el período pasado?». Antes el rótulo «vs.
 * período pasado» iba a la derecha del título y, a media pantalla, se lo comía entero; cada fila
 * llevaba una flecha sin número y una barra casi vacía que no decía contra qué se medía. Ahora el
 * título se ve, la comparación se dice una vez debajo, y cada categoría va en tres renglones: el
 * ícono y el nombre (lo único que puede cortarse con «…»), el monto con [Cifra] entero, y la
 * diferencia en palabras («$120.000 más», «nuevo»), en `sale` solo si sube mucho.
 */
@Composable
internal fun WidgetEnQueSeVa(data: DashboardData, onNavigate: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val categorias = categoriasDelPeriodo(
        gastoPorCategoria = data.spentByCategory.orEmpty(),
        presupuestos = data.budgets.orEmpty(),
        cuantas = CATEGORIAS_EN_EL_WIDGET,
        gastoDelAnterior = data.gastoDelAnterior,
    ).take(CATEGORIAS_EN_EL_WIDGET)
    val diferencias = categorias.map(::diferenciaDeLaCategoria)
    Widget(
        titulo = "En qué se va",
        onClick = { onNavigate(Screen.EnQueSeVa()) },
        modifier = modifier.testTag(TAG_TARJETA_EN_QUE_SE_VA),
    ) {
        // Contra qué se compara, dicho una vez (cada diferencia dice la frase entera al lector).
        if (diferencias.any { it != null }) {
            Text(
                CONTRA_EL_PERIODO_PASADO,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.testTag(TAG_CONTRA_EL_PERIODO_PASADO),
            )
        }
        categorias.zip(diferencias).forEach { (categoria, diferencia) ->
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                    // El ícono de la categoría, como en Movimientos (ola B): «Comida» se reconoce igual
                    // en toda la app.
                    IconoDeCategoria(categoria.nombre, tamano = TamanoDeIconoDeCategoria.Chico)
                    Text(
                        text = categoria.nombre,
                        style = Movi.textos.apoyo,
                        color = Movi.colores.texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                // En su renglón: entero siempre, aun a media pantalla y con la letra agrandada.
                Cifra(
                    categoria.gastado,
                    VarianteDeCifra.Fila,
                    tono = if (categoria.superada) TonoDeCifra.Alerta else TonoDeCifra.Neutro,
                )
                if (diferencia != null) DiferenciaDelWidget(diferencia)
            }
        }
    }
}

/** El tag de la línea «Contra el período pasado» del widget. */
const val TAG_CONTRA_EL_PERIODO_PASADO: String = "inicio-contra-el-periodo-pasado"

/** El tag de la diferencia de una categoría en el widget («$120.000 más», «nuevo»). */
const val TAG_DIFERENCIA_DEL_WIDGET: String = "inicio-diferencia-del-widget"

/**
 * «$120.000 más», «$45.000 menos», «igual» o «nuevo», con la letra de una cifra chica y el color de su
 * tono (`colorDeLaCifra`). La frase entera es para el lector de pantalla.
 */
@Composable
private fun DiferenciaDelWidget(diferencia: DiferenciaDeLaCategoria) {
    Text(
        text = diferencia.texto,
        style = estiloDeLaCifra(VarianteDeCifra.Chica, Movi.textos),
        color = colorDeLaCifra(diferencia.tono, Movi.colores),
        modifier = Modifier
            .testTag(TAG_DIFERENCIA_DEL_WIDGET)
            .semantics { contentDescription = diferencia.descripcion },
    )
}
