package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.data.CacheDeLecturas
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SmsMessage
import kotlinx.datetime.Clock

/*
 * # Vuelta 74 · «Es este» en Revisar aviso no deja la bandeja en esqueleto
 *
 * La revisión del 30-oct (punto 13): tras «Es este» en Revisar aviso, al volver, «Por revisar» pasaba
 * a esqueleto entero y perdía la posición. No era un bug de la bandeja sino la regla de
 * [CacheDeLecturas]: **toda** escritura vacía lo recordado (lo que una escritura volvió viejo no se
 * pinta como «lo último»), y la bandeja, recién compuesta al volver, no tenía nada que pintar.
 *
 * «Es este» es la escritura de la que se sabe exactamente qué cambió: **solo** el estado de esos
 * avisos —no crea ni toca ningún movimiento, ninguna cuenta, ningún débito—. Así que lo que la bandeja
 * lee se puede reponer tal cual, con esos avisos ya confirmados, y la bandeja vuelve con sus
 * tarjetas (menos esa), «Actualizando…» arriba y la lectura de verdad en camino. Confirmar un aviso
 * que CREA su movimiento no pasa por acá: eso sí cambia la historia, y lo honesto es releerla.
 */

/** Lo que la bandeja lee y «Es este» no cambia (más los mensajes, que se reponen con el aviso cerrado). */
private val LO_QUE_LEE_LA_BANDEJA: List<ClaveDeLectura<*>> = listOf(
    ClaveDeLectura.MensajesDelBanco,
    ClaveDeLectura.PropuestasDeLaBandeja,
    ClaveDeLectura.CandidatosPagoDeTarjeta,
    ClaveDeLectura.DebitosAutomaticos,
    ClaveDeLectura.PropuestasDeSaldar,
    ClaveDeLectura.PropuestasDeRepartir,
    ClaveDeLectura.EventosPorDia,
    ClaveDeLectura.Cuentas,
    ClaveDeLectura.Perfil,
)

/** Los mensajes con [avisos] confirmados: lo único que cambió. */
internal fun conLosAvisosConfirmados(mensajes: List<SmsMessage>, avisos: Set<String>): List<SmsMessage> =
    mensajes.map { if (it.id in avisos) it.copy(state = SMS_STATE_CONFIRMED) else it }

/**
 * Corre [escribir] —un «Es este» que cierra [avisos]— y después repone lo que la bandeja tenía
 * recordado, con esos avisos confirmados. Si [escribir] falla, no se repone nada (la escritura no
 * pasó, y lo recordado tampoco se vació).
 */
internal suspend fun <T> sinVaciarLaBandeja(avisos: Set<String>, escribir: suspend () -> T): T {
    val usuario = SessionManager.userId
    val ahora = Clock.System.now().toEpochMilliseconds()
    @Suppress("UNCHECKED_CAST") // cada clave fija su tipo; la foto lo devuelve con la misma
    val fotos = LO_QUE_LEE_LA_BANDEJA.map { Foto.de(it as ClaveDeLectura<Any>, ahora) }
    val resultado = escribir()
    val despues = Clock.System.now().toEpochMilliseconds()
    fotos.forEach { it.reponer(usuario, despues, avisos) }
    return resultado
}

private class Foto<T : Any>(private val clave: ClaveDeLectura<T>, private val valor: T?) {
    fun reponer(usuario: String?, ahora: Long, avisos: Set<String>) {
        val visto = valor ?: return
        @Suppress("UNCHECKED_CAST") // la clave fija el tipo
        val repuesto = if (clave == ClaveDeLectura.MensajesDelBanco) conLosAvisosConfirmados(visto as List<SmsMessage>, avisos) as T else visto
        CacheDeLecturas.guardar(clave, repuesto, usuario, ahora)
    }

    companion object {
        fun <T : Any> de(clave: ClaveDeLectura<T>, ahora: Long): Foto<T> = Foto(clave, CacheDeLecturas.ultima(clave, ahora))
    }
}
