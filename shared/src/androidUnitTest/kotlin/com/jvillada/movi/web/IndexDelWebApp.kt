package com.jvillada.movi.web

import java.io.File
import kotlin.test.fail

/** El `index.html` del webApp, desde donde corra la prueba (`shared/` o la raíz). */
internal fun indexDelWebApp(): String {
    val candidatos = listOf("../webApp/src/wasmJsMain/resources/index.html", "webApp/src/wasmJsMain/resources/index.html")
    val archivo = candidatos.map(::File).firstOrNull { it.exists() }
        ?: fail("No encuentro index.html del webApp desde ${File(".").absolutePath}")
    return archivo.readText()
}
