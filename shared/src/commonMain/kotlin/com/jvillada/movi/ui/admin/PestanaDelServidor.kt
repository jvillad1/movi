package com.jvillada.movi.ui.admin

import androidx.compose.runtime.Composable
import com.jvillada.movi.ui.components.ds.Vacio

/**
 * **La pestaña «Servidor» de «Fallos y uso»** (vuelta 100 deja el lugar; la llena la vuelta de la
 * observabilidad del servidor: latencia y errores por ruta, servicios externos, jobs). Vive en su
 * propio archivo para que esa vuelta la reemplace sin tocar [FallosYUsoScreen].
 */
@Composable
fun PestanaDelServidor() {
    Vacio(
        titulo = "Todavía no hay datos del servidor",
        detalle = "Acá van a estar la latencia y los errores de cada ruta, los servicios externos y las tareas programadas.",
    )
}
