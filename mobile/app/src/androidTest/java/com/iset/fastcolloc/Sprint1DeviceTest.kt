package com.iset.fastcolloc

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Spinner
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.iset.fastcolloc.data.AppContainer
import com.iset.fastcolloc.ui.auth.AuthActivity
import com.iset.fastcolloc.viewmodel.Sprint1ViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Opt-in real backend recipe. The loopback fixture bridge must be running; credentials stay in memory. */
@RunWith(AndroidJUnit4::class)
class Sprint1DeviceTest {
    private fun bridge(path: String): com.google.gson.JsonObject {
        val connection = URL("http://127.0.0.1:8123$path").openConnection() as HttpURLConnection
        connection.connectTimeout=10000;connection.readTimeout=120000
        try { require(connection.responseCode == 200) { "Fixture bridge failure" }; return JsonParser.parseString(connection.inputStream.bufferedReader().use { it.readText() }).asJsonObject }
        finally { connection.disconnect() }
    }
    @Test fun realAccountsFiveStepsPhotoRefreshAndCrossPlatformProfile() = runBlocking {
        org.junit.Assume.assumeTrue("Opt-in live recipe", InstrumentationRegistry.getArguments().getString("fastcolocLive") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val container=AppContainer.get(context)
        container.repository.clear()
        val fixture=bridge("/fixture")
        val password=fixture.get("password").asString
        val unsigned=URL(BuildConfig.API_BASE_URL+"users/me").openConnection() as HttpURLConnection
        try { assertEquals(401,unsigned.responseCode) } finally { unsigned.disconnect() }
        ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
            fun vm(activity: AuthActivity) = ViewModelProvider(activity)[Sprint1ViewModel::class.java]
            fun onMain(action: (AuthActivity) -> Unit) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<AuthActivity>().first()
                    action(activity)
                }
            }
            fun destination(activity: AuthActivity) = (activity.supportFragmentManager.findFragmentById(R.id.authNavHost) as NavHostFragment).navController.currentDestination?.id
            fun await(label: String, condition: (AuthActivity) -> Boolean) {
                val end=System.currentTimeMillis()+60000
                while(System.currentTimeMillis()<end) {
                    var ok=false;onMain { ok=condition(it) }
                    if(ok)return
                    Thread.sleep(100)
                }
                var diagnostic=""
                onMain { diagnostic="destination="+destination(it)+", busy="+vm(it).busy.value+", feedback="+vm(it).message.value }
                throw AssertionError("Timed out: $label; $diagnostic")
            }

            fun waitFor(id: Int) = await("destination $id") { destination(it)==id && vm(it).busy.value!=true }
            fun text(id: Int, value: String) { onMain { it.findViewById<EditText>(id).setText(value) } }
            fun click(id: Int) { onMain { it.findViewById<android.view.View>(id).performClick() } }
            waitFor(R.id.loginFragment)
            // Login to a confirmed account provisioned by the Web-side fixture harness.
            text(R.id.etLoginEmail,fixture.get("webEmail").asString);text(R.id.etLoginPassword,password);click(R.id.btnLogin)
            waitFor(R.id.registerStep1Fragment)
            text(R.id.etFirstName,"Recette");text(R.id.etLastName,"DepuisAndroid");text(R.id.etAge,"23");click(R.id.btnNext)
            waitFor(R.id.registerStep2Fragment)
            await("countries loaded") { vm(it).countries.value.orEmpty().isNotEmpty() }
            onMain { activity ->
                val index=vm(activity).countries.value!!.indexOfFirst { it.code=="TN" }
                activity.findViewById<Spinner>(R.id.spCountry).setSelection(index+1)
            }
            await("governorates loaded") { vm(it).governorates.value.orEmpty().isNotEmpty() }
            onMain { it.findViewById<Spinner>(R.id.spGovernorate).setSelection(1) }
            await("localities loaded") { vm(it).localities.value.orEmpty().isNotEmpty() }
            onMain { it.findViewById<Spinner>(R.id.spLocality).setSelection(1) }
            text(R.id.etStreet,"Rue de recette temporaire");text(R.id.etPostalCode,"1000");click(R.id.btnNext)
            waitFor(R.id.registerStep3Fragment)
            text(R.id.etActivities,"Étudiant");text(R.id.etHobbies,"Musique");text(R.id.etLanguages,"fr, ar");text(R.id.etBiography,"Profil temporaire de recette Android")
            click(R.id.btnNext);waitFor(R.id.registerStep4Fragment)
            text(R.id.etMobile,"20123456");text(R.id.etAvailability,"En soirée");click(R.id.btnNext)
            waitFor(R.id.registerStep5Fragment)
            val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"fastcoloc-recipe-${System.nanoTime()}.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            })!!
            try {
                context.contentResolver.openOutputStream(uri)!!.use { Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,it) }
                onMain { vm(it).upload(uri,"Photo temporaire") }
                await("private photo upload") { vm(it).details.value?.photos?.size==1 && vm(it).busy.value!=true }
                onMain { activity -> vm(activity).caption(vm(activity).details.value!!.photos.first().id,"Description mise à jour") }
                await("photo caption") { vm(it).details.value?.photos?.firstOrNull()?.caption=="Description mise à jour" && vm(it).busy.value!=true }
                onMain { activity -> vm(activity).deletePhoto(vm(activity).details.value!!.photos.first().id) }
                await("photo deletion") { vm(it).details.value?.photos?.isEmpty()==true && vm(it).busy.value!=true }
                onMain { vm(it).upload(uri,"Photo temporaire finale") }
                await("replacement private photo") { vm(it).details.value?.photos?.size==1 && vm(it).busy.value!=true }
            } finally { context.contentResolver.delete(uri,null,null) }
            click(R.id.btnNext);waitFor(R.id.profileFragment)
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.getExternalFilesDir(null),"sprint1-profile.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
            val web=bridge("/web-check")
            onMain { assertEquals(web.get("id").asString,vm(it).details.value!!.profile.id) }
            click(R.id.btnRefresh)
            await("web change visible on Android") { vm(it).details.value?.profile?.lastName=="DepuisWeb" && vm(it).busy.value!=true }
            // Force a real rotation without printing either token.
            val before=container.sessions.session()!!
            container.sessions.save(before.copy(expiresAt=1))
            container.repository.authorized { session() }
            assertTrue("Refresh token did not rotate",before.refreshToken!=container.sessions.session()!!.refreshToken)
            scenario.recreate();waitFor(R.id.profileFragment)
            click(R.id.btnLogout);waitFor(R.id.loginFragment)
            assertNull(container.sessions.session())
            text(R.id.etLoginEmail,fixture.get("webEmail").asString);text(R.id.etLoginPassword,password);click(R.id.btnLogin)
            waitFor(R.id.homeFragment);click(R.id.btnProfile);waitFor(R.id.profileFragment)
            click(R.id.btnLogout);waitFor(R.id.loginFragment)
            // Actual signup from Android; the harness confirms only this owned fixture.
            click(R.id.btnOpenRegister);waitFor(R.id.registerStep1Fragment)
            text(R.id.etFirstName,"Mobile");text(R.id.etLastName,"Fixture");text(R.id.etAge,"24")
            text(R.id.etEmail,fixture.get("email").asString);text(R.id.etPassword,password);text(R.id.etConfirmPassword,password);click(R.id.btnNext)
            waitFor(R.id.verifyFragment)
            val confirmed=bridge("/confirm-mobile")
            onMain { vm(it).callback(android.net.Uri.parse(confirmed.get("callback").asString)) }
            waitFor(R.id.registerStep2Fragment)
            val mobile=bridge("/mobile-check")
            onMain { assertEquals(mobile.get("id").asString,vm(it).details.value!!.profile.id) }
            val recovery=bridge("/recovery-link")
            val returnUri=android.net.Uri.Builder().scheme("fastcoloc").authority("auth").path("/callback")
                .appendQueryParameter("token_hash",recovery.get("tokenHash").asString).appendQueryParameter("type","recovery").build()
            onMain { vm(it).callback(returnUri) };waitFor(R.id.resetFragment)
            text(R.id.etPassword,fixture.get("newPassword").asString);text(R.id.etConfirm,fixture.get("newPassword").asString);click(R.id.btnSave)
            waitFor(R.id.loginFragment);bridge("/reset-check")
            try { container.repository.api.login(com.iset.fastcolloc.data.remote.LoginRequest(fixture.get("email").asString,password));fail("Old password accepted") }
            catch(e: retrofit2.HttpException) { assertEquals(401,e.code()) }
            container.repository.logout()
        }
        Unit
    }
}
