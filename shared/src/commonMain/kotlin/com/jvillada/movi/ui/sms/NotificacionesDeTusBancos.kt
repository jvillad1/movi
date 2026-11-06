package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.platform.hojaDeCompartir
import com.jvillada.movi.shared.model.CodigoDeCaptura
import com.jvillada.movi.shared.model.NOMBRE_DEL_ATAJO_DE_MENSAJES
import com.jvillada.movi.shared.model.NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES
import com.jvillada.movi.shared.model.RUTA_DEL_ATAJO_DE_NOTIFICACIONES
import com.jvillada.movi.shared.model.RUTA_DE_LA_CAPTURA_POR_ATAJO
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton

/*
 * # Vuelta 137 · «Notificaciones de tus bancos», debajo de lo de los mensajes en «Desde tu iPhone»
 *
 * El atajo de mensajes aplica la regla de un SMS (empezar por el banco y traer monto) y casi ninguna notificación
 * de app empieza así: en el iPhone de Caro solo entraban las de Nu. El atajo «Movi - Avisar una notificacion»
 * manda `tipo` = `notificacion` y el server guarda lo que trae un monto. La garantía de privacidad es el
 * disparador: el atajo se ata **solo** a automatizaciones «Notificación» de las apps de banco que el dueño elige
 * (en Android hace ese papel la lista de paquetes).
 *
 * Los pasos de iOS 27 son los de «Mensaje» (verificados el 2-nov en el iPhone de Caro) con «Notificación» y la
 * app; el disparador «Notificación» existe en ese iPhone (Caro tiene uno de Nu desde la vuelta 127), pero el
 * nombre exacto de cada pantalla se confirma con la prueba de `docs/lanzamiento/atajos-de-iphone.md`. En iOS 17
 * a 26 no se afirma: se dice «por verificar», como Apple Pay en iOS 27.
 */

/** El título del bloque. */
const val TITULO_NOTIFICACIONES_DE_TUS_BANCOS: String = "Notificaciones de tus bancos"

/** El tag del bloque entero. */
const val TAG_NOTIFICACIONES_DE_TUS_BANCOS: String = "notificaciones-de-tus-bancos"

/** Los tags de los pasos de cada variante (para las pruebas). */
const val TAG_PASOS_DE_NOTIFICACIONES_IOS_27: String = "pasos-de-notificaciones-ios-27"
const val TAG_PASOS_DE_NOTIFICACIONES_IOS_17_A_26: String = "pasos-de-notificaciones-ios-17-a-26"

const val AGREGAR_EL_ATAJO_DE_NOTIFICACIONES: String = "Agregar el atajo de notificaciones"

const val INTRO_NOTIFICACIONES_DE_TUS_BANCOS: String =
    "Las apps de tus bancos avisan compras, transferencias y la plata que llega. Con un atajo aparte, tu iPhone " +
        "se las pasa a Movi, y Movi guarda las que traen un monto."

/** Lo que dice el paso de agregar: el mismo código del paso 1. */
const val EL_MISMO_CODIGO: String =
    "Al agregarlo, pega el mismo código del paso 1. Si «Agregar atajo» no responde, toca «Omitir configuración»."

/**
 * **La línea de privacidad del bloque**: solo salen las notificaciones de las apps elegidas, y conviene una
 * tarjeta por cada app de banco que se use.
 */
const val SOLO_LAS_APPS_QUE_ELIJAS: String =
    "Solo salen de tu iPhone las notificaciones de las apps que elijas. Conviene una tarjeta por cada app de banco " +
        "que uses (Bancolombia, Nu, Davivienda, Nequi…) y ninguna de otra app: Movi guarda lo que trae un monto."

/** Para quien ya ató Nu al atajo de mensajes (vuelta 127): sigue andando. */
const val SI_YA_ATASTE_NU: String =
    "Si ya tienes la app Nu en el atajo «$NOMBRE_DEL_ATAJO_DE_MENSAJES», sigue funcionando: no hace falta moverla."

/** iOS 17 a 26: sin verificar. No se adelanta un camino que nadie probó. */
const val NOTIFICACIONES_EN_IOS_17_A_26: String =
    "En iOS 17 a 26 todavía no lo verificamos: si en la pestaña «Automatización» aparece «Notificación», elige la " +
        "app de tu banco, «Ejecutar de inmediato» y el atajo «$NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES». Si no aparece, " +
        "tu iPhone todavía no puede avisar las notificaciones: los SMS y los correos del banco sí."

/** Antes de iOS 17 no corre sola ninguna automatización de este tipo: los correos. */
const val NOTIFICACIONES_ANTES_DE_IOS_17: String =
    "Antes de iOS 17 esta automatización no corre sola. Si es tu caso, mejor reenvíale a Movi los correos del banco."

/** El último paso: mirarlo con una notificación de verdad. */
const val PROBAR_CON_UNA_NOTIFICACION_REAL: String =
    "Cuando te llegue una notificación real del banco con un monto, mira que aparezca en «Por revisar»."

/**
 * **Los pasos de la automatización de notificaciones** para cada [variante]: en iOS 27, los mismos de la de
 * «Mensaje» con «Notificación» y la app del banco (una tarjeta por app, unidas por «o», nunca «+ Agregar
 * filtro»); en iOS 17 a 26, una línea «por verificar»; antes de iOS 17, una línea.
 */
fun pasosDeLaAutomatizacionDeNotificaciones(variante: VarianteDeLaAutomatizacion): List<String> = when (variante) {
    VarianteDeLaAutomatizacion.IOS_27_O_MAS -> listOf(
        "En Atajos abre el atajo «$NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES».",
        "Abajo toca «Buscar» y después la pastilla «Automatización».",
        "Elige «Notificación». En la tarjeta que se agrega al atajo, toca la app y elige la de tu banco.",
        "Toca la flecha junto a «que»: deja prendido «Automatización» y apagado «Confirmar antes de ejecutar».",
        "Para cada app de banco más agrega otra tarjeta «Notificación» (quedan unidas por «o»), no «+ Agregar " +
            "filtro»: un filtro se une con «y» y la automatización no correría nunca.",
        PROBAR_CON_UNA_NOTIFICACION_REAL,
    )
    VarianteDeLaAutomatizacion.IOS_17_A_26 -> listOf(NOTIFICACIONES_EN_IOS_17_A_26)
    VarianteDeLaAutomatizacion.ANTES_DE_IOS_17 -> listOf(NOTIFICACIONES_ANTES_DE_IOS_17)
}

/**
 * **«Notificaciones de tus bancos»**: el atajo (descarga el firmado, o la receta a mano si el server no lo tiene)
 * y la automatización. [codigo] es el que leyó «Desde tu iPhone» (`null` mientras lee o si no pudo): de ahí sale si
 * el atajo está firmado. [versionDeIos] elige los pasos, como en la de mensajes.
 */
@Composable
internal fun NotificacionesDeTusBancos(codigo: CodigoDeCaptura?, versionDeIos: Int?, modifier: Modifier = Modifier) {
    val hoja = hojaDeCompartir()
    val uri = LocalUriHandler.current
    var recetaAbierta by remember { mutableStateOf(false) }
    var copiada by remember { mutableStateOf(false) }
    var otraVersionAbierta by remember { mutableStateOf(false) }
    val url = apiBaseUrl.trimEnd('/') + RUTA_DE_LA_CAPTURA_POR_ATAJO

    Column(modifier = modifier.fillMaxWidth().testTag(TAG_NOTIFICACIONES_DE_TUS_BANCOS)) {
        Seccion(TITULO_NOTIFICACIONES_DE_TUS_BANCOS)
        Text(INTRO_NOTIFICACIONES_DE_TUS_BANCOS, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            PasoDelAtajo(1, "Agrega el atajo de notificaciones") {
                Text(abrirLoDescargado(NOMBRE_DEL_ATAJO_DE_NOTIFICACIONES), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(Espacio.xxs))
                Text(EL_MISMO_CODIGO, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Boton(
                    AGREGAR_EL_ATAJO_DE_NOTIFICACIONES,
                    onClick = {
                        if (codigo?.atajoDeNotificaciones == true) {
                            runCatching { uri.openUri(apiBaseUrl.trimEnd('/') + RUTA_DEL_ATAJO_DE_NOTIFICACIONES) }
                        } else {
                            recetaAbierta = !recetaAbierta
                        }
                    },
                    variante = VarianteDeBoton.Secundario,
                    tamano = TamanoDeBoton.S,
                    icono = Icons.Rounded.Download,
                )
                if (recetaAbierta) {
                    Spacer(Modifier.height(Espacio.xs))
                    RecetaDelAtajo(recetaDelAtajo(url, deApplePay = false, deNotificaciones = true))
                    Spacer(Modifier.height(Espacio.xs))
                    Boton(
                        if (copiada) "Copiada" else "Copiar la dirección",
                        onClick = { hoja.copiar(url); copiada = true },
                        variante = VarianteDeBoton.Fantasma,
                        tamano = TamanoDeBoton.S,
                        icono = Icons.Rounded.ContentCopy,
                    )
                }
            }
            Separador()
            PasoDelAtajo(2, "Crea la automatización") {
                val variantes = variantesDeLaGuia(versionDeIos)
                val conRotulo = variantes.aLaVista.size > 1
                variantes.aLaVista.forEach { variante ->
                    Spacer(Modifier.height(Espacio.xs))
                    VarianteDeNotificaciones(variante, conRotulo)
                }
                if (variantes.detras.isNotEmpty()) {
                    Spacer(Modifier.height(Espacio.xs))
                    Boton(
                        TU_IPHONE_MUESTRA_OTRA_COSA,
                        onClick = { otraVersionAbierta = !otraVersionAbierta },
                        variante = VarianteDeBoton.Fantasma,
                        tamano = TamanoDeBoton.S,
                    )
                    if (otraVersionAbierta) {
                        variantes.detras.forEach { variante ->
                            Spacer(Modifier.height(Espacio.xs))
                            VarianteDeNotificaciones(variante, conRotulo = true)
                        }
                    }
                }
                Spacer(Modifier.height(Espacio.xs))
                Text(SOLO_LAS_APPS_QUE_ELIJAS, style = Movi.textos.apoyo, color = Movi.colores.texto)
                Spacer(Modifier.height(Espacio.xs))
                Text(SI_YA_ATASTE_NU, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
        }
    }
}

@Composable
private fun VarianteDeNotificaciones(variante: VarianteDeLaAutomatizacion, conRotulo: Boolean) {
    val tag = when (variante) {
        VarianteDeLaAutomatizacion.IOS_27_O_MAS -> TAG_PASOS_DE_NOTIFICACIONES_IOS_27
        VarianteDeLaAutomatizacion.IOS_17_A_26 -> TAG_PASOS_DE_NOTIFICACIONES_IOS_17_A_26
        VarianteDeLaAutomatizacion.ANTES_DE_IOS_17 -> null
    }
    val pasos = pasosDeLaAutomatizacionDeNotificaciones(variante)
    Column(modifier = if (tag != null) Modifier.testTag(tag) else Modifier) {
        if (conRotulo && variante != VarianteDeLaAutomatizacion.ANTES_DE_IOS_17) {
            Text(
                if (variante == VarianteDeLaAutomatizacion.IOS_27_O_MAS) ROTULO_IOS_27_O_MAS else ROTULO_IOS_17_A_26,
                style = Movi.textos.apoyo,
                color = Movi.colores.texto,
            )
        }
        if (pasos.size == 1) {
            Text(pasos.single(), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        } else {
            RecetaDelAtajo(pasos)
        }
    }
}
