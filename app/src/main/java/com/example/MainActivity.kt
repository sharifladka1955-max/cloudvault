package com.example

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.AuthManager
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PendingUpload
import com.example.ui.screens.UploadDialog
import com.example.ui.screens.VaultHomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VaultViewModel
import com.example.util.FileStorageHelper

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        authManager = AuthManager(this)

        setContent {
            MyApplicationTheme {
                CloudVaultApp(authManager = authManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        authManager.cleanup()
    }
}

@Composable
fun CloudVaultApp(authManager: AuthManager) {
    val coroutineScope = rememberCoroutineScope()
    val currentUser by authManager.currentUser.collectAsState()

    // Attempt silent auto-sign-in on initial composition
    LaunchedEffect(Unit) {
        authManager.attemptAutoSignIn(coroutineScope)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            val user = currentUser
            if (user == null) {
                AuthScreen(authManager = authManager)
            } else {
                val viewModel: VaultViewModel = viewModel(
                    key = user.uid,
                    factory = VaultViewModel.Factory(androidx.compose.ui.platform.LocalContext.current)
                )

                var pendingUpload by remember { mutableStateOf<PendingUpload?>(null) }
                val isUploading by viewModel.isUploading.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current

                // Zero-permission Android Photo Picker for high quality photos
                val photoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val result = FileStorageHelper.processImageUri(context, uri)
                        if (result != null) {
                            val fileName = resolveFileName(context, uri, "photo.jpg")
                            pendingUpload = PendingUpload(
                                fileName = fileName,
                                mimeType = "image/jpeg",
                                fileType = "PHOTO",
                                bytes = result.first,
                                thumbnailBytes = result.second
                            )
                        } else {
                            Toast.makeText(context, "Could not load selected photo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Document Picker for PDF files
                val pdfPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val bytes = FileStorageHelper.readUriBytes(context, uri)
                        if (bytes != null) {
                            val fileName = resolveFileName(context, uri, "document.pdf")
                            pendingUpload = PendingUpload(
                                fileName = fileName,
                                mimeType = "application/pdf",
                                fileType = "PDF",
                                bytes = bytes
                            )
                        } else {
                            Toast.makeText(context, "Could not read selected PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                VaultHomeScreen(
                    user = user,
                    viewModel = viewModel,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickPdf = {
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    },
                    onSignOut = {
                        authManager.signOut(coroutineScope) {}
                    }
                )

                pendingUpload?.let { pending ->
                    UploadDialog(
                        pendingUpload = pending,
                        isUploading = isUploading,
                        onDismiss = { pendingUpload = null },
                        onConfirmUpload = { name, category, description ->
                            viewModel.uploadPickedFile(
                                name = name,
                                mimeType = pending.mimeType,
                                fileType = pending.fileType,
                                sizeBytes = pending.bytes.size.toLong(),
                                bytes = pending.bytes,
                                thumbnailBytes = pending.thumbnailBytes,
                                category = category,
                                description = description
                            )
                            pendingUpload = null
                        }
                    )
                }
            }
        }
    }
}

private fun resolveFileName(context: android.content.Context, uri: Uri, fallback: String): String {
    var name = fallback
    try {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    } catch (e: Exception) {
        // Use fallback
    }
    return name
}
