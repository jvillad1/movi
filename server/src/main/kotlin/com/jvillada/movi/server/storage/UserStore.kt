package com.jvillada.movi.server.storage

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.shared.model.User
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class StoredUser(
    val id: String,
    val email: String,
    val name: String,
    val passwordHash: String,
) {
    fun toPublic() = User(id = id, email = email, name = name)
}

class UserStore(file: File) {
    private val store = JsonListStore(file, StoredUser.serializer(), emptyList())

    suspend fun findByEmail(email: String): StoredUser? =
        store.snapshot().find { it.email.lowercase() == email.lowercase() }

    suspend fun create(email: String, name: String, plainPassword: String): StoredUser {
        val hash = BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray())
        val user = StoredUser(
            id = "usr_${System.currentTimeMillis()}",
            email = email.lowercase().trim(),
            name = name.trim(),
            passwordHash = hash,
        )
        store.mutate { it.add(user) }
        return user
    }

    fun checkPassword(user: StoredUser, plainPassword: String): Boolean =
        BCrypt.verifyer().verify(plainPassword.toCharArray(), user.passwordHash).verified
}
