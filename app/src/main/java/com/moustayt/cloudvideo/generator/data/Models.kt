package com.moustayt.cloudvideo.generator.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class Kind { TEXT_TO_VIDEO, IMAGE_TO_VIDEO, TEXT_TO_IMAGE, IMAGE_EDIT;
    val isVideo get() = this == TEXT_TO_VIDEO || this == IMAGE_TO_VIDEO
    val needsImage get() = this == IMAGE_TO_VIDEO || this == IMAGE_EDIT
}

@Serializable
enum class Status { QUEUED, RUNNING, DONE, FAILED }

@Serializable
data class GenRequest(
    val kind: Kind,
    val prompt: String,
    val style: String,
    val aspect: String,
    val durationSec: Int = 5,
    /** data:image/jpeg;base64,... for image-to-video and image editing */
    val sourceImage: String? = null,
    val provider: String? = null,
    /** Let the server's AI translate (e.g. Arabic) and enrich the prompt before generation. */
    val smart: Boolean = true,
    /** "fast" (default models) or "pro" (premium licensed video models, owner only). */
    val quality: String = "fast",
    /** Different seeds make several variations of the same prompt. */
    val seed: Int? = null,
)

@Serializable
data class JobDto(
    val id: String,
    val status: Status,
    val progress: Float = 0f,
    val resultUrl: String? = null,
    val error: String? = null,
    /** BALANCE, QUOTA, NO_PROVIDER, UNAUTHORIZED, PROVIDER... (see backend) */
    val code: String? = null,
    /** The English prompt the server's AI actually used. */
    val finalPrompt: String? = null,
    val provider: String? = null,
)

@Serializable
data class AssistRequest(
    val mode: String,
    val prompt: String = "",
    val kind: Kind,
    val style: String,
    val lang: String,
    val scenes: Int = 4,
)

@Serializable
data class StoryDto(val title: String = "", val scenes: List<String> = emptyList())

@Serializable
data class AssistDto(val prompt: String)

@Serializable
data class ErrorDto(val error: String = "", val code: String? = null)

/** A failure the UI can translate into a friendly, localized message. */
class ApiException(val code: String?, message: String) : Exception(message)

@Serializable
@Entity(tableName = "items")
data class LibraryItem(
    @PrimaryKey val id: String,
    val kind: Kind,
    val prompt: String,
    val style: String = "",
    val aspect: String = "",
    val createdAt: Long,
    val status: Status,
    val progress: Float = 0f,
    val resultUrl: String? = null,
    val error: String? = null,
    val demo: Boolean = false,
    /** true for videos exported by the local editor */
    @ColumnInfo(name = "is_local") val local: Boolean = false,
    val finalPrompt: String? = null,
    val errorCode: String? = null,
    @ColumnInfo(defaultValue = "5") val durationSec: Int = 5,
    /** Scenes of one AI story share a group id; the assembled film carries it too. */
    val groupId: String? = null,
    val groupTitle: String? = null,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** Order of a scene inside its story. */
    @ColumnInfo(defaultValue = "0") val sceneIndex: Int = 0,
)

@Serializable
data class ServerStatus(
    val ok: Boolean = true,
    val owner: Boolean = false,
    val quota: Boolean = false,
    val ai: Boolean = false,
    val providers: List<String> = emptyList(),
)
