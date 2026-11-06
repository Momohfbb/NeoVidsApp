package com.moustayt.cloudvideo.generator.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale

/** A prompt handed to the Create screen from elsewhere (e.g. "Remix" in the detail screen). */
data class Draft(
    val kind: Kind,
    val prompt: String,
    val style: String,
    val aspect: String,
    val durationSec: Int,
    /** An image already online (e.g. an earlier creation) to animate or edit. */
    val sourceUrl: String? = null,
)

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    private val dao = AppDb.get(app).items()

    val items: StateFlow<List<LibraryItem>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _theme = MutableStateFlow(settings.theme)
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _backend = MutableStateFlow(Backends.backendUrl(app))
    val backend: StateFlow<String> = _backend.asStateFlow()

    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()

    private val _server = MutableStateFlow<ServerStatus?>(null)
    /** Last known server capabilities (AI assistant, providers); null until fetched. */
    val server: StateFlow<ServerStatus?> = _server.asStateFlow()

    init {
        viewModelScope.launch {
            importLegacyLibrary()
            // Resume tracking of anything that was still running when the app was closed.
            dao.active().forEach { TrackJobWorker.enqueue(app, it.id) }
        }
        refreshServer()
    }

    /** One-time import of the pre-database library.json. */
    private suspend fun importLegacyLibrary() = withContext(Dispatchers.IO) {
        val legacy = File(app.filesDir, "library.json")
        if (legacy.exists() && dao.count() == 0) {
            dao.upsertAll(LibraryStore(app).load())
        }
        if (legacy.exists()) legacy.renameTo(File(app.filesDir, "library.json.migrated"))
    }

    fun refreshServer() {
        viewModelScope.launch { _server.value = testConnection().getOrNull() }
    }

    fun setTheme(v: String) { settings.theme = v; _theme.value = v }

    fun setBackend(url: String) {
        settings.backendUrl = url
        _backend.value = Backends.backendUrl(app)
        refreshServer()
    }

    fun setDemoMode(on: Boolean) {
        settings.demoMode = on
        _backend.value = Backends.backendUrl(app)
        refreshServer()
    }

    fun remix(item: LibraryItem) {
        val kind = if (item.kind.needsImage) (if (item.kind.isVideo) Kind.TEXT_TO_VIDEO else Kind.TEXT_TO_IMAGE) else item.kind
        _draft.value = Draft(kind, item.prompt, item.style.ifBlank { "cinematic" }, item.aspect.ifBlank { "16:9" }, item.durationSec)
    }

    fun consumeDraft() { _draft.value = null }

    /** Opens Create with any prompt (e.g. from an Explore template). */
    fun useTemplate(kind: Kind, prompt: String, style: String, aspect: String) {
        _draft.value = Draft(kind, prompt, style, aspect, 5)
    }

    /** "Animate" a finished image: image-to-video from its URL. */
    fun animate(item: LibraryItem) {
        _draft.value = Draft(Kind.IMAGE_TO_VIDEO, "", item.style.ifBlank { "cinematic" }, item.aspect.ifBlank { "16:9" }, 5, item.resultUrl)
    }

    /** "Edit" a finished image with words. */
    fun editImage(item: LibraryItem) {
        _draft.value = Draft(Kind.IMAGE_EDIT, "", item.style, item.aspect, 5, item.resultUrl)
    }

    fun toggleFavorite(item: LibraryItem) {
        viewModelScope.launch { dao.setFavorite(item.id, !item.favorite) }
    }

    fun group(groupId: String) = dao.observeGroup(groupId)

    /** AI director: one idea -> title + scenes. */
    fun writeStory(idea: String, scenes: Int, style: String, onDone: (Result<StoryDto>) -> Unit) {
        viewModelScope.launch {
            val r = runCatching {
                if (Backends.backendUrl(app).isBlank()) throw ApiException("NO_PROVIDER", "demo mode")
                withTimeout(90_000) {
                    Backends.remote(app).story(AssistRequest("story", idea, Kind.TEXT_TO_VIDEO, style, lang(), scenes))
                }
            }.recoverCatching { e -> if (e is TimeoutCancellationException) throw ApiException("TIMEOUT", "timeout") else throw e }
            onDone(r)
        }
    }

    /** Starts one video job per scene, all in one group. Returns the group id, or the first failure. */
    fun startStory(title: String, scenes: List<String>, style: String, aspect: String, quality: String, onDone: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val groupId = "story-" + java.util.UUID.randomUUID().toString().take(12)
            val r = runCatching {
                val svc = Backends.current(app)
                val seed = (1..999_999).random() // same seed keeps the look consistent between shots
                scenes.forEachIndexed { i, scene ->
                    val req = GenRequest(Kind.TEXT_TO_VIDEO, scene, style, aspect, 5, smart = settings.smartPrompts, quality = quality, seed = seed)
                    val job = withTimeout(90_000) { svc.submit(req) }
                    dao.upsert(
                        LibraryItem(
                            id = job.id, kind = Kind.TEXT_TO_VIDEO, prompt = scene, style = style, aspect = aspect,
                            createdAt = System.currentTimeMillis(), status = Status.QUEUED, demo = svc.isDemo,
                            finalPrompt = job.finalPrompt, groupId = groupId, groupTitle = title, sceneIndex = i,
                        ),
                    )
                    TrackJobWorker.enqueue(app, job.id)
                }
                groupId
            }.recoverCatching { e -> if (e is TimeoutCancellationException) throw ApiException("TIMEOUT", "timeout") else throw e }
            onDone(r)
        }
    }

    private fun lang(): String {
        val l = settings.language.takeIf { it != "system" } ?: Locale.getDefault().language
        return if (l == "ar") "ar" else "en"
    }

    /**
     * Submits [count] variations of [req] (different seeds). [onDone] gets null when at least one
     * started, or the failure. Runs on the main thread.
     */
    fun submit(req: GenRequest, count: Int = 1, onDone: (Throwable?) -> Unit) {
        viewModelScope.launch {
            try {
                val svc = Backends.current(app)
                var started = 0
                var firstError: Exception? = null
                for (n in 0 until count.coerceIn(1, 4)) {
                    try {
                        val seed = if (count > 1) (1..999_999).random() else req.seed
                        val job = withTimeout(90_000) { svc.submit(req.copy(smart = settings.smartPrompts, seed = seed)) }
                        dao.upsert(
                            LibraryItem(
                                id = job.id, kind = req.kind, prompt = req.prompt, style = req.style, aspect = req.aspect,
                                createdAt = System.currentTimeMillis(), status = Status.QUEUED, demo = svc.isDemo,
                                finalPrompt = job.finalPrompt, durationSec = req.durationSec,
                            ),
                        )
                        TrackJobWorker.enqueue(app, job.id)
                        started++
                    } catch (e: TimeoutCancellationException) {
                        if (firstError == null) firstError = ApiException("TIMEOUT", "Server timed out")
                    } catch (e: Exception) {
                        if (firstError == null) firstError = e
                        if (e is ApiException && e.code in setOf("BALANCE", "QUOTA", "PRO_LOCKED", "UNAUTHORIZED")) break
                    }
                }
                if (started == 0) throw firstError ?: ApiException(null, "failed")
                onDone(null)
            } catch (e: TimeoutCancellationException) {
                onDone(ApiException("TIMEOUT", "Server timed out"))
            } catch (e: Exception) {
                onDone(e)
            }
        }
    }

    /** Re-runs a failed text prompt as a new job. */
    fun retry(item: LibraryItem, onDone: (Throwable?) -> Unit) {
        submit(GenRequest(item.kind, item.prompt, item.style, item.aspect, item.durationSec)) { e ->
            if (e == null) delete(item.id)
            onDone(e)
        }
    }

    /** AI helper: "enhance" rewrites [prompt] into a rich prompt, "surprise" invents a new idea. */
    fun assist(mode: String, prompt: String, kind: Kind, style: String, onDone: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val r = runCatching {
                if (Backends.backendUrl(app).isBlank()) throw ApiException("NO_PROVIDER", "demo mode")
                withTimeout(60_000) { Backends.remote(app).assist(AssistRequest(mode, prompt, kind, style, lang())) }
            }.recoverCatching { e ->
                if (e is TimeoutCancellationException) throw ApiException("TIMEOUT", "timeout") else throw e
            }
            onDone(r)
        }
    }

    /** Asks the backend who we are and which providers it has configured. */
    suspend fun testConnection(): Result<ServerStatus> {
        if (Backends.backendUrl(app).isBlank()) return Result.failure(ApiException("NO_PROVIDER", "demo mode"))
        return runCatching { withTimeout(30_000) { Backends.remote(app).status() } }
            .onSuccess { _server.value = it }
    }

    suspend fun addLocalNow(item: LibraryItem) = dao.upsert(item)

    fun addLocal(item: LibraryItem) {
        viewModelScope.launch { dao.upsert(item) }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.delete(id) }
    }
}
