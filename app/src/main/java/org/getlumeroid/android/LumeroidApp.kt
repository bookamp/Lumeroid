package org.getlumeroid.android

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import org.getlumeroid.android.core.data.LumeroidDatabase

class LumeroidApp : Application() {

    val database: LumeroidDatabase by lazy {
        LumeroidDatabase.getInstance(this)
    }

    val isRunningOnTv: Boolean by lazy {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        org.getlumeroid.android.core.settings.ContentFilterCache.init(this)
        org.getlumeroid.android.core.data.BrowseCache.init(this)
    }

    companion object {
        lateinit var instance: LumeroidApp
            private set
    }
}
