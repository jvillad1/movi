package com.jvillada.movi.ui.categorias

import com.jvillada.movi.shared.model.CATEGORY_COLOR_MAX_LENGTH
import com.jvillada.movi.shared.model.CATEGORY_ICONO_MAX_LENGTH
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.theme.COLORES_DEL_CATALOGO
import com.jvillada.movi.theme.COLOR_DE_CATEGORIA_RESPALDO
import com.jvillada.movi.theme.esColorDelCatalogo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * La regla de [aparienciaDe]: lo del dueño, después el nombre exacto, después una palabra del
 * nombre, y al final un color estable. Y que el catálogo que la sostiene no tenga huecos.
 */
class AparienciaDeCategoriaTest {

    private fun ap(icono: String, color: String) = AparienciaDeCategoria(icono, color)

    // ── La prioridad ─────────────────────────────────────────────────────────

    @Test
    fun `lo que eligio el duenno gana a la tabla, icono y color por su lado`() {
        val soloIcono = CategoryPref(icono = "futbol")
        assertEquals(ap("futbol", "naranja"), aparienciaDe("Comida", soloIcono))

        val soloColor = CategoryPref(color = "violeta")
        assertEquals(ap("comida", "violeta"), aparienciaDe("Comida", soloColor))

        val ambos = CategoryPref(icono = "cafe", color = "rojo")
        assertEquals(ap("cafe", "rojo"), aparienciaDe("Comida", ambos))
    }

    @Test
    fun `lo que eligio el duenno gana tambien sobre las palabras clave y el hash`() {
        assertEquals(ap("mascota", "celeste"), aparienciaDe("Uber", CategoryPref(icono = "mascota", color = "celeste")))
        assertEquals(ap("viajes", "lima"), aparienciaDe("Coomeva", CategoryPref(icono = "viajes", color = "lima")))
    }

    @Test
    fun `una clave elegida que esta version no conoce se ignora y sigue la regla`() {
        val deUnaVersionNueva = CategoryPref(icono = "cohete", color = "fucsia")
        assertEquals(ap("comida", "naranja"), aparienciaDe("Comida", deUnaVersionNueva))
        // En blanco tampoco cuenta: es lo que el server manda como «volver al de Movi».
        assertEquals(ap("comida", "naranja"), aparienciaDe("Comida", CategoryPref(icono = " ", color = "")))
    }

    @Test
    fun `la tabla por nombre gana a las palabras clave`() {
        // «arriendo» como palabra clave es un gasto (ámbar); «Arriendo recibido» es un ingreso.
        assertEquals(ap("arriendo", "ambar"), aparienciaDe("Arriendo del apartamento", null))
        assertEquals(ap("arriendo", "verde"), aparienciaDe("Arriendo recibido", null))
    }

    @Test
    fun `las palabras clave ganan al respaldo`() {
        assertEquals(ap("comida", "naranja"), aparienciaDe("Almuerzo con la hija", null))
        assertEquals(ap("restaurante", "naranja"), aparienciaDe("Restaurante del domingo", null))
        assertEquals(ap("taxi", "azul"), aparienciaDe("Uber", null))
        assertEquals(ap("taxi", "azul"), aparienciaDe("Taxi aeropuerto", null))
        assertEquals(ap("suscripciones", "ambar"), aparienciaDe("Netflix", null))
        assertEquals(ap("suscripciones", "ambar"), aparienciaDe("Spotify familiar", null))
        assertEquals(ap("suscripciones", "ambar"), aparienciaDe("Suscripciones", null))
        assertEquals(ap("salud", "rojo"), aparienciaDe("Médico general", null))
        assertEquals(ap("medicamentos", "rojo"), aparienciaDe("Farmacia", null))
    }

    @Test
    fun `lo especifico va antes que lo general en las palabras clave`() {
        // «cuota» sola sería un crédito; la de manejo la cobra el banco.
        assertEquals(ap("banco", "gris"), aparienciaDe("Cuota de manejo", null))
        assertEquals(ap("credito", "violeta"), aparienciaDe("Cuota del carro", null))
    }

    @Test
    fun `una palabra clave entera no atrapa una palabra que solo empieza igual`() {
        // «agua» es entera: un aguacate no es un servicio público.
        assertEquals("otros", aparienciaDe("Aguacate", null).icono)
        assertEquals(ap("agua", "ambar"), aparienciaDe("Recibo del agua", null))
    }

    // ── La tabla, con los nombres reales del dueño ───────────────────────────

    @Test
    fun `los nombres reales del duenno tienen su apariencia`() {
        val esperado = mapOf(
            "Comida" to ap("comida", "naranja"),
            "Mercado" to ap("mercado", "lima"),
            "Mercado extra" to ap("mercado", "lima"),
            "Fútbol" to ap("futbol", "verde"),
            "Estadio" to ap("estadio", "verde"),
            "Gimnasio" to ap("gimnasio", "celeste"),
            "Hija" to ap("hija", "rosa"),
            "Familia" to ap("familia", "rosa"),
            "Celular" to ap("celular", "azul"),
            "Cuota de crédito" to ap("credito", "violeta"),
            "Crédito" to ap("credito", "violeta"),
            "Pago de tarjeta" to ap("tarjeta", "violeta"),
            "Comisiones del banco" to ap("banco", "gris"),
            "Impuestos" to ap("impuestos", "gris"),
            "Salud" to ap("salud", "rojo"),
            "Transporte" to ap("transporte", "azul"),
            "Tecnología" to ap("tecnologia", "celeste"),
            "Entretenimiento" to ap("entretenimiento", "ambar"),
            "Servicios" to ap("servicios", "ambar"),
            "Vivienda" to ap("casa", "ambar"),
            "Gardenera" to ap("casa", "ambar"),
            "Educación" to ap("educacion", "azul"),
            "Ropa" to ap("ropa", "rosa"),
            "Otros" to ap("otros", "gris"),
            "Salario" to ap("salario", "verde"),
            "Nómina" to ap("salario", "verde"),
            "Freelance" to ap("trabajo", "verde"),
            "Arriendo recibido" to ap("arriendo", "verde"),
            "Inversiones" to ap("inversiones", "verde"),
            "Otros ingresos" to ap("ingreso", "verde"),
            "Ingreso" to ap("ingreso", "verde"),
            "Transferencia" to ap("ingreso", "verde"),
            "Pago de un tercero" to ap("ingreso", "verde"),
        )
        for ((nombre, apariencia) in esperado) {
            assertEquals(apariencia, aparienciaDe(nombre, null), "«$nombre»")
        }
    }

    @Test
    fun `el nombre se compara sin tildes, mayusculas ni espacios de mas`() {
        assertEquals(ap("futbol", "verde"), aparienciaDe("  FUTBOL ", null))
        assertEquals(ap("futbol", "verde"), aparienciaDe("fútbol", null))
        assertEquals(ap("credito", "violeta"), aparienciaDe("cuota  de  CREDITO", null))
        assertEquals(ap("medicamentos", "rojo"), aparienciaDe("DROGUERÍA", null))
    }

    // ── El respaldo ──────────────────────────────────────────────────────────

    @Test
    fun `un nombre que nadie reconoce cae en otros con un color del hash`() {
        val a = aparienciaDe("Coomeva", null)
        assertEquals(ICONO_DE_CATEGORIA_RESPALDO, a.icono)
        assertEquals(colorPorHash(normalizarParaBuscar("Coomeva")), a.color)
    }

    /**
     * Resultados **fijos**: si alguien cambia la fórmula, esta prueba se cae antes de que todas
     * las categorías propias de todos los dueños cambien de color de un día para otro.
     */
    @Test
    fun `el hash es estable`() {
        assertEquals("rosa", colorPorHash("coomeva"))
        assertEquals("naranja", colorPorHash("nata"))
        assertEquals("verde", colorPorHash("xyz"))
        assertEquals("ambar", colorPorHash("gardenera sur"))
        assertEquals(aparienciaDe("Coomeva", null), aparienciaDe("COOMEVA", null))
    }

    @Test
    fun `el hash cae siempre dentro de la paleta y nunca en gris`() {
        val claves = COLORES_DEL_CATALOGO.map { it.clave }.toSet()
        val usados = mutableSetOf<String>()
        for (i in 0 until 500) {
            val color = colorPorHash("categoria $i")
            assertTrue(color in claves, "«$color» no está en la paleta")
            assertTrue(color != COLOR_DE_CATEGORIA_RESPALDO, "el hash no debería dar gris")
            usados += color
        }
        // Y reparte: con 500 nombres distintos salen todos los colores que puede dar.
        assertEquals(claves.size - 1, usados.size)
    }

    // ── El catálogo ──────────────────────────────────────────────────────────

    @Test
    fun `cada icono del catalogo tiene clave valida y rotulo`() {
        val claves = ICONOS_DEL_CATALOGO.map { it.clave }
        assertEquals(claves.size, claves.toSet().size, "claves repetidas")
        assertTrue(ICONO_DE_CATEGORIA_RESPALDO in claves)
        for (icono in ICONOS_DEL_CATALOGO) {
            assertTrue(icono.rotulo.isNotBlank(), "«${icono.clave}» sin rótulo")
            assertTrue(icono.clave.length <= CATEGORY_ICONO_MAX_LENGTH, "«${icono.clave}» no cabe en el server")
            assertTrue(icono.clave.all { it in 'a'..'z' }, "«${icono.clave}»: solo a-z")
        }
        val rotulos = ICONOS_DEL_CATALOGO.map { it.rotulo }
        assertEquals(rotulos.size, rotulos.toSet().size, "rótulos repetidos")
    }

    @Test
    fun `cada color del catalogo tiene clave valida y rotulo`() {
        for (color in COLORES_DEL_CATALOGO) {
            assertTrue(color.rotulo.isNotBlank(), "«${color.clave}» sin rótulo")
            assertTrue(color.clave.length <= CATEGORY_COLOR_MAX_LENGTH, "«${color.clave}» no cabe en el server")
            assertTrue(color.clave.all { it in 'a'..'z' }, "«${color.clave}»: solo a-z")
        }
        assertEquals(10, COLORES_DEL_CATALOGO.map { it.clave }.toSet().size)
    }

    @Test
    fun `las reglas solo usan claves que existen`() {
        val filas = TABLA_POR_NOMBRE.map { "tabla ${it.nombres}" to (it.icono to it.color) } +
            PALABRAS_CLAVE.map { "palabra «${it.raiz}»" to (it.icono to it.color) }
        for ((donde, par) in filas) {
            assertTrue(esIconoDelCatalogo(par.first), "$donde: ícono «${par.first}» no existe")
            assertTrue(esColorDelCatalogo(par.second), "$donde: color «${par.second}» no existe")
        }
        // Las raíces se comparan contra texto normalizado: una raíz con tilde no atraparía nada.
        for (p in PALABRAS_CLAVE) assertEquals(normalizarParaBuscar(p.raiz), p.raiz, "raíz «${p.raiz}»")
    }

    @Test
    fun `una clave de icono desconocida da el de otros`() {
        val otros = ICONOS_DEL_CATALOGO.first { it.clave == ICONO_DE_CATEGORIA_RESPALDO }.imagen
        assertSame(otros, imagenDeIcono("cohete"))
        assertSame(otros, imagenDeIcono(null))
        assertSame(otros, ap("cohete", "gris").imagen)
    }

    // ── Dónde está lo del dueño ──────────────────────────────────────────────

    @Test
    fun `la preferencia se encuentra por nombre exacto o normalizado`() {
        val prefs = mapOf("Fútbol" to CategoryPref(color = "rojo"))
        assertEquals("rojo", prefDeCategoria("Fútbol", prefs)?.color)
        assertEquals("rojo", prefDeCategoria(" futbol ", prefs)?.color)
        assertNull(prefDeCategoria("Estadio", prefs))
    }
}
