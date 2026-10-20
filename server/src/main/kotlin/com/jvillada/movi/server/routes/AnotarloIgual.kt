package com.jvillada.movi.server.routes

import com.jvillada.movi.server.sms.motivoParaApartar
import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.CATEGORIAS_DE_DOS_PATAS
import com.jvillada.movi.shared.model.COMERCIO_DE_UN_AVISO_SIN_LEER
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esUnComprobante
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.normalizarParaBuscar

/*
 * # «Anotarlo igual», del lado del server
 *
 * Ver `AnotarloIgual.kt` en `:core` para el caso que lo pidió. Acá vive **cuándo se ofrece** y qué
 * se propone; la app lo pide con `GET /api/sms/{id}/parse?anotarloIgual=1` y lo anota por el camino
 * de siempre (`POST /api/events` + `POST /api/sms/{id}/confirm?eventoId=`).
 */

/**
 * Frases de lo que trae plata en el texto y **no es un movimiento**, además de las listas que ya usa
 * `parseSms` ([NO_PASARON], [NO_SON_MOVIMIENTOS], [NU_NO_SON_MOVIMIENTOS]): un CDT que se abre o que
 * vence avisa su monto, y ninguno de esos avisos es plata que salió o entró.
 */
private val TAMPOCO_SE_ANOTAN = listOf("cdt")

/** «Recibiste $X», «Te llegó dinero»: la plata entró. Todo lo demás se propone como gasto. */
private val plataQueLlego = Regex("""\brecibiste\b|\bte\s+llego\b""")

/**
 * **¿Este aviso que Movi no leyó se puede anotar igual, y con qué monto?** El criterio, entero:
 *
 * 1. **Solo un aviso pendiente** (`pending`). Lo apartado por Movi —un recordatorio, una promoción,
 *    un código, un rechazo, la confirmación de reenvío de Gmail— está `ignored` con su motivo y no
 *    se ofrece; tampoco lo que el dueño ya ignoró o confirmó. Ni un comprobante: su lectura es la del
 *    papel, no la de este texto.
 * 2. **Ni un texto que diga que no es un movimiento**, aunque esté pendiente (un aviso que el dueño
 *    devolvió con «Era un movimiento», o uno anterior a las reglas que apartan):
 *    - lo que reconoce [motivoParaApartar] (promoción, recordatorio de pago, aviso de seguridad,
 *      código, extracto, resumen de gastos, rechazo, confirmación de reenvío);
 *    - lo que no pasó ([NO_PASARON]: rechazada, declinada, fondos insuficientes);
 *    - lo que `parseSms` ya sabe que no es un movimiento ([NO_SON_MOVIMIENTOS]: la ampliación de
 *      plazo, que refinancia una deuda sin mover un peso; [NU_NO_SON_MOVIMIENTOS]: el pago mínimo, la
 *      fecha límite, lo que rindió la Cajita) y un CDT ([TAMPOCO_SE_ANOTAN]).
 *
 *    **Lo que no pasó no se anota**, ni siquiera con el botón: ahí no hay nada que trabe al dueño.
 * 3. **Y un monto legible y mayor que cero**, con las mismas regex y la misma regla de separadores que
 *    un aviso que sí se entiende ([montoYMonedaDelTexto]). Sin monto, todo queda como siempre: solo
 *    «Ignorar».
 *
 * El tipo propuesto es gasto, salvo que el texto diga que la plata llegó («recibiste», «te llegó»);
 * el dueño lo cambia en Reconciliar. Lo que no encaja en ningún motivo y trae un monto se ofrece: es
 * la misma regla de oro de `queEsEsteMensaje` —ante la duda, que el dueño decida—, y anotarlo sigue
 * siendo un toque suyo.
 *
 * Pura: no lee la base. [avisoSinLeerDe] le suma la fecha, el comercio y la memoria del dueño.
 */
internal fun loQueTraeUnAvisoSinLeer(sms: SmsMessage): AvisoSinLeer? {
    if (sms.state != SMS_STATE_PENDING) return null
    if (esUnComprobante(sms)) return null
    if (motivoParaApartar(sms.text, sms.bank) != null) return null
    val t = normalizarParaBuscar(sms.text)
    val noSonMovimientos = NO_PASARON + NO_SON_MOVIMIENTOS + NU_NO_SON_MOVIMIENTOS + TAMPOCO_SE_ANOTAN
    if (noSonMovimientos.any { normalizarParaBuscar(it) in t }) return null
    val (monto, moneda) = montoYMonedaDelTexto(sms.text) ?: return null
    if (monto <= 0.0) return null
    val tipo = if (plataQueLlego.containsMatchIn(t)) TransactionType.INCOME else TransactionType.EXPENSE
    return AvisoSinLeer(monto = monto, moneda = moneda, tipo = tipo)
}

/**
 * **Lo que se ofrece para anotar igual [sms]**, si se ofrece ([loQueTraeUnAvisoSinLeer]): con la
 * fecha del aviso y lo poco que se puede proponer sin entenderlo.
 *
 * - **El comercio**, del lector genérico de `parseSms` (sin la regla de Nu, que es la que dejó el
 *   aviso sin leer), solo si leyó un nombre y del mismo lado (gasto o ingreso).
 * - **La categoría, de la memoria del dueño**: la misma pasada que cualquier aviso
 *   ([conLaMemoriaYLosTerceros]). Así, lo que él eligió la vez pasada para ese comercio vuelve a
 *   venir puesto. «Otros» no se propone (es no saber), ni una categoría de dos patas: sin la cuenta
 *   del otro lado no se puede armar.
 */
internal fun avisoSinLeerDe(sms: SmsMessage, sabe: LoQueMoviSabeDelDueno, ahora: Long): AvisoSinLeer? {
    val base = loQueTraeUnAvisoSinLeer(sms) ?: return null
    val generico = parseSms(sms.text, origen = null)?.takeIf { it.type == base.tipo }
    val comercio = generico?.merchant?.takeIf { it.isNotBlank() && it != COMERCIO_DE_UN_AVISO_SIN_LEER }
    val propuesta = ParsedSms(
        amount = base.monto,
        merchant = comercio ?: COMERCIO_DE_UN_AVISO_SIN_LEER,
        type = base.tipo,
        category = SIN_CATEGORIA,
        currency = base.moneda,
        identificadorDelDestino = generico?.identificadorDelDestino,
        identificadorEsLlave = generico?.identificadorEsLlave ?: false,
    )
    val recordada = conLaMemoriaYLosTerceros(propuesta, sms.text, sabe.memoria, sabe.destinos)
    val categoria = recordada.category.trim()
        .takeIf { it.isNotEmpty() && !it.equals(SIN_CATEGORIA, ignoreCase = true) && it !in CATEGORIAS_DE_DOS_PATAS }
    return base.copy(
        momento = momentoDelSms(sms.time, ahora = ahora),
        comercio = recordada.merchant.takeIf { it.isNotBlank() && it != COMERCIO_DE_UN_AVISO_SIN_LEER },
        categoria = categoria,
        aprendidoDe = recordada.aprendidoDe.takeIf { categoria != null },
    )
}

/**
 * La lectura mínima de un aviso sin leer para buscar si ya está anotado («¿Ya lo anotaste?»): el
 * monto, la moneda y el tipo propuesto. `null` si no se ofrece anotarlo igual.
 */
internal fun lecturaParaCoincidencias(sms: SmsMessage): ParsedSms? =
    loQueTraeUnAvisoSinLeer(sms)?.let { ParsedSms(it.monto, COMERCIO_DE_UN_AVISO_SIN_LEER, it.tipo, SIN_CATEGORIA, it.moneda) }
