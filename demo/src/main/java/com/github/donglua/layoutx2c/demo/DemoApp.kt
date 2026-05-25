package com.github.donglua.layoutx2c.demo

import android.app.Application
import android.util.Log

class DemoApp : Application() {

    companion object {
        private const val TAG = "LayoutX2C"
    }

    override fun onCreate() {
        super.onCreate()
        registerGeneratedLayouts()
    }

    private fun registerGeneratedLayouts() {
        try {
            val clazz = Class.forName("com.github.donglua.layoutx2c.demo.generated.LayoutX2CGenerated")
            val instance = clazz.getField("INSTANCE").get(null)
            clazz.getMethod("register").invoke(instance)
            Log.d(TAG, "Generated layouts registered successfully")
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "LayoutX2CGenerated not found — run KSP build first")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register generated layouts", e)
        }
    }
}
