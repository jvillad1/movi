package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **La regla única del patrimonio** ([patrimonioDe]), con los números del dueño al 23-sep.
 *
 * El patrimonio decía −$2.074M: $558.350 de plata, $116,2M de uso condicionado (Skandia, solo
 * para vivienda) y $2.191M de deudas, sin ningún bien. La casa de Almendros de Zúñiga tiene avalúo
 * de $1.411.903.920 y no existía en Movi. Estas pruebas fijan las dos cosas que no se pueden
 * romper: la casa entra al patrimonio, y NO entra a «Tu plata» ni a «uso condicionado».
 */
class PatrimonioTest {

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L)
    private val skandia = Account(
        "acc-skandia", "Skandia pensión voluntaria", AccountType.INVESTMENT, 116_200_000L,
        condicionadaA = "Vivienda",
    )
    private val hipoteca1254 = Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 1_030_600_000L)
    private val deudas = listOf(
        hipoteca1254,
        Account("acc-hip2", "Hipoteca 2", AccountType.LOAN, 700_000_000L),
        Account("acc-lib1", "Libranza 4818", AccountType.LOAN, 200_000_000L),
        Account("acc-lib2", "Libranza 2", AccountType.LOAN, 83_400_000L),
        Account("acc-8761", "Vehículo 8761", AccountType.LOAN, 177_000_000L),
    )
    private val casa = Account(
        "acc-casa", "Casa Almendros", AccountType.INVESTMENT, 0L,
        bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = "2026-08-28", deudaId = "acc-1254"),
    )

    @Test
    fun sin_la_casa_el_patrimonio_es_la_media_foto_de_antes() {
        val p = patrimonioDe(listOf(nu, skandia) + deudas)

        assertEquals(2_191_000_000L, p.deudas)
        assertEquals(-2_074_241_650L, p.neto, "el −$2.074M que el dueño veía")
    }

    @Test
    fun con_la_casa_el_patrimonio_la_suma_y_Tu_plata_y_lo_condicionado_no_se_mueven() {
        val p = patrimonioDe(listOf(nu, skandia, casa) + deudas)

        assertEquals(558_350L, p.tuPlata, "la casa no es plata disponible")
        assertEquals(116_200_000L, p.condicionado, "la casa no es plata con destino")
        assertEquals("Vivienda", p.condicionadoA)
        assertEquals(1_411_903_920L, p.bienes)
        assertEquals(2_191_000_000L, p.deudas)
        assertEquals(558_350L + 116_200_000L + 1_411_903_920L, p.loQueTienes)
        assertEquals(-662_337_730L, p.neto)
    }

    @Test
    fun un_bien_no_entra_a_Tu_plata_ni_a_condicionado_aunque_traiga_saldo_o_condicion() {
        // Un bien «sucio»: con un saldo que alguien le anotó y una condición que no debería tener.
        // El balde lo decide el bien, primero, y vale su avalúo.
        val raro = casa.copy(balance = 5_000_000L, estimatedTotalCop = 5_000_000L, condicionadaA = "Vivienda")
        val p = patrimonioDe(listOf(raro))

        assertEquals(0L, p.tuPlata)
        assertEquals(0L, p.invertido)
        assertEquals(0L, p.condicionado)
        assertNull(p.condicionadoA, "una condición de un bien no nombra a la plata condicionada")
        assertEquals(1_411_903_920L, p.bienes)
        assertEquals(1_411_903_920L, valorEnPesosDe(raro))
    }

    @Test
    fun la_regla_de_Tu_plata_excluye_los_bienes_en_los_dos_lados() {
        assertFalse(casa.esDeTuPlata())
        assertFalse(CuentaDelDisponible(AccountType.INVESTMENT, null, esBien = true).esTuPlata)
        // Lo que el server suma como «lo que tenías al empezar el período»: una suma sobre la casa
        // no entra aunque exista.
        val saldo = saldoDeTuPlata(
            sumas = listOf(
                SumaDeMovimientos("acc-nu", TransactionType.INCOME, 558_350L),
                SumaDeMovimientos("acc-casa", TransactionType.INCOME, 9_999_999L),
            ),
            cuentas = mapOf(
                "acc-nu" to CuentaDelDisponible(AccountType.SAVINGS, null),
                "acc-casa" to CuentaDelDisponible(AccountType.INVESTMENT, null, esBien = true),
            ),
        )
        assertEquals(558_350L, saldo)
    }

    @Test
    fun un_bien_solo_sirve_para_colgarle_papeles() {
        UsoDeCuenta.entries.forEach { uso ->
            assertEquals(uso == UsoDeCuenta.PAPEL_GUARDADO, sirvePara(casa, uso), "uso $uso")
        }
        // Y nunca es el valor por defecto de un gasto: queda detrás de «Ver todas».
        val picker = cuentasPara(listOf(casa, nu), UsoDeCuenta.ORIGEN_DE_GASTO)
        assertEquals(listOf(nu), picker.principales)
        assertEquals(listOf(casa), picker.otras)
    }

    @Test
    fun lo_que_es_tuyo_de_verdad_es_el_valor_menos_la_deuda_que_lo_financia() {
        val d = deudaDelBien(casa, listOf(casa, hipoteca1254))!!

        assertEquals("acc-1254", d.deuda.id)
        assertEquals(1_030_600_000L, d.debes)
        assertEquals(381_303_920L, d.tuyo)
        // Asociarla no mueve el patrimonio: la deuda ya restaba y el bien ya sumaba.
        val sinAsociar = casa.copy(bien = casa.bien!!.copy(deudaId = null))
        assertEquals(
            patrimonioDe(listOf(casa, hipoteca1254)).neto,
            patrimonioDe(listOf(sinAsociar, hipoteca1254)).neto,
        )
    }

    @Test
    fun sin_la_deuda_en_la_lista_no_hay_resta_que_mostrar() {
        assertNull(deudaDelBien(casa, listOf(casa)), "la borraron: el bien sigue, la resta no")
        assertNull(deudaDelBien(casa, listOf(casa, nu.copy(id = "acc-1254"))), "no es una deuda")
        assertNull(deudaDelBien(nu, listOf(nu)), "no es un bien")
    }

    @Test
    fun la_clase_viaja_como_texto_y_lo_desconocido_es_Otro() {
        assertEquals(ClaseDeBien.INMUEBLE, claseDeBien("INMUEBLE"))
        assertEquals(ClaseDeBien.VEHICULO, claseDeBien(" vehiculo "))
        assertEquals(ClaseDeBien.OTRO, claseDeBien("SEMOVIENTE"), "una clase de una versión futura no revienta")
        assertEquals(ClaseDeBien.OTRO, claseDeBien(null))
    }

    @Test
    fun normalizar_y_validar_un_bien() {
        val limpio = normalizarBien(Bien(clase = "inmueble", valor = 1L, valorAl = "  ", deudaId = " "))
        assertEquals(CLASE_DE_BIEN_INMUEBLE, limpio.clase)
        assertNull(limpio.valorAl)
        assertNull(limpio.deudaId)

        assertEquals("Escribe cuánto vale", problemaDelBien(Bien(valor = 0L)))
        assertEquals("Valor fuera de rango — revisa el monto", problemaDelBien(Bien(valor = MAX_VALOR_DE_BIEN_COP + 1)))
        assertEquals("La fecha del valor no es válida", problemaDelBien(Bien(valor = 1L, valorAl = "28/08/2026")))
        assertNull(problemaDelBien(Bien(valor = 1_411_903_920L, valorAl = "2026-08-28")))
    }

    @Test
    fun sin_bienes_el_neto_es_exactamente_el_de_antes() {
        // tuPlata + condicionado SON los activos que sumaba `assetsDebtsNet`: ninguna cifra vieja
        // cambia por la regla nueva.
        val cuentas = listOf(nu, skandia) + deudas
        val activosDeAntes = cuentas.filter { !esCuentaDeDeuda(it.type) }.sumOf { it.estimatedTotalCop ?: it.balance }
        val deudasDeAntes = cuentas.filter { esCuentaDeDeuda(it.type) }.sumOf { it.estimatedTotalCop ?: it.balance }
        assertEquals(activosDeAntes - deudasDeAntes, patrimonioDe(cuentas).neto)
        assertTrue(patrimonioDe(cuentas).bienes == 0L)
    }
}
