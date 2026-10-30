package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.creditoSinSaldo
import com.jvillada.movi.shared.model.deudasSinSaldoDe
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.formatMoneyCompact

/**
 * # El préstamo sin saldo, una sola pieza (vuelta 80)
 *
 * La revisión 7 contó **cuatro maneras** de hablarle a un préstamo al que Movi no le tiene el saldo:
 * el enlace «Usar este» en Patrimonio, un botón con borde en su ficha, «Sin saldo en Movi» con «cuádrala
 * con el banco» y sin «Usar este» en Créditos, y «1 crédito sin saldo · Cuadrar» en el resumen de
 * Créditos. Ahora las cuatro son [ElPrestamoSinSaldo], con el texto de [loQueSeDiceSinSaldo] y el
 * mismo [Boton] `Secundario` chico.
 */

/** Lo que hace el botón de [ElPrestamoSinSaldo]. */
sealed interface AccionSinSaldo {
    /** Lo que dice el botón. */
    val texto: String

    /** «Usar este»: la hoja de ajustar el saldo de [id], ya llena con [saldo] (la cifra de su plan). */
    data class UsarElDelPlan(val id: String, val saldo: Long) : AccionSinSaldo {
        override val texto: String get() = USAR_EL_SALDO_DEL_PLAN
    }

    /** «Cargarlo»: el ajuste de saldo de siempre de [id], un crédito con condiciones y sin plan completo. */
    data class Cargar(val id: String) : AccionSinSaldo {
        override val texto: String get() = CARGAR_EL_SALDO_DEL_CREDITO
    }

    /** «Cuadrar»: Cuadre de saldos con [id] arriba, o sin ninguna con varias deudas. */
    data class Cuadrar(val id: String?) : AccionSinSaldo {
        override val texto: String get() = CUADRAR_LAS_DEUDAS_SIN_SALDO
    }
}

/**
 * **Lo que se dice de las deudas sin saldo**: el [titulo] en el tono de aviso («Falta el saldo de
 * Vehículo 4083», o «2 créditos sin saldo»), el [detalle] debajo (lo que dice el plan, qué escribir o
 * cómo cuadrarla) y la [accion].
 */
data class LoQueSeDiceSinSaldo(val titulo: String, val detalle: String?, val accion: AccionSinSaldo) {
    /** Si el detalle es lo que dice el plan (la propuesta de la vuelta 77, con su tag). */
    val conPlan: Boolean get() = accion is AccionSinSaldo.UsarElDelPlan
}

/**
 * **Las deudas sin saldo, contadas igual en todas partes** (vuelta 80): las que están en negativo
 * (`deudasSinSaldoDe`) más los créditos con condiciones en $0 sin movimientos (`creditoSinSaldo`).
 * Patrimonio ya las contaba así; el resumen de Créditos se quedaba con las primeras. Pura.
 */
fun deudasSinSaldoParaDecir(creditos: List<CreditSummary>, tarjetas: List<CardSummary>): List<Account> =
    (deudasSinSaldoDe(creditos.map { it.account } + tarjetas.map { it.account }) + creditos.filter(::creditoSinSaldo).map { it.account })
        .distinctBy { it.id }

/**
 * **El texto de [ElPrestamoSinSaldo]**, el mismo en los cuatro lugares. Pura.
 *
 * - **Una deuda**: «Falta el saldo de Vehículo 4083». Si es un crédito con condiciones
 *   ([creditosConCondiciones]) y su plan está completo ([planes]), debajo «Según tu plan debes unos
 *   $35,8M y terminas en enero de 2031 · Usar este»; sin plan, «Escribe lo que debes hoy según el banco
 *   · Cargarlo». Si no, «Movi no tiene su saldo: cuádrala con el banco · Cuadrar».
 * - **Varias**: «2 créditos sin saldo · Cuadrar».
 * - **Ninguna**: `null`.
 */
fun loQueSeDiceSinSaldo(
    deudasSinSaldo: List<Account>,
    creditosConCondiciones: Set<String>,
    planes: Map<String, ElSaldoDelPlanEnLaFicha>,
): LoQueSeDiceSinSaldo? {
    if (deudasSinSaldo.isEmpty()) return null
    val una = deudasSinSaldo.singleOrNull()
        ?: return LoQueSeDiceSinSaldo(
            titulo = textoDeLasDeudasSinSaldo(
                creditos = deudasSinSaldo.count { it.type == AccountType.LOAN },
                tarjetas = deudasSinSaldo.count { it.type != AccountType.LOAN },
            ) ?: return null,
            detalle = null,
            accion = AccionSinSaldo.Cuadrar(null),
        )
    val titulo = textoDelCreditoSinSaldo(una.name)
    val plan = planes[una.id]
    return when {
        una.id in creditosConCondiciones && plan != null ->
            LoQueSeDiceSinSaldo(titulo, textoDelSaldoDelPlanEnLaFicha(plan), AccionSinSaldo.UsarElDelPlan(una.id, plan.saldo))
        una.id in creditosConCondiciones -> LoQueSeDiceSinSaldo(titulo, CUANTO_DEBES_SEGUN_EL_BANCO, AccionSinSaldo.Cargar(una.id))
        else -> LoQueSeDiceSinSaldo(titulo, FALTA_EL_SALDO_DE_LA_DEUDA, AccionSinSaldo.Cuadrar(una.id))
    }
}

/** «Falta el saldo de Vehículo 4083»: el título de una deuda sin saldo, en todas partes. Pura. */
fun textoDelCreditoSinSaldo(nombre: String): String = "Falta el saldo de $nombre"

/** El `testTag` de [ElPrestamoSinSaldo]. */
const val TAG_PRESTAMO_SIN_SALDO: String = "credito:prestamo-sin-saldo"

/** El `testTag` del título de [ElPrestamoSinSaldo]. */
const val TAG_TITULO_SIN_SALDO: String = "credito:prestamo-sin-saldo:titulo"

/**
 * **La pieza del préstamo sin saldo**: el título en el tono de aviso y el detalle a la izquierda, y el
 * botón (`Secundario`, chico) a la derecha, en la columna de la acción de una fila. La usan Patrimonio,
 * la ficha del crédito, su fila en Créditos y el resumen de Créditos.
 *
 * @param estiloDelTitulo la ficha lo pide en `titulo` (va en el lugar de la cifra); los demás, `apoyoMedio`.
 */
@Composable
fun ElPrestamoSinSaldo(
    dice: LoQueSeDiceSinSaldo,
    onAccion: (AccionSinSaldo) -> Unit,
    modifier: Modifier = Modifier,
    estiloDelTitulo: TextStyle = Movi.textos.apoyoMedio,
) {
    // El tag de quien lo monta va afuera; el de la pieza, adentro: los dos se encuentran.
    Column(modifier = modifier.fillMaxWidth()) { Row(
        modifier = Modifier.fillMaxWidth().testTag(TAG_PRESTAMO_SIN_SALDO),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
            Text(dice.titulo, style = estiloDelTitulo, color = Movi.colores.aviso, modifier = Modifier.testTag(TAG_TITULO_SIN_SALDO))
            if (dice.detalle != null) {
                Text(
                    dice.detalle,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    modifier = if (dice.conPlan) Modifier.testTag(TAG_SALDO_SEGUN_EL_PLAN) else Modifier,
                )
            }
        }
        Boton(
            texto = dice.accion.texto,
            onClick = { onAccion(dice.accion) },
            variante = VarianteDeBoton.Secundario,
            tamano = TamanoDeBoton.S,
        )
    }
    }
}

// ── Vuelta 82 · la deuda total, honesta ─────────────────────────────────────────────────────────

/**
 * **Lo que la «Deuda total» no cuenta, en una línea** (vuelta 82): la cifra grande es lo que Movi sabe
 * —un préstamo sin saldo suma cero ([totalDebtCop])—, y debajo se dice lo que falta:
 *
 * - una deuda con su plan completo ([planes]): «+ Vehículo 4083 sin saldo (unos $35,8M según el plan)»;
 * - una sin plan: «+ 1 préstamo sin saldo» (o «+ 1 tarjeta sin saldo»);
 * - varias: «+ 2 préstamos sin saldo», con «(unos $X según el plan)» solo si **todas** tienen plan.
 *
 * **No es una regla de plata**: no suma nada a la cifra de arriba, nombra lo que falta. La acción vive en
 * la fila del préstamo ([ElPrestamoSinSaldo], una sola vez por vista). «Unos» y no «≈»: Figtree no tiene
 * ese glifo. `null` sin deudas sin saldo. Pura.
 */
fun lineaDeLasDeudasSinSaldo(
    deudasSinSaldo: List<Account>,
    planes: Map<String, ElSaldoDelPlanEnLaFicha>,
): String? {
    if (deudasSinSaldo.isEmpty()) return null
    val conPlan = deudasSinSaldo.mapNotNull { d -> planes[d.id]?.saldo?.takeIf { it > 0L } }
    val segunElPlan = if (conPlan.size == deudasSinSaldo.size) " (unos ${formatMoneyCompact(conPlan.sum())} según el plan)" else ""
    val una = deudasSinSaldo.singleOrNull()
    if (una != null && segunElPlan.isNotEmpty()) return "+ ${una.name} sin saldo$segunElPlan"
    val prestamos = deudasSinSaldo.count { it.type == AccountType.LOAN }
    val tarjetas = deudasSinSaldo.size - prestamos
    val cuantas = when {
        tarjetas == 0 -> if (prestamos == 1) "1 préstamo" else "$prestamos préstamos"
        prestamos == 0 -> if (tarjetas == 1) "1 tarjeta" else "$tarjetas tarjetas"
        else -> "${deudasSinSaldo.size} deudas"
    }
    return "+ $cuantas sin saldo$segunElPlan"
}
