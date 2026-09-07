package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.UsoDeCuenta
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ola 17: la regla que decide si la cuenta ya elegida se suelta al cambiar de uso.
 *
 * Sacar el selector de cuenta de la rama de regla —para que una suscripción también diga con qué
 * tarjeta se paga— abrió un agujero que antes no existía: el selector dejó de desaparecer al pasar
 * a suscripción, pero la elección no se limpiaba, y `cuentasPara(..., conservar)` deja la elegida
 * visible y marcada aunque ya no califique. La secuencia era: «Ingreso» → una cuenta de inversión
 * (destino válido de un ingreso) → «Dólares» o «Una vez al año», que fuerzan el tipo a Gasto. Se
 * guardaba un cobro que sale de una cuenta de la que no sale plata.
 */
class LaCuentaSobreviveAlUsoTest {

    private val ahorros = Account("acc-ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000L, "COP")
    private val fondo = Account("acc-fondo", "Fondo de inversión", AccountType.INVESTMENT, 5_000_000L, "COP")
    private val tarjeta = Account("acc-tc", "Nu Tarjeta", AccountType.CREDIT_CARD, 0L, "COP")

    /** El caso exacto que motivó el arreglo. */
    @Test
    fun `una cuenta de inversion no sobrevive al pasar a gasto`() {
        assertTrue(laCuentaSobreviveAlUso(fondo, UsoDeCuenta.DESTINO_DE_INGRESO))
        assertFalse(laCuentaSobreviveAlUso(fondo, UsoDeCuenta.ORIGEN_DE_GASTO))
    }

    /**
     * El contrapeso, y es lo que hace que la regla no sea una excusa para borrarle la elección al
     * dueño en cada toque: lo que sirve para las dos cosas se queda.
     */
    @Test
    fun `una cuenta de ahorros sirve para las dos y no se suelta`() {
        assertTrue(laCuentaSobreviveAlUso(ahorros, UsoDeCuenta.DESTINO_DE_INGRESO))
        assertTrue(laCuentaSobreviveAlUso(ahorros, UsoDeCuenta.ORIGEN_DE_GASTO))
    }

    /** Una tarjeta paga cobros —es el caso de las cuatro suscripciones del dueño— pero no recibe ingresos. */
    @Test
    fun `una tarjeta sobrevive como origen de gasto y no como destino de ingreso`() {
        assertTrue(laCuentaSobreviveAlUso(tarjeta, UsoDeCuenta.ORIGEN_DE_GASTO))
        assertFalse(laCuentaSobreviveAlUso(tarjeta, UsoDeCuenta.DESTINO_DE_INGRESO))
    }

    /** Sin cuenta elegida no hay nada que soltar, y el efecto no tiene que hacer nada. */
    @Test
    fun `sin cuenta elegida siempre sobrevive`() {
        assertTrue(laCuentaSobreviveAlUso(null, UsoDeCuenta.ORIGEN_DE_GASTO))
        assertTrue(laCuentaSobreviveAlUso(null, UsoDeCuenta.DESTINO_DE_INGRESO))
    }
}
