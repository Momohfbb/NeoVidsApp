package com.moustayt.cloudvideo.generator.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    private val dao = AppDb.get(app).items()

    val items: StateFlow<List<LibraryItem>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _theme = MutableStateFlow(settings.theme)
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _backend = MutableStateFlow(Backends.backendUrl(app))
    val backend: StateFlow<String> = _backend.asStateFlow()

    init {
        viewModelScope.launch {
            importLegacyLibrary()
            // Resume tracking of anything that was still running when the app was closed.
            dao.active().forEach { TrackJobWorker.enqueue(app, it.id) }
        }
    }

    /** One-time import of the pre-database library.json. */
    private suspend fun importLegacyLibrary() = withContext(Dispatchers.IO) {
        val legacy = File(app.filesDir, "library.json")
        if (legacy.exists() && dao.count() == 0) {
            dao.upsertAll(LibraryStore(app).load())
        }
        if (legacy.exists()) legacy.renameTo(File(app.filesDir, "library.json.migrated"))
    }

    fun setTheme(v: String) { settings.theme = v; _theme.value = v }

    fun setBackend(url: String) {
        settings.backendUrl = url
        _backend.value = Backends.backendUrl(app)
    }

    /** [onDone] gets null on success, or the failure. Runs on the main thread. */
    fun submit(req: GenRequest, onDone: (Throwable?) -> Unit) {
        viewModelScope.launch {
            try {
                val svc = Backends.current(app)
                val id = svc.submit(req)
                dao.upsert(
                    LibraryItem(
                        id = id, kind = req.kind, prompt = req.prompt, style = req.style, aspect = req.aspect,
                        createdAt = System.currentTimeMillis(), status = Status.QUEUED, demo = svc.isDemo,
                    ),
                )
                TrackJobWorker.enqueue(app, id)
                onDone(null)
            } catch (e: Exception) {
                onDone(e)
            }
        }
    }

    /** Asks the backend who we are and which providers it has configured. */
    suspend fun testConnection(): Result<ServerStatus> {
        if (Backends.backendUrl(app).isBlank()) return Result.failure(Exception("no server address"))
        return runCatching { Backends.remote(app).status() }
    }

    fun addLocal(item: LibraryItem) {
        viewModelScope.launch { dao.upsert(item) }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.delete(id) }
    }
}
