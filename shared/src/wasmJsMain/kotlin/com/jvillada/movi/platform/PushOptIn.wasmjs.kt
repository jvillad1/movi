package com.jvillada.movi.platform

private fun jsSupported(): Boolean = js("!!(window.moviPush && window.moviPush.supported())")
private fun jsStatus(): String = js("window.moviPush ? window.moviPush.status() : 'unsupported'")
private fun jsEnable(): Boolean = js("(window.moviPush && window.moviPush.enable(), true)")
private fun jsDisable(): Boolean = js("(window.moviPush && window.moviPush.disable(), true)")

actual object PushOptIn {
    actual val supported: Boolean get() = jsSupported()
    actual fun status(): String = jsStatus()
    actual fun enable() { jsEnable() }
    actual fun disable() { jsDisable() }
}
