package com.jvillada.movi.shared.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Un solo ícono por movimiento, un solo avatar por persona (vuelta 80)
 *
 * La revisión 7 contó tres sistemas de íconos en las listas (logos de marca, círculos de categoría y
 * letras) y tres avatares del dueño («DM» blanco en el Inicio, en rojo en Tú, «D» en el tinte en
 * Grupos). Esta guarda, sobre el código de `ui/` sin comentarios, falla si vuelve:
 *
 * - **un movimiento pintado a mano**: los lugares que pintan un movimiento usan `IconoDeMovimiento`, y
 *   el logotipo de una marca (`CirculoDeMarca(`) solo se dibuja en [MARCAS_A_MANO] —el componente
 *   mismo, una cuenta (su banco) y la cabeza de un aviso cuando su título es el banco—.
 * - **el dueño con otro avatar**: las iniciales del dueño (`inicialesDelUsuario` / `inicialesDelDueno`)
 *   solo se pintan por `AvatarDePersona` (`AvatarDelDueno`), y ningún avatar lee el color que guardaba
 *   el perfil (`avatarColorOrDefault`) fuera de su definición.
 */
class UnSoloIconoYUnSoloAvatarTest {

    private val ui by lazy { LecturaDelCodigo.archivos(LecturaDelCodigo.UI) }

    private fun nombre(ruta: String) = ruta.substringAfter("/ui/")

    /** Los lugares que pintan un movimiento: todos llaman a `IconoDeMovimiento`. */
    private val PINTAN_UN_MOVIMIENTO = setOf(
        "transactions/TransactionsScreen.kt", // Actividad, «Últimos movimientos» del Inicio y el detalle de una cuenta
        "transactions/DetalleDelMovimiento.kt", // la hoja del movimiento
        "transactions/LosAnulados.kt",
        "buscar/BuscarScreen.kt",
        "sms/LaCabezaDelAviso.kt", // Por revisar
        "espacios/EspaciosScreen.kt", // los gastos de un grupo
        "espacios/DetalleDelGasto.kt",
        "periodos/DetalleDePeriodoScreen.kt",
        "analisis/EnQueSeVaScreen.kt",
    )

    /** Los únicos archivos que dibujan el logotipo de una marca a mano. */
    private val MARCAS_A_MANO = setOf(
        "components/AvatarDelComercio.kt", // el círculo mismo y el banco de una cuenta
        "components/ds/IconoDeMovimiento.kt",
        "sms/LaCabezaDelAviso.kt", // el banco, cuando el título del aviso es el banco
        "transactions/CategorySheets.kt", // la marca junto a un nombre, en una línea
        "accounts/FormularioDeCuenta.kt", // el banco que se elige al crear una cuenta
    )

    @Test
    fun `todo lugar que pinta un movimiento usa IconoDeMovimiento`() {
        val faltan = PINTAN_UN_MOVIMIENTO.filter { archivo ->
            val codigo = ui.entries.singleOrNull { nombre(it.key) == archivo }?.value ?: error("No encontré $archivo")
            "IconoDeMovimiento(" !in codigo
        }
        assertTrue(faltan.isEmpty(), "Estos lugares pintan un movimiento sin IconoDeMovimiento: $faltan")
    }

    @Test
    fun `el logotipo de una marca no se pinta a mano`() {
        val conMarca = ui.filterValues { "CirculoDeMarca(" in it }.keys.map(::nombre).toSet()
        assertEquals(MARCAS_A_MANO, conMarca, "los únicos que dibujan CirculoDeMarca")
        val viejo = LecturaDelCodigo.dondeAparece(ui, Regex("""\bAvatarDelComercio\("""))
        assertTrue(viejo.isEmpty(), "AvatarDelComercio volvió (es IconoDeMovimiento):\n" + viejo.joinToString("\n"))
    }

    @Test
    fun `las iniciales del dueno solo las pinta su avatar`() {
        // Un `Text(inicialesDelUsuario(…))` armado a mano: así el Inicio y Tú tenían cada uno su círculo.
        val aMano = LecturaDelCodigo.dondeAparece(ui, Regex("""Text\(\s*inicialesDel(Usuario|Dueno)\("""))
        assertTrue(aMano.isEmpty(), "Las iniciales del dueño en un Text a mano (van en AvatarDelDueno):\n" + aMano.joinToString("\n"))
        val color = LecturaDelCodigo.dondeAparece(ui, Regex("""avatarColorOrDefault\(""")).filterNot { "components/AvatarButton.kt" in it }
        assertTrue(color.isEmpty(), "Un avatar con el color del perfil (el dueño va siempre en el tinte):\n" + color.joinToString("\n"))
    }
}
