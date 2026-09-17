package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.PropositoDeHuella
import com.jvillada.movi.data.ResultadoDeHuella

/**
 * **El lector de huellas de ESTE aparato, o nada.**
 *
 * Hoy solo Android tiene un `actual` de verdad. En iOS y en la web [huellaDeLaPlataforma]
 * devuelve `null`, y todas las pantallas están escritas para que `null` signifique «acá no se
 * ofrece nada»: ni el ofrecimiento tras entrar, ni el interruptor de Perfil. Es el mismo trato
 * que ya tenía [PushOptIn] con su `supported = false`, pero con un `null` en vez de un flag,
 * porque acá no hay ningún método que tenga sentido llamar cuando no hay lector.
 *
 * **Son dos métodos y ninguno guarda nada.** El lector no custodia la sesión: la deja pasar o no
 * la deja pasar, y el interruptor que dice si hay que preguntarle vive en `SessionManager`. Ver
 * el encabezado de `EntrarConHuella.kt` para por qué el token NO está bajo una llave del Keystore
 * y qué se ganó a cambio.
 *
 * [pedir] es **asíncrono con callback** y no `suspend` a propósito: el prompt de Android contesta
 * en su propio callback, y envolverlo en una corrutina obligaría a cancelarla a mano cuando la
 * pantalla se va. El callback puede no llegar nunca (la persona deja el diálogo abierto y mata la
 * app); ninguna pantalla depende de que llegue.
 */
interface HuellaDelAparato {

    /** Qué puede hacer el aparato **ahora**. Se pregunta cada vez: las huellas se agregan y borran. */
    fun estado(): EstadoDeHuella

    /** Muestra el prompt del sistema. [proposito] solo cambia lo que dice el diálogo. */
    fun pedir(proposito: PropositoDeHuella, alTerminar: (ResultadoDeHuella) -> Unit)
}

/** El `actual` de cada plataforma. `internal`: las pantallas entran por [Huella.deEsteAparato]. */
@Composable
internal expect fun huellaDeLaPlataforma(): HuellaDelAparato?

object Huella {
    /**
     * Solo para pruebas, igual que [com.jvillada.movi.data.Repositories.sustitutoDePrueba] y por
     * el mismo motivo: el prompt biométrico no existe en la JVM, así que sin esto la pantalla de
     * login no se podría montar con un aparato que sí tiene huella. `AppDePrueba` lo devuelve a
     * `null` antes de cada método.
     */
    internal var sustitutoDePrueba: HuellaDelAparato? = null

    /** Lo que usan las pantallas. `null` = este aparato no ofrece huella. */
    @Composable
    fun deEsteAparato(): HuellaDelAparato? = sustitutoDePrueba ?: huellaDeLaPlataforma()
}
