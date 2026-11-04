package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CloudFile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val mimeType: String = "",
    val fileType: String = "PHOTO", // "PHOTO", "PDF", "DOCUMENT"
    val sizeBytes: Long = 0L,
    val storageUrl: String? = null,
    val storagePath: String? = null,
    val contentBase64: String? = null,
    val thumbnailBase64: String? = null,
    val description: String? = null,
    val category: String = "General",
    val isStarred: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun formattedSize(): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
            else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }
    }
}
