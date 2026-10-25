package org.getlume.android

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import org.getlume.android.core.data.LumeDatabase

class LumeApp : Application() {

    val database: LumeDatabase by lazy {
        LumeDatabase.getInstance(this)
    }

    val isRunningOnTv: Boolean by lazy {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        org.getlume.android.core.settings.ContentFilterCache.init(this)
        org.getlume.android.core.data.BrowseCache.init(this)
    }

    companion object {
        lateinit var instance: LumeApp
            private set
    }
}
