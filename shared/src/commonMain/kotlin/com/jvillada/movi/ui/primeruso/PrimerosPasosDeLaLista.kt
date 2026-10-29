package com.jvillada.movi.ui.primeruso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.shared.model.CapturaDelPrimerUso
import com.jvillada.movi.shared.model.EstadoDelPrimerPaso
import com.jvillada.movi.shared.model.LoQueYaHay
import com.jvillada.movi.shared.model.PrimerPaso
import com.jvillada.movi.shared.model.TIPOS_DE_CUENTA_DE_DINERO
import com.jvillada.movi.shared.model.contadorDeLosPrimerosPasos
import com.jvillada.movi.shared.model.pasosQueFaltan
import com.jvillada.movi.shared.model.periodoElegidoEn
import com.jvillada.movi.shared.model.primerPasoPendiente
import com.jvillada.movi.shared.model.primerosPasos
import com.jvillada.movi.shared.model.textoDeLosPasosQueFaltan
import com.jvillada.movi.shared.model.tituloDelPrimerPaso
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.sms.rememberSmsCaptureReady

/*
 * # La lista única de primeros pasos, en pantalla (vuelta 54)
 *
 * La lista vive en `:core` (`primerosPasos`). Acá se arma con lo que el Inicio ya leyó
 * ([primerosPasosDe]) y se pinta de dos formas: la fila compacta del Inicio («Te faltan 3 pasos ›»,
 * [FilaDePrimerosPasos]) y la lista entera de Tú → «Primeros pasos» ([ListaDePrimerosPasos]). La guía
 * usa la misma lista para sus pasos y su resumen.
 */

/**
 * **Lo que ya hay, según lo que el Inicio leyó**; `null` mientras no alcanza para afirmarlo: las
 * lecturas de siempre (`puedeAfirmarVacio`) y además el período (sin perfil no se sabe si se eligió).
 * [permisoDelTelefono] es el permiso de SMS de este teléfono (en la web y en iOS no se mira).
 */
fun loQueYaHayEn(data: DashboardData, modo: CapturaDelPrimerUso, permisoDelTelefono: Boolean): LoQueYaHay? {
    if (!data.puedeAfirmarVacio || data.periodoActual == null) return null
    val capturaHecha = when (modo) {
        // El permiso, o algún aviso que ya llegó (de este teléfono o de otro).
        CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO -> permisoDelTelefono || (data.captura?.total ?: 0) > 0
        // Un extracto leído deja movimientos; cualquier movimiento, venga de donde venga, ya cumple.
        CapturaDelPrimerUso.SUBIR_UN_EXTRACTO -> data.hasMovement
        CapturaDelPrimerUso.NINGUNA -> false
    }
    return LoQueYaHay(
        periodoElegido = periodoElegidoEn(data.ajustesDePeriodo),
        cuentasDeDinero = data.accounts.orEmpty().count { it.type in TIPOS_DE_CUENTA_DE_DINERO },
        capturaHecha = capturaHecha,
        // Las reglas de verdad: las cuotas de un crédito y los pagos de tarjeta no son «lo que pagas cada mes».
        fijos = data.upcoming.orEmpty().count {
            !it.rule.id.startsWith(CREDIT_RULE_PREFIX) && !it.rule.id.startsWith(CARD_RULE_PREFIX)
        },
    )
}

/** La lista única con lo que el Inicio leyó; `null` mientras no se puede afirmar. Pura. */
fun primerosPasosDe(data: DashboardData, modo: CapturaDelPrimerUso, permisoDelTelefono: Boolean): List<EstadoDelPrimerPaso>? =
    loQueYaHayEn(data, modo, permisoDelTelefono)?.let { primerosPasos(it, modo) }

/** La lista única en esta plataforma, con el permiso del teléfono al día. */
@Composable
fun rememberPrimerosPasos(data: DashboardData?, modo: CapturaDelPrimerUso = modoDeCapturaDe(isAndroid)): List<EstadoDelPrimerPaso>? {
    val permiso = rememberSmsCaptureReady()
    return data?.let { primerosPasosDe(it, modo, permiso) }
}

/**
 * **«Ahora no» a la fila del Inicio**, recordado como los otros «Ahora no» del dueño
 * ([PropuestasDescartadasStore]: se limpia al cerrar sesión). La lista sigue en Tú → «Primeros pasos».
 */
object PrimerosPasosDescartados {
    private const val CLAVE = "primeros-pasos:fila-del-inicio"
    fun estan(): Boolean = PropuestasDescartadasStore.estaDescartada(CLAVE)
    fun descartar() {
        PropuestasDescartadasStore.marcar(CLAVE)
    }
}

/** El tag de la fila «Te faltan N pasos» del Inicio. */
const val TAG_FILA_DE_PRIMEROS_PASOS: String = "fila-de-primeros-pasos"

/** Lo que dice el botón que descarta la fila. */
const val DESCARTAR_PRIMEROS_PASOS: String = "Ocultar los primeros pasos"

/** El subtítulo de la fila: por dónde sigue. */
fun siguePorElPaso(paso: PrimerPaso, modo: CapturaDelPrimerUso): String = "Sigue con «${tituloDelPrimerPaso(paso, modo)}»"

/**
 * **«Te faltan 3 pasos ›»**: los primeros pasos en el Inicio, en **una sola fila**, debajo de las
 * acciones (vuelta 54: la tarjeta de cinco renglones iba arriba y empujaba «Te quedan» hacia abajo).
 * Tocarla abre la guía en el primer paso que falta; la × la esconde. Con todo hecho no se pinta.
 */
@Composable
fun FilaDePrimerosPasos(
    lista: List<EstadoDelPrimerPaso>,
    modo: CapturaDelPrimerUso,
    onAbrir: (PrimerPaso) -> Unit,
    onDescartar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texto = textoDeLosPasosQueFaltan(pasosQueFaltan(lista)) ?: return
    val siguiente = primerPasoPendiente(lista) ?: return
    Superficie(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla)
            .testTag(TAG_FILA_DE_PRIMEROS_PASOS),
        relleno = RellenoDeSuperficie.Lista,
    ) {
        Fila(
            titulo = texto,
            subtitulo = siguePorElPaso(siguiente, modo),
            inicio = { IconoEnCirculo(Icons.Rounded.Flag) },
            final = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BotonDeIcono(Icons.Rounded.Close, DESCARTAR_PRIMEROS_PASOS, onClick = onDescartar)
                    Chevron()
                }
            },
            onClick = { onAbrir(siguiente) },
            onClickLabel = "Seguir con los primeros pasos",
        )
    }
}

/**
 * **La lista entera** (Tú → «Primeros pasos»): «Primeros pasos · 2 de 4» y un renglón por paso, con su
 * tilde o su círculo vacío. Tocar un paso abre la guía en ese paso.
 */
@Composable
fun ListaDePrimerosPasos(
    lista: List<EstadoDelPrimerPaso>,
    modo: CapturaDelPrimerUso,
    onAbrir: (PrimerPaso) -> Unit,
    modifier: Modifier = Modifier,
) {
    Superficie(
        modifier = modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla),
        relleno = RellenoDeSuperficie.Lista,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla, vertical = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Text("Primeros pasos", style = Movi.textos.titulo, color = Movi.colores.texto, modifier = Modifier.weight(1f))
            Text(contadorDeLosPrimerosPasos(lista), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
        lista.forEachIndexed { i, estado ->
            if (i > 0) Separador()
            Fila(
                titulo = tituloDelPrimerPaso(estado.paso, modo),
                inicio = {
                    Icon(
                        imageVector = if (estado.hecho) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (estado.hecho) "Hecho" else "Pendiente",
                        tint = if (estado.hecho) Movi.colores.entra else Movi.colores.textoApagado,
                        modifier = Modifier.size(Icono.m),
                    )
                },
                final = { Chevron() },
                onClick = { onAbrir(estado.paso) },
            )
        }
    }
}
