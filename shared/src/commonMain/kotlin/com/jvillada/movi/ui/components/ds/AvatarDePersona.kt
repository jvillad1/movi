package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi

/** La inicial con que [AvatarDePersona] nombra a un tercero: la primera letra, en mayúscula. */
fun inicialDe(nombre: String): String = nombre.trim().firstOrNull()?.uppercase() ?: "?"

/**
 * **Las iniciales del dueño** (vuelta 41, ahora en `ds/`): las del primer nombre y el primer apellido
 * («Juan Villada» → «JV»), o la del nombre solo; «?» sin nombre. Es la única función para todos sus
 * avatares. Pura.
 */
fun inicialesDelDueno(nombre: String?): String {
    val palabras = nombre?.trim()?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
    if (palabras.isEmpty()) return "?"
    return palabras.take(2).joinToString("") { it.first().uppercase() }
}

/** El `testTag` de [AvatarDePersona]. */
const val TAG_AVATAR_DE_PERSONA: String = "ds:avatar-de-persona"

/** **De quién es un [AvatarDePersona]**, en su semántica: para vigilar que el dueño se vea igual en todas partes. */
enum class QuienEsElAvatar { Dueno, Tercero }

/** Ver [QuienEsElAvatarKey]. */
val QuienEsElAvatarKey: SemanticsPropertyKey<QuienEsElAvatar> = SemanticsPropertyKey("QuienEsElAvatar")

/** Ver [QuienEsElAvatarKey]. */
var SemanticsPropertyReceiver.quienEsElAvatar: QuienEsElAvatar by QuienEsElAvatarKey

/** Lo que dice el círculo, también en la semántica: «DM» en todas partes. */
val InicialesDelAvatarKey: SemanticsPropertyKey<String> = SemanticsPropertyKey("InicialesDelAvatar")

/** Ver [InicialesDelAvatarKey]. */
var SemanticsPropertyReceiver.inicialesDelAvatar: String by InicialesDelAvatarKey

/**
 * El tono de un tercero: **uno solo y estable**, el de información (vuelta 80). Caro es azul en
 * Grupos, en «Personas y comercios» y en el detalle de un gasto. Pura.
 */
val TONO_DE_UN_TERCERO: TonoDeIcono = TonoDeIcono.Info

/** El tono del dueño: el tinte con el ocre ([TonoDeIcono.Propio]). Nunca rojo: el rojo se lee como error. */
val TONO_DEL_DUENO: TonoDeIcono = TonoDeIcono.Propio

/**
 * **Una persona: un solo avatar para toda la app** (vuelta 80).
 *
 * La revisión 7 encontró al dueño en tres caras: «DM» blanco en el Inicio, «DM» en rojo en Tú (el
 * color que el perfil guardaba) y «D» en el tinte en Grupos. Ahora el dueño va **siempre** con sus
 * iniciales ([inicialesDelDueno]) en el tinte ([AvatarDelDueno]), y un tercero con su inicial en su
 * tono estable ([AvatarDeTercero]).
 *
 * Un círculo de [tamano] (por defecto [Medida.avatar]) en el fondo de su [tono]. Una persona **nunca**
 * va en [TonoDeIcono.Marca]: el amarillo lleno es de la acción; el suave, sí.
 */
@Composable
fun AvatarDePersona(
    nombre: String,
    modifier: Modifier = Modifier,
    tono: TonoDeIcono = TonoDeIcono.Neutro,
    tamano: Dp = Medida.avatar,
    iniciales: String = inicialDe(nombre),
    quien: QuienEsElAvatar = QuienEsElAvatar.Tercero,
) {
    val color = colorDelTonoDeIcono(tono, Movi.colores)
    // Dos nodos: el modificador de quien lo monta (su toque, su foco, su `testTag`) afuera, y el círculo
    // con el tag del sistema adentro, para que ningún tag pise al otro.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(tamano)
                .background(fondoDelTonoDeIcono(tono, Movi.colores), CircleShape)
                .testTag(TAG_AVATAR_DE_PERSONA)
                .semantics {
                    tonoDeIcono = tono
                    quienEsElAvatar = quien
                    inicialesDelAvatar = iniciales
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                iniciales,
                style = if (tamano < Medida.avatar) Movi.textos.cuerpoFuerte else Movi.textos.titulo,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/**
 * **El dueño**: sus iniciales en el tinte, en el Inicio, en Tú, en Grupos y en los encabezados.
 * El modificador lo pone quien lo monta (el toque, el foco, el `testTag` de la pantalla).
 */
@Composable
fun AvatarDelDueno(
    nombre: String?,
    modifier: Modifier = Modifier,
    tamano: Dp = Medida.avatar,
) = AvatarDePersona(
    nombre = nombre.orEmpty(),
    modifier = modifier,
    tono = TONO_DEL_DUENO,
    tamano = tamano,
    iniciales = inicialesDelDueno(nombre),
    quien = QuienEsElAvatar.Dueno,
)

/** **Un tercero** (Caro, el papá): su inicial en su tono estable, [TONO_DE_UN_TERCERO]. */
@Composable
fun AvatarDeTercero(
    nombre: String,
    modifier: Modifier = Modifier,
    tamano: Dp = Medida.avatar,
) = AvatarDePersona(nombre = nombre, modifier = modifier, tono = TONO_DE_UN_TERCERO, tamano = tamano)
