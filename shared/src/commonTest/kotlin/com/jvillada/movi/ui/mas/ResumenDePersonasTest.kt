package com.jvillada.movi.ui.mas

import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.saldoDelEspacio
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * La fila «Personas» de Tú (vuelta 38): el saldo pendiente con los demás, con la misma frase que
 * Personas (`fraseDelSaldo`), o qué hay adentro cuando no hay nada pendiente.
 */
class ResumenDePersonasTest {

    private fun espacio(id: String, con: String) = Espacio(
        id = id,
        nombre = "Casa con $con",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_$con", con)),
    )

    private fun detalle(e: Espacio, pagadoPorMi: Long = 0L): DetalleDelEspacio {
        val gastos = if (pagadoPorMi > 0) listOf(GastoDelEspacio(
                id = "g_${e.id}", espacioId = e.id, pagadoPor = MIEMBRO_YO, monto = pagadoPorMi,
                // Mitad y mitad, como lo reparte «Repartir».
                partes = e.miembros.associate { it.id to pagadoPorMi / 2 },
            )) else emptyList()
        val saldo = saldoDelEspacio(e, gastos, emptyList())
        return DetalleDelEspacio(espacio = e, saldo = saldo, delPeriodo = saldo, gastos = gastos)
    }

    @Test
    fun `sin leer o sin espacios dice que hay adentro, sin cifra`() {
        assertEquals(ResumenDePersonas(PERSONAS_SIN_SALDO, null), resumenDePersonas(null))
        assertEquals(ResumenDePersonas(PERSONAS_SIN_SALDO, null), resumenDePersonas(emptyList()))
    }

    @Test
    fun `a mano no afirma ninguna deuda`() {
        assertEquals(ResumenDePersonas(PERSONAS_SIN_SALDO, null), resumenDePersonas(listOf(detalle(espacio("e1", "Caro")))))
    }

    @Test
    fun `con un saldo pendiente dice quien le debe a quien y cuanto`() {
        val resumen = resumenDePersonas(listOf(detalle(espacio("e1", "Caro"), pagadoPorMi = 780_000L)))
        assertEquals(ResumenDePersonas("Caro te debe", 390_000L), resumen)
    }

    @Test
    fun `con varios saldos dice cuantos, sin sumar deudas de personas distintas`() {
        val resumen = resumenDePersonas(
            listOf(
                detalle(espacio("e1", "Caro"), pagadoPorMi = 780_000L),
                detalle(espacio("e2", "Ana"), pagadoPorMi = 100_000L),
            ),
        )
        assertEquals(ResumenDePersonas("2 grupos con saldo pendiente", null), resumen)
    }
}
