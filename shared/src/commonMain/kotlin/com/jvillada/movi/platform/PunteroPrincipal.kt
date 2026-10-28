package com.jvillada.movi.platform

/**
 * **¿El puntero principal de este aparato es un mouse o un trackpad?** (vuelta 34). Con `true` se
 * está frente a un computador, con un teclado físico al lado; con `false`, frente a una pantalla
 * que se toca —un teléfono o una tablet—, o no se sabe.
 *
 * - **Web**: la consulta `(pointer: fine)` del navegador, que habla del puntero **principal**: un
 *   portátil con pantalla táctil sigue diciendo «fino» (su puntero principal es el trackpad), y un
 *   teléfono o una tablet dicen «grueso». Un navegador sin `matchMedia` cuenta como «no se sabe».
 * - **Android e iOS**: `false`. Son de tocar; una tablet con teclado y trackpad sigue siendo de
 *   tocar, y ahí no se le quita nada a nadie.
 *
 * Se lee una vez, al componer la app. No es la clase de ancho: una tablet es ancha y se toca, y una
 * ventana de escritorio angosta es angosta y no se toca.
 */
internal expect fun elPunteroPrincipalEsFino(): Boolean
