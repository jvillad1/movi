package com.jvillada.movi.ui.cuadre

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.saldoEnSuMoneda
import com.jvillada.movi.ui.components.signedMoney
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.datetime.LocalDate

/**
 * # Cuadrar saldos: las reglas, sin Compose
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

/** Un día en milisegundos. */
private const val UN_DIA_MS = 24L * 60L * 60L * 1000L

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
const val DIAS_PARA_VOLVER_A_CUADRAR = 45

/**
 * **¿Esta cuenta se puede cuadrar contra el banco?** Solo Dinero e Inversión.
 *
 * Es la misma regla que aplica el server (`POST /api/accounts/{id}/balance-adjustment`), y el
 * porqué está escrito allá con detalle. En corto: acá entran las cuentas donde «lo que dice el
 * banco» es **un** número. Una tarjeta no tiene esa propiedad —saldo actual, saldo a pagar, cupo
 * disponible y compras sin facturar son cuatro cifras distintas, y ninguna significa lo mismo que
 * la deuda que Movi acumula compra por compra—, y además ya tiene quién la concilie: el extracto y
 * la captura de mensajes. Un préstamo se cuadra en Créditos, donde al lado se ven la cuota, la
 * tasa y los intereses que explican la diferencia.
 */
fun sePuedeCuadrar(cuenta: Account): Boolean = cuenta.type.group != AccountGroup.DEUDA

/** Las cuentas que la pantalla «Cuadre de saldos» lista, en el orden en que vienen. */
fun cuentasParaCuadrar(cuentas: List<Account>): List<Account> = cuentas.filter { sePuedeCuadrar(it) }

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
 */
fun textoDelCuadre(saldoEnMovi: Long, saldoDelBanco: Long?, moneda: String): String {
    val diferencia = diferenciaDelCuadre(saldoEnMovi, saldoDelBanco)
    return when {
        diferencia == null -> "Escribe el saldo que te muestra el banco hoy."
        diferencia == 0L -> "Coincide con Movi: no hay nada que anotar."
        else -> "Movi dice ${formatMoney(saldoEnMovi, moneda)}, tú dices ${formatMoney(saldoDelBanco!!, moneda)}: " +
            "se va a anotar un ajuste de ${montoDelAjuste(diferencia, moneda)}."
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
fun diasSinCuadrar(cuenta: Account, ahora: Long): Long? {
    val desde = cuenta.lastAdjustmentAt ?: cuenta.firstEventAt ?: return null
    return ((ahora - desde) / UN_DIA_MS).coerceAtLeast(0L)
}

/** Ver [DIAS_PARA_VOLVER_A_CUADRAR]. Una cuenta que no se puede cuadrar nunca está atrasada. */
fun estaSinCuadrar(cuenta: Account, ahora: Long): Boolean =
    sePuedeCuadrar(cuenta) && (diasSinCuadrar(cuenta, ahora) ?: 0L) >= DIAS_PARA_VOLVER_A_CUADRAR

/**
 * Las cuentas que el aviso del Inicio menciona. Vacío = no se dice nada, que es lo que tiene que
 * pasar el día después de cuadrarlas: **nunca se le reclama una cuenta que acaba de cuadrar.**
 */
fun cuentasSinCuadrar(cuentas: List<Account>, ahora: Long): List<Account> =
    cuentas.filter { estaSinCuadrar(it, ahora) }

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
fun textoDelAvisoDeCuadre(atrasadas: List<Account>): String? = when {
    atrasadas.isEmpty() -> null
    atrasadas.size == 1 -> "Hace más de un mes que no cuadras ${atrasadas.first().name}"
    else -> "${atrasadas.size} cuentas llevan más de un mes sin cuadrar"
}

/**
 * **Desde cuándo nadie mira esta cuenta**, para el renglón de cada fila: «Cuadrada hoy»,
 * «Cuadrada el 3 de agosto», «Nunca la has cuadrado».
 *
 * La fecha se escribe con [etiquetaDeFecha], la misma forma que usan Movimientos y el selector de
 * fecha: si dos pantallas nombraran el mismo día distinto, parecería otro día.
 */
fun textoDelUltimoCuadre(cuenta: Account, hoy: LocalDate): String {
    val cuadre = cuenta.lastAdjustmentAt
        ?: return if (cuenta.firstEventAt == null) "Sin movimientos todavía" else "Nunca la has cuadrado"
    return when (val etiqueta = etiquetaDeFecha(fechaDeEpoch(cuadre), hoy)) {
        "Hoy" -> "Cuadrada hoy"
        "Ayer" -> "Cuadrada ayer"
        else -> "Cuadrada el $etiqueta"
    }
}
