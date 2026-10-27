package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.ui.dashboard.cuentasDelCarrusel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Crear una cuenta es igual en todos lados (vuelta de diseño 16)
 *
 * La guía de primer uso y Patrimonio → «Nueva cuenta» pintan el mismo [FormularioDeCuenta]. Acá, la
 * parte pura: los tipos en palabras, el nombre que se propone solo, y la regla de «el tipo no se dice
 * si el nombre ya lo dice», que también usa la tarjeta de cuenta del Inicio.
 */
class FormularioDeCuentaTest {

    @Test
    fun `los tipos se entienden y van a los tipos que ya existen`() {
        assertEquals(listOf("Ahorros", "Corriente", "Efectivo", "Inversión"), TIPOS_DE_CUENTA.map { it.first })
        assertEquals(
            listOf(AccountType.SAVINGS, AccountType.CHECKING, AccountType.CASH, AccountType.INVESTMENT),
            TIPOS_DE_CUENTA.map { it.second },
        )
        assertTrue(TIPOS_DE_CUENTA.none { it.first == "Dinero" }, "«Dinero» es un grupo de Patrimonio, no un tipo")
    }

    @Test
    fun `elegir un banco propone el nombre con el tipo`() {
        assertEquals("Bancolombia Ahorros", nombrePropuestoDeLaCuenta("Bancolombia", AccountType.SAVINGS))
        assertEquals("Nu Inversión", nombrePropuestoDeLaCuenta("Nu", AccountType.INVESTMENT))
        assertNull(nombrePropuestoDeLaCuenta(null, AccountType.SAVINGS))

        val f = EstadoDelFormularioDeCuenta()
        f.elegirBanco("Bancolombia")
        assertEquals("Bancolombia Ahorros", f.nombre)
        // Mientras siga siendo la propuesta, cambiar el tipo o el banco la actualiza.
        f.elegirTipo(AccountType.CHECKING)
        assertEquals("Bancolombia Corriente", f.nombre)
        f.elegirBanco("Davivienda")
        assertEquals("Davivienda Corriente", f.nombre)
        assertEquals(AccountType.CHECKING, f.cuentaNueva().type)
        assertEquals("Davivienda Corriente", f.cuentaNueva().name)
        assertEquals("COP", f.cuentaNueva().currency)
    }

    @Test
    fun `un nombre escrito a mano no se pisa`() {
        val f = EstadoDelFormularioDeCuenta()
        f.nombre = "La de la nómina"
        f.elegirBanco("Bancolombia")
        f.elegirTipo(AccountType.CHECKING)
        assertEquals("La de la nómina", f.nombre)
        assertEquals("La de la nómina", f.nombreParaUnBien())
    }

    @Test
    fun `un bien no hereda el nombre propuesto para una cuenta`() {
        val f = EstadoDelFormularioDeCuenta()
        f.elegirBanco("Bancolombia")
        assertEquals("", f.nombreParaUnBien())
    }

    @Test
    fun `sin nombre no se crea, y limpiar vuelve al principio`() {
        val f = EstadoDelFormularioDeCuenta()
        assertEquals(false, f.sePuedeCrear)
        f.elegirBanco("Nu")
        f.saldo = 1_500_000L
        assertTrue(f.sePuedeCrear)
        assertEquals(1_500_000L, f.cuentaNueva().balance)
        f.limpiar()
        assertNull(f.banco)
        assertEquals("", f.nombre)
        assertNull(f.saldo)
        assertEquals(AccountType.SAVINGS, f.tipo)
    }

    @Test
    fun `el tipo no se dice si el nombre ya lo dice`() {
        assertNull(tipoSiElNombreNoLoDice("Bancolombia Ahorros", "Ahorros"))
        assertNull(tipoSiElNombreNoLoDice("Cuenta de ahorros 8133", "Ahorros"))
        assertNull(tipoSiElNombreNoLoDice("Fondo de inversion", "Inversión"))
        assertNull(tipoSiElNombreNoLoDice("Tarjeta Nu", "Tarjeta"))
        assertEquals("Ahorros", tipoSiElNombreNoLoDice("Nu", "Ahorros"))
        assertEquals("Tarjeta", tipoSiElNombreNoLoDice("Master Black 3684", "Tarjeta"))
    }

    /**
     * La tarjeta de cuenta del Inicio decía «Bancolombia Ahorros» con «Ahorros» a la derecha. Ahora usa
     * la misma regla que Patrimonio.
     */
    @Test
    fun `la tarjeta del Inicio no repite el tipo que dice el nombre`() {
        val carrusel = cuentasDelCarrusel(
            listOf(
                Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800),
                Account("a2", "Nu", AccountType.SAVINGS, 2_890_000),
                Account("i1", "Skandia", AccountType.INVESTMENT, 5_000_000),
                Account("t1", "Master Black", AccountType.CREDIT_CARD, -27_350_341),
            ),
        )!!.associateBy { it.cuenta.name }
        assertNull(carrusel.getValue("Bancolombia Ahorros").tipo)
        assertEquals("Ahorros", carrusel.getValue("Nu").tipo)
        assertEquals("Inversión", carrusel.getValue("Skandia").tipo)
        assertEquals("Tarjeta", carrusel.getValue("Master Black").tipo)
    }
}
