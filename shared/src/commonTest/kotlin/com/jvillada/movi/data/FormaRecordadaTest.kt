package com.jvillada.movi.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La forma recordada, sobre un almacén de mentira (un mapa): acá `Settings()` no existe, y lo que
 * se prueba es la lógica — la ida y vuelta, la clave por usuario, y que nada de lo que venga
 * guardado pueda tumbar una pantalla.
 */
class FormaRecordadaTest {

    private val guardado = mutableMapOf<String, String>()
    private val forma = formaEnMemoria(guardado)

    private val creditos = FormaDeCreditos(
        gruposDelResumen = listOf(2, 2, 1),
        renglonesDelAvisoAmbar = 2,
        renglonesDelAvisoRojo = 3,
        prestamos = 12,
    )
    private val categorias = FormaDeCategorias(renglonesDeLaTarjetaDeOrden = 2, filas = 23)
    private val cuentas = FormaDeCuentas(renglonesDelPatrimonio = 4, filasPorGrupo = listOf(5, 2))
    private val movimientos = FormaDeMovimientos(lineaDePeriodo = false)
    private val periodos = FormaDePeriodos(filas = 6)

    @Test
    fun `lo que se guarda vuelve igual, pantalla por pantalla`() {
        forma.guardarCreditos("u1", creditos)
        forma.guardarCategorias("u1", categorias)
        forma.guardarCuentas("u1", cuentas)
        forma.guardarMovimientos("u1", movimientos)
        forma.guardarPeriodos("u1", periodos)

        assertEquals(creditos, forma.creditos("u1"))
        assertEquals(categorias, forma.categorias("u1"))
        assertEquals(cuentas, forma.cuentas("u1"))
        assertEquals(movimientos, forma.movimientos("u1"))
        assertEquals(periodos, forma.periodos("u1"))
    }

    @Test
    fun `la ultima escritura manda`() {
        forma.guardarCreditos("u1", creditos)
        val sinAvisos = creditos.copy(renglonesDelAvisoAmbar = 0, renglonesDelAvisoRojo = 0)
        forma.guardarCreditos("u1", sinAvisos)
        assertEquals(sinAvisos, forma.creditos("u1"))
    }

    @Test
    fun `otro usuario no ve la del primero`() {
        forma.guardarCreditos("u1", creditos)
        forma.guardarCategorias("u1", categorias)
        forma.guardarCuentas("u1", cuentas)
        forma.guardarMovimientos("u1", movimientos)
        forma.guardarPeriodos("u1", periodos)

        assertNull(forma.creditos("u2"))
        assertNull(forma.categorias("u2"))
        assertNull(forma.cuentas("u2"))
        assertNull(forma.movimientos("u2"))
        assertNull(forma.periodos("u2"))
    }

    @Test
    fun `sin usuario no se lee ni se escribe nada`() {
        forma.guardarCreditos(null, creditos)
        forma.guardarCategorias("  ", categorias)
        forma.guardarCuentas(null, cuentas)
        forma.guardarMovimientos(null, movimientos)
        forma.guardarPeriodos(null, periodos)

        assertTrue(guardado.isEmpty(), "sin usuario no hay clave a la que escribir")
        assertNull(forma.creditos(null))
        assertNull(forma.categorias(null))
        assertNull(forma.cuentas(" "))
        assertNull(forma.movimientos(null))
        assertNull(forma.periodos(null))
    }

    @Test
    fun `solo guarda numeros, ni plata ni nombres`() {
        forma.guardarCreditos("u1", creditos)
        forma.guardarCategorias("u1", categorias)
        forma.guardarCuentas("u1", cuentas)

        // Lo único que puede haber en los valores: llaves, corchetes, comas, dos puntos, dígitos y
        // los nombres de los campos. Ni un signo de pesos ni un punto de miles.
        guardado.values.forEach { valor ->
            val sinCampos = valor.replace(Regex("\"[a-zA-Z]+\""), "")
            assertTrue(sinCampos.all { it in "{}[],:0123456789" }, "la forma guardó algo que no es un número: $valor")
        }
    }

    @Test
    fun `borrar se lleva las formas del usuario y deja las de otro`() {
        forma.guardarCreditos("u1", creditos)
        forma.guardarCategorias("u1", categorias)
        forma.guardarCuentas("u1", cuentas)
        forma.guardarMovimientos("u1", movimientos)
        forma.guardarPeriodos("u1", periodos)
        forma.guardarCuentas("u2", cuentas)

        forma.borrar("u1")

        assertNull(forma.creditos("u1"))
        assertNull(forma.categorias("u1"))
        assertNull(forma.cuentas("u1"))
        assertNull(forma.movimientos("u1"))
        assertNull(forma.periodos("u1"))
        assertEquals(cuentas, forma.cuentas("u2"))
    }

    @Test
    fun `algo corrupto vale nada, sin lanzar`() {
        forma.guardarCreditos("u1", creditos)
        forma.guardarCategorias("u1", categorias)
        forma.guardarCuentas("u1", cuentas)
        forma.guardarMovimientos("u1", movimientos)
        forma.guardarPeriodos("u1", periodos)
        guardado.keys.toList().forEach { guardado[it] = "{esto no es json" }

        assertNull(forma.creditos("u1"))
        assertNull(forma.categorias("u1"))
        assertNull(forma.cuentas("u1"))
        assertNull(forma.movimientos("u1"))
        assertNull(forma.periodos("u1"))
    }

    /**
     * Ola B, tarea 2 (whole-branch review, final fix wave): sin nada guardado, [FormaRecordada.movimientos]
     * devuelve `null` — no un `FormaDeMovimientos()` con su default `lineaDePeriodo = true`. La
     * pantalla lee esa diferencia (`null` = «nada recordado, reservar igual que siempre») distinto
     * de un `false` explícito («la última vez que salió bien no había línea»).
     */
    @Test
    fun `sin nada recordado, movimientos no dice ni que hay linea ni que no la hay`() {
        assertNull(forma.movimientos("u1"))
    }

    @Test
    fun `movimientos recuerda si la ultima carga tenia linea de periodo o no`() {
        forma.guardarMovimientos("u1", FormaDeMovimientos(lineaDePeriodo = false))
        assertEquals(FormaDeMovimientos(lineaDePeriodo = false), forma.movimientos("u1"))

        forma.guardarMovimientos("u1", FormaDeMovimientos(lineaDePeriodo = true))
        assertEquals(FormaDeMovimientos(lineaDePeriodo = true), forma.movimientos("u1"))
    }

    @Test
    fun `un numero imposible vale nada`() {
        forma.guardarCreditos("u1", creditos)
        forma.guardarCategorias("u1", categorias)
        forma.guardarCuentas("u1", cuentas)
        val claveDeCreditos = guardado.keys.single { "creditos" in it }
        val claveDeCategorias = guardado.keys.single { "categorias" in it }
        val claveDeCuentas = guardado.keys.single { "cuentas" in it }

        guardado[claveDeCreditos] = """{"prestamos":-1}"""
        guardado[claveDeCategorias] = """{"filas":100000}"""
        guardado[claveDeCuentas] = """{"renglonesDelPatrimonio":9,"filasPorGrupo":[5,2]}"""

        assertNull(forma.creditos("u1"))
        assertNull(forma.categorias("u1"))
        assertNull(forma.cuentas("u1"))
    }

    @Test
    fun `periodos recuerda cuantas filas trajo la ultima carga que salio bien`() {
        assertNull(forma.periodos("u1"), "sin nada recordado, nada")
        forma.guardarPeriodos("u1", FormaDePeriodos(filas = 4))
        assertEquals(FormaDePeriodos(filas = 4), forma.periodos("u1"))
    }

    @Test
    fun `un campo que esta version no conoce no la pierde`() {
        forma.guardarCategorias("u1", categorias)
        val clave = guardado.keys.single()
        guardado[clave] = """{"renglonesDeLaTarjetaDeOrden":2,"filas":23,"algoDelFuturo":7}"""
        assertEquals(categorias, forma.categorias("u1"))
    }
}

/** El almacén de mentira que usan las pruebas: [almacen] hace de `Settings`. */
internal fun formaEnMemoria(almacen: MutableMap<String, String>) = FormaRecordada(
    leer = { clave -> almacen[clave] },
    escribir = { clave, valor -> if (valor == null) almacen.remove(clave) else almacen[clave] = valor },
)
