package com.watermelon.app

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.watermelon.mediatools.job.MediaJobManager
import com.watermelon.mediatools.output.OutputFileStore
import com.watermelon.mediatools.service.MediaJobService
import com.watermelon.storage.prefs.FolderVisibilityStoreImpl

/**
 * Composition root. This app has no DI framework (confirmed by audit during media-tools
 * development), so this is a plain singleton-holder, matching the app's existing style
 * (e.g. MainActivity constructing FolderVisibilityStoreImpl/repositories directly) rather
 * than introducing Hilt/Koin/etc. for one new module.
 *
 * MediaJobManager needs to be reachable from both MainActivity (screens/dialogs) and
 * MediaJobService (notification/cancel) without either owning the other's lifecycle --
 * an Application-scoped singleton is the simplest correct answer.
 */
@UnstableApi
class WatermelonApplication : Application() {

    lateinit var settingsStore: FolderVisibilityStoreImpl
        private set

    lateinit var outputFileStore: OutputFileStore
        private set

    lateinit var mediaJobManager: MediaJobManager
        private set

    override fun onCreate() {
        super.onCreate()

        settingsStore = FolderVisibilityStoreImpl(applicationContext)

        outputFileStore = OutputFileStore(
            context = applicationContext,
            // These match SettingsPersistence exactly, so an export uses the destination the
            // user just selected in Settings rather than a stale duplicate preference store.
            mp3RelativePath = {
                getSharedPreferences("watermelon_prefs", MODE_PRIVATE)
                    .getString("mt_mp3_output_path", "Music/Watermelon") ?: "Music/Watermelon"
            },
            compressedRelativePath = {
                getSharedPreferences("watermelon_prefs", MODE_PRIVATE)
                    .getString("mt_compressed_output_path", "Movies/Watermelon/compressed")
                    ?: "Movies/Watermelon/compressed"
            },
            trimmedRelativePath = {
                getSharedPreferences("watermelon_prefs", MODE_PRIVATE)
                    .getString("mt_trimmed_output_path", "Movies/Watermelon/trimmed")
                    ?: "Movies/Watermelon/trimmed"
            },
        )

        mediaJobManager = MediaJobManager(outputFileStore, appContext = applicationContext)

        // MediaJobService reads this static provider on its own onStartCommand (see that
        // class's doc for why it's a stopgap, not a real DI solution).
        MediaJobService.jobManagerProvider = { mediaJobManager }
    }
}
