package com.iset.fastcolloc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.iset.fastcolloc.data.AppContainer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import com.iset.fastcolloc.data.remote.SendMessageDto
import com.iset.fastcolloc.data.remote.StartConversationDto
import java.util.UUID

/** Opt-in diagnostics using the signed-in account; never sends messages or changes read cursors. */
@RunWith(AndroidJUnit4::class)
class MessagingReadOnlyDeviceTest {
    @Test fun signedInAccountCanReadMessagingEndpoints() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fastcolocReadOnly") == "true")
        val app = AppContainer.get(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("Sign in on the device first", app.sessions.session() != null)
        val failures = mutableListOf<String>()
        suspend fun <T> inspect(label: String, action: suspend () -> T): T? = try {
            action().also { println("FASTCOLOC_READ_ONLY $label OK") }
        } catch (e: HttpException) {
            val code = runCatching { JsonParser.parseString(e.response()?.errorBody()?.string()).asJsonObject.get("code")?.asString }.getOrNull()
            val message = "$label HTTP ${e.code()} code=${code ?: "unknown"}"
            println("FASTCOLOC_READ_ONLY $message"); failures += message; null
        }
        inspect("session") { app.repository.authorized { session() } }
        val profile = inspect("profile") { app.repository.api.details() }
        if (profile != null) println("FASTCOLOC_READ_ONLY profileCompleted=${profile.profile.completedAt != null}")
        val inbox = inspect("inbox") { app.repository.api.conversations() }
        inbox?.items?.firstOrNull()?.let { row ->
            inspect("conversation") { app.repository.api.conversation(row.id) }
            inspect("history") { app.repository.api.privateMessages(row.id) }
            // Blank content is rejected by @Valid before the messaging service can write.
            suspend fun rejectedBlank(label: String, action: suspend () -> Unit) {
                try { action(); failures += "$label accepted blank content" }
                catch (e: HttpException) {
                    println("FASTCOLOC_READ_ONLY $label HTTP ${e.code()}")
                    if (e.code() != 400) failures += "$label HTTP ${e.code()}"
                }
            }
            rejectedBlank("send-validation") { app.repository.api.sendPrivateMessage(row.id, SendMessageDto("", UUID.randomUUID().toString())) }
            rejectedBlank("start-validation") { app.repository.api.startConversation(StartConversationDto(row.listingReference, "", UUID.randomUUID().toString())) }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}
