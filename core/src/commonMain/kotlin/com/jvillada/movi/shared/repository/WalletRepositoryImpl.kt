package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.GrupoDeAvisos
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.GuardarRecuerdoRequest
import com.jvillada.movi.shared.model.OrigenDelRecuerdo
import com.jvillada.movi.shared.model.RecuerdoDelAsistente
import com.jvillada.movi.shared.model.ResolverPropuestaRequest
import com.jvillada.movi.shared.model.EstadoDePropuesta
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.MovimientoRechazado
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.NOTA_DEL_PAPEL_COMPARTIDO
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.model.EnlaceDeDescarga
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.DescartarDebitoAutomatico
import com.jvillada.movi.shared.model.VincularPagoDeDeudaRequest
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.EdicionDeDocumento
import com.jvillada.movi.shared.model.EdicionDeMovimiento
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AdjustAccountBalanceRequest
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.shared.model.AdjustCreditBalanceRequest
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.PropuestaDePresupuesto
import com.jvillada.movi.shared.model.CategoryPrefsRequest
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.RecuerdoDeCategoria
import com.jvillada.movi.shared.model.AnotarElPagoDeLaOcurrencia
import com.jvillada.movi.shared.model.PagoDeLaOcurrenciaAnotado
import com.jvillada.movi.shared.model.CuentasDeCadaUso
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.MergeCategoryRequest
import com.jvillada.movi.shared.model.RenameCategoryRequest
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreateSubscriptionRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.TransferResult
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.DescartarSugerido
import com.jvillada.movi.shared.model.DestinoSugerido
import com.jvillada.movi.shared.model.DestinosDescartados
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.RenombradosDelDestino
import com.jvillada.movi.shared.model.RenombrarMovimientos
import com.jvillada.movi.shared.model.DeleteBudgetRequest
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.PasswordResetRequest
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ReminderChannels
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.shared.model.RenameBudgetRequest
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.MarkOccurrenceRequest
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RechazarOcurrenciaRequest
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpdateEventCategoryRequest
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.UpdateEventRepeatsRequest
import com.jvillada.movi.shared.model.UpdateEventTimestampRequest
import com.jvillada.movi.shared.model.ChangePasswordRequest
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.VoidEvent
import com.jvillada.movi.shared.model.RenameAccountRequest
import com.jvillada.movi.shared.model.UpdateAccountConditionRequest
import com.jvillada.movi.shared.model.RecategorizarEnLoteRequest
import com.jvillada.movi.shared.model.RecategorizarEnLoteResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class WalletRepositoryImpl(
    private val client: HttpClient,
    private val baseUrl: String,
) : WalletRepository {

    override suspend fun getCredits(): List<CreditSummary> =
        client.get("$baseUrl/api/credits").body()

    override suspend fun createCredit(request: CreateCreditRequest): CreditSummary =
        client.post("$baseUrl/api/credits") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary =
        client.put("$baseUrl/api/credits/${terms.accountId}") {
            contentType(ContentType.Application.Json)
            setBody(terms)
        }.body()

    override suspend fun deleteCreditTerms(accountId: String) {
        client.delete("$baseUrl/api/credits/$accountId")
    }

    // Único call site que mira el status antes de deserializar: es el que tiene rechazos
    // legibles del server (400 fuera de rango, 404, 422 no-LOAN / no-COP) y son justo los que
    // el usuario necesita leer. Sin esto, `.body()` sobre el 400 fallaba deserializando y el
    // texto del server se perdía.
    override suspend fun registerPayrollDeduction(accountId: String): CreditSummary {
        val response = client.post("$baseUrl/api/credits/$accountId/payroll-deduction")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun adjustCreditBalance(accountId: String, targetBalance: Long): CreditSummary {
        val response = client.post("$baseUrl/api/credits/$accountId/balance-adjustment") {
            contentType(ContentType.Application.Json)
            setBody(AdjustCreditBalanceRequest(targetBalance))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    /**
     * Mismo idioma que [adjustCreditBalance] —y mismo motivo para no usar `.body()` directo—: los
     * rechazos de esta ruta son texto que el dueño tiene que poder leer en la pantalla (422 si la
     * cuenta es una deuda, 400 si el monto se fue de rango).
     */
    override suspend fun adjustAccountBalance(accountId: String, targetBalance: Long): AdjustAccountBalanceResponse {
        val response = client.post("$baseUrl/api/accounts/$accountId/balance-adjustment") {
            contentType(ContentType.Application.Json)
            setBody(AdjustAccountBalanceRequest(targetBalance))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun getCards(): List<CardSummary> =
        client.get("$baseUrl/api/cards").body()

    // Mismo idioma que adjustCreditBalance: estos dos son los que tienen rechazos legibles del
    // server (400 nombre/deuda/moneda, 404 ajena, 422 no-tarjeta) y el usuario necesita leerlos
    // en la hoja — `.body()` directo sobre un 4xx de texto perdía el mensaje deserializando.
    override suspend fun createCard(request: CreateCardRequest): CardSummary {
        val response = client.post("$baseUrl/api/cards") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun putCardTerms(terms: CardTerms): CardSummary {
        val response = client.put("$baseUrl/api/cards/${terms.accountId}") {
            contentType(ContentType.Application.Json)
            setBody(terms)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun deleteCardTerms(accountId: String) {
        client.delete("$baseUrl/api/cards/$accountId")
    }

    override suspend fun getSubscriptions(): SubscriptionsResult =
        client.get("$baseUrl/api/subscriptions").body()

    override suspend fun detectSubscriptions(): SubscriptionsResult =
        client.post("$baseUrl/api/subscriptions/detect").body()

    override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription =
        client.put("$baseUrl/api/subscriptions/$id") {
            contentType(ContentType.Application.Json)
            setBody(subscription)
        }.body()

    override suspend fun deleteSubscription(id: String) {
        client.delete("$baseUrl/api/subscriptions/$id")
    }

    // Mismo idioma que createCard: un nombre repetido da 409 con texto del server — se pierde
    // si se deserializa a ciegas sobre el 4xx.
    override suspend fun createSubscription(request: CreateSubscriptionRequest): Subscription {
        val response = client.post("$baseUrl/api/subscriptions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun getGoals(): List<Goal> =
        client.get("$baseUrl/api/goals").body()

    // Mismo idioma que createCard/createSubscription: 404 (cuenta ajena) y 422 (cuenta de
    // deuda) traen su propio texto del server.
    override suspend fun createGoal(goal: Goal): Goal {
        val response = client.post("$baseUrl/api/goals") {
            contentType(ContentType.Application.Json)
            setBody(goal)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun updateGoal(id: String, goal: Goal): Goal {
        val response = client.put("$baseUrl/api/goals/$id") {
            contentType(ContentType.Application.Json)
            setBody(goal)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun deleteGoal(id: String) {
        client.delete("$baseUrl/api/goals/$id")
    }

    override suspend fun getApartados(): List<Apartado> {
        val response = client.get("$baseUrl/api/apartados")
        // Un server anterior a los apartados no tiene la ruta: para el cliente, no hay ninguno.
        if (response.status == HttpStatusCode.NotFound) return emptyList()
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun guardarApartado(a: Apartado): Apartado {
        val response = client.post("$baseUrl/api/apartados") {
            contentType(ContentType.Application.Json)
            setBody(if (a.id.isBlank()) a.copy(id = newId("apt")) else a)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun quitarApartado(id: String) {
        val response = client.delete("$baseUrl/api/apartados/$id")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

    // ── Espacios (R3) ─────────────────────────────────────────────────────────

    private suspend fun <T> conRespuesta(response: io.ktor.client.statement.HttpResponse, leer: suspend (io.ktor.client.statement.HttpResponse) -> T): T {
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return leer(response)
    }

    override suspend fun getEspacios(): List<com.jvillada.movi.shared.model.Espacio> {
        val response = client.get("$baseUrl/api/espacios")
        if (response.status == HttpStatusCode.NotFound) return emptyList()
        return conRespuesta(response) { it.body() }
    }

    override suspend fun guardarEspacio(espacio: com.jvillada.movi.shared.model.Espacio): com.jvillada.movi.shared.model.Espacio =
        conRespuesta(
            client.post("$baseUrl/api/espacios") {
                contentType(ContentType.Application.Json)
                setBody(if (espacio.id.isBlank()) espacio.copy(id = newId("esp")) else espacio)
            },
        ) { it.body() }

    override suspend fun getDetalleDelEspacio(id: String): com.jvillada.movi.shared.model.DetalleDelEspacio =
        conRespuesta(client.get("$baseUrl/api/espacios/$id")) { it.body() }

    override suspend fun repartirMovimiento(espacioId: String, eventoId: String): com.jvillada.movi.shared.model.GastoDelEspacio =
        conRespuesta(
            client.post("$baseUrl/api/espacios/$espacioId/compartir") {
                contentType(ContentType.Application.Json)
                setBody(com.jvillada.movi.shared.model.CompartirMovimientoRequest(eventoId = eventoId, gastoId = newId("gde")))
            },
        ) { it.body() }

    override suspend fun quitarDelEspacio(espacioId: String, eventoId: String) {
        conRespuesta(client.delete("$baseUrl/api/espacios/$espacioId/compartir/$eventoId")) { }
    }

    override suspend fun quitarGastoDelEspacio(espacioId: String, gastoId: String) {
        conRespuesta(client.delete("$baseUrl/api/espacios/$espacioId/gastos/$gastoId")) { }
    }

    override suspend fun anotarGastoDeOtro(espacioId: String, gasto: com.jvillada.movi.shared.model.GastoDelEspacio): com.jvillada.movi.shared.model.GastoDelEspacio =
        conRespuesta(
            client.post("$baseUrl/api/espacios/$espacioId/gastos") {
                contentType(ContentType.Application.Json)
                setBody(if (gasto.id.isBlank()) gasto.copy(id = newId("gde")) else gasto)
            },
        ) { it.body() }

    override suspend fun liquidarEspacio(espacioId: String, liquidacion: com.jvillada.movi.shared.model.LiquidacionDelEspacio): com.jvillada.movi.shared.model.LiquidacionDelEspacio =
        conRespuesta(
            client.post("$baseUrl/api/espacios/$espacioId/liquidaciones") {
                contentType(ContentType.Application.Json)
                setBody(if (liquidacion.id.isBlank()) liquidacion.copy(id = newId("liq")) else liquidacion)
            },
        ) { it.body() }

    override suspend fun getTuParteDeOtros(): List<FinancialEvent> {
        val response = client.get("$baseUrl/api/espacios/tu-parte")
        if (response.status == HttpStatusCode.NotFound) return emptyList()
        return conRespuesta(response) { it.body() }
    }

    override suspend fun getDestinos(): List<DestinoConocido> =
        client.get("$baseUrl/api/destinos").body()

    // Mismo idioma que createGoal/createCard: los tres rechazos de un destino (400 por la forma,
    // 409 por repetido, 422 por ser una cuenta suya) traen su texto del server, y ese texto es el
    // que la hoja muestra. Deserializar a ciegas sobre el 4xx lo perdería.
    override suspend fun createDestino(destino: DestinoConocido): DestinoConocido {
        val response = client.post("$baseUrl/api/destinos") {
            contentType(ContentType.Application.Json)
            setBody(destino)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun updateDestino(id: String, destino: DestinoConocido): DestinoConocido {
        val response = client.put("$baseUrl/api/destinos/$id") {
            contentType(ContentType.Application.Json)
            setBody(destino)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun deleteDestino(id: String) {
        client.delete("$baseUrl/api/destinos/$id")
    }

    override suspend fun getMovimientosDelDestino(id: String): MovimientosDelDestino =
        client.get("$baseUrl/api/destinos/$id/movimientos").body()

    override suspend fun getDestinosSugeridos(): List<DestinoSugerido> =
        conElTextoDelRechazo(client.get("$baseUrl/api/destinos/sugeridos")).body()

    override suspend fun descartarSugerido(pedido: DescartarSugerido) {
        conElTextoDelRechazo(
            client.post("$baseUrl/api/destinos/sugeridos/descartar") {
                contentType(ContentType.Application.Json)
                setBody(pedido)
            },
        )
    }

    override suspend fun getDestinosDescartados(): DestinosDescartados =
        conElTextoDelRechazo(client.get("$baseUrl/api/destinos/descartados")).body()

    // Mismo idioma que createDestino: el 409 («ya lo tiene Daniel») y el 422 («es tu cuenta») traen
    // su texto, y ese texto es el que la pantalla muestra.
    override suspend fun agregarIdentificador(id: String, pedido: AgregarIdentificador): DestinoConocido =
        conElTextoDelRechazo(
            client.post("$baseUrl/api/destinos/$id/identificadores") {
                contentType(ContentType.Application.Json)
                setBody(pedido)
            },
        ).body()

    override suspend fun unirDestinos(id: String, con: String): DestinoConocido =
        conElTextoDelRechazo(client.post("$baseUrl/api/destinos/$id/unir/$con")).body()

    override suspend fun noSonLaMisma(id: String, otro: String) {
        conElTextoDelRechazo(client.post("$baseUrl/api/destinos/$id/no-es/$otro"))
    }

    override suspend fun quitarIdentificador(id: String, identificador: IdentificadorDelDestino): DestinoConocido =
        conElTextoDelRechazo(
            client.post("$baseUrl/api/destinos/$id/identificadores/quitar") {
                contentType(ContentType.Application.Json)
                setBody(identificador)
            },
        ).body()

    override suspend fun getRenombrablesDelDestino(id: String): MovimientosParaRenombrar =
        conElTextoDelRechazo(client.get("$baseUrl/api/destinos/$id/renombrables")).body()

    override suspend fun renombrarMovimientosDelDestino(id: String, ids: List<String>): RenombradosDelDestino =
        conElTextoDelRechazo(
            client.post("$baseUrl/api/destinos/$id/renombrar") {
                contentType(ContentType.Application.Json)
                setBody(RenombrarMovimientos(ids))
            },
        ).body()

    /** El 4xx con su texto, como [ApiException]; si salió bien, la respuesta tal cual. */
    private suspend fun conElTextoDelRechazo(response: io.ktor.client.statement.HttpResponse): io.ktor.client.statement.HttpResponse {
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response
    }

    override suspend fun getSmsMessages(): List<SmsMessage> =
        client.get("$baseUrl/api/sms").body()

    override suspend fun getSms(id: String): SmsMessage =
        client.get("$baseUrl/api/sms/$id").body()

    // Mira el status antes de deserializar, como `registerPayrollDeduction`: el 422 trae en el
    // cuerpo por qué ese mensaje no se puede anotar, y con `.body()` a secas ese texto se perdía y
    // «Reconciliar movimiento» quedaba en «Parseando…» con «Algo salió mal».
    override suspend fun parseSms(id: String): ParsedSms {
        val response = client.get("$baseUrl/api/sms/$id/parse")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun leerParaReconciliar(id: String): com.jvillada.movi.shared.model.LecturaParaReconciliar {
        val response = client.get("$baseUrl/api/sms/$id/parse") {
            parameter(com.jvillada.movi.shared.model.PARAMETRO_ANOTARLO_IGUAL, "1")
        }
        if (response.status.isSuccess()) return com.jvillada.movi.shared.model.LecturaParaReconciliar(leido = response.body())
        // Con el parámetro, un aviso que no se pudo leer contesta 422 con JSON: el motivo y lo que
        // trae. Un server viejo contesta el texto de siempre, y eso sigue saliendo como error.
        if (response.status == HttpStatusCode.UnprocessableEntity &&
            response.contentType()?.match(ContentType.Application.Json) == true
        ) {
            val noLeido = runCatching { response.body<com.jvillada.movi.shared.model.NoSePudoLeerElAviso>() }.getOrNull()
            if (noLeido != null) {
                return com.jvillada.movi.shared.model.LecturaParaReconciliar(noSePudoLeer = noLeido.motivo, sinLeer = noLeido.sinLeer)
            }
        }
        throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
    }

    override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> =
        client.get("$baseUrl/api/sms/$id/coincidencias").body()

    override suspend fun getPropuestasDeLaBandeja(): com.jvillada.movi.shared.model.PropuestasDeLaBandeja =
        client.get("$baseUrl/api/sms/propuestas").exigirExito().body()

    override suspend fun getRepartoDelAviso(id: String): com.jvillada.movi.shared.model.PropuestaDeReparto =
        client.get("$baseUrl/api/sms/$id/reparto").exigirExito().body()

    override suspend fun confirmSms(id: String) {
        client.post("$baseUrl/api/sms/$id/confirm")
    }

    override suspend fun ignoreSms(id: String) {
        client.post("$baseUrl/api/sms/$id/ignore")
    }

    override suspend fun devolverSmsALaBandeja(id: String) {
        client.post("$baseUrl/api/sms/$id/era-un-movimiento").exigirExito()
    }

    override suspend fun getAvisosDelMismoPago(grupoId: String): GrupoDeAvisos =
        client.get("$baseUrl/api/sms/grupo/$grupoId").exigirExito().body()

    override suspend fun confirmarElMismoPago(grupoId: String, pedido: ConfirmarElMismoPago): MismoPagoConfirmado =
        client.post("$baseUrl/api/sms/grupo/$grupoId/confirmar") {
            contentType(ContentType.Application.Json)
            setBody(pedido)
        }.exigirExito().body()

    override suspend fun ignorarElMismoPago(grupoId: String, miembros: List<String>) {
        client.post("$baseUrl/api/sms/grupo/$grupoId/ignorar") {
            contentType(ContentType.Application.Json)
            setBody(com.jvillada.movi.shared.model.AvisosDelMismoPago(miembros))
        }.exigirExito()
    }

    override suspend fun noSonElMismoPago(grupoId: String, miembros: List<String>) {
        client.post("$baseUrl/api/sms/grupo/$grupoId/desagrupar") {
            contentType(ContentType.Application.Json)
            setBody(com.jvillada.movi.shared.model.AvisosDelMismoPago(miembros))
        }.exigirExito()
    }

    override suspend fun esteEsOtroPago(id: String) {
        client.post("$baseUrl/api/sms/$id/no-es-el-mismo-pago").exigirExito()
    }

    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
        client.get("$baseUrl/api/finance-summary?scope=${scope.name}").body()

    override suspend fun getGastoDelDiaADia(): com.jvillada.movi.shared.model.GastoDelDiaADia =
        client.get("$baseUrl/api/caja-proyectada/gasto-del-dia-a-dia").exigirExito().body()

    override suspend fun getHistoriaDelPatrimonio(): com.jvillada.movi.shared.model.HistoriaDelPatrimonio =
        client.get("$baseUrl/api/patrimonio/historia").exigirExito().body()

    override suspend fun getAnomalias(): List<com.jvillada.movi.shared.model.Anomalia> =
        // `conDeudas=1`: esta app conoce los dos avisos de las deudas (ver `TipoDeAnomalia`).
        client.get("$baseUrl/api/anomalias?conDeudas=1&conRecurrentes=1").exigirExito().body()

    override suspend fun descartarAnomalia(huella: String) {
        client.post("$baseUrl/api/anomalias/descartar") {
            contentType(ContentType.Application.Json)
            setBody(com.jvillada.movi.shared.model.DescartarAnomaliaRequest(huella))
        }.exigirExito()
    }

    override suspend fun getCierreDelPeriodo(): com.jvillada.movi.shared.model.CierreDelPeriodo? {
        val respuesta = client.get("$baseUrl/api/cierre-del-periodo")
        // 204 = no hay tarjeta que mostrar (pasaron los primeros días, se descartó, o no hay qué contar).
        if (respuesta.status == HttpStatusCode.NoContent) return null
        return respuesta.exigirExito().body()
    }

    override suspend fun descartarCierre(periodo: String) {
        client.post("$baseUrl/api/cierre-del-periodo/descartar") {
            contentType(ContentType.Application.Json)
            setBody(com.jvillada.movi.shared.model.DescartarCierreRequest(periodo))
        }.exigirExito()
    }

    override suspend fun getCuadresPorAviso(): List<com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso> =
        client.get("$baseUrl/api/cuadres-por-aviso").exigirExito().body()

    override suspend fun ajustarPorElAviso(avisoId: String): FinancialEvent? {
        val respuesta = client.post("$baseUrl/api/cuadres-por-aviso/$avisoId/ajustar")
        // 404 = ya no había nada que ajustar: un segundo toque, o la propuesta se resolvió en otro
        // lado. No es un error que haya que mostrarle a nadie.
        if (respuesta.status == HttpStatusCode.NotFound) return null
        return respuesta.exigirExito().body()
    }

    override suspend fun descartarCuadrePorAviso(avisoId: String) {
        client.post("$baseUrl/api/cuadres-por-aviso/$avisoId/descartar").exigirExito()
    }

    override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
        client.get("$baseUrl/api/dashboard/summary?scope=${scope.name}").body()

    // Mismo chequeo de status que `createBudget`: sin `expectSuccess` en el cliente, un 4xx/5xx no
    // lanza solo — se intentaría deserializar el cuerpo de error como la lista/el detalle y
    // reventaría con un error de serialización que no dice nada del status real.
    override suspend fun getPeriodos(): List<ResumenDePeriodo> {
        val response = client.get("$baseUrl/api/periodos")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun getDetalleDePeriodo(id: String): DetalleDePeriodo {
        val response = client.get("$baseUrl/api/periodos/$id")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun getBudgets(): List<Budget> =
        client.get("$baseUrl/api/budgets").body()

    override suspend fun getPropuestasDePresupuesto(): List<PropuestaDePresupuesto> =
        client.get("$baseUrl/api/budgets/propuestas").body()

    // El estado se mira antes de leer el cuerpo: el 409 de «ya existe» llega en texto plano, y
    // leerlo como `Budget` lo convertía en un error de deserialización. Las propuestas de
    // Presupuestos cuentan justo ese 409 como «ya estaba creado» (el reintento de un POST cuya
    // respuesta se perdió), y la hoja «Nuevo presupuesto» muestra el mensaje del server.
    override suspend fun createBudget(budget: Budget): Budget {
        val response = client.post("$baseUrl/api/budgets") {
            contentType(ContentType.Application.Json)
            setBody(budget)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // El nombre del presupuesto viaja en el CUERPO, nunca en la ruta — la misma regla que está
    // escrita unas líneas más abajo para Categorías, y por el mismo motivo: es texto libre del
    // dueño. Interpolarlo en el path dejaba sin editar ni borrar cualquier presupuesto con
    // «/», «#» o «%» en el nombre: «Luz/Agua» se creaba bien (el POST lo manda en el cuerpo) y
    // después daba 404 para siempre, porque el server hace coincidir UN solo segmento de ruta.
    // El server sigue atendiendo las rutas viejas con el nombre en el path: el APK que el dueño
    // tiene instalado todavía las llama.
    override suspend fun updateBudget(category: String, budget: Budget): Budget =
        client.put("$baseUrl/api/budgets") {
            contentType(ContentType.Application.Json)
            setBody(budget.copy(category = category))
        }.body()

    override suspend fun deleteBudget(category: String) {
        client.post("$baseUrl/api/budgets/delete") {
            contentType(ContentType.Application.Json)
            setBody(DeleteBudgetRequest(category))
        }
    }

    // Mismo idioma que adjustCreditBalance/updateEventCategory: 404 (no existe) y 409 (el
    // nombre nuevo ya está en uso) traen su propio texto del server y se pierden si se
    // deserializa a ciegas.
    override suspend fun renameBudget(category: String, newCategory: String): Budget {
        val response = client.post("$baseUrl/api/budgets/rename") {
            contentType(ContentType.Application.Json)
            setBody(RenameBudgetRequest(newCategory = newCategory, category = category))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // ── Categorías (Ola 10) ───────────────────────────────────────────────────
    // Los nombres viajan SIEMPRE en el cuerpo, nunca en la ruta: una categoría es texto libre
    // del dueño («Salud & bienestar», «Otros ingresos») y meter eso en un path segment obliga a
    // codificar/decodificar bien en cuatro clientes distintos para nada.

    override suspend fun getCategories(): List<CategoryUsage> =
        client.get("$baseUrl/api/categories").body()

    // Mismo idioma que renameBudget: 409 (el nombre ya existe) y 422 (reservada / del catálogo)
    // traen su propio texto del server, y ese texto es LO QUE LA PANTALLA MUESTRA — deserializar
    // a ciegas lo perdería y dejaría al dueño con un error mudo.
    override suspend fun renameCategory(from: String, to: String): CategoryRewriteResult {
        val response = client.post("$baseUrl/api/categories/rename") {
            contentType(ContentType.Application.Json)
            setBody(RenameCategoryRequest(from = from, to = to))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun mergeCategory(from: String, into: String): CategoryRewriteResult {
        val response = client.post("$baseUrl/api/categories/merge") {
            contentType(ContentType.Application.Json)
            setBody(MergeCategoryRequest(from = from, into = into))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun setCategoryPrefs(
        name: String,
        hidden: Boolean,
        pinnedType: String?,
        icono: String?,
        color: String?,
    ): CategoryUsage {
        val response = client.put("$baseUrl/api/categories/prefs") {
            contentType(ContentType.Application.Json)
            setBody(CategoryPrefsRequest(name = name, hidden = hidden, pinnedType = pinnedType, icono = icono, color = color))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun getMemoriaDeCategorias(): List<RecuerdoDeCategoria> =
        client.get("$baseUrl/api/categorias/memoria").body()

    override suspend fun getCuentasDeCadaUso(): CuentasDeCadaUso =
        client.get("$baseUrl/api/cuentas/de-cada-uso").exigirExito().body()

    override suspend fun getRecurringRules(): List<RecurringRule> =
        client.get("$baseUrl/api/recurring-rules").body()

    override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule =
        client.post("$baseUrl/api/recurring-rules") {
            contentType(ContentType.Application.Json)
            setBody(rule)
        }.body()

    override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule =
        client.put("$baseUrl/api/recurring-rules/$id") {
            contentType(ContentType.Application.Json)
            setBody(rule)
        }.body()

    override suspend fun deleteRecurringRule(id: String) {
        client.delete("$baseUrl/api/recurring-rules/$id")
    }

    override suspend fun getUpcomingPayments(): List<UpcomingPayment> =
        client.get("$baseUrl/api/payments/upcoming").body()

    override suspend fun getReminderChannels(): ReminderChannels =
        client.get("$baseUrl/api/reminders/channels").body()

    override suspend fun getOccurrenceStates(): List<OccurrenceState> =
        client.get("$baseUrl/api/payments/occurrences").body()

    // Mismo idioma que createCard: los rechazos de acá son legibles y el dueño los necesita leer
    // («ese movimiento está anulado», «ya está marcado como la ocurrencia de otro periodo»).
    // `.body()` a ciegas sobre un 4xx de texto plano pierde el mensaje deserializando.
    override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence {
        val response = client.post("$baseUrl/api/recurring-rules/$ruleId/occurrence") {
            contentType(ContentType.Application.Json)
            setBody(MarkOccurrenceRequest(period = period, eventId = eventId))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Mismo idioma que `markOccurrence`: el server contesta en texto por qué no (un 409 «Ese pago ya
    // está anotado con otro movimiento.»), y la hoja lo tiene que poder decir.
    override suspend fun anotarElPagoDeLaOcurrencia(
        ruleId: String,
        pedido: AnotarElPagoDeLaOcurrencia,
    ): PagoDeLaOcurrenciaAnotado {
        val response = client.post("$baseUrl/api/recurring-rules/$ruleId/occurrence/pago") {
            contentType(ContentType.Application.Json)
            setBody(pedido)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Se mira el status, igual que `markOccurrence`: sin esto un 404 o un 500 se leían como
    // éxito, la pantalla recargaba y el «Deshacer» seguía ahí — el dueño tocando un botón que no
    // hace nada y sin un solo mensaje que se lo diga.
    override suspend fun unmarkOccurrence(ruleId: String, period: String) {
        val response = client.delete("$baseUrl/api/recurring-rules/$ruleId/occurrence/$period")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

    // Mismo idioma que los dos de arriba, y por el mismo motivo: acá el 400 dice algo que el dueño
    // necesita leer («Ese movimiento no existe»), y un `.body()` a ciegas sobre un 204 sin cuerpo
    // reventaría deserializando la nada.
    override suspend fun rechazarOcurrencia(ruleId: String, eventId: String) {
        val response = client.post("$baseUrl/api/recurring-rules/$ruleId/occurrence/rechazo") {
            contentType(ContentType.Application.Json)
            setBody(RechazarOcurrenciaRequest(eventId = eventId))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

    override suspend fun chatAi(request: AiChatRequest): AiChatResponse =
        client.post("$baseUrl/api/ai/chat") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun getConversacionDelAsistente(): ConversacionDelAsistente =
        client.get("$baseUrl/api/ai/conversacion").exigirExito().body()

    override suspend fun empezarConversacionNueva() {
        client.post("$baseUrl/api/ai/conversacion/nueva").exigirExito()
    }

    override suspend fun getMemoriaDelAsistente(): List<RecuerdoDelAsistente> =
        client.get("$baseUrl/api/asistente/memoria").exigirExito().body()

    override suspend fun guardarRecuerdo(texto: String, origen: OrigenDelRecuerdo, propuestaId: String?): RecuerdoDelAsistente =
        client.post("$baseUrl/api/asistente/memoria") {
            contentType(ContentType.Application.Json)
            setBody(GuardarRecuerdoRequest(texto, origen, propuestaId))
        }.exigirExito().body()

    override suspend fun editarRecuerdo(id: String, texto: String): RecuerdoDelAsistente =
        client.put("$baseUrl/api/asistente/memoria/$id") {
            contentType(ContentType.Application.Json)
            setBody(GuardarRecuerdoRequest(texto, OrigenDelRecuerdo.A_MANO))
        }.exigirExito().body()

    override suspend fun borrarRecuerdo(id: String) {
        client.delete("$baseUrl/api/asistente/memoria/$id").exigirExito()
    }

    override suspend fun resolverPropuesta(id: String, estado: EstadoDePropuesta) {
        client.post("$baseUrl/api/ai/propuestas/$id/estado") {
            contentType(ContentType.Application.Json)
            setBody(ResolverPropuestaRequest(estado))
        }.exigirExito()
    }

    override suspend fun getAccounts(): List<Account> =
        client.get("$baseUrl/api/accounts").body()

    override suspend fun getAccount(id: String): Account =
        client.get("$baseUrl/api/accounts/$id").body()

    override suspend fun createAccount(account: Account): Account =
        client.post("$baseUrl/api/accounts") {
            contentType(ContentType.Application.Json)
            setBody(account)
        }.body()

    // Mismo idioma que updateEventCategory/adjustCreditBalance: 404 (cuenta inexistente o de
    // otro usuario) trae su propio texto y se pierde si se deserializa a ciegas — acá además
    // no hay body que deserializar (204), así que sin esto un 404 pasaría desapercibido.
    override suspend fun deleteAccount(id: String) {
        val response = client.delete("$baseUrl/api/accounts/$id")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

    override suspend fun renameAccount(id: String, name: String): Account {
        val response = client.put("$baseUrl/api/accounts/$id/name") {
            contentType(ContentType.Application.Json)
            setBody(RenameAccountRequest(name))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun updateAccountCondition(id: String, condicionadaA: String?): Account {
        val response = client.put("$baseUrl/api/accounts/$id/conditioned-to") {
            contentType(ContentType.Application.Json)
            setBody(UpdateAccountConditionRequest(condicionadaA))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun updateBien(id: String, bien: com.jvillada.movi.shared.model.Bien): Account {
        val response = client.put("$baseUrl/api/accounts/$id/bien") {
            contentType(ContentType.Application.Json)
            setBody(com.jvillada.movi.shared.model.ActualizarBienRequest(bien))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Sin `expectSuccess`, un 404/422 se intentaba deserializar como evento y reventaba con un error
    // de JSON sin el código ni el motivo: el SyncEngine no podía distinguir «el server lo rechaza»
    // de «no hubo red». Ahora el rechazo viaja como [ApiException] con el texto del server.
    override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
        val response = client.post("$baseUrl/api/events") {
            contentType(ContentType.Application.Json)
            setBody(event)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun getEvents(accountId: String?): List<FinancialEvent> {
        val url = if (accountId != null) "$baseUrl/api/events?accountId=$accountId"
                  else "$baseUrl/api/events"
        return client.get(url).body()
    }

    override suspend fun getEventsByDay(): List<EventDay> =
        client.get("$baseUrl/api/events/by-day").body()

    override suspend fun getUltimosDias(ultimos: Int): List<EventDay> =
        client.get("$baseUrl/api/events/by-day?ultimos=$ultimos").body()

    // Mismo idioma que updateEventCategory/adjustCreditBalance: el server puede rechazar con
    // 422 (una validación de validateTransfer, con su texto en español ya listo para mostrar),
    // 404 (cuenta inexistente o de otro usuario) o 409 (traspaso repetido), y ese texto se
    // pierde si se deserializa a ciegas — que es justo el mensaje que la hoja necesita.
    override suspend fun createTransfer(request: CreateTransferRequest): TransferResult {
        val response = client.post("$baseUrl/api/transfers") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Mismo idioma que updateEventCategory: sin mirar el status, un 409 «Already voided» se
    // deserializaba a ciegas y explotaba como un error de parseo cualquiera — sin código, sin
    // texto. Eso importa acá más que en otras rutas: el `SyncEngine` no puede distinguir «no
    // llegó» de «ya estaba anulado», así que reintentaba esa fila cada 30 segundos para siempre.
    // Con el código a la vista, `syncVoids` sella la fila y deja de insistir.
    override suspend fun voidEvent(id: String, reason: String?): VoidEvent {
        val url = if (reason != null) "$baseUrl/api/events/$id/void?reason=$reason"
                  else "$baseUrl/api/events/$id/void"
        val response = client.post(url)
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    /**
     * Sin `throw` por status: una lista de parecidos que no se pudo leer **no puede impedir**
     * cambiar la categoría del movimiento que el dueño vino a arreglar. Es una oferta de más; si
     * falla, no se ofrece.
     */
    override suspend fun getParecidos(id: String): List<FinancialEvent> =
        runCatching { client.get("$baseUrl/api/events/$id/parecidos").body<List<FinancialEvent>>() }
            .getOrDefault(emptyList())

    // Mismo idioma que updateEventCategory: el server rechaza con 400 (lista vacía o categoría
    // mal escrita) o 422 (una categoría que Movi escribe sola), y ese texto es lo único que le
    // explica al dueño por qué no se guardó.
    override suspend fun recategorizarEnLote(ids: List<String>, category: String): RecategorizarEnLoteResponse {
        val response = client.put("$baseUrl/api/events/category-en-lote") {
            contentType(ContentType.Application.Json)
            setBody(RecategorizarEnLoteRequest(ids, category))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Mismo idioma que adjustCreditBalance: el server puede rechazar con 404 (otro usuario) o
    // 400 (categoría vacía o demasiado larga) y ese texto se pierde si se deserializa a ciegas.
    override suspend fun updateEventCategory(id: String, category: String): FinancialEvent {
        val response = client.put("$baseUrl/api/events/$id/category") {
            contentType(ContentType.Application.Json)
            setBody(UpdateEventCategoryRequest(category))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Mismo idioma que updateEventCategory: el server puede rechazar con 404 (evento
    // inexistente, de otro usuario o anulado) o 422 (fecha futura) y ese texto es lo único que
    // le explica al dueño por qué no se guardó — se pierde si se deserializa a ciegas.
    override suspend fun updateEventTimestamp(id: String, timestamp: Long): FinancialEvent {
        val response = client.put("$baseUrl/api/events/$id/timestamp") {
            contentType(ContentType.Application.Json)
            setBody(UpdateEventTimestampRequest(timestamp))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Mismo idioma que updateEventTimestamp: el server rechaza con 404 (movimiento inexistente,
    // de otro usuario o anulado) y ese texto es lo único que le explica al dueño por qué no se
    // guardó.
    // En la web no hay cola de subida: nada puede quedar rechazado.
    override suspend fun getMovimientosRechazados(): List<MovimientoRechazado> = emptyList()

    // Mismo idioma que updateEventRepeats: el 404 (inexistente, de otro o anulado) viaja con su texto.
    override suspend fun confirmEvent(id: String): FinancialEvent {
        val response = client.put("$baseUrl/api/events/$id/confirm")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun updateEventRepeats(id: String, repeats: Boolean): FinancialEvent {
        val response = client.put("$baseUrl/api/events/$id/repeats") {
            contentType(ContentType.Application.Json)
            setBody(UpdateEventRepeatsRequest(repeats))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Mismo idioma que updateEventTimestamp: el server rechaza con 400 (monto o concepto mal
    // formados), 404 (movimiento inexistente/de otro/anulado, o cuenta destino ajena) y 422 (una
    // pata de un par cambiando de cuenta, o una cuenta de otra moneda). Ese texto es lo único que
    // le explica al dueño por qué no se guardó — se pierde si se deserializa a ciegas.
    override suspend fun updateEvent(id: String, cambios: EdicionDeMovimiento): FinancialEvent {
        val response = client.put("$baseUrl/api/events/$id") {
            contentType(ContentType.Application.Json)
            setBody(cambios)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // 204 = «no hay marca», que es la respuesta normal y no un error. Cualquier otro fallo
    // (sin red, 500) también cae en null: este dato solo alimenta un aviso, y un aviso que no
    // se pudo cargar no puede impedir que el dueño corrija una fecha.
    override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? {
        val response = runCatching { client.get("$baseUrl/api/events/$id/occurrence") }.getOrNull()
            ?: return null
        if (response.status != HttpStatusCode.OK) return null
        return runCatching { response.body<EventOccurrenceMark>() }.getOrNull()
    }

    override suspend fun getCardPaymentCandidates(): List<FinancialEvent> =
        client.get("$baseUrl/api/events/card-payment-candidates").body()

    // Mismo idioma que updateEventCategory: 404 (evento inexistente o de otro usuario) trae su
    // propio texto y se pierde si se deserializa a ciegas.
    override suspend fun dismissCardPaymentCandidate(id: String) {
        val response = client.post("$baseUrl/api/events/$id/not-card-payment")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

    override suspend fun getDebitosAutomaticos(): List<DebitoAutomaticoPorConfirmar> =
        client.get("$baseUrl/api/debitos-automaticos").exigirExito().body()

    override suspend fun confirmarDebitoAutomatico(pedido: com.jvillada.movi.shared.model.ConfirmarDebitoAutomatico): List<FinancialEvent> =
        client.post("$baseUrl/api/debitos-automaticos/confirmar") {
            contentType(ContentType.Application.Json)
            setBody(pedido)
        }.exigirExito().body()

    override suspend fun descartarDebitoAutomatico(ruleId: String, periodo: String) {
        client.post("$baseUrl/api/debitos-automaticos/descartar") {
            contentType(ContentType.Application.Json)
            setBody(DescartarDebitoAutomatico(ruleId, periodo))
        }.exigirExito()
    }

    // ── Por qué acá NO alcanza con `.body()` ──────────────────────────────────────────────
    //
    // El cliente no tiene `expectSuccess`, así que un 401 no lanza nada por sí solo: lo que
    // explotaba era `.body()`, intentando deserializar `AuthResponse` de un cuerpo que el
    // servidor mandó como `text/plain` ("Invalid credentials"). El error resultante no dice
    // "401" en ninguna parte — y es EXACTAMENTE el mismo error que sale de un 500 cuyo cuerpo
    // es el HTML del proxy. Con la red caída sale un tercer error distinto, pero el `onFailure`
    // de la pantalla los trataba a los tres igual y acusaba a la contraseña.
    //
    // Mismo idioma que `dismissCardPaymentCandidate` y compañía: si no fue 2xx, se lanza
    // [ApiException] con el código, que es el dato que la pantalla necesita para distinguir
    // "te rechazaron las credenciales" de "no se pudo hablar con el servidor".
    override suspend fun register(request: RegisterRequest): AuthResponse =
        client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.bodyOrApiException()

    override suspend fun login(request: LoginRequest): AuthResponse =
        client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.bodyOrApiException()

    private suspend fun HttpResponse.bodyOrApiException(): AuthResponse {
        if (!status.isSuccess()) {
            throw ApiException(status.value, runCatching { bodyAsText() }.getOrNull())
        }
        return body()
    }

    // No se usa .body(): la respuesta puede ser 202 o 503 y lo que la UI necesita es el código.
    override suspend fun requestPasswordReset(request: PasswordResetRequest): Int =
        client.post("$baseUrl/api/auth/password-reset/request") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.status.value

    // `exigirExito()` y no `.body()` a secas, por el mismo motivo que los cuatro de abajo: esta
    // ruta explica sus rechazos en el cuerpo, en español y a mano —«el extracto es muy largo»,
    // «falta la clave», «no encontramos movimientos»— y sin esto el cuerpo se perdía adentro de la
    // excepción de deserialización. Escribir un buen mensaje de error no sirve de nada si el
    // camino por el que viaja lo tira.
    // `conCreditos=1`: esta app sabe mostrar la propuesta del extracto de un crédito (ver
    // `StatementParseResult.extractoDeCredito`); sin el parámetro el server lo rechaza como siempre.
    // `conSaldos=1`: y la del saldo de un extracto de Skandia o de la AFC (`extractoDeSaldo`).
    override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult =
        client.post("$baseUrl/api/statements/upload?conCreditos=1&conSaldos=1") {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    append(HttpHeaders.ContentType, mimeType)
                })
            }))
        }.exigirExito().body()

    // `exigirExito()` por el mismo motivo que `uploadStatement`: el server explica el rechazo en
    // el cuerpo («no encontramos movimientos», «falta la clave»), y sin esto se pierde adentro de
    // la excepción de deserialización.
    override suspend fun readStatementFromDocument(id: String): StatementParseResult =
        client.post("$baseUrl/api/documents/$id/leer-extracto?conCreditos=1&conSaldos=1").exigirExito().body()

    override suspend fun aceptarElExtractoDelCredito(
        accountId: String,
        resumen: com.jvillada.movi.shared.model.ResumenDelExtractoDeCredito,
    ): CreditSummary =
        client.post("$baseUrl/api/credits/$accountId/extracto") {
            contentType(ContentType.Application.Json)
            setBody(resumen)
        }.exigirExito().body()

    override suspend fun actualizarElSaldoConElExtracto(
        accountId: String,
        resumen: com.jvillada.movi.shared.model.ResumenDelExtractoDeSaldo,
    ): AdjustAccountBalanceResponse =
        client.post("$baseUrl/api/accounts/$accountId/saldo-del-extracto") {
            contentType(ContentType.Application.Json)
            setBody(resumen)
        }.exigirExito().body()

    // Los cuatro comprueban el status, como el resto del archivo. Sin esto `deleteDocument`
    // decía «listo» ante un 500 —el documento seguía ahí y la lista se recargaba igual— y un 401
    // al subir salía como «Algo salió mal» en vez de «Tu sesión expiró».
    override suspend fun payInstallment(request: CreatePagoDeCuotaRequest): PagoDeCuotaResult =
        client.post("$baseUrl/api/payments/installment") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.exigirExito().body()

    override suspend fun vincularPagoDeDeuda(eventId: String, request: VincularPagoDeDeudaRequest): PagoDeCuotaResult =
        client.put("$baseUrl/api/events/$eventId/vincular-deuda") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.exigirExito().body()

    override suspend fun getDocuments(): List<Documento> =
        client.get("$baseUrl/api/documents").exigirExito().body()

    override suspend fun uploadDocument(
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        tipo: TipoDeDocumento,
        accountId: String?,
        periodo: String?,
        notas: String?,
    ): Documento =
        client.post("$baseUrl/api/documents") {
            setBody(MultiPartFormDataContent(formData {
                // Los campos de texto van ANTES del archivo: el server los lee en cualquier
                // orden, pero un multipart con el binario primero obliga a bufferearlo entero
                // antes de conocer el tipo, y estos archivos pesan megas.
                append("tipo", tipo.name)
                accountId?.let { append("accountId", it) }
                periodo?.let { append("periodo", it) }
                notas?.let { append("notas", it) }
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    append(HttpHeaders.ContentType, mimeType)
                })
            }))
        }.exigirExito().body()

    override suspend fun getDocumentLink(id: String): EnlaceDeDescarga {
        val relativo: EnlaceDeDescarga = client.post("$baseUrl/api/documents/$id/link").exigirExito().body()
        // El server no sabe con qué origen lo llamaron; el cliente sí. Componerla acá evita que
        // cada pantalla tenga que acordarse de anteponer el baseUrl —y que una se olvide.
        return relativo.copy(url = baseUrl + relativo.url)
    }

    override suspend fun updateDocument(id: String, cambios: EdicionDeDocumento): Documento =
        client.patch("$baseUrl/api/documents/$id") {
            contentType(ContentType.Application.Json)
            setBody(cambios)
        }.exigirExito().body()

    override suspend fun deleteDocument(id: String) {
        client.delete("$baseUrl/api/documents/$id").exigirExito()
    }

    // Ola 2 · «Compartir con Movi». `reusar`: si ese mismo archivo ya estaba guardado, el server
    // devuelve ese documento en vez de otro igual (ver `POST /api/documents`).
    override suspend fun subirPapel(fileName: String, bytes: ByteArray, mimeType: String): Documento =
        client.post("$baseUrl/api/documents") {
            setBody(MultiPartFormDataContent(formData {
                append("tipo", TipoDeDocumento.OTRO.name)
                append("notas", NOTA_DEL_PAPEL_COMPARTIDO)
                append("reusar", "true")
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                    append(HttpHeaders.ContentType, mimeType)
                })
            }))
        }.exigirExito().body()

    // `exigirExito()` por lo mismo que los extractos: el motivo («el PDF tiene contraseña», «falta
    // la clave») viaja en el cuerpo y es lo único que el dueño puede usar.
    override suspend fun leerPapel(documentoId: String, anotarAunqueEsteAnotado: Boolean): LecturaDelPapel =
        // `conSaldos=1`: esta app sabe mostrar la propuesta del saldo de un extracto de Skandia o de
        // la AFC; un APK viejo no lo pide y el server le contesta como siempre.
        client.post("$baseUrl/api/documents/$documentoId/leer?conSaldos=1" + if (anotarAunqueEsteAnotado) "&anotarAunque=true" else "")
            .exigirExito().body()

    override suspend fun confirmarComprobante(smsId: String, eventoId: String) {
        client.post("$baseUrl/api/sms/$smsId/confirm?eventoId=$eventoId").exigirExito()
    }

    override suspend fun confirmSmsCon(id: String, eventoId: String) {
        client.post("$baseUrl/api/sms/$id/confirm?eventoId=$eventoId").exigirExito()
    }

    override suspend fun confirmarConLasDosPatas(smsId: String, patas: com.jvillada.movi.shared.model.DosPatasDelAviso): MismoPagoConfirmado =
        client.post("$baseUrl/api/sms/$smsId/confirm") {
            contentType(ContentType.Application.Json)
            setBody(patas)
        }.exigirExito().body()

    override suspend fun confirmarEnPartes(smsId: String, partes: com.jvillada.movi.shared.model.PartesDelAviso): MismoPagoConfirmado =
        client.post("$baseUrl/api/sms/$smsId/confirm") {
            contentType(ContentType.Application.Json)
            setBody(partes)
        }.exigirExito().body()

    override suspend fun repartirElMovimiento(eventoId: String, partes: com.jvillada.movi.shared.model.PartesDelAviso): MismoPagoConfirmado =
        client.post("$baseUrl/api/events/$eventoId/repartir") {
            contentType(ContentType.Application.Json)
            setBody(partes)
        }.exigirExito().body()

    override suspend fun getAvisosConfirmadosSinMovimiento(): List<com.jvillada.movi.shared.model.AvisoConfirmadoSinMovimiento> =
        client.get("$baseUrl/api/sms/confirmados-sin-movimiento").exigirExito().body()

    override suspend fun getAvisosPendientesParecidos(
        monto: Long,
        tipo: com.jvillada.movi.shared.model.TransactionType,
        moneda: String,
    ): List<com.jvillada.movi.shared.model.AvisoPendienteParecido> =
        client.get("$baseUrl/api/sms/pendientes-parecidos") {
            parameter("monto", monto)
            parameter("tipo", tipo.name)
            parameter("moneda", moneda)
        }.exigirExito().body()

    override suspend fun getOrigenesMudos(): List<com.jvillada.movi.shared.model.OrigenMudo> =
        client.get("$baseUrl/api/sms/origenes-mudos").exigirExito().body()

    /** La comprobación de status del archivo, extraída para no repetirla cuatro veces. */
    private suspend fun HttpResponse.exigirExito(): HttpResponse {
        if (!status.isSuccess()) {
            throw ApiException(status.value, runCatching { bodyAsText() }.getOrNull())
        }
        return this
    }

    override suspend fun importStatement(decision: ImportDecision) {
        client.post("$baseUrl/api/statements/import") {
            contentType(ContentType.Application.Json)
            setBody(decision)
        }
    }

    override suspend fun deleteStatementImport(id: String) {
        val response = client.delete("$baseUrl/api/statements/imports/$id")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

    override suspend fun getStatementImports(): List<StatementImport> =
        client.get("$baseUrl/api/statements/imports").body()

    override suspend fun getStatementImportDetail(id: String): StatementImportDetail =
        client.get("$baseUrl/api/statements/imports/$id").body()

    override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? {
        val response = client.get("$baseUrl/api/screens/$slug") {
            cachedVersion?.let { header("If-None-Match", it.toString()) }
        }
        // No safeCall en este repo: chequear 304 ANTES de .body() (lección NeoVita).
        if (response.status == HttpStatusCode.NotModified) return null
        return response.body()
    }

    override suspend fun getScreenParaEditar(slug: String): ScreenDefinition? =
        client.get("$baseUrl/api/screens/$slug") { parameter("crudo", "1") }.exigirExito().body()

    override suspend fun getPreferenciasDelInicio(): com.jvillada.movi.shared.model.PreferenciasDelInicio =
        client.get("$baseUrl/api/inicio/preferencias").exigirExito().body()

    override suspend fun guardarPreferenciasDelInicio(
        preferencias: com.jvillada.movi.shared.model.PreferenciasDelInicio,
    ): com.jvillada.movi.shared.model.PreferenciasDelInicio =
        client.put("$baseUrl/api/inicio/preferencias") {
            contentType(ContentType.Application.Json)
            setBody(preferencias)
        }.exigirExito().body()

    override suspend fun putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition =
        client.put("$baseUrl/api/screens/$slug") {
            contentType(ContentType.Application.Json)
            setBody(ScreenDefinition(slug = slug, version = 0, sections = sections))
        }.body()

    override suspend fun restoreScreen(slug: String): ScreenDefinition =
        client.post("$baseUrl/api/screens/$slug/restore").body()

    override suspend fun isScreenAdmin(): Boolean =
        runCatching {
            client.get("$baseUrl/api/screens/admin/status").body<Map<String, Boolean>>()["isAdmin"] == true
        }.getOrDefault(false)

    override suspend fun getUserProfile(): UserProfile =
        client.get("$baseUrl/api/users/me").body()

    override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
        val response = client.put("$baseUrl/api/users/me") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun marcarPrimerUso(): UserProfile =
        client.post("$baseUrl/api/users/me/primer-uso").exigirExito().body()

    override suspend fun getDireccionDeCorreo(): com.jvillada.movi.shared.model.DireccionDeCorreo =
        client.get("$baseUrl/api/correo-entrante/direccion").exigirExito().body()

    override suspend fun changePassword(request: ChangePasswordRequest): String? {
        val response = client.put("$baseUrl/api/users/me/password") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.headers["X-Movi-Token"]?.takeIf { it.isNotBlank() }
    }

    override suspend fun getExportLink(): EnlaceDeDescarga {
        val relativo: EnlaceDeDescarga = client.post("$baseUrl/api/export/enlace").exigirExito().body()
        return relativo.copy(url = baseUrl + relativo.url)
    }

    override suspend fun cerrarSesionesEnTodosLosAparatos() {
        client.post("$baseUrl/api/users/me/cerrar-sesiones").exigirExito()
    }

    // La contraseña va en el CUERPO del DELETE, nunca en la URL: ver `BorrarLaCuentaRequest`.
    override suspend fun borrarLaCuenta(request: com.jvillada.movi.shared.model.BorrarLaCuentaRequest) {
        client.delete("$baseUrl/api/me") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.exigirExito()
    }
}
