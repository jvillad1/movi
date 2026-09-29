package com.jvillada.movi.platform

/**
 * **Le devuelve el teclado físico a la app** después de que un campo de texto se fue.
 *
 * Solo hace algo en la web. Ahí un campo de Compose escribe a través de un `<input>` oculto del
 * DOM, que se lleva el foco del navegador; cuando el campo desaparece (se cerró la Nota de
 * «Agregar»), el foco queda en el `<body>` y **ninguna tecla vuelve a llegar al lienzo** hasta que
 * se haga clic: Compose ni se entera, así que su propio reclamo de foco no alcanza. Medido con
 * Playwright el 29-sep (`document.activeElement` = `BODY`; enfocar el `<canvas>` lo arregla).
 *
 * En Android e iOS el foco no pasa por un DOM: no hay nada que devolver.
 */
internal expect fun devolverElTecladoAlLienzo()
