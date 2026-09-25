package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El dueño nombra su sueldo «Salario Octubre 2026» y la regla se llama «Salario»: sin esto no
 * pegaba, y como el monto cambió mes a mes tampoco pegaban las tres circunstancias (ver
 * `OccurrenceMatching.kt`). [nombreDeMovimientoPegaConRegla] es la excepción angosta: el mes/año
 * se perdona solo como cola, nunca como reemplazo del nombre.
 */
class NameKeyTest {

    @Test
    fun `el mes y el anio se perdonan al final`() {
        assertTrue(nombreDeMovimientoPegaConRegla("Salario", "Salario Octubre 2026"))
        assertTrue(nombreDeMovimientoPegaConRegla("Salario", "Salario de octubre"))
        assertTrue(nombreDeMovimientoPegaConRegla("Salario", "SALARIO OCT 2026"))
    }

    @Test
    fun `un anio suelto tambien se perdona`() {
        assertTrue(nombreDeMovimientoPegaConRegla("Salario", "Salario 2026"))
    }

    @Test
    fun `mayusculas y tildes no rompen el emparejamiento`() {
        assertTrue(nombreDeMovimientoPegaConRegla("Peluquería", "PELUQUERÍA Octubre 2026"))
    }

    @Test
    fun `una palabra que no es de fecha no se perdona`() {
        assertFalse(nombreDeMovimientoPegaConRegla("Salario", "Salario Caro"))
        assertFalse(nombreDeMovimientoPegaConRegla("Gimnasio", "Gimnasio Caro"))
        assertFalse(nombreDeMovimientoPegaConRegla("Cuota Hipoteca", "Cuota Hipoteca Papá"))
    }

    /**
     * «Prima de junio» es el aguinaldo de mitad de año: el mes ya es parte de CÓMO se llama la
     * regla, no un dato suelto que el movimiento agregó. Quitárselo a la regla para comparar
     * confundiría «Prima de junio 2026» (SÍ es la prima) con un «Prima» a secas (de cuál prima, no
     * dice).
     */
    @Test
    fun `un nombre de regla que ya contiene un mes no se afloja de mas`() {
        assertTrue(nombreDeMovimientoPegaConRegla("Prima de junio", "Prima de junio 2026"))
        assertFalse(nombreDeMovimientoPegaConRegla("Prima de junio", "Prima"))
    }

    @Test
    fun `vacio no pega con nada`() {
        assertFalse(nombreDeMovimientoPegaConRegla("", "Octubre 2026"))
        assertFalse(nombreDeMovimientoPegaConRegla("   ", "Salario Octubre 2026"))
    }

    @Test
    fun `el movimiento mas corto que la regla no pega`() {
        assertFalse(nombreDeMovimientoPegaConRegla("Cuota Hipoteca", "Cuota"))
    }

    @Test
    fun `un conector suelto sin fecha al lado no se perdona`() {
        assertFalse(nombreDeMovimientoPegaConRegla("Salario", "Salario de"))
    }

    /**
     * Los bancos pegan las palabras del comercio (`SMARTFIT`, `DIDIFOOD`, `CLAROHOGAR`), y antes de
     * perdonar el mes/año esto pegaba por la clave comparable completa. Comparar palabra por
     * palabra no puede reemplazar eso: tiene que sumarse a él.
     */
    @Test
    fun `las palabras pegadas o separadas siguen pegando como antes`() {
        assertTrue(nombreDeMovimientoPegaConRegla("Smart Fit", "SMARTFIT"))
        assertTrue(nombreDeMovimientoPegaConRegla("SmartFit", "SMART FIT"))
        assertTrue(nombreDeMovimientoPegaConRegla("Salario", "Salario Octubre 2026"))
        assertFalse(nombreDeMovimientoPegaConRegla("Gimnasio", "Gimnasio Caro"))
    }

    /** Las abreviaturas de tres letras solo se perdonan como palabra entera, nunca como pedazo. */
    @Test
    fun `una abreviatura de mes solo cuenta como palabra completa`() {
        assertFalse(nombreDeMovimientoPegaConRegla("Salario", "Salario Sepultura"))
        assertFalse(nombreDeMovimientoPegaConRegla("Salario", "Salario Mercado"))
        assertTrue(nombreDeMovimientoPegaConRegla("Arriendo", "Arriendo Mar 2026"))
    }
}
