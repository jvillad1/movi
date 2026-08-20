package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import com.jvillada.movi.shared.model.SmsMessage

@Composable
actual fun rememberSmsSync(onResult: (List<SmsMessage>) -> Unit): SmsSyncController =
    SmsSyncController(available = false, requestAndRead = {})
