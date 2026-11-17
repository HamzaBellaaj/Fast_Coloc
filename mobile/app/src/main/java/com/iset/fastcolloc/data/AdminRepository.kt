package com.iset.fastcolloc.data

import com.iset.fastcolloc.data.local.UserDao
import com.iset.fastcolloc.data.local.UserEntity
import com.iset.fastcolloc.model.AdminUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AdminRepository(
    private val userDao: UserDao,
    private val sessionManager: SessionManager
) {
    private fun requireAdmin() {
        check(sessionManager.hasValidSession() && sessionManager.getAccountType() == "ADMIN") {
            "Accès réservé à l'administrateur."
        }
    }

    fun observeUsers(): Flow<List<AdminUser>> {
        requireAdmin()
        return userDao.observeAllRegularUsers().map { users -> users.map { it.toAdminUser() } }
    }

    suspend fun setBlocked(userId: Int, blocked: Boolean) {
        requireAdmin()
        val changed = userDao.setBlocked(userId, blocked)
        check(changed > 0) { "Utilisateur introuvable ou action non autorisée." }
    }

    suspend fun deleteUser(userId: Int) {
        requireAdmin()
        check(userId != sessionManager.getUserId()) { "Vous ne pouvez pas supprimer votre propre compte administrateur." }
        val deleted = userDao.deleteRegularUser(userId)
        check(deleted > 0) { "Utilisateur introuvable ou suppression non autorisée." }
    }

    suspend fun updateUser(userId: Int, firstName: String, lastName: String, email: String, phone: String) {
        requireAdmin()
        require(firstName.isNotBlank() && lastName.isNotBlank()) { "Le nom et le prénom sont obligatoires." }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { "Adresse e-mail invalide." }
        require(phone.isBlank() || phone.matches(Regex("[0-9]{8}"))) { "Le téléphone doit contenir 8 chiffres." }

        val existing = userDao.findById(userId) ?: error("Utilisateur introuvable.")
        require(existing.accountType != "ADMIN") { "Les comptes administrateurs ne peuvent pas être modifiés depuis cette interface." }
        val normalizedEmail = email.trim().lowercase()
        val emailOwner = userDao.findByEmail(normalizedEmail)
        require(emailOwner == null || emailOwner.id == userId) { "Cette adresse e-mail est déjà utilisée." }

        userDao.update(existing.copy(
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            email = normalizedEmail,
            phone = phone.trim()
        ))
    }

    private fun UserEntity.toAdminUser() = AdminUser(
        id = id,
        firstName = firstName,
        lastName = lastName,
        email = email,
        phone = phone,
        accountRole = accountType,
        collocationRole = role,
        isBlocked = isBlocked
    )
}
