package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.tuParteDe
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.ConfirmarEnHoja
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.ds.AvatarDePersona
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.PieDeHoja
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * # Tocar un gasto del espacio (vuelta 31)
 *
 * Las filas de «Gastos» de un espacio no respondían al tocarlas. Ahora cada una abre algo:
 * - **Un gasto con movimiento propio** (el dueño lo pagó y lo repartió) abre **el mismo detalle de
 *   Movimientos** ([HojaDelMovimiento]): ahí se corrige, se anula y se saca del espacio («Repartir»).
 * - **Uno sin movimiento** («Pagó Caro», anotado con «Pagó otro») no tiene ese detalle: abre una hoja
 *   con quién pagó, cuánto, tu parte, la fecha y la categoría, y «Quitar del espacio», que usa la
 *   operación que el server ya tenía (`DELETE /api/espacios/{id}/gastos/{gastoId}`).
 *
 * Si el movimiento no aparece (se anuló, o la lectura falló) se abre la hoja del gasto: nunca un toque
 * que no hace nada.
 */

/** El tag de la hoja de un gasto del espacio sin movimiento propio. */
const val TAG_DETALLE_DEL_GASTO: String = "espacios:detalle-del-gasto"

/** El botón que saca un gasto del espacio, en su hoja. */
const val QUITAR_DEL_ESPACIO: String = "Quitar del espacio"

/**
 * Lo que se abre al tocar [gasto]: el detalle de su movimiento si lo tiene, o [HojaDelGasto]. [onCambio]
 * relee el espacio (después de corregir el movimiento o de quitar el gasto).
 */
@Composable
internal fun AlTocarUnGasto(espacio: Espacio, gasto: GastoDelEspacio, onCerrar: () -> Unit, onCambio: () -> Unit) {
    val eventoId = gasto.eventoId
    if (eventoId == null) {
        HojaDelGasto(espacio, gasto, onCerrar, onCambio)
        return
    }
    // La misma lectura que Movimientos: viniendo de ahí ya está recordada y la hoja abre en el acto.
    val eventos = rememberLectura(ClaveDeLectura.EventosPorDia, reintento = 0) { Repositories.wallets.getEventsByDay() }
    val cuentas = rememberLectura(ClaveDeLectura.Cuentas, reintento = 0) { Repositories.wallets.getAccounts() }
    val evento = eventos.valor?.firstNotNullOfOrNull { dia -> dia.items.firstOrNull { it.id == eventoId } }
    when {
        evento != null -> HojaDelMovimiento(
            event = evento,
            cuentas = cuentas.valor.orEmpty(),
            onDismiss = onCerrar,
            onCambiado = { onCerrar(); onCambio() },
        )
        eventos.terminada && !eventos.actualizando -> HojaDelGasto(espacio, gasto, onCerrar, onCambio)
        else -> Unit
    }
}

/**
 * **Un gasto del espacio, en su hoja**: quién pagó y cuánto, tu parte, la categoría y la fecha, y
 * «Quitar del espacio» (con su pregunta antes: deja de contar en el saldo).
 */
@Composable
internal fun HojaDelGasto(espacio: Espacio, gasto: GastoDelEspacio, onCerrar: () -> Unit, onCambio: () -> Unit) {
    val coroutine = rememberCoroutineScope()
    var preguntando by remember(gasto.id) { mutableStateOf(false) }
    var quitando by remember(gasto.id) { mutableStateOf(false) }
    var error by remember(gasto.id) { mutableStateOf<String?>(null) }
    val pagoOtro = gasto.pagadoPor != MIEMBRO_YO
    val quien = nombreEnElEspacio(espacio, gasto.pagadoPor)

    fun quitar() {
        if (quitando) return
        quitando = true
        error = null
        coroutine.launch {
            try {
                val eventoId = gasto.eventoId
                if (eventoId != null) {
                    Repositories.wallets.quitarDelEspacio(espacio.id, eventoId)
                } else {
                    Repositories.wallets.quitarGastoDelEspacio(espacio.id, gasto.id)
                }
                onCerrar()
                onCambio()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = mensajeDelEspacio(e)
                preguntando = false
            } finally {
                quitando = false
            }
        }
    }

    if (preguntando) {
        ConfirmarEnHoja(
            pregunta = "¿Quitar «${tituloDelGasto(gasto)}» de ${espacio.nombre}?",
            detalle = if (gasto.eventoId != null) {
                "Deja de contar en lo que se deben, y el movimiento vuelve a contar entero en tus gastos."
            } else {
                "Deja de contar en lo que se deben y tu parte sale de tus gastos."
            },
            textoConfirmar = "Quitar",
            ocupado = quitando,
            onConfirmar = ::quitar,
            onCancelar = { if (!quitando) preguntando = false },
        )
        return
    }

    MarcoDeHoja(onDismiss = onCerrar) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false).testTag(TAG_DETALLE_DEL_GASTO),
        ) {
            EncabezadoDeHoja(tituloDelGasto(gasto), subtitulo = espacio.nombre)
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Cifra(gasto.monto, VarianteDeCifra.Grande)
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            Superficie(modifier = Modifier.fillMaxWidth(), nivel = NivelDeSuperficie.Capa2, relleno = RellenoDeSuperficie.Lista) {
                Fila(
                    titulo = if (pagoOtro) "Pagó $quien" else "Pagaste tú",
                    subtitulo = if (pagoOtro) "No salió de tus cuentas" else null,
                    inicio = { AvatarDePersona(quien, tono = if (pagoOtro) TonoDeIcono.Info else TonoDeIcono.Marca) },
                )
                Separador()
                Fila(
                    titulo = "Tu parte",
                    subtitulo = rotuloDeTuParte(espacio),
                    inicio = { IconoEnCirculo(Icons.Rounded.PieChart, tono = TonoDeIcono.Neutro) },
                    final = { Cifra(tuParteDe(gasto)) },
                )
                if (gasto.categoria.isNotBlank()) {
                    Separador()
                    Fila(
                        titulo = gasto.categoria,
                        subtitulo = "Categoría",
                        inicio = { IconoDeCategoria(gasto.categoria) },
                    )
                }
                if (gasto.fecha > 0L) {
                    val hoy = hoyEnAppZone()
                    Separador()
                    Fila(
                        titulo = fechaEnPalabras(fechaDeEpoch(gasto.fecha), hoy),
                        subtitulo = "Fecha",
                        inicio = { IconoEnCirculo(Icons.Rounded.Event, tono = TonoDeIcono.Neutro) },
                    )
                }
            }
            Spacer(Modifier.height(Medida.entreSecciones))
            PieDeHoja(
                QUITAR_DEL_ESPACIO,
                onClick = { preguntando = true },
                variante = VarianteDeBoton.Peligro,
                error = error,
            )
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}
