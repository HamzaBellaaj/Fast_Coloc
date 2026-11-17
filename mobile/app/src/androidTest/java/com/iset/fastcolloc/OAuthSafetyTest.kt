package com.iset.fastcolloc

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.iset.fastcolloc.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class OAuthSafetyTest {
    @Test fun callbackRejectsWrongDestinationStateAndExpiredFlow() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val coordinator=AppContainer.get(context).oauth
        val store=SecureStore(context)
        try {
            for(uri in listOf("https://evil.invalid/callback?code=x", "fastcoloc://auth/other?code=x")) {
                try { coordinator.accept(Uri.parse(uri));fail("Foreign callback accepted") } catch(_:IllegalArgumentException) { }
            }
            store.write("oauth",Gson().toJson(PendingOAuth("v".repeat(43),"s".repeat(43),System.currentTimeMillis())))
            try { coordinator.accept(Uri.parse("fastcoloc://auth/callback?code=x&state="+"z".repeat(43)));fail("Wrong state accepted") } catch(_:IllegalArgumentException) { }
            store.write("oauth",Gson().toJson(PendingOAuth("v".repeat(43),"s".repeat(43),1)))
            try { coordinator.accept(Uri.parse("fastcoloc://auth/callback?code=x&state="+"s".repeat(43)));fail("Expired flow accepted") } catch(_:IllegalArgumentException) { }
        } finally { store.delete("oauth") }
    }
    @Test fun realGoogleInitiationReachesProviderWithNativePkceCallback() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("fastcolocLive")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val url=AppContainer.get(context).oauth.begin()
        val parsed=Uri.parse(url)
        assertEquals("https",parsed.scheme)
        val callback=Uri.parse(parsed.getQueryParameter("redirect_to"))
        assertEquals("fastcoloc",callback.scheme);assertEquals("auth",callback.host)
        assertEquals(43,callback.getQueryParameter("state")!!.length)
        assertEquals("s256",parsed.getQueryParameter("code_challenge_method"))
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects=false;connection.connectTimeout=10000;connection.readTimeout=30000
        try { assertEquals(302,connection.responseCode);assertEquals("accounts.google.com",Uri.parse(connection.getHeaderField("Location")).host) }
        finally { connection.disconnect();SecureStore(context).delete("oauth") }
    }
}
