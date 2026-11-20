package com.dontstop

import android.app.Application
import com.dontstop.bridge.DontStopPackage
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {
    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(
            context = applicationContext,
            packageList = PackageList(this).packages.apply { add(DontStopPackage()) }
        )
    }

    override fun onCreate() {
        super.onCreate()
        loadReactNative(this)
    }
}
