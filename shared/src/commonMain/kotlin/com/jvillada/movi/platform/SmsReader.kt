package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import com.jvillada.movi.shared.model.SmsMessage

/**
 * Platform-specific SMS reader controller.
 * On Android: requests READ_SMS permission and queries the device inbox.
 * On iOS/Web: always unavailable (no-op).
 */
class SmsSyncController(
    val available: Boolean,
    val requestAndRead: () -> Unit,
)

@Composable
expect fun rememberSmsSync(onResult: (List<SmsMessage>) -> Unit): SmsSyncController
