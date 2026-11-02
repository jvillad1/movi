package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.LIBRE_PARA_GASTAR

/*
 * # «¿De dónde sale?»: la cascada de «Te quedan» (vuelta de diseño 28)
 *
 * Un segundo diseñador encontró cinco cifras de «plata disponible» que no se distinguían: «Te quedan»
 * en Hoy, «Disponible del período» en Plan, «llegas a» en la caja, el «por día» de la columna de Plan
 * y «Te quedó» en Tus períodos. Cada una era correcta para su concepto. Esta cascada es la que une las
 * dos primeras: dice, renglón por renglón, cómo se llega de lo que tenías al empezar a «Disponible del
 * período» y de ahí, restando lo que gastaste, a «Te quedan».
 *
 * **No es una cuenta nueva.** Cada renglón es un campo que [DisponibleDelPeriodo] ya tiene —lo que
 * tenías, lo que entró, lo que falta por recibir, lo que guardaste o apartaste, los otros pagos de
 * deuda, los fijos, el ahorro del período y lo gastado en el período— y los dos totales son
 * [DisponibleDelPeriodo.disponible] y [VentanaDelDisponible.teQuedan] del período, las cifras que ya
 * pintan Plan y el héroe. La prueba (`CascadaDelDisponibleTest`) exige que los renglones sumen esas
 * dos cifras al peso.
 *
 * La pintan el héroe de Hoy (al tocarlo, en una hoja) y la tarjeta del Disponible (Plan y Hoy, detrás
 * de «¿De dónde sale?») con el MISMO componente, `CascadaDelDisponibleEnPantalla`.
 */

/** Un renglón de la cascada: su nombre, cuánto suma (con signo) y, si hace falta, una aclaración. */
data class RenglonDeLaCascada(
    val rotulo: String,
    val monto: Long,
    val detalle: String? = null,
    /** Lo que suma: el «+» y el verde de plata que entra. Lo que resta va con su «−», sin color. */
    val suma: Boolean = false,
    /** Un renglón que solo ajusta la cuenta (sin margen) y no es plata: en gris. */
    val apagado: Boolean = false,
)

/**
 * **La cascada entera**: de lo que hubo a «Disponible del período» ([deDondeSale] suma [disponible]),
 * y de ahí a «Te quedan» ([despues] suma `total − disponible`).
 *
 * @property rotuloDelTotal el mismo rótulo del héroe: «Te quedan» o «Te faltan».
 * @property total la cifra del héroe CON signo: [DisponibleDelPeriodo.loQueQueda] (vuelta 108).
 * @property enRojo el mismo criterio que pinta de rojo el héroe.
 */
data class CascadaDelDisponible(
    val deDondeSale: List<RenglonDeLaCascada>,
    val disponible: Long,
    val despues: List<RenglonDeLaCascada>,
    val rotuloDelTotal: String,
    val total: Long,
    val enRojo: Boolean,
)

/**
 * El rótulo del subtotal: el mismo nombre que la tarjeta de Plan le da a esa cifra. Vuelta 108: «Libre para
 * gastar» ([LIBRE_PARA_GASTAR]); «Disponible del período» era jerga.
 */
const val ROTULO_DISPONIBLE_DEL_PERIODO: String = LIBRE_PARA_GASTAR

/** El rótulo de lo gastado en el período, el último renglón antes del total. */
const val ROTULO_GASTASTE_EN_EL_PERIODO: String = "Gastaste"

/** La aclaración de «Gastaste»: no es todo lo que salió, sino el gasto del día a día. */
const val DETALLE_GASTASTE_EN_EL_PERIODO: String = "En el día a día, sin los fijos"

/** El renglón de los fijos: los del checklist del período, pagados o no ([fijosDelPeriodo]). */
const val ROTULO_FIJOS_DEL_PERIODO: String = "Fijos del período"

/** La aclaración de los fijos: también los que ya pagaste (lo que pagaste no está en «Gastaste»). */
const val DETALLE_FIJOS_DEL_PERIODO: String = "Pagados y por pagar"

/**
 * **[CascadaDelDisponible] desde el disponible**, pura. Solo nombra lo que no es cero (salvo lo que
 * tenías, lo que entró, los fijos y lo gastado, que son la cuenta misma).
 *
 * Con un server viejo (sin [PlataDelDisponible]) la cuenta es «ingresos menos fijos» y la cascada
 * también: lo que entró, lo que falta por recibir y los fijos.
 */
fun cascadaDelDisponible(d: DisponibleDelPeriodo): CascadaDelDisponible {
    val plata = d.plata
    val deDondeSale = buildList {
        if (plata == null) {
            add(RenglonDeLaCascada("Lo que entró", d.ingresosRecibidos, suma = true))
        } else {
            add(RenglonDeLaCascada("Tenías el ${d.diaDeInicio}", plata.saldoAlInicio, detalle = "En Tu plata, al empezar el período"))
            add(RenglonDeLaCascada("Lo que entró", plata.entradas, suma = true))
        }
        if (d.ingresosPorRecibir > 0L) {
            add(RenglonDeLaCascada("Por recibir", d.ingresosPorRecibir, detalle = "Ingresos del período que faltan", suma = true))
        }
        if (plata != null) {
            if (plata.guardado > 0L) add(RenglonDeLaCascada("Guardaste en un ahorro", -plata.guardado))
            if (plata.apartado > 0L) add(RenglonDeLaCascada("Apartaste", -plata.apartado))
            if (plata.otrosPagosDeDeuda > 0L) add(RenglonDeLaCascada("Otros pagos de deuda", -plata.otrosPagosDeDeuda))
        }
        add(RenglonDeLaCascada(ROTULO_FIJOS_DEL_PERIODO, -d.fijos, detalle = DETALLE_FIJOS_DEL_PERIODO))
        if (d.ahorroDelPeriodo > 0L) {
            add(RenglonDeLaCascada("Tu ahorro del período", -d.ahorroDelPeriodo, detalle = "El que elegiste en Ajustes"))
        }
    }
    val periodo = d.periodo
    // Vuelta 108: sin margen ya no se cuenta «desde $0» (el renglón «Sin margen, Movi cuenta desde $0» se
    // fue): la cifra del héroe es `loQueQueda`, y lo que faltó para los fijos también falta.
    val despues = listOf(RenglonDeLaCascada(ROTULO_GASTASTE_EN_EL_PERIODO, -periodo.gastado, detalle = DETALLE_GASTASTE_EN_EL_PERIODO))
    val heroe = teQuedanDeHoy(d)
    return CascadaDelDisponible(
        deDondeSale = deDondeSale,
        disponible = d.disponible,
        despues = despues,
        rotuloDelTotal = heroe.rotulo,
        total = heroe.teQuedan,
        enRojo = heroe.enRojo,
    )
}

// ── Vuelta 108 · la cascada corta: hasta que te paguen ───────────────────────

/** La cascada corta de «¿te alcanza hasta que te paguen?»: lo libre, lo que entra, los pagos y el total. */
data class CascadaHastaQueTePaguen(
    val renglones: List<RenglonDeLaCascada>,
    val rotuloDelTotal: String,
    val total: Long,
    val enRojo: Boolean,
)

/** El primer renglón de la cascada corta. */
const val ROTULO_TIENES_LIBRE_HOY: String = "Tienes libre hoy"

/** El renglón de los pagos que faltan. */
const val ROTULO_FALTAN_EN_PAGOS: String = "Faltan en pagos"

/**
 * **La cascada corta** (vuelta 108, la propuesta A): la historia de la cifra del héroe contada al revés de
 * la del período —«Tienes libre hoy $9.086.784 → Te entra $640.000 → Faltan en pagos −$10.315.703 (9 pagos)
 * → Te faltan $588.919»—. Sale entera de [TeQuedanDeHoy]: «libre hoy» es `libres − porRecibir`, así que
 * los renglones suman la cifra al peso por construcción (lo prueba `CascadaDelDisponibleTest`). La cuenta
 * del período ([cascadaDelDisponible]) va debajo, para quien la quiera completa.
 */
fun cascadaHastaQueTePaguen(t: TeQuedanDeHoy): CascadaHastaQueTePaguen = CascadaHastaQueTePaguen(
    renglones = buildList {
        add(
            RenglonDeLaCascada(
                ROTULO_TIENES_LIBRE_HOY, t.libres - t.porRecibir,
                detalle = "En tus cuentas, sin lo apartado, tu ahorro ni lo que debes de compras con tarjeta",
            ),
        )
        if (t.porRecibir > 0L) add(RenglonDeLaCascada("Te entra", t.porRecibir, detalle = "Ingresos del período que faltan", suma = true))
        if (t.pagosQueFaltan > 0L) {
            val cuantos = if (t.cuantosPagosFaltan == 1) "1 pago" else "${t.cuantosPagosFaltan} pagos"
            add(RenglonDeLaCascada(ROTULO_FALTAN_EN_PAGOS, -t.pagosQueFaltan, detalle = cuantos))
        }
    },
    rotuloDelTotal = t.rotulo,
    total = t.teQuedan,
    enRojo = t.enRojo,
)
