package com.jvillada.movi.platform

actual object PushOptIn {
    actual val supported: Boolean = false
    actual fun status(): String = "unsupported"
    actual fun enable() {}
    actual fun disable() {}
}
