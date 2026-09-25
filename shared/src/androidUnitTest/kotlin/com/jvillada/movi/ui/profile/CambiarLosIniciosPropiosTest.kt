package com.jvillada.movi.ui.profile

import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * # Guardar un arranque propio no borra los demás
 *
 * `periodStarts` viaja entero y reemplaza al que había. Si se armara sobre lo que la pantalla tenía
 * —un perfil que no se pudo leer al abrirla deja el mapa vacío— guardar el arranque de un mes
 * borraba la excepción real del dueño, `{"2026-10":"2026-09-24"}`, y con ella la ventana de octubre
 * en toda la app. Por eso la escritura relee el perfil justo antes, y sin esa lectura no escribe.
 */
class CambiarLosIniciosPropiosTest {

    private val perfil = UserProfile(
        id = "u1", email = "jvillad1@gmail.com", name = "Juan", avatarColor = "#FF0000",
        periodCutoffDay = 25, periodStarts = mapOf("2026-10" to "2026-09-24"),
    )

    private inner class Repo(private val leer: () -> UserProfile) : RepositorioDePrueba() {
        val escrituras = mutableListOf<UpdateProfileRequest>()
        override suspend fun getUserProfile(): UserProfile = leer()
        override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
            escrituras += request
            return perfil.copy(periodStarts = request.periodStarts ?: perfil.periodStarts)
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `suma el arranque nuevo a lo recien leido, no a lo que tenia la pantalla`() {
        val repo = Repo { perfil }
        Repositories.sustitutoDePrueba = repo

        runBlocking { guardarInicioDelPeriodo(PeriodoFinanciero(2026, 11), "2026-10-20") }

        assertEquals(
            listOf(UpdateProfileRequest(periodStarts = mapOf("2026-10" to "2026-09-24", "2026-11" to "2026-10-20"))),
            repo.escrituras,
        )
    }

    @Test
    fun `si la relectura falla no se escribe nada`() {
        val repo = Repo { error("sin red") }
        Repositories.sustitutoDePrueba = repo

        assertFailsWith<IllegalStateException> {
            runBlocking { guardarInicioDelPeriodo(PeriodoFinanciero(2026, 11), "2026-10-20") }
        }
        assertTrue(repo.escrituras.isEmpty())
    }

    @Test
    fun `si lo recien leido ya no admite el cambio, no se escribe nada`() {
        val repo = Repo { perfil }
        Repositories.sustitutoDePrueba = repo

        assertFailsWith<LosPeriodosCambiaron> { runBlocking { cambiarLosIniciosPropios { null } } }
        assertTrue(repo.escrituras.isEmpty())
    }
}
