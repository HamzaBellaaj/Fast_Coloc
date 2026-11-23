package com.iset.fastcolloc.data

import com.iset.fastcolloc.data.local.ProfileCacheDao
import com.iset.fastcolloc.data.local.ProfileSummary
import com.iset.fastcolloc.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import com.google.gson.JsonParser

class SessionExpired : IOException("Ta session a expiré. Reconnecte-toi.")
class AccountBlocked : IOException("Ce compte est bloqué. Contacte l’administration.")
class AuthRepository(val api: FastColocApi, val sessions: SessionStore, private val cache: ProfileCacheDao,
    private val clearBusinessCache: suspend () -> Unit = {}) {
    private val rotation = Mutex()
    val cachedProfile = cache.observe()
    private suspend fun token(rejected: String? = null) = rotation.withLock {
        val value = sessions.session() ?: throw SessionExpired()
        if ((rejected == null && value.validFor(45)) || (rejected != null && value.accessToken != rejected)) return@withLock
        try {
            val updated = api.refresh(RefreshRequest(value.refreshToken))
            if(sessions.session()?.refreshToken != value.refreshToken) throw SessionExpired()
            sessions.save(updated)
        }
        catch (e: HttpException) {
            if (e.code() in listOf(400, 401, 403)) { clear(); throw SessionExpired() }
            throw e
        }
    }
    suspend fun <T> authorized(action: suspend FastColocApi.() -> T): T {
        token()
        val sent = sessions.session()?.accessToken
        try { return api.action() }
        catch (e: HttpException) {
            if (e.code() == 401) {
                token(sent)
                try { return api.action() } catch (retry: HttpException) {
                    if (retry.code() == 401) { clear(); throw SessionExpired() }
                    checkBlocked(retry); throw retry
                }
            }
            checkBlocked(e); throw e
        }
    }
    private suspend fun checkBlocked(e: HttpException) {
        if (e.code() == 403 && errorCode(e) == "ACCOUNT_BLOCKED") {
            clear(); throw AccountBlocked()
        }
    }
    suspend fun clear() { sessions.clearSession(); cache.clear(); clearBusinessCache() }
    suspend fun accountId(): String {
        if(sessions.session()==null) throw SessionExpired()
        return cache.current()?.id ?: authorized { session() }.id
    }
    suspend fun login(email: String, password: String): DetailsDto {
        clear(); sessions.save(api.login(LoginRequest(email.trim(), password))); return restore()
    }
    suspend fun restore(): DetailsDto {
        try {
            val identity = authorized { session() }
            require(identity.emailConfirmed) { "Confirme ton adresse e-mail avant de continuer." }
            authorized { sync() }
            return details()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (e is SessionExpired || e is AccountBlocked || e is IllegalArgumentException) clear(); throw e }
    }
    suspend fun details(): DetailsDto {
        val value = authorized { details() }
        cache.replace(ProfileSummary(value.profile.id, value.profile.firstName, value.profile.lastName, value.profile.completedAt != null))
        return value
    }
    suspend fun signup(email: String, password: String) { validatePassword(password); api.signup(SignupRequest(email.trim(), password)) }
    suspend fun recover(email: String) { api.recover(EmailRequest(email.trim())) }
    suspend fun resend(email: String) { api.resend(EmailRequest(email.trim())) }
    suspend fun logout(): Boolean {
        var revoked = true
        try { if (sessions.hasValidSession()) authorized { logout() } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { revoked = false }
        finally { clear() }
        return revoked
    }
    companion object {
        private fun errorCode(error: HttpException): String? = runCatching {
            error.response()?.errorBody()?.source()?.peek()?.readUtf8()?.let {
                JsonParser.parseString(it).asJsonObject.get("code")?.asString
            }
        }.getOrNull()
        fun validatePassword(value: String) {
            require(value.length >= 12 && value.toByteArray(Charsets.UTF_8).size <= 72) { "Utilise au moins 12 caractères, au maximum 72 octets UTF-8." }
        }
        fun message(error: Exception): String = when (error) {
            is SessionExpired, is AccountBlocked -> error.message!!
            is HttpException -> when(error.code()) {
                400 -> "Vérifie les champs et la confirmation de ton e-mail."
                401 -> "Identifiants invalides ou e-mail non confirmé."
                403 -> when (errorCode(error)) {
                    "MESSAGE_RECIPIENT_UNAVAILABLE" -> "Ce destinataire ne peut pas recevoir de messages actuellement."
                    "MESSAGE_PROFILE_REQUIRED" -> "Complète ton profil avant d’envoyer un message."
                    else -> "Accès refusé."
                }
                409 -> "Étapes incomplètes ou conflit. Recharge tes informations."
                413 -> "Photo trop volumineuse : 5 MiB maximum."
                429 -> "Trop de tentatives. Réessaie plus tard."
                else -> "Le service est indisponible. Réessaie."
            }
            is IOException -> "Connexion impossible. Vérifie le réseau et l’adresse du serveur."
            is IllegalArgumentException -> error.message ?: "Informations invalides."
            else -> "L’opération a échoué. Réessaie."
        }
    }
}
