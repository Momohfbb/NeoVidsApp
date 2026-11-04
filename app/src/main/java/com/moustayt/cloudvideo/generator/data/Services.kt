package com.moustayt.cloudvideo.generator.data

import android.content.Context
import com.moustayt.cloudvideo.generator.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder
import java.util.UUID

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

const val DEMO_VIDEO = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

interface GenerationService {
    val isDemo: Boolean
    suspend fun submit(req: GenRequest): String
    suspend fun poll(id: String): JobDto
}

/** Talks to the NeoVids backend (see /backend). API keys live only on the server. */
class RemoteService(baseUrl: String, private val ownerKey: String, private val deviceId: String) : GenerationService {
    private val base = baseUrl.trim().trimEnd('/')
    override val isDemo = false

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(AppJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 20_000
        }
        expectSuccess = true
    }

    private fun HttpRequestBuilder.auth() {
        if (BuildConfig.APP_TOKEN.isNotBlank()) header("x-app-token", BuildConfig.APP_TOKEN)
        if (ownerKey.isNotBlank()) header("x-owner-key", ownerKey)
        header("x-device-id", deviceId)
    }

    /** Turns {"error": "..."} bodies from the backend into readable messages (e.g. daily limit reached). */
    private suspend fun <T> readable(block: suspend () -> T): T = try {
        block()
    } catch (e: ResponseException) {
        val msg = runCatching { AppJson.parseToJsonElement(e.response.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        throw Exception(msg ?: e.message)
    }

    override suspend fun submit(req: GenRequest): String = readable {
        client.post("$base/v1/jobs") {
            contentType(ContentType.Application.Json)
            auth()
            setBody(req)
        }.body<JobDto>().id
    }

    override suspend fun poll(id: String): JobDto = readable {
        client.get("$base/v1/jobs/${URLEncoder.encode(id, "UTF-8")}") { auth() }.body()
    }
}

/** Works with no server at all, so the app is usable right after install. */
class DemoService : GenerationService {
    override val isDemo = true

    override suspend fun submit(req: GenRequest): String =
        "demo.${req.kind.name}.${System.currentTimeMillis()}.${UUID.randomUUID().toString().take(8)}"

    override suspend fun poll(id: String): JobDto {
        val p = id.split(".")
        val kind = Kind.valueOf(p[1])
        val start = p[2].toLong()
        val seed = p[3]
        val progress = ((System.currentTimeMillis() - start) / 6000f).coerceIn(0f, 1f)
        if (progress < 1f) {
            return JobDto(id, if (progress < 0.1f) Status.QUEUED else Status.RUNNING, progress)
        }
        val url = if (kind.isVideo) DEMO_VIDEO else "https://picsum.photos/seed/$seed/768/768"
        return JobDto(id, Status.DONE, 1f, url)
    }
}

class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "library.json")

    fun load(): List<LibraryItem> =
        runCatching { AppJson.decodeFromString<List<LibraryItem>>(file.readText()) }.getOrDefault(emptyList())

    fun save(items: List<LibraryItem>) {
        runCatching { file.writeText(AppJson.encodeToString(items)) }
    }
}

class Settings(context: Context) {
    private val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var language: String
        get() = p.getString("lang", "system") ?: "system"
        set(v) = p.edit().putString("lang", v).apply()
    var theme: String
        get() = p.getString("theme", "system") ?: "system"
        set(v) = p.edit().putString("theme", v).apply()
    var backendUrl: String
        get() = p.getString("backend", "") ?: ""
        set(v) = p.edit().putString("backend", v.trim()).apply()
    /** Personal key that unlocks unlimited use on your own backend. */
    var ownerKey: String
        get() = p.getString("owner_key", "") ?: ""
        set(v) = p.edit().putString("owner_key", v.trim()).apply()
    val deviceId: String
        get() = p.getString("device_id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device_id", it).apply() }
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    companion object {
        private const val PREFS = "settings"

        /** Applies the in-app language choice (also flips layout direction for Arabic). */
        fun wrap(base: Context): Context {
            val lang = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "system")
            if (lang == null || lang == "system") return base
            val locale = java.util.Locale(lang)
            java.util.Locale.setDefault(locale)
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }
    }
}
