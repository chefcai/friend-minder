package com.example.friendminder

import android.app.Application
import com.example.friendminder.utils.ServiceLocator

class FriendMinderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
