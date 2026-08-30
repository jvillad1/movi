package com.jvillada.movi.ui.extractos

import androidx.compose.runtime.Composable

/**
 * Qué archivos deja elegir el selector.
 *
 * Nace porque el mismo selector lo usan dos pantallas con necesidades **opuestas**, y hasta acá
 * mandaba la primera que se escribió:
 *
 * - **Extractos** quiere filtrar. Ofrecer un `.zip` cuando lo único que se puede parsear es un
 *   PDF, un CSV o una imagen es prometer algo que va a fallar después de la subida.
 * - **Documentos** no. La pantalla dice «contratos y cualquier papel que quieras tener a mano», y
 *   con el filtro de extractos un contrato en `.docx` o un `.zip` con la escritura escaneada
 *   simplemente **no se podían elegir** en la web ni en iOS. Lo encontró la revisión.
 */
enum class TiposDeArchivo {
    /** PDF, CSV, hojas de cálculo e imágenes: lo que el importador sabe leer. */
    EXTRACTOS,

    /** Cualquier cosa. Un papel es un papel. */
    TODOS,

    /**
     * Solo imágenes. Lo usa el chat de Movi AI, que contesta «Por ahora solo imágenes» a
     * cualquier otra cosa — así que ofrecer un PDF en su selector es mandar al dueño derecho a
     * ese error, que es justo lo que este enum vino a evitar.
     */
    IMAGENES,
}

@Composable
expect fun rememberFilePicker(
    aceptar: TiposDeArchivo = TiposDeArchivo.EXTRACTOS,
    onResult: (fileName: String, bytes: ByteArray, mimeType: String) -> Unit,
): () -> Unit
