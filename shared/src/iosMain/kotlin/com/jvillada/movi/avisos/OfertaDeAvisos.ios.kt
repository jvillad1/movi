package com.jvillada.movi.avisos

import androidx.compose.runtime.Composable

/** Sin avisos locales en esta plataforma (Ola 1): la web tiene su push por VAPID aparte. */
@Composable
internal actual fun rememberOfertaDeAvisos(): OfertaDeAvisos? = null
