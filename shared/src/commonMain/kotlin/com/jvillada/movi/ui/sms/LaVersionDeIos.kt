package com.jvillada.movi.ui.sms

/*
 * # Vuelta 114 · qué pasos de Atajos mostrar, según la versión de iOS
 *
 * En iOS 27 la app Atajos ya no tiene la pestaña «Automatización» («Tus automatizaciones ahora se guardan como
 * atajos»): la automatización «Mensaje» se agrega **dentro del atajo**, por «Buscar» y la pastilla
 * «Automatización» (verificado a mano en el iPhone de Caro, iOS 27.0.1, el 2-nov-2026). De iOS 17 a iOS 26 sigue
 * la pestaña, con «Ejecutar de inmediato» (existe para «Mensaje» desde iOS 17). Antes de iOS 17 el iPhone pide
 * tocar una notificación cada vez.
 *
 * **Cómo se sabe la versión en la web.** Desde Safari 26, Safari en iPhone **congela** el sistema del *user
 * agent* en «CPU iPhone OS 18_6» (o 18_6_2 / 18_7), a propósito y para siempre, porque muchos sitios no
 * entendían que 26 era mayor que 18 (notas de Safari 26.0; WebKit bug 298260; 51Degrees, «Apple iOS 26 /
 * Safari 26 user agent string»). «Version/26.0» sí sigue la versión de Safari, que en el iPhone va atada a la
 * versión mayor del sistema (Evil Martians, «How to detect Safari and iOS versions with ease», 2026). Por eso:
 *
 * - un «iPhone OS» **menor que 18** es anterior al congelamiento: es la versión real;
 * - en **Safari** (sin CriOS, FxiOS, EdgiOS, OPiOS, OPT —Opera—, YaBrowser, Focus —Firefox Focus/Klar—, la app
 *   de Google ni DuckDuckGo), un «Version/» de **26 o más** es la
 *   versión del sistema;
 * - un «iPhone OS» de **19 o más** no puede ser el valor congelado (Chrome en iOS manda el real);
 * - Safari 18 con «iPhone OS 18_x» es iOS 18;
 * - lo demás (un 18 sin «Version/», un navegador dentro de otra app) **no se sabe**: la guía muestra las dos
 *   variantes, la de iOS 27 primero.
 */

/** Las variantes de la guía de la automatización de mensajes. */
enum class VarianteDeLaAutomatizacion {
    /** iOS 27 o más nuevo: dentro del atajo, por «Buscar» y la pastilla «Automatización». */
    IOS_27_O_MAS,
    /** iOS 17 a 26: la pestaña «Automatización». */
    IOS_17_A_26,
    /** Antes de iOS 17: una línea (el iPhone pide tocar una notificación cada vez). */
    ANTES_DE_IOS_17,
}

/** Los navegadores de iOS que no son Safari: su «Version/», si lo traen, no dice nada del sistema. */
private val NAVEGADORES_QUE_NO_SON_SAFARI =
    listOf("CriOS", "FxiOS", "EdgiOS", "OPiOS", "OPT/", "YaBrowser", "Focus/", "GSA/", "DuckDuckGo")

/**
 * **La versión mayor de iOS según el *user agent***, solo si se sabe con seguridad (ver arriba); si no, `null`.
 * Solo para un iPhone: un iPad con iPadOS se presenta como una Mac.
 */
fun versionDeIosDelUserAgent(userAgent: String): Int? {
    if ("iPhone" !in userAgent) return null
    val sistema = Regex("""iPhone OS (\d+)_""").find(userAgent)?.groupValues?.get(1)?.toIntOrNull()
    val esSafari = NAVEGADORES_QUE_NO_SON_SAFARI.none { it in userAgent }
    val safari = if (esSafari) Regex("""Version/(\d+)""").find(userAgent)?.groupValues?.get(1)?.toIntOrNull() else null
    return when {
        sistema != null && sistema < 18 -> sistema
        safari != null && safari >= 26 -> safari
        sistema != null && sistema >= 19 -> sistema
        sistema == 18 && safari == 18 -> 18
        else -> null
    }
}

/**
 * **Qué variantes se ven de entrada y cuáles quedan detrás de «¿Tu iPhone muestra otra cosa?»**, según la
 * versión mayor de iOS ([version], `null` si no se sabe con seguridad).
 */
data class VariantesDeLaGuia(
    val aLaVista: List<VarianteDeLaAutomatizacion>,
    val detras: List<VarianteDeLaAutomatizacion>,
)

fun variantesDeLaGuia(version: Int?): VariantesDeLaGuia = when {
    version == null -> VariantesDeLaGuia(
        aLaVista = listOf(
            VarianteDeLaAutomatizacion.IOS_27_O_MAS,
            VarianteDeLaAutomatizacion.IOS_17_A_26,
            VarianteDeLaAutomatizacion.ANTES_DE_IOS_17,
        ),
        detras = emptyList(),
    )
    version >= 27 -> VariantesDeLaGuia(
        aLaVista = listOf(VarianteDeLaAutomatizacion.IOS_27_O_MAS),
        detras = listOf(VarianteDeLaAutomatizacion.IOS_17_A_26),
    )
    version >= 17 -> VariantesDeLaGuia(
        aLaVista = listOf(VarianteDeLaAutomatizacion.IOS_17_A_26),
        detras = listOf(VarianteDeLaAutomatizacion.IOS_27_O_MAS),
    )
    else -> VariantesDeLaGuia(
        aLaVista = listOf(VarianteDeLaAutomatizacion.ANTES_DE_IOS_17, VarianteDeLaAutomatizacion.IOS_17_A_26),
        detras = listOf(VarianteDeLaAutomatizacion.IOS_27_O_MAS),
    )
}
