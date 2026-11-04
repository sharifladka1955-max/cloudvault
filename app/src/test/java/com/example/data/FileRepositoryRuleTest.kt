package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.repository.FileRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FileRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun uploadFile_authenticatedOwner_createsDocumentSuccessfully() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FileRepository(firestore)
        val fileId = "test_file_" + UUID.randomUUID().toString().replace("-", "")

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.uploadFile(
                fileId = fileId,
                name = "vacation_photo.jpg",
                mimeType = "image/jpeg",
                fileType = "PHOTO",
                sizeBytes = 4096,
                contentBase64 = "aGVsbG8gd29ybGQ=",
                thumbnailBase64 = "dGh1bWI=",
                category = "Photos",
                description = "Summer vacation photo"
            )
        }
        assertTrue(result.isSuccess)
        assertEquals(fileId, result.getOrThrow())
    }

    @Test
    fun observeFiles_authenticatedOwner_emitsUploadedFile() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FileRepository(firestore)
        val fileId = "test_file_" + UUID.randomUUID().toString().replace("-", "")

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.uploadFile(
                fileId = fileId,
                name = "contract.pdf",
                mimeType = "application/pdf",
                fileType = "PDF",
                sizeBytes = 8192,
                contentBase64 = "JVBERi0xLjQK",
                category = "PDFs"
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeFiles().first { files -> files.any { it.id == fileId } }
        }
        assertTrue(emitted.any { it.id == fileId })
    }

    @Test
    fun getFileById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FileRepository(firestore)
        val fileId = "test_file_" + UUID.randomUUID().toString().replace("-", "")

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.uploadFile(
                fileId = fileId,
                name = "private_doc.pdf",
                mimeType = "application/pdf",
                fileType = "PDF",
                sizeBytes = 1024,
                contentBase64 = "JVBERi0=",
                category = "Work"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val aliceDocRef = firestore.collection("users").document(aliceUid).collection("files").document(fileId)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                aliceDocRef.get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob accesses Alice's document")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    @Test
    fun observeFiles_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FileRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeFiles().first()
            }
            fail("Expected exception when unauthenticated")
        } catch (e: Exception) {
            // Expected
            assertTrue(e is IllegalStateException || e.unwrapFirestoreException() != null)
        }
    }

    @Test
    fun toggleStar_authenticatedOwner_updatesFlag() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FileRepository(firestore)
        val fileId = "test_file_" + UUID.randomUUID().toString().replace("-", "")

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.uploadFile(
                fileId = fileId,
                name = "starred_item.jpg",
                mimeType = "image/jpeg",
                fileType = "PHOTO",
                sizeBytes = 2048,
                contentBase64 = "aGVsbG8=",
                category = "Photos"
            ).getOrThrow()
        }

        val toggleResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.toggleStar(fileId, currentStarred = false)
        }
        assertTrue(toggleResult.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@example.com"
        const val BOB_EMAIL = "bob@example.com"
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
