package com.jvillada.movi.ui.mas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO
import com.jvillada.movi.ui.components.cabenDosColumnasIguales
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.dashboard.heroBalance
import com.jvillada.movi.ui.espacios.TITULO_DE_PERSONAS
import com.jvillada.movi.ui.espacios.fraseDelSaldo
import kotlinx.coroutines.CancellationException

/** El título de la cuarta pestaña (vuelta 38). Ver [MasScreen]. */
const val TITULO_DE_TU: String = "Tú"

/** El título de la sección de arriba de «Tú»: lo que eran Patrimonio y Espacios, como acceso. */
const val SECCION_LO_TUYO: String = "Lo tuyo"

/** La fila de «Tu patrimonio» en Tú. */
const val FILA_TU_PATRIMONIO: String = "Tu patrimonio"

/** La fila del cuadre de saldos en Tú. */
const val FILA_CUADRE_DE_SALDOS: String = "Cuadre de saldos"

/** Para las pruebas: la sección «Lo tuyo» y las dos columnas de Tú en pantalla ancha. */
const val TAG_LO_TUYO: String = "tu:lo-tuyo"
const val TAG_COLUMNA_DE_LO_TUYO: String = "tu:columna-de-lo-tuyo"
const val TAG_COLUMNA_DE_LOS_AJUSTES: String = "tu:columna-de-los-ajustes"

/**
 * **¿«Tú» va en dos columnas en un panel de [anchoDelPanel]?** (ya sin el rail): lo tuyo a la
 * izquierda y los ajustes a la derecha, con el mismo mínimo por columna que Patrimonio (420 dp; dos
 * columnas desde una ventana de 1.104 en escritorio, ver `PatrimonioEnColumnas.kt`).
 */
fun tuEnDosColumnas(anchoDelPanel: Dp): Boolean =
    cabenDosColumnasIguales(anchoDelPanel, ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO)

/**
 * Lo que dice la fila «Personas» de Tú: el saldo pendiente con los demás, si lo hay.
 *
 * @property subtitulo «Caro te debe», «Le debes a Caro», «2 espacios con saldo pendiente» o, sin
 *   nada pendiente (o sin leer), qué hay adentro.
 * @property monto la cifra de la derecha; `null` cuando no hay una sola cifra que decir.
 */
data class ResumenDePersonas(val subtitulo: String, val monto: Long?)

/** Sin espacios leídos, o todo a mano: qué hay en Personas. */
const val PERSONAS_SIN_SALDO: String = "Gastos compartidos y personas"

/**
 * El [ResumenDePersonas] de [detalles] (los espacios con su saldo; `null` = no se pudo leer o todavía
 * no llegó). Con **un** espacio con saldo, su frase de siempre ([fraseDelSaldo]: «Caro te debe» y la
 * cifra); con varios, cuántos; sin ninguno, [PERSONAS_SIN_SALDO]. Pura, para probarla sin pantalla.
 */
fun resumenDePersonas(detalles: List<DetalleDelEspacio>?): ResumenDePersonas {
    val conSaldo = detalles.orEmpty()
        .map { fraseDelSaldo(it.espacio, it.saldo) }
        .filter { it.monto != 0L }
    return when (conSaldo.size) {
        0 -> ResumenDePersonas(PERSONAS_SIN_SALDO, null)
        1 -> ResumenDePersonas(conSaldo.single().titulo, conSaldo.single().monto)
        else -> ResumenDePersonas("${conSaldo.size} espacios con saldo pendiente", null)
    }
}

/**
 * # «Lo tuyo», arriba de Tú (vuelta 38)
 *
 * Las dos pestañas que se fueron, como acceso y con su cifra:
 * - **Tu patrimonio** con el neto de `patrimonioDe` (la MISMA cuenta que Patrimonio, [heroBalance]):
 *   abre Patrimonio, con sus cuentas, sus deudas, «Entre cuentas» y los bienes;
 * - **Personas** (antes Espacios) con el saldo pendiente si lo hay ([resumenDePersonas]);
 * - **Cuadre de saldos**: tus cuentas contra el banco, con su modo «Tarjetas».
 *
 * Las lecturas son las de siempre: las cuentas y los apartados por el caché de lecturas (lo último que
 * se vio, al primer cuadro), los espacios como los lee Personas. Lo que no llega no se afirma: sin
 * cuentas la fila va sin cifra, sin espacios dice qué hay adentro.
 */
@Composable
internal fun LoTuyo(onNavigate: (Screen) -> Unit) {
    val refresco = LocalRefreshTick.current
    val cuentas = rememberLectura(ClaveDeLectura.Cuentas, reintento = 0) { Repositories.wallets.getAccounts() }.valor
    val apartados = rememberLectura(ClaveDeLectura.Apartados, reintento = 0) { Repositories.wallets.getApartados() }.valor.orEmpty()
    var espacios by remember { mutableStateOf<List<DetalleDelEspacio>?>(null) }
    LaunchedEffect(refresco) {
        espacios = try {
            Repositories.wallets.getEspacios().map { Repositories.wallets.getDetalleDelEspacio(it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }
    val neto = cuentas?.let { heroBalance(it, apartados).patrimonio }
    val personas = resumenDePersonas(espacios)

    Column(modifier = Modifier.testTag(TAG_LO_TUYO)) {
        Seccion(titulo = SECCION_LO_TUYO)
        Superficie(relleno = RellenoDeSuperficie.Lista) {
            Fila(
                titulo = FILA_TU_PATRIMONIO,
                subtitulo = "Lo que tienes menos lo que debes",
                inicio = { IconoEnCirculo(Icons.Rounded.AccountBalance, tono = TonoDeIcono.Neutro) },
                final = { CifraYChevron(neto) },
                onClick = { onNavigate(Screen.Accounts) },
            )
            Separador()
            Fila(
                titulo = TITULO_DE_PERSONAS,
                subtitulo = personas.subtitulo,
                inicio = { IconoEnCirculo(Icons.Rounded.Groups, tono = TonoDeIcono.Neutro) },
                final = { CifraYChevron(personas.monto) },
                onClick = { onNavigate(Screen.Espacios) },
            )
            Separador()
            Fila(
                titulo = FILA_CUADRE_DE_SALDOS,
                subtitulo = "Tus cuentas y tarjetas contra el banco",
                inicio = { IconoEnCirculo(Icons.Rounded.Balance, tono = TonoDeIcono.Neutro) },
                final = { Chevron() },
                onClick = { onNavigate(Screen.CuadreDeSaldos) },
            )
        }
    }
}

/** La cifra de una fila de «Lo tuyo» (si la hay) y la flecha de «esto se abre». */
@Composable
private fun CifraYChevron(monto: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
        if (monto != null) Cifra(monto)
        Chevron()
    }
}
