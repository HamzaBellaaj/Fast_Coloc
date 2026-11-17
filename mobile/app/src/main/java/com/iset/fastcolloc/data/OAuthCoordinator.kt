package com.iset.fastcolloc.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.iset.fastcolloc.data.remote.*
import java.security.MessageDigest
import java.security.SecureRandom

data class PendingOAuth(val verifier: String, val state: String, val created: Long)
class OAuthCoordinator(context: Context, private val repository: AuthRepository) {
    private val store = SecureStore(context)
    private val gson = Gson()
    private fun random() = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    suspend fun begin(): String {
        val pending = PendingOAuth(random(), random(), System.currentTimeMillis())
        store.write("oauth", gson.toJson(pending))
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(pending.verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return repository.api.google(challenge, pending.state).authorizationUrl
    }
    suspend fun accept(uri: Uri): Boolean {
        require(uri.scheme == "fastcoloc" && uri.host == "auth" && uri.path == "/callback" && uri.userInfo == null && uri.port == -1) { "Retour Auth invalide." }
        val fragment = Uri.parse("https://callback.invalid/?" + (uri.fragment ?: ""))
        fun param(key: String) = uri.getQueryParameter(key) ?: fragment.getQueryParameter(key)
        require(param("error") == null) { "Lien expiré ou connexion annulée." }
        val kind = param("type")
        val code = param("code")
        val value = if (code != null) {
            val pending = store.read("oauth")?.let { gson.fromJson(it, PendingOAuth::class.java) }
                ?: throw IllegalArgumentException("Relance Google depuis cette application.")
            require(System.currentTimeMillis() - pending.created in 0..600000 && param("state") == pending.state) { "Le retour ne correspond pas à la demande Google." }
            store.delete("oauth")
            repository.api.exchange(ExchangeRequest(code, pending.verifier))
        } else if (param("token_hash") != null && kind in listOf("signup", "email", "recovery")) {
            repository.api.verify(VerifyRequest(param("token_hash")!!, kind!!))
        } else {
            require(kind in listOf("signup", "email", "recovery")) { "Type de lien invalide." }
            Session(param("access_token") ?: error("Session absente."), param("refresh_token") ?: error("Session absente."), param("expires_in")?.toLongOrNull() ?: 3600)
        }
        repository.clear()
        repository.sessions.save(value)
        val identity = repository.authorized { session() }
        require(identity.emailConfirmed) { "Adresse non confirmée." }
        if (kind != "recovery") repository.restore()
        return kind == "recovery"
    }
}
