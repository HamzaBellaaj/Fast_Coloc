package com.iset.fastcolloc.ui.auth

import android.content.Intent
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
    override fun onCreate(state: Bundle?) {
        installSplashScreen()
        super.onCreate(state)
        binding = ActivityAuthBinding.inflate(layoutInflater); setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val nav = (supportFragmentManager.findFragmentById(R.id.authNavHost) as NavHostFragment).navController
        binding.toolbar.title = getString(R.string.fc_fastcoloc)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { if (!nav.navigateUp()) onBackPressedDispatcher.onBackPressed() }
        nav.addOnDestinationChangedListener { _, destination, _ ->
            binding.toolbar.isVisible = destination.id != R.id.loginFragment
            binding.toolbar.title = destination.label
        }
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
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.data?.let { vm.callback(it) }; intent.data = null; setIntent(intent) }
}
