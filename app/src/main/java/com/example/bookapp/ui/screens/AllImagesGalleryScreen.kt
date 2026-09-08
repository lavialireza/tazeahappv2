package com.example.bookapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** یک تصویر در گالری سراسری. */
data class GalleryImageItem(
    val id: Long,
    val filePath: String,
    val caption: String,
    val taziehId: Long,
    val taziehTitle: String
)

data class GalleryTaziehItem(val id: Long, val title: String)

/**
 * مدیریت سراسری تصاویر همه تعزیه‌ها.
 * افزودن، ویرایش توضیح، حذف، جستجو/فیلتر و مشاهده تمام‌صفحه در همین صفحه انجام می‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllImagesGalleryScreen(
    images: List<GalleryImageItem>,
    taziehs: List<GalleryTaziehItem>,
    onAddImage: (Uri, Long) -> Unit,
    onDeleteImage: (GalleryImageItem) -> Unit,
    onUpdateCaption: (GalleryImageItem, String) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedTaziehId by remember { mutableStateOf<Long?>(null) }
    var editingImage by remember { mutableStateOf<GalleryImageItem?>(null) }
    var captionText by remember { mutableStateOf("") }
    var fullImage by remember { mutableStateOf<GalleryImageItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GalleryImageItem?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            showAddDialog = true
        }
    }

    val filteredImages = remember(images, query, selectedTaziehId) {
        val q = query.trim()
        images.filter { image ->
            (selectedTaziehId == null || image.taziehId == selectedTaziehId) &&
                (q.isBlank() || image.caption.contains(q, ignoreCase = true) ||
                    image.taziehTitle.contains(q, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدیریت تصاویر") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { pickImageLauncher.launch("image/*") }) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = "افزودن عکس")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("افزودن عکس") },
                icon = { Icon(Icons.Filled.AddAPhoto, contentDescription = null) },
                onClick = { pickImageLauncher.launch("image/*") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("جستجوی تصویر، توضیح یا نام تعزیه") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTaziehId == null,
                    onClick = { selectedTaziehId = null },
                    label = { Text("همه تعزیه‌ها") }
                )
                OutlinedButton(onClick = { showFilterDialog = true }) {
                    Text(taziehs.firstOrNull { it.id == selectedTaziehId }?.title ?: "انتخاب تعزیه")
                }
            }

            Text(
                "${filteredImages.size} تصویر",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(12.dp)
            )

            if (filteredImages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (images.isEmpty()) "هنوز عکسی اضافه نشده است." else "تصویری مطابق جستجو پیدا نشد.",
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredImages, key = { it.id }) { image ->
                        Card(shape = RoundedCornerShape(12.dp)) {
                            Column {
                                AsyncImage(
                                    model = image.filePath,
                                    contentDescription = image.caption,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                        .clickable { fullImage = image }
                                )
                                Text(
                                    image.taziehTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, top = 7.dp, end = 8.dp)
                                )
                                Text(
                                    image.caption.ifBlank { "بدون توضیح" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(onClick = {
                                        editingImage = image
                                        captionText = image.caption
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "ویرایش توضیح")
                                    }
                                    IconButton(onClick = { deleteTarget = image }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف عکس")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val currentEditing = editingImage
    if (currentEditing != null) {
        AlertDialog(
            onDismissRequest = { editingImage = null },
            title = { Text("ویرایش توضیح عکس") },
            text = {
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    label = { Text("توضیح") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateCaption(currentEditing, captionText.trim())
                    editingImage = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editingImage = null }) { Text("انصراف") }
            }
        )
    }

    val uriToAdd = pendingUri
    if (showAddDialog && uriToAdd != null) {
        var addTaziehId by remember { mutableStateOf<Long?>(taziehs.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false; pendingUri = null },
            title = { Text("افزودن تصویر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("این تصویر مربوط به کدام تعزیه است؟")
                    taziehs.forEach { tazieh ->
                        FilterChip(
                            selected = addTaziehId == tazieh.id,
                            onClick = { addTaziehId = tazieh.id },
                            label = { Text(tazieh.title) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = addTaziehId != null,
                    onClick = {
                        addTaziehId?.let { onAddImage(uriToAdd, it) }
                        showAddDialog = false
                        pendingUri = null
                    }
                ) { Text("افزودن") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; pendingUri = null }) { Text("انصراف") }
            }
        )
    }

    val full = fullImage
    if (full != null) {
        AlertDialog(
            onDismissRequest = { fullImage = null },
            confirmButton = {
                TextButton(onClick = { fullImage = null }) { Text("بستن") }
            },
            title = { Text(full.taziehTitle) },
            text = {
                Column {
                    AsyncImage(
                        model = full.filePath,
                        contentDescription = full.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                    )
                    if (full.caption.isNotBlank()) {
                        Text(full.caption, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        )
    }
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("فیلتر بر اساس تعزیه") },
            text = {
                Column {
                    TextButton(
                        onClick = { selectedTaziehId = null; showFilterDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("همه تعزیه‌ها") }
                    taziehs.forEach { tazieh ->
                        TextButton(
                            onClick = { selectedTaziehId = tazieh.id; showFilterDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(tazieh.title) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) { Text("بستن") }
            }
        )
    }

    val targetToDelete = deleteTarget
    if (targetToDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف تصویر") },
            text = { Text("آیا این تصویر از برنامه حذف شود؟ این عمل قابل بازگشت نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteImage(targetToDelete)
                    deleteTarget = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("انصراف") }
            }
        )
    }

}
