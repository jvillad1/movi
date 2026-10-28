package com.jvillada.movi.ui.dashboard

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
 * @property rotuloDelTotal el mismo rótulo del héroe: «Te quedan» o «Te pasaste por».
 * @property total la cifra del héroe CON signo: [VentanaDelDisponible.teQuedan] del período.
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

/** El rótulo del subtotal: el mismo nombre que la tarjeta de Plan le da a esa cifra. */
const val ROTULO_DISPONIBLE_DEL_PERIODO: String = "Disponible del período"

/** El rótulo de lo gastado en el período, el último renglón antes del total. */
const val ROTULO_GASTASTE_EN_EL_PERIODO: String = "Gastaste"

/** La aclaración de «Gastaste»: no es todo lo que salió, sino el gasto del día a día. */
const val DETALLE_GASTASTE_EN_EL_PERIODO: String = "En el día a día, sin los fijos"

/** El renglón de los fijos: los del checklist del período, pagados o no ([fijosDelPeriodo]). */
const val ROTULO_FIJOS_DEL_PERIODO: String = "Fijos del período"

/** La aclaración de los fijos: también los que ya pagaste (lo que pagaste no está en «Gastaste»). */
const val DETALLE_FIJOS_DEL_PERIODO: String = "Pagados y por pagar"

/**
 * El renglón que aparece solo **sin margen**: cuando los fijos superan lo que hubo, la meta del
 * período es cero (no un negativo) y «Te pasaste por» es solo lo gastado. Sin este renglón, la
 * cascada no daría la cifra del héroe.
 */
const val ROTULO_SIN_MARGEN: String = "Sin margen, Movi cuenta desde $0"

/** La aclaración del renglón sin margen. */
const val DETALLE_SIN_MARGEN: String = "Lo que faltó para los fijos no se resta otra vez"

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
    val despues = buildList {
        // La meta del período nunca es negativa (ver `disponibleDelPeriodo`): sin margen se cuenta desde
        // cero, y lo que faltó ya lo dice el renglón de arriba.
        val ajuste = periodo.meta - d.disponible
        if (ajuste != 0L) add(RenglonDeLaCascada(ROTULO_SIN_MARGEN, ajuste, detalle = DETALLE_SIN_MARGEN, apagado = true))
        add(RenglonDeLaCascada(ROTULO_GASTASTE_EN_EL_PERIODO, -periodo.gastado, detalle = DETALLE_GASTASTE_EN_EL_PERIODO))
    }
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
