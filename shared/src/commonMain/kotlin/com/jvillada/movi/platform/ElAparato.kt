package com.jvillada.movi.platform

/**
 * Vuelta 111 · **¿Movi corre en un iPhone?** La app de iOS en un iPhone (no en un iPad), o la web abierta
 * en un iPhone (por el *user agent*). Lo usa la guía de primer uso para ofrecer los atajos de Atajos
 * («Desde tu iPhone») solo donde se pueden armar. En Android, siempre `false`.
 */
internal expect fun esUnIphone(): Boolean

/**
 * Vuelta 114 · **la versión mayor de iOS, si se sabe con seguridad**, para elegir los pasos de la automatización
 * (en iOS 27 ya no hay pestaña «Automatización» en Atajos). La app de iOS la lee del sistema; la web, del *user
 * agent* (`versionDeIosDelUserAgent`), que desde Safari 26 tiene la versión del sistema congelada. `null` si no
 * se sabe (y en Android): la guía muestra entonces todas las variantes.
 */
internal expect fun versionDeIos(): Int?
