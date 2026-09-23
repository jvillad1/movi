package com.jvillada.movi.shared.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * # El teclado no puede taparle lo que está escribiendo
 *
 * El dueño lo reportó así: *«el teclado se levanta pero la pantalla no se ajusta, entonces no
 * puedo ver lo que estoy escribiendo en el chat sin cerrar el teclado»*.
 *
 * **La causa no está en la pantalla del chat.** Movi dibuja de borde a borde
 * (`enableEdgeToEdge`, en `MainActivity`), y con eso el `android:windowSoftInputMode="adjustResize"`
 * del manifiesto **deja de encoger la ventana**: Android pasa a mandar el alto del teclado como un
 * *inset* y la app tiene que descontarlo ella misma. Nadie lo descontaba, así que el teclado se
 * abría encima del contenido en **todas** las pantallas con un campo abajo — el chat es donde más
 * se nota porque ahí el campo vive pegado al borde inferior.
 *
 * Lo arregla una línea en la columna raíz de `App.kt`, y por eso esta prueba mira el código fuente
 * y no una pantalla: lo que hay que proteger es que esa línea siga ahí. Una prueba de UI sobre el
 * chat pasaría igual sin ella (en Robolectric no hay teclado de verdad que levantar) y daría una
 * garantía que no existe.
 */
class ElTecladoNoTapaLoQueEscribisTest {

    private val app: String by lazy {
        val raiz = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "shared/src/commonMain/kotlin/com/jvillada/movi/App.kt").exists() }
        File(raiz, "shared/src/commonMain/kotlin/com/jvillada/movi/App.kt").readText()
    }

    @Test
    fun `la columna raiz descuenta el alto del teclado`() {
        // Desde la entrega B el ancho máximo depende de la pantalla (el Inicio se ensancha en
        // escritorio, ver `anchoMaximoDeLaPantalla`); la columna raíz es la misma.
        val columnaRaiz = app.lineSequence().firstOrNull { "widthIn(max = anchoMaximoDeLaPantalla(" in it }
        assertTrue(columnaRaiz != null, "cambió la columna raíz de App.kt: revisa que siga descontando el teclado")
        assertTrue(
            "imePadding()" in columnaRaiz,
            "la columna raíz dejó de descontar el teclado y vuelve a taparle lo que escribe:\n$columnaRaiz",
        )
    }

    @Test
    fun `la barra de abajo se esconde mientras el teclado esta arriba`() {
        assertTrue(
            "showBottomNav && !tecladoALaVista" in app,
            "la barra inferior volvió a mostrarse con el teclado arriba: son 60 dp de los pocos que quedan",
        )
    }
}
