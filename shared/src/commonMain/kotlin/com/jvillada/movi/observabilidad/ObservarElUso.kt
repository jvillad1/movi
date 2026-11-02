package com.jvillada.movi.observabilidad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.ui.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cada cuánto sube el uso mientras la app está abierta. */
private const val CADA_CUANTO_SUBE_MS = 60_000L

/**
 * **La navegación central del uso** (vuelta 100): una sola llamada en `App.kt`, no una por pantalla.
 *
 * - Cada vez que cambia la pantalla de arriba, pone la pila como ruta y anota [EventoDeUso.PANTALLA].
 * - Abrir «Agregar» (la hoja encima, que no entra a la pila) es el toque de la acción principal.
 * - `ON_START`/`ON_STOP` de la app abren o siguen la sesión y anotan el segundo plano ([Uso]).
 * - Sube lo pendiente cada minuto y al irse a segundo plano.
 */
@Composable
fun ObservarElUso(pila: List<Screen>, agregarAbierto: Boolean) {
    val arriba = pila.lastOrNull()
    LaunchedEffect(pila) {
        Uso.ponerLaPila(tramosDeLaPila(pila))
    }
    LaunchedEffect(arriba?.let(::tramoDeRuta)) {
        val pantalla = arriba ?: return@LaunchedEffect
        Uso.evento(EventoDeUso.PANTALLA, mapOf(ClaveDeUso.PANTALLA to tramoDeRuta(pantalla).substringBefore('(')))
    }
    LaunchedEffect(agregarAbierto) {
        if (agregarAbierto) Uso.evento(EventoDeUso.TOQUE, mapOf(ClaveDeUso.ACCION to "AGREGAR"))
    }
    val alcance = rememberCoroutineScope()
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> Uso.alVolverAPrimerPlano()
                Lifecycle.Event.ON_STOP -> {
                    Uso.alIrASegundoPlano()
                    alcance.launch { Uso.enviar() }
                }
                else -> Unit
            }
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CADA_CUANTO_SUBE_MS)
            Uso.enviar()
        }
    }
}
