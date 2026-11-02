package com.jvillada.movi.platform

/** Ver el `expect`: un Android nunca es un iPhone. */
internal actual fun esUnIphone(): Boolean = false

/** Ver el `expect`: un Android no tiene versión de iOS. */
internal actual fun versionDeIos(): Int? = null
