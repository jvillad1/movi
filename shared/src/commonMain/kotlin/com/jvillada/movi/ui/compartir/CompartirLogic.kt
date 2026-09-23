package com.jvillada.movi.ui.compartir

/**
 * # Los textos y las cuentas de la pantalla «Compartir», sin Compose
 *
 * Todo lo que se puede decidir sin dibujar vive acá para que `CompartirLogicTest` lo fije. La
 * pantalla tiene un solo trabajo difícil y es de redacción: que el dueño entienda, **antes** de
 * tocar el botón, qué va a ver la otra persona, qué no, y que lo puede cortar cuando quiera. Si eso
 * se entiende mal, compartir se usa de menos (por miedo) o de más (sin saber que el enlace vive
 * una semana).
 */

private const val MINUTO_MS = 60_000L
private const val HORA_MS = 60 * MINUTO_MS
private const val DIA_MS = 24 * HORA_MS

/** «1 día», «7 días», «30 días»: el rótulo de cada opción de vigencia. */
fun rotuloDeVigencia(dias: Int): String = if (dias == 1) "1 día" else "$dias días"

/** Lo que la pantalla explica arriba de todo. */
const val QUE_ES_COMPARTIR: String =
    "Crea un enlace de solo lectura para que otra persona —tu pareja, tu asesor— vea un resumen " +
        "de tus finanzas sin entrar a tu cuenta ni saber tu contraseña."

/**
 * Lo que va a ver la otra persona, renglón por renglón. Tiene que coincidir con lo que pinta la
 * página del server (`PaginaCompartida.kt`): si acá se promete menos de lo que se muestra, el dueño
 * comparte algo que no sabía que compartía.
 */
val LO_QUE_VA_A_VER: List<String> = listOf(
    "Tu plata disponible, lo de uso condicionado y tu patrimonio neto.",
    "Ingresos, gastos y flujo del período en curso, y en qué categorías se fue la plata.",
    "Tus deudas con su saldo, cuota y tasa. De los números de cuenta, solo los últimos 4 dígitos.",
)

/** Y lo que no: la otra mitad de la tranquilidad. */
const val LO_QUE_NO_VA_A_VER: String =
    "No verá tus movimientos uno por uno ni podrá cambiar nada. Puedes revocar el enlace cuando quieras."

/**
 * Por qué el enlace se comparte ahora o nunca. Movi guarda solo una huella del enlace (ver
 * `EnlaceCompartido` en `:core`), así que no puede volver a mostrarlo.
 */
const val SE_COMPARTE_AHORA: String =
    "Por seguridad, Movi no guarda el enlace: compártelo o cópialo ahora. Si lo pierdes, revócalo y crea otro."

/** El texto que acompaña al enlace en la hoja de compartir del sistema. */
fun mensajeParaCompartir(url: String, dias: Int): String =
    "Te comparto un resumen de mis finanzas en Movi. Es de solo lectura y vale ${rotuloDeVigencia(dias)}: $url"

/** La pregunta y el detalle de la confirmación antes de revocar. */
const val PREGUNTA_REVOCAR: String = "¿Revocar este enlace?"
const val DETALLE_REVOCAR: String =
    "Quien lo tenga dejará de ver tus datos en ese momento. Tus datos no se tocan, y puedes crear otro enlace cuando quieras."

/**
 * Cuánto le queda a un enlace. Horas mientras quede menos de día y medio —«vence en 5 días» no le
 * sirve a quien creó uno de 24 horas—, días redondeados de ahí para arriba, así un enlace de siete
 * días recién creado dice «7 días» y no «6».
 */
fun textoDeVencimiento(venceEn: Long, ahora: Long): String {
    val resta = venceEn - ahora
    if (resta <= 0L) return "Vencido"
    if (resta < HORA_MS) {
        val minutos = ((resta + MINUTO_MS - 1) / MINUTO_MS).coerceAtLeast(1)
        return if (minutos == 1L) "Vence en 1 minuto" else "Vence en $minutos minutos"
    }
    if (resta < 36 * HORA_MS) {
        val horas = (resta + HORA_MS - 1) / HORA_MS
        return if (horas == 1L) "Vence en 1 hora" else "Vence en $horas horas"
    }
    val dias = (resta + DIA_MS / 2) / DIA_MS
    return "Vence en $dias días"
}

/** «hace un momento», «hace 5 minutos», «hace 1 hora», «hace 3 días». */
fun haceCuanto(momento: Long, ahora: Long): String {
    val paso = (ahora - momento).coerceAtLeast(0L)
    return when {
        paso < MINUTO_MS -> "hace un momento"
        paso < HORA_MS -> (paso / MINUTO_MS).let { if (it == 1L) "hace 1 minuto" else "hace $it minutos" }
        paso < DIA_MS -> (paso / HORA_MS).let { if (it == 1L) "hace 1 hora" else "hace $it horas" }
        else -> (paso / DIA_MS).let { if (it == 1L) "hace 1 día" else "hace $it días" }
    }
}

/**
 * Si alguien ya lo abrió, y cuándo. Es lo que le deja al dueño saber si Caro ya lo miró — y notar
 * si un enlace que mandó a una sola persona se está abriendo veinte veces.
 */
fun textoDeVistas(ultimaVista: Long?, vistas: Int, ahora: Long): String {
    if (ultimaVista == null || vistas <= 0) return "Nadie lo ha abierto todavía"
    val veces = if (vistas == 1) "1 vez" else "$vistas veces"
    return "Abierto $veces · la última, ${haceCuanto(ultimaVista, ahora)}"
}
