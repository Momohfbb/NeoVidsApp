package com.moustayt.cloudvideo.generator.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moustayt.cloudvideo.generator.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    private val store = LibraryStore(app)

    private val _items = MutableStateFlow(store.load().sortedByDescending { it.createdAt })
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    private val _theme = MutableStateFlow(settings.theme)
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _backend = MutableStateFlow(settings.backendUrl.ifBlank { BuildConfig.BACKEND_URL })
    val backend: StateFlow<String> = _backend.asStateFlow()

    private val demo = DemoService()
    private var remote: RemoteService? = null
    private var remoteUrl = ""
    private var remoteOwner = ""

    init {
        _items.value.filter { it.status == Status.QUEUED || it.status == Status.RUNNING && !it.local }
            .forEach { track(it.id) }
    }

    private fun remoteService(): RemoteService {
        val url = _backend.value
        val owner = settings.ownerKey
        if (remote == null || remoteUrl != url || remoteOwner != owner) {
            remote = RemoteService(url, owner, settings.deviceId)
            remoteUrl = url
            remoteOwner = owner
        }
        return remote!!
    }

    private fun current(): GenerationService = if (_backend.value.isBlank()) demo else remoteService()
    private fun serviceFor(item: LibraryItem): GenerationService = if (item.demo) demo else remoteService()

    fun isDemo() = _backend.value.isBlank()

    fun setTheme(v: String) { settings.theme = v; _theme.value = v }

    fun setBackend(url: String) {
        settings.backendUrl = url
        _backend.value = url.trim().ifBlank { BuildConfig.BACKEND_URL }
    }

    /** [onDone] gets null on success, or the failure. Runs on the main thread. */
    fun submit(req: GenRequest, onDone: (Throwable?) -> Unit) {
        viewModelScope.launch {
            val svc = current()
            try {
                val id = svc.submit(req)
                val item = LibraryItem(
                    id = id, kind = req.kind, prompt = req.prompt, style = req.style, aspect = req.aspect,
                    createdAt = System.currentTimeMillis(), status = Status.QUEUED, demo = svc.isDemo,
                )
                _items.value = listOf(item) + _items.value
                persist()
                track(id)
                onDone(null)
            } catch (e: Exception) {
                onDone(e)
            }
        }
    }

    /** Asks the backend who we are and which providers it has configured. */
    suspend fun testConnection(): Result<ServerStatus> {
        if (_backend.value.isBlank()) return Result.failure(Exception("no server address"))
        return runCatching { remoteService().status() }
    }

    fun addLocal(item: LibraryItem) {
        _items.value = listOf(item) + _items.value
        persist()
    }

    fun delete(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
        persist()
    }

    fun item(id: String) = _items.value.firstOrNull { it.id == id }

    private fun mutate(id: String, f: (LibraryItem) -> LibraryItem) {
        _items.value = _items.value.map { if (it.id == id) f(it) else it }
        persist()
    }

    private fun persist() = store.save(_items.value)

    private fun track(id: String) {
        viewModelScope.launch {
            var failures = 0
            while (true) {
                val item = _items.value.firstOrNull { it.id == id } ?: return@launch
                try {
                    val dto = serviceFor(item).poll(id)
                    failures = 0
                    mutate(id) { it.copy(status = dto.status, progress = dto.progress, resultUrl = dto.resultUrl, error = dto.error) }
                    if (dto.status == Status.DONE || dto.status == Status.FAILED) return@launch
                } catch (e: Exception) {
                    failures++
                    if (failures >= 5) {
                        mutate(id) { it.copy(status = Status.FAILED, error = e.message) }
                        return@launch
                    }
                }
                delay(2500)
            }
        }
    }
}
