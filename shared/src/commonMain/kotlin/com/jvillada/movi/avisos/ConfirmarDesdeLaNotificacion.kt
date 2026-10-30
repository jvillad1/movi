package com.jvillada.movi.avisos

import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.ui.porrevisar.LoQueMoviEntendio
import com.jvillada.movi.ui.porrevisar.confirmarLoEntendido
import com.jvillada.movi.ui.porrevisar.loQueMoviEntendio
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * # Ola 5 · «Confirmar» desde la notificación, por el camino de siempre
 *
 * Lo llama el Worker de `:androidApp` cuando el dueño toca «Confirmar» en la notificación de un
 * aviso. **No hay un segundo camino**: se lee lo mismo que lee la bandeja (`GET /api/sms`, `GET
 * /api/sms/propuestas`, las cuentas), se decide con la misma regla ([loQueMoviEntendio]) y se
 * anota con la misma función que la fila y el lote de «Por revisar» ([confirmarLoEntendido]):
 * `POST /api/events` y `POST /api/sms/{id}/confirm?eventoId=`, que deja el aviso `confirmed` con su
 * `evento_id` una sola vez.
 *
 * ## La huella, a propósito, no
 *
 * Confirmar desde la notificación **no pasa por «Entrar con huella»**. Es una acción acotada —anota
 * exactamente lo que el dueño está leyendo en la notificación, nada más— sobre algo que ya está en su
 * pantalla, igual que la captura sube los SMS en segundo plano sin pedir el dedo (ver
 * `EntrarConHuella.kt`: la huella es una puerta para abrir la app, no una caja fuerte). «Otra
 * categoría» y «Revisar» sí abren la app, y esos pasan por la puerta de siempre.
 *
 * ## Idempotente
 *
 * - El movimiento lleva un id que sale del aviso ([idDelMovimientoDeLaNotificacion]): dos toques, o
 *   un reintento después de que el `POST` llegó y la respuesta no, reescriben el MISMO movimiento.
 * - Un aviso que ya está `confirmed` no se vuelve a anotar: contesta [ResultadoDeConfirmar.YaEstabaAnotado].
 * - Dos toques a la vez en el mismo proceso esperan uno al otro ([candado]).
 */

/**
 * **Lo que la notificación mostró**: la categoría, la cuenta y el monto que leyó el dueño. Si al tocar
 * «Confirmar» Movi entiende otra cosa (cambió la memoria de categorías, apareció un «¿Ya lo
 * anotaste?»), no se anota: confirmar es aceptar lo que se vio, no lo que haya ahora.
 */
data class LoQueSeMostro(val categoria: String, val cuentaId: String, val monto: Long)

/** Cómo terminó un «Confirmar» de la notificación. */
sealed interface ResultadoDeConfirmar {
    /** Se anotó ahora. */
    data object Anotado : ResultadoDeConfirmar

    /** Ya estaba confirmado (otro toque, otra pantalla, otro teléfono): no se anotó otro. */
    data object YaEstabaAnotado : ResultadoDeConfirmar

    /** No se anotó nada que no estuviera; el aviso sigue en «Por revisar». */
    data class NoSePudo(val porQue: String) : ResultadoDeConfirmar
}

/** El id del movimiento que anota la notificación para [avisoId]: siempre el mismo para el mismo aviso. */
fun idDelMovimientoDeLaNotificacion(avisoId: String): String = "ev_aviso_$avisoId"

/**
 * **Lo que Movi entendió del aviso [avisoId]**, leído contra [repo] con las tres lecturas de la
 * bandeja. `null` si el aviso no está en la bandeja (ya se resolvió, o es de otro).
 */
suspend fun leerLoQueMoviEntendioDelAviso(
    repo: WalletRepository,
    avisoId: String,
    ahora: Long,
): Pair<SmsMessage, LoQueMoviEntendio>? {
    val mensajes = repo.getSmsMessages()
    val aviso = mensajes.firstOrNull { it.id == avisoId } ?: return null
    val propuestas = repo.getPropuestasDeLaBandeja()
    val cuentas = repo.getAccounts()
    return aviso to loQueMoviEntendio(aviso, mensajes, propuestas, cuentas, ahora)
}

/** Dos «Confirmar» a la vez (un doble toque) se ponen en fila: el segundo ve lo que dejó el primero. */
private val candado = Mutex()

/**
 * **Confirma el aviso [avisoId] desde la notificación**, si sigue siendo inequívoco y es lo que se
 * [mostro]. Nunca lanza: una falla de red, un 4xx o un aviso que cambió terminan en
 * [ResultadoDeConfirmar.NoSePudo], y el aviso queda pendiente.
 */
suspend fun confirmarDesdeLaNotificacion(
    repo: WalletRepository,
    avisoId: String,
    mostro: LoQueSeMostro,
    ahora: Long,
): ResultadoDeConfirmar = candado.withLock {
    val leido = intentar { leerLoQueMoviEntendioDelAviso(repo, avisoId, ahora) }
        .getOrElse { return@withLock ResultadoDeConfirmar.NoSePudo("sin conexión") }
        ?: return@withLock ResultadoDeConfirmar.NoSePudo("el aviso ya no está")
    val (aviso, entendido) = leido
    if (aviso.state == SMS_STATE_CONFIRMED) return@withLock ResultadoDeConfirmar.YaEstabaAnotado
    if (aviso.state != SMS_STATE_PENDING) return@withLock ResultadoDeConfirmar.NoSePudo("el aviso ya se resolvió")
    if (botonesDelAviso(1, entendido) != BotonesDelAviso.CONFIRMAR_U_OTRA_CATEGORIA) {
        return@withLock ResultadoDeConfirmar.NoSePudo("Movi tiene dudas")
    }
    val anotacion = entendido.anotacion ?: return@withLock ResultadoDeConfirmar.NoSePudo("Movi tiene dudas")
    if (anotacion.categoria != mostro.categoria || anotacion.cuenta.id != mostro.cuentaId || anotacion.monto != mostro.monto) {
        return@withLock ResultadoDeConfirmar.NoSePudo("cambió lo que Movi propone")
    }
    intentar {
        confirmarLoEntendido(anotacion, ahora, repo = repo, idDelMovimiento = idDelMovimientoDeLaNotificacion(avisoId))
    }.fold(
        // Vuelta 78: este cliente no pasa por el repositorio de la app; lo leído ya no es fresco.
        onSuccess = { com.jvillada.movi.data.olvidarLoFresco(); ResultadoDeConfirmar.Anotado },
        onFailure = { ResultadoDeConfirmar.NoSePudo("sin conexión") },
    )
}
