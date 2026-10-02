package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # Lo que el asistente guarda de una conversación (Ola 3 · «Movi actúa»)
 *
 * Hasta la Ola 3 el chat vivía en un `remember` de la pantalla: salir y volver era empezar de
 * cero, aunque el server ya guardaba cada turno en `ai_turns` para diagnosticar. Ahora esa misma
 * tabla es la que devuelve la conversación al volver.
 */

/**
 * `GET /api/ai/conversacion`: los últimos mensajes de la conversación en curso, del más viejo al
 * más nuevo, listos para pintar. Vacía después de «Nueva conversación».
 */
@Serializable
data class ConversacionDelAsistente(
    val mensajes: List<ChatMessage> = emptyList(),
)
