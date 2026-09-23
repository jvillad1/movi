package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_INMUEBLE
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_VEHICULO
import com.jvillada.movi.shared.model.deudaDelBien
import com.jvillada.movi.ui.accounts.deudasParaAsociar
import com.jvillada.movi.ui.accounts.lineaDeLoQueEsTuyo
import com.jvillada.movi.ui.accounts.subtituloDelBien
import com.jvillada.movi.ui.accounts.textoDelAvaluo
import com.jvillada.movi.ui.components.assetsDebtsNet
import com.jvillada.movi.ui.cuadre.sePuedeCuadrar
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Lo que el Inicio y la pantalla Cuentas dicen con la casa cargada**, con los números del dueño
 * al 23-sep: $558.350 de plata, $116,2M condicionados a vivienda, $2.191M de deudas y la casa de
 * Almendros de Zúñiga en $1.411.903.920.
 *
 * La regla vive en `:core` (`patrimonioDe`, con sus propias pruebas); acá se fija que las dos
 * pantallas la usen y que la línea que explica el patrimonio nombre los bienes — sin ellos la
 * resta no cierra, que es el único trabajo de esa línea.
 */
class BienesEnElPatrimonioTest {

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L)
    private val skandia = Account("acc-skandia", "Skandia", AccountType.INVESTMENT, 116_200_000L, condicionadaA = "Vivienda")
    private val hipoteca1254 = Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 1_030_600_000L)
    private val otrasDeudas = Account("acc-resto", "Otras deudas", AccountType.LOAN, 1_160_400_000L)
    private val tarjeta = Account("acc-amex", "AMEX", AccountType.CREDIT_CARD, 0L)
    private val casa = Account(
        "acc-casa", "Casa Almendros", AccountType.INVESTMENT, 0L,
        bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = "2026-08-28", deudaId = "acc-1254"),
    )
    private val hoy = LocalDate(2026, 9, 23)

    private val delDueno = listOf(nu, skandia, casa, hipoteca1254, otrasDeudas)

    @Test
    fun el_hero_suma_la_casa_al_patrimonio_y_no_a_Tu_plata() {
        val b = heroBalance(delDueno)

        assertEquals(558_350L, b.tuPlata)
        assertEquals(116_200_000L, b.condicionado)
        assertEquals(1_411_903_920L, b.bienes)
        assertEquals(2_191_000_000L, b.deudas)
        assertEquals(-662_337_730L, b.patrimonio)
        assertEquals(b.tuPlata + b.condicionado + b.bienes - b.deudas, b.patrimonio)
    }

    @Test
    fun la_linea_del_patrimonio_nombra_los_bienes() {
        assertEquals(
            "Tu plata más $116,2M solo para Vivienda, más $1.411,9M en bienes, menos $2.191M en deudas",
            patrimonioExplicacion(heroBalance(delDueno)),
        )
    }

    @Test
    fun con_solo_un_bien_el_patrimonio_aparece_y_se_explica() {
        // Alguien sin deudas ni plata condicionada cuya única cosa es el carro: el patrimonio
        // es distinto de «Tu plata» y la línea tiene que aparecer.
        val carro = casa.copy(id = "acc-carro", name = "Carro", bien = Bien(CLASE_DE_BIEN_VEHICULO, 80_000_000L))
        val b = heroBalance(listOf(nu, carro))

        assertTrue(b.muestraPatrimonio)
        assertEquals("Tu plata más $80M en bienes", patrimonioExplicacion(b))
    }

    @Test
    fun la_casa_no_aparece_en_el_desglose_de_Tu_plata_ni_se_cuenta_como_cuenta() {
        assertEquals(listOf("Nu"), cuentasDelHero(listOf(nu, casa))!!.map { it.nombre })

        val fila = quickLinkFigure("accounts", DashboardData(accounts = listOf(nu, casa)))
        assertEquals("1 cuenta", fila.sub)
        assertNull(quickLinkFigure("investments", DashboardData(accounts = listOf(casa))).value)
    }

    @Test
    fun la_pantalla_Cuentas_y_el_Inicio_dan_el_mismo_neto() {
        val (activos, deudas, neto) = assetsDebtsNet(delDueno)

        assertEquals(558_350L + 116_200_000L + 1_411_903_920L, activos)
        assertEquals(2_191_000_000L, deudas)
        assertEquals(heroBalance(delDueno).patrimonio, neto)
    }

    @Test
    fun un_bien_no_se_cuadra() {
        assertFalse(sePuedeCuadrar(casa))
        assertTrue(sePuedeCuadrar(skandia))
    }

    @Test
    fun el_avaluo_se_dice_con_su_fecha() {
        assertEquals("avalúo del 28 de agosto", textoDelAvaluo("2026-08-28", hoy))
        assertEquals("avalúo del 28 de agosto de 2025", textoDelAvaluo("2025-08-28", hoy))
        assertEquals("avalúo de hoy", textoDelAvaluo("2026-09-23", hoy))
        assertNull(textoDelAvaluo(null, hoy))
        assertNull(textoDelAvaluo("28/08/2026", hoy), "una fecha que no se puede leer no se inventa")

        assertEquals("Inmueble · avalúo del 28 de agosto", subtituloDelBien(casa, hoy))
        assertEquals("Inmueble", subtituloDelBien(casa.copy(bien = casa.bien!!.copy(valorAl = null)), hoy))
    }

    @Test
    fun lo_que_es_tuyo_de_verdad_en_una_linea() {
        val d = deudaDelBien(casa, delDueno)!!
        assertEquals("Debes $1.030,6M en Hipoteca 1254 · tuyo $381,3M", lineaDeLoQueEsTuyo(d))

        // Un carro que vale menos de lo que se debe: la frase no escribe «tuyo −$5M».
        val carro = casa.copy(bien = Bien(CLASE_DE_BIEN_VEHICULO, 80_000_000L, deudaId = "acc-8761"))
        val credito = Account("acc-8761", "Vehículo 8761", AccountType.LOAN, 85_000_000L)
        assertEquals(
            "Debes $85M en Vehículo 8761 · debes $5M más de lo que vale",
            lineaDeLoQueEsTuyo(deudaDelBien(carro, listOf(carro, credito))!!),
        )
    }

    @Test
    fun solo_se_asocian_deudas_y_los_prestamos_van_primero() {
        assertEquals(
            listOf("acc-1254", "acc-resto", "acc-amex"),
            deudasParaAsociar(listOf(tarjeta, nu, casa, hipoteca1254, otrasDeudas)).map { it.id },
        )
    }
}
