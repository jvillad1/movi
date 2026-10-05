package com.jvillada.movi.shared.model

/**
 * # Cuando la IA no está disponible (y no es culpa del archivo ni de la pregunta)
 *
 * El 2026-10-04 la cuenta de Anthropic se quedó sin crédito: cada llamada contestaba
 * `400 · Your credit balance is too low…`. El server lo dejaba salir como un 500 crudo, y la app
 * decía «Error en el servidor» en «Compartir con Movi» y «Error llamando a Claude: 400 …» en Movi
 * AI. Ninguna de las dos le dice al dueño lo que importa: que su archivo está a salvo y que no es
 * algo que él pueda arreglar reintentando.
 *
 * Ahora el server contesta **503 con uno de estos códigos** —en el cuerpo de las rutas que leen
 * papeles, en [AiChatResponse.codigo] en el chat— y la app los traduce a una frase fija. Viven en
 * `:core` porque los escribe el server y los lee la app: un código que se escribe en dos lados
 * termina siendo dos códigos.
 */

/** La cuenta de Anthropic no tiene saldo (el 400 de «credit balance» o un 402 de facturación). */
const val IA_SIN_CREDITO: String = "IA_SIN_CREDITO"

/** La API rechazó la llamada por la clave (401/403) o por carga (429/529): no se pudo leer ahora. */
const val IA_NO_DISPONIBLE: String = "IA_NO_DISPONIBLE"

/** ¿Este cuerpo o código es uno de los de arriba? Tolera espacios y un salto de línea al final. */
fun esCodigoDeIaNoDisponible(codigo: String?): Boolean =
    codigo?.trim().let { it == IA_SIN_CREDITO || it == IA_NO_DISPONIBLE }
