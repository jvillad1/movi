package com.jvillada.movi.platform

/**
 * Vuelta 111 · **¿Movi corre en un iPhone?** La app de iOS en un iPhone (no en un iPad), o la web abierta
 * en un iPhone (por el *user agent*). Lo usa la guía de primer uso para ofrecer los atajos de Atajos
 * («Desde tu iPhone») solo donde se pueden armar. En Android, siempre `false`.
 */
internal expect fun esUnIphone(): Boolean
