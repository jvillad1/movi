package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.AvisoPorRevisar
import com.jvillada.movi.shared.model.CATEGORIAS_DE_DOS_PATAS
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.conElPeriodoDe
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import com.jvillada.movi.ui.porrevisar.AnotacionEntendida
import com.jvillada.movi.ui.porrevisar.LoQueMoviEntendio
import kotlin.math.roundToLong

/*
 * # Ola 5 · Sin abrir la app: la notificación de un aviso, con botones
 *
 * Monzo y Revolut avisan cada compra al instante («Rappi · $27.500») y dejan actuar desde la
 * notificación. Movi ya avisaba que algo llegó a «Por revisar» (Ola 1), pero para confirmarlo había
 * que abrir la app. Acá vive **qué ofrece la notificación y qué dice**, puro y probado; quién la
 * pinta y quién confirma viven en `:androidApp` (`Avisador`, `ConfirmarDesdeLaNotificacionWorker`).
 *
 * La regla de «¿se puede confirmar sin mirar?» NO es nueva: es la de la bandeja que se vacía sola
 * ([LoQueMoviEntendio], `dudasDelAviso` en `:core`). Si la fila de «Por revisar» ofrecería
 * «Confirmar», la notificación también; si la fila manda a «Revisar», la notificación también.
 */

/** Qué botones lleva la notificación de «Por revisar». */
enum class BotonesDelAviso {
    /** Un gasto o ingreso suelto que Movi entendió entero: «Confirmar» y «Otra categoría». */
    CONFIRMAR_U_OTRA_CATEGORIA,

    /** Un solo aviso con alguna duda (un pago de tarjeta, un traspaso, la cuenta sin saber): solo «Revisar». */
    SOLO_REVISAR,

    /** Varios avisos juntos: la notificación de resumen, sin botones de confirmar. */
    RESUMEN,
}

/**
 * **Qué ofrece la notificación** con [cuantos] avisos a la vista y lo que Movi entendió del único
 * ([entendido], `null` si no se pudo leer la propuesta: sin red, un server viejo).
 *
 * «Confirmar» solo cuando la confirmación es **inequívoca**: un aviso, sin dudas, con la propuesta
 * completa (monto, cuenta y categoría), suelto (no un pago avisado varias veces) y que sea un gasto o
 * un ingreso. Un pago de tarjeta, una cuota, un traspaso o un avance ya traen la duda de dos patas,
 * pero se revisa también la categoría por si acaso: confirmar uno de esos suelto lo contaría como gasto.
 */
fun botonesDelAviso(cuantos: Int, entendido: LoQueMoviEntendio?): BotonesDelAviso {
    if (cuantos > 1) return BotonesDelAviso.RESUMEN
    val anotacion = entendido?.anotacion ?: return BotonesDelAviso.SOLO_REVISAR
    val inequivoco = entendido.dudas.isEmpty() &&
        anotacion.pago == null &&
        anotacion.monto > 0 &&
        anotacion.categoria.isNotBlank() &&
        anotacion.categoria !in CATEGORIAS_DE_DOS_PATAS &&
        anotacion.leido.type in setOf(TransactionType.EXPENSE, TransactionType.INCOME)
    return if (inequivoco) BotonesDelAviso.CONFIRMAR_U_OTRA_CATEGORIA else BotonesDelAviso.SOLO_REVISAR
}

/** Un monto para la notificación, o [PLATA_OCULTA] con «Ocultar los saldos» prendido. */
fun montoVisibleEnElAviso(monto: Double, moneda: String, ocultos: Boolean): String =
    if (ocultos) PLATA_OCULTA else montoParaElAviso(monto, moneda)

/**
 * **El texto de la notificación de UN aviso** (Ola 5).
 *
 * - Título: «Rappi · $27.500» (el nombre que propone la bandeja y el monto del banco).
 * - Texto: «Restaurantes · Nu» —la categoría que Movi propone y la cuenta— cuando Movi lo entendió;
 *   si no, de dónde vino y que se toca para revisar.
 * - Una línea más, [llevasHoy], si se sabe sin pedir nada a la red (ver [lineaDeLlevasHoy]).
 *
 * Con [ocultos] ningún monto sale en claro: ni el del título ni el de «Llevas».
 */
fun textoDelAvisoSuelto(
    aviso: AvisoPorRevisar,
    anotacion: AnotacionEntendida?,
    llevasHoy: String?,
    ocultos: Boolean,
): TextoDeAviso {
    val nombre = aviso.descripcion.trim().ifBlank { origenParaElAviso(aviso.origen) ?: "Movimiento" }
    val titulo = "$nombre · ${montoVisibleEnElAviso(aviso.monto, aviso.moneda, ocultos)}"
    val texto = if (anotacion != null) {
        "${anotacion.categoria} · ${anotacion.cuenta.name}"
    } else {
        val origen = origenParaElAviso(aviso.origen)
        if (origen != null) "$origen — $TOCA_PARA_REVISAR" else "Tienes un movimiento por revisar — $TOCA_PARA_REVISAR"
    }
    return TextoDeAviso(titulo = titulo, texto = texto, lineas = listOfNotNull(texto, llevasHoy).takeIf { llevasHoy != null }.orEmpty())
}

/**
 * **«Llevas $64.300 hoy de $404.412»**, o `null` si no se puede decir con honestidad.
 *
 * Sale **solo de la instantánea del Inicio** que el teléfono ya guarda ([instantanea], la misma del
 * widget): una notificación no pide red para esto. «Llevas» es lo gastado hoy según esa instantánea
 * más este aviso si es un gasto en pesos (todavía no está anotado, pero la plata ya salió); «de» es
 * la meta del día de la tarjeta «Disponible».
 *
 * `null` cuando: no hay instantánea, es de otro día ([selladaEn] no es de hoy: lo gastado hoy estaría
 * viejo), no hay disponible que afirmar o la meta del día es cero, o el aviso no es un gasto en pesos.
 */
fun lineaDeLlevasHoy(
    instantanea: DashboardData?,
    selladaEn: Long?,
    aviso: AvisoPorRevisar,
    ahora: Long,
    ocultos: Boolean,
): String? {
    if (instantanea == null || selladaEn == null) return null
    if (aviso.tipo != TransactionType.EXPENSE || aviso.moneda != "COP") return null
    val hoy = epochMillisToAppDate(ahora)
    if (epochMillisToAppDate(selladaEn) != hoy) return null
    val disponible = runCatching { disponibleDelInicio(instantanea.conElPeriodoDe(ahora), hoy) }.getOrNull() ?: return null
    val meta = disponible.hoy.meta
    if (meta <= 0L) return null
    val llevas = disponible.hoy.gastado + aviso.monto.roundToLong()
    fun plata(v: Long) = if (ocultos) PLATA_OCULTA else formatCOPSinOcultar(v)
    return "Llevas ${plata(llevas)} hoy de ${plata(meta)}"
}

/** Lo que dice la notificación cuando «Confirmar» terminó bien. Se cierra sola a los pocos segundos. */
const val AVISO_ANOTADO: String = "Anotado ✓"

/** Lo que dice cuando «Confirmar» no pudo: el aviso sigue en «Por revisar». */
const val AVISO_NO_SE_PUDO: String = "No se pudo, ábrelo en Movi"

/** Mientras «Confirmar» viaja: sin botones, para que un segundo toque no tenga dónde caer. */
const val AVISO_ANOTANDO: String = "Anotando…"

/** Los rótulos de los botones. */
const val BOTON_CONFIRMAR: String = "Confirmar"
const val BOTON_OTRA_CATEGORIA: String = "Otra categoría"
const val BOTON_REVISAR: String = "Revisar"

/** Cuánto se queda a la vista «Anotado ✓» antes de cerrarse sola. */
const val MILIS_DE_ANOTADO_A_LA_VISTA: Long = 4_000L

// ── A dónde lleva cada botón ────────────────────────────────────────────────

/** «Revisar»: abre Reconciliar de ese aviso. `"aviso:<id>"`. */
const val PREFIJO_ABRIR_AVISO: String = "aviso:"

/** «Otra categoría»: abre Reconciliar de ese aviso con el selector de categoría abierto. */
const val PREFIJO_ABRIR_AVISO_CATEGORIA: String = "aviso-categoria:"

fun abrirElAviso(avisoId: String): String = "$PREFIJO_ABRIR_AVISO$avisoId"
fun abrirElAvisoEnLaCategoria(avisoId: String): String = "$PREFIJO_ABRIR_AVISO_CATEGORIA$avisoId"
