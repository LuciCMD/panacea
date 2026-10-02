package com.clementine.panacea

import android.app.Application
import android.content.Context
import android.util.Log
import com.clementine.panacea.data.MedicationRepository
import com.clementine.panacea.data.PhotoStore
import com.clementine.panacea.data.Settings
import com.clementine.panacea.data.backup.Backups
import com.clementine.panacea.data.db.PanaceaDatabase
import com.clementine.panacea.data.legacy.ImportOutcome
import com.clementine.panacea.data.legacy.Legacy34Importer
import com.clementine.panacea.sound.SoundLibrary
import com.clementine.panacea.reminder.Reminders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PanaceaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.appScope.launch {
            when (val outcome = container.legacyImporter.importIfNeeded()) {
                is ImportOutcome.Imported -> {
                    Log.i(TAG, "Imported 3.4 data: $outcome")
                    outcome.problems.forEach { Log.w(TAG, "Import: $it") }
                }
                ImportOutcome.AlreadyDone -> Unit
            }
            container.started.complete(Unit)
            container.photos.sweep(container.medications.photoFiles())
            // Catches up on anything missed while the app was stopped.
            container.reminders.resync()
        }
        container.appScope.launch {
            container.started.await()
            container.reminders.watch()
        }
        container.appScope.launch {
            container.started.await()
            container.reminders.watchLearned()
        }
    }

    private companion object {
        const val TAG = "Panacea"
    }
}

/** The app's long-lived objects, built once. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob())

    /** Done once the 3.4 import has run; nothing touches reminders before. */
    val started = CompletableDeferred<Unit>()
    val database: PanaceaDatabase = PanaceaDatabase.build(context)
    val legacyImporter = Legacy34Importer(context, database)
    val medications = MedicationRepository(database)
    val settings = Settings(context)
    val soundLibrary = SoundLibrary(context, settings)
    val photos = PhotoStore(context)
    val reminders = Reminders(context, database, medications)
    val backups = Backups(context, database, photos, settings, soundLibrary, reminders)
}
