package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.NO_PASARON
import com.jvillada.movi.server.routes.esUnaNotificacion
import com.jvillada.movi.shared.model.MotivoDeApartado
import com.jvillada.movi.shared.model.normalizarParaBuscar

/** Lo que decide [queEsEsteMensaje]. */
sealed interface QueEsElMensaje {
    /** Un movimiento, o algo que podría serlo: va a «Por revisar», como siempre. */
    data object Movimiento : QueEsElMensaje

    /** No es un movimiento, y se sabe por qué: se guarda apartado. */
    data class NoEsMovimiento(val motivo: MotivoDeApartado) : QueEsElMensaje
}

/**
 * # ¿Este mensaje del banco es un movimiento?
 *
 * A «Por revisar» llegaban recordatorios de pago, códigos de verificación, promociones, avisos de
 * inicio de sesión y resúmenes de gastos, y el dueño los ignoraba uno por uno (3-oct-2026). Esta
 * función los reconoce para que el server los guarde **apartados** (ver `MensajesApartados.kt` en
 * `:core`): siguen en la base y en el historial, pero no piden una decisión.
 *
 * Vive en el server y no en el teléfono a propósito: el server es el único lugar por el que pasan
 * los tres orígenes (SMS, notificaciones y correo), y una regla nueva acá se despliega sin APK.
 *
 * ### La regla de oro: ante la duda, es movimiento
 *
 * Un movimiento escondido es mucho peor que un aviso de más en la bandeja: el primero es plata que
 * Movi no cuenta sin que nadie lo note; el segundo es un toque en «Ignorar». Por eso:
 *
 * 1. **La forma de un movimiento manda** ([tieneLaFormaDeUnMovimiento]): un mensaje que dice
 *    «compraste», «transferiste», «recibiste», «le informa compra por…» es movimiento aunque
 *    después hable de una promoción. Lo único que le gana es que **no pasó** (rechazado).
 * 2. **Cada motivo pide frases propias de ese aviso**, no palabras sueltas. «Aprovecha» o un enlace
 *    no bastan para llamar promoción a un mensaje: hace falta un «dcto», unas «TyC», un sorteo.
 *    Un código pide además el número del código.
 * 3. **Lo que no encaja en ningún motivo es movimiento.**
 *
 * Se comprobó contra el histórico real del dueño (235 mensajes de Movi y 5.923 SMS de bancos del
 * teléfono): **ninguno de los que él confirmó** queda apartado. Los textos reales no están en el
 * repo; las pruebas usan textos sintéticos con la misma forma (`QueEsEsteMensajeTest`).
 *
 * @param origen el rótulo `bank` de la fila («85540», «Notificación · Nu», «Correo · Bancolombia»).
 */
fun queEsEsteMensaje(texto: String, origen: String?): QueEsElMensaje {
    // El aviso de una app sin un solo dígito no trae monto posible («Set up a shortcut to pay…»).
    // Era `estadoAlLlegar` (#22-sep): ahora es un motivo más, y se puede devolver como los otros.
    if (origen != null && esUnaNotificacion(origen) && texto.none { it.isDigit() }) {
        return QueEsElMensaje.NoEsMovimiento(MotivoDeApartado.SIN_MONTO)
    }
    val t = normalizarParaBuscar(texto)
    if (t.isEmpty()) return QueEsElMensaje.Movimiento

    // «Nos alegra que todo esté bien. Si tu transacción fue aprobada… si fue rechazada…»: la
    // respuesta a una verificación, que nombra las dos cosas y no es ninguna.
    if (DESPUES_DE_VERIFICAR.any { it in t }) return QueEsElMensaje.NoEsMovimiento(MotivoDeApartado.AVISO_DE_SEGURIDAD)
    // Lo que no pasó le gana a todo: una compra rechazada tiene la forma de una compra.
    if (NO_PASARON.any { normalizarParaBuscar(it) in t } || NO_PASO.any { it in t }) {
        return QueEsElMensaje.NoEsMovimiento(MotivoDeApartado.NO_PASO)
    }
    if (tieneLaFormaDeUnMovimiento(t)) return QueEsElMensaje.Movimiento
    if (LOS_DECIDE_EL_DUENO.any { it in t }) return QueEsElMensaje.Movimiento

    val motivo = when {
        SEGURIDAD.any { it in t } -> MotivoDeApartado.AVISO_DE_SEGURIDAD
        esUnCodigo(t) -> MotivoDeApartado.CODIGO_DE_VERIFICACION
        RECORDATORIO.any { it in t } -> MotivoDeApartado.RECORDATORIO_DE_PAGO
        EXTRACTO.any { it in t } -> MotivoDeApartado.EXTRACTO_DISPONIBLE
        RESUMEN.any { it in t } || resumenConCifra.containsMatchIn(t) -> MotivoDeApartado.RESUMEN_DE_GASTOS
        PROMOCION.any { it in t } -> MotivoDeApartado.PROMOCION
        else -> null
    }
    return motivo?.let { QueEsElMensaje.NoEsMovimiento(it) } ?: QueEsElMensaje.Movimiento
}

/** Atajo: ¿lo aparta? `null` si es (o podría ser) un movimiento. */
fun motivoParaApartar(texto: String, origen: String?): MotivoDeApartado? =
    (queEsEsteMensaje(texto, origen) as? QueEsElMensaje.NoEsMovimiento)?.motivo

// ── Las frases. Todas ya normalizadas: minúsculas, sin tildes, espacios simples. ─────────────────

/**
 * **Las frases con que un banco cuenta un movimiento.** Verbos en pasado y de segunda persona
 * («compraste», «recibiste»), las fórmulas viejas de Bancolombia («le informa compra por…») y las de
 * Nu, Glim y Google Wallet. Si aparece una, el mensaje es movimiento aunque traiga otras palabras.
 */
private val FORMAS_DE_MOVIMIENTO = listOf(
    "compraste", "transferiste", "pagaste", "recibiste", "retiraste", "realizaste una transferencia",
    "hiciste un avance", "hiciste una transferencia", "hiciste un pago", "hiciste una compra",
    "le informa compra", "le informa transferencia", "le informa pago", "le informa retiro",
    "le informa avance", "le informa un pago", "le informa recepcion", "te informa recepcion",
    "te informa pago", "informa transferencia", "informa retiro", "informa pago",
    "recibimos pago", "recibimos tu pago", "transferencia realizada", "transferencia recibida",
    "compra aprobada", "pago aprobado", "fue aprobada", "fue aprobado", "hemos aprobado",
    "te llego dinero", "recargo ", "debitamos", "realizo debito", "realizo abono", "hizo un abono",
    "desembolso", "nomina recibida", "abono a tu", "abonamos", "consignacion", "reembolso",
    // «el reclamo de fraude … se soluciona con devolución a su T.Crédito»: plata que vuelve.
    "devolucion a su", "devolucion a tu",
)

/**
 * **Lo que se queda en la bandeja aunque no sea una compra**, porque el dueño lo quiere ver y decidir:
 *
 * - Los avisos que ya conoce `parseSms` como no-movimientos (la ampliación de plazo, la bienvenida a
 *   un plan, la cuenta de un tercero inscrita): el dueño **confirmó** varios en el histórico — una
 *   ampliación de plazo refinancia una deuda, un plan nuevo es un cobro que viene, y la cuenta
 *   inscrita es la de alguien a quien le va a mandar plata.
 * - Los cobros anunciados («se activó su PLAN CARDIF por 109.990», «se le notifica COBRO por…», «se
 *   te cobrará el impuesto»): no tienen la forma de una compra, pero son plata que sale.
 */
private val LOS_DECIDE_EL_DUENO = listOf(
    "ampliacion de plazo", "bienvenido", "inscribiste", "inscripcion de cuentas",
    "cobro por", "cargo por", "se activo su", "se te cobrara", "cobraremos", "te cobraremos",
)

/** «TOSTAO CAFE: COP15,100 with Glim ••3037» — el aviso de Google Wallet. */
private val formaDeWallet = Regex(""":\s*(cop|usd)\s*[0-9][0-9.,]*\s+with\s""")

private fun tieneLaFormaDeUnMovimiento(t: String): Boolean =
    FORMAS_DE_MOVIMIENTO.any { it in t } || formaDeWallet.containsMatchIn(t)

/** Lo que no pasó y [NO_PASARON] no cubre: «esta vez no continuamos la transacción». */
private val NO_PASO = listOf("no continuamos la transaccion")

/**
 * **Un código de verificación** pide dos cosas: una frase de código y el código mismo (4 a 8
 * dígitos). Sin el número, «usa el código APPLE» es una promoción, no una clave.
 */
private val FRASES_DE_CODIGO = listOf(
    "clave dinamica", "es el codigo", "es tu codigo", "codigo de verificacion", "codigo de seguridad",
    "codigo de activacion", "codigo de confirmacion", "clave temporal", "contrasena temporal",
    "no la compartas", "no lo compartas", "otp",
)
private val elCodigo = Regex("""\b\d{4,8}\b""")
private fun esUnCodigo(t: String): Boolean = FRASES_DE_CODIGO.any { it in t } && elCodigo.containsMatchIn(t)

/** Inicios de sesión, claves, inscripciones, bloqueos y la verificación de una compra sospechosa. */
private val SEGURIDAD = listOf(
    "te autenticaste", "inicio de sesion", "iniciaste sesion", "nuevo dispositivo", "te identificaste",
    "inscripcion al servicio", "clave principal", "cambiaste tu clave",
    "cambio de clave", "bloquearemos", "bloqueamos tu", "se ha bloqueado", "actualizaste los topes",
    "topes de tus transacciones", "registraste tu llave", "elegiste tu llave", "necesitamos tu confirmacion",
    "te esperamos para confirmar", "no recibimos tu respuesta", "responde si o no", "escribe si o no",
    "movimiento inusual", "transaccion extra", "nos alegra que",
    "nunca compartas", "nunca des tus", "nunca entregues", "actualizaste tu informacion",
    "activaste tu tarjeta", "ha sido bloquead", "inusual o riesgos",
)

/** La respuesta del banco después de una verificación: no es la compra, ni su rechazo. */
private val DESPUES_DE_VERIFICAR = listOf("nos alegra que todo este bien", "confirmacion recibida")

/** Lo que el banco te recuerda pagar: todavía no salió nada. */
private val RECORDATORIO = listOf(
    "tienes un pago por", "tienes un pago pendiente", "completa tu pago", "recuerda pagar",
    "recuerda tu pago", "recuerda realizar tu pago", "te recordamos", "recordatorio de pago",
    "pago minimo", "fecha limite", "proximo pago", "vence el", "vence hoy", "vence manana",
)

private val EXTRACTO = listOf(
    "extracto", "estado de cuenta", "factura esta lista", "factura ya esta", "factura esta disponible",
)

/** Los informes del banco sobre cuánto gastaste: cifras de un mes, no un movimiento. */
private val RESUMEN = listOf(
    "fue tu gasto", "has gastado", "tus gastos", "tu gasto", "tuinforme", "informe de gastos",
    "tu informe", "moviste tu plata", "suman tus compras", "lo que suman", "has categorizado",
    "categorizaste", "mayor gasto", "le has enviado", "pagos de tus suscripciones suman",
)
/** «gastaste $X en promedio», «hiciste 12 transferencias en agosto», «llevas $X de gastos». */
private val resumenConCifra = Regex("""\bgastaste \$|\bhiciste \d+ transferencias|\busaste \d+ veces|\bllevas \$[0-9.,]+ de gastos""")

/**
 * **Una promoción, con su marca inconfundible**: términos y condiciones, un
 * descuento, un sorteo, puntos, cuotas sin interés. No basta «aprovecha» ni un enlace (ni siquiera
 * un `bit.ly`): el banco también los usa para avisar de un producto del dueño — la ampliación de
 * plazo que él confirmó y el aviso del 4x1000 traen los dos un enlace acortado.
 */
private val PROMOCION = listOf(
    "tyc", "t&c", "terminos y condiciones", "dcto", "descuento", "% off",
    "%off", "dto*", "% de dto", "cupon", "preventa", "participa", "invita a", "invitamos a", "premios",
    "sorteo", "puntos colombia", "acumulaste", "% de interes", "% interes", "%interes", "tu360compras",
    "tu360movilidad", "tu360inmobiliario", "hot sale", "cyber days", "black days",
    "seguro contra el cancer", "seguro integral", "estrenar carro", "estrenar moto",
)
