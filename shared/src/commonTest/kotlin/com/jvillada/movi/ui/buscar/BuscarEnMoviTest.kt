package com.jvillada.movi.ui.buscar

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import com.jvillada.movi.ui.quickadd.hacerElAtajo
import com.jvillada.movi.ui.quickadd.pantallaDelAtajo
import com.jvillada.movi.ui.quickadd.pestanaInicialDeAgregar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Ola 5 · Buscar en toda la app, sin dibujo
 *
 * El monto exacto, sin tildes ni mayúsculas, los grupos y los chips. Y de paso los atajos del «+»,
 * que son la otra mitad de «sin abrir la app».
 */
class BuscarEnMoviTest {

    private val periodo = RangoDelPeriodo(desde = 1_000_000L, hasta = 2_000_000L)

    private fun ev(
        id: String,
        descripcion: String,
        monto: Long,
        categoria: String = "Comida",
        tipo: TransactionType = TransactionType.EXPENSE,
        cuenta: String = "a1",
        cuando: Long = 1_500_000L,
        comercio: String? = null,
        traspaso: String? = null,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, category = categoria, description = descripcion,
        merchant = comercio, timestamp = cuando, transferId = traspaso,
    )

    private val datos = DatosDeBusqueda(
        eventos = listOf(
            ev("r1", "Rappi", 27_500, comercio = "RAPPI"),
            ev("r2", "Rappi", 41_000, comercio = "RAPPI", cuando = 1_600_000L),
            ev("r3", "Rappi", 30_000, comercio = "RAPPI", cuando = 500_000L), // período anterior
            ev("e1", "Compra en Éxito", 80_000, categoria = "Mercado", cuenta = "a2"),
            ev("s1", "Sueldo", 9_000_000, categoria = "Salario", tipo = TransactionType.INCOME),
            ev("t1", "Traspaso a Nu", 500_000, categoria = TRANSFER_CATEGORY, traspaso = "tr1"),
            ev("t2", "Traspaso a Nu", 500_000, categoria = TRANSFER_CATEGORY, tipo = TransactionType.INCOME, cuenta = "a2", traspaso = "tr1"),
        ),
        cuentas = listOf(
            Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000L),
            Account("a2", "Nu", AccountType.SAVINGS, 2_000_000L),
        ),
        destinos = listOf(DestinoConocido(id = "d1", nombre = "Caro", numero = "31973270756")),
        categorias = listOf("Comida", "Mercado", "Colegio"),
    )

    private fun grupo(grupos: List<GrupoDeResultados>, tipo: TipoDeResultado) = grupos.firstOrNull { it.tipo == tipo }?.resultados.orEmpty()

    private fun idsDeMovimientos(grupos: List<GrupoDeResultados>) =
        grupo(grupos, TipoDeResultado.MOVIMIENTO).map { (it as ResultadoDeBusqueda.Movimiento).evento.id }

    @Test
    fun `un numero busca el monto exacto, con o sin signo y puntos`() {
        assertEquals(listOf("r1"), idsDeMovimientos(buscarEnMovi("27500", datos)))
        assertEquals(listOf("r1"), idsDeMovimientos(buscarEnMovi("\$27.500", datos)))
        assertEquals(27_500L, montoDeLaConsulta("27,500"))
        assertNull(montoDeLaConsulta("7-Eleven"))
        assertNull(montoDeLaConsulta("rappi"))
    }

    @Test
    fun `sin tildes ni mayusculas`() {
        assertEquals(listOf("e1"), idsDeMovimientos(buscarEnMovi("EXITO", datos)))
        assertEquals(listOf("e1"), idsDeMovimientos(buscarEnMovi("éxito", datos)))
        assertTrue(grupo(buscarEnMovi("CARO", datos), TipoDeResultado.PERSONA).isNotEmpty())
    }

    @Test
    fun `en blanco no busca nada`() {
        assertTrue(buscarEnMovi("   ", datos).isEmpty())
    }

    @Test
    fun `los movimientos salen del mas reciente al mas viejo y un traspaso una sola vez`() {
        assertEquals(listOf("r2", "r1", "r3"), idsDeMovimientos(buscarEnMovi("rappi", datos)))
        assertEquals(1, idsDeMovimientos(buscarEnMovi("traspaso", datos)).size)
    }

    @Test
    fun `el comercio se agrupa con cuantas veces y cuanto en el periodo, y lleva a Movimientos con la busqueda`() {
        val comercio = grupo(buscarEnMovi("rappi", datos, periodo = periodo), TipoDeResultado.COMERCIO).single() as ResultadoDeBusqueda.Comercio
        assertEquals(2, comercio.enElPeriodo)
        assertEquals(68_500L, comercio.totalDelPeriodo)
        assertEquals(Screen.Transactions(consultaInicial = "RAPPI"), comercio.destino)
    }

    @Test
    fun `los chips filtran por tipo, por periodo y por cuenta`() {
        assertEquals(listOf("s1"), idsDeMovimientos(buscarEnMovi("sueldo", datos, FiltrosDeBusqueda(tipo = FiltroDeTipo.INGRESOS))))
        assertTrue(idsDeMovimientos(buscarEnMovi("sueldo", datos, FiltrosDeBusqueda(tipo = FiltroDeTipo.GASTOS))).isEmpty())
        assertEquals(
            listOf("r2", "r1"),
            idsDeMovimientos(buscarEnMovi("rappi", datos, FiltrosDeBusqueda(soloEstePeriodo = true), periodo)),
        )
        assertEquals(listOf("e1"), idsDeMovimientos(buscarEnMovi("e", datos, FiltrosDeBusqueda(tipo = FiltroDeTipo.GASTOS, cuentaId = "a2"))))
    }

    @Test
    fun `categorias, cuentas y personas por nombre, cada una a su lugar`() {
        val colegio = grupo(buscarEnMovi("coleg", datos), TipoDeResultado.CATEGORIA).single()
        assertEquals(Screen.Categorias, colegio.destino)
        val nu = grupo(buscarEnMovi("nu", datos), TipoDeResultado.CUENTA).single()
        assertEquals(Screen.AccountDetail("a2", com.jvillada.movi.shared.model.AccountGroup.DINERO), nu.destino)
        val caro = grupo(buscarEnMovi("caro", datos), TipoDeResultado.PERSONA).single()
        assertEquals(Screen.Destinos(abrir = "d1"), caro.destino)
    }

    @Test
    fun `las pantallas y los ajustes se encuentran por su nombre o por otra palabra`() {
        fun pantallas(q: String) = grupo(buscarEnMovi(q, datos), TipoDeResultado.PANTALLA).map { it.destino }
        assertTrue(Screen.CapturaDelBanco in pantallas("captura"))
        assertTrue(Screen.Categorias in pantallas("categorias"))
        assertTrue(Screen.BorrarLaCuenta in pantallas("borrar mi cuenta"))
        assertTrue(Screen.Compartir in pantallas("Compartir"))
        // Vuelta 38: Perfil y Ajustes viven en «Tú»; los nombres viejos lo siguen encontrando.
        assertTrue(Screen.Tu in pantallas("huella"), "Perfil se mudó dentro de Tú")
        assertTrue(Screen.Tu in pantallas("perfil"))
        assertTrue(Screen.Tu in pantallas("ajustes"))
        assertTrue(Screen.Transactions() in pantallas("movimientos"))
        assertTrue(Screen.Plan() in pantallas("plan"))
        assertTrue(Screen.Espacios in pantallas("espacios"))
        // Un número no es el nombre de ninguna pantalla.
        assertTrue(pantallas("27500").isEmpty())
    }

    @Test
    fun `los grupos salen en orden y sin grupos vacios`() {
        val tipos = buscarEnMovi("comida", datos).map { it.tipo }
        assertEquals(listOf(TipoDeResultado.MOVIMIENTO, TipoDeResultado.CATEGORIA), tipos)
    }

    // ── Los atajos del «+» ──────────────────────────────────────────────────

    @Test
    fun `cada atajo abre Agregar en su pestana, y Subir comprobante no es la hoja`() {
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.GASTO), pantallaDelAtajo(AtajoDeAgregar.GASTO))
        assertEquals(PestanaDeAgregar.INGRESO, pantallaDelAtajo(AtajoDeAgregar.INGRESO)?.presetPestana)
        assertEquals(true, pantallaDelAtajo(AtajoDeAgregar.INGRESO)?.presetEsIngreso)
        assertEquals(PestanaDeAgregar.TRASPASO, pantallaDelAtajo(AtajoDeAgregar.TRASPASO)?.presetPestana)
        assertEquals(PestanaDeAgregar.PAGO_DE_TARJETA, pantallaDelAtajo(AtajoDeAgregar.PAGO_DE_TARJETA)?.presetPestana)
        assertNull(pantallaDelAtajo(AtajoDeAgregar.SUBIR_COMPROBANTE))
        assertNull(pantallaDelAtajo(AtajoDeAgregar.REPARTIR), "Repartir abre la hoja del espacio, no Agregar")
    }

    /**
     * Vuelta de diseño 26: las cuatro acciones que salieron de la cuadrícula del Inicio (Traspaso,
     * Pagar tarjeta, Repartir, Comprobante) están en el menú del «+», cada una una vez, y hacen lo de
     * antes.
     */
    @Test
    fun `el menu del mas tiene las cuatro que salieron de Hoy, sin repetir, y cada una hace lo suyo`() {
        assertEquals(
            listOf("Gasto", "Ingreso", "Traspaso", "Pago de tarjeta", "Repartir", "Subir comprobante"),
            AtajoDeAgregar.entries.map { it.rotulo },
        )
        val hecho = mutableListOf<String>()
        AtajoDeAgregar.entries.forEach { atajo ->
            hacerElAtajo(
                atajo,
                navegar = { hecho += "ir:$it" },
                repartir = { hecho += "repartir" },
                elegirComprobante = { hecho += "comprobante" },
            )
        }
        assertEquals(
            listOf(
                "ir:${Screen.QuickAdd(presetPestana = PestanaDeAgregar.GASTO)}",
                "ir:${Screen.QuickAdd(presetEsIngreso = true, presetPestana = PestanaDeAgregar.INGRESO)}",
                "ir:${Screen.QuickAdd(presetPestana = PestanaDeAgregar.TRASPASO)}",
                "ir:${Screen.QuickAdd(presetPestana = PestanaDeAgregar.PAGO_DE_TARJETA)}",
                "repartir",
                "comprobante",
            ),
            hecho,
        )
    }

    @Test
    fun `la pestana inicial de Agregar respeta el atajo y, sin el, lo de siempre`() {
        assertEquals(2, pestanaInicialDeAgregar(PestanaDeAgregar.TRASPASO, presetDeudaId = null, presetEsIngreso = false))
        assertEquals(3, pestanaInicialDeAgregar(PestanaDeAgregar.PAGO_DE_TARJETA, presetDeudaId = null, presetEsIngreso = false))
        assertEquals(3, pestanaInicialDeAgregar(null, presetDeudaId = "cr1", presetEsIngreso = false))
        assertEquals(1, pestanaInicialDeAgregar(null, presetDeudaId = null, presetEsIngreso = true))
        assertEquals(0, pestanaInicialDeAgregar(null, presetDeudaId = null, presetEsIngreso = false))
    }

    /** Ola R4: «Personas y comercios» vive en Espacios (vuelta 31: salió de Ajustes), no colgado de Movimientos. */
    @Test
    fun `Personas y comercios ya no se nombra desde Movimientos`() {
        val personas = PANTALLAS_BUSCABLES.single { it.destino == com.jvillada.movi.ui.Screen.Destinos() }
        kotlin.test.assertEquals("Personas", personas.donde)
    }
    /** Vuelta 35: el título de un movimiento es el mismo que en Movimientos, no la categoría repetida. */
    @Test
    fun `un movimiento cuya descripcion es la categoria se titula con el comercio, como en Movimientos`() {
        val mov = ResultadoDeBusqueda.Movimiento(ev("x1", "Mercado", 50_000, categoria = "Mercado", comercio = "EXITO SUBA"))
        kotlin.test.assertEquals("Éxito Suba", mov.titulo)
        kotlin.test.assertEquals("Uber", ResultadoDeBusqueda.Movimiento(ev("x2", "UBER", 21_400, categoria = "Transporte")).titulo)
    }
}
