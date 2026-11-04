package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CloudFile
import com.example.data.remote.SupabaseStorageService
import com.example.ui.theme.VaultAmber
import com.example.ui.theme.VaultBlueLight
import com.example.ui.theme.VaultBluePrimary
import com.example.ui.theme.VaultCyanAccent
import com.example.ui.theme.VaultEmerald
import com.example.ui.viewmodel.FileFilter
import com.example.ui.viewmodel.VaultUiState
import com.example.ui.viewmodel.VaultViewModel
import com.example.util.FileStorageHelper
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHomeScreen(
    user: FirebaseUser,
    viewModel: VaultViewModel,
    onPickPhoto: () -> Unit,
    onPickPdf: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rawState by viewModel.rawFilesState.collectAsState()
    val filteredFiles by viewModel.filteredFiles.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }
    var showPcGuideDialog by remember { mutableStateOf(false) }
    var showSupabaseConfigDialog by remember { mutableStateOf(false) }
    var selectedFileForDetail by remember { mutableStateOf<CloudFile?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(user) {
        viewModel.syncUserAccount(
            email = user.email ?: "",
            displayName = user.displayName ?: (user.email?.substringBefore("@") ?: "User")
        )
    }

    BackHandler(enabled = searchQuery.isNotBlank()) {
        viewModel.onSearchQueryChanged("")
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(VaultBluePrimary, VaultBlueLight)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CloudVault",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Sync indicator
                                Box(
                                    modifier = Modifier
                                        .background(VaultEmerald.copy(alpha = 0.15f), CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDone,
                                            contentDescription = "Synced",
                                            tint = VaultEmerald,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Synced",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VaultEmerald
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Supabase Cloud Storage Status / Settings Button
                    IconButton(
                        onClick = { showSupabaseConfigDialog = true },
                        modifier = Modifier.testTag("supabase_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Supabase Storage Config",
                            tint = if (viewModel.supabaseService.isConfigured()) VaultEmerald else VaultAmber
                        )
                    }

                    // PC & Multi-Device Access Guide Button
                    IconButton(
                        onClick = { showPcGuideDialog = true },
                        modifier = Modifier.testTag("pc_access_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "Access on PC",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // User Profile / Avatar
                    IconButton(
                        onClick = { showProfileDialog = true },
                        modifier = Modifier.testTag("profile_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User Profile",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { showFabMenu = true },
                    icon = { Icon(imageVector = Icons.Default.Add, contentDescription = "Add") },
                    text = { Text("Upload", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("upload_fab")
                )

                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Upload Photo") },
                        onClick = {
                            showFabMenu = false
                            onPickPhoto()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = VaultBluePrimary
                            )
                        },
                        modifier = Modifier.testTag("upload_photo_menu_item")
                    )
                    DropdownMenuItem(
                        text = { Text("Upload PDF Document") },
                        onClick = {
                            showFabMenu = false
                            onPickPdf()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = VaultCyanAccent
                            )
                        },
                        modifier = Modifier.testTag("upload_pdf_menu_item")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Uploading progress bar
            AnimatedVisibility(visible = isUploading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Encrypting & uploading to Cloud...",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Storage Summary Card
            StorageBannerCard(
                stats = storageStats,
                isSupabaseConfigured = viewModel.supabaseService.isConfigured(),
                onPcGuideClicked = { showPcGuideDialog = true },
                onSupabaseClicked = { showSupabaseConfigDialog = true }
            )

            // Search Bar & View Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search photos or PDFs...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("search_field")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { viewModel.toggleViewMode() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(14.dp)
                        )
                        .testTag("toggle_view_mode_button")
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = if (isGridView) "Switch to List" else "Switch to Grid",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileFilter.entries.forEach { filter ->
                    val isSelected = currentFilter == filter
                    val count = when (filter) {
                        FileFilter.ALL -> storageStats.totalFiles
                        FileFilter.PHOTOS -> storageStats.photoCount
                        FileFilter.PDFS -> storageStats.pdfCount
                        FileFilter.STARRED -> filteredFiles.count { it.isStarred }
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = {
                            Text("${filter.label} ($count)")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Content: Loading, Empty, or File List/Grid
            when (val state = rawState) {
                is VaultUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is VaultUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Unable to connect to CloudVault",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is VaultUiState.Success -> {
                    if (filteredFiles.isEmpty()) {
                        EmptyVaultView(
                            hasSearchQuery = searchQuery.isNotBlank(),
                            onUploadPhoto = onPickPhoto,
                            onUploadPdf = onPickPdf
                        )
                    } else if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredFiles, key = { it.id }) { file ->
                                FileGridCard(
                                    file = file,
                                    onClick = { selectedFileForDetail = file },
                                    onToggleStar = { viewModel.toggleStar(file) },
                                    onDownload = {
                                        FileStorageHelper.saveFileToDevice(
                                            context = context,
                                            fileName = file.name,
                                            mimeType = file.mimeType,
                                            base64Content = file.contentBase64,
                                            storageUrl = file.storageUrl,
                                            supabaseService = viewModel.supabaseService,
                                            scope = coroutineScope
                                        )
                                    },
                                    onShare = {
                                        FileStorageHelper.shareFile(
                                            context = context,
                                            fileName = file.name,
                                            mimeType = file.mimeType,
                                            base64Content = file.contentBase64,
                                            storageUrl = file.storageUrl,
                                            supabaseService = viewModel.supabaseService,
                                            scope = coroutineScope
                                        )
                                    },
                                    onCopyPcLink = {
                                        if (!file.storageUrl.isNullOrBlank()) {
                                            FileStorageHelper.copyToClipboard(
                                                context = context,
                                                text = file.storageUrl,
                                                label = "Web Download Link for PC"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredFiles, key = { it.id }) { file ->
                                FileListRow(
                                    file = file,
                                    onClick = { selectedFileForDetail = file },
                                    onToggleStar = { viewModel.toggleStar(file) },
                                    onDownload = {
                                        FileStorageHelper.saveFileToDevice(
                                            context = context,
                                            fileName = file.name,
                                            mimeType = file.mimeType,
                                            base64Content = file.contentBase64,
                                            storageUrl = file.storageUrl,
                                            supabaseService = viewModel.supabaseService,
                                            scope = coroutineScope
                                        )
                                    },
                                    onShare = {
                                        FileStorageHelper.shareFile(
                                            context = context,
                                            fileName = file.name,
                                            mimeType = file.mimeType,
                                            base64Content = file.contentBase64,
                                            storageUrl = file.storageUrl,
                                            supabaseService = viewModel.supabaseService,
                                            scope = coroutineScope
                                        )
                                    },
                                    onCopyPcLink = {
                                        if (!file.storageUrl.isNullOrBlank()) {
                                            FileStorageHelper.copyToClipboard(
                                                context = context,
                                                text = file.storageUrl,
                                                label = "Web Download Link for PC"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showProfileDialog) {
        UserProfileDialog(
            displayName = user.displayName,
            email = user.email,
            totalFiles = storageStats.totalFiles,
            storageFormatted = storageStats.formattedTotalSize(),
            onDismiss = { showProfileDialog = false },
            onSignOut = {
                showProfileDialog = false
                onSignOut()
            }
        )
    }

    if (showPcGuideDialog) {
        PcAccessGuideDialog(
            userEmail = user.email,
            onDismiss = { showPcGuideDialog = false }
        )
    }

    selectedFileForDetail?.let { file ->
        FileDetailViewerDialog(
            file = file,
            supabaseService = viewModel.supabaseService,
            onDismiss = { selectedFileForDetail = null },
            onToggleStar = { viewModel.toggleStar(file) },
            onUpdateMetadata = { newName, newCategory, newDesc ->
                viewModel.updateMetadata(file, newName, newCategory, newDesc)
            },
            onDelete = {
                viewModel.deleteFile(file)
            }
        )
    }

    if (showSupabaseConfigDialog) {
        SupabaseConfigDialog(
            supabaseService = viewModel.supabaseService,
            onDismiss = { showSupabaseConfigDialog = false },
            onSave = { newUrl, newKey ->
                viewModel.updateSupabaseCredentials(newUrl, newKey)
                showSupabaseConfigDialog = false
            }
        )
    }
}

@Composable
private fun StorageBannerCard(
    stats: com.example.ui.viewmodel.StorageStats,
    isSupabaseConfigured: Boolean,
    onPcGuideClicked: () -> Unit,
    onSupabaseClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cloud Storage: ${stats.formattedTotalSize()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSupabaseConfigured) VaultEmerald.copy(alpha = 0.2f)
                                else VaultAmber.copy(alpha = 0.25f)
                            )
                            .clickable(onClick = onSupabaseClicked)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSupabaseConfigured) "Supabase Active" else "Supabase Setup",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSupabaseConfigured) VaultEmerald else VaultAmber
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${stats.photoCount} Photos • ${stats.pdfCount} PDFs • Supabase & Firestore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onPcGuideClicked,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("PC Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FileGridCard(
    file: CloudFile,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onCopyPcLink: () -> Unit
) {
    val context = LocalContext.current
    val fallbackBitmap = remember(file.id, file.thumbnailBase64, file.contentBase64) {
        if (file.fileType == "PHOTO") {
            try {
                val b64 = file.thumbnailBase64 ?: file.contentBase64
                if (!b64.isNullOrBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } else null
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("file_item_${file.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Preview / Icon Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (file.fileType == "PHOTO") {
                    if (!file.storageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(file.storageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (fallbackBitmap != null) {
                        Image(
                            bitmap = fallbackBitmap,
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Photo",
                            tint = VaultBluePrimary,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF File",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "PDF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Star badge top-end
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .testTag("star_${file.id}")
                ) {
                    Icon(
                        imageVector = if (file.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Star",
                        tint = if (file.isStarred) VaultAmber else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Info Area
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = file.formattedSize(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action buttons: PC Link, Download & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!file.storageUrl.isNullOrBlank()) {
                        IconButton(
                            onClick = onCopyPcLink,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("copy_pc_link_${file.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Copy Link for PC",
                                modifier = Modifier.size(18.dp),
                                tint = VaultEmerald
                            )
                        }
                    }

                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("download_${file.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download to Device",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("share_${file.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Send to PC / Share",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileListRow(
    file: CloudFile,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onCopyPcLink: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("file_item_${file.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon / Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (file.fileType == "PHOTO") {
                    if (!file.storageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(file.storageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Photo",
                            tint = VaultBluePrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF",
                        tint = VaultCyanAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Category
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " • ${file.formattedSize()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Copy PC link
            if (!file.storageUrl.isNullOrBlank()) {
                IconButton(
                    onClick = onCopyPcLink,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Copy Link for PC",
                        tint = VaultEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Star
            IconButton(
                onClick = onToggleStar,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (file.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Star",
                    tint = if (file.isStarred) VaultAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Download
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Share / Send to PC
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Send to PC",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyVaultView(
    hasSearchQuery: Boolean,
    onUploadPhoto: () -> Unit,
    onUploadPdf: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (hasSearchQuery) "No matching files" else "CloudVault is Empty",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasSearchQuery)
                    "Try a different search term or clear the filter."
                else
                    "Upload photos and PDF documents to sync and access them on any phone or PC!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (!hasSearchQuery) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onUploadPhoto,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("empty_upload_photo_button")
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Photo")
                    }

                    OutlinedButton(
                        onClick = onUploadPdf,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("empty_upload_pdf_button")
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add PDF")
                    }
                }
            }
        }
    }
}
