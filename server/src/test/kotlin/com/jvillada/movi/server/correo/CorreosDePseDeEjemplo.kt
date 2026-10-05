package com.jvillada.movi.server.correo

/**
 * **El correo de PSE, sintético pero con la forma del real** (`serviciopse@achcolombia.com.co`): el
 * asunto «PSE - Transacción Aprobada CUS …» y un cuerpo de texto plano con una etiqueta por línea.
 * Los nombres de empresas públicas se dejan; el nombre del dueño y los CUS son inventados.
 */
fun asuntoDePse(cus: String = "700000001") = "PSE - Transacción Aprobada CUS $cus"

fun cuerpoDePse(
    valor: String = "\$ 138.600,00",
    empresa: String = "Coomeva Medicina Prepagada S.A.",
    descripcion: String = "Coomeva Pago de saldo plan familiar",
    fecha: String = "03/10/2026",
    cus: String = "700000001",
): String = """
    ¡Hola, Persona de Prueba!
    Los siguientes son los datos de tu transacción:
    Valor: $valor
    Empresa: $empresa
    Descripción: $descripcion
    Fecha de la transacción: $fecha
    CUS: $cus
    Este correo es informativo, por favor no lo respondas.
""".trimIndent()

/** Asunto y cuerpo como los junta `textoDelCorreo`: así lo guarda la bandeja y así lo lee `parseSms`. */
fun textoDePse(
    valor: String = "\$ 138.600,00",
    empresa: String = "Coomeva Medicina Prepagada S.A.",
    descripcion: String = "Coomeva Pago de saldo plan familiar",
    fecha: String = "03/10/2026",
    cus: String = "700000001",
): String = textoDelCorreo(asuntoDePse(cus), limpiarCuerpoDelCorreo(cuerpoDePse(valor, empresa, descripcion, fecha, cus)))
