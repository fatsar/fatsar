package com.fatsar.hermes

import android.app.Application
import com.fatsar.hermes.data.BotEngine
import com.fatsar.hermes.service.Notifications
import com.fatsar.hermes.core.store.FileStorage
import com.fatsar.hermes.core.store.Repository
import java.io.File

class HermesApplication : Application() {

    lateinit var engine: BotEngine
        private set

    override fun onCreate() {
        super.onCreate()
        val repository = Repository(FileStorage(File(filesDir, "hermes")))
        engine = BotEngine(applicationContext, repository)
        Notifications.createChannels(this)
    }

    companion object {
        fun engineOf(context: android.content.Context): BotEngine =
            (context.applicationContext as HermesApplication).engine
    }
}
