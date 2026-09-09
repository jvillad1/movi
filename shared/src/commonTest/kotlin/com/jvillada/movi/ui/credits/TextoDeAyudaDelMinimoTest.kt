package com.jvillada.movi.ui.credits

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Lo que la hoja tiene que decir debajo del campo del mínimo**, y por qué cada mitad está ahí.
 *
 * Es texto y no lógica, y aun así se fija: las dos cosas que dice son las dos formas conocidas de
 * que el dato quede mal cargado, y las dos son silenciosas.
 */
class TextoDeAyudaDelMinimoTest {

    /**
     * Dice **para qué sirve**: sin eso el campo se lee como un dato de archivo y se queda vacío —
     * que es el estado en el que el «Flujo libre» le decía $601.574 sin descontar $1.843.014.
     */
    @Test
    fun `dice que esto cambia el flujo libre, no que es un pago minimo`() {
        assertTrue("Flujo libre" in TEXTO_DE_AYUDA_DEL_MINIMO, TEXTO_DE_AYUDA_DEL_MINIMO)
    }

    /**
     * Y dice **de cuál de los dos mínimos se trata**. Una Master Black tiene dos cuentas en Movi,
     * pesos y dólares, y el extracto de Bancolombia trae dos pagos mínimos separados, uno por
     * bloque de moneda. Sin esta frase, «un mínimo con dos campos» invita a teclear el mismo número
     * en los dos y a restarlo dos veces del disponible — un error que nadie ve, porque la cifra
     * grande sigue siendo plausible.
     */
    @Test
    fun `dice que con dos monedas hay un minimo por cuenta`() {
        assertTrue("dólares" in TEXTO_DE_AYUDA_DEL_MINIMO, TEXTO_DE_AYUDA_DEL_MINIMO)
        assertTrue("esta cuenta" in TEXTO_DE_AYUDA_DEL_MINIMO, TEXTO_DE_AYUDA_DEL_MINIMO)
    }
}
