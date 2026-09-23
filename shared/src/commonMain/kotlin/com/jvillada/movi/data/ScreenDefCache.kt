package com.jvillada.movi.data

import com.jvillada.movi.shared.model.ScreenDefinition

/**
 * In-memory-only cache of the last valid [ScreenDefinition] fetched per screen, kept for
 * the lifetime of the process (no persistence — movi's real surface is the wasm PWA,
 * which has no SQLDelight). This is anti-rotura layer 2: if a later fetch fails or the
 * server returns something invalid, the UI keeps rendering the last good definition
 * instead of falling all the way back to `defaultDashboardDefinition()` (core) — the same list
 * the server seeds.
 *
 * Since Ola A the last valid definition is ALSO kept on the device, per user, next to the Inicio's
 * data snapshot (see `InstantaneaDelInicio`): this in-memory copy dies with the process, and a
 * cold start without it had to wait for the definition before asking for any figure.
 */
object ScreenDefCache {
    var dashboard: ScreenDefinition? = null
}
