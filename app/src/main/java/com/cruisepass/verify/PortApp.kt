package com.cruisepass.verify

import android.app.Application
import android.content.Context
import com.cruisepass.verify.data.CruiseRepository
import com.cruisepass.verify.ui.AppLanguage

class PortApp : Application() {
    lateinit var repository: CruiseRepository
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLanguage.apply(this)
        repository = CruiseRepository(this)
    }
}
