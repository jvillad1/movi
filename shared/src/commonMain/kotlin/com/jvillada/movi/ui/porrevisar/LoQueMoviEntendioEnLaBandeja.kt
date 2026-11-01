package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.DudaDelAviso
import com.jvillada.movi.shared.model.GrupoDeAvisos
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.ui.components.nombreCortoDeCuenta
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.shared.model.dudasDelAviso
import com.jvillada.movi.shared.model.esAntesDeLaCuenta
import com.jvillada.movi.shared.model.esIdDeComprobante
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco
import com.jvillada.movi.ui.components.conLaCuentaQueSugiereMovi
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.resolverCuentaDelBanco
import com.jvillada.movi.ui.components.toUserMessage
import com.jvillada.movi.ui.sms.advertenciaDeBancos
import com.jvillada.movi.ui.sms.anotarElMovimientoDelAviso
import com.jvillada.movi.ui.sms.avisosDelPago
import com.jvillada.movi.ui.sms.avisosQueDicenLaCuenta
import com.jvillada.movi.ui.sms.deudaPropuestaDelAviso
import com.jvillada.movi.ui.sms.destinoPropuestoDelAviso
import com.jvillada.movi.ui.sms.momentoDelMovimientoDelAviso
import com.jvillada.movi.ui.sms.movimientoDelAviso
import com.jvillada.movi.ui.sms.origenPropuestoDelAviso
import com.jvillada.movi.ui.sms.resolverCuentaDelPago
import kotlin.math.roundToLong

/*
 * # La bandeja que se vacía sola, sin dibujo
 *
 * Qué pago de «Por revisar» ya entendió Movi —y entonces se confirma desde la fila o en el lote— y
 * qué va a anotar. El criterio es `dudasDelAviso` de `:core`; acá se le suma lo que solo sabe la app
 * —la cuenta, que se resuelve con las MISMAS funciones que Reconciliar— y se arma el movimiento con
 * el mismo camino ([anotarElMovimientoDelAviso]).
 */

/**
 * **Lo que Movi va a anotar desde la fila**: un movimiento suelto, con la cuenta, la categoría y el
 * monto que se ven en la línea de la tarjeta ([linea]).
 *
 * @property cabezaId el aviso que pinta la tarjeta (el más reciente del pago).
 * @property sms el aviso del que sale la propuesta ([PropuestaDelAviso.avisoId]).
 * @property pago los avisos del pago, con la propuesta primero, si es uno de varios; `null` si es suelto.
 */
data class AnotacionEntendida(
    val cabezaId: String,
    val sms: SmsMessage,
    val pago: GrupoDeAvisos?,
    val leido: ParsedSms,
    val cuenta: Account,
    val categoria: String,
) {
    val monto: Long get() = leido.amount.roundToLong()

    /** «Comida · Bancolombia Ahorros · $46.000»: lo que se va a anotar, en una línea. */
    val linea: String get() = lineaDeLoQueVaAAnotar(categoria, cuenta.name, monto, leido.currency)

    /** Los avisos que este pago cierra: el suelto, o todos los del pago. */
    val avisos: List<String> get() = pago?.miembros?.map { it.id } ?: listOf(sms.id)
}

/** «Comida · Bancolombia Ahorros · $46.000». */
fun lineaDeLoQueVaAAnotar(categoria: String, cuenta: String, monto: Long, moneda: String): String =
    "$categoria · $cuenta · ${formatMoney(monto, moneda)}"

/**
 * Lo que Movi entendió de un pago: lo que anotaría, o por qué todavía no.
 *
 * @property advertencia «Ojo: el aviso es de Falabella y la cuenta es de Bancolombia» cuando la
 *   cuenta que Movi propone —segura o no— no es del banco que mandó el aviso ([advertenciaDeBancos]).
 */
data class LoQueMoviEntendio(
    val anotacion: AnotacionEntendida?,
    val dudas: Set<DudaDelAviso>,
    val advertencia: String? = null,
    /**
     * **«Transporte · Bancolombia Ahorros»** (vuelta 44): la categoría y la cuenta que Movi propone,
     * entendidas o no —son las que Revisar aviso va a mostrar—, para la tarjeta de la bandeja. Se ve
     * sin abrir el aviso, y así el mal categorizado salta a la vista. `null` sin lectura.
     */
    val linea: String? = null,
    /**
     * **Vuelta 46: lo único que falta es la categoría** («Otros», o una que el dueño nunca usó) —con el
     * monto, la cuenta segura y nada más que revisar—. La tarjeta la pide ahí mismo («Elegir
     * categoría») y, elegida ([loQueMoviEntendio] con `categoriaElegida`), queda confirmable.
     */
    val faltaSoloLaCategoria: Boolean = false,
    /**
     * Vuelta 91 · **el título de un traspaso entre cuentas suyas** («Traspaso de Bancolombia ·8133 a
     * Nu»), en vez del nombre del beneficiario («NU Compañía de Financiamiento»): lo que pasó, dicho con
     * las cuentas de Movi ([tituloDelTraspaso]). `null` en todo lo que no es un traspaso.
     */
    val titulo: String? = null,
)

/**
 * Vuelta 91 · «Traspaso de Bancolombia ·8133 a Nu», con el nombre corto de cada cuenta; con una sola
 * punta conocida, «Traspaso a Nu» o «Traspaso de Bancolombia ·8133». `null` sin ninguna. Pura.
 */
fun tituloDelTraspaso(origen: Account?, destino: Account?): String? {
    val de = origen?.let { nombreCortoDeCuenta(it.name) }
    val a = destino?.let { nombreCortoDeCuenta(it.name) }
    return when {
        de != null && a != null -> "Traspaso de $de a $a"
        a != null -> "Traspaso a $a"
        de != null -> "Traspaso de $de"
        else -> null
    }
}

/**
 * **Las dos puntas de un traspaso entre cuentas suyas**, como las va a proponer Reconciliar: el lado
 * que sale (un gasto en «Traspaso», o con [ParsedSms.traspasoHaciaId]) va de la cuenta del aviso a la
 * que Movi propone; el que entra (un ingreso en «Traspaso»), de la que Movi propone a la del aviso. Un
 * [Pair] de `null` si no es un traspaso.
 */
internal fun puntasDelTraspaso(leido: ParsedSms, resuelta: com.jvillada.movi.ui.components.CuentaDelBanco, cuentas: List<Account>): Pair<Account?, Account?>? {
    val esTraspaso = leido.category == TRANSFER_CATEGORY || leido.traspasoHaciaId != null || leido.traspasoDesdeId != null
    if (!esTraspaso) return null
    return if (leido.type == TransactionType.INCOME) {
        origenPropuestoDelAviso(leido, resuelta, cuentas) to resuelta.cuenta
    } else {
        resuelta.cuenta to destinoPropuestoDelAviso(leido, resuelta, cuentas)
    }
}

/** El porqué de una categoría que el dueño eligió en la tarjeta de Por revisar (vuelta 46). */
const val ELEGIDA_EN_LA_TARJETA: String = "La elegiste aquí"

/** «Transporte · Bancolombia Ahorros», lo que haya de las dos; `null` sin ninguna. */
fun lineaDeLoEntendido(categoria: String?, cuenta: String?): String? =
    listOfNotNull(categoria?.trim()?.takeIf { it.isNotEmpty() }, cuenta?.trim()?.takeIf { it.isNotEmpty() })
        .joinToString(" · ").ifEmpty { null }

/** Las cuentas que se resolvieron por un dato y no por defecto: el número, el banco o la marca, lo que sabe Movi del pago. */
private val CUENTAS_SEGURAS = setOf(
    OrigenDeLaCuentaDelBanco.POR_EL_NUMERO,
    OrigenDeLaCuentaDelBanco.POR_EL_BANCO,
    OrigenDeLaCuentaDelBanco.SUGERIDA_POR_MOVI,
)

/**
 * **¿Movi entendió el pago de [cabeza]?** Con la propuesta del server ([propuestas]), las cuentas del
 * dueño y la bandeja entera ([mensajes], para encontrar los avisos del pago).
 *
 * La cuenta se resuelve **igual que Reconciliar**: un pago avisado varias veces con
 * [resolverCuentaDelPago] sobre sus avisos (la propuesta primero), uno suelto con
 * [resolverCuentaDelBanco], y en los dos [conLaCuentaQueSugiereMovi]. Y lo que en Reconciliar haría
 * aparecer un «Sale de … · entra a …» —una cuenta suya nombrada como destino u origen, una deuda que
 * el aviso nombra o que Movi reconoció— es una duda de dos patas.
 */
fun loQueMoviEntendio(
    cabeza: SmsMessage,
    mensajes: List<SmsMessage>,
    propuestas: PropuestasDeLaBandeja,
    cuentas: List<Account>,
    ahora: Long,
    /**
     * Vuelta 46: la categoría que el dueño eligió en la tarjeta (cuando [LoQueMoviEntendio.faltaSoloLaCategoria]).
     * Manda sobre la que leyó Movi, como si la hubiera elegido en Revisar aviso.
     */
    categoriaElegida: String? = null,
): LoQueMoviEntendio {
    val noSe = LoQueMoviEntendio(null, setOf(DudaDelAviso.NO_SE_LEYO))
    if (cabeza.state != SMS_STATE_PENDING || cuentas.isEmpty()) return noSe
    val grupo = cabeza.grupoId
    val avisos = if (grupo != null) avisosDelPago(cabeza, mensajes) else listOf(cabeza)
    // Ya anotado con otro aviso del pago: la tarjeta ofrece «Cerrar», no «Confirmar».
    if (avisos.any { it.state == SMS_STATE_CONFIRMED } || avisos.any { it.yaAnotadoCon != null }) return noSe
    val propuesta: PropuestaDelAviso = propuestas.propuestas.firstOrNull { p ->
        if (grupo != null && avisos.size > 1) p.grupoId == grupo else p.avisoId == cabeza.id
    } ?: return noSe
    val delQueSale = avisos.firstOrNull { it.id == propuesta.avisoId } ?: return noSe
    val pago = if (grupo != null && avisos.size > 1) {
        GrupoDeAvisos(
            grupoId = grupo,
            // Como `GET /api/sms/grupo/{id}`: la propuesta primero, después del más viejo al más nuevo.
            miembros = listOf(delQueSale) + avisos.filter { it.id != delQueSale.id }
                .sortedWith(compareBy({ momentoDelSms(it.time, Long.MAX_VALUE) }, { it.id })),
            propuestaDe = delQueSale.id,
        )
    } else null
    val leidoPorMovi = propuesta.leido ?: return noSe
    // La elegida a mano cuenta como una categoría que el dueño decidió (ver `categoriaEntendida`).
    val leido = categoriaElegida?.takeIf { it.isNotBlank() }
        ?.let { leidoPorMovi.copy(category = it, aprendidoDe = leidoPorMovi.aprendidoDe ?: ELEGIDA_EN_LA_TARJETA) }
        ?: leidoPorMovi

    val uso = if (leido.type == TransactionType.INCOME) UsoDeCuenta.DESTINO_DE_INGRESO else UsoDeCuenta.ORIGEN_DE_GASTO
    val resuelta = conLaCuentaQueSugiereMovi(
        resuelta = if (pago != null) resolverCuentaDelPago(cuentas, uso, pago.miembros, elegidaAMano = null)
        else resolverCuentaDelBanco(cuentas, uso, banco = cabeza.bank, elegidaAMano = null, textoDelMensaje = cabeza.text),
        accounts = cuentas,
        sugeridaId = leido.cuentaSugeridaId,
    )
    val cuenta = resuelta.cuenta?.takeIf { resuelta.origen in CUENTAS_SEGURAS }
    // La cuenta que se propone, aunque no sea segura: es la que Reconciliar va a mostrar.
    // Vuelta 91: el depósito que entró a Nu no es «el aviso de Nu» de una cuenta de Bancolombia.
    val advertencia = advertenciaDeBancos(avisosQueDicenLaCuenta(avisos).map { it.bank }, resuelta.cuenta)
    val nombraOtraCuentaSuya = destinoPropuestoDelAviso(leido, resuelta, cuentas) != null ||
        origenPropuestoDelAviso(leido, resuelta, cuentas) != null ||
        deudaPropuestaDelAviso(resuelta, leido.deudaSugeridaId, cuentas) != null
    val momento = momentoDelMovimientoDelAviso(delQueSale, pago, leido, ahora)
    val dudas = dudasDelAviso(
        leido = leido,
        coincidencias = propuesta.coincidencias.size,
        destinoSinNombre = propuesta.destinoSinNombre,
        cuentaSegura = cuenta != null,
        nombraOtraCuentaSuya = nombraOtraCuentaSuya,
        categoriasDelDueno = propuestas.categoriasDelDueno,
        antesDeLaCuenta = cuenta != null && esAntesDeLaCuenta(momento, propuestas.inicioDeLasCuentas[cuenta.id]),
        seReparte = propuesta.seReparte,
    )
    val linea = lineaDeLoEntendido(leido.category, resuelta.cuenta?.name)
    val faltaSoloLaCategoria = dudas == setOf(DudaDelAviso.SIN_CATEGORIA) && cuenta != null
    // Vuelta 91: un traspaso entre cuentas suyas se titula con las cuentas, no con el beneficiario.
    val titulo = puntasDelTraspaso(leido, resuelta, cuentas)?.let { (de, a) -> tituloDelTraspaso(de, a) }
    if (dudas.isNotEmpty() || cuenta == null) return LoQueMoviEntendio(null, dudas, advertencia, linea, faltaSoloLaCategoria, titulo)
    return LoQueMoviEntendio(
        AnotacionEntendida(cabezaId = cabeza.id, sms = delQueSale, pago = pago, leido = leido, cuenta = cuenta, categoria = leido.category),
        emptySet(),
        advertencia,
        linea,
        titulo = titulo,
    )
}

/**
 * **Anota lo que Movi entendió**, por el MISMO camino que Reconciliar: el mismo movimiento
 * ([movimientoDelAviso], con el comercio que leyó Movi) y la misma confirmación
 * ([anotarElMovimientoDelAviso]: el pago entero en una transacción, o el movimiento y el aviso).
 * Devuelve el movimiento creado, o `null` si ya estaba anotado (ver [anotarElMovimientoDelAviso]).
 */
suspend fun confirmarLoEntendido(
    anotacion: AnotacionEntendida,
    ahora: Long,
    alCrearElMovimiento: () -> Unit = {},
    /** Ola 5 · la notificación confirma contra el server directo, con los mismos métodos. */
    repo: WalletRepository = Repositories.wallets,
    /**
     * El id del movimiento. La fila y el lote estrenan uno; la notificación (Ola 5) lo deriva del
     * aviso, así un segundo toque —o un reintento— reescribe el MISMO movimiento (`POST
     * /api/events` es idempotente por id) en vez de anotar otro.
     */
    idDelMovimiento: String = newId("ev"),
): FinancialEvent? {
    val evento = movimientoDelAviso(
        leido = anotacion.leido,
        cuentaId = anotacion.cuenta.id,
        categoria = anotacion.categoria,
        comercio = null,
        momento = momentoDelMovimientoDelAviso(anotacion.sms, anotacion.pago, anotacion.leido, ahora),
        textoDelAviso = anotacion.sms.text,
        esComprobante = esIdDeComprobante(anotacion.sms.id),
        id = idDelMovimiento,
    )
    return anotarElMovimientoDelAviso(anotacion.sms.id, anotacion.pago, evento, esIdDeComprobante(anotacion.sms.id), alCrearElMovimiento, repo)
}

/**
 * **«Anotado $18.300 · Uber Rides»** (vuelta 44): lo que dice el aviso de abajo al confirmar desde la
 * fila. Sin «Deshacer»: confirmar un aviso no tiene vuelta en Movi (se anula el movimiento, como
 * siempre). Vuelta 82: el monto primero, como [com.jvillada.movi.ui.espacios.textoDeLoAnotado]: si no
 * cabe, se corta el comercio, nunca el monto.
 */
fun textoDeAnotado(anotacion: AnotacionEntendida): String =
    com.jvillada.movi.ui.espacios.textoDeLoAnotado(
        com.jvillada.movi.shared.model.nombreParaMostrar(anotacion.leido.merchant),
        anotacion.monto,
        anotacion.leido.currency,
    )

/** Cómo terminó el lote: lo que se anotó y lo que quedó en la bandeja, con por qué. */
data class ResultadoDelLote(
    val anotados: List<AnotacionEntendida>,
    val quedaron: List<Pair<AnotacionEntendida, String>>,
)

/**
 * **El lote, uno por uno.** Cada aviso es atómico por su cuenta: si uno falla, los demás siguen, y
 * al final se sabe cuál quedó. Va en orden y no en paralelo: son pocos, y dos escrituras a la vez
 * sobre la misma cuenta no ganan nada.
 */
suspend fun confirmarElLote(
    lote: List<AnotacionEntendida>,
    alAvanzar: (hechos: Int) -> Unit = {},
    confirmar: suspend (AnotacionEntendida) -> Unit,
): ResultadoDelLote {
    val anotados = mutableListOf<AnotacionEntendida>()
    val quedaron = mutableListOf<Pair<AnotacionEntendida, String>>()
    lote.forEachIndexed { i, anotacion ->
        intentar { confirmar(anotacion) }
            .onSuccess { anotados += anotacion }
            .onFailure { quedaron += anotacion to it.toUserMessage() }
        alAvanzar(i + 1)
    }
    return ResultadoDelLote(anotados, quedaron)
}
