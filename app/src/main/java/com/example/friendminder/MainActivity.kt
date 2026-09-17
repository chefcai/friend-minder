package com.example.friendminder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.friendminder.databinding.ActivityMainBinding

/**
 * Navigation shell only — no business logic. Designer (FRM-4) will spec the
 * real screen flow (Friend List -> Settings -> Notification Preview); this
 * scaffold just proves the app launches without crashing and gives Designer
 * a concrete container (nav_host_container) to plan Fragments/Compose
 * destinations against.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO(FRM-12): host FriendListFragment / SettingsFragment here once
        // Designer's navigation spec and Publisher's Fragments land.
    }
}
