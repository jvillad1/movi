package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * **La ventana volvió a estar al frente** (vuelta 79): en la web, la pestaña volvió a verse
 * (`visibilitychange`) o la ventana recuperó el foco (`focus`). Ahí no hay ciclo de vida del teléfono que
 * diga «volví del segundo plano», y la revisión visual 7 encontró que un gasto escrito desde otro lado
 * (la API, otra pestaña) no aparecía hasta pasados 30 s.
 *
 * En Android e iOS no hace nada: la vuelta del segundo plano la dice el ciclo de vida
 * (`AlVolverDelSegundoPlano`, en `App.kt`).
 */
@Composable
expect fun AlVolverElFocoALaVentana(alVolver: () -> Unit)
