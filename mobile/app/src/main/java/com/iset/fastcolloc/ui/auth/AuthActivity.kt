package com.iset.fastcolloc.ui.auth

import android.content.Intent
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.ui.navigateUp
import androidx.appcompat.app.AlertDialog
import coil.load
import com.iset.fastcolloc.databinding.DrawerHeaderBinding
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.isVisible
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.iset.fastcolloc.R
import com.iset.fastcolloc.databinding.ActivityAuthBinding
import com.iset.fastcolloc.viewmodel.Sprint1ViewModel

class AuthActivity : AppCompatActivity() {
    private val vm: Sprint1ViewModel by viewModels()
    private lateinit var binding: ActivityAuthBinding
    override fun attachBaseContext(newBase: android.content.Context) {
        java.util.Locale.setDefault(java.util.Locale.FRANCE)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(java.util.Locale.FRENCH)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
    override fun onCreate(state: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(state)
        binding = ActivityAuthBinding.inflate(layoutInflater); setContentView(binding.root)
        splash.setOnExitAnimationListener { provider ->
            if (!com.iset.fastcolloc.ui.motion.Motion.enabled(this)) provider.remove()
            else provider.view.animate().alpha(0f).scaleX(1.04f).scaleY(1.04f).setDuration(180).withEndAction { provider.remove() }.start()
        }
        setSupportActionBar(binding.toolbar)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        androidx.core.view.ViewCompat.requestApplyInsets(binding.root)
        val nav = (supportFragmentManager.findFragmentById(R.id.authNavHost) as NavHostFragment).navController
        val mainDestinations = setOf(R.id.homeFragment, R.id.recommendationsFragment, R.id.listingCatalogFragment, R.id.favoritesFragment, R.id.mineListingsFragment, R.id.profileFragment)
        val appBar = AppBarConfiguration(mainDestinations, binding.drawerLayout)
        binding.toolbar.setupWithNavController(nav, appBar)
        binding.toolbar.setNavigationOnClickListener { if (!nav.navigateUp(appBar)) onBackPressedDispatcher.onBackPressed() }
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (nav.currentDestination?.id != item.itemId) nav.navigate(item.itemId, null,
                NavOptions.Builder().setLaunchSingleTop(true).setPopUpTo(R.id.homeFragment, false, true).setRestoreState(true).build())
            true
        }
        binding.bottomNav.setOnItemReselectedListener { }
        val header = DrawerHeaderBinding.bind(binding.navigationView.getHeaderView(0))
        vm.details.observe(this) { details ->
            header.drawerName.text = details?.profile?.let { listOfNotNull(it.firstName, it.lastName).joinToString(" ") }.orEmpty()
            header.drawerSubtitle.text = if (details?.profile?.completedAt != null) "Ton espace FastColoc" else "Ton profil est à compléter"
        }
        vm.photoUrls.observe(this) { urls ->
            header.drawerAvatar.load(urls.values.firstOrNull()) { placeholder(R.drawable.ic_fastcoloc_symbol); error(R.drawable.ic_fastcoloc_symbol); memoryCachePolicy(coil.request.CachePolicy.DISABLED); diskCachePolicy(coil.request.CachePolicy.DISABLED) }
        }
        binding.navigationView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            if (item.itemId == R.id.menu_logout) {
                AlertDialog.Builder(this).setTitle("Se déconnecter ?").setMessage("Tu pourras retrouver ton espace en te reconnectant.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Déconnexion") { _, _ -> vm.logout() }.show()
            } else if (nav.currentDestination?.id != item.itemId) {
                nav.navigate(item.itemId, null, NavOptions.Builder().setLaunchSingleTop(true)
                    .setPopUpTo(R.id.homeFragment, false, true).setRestoreState(item.itemId != R.id.listingEditorFragment).build())
            }
            true
        }
        nav.addOnDestinationChangedListener { _, destination, _ ->
            val signedInScreen = destination.id in mainDestinations || destination.id in setOf(R.id.listingDetailFragment, R.id.listingEditorFragment, R.id.publicationSuccessFragment)
            binding.drawerLayout.setDrawerLockMode(if (signedInScreen) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            val home = destination.id == R.id.homeFragment
            binding.toolbar.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.fc_cream))
            binding.toolbar.setTitleTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.fc_plum))
            binding.toolbar.navigationIcon?.setTint(androidx.core.content.ContextCompat.getColor(this, R.color.fc_violet))
            binding.toolbar.setTitleTextAppearance(this, R.style.TextAppearance_FastColoc_Section)
            title = if (home) "FastColoc" else destination.label
            binding.toolbar.isVisible = destination.id != R.id.loginFragment
            binding.bottomNav.isVisible = destination.id in setOf(R.id.homeFragment, R.id.recommendationsFragment, R.id.listingCatalogFragment, R.id.favoritesFragment, R.id.profileFragment, R.id.mineListingsFragment)
            binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
            binding.navigationView.menu.findItem(destination.id)?.let { binding.navigationView.setCheckedItem(it) }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.liquidPop.running) binding.liquidPop.cancel()
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) binding.drawerLayout.closeDrawer(GravityCompat.START)
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
        vm.busy.observe(this) {
            binding.progress.isVisible = it
            if(it) androidx.core.view.WindowCompat.getInsetsController(window, binding.root).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        vm.message.observe(this) { binding.tvMessage.text = it; binding.tvMessage.isVisible = !it.isNullOrBlank() }
        vm.event.observe(this) { event -> event.consume()?.let { route ->
            if (route.startsWith("browser:")) {
                val uri = android.net.Uri.parse(route.removePrefix("browser:"))
                if (uri.scheme == "https" && uri.host?.endsWith(".supabase.co") == true) CustomTabsIntent.Builder().build().launchUrl(this, uri)
                else vm.formError("Adresse OAuth refusée.")
            } else {
                val id = when(route) {
                    "login" -> R.id.loginFragment; "verify" -> R.id.verifyFragment; "reset" -> R.id.resetFragment
                    "home" -> R.id.homeFragment; "profile" -> R.id.profileFragment
                    "step1" -> R.id.registerStep1Fragment; "step2" -> R.id.registerStep2Fragment
                    "step3" -> R.id.registerStep3Fragment; "step4" -> R.id.registerStep4Fragment
                    else -> R.id.registerStep5Fragment
                }
                if (route == "login") { mainDestinations.forEach { nav.clearBackStack(it) }; coil.Coil.imageLoader(this).memoryCache?.clear() }
                val clear = route in listOf("login", "home", "reset") || route == "profile" && !vm.editing
                if (nav.currentDestination?.id != id) nav.navigate(id, null, NavOptions.Builder().setLaunchSingleTop(true)
                    .apply {
                        if(clear) setPopUpTo(nav.graph.id, false)
                        else if(route == "profile" && vm.editing) setPopUpTo(R.id.profileFragment, true)
                    }.build())
            }
        } }
        if (state == null || vm.details.value == null && !vm.recovery) {
            val uri = intent.data
            intent.data = null
            if (uri != null) vm.callback(uri) else vm.restore()
        }
    }
    fun navigateWithLiquidPop(source: android.view.View, destination: Int) {
        val nav = (supportFragmentManager.findFragmentById(R.id.authNavHost) as NavHostFragment).navController
        if (nav.currentDestination?.id != R.id.homeFragment || binding.liquidPop.running) return
        binding.liquidPop.play(source) {
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && nav.currentDestination?.id == R.id.homeFragment)
                nav.navigate(destination, null, NavOptions.Builder().setLaunchSingleTop(true).build())
        }
    }
    override fun onStop() { binding.liquidPop.cancel(); super.onStop() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.data?.let { vm.callback(it) }; intent.data = null; setIntent(intent) }
}
