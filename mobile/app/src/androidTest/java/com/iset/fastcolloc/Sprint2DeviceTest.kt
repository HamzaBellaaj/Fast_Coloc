package com.iset.fastcolloc

import android.os.Bundle
import android.widget.EditText
import android.widget.Spinner
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.iset.fastcolloc.data.*
import com.iset.fastcolloc.data.local.*
import com.iset.fastcolloc.data.remote.*
import com.iset.fastcolloc.ui.auth.AuthActivity
import com.iset.fastcolloc.viewmodel.*
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Sprint2DeviceTest {
    private fun bridge(path: String): com.google.gson.JsonObject {
        val c = URL("http://127.0.0.1:8124$path").openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 120000
        try {
            check(c.responseCode == 200) { "Fixture bridge failed at $path" }
            return JsonParser.parseString(c.inputStream.bufferedReader().use { it.readText() })
                .asJsonObject
        } finally {
            c.disconnect()
        }
    }

    @Test
    fun realWebListingNativeCrudFavoritesCacheAndOwnership() = runBlocking {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("fastcolocLive") == "true"
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = AppContainer.get(context)
        app.repository.clear()
        val fixture = bridge("/fixture")
        ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
            fun onMain(action: (AuthActivity) -> Unit) {
                instrumentation.runOnMainSync {
                    val a =
                        androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
                            .getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                            .filterIsInstance<AuthActivity>()
                            .first()
                    action(a)
                }
            }
            fun auth(a: AuthActivity) = ViewModelProvider(a)[Sprint1ViewModel::class.java]
            fun vm(a: AuthActivity) =
                ViewModelProvider(a, ListingViewModelFactory(a.application))[
                    ListingViewModel::class.java]
            fun nav(a: AuthActivity) =
                (a.supportFragmentManager.findFragmentById(R.id.authNavHost) as NavHostFragment)
                    .navController
            fun await(label: String, predicate: (AuthActivity) -> Boolean) {
                val end = System.currentTimeMillis() + 60000
                while (System.currentTimeMillis() < end) {
                    var yes = false
                    onMain { yes = predicate(it) }
                    if (yes) return
                    Thread.sleep(100)
                }
                var state = ""
                onMain {
                    state =
                        "destination=${nav(it).currentDestination?.id}, busy=${vm(it).busy.value}, feedback=${auth(it).message.value}"
                }
                throw AssertionError("Timed out: $label; $state")
            }
            fun waitFor(id: Int) =
                await("destination $id") {
                    nav(it).currentDestination?.id == id && auth(it).busy.value != true
                }
            fun text(id: Int, value: String) {
                onMain { it.findViewById<EditText>(id).setText(value) }
            }
            fun click(id: Int) {
                onMain { it.findViewById<android.view.View>(id).performClick() }
            }
            waitFor(R.id.loginFragment)
            text(R.id.etLoginEmail, fixture["readerEmail"].asString)
            text(R.id.etLoginPassword, fixture["readerPassword"].asString)
            click(R.id.btnLogin)
            waitFor(R.id.homeFragment)
            click(R.id.btnListings)
            waitFor(R.id.listingCatalogFragment)
            onMain {
                vm(it)
                    .load(
                        query = mapOf("q" to fixture["webTitle"].asString, "maxRent" to "400"),
                        page = 0,
                    )
            }
            await("Web listing via Retrofit and Room") {
                vm(it).page.value?.items?.singleOrNull()?.id == fixture["webId"].asString &&
                    vm(it).busy.value != true
            }
            onMain {
                assertTrue(
                    it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvListings)
                        .layoutManager is androidx.recyclerview.widget.LinearLayoutManager
                )
            }
            onMain {
                nav(it)
                    .navigate(
                        R.id.listingDetailFragment,
                        Bundle().apply { putString("listingId", fixture["webId"].asString) },
                    )
            }
            await("Native detail and real photo") {
                vm(it).selected.value?.id == fixture["webId"].asString &&
                    vm(it).selected.value?.photos?.size == 1 &&
                    vm(it).busy.value != true
            }
            await("Actual Coil image from private Storage") { a ->
                val rv = a.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvPhotos)
                rv.findViewHolderForAdapterPosition(0)
                    ?.itemView
                    ?.findViewById<android.widget.ImageView>(R.id.ivPhoto)
                    ?.drawable is android.graphics.drawable.BitmapDrawable
            }
            onMain {
                assertFalse(it.findViewById<android.view.View>(R.id.btnEditListing).isShown)
                assertEquals(350.125, vm(it).selected.value!!.rent, 0.0)
            }
            click(R.id.btnFavorite)
            await("Favorite saved") {
                vm(it).selected.value?.favorite == true && vm(it).busy.value != true
            }
            bridge("/check-favorite")
            click(R.id.btnFavorite)
            await("Favorite removed") {
                vm(it).selected.value?.favorite == false && vm(it).busy.value != true
            }
            bridge("/check-unfavorite")
            // RLS-backed rejection through the exact authenticated Retrofit client used by the UI.
            try {
                app.repository.authorized { deleteListing(fixture["webId"].asString) }
                fail("Cross-author delete was accepted")
            } catch (e: retrofit2.HttpException) {
                assertEquals(404, e.code())
            }
            val owner = app.repository.accountId()
            val cached =
                ListingCache.get(context).listings().detail(owner, fixture["webId"].asString)
            assertNotNull(cached)
            // A refused loopback connection produces a real IOException; Room is fallback, never
            // authority.
            val offline =
                ListingRepository(
                    AuthRepository(
                        ApiClient.create(app.sessions, "http://127.0.0.1:1/api/v1/"),
                        app.sessions,
                        ProfileCache.get(context).profiles(),
                    ),
                    ListingCache.get(context).listings(),
                )
            val fallback = offline.detail(fixture["webId"].asString)
            assertTrue(fallback.second)
            assertTrue(fallback.first.photos.all { it.url == null })
            val before =
                ListingCache.get(context).listings().detail(owner, fixture["webId"].asString)!!.body
            try {
                offline.save(
                    null,
                    ListingDraft.parse(
                        mapOf(
                            "title" to "Should never be local",
                            "description" to "No independent local publication is allowed here.",
                            "propertyType" to "Appartement",
                            "street" to "Test",
                            "latitude" to "36.8",
                            "longitude" to "10.18",
                            "rent" to "1",
                            "capacity" to "1",
                            "publicationDays" to "1",
                            "availableFrom" to "2026-01-01",
                        ),
                        "OFFER",
                        fixture["localityId"].asLong,
                        null,
                    ),
                )
                fail("Offline create succeeded")
            } catch (_: java.io.IOException) {}
            assertTrue(
                before ==
                    ListingCache.get(context)
                        .listings()
                        .detail(owner, fixture["webId"].asString)!!
                        .body
            )
            onMain { auth(it).logout() }
            waitFor(R.id.loginFragment)
            assertNull(
                ListingCache.get(context).listings().detail(owner, fixture["webId"].asString)
            )
            text(R.id.etLoginEmail, fixture["ownerEmail"].asString)
            text(R.id.etLoginPassword, fixture["ownerPassword"].asString)
            click(R.id.btnLogin)
            waitFor(R.id.homeFragment)
            click(R.id.btnListings)
            waitFor(R.id.listingCatalogFragment)
            await("catalog ready") { vm(it).busy.value != true }
            click(R.id.btnNewListing)
            waitFor(R.id.listingEditorFragment)
            await("editor localities") {
                vm(it).areas.value?.isNotEmpty() == true && vm(it).busy.value != true
            }
            text(R.id.etListingTitle, fixture["nativeTitle"].asString)
            text(
                R.id.etListingDescription,
                "Annonce Android technique temporaire. Créée via les vrais écrans XML et supprimée après la recette.",
            )
            text(R.id.etPropertyType, "Appartement")
            click(R.id.btnNextStep)
            onMain { a ->
                val index =
                    vm(a).areas.value!!.indexOfFirst { it.id == fixture["localityId"].asLong }
                a.findViewById<Spinner>(R.id.spListingLocality).setSelection(index + 1)
            }
            text(R.id.etStreet, "Adresse de recette temporaire Android")
            text(R.id.etLatitude, "36.8")
            text(R.id.etLongitude, "10.18")
            click(R.id.btnNextStep)
            text(R.id.etRent, "690.5")
            text(R.id.etCapacity, "2")
            text(R.id.etAvailableFrom, "2026-01-01")
            text(R.id.etEquipment, "WiFi, Cuisine")
            click(R.id.btnSaveListing)
            await("Android draft created") {
                vm(it).selected.value?.title == fixture["nativeTitle"].asString &&
                    vm(it).busy.value != true
            }
            val image =
                context.contentResolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    android.content.ContentValues().apply {
                        put(
                            android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                            "fastcoloc-s2-${System.nanoTime()}.png",
                        )
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    },
                )!!
            try {
                context.contentResolver.openOutputStream(image)!!.use {
                    android.graphics.Bitmap.createBitmap(
                            4,
                            4,
                            android.graphics.Bitmap.Config.ARGB_8888,
                        )
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                onMain { vm(it).upload(image, "Image technique Android temporaire") }
                await("Android photo uploaded") {
                    vm(it).selected.value?.photos?.size == 1 && vm(it).busy.value != true
                }
            } finally {
                context.contentResolver.delete(image, null, null)
            }
            var nativeId = ""
            onMain {
                nativeId = vm(it).selected.value!!.id
                vm(it).publish()
            }
            waitFor(R.id.listingDetailFragment)
            await("published Android listing") {
                vm(it).selected.value?.id == nativeId &&
                    vm(it).selected.value?.status == "PUBLISHED" &&
                    vm(it).busy.value != true
            }
            bridge("/check-native")
            click(R.id.btnRefreshListing)
            await("Web edit visible on Android") {
                vm(it).selected.value?.rent == 700.5 && vm(it).busy.value != true
            }
            click(R.id.btnEditListing)
            waitFor(R.id.listingEditorFragment)
            await("editor loaded") {
                vm(it).selected.value?.id == nativeId && vm(it).busy.value != true
            }
            // The shared ViewModel retains form state through Activity recreation (chap.3/5).
            scenario.recreate()
            waitFor(R.id.listingEditorFragment)
            await("editor restored") {
                vm(it).selected.value?.id == nativeId && vm(it).busy.value != true
            }
            onMain {
                assertEquals(
                    fixture["nativeTitle"].asString,
                    it.findViewById<EditText>(R.id.etListingTitle).text.toString(),
                )
            }
            click(R.id.btnNextStep)
            click(R.id.btnNextStep)
            text(R.id.etRent, "710.5")
            click(R.id.btnSaveListing)
            await("Android update saved") {
                vm(it).selected.value?.rent == 710.5 && vm(it).busy.value != true
            }
            bridge("/check-native-update")
            onMain {
                vm(it).detail(nativeId)
                nav(it)
                    .navigate(
                        R.id.listingDetailFragment,
                        Bundle().apply { putString("listingId", nativeId) },
                    )
            }
            await("detail before deletion") {
                vm(it).selected.value?.id == nativeId && vm(it).busy.value != true
            }
            onMain { vm(it).delete() }
            waitFor(R.id.listingCatalogFragment)
            bridge("/check-delete")
        }
        app.repository.clear()
    }
}
