package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SupabaseUploadResult(
    val publicUrl: String,
    val storagePath: String
)

class SupabaseStorageService(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_prefs", Context.MODE_PRIVATE)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val bucketName = "cloudvault"

    fun getSupabaseUrl(): String {
        val custom = prefs.getString("custom_supabase_url", null)
        if (!custom.isNullOrBlank()) return custom.trim().removeSuffix("/")
        return try {
            val configUrl = BuildConfig.SUPABASE_URL
            if (!configUrl.isNullOrBlank() && !configUrl.contains("your-project")) {
                configUrl.trim().removeSuffix("/")
            } else {
                DEFAULT_SUPABASE_URL
            }
        } catch (e: Exception) {
            DEFAULT_SUPABASE_URL
        }
    }

    fun getSupabaseAnonKey(): String {
        val custom = prefs.getString("custom_supabase_anon_key", null)
        if (!custom.isNullOrBlank()) return custom.trim()
        return try {
            val configKey = BuildConfig.SUPABASE_ANON_KEY
            if (!configKey.isNullOrBlank() && !configKey.contains("placeholder")) {
                configKey.trim()
            } else {
                DEFAULT_SUPABASE_ANON_KEY
            }
        } catch (e: Exception) {
            DEFAULT_SUPABASE_ANON_KEY
        }
    }

    fun setCustomCredentials(url: String?, key: String?) {
        prefs.edit().apply {
            if (url.isNullOrBlank()) remove("custom_supabase_url") else putString("custom_supabase_url", url.trim().removeSuffix("/"))
            if (key.isNullOrBlank()) remove("custom_supabase_anon_key") else putString("custom_supabase_anon_key", key.trim())
            apply()
        }
    }

    fun isConfigured(): Boolean {
        val url = getSupabaseUrl()
        val key = getSupabaseAnonKey()
        return url.startsWith("https://") &&
            !url.contains("your-project") &&
            key.isNotBlank() &&
            !key.contains("placeholder")
    }

    suspend fun uploadFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        userId: String
    ): Result<SupabaseUploadResult> = withContext(Dispatchers.IO) {
        val url = getSupabaseUrl()
        val key = getSupabaseAnonKey()

        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Supabase credentials not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in Secrets or app settings.")
            )
        }

        // Clean file name
        val sanitizedName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val uniquePrefix = UUID.randomUUID().toString().take(8)
        val storagePath = "$userId/${uniquePrefix}_$sanitizedName"

        // Ensure bucket exists first
        ensureBucketExists(url, key)

        val uploadUrl = "$url/storage/v1/object/$bucketName/$storagePath"
        val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        val body = bytes.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(uploadUrl)
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("x-upsert", "true")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val publicUrl = "$url/storage/v1/object/public/$bucketName/$storagePath"
                    Log.i(TAG, "Uploaded to Supabase: $publicUrl")
                    Result.success(SupabaseUploadResult(publicUrl = publicUrl, storagePath = storagePath))
                } else {
                    val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                    Log.e(TAG, "Supabase upload error: $errorBody")
                    Result.failure(IOException("Supabase upload failed (${response.code}): $errorBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during Supabase upload", e)
            Result.failure(e)
        }
    }

    suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val key = getSupabaseAnonKey()
        val requestBuilder = Request.Builder().url(url)
        if (isConfigured()) {
            requestBuilder.addHeader("apikey", key)
            requestBuilder.addHeader("Authorization", "Bearer $key")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    Log.e(TAG, "Failed to download from Supabase: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            null
        }
    }

    suspend fun deleteFile(storagePath: String): Boolean = withContext(Dispatchers.IO) {
        val url = getSupabaseUrl()
        val key = getSupabaseAnonKey()
        if (!isConfigured() || storagePath.isBlank()) return@withContext false

        val deleteUrl = "$url/storage/v1/object/$bucketName/$storagePath"
        val request = Request.Builder()
            .url(deleteUrl)
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Supabase file", e)
            false
        }
    }

    private fun ensureBucketExists(url: String, key: String) {
        val bucketUrl = "$url/storage/v1/bucket"
        val jsonPayload = JSONObject().apply {
            put("id", bucketName)
            put("name", bucketName)
            put("public", true)
        }.toString()

        val body = jsonPayload.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(bucketUrl)
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                // 200/201 or 409 (already exists) are fine
                Log.d(TAG, "Bucket check response: ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bucket check warning: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SupabaseStorage"
        const val DEFAULT_SUPABASE_URL = "https://aohgpylausnmgbrzxbiy.supabase.co"
        const val DEFAULT_SUPABASE_ANON_KEY = "sb_publishable_5mxHnLc5OVvU6PtVTukc1w_qa0-lxYV"
    }
}
