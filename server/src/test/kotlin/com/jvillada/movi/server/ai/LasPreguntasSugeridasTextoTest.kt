package com.jvillada.movi.server.ai

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.PequenosQueSuman
import com.jvillada.movi.shared.model.PlataDelDisponible
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.disponibleAlDia
import com.jvillada.movi.shared.model.pesosDelCierre
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Lo que dicen las tres respuestas, palabra por palabra
 *
 * Las funciones puras de `LasPreguntasSugeridas.kt` con un período armado a mano (octubre de 2026,
 * corte 1, hoy el 13): cada cifra se puede seguir con lápiz. Que esas cifras sean las de las
 * pantallas lo fija `LasPreguntasSugeridasTest`, por la ruta.
 */
class LasPreguntasSugeridasTextoTest {

    private val arriendo = PagoDelPeriodo(ruleId = "r-arriendo", nombre = "Arriendo", monto = 1_500_000, pagado = true, diasParaVencer = -8)
    private val celular = PagoDelPeriodo(ruleId = "r-celular", nombre = "Celular", monto = 53_000, pagado = false, diasParaVencer = 5)
    private val gimnasio = PagoDelPeriodo(ruleId = "r-gimnasio", nombre = "Gimnasio", monto = 120_000, pagado = false, diasParaVencer = -2)
    private val sueldo = PagoDelPeriodo(ruleId = "r-sueldo", nombre = "Sueldo", monto = 3_000_000, pagado = true, diasParaVencer = -12, esIngreso = true)
    private val pagos = listOf(arriendo, celular, gimnasio, sueldo)

    /**
     * Tenía $2.000.000 el 1, entraron $3.000.000; los fijos son $1.673.000 → disponible $3.327.000,
     * meta diaria $3.327.000 / 31 = $107.322. Gastó $450.000 de variable → le quedan $2.877.000, y
     * del 13 al 31 son 19 días contando hoy: $2.877.000 / 19 = $151.421 por día (lo que dice el
     * héroe de Hoy, no la meta diaria del período entero).
     */
    private val disponible = disponibleAlDia(
        ingresosRecibidos = 3_000_000,
        pagos = pagos,
        gastoVariablePorDia = mapOf("2026-10-05" to 400_000L, "2026-10-13" to 50_000L),
        desde = "2026-10-01",
        hasta = "2026-10-31",
        hoy = "2026-10-13",
        plata = PlataDelDisponible(saldoAlInicio = 2_000_000, entradas = 3_000_000, guardado = 0),
    )

    private val datos = DatosDeLasSugeridas(
        rango = "1 de octubre al 31 de octubre",
        entro = 3_000_000,
        gastoPorCategoria = mapOf("Vivienda" to 1_500_000L, "Comida" to 300_000L, "Transporte" to 150_000L),
        anterior = mapOf("Vivienda" to 1_500_000L, "Comida" to 100_000L, "Transporte" to 160_000L),
        diasComparados = 13,
        disponible = disponible,
        pagos = pagos,
        pequenos = listOf(PequenosQueSuman("Juan Valdez", porComercio = true, veces = 27, total = 186_000)),
    )

    @Test
    fun `como voy`() {
        assertEquals(
            """
            Te quedan ${'$'}2.877.000 para 19 días · ${'$'}151.421 por día.

            Este período (1 de octubre al 31 de octubre) entró ${'$'}3.000.000 y salió ${'$'}1.950.000.

            Contra los mismos 13 días del período pasado, gastaste ${'$'}190.000 más (entonces llevabas ${'$'}1.760.000).

            Te faltan 2 pagos del período por ${'$'}173.000:
            - Gimnasio ${'$'}120.000 · venció hace 2 días
            - Celular ${'$'}53.000 · vence en 5 días
            """.trimIndent(),
            respuestaSinModelo(PreguntaSugerida.COMO_VOY, datos),
        )
    }

    /**
     * Vuelta de diseño 3: «¿Cómo voy?» dice lo mismo que el héroe de Hoy. Por encima del ritmo, el
     * héroe dice «para N días · vas $X por encima de lo previsto a hoy» (sin cifra por día); el último
     * día, «para hoy». Nunca «Tu meta diaria», que era la meta del período entero.
     */
    @Test
    fun `como voy por encima del ritmo y el ultimo dia dice lo que dice el heroe`() {
        val rapido = disponibleAlDia(
            ingresosRecibidos = 3_000_000,
            pagos = pagos,
            gastoVariablePorDia = mapOf("2026-10-05" to 2_450_000L, "2026-10-13" to 50_000L),
            desde = "2026-10-01",
            hasta = "2026-10-31",
            hoy = "2026-10-13",
            plata = PlataDelDisponible(saldoAlInicio = 2_000_000, entradas = 3_000_000, guardado = 0),
        )!!
        assertTrue(rapido.periodo.contraElRitmo > 0L)
        val primera = respuestaSinModelo(PreguntaSugerida.COMO_VOY, datos.copy(disponible = rapido)).lines().first()
        assertEquals(
            "Te quedan ${pesosDelCierre(rapido.periodo.teQuedan)} para 19 días · " +
                "vas ${pesosDelCierre(rapido.periodo.contraElRitmo)} por encima de lo previsto a hoy.",
            primera,
        )

        val ultimoDia = disponibleAlDia(
            ingresosRecibidos = 3_000_000,
            pagos = pagos,
            gastoVariablePorDia = mapOf("2026-10-05" to 400_000L),
            desde = "2026-10-01",
            hasta = "2026-10-31",
            hoy = "2026-10-31",
            plata = PlataDelDisponible(saldoAlInicio = 2_000_000, entradas = 3_000_000, guardado = 0),
        )!!
        val ultima = respuestaSinModelo(PreguntaSugerida.COMO_VOY, datos.copy(disponible = ultimoDia)).lines().first()
        assertEquals("Te quedan ${pesosDelCierre(ultimoDia.periodo.teQuedan)} para hoy.", ultima)
    }

    @Test
    fun `como voy sin disponible ni pagos ni periodo pasado no inventa nada`() {
        val vacio = DatosDeLasSugeridas(rango = "1 de octubre al 31 de octubre", entro = 0, gastoPorCategoria = emptyMap())
        assertEquals(
            """
            Todavía no puedo decirte cuánto te queda: anota tu sueldo o el saldo de tus cuentas y Movi lo calcula.

            Este período (1 de octubre al 31 de octubre) entró ${'$'}0 y salió ${'$'}0.
            """.trimIndent(),
            respuestaSinModelo(PreguntaSugerida.COMO_VOY, vacio),
        )
    }

    @Test
    fun `como voy con todo pagado lo dice`() {
        val pagado = datos.copy(pagos = listOf(arriendo, sueldo))
        assertTrue(respuestaSinModelo(PreguntaSugerida.COMO_VOY, pagado).endsWith("Ya salió el único pago del período."))
    }

    @Test
    fun `en que se va`() {
        assertEquals(
            """
            Este período (1 de octubre al 31 de octubre) salieron ${'$'}1.950.000. Lo que más pesa:
            1. Vivienda ${'$'}1.500.000 · casi igual que el período pasado
            2. Comida ${'$'}300.000 · ${'$'}200.000 más que el período pasado
            3. Transporte ${'$'}150.000 · ${'$'}10.000 menos que el período pasado

            Comparo contra los primeros 13 días del período pasado, los mismos que lleva este.

            Pequeños que suman:
            - 27 compras en Juan Valdez: ${'$'}186.000
            """.trimIndent(),
            respuestaSinModelo(PreguntaSugerida.EN_QUE_SE_VA, datos),
        )
    }

    @Test
    fun `en que se va sin periodo pasado no compara y nombra cinco como mucho`() {
        val muchas = datos.copy(
            gastoPorCategoria = (1..7).associate { "Categoría $it" to it * 10_000L },
            anterior = null,
            diasComparados = null,
            pequenos = emptyList(),
        )
        val texto = respuestaSinModelo(PreguntaSugerida.EN_QUE_SE_VA, muchas)
        assertTrue("1. Categoría 7 \$70.000\n" in texto, texto)
        assertTrue("5. Categoría 3 \$30.000" in texto && "Categoría 2" !in texto, texto)
        assertTrue("período pasado" !in texto && "Pequeños" !in texto, texto)
    }

    @Test
    fun `que revisar va de lo urgente a lo que puede esperar`() {
        val conTodo = datos.copy(
            presupuestos = listOf("Comida" to 250_000L),
            porRevisar = 4,
            cuadresPorAviso = listOf(
                PropuestaDeCuadrePorAviso(
                    avisoId = "sms-1", cuentaId = "acc-nu", cuentaNombre = "Nu", origen = "Notificación · Nu",
                    saldoDelBanco = 138_407, saldoEnMovi = 138_361, momento = 1L,
                ),
            ),
            debitos = listOf(
                DebitoAutomaticoPorConfirmar(
                    ruleId = "credit_acc-9695", periodo = "2026-10", origen = OrigenDelDebito.CUOTA_DE_CREDITO,
                    nombre = "Cuota Libre inversión 9695", monto = 600_000, vence = "2026-10-10", cuentaId = "acc-nu",
                    cuentaNombre = "Nu", categoria = "Cuota de crédito", pataDelDineroId = "ev_deb_1",
                ),
            ),
            anomalias = listOf(
                Anomalia(
                    huella = "dup-rappi", tipo = TipoDeAnomalia.COBRO_DUPLICADO, titulo = "¿Cobro duplicado en Rappi?",
                    detalle = "Dos cobros de \$45.000 el 10 de octubre.", evidencia = emptyList(),
                ),
            ),
            sinCuadrar = listOf(Account(id = "acc-fidu", name = "Fiducuenta", type = AccountType.SAVINGS, balance = 0)),
        )
        assertEquals(
            """
            Revisa esto, en este orden:
            1. «Gimnasio» venció y no está marcado. Si ya lo pagaste, anótalo y Movi lo tilda solo en Plan.
            2. Te pasaste del presupuesto de Comida por ${'$'}50.000. Puedes ajustar el límite o frenar el gasto en Presupuestos.
            3. Tienes 4 pagos del banco por revisar. Hasta confirmarlos no cuentan en el gasto del período: ábrelos en Por revisar.
            4. El saldo de Nu no coincide con el del banco. Te lo dijo el banco en un aviso: en Cuadre de saldos Movi anota la diferencia con un toque.
            5. ¿Se cobró la cuota de Libre inversión 9695? El banco lo cobra solo y no avisa: confírmalo en Por revisar para que cuente.
            6. ¿Cobro duplicado en Rappi? Dos cobros de ${'$'}45.000 el 10 de octubre.

            Y 1 cosa más en «Para revisar», en Hoy.
            """.trimIndent(),
            respuestaSinModelo(PreguntaSugerida.QUE_REVISAR, conTodo),
        )
    }

    @Test
    fun `que revisar avisa cuando el disponible se paso`() {
        val pasado = datos.copy(
            disponible = disponibleAlDia(
                ingresosRecibidos = 3_000_000,
                pagos = pagos,
                gastoVariablePorDia = mapOf("2026-10-05" to 4_000_000L),
                desde = "2026-10-01",
                hasta = "2026-10-31",
                hoy = "2026-10-13",
                plata = PlataDelDisponible(saldoAlInicio = 2_000_000, entradas = 3_000_000, guardado = 0),
            ),
            pagos = listOf(arriendo, celular, sueldo),
        )
        val texto = respuestaSinModelo(PreguntaSugerida.QUE_REVISAR, pasado)
        // $3.327.000 de disponible − $4.000.000 gastados = $673.000 de más.
        assertTrue(texto.startsWith("Revisa esto:\n1. Te pasaste del disponible del período por \$673.000."), texto)
        assertTrue(respuestaSinModelo(PreguntaSugerida.COMO_VOY, pasado).startsWith("Te pasaste del disponible del período por \$673.000."))
    }

    @Test
    fun `que revisar sin nada lo dice`() {
        val tranquilo = datos.copy(pagos = listOf(arriendo, celular, sueldo))
        assertEquals(
            "No veo nada urgente: no hay pagos vencidos, nada por revisar del banco, nada fuera de lo normal y tus cuentas están cuadradas.",
            respuestaSinModelo(PreguntaSugerida.QUE_REVISAR, tranquilo),
        )
    }

    @Test
    fun `ninguna respuesta habla de vos ni de sus calculos`() {
        val voseo = Regex("""\b(tenés|podés|querés|sabés|mirá|revisá|fijate|vos)\b""", RegexOption.IGNORE_CASE)
        PreguntaSugerida.entries.forEach { p ->
            val texto = respuestaSinModelo(p, datos.copy(porRevisar = 2, presupuestos = listOf("Comida" to 1L)))
            assertTrue(voseo.find(texto) == null, texto)
            assertTrue("según mis cálculos" !in texto.lowercase(), texto)
        }
    }
}
