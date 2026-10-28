package com.jvillada.movi.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * # Las escalas del rediseño (ola R0, 26-oct-2026)
 *
 * El pedido del dueño, literal: «que los espaciados, los componentes de UI y todo sea extremadamente
 * estandarizado y correcto en toda la app, que no haya inconsistencias entre pantallas». La auditoría
 * (`docs/diseno/auditoria-2026-10.md`) contó **2.674 `.dp` escritos a mano con 76 valores
 * distintos** en `ui/`, y la tarjeta más usada con **27 rellenos distintos**. Este archivo es la
 * lista corta de la que se elige; nada más.
 *
 * Son `object`s con `val`, no `CompositionLocal`: no cambian con el tema ni con nada, así que se
 * pueden leer fuera de una composición (una constante, una prueba pura). `Movi.espacios` y
 * `Movi.formas` siguen existiendo —los nombra media app— y ahora salen de acá.
 *
 * La regla que lo cierra es una prueba: `LosValoresSueltosSoloBajanTest` (en las pruebas de calidad
 * de `:core`) no deja que el número de `.dp` sueltos suba, y `ElSistemaNoTieneValoresSueltosTest`
 * no deja ninguno dentro de `ui/components/ds/`.
 */

/**
 * **La escala de espacio: ocho pasos de 4 en 4 al principio y de 8 en 8 después.** Es la que pidió el
 * brief y la auditoría no dio razón para cambiarla: los cuatro valores más usados que no están acá
 * (14, 10, 6 y 18, con 799 usos entre los cuatro) son «el de al lado» de uno que sí está, puestos a
 * ojo. Se migran a su vecino (14 → 12 o 16, 10 → 8 o 12, 6 → 4 u 8, 18 → 16 o 20) pantalla por
 * pantalla.
 *
 * Cuándo usar cada uno lo dice [Medida] para los casos que se repiten; para el resto:
 *
 * - [xxs] entre un ícono y su rótulo, y entre dos renglones apretados.
 * - [xs] entre hermanos chicos: chips, píldoras, botones de una fila.
 * - [s] dentro de una sección: entre el título y lo que titula, entre dos tarjetas de una lista.
 * - [m] el margen de la pantalla y el relleno de una superficie.
 * - [l] el relleno horizontal de un botón principal.
 * - [xl] entre dos secciones.
 * - [xxl] y [xxxl] para respirar en vacíos y pantallas de una sola cosa.
 */
object Espacio {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 40.dp
}

/**
 * **Los radios: cuatro y la píldora.**
 *
 * **Vuelta 29 (opción 3):** [l] pasó de 16 a 18 —las listas y las tarjetas de la maqueta— y [xl] de 24
 * a 22 —la tarjeta principal (el héroe), la de una cuenta y las hojas—. Los botones son píldoras.
 *
 * Antes eran 6 / 11 / 16 / píldora (`FormasDeMovi`), con 247 radios escritos a mano al lado
 * (12, 10, 14 y 8 los más usados). Se unificó con criterio: el 6 subió a 8 y el 11 a 12 —en la grilla
 * de 4 y, de paso, los dos valores que la app ya usaba a mano más que los tokens—, el 16 quedó, y se
 * sumó el 24 que piden las hojas y las tarjetas de cuenta del camino 1 (las hojas estaban en 28).
 *
 * - [s] chips chicos, campos, muestras de color.
 * - [m] filas destacadas y botones.
 * - [l] superficies: tarjetas, listas agrupadas, propuestas.
 * - [xl] hojas y tarjetas de cuenta.
 * - [xxl] el borde de abajo de la banda amarilla del Inicio (vuelta 39, la opción 3).
 * - [pill] píldoras, filtros y círculos. 999 y no un porcentaje: en Compose un radio mayor que medio
 *   lado se recorta al lado, así que siempre da la píldora completa sin saber el alto.
 */
object Radio {
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 18.dp
    val xl: Dp = 22.dp
    val xxl: Dp = 28.dp
    val pill: Dp = 999.dp
}

/**
 * **Los íconos: 16, 20 y 24. Nada más.** La auditoría encontró íconos de 11, 12, 13, 14, 15, 16, 18,
 * 20, 22, 24 y 26 dp.
 *
 * - [s] dentro de un texto o de una píldora.
 * - [m] en una fila, en un botón y en la barra de pestañas.
 * - [l] solo, como acción de un encabezado o en el centro de un círculo grande.
 */
object Icono {
    val s: Dp = 16.dp
    val m: Dp = 20.dp
    val l: Dp = 24.dp
}

/**
 * **Las reglas por contexto, con nombre.** La escala dice qué valores hay; esto dice cuál va en cada
 * lugar, para que dos pantallas no elijan distinto la misma cosa.
 */
object Medida {
    /** El margen a cada lado de una pantalla. Era 20 (`Movi.espacios.margen`); el camino 1 usa 16. */
    val margenDePantalla: Dp = Espacio.m

    /** Entre dos secciones que hablan de cosas distintas. Era 32 (`Movi.espacios.seccion`). */
    val entreSecciones: Dp = Espacio.xl

    /** El relleno de adentro de una superficie (tarjeta, propuesta). */
    val rellenoDeSuperficie: Dp = Espacio.m

    /** Dentro de una sección: del título a lo que titula, y entre dos tarjetas de la misma lista. */
    val dentroDeSeccion: Dp = Espacio.s

    /** El alto de una fila de lista de un renglón. */
    val altoDeFila: Dp = 56.dp

    /**
     * El alto de una fila de lista de dos renglones (título y subtítulo). Vuelta 29: 58, el de las
     * filas de Actividad en la opción 3 (era 64).
     */
    val altoDeFilaDoble: Dp = 58.dp

    /** El objetivo táctil mínimo: nada que se toque mide menos de esto (Material, WCAG 2.5.8). */
    val objetivoTactil: Dp = 48.dp

    /** El alto de un botón chico: solo dentro de una tarjeta, nunca suelto. */
    val altoChico: Dp = 36.dp

    /** El alto de un filtro (`PildoraDeFiltro`): 44, el de los chips de la opción 3 (vuelta 29). */
    val altoDeFiltro: Dp = 44.dp

    /**
     * El alto del encabezado de una pantalla, igual en todas. Vuelta 29: siguió en 56 (una primera
     * versión lo subió a 64 por el título de pestaña, hasta que su interlineado bajó a 30).
     */
    val altoDeEncabezado: Dp = 56.dp

    /** El alto de la barra de pestañas del teléfono: 76, el de la opción 3 (vuelta 38; era 64). */
    val altoDeBarraDePestanas: Dp = 76.dp

    /**
     * La píldora amarilla detrás del ícono de la pestaña activa (opción 3, vuelta 29): 56 × 30, en la
     * barra del teléfono y en el rail.
     */
    val anchoDePildoraDePestana: Dp = 56.dp

    /** Ver [anchoDePildoraDePestana]. */
    val altoDePildoraDePestana: Dp = 30.dp

    /** El botón flotante de «Agregar». */
    val botonFlotante: Dp = 56.dp

    /** El círculo del inicio de una fila: avatar de comercio, ícono en círculo, persona. */
    val avatar: Dp = 40.dp

    /** El círculo grande: el de un vacío, el de arriba de un detalle. */
    val avatarGrande: Dp = 48.dp

    /** El círculo de una acción rápida (la cuadrícula del camino 3). */
    val accionRapida: Dp = 56.dp

    /**
     * **La banda amarilla del Inicio** (vuelta 39, la opción 3): de arriba de la pantalla —debajo de la
     * barra del sistema— hasta la mitad de la tarjeta héroe, que se monta encima. El encabezado
     * ([altoDeEncabezado]) va sobre ella y el resto lo dibuja el scroll, con [Radio.xxl] abajo.
     */
    val altoDeLaBandaDelInicio: Dp = 168.dp

    /**
     * Los círculos del encabezado del Inicio, sobre la banda: el avatar blanco y los dos botones en
     * `marcaSuave` (ocultar los saldos y la campana). 44, como en la maqueta: el encabezado de 64 deja
     * diez arriba y abajo.
     */
    val circuloDeLaBanda: Dp = 44.dp

    /**
     * El alto de una tarjeta de cuenta del carrusel «Tus cuentas» (ola R1): el nombre arriba y el
     * saldo en `cifraDeTarjeta` abajo, con aire entre los dos.
     */
    val altoDeTarjetaDeCuenta: Dp = 128.dp

    /** El alto de una píldora de estado. */
    val altoDePildora: Dp = 24.dp

    /** La insignia con un conteo, arriba de una acción rápida. */
    val insignia: Dp = 20.dp

    /** El grosor de una barra de progreso. */
    val altoDeBarraDeProgreso: Dp = 4.dp

    /**
     * El grosor de la barra de una tarjeta de color: la de «Falta por pagar» en Pagos (vuelta 40, 10
     * en la maqueta de la opción 3), que se lee de lejos sobre el amarillo.
     */
    val altoDeBarraEnTarjeta: Dp = 10.dp

    /** El alto de la marca sobre una barra de progreso («la mitad», en Espacios). */
    val altoDeMarcaDeBarra: Dp = 12.dp

    /** Una línea: separador, borde. */
    val trazo: Dp = 1.dp

    /** El borde de un botón secundario: 1,5, como en la maqueta de la opción 3. */
    val trazoDeBoton: Dp = 1.5.dp

    /**
     * El borde de la insignia de una acción rápida (2 en la maqueta): la recorta del círculo que tapa.
     */
    val trazoDeInsignia: Dp = 2.dp

    /** El trazo de la ruedita de un botón que está cargando. */
    val trazoDeCarga: Dp = 2.dp

    /**
     * El ancho del cursor que parpadea al lado de una cifra que se escribe con el teclado físico (el
     * monto de «Agregar» fuera del teléfono, vuelta 34). El alto lo pone la letra de la cifra.
     */
    val anchoDelCursor: Dp = 2.dp

    /**
     * Lo más que se estira un botón de ancho completo **fuera del teléfono** (vuelta 33): el ancho de
     * un teléfono grande. A 1.280 px «Pedirle a Caro» medía 656 dp: un botón que cruza media pantalla
     * no se lee como un botón. En el teléfono nunca llega a este tope (390 − 64 = 326).
     */
    val anchoMaximoDeBoton: Dp = 360.dp
}

/**
 * **Las medidas de una gráfica dibujada a mano** (ola R4, «Tu plata, día a día» de Plan): el alto,
 * el aire de arriba y abajo para que la cifra del punto más bajo no se corte, el trazo de la línea y
 * los puntos. Estaban como `6.dp`, `3.5.dp`, `2.5.dp` sueltos dentro del `Canvas`.
 */
object Grafica {
    /** El alto de la gráfica de una tarjeta. */
    val alto: Dp = 120.dp

    /** El aire de arriba y de abajo de la línea, y entre un punto y su cifra. */
    val margen: Dp = 6.dp

    /** El trazo de la línea. */
    val trazo: Dp = Medida.trazoDeCarga

    /** El radio de un punto chico sobre la línea (un pago pendiente). */
    val punto: Dp = 2.5.dp

    /** El aro de fondo alrededor de un punto chico, y el del día elegido. */
    val aroDelPunto: Dp = 3.5.dp

    /** El punto destacado: el día más justo. */
    val puntoDestacado: Dp = 4.dp
}

/**
 * **La sombra suave de la opción 3** (vuelta 29): la de la tarjeta del héroe y la de avisos, en la
 * maqueta `0 1px 2px rgba(31,27,22,.06), 0 10px 24px rgba(31,27,22,.08)`. Compose no tiene dos
 * sombras de CSS: la plataforma dibuja una con su luz, desde [destacada] de elevación y con el color
 * de `ColoresDeMovi.sombra`. Solo la llevan las superficies destacadas; **las listas van sin sombra**.
 */
object Sombra {
    /** La elevación de una superficie destacada: el héroe, una propuesta. */
    val destacada: Dp = 6.dp
}
