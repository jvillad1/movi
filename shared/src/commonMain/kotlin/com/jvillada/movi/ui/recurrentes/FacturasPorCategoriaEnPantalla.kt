package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.GruposPlegadosDeFacturas
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.data.VistaDeFacturas
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.ClaseDeGrupoDeFacturas
import com.jvillada.movi.shared.model.GrupoDeFacturas
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.EncabezadoPlegable
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDelDetalle
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo

/*
 * # «Por categoría»: la segunda vista de las facturas (vuelta 120)
 *
 * Arriba de «Tus facturas del período», «Por fecha | Por categoría». «Por fecha» es la lista de siempre;
 * «Por categoría» reparte LAS MISMAS filas en grupos (`facturasPorCategoria`, `:core`), cada uno con un
 * encabezado plegable que dice su total y cuánto falta. Las filas son las de siempre ([FilaDeFactura] con sus
 * acciones), en el orden de la lista por fecha. Ver `docs/diseno/sistema.md`, «Vuelta 120».
 */

/** Los rótulos del selector. */
const val ROTULO_POR_FECHA: String = "Por fecha"
const val ROTULO_POR_CATEGORIA: String = "Por categoría"

/** El tag del selector «Por fecha | Por categoría» y el de un grupo de la vista por categoría. */
const val TAG_SELECTOR_DE_VISTA_DE_FACTURAS: String = "pagos:vista-de-facturas"
const val TAG_GRUPO_DE_FACTURAS: String = "pagos:grupo-de-facturas"

/** Lo que dice un grupo sin nada por pagar (con el ✓ como ícono al lado: Figtree no dibuja «✓»). */
const val GRUPO_PAGADO: String = "Pagado"

/** Lo que dice un grupo con algo pendiente que no suma en «Falta por pagar» (una tarjeta sin su mínimo). */
const val GRUPO_PENDIENTE_SIN_SUMAR: String = "Pendiente · no suma"

/** **El selector «Por fecha | Por categoría»**: dos píldoras de filtro, la elegida en tinta. */
@Composable
internal fun SelectorDeVistaDeFacturas(vista: VistaDeFacturas, onElegir: (VistaDeFacturas) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(TAG_SELECTOR_DE_VISTA_DE_FACTURAS),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        PildoraDeFiltro(ROTULO_POR_FECHA, seleccionada = vista == VistaDeFacturas.POR_FECHA, onClick = { onElegir(VistaDeFacturas.POR_FECHA) })
        PildoraDeFiltro(ROTULO_POR_CATEGORIA, seleccionada = vista == VistaDeFacturas.POR_CATEGORIA, onClick = { onElegir(VistaDeFacturas.POR_CATEGORIA) })
    }
}

/**
 * **Lo que dice un grupo debajo de su nombre**: «Falta $2.613.714» si falta algo que suma; «Pagado» (con su ✓)
 * si no queda ningún pago por hacer; y si lo que queda no suma (una tarjeta sin su mínimo), «Pendiente · no
 * suma», en aviso, como la fila. Las cifras son las del grupo ([GrupoDeFacturas.falta]). Pura.
 */
fun detalleDelGrupo(grupo: GrupoDeFacturas): Pair<String, TonoDelDetalle> = when {
    grupo.falta > 0L -> "Falta ${formatCOP(grupo.falta)}" to TonoDelDetalle.Medio
    grupo.todoPagado -> GRUPO_PAGADO to TonoDelDetalle.Exito
    else -> GRUPO_PENDIENTE_SIN_SUMAR to TonoDelDetalle.Aviso
}

/** Una cifra para el lector: «2.613.714 pesos»; con los saldos ocultos, «cifra oculta». Pura. */
fun cifraParaElLector(monto: Long): String =
    if (SaldosOcultosStore.ocultos) "cifra oculta" else "${formatCOPSinOcultar(monto).removePrefix("$")} pesos"

/**
 * **La frase del encabezado para el lector, sin el estado**: «Vivienda, falta 2.613.714 pesos de 3.000.000
 * pesos»; «Servicios, pagado, 200.000 pesos»; «Tarjetas, pendiente, no suma en lo que falta». El estado
 * («plegado») lo suma el encabezado. Pura.
 */
fun descripcionDelGrupo(grupo: GrupoDeFacturas): String = when {
    grupo.falta > 0L -> "${grupo.nombre}, falta ${cifraParaElLector(grupo.falta)} de ${cifraParaElLector(grupo.total)}"
    grupo.todoPagado -> "${grupo.nombre}, pagado, ${cifraParaElLector(grupo.total)}"
    else -> "${grupo.nombre}, pendiente, no suma en lo que falta"
}

/** Con qué nombre se elige el ícono de un grupo: la categoría, o la de la cuota y la del pago de tarjeta. */
internal fun nombreParaElIconoDelGrupo(grupo: GrupoDeFacturas): String = when (grupo.clase) {
    ClaseDeGrupoDeFacturas.CREDITOS -> CUOTA_CATEGORY
    ClaseDeGrupoDeFacturas.TARJETAS -> CARD_PAYMENT_CATEGORY
    ClaseDeGrupoDeFacturas.CATEGORIA -> nombreParaElIconoDeLaFactura(grupo.nombre, grupo.filas.firstOrNull()?.nombre ?: grupo.nombre)
}

/** Arranca plegado lo que no pide nada: un grupo sin nada abierto. */
internal fun plegadoPorDefecto(grupo: GrupoDeFacturas): Boolean = !grupo.tieneAlgoPendiente

/**
 * **Las facturas por categoría**: un grupo por superficie, uno debajo del otro (también a pantalla ancha: en
 * la columna de los pagos, 664 dp a 1.280, dos columnas de grupos dejaban la fila de una factura en ~320 y
 * cortaban el nombre al lado de «Anotar pago»). Cada grupo: su [EncabezadoPlegable] y, desplegado, sus filas
 * con [fila] —la misma de la vista por fecha—. [pie] va debajo de las filas de un grupo desplegado (de dónde
 * salen las estimaciones de las cuotas), o nada.
 */
@Composable
internal fun FacturasPorCategoria(
    grupos: List<GrupoDeFacturas>,
    fila: @Composable (PagoDelPeriodo) -> Unit,
    pie: @Composable (GrupoDeFacturas) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
        grupos.forEach { grupo ->
            val porDefecto = plegadoPorDefecto(grupo)
            val plegado = GruposPlegadosDeFacturas.plegado(grupo.clave, porDefecto)
            val (detalle, tono) = detalleDelGrupo(grupo)
            Superficie(modifier = Modifier.fillMaxWidth().testTag(TAG_GRUPO_DE_FACTURAS), relleno = RellenoDeSuperficie.Lista) {
                EncabezadoPlegable(
                    titulo = grupo.nombre,
                    plegado = plegado,
                    onAlternar = { GruposPlegadosDeFacturas.alternar(grupo.clave, porDefecto) },
                    descripcion = descripcionDelGrupo(grupo),
                    detalle = detalle,
                    tonoDelDetalle = tono,
                    iconoDelDetalle = if (tono == TonoDelDetalle.Exito) Icons.Rounded.Check else null,
                    inicio = { IconoDeCategoria(nombreParaElIconoDelGrupo(grupo)) },
                    final = { Cifra(grupo.total) },
                )
                if (!plegado) {
                    grupo.filas.forEach { pago ->
                        Separador(SangriaDeSeparador.ConInicio)
                        fila(pago)
                    }
                    pie(grupo)
                }
            }
        }
    }
}
