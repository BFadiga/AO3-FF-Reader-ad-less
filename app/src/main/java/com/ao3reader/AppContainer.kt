package com.ao3reader

import android.content.Context
import com.ao3reader.data.local.AppDatabase
import com.ao3reader.data.local.DownloadStore
import com.ao3reader.data.prefs.SettingsRepository
import com.ao3reader.data.remote.Ao3Client
import com.ao3reader.data.remote.ffn.FfnClient
import com.ao3reader.data.remote.web.HiddenBrowser
import com.ao3reader.data.remote.web.WebCookieJar
import com.ao3reader.data.repo.Ao3Repository
import com.ao3reader.data.repo.AccountRepository
import com.ao3reader.data.repo.FfnRepository
import com.ao3reader.data.repo.FilterRepository
import com.ao3reader.data.repo.LibraryRepository
import com.ao3reader.data.repo.WorksRepository
import com.ao3reader.work.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import com.ao3reader.data.model.FfnFilter

/** Hand-rolled dependency container; one per process. */
class AppContainer(context: Context) {
    private val db = AppDatabase.create(context)
    /** For work that should outlive the screen that started it (syncing, pushing follows). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val cookies = WebCookieJar()
    val browser = HiddenBrowser(context.applicationContext)
    val settings = SettingsRepository(context)
    val ao3 = Ao3Repository(Ao3Client(cookies = cookies))
    val ffn = FfnRepository(FfnClient(browser, cookies))
    val works = WorksRepository(ao3, ffn)
    val library = LibraryRepository(db, DownloadStore(context), works)
    val filters = FilterRepository(db)
    val accounts = AccountRepository(settings, works, library, cookies)
    val updateChecker = UpdateChecker(context, works, library, settings)

    /** A FanFiction.net search another screen asked the Search tab to run (e.g. a fandom tapped in Categories). */
    val ffnSearchRequest = MutableStateFlow<FfnFilter?>(null)

    /** The last FanFiction.net search run, so tag taps can add to it. */
    @Volatile
    var ffnLastFilter = FfnFilter()
}
