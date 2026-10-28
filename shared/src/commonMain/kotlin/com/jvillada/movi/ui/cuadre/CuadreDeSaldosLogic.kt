package com.jvillada.movi.ui.cuadre

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.esBien
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.saldoEnSuMoneda
import com.jvillada.movi.ui.components.signedMoney
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * # Cuadrar saldos: las reglas, sin Compose
 *
 * Las de cuándo una cuenta lleva demasiado sin cuadrarse viven en `:core` (`CuadreDeSaldos.kt`)
 * desde que Movi AI contesta «¿Qué me recomiendas revisar primero?» sin el modelo; acá quedan con los
 * mismos nombres, y con el porqué de cada una.
 *
 * **El agujero que esto tapa.** Movi deriva cada saldo de los movimientos, así que todo lo que
 * mueve plata **sin emitir un movimiento** se desvía para siempre, y la diferencia se compone.
 * Medido sobre los datos reales del dueño el 21-sep: los rendimientos de Nu habían crecido
 * **$745.856** y los de la Fiducuenta **$1.637** sin un solo SMS, notificación ni fila de extracto
 * que capturar. Lo mismo pasa con una cuota de manejo que solo aparece en el extracto del mes.
 *
 * El mecanismo para corregirlo ya existía —el ajuste de saldo—, pero estaba de a una cuenta por
 * vez, escondido, y nada avisaba. Esto es lo que faltaba: la cuenta de la diferencia, quién puede
 * cuadrarse, y desde cuándo nadie mira una cuenta.
 */

/**
 * **Cuántos días puede pasar una cuenta sin cuadrarse antes de que Movi lo mencione.**
 *
 * El período del dueño es mensual con corte el 25, así que cuadrar una vez por período es el ritmo
 * natural: la cifra del banco al cierre es la que cuadra ese período. Entre dos cuadres seguidos
 * pasan ~30 días, así que un umbral de 30 le saltaría todos los meses justo antes de hacerlo —y un
 * aviso que aparece siempre enseña a ignorar la sección donde vive.
 *
 * 45 días es **un período entero más medio de gracia**: quien cuadra una vez por período no lo ve
 * nunca, y aparece recién cuando se saltó un corte completo. Que es exactamente cuando vale la
 * pena mirar, porque a esa altura la diferencia ya lleva dos meses de rendimientos encima.
 */
const val DIAS_PARA_VOLVER_A_CUADRAR = com.jvillada.movi.shared.model.DIAS_PARA_VOLVER_A_CUADRAR

/**
 * **¿Esta cuenta se «atrasa» en el cuadre?** Solo Dinero e Inversión: es la regla de `:core` que usan
 * el server, Movi AI y los avisos de «sin cuadrar». **No es la lista de la pantalla**: desde la vuelta
 * de diseño 37 la pantalla también lista las tarjetas (ver [seccionDelCuadre]), pero ninguna se
 * reclama como atrasada.
 *
 * Por qué una tarjeta no se reclama: «lo que dice el banco» de una tarjeta no es **un** número —saldo
 * actual, saldo a pagar, cupo disponible y compras sin facturar son cuatro cifras distintas—, y ya
 * tiene quién la concilie: el extracto y la captura de mensajes. Cuando el dueño sí quiere cuadrarla,
 * la pantalla le pide la cifra que va («Cuánto debes según el banco», la deuda total de hoy). Un
 * préstamo se cuadra en Créditos, donde al lado se ven la cuota, la tasa y los intereses que explican
 * la diferencia.
 */
fun sePuedeCuadrar(cuenta: Account): Boolean = com.jvillada.movi.shared.model.sePuedeCuadrar(cuenta)
// ↑ Un bien tampoco: no tiene saldo que comparar contra un banco, tiene un avalúo, y se actualiza
// en su propia hoja. El server lo rechaza igual (422), así que ofrecerlo acá sería un control muerto.

/**
 * **Las secciones de Cuadre de saldos** (vuelta de diseño 37), en el orden en que se ven. Cada cuenta
 * va bajo su encabezado; antes llevaba un rótulo «Dinero» suelto al final de su tarjeta.
 */
enum class SeccionDelCuadre(val titulo: String) {
    DINERO("Dinero"),
    INVERSION("Inversión"),
    TARJETAS("Tarjetas"),
}

/**
 * **En qué sección de Cuadre de saldos va [cuenta]**, o `null` si no va en la pantalla.
 *
 * Dinero e Inversión por [sePuedeCuadrar]; las **tarjetas** (vuelta de diseño 37) por este predicado
 * propio de la pantalla, sin tocar [sePuedeCuadrar] —que sigue sin tarjetas para el server y los
 * avisos—. Su ajuste va por la misma ruta que la hoja «Ajustar saldo» del detalle de la tarjeta
 * (`CardBalanceSheet`, `POST /api/accounts/{id}/balance-adjustment`, que acepta `CREDIT_CARD` desde
 * la vuelta 33). Los préstamos no: se cuadran en Créditos. Un bien tampoco.
 */
fun seccionDelCuadre(cuenta: Account): SeccionDelCuadre? = when {
    cuenta.esBien -> null
    cuenta.type == AccountType.CREDIT_CARD -> SeccionDelCuadre.TARJETAS
    !sePuedeCuadrar(cuenta) -> null
    cuenta.type.group == AccountGroup.INVERSION -> SeccionDelCuadre.INVERSION
    else -> SeccionDelCuadre.DINERO
}

/** ¿[cuenta] es una deuda que se cuadra acá (una tarjeta)? Cambia el rótulo y el texto del ajuste. */
fun seCuadraComoDeuda(cuenta: Account): Boolean = seccionDelCuadre(cuenta) == SeccionDelCuadre.TARJETAS

/** Las cuentas que la pantalla «Cuadre de saldos» lista, en el orden en que vienen. */
fun cuentasParaCuadrar(cuentas: List<Account>): List<Account> = cuentas.filter { seccionDelCuadre(it) != null }

/**
 * **Las cuentas agrupadas por sección**, en el orden de [SeccionDelCuadre] y, adentro, en el orden en
 * que vienen (así la cuenta de [cuentasConLaPrimera] queda primera de la suya). Sin secciones vacías.
 */
fun seccionesDelCuadre(cuentas: List<Account>): List<Pair<SeccionDelCuadre, List<Account>>> =
    SeccionDelCuadre.entries.mapNotNull { seccion ->
        cuentas.filter { seccionDelCuadre(it) == seccion }.takeIf { it.isNotEmpty() }?.let { seccion to it }
    }

/** El rótulo del campo de una cuenta de Dinero o Inversión. */
const val LO_QUE_DICE_EL_BANCO: String = "Lo que dice el banco"

/** El rótulo del campo de una tarjeta: la deuda total, no el pago mínimo ni el cupo. */
const val CUANTO_DEBES_SEGUN_EL_BANCO: String = "Cuánto debes según el banco"

/** El rótulo del campo de [cuenta]. Basta: la pantalla no repite una ayuda debajo de cada campo. */
fun rotuloDelCampoDelCuadre(cuenta: Account): String =
    if (seCuadraComoDeuda(cuenta)) CUANTO_DEBES_SEGUN_EL_BANCO else LO_QUE_DICE_EL_BANCO

/**
 * **Las cuentas del cuadre con [primeraId] arriba** (vuelta de diseño 23): «Cuadrar efectivo» abre el
 * cuadre de siempre con esa cuenta preseleccionada, la primera de la lista. Sin [primeraId] (o si no
 * está), el orden de siempre.
 */
fun cuentasConLaPrimera(cuentas: List<Account>, primeraId: String?): List<Account> {
    val primera = cuentas.firstOrNull { it.id == primeraId } ?: return cuentas
    return listOf(primera) + cuentas.filter { it.id != primera.id }
}

/**
 * **Un saldo imposible** (vuelta de diseño 23): efectivo en negativo. No se puede tener menos de cero
 * billetes; es un gasto anotado de más, uno que faltó por anotar al revés, o el efectivo que nunca se
 * cuadró. Se pintaba en rojo sin explicar nada: ahora la fila lo dice y el detalle ofrece
 * [CUADRAR_EFECTIVO].
 */
fun efectivoImposible(cuenta: Account): Boolean =
    cuenta.type == AccountType.CASH && saldoEnSuMoneda(cuenta).first < 0 && sePuedeCuadrar(cuenta)

/** La acción del detalle de un efectivo en negativo: el cuadre de saldos con esa cuenta arriba. */
const val CUADRAR_EFECTIVO: String = "Cuadrar efectivo"

/**
 * Lo que dice la fila de un efectivo en negativo. Corto a propósito (vuelta de diseño 24): «No puede
 * quedar en negativo: cuádralo» salía «No puede quedar en negativ…» en Patrimonio a 390 px. La cifra
 * ya va en rojo y con el signo; la fila dice qué pasa y qué hacer, y el porqué completo lo dice el
 * detalle ([EFECTIVO_EN_NEGATIVO]). Lo vigila `ElEfectivoEnNegativoSeLeeEnteroTest` a 360 dp ×1,3.
 */
const val EFECTIVO_EN_NEGATIVO_CORTO: String = "En negativo: cuádralo"

/** Lo que dice el detalle de un efectivo en negativo, al lado de [CUADRAR_EFECTIVO]. */
const val EFECTIVO_EN_NEGATIVO: String =
    "El efectivo no puede quedar en negativo. Cuenta lo que tienes en la mano y cuádralo."

/**
 * **Lo que Movi cree que hay en esta cuenta**, en la moneda de la cuenta — el mismo par
 * (monto, moneda) que muestran Cuentas y el selector de «Agregar», vía [saldoEnSuMoneda]. Dos
 * pantallas que dicen el saldo de la misma cuenta no pueden calcularlo cada una por su lado.
 */
fun saldoQueCreeMovi(cuenta: Account): Pair<Long, String> = saldoEnSuMoneda(cuenta)

/**
 * **La diferencia que se va a anotar**: lo que dice el banco menos lo que cree Movi.
 *
 * `null` cuando el campo está vacío, y eso **no es cero**: un campo en blanco significa «esta no la
 * cuadré», no «el banco dice $0». Confundirlos sería la peor falla posible de esta pantalla —
 * dejar una cuenta sin tocar le vaciaría el saldo.
 */
fun diferenciaDelCuadre(saldoEnMovi: Long, saldoDelBanco: Long?): Long? =
    saldoDelBanco?.let { it - saldoEnMovi }

/**
 * ¿Hay algo que escribir? Solo si el campo tiene una cifra **y** no es la misma que ya tiene Movi:
 * un movimiento de $0 sería ruido en el listado y no movería ningún saldo (el server piensa lo
 * mismo — ver `balanceAdjustmentFor`, que devuelve null y por eso el cuadre repetido es inofensivo).
 */
fun hayAjusteQueAnotar(diferencia: Long?): Boolean = diferencia != null && diferencia != 0L

/** El monto del ajuste con su signo, siempre explícito: «+$745.856», «−$81.352». */
fun montoDelAjuste(diferencia: Long, moneda: String): String =
    if (diferencia > 0) "+" + formatMoney(diferencia, moneda) else signedMoney(diferencia, moneda)

/**
 * **Lo que se va a anotar, dicho ANTES de anotarlo.**
 *
 * La pantalla no escribe nada hasta que el dueño confirma, y este texto es la mitad que hace que
 * confirmar signifique algo: dice las dos cifras que se están comparando y el movimiento exacto
 * que va a quedar. «Movi dice $352.082, tú dices $270.730: se va a anotar un ajuste de −$81.352».
 *
 * `null` con el campo vacío (vuelta de diseño 37): antes decía «Escribe el saldo que te muestra el
 * banco hoy.» debajo de **cada** cuenta; el rótulo del campo ya lo dice.
 *
 * Con [esDeuda] (una tarjeta) se dice como la hoja «Ajustar saldo» de la tarjeta: subir lo que se
 * debe es un **cargo**, bajarlo un **abono**.
 */
fun textoDelCuadre(saldoEnMovi: Long, saldoDelBanco: Long?, moneda: String, esDeuda: Boolean = false): String? {
    val diferencia = diferenciaDelCuadre(saldoEnMovi, saldoDelBanco) ?: return null
    val dos = "Movi dice ${formatMoney(saldoEnMovi, moneda)}, tú dices ${formatMoney(saldoDelBanco!!, moneda)}"
    return when {
        diferencia == 0L -> "Coincide con Movi: no hay nada que anotar."
        esDeuda && diferencia > 0L -> "$dos: se va a anotar un cargo de ${formatMoney(diferencia, moneda)}."
        esDeuda -> "$dos: se va a anotar un abono de ${formatMoney(-diferencia, moneda)}."
        else -> "$dos: se va a anotar un ajuste de ${montoDelAjuste(diferencia, moneda)}."
    }
}

/**
 * **Hace cuántos días que esta cuenta no se cuadra**, o `null` si la pregunta no aplica todavía.
 *
 * Se mide desde el último ajuste; si nunca hubo uno, desde su movimiento más viejo, que es la edad
 * de la cuenta dentro de Movi. Esa segunda mitad no es un detalle: sin ella, una cuenta recién
 * creada aparecería como atrasada el mismo día en que nació. Una cuenta sin ningún movimiento no
 * tiene contra qué medirse y devuelve `null` — no hay nada que cuadrar ahí.
 */
fun diasSinCuadrar(cuenta: Account, ahora: Long): Long? = com.jvillada.movi.shared.model.diasSinCuadrar(cuenta, ahora)

/** Ver [DIAS_PARA_VOLVER_A_CUADRAR]. Una cuenta que no se puede cuadrar nunca está atrasada. */
fun estaSinCuadrar(cuenta: Account, ahora: Long): Boolean = com.jvillada.movi.shared.model.estaSinCuadrar(cuenta, ahora)

/**
 * Las cuentas que el aviso del Inicio menciona. Vacío = no se dice nada, que es lo que tiene que
 * pasar el día después de cuadrarlas: **nunca se le reclama una cuenta que acaba de cuadrar.**
 */
fun cuentasSinCuadrar(cuentas: List<Account>, ahora: Long): List<Account> =
    com.jvillada.movi.shared.model.cuentasSinCuadrar(cuentas, ahora)

/**
 * **El aviso, en una línea**, o `null` cuando no hay nada que decir.
 *
 * Una sola línea y sin cifras: el aviso no sabe cuánto se desvió una cuenta —para saberlo habría
 * que preguntarle al banco, que es justamente lo que se le está pidiendo al dueño—, así que
 * inventar un número acá sería peor que no decir nada. Con el nombre alcanza para que la fila sea
 * accionable, que es la regla de todo lo que el Inicio sugiere revisar.
 *
 * Lo usan el Inicio (sección «Para revisar») y la pantalla de Cuentas. La misma frase en los dos
 * lados: dos avisos sobre el mismo hecho, escritos distinto, se leen como dos problemas.
 */
fun textoDelAvisoDeCuadre(atrasadas: List<Account>, ahora: Long = Clock.System.now().toEpochMilliseconds()): String? =
    com.jvillada.movi.shared.model.textoDelAvisoDeCuadre(atrasadas, ahora)

/**
 * **Desde cuándo nadie mira esta cuenta**, para el renglón de cada fila y el encabezado de su detalle:
 * «Cuadrada el 3 oct», «Nunca la has cuadrado», «Hace más de un mes que no la cuadras · última el 3
 * ago». Es [fraseDelCuadre] (`:core`), la misma regla con que Patrimonio y Hoy nombran esa cuenta
 * (vuelta 31: decían «hace más de un mes» y el detalle «nunca»). [hoy] queda por compatibilidad: la
 * fecha sale de [ahora] en la zona de la app.
 */
@Suppress("UNUSED_PARAMETER")
fun textoDelUltimoCuadre(cuenta: Account, hoy: LocalDate, ahora: Long = Clock.System.now().toEpochMilliseconds()): String =
    com.jvillada.movi.shared.model.fraseDelCuadre(cuenta, ahora)

/**
 * **La propuesta de un aviso que trajo el saldo, en una línea**: «El banco dice $138.407 y Movi
 * $138.361 → ajustar +$46». Las dos cifras y el movimiento exacto, antes de tocar nada: la misma
 * disciplina que [textoDelCuadre].
 */
fun textoDeLaPropuestaPorAviso(p: com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso): String =
    "El banco dice ${formatMoney(p.saldoDelBanco, p.moneda)} y Movi ${formatMoney(p.saldoEnMovi, p.moneda)} " +
        "→ ajustar ${montoDelAjuste(p.diferencia, p.moneda)}"

/** De cuándo es el saldo que se compara: «Según el aviso de hoy», «Según el aviso del 3 de octubre». */
fun textoDelMomentoDelAviso(momento: Long, hoy: LocalDate): String =
    when (val etiqueta = etiquetaDeFecha(fechaDeEpoch(momento), hoy)) {
        "Hoy" -> "Según el aviso de hoy"
        "Ayer" -> "Según el aviso de ayer"
        else -> "Según el aviso del $etiqueta"
    }

/**
 * La fila de «Para revisar» del Inicio cuando hay propuestas, o `null` sin ninguna. Con una sola
 * se nombra la cuenta, como el aviso de cuadre de siempre ([textoDelAvisoDeCuadre]).
 */
fun textoDeLasPropuestasPorAviso(propuestas: List<com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso>): String? =
    com.jvillada.movi.shared.model.textoDeLasPropuestasPorAviso(propuestas)
