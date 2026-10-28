package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.theme.ColoresDeMovi
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.theme.Sombra

/**
 * # El sistema de diseño de Movi, en componentes (`ui/components/ds/`)
 *
 * Ola R0 del rediseño. Cada pieza que se repite en la app tiene **un** componente acá, con pocos
 * parámetros y las variantes en un `enum` —nunca un número suelto—. Las pantallas eligen de una
 * lista; no deciden rellenos, radios ni colores. `ElSistemaNoTieneValoresSueltosTest` vigila que
 * adentro de este paquete no haya ni un `.dp`, `.sp`, `Color(0x…)` ni alfa escritos a mano: todo
 * sale de `theme/` (`Escalas.kt` y `Tokens.kt`). Las reglas, con un ejemplo de cada componente,
 * están en `docs/diseno/sistema.md`.
 */

/**
 * **La altura de una superficie.** Superficies por capas y sin bordes duros: una superficie se
 * distingue de lo de abajo por su color (el blanco sobre el crema, en la opción 3) y, si es
 * destacada, por su sombra; no por una línea.
 */
enum class NivelDeSuperficie {
    /** Sobre el fondo: tarjetas, listas agrupadas. Lo normal. */
    Capa1,

    /** Encima de una capa 1: una superficie anidada. */
    Capa2,

    /** Encima de una capa 2. Rara vez hace falta. */
    Capa3,

    /**
     * Una propuesta de Movi: blanca como una tarjeta, con el radio grande y la sombra suave de la
     * opción 3 (la tarjeta de avisos de la maqueta). Ver [TarjetaDePropuesta].
     */
    Propuesta,
}

/**
 * **Cuánto relleno lleva una superficie por dentro.**
 *
 * Son tres, no un `PaddingValues`: la auditoría encontró 27 rellenos distintos pasados a `MinCard`.
 */
enum class RellenoDeSuperficie {
    /** [Medida.rellenoDeSuperficie] (16) por los cuatro lados. Lo normal. */
    Normal,

    /**
     * Para una lista de [Fila]s: nada a los lados (cada fila trae su propio margen, así el toque
     * cubre todo el ancho) y [Espacio.xxs] arriba y abajo.
     */
    Lista,

    /** Nada: el contenido decide (una imagen, un gráfico de borde a borde). */
    Ninguno,
}

/** El color de cada nivel en la paleta del tema. Pura, para poder probarla. */
fun colorDeLaSuperficie(nivel: NivelDeSuperficie, colores: ColoresDeMovi): Color = when (nivel) {
    NivelDeSuperficie.Capa1 -> colores.capa1
    NivelDeSuperficie.Capa2 -> colores.capa2
    NivelDeSuperficie.Capa3 -> colores.capa3
    NivelDeSuperficie.Propuesta -> colores.propuesta
}

/**
 * **¿Esta superficie lleva sombra?** Las propuestas siempre, y la que la pantalla marca como
 * [destacada]; nada más. Pura.
 */
fun laSuperficieLlevaSombra(nivel: NivelDeSuperficie, destacada: Boolean): Boolean =
    destacada || nivel == NivelDeSuperficie.Propuesta

/**
 * **Si una superficie va destacada (con sombra), en su semántica**, como `VarianteDeCifraKey`: deja
 * vigilar que las listas vayan sin sombra y las propuestas con ella sin leer píxeles. Solo la llevan
 * las destacadas; una superficie común no suma ningún nodo.
 */
val SuperficieDestacadaKey: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("SuperficieDestacada")

/** Ver [SuperficieDestacadaKey]. */
var SemanticsPropertyReceiver.superficieDestacada: Boolean by SuperficieDestacadaKey

private fun rellenoDe(relleno: RellenoDeSuperficie): PaddingValues = when (relleno) {
    RellenoDeSuperficie.Normal -> PaddingValues(Medida.rellenoDeSuperficie)
    RellenoDeSuperficie.Lista -> PaddingValues(vertical = Espacio.xxs)
    RellenoDeSuperficie.Ninguno -> PaddingValues()
}

/**
 * **Una superficie**: el contenedor de toda tarjeta de la app. Reemplazó a `MinCard`, que se borró
 * en la vuelta de diseño 21 con su última llamada.
 *
 * Radio [Radio.l] (18, las listas y tarjetas de la opción 3), sin borde, con el color de su [nivel].
 * **Destacada** —[destacada], o el nivel [NivelDeSuperficie.Propuesta]— va con [Radio.xl] (22) y la
 * sombra suave ([Sombra.destacada], [ColoresDeMovi.sombra]): el héroe, una propuesta. Las listas,
 * nunca: una pantalla llena de sombras no destaca nada (ver [laSuperficieLlevaSombra]).
 *
 * ```
 * Superficie {
 *     Text("Tu plata", style = Movi.textos.apoyo)
 *     Cifra(1_234_000, VarianteDeCifra.Grande)
 * }
 * Superficie(relleno = RellenoDeSuperficie.Lista) {
 *     Fila(titulo = "Nu", subtitulo = "Ahorros", final = { Cifra(saldo) })
 *     Separador(SangriaDeSeparador.ConInicio)
 *     Fila(titulo = "Bancolombia", final = { Cifra(saldo2) })
 * }
 * ```
 *
 * @param onClick si la superficie entera se toca (una tarjeta que abre un detalle).
 * @param destacada la tarjeta principal de la pantalla (el héroe de Hoy): radio grande y sombra.
 */
@Composable
fun Superficie(
    modifier: Modifier = Modifier,
    nivel: NivelDeSuperficie = NivelDeSuperficie.Capa1,
    relleno: RellenoDeSuperficie = RellenoDeSuperficie.Normal,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    destacada: Boolean = false,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    SuperficieConRellenoLibre(
        modifier = modifier,
        nivel = nivel,
        destacada = laSuperficieLlevaSombra(nivel, destacada),
        relleno = rellenoDe(relleno),
        onClick = onClick,
        onClickLabel = onClickLabel,
        contenido = contenido,
    )
}

/**
 * Lo que dibuja [Superficie], con el relleno ya resuelto. Fue `internal` mientras `MinCard` pasaba
 * rellenos sueltos; desde la vuelta de diseño 21 no queda ninguna y es privada: un relleno que no es
 * de [RellenoDeSuperficie] va dentro del contenido, en tokens.
 */
@Composable
private fun SuperficieConRellenoLibre(
    modifier: Modifier,
    nivel: NivelDeSuperficie,
    destacada: Boolean,
    relleno: PaddingValues,
    onClick: (() -> Unit)?,
    onClickLabel: String?,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val forma = RoundedCornerShape(if (destacada) Radio.xl else Radio.l)
    val sombra = Movi.colores.sombra
    Column(
        modifier = modifier
            .then(
                if (destacada) {
                    Modifier
                        .shadow(Sombra.destacada, forma, clip = false, ambientColor = sombra, spotColor = sombra)
                        .semantics { superficieDestacada = true }
                } else {
                    Modifier
                },
            )
            .clip(forma)
            .background(colorDeLaSuperficie(nivel, Movi.colores))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(relleno),
        content = contenido,
    )
}
