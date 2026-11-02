package com.jvillada.movi.platform

import kotlinx.browser.window

/**
 * Ver el `expect`. Por el *user agent*: Safari (y cualquier navegador) en un iPhone dice «iPhone». Un iPad
 * con iPadOS se presenta como una Mac, y queda afuera, que es lo que se quiere.
 */
internal actual fun esUnIphone(): Boolean =
    runCatching { "iPhone" in window.navigator.userAgent }.getOrDefault(false)
