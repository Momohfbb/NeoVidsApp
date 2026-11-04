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
)

@Serializable
data class JobDto(
    val id: String,
    val status: Status,
    val progress: Float = 0f,
    val resultUrl: String? = null,
    val error: String? = null,
)

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
)

@Serializable
data class ServerStatus(
    val ok: Boolean = true,
    val owner: Boolean = false,
    val quota: Boolean = false,
    val providers: List<String> = emptyList(),
)
