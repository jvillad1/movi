package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.SesionGuardada

/**
 * **El lector de huellas de ESTE aparato, o nada.**
 *
 * Hoy solo Android tiene un `actual` de verdad. En iOS y en la web [huellaDeLaPlataforma]
 * devuelve `null`, y todas las pantallas están escritas para que `null` signifique «acá no se
 * ofrece nada»: ni el ofrecimiento tras entrar, ni el interruptor de Perfil. Es el mismo trato
 * que ya tenía [PushOptIn] con su `supported = false`, pero con un `null` en vez de un flag,
 * porque acá no hay ningún método que tenga sentido llamar cuando no hay lector.
 *
 * Las cuatro operaciones son **asíncronas con callback** y no `suspend` a propósito: el prompt
 * biométrico de Android contesta en su propio callback, y envolverlo en una corrutina obligaría
 * a cancelarla a mano cuando la pantalla se va. El callback puede no llegar nunca (la persona
 * deja el diálogo abierto y mata la app); ninguna pantalla depende de que llegue.
 */
interface HuellaDelAparato {

    /** Qué puede hacer el aparato **ahora**. Se pregunta cada vez: las huellas se agregan y borran. */
    fun estado(): EstadoDeHuella

    /** ¿Quedó una sesión cifrada de una vez anterior? */
    fun haySesionGuardada(): Boolean

    /**
     * Cifra [sesion] con una llave del Keystore que solo se desbloquea con huella, y la guarda.
     * Pide la huella una vez, para confirmar. No guarda la contraseña: [SesionGuardada] no la tiene.
     */
    fun guardar(sesion: SesionGuardada, alTerminar: (ResultadoDeHuella) -> Unit)

    /** Pide la huella y, si sale bien, devuelve lo que estaba guardado. */
    fun abrir(alTerminar: (ResultadoDeHuella, SesionGuardada?) -> Unit)

    /**
     * Borra lo cifrado y apaga el interruptor. Lo llaman el logout, el apagado desde Perfil, y
     * todo camino donde lo guardado ya no sirva (ver
     * [com.jvillada.movi.data.quePasaTrasLaHuella]).
     */
    fun olvidar()
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
