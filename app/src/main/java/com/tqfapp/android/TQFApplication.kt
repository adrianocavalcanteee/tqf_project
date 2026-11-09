package com.tqfapp.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class TQFApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
            try {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        "Erro fatal: ${throwable.localizedMessage ?: throwable.javaClass.simpleName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                Thread.sleep(3500)
            } catch (_: Exception) {}

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
