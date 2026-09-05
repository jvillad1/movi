package com.jvillada.movi.server.version

/**
 * Qué commit está corriendo este proceso.
 *
 * **Por qué existe.** El 2026-09-02 el build de Railway quedó roto y producción se quedó cuatro
 * merges atrás durante horas sin que nadie se enterara: cuando el build falla, Railway sigue
 * sirviendo la versión anterior y la app contesta 200 a todo. La única señal disponible para
 * saber si un merge había llegado era el hash del bundle wasm servido en `/composeApp.js`, y esa
 * señal es ciega dos veces: un cambio que no toca la web produce un bundle idéntico (y entonces
 * «no cambió» significa lo mismo si desplegó bien que si el build murió), y aunque cambie no dice
 * *cuál* commit es.
 *
 * **Resolución**, en orden y con el mismo patrón que VapidConfig/AdminConfig (system property
 * primero para poder probarlo sin tocar el ambiente):
 *
 *  1. `movi.commit.sha` (system property) — solo tests.
 *  2. `RAILWAY_GIT_COMMIT_SHA` (env) — lo inyecta Railway en el proceso de cada despliegue.
 *  3. `MOVI_COMMIT_SHA` (env) — red de contención: el Dockerfile lo hornea en la imagen final a
 *     partir del build-arg homónimo, por si la variable de runtime no llegara.
 *
 * **No inventa.** Un valor que no parece un SHA (vacío, `unknown`, el nombre de una rama, lo que
 * sea) se descarta y la respuesta pasa a ser «no lo sé» explícito. Es preferible un `null` visible
 * a un string que un script pueda confundir con un commit real.
 */
object DeployedCommit {

    private val FORMATO_SHA = Regex("^[0-9a-f]{7,40}$")

    /** El SHA desplegado, o `null` si este proceso no lo sabe. */
    fun sha(): String? = listOf(
        System.getProperty("movi.commit.sha"),
        System.getenv("RAILWAY_GIT_COMMIT_SHA"),
        System.getenv("MOVI_COMMIT_SHA"),
    ).firstNotNullOfOrNull { candidato ->
        candidato?.trim()?.lowercase()?.takeIf { FORMATO_SHA.matches(it) }
    }
}
