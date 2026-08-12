package com.example.bookapp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.Prefs
import com.example.bookapp.data.SpeechHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextScreen(
    title: String,
    content: String,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    sectionId: Long? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isSpeaking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val speechHelper = remember {
        SpeechHelper(context) { status ->
            when (status) {
                "no_engine" -> statusMessage = "موتور خواندن صوتی روی این گوشی در دسترس نیست"
                "no_persian_voice" -> statusMessage = "صدای فارسی روی این گوشی نصب نیست (به تنظیمات گوشی مراجعه کنید)"
                "error" -> statusMessage = "خطا در پخش صدا"
            }
            if (status == "done" || status == "error") isSpeaking = false
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            isSpeaking = false
            statusMessage = null
        }
    }
    var tag by remember(sectionId) { mutableStateOf(sectionId?.let { Prefs.getTag(context, it) }) }
    var showTagDialog by remember { mutableStateOf(false) }

    // حالت تمام‌صفحه: نوار بالا هنگام اسکرول به پایین جمع می‌شود
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    DisposableEffect(Unit) {
        onDispose { speechHelper.shutdown() }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isSpeaking) {
                            speechHelper.stop()
                            isSpeaking = false
                        } else {
                            speechHelper.speak(content)
                            isSpeaking = true
                        }
                    }) {
                        Icon(
                            if (isSpeaking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (isSpeaking) "توقف خواندن" else "خواندن صوتی"
                        )
                    }
                    if (sectionId != null) {
                        IconButton(onClick = { showTagDialog = true }) {
                            Icon(Icons.Filled.Label, contentDescription = "برچسب شخصی")
                        }
                    }
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "نشان کردن"
                        )
                    }
                    IconButton(onClick = { copyToClipboard(context, title, content) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "کپی متن")
                    }
                    IconButton(onClick = { shareText(context, title, content) }) {
                        Icon(Icons.Filled.Share, contentDescription = "اشتراک‌گذاری")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (!tag.isNullOrBlank()) {
                AssistChip(onClick = { showTagDialog = true }, label = { Text(tag!!) })
                Spacer(Modifier.height(8.dp))
            }
            Text(content, style = MaterialTheme.typography.bodyLarge)
        }
    }

    if (showTagDialog && sectionId != null) {
        var input by remember { mutableStateOf(tag ?: "") }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("برچسب شخصی") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("مثلاً: حفظ کنم، برای مجلس بعدی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.setTag(context, sectionId, input)
                    tag = input.ifBlank { null }
                    showTagDialog = false
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("انصراف") }
            }
        )
    }
}

private fun copyToClipboard(context: Context, title: String, content: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(title, content)
    clipboard.setPrimaryClip(clip)
}

private fun shareText(context: Context, title: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n\n$content")
    }
    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
}

/**
 * حالت مطالعه حرفه‌ای: امکان سوایپ (کشیدن انگشت) بین بخش‌های یک نقش،
 * بدون نیاز به برگشتن به فهرست بعد از هر بخش.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TextPagerScreen(
    sections: List<com.example.bookapp.data.SectionEntity>,
    startIndex: Int,
    isBookmarked: (Long) -> Boolean,
    onToggleBookmark: (Long) -> Unit,
    onPageShown: (Long) -> Unit,
    onBack: () -> Unit
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = startIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
    ) { sections.size }

    LaunchedEffect(pagerState.currentPage) {
        if (sections.isNotEmpty()) {
            onPageShown(sections[pagerState.currentPage].id)
        }
    }

    androidx.compose.foundation.pager.HorizontalPager(state = pagerState) { page ->
        val section = sections[page]
        Column(Modifier.fillMaxSize()) {
            TextScreen(
                title = "${section.title}  (${page + 1}/${sections.size})",
                content = section.content,
                isBookmarked = isBookmarked(section.id),
                onToggleBookmark = { onToggleBookmark(section.id) },
                sectionId = section.id,
                onBack = onBack
            )
        }
    }
}
