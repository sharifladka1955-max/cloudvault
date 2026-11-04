package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CloudFile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FileRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing CloudVault.")
    }

    fun observeFiles(): Flow<List<CloudFile>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/files"
        val query = db.collection("users").document(uid).collection("files")

        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CloudFile::class.java).sortedByDescending { it.createdAt }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getFileById(fileId: String): Result<CloudFile?> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("files").document(fileId)
        return try {
            val snapshot = docRef.get().await()
            Result.success(snapshot.toObject(CloudFile::class.java))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        fileId: String,
        name: String,
        mimeType: String,
        fileType: String,
        sizeBytes: Long,
        storageUrl: String? = null,
        storagePath: String? = null,
        contentBase64: String? = null,
        thumbnailBase64: String? = null,
        category: String = "General",
        description: String? = null
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("files").document(fileId)

        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "name" to name,
            "mimeType" to mimeType,
            "fileType" to fileType,
            "sizeBytes" to sizeBytes,
            "category" to category,
            "isStarred" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (storageUrl != null) payload["storageUrl"] = storageUrl
        if (storagePath != null) payload["storagePath"] = storagePath
        if (contentBase64 != null) payload["contentBase64"] = contentBase64
        if (thumbnailBase64 != null) payload["thumbnailBase64"] = thumbnailBase64
        if (description != null) payload["description"] = description

        return try {
            docRef.set(payload).await()
            Result.success(fileId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateFileMetadata(
        fileId: String,
        name: String,
        category: String,
        description: String?
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("files").document(fileId)

        val updates = mutableMapOf<String, Any?>(
            "name" to name,
            "category" to category,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (description != null) {
            updates["description"] = description
        }

        val cleanUpdates: Map<String, Any> = buildMap {
            updates.forEach { (k, v) -> if (v != null) put(k, v) }
        }
        return try {
            docRef.update(cleanUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleStar(fileId: String, currentStarred: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("files").document(fileId)
        val updates = mapOf<String, Any>(
            "isStarred" to !currentStarred,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteFile(fileId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("files").document(fileId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncUserProfile(email: String, displayName: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "displayName" to displayName,
                    "email" to email,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
