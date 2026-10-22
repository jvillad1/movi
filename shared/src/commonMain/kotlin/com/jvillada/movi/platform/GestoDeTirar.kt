package com.jvillada.movi.platform

/**
 * ¿En esta plataforma se puede **tirar hacia abajo para actualizar**? En el teléfono (Android e
 * iOS) sí. En la web no: ahí se mueve con la rueda del mouse, y la rueda hacia arriba estando ya
 * arriba de todo tira del indicador sin que nunca llegue un «soltar» —queda a medio salir—. La web
 * sigue teniendo «Reintentar» y la recarga de siempre.
 */
expect val tirarParaActualizarDisponible: Boolean
