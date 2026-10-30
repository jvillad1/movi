package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.idsDeMiembros
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.formatCOP

/**
 * # El tercero en sus grupos (vuelta 73)
 *
 * La revisión `r4`: en «Casa con Caro», Caro puso $240.000 y te debe $168.050; en su ficha de
 * «Personas y comercios» decía «Nada este período · Le enviaste Nada · Te envió Nada». Las dos cosas
 * eran ciertas —la ficha cuenta **transferencias**, el grupo cuenta **gastos compartidos**— pero leídas
 * juntas se contradecían.
 *
 * Ahora la ficha de un tercero que es miembro de un grupo dice arriba, por grupo, lo que dice el grupo:
 * «En Casa con Caro: puso $240.000 · te debe $168.050» (o «le debes $X», o «están a mano»). Las cifras
 * salen **tal cual** de lo que el server armó con `saldoDelEspacio` —la función única del saldo— en el
 * [DetalleDelEspacio]: lo que puso es su `puso` del período (lo mismo que «Quién puso qué» del grupo) y
 * lo que se deben es el reembolso entre los dos (lo mismo que la tarjeta del saldo). Nada se calcula de
 * nuevo acá.
 */
data class ElTerceroEnUnGrupo(val espacioId: String, val grupo: String, val detalle: String) {
    /** «En Casa con Caro: puso $240.000 · te debe $168.050», en una línea. */
    val texto: String get() = "En $grupo: $detalle"
}

/**
 * Los grupos de [detalles] donde está [terceroId], cada uno con su línea. Sin grupos, vacía. Pura.
 *
 * Lo que se deben sale de los reembolsos entre el tercero y el dueño (`SaldoDelEspacio.reembolsos`):
 * con dos miembros es exactamente la frase del grupo; con más, es lo que hay entre ustedes dos, y por
 * eso «a mano contigo» y no «a mano» (el tercero puede deberle a otro del grupo).
 */
fun elTerceroEnLosGrupos(terceroId: String, detalles: List<DetalleDelEspacio>): List<ElTerceroEnUnGrupo> =
    detalles.filter { d -> d.espacio.miembros.any { it.id == terceroId } }.map { d ->
        val puso = d.delPeriodo.miembros.firstOrNull { it.miembroId == terceroId }?.puso ?: 0L
        val teDebe = d.saldo.reembolsos.filter { it.de == terceroId && it.a == MIEMBRO_YO }.sumOf { it.monto }
        val leDebes = d.saldo.reembolsos.filter { it.de == MIEMBRO_YO && it.a == terceroId }.sumOf { it.monto }
        val deuda = when {
            teDebe > 0L -> "te debe ${formatCOP(teDebe)}"
            leDebes > 0L -> "le debes ${formatCOP(leDebes)}"
            idsDeMiembros(d.espacio).size > 2 -> "a mano contigo"
            else -> "están a mano"
        }
        val partes = listOfNotNull(puso.takeIf { it > 0L }?.let { "puso ${formatCOP(it)}" }, deuda)
        ElTerceroEnUnGrupo(d.espacio.id, d.espacio.nombre, partes.joinToString(" · "))
    }

/** El tag de una fila «En Casa con Caro: …» en la ficha del tercero. */
const val TAG_TERCERO_EN_UN_GRUPO: String = "ficha-del-tercero:grupo"

/** Lo que dice «Este período» cuando no hubo transferencias pero sí hay un grupo de por medio. */
const val SIN_TRANSFERENCIAS_ESTE_PERIODO: String = "Sin transferencias este período"

/** Las filas de los grupos, arriba en la ficha; tocar una abre el grupo (si [onAbrirGrupo] no es `null`). */
@Composable
internal fun ElTerceroEnSusGrupos(grupos: List<ElTerceroEnUnGrupo>, onAbrirGrupo: ((String) -> Unit)?) {
    if (grupos.isEmpty()) return
    Superficie(relleno = RellenoDeSuperficie.Lista) {
        Column {
            grupos.forEachIndexed { i, g ->
                if (i > 0) Separador()
                // El grupo de título y lo que dice de subtítulo: en una sola línea, a 360 dp con la
                // letra grande, «En Casa con Caro: puso … · te debe …» no cabía en el título.
                Fila(
                    titulo = "En ${g.grupo}",
                    subtitulo = g.detalle.replaceFirstChar { it.uppercase() },
                    lineasDelSubtitulo = 2,
                    inicio = { IconoEnCirculo(Icons.Rounded.Groups, tono = TonoDeIcono.Neutro) },
                    final = if (onAbrirGrupo != null) ({ Chevron() }) else null,
                    onClick = onAbrirGrupo?.let { abrir -> { abrir(g.espacioId) } },
                    onClickLabel = "Abrir el grupo",
                    modifier = Modifier.testTag(TAG_TERCERO_EN_UN_GRUPO),
                )
            }
        }
    }
}
