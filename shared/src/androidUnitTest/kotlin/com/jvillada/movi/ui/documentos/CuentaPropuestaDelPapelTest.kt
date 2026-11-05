package com.jvillada.movi.ui.documentos

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.PistasDelPapel
import com.jvillada.movi.shared.model.ProductoDelPapel
import com.jvillada.movi.shared.model.TipoDeDocumento
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 129 · la hoja de subida propone la cuenta
 *
 * Una probadora subió «Nu_2026-09-17.pdf» y la hoja le dejaba adivinar entre «Bancolombia Ahorros» y
 * «Nu Ahorros». Si el archivo era el extracto de su tarjeta (que no tiene en Movi), las dos eran
 * equivocadas. La hoja ahora propone por lo que Movi ya leyó, por los últimos cuatro del nombre o por
 * el banco y el producto del nombre; y si no hay certeza, no adivina.
 */
class CuentaPropuestaDelPapelTest {

    private val bancolombia = Account("a-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 1)
    private val bancolombia0756 = Account("a-0756", "Bancolombia Ahorros 0756", AccountType.SAVINGS, 1)
    private val nuAhorros = Account("a-nu", "Nu Ahorros", AccountType.SAVINGS, 1)
    private val nuTarjeta = Account("c-nu", "Nu Tarjeta", AccountType.CREDIT_CARD, 1)
    private val efectivo = Account("e-1", "Efectivo 0756", AccountType.CASH, 1)

    @Test
    fun `los ultimos cuatro del nombre eligen la cuenta con ese numero`() {
        val p = cuentaPropuestaParaElPapel("Extracto_0756_09_2026.pdf", listOf(bancolombia, bancolombia0756, nuAhorros))
        assertEquals(bancolombia0756, p.cuenta)
        assertEquals(DeDondeSaleLaCuenta.NOMBRE_DEL_ARCHIVO, p.deDonde)
    }

    @Test
    fun `CuentaNu con solo Nu Ahorros propone Nu Ahorros`() {
        val p = cuentaPropuestaParaElPapel("CuentaNu_CRS119_2026-09.pdf", listOf(bancolombia, nuAhorros))
        assertEquals(nuAhorros, p.cuenta)
        assertEquals(DeDondeSaleLaCuenta.NOMBRE_DEL_ARCHIVO, p.deDonde)
    }

    @Test
    fun `Nu a secas no propone la cuenta de ahorros, que puede no ser la del archivo`() {
        // Sin la tarjeta en Movi: el archivo puede ser su extracto.
        assertEquals(CuentaPropuesta(null, null), cuentaPropuestaParaElPapel("Nu_2026-09-17.pdf", listOf(bancolombia, nuAhorros)))
        // Con las dos: el nombre no dice cuál.
        assertEquals(CuentaPropuesta(null, null), cuentaPropuestaParaElPapel("Nu_2026-09-17.pdf", listOf(nuAhorros, nuTarjeta)))
    }

    @Test
    fun `sin pistas, sin cuenta`() {
        assertEquals(CuentaPropuesta(null, null), cuentaPropuestaParaElPapel("documento.pdf", listOf(bancolombia, nuAhorros)))
    }

    @Test
    fun `lo que Movi ya leyo manda sobre el nombre`() {
        val leido = PistasDelPapel(banco = "Nu", numerosDeCuenta = listOf("1111"), producto = ProductoDelPapel.TARJETA)
        val p = cuentaPropuestaParaElPapel("Nu_2026-09-17.pdf", listOf(nuAhorros, nuTarjeta), leido)
        assertEquals(nuTarjeta, p.cuenta)
        assertEquals(DeDondeSaleLaCuenta.LO_QUE_MOVI_LEYO, p.deDonde)
        // Si el contenido dice tarjeta y el nombre dice cuenta, no se propone la cuenta.
        assertEquals(CuentaPropuesta(null, null), cuentaPropuestaParaElPapel("CuentaNu.pdf", listOf(nuAhorros), leido))
    }

    @Test
    fun `nunca propone una cuenta escondida detras de Ver todas`() {
        assertEquals(CuentaPropuesta(null, null), cuentaPropuestaParaElPapel("Extracto_0756.pdf", listOf(efectivo, nuAhorros)))
    }

    @Test
    fun `lo que la hoja dice de la cuenta`() {
        val propuesta = CuentaPropuesta(nuAhorros, DeDondeSaleLaCuenta.NOMBRE_DEL_ARCHIVO)
        assertEquals(
            listOf(PARA_QUE_SIRVE_LA_CUENTA, POR_EL_NOMBRE_DEL_ARCHIVO),
            lineasDeLaCuentaAlSubir(TipoDeDocumento.EXTRACTO, propuesta, elegidaAMano = false),
        )
        assertEquals(
            listOf(PARA_QUE_SIRVE_LA_CUENTA, ELIGE_LA_CUENTA),
            lineasDeLaCuentaAlSubir(TipoDeDocumento.EXTRACTO, CuentaPropuesta(null, null), elegidaAMano = false),
        )
        assertEquals(listOf(PARA_QUE_SIRVE_LA_CUENTA), lineasDeLaCuentaAlSubir(TipoDeDocumento.EXTRACTO, propuesta, elegidaAMano = true))
        // Una nómina o un contrato no llegan con cuenta propuesta: no se dice de dónde salió nada.
        assertEquals(emptyList(), lineasDeLaCuentaAlSubir(TipoDeDocumento.CONTRATO, propuesta, elegidaAMano = false))
        assertEquals(emptyList(), lineasDeLaCuentaAlSubir(TipoDeDocumento.OTRO, CuentaPropuesta(null, null), elegidaAMano = false))
    }
}
