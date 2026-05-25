package com.github.donglua.layoutx2c.demo

import android.app.Application
import android.util.Log
import com.github.donglua.layoutx2c.runtime.LayoutX2CRegistry

class DemoApp : Application() {

    companion object {
        private const val TAG = "LayoutX2C"
    }

    override fun onCreate() {
        super.onCreate()
        registerGeneratedLayouts()
    }

    private fun registerGeneratedLayouts() {
        runCatching {
            LayoutX2CRegistry.initialize(this)
        }.onSuccess { registered ->
            if (registered) {
                Log.d(TAG, "Generated layouts registered successfully")
            } else {
                Log.w(TAG, "LayoutX2CGenerated not found — run KSP build first")
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to register generated layouts", e)
        }
    }
}
