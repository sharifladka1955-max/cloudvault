package com.example.ui.viewmodel

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.CloudFile
import com.example.data.remote.SupabaseStorageService
import com.example.data.repository.FileRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class FileFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    PDFS("PDFs"),
    STARRED("Starred")
}

data class StorageStats(
    val totalFiles: Int = 0,
    val photoCount: Int = 0,
    val pdfCount: Int = 0,
    val totalBytes: Long = 0L
) {
    fun formattedTotalSize(): String {
        return when {
            totalBytes < 1024 -> "$totalBytes B"
            totalBytes < 1024 * 1024 -> String.format("%.1f KB", totalBytes / 1024.0)
            else -> String.format("%.2f MB", totalBytes / (1024.0 * 1024.0))
        }
    }
}

sealed interface VaultUiState {
    data object Loading : VaultUiState
    data class Success(val files: List<CloudFile>) : VaultUiState
    data class Error(val message: String) : VaultUiState
}

class VaultViewModel(
    private val repository: FileRepository,
    val supabaseService: SupabaseStorageService
) : ViewModel() {

    val rawFilesState: StateFlow<VaultUiState> = repository.observeFiles()
        .map<List<CloudFile>, VaultUiState> { VaultUiState.Success(it) }
        .catch { error -> emit(VaultUiState.Error(error.localizedMessage ?: "Failed to load files")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = VaultUiState.Loading
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentFilter = MutableStateFlow(FileFilter.ALL)
    val currentFilter: StateFlow<FileFilter> = _currentFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _isSupabaseConfigured = MutableStateFlow(supabaseService.isConfigured())
    val isSupabaseConfigured: StateFlow<Boolean> = _isSupabaseConfigured.asStateFlow()

    val filteredFiles: StateFlow<List<CloudFile>> = combine(
        rawFilesState,
        _searchQuery,
        _currentFilter,
        _selectedCategory
    ) { state, query, filter, category ->
        if (state !is VaultUiState.Success) return@combine emptyList()
        var list = state.files

        list = when (filter) {
            FileFilter.ALL -> list
            FileFilter.PHOTOS -> list.filter { it.fileType == "PHOTO" }
            FileFilter.PDFS -> list.filter { it.fileType == "PDF" }
            FileFilter.STARRED -> list.filter { it.isStarred }
        }

        if (category != null && category != "All") {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                    (it.description?.lowercase()?.contains(q) == true) ||
                    it.category.lowercase().contains(q)
            }
        }

        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val storageStats: StateFlow<StorageStats> = rawFilesState.map { state ->
        if (state is VaultUiState.Success) {
            val files = state.files
            StorageStats(
                totalFiles = files.size,
                photoCount = files.count { it.fileType == "PHOTO" },
                pdfCount = files.count { it.fileType == "PDF" },
                totalBytes = files.sumOf { it.sizeBytes }
            )
        } else {
            StorageStats()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = StorageStats()
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onFilterSelected(filter: FileFilter) {
        _currentFilter.value = filter
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun toggleStar(file: CloudFile) {
        viewModelScope.launch {
            val result = repository.toggleStar(file.id, file.isStarred)
            if (result.isFailure) {
                _userMessage.emit("Failed to update favorite status")
            }
        }
    }

    fun updateMetadata(file: CloudFile, newName: String, newCategory: String, newDesc: String?) {
        viewModelScope.launch {
            val result = repository.updateFileMetadata(file.id, newName, newCategory, newDesc)
            if (result.isSuccess) {
                _userMessage.emit("Updated successfully")
            } else {
                _userMessage.emit("Failed to update: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteFile(file: CloudFile) {
        viewModelScope.launch {
            if (!file.storagePath.isNullOrBlank()) {
                supabaseService.deleteFile(file.storagePath)
            }
            val result = repository.deleteFile(file.id)
            if (result.isSuccess) {
                _userMessage.emit("Deleted '${file.name}' from cloud")
            } else {
                _userMessage.emit("Failed to delete file")
            }
        }
    }

    fun updateSupabaseCredentials(url: String?, key: String?) {
        supabaseService.setCustomCredentials(url, key)
        _isSupabaseConfigured.value = supabaseService.isConfigured()
        viewModelScope.launch {
            if (supabaseService.isConfigured()) {
                _userMessage.emit("Supabase Storage connected successfully!")
            } else {
                _userMessage.emit("Supabase credentials reset to default")
            }
        }
    }

    fun uploadPickedFile(
        name: String,
        mimeType: String,
        fileType: String,
        sizeBytes: Long,
        bytes: ByteArray,
        thumbnailBytes: ByteArray? = null,
        category: String = "General",
        description: String? = null
    ) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val fileId = "file_" + UUID.randomUUID().toString().replace("-", "")
                val uid = Firebase.auth.currentUser?.uid ?: "anonymous"
                val thumbBase64 = thumbnailBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

                var storageUrl: String? = null
                var storagePath: String? = null
                var contentBase64Fallback: String? = null

                // 1. Try Supabase Storage first if configured
                if (supabaseService.isConfigured()) {
                    val supabaseResult = supabaseService.uploadFile(
                        bytes = bytes,
                        fileName = name,
                        mimeType = mimeType,
                        userId = uid
                    )
                    if (supabaseResult.isSuccess) {
                        val res = supabaseResult.getOrThrow()
                        storageUrl = res.publicUrl
                        storagePath = res.storagePath
                    } else {
                        // Log issue and notify
                        val err = supabaseResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                        _userMessage.emit("Supabase upload note: $err. Saving with cloud fallback.")
                    }
                }

                // 2. If storageUrl not generated or file is small enough, provide base64 fallback
                if (storageUrl == null) {
                    if (bytes.size <= 950 * 1024) {
                        contentBase64Fallback = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    } else {
                        _userMessage.emit("File is larger than 950KB. Please configure Supabase Storage in Settings to upload large files for free!")
                        _isUploading.value = false
                        return@launch
                    }
                }

                // 3. Save file document in Firestore
                val result = repository.uploadFile(
                    fileId = fileId,
                    name = name,
                    mimeType = mimeType,
                    fileType = fileType,
                    sizeBytes = sizeBytes,
                    storageUrl = storageUrl,
                    storagePath = storagePath,
                    contentBase64 = contentBase64Fallback,
                    thumbnailBase64 = thumbBase64,
                    category = category,
                    description = description
                )

                if (result.isSuccess) {
                    if (storageUrl != null) {
                        _userMessage.emit("Uploaded to Supabase Storage! Accessible on PC and mobile.")
                    } else {
                        _userMessage.emit("Uploaded '$name' to CloudVault!")
                    }
                } else {
                    _userMessage.emit("Failed to save: ${result.exceptionOrNull()?.localizedMessage}")
                }
            } catch (e: Exception) {
                _userMessage.emit("Upload error: ${e.localizedMessage}")
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun syncUserAccount(email: String, displayName: String) {
        viewModelScope.launch {
            repository.syncUserProfile(email, displayName)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            return VaultViewModel(
                repository = FileRepository(appCtx),
                supabaseService = SupabaseStorageService(appCtx)
            ) as T
        }
    }
}
