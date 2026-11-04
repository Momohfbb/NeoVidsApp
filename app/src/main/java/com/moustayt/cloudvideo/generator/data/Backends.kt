package com.moustayt.cloudvideo.generator.data

import android.content.Context
import com.moustayt.cloudvideo.generator.BuildConfig

/** Chooses between the demo service and the real backend. Shared by the UI and the background worker. */
object Backends {
    private val demo = DemoService()
    private var remote: RemoteService? = null
    private var remoteKey = ""

    fun backendUrl(context: Context): String =
        Settings(context).backendUrl.ifBlank { BuildConfig.BACKEND_URL }

    fun remote(context: Context): RemoteService = synchronized(this) {
        val s = Settings(context)
        val url = backendUrl(context)
        val key = "$url|${s.ownerKey}"
        if (remote == null || remoteKey != key) {
            remote = RemoteService(url, s.ownerKey, s.deviceId)
            remoteKey = key
        }
        remote!!
    }

    fun serviceFor(context: Context, demoItem: Boolean): GenerationService = if (demoItem) demo else remote(context)

    fun current(context: Context): GenerationService =
        if (backendUrl(context).isBlank()) demo else remote(context)
}
