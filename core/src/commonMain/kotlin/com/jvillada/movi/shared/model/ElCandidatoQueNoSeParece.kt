package com.jvillada.movi.shared.model

import kotlin.math.abs

/*
 * # Una propuesta de pago muy distinta del monto no va de principal (vuelta 51)
 *
 * «Revisar» del Carro en el Inicio llevaba a Pagos con «¿Ya pagaste el de octubre?» sugiriendo un
 * tanqueo de $260.000 para una factura de $9.000. El emparejamiento no exige el monto (un salario varía
 * con las retenciones), pero un candidato que se aleja más de la mitad del monto de la regla no es una
 * buena respuesta a «¿ya pagaste?». La fila deja «Anotar pago» como principal y el candidato queda como
 * enlace («¿Es el de $260.000?»). **Nunca se tilda solo**: esto solo decide qué
 * se ofrece primero.
 */

/** Cuánto puede alejarse un candidato del monto de la regla para proponerse como principal: 50 %. Elegido. */
const val DIFERENCIA_MAXIMA_DEL_CANDIDATO: Double = 0.5

/**
 * **¿[candidato] está demasiado lejos de [esperado]** para proponerse como principal? Más del 50 % del
 * monto de la regla, hacia arriba o hacia abajo. Sin un esperado comparable no se afirma nada (`false`):
 * una tarjeta, cuyo monto es el saldo ([montoEsSaldo]); un candidato en otra moneda que la de la regla;
 * una regla sin monto.
 */
fun candidatoMuyDistinto(esperado: Long, montoEsSaldo: Boolean, monedaEsperada: String, candidato: FinancialEvent): Boolean {
    if (montoEsSaldo || esperado <= 0L || candidato.currency != monedaEsperada) return false
    return abs(candidato.amount - esperado) > esperado * DIFERENCIA_MAXIMA_DEL_CANDIDATO
}

/** ¿Este candidato es muy distinto del monto de la fila? Ver [candidatoMuyDistinto]. */
fun PagoDelPeriodo.candidatoMuyDistinto(candidato: FinancialEvent): Boolean =
    candidatoMuyDistinto(monto, montoEsSaldo, moneda, candidato)

/**
 * **Los candidatos que sí se proponen como principal**: los que no son muy distintos del monto. Vacío
 * cuando todos se alejan: la fila ofrece «Anotar pago» primero, y el Inicio también.
 */
val PagoDelPeriodo.candidatosParecidos: List<FinancialEvent>
    get() = candidatos.filterNot { candidatoMuyDistinto(it) }

/*
 * # Vuelta 72 · un candidato que no se parece ni de lejos no se propone
 *
 * La revisión 4 del 29-oct: para «Carro $9.000» Pagos preguntaba «¿Es el de $260.000?», un tanqueo en
 * Terpel de 29 veces el monto, solo porque compartía la categoría. Con el enlace de la vuelta 51 la
 * pregunta ya no era principal, pero seguía siendo una pregunta sin sentido. Ahora el server **no lo
 * propone**: un candidato que pasa del doble del monto de la regla o queda por debajo de la mitad sale
 * de la lista del «¿Es este?». Solo de la lista: lo que Movi empareja sola (el nombre que pega, o
 * categoría + cuenta + monto exacto) no cambia, así que ninguna fila tildada se destilda.
 */

/** Un candidato por encima de este múltiplo del monto de la regla no se propone: el doble. Elegido. */
const val VECES_MAXIMAS_DEL_CANDIDATO: Long = 2

/** Un candidato por debajo de esta fracción del monto de la regla no se propone: la mitad (1/2). Elegido. */
const val FRACCION_MINIMA_DEL_CANDIDATO_DIVISOR: Long = 2

/**
 * **¿[candidato] está fuera de proporción con [esperado]?** Más del doble ([VECES_MAXIMAS_DEL_CANDIDATO])
 * o menos de la mitad ([FRACCION_MINIMA_DEL_CANDIDATO_DIVISOR]); el doble y la mitad exactos todavía se
 * proponen. Sin un esperado comparable no se afirma nada (`false`, se propone como hoy): una tarjeta
 * (su monto es el saldo, [montoEsSaldo]), una regla sin monto, o un candidato en otra moneda.
 */
fun candidatoFueraDeProporcion(esperado: Long, montoEsSaldo: Boolean, monedaEsperada: String, candidato: FinancialEvent): Boolean {
    if (montoEsSaldo || esperado <= 0L || candidato.currency != monedaEsperada) return false
    return candidato.amount > esperado * VECES_MAXIMAS_DEL_CANDIDATO ||
        candidato.amount * FRACCION_MINIMA_DEL_CANDIDATO_DIVISOR < esperado
}
