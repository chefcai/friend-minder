package com.example.friendminder

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.databinding.ActivityMainBinding
import com.example.friendminder.ui.friendlist.FriendListFragment
import com.example.friendminder.ui.home.HomeFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Navigation shell (Designer spec §2): decides first-launch (onboarding) vs.
 * returning-user (Home) start destination, then hosts FriendListFragment /
 * SettingsFragment / HomeFragment via plain FragmentTransactions. No
 * Navigation-Component dependency — consistent with the project's minimal,
 * easy-to-audit dependency tree (see ServiceLocator).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // FRM-65: must run before super.onCreate() per the SplashScreen API contract.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Single global back handler: pop the fragment back stack while
        // there's something on it (Designer spec §2.1/§2.2 "edit" mode back
        // arrows behave the same as system back); otherwise fall through to
        // the platform default, which finishes the activity.
        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val hasFriends = ServiceLocator.friendListRepository.getFriendList().isNotEmpty()
                supportFragmentManager.commit {
                    replace(
                        R.id.nav_host_container,
                        if (hasFriends) HomeFragment() else FriendListFragment.newInstance(isOnboarding = true)
                    )
                }
            }
        }
    }
}
