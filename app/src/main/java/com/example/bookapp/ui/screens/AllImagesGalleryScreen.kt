package com.example.bookapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bookapp.data.TaziehEntity

data class GalleryImageItem(val id: Long, val filePath: String, val caption: String, val taziehTitle: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllImagesGalleryScreen(
    images: List<GalleryImageItem>,
    taziehs: List<TaziehEntity> = emptyList(),
    onBack: () -> Unit,
    onDeleteImage: (GalleryImageItem) -> Unit = {},
    onUpdateCaption: (GalleryImageItem, String) -> Unit = { _, _ -> }
) {
    var query by remember { mutableStateOf("") }
    var selectedTazieh by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<GalleryImageItem?>(null) }
    var caption by remember { mutableStateOf("") }
    val filtered = remember(images, query, selectedTazieh) {
        images.filter { image ->
            (query.isBlank() || image.caption.contains(query, true) || image.taziehTitle.contains(query, true)) &&
                (selectedTazieh == null || taziehs.firstOrNull { it.id == selectedTazieh }?.title == image.taziehTitle)
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("مدیریت گالری تصاویر") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("جستجو در توضیح و نام تعزیه") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedTazieh == null, onClick = { selectedTazieh = null }, label = { Text("همه") })
                taziehs.take(8).forEach { t -> FilterChip(selected = selectedTazieh == t.id, onClick = { selectedTazieh = t.id }, label = { Text(t.title) }) }
            }
            if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("عکسی مطابق فیلتر پیدا نشد") }
            else LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { image ->
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column {
                            AsyncImage(model = image.filePath, contentDescription = image.caption, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).clickable { editing=image; caption=image.caption })
                            Text(image.taziehTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal=8.dp, vertical=6.dp))
                            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { editing=image; caption=image.caption }) { Text("✎") }
                                IconButton(onClick = { onDeleteImage(image) }) { Icon(Icons.Filled.Delete, contentDescription="حذف عکس") }
                            }
                            Text(image.caption.ifBlank { "بدون توضیح" }, style=MaterialTheme.typography.bodySmall, modifier=Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
    val current=editing
    if(current!=null) AlertDialog(onDismissRequest={editing=null}, title={Text("ویرایش توضیح عکس")}, text={OutlinedTextField(value=caption,onValueChange={caption=it},modifier=Modifier.fillMaxWidth())}, confirmButton={TextButton(onClick={onUpdateCaption(current,caption.trim());editing=null}){Text("ذخیره")}}, dismissButton={TextButton(onClick={editing=null}){Text("انصراف")}})
}
