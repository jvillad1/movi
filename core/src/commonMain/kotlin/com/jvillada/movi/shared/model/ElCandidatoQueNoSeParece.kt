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
