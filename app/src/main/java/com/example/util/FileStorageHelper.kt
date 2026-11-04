package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.remote.SupabaseStorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileStorageHelper {

    fun saveBytesToDevice(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            var targetFile = File(downloadsDir, fileName)
            var counter = 1
            val baseName = fileName.substringBeforeLast(".")
            val ext = if (fileName.contains(".")) "." + fileName.substringAfterLast(".") else ""
            while (targetFile.exists()) {
                targetFile = File(downloadsDir, "${baseName}_$counter$ext")
                counter++
            }

            FileOutputStream(targetFile).use { it.write(bytes) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, targetFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
            }

            Toast.makeText(context, "Saved to Downloads: ${targetFile.name}", Toast.LENGTH_LONG).show()
            targetFile
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun saveFileToDevice(
        context: Context,
        fileName: String,
        mimeType: String,
        base64Content: String?,
        storageUrl: String?,
        supabaseService: SupabaseStorageService,
        scope: CoroutineScope
    ) {
        if (!base64Content.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(base64Content, Base64.DEFAULT)
                saveBytesToDevice(context, fileName, mimeType, bytes)
                return
            } catch (e: Exception) {
                // Try URL if available
            }
        }

        if (!storageUrl.isNullOrBlank()) {
            Toast.makeText(context, "Downloading from Supabase...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val bytes = supabaseService.downloadBytes(storageUrl)
                withContext(Dispatchers.Main) {
                    if (bytes != null) {
                        saveBytesToDevice(context, fileName, mimeType, bytes)
                    } else {
                        Toast.makeText(context, "Failed to download from Supabase URL", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "No file content available to download", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(
        context: Context,
        fileName: String,
        mimeType: String,
        base64Content: String?,
        storageUrl: String?,
        supabaseService: SupabaseStorageService,
        scope: CoroutineScope
    ) {
        if (!base64Content.isNullOrBlank()) {
            try {
                val file = getCachedFile(context, fileName, Base64.decode(base64Content, Base64.DEFAULT))
                launchShareIntent(context, fileName, mimeType, file, storageUrl)
                return
            } catch (e: Exception) {
                // Fallback to URL
            }
        }

        if (!storageUrl.isNullOrBlank()) {
            scope.launch {
                val bytes = supabaseService.downloadBytes(storageUrl)
                withContext(Dispatchers.Main) {
                    if (bytes != null) {
                        val file = getCachedFile(context, fileName, bytes)
                        launchShareIntent(context, fileName, mimeType, file, storageUrl)
                    } else {
                        // Share direct web link
                        shareUrlOnly(context, fileName, storageUrl)
                    }
                }
            }
        }
    }

    private fun launchShareIntent(
        context: Context,
        fileName: String,
        mimeType: String,
        file: File,
        storageUrl: String?
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                val extraText = if (!storageUrl.isNullOrBlank()) {
                    "File: $fileName\nDownload Link (for PC/Mobile): $storageUrl"
                } else {
                    "Sent from CloudVault: $fileName"
                }
                putExtra(Intent.EXTRA_TEXT, extraText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share or Send to PC / Device"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareUrlOnly(context: Context, fileName: String, url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, "Open and download '$fileName' on your PC or Phone:\n$url")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Web Link for PC"))
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Link") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openFile(
        context: Context,
        fileName: String,
        mimeType: String,
        base64Content: String?,
        storageUrl: String?,
        supabaseService: SupabaseStorageService,
        scope: CoroutineScope
    ) {
        if (!base64Content.isNullOrBlank()) {
            try {
                val file = getCachedFile(context, fileName, Base64.decode(base64Content, Base64.DEFAULT))
                launchViewIntent(context, file, mimeType)
                return
            } catch (e: Exception) {
                // Try URL
            }
        }

        if (!storageUrl.isNullOrBlank()) {
            scope.launch {
                val bytes = supabaseService.downloadBytes(storageUrl)
                withContext(Dispatchers.Main) {
                    if (bytes != null) {
                        val file = getCachedFile(context, fileName, bytes)
                        launchViewIntent(context, file, mimeType)
                    } else {
                        openInBrowser(context, storageUrl)
                    }
                }
            }
        } else {
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchViewIntent(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(viewIntent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCachedFile(context: Context, fileName: String, bytes: ByteArray): File {
        val cacheFolder = File(context.cacheDir, "shared_files").apply { mkdirs() }
        val file = File(cacheFolder, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    fun readUriBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    fun processImageUri(context: Context, uri: Uri): Pair<ByteArray, ByteArray?>? {
        return try {
            val bytes = readUriBytes(context, uri) ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            val maxDim = 1280
            val width = bitmap.width
            val height = bitmap.height
            val scale = if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio > 1) {
                    Pair(maxDim, (maxDim / ratio).toInt())
                } else {
                    Pair((maxDim * ratio).toInt(), maxDim)
                }
            } else {
                Pair(width, height)
            }

            val scaledBitmap = if (scale.first != width || scale.second != height) {
                Bitmap.createScaledBitmap(bitmap, scale.first, scale.second, true)
            } else {
                bitmap
            }

            val fullStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fullStream)
            val fullBytes = fullStream.toByteArray()

            val thumbBitmap = Bitmap.createScaledBitmap(bitmap, 140, 140, true)
            val thumbStream = ByteArrayOutputStream()
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 70, thumbStream)
            val thumbBytes = thumbStream.toByteArray()

            Pair(fullBytes, thumbBytes)
        } catch (e: Exception) {
            null
        }
    }
}
