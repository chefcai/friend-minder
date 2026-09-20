package com.example.friendminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.databinding.ActivityMainBinding
import com.example.friendminder.ui.dashboard.DashboardFragment
import com.example.friendminder.ui.friendlist.FriendListFragment
import com.example.friendminder.ui.settings.SettingsFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Navigation shell (Designer spec §2): decides first-launch (onboarding) vs.
 * returning-user (Dashboard, GH #56) start destination, then hosts
 * FriendListFragment / SettingsFragment / DashboardFragment via plain
 * FragmentTransactions. No Navigation-Component dependency — consistent
 * with the project's minimal, easy-to-audit dependency tree (see
 * ServiceLocator).
 *
 * FRM-78: also handles [ACTION_OPEN_SETTINGS], the tap action for the test
 * notification (see NotificationHelper.postTestNotification) - the only
 * external entry point into this activity today besides plain launch.
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
            if (intent?.action == ACTION_OPEN_SETTINGS) {
                openSettings()
            } else {
                lifecycleScope.launch {
                    val hasFriends = ServiceLocator.friendListRepository.getFriendList().isNotEmpty()
                    supportFragmentManager.commit {
                        replace(
                            R.id.nav_host_container,
                            if (hasFriends) DashboardFragment.newInstance() else FriendListFragment.newInstance(isOnboarding = true)
                        )
                    }
                }
            }
        }
    }

    // FRM-78: reached when the activity is already running and the test
    // notification's tap PendingIntent (FLAG_ACTIVITY_SINGLE_TOP) is
    // delivered to the existing instance instead of creating a new one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_OPEN_SETTINGS) {
            openSettings()
        }
    }

    private fun openSettings() {
        supportFragmentManager.commit {
            replace(R.id.nav_host_container, SettingsFragment.newInstance(isOnboarding = false))
        }
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.example.friendminder.action.OPEN_SETTINGS"
    }
}
