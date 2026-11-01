package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import com.jvillada.movi.ui.components.ds.TAG_WIDGET
import com.jvillada.movi.ui.components.ds.TonoDeBarra
import com.jvillada.movi.ui.components.ds.BarraDeProgreso
import com.jvillada.movi.ui.components.ds.Fila
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.key
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.dashboard.CuentaDelCarrusel
import com.jvillada.movi.ui.dashboard.notaDeLoApartado
import com.jvillada.movi.ui.dashboard.tarjetasPorRenglon
import androidx.compose.foundation.layout.Column
import com.jvillada.movi.ui.components.LocalAtajosDeAgregar
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import com.jvillada.movi.ui.quickadd.pantallaDelAtajo
import com.jvillada.movi.ui.dashboard.DestinoDeLaAccion
import com.jvillada.movi.ui.components.ds.RenglonesDelRotulo
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Receipt
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jvillada.movi.ui.dashboard.aceptarElCambioDeCategoria
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.elHeroePideLaCuenta
import com.jvillada.movi.ui.dashboard.LocalCargandoElInicio
import com.jvillada.movi.ui.dashboard.PropuestaDelInicio
import com.jvillada.movi.ui.dashboard.TOPE_DE_PROPUESTAS
import com.jvillada.movi.ui.dashboard.TipoDePropuesta
import com.jvillada.movi.ui.dashboard.accionesDelInicio
import com.jvillada.movi.ui.dashboard.avanceDelChecklist
import com.jvillada.movi.ui.dashboard.categoriasDelPeriodo
import com.jvillada.movi.ui.dashboard.checklistDelPeriodoDe
import com.jvillada.movi.ui.dashboard.cuentasDelCarrusel
import com.jvillada.movi.ui.dashboard.CONTRA_EL_PERIODO_PASADO
import com.jvillada.movi.ui.dashboard.DiferenciaDeLaCategoria
import com.jvillada.movi.ui.dashboard.diferenciaDeLaCategoria
import com.jvillada.movi.ui.dashboard.cuantasCosasProponen
import com.jvillada.movi.ui.components.ds.colorDeLaCifra
import com.jvillada.movi.ui.components.ds.estiloDeLaCifra
import com.jvillada.movi.ui.dashboard.dueLabel
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.pendientesPorRevisar
import com.jvillada.movi.ui.dashboard.propuestasALaVista
import com.jvillada.movi.ui.dashboard.propuestasDelInicio
import com.jvillada.movi.ui.dashboard.descripcionDeLaInsignia
import com.jvillada.movi.ui.dashboard.filasDelWidgetDeFalta
import com.jvillada.movi.ui.dashboard.yNMas
import com.jvillada.movi.shared.model.cifraDelPago
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.EsqueletoDeFila
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.dashboard.FILAS_DE_ULTIMOS_MOVIMIENTOS
import com.jvillada.movi.ui.dashboard.filasDeUltimosMovimientos
import com.jvillada.movi.ui.dashboard.proximoPagoDelInicio
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import com.jvillada.movi.ui.transactions.MovementRow
import com.jvillada.movi.ui.transactions.MovementSingleRow
import com.jvillada.movi.ui.transactions.TransferRow

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

/** El esqueleto de una propuesta, mientras las lecturas no llegan. */
const val TAG_ESQUELETO_DE_PROPUESTA: String = "inicio-esqueleto-de-propuesta"

/** El carrusel «Tus cuentas». */
const val TAG_CARRUSEL_DE_CUENTAS: String = "inicio-carrusel-de-cuentas"

/** La grilla «Tus cuentas» de pantalla ancha (vuelta de diseño 9). */
const val TAG_GRILLA_DE_CUENTAS: String = "inicio-grilla-de-cuentas"

/** El esqueleto del carrusel. */
const val TAG_ESQUELETO_DEL_CARRUSEL: String = "inicio-esqueleto-del-carrusel"

/** El widget «Falta por pagar». */
const val TAG_WIDGET_FALTA_POR_PAGAR: String = "inicio-widget-falta-por-pagar"

/** El renglón «y N más» (o «Está arriba…») del widget «Falta por pagar». */
const val TAG_RESTO_DEL_WIDGET_FALTA_POR_PAGAR: String = "inicio-widget-falta-por-pagar-resto"


// ── La cuadrícula de acciones ────────────────────────────────────────────────

/** El ícono de cada acción de la cuadrícula: los de la maqueta de la opción 3. */
private fun iconoDe(accion: AccionDelInicio): ImageVector = when (accion) {
    AccionDelInicio.ANOTAR_GASTO -> Icons.Rounded.NorthEast
    AccionDelInicio.ANOTAR_INGRESO -> Icons.Rounded.SouthWest
    AccionDelInicio.TRASPASAR -> Icons.Rounded.SwapHoriz
    AccionDelInicio.PAGAR_TARJETA -> Icons.Rounded.CreditCard
    AccionDelInicio.REPARTIR -> Icons.Rounded.Group
    AccionDelInicio.SUBIR_COMPROBANTE -> Icons.Rounded.Receipt
    AccionDelInicio.REVISAR_AVISOS -> Icons.Rounded.Inbox
    AccionDelInicio.PREGUNTAR_A_MOVI -> Icons.Rounded.AutoAwesome
}

/**
 * **Hace [accion]**: una pantalla se abre; un atajo de anotar lo hace la cáscara ([hacerElAtajo], por
 * `LocalAtajosDeAgregar`), igual que desde el menú del «+». Fuera de la app (una prueba que monta el
 * Inicio solo) los atajos que son una pantalla igual navegan; «Repartir» y «Subir comprobante», que
 * son hojas de la cáscara, no hacen nada.
 */
internal fun hacerLaAccion(
    accion: AccionDelInicio,
    onNavigate: (Screen) -> Unit,
    hacerElAtajo: ((AtajoDeAgregar) -> Unit)?,
) {
    when (val destino = accion.destino) {
        is DestinoDeLaAccion.Pantalla -> onNavigate(destino.pantalla)
        is DestinoDeLaAccion.Atajo ->
            if (hacerElAtajo != null) hacerElAtajo(destino.atajo) else pantallaDelAtajo(destino.atajo)?.let(onNavigate)
    }
}

/**
 * **La cuadrícula de acciones** (camino 3): **ocho, en dos filas de cuatro** (vuelta 39, la opción 3).
 * «Anotar gasto» es la destacada —lo que más se hace, y el único amarillo del Inicio (vuelta 79)— y
 * «Revisar avisos» lleva la insignia de lo que espera en Por revisar. Cada una abre lo que ya existe
 * (ver [hacerLaAccion]). Vuelta 79: los rótulos van en **un renglón** y son cortos («Comprobante»,
 * «Preguntar»); a pantalla ancha, dentro de una [Superficie].
 */
@Composable
internal fun AccionesDelInicioSection(
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
) {
    val insignia = pendientesPorRevisar(data)
    val atajos = LocalAtajosDeAgregar.current
    val cuadricula: @Composable (Modifier) -> Unit = { modificador ->
    CuadriculaDeAcciones(
        acciones = accionesDelInicio().map { accion ->
            Accion(
                rotulo = accion.rotulo,
                icono = iconoDe(accion),
                onClick = { hacerLaAccion(accion, onNavigate, atajos) },
                // Vuelta 79: sin cuentas, la acción amarilla es «Crear mi primera cuenta» del héroe.
                destacada = accion == AccionDelInicio.ANOTAR_GASTO && !elHeroePideLaCuenta(data),
                insignia = if (accion == AccionDelInicio.REVISAR_AVISOS) insignia else null,
                // Vuelta de diseño 22: la insignia dice de qué es su número («2 avisos · 1 movimiento»).
                descripcion = if (accion == AccionDelInicio.REVISAR_AVISOS) descripcionDeLaInsignia(data) else accion.descripcion,
            )
        },
        modifier = modificador,
        // Vuelta 79: un renglón, con rótulos cortos («Comprobante», «Preguntar»): las dos filas son parejas
        // y bajan de ~220 a ~170 dp.
        renglones = RenglonesDelRotulo.Uno,
    )
    }
    // Vuelta 79: a pantalla ancha la cuadrícula flotaba sin contenedor; va dentro de una superficie.
    if (LocalWindowWidthClass.current != WindowWidthClass.Compact) {
        Superficie(
            modifier = Modifier.padding(horizontal = Medida.margenDePantalla).fillMaxWidth().testTag(TAG_SUPERFICIE_DE_LAS_ACCIONES),
        ) {
            cuadricula(Modifier)
        }
    } else {
        cuadricula(Modifier.padding(horizontal = Medida.margenDePantalla))
    }
}

/** La superficie de la cuadrícula del Inicio a pantalla ancha (vuelta 79). */
const val TAG_SUPERFICIE_DE_LAS_ACCIONES: String = "inicio-superficie-de-las-acciones"

// ── Movi propone hoy ─────────────────────────────────────────────────────────

private fun iconoDe(tipo: TipoDePropuesta): ImageVector = when (tipo) {
    TipoDePropuesta.PAGO -> Icons.Rounded.Schedule
    TipoDePropuesta.DEBITO -> Icons.Rounded.AccountBalance
    TipoDePropuesta.CUADRE -> Icons.Rounded.Tune
    TipoDePropuesta.ANOMALIA -> Icons.AutoMirrored.Rounded.TrendingUp
    TipoDePropuesta.CIERRE -> Icons.AutoMirrored.Rounded.EventNote
}

/**
 * **«Movi propone hoy»** (camino 5): una [TarjetaDePropuesta] por cosa que pide decisión, de la más
 * urgente a la menos, hasta [TOPE_DE_PROPUESTAS]. «Ver todo» lleva a Por revisar. Mientras las
 * lecturas cargan va el esqueleto de una tarjeta. Sin nada pendiente y con todo leído
 * (`puedeAfirmarVacio`) la sección no se pinta (vuelta de diseño 11, ver `visibleSections`): antes
 * decía «Nada por decidir hoy», una sección entera para decir que no había nada.
 */
@Composable
internal fun PropuestasDelInicioSection(
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    onDescartarCierre: (String) -> Unit,
    /** Vuelta 39: `false` cuando «Próximo pago» ya lo dice arriba (ver `visibleSections`). */
    conElPago: Boolean = true,
) {
    val todas = propuestasDelInicio(data, conElPago)
    val aLaVista = propuestasALaVista(todas)
    val cargando = LocalCargandoElInicio.current
    val hacer: (AccionDeLaPropuesta) -> Unit = { accion ->
        when (accion) {
            is AccionDeLaPropuesta.Ir -> onNavigate(accion.pantalla)
            is AccionDeLaPropuesta.DescartarCierre -> onDescartarCierre(accion.periodo)
            // «Está bien»: se va en el mismo toque; si el server no lo guarda, vuelve. Vuelta 89: el pedido
            // no va en un alcance de esta sección, que se va con la última tarjeta (ver `descartarYGuardar`).
            is AccionDeLaPropuesta.DescartarAnomalia ->
                AnomaliasDescartadasEnLaSesion.descartarYGuardar(accion.huella) {
                    Repositories.wallets.descartarAnomalia(accion.huella)
                }
            // Vuelta 60: «Sí, cambiar». Se va en el mismo toque; si no se pudo, vuelve.
            is AccionDeLaPropuesta.CambiarLaCategoria ->
                AnomaliasDescartadasEnLaSesion.descartarYGuardar(accion.cambio.huella) {
                    aceptarElCambioDeCategoria(Repositories.wallets, accion.cambio)
                }
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = Medida.margenDePantalla).testTag(TAG_PROPUESTAS_DEL_INICIO),
    ) {
        Seccion(
            titulo = "Movi propone hoy",
            // Vuelta 76: cuenta lo que dicen las tarjetas, con sus «y N más» (ver `cuantasCosasProponen`).
            conteo = cuantasCosasProponen(todas).takeIf { it > 0 },
            accion = "Ver todo",
            onAccion = { onNavigate(Screen.PorRevisar) },
        )
        when {
            aLaVista.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                aLaVista.forEach { propuesta -> TarjetaDelFeed(propuesta, hacer) }
            }
            // Sin nada que decidir y con todo leído, la sección no llega hasta acá (`visibleSections`).
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
        // Vuelta 79 · un solo amarillo por vista: el amarillo del Inicio es «Anotar gasto». El círculo de
        // la propuesta va en el tinte si urge (con el ocre) o en la capa 3, y su acción, secundaria.
        tono = if (propuesta.urgente) TonoDeIcono.Aviso else TonoDeIcono.Neutro,
        varianteDeLaAccion = VarianteDeBoton.Secundario,
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

// ── Próximo pago ─────────────────────────────────────────────────────────────

/** La tarjeta «Próximo pago» del Inicio. */
const val TAG_PROXIMO_PAGO: String = "inicio-proximo-pago"

/** El esqueleto de «Próximo pago», mientras los vencimientos no llegan. */
const val TAG_ESQUELETO_DEL_PROXIMO_PAGO: String = "inicio-esqueleto-del-proximo-pago"

/**
 * **«Próximo pago»** (vuelta 39, la opción 3): el pago pendiente más urgente del período en una
 * tarjeta blanca —«Próximo pago · vence el 22 de octubre», su ícono, su nombre y su cifra (la de
 * siempre: `cifraDelPago`, el mínimo en una tarjeta)— con el botón amarillo «Anotar pago», que hace lo mismo que el de «Movi propone hoy»
 * ([proximoPagoDelInicio], `accionDelPago`). «y N más» lleva a Pagos. Sin pagos pendientes no se pinta
 * (`visibleSections`); mientras los vencimientos no llegan, un esqueleto del mismo alto.
 */
@Composable
internal fun ProximoPagoSection(data: DashboardData, onNavigate: (Screen) -> Unit) {
    val proximo = proximoPagoDelInicio(data)
    if (proximo == null) {
        if (LocalCargandoElInicio.current) {
            BloqueEsqueleto(
                alto = Medida.altoDeFilaDoble + Medida.objetivoTactil,
                modifier = Modifier.padding(horizontal = Medida.margenDePantalla).testTag(TAG_ESQUELETO_DEL_PROXIMO_PAGO),
            )
        }
        return
    }
    val pago = proximo.pago
    // Como en la maqueta: el nombre y la cifra en texto, y el botón a la derecha. Sin ícono ni pastilla
    // (la revisión: a 360 «Arriendo» se partía en «Arrien / do»); el estado lo dice el título.
    Superficie(
        modifier = Modifier.padding(horizontal = Medida.margenDePantalla).fillMaxWidth().testTag(TAG_PROXIMO_PAGO),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                Text(
                    text = proximo.titulo,
                    style = Movi.textos.apoyoMedio,
                    color = if (proximo.urgente) Movi.colores.aviso else Movi.colores.textoMedio,
                )
                // Vuelta 55: hasta dos renglones. Con la letra al 130 % a 360 dp, «Administración del
                // conjunto» al lado de «Anotar pago» no entra en uno y se cortaba.
                Text(
                    text = pago.nombre,
                    style = Movi.textos.titulo,
                    color = Movi.colores.texto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(TAG_NOMBRE_DEL_PROXIMO_PAGO),
                )
                // Una tarjeta sin mínimo conocido no tiene cifra que decir: su «monto» es el saldo.
                cifraDelPago(pago)?.let { Cifra(it, VarianteDeCifra.Fila, moneda = pago.moneda) }
            }
            // Vuelta 79: secundario. El único amarillo del Inicio es «Anotar gasto».
            Boton(proximo.accion, onClick = { onNavigate(proximo.alTocar) }, variante = VarianteDeBoton.Secundario, tamano = TamanoDeBoton.S)
        }
        yNMas(proximo.masPagos)?.let { mas ->
            Boton(
                texto = mas,
                onClick = { onNavigate(Screen.Plan(com.jvillada.movi.ui.plan.SEGMENTO_PAGOS)) },
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
            )
        }
    }
}

/** El nombre del pago de «Próximo pago»: a 360 dp va entero (en un renglón con la letra de siempre). */
const val TAG_NOMBRE_DEL_PROXIMO_PAGO: String = "inicio-nombre-del-proximo-pago"

// ── Últimos movimientos ──────────────────────────────────────────────────────

/** La lista «Últimos movimientos» del Inicio. */
const val TAG_ULTIMOS_MOVIMIENTOS: String = "inicio-ultimos-movimientos"

/** Lo que dice el enlace de «Últimos movimientos» que abre Actividad. */
const val VER_TODA_LA_ACTIVIDAD: String = "Ver toda la actividad"

/**
 * **«Últimos movimientos»** (vuelta 39, la opción 3): las [FILAS_DE_ULTIMOS_MOVIMIENTOS] filas más
 * recientes de Actividad en una lista blanca, con **la misma fila** ([MovementSingleRow], y
 * [TransferRow] para un traspaso): la misma cifra —tu parte primero, «−» en un gasto, «+» verde en un
 * ingreso—, el mismo subtítulo y el mismo ícono. Tocar una abre su detalle ([HojaDelMovimiento], la de
 * Actividad); «Ver toda la actividad» abre Actividad. Mientras no llegaron, tres filas esqueleto.
 */
@Composable
internal fun UltimosMovimientosSection(
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    onCambiado: () -> Unit,
) {
    val eventos = data.ultimosMovimientos
    if (eventos == null && !LocalCargandoElInicio.current) return
    if (eventos != null && eventos.isEmpty()) return
    val cuentas = data.accounts.orEmpty()
    val nombres = remember(cuentas) { cuentas.associate { it.id to it.name } }
    val tipos = remember(cuentas) { cuentas.associate { it.id to it.type } }
    // Vuelta 83: la cuenta habitual no se repite en cada fila (ver `cuentaHabitual`).
    val masUsada = com.jvillada.movi.data.CuentaMasUsadaCache.id
    val habitual = remember(eventos, masUsada) {
        com.jvillada.movi.ui.components.cuentaHabitual(eventos.orEmpty().map { it.accountId }, masUsada)
    }
    var abierto by remember { mutableStateOf<FinancialEvent?>(null) }
    Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla).testTag(TAG_ULTIMOS_MOVIMIENTOS)) {
        Seccion(
            titulo = "Últimos movimientos",
            accion = VER_TODA_LA_ACTIVIDAD,
            onAccion = { onNavigate(Screen.Transactions()) },
        )
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            if (eventos == null) {
                repeat(FILAS_DE_ULTIMOS_MOVIMIENTOS) { i ->
                    EsqueletoDeFila()
                    if (i < FILAS_DE_ULTIMOS_MOVIMIENTOS - 1) Separador()
                }
            } else {
                val filas = filasDeUltimosMovimientos(eventos)
                filas.forEachIndexed { i, fila ->
                    key(fila.key) {
                        when (fila) {
                            is MovementRow.Single -> MovementSingleRow(
                                tx = fila.event,
                                accountNames = nombres,
                                cuentaHabitual = habitual,
                                onClick = { abierto = fila.event },
                            )
                            is MovementRow.Transfer -> TransferRow(
                                row = fila,
                                accountNames = nombres,
                                accountTypes = tipos,
                                onClick = { abierto = fila.out },
                            )
                            is MovementRow.Ajustes -> fila.events.forEach { ajuste ->
                                MovementSingleRow(tx = ajuste, accountNames = nombres, onClick = { abierto = ajuste })
                            }
                        }
                    }
                    if (i < filas.lastIndex) Separador()
                }
            }
        }
    }
    abierto?.let { evento ->
        HojaDelMovimiento(
            event = evento,
            cuentas = cuentas,
            onDismiss = { abierto = null },
            onCambiado = {
                abierto = null
                onCambiado()
            },
        )
    }
}

// ── Tus cuentas ──────────────────────────────────────────────────────────────

/**
 * **«Tus cuentas»**: el carrusel de tarjetas con el color y la marca de cada banco (camino 1), la
 * siguiente asomando. Tocar una abre su detalle; «Ver todo» lleva a Patrimonio. Mientras las cuentas
 * no contestan, una tarjeta esqueleto del mismo alto. Fuera del teléfono (vuelta de diseño 9), una
 * grilla de tarjetas enteras en vez del carrusel ([tarjetasPorRenglon]): el tipo SDUI no cambia, es
 * cómo el cliente pinta la sección.
 */
@Composable
internal fun CuentasDelInicioSection(data: DashboardData, onNavigate: (Screen) -> Unit) {
    // Vuelta 64: con lo apartado de cada cuenta, que la tarjeta dice debajo del saldo.
    val cuentas = cuentasDelCarrusel(data.accounts, data.apartados.orEmpty())
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
        // Vuelta de diseño 9: fuera del teléfono, una grilla (ver [tarjetasPorRenglon]). En el
        // teléfono no se mide nada: el carrusel de siempre, sin un `SubcomposeLayout` de más.
        if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
            CarruselDeCuentas(cuentas, onNavigate)
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val porRenglon = tarjetasPorRenglon(LocalWindowWidthClass.current, maxWidth, cuentas.size)
                if (porRenglon == 0) CarruselDeCuentas(cuentas, onNavigate)
                else GrillaDeCuentas(cuentas, porRenglon, onNavigate)
            }
        }
    }
}

/** La tarjeta de una cuenta del Inicio, en el carrusel o en la grilla. */
@Composable
private fun TarjetaDelInicio(c: CuentaDelCarrusel, onNavigate: (Screen) -> Unit, modifier: Modifier, reservarNota: Boolean) {
    TarjetaDeCuenta(
        nombre = c.cuenta.name,
        tipo = c.tipo,
        rotuloDelSaldo = c.rotuloDelSaldo,
        saldo = c.saldo,
        moneda = c.moneda,
        marca = marcaDelBanco(c.cuenta.name),
        icono = iconoDelTipoDeCuenta(c.cuenta.type).first,
        onClick = { onNavigate(c.destino) },
        modifier = modifier,
        nota = notaDeLoApartado(c),
        reservarNota = reservarNota,
    )
}

/** El carrusel del teléfono: la siguiente tarjeta asoma a la derecha. */
@Composable
private fun CarruselDeCuentas(cuentas: List<CuentaDelCarrusel>, onNavigate: (Screen) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag(TAG_CARRUSEL_DE_CUENTAS),
        contentPadding = PaddingValues(horizontal = Medida.margenDePantalla),
        horizontalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
    ) {
        items(cuentas, key = { it.cuenta.id }) { c ->
            TarjetaDelInicio(
                c, onNavigate,
                // Con una sola cuenta no hay siguiente que asome: la tarjeta va a todo el ancho.
                Modifier.fillParentMaxWidth(if (cuentas.size == 1) 1f else FRACCION_DE_TARJETA_DE_CUENTA),
                reservarNota = cuentas.any { it.apartado > 0L },
            )
        }
    }
}

/**
 * La grilla de pantalla ancha: [porRenglon] tarjetas enteras por renglón, con la misma separación
 * del carrusel (`Medida.dentroDeSeccion`) a lo ancho y a lo alto. Las de un renglón miden lo mismo
 * de alto (un nombre en dos renglones no deja a su vecina más baja), y el último renglón deja sus
 * lugares vacíos en vez de estirar sus tarjetas.
 */
@Composable
private fun GrillaDeCuentas(cuentas: List<CuentaDelCarrusel>, porRenglon: Int, onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla)
            .testTag(TAG_GRILLA_DE_CUENTAS),
        verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
    ) {
        cuentas.chunked(porRenglon).forEach { renglon ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
            ) {
                renglon.forEach { c ->
                    key(c.cuenta.id) {
                        TarjetaDelInicio(c, onNavigate, Modifier.weight(1f).fillMaxHeight(), reservarNota = renglon.any { it.apartado > 0L })
                    }
                }
                repeat(porRenglon - renglon.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Los widgets: falta por pagar y en qué se va ──────────────────────────────

/**
 * **Los widgets del Inicio** —«Falta por pagar» y «En qué se va», los que el dueño elige en «Editar
 * inicio»—, uno debajo del otro y a todo el ancho en el teléfono (vuelta 80), y lado a lado ([ParDeWidgets])
 * en una ventana ancha. Cada uno
 * **resume** y lleva a su casa: Plan y «En qué se va».
 */
@Composable
internal fun WidgetsDelInicio(
    tipos: List<String>,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    /** Vuelta 68: «Próximo pago» está en el Inicio, así que el widget no lo repite (ver [filasDelWidgetDeFalta]). */
    sinElProximoPago: Boolean = false,
) {
    @Composable
    fun Uno(tipo: String, modifier: Modifier) = when (tipo) {
        "CHECKLIST_DEL_PERIODO" -> WidgetFaltaPorPagar(data, onNavigate, modifier, sinElProximoPago = sinElProximoPago)
        else -> WidgetEnQueSeVa(data, onNavigate, modifier)
    }
    Column(modifier = Modifier.padding(horizontal = Medida.margenDePantalla)) {
        // Vuelta 80: sin «Tus widgets» (palabra de desarrollador). Cada widget lleva su propio título.
        // Vuelta 80: en el teléfono, uno debajo del otro y a todo el ancho. «En qué se va» es una lista de filas
        // (nombre a la izquierda, monto a la derecha y su barra): a media pantalla de un teléfono, con la letra
        // agrandada, no entraban. En una ventana ancha siguen lado a lado (las dos columnas del Inicio parejas).
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (tipos.size >= 2 && maxWidth >= ANCHO_PARA_LOS_WIDGETS_LADO_A_LADO) {
                ParDeWidgets(izquierdo = { Uno(tipos[0], it) }, derecho = { Uno(tipos[1], it) })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                    tipos.forEach { Uno(it, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

/**
 * Vuelta 80: desde qué ancho los dos widgets van lado a lado (tres cuartos del mediano, 450 dp). Un teléfono
 * (hasta ~430 dp) los apila; una columna del Inicio ancho, no.
 */
internal val ANCHO_PARA_LOS_WIDGETS_LADO_A_LADO = com.jvillada.movi.ui.components.ANCHO_DEL_MEDIANO * 0.75f

/** Vuelta 68: lo que dice el widget cuando lo único que falta es el pago de «Próximo pago». */
const val EL_DE_PROXIMO_PAGO: String = "Es el de «Próximo pago»"

/** Lo que dice el widget de «Falta por pagar» cuando ya salió todo. */
const val TODO_PAGADO: String = "Ya salió todo lo del período"

/** El `testTag` del nombre de un pago en el widget «Falta por pagar». */
const val TAG_NOMBRE_EN_EL_WIDGET_FALTA_POR_PAGAR: String = "widget-falta:nombre"

/** «Faltan 2 de 3 pagos», «Falta 1 de 3 pagos»: lo que lista el widget es lo pendiente. */
internal fun lineaCortaDeLoQueFalta(pendientes: Int, total: Int): String =
    if (pendientes == 1) "Falta 1 de $total pagos" else "Faltan $pendientes de $total pagos"

/**
 * **«Falta por pagar»** compacto: cuánto falta (`faltaPorPagar`, la misma de Plan), de cuántos pagos es
 * eso y los dos más urgentes de la cola —aunque uno también esté en «Movi propone hoy» (vuelta de
 * diseño 28)—, con «y N más» si quedan sin fila (ver [filasDelWidgetDeFalta]). Lo vencido lleva su
 * marca: «Vencido hace 22 días» en el color de aviso. Lleva a Plan · Pagos del mes.
 */
@Composable
internal fun WidgetFaltaPorPagar(
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    sinElProximoPago: Boolean = false,
) {
    val checklist = checklistDelPeriodoDe(data)
    val total = avanceDelChecklist(checklist).second
    val pendientes = pagosPendientes(checklist)
    val lista = filasDelWidgetDeFalta(data, sinElProximoPago = sinElProximoPago)
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
        lista.filas.forEach { pago ->
            Column {
                // Vuelta 29: el nombre en su renglón y el monto debajo, como en «En qué se va». Al lado
                // del monto, a media pantalla, el nombre se cortaba en «Arrie…» con Figtree (lo vigila
                // `LoQueSeVeEnteroTest`).
                Text(
                    text = pago.nombre,
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(TAG_NOMBRE_EN_EL_WIDGET_FALTA_POR_PAGAR),
                )
                // Una tarjeta no tiene cuota: su monto es el saldo, y no se muestra como un pago.
                val monto = cifraDelPago(pago)
                if (monto != null) Cifra(monto, VarianteDeCifra.Chica, moneda = pago.moneda)
                Text(
                    text = dueLabel(pago.diasParaVencer),
                    style = Movi.textos.apoyo,
                    color = if (pago.diasParaVencer <= 2) Movi.colores.aviso else Movi.colores.textoMedio,
                    maxLines = 1,
                )
            }
        }
        // Las filas más este renglón dan lo que dice «Faltan N»: ninguna lista se recorta callada.
        // Vuelta 68: el de «Próximo pago» no tiene fila acá; si es el único, se dice dónde está.
        val resto = yNMas(lista.masSinFila) ?: lista.elDeArriba?.takeIf { lista.filas.isEmpty() }?.let { EL_DE_PROXIMO_PAGO }
        if (resto != null) {
            Text(
                text = resto,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.testTag(TAG_RESTO_DEL_WIDGET_FALTA_POR_PAGAR),
            )
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
 * Vuelta 80 (revisión 7): se veía sin terminar —rótulo arriba, cifra debajo, sin barras—. Ahora va en
 * una [Superficie] blanca de lista, con su título, y **cada categoría es una [Fila]**: el nombre a la
 * izquierda y el monto a la derecha, y debajo una [BarraDeProgreso] de 4 dp relativa a la mayor
 * ([fraccionDeLaCategoriaEnElWidget]) con la diferencia en una [Pildora] (la regla de «nuevo» de la
 * vuelta 76: lo fijo nunca es nuevo). El monto va neutro siempre (vuelta de diseño 16): el tono va
 * solo en la píldora, en `Peligro` solo si sube mucho.
 */
@Composable
internal fun WidgetEnQueSeVa(data: DashboardData, onNavigate: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val categorias = categoriasDelPeriodo(
        gastoPorCategoria = data.spentByCategory.orEmpty(),
        presupuestos = data.budgets.orEmpty(),
        cuantas = CATEGORIAS_EN_EL_WIDGET,
        gastoDelAnterior = data.gastoDelAnterior,
    ).take(CATEGORIAS_EN_EL_WIDGET)
    // Vuelta 76: lo fijo (las reglas y la cuota) nunca dice «nuevo» (ver `categoriasFijas`).
    val fijas = com.jvillada.movi.ui.dashboard.categoriasFijas(data.upcoming.orEmpty().map { it.rule })
    val diferencias = categorias.map { diferenciaDeLaCategoria(it, fijas) }
    val mayor = categorias.maxOfOrNull { it.gastado } ?: 0L
    Superficie(
        modifier = modifier.testTag(TAG_TARJETA_EN_QUE_SE_VA).testTag(TAG_WIDGET),
        relleno = RellenoDeSuperficie.Lista,
        onClick = { onNavigate(Screen.EnQueSeVa()) },
        onClickLabel = "Ver En qué se va",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Medida.margenDePantalla)
                .padding(top = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            Text(
                text = TITULO_DEL_WIDGET_EN_QUE_SE_VA,
                style = Movi.textos.titulo,
                color = Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            // Contra qué se compara, dicho una vez (cada píldora dice la frase entera al lector).
            if (diferencias.any { it != null }) {
                Text(
                    CONTRA_EL_PERIODO_PASADO,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).testTag(TAG_CONTRA_EL_PERIODO_PASADO),
                )
            }
        }
        // Vuelta 83 · más denso: cada categoría mide ~52 dp —el nombre, la píldora junto al monto, y la
        // barra a `Espacio.xs` debajo del nombre—. Era una [Fila] de 56 con la barra y la píldora en un
        // renglón aparte (~72 dp por categoría, ~275 px tres).
        Spacer(Modifier.height(Espacio.xxs))
        categorias.zip(diferencias).forEach { (categoria, diferencia) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_CATEGORIA_DEL_WIDGET)
                    .padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                ) {
                    Text(
                        text = categoria.nombre,
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (diferencia != null) DiferenciaDelWidget(diferencia)
                    // **Siempre neutro** (vuelta de diseño 16): un gasto no es una alarma.
                    Cifra(categoria.gastado, VarianteDeCifra.Fila, tono = TonoDeCifra.Neutro)
                }
                Spacer(Modifier.height(Espacio.xs))
                BarraDeProgreso(
                    fraccion = fraccionDeLaCategoriaEnElWidget(categoria.gastado, mayor),
                    tono = TonoDeBarra.Marca,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(Espacio.xs))
    }
}

/** El título del widget «En qué se va»: cada widget lleva el suyo (vuelta 80, sin «Tus widgets»). */
const val TITULO_DEL_WIDGET_EN_QUE_SE_VA: String = "En qué se va"

/** El tag de cada categoría del widget «En qué se va» (la fila con su barra). */
const val TAG_CATEGORIA_DEL_WIDGET: String = "inicio-categoria-del-widget"

/** Cuánto de la barra llena una categoría: relativa a la mayor del widget (la mayor llena la barra). Pura. */
internal fun fraccionDeLaCategoriaEnElWidget(gastado: Long, mayor: Long): Float =
    if (mayor <= 0L) 0f else (gastado.toFloat() / mayor).coerceIn(0f, 1f)

/** El tono de la píldora de la diferencia: `Peligro` solo si sube mucho (la cifra en alerta); si no, neutra. Pura. */
internal fun tonoDeLaPildoraDeLaDiferencia(tono: TonoDeCifra): TonoDePildora =
    if (tono == TonoDeCifra.Alerta) TonoDePildora.Peligro else TonoDePildora.Neutra

/** El tag de la línea «Contra el período pasado» del widget. */
const val TAG_CONTRA_EL_PERIODO_PASADO: String = "inicio-contra-el-periodo-pasado"

/** El tag de la diferencia de una categoría en el widget («$120.000 más», «nuevo»). */
const val TAG_DIFERENCIA_DEL_WIDGET: String = "inicio-diferencia-del-widget"

/**
 * «$120.000 más», «$45.000 menos», «igual» o «nuevo», en una [Pildora] (vuelta 80) del tono de
 * [tonoDeLaPildoraDeLaDiferencia]. La frase entera es para el lector de pantalla.
 */
@Composable
private fun DiferenciaDelWidget(diferencia: DiferenciaDeLaCategoria) {
    // El tag en su propio nodo: la Pildora trae el suyo.
    Box(modifier = Modifier.testTag(TAG_DIFERENCIA_DEL_WIDGET).semantics { contentDescription = diferencia.descripcion }) {
        Pildora(texto = diferencia.texto, tono = tonoDeLaPildoraDeLaDiferencia(diferencia.tono))
    }
}
