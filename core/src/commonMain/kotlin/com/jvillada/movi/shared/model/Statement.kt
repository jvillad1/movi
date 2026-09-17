package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ParsedTransaction(
    val id: String,           // UUID, session-scoped
    val date: String,         // "2025-05-28"
    val merchant: String,
    val amount: Long,         // native currency, always positive
    val currency: String = "COP",
    val type: TransactionType,
    val category: String,
    val description: String,
    val rawText: String,
)

@Serializable
data class ReconciliationMatch(
    val parsed: ParsedTransaction,
    val existingEventId: String,
    val existingEvent: FinancialEvent,
    val matchConfidence: Float,
)

@Serializable
data class StatementParseResult(
    val statementId: String,
    val bankName: String,
    val period: String,
    val newTransactions: List<ParsedTransaction>,
    val matches: List<ReconciliationMatch>,
    /**
     * Los últimos cuatro dígitos de tarjeta o cuenta que nombra el extracto (del nombre del archivo
     * y de los números enmascarados del PDF), del más confiable al menos. La app los usa para elegir
     * la cuenta de destino por el número, como ya hace con los SMS, en vez de adivinar por el nombre
     * del banco — que proponía «Bancolombia Ahorros» para el extracto de la Master Black.
     */
    val numerosDeCuenta: List<String> = emptyList(),
    /**
     * **El papel que esta lectura archivó**, o `null` si no se pudo archivar.
     *
     * Viaja de vuelta para que la importación le pueda colgar la cuenta que el dueño eligió (ver
     * [ImportDecision.documentoId]). Al subir todavía no se sabe cuál es: la cuenta se elige en la
     * pantalla de revisión, después. Si el archivo ya estaba guardado —subirlo, mirarlo, volver
     * atrás y volver a subirlo— es el id del que ya estaba, no uno nuevo.
     */
    val documentoId: String? = null,
)

@Serializable
data class ReconciliationDecision(
    val parsedId: String,
    val existingEventId: String,
    val confirm: Boolean,
    val categorySource: FieldSource,
    val descriptionSource: FieldSource,
    val merchantSource: FieldSource,
    val parsed: ParsedTransaction,
)

@Serializable
enum class FieldSource { MANUAL, STATEMENT }

@Serializable
data class ImportDecision(
    val statementId: String,
    val accountId: String,
    val bankName: String = "",
    val period: String = "",
    val imports: List<ParsedTransaction>,
    val reconciliations: List<ReconciliationDecision>,
    val skipped: List<String>,
    /**
     * El documento que archivó la lectura de este extracto (ver [StatementParseResult.documentoId]).
     * El importe le cuelga [accountId]: el papel es de la cuenta contra la que se importó.
     *
     * Opcional a propósito: un cliente viejo no lo manda y la importación funciona igual, solo que
     * el extracto archivado se queda sin cuenta —que es exactamente como estaba antes.
     */
    val documentoId: String? = null,
)

@Serializable
data class MerchantRule(
    val merchantPattern: String,
    val category: String,
)

@Serializable
data class StatementImport(
    val id: String,
    val accountId: String,
    val bankName: String,
    val period: String,
    val importedAt: Long,
    val importedCount: Int,
    val reconciledCount: Int,
)

@Serializable
data class StatementImportDetail(
    val import: StatementImport,
    val events: List<FinancialEvent>,
)
