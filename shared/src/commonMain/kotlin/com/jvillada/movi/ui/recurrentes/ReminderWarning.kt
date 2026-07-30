package com.jvillada.movi.ui.recurrentes

/**
 * Decide si la pantalla de Recurrentes debe mostrar el aviso de
 * "tus recordatorios no te van a llegar".
 *
 * Es cierto y accionable solo cuando hay al menos un pago próximo por el que
 * recordar Y las notificaciones push no están activas. `pushStatus` viene de
 * [com.jvillada.movi.platform.PushOptIn.status]: "enabled" | "disabled" | "denied" | "unsupported".
 *
 * - "enabled"     -> ya funciona, no hay nada que avisar.
 * - "unsupported" -> la plataforma (iOS/Android nativo hoy) no tiene ninguna
 *                    instrucción posible para el usuario, así que tampoco se avisa.
 * - "disabled" / "denied" -> el aviso aplica; RecurrentesScreen distingue esos dos
 *                    para mostrar una acción ("Activar") o una instrucción
 *                    ("reactiva en el navegador"), pero ambos cuentan como "avisar".
 */
fun shouldShowReminderWarning(pushStatus: String, hasUpcomingPayments: Boolean): Boolean =
    hasUpcomingPayments && pushStatus != "enabled" && pushStatus != "unsupported"
