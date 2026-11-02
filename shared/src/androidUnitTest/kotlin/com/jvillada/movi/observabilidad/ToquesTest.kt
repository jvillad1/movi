package com.jvillada.movi.observabilidad

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.esNombreDeToque
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Vuelta 104 · **cómo se nombra un toque** ([resolverElToque]): el id fijo, el tag de quien lo monta,
 * el rótulo de la app y, si nada, el tipo de componente. Lo que importa de verdad es lo que **no** pasa:
 * un texto dinámico (un monto, un comercio, un nombre) nunca llega al nombre.
 */
class ToquesTest {

    private val rotulos = setOf("Guardar", "Guardar y seguir", "¿Ya lo anotaste?", "Atrás", "Ahorros", "Pagar \$27.500")
    private val tags = setOf("agregar:guardar", "barra-de-pestanas:", "cuenta:fila:", "ds-boton", "campo_de_monto_del_traspaso")

    private fun nombre(toque: ToqueDeUso, tag: String? = null) = resolverElToque(toque, tag, rotulos, tags).nombre

    @Test
    fun elTextoSeVuelveUnIdLegible() {
        assertEquals("guardar-y-seguir", slugDeUso("Guardar y seguir"))
        assertEquals("ya-lo-anotaste", slugDeUso("¿Ya lo anotaste?"))
        assertEquals("atras", slugDeUso("Atrás"))
        assertEquals("ver-todas-las-categorias", slugDeUso("Ver todas las categorías"))
        assertTrue(slugDeUso("x".repeat(80)).length <= 34)
    }

    @Test
    fun unRotuloDeLaAppNombraElToque() {
        assertEquals("toque:guardar", nombre(ToqueDeUso("boton", rotulo = "Guardar")))
        assertEquals("toque:ya-lo-anotaste", nombre(ToqueDeUso("boton", rotulo = "¿Ya lo anotaste?")))
    }

    @Test
    fun unTextoDinamicoNuncaLlegaAlNombre() {
        // Un texto que no es un literal de la app: el tipo de componente.
        assertEquals("toque:boton", nombre(ToqueDeUso("boton", rotulo = "Pagarle a Caro")))
        assertEquals("toque:fila", nombre(ToqueDeUso("fila", rotulo = "Rappi · Las Doce")))
        // Aunque esté en la lista, con cifras o «$» no: un monto nunca.
        assertEquals("toque:boton", nombre(ToqueDeUso("boton", rotulo = "Pagar \$27.500")))
        // Una fila de contenido dinámico no usa su título aunque coincida con un rótulo («Ahorros» es una cuenta).
        assertEquals("toque:fila", nombre(ToqueDeUso("fila", rotulo = "Ahorros", dinamico = true)))
        // Una fila dinámica con tag: el tipo de componente más el tag.
        assertEquals("toque:fila:agregar:guardar", nombre(ToqueDeUso("fila", rotulo = "Nu", dinamico = true), tag = "agregar:guardar"))
        listOf("Pagarle a Caro", "Rappi · Las Doce", "Pagar \$27.500", "Ahorros").forEach { texto ->
            val todos = listOfNotNull(
                // «Ahorros» es un rótulo de la app: un botón con ese texto sí se nombra así (no es dinámico).
                nombre(ToqueDeUso("boton", rotulo = texto)).takeIf { texto != "Ahorros" },
                nombre(ToqueDeUso("fila", rotulo = texto, dinamico = true)),
                nombre(ToqueDeUso("pildora-de-filtro", rotulo = texto, dinamico = true)),
            )
            todos.forEach { n ->
                assertTrue(esNombreDeToque(n), n)
                assertFalse(n.contains("caro") || n.contains("rappi") || n.contains("27") || n.contains("ahorros"), "«$texto» llegó a «$n»")
            }
        }
    }

    @Test
    fun elTagDeQuienLoMontaGanaSobreElTexto() {
        assertEquals("toque:agregar:guardar", nombre(ToqueDeUso("boton", rotulo = "Guardar"), tag = "agregar:guardar"))
        // Un tag con guion bajo, normalizado.
        assertEquals("toque:campo-de-monto-del-traspaso", nombre(ToqueDeUso("boton"), tag = "campo_de_monto_del_traspaso"))
        // Un tag que no es del código no cuenta (podría ser `testTag(cuenta.nombre)`): el texto o el tipo.
        assertEquals("toque:guardar", nombre(ToqueDeUso("boton", rotulo = "Guardar"), tag = "Mi Cuenta Nu"))
    }

    @Test
    fun loArmadoConDolarSeQuedaConLaParteDelCodigo() {
        // Un enum al final se queda en el id.
        assertEquals("toque:barra-de-pestanas:hoy", nombre(ToqueDeUso("pestana"), tag = "barra-de-pestanas:HOY"))
        // Un id de Movi va a los datos, no al nombre.
        val conCuenta = resolverElToque(ToqueDeUso("fila"), "cuenta:fila:acc_9c1d2e3f-0000-4000-8000-000000000001", rotulos, tags)
        assertEquals("toque:cuenta:fila", conCuenta.nombre)
        assertEquals("acc_9c1d2e3f-0000-4000-8000-000000000001", conCuenta.datos[ClaveDeUso.CUENTA_ID])
        // Un texto cualquiera al final se tira.
        assertEquals("toque:cuenta:fila", nombre(ToqueDeUso("fila"), tag = "cuenta:fila:Bancolombia Ahorros"))
    }

    @Test
    fun elIdFijoGanaSiempre() {
        val atras = resolverElToque(ToqueDeUso("atras", idFijo = "atras", datos = mapOf(ClaveDeUso.COMO to "FLECHA")), "agregar:guardar", rotulos, tags)
        assertEquals("toque:atras", atras.nombre)
        assertEquals("FLECHA", atras.datos[ClaveDeUso.COMO])
    }

    @Test
    fun elTagSeLeeDelModifierDeQuienLlama() {
        assertEquals("agregar:guardar", tagDelModificador(Modifier.testTag("agregar:guardar")))
        assertNull(tagDelModificador(Modifier))
    }

    @Test
    fun losRotulosGeneradosSonLiteralesDelCodigoSinLosComentarios() {
        val generados = RotulosDeUsoGenerados.rotulos
        assertTrue("Guardar" in generados, "un rótulo de un Boton")
        assertTrue("Ahora no" in generados, "una constante pasada como rótulo")
        assertTrue("Buscar por nombre" in generados, "la guía de un campo")
        // El ejemplo del KDoc de `Fila` («Rappi · Las Doce») es un comentario: no entra.
        assertFalse("Rappi · Las Doce" in generados)
        assertTrue(generados.none { r -> r.any { it.isDigit() || it == '$' || it == '@' } }, "ningún rótulo con cifras, «$» o «@»")
        assertTrue("ds-boton" in RotulosDeUsoGenerados.tags)
        assertTrue("barra-de-pestanas:" in RotulosDeUsoGenerados.tags, "el prefijo de un tag armado con \$")
    }

    @Test
    fun laEntidadDeLaTarjetaVaALosDatos() {
        assertEquals(mapOf(ClaveDeUso.AVISO_ID to "sms_rt_9c1d2e3f4a5b6c7d"), datosDeLaEntidad("sms_rt_9c1d2e3f4a5b6c7d"))
        assertEquals(mapOf(ClaveDeUso.MOVIMIENTO_ID to "ev_9c1d2e3f4a5b"), datosDeLaEntidad("ev_9c1d2e3f4a5b"))
        assertEquals(emptyMap(), datosDeLaEntidad("0123456789"), "un número de cuenta no es un id de Movi")
        assertEquals(emptyMap(), datosDeLaEntidad("Nu"))
    }
}
