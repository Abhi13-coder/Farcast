package com.farcast

import android.app.Application
import androidx.room.Room
import com.farcast.agent.darkalgorithm.features.MomentFinderService
import com.farcast.agent.darkalgorithm.features.SecureKeyVault
import com.farcast.agent.darkalgorithm.plugins.*
import java.io.File

/**
 * No DI framework (Hilt/Dagger) — a plain lazy-singleton container. Given
 * this app's actual size, Hilt's annotation-processing overhead buys less
 * than it costs; add it later if the object graph outgrows this file, not
 * preemptively.
 */
class FarcastApplication : Application() {

    val pluginDatabase: PluginDatabase by lazy {
        Room.databaseBuilder(this, PluginDatabase::class.java, "farcast_plugins.db").build()
    }
    val pluginRegistry: PluginRegistry by lazy { PluginRegistry(pluginDatabase) }
    val pluginDownloader: PluginDownloader by lazy { PluginDownloader(this) }
    val pluginInstaller: PluginInstaller by lazy { PluginInstaller(this, pluginDownloader, pluginRegistry) }

    val secureKeyVault: SecureKeyVault by lazy { SecureKeyVault(this) }
    val momentFinder: MomentFinderService by lazy { MomentFinderService(this) }

    val imageCacheDir: File by lazy { File(filesDir, "image_cache").also { it.mkdirs() } }

    override fun onCreate() {
        super.onCreate()
        // YOLO model loading is deliberately NOT done here — Section 6 says
        // avoid permanently loaded AI models; MomentFinderService.loadModel()
        // gets called lazily from whichever screen first needs detection,
        // and release() when that work is done, not held for the app's
        // whole lifetime.
    }
}
