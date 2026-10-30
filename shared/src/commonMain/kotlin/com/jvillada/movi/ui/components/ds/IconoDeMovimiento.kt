package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.Marca
import com.jvillada.movi.shared.model.RubroDeMarca
import com.jvillada.movi.shared.model.marcaDelTexto
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.components.CirculoDeMarca
import com.jvillada.movi.ui.components.TamanoDeAvatar

/**
 * **El ícono que le toca a un movimiento** (vuelta 80), ya resuelto. Pura: lo deciden
 * [iconoDelMovimiento] y nada más.
 */
sealed interface IconoResuelto {
    /** La clave estable con que una prueba compara dos lugares: «marca:uber», «categoria:Mercado». */
    val clave: String

    /** El logotipo de un comercio conocido (Uber, D1, Juan Valdez). */
    data class DeMarca(val marca: Marca) : IconoResuelto {
        override val clave: String get() = "marca:${marca.id}"
    }

    /** El círculo de la categoría, con su ícono y su color. */
    data class DeCategoria(val categoria: String) : IconoResuelto {
        override val clave: String get() = "categoria:$categoria"
    }
}

/**
 * **La marca del comercio de un movimiento**, si Movi la conoce: primero por el [comercio] y, si no
 * dice ninguna, por la [descripcion]. **Un banco no es un comercio** (vuelta 80): «Cuota de Vehículo
 * 4083» con el `merchant` «Banco de Occidente» llevaba la «O» azul en el detalle de la cuenta y el
 * ícono lila de su categoría en el Inicio. El banco ya lo dice la cuenta; la fila lleva el comercio o
 * la categoría. Pura.
 */
fun marcaDelComercio(comercio: String?, descripcion: String): Marca? =
    listOfNotNull(comercio?.takeIf { it.isNotBlank() }, descripcion.takeIf { it.isNotBlank() })
        .firstNotNullOfOrNull { texto -> marcaDelTexto(texto)?.takeIf { it.rubro != RubroDeMarca.BANCOS_Y_BILLETERAS } }

/**
 * **La única prioridad del ícono de un movimiento** (vuelta 80): la marca del comercio si Movi la
 * conoce ([marcaDelComercio]); si no, la categoría. Pura.
 */
fun iconoDelMovimiento(comercio: String?, descripcion: String, categoria: String): IconoResuelto =
    marcaDelComercio(comercio, descripcion)?.let { IconoResuelto.DeMarca(it) } ?: IconoResuelto.DeCategoria(categoria)

/** Lo mismo con el movimiento entero. Pura. */
fun iconoDelMovimiento(evento: FinancialEvent): IconoResuelto =
    iconoDelMovimiento(evento.merchant, evento.description, evento.category)

/** El `testTag` de [IconoDeMovimiento]. */
const val TAG_ICONO_DE_MOVIMIENTO: String = "ds:icono-de-movimiento"

/** La [IconoResuelto.clave] de un [IconoDeMovimiento], en su semántica. */
val ClaveDelIconoKey: SemanticsPropertyKey<String> = SemanticsPropertyKey("ClaveDelIcono")

/** Ver [ClaveDelIconoKey]. */
var SemanticsPropertyReceiver.claveDelIcono: String by ClaveDelIconoKey

/**
 * **Un solo ícono por movimiento, en toda la app** (vuelta 80).
 *
 * La revisión 7 contó tres sistemas en las listas: logos de marca, círculos de categoría y letras, y
 * un mismo movimiento cambiaba de cara según la pantalla. Ahora lo pintan **todos** los lugares que
 * muestran un movimiento —Inicio, Actividad, el detalle de una cuenta, Buscar, Por revisar, la hoja
 * del movimiento, los grupos, los anulados y el detalle de un período— y siempre con la misma
 * prioridad ([iconoDelMovimiento]).
 *
 * Mide lo de [tamano]: [TamanoDeAvatar.Fila] en una lista, [TamanoDeAvatar.Encabezado] arriba de un
 * detalle, [TamanoDeAvatar.Linea] junto a un nombre.
 */
@Composable
fun IconoDeMovimiento(
    icono: IconoResuelto,
    modifier: Modifier = Modifier,
    tamano: TamanoDeAvatar = TamanoDeAvatar.Fila,
) {
    Box(
        modifier = modifier
            .testTag(TAG_ICONO_DE_MOVIMIENTO)
            .semantics { claveDelIcono = icono.clave },
        contentAlignment = Alignment.Center,
    ) {
        when (icono) {
            is IconoResuelto.DeMarca -> CirculoDeMarca(icono.marca, tamano = tamano)
            is IconoResuelto.DeCategoria -> IconoDeCategoria(
                icono.categoria,
                tamano = if (tamano == TamanoDeAvatar.Linea) TamanoDeIconoDeCategoria.Chico else TamanoDeIconoDeCategoria.Normal,
            )
        }
    }
}

/** El ícono de un movimiento entero. */
@Composable
fun IconoDeMovimiento(
    evento: FinancialEvent,
    modifier: Modifier = Modifier,
    tamano: TamanoDeAvatar = TamanoDeAvatar.Fila,
) {
    val icono = remember(evento.merchant, evento.description, evento.category) { iconoDelMovimiento(evento) }
    IconoDeMovimiento(icono, modifier, tamano)
}

/** El ícono de lo que no es un `FinancialEvent` pero se pinta como uno: un gasto de un grupo, un aviso leído. */
@Composable
fun IconoDeMovimiento(
    comercio: String?,
    descripcion: String,
    categoria: String,
    modifier: Modifier = Modifier,
    tamano: TamanoDeAvatar = TamanoDeAvatar.Fila,
) {
    val icono = remember(comercio, descripcion, categoria) { iconoDelMovimiento(comercio, descripcion, categoria) }
    IconoDeMovimiento(icono, modifier, tamano)
}
