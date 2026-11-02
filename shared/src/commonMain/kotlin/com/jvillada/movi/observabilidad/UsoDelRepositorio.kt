package com.jvillada.movi.observabilidad

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.repository.WalletRepository

/**
 * # Las funciones clave, anotadas en un solo lugar (vuelta 100)
 *
 * Un envoltorio del repositorio de la app (`Repositories.paraLaApp`) que anota en [Uso] las acciones
 * que mide la adopción —crear una cuenta, confirmar o descartar en Por revisar, usar Movi AI, repartir
 * en un espacio, subir un papel o un extracto, crear un recurrente, editar el inicio— y la lectura de
 * un papel con su resultado y lo que tardó. **Delega todo**: no cambia una sola respuesta ni un
 * error; solo anota después de que la llamada terminó bien (o, en la lectura, también cuando falló).
 *
 * Acá y no en cada pantalla: la misma acción se hace desde varios lugares (confirmar un aviso desde
 * Por revisar, desde Reconciliar o desde «Agregar»), y una línea por pantalla se olvida en la próxima.
 * Lo que solo una pantalla sabe —que el movimiento se escribió a mano en Agregar, un paso de la guía—
 * lo anota esa pantalla.
 */
class UsoDelRepositorio(private val repo: WalletRepository) : WalletRepository by repo {

    private fun avisoId(id: String): Map<ClaveDeUso, String> =
        if (esIdDeMovi(id)) mapOf(ClaveDeUso.AVISO_ID to id) else emptyMap()

    private fun porRevisar(resultado: String, clase: String, extra: Map<ClaveDeUso, String> = emptyMap()) {
        Uso.evento(EventoDeUso.POR_REVISAR, mapOf(ClaveDeUso.RESULTADO to resultado, ClaveDeUso.CLASE to clase) + extra)
    }

    // ── Por revisar ──────────────────────────────────────────────────────────────────────────

    override suspend fun confirmSms(id: String) = repo.confirmSms(id).also { porRevisar("CONFIRMADO", "AVISO", avisoId(id)) }
    override suspend fun confirmSmsCon(id: String, eventoId: String) =
        repo.confirmSmsCon(id, eventoId).also { porRevisar("CONFIRMADO", "AVISO", avisoId(id)) }
    override suspend fun confirmarComprobante(smsId: String, eventoId: String) =
        repo.confirmarComprobante(smsId, eventoId).also { porRevisar("CONFIRMADO", "COMPROBANTE", avisoId(smsId)) }
    override suspend fun confirmarConLasDosPatas(smsId: String, patas: com.jvillada.movi.shared.model.DosPatasDelAviso): MismoPagoConfirmado =
        repo.confirmarConLasDosPatas(smsId, patas).also { porRevisar("CONFIRMADO", "AVISO", avisoId(smsId)) }
    override suspend fun confirmarEnPartes(smsId: String, partes: com.jvillada.movi.shared.model.PartesDelAviso): MismoPagoConfirmado =
        repo.confirmarEnPartes(smsId, partes).also { porRevisar("CONFIRMADO", "AVISO", avisoId(smsId)) }
    override suspend fun confirmarElMismoPago(grupoId: String, pedido: ConfirmarElMismoPago): MismoPagoConfirmado =
        repo.confirmarElMismoPago(grupoId, pedido).also { porRevisar("CONFIRMADO", "AVISO") }
    override suspend fun ignoreSms(id: String) = repo.ignoreSms(id).also { porRevisar("DESCARTADO", "AVISO", avisoId(id)) }
    override suspend fun ignorarElMismoPago(grupoId: String, miembros: List<String>) =
        repo.ignorarElMismoPago(grupoId, miembros).also { porRevisar("DESCARTADO", "AVISO") }
    override suspend fun dismissCardPaymentCandidate(id: String) =
        repo.dismissCardPaymentCandidate(id).also { porRevisar("DESCARTADO", "PAGO_DE_TARJETA") }
    override suspend fun descartarDebitoAutomatico(ruleId: String, periodo: String) =
        repo.descartarDebitoAutomatico(ruleId, periodo).also { porRevisar("DESCARTADO", "DEBITO") }
    override suspend fun confirmarDebitoAutomatico(pedido: com.jvillada.movi.shared.model.ConfirmarDebitoAutomatico): List<FinancialEvent> =
        repo.confirmarDebitoAutomatico(pedido).also { porRevisar("CONFIRMADO", "DEBITO") }

    // ── Las demás funciones clave ────────────────────────────────────────────────────────────

    override suspend fun createAccount(account: Account): Account =
        repo.createAccount(account).also { cuenta ->
            Uso.evento(EventoDeUso.CUENTA_CREADA, if (esIdDeMovi(cuenta.id)) mapOf(ClaveDeUso.CUENTA_ID to cuenta.id) else emptyMap())
        }

    override suspend fun chatAi(request: AiChatRequest): AiChatResponse =
        repo.chatAi(request).also {
            val sugerida = request.messages.lastOrNull()?.preguntaSugerida != null
            Uso.evento(EventoDeUso.MOVI_AI, mapOf(ClaveDeUso.ORIGEN to if (sugerida) "SUGERIDA" else "LIBRE"))
        }

    override suspend fun repartirMovimiento(espacioId: String, eventoId: String): com.jvillada.movi.shared.model.GastoDelEspacio =
        repo.repartirMovimiento(espacioId, eventoId).also { Uso.evento(EventoDeUso.ESPACIO_REPARTIDO, mapOf(ClaveDeUso.ORIGEN to "YO")) }

    override suspend fun anotarGastoDeOtro(espacioId: String, gasto: com.jvillada.movi.shared.model.GastoDelEspacio): com.jvillada.movi.shared.model.GastoDelEspacio =
        repo.anotarGastoDeOtro(espacioId, gasto).also { Uso.evento(EventoDeUso.ESPACIO_REPARTIDO, mapOf(ClaveDeUso.ORIGEN to "OTRO")) }

    override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule =
        repo.createRecurringRule(rule).also { Uso.evento(EventoDeUso.RECURRENTE_CREADO) }

    override suspend fun guardarPreferenciasDelInicio(
        preferencias: com.jvillada.movi.shared.model.PreferenciasDelInicio,
    ): com.jvillada.movi.shared.model.PreferenciasDelInicio =
        repo.guardarPreferenciasDelInicio(preferencias).also { Uso.evento(EventoDeUso.INICIO_EDITADO) }

    // ── Papeles y extractos ──────────────────────────────────────────────────────────────────

    override suspend fun uploadDocument(
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        tipo: TipoDeDocumento,
        accountId: String?,
        periodo: String?,
        notas: String?,
    ): Documento = repo.uploadDocument(fileName, bytes, mimeType, tipo, accountId, periodo, notas).also { doc ->
        Uso.evento(EventoDeUso.PAPEL_SUBIDO, mapOf(ClaveDeUso.ORIGEN to "PAPEL") + documentoId(doc.id))
    }

    override suspend fun subirPapel(fileName: String, bytes: ByteArray, mimeType: String): Documento =
        repo.subirPapel(fileName, bytes, mimeType).also { doc ->
            Uso.evento(EventoDeUso.PAPEL_SUBIDO, mapOf(ClaveDeUso.ORIGEN to "PAPEL") + documentoId(doc.id))
        }

    override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult =
        leyendo("EXTRACTO", null) { repo.uploadStatement(fileName, bytes, mimeType) }.also {
            Uso.evento(EventoDeUso.PAPEL_SUBIDO, mapOf(ClaveDeUso.ORIGEN to "EXTRACTO"))
        }

    override suspend fun readStatementFromDocument(id: String): StatementParseResult =
        leyendo("EXTRACTO", id) { repo.readStatementFromDocument(id) }

    override suspend fun leerPapel(documentoId: String, anotarAunqueEsteAnotado: Boolean): LecturaDelPapel {
        val inicio = Uso.reloj()
        return try {
            repo.leerPapel(documentoId, anotarAunqueEsteAnotado).also { lectura ->
                val resultado = if (lectura.yaAnotado.isNotEmpty()) "YA_ANOTADO" else "OK"
                lectura(resultado, lectura.que.name, documentoId, inicio, error = null)
            }
        } catch (e: Throwable) {
            lectura("ERROR", "PAPEL", documentoId, inicio, error = codigoDelError(e))
            throw e
        }
    }

    private suspend fun <T> leyendo(clase: String, documento: String?, leer: suspend () -> T): T {
        val inicio = Uso.reloj()
        return try {
            leer().also { lectura("OK", clase, documento, inicio, error = null) }
        } catch (e: Throwable) {
            lectura("ERROR", clase, documento, inicio, error = codigoDelError(e))
            throw e
        }
    }

    private fun lectura(resultado: String, clase: String, documento: String?, inicio: Long, error: String?) {
        val datos = buildMap {
            put(ClaveDeUso.RESULTADO, resultado)
            put(ClaveDeUso.CLASE, clase)
            put(ClaveDeUso.MS, (Uso.reloj() - inicio).coerceAtLeast(0).toString())
            putAll(documentoId(documento))
            if (error != null) put(ClaveDeUso.ERROR, error)
        }
        Uso.evento(EventoDeUso.LECTURA_DE_PAPEL, datos)
    }

    private fun documentoId(id: String?): Map<ClaveDeUso, String> =
        if (esIdDeMovi(id)) mapOf(ClaveDeUso.DOCUMENTO_ID to id!!) else emptyMap()
}

/**
 * El código de un error para el uso: el del server si lo dio (`IA_SIN_CREDITO`), si no el HTTP como
 * `HTTP_SIN_CODIGO`, y si no llegó al server `SIN_CONEXION`. Nunca el mensaje.
 */
internal fun codigoDelError(e: Throwable): String {
    val codigoDelServer = (e as? ApiException)?.serverMessage
        ?.let { Regex("\"(?:codigo|code|error)\"\\s*:\\s*\"([A-Z][A-Z_]{1,39})\"").find(it)?.groupValues?.get(1) }
    return when {
        codigoDelServer != null -> codigoDelServer
        e is ApiException -> "HTTP_SIN_CODIGO"
        else -> "SIN_CONEXION"
    }
}
