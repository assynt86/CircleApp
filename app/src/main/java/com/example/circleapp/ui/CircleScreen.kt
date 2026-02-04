package com.example.circleapp.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.circleapp.data.CircleRepository
import com.google.firebase.storage.FirebaseStorage
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.rememberCoroutineScope
import com.example.circleapp.data.SavedPhotosStore
import com.example.circleapp.data.saveJpegToGallery
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog


// Simple data class for circle info we care about in UI
data class CircleInfo(
    val name: String = "",
    val inviteCode: String = "",
    val status: String = "open",
    val closeAt: Timestamp? = null
)

// Simple data class for photo metadata (we will add more later in C3/C4)
data class PhotoItem(
    val id: String,
    val uploaderUid: String,
    val storagePath: String,
    val createdAt: Timestamp?,
    val downloadUrl: String? = null
)

private fun fetchDownloadUrl(
    storagePath: String,
    onResult: (String?) -> Unit
) {
    if (storagePath.isBlank()) {
        onResult(null)
        return
    }

    FirebaseStorage.getInstance()
        .reference
        .child(storagePath)
        .downloadUrl
        .addOnSuccessListener { uri -> onResult(uri.toString()) }
        .addOnFailureListener { onResult(null) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Composable
fun CircleScreen(
    circleId: String,
    onBack: () -> Unit
) {

    val db = remember { FirebaseFirestore.getInstance() }
    val repo = remember { CircleRepository() }
    val context = LocalContext.current

    // controls whether camera capture screen is showing
    var showCamera by remember { mutableStateOf(false) }

    // show upload progress
    var isUploading by remember { mutableStateOf(false) }

    // State for circle doc
    var circleInfo by remember { mutableStateOf<CircleInfo?>(null) }
    var circleError by remember { mutableStateOf<String?>(null) }

    // State for photos list
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }

    val savedStore = remember { SavedPhotosStore(context) }
    val scope = rememberCoroutineScope()
    var fullscreenImage by remember { mutableStateOf<Int?>(null) }

    // Tracks downloads in progress so we don’t start the same download twice
    val inProgress = remember { mutableStateListOf<String>() } // stores photoIds
    var remainingTime by remember { mutableStateOf("") }

    // --- 1) Listen to circle document ---
    DisposableEffect(circleId) {
        val reg = db.collection("circles")
            .document(circleId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    circleError = e.message
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    circleError = "Circle not found"
                    return@addSnapshotListener
                }

                val name = snap.getString("name") ?: ""
                val invite = snap.getString("inviteCode") ?: ""
                val status = snap.getString("status") ?: "open"
                val closeAt = snap.getTimestamp("closeAt")

                circleInfo = CircleInfo(
                    name = name,
                    inviteCode = invite,
                    status = status,
                    closeAt = closeAt
                )
            }

        onDispose {
            reg.remove()
        }
    }

    LaunchedEffect(circleInfo?.closeAt) {
        circleInfo?.closeAt?.toDate()?.time?.let { closeAtMillis ->
            while (System.currentTimeMillis() < closeAtMillis) {
                val remaining = closeAtMillis - System.currentTimeMillis()
                if (remaining > 0) {
                    remainingTime = DateUtils.formatElapsedTime(remaining / 1000)
                } else {
                    remainingTime = ""
                    break
                }
                delay(1000)
            }
            circleInfo = circleInfo?.copy()
        }
    }


    // --- 2) Listen to photos subcollection ---
    DisposableEffect(circleId) {
        val reg = db.collection("circles")
            .document(circleId)
            .collection("photos")
            .orderBy("createdAt")
            .addSnapshotListener { snap, e ->
                if (e != null) return@addSnapshotListener
                if (snap == null) return@addSnapshotListener

                // 1) Build the list WITHOUT URLs first
                val newList = snap.documents.map { doc ->
                    PhotoItem(
                        id = doc.id,
                        uploaderUid = doc.getString("uploaderUid") ?: "",
                        storagePath = doc.getString("storagePath") ?: "",
                        createdAt = doc.getTimestamp("createdAt"),
                        downloadUrl = null
                    )
                }

                // 2) Set it to state so UI updates immediately (shows "Loading image...")
                photos = newList

                // 3) For each photo, fetch the download URL and update that item in state
                newList.forEach { item ->
                    fetchDownloadUrl(item.storagePath) { url ->
                        if (url != null) {
                            photos = photos.map { p ->
                                if (p.id == item.id) p.copy(downloadUrl = url) else p
                            }
                        }
                    }
                }
            }

        onDispose {
            reg.remove()
        }
    }

    // Auto-save logic: whenever photos list updates, try saving new ones.
    LaunchedEffect(photos) {
        photos.forEach { p ->
            // We need a valid storagePath and photo id
            val photoId = p.id
            val storagePath = p.storagePath

            if (storagePath.isBlank()) return@forEach

            // Avoid starting the same download twice
            if (inProgress.contains(photoId)) return@forEach

            // Check if already saved (DataStore)
            val alreadySaved = savedStore.isSaved(circleId, photoId)
            if (alreadySaved) return@forEach

            // Mark "in progress" so we don't duplicate work
            inProgress.add(photoId)

            // Download + save in a coroutine
            scope.launch {
                try {
                    // 1) Download bytes from Firebase Storage (max 10MB)
                    val bytes = FirebaseStorage.getInstance()
                        .reference
                        .child(storagePath)
                        .getBytes(10L * 1024L * 1024L)
                        .await()

                    // 2) Save to gallery (Pictures/Circle)
                    val savedUri = saveJpegToGallery(
                        context = context,
                        bytes = bytes,
                        displayNameNoExt = "Circle_${circleId}_$photoId",
                        relativePath = "Pictures/Circle"
                    )

                    if (savedUri != null) {
                        // 3) Remember it's saved so we never save again
                        savedStore.markSaved(circleId, photoId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    inProgress.remove(photoId)
                }
            }
        }
    }


    // --- UI ---

    if (showCamera) {
        CameraCapture(
            onPhotoSaved = { uri ->
                // After capture -> upload
                isUploading = true

                repo.uploadPhotoToCircle(
                    circleId = circleId,
                    photoUri = uri,
                    onSuccess = { photoId ->
                        isUploading = false
                        showCamera = false

                        // ✅ NEW: mark uploader's own photo as already saved (prevents duplicates)
                        scope.launch {
                            savedStore.markSaved(circleId, photoId)
                        }

                        Toast.makeText(context, "Uploaded!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { e ->
                        isUploading = false
                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                )
            },
            onCancel = { showCamera = false }
        )
        return
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(circleInfo?.name ?: "Circle") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (circleError != null) {
                Text("Error: $circleError", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            // If we haven't loaded circle yet
            if (circleInfo == null) {
                Text("Loading circle...")
                return@Column
            }

            val info = circleInfo!!

            // Show invite code
            Text("Invite code: ${info.inviteCode}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // Show open/closed + time remaining
            val closeAtMillis = info.closeAt?.toDate()?.time
            val isClosed = closeAtMillis != null && System.currentTimeMillis() >= closeAtMillis

            Text(
                text = if (isClosed) "Status: CLOSED" else "Status: OPEN",
                style = MaterialTheme.typography.titleMedium
            )

            if (closeAtMillis != null && !isClosed) {
                Text("Closes in: $remainingTime")
            }

            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { showCamera = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading
            ) {
                Text(if (isUploading) "Uploading..." else "Share Photo")
            }

            Text("Photos (${photos.size})", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            if (photos.isEmpty()) {
                Text("No photos yet.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) { itemsIndexed(photos) { index, p ->
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fullscreenImage = index }) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                AsyncImage(
                                    model = p.downloadUrl,
                                    contentDescription = "Circle photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (fullscreenImage != null) {
        Dialog(onDismissRequest = { fullscreenImage = null }) {
            val pagerState = rememberPagerState(initialPage = fullscreenImage!!)
            HorizontalPager(
                count = photos.size,
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) {
                page ->
                AsyncImage(
                    model = photos[page].downloadUrl,
                    contentDescription = "Full screen image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}