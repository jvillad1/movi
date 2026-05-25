package com.jvillada.movi.ui.extractos

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit
): () -> Unit = { }
