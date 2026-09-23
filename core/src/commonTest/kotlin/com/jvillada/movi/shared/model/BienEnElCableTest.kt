package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Lo que ve un APK viejo con la casa cargada.** Es el contrato de [Account.bien], fijado.
 *
 * La restricción que mandó el diseño: el dueño tiene un APK instalado que no sabe nada de bienes,
 * y ese APK no puede ni reventar (por eso no hay un valor nuevo en [AccountType]) ni decir «Tu
 * plata $1.412 millones» (por eso el valor no viaja en `balance`). Acá se simula ese cliente con
 * una copia de `Account` tal como era antes de este campo, y se le hace la misma cuenta que hace
 * su Inicio.
 */
class BienEnElCableTest {

    /** `Account` como lo conoce el APK instalado: todo menos [Account.bien]. */
    @Serializable
    private data class CuentaDeUnApkViejo(
        val id: String,
        val name: String,
        val type: AccountType,
        val balance: Long,
        val currency: String = "COP",
        val balancesByCurrency: Map<String, Long> = emptyMap(),
        val estimatedTotalCop: Long? = null,
        val condicionadaA: String? = null,
        val lastEditedAt: Long? = null,
        val lastAdjustmentAt: Long? = null,
        val firstEventAt: Long? = null,
    )

    /** Como serializa el server: sin defaults (ver `configureSerialization`). */
    private val delServer = Json { encodeDefaults = false }

    /** Como lee cualquiera de los tres `Platform`. */
    private val delCliente = Json { ignoreUnknownKeys = true }

    /** Lo que el server manda por la casa: INVESTMENT, saldo 0, el valor solo en `bien`. */
    private val casa = Account(
        "acc-casa", "Casa Almendros", AccountType.INVESTMENT, 0L,
        bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = "2026-08-28", deudaId = "acc-1254"),
    )
    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L)
    private val skandia = Account("acc-skandia", "Skandia", AccountType.INVESTMENT, 116_200_000L, condicionadaA = "Vivienda")
    private val hipoteca = Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 2_191_000_000L)

    private fun cableDe(cuentas: List<Account>): String =
        delServer.encodeToString(ListSerializer(Account.serializer()), cuentas)

    @Test
    fun el_APK_viejo_lee_la_lista_entera_sin_reventar_y_la_casa_le_llega_como_una_inversion_en_cero() {
        val viejas = delCliente.decodeFromString(
            ListSerializer(CuentaDeUnApkViejo.serializer()),
            cableDe(listOf(casa, hipoteca, nu, skandia)),
        )

        val casaVieja = viejas.first { it.id == "acc-casa" }
        assertEquals(AccountType.INVESTMENT, casaVieja.type)
        assertEquals(0L, casaVieja.balance)
        assertNull(casaVieja.estimatedTotalCop)
        assertTrue(casaVieja.balancesByCurrency.isEmpty())
        assertNull(casaVieja.condicionadaA)
    }

    @Test
    fun su_Tu_plata_y_su_patrimonio_no_cambian_por_la_casa() {
        val cuentas = listOf(casa, hipoteca, nu, skandia)
        val viejas = delCliente.decodeFromString(ListSerializer(CuentaDeUnApkViejo.serializer()), cableDe(cuentas))

        // La cuenta que hace el Inicio de ese APK: ni deuda ni condicionada, con `estimatedTotalCop ?: balance`.
        fun valor(c: CuentaDeUnApkViejo) = c.estimatedTotalCop ?: c.balance
        val esDeuda = { c: CuentaDeUnApkViejo -> c.type == AccountType.LOAN || c.type == AccountType.CREDIT_CARD }
        val tuPlataVieja = viejas.filter { !esDeuda(it) && it.condicionadaA.isNullOrBlank() }.sumOf(::valor)
        val netoViejo = viejas.filter { !esDeuda(it) }.sumOf(::valor) - viejas.filter(esDeuda).sumOf(::valor)

        assertEquals(558_350L, tuPlataVieja, "nunca «Tu plata $1.412 millones»")
        // La media foto de antes (sin la casa), no una foto falsa.
        assertEquals(patrimonioDe(listOf(hipoteca, nu, skandia)).neto, netoViejo)
    }

    @Test
    fun el_cliente_nuevo_lee_el_bien_y_lo_cuenta_donde_va() {
        val nuevas = delCliente.decodeFromString(ListSerializer(Account.serializer()), cableDe(listOf(casa, nu)))

        assertEquals(casa.bien, nuevas.first { it.id == "acc-casa" }.bien)
        assertEquals(1_411_903_920L, patrimonioDe(nuevas).bienes)
        assertEquals(558_350L, patrimonioDe(nuevas).tuPlata)
    }

    @Test
    fun una_cuenta_que_no_es_bien_sale_sin_la_clave_igual_que_antes() {
        val json = delServer.encodeToString(Account.serializer(), nu)

        assertTrue("\"bien\"" !in json, "un null no viaja: el reenvío de un APK viejo y el de uno nuevo se ven igual\n$json")
        assertNull(delCliente.decodeFromString(Account.serializer(), json).bien)
    }

    @Test
    fun una_clase_que_esta_version_no_conoce_se_lee_como_Otro_no_revienta() {
        val json = """{"id":"a","name":"Vaca","type":"INVESTMENT","balance":0,"bien":{"clase":"SEMOVIENTE","valor":3000000}}"""
        val cuenta = delCliente.decodeFromString(Account.serializer(), json)

        assertEquals(ClaseDeBien.OTRO, claseDeBien(cuenta.bien!!.clase))
        assertEquals(3_000_000L, patrimonioDe(listOf(cuenta)).bienes)
    }
}
