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
    /**
     * Vuelta 63: a cuántas cuotas va la compra, cuando el extracto lo dice (la columna de cuotas de los
     * movimientos de la tarjeta de Bancolombia). `null` = no se sabe. Al importar queda en
     * [FinancialEvent.cuotas].
     */
    val cuotas: Int? = null,
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
    /**
     * **Cuáles de [newTransactions] son cargos o abonos del banco** (4x1000, cuota de manejo,
     * intereses, rendimientos), y qué movimientos ya anotados pueden ser su suma. La revisión los
     * agrupa en «Cargos y abonos del banco» y no propone los que ya están anotados en la cuenta del
     * extracto (ver [estadoDelCargo]). Las filas ya traen la categoría del dueño para su cargo.
     *
     * Con default y aparte de las filas: un APK viejo lo ignora y las sigue viendo como nuevas.
     */
    val cargosDelBanco: List<CargoEnElExtracto> = emptyList(),
    /**
     * **El extracto era de un crédito**: no trae movimientos sino cómo va la deuda, y esto es la
     * propuesta de actualizar Movi con eso (ver [PropuestaDelExtractoDeCredito]). La revisión lo
     * muestra en vez de la lista de movimientos, que viene vacía.
     *
     * Solo lo manda el server a quien lo pide (`?conCreditos=1`): un APK viejo abriría una revisión
     * vacía, así que a él se le sigue contestando el 422 de siempre («resumen de crédito»).
     */
    val extractoDeCredito: PropuestaDelExtractoDeCredito? = null,
    /**
     * **El extracto solo decía cuánto hay** (el fondo voluntario de Skandia, la AFC de Davibank):
     * la propuesta de poner ese saldo en la cuenta (ver [PropuestaDelExtractoDeSaldo]). La revisión
     * lo muestra en vez de la lista de movimientos, que viene vacía.
     *
     * Solo lo manda el server a quien lo pide (`?conSaldos=1`), igual que [extractoDeCredito]: un
     * APK viejo abriría una revisión vacía, que se lee como «este mes ya estaba conciliado».
     */
    val extractoDeSaldo: PropuestaDelExtractoDeSaldo? = null,
    /**
     * Vuelta 129 · **el extracto es de una tarjeta de crédito** y esto es lo que dice de ella (ver
     * [TarjetaDelExtracto]). `null` si el lector no lo sabe: no quiere decir «no es una tarjeta».
     *
     * Con esto la revisión busca la cuenta solo entre las tarjetas —un extracto de la tarjeta de Nu ya
     * no cae en «Nu Ahorros» por llamarse igual—, ofrece crear la tarjeta si no está en Movi y advierte
     * si se eligió una cuenta que no es una tarjeta. Solo lo manda el server a quien lo pide
     * (`?conTarjeta=1`), como [extractoDeSaldo].
     */
    val tarjeta: TarjetaDelExtracto? = null,
    /**
     * Vuelta 129 · **la cuenta que el dueño le puso al papel** al subirlo a Documentos, o `null`. La
     * revisión la usa como la elegida: la hoja de subida dice «Los movimientos del extracto se anotan
     * en esta cuenta», y esto es lo que lo hace cierto.
     */
    val cuentaDelPapel: String? = null,
    /**
     * Vuelta 129 (revisión) · **las tarjetas del dueño que ya se cuadraron después del día de la deuda
     * del extracto** (un ajuste de saldo o un saldo inicial posterior, sin anulados). A esas no se les
     * propone «Cuadrar la deuda con el extracto»: el ajuste a esa fecha más ese saldo inicial contarían la
     * deuda dos veces (y el server contesta 409). Solo con `?conTarjeta=1` y si el extracto dice su deuda.
     */
    val tarjetasCuadradasDespues: List<String> = emptyList(),
)

/**
 * Vuelta 129 · **lo que el extracto de una tarjeta de crédito dice de la tarjeta**: el banco, los
 * últimos cuatro, el día de corte, el día de pago y el cupo, cuando los trae. Sirve para crear la
 * tarjeta ya llena si no está en Movi. Nada se inventa: lo que el extracto no dice va en `null`.
 */
@Serializable
data class TarjetaDelExtracto(
    /** El banco, como lo nombra la lectura («Nu», «Bancolombia»). */
    val banco: String,
    val ultimosCuatro: String? = null,
    val diaDeCorte: Int? = null,
    val diaDePago: Int? = null,
    /** El cupo total, en pesos (o en [moneda]). */
    val cupo: Long? = null,
    val moneda: String = "COP",
    /**
     * **Lo que se debía**, según el extracto, al final de [deudaHasta] (en Nu: la «DEUDA TOTAL HASTA EL
     * …», solo si cuadra al centavo con el pago mínimo − los abonos + la deuda restante; al peso más
     * cercano). Con esto, después de importar, la revisión propone UN ajuste de la tarjeta a esa cifra
     * ([DeudaDelExtracto]). `null` si el extracto no lo dice o no cuadra: no se propone nada. Puede ser
     * negativa (saldo a favor): ahí tampoco se propone.
     */
    val deudaAlCorte: Long? = null,
    /**
     * El último día que cuenta esa deuda, `YYYY-MM-DD` («hasta el 11 octubre»; en Nu, el día antes del
     * corte: lo del día del corte es del extracto siguiente). Sin él tampoco se propone cuadrar.
     */
    val deudaHasta: String? = null,
)

/**
 * Vuelta 129 · `POST /api/accounts/{id}/deuda-del-extracto`: **cuadrar la deuda de una tarjeta con
 * su extracto**, con un solo ajuste de saldo al final del día [hasta] (el de siempre,
 * `balanceAdjustmentEventFor`). Nunca automático: lo pide el dueño desde la revisión.
 */
@Serializable
data class DeudaDelExtracto(
    val deuda: Long,
    /** El último día que cuenta la deuda del extracto, `YYYY-MM-DD` («hasta el 11 octubre»). */
    val hasta: String,
    val moneda: String = "COP",
)

/**
 * La deuda para cuadrar de [this], si el extracto la dice con su día y no es un saldo a favor (una
 * deuda negativa no se ofrece: el server no la acepta).
 */
fun TarjetaDelExtracto.deudaParaCuadrar(): DeudaDelExtracto? {
    val deuda = deudaAlCorte?.takeIf { it >= 0 } ?: return null
    val hasta = deudaHasta ?: return null
    return DeudaDelExtracto(deuda = deuda, hasta = hasta, moneda = moneda)
}

/**
 * El nombre con que se propone crear la tarjeta de [t]: el banco, «Tarjeta» y los últimos cuatro si
 * se saben («Nu Tarjeta 5237»). Los cuatro dígitos en el nombre son los que le permiten a Movi
 * reconocerla sola en el próximo aviso o extracto (los avisos se emparejan por el número del nombre).
 */
fun nombreDeLaTarjetaNueva(t: TarjetaDelExtracto): String =
    listOfNotNull(t.banco.trim().ifBlank { null }, "Tarjeta", t.ultimosCuatro).joinToString(" ")

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
